package com.l299l.npcspeak.conversation;

import com.l299l.npcspeak.ai.AiBackend;
import com.l299l.npcspeak.ai.AiMessage;
import com.l299l.npcspeak.config.NpcTaskConfig;
import com.l299l.npcspeak.cooldown.CooldownManager;
import com.l299l.npcspeak.npc.NpcData;
import com.l299l.npcspeak.npc.NpcFormatter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConversationManager {

    private final JavaPlugin plugin;
    private final AiBackend backend;
    private final CooldownManager cooldownManager;
    private final TaskManager taskManager;
    private final int maxExchanges;
    private final long cooldownMs;
    private final int listenTimeoutSeconds;

    // player UUID → NPC they are currently talking to
    private final Map<UUID, NpcData> activeConversations = new ConcurrentHashMap<>();
    // player UUID → (npc id → session)
    private final Map<UUID, Map<String, ConversationSession>> sessions = new ConcurrentHashMap<>();
    // player UUID → pending listen-timeout task
    private final Map<UUID, BukkitTask> listenTimeouts = new ConcurrentHashMap<>();

    public ConversationManager(JavaPlugin plugin, AiBackend backend, CooldownManager cooldownManager,
                               TaskManager taskManager, int maxExchanges, int cooldownSeconds,
                               int listenTimeoutSeconds) {
        this.plugin = plugin;
        this.backend = backend;
        this.cooldownManager = cooldownManager;
        this.taskManager = taskManager;
        this.maxExchanges = maxExchanges;
        this.cooldownMs = cooldownSeconds * 1000L;
        this.listenTimeoutSeconds = listenTimeoutSeconds;
    }

    /** Called from main thread when a player right-clicks an NPC. */
    public void startConversation(Player player, NpcData npc) {
        NpcData current = activeConversations.get(player.getUniqueId());

        if (current != null && current.getId().equals(npc.getId())) {
            endConversation(player, npc, true);
            return;
        }

        activeConversations.put(player.getUniqueId(), npc);
        NpcFormatter.sendGreeting(player, npc.getDisplayName(), npc.getConfig().getGreetingOrDefault());
        scheduleListenTimeout(player, npc);
    }

    public boolean isInConversation(UUID playerId) {
        return activeConversations.containsKey(playerId);
    }

    /** Called from the async chat thread. */
    public void handleChatInput(Player player, String text) {
        NpcData npc = activeConversations.get(player.getUniqueId());
        if (npc == null) return;

        ConversationSession session = getOrCreateSession(player.getUniqueId(), npc.getId());

        if (session.isPending()) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    NpcFormatter.sendPendingNotice(player, npc.getDisplayName()));
            return;
        }

        if (cooldownManager.isOnCooldown(player.getUniqueId(), npc.getId(), cooldownMs)) {
            long left = cooldownManager.remainingSeconds(player.getUniqueId(), npc.getId(), cooldownMs);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    NpcFormatter.sendCooldownNotice(player, npc.getDisplayName(), left));
            return;
        }

        cancelListenTimeout(player.getUniqueId());
        cooldownManager.record(player.getUniqueId(), npc.getId());
        session.setPending(true);

        plugin.getServer().getScheduler().runTask(plugin, () ->
                NpcFormatter.sendTypingIndicator(player, npc.getDisplayName()));

        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", npc.getConfig().buildSystemPrompt(npc.getDisplayName())));
        messages.addAll(session.getHistory());
        messages.add(new AiMessage("user", text));

        backend.complete(messages)
                .thenAccept(response -> {
                    session.addExchange(text, response);
                    session.setPending(false);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && activeConversations.containsKey(player.getUniqueId())) {
                            NpcFormatter.sendResponse(player, npc.getDisplayName(), response);
                            scheduleListenTimeout(player, npc);
                        }
                    });
                    maybeRunEvaluator(player, npc, session.getHistory());
                })
                .exceptionally(err -> {
                    session.setPending(false);
                    plugin.getLogger().warning(backend.getName() + " error: " + err.getMessage());
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            NpcFormatter.sendError(player, npc.getDisplayName(), backend.getName());
                            scheduleListenTimeout(player, npc);
                        }
                    });
                    return null;
                });
    }

    /** Called when a player disconnects. */
    public void clearPlayer(UUID playerId) {
        cancelListenTimeout(playerId);
        activeConversations.remove(playerId);
        sessions.remove(playerId);
        cooldownManager.clearPlayer(playerId);
        taskManager.clearPlayer(playerId);
    }

    private void endConversation(Player player, NpcData npc, boolean sendFarewell) {
        cancelListenTimeout(player.getUniqueId());
        activeConversations.remove(player.getUniqueId());
        if (sendFarewell) {
            NpcFormatter.sendFarewell(player, npc.getDisplayName());
        }
    }

    private void scheduleListenTimeout(Player player, NpcData npc) {
        cancelListenTimeout(player.getUniqueId());
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            listenTimeouts.remove(player.getUniqueId());
            if (activeConversations.remove(player.getUniqueId(), npc) && player.isOnline()) {
                NpcFormatter.sendTimeout(player, npc.getDisplayName());
            }
        }, listenTimeoutSeconds * 20L);
        listenTimeouts.put(player.getUniqueId(), task);
    }

    private void cancelListenTimeout(UUID playerId) {
        BukkitTask task = listenTimeouts.remove(playerId);
        if (task != null) task.cancel();
    }

    private ConversationSession getOrCreateSession(UUID playerId, String npcId) {
        return sessions
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(npcId, k -> new ConversationSession(maxExchanges));
    }

    // --- Task evaluation ---

    private void maybeRunEvaluator(Player player, NpcData npc, List<AiMessage> history) {
        NpcTaskConfig task = npc.getConfig().getTask();
        if (task == null || !task.isEvaluatable()) return;

        TaskState state = taskManager.getState(player.getUniqueId(), npc.getId());
        if (state == TaskState.SUCCESS || state == TaskState.FAILED) return;

        int exchanges = taskManager.incrementExchanges(player.getUniqueId(), npc.getId());
        if (task.getMaxExchanges() > 0 && exchanges >= task.getMaxExchanges()) {
            taskManager.setState(player.getUniqueId(), npc.getId(), TaskState.FAILED);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    NpcFormatter.sendTaskFailed(player, npc.getDisplayName());
                    executeCommands(task.getOnFailure(), player, "");
                    endConversation(player, npc, false);
                }
            });
            return;
        }

        List<AiMessage> evalMessages = buildEvaluatorMessages(history, task.getOutcomeCheck());
        backend.complete(evalMessages)
                .thenAccept(evalResponse -> {
                    String trimmed = evalResponse.trim();
                    if (trimmed.toUpperCase().startsWith("YES")) {
                        String outcome = trimmed.length() > 3 ? trimmed.substring(3).trim() : "";
                        taskManager.setState(player.getUniqueId(), npc.getId(), TaskState.SUCCESS);
                        sendClosingStatement(player, npc, history, outcome, task);
                    }
                })
                .exceptionally(err -> {
                    plugin.getLogger().warning("Task evaluator error for NPC '" + npc.getId() + "': " + err.getMessage());
                    return null;
                });
    }

    private void sendClosingStatement(Player player, NpcData npc, List<AiMessage> history,
                                      String outcome, NpcTaskConfig task) {
        List<AiMessage> closingMessages = new ArrayList<>();
        closingMessages.add(new AiMessage("system", npc.getConfig().buildSystemPrompt(npc.getDisplayName())));
        closingMessages.addAll(history);
        closingMessages.add(new AiMessage("user",
                "[The conversation has reached its conclusion. Outcome: " + (outcome.isBlank() ? "success" : outcome) +
                ". Deliver ONE brief in-character closing statement to end the conversation naturally. " +
                "Do NOT ask any questions. Do NOT continue the topic.]"));

        backend.complete(closingMessages)
                .thenAccept(closing -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        NpcFormatter.sendResponse(player, npc.getDisplayName(), closing);
                        NpcFormatter.sendTaskSuccess(player, npc.getDisplayName(), outcome);
                        executeCommands(task.getOnSuccess(), player, outcome);
                        endConversation(player, npc, false);
                    }
                }))
                .exceptionally(err -> {
                    plugin.getLogger().warning("Task closing call error for NPC '" + npc.getId() + "': " + err.getMessage());
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            NpcFormatter.sendTaskSuccess(player, npc.getDisplayName(), outcome);
                            executeCommands(task.getOnSuccess(), player, outcome);
                            endConversation(player, npc, false);
                        }
                    });
                    return null;
                });
    }

    private List<AiMessage> buildEvaluatorMessages(List<AiMessage> history, String outcomeCheck) {
        StringBuilder transcript = new StringBuilder("Transcript:");
        for (AiMessage msg : history) {
            String role = "user".equals(msg.role()) ? "Player" : "NPC";
            transcript.append("\n[").append(role).append("]: ").append(msg.content());
        }
        transcript.append("\n\nQuestion: ").append(outcomeCheck);

        return List.of(
                new AiMessage("system",
                        "You are a strict conversation evaluator. Review the transcript and answer the question exactly as instructed. " +
                        "Reply ONLY with \"YES <value>\" (replace <value> with the relevant extracted value) or \"NO\". No other text."),
                new AiMessage("user", transcript.toString())
        );
    }

    private void executeCommands(List<String> commands, Player player, String outcome) {
        for (String cmd : commands) {
            String processed = cmd
                    .replace("%player%", player.getName())
                    .replace("%outcome%", outcome);
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), processed);
        }
    }
}

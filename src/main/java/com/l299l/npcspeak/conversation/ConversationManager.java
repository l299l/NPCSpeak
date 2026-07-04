package com.l299l.npcspeak.conversation;

import com.l299l.npcspeak.ai.AiBackend;
import com.l299l.npcspeak.ai.AiMessage;
import com.l299l.npcspeak.config.ModerationConfig;
import com.l299l.npcspeak.config.NpcConfig;
import com.l299l.npcspeak.config.NpcTaskConfig;
import com.l299l.npcspeak.cooldown.CooldownManager;
import com.l299l.npcspeak.logging.ConversationLogger;
import com.l299l.npcspeak.memory.NpcMemoryManager;
import com.l299l.npcspeak.npc.NpcData;
import com.l299l.npcspeak.npc.NpcFormatter;
import com.l299l.npcspeak.papi.PapiExpander;
import com.l299l.npcspeak.vault.VaultHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ConversationManager {

    private final JavaPlugin plugin;
    private final AiBackend backend;
    private final CooldownManager cooldownManager;
    private final TaskManager taskManager;
    private final NpcMemoryManager memoryManager;
    private final boolean memorySummaryEnabled;
    private final int memoryMaxAgeDays;
    private final boolean streamingEnabled;
    private final ModerationConfig moderation;
    private final ConversationLogger logger;
    private final VaultHook vault;
    private final int maxExchanges;
    private final long cooldownMs;
    private final int listenTimeoutSeconds;

    private final Map<UUID, NpcData> activeConversations = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, ConversationSession>> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> listenTimeouts = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> cachedMemory = new ConcurrentHashMap<>();

    public ConversationManager(JavaPlugin plugin, AiBackend backend, CooldownManager cooldownManager,
                               TaskManager taskManager, NpcMemoryManager memoryManager,
                               boolean memorySummaryEnabled, int memoryMaxAgeDays,
                               boolean streamingEnabled, ModerationConfig moderation,
                               ConversationLogger logger, VaultHook vault,
                               int maxExchanges, int cooldownSeconds, int listenTimeoutSeconds) {
        this.plugin = plugin;
        this.backend = backend;
        this.cooldownManager = cooldownManager;
        this.taskManager = taskManager;
        this.memoryManager = memoryManager;
        this.memorySummaryEnabled = memorySummaryEnabled;
        this.memoryMaxAgeDays = memoryMaxAgeDays;
        this.streamingEnabled = streamingEnabled;
        this.moderation = moderation;
        this.logger = logger;
        this.vault = vault;
        this.maxExchanges = maxExchanges;
        this.cooldownMs = cooldownSeconds * 1000L;
        this.listenTimeoutSeconds = listenTimeoutSeconds;
    }

    public void startConversation(Player player, NpcData npc) {
        NpcData current = activeConversations.get(player.getUniqueId());

        if (current != null && current.getId().equals(npc.getId())) {
            endConversation(player, npc, true);
            return;
        }

        NpcConfig cfg = npc.getConfig();
        if (cfg.getMaxInteractions() > 0 || cfg.isLockAfterTask()) {
            NpcMemoryManager.PlayerMeta meta = memoryManager.loadMeta(npc.getId(), player.getUniqueId());
            if (cfg.isLockAfterTask() && meta.locked()) {
                NpcFormatter.sendInteractionBlocked(player, npc.getDisplayName());
                return;
            }
            if (cfg.getMaxInteractions() > 0 && meta.interactions() >= cfg.getMaxInteractions()) {
                NpcFormatter.sendInteractionBlocked(player, npc.getDisplayName());
                return;
            }
        }

        taskManager.clearNpc(player.getUniqueId(), npc.getId());
        if (cfg.getTask() != null && cfg.getTask().isEvaluatable()) {
            clearSession(player.getUniqueId(), npc.getId());
        }

        activeConversations.put(player.getUniqueId(), npc);

        if (memorySummaryEnabled && cfg.isMemoryEnabledFor(true)) {
            String memory = memoryManager.load(npc.getId(), player.getUniqueId(), memoryMaxAgeDays);
            if (memory != null) {
                cachedMemory.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                        .put(npc.getId(), memory);
            } else {
                Map<String, String> m = cachedMemory.get(player.getUniqueId());
                if (m != null) m.remove(npc.getId());
            }
        }

        String greeting = PapiExpander.expand(player, cfg.getGreetingOrDefault());
        NpcFormatter.sendGreeting(player, npc.getDisplayName(), greeting);
        scheduleListenTimeout(player, npc);
    }

    public boolean isInConversation(UUID playerId) {
        return activeConversations.containsKey(playerId);
    }

    public String getActiveNpcName(UUID playerId) {
        NpcData npc = activeConversations.get(playerId);
        return npc != null ? npc.getDisplayName() : "";
    }

    public int getSessionExchangeCount(UUID playerId, String npcId) {
        Map<String, ConversationSession> playerSessions = sessions.get(playerId);
        if (playerSessions == null) return 0;
        ConversationSession session = playerSessions.get(npcId);
        return session != null ? session.getExchangeCount() : 0;
    }

    public String getTaskStatusString(UUID playerId, String npcId) {
        TaskState state = taskManager.getState(playerId, npcId);
        if (state == null) return "none";
        return switch (state) {
            case ACTIVE -> "active";
            case SUCCESS -> "success";
            case FAILED -> "failed";
        };
    }

    public void handleChatInput(Player player, String text) {
        NpcData npc = activeConversations.get(player.getUniqueId());
        if (npc == null) return;

        ConversationSession session = getOrCreateSession(player.getUniqueId(), npc.getId());

        if (session.isPending()) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    NpcFormatter.sendPendingNotice(player, npc.getDisplayName()));
            return;
        }

        if (moderation.enabled() && isBlocked(text)) {
            logger.logBlocked(npc.getId(), npc.getDisplayName(), player.getName(), text);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                NpcFormatter.sendResponse(player, npc.getDisplayName(), moderation.blockedResponse());
                scheduleListenTimeout(player, npc);
            });
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

        if (streamingEnabled) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendActionBar(Component.text("✏ ...", NamedTextColor.GRAY)));
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    NpcFormatter.sendTypingIndicator(player, npc.getDisplayName()));
        }

        dispatchToBackend(player, npc, session, text);
    }

    public void injectMessage(Player player, String text) {
        NpcData npc = activeConversations.get(player.getUniqueId());
        if (npc == null) return;
        ConversationSession session = getOrCreateSession(player.getUniqueId(), npc.getId());
        if (session.isPending()) {
            NpcFormatter.sendPendingNotice(player, npc.getDisplayName());
            return;
        }
        cancelListenTimeout(player.getUniqueId());
        cooldownManager.record(player.getUniqueId(), npc.getId());
        session.setPending(true);
        if (streamingEnabled) {
            player.sendActionBar(Component.text("✏ ...", NamedTextColor.GRAY));
        } else {
            NpcFormatter.sendTypingIndicator(player, npc.getDisplayName());
        }
        dispatchToBackend(player, npc, session, text);
    }

    public void clearPlayer(UUID playerId) {
        cancelListenTimeout(playerId);
        activeConversations.remove(playerId);
        sessions.remove(playerId);
        cachedMemory.remove(playerId);
        cooldownManager.clearPlayer(playerId);
        taskManager.clearPlayer(playerId);
    }

    private void endConversation(Player player, NpcData npc, boolean sendFarewell) {
        cancelListenTimeout(player.getUniqueId());
        activeConversations.remove(player.getUniqueId());
        doEndConversationWork(player, npc);
        if (sendFarewell) {
            NpcFormatter.sendFarewell(player, npc.getDisplayName());
        }
    }

    private void doEndConversationWork(Player player, NpcData npc) {
        maybeFailActiveTask(player, npc);
        updateInteractionMeta(player, npc);
        maybeSummarize(player, npc);
    }

    private void maybeFailActiveTask(Player player, NpcData npc) {
        NpcTaskConfig task = npc.getConfig().getTask();
        if (task == null || !task.isEvaluatable()) return;
        TaskState state = taskManager.getState(player.getUniqueId(), npc.getId());
        if (state == TaskState.SUCCESS || state == TaskState.FAILED) return;
        taskManager.setState(player.getUniqueId(), npc.getId(), TaskState.FAILED);
        if (player.isOnline()) {
            NpcFormatter.sendTaskFailed(player, npc.getDisplayName());
            executeCommands(task.getOnFailure(), player, "");
        }
    }

    private void updateInteractionMeta(Player player, NpcData npc) {
        NpcConfig cfg = npc.getConfig();
        if (cfg.getMaxInteractions() <= 0 && !cfg.isLockAfterTask()) return;
        NpcMemoryManager.PlayerMeta meta = memoryManager.loadMeta(npc.getId(), player.getUniqueId());
        TaskState finalState = cfg.getTask() != null
                ? taskManager.getState(player.getUniqueId(), npc.getId()) : null;
        boolean nowLocked = meta.locked() ||
                (cfg.isLockAfterTask() &&
                 (finalState == TaskState.SUCCESS || finalState == TaskState.FAILED));
        memoryManager.saveMeta(npc.getId(), player.getUniqueId(),
                new NpcMemoryManager.PlayerMeta(meta.interactions() + 1, nowLocked));
    }

    private void scheduleListenTimeout(Player player, NpcData npc) {
        cancelListenTimeout(player.getUniqueId());
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            listenTimeouts.remove(player.getUniqueId());
            if (activeConversations.remove(player.getUniqueId(), npc)) {
                doEndConversationWork(player, npc);
                if (player.isOnline()) {
                    NpcFormatter.sendTimeout(player, npc.getDisplayName());
                }
            }
        }, listenTimeoutSeconds * 20L);
        listenTimeouts.put(player.getUniqueId(), task);
    }

    private void cancelListenTimeout(UUID playerId) {
        BukkitTask task = listenTimeouts.remove(playerId);
        if (task != null) task.cancel();
    }

    private void clearSession(UUID playerId, String npcId) {
        Map<String, ConversationSession> m = sessions.get(playerId);
        if (m != null) m.remove(npcId);
    }

    private ConversationSession getOrCreateSession(UUID playerId, String npcId) {
        return sessions
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(npcId, k -> new ConversationSession(maxExchanges));
    }

    private void dispatchToBackend(Player player, NpcData npc, ConversationSession session, String text) {
        List<AiMessage> messages = buildConversationMessages(player, npc, session, text);

        CompletableFuture<String> future;
        if (streamingEnabled) {
            StringBuilder buffer = new StringBuilder();
            AtomicLong lastUpdateMs = new AtomicLong(0);
            future = backend.streamComplete(messages, token -> {
                buffer.append(token);
                long now = System.currentTimeMillis();
                if (now - lastUpdateMs.get() < 80) return;
                lastUpdateMs.set(now);
                String raw = buffer.toString();
                String preview = raw.length() <= 55 ? raw : "…" + raw.substring(raw.length() - 54);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && activeConversations.containsKey(player.getUniqueId())) {
                        player.sendActionBar(Component.text(preview, NamedTextColor.WHITE));
                    }
                });
            });
        } else {
            future = backend.complete(messages);
        }

        future.thenAccept(response -> {
            session.addExchange(text, response);
            session.setPending(false);
            if (npc.getConfig().isLogConversations()) {
                logger.log(npc.getId(), player.getName(), text, response);
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && activeConversations.containsKey(player.getUniqueId())) {
                    if (streamingEnabled) player.sendActionBar(Component.text(" "));
                    NpcFormatter.sendResponse(player, npc.getDisplayName(), response);
                    NpcTaskConfig task = npc.getConfig().getTask();
                    if (task != null && "negotiate".equals(task.getType())
                            && task.isEvaluatable() && responseContainsOffer(response)) {
                        NpcFormatter.sendNegotiateButtons(player);
                    }
                    scheduleListenTimeout(player, npc);
                }
            });
            maybeRunEvaluator(player, npc, session.getHistory());
        }).exceptionally(err -> {
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

    private List<AiMessage> buildConversationMessages(Player player, NpcData npc,
                                                      ConversationSession session, String userText) {
        String systemPrompt = PapiExpander.expand(player, npc.getConfig().buildSystemPrompt(npc.getDisplayName()));
        Map<String, String> playerMemory = cachedMemory.get(player.getUniqueId());
        if (playerMemory != null) {
            String memoryNote = playerMemory.get(npc.getId());
            if (memoryNote != null) {
                systemPrompt += " [Your memory from a previous conversation with this player: " + memoryNote + "]";
            }
        }
        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", systemPrompt));
        messages.addAll(session.getHistory());
        messages.add(new AiMessage("user", userText));
        return messages;
    }

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

        List<AiMessage> evalMessages = buildEvaluatorMessages(history, task.getOutcomeCheck(), task.isIntelligentQuantity());
        backend.complete(evalMessages)
                .thenAccept(evalResponse -> {
                    String trimmed = evalResponse.trim();
                    String upper = trimmed.toUpperCase();
                    if (upper.startsWith("YES")) {
                        String raw = trimmed.length() > 3 ? trimmed.substring(3).trim() : "";
                        int quantity = 1;
                        String outcome = raw;
                        if (task.isIntelligentQuantity()) {
                            int xIdx = raw.indexOf('x');
                            if (xIdx > 0) {
                                try {
                                    int q = Integer.parseInt(raw.substring(0, xIdx).trim());
                                    if (q > 0) {
                                        quantity = q;
                                        outcome = raw.substring(xIdx + 1).trim();
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                        taskManager.setState(player.getUniqueId(), npc.getId(), TaskState.SUCCESS);
                        sendClosingStatement(player, npc, history, outcome, task, quantity);
                    } else if (upper.startsWith("FAIL")) {
                        taskManager.setState(player.getUniqueId(), npc.getId(), TaskState.FAILED);
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                NpcFormatter.sendTaskFailed(player, npc.getDisplayName());
                                executeCommands(task.getOnFailure(), player, "");
                                endConversation(player, npc, false);
                            }
                        });
                    }
                })
                .exceptionally(err -> {
                    plugin.getLogger().warning("Task evaluator error for NPC '" + npc.getId() + "': " + err.getMessage());
                    return null;
                });
    }

    private void sendClosingStatement(Player player, NpcData npc, List<AiMessage> history,
                                      String outcome, NpcTaskConfig task, int quantity) {
        List<AiMessage> closingMessages = new ArrayList<>();
        String closingSystemPrompt = PapiExpander.expand(player, npc.getConfig().buildSystemPrompt(npc.getDisplayName()));
        closingMessages.add(new AiMessage("system", closingSystemPrompt));
        closingMessages.addAll(history);
        closingMessages.add(new AiMessage("user",
                "[The conversation has reached its conclusion. Outcome: " + (outcome.isBlank() ? "success" : outcome) +
                ". Deliver ONE brief in-character closing statement to end the conversation naturally. " +
                "Do NOT ask any questions. Do NOT continue the topic.]"));

        backend.complete(closingMessages)
                .thenAccept(closing -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    NpcFormatter.sendResponse(player, npc.getDisplayName(), closing);
                    if (!checkRequirements(task.getRequire(), player, outcome, quantity)) {
                        NpcFormatter.sendRequirementsFailed(player, npc.getDisplayName());
                        taskManager.setState(player.getUniqueId(), npc.getId(), TaskState.FAILED);
                        executeCommands(task.getOnFailure(), player, outcome);
                        endConversation(player, npc, false);
                        return;
                    }
                    NpcFormatter.sendTaskSuccess(player, npc.getDisplayName(), outcome);
                    executeCommands(task.getOnSuccess(), player, outcome, quantity);
                    endConversation(player, npc, false);
                }))
                .exceptionally(err -> {
                    plugin.getLogger().warning("Task closing call error for NPC '" + npc.getId() + "': " + err.getMessage());
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (!checkRequirements(task.getRequire(), player, outcome, quantity)) {
                            NpcFormatter.sendRequirementsFailed(player, npc.getDisplayName());
                            taskManager.setState(player.getUniqueId(), npc.getId(), TaskState.FAILED);
                            executeCommands(task.getOnFailure(), player, outcome);
                        } else {
                            NpcFormatter.sendTaskSuccess(player, npc.getDisplayName(), outcome);
                            executeCommands(task.getOnSuccess(), player, outcome, quantity);
                        }
                        endConversation(player, npc, false);
                    });
                    return null;
                });
    }

    private boolean checkRequirements(List<String> require, Player player, String outcome, int quantity) {
        for (String req : require) {
            String processed = req
                    .replace("%player%", player.getName())
                    .replace("%outcome%", outcome);
            String[] parts = processed.split("\\s+", 2);
            if (parts.length < 2) continue;
            switch (parts[0].toLowerCase()) {
                case "eco" -> {
                    if (vault == null) continue;
                    try {
                        double amount = Double.parseDouble(parts[1].trim()) * quantity;
                        if (!vault.has(player, amount)) return false;
                    } catch (NumberFormatException ignored) {}
                }
                case "item" -> {
                    String[] itemParts = parts[1].split("\\s+");
                    if (itemParts.length < 2) continue;
                    Material mat = Material.matchMaterial(itemParts[0]);
                    if (mat == null) continue;
                    try {
                        int count = Integer.parseInt(itemParts[1]) * quantity;
                        if (!player.getInventory().containsAtLeast(new ItemStack(mat), count)) return false;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return true;
    }

    private List<AiMessage> buildEvaluatorMessages(List<AiMessage> history, String outcomeCheck,
                                                    boolean intelligentQuantity) {
        StringBuilder transcript = new StringBuilder("Transcript:");
        for (AiMessage msg : history) {
            String role = "user".equals(msg.role()) ? "Player" : "NPC";
            transcript.append("\n[").append(role).append("]: ").append(msg.content());
        }
        transcript.append("\n\nQuestion: ").append(outcomeCheck);

        String quantityNote = intelligentQuantity
                ? "\n\nQuantity format: if multiple units were explicitly agreed upon, write YES <quantity>x<value>" +
                  " (e.g. YES 2x40 for 2 items at 40 each). For a single unit write YES <value> as normal."
                : "";

        return List.of(
                new AiMessage("system",
                        "You are a task-completion evaluator for a Minecraft NPC conversation. " +
                        "Reply with exactly one of three tokens:\n\n" +
                        "\"YES <value>\" — BOTH sides have confirmed the SAME specific terms. ALL of these must be true:\n" +
                        "  1. The Player used explicit acceptance language in their own messages: " +
                        "'yes', 'deal', 'agreed', 'fine', 'I accept', 'I'll take it', 'okay', 'done', 'sold', or equivalent.\n" +
                        "  2. The NPC confirmed with 'deal', 'agreed', 'sold', 'done', or similar — OR performed a completion " +
                        "action in asterisks (*hands over item*, *accepts payment*, *gives the sword*, etc.).\n" +
                        "  3. Both refer to the same price or outcome.\n" +
                        "CRITICAL: A player OFFERING a price ('How about 45?', 'I can do 40', 'What about 38?') is NOT acceptance — " +
                        "it is a counter-offer. An NPC announcing 'we have a deal!' or 'deal!' after a player offer (not acceptance) " +
                        "does NOT make it YES. The player themselves must say yes.\n\n" +
                        "\"FAIL\" — the player has clearly refused or abandoned the task:\n" +
                        "  • Player explicitly refuses ('not interested', 'no thanks', 'forget it', 'goodbye', 'never mind')\n" +
                        "  • 3+ consecutive messages where the player ignores the task with no sign of returning\n" +
                        "A single off-topic message or counter-offer is NOT a FAIL.\n\n" +
                        "\"NO\" — everything else: haggling ongoing, player asked a question, player made an offer, uncertainty. " +
                        "Default to NO when in doubt.\n\n" +
                        "No other text." + quantityNote),
                new AiMessage("user", transcript.toString())
        );
    }

    private void maybeSummarize(Player player, NpcData npc) {
        if (!memorySummaryEnabled || !npc.getConfig().isMemoryEnabledFor(true)) return;
        Map<String, ConversationSession> playerSessions = sessions.get(player.getUniqueId());
        if (playerSessions == null) return;
        ConversationSession session = playerSessions.get(npc.getId());
        if (session == null || session.getExchangeCount() == 0) return;

        List<AiMessage> summaryMessages = buildSummaryMessages(session.getHistory(), npc.getDisplayName());
        backend.complete(summaryMessages)
                .thenAccept(summary -> memoryManager.save(npc.getId(), player.getUniqueId(), summary.trim()))
                .exceptionally(err -> {
                    plugin.getLogger().warning("Memory summary error for NPC '" + npc.getId() + "': " + err.getMessage());
                    return null;
                });
    }

    private List<AiMessage> buildSummaryMessages(List<AiMessage> history, String npcName) {
        StringBuilder transcript = new StringBuilder("Conversation:\n");
        for (AiMessage msg : history) {
            transcript.append("[").append("user".equals(msg.role()) ? "Player" : npcName)
                    .append("]: ").append(msg.content()).append("\n");
        }
        return List.of(
                new AiMessage("system",
                        "You are a memory assistant for an NPC named " + npcName + ". " +
                        "Summarize the following conversation in 1-2 sentences from " + npcName + "'s perspective. " +
                        "Focus on: what was discussed, any agreements or refusals, and notable things the player revealed. " +
                        "Write in past tense as a personal memory. Be concise."),
                new AiMessage("user", transcript.toString())
        );
    }

    private boolean isBlocked(String text) {
        String lower = text.toLowerCase();
        return moderation.blockedPhrases().stream().anyMatch(phrase -> lower.contains(phrase.toLowerCase()));
    }

    private static boolean responseContainsOffer(String text) {
        if (!text.matches(".*\\d+.*")) return false;
        String lower = text.toLowerCase();
        if (lower.contains("no deal") || lower.contains("not a deal") || lower.contains("won't deal") ||
            lower.contains("refuse") || lower.contains("out of the question") ||
            lower.contains("cannot accept") || lower.contains("can't accept") ||
            lower.contains("won't accept") || lower.contains("not willing") ||
            lower.contains("last time") || lower.contains("previous offer") ||
            lower.contains("that price") || lower.contains("that offer") ||
            lower.contains("not going to") || lower.contains("cannot go") || lower.contains("can't go")) {
            return false;
        }
        return lower.contains("how about") || lower.contains("what about") ||
               lower.contains("i'll take") || lower.contains("i can offer") ||
               lower.contains("i offer") || lower.contains("my offer") ||
               lower.contains("my price") || lower.contains("final offer") ||
               lower.contains("i'll sell") || lower.contains("i'll let") ||
               lower.contains("deal for") || lower.contains("deal at") ||
               lower.contains("price of") || lower.contains("priced at") ||
               lower.contains("sell for") || lower.contains("sell it for") ||
               lower.contains("for ") && (lower.contains(" gold") || lower.contains(" coin") || lower.contains(" silver")) ||
               lower.contains("we have a deal") || lower.contains("it's a deal") ||
               lower.contains("agreed") || lower.contains("done deal");
    }

    private void executeCommands(List<String> commands, Player player, String outcome) {
        executeCommands(commands, player, outcome, 1);
    }

    private void executeCommands(List<String> commands, Player player, String outcome, int quantity) {
        for (int i = 0; i < quantity; i++) {
            for (String cmd : commands) {
                String processed = cmd
                        .replace("%player%", player.getName())
                        .replace("%outcome%", outcome)
                        .replace("%quantity%", String.valueOf(quantity));
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), processed);
            }
        }
    }
}

package com.l299l.npcspeak.command;

import com.l299l.npcspeak.config.NpcConfig;
import com.l299l.npcspeak.config.NpcTaskConfig;
import com.l299l.npcspeak.npc.NpcData;
import com.l299l.npcspeak.npc.NpcManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class NpcCommand implements CommandExecutor {

    private final NpcManager npcManager;

    public NpcCommand(NpcManager npcManager) {
        this.npcManager = npcManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("npcspeak.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "spawn" -> handleSpawn(sender, label, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "edit" -> handleEdit(sender, label, args);
            case "task" -> handleTask(sender, label, args);
            case "example" -> handleExample(sender, label, args);
            case "reload" -> handleReload(sender);
            default -> { sendUsage(sender, label); yield true; }
        };
    }

    private boolean handleSpawn(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " spawn <id> [display name]", NamedTextColor.YELLOW));
            return true;
        }

        String id = args[1].toLowerCase();
        if (npcManager.exists(id)) {
            player.sendMessage(Component.text("An NPC with id '" + id + "' already exists.", NamedTextColor.RED));
            return true;
        }

        String displayName = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : id;

        NpcData data = new NpcData(id, displayName, null, player.getLocation(), new NpcConfig());
        try {
            npcManager.add(data);
            player.sendMessage(Component.text("Spawned NPC '", NamedTextColor.GREEN)
                    .append(Component.text(displayName, NamedTextColor.GOLD))
                    .append(Component.text("' (id: " + id + ") at your location.", NamedTextColor.GREEN)));
        } catch (IOException e) {
            player.sendMessage(Component.text("Failed to save NPC config: " + e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /npcspeak remove <id>", NamedTextColor.YELLOW));
            return true;
        }
        String id = args[1].toLowerCase();
        if (npcManager.getById(id) == null) {
            sender.sendMessage(Component.text("No NPC with id '" + id + "' found.", NamedTextColor.RED));
            return true;
        }
        npcManager.remove(id);
        sender.sendMessage(Component.text("Removed NPC '" + id + "'.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (npcManager.count() == 0) {
            sender.sendMessage(Component.text("No NPCs are currently loaded.", NamedTextColor.YELLOW));
            return true;
        }
        sender.sendMessage(Component.text("Loaded NPCs (" + npcManager.count() + "):", NamedTextColor.GOLD));
        for (NpcData npc : npcManager.getAll()) {
            sender.sendMessage(Component.text("  " + npc.getId() + " — " + npc.getDisplayName(), NamedTextColor.WHITE));
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /npcspeak info <id>", NamedTextColor.YELLOW));
            return true;
        }
        NpcData npc = npcManager.getById(args[1].toLowerCase());
        if (npc == null) {
            sender.sendMessage(Component.text("No NPC with id '" + args[1] + "' found.", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("=== " + npc.getDisplayName() + " ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("ID: ", NamedTextColor.GRAY).append(Component.text(npc.getId(), NamedTextColor.WHITE)));

        if (npc.getSpawnLocation() != null) {
            sender.sendMessage(Component.text("Location: ", NamedTextColor.GRAY).append(
                    Component.text(String.format("%s (%.1f, %.1f, %.1f)",
                            npc.getSpawnLocation().getWorld().getName(),
                            npc.getSpawnLocation().getX(),
                            npc.getSpawnLocation().getY(),
                            npc.getSpawnLocation().getZ()), NamedTextColor.WHITE)));
        }

        sender.sendMessage(Component.text("Entity UUID: ", NamedTextColor.GRAY).append(
                Component.text(npc.getEntityUUID() != null ? npc.getEntityUUID().toString() : "none", NamedTextColor.WHITE)));

        NpcConfig cfg = npc.getConfig();
        if (cfg.getTopic() != null) {
            sender.sendMessage(Component.text("Topic: ", NamedTextColor.GRAY).append(Component.text(cfg.getTopic(), NamedTextColor.WHITE)));
        }
        if (cfg.getAvoid() != null) {
            sender.sendMessage(Component.text("Avoid: ", NamedTextColor.GRAY).append(Component.text(cfg.getAvoid(), NamedTextColor.WHITE)));
        }
        return true;
    }

    private static final List<String> EDIT_FIELDS = List.of("name", "greeting", "prompt", "topic", "avoid");

    private boolean handleEdit(CommandSender sender, String label, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /" + label + " edit <id> <field> <value>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Fields: " + String.join(", ", EDIT_FIELDS), NamedTextColor.YELLOW));
            return true;
        }

        String id = args[1].toLowerCase();
        NpcData npc = npcManager.getById(id);
        if (npc == null) {
            sender.sendMessage(Component.text("No NPC with id '" + id + "' found.", NamedTextColor.RED));
            return true;
        }

        String field = args[2].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        switch (field) {
            case "name" -> {
                try {
                    npcManager.renameNpc(id, value);
                    sender.sendMessage(Component.text("Renamed NPC '" + id + "' to '", NamedTextColor.GREEN)
                            .append(Component.text(value, NamedTextColor.GOLD))
                            .append(Component.text("'.", NamedTextColor.GREEN)));
                } catch (IOException e) {
                    sender.sendMessage(Component.text("Failed to save: " + e.getMessage(), NamedTextColor.RED));
                }
            }
            case "greeting" -> {
                npc.getConfig().setGreeting(value);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated greeting for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "prompt" -> {
                npc.getConfig().setSystemPrompt(value);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated system prompt for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "topic" -> {
                npc.getConfig().setTopic(value);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated topic for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "avoid" -> {
                npc.getConfig().setAvoid(value);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated avoid for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            default -> sender.sendMessage(Component.text(
                    "Unknown field '" + field + "'. Fields: " + String.join(", ", EDIT_FIELDS),
                    NamedTextColor.RED));
        }
        return true;
    }

    private static final List<String> TASK_SUBCOMMANDS = List.of("info", "clear", "type", "goal", "check", "maxexchanges", "difficulty", "onsuccess", "onfailure");
    private static final List<String> TASK_TYPES = List.of("negotiate", "persuade", "interrogate", "quest", "freeform");
    private static final List<String> ON_ACTIONS = List.of("add", "clear");

    private boolean handleTask(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sendTaskUsage(sender, label);
            return true;
        }
        String id = args[1].toLowerCase();
        NpcData npc = npcManager.getById(id);
        if (npc == null) {
            sender.sendMessage(Component.text("No NPC with id '" + id + "' found.", NamedTextColor.RED));
            return true;
        }
        String sub = args[2].toLowerCase();
        switch (sub) {
            case "info" -> showTaskInfo(sender, npc);
            case "clear" -> {
                npc.getConfig().setTask(null);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Removed task from '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "type" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> type <type>", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("Types: " + String.join(", ", TASK_TYPES), NamedTextColor.YELLOW));
                    return true;
                }
                String type = args[3].toLowerCase();
                if (!TASK_TYPES.contains(type)) {
                    sender.sendMessage(Component.text("Unknown type. Valid: " + String.join(", ", TASK_TYPES), NamedTextColor.RED));
                    return true;
                }
                getOrCreateTask(npc).setType(type);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Set task type to '", NamedTextColor.GREEN)
                        .append(Component.text(type, NamedTextColor.GOLD))
                        .append(Component.text("' for '" + id + "'.", NamedTextColor.GREEN)));
            }
            case "goal" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> goal <text...>", NamedTextColor.YELLOW));
                    return true;
                }
                getOrCreateTask(npc).setGoal(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated task goal for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "check" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> check <text...>", NamedTextColor.YELLOW));
                    return true;
                }
                getOrCreateTask(npc).setOutcomeCheck(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Updated task check for '", NamedTextColor.GREEN)
                        .append(Component.text(id, NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            case "maxexchanges" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> maxexchanges <n|-1>", NamedTextColor.YELLOW));
                    return true;
                }
                try {
                    int n = Integer.parseInt(args[3]);
                    getOrCreateTask(npc).setMaxExchanges(n);
                    trySaveNpc(sender, npc);
                    sender.sendMessage(Component.text("Set max exchanges to " + n + " for '", NamedTextColor.GREEN)
                            .append(Component.text(id, NamedTextColor.GOLD))
                            .append(Component.text("'.", NamedTextColor.GREEN)));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("'" + args[3] + "' is not a valid number.", NamedTextColor.RED));
                }
            }
            case "difficulty" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> difficulty <1-5>", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("1=very easy  2=easy  3=normal  4=hard  5=very hard", NamedTextColor.GRAY));
                    return true;
                }
                try {
                    int d = Integer.parseInt(args[3]);
                    if (d < 1 || d > 5) throw new NumberFormatException();
                    getOrCreateTask(npc).setDifficulty(d);
                    trySaveNpc(sender, npc);
                    sender.sendMessage(Component.text("Set difficulty to " + d + "/5 for '", NamedTextColor.GREEN)
                            .append(Component.text(id, NamedTextColor.GOLD))
                            .append(Component.text("'.", NamedTextColor.GREEN)));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Difficulty must be 1–5.", NamedTextColor.RED));
                }
            }
            case "onsuccess" -> handleOnList(sender, label, args, npc, true);
            case "onfailure" -> handleOnList(sender, label, args, npc, false);
            default -> sendTaskUsage(sender, label);
        }
        return true;
    }

    private void handleOnList(CommandSender sender, String label, String[] args, NpcData npc, boolean isSuccess) {
        String field = isSuccess ? "onsuccess" : "onfailure";
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /" + label + " task <id> " + field + " <add|clear> [cmd...]", NamedTextColor.YELLOW));
            return;
        }
        NpcTaskConfig task = getOrCreateTask(npc);
        List<String> list = isSuccess ? task.getOnSuccess() : task.getOnFailure();
        switch (args[3].toLowerCase()) {
            case "add" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text("Usage: /" + label + " task <id> " + field + " add <command...>", NamedTextColor.YELLOW));
                    return;
                }
                String cmd = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                list.add(cmd);
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Added to " + field + " for '", NamedTextColor.GREEN)
                        .append(Component.text(npc.getId(), NamedTextColor.GOLD))
                        .append(Component.text("': " + cmd, NamedTextColor.GREEN)));
            }
            case "clear" -> {
                list.clear();
                trySaveNpc(sender, npc);
                sender.sendMessage(Component.text("Cleared " + field + " for '", NamedTextColor.GREEN)
                        .append(Component.text(npc.getId(), NamedTextColor.GOLD))
                        .append(Component.text("'.", NamedTextColor.GREEN)));
            }
            default -> sender.sendMessage(Component.text("Unknown action. Use 'add' or 'clear'.", NamedTextColor.RED));
        }
    }

    private void showTaskInfo(CommandSender sender, NpcData npc) {
        NpcTaskConfig task = npc.getConfig().getTask();
        sender.sendMessage(Component.text("=== Task: " + npc.getDisplayName() + " ===", NamedTextColor.GOLD));
        if (task == null) {
            sender.sendMessage(Component.text("No task configured.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Type: ", NamedTextColor.GRAY).append(Component.text(task.getType(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Goal: ", NamedTextColor.GRAY).append(Component.text(task.getGoal() != null ? task.getGoal() : "(none)", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Check: ", NamedTextColor.GRAY).append(Component.text(task.getOutcomeCheck() != null ? task.getOutcomeCheck() : "(none)", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Difficulty: ", NamedTextColor.GRAY).append(Component.text(task.getDifficulty() + "/5", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Max exchanges: ", NamedTextColor.GRAY).append(Component.text(task.getMaxExchanges() > 0 ? String.valueOf(task.getMaxExchanges()) : "unlimited", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("On success (" + task.getOnSuccess().size() + "):", NamedTextColor.GRAY));
        for (String cmd : task.getOnSuccess()) sender.sendMessage(Component.text("  " + cmd, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("On failure (" + task.getOnFailure().size() + "):", NamedTextColor.GRAY));
        for (String cmd : task.getOnFailure()) sender.sendMessage(Component.text("  " + cmd, NamedTextColor.WHITE));
    }

    private NpcTaskConfig getOrCreateTask(NpcData npc) {
        NpcConfig config = npc.getConfig();
        if (config.getTask() == null) {
            config.setTask(new NpcTaskConfig());
        }
        return config.getTask();
    }

    private void sendTaskUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Task management:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /" + label + " task <id> info", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> clear", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> type <negotiate|persuade|interrogate|quest|freeform>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> goal <text...>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> check <text...>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> maxexchanges <n|-1>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> difficulty <1-5>  (1=easy 5=very hard)", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> onsuccess <add|clear> [cmd...]", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> onfailure <add|clear> [cmd...]", NamedTextColor.WHITE));
    }

    private void trySaveNpc(CommandSender sender, NpcData npc) {
        try {
            npcManager.saveNpc(npc);
        } catch (IOException e) {
            sender.sendMessage(Component.text("Saved in memory but failed to write file: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private boolean handleReload(CommandSender sender) {
        npcManager.reloadAll();
        sender.sendMessage(Component.text("Reloaded " + npcManager.count() + " NPC(s).", NamedTextColor.GREEN));
        return true;
    }

    private static final List<String> EXAMPLE_TYPES = List.of("negotiator", "persuader", "interrogator", "quest", "free");

    private boolean handleExample(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2 || !EXAMPLE_TYPES.contains(args[1].toLowerCase())) {
            sender.sendMessage(Component.text("Usage: /" + label + " example <type>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Types: " + String.join(", ", EXAMPLE_TYPES), NamedTextColor.YELLOW));
            return true;
        }
        String type = args[1].toLowerCase();
        String baseId = "ex_" + type;
        String id = baseId;
        int n = 2;
        while (npcManager.exists(id)) id = baseId + "_" + n++;

        NpcData data = buildExampleNpc(type, id, player.getLocation());
        try {
            npcManager.add(data);
            player.sendMessage(Component.text("Spawned example NPC '", NamedTextColor.GREEN)
                    .append(Component.text(data.getDisplayName(), NamedTextColor.GOLD))
                    .append(Component.text("' (id: " + id + ").", NamedTextColor.GREEN)));
        } catch (IOException e) {
            player.sendMessage(Component.text("Failed to save NPC: " + e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private NpcData buildExampleNpc(String type, String id, org.bukkit.Location location) {
        NpcConfig config = new NpcConfig();
        NpcTaskConfig task = null;
        String displayName;

        switch (type) {
            case "negotiator" -> {
                displayName = "Merchant Bob";
                config.setGreeting("Ah, a customer! I have a fine sword for sale — only 50 gold!");
                config.setSystemPrompt("You are a shrewd merchant. Stay in character at all times.");
                config.setTopic("trading, weapons, commerce");
                task = new NpcTaskConfig();
                task.setType("negotiate");
                task.setGoal("You are selling an iron sword. Opening price is 50 gold; your minimum is 30 gold (never reveal the minimum). Haggle back.");
                task.setOutcomeCheck("Did the player and NPC agree on a final price? Answer YES <price> or NO.");
                task.setMaxExchanges(8);
                task.setDifficulty(3);
            }
            case "persuader" -> {
                displayName = "Village Elder";
                config.setGreeting("I don't trust outsiders. State your business.");
                config.setSystemPrompt("You are a suspicious but ultimately reasonable village elder. Be skeptical, but yield if the argument is genuinely compelling.");
                config.setTopic("village safety, local history");
                task = new NpcTaskConfig();
                task.setType("persuade");
                task.setGoal("You distrust the player. They must convince you to allow them into the village. Hold firm unless their reasoning is persuasive.");
                task.setOutcomeCheck("Did the NPC agree to let the player into the village? Answer YES or NO.");
                task.setMaxExchanges(6);
                task.setDifficulty(4);
            }
            case "interrogator" -> {
                displayName = "Guard Captain";
                config.setGreeting("Halt! You match the description of a suspect. You will answer my questions.");
                config.setSystemPrompt("You are a stern guard captain conducting an interrogation. Ask pointed questions and probe for inconsistencies.");
                config.setTopic("crime, justice, town security");
                task = new NpcTaskConfig();
                task.setType("interrogate");
                task.setGoal("You are interrogating the player about a stolen artifact. Ask where they were last night and look for inconsistencies.");
                task.setOutcomeCheck("Did the player give a satisfying alibi or admit guilt? Answer YES <verdict> or NO.");
                task.setMaxExchanges(10);
                task.setDifficulty(3);
            }
            case "quest" -> {
                displayName = "Old Wizard";
                config.setGreeting("Ah, an adventurer! I have a task that requires your... unique talents.");
                config.setSystemPrompt("You are a mysterious wizard who needs help retrieving a lost tome. Be cryptic at first, then reveal the quest details.");
                config.setTopic("magic, ancient lore, quests");
                task = new NpcTaskConfig();
                task.setType("quest");
                task.setGoal("You need the player to agree to retrieve the Ancient Tome of Shadows from the old dungeon. Only consider it accepted once they clearly agree.");
                task.setOutcomeCheck("Did the player explicitly agree to accept the quest? Answer YES or NO.");
                task.setMaxExchanges(-1);
                task.setDifficulty(2);
            }
            default -> { // free
                displayName = "Friendly Villager";
                config.setGreeting("Hello there! Lovely day, isn't it?");
                config.setSystemPrompt("You are a friendly villager who loves to chat about daily life in the village.");
                config.setTopic("village life, weather, crops, local gossip");
            }
        }

        if (task != null) config.setTask(task);
        return new NpcData(id, displayName, null, location, config);
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("NPCSpeak commands:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /" + label + " spawn <id> [display name]", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " remove <id>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " list", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " info <id>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " edit <id> <name|greeting|prompt|topic|avoid> <value>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " task <id> <info|clear|type|goal|check|maxexchanges|onsuccess|onfailure>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " example <negotiator|persuader|interrogator|quest|free>", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  /" + label + " reload", NamedTextColor.WHITE));
    }
}

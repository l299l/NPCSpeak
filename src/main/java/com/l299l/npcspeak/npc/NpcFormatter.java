package com.l299l.npcspeak.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class NpcFormatter {

    private NpcFormatter() {}

    public static void sendResponse(Player player, String npcName, String response) {
        player.sendMessage(prefix(npcName).append(Component.text(response, NamedTextColor.WHITE)));
    }

    public static void sendTypingIndicator(Player player, String npcName) {
        player.sendMessage(prefix(npcName).append(Component.text("✏ ...", NamedTextColor.GRAY)));
    }

    public static void sendPendingNotice(Player player, String npcName) {
        player.sendMessage(prefix(npcName).append(Component.text("I'm still thinking...", NamedTextColor.GRAY)));
    }

    public static void sendCooldownNotice(Player player, String npcName, long secondsLeft) {
        player.sendMessage(prefix(npcName)
                .append(Component.text("Please wait " + secondsLeft + "s before speaking again.", NamedTextColor.YELLOW)));
    }

    public static void sendGreeting(Player player, String npcName, String greeting) {
        player.sendMessage(prefix(npcName).append(Component.text(greeting, NamedTextColor.WHITE)));
        player.sendMessage(Component.text("(Type in chat to speak — right-click again to leave)", NamedTextColor.DARK_GRAY));
    }

    public static void sendFarewell(Player player, String npcName) {
        player.sendMessage(prefix(npcName).append(Component.text("Farewell, traveler!", NamedTextColor.WHITE)));
    }

    public static void sendTimeout(Player player, String npcName) {
        player.sendMessage(prefix(npcName).append(Component.text("Come back when you're ready to talk.", NamedTextColor.GRAY)));
    }

    public static void sendTaskSuccess(Player player, String npcName, String outcome) {
        Component msg = Component.text("[ Task complete", NamedTextColor.GREEN);
        if (!outcome.isBlank()) msg = msg.append(Component.text(": " + outcome, NamedTextColor.WHITE));
        player.sendMessage(msg.append(Component.text(" ]", NamedTextColor.GREEN)));
    }

    public static void sendTaskFailed(Player player, String npcName) {
        player.sendMessage(Component.text("[ Task failed ]", NamedTextColor.RED));
    }

    public static void sendError(Player player, String npcName, String backendName) {
        player.sendMessage(prefix(npcName)
                .append(Component.text("I cannot speak right now. (" + backendName + " unreachable)", NamedTextColor.RED)));
    }

    private static Component prefix(String npcName) {
        return Component.text("[" + npcName + "] ", NamedTextColor.GOLD);
    }
}

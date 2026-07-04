package com.l299l.npcspeak.papi;

import com.l299l.npcspeak.conversation.ConversationManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class NpcSpeakExpansion extends PlaceholderExpansion {

    private final ConversationManager conversationManager;
    private final String version;

    public NpcSpeakExpansion(ConversationManager conversationManager, String version) {
        this.conversationManager = conversationManager;
        this.version = version;
    }

    @Override public String getIdentifier() { return "npcspeak"; }
    @Override public String getAuthor()     { return "l299l"; }
    @Override public String getVersion()    { return version; }
    @Override public boolean persist()      { return true; }
    @Override public boolean canRegister()  { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";

        if (params.equalsIgnoreCase("active_npc")) {
            return conversationManager.getActiveNpcName(player.getUniqueId());
        }

        if (params.toLowerCase().startsWith("exchange_count_")) {
            String npcId = params.substring("exchange_count_".length());
            return String.valueOf(conversationManager.getSessionExchangeCount(player.getUniqueId(), npcId));
        }

        if (params.toLowerCase().startsWith("task_status_")) {
            String npcId = params.substring("task_status_".length());
            return conversationManager.getTaskStatusString(player.getUniqueId(), npcId);
        }

        return null;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player instanceof Player online) {
            return onPlaceholderRequest(online, params);
        }
        return "";
    }
}

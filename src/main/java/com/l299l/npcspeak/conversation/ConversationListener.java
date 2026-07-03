package com.l299l.npcspeak.conversation;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConversationListener implements Listener {

    private final ConversationManager conversationManager;

    public ConversationListener(ConversationManager conversationManager) {
        this.conversationManager = conversationManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        if (!conversationManager.isInConversation(event.getPlayer().getUniqueId())) return;

        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (!text.isEmpty()) {
            conversationManager.handleChatInput(event.getPlayer(), text);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        conversationManager.clearPlayer(event.getPlayer().getUniqueId());
    }
}

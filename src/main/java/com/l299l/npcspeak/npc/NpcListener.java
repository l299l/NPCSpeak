package com.l299l.npcspeak.npc;

import com.l299l.npcspeak.conversation.ConversationManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public class NpcListener implements Listener {

    private final NpcManager npcManager;
    private final ConversationManager conversationManager;

    public NpcListener(NpcManager npcManager, ConversationManager conversationManager) {
        this.npcManager = npcManager;
        this.conversationManager = conversationManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity entity = event.getRightClicked();
        if (!(entity instanceof Villager)) return;

        NpcData npc = npcManager.getByEntityUUID(entity.getUniqueId());
        if (npc == null) return;

        event.setCancelled(true);
        conversationManager.startConversation(event.getPlayer(), npc);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager)) return;
        if (npcManager.getByEntityUUID(event.getEntity().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }
}

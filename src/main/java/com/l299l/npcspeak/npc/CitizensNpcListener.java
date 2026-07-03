package com.l299l.npcspeak.npc;

import com.l299l.npcspeak.conversation.ConversationManager;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class CitizensNpcListener implements Listener {

    private final NpcManager npcManager;
    private final ConversationManager conversationManager;

    public CitizensNpcListener(NpcManager npcManager, ConversationManager conversationManager) {
        this.npcManager = npcManager;
        this.conversationManager = conversationManager;
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!npc.hasTrait(NPCSpeakTrait.class)) return;

        NPCSpeakTrait trait = npc.getTraitNullable(NPCSpeakTrait.class);
        if (trait.getNpcId() == null) return;

        NpcData npcData = npcManager.getById(trait.getNpcId());
        if (npcData == null) return;

        if (!event.getClicker().hasPermission("npcspeak.interact")) return;

        event.setCancelled(true);
        conversationManager.startConversation(event.getClicker(), npcData);
    }
}

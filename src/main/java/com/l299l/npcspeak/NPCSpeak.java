package com.l299l.npcspeak;

import com.l299l.npcspeak.ai.AiBackend;
import com.l299l.npcspeak.ai.BackendFactory;
import com.l299l.npcspeak.command.NpcCommand;
import com.l299l.npcspeak.command.NpcCommandCompleter;
import com.l299l.npcspeak.config.PluginConfig;
import com.l299l.npcspeak.conversation.ConversationListener;
import com.l299l.npcspeak.conversation.ConversationManager;
import com.l299l.npcspeak.conversation.TaskManager;
import com.l299l.npcspeak.cooldown.CooldownManager;
import com.l299l.npcspeak.npc.BuiltinNpcProvider;
import com.l299l.npcspeak.npc.CitizensNpcListener;
import com.l299l.npcspeak.npc.CitizensNpcProvider;
import com.l299l.npcspeak.npc.NPCSpeakTrait;
import com.l299l.npcspeak.npc.NpcListener;
import com.l299l.npcspeak.npc.NpcManager;
import com.l299l.npcspeak.npc.NpcProvider;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.trait.TraitInfo;
import org.bukkit.plugin.java.JavaPlugin;

public final class NPCSpeak extends JavaPlugin {

    private PluginConfig pluginConfig;
    private NpcManager npcManager;
    private ConversationManager conversationManager;

    @Override
    public void onEnable() {
        pluginConfig = new PluginConfig(this);

        AiBackend backend;
        try {
            backend = BackendFactory.create(pluginConfig);
            getLogger().info("Using AI backend: " + backend.getName());
        } catch (IllegalArgumentException e) {
            getLogger().severe(e.getMessage() + " — disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        conversationManager = new ConversationManager(
                this, backend, new CooldownManager(), new TaskManager(),
                pluginConfig.getMaxConversationExchanges(),
                pluginConfig.getCooldownSeconds(),
                pluginConfig.getListenTimeoutSeconds()
        );

        NpcProvider provider = createProvider();
        npcManager = new NpcManager(this, provider);
        npcManager.loadAll();

        getServer().getPluginManager().registerEvents(new NpcListener(npcManager, conversationManager), this);
        getServer().getPluginManager().registerEvents(new ConversationListener(conversationManager), this);

        if (provider instanceof CitizensNpcProvider) {
            getServer().getPluginManager().registerEvents(
                    new CitizensNpcListener(npcManager, conversationManager), this);
        }

        NpcCommand npcCommand = new NpcCommand(npcManager);
        getCommand("npcspeak").setExecutor(npcCommand);
        getCommand("npcspeak").setTabCompleter(new NpcCommandCompleter(npcManager));

        getLogger().info("Enabled with " + npcManager.count() + " NPC(s) loaded (provider: " + provider.getName() + ").");
    }

    @Override
    public void onDisable() {
        if (npcManager != null) {
            npcManager.saveAll();
        }
    }

    private NpcProvider createProvider() {
        String configured = pluginConfig.getNpcProvider();
        if ("citizens".equalsIgnoreCase(configured)) {
            if (getServer().getPluginManager().isPluginEnabled("Citizens")) {
                CitizensAPI.getTraitFactory().registerTrait(
                        TraitInfo.create(NPCSpeakTrait.class).withName("npcspeak"));
                getLogger().info("Citizens2 detected — using Citizens NPC provider.");
                return new CitizensNpcProvider();
            }
            getLogger().warning("npc-provider is 'citizens' but Citizens2 is not installed — falling back to built-in.");
        }
        return new BuiltinNpcProvider(this);
    }

    public PluginConfig getPluginConfig() { return pluginConfig; }
    public NpcManager getNpcManager() { return npcManager; }
    public ConversationManager getConversationManager() { return conversationManager; }
}

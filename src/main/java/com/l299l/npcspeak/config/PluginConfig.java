package com.l299l.npcspeak.config;

import org.bukkit.plugin.java.JavaPlugin;

public class PluginConfig {

    private final JavaPlugin plugin;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    public String getBackend() {
        return plugin.getConfig().getString("backend", "ollama");
    }

    public String getOllamaUrl() {
        return plugin.getConfig().getString("ollama.url", "http://localhost:11434");
    }

    public String getOllamaModel() {
        return plugin.getConfig().getString("ollama.model", "llama3");
    }

    public int getOllamaTimeoutSeconds() {
        return plugin.getConfig().getInt("ollama.timeout-seconds", 60);
    }

    public int getMaxConversationExchanges() {
        return plugin.getConfig().getInt("conversation.max-exchanges", 10);
    }

    public int getCooldownSeconds() {
        return plugin.getConfig().getInt("conversation.cooldown-seconds", 3);
    }

    public int getListenTimeoutSeconds() {
        return plugin.getConfig().getInt("conversation.listen-timeout-seconds", 60);
    }

    public String getOpenAiUrl() {
        return plugin.getConfig().getString("openai.url", "https://api.openai.com");
    }

    public String getOpenAiApiKey() {
        return plugin.getConfig().getString("openai.api-key", "");
    }

    public String getOpenAiModel() {
        return plugin.getConfig().getString("openai.model", "gpt-4o-mini");
    }

    public String getNpcProvider() {
        return plugin.getConfig().getString("npc-provider", "builtin");
    }
}

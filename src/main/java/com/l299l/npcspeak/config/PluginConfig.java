package com.l299l.npcspeak.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class PluginConfig {

    private final JavaPlugin plugin;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        mergeMissingDefaults(plugin);
    }

    private static void mergeMissingDefaults(JavaPlugin plugin) {
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(
                        Objects.requireNonNull(plugin.getResource("config.yml")),
                        StandardCharsets.UTF_8
                )
        );
        FileConfiguration current = plugin.getConfig();
        boolean needsSave = false;
        for (String key : defaults.getKeys(true)) {
            if (!current.contains(key)) {
                current.set(key, defaults.get(key));
                needsSave = true;
            }
        }
        if (needsSave) {
            plugin.saveConfig();
        }
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

    public boolean isStreamingEnabled() {
        return plugin.getConfig().getBoolean("streaming", false);
    }

    public boolean isMemoryEnabled() {
        return plugin.getConfig().getBoolean("memory.enabled", true);
    }

    public int getMemoryMaxAgeDays() {
        return plugin.getConfig().getInt("memory.max-age-days", 30);
    }

    public ModerationConfig getModerationConfig() {
        boolean enabled = plugin.getConfig().getBoolean("moderation.enabled", false);
        List<String> phrases = plugin.getConfig().getStringList("moderation.blocked-phrases");
        String response = plugin.getConfig().getString("moderation.blocked-response", "I cannot discuss that.");
        String webhook = plugin.getConfig().getString("moderation.discord-webhook", "");
        return new ModerationConfig(enabled, phrases, response, webhook);
    }
}

package com.l299l.npcspeak.ai;

import com.l299l.npcspeak.config.PluginConfig;

public final class BackendFactory {

    private BackendFactory() {}

    public static AiBackend create(PluginConfig config) {
        String backend = config.getBackend().toLowerCase();
        return switch (backend) {
            case "ollama" -> new OllamaBackend(config);
            case "openai" -> new OpenAiBackend(config);
            default -> throw new IllegalArgumentException(
                    "Unknown backend '" + config.getBackend() + "'. Valid options: ollama, openai");
        };
    }
}

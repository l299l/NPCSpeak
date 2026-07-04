package com.l299l.npcspeak.config;

import java.util.List;

public record ModerationConfig(
        boolean enabled,
        List<String> blockedPhrases,
        String blockedResponse,
        String discordWebhook
) {
    public static final ModerationConfig DISABLED = new ModerationConfig(
            false, List.of(), "I cannot discuss that.", "");
}

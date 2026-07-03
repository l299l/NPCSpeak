package com.l299l.npcspeak.cooldown;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final Map<UUID, Map<String, Long>> lastMessage = new ConcurrentHashMap<>();

    public boolean isOnCooldown(UUID playerId, String npcId, long cooldownMs) {
        Map<String, Long> playerMap = lastMessage.get(playerId);
        if (playerMap == null) return false;
        Long last = playerMap.get(npcId);
        if (last == null) return false;
        return System.currentTimeMillis() - last < cooldownMs;
    }

    /** Returns remaining cooldown in whole seconds (always >= 1 when on cooldown). */
    public long remainingSeconds(UUID playerId, String npcId, long cooldownMs) {
        Map<String, Long> playerMap = lastMessage.get(playerId);
        if (playerMap == null) return 0;
        Long last = playerMap.get(npcId);
        if (last == null) return 0;
        long remaining = cooldownMs - (System.currentTimeMillis() - last);
        return remaining > 0 ? (remaining / 1000) + 1 : 0;
    }

    public void record(UUID playerId, String npcId) {
        lastMessage.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(npcId, System.currentTimeMillis());
    }

    public void clearPlayer(UUID playerId) {
        lastMessage.remove(playerId);
    }
}

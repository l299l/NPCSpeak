package com.l299l.npcspeak.conversation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TaskManager {

    // player UUID → (npc id → task state)
    private final Map<UUID, Map<String, TaskState>> stateMap = new ConcurrentHashMap<>();
    // player UUID → (npc id → exchanges completed against this task)
    private final Map<UUID, Map<String, Integer>> exchangeCount = new ConcurrentHashMap<>();

    public TaskState getState(UUID playerId, String npcId) {
        Map<String, TaskState> inner = stateMap.get(playerId);
        return inner != null ? inner.get(npcId) : null;
    }

    public void setState(UUID playerId, String npcId, TaskState state) {
        stateMap.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(npcId, state);
    }

    /** Increments and returns the new exchange count for this player+NPC task. */
    public int incrementExchanges(UUID playerId, String npcId) {
        return exchangeCount
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .merge(npcId, 1, Integer::sum);
    }

    public void clearPlayer(UUID playerId) {
        stateMap.remove(playerId);
        exchangeCount.remove(playerId);
    }
}

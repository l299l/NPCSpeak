package com.l299l.npcspeak.conversation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TaskManager {

    private final Map<UUID, Map<String, TaskState>> stateMap = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> exchangeCount = new ConcurrentHashMap<>();

    public TaskState getState(UUID playerId, String npcId) {
        Map<String, TaskState> inner = stateMap.get(playerId);
        return inner != null ? inner.get(npcId) : null;
    }

    public void setState(UUID playerId, String npcId, TaskState state) {
        stateMap.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(npcId, state);
    }

    public int incrementExchanges(UUID playerId, String npcId) {
        return exchangeCount
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .merge(npcId, 1, Integer::sum);
    }

    public void clearNpc(UUID playerId, String npcId) {
        Map<String, TaskState> states = stateMap.get(playerId);
        if (states != null) states.remove(npcId);
        Map<String, Integer> counts = exchangeCount.get(playerId);
        if (counts != null) counts.remove(npcId);
    }

    public void clearPlayer(UUID playerId) {
        stateMap.remove(playerId);
        exchangeCount.remove(playerId);
    }
}

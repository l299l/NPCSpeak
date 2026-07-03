package com.l299l.npcspeak.npc;

public interface NpcProvider {
    void restoreOrSpawn(NpcData data);
    void despawn(NpcData data);
    void rename(NpcData data, String newName);
    String getName();
}

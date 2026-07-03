package com.l299l.npcspeak.npc;

import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.util.DataKey;

@TraitName("npcspeak")
public class NPCSpeakTrait extends Trait {

    private String npcId;

    public NPCSpeakTrait() {
        super("npcspeak");
    }

    @Override
    public void save(DataKey key) {
        if (npcId != null) key.setString("npc-id", npcId);
    }

    @Override
    public void load(DataKey key) {
        npcId = key.getString("npc-id");
    }

    public String getNpcId() { return npcId; }
    public void setNpcId(String npcId) { this.npcId = npcId; }
}

package com.l299l.npcspeak.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class BuiltinNpcProvider implements NpcProvider {

    private final JavaPlugin plugin;

    public BuiltinNpcProvider(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void restoreOrSpawn(NpcData data) {
        UUID uuid = data.getEntityUUID();
        if (uuid != null) {
            data.getSpawnLocation().getWorld().getChunkAt(data.getSpawnLocation());
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof Villager villager && !villager.isDead()) {
                configureVillager(villager, data.getDisplayName());
                return;
            }
        }
        spawnFreshVillager(data);
    }

    @Override
    public void despawn(NpcData data) {
        if (data.getEntityUUID() != null) {
            Entity entity = Bukkit.getEntity(data.getEntityUUID());
            if (entity != null) entity.remove();
        }
        data.setEntityUUID(null);
    }

    @Override
    public void rename(NpcData data, String newName) {
        if (data.getEntityUUID() == null) return;
        org.bukkit.entity.Entity entity = Bukkit.getEntity(data.getEntityUUID());
        if (entity != null) {
            entity.customName(Component.text(newName, NamedTextColor.GOLD));
        }
    }

    @Override
    public String getName() {
        return "builtin";
    }

    private void spawnFreshVillager(NpcData data) {
        Villager villager = data.getSpawnLocation().getWorld().spawn(
                data.getSpawnLocation(), Villager.class,
                v -> configureVillager(v, data.getDisplayName())
        );
        data.setEntityUUID(villager.getUniqueId());
    }

    private void configureVillager(Villager villager, String displayName) {
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.customName(Component.text(displayName, NamedTextColor.GOLD));
        villager.setCustomNameVisible(true);
        villager.setSilent(false);
    }
}

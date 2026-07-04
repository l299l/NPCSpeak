package com.l299l.npcspeak.vault;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {

    private final Economy economy;

    private VaultHook(Economy economy) {
        this.economy = economy;
    }

    public static VaultHook init(Plugin plugin) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Vault")) return null;
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("Vault found but no economy provider registered.");
            return null;
        }
        plugin.getLogger().info("Vault economy hooked: " + rsp.getProvider().getName());
        return new VaultHook(rsp.getProvider());
    }

    public boolean has(Player player, double amount) {
        return economy.has(player, amount);
    }
}

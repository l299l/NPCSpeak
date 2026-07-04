package com.l299l.npcspeak.papi;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PapiExpander {

    private static boolean available = false;

    private PapiExpander() {}

    public static void init(JavaPlugin plugin) {
        available = plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (available) {
            plugin.getLogger().info("PlaceholderAPI detected — placeholder expansion enabled.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static String expand(Player player, String text) {
        if (!available || text == null || player == null) return text;
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}

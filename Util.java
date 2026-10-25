package com.boxpvp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class Util {

    private Util() {
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public static void saveLoc(ConfigurationSection sec, String path, Location l) {
        sec.set(path + ".world", l.getWorld().getName());
        sec.set(path + ".x", l.getX());
        sec.set(path + ".y", l.getY());
        sec.set(path + ".z", l.getZ());
        sec.set(path + ".yaw", (double) l.getYaw());
        sec.set(path + ".pitch", (double) l.getPitch());
    }

    public static Location loadLoc(ConfigurationSection sec, String path) {
        ConfigurationSection s = sec.getConfigurationSection(path);
        if (s == null) {
            return null;
        }
        World w = Bukkit.getWorld(s.getString("world", ""));
        if (w == null) {
            return null;
        }
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    /** Text above the hotbar. Silently does nothing if the server can't show it. */
    public static void actionBar(Player p, String msg) {
        try {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(color(msg)));
        } catch (Throwable ignored) {
            // no action bar support
        }
    }

    public static String fmt(long n) {
        return String.format("%,d", n);
    }

    public static double kdr(int kills, int deaths) {
        return deaths == 0 ? (double) kills : (double) kills / (double) deaths;
    }

    public static String kdrStr(int kills, int deaths) {
        return String.format("%.2f", kdr(kills, deaths));
    }

    public static Integer parseInt(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static Double parseDouble(String s) {
        try {
            return Double.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}

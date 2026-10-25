package com.boxpvp;

import java.util.List;

/**
 * Reads the custom-enchants section of the config. Uses the single-argument getters on purpose, so that a server whose
 * config.yml predates this feature still gets the built-in defaults from the plugin jar.
 */
public class EnchantSettings {

    private final BoxPvP plugin;

    public EnchantSettings(BoxPvP plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("custom-enchants.enabled");
    }

    public int baseSlots() {
        return Math.max(0, plugin.getConfig().getInt("custom-enchants.base-slots"));
    }

    public int maxSlots() {
        int max = plugin.getConfig().getInt("custom-enchants.max-slots");
        return Math.max(baseSlots(), Math.min(9, max));
    }

    /** Coins to unlock the next slot when a tool currently has currentSlots. */
    public long slotPrice(int currentSlots) {
        List<Long> list = plugin.getConfig().getLongList("custom-enchants.slot-prices");
        if (list.isEmpty()) {
            return 1000L;
        }
        int idx = Math.max(0, Math.min(list.size() - 1, currentSlots - baseSlots()));
        return list.get(idx);
    }

    public long pullPrice(String which) {
        return plugin.getConfig().getLong("custom-enchants.pull-prices." + which);
    }

    public int max(String id) {
        int m = plugin.getConfig().getInt("custom-enchants.enchants." + id + ".max");
        return m <= 0 ? 1 : m;
    }

    /** Coins to go from currentLevel to currentLevel + 1. */
    public long priceFor(String id, int currentLevel) {
        List<Long> list = plugin.getConfig().getLongList("custom-enchants.enchants." + id + ".prices");
        if (list.isEmpty()) {
            return 1000L;
        }
        int idx = Math.max(0, Math.min(list.size() - 1, currentLevel - 1));
        return list.get(idx);
    }

    /** A tuning number for one enchant, e.g. d("explosion", "chance-per-level"). */
    public double d(String id, String key) {
        return plugin.getConfig().getDouble("custom-enchants.enchants." + id + "." + key);
    }
}

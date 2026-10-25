package com.boxpvp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Each player's collection of pulled custom enchants. They are kept on the player's account (not as items), so they
 * can't be dropped, stolen or duplicated; applying one to a tool at the Upgrader uses up one copy.
 */
public class EnchantStore {

    private final BoxPvP plugin;
    private final File file;
    private final Map<UUID, Map<String, Integer>> tokens = new HashMap<UUID, Map<String, Integer>>();

    public EnchantStore(BoxPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "enchants.yml");
    }

    public void load() {
        tokens.clear();
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ps = c.getConfigurationSection("players");
        if (ps == null) {
            return;
        }
        for (String key : ps.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection s = ps.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                Map<String, Integer> m = new HashMap<String, Integer>();
                for (String eid : s.getKeys(false)) {
                    if (CustomEnchant.byId(eid) != null && s.getInt(eid) > 0) {
                        m.put(eid, s.getInt(eid));
                    }
                }
                if (!m.isEmpty()) {
                    tokens.put(id, m);
                }
            } catch (IllegalArgumentException ignored) {
                // bad uuid key
            }
        }
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Integer>> en : tokens.entrySet()) {
            for (Map.Entry<String, Integer> t : en.getValue().entrySet()) {
                if (t.getValue() > 0) {
                    c.set("players." + en.getKey() + "." + t.getKey(), t.getValue());
                }
            }
        }
        try {
            c.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save enchants.yml: " + e.getMessage());
        }
    }

    public int count(UUID id, String enchantId) {
        Map<String, Integer> m = tokens.get(id);
        if (m == null) {
            return 0;
        }
        Integer n = m.get(enchantId);
        return n == null ? 0 : n;
    }

    public void add(UUID id, String enchantId, int amount) {
        Map<String, Integer> m = tokens.get(id);
        if (m == null) {
            m = new HashMap<String, Integer>();
            tokens.put(id, m);
        }
        m.put(enchantId, count(id, enchantId) + amount);
        save();
    }

    /** Uses up one copy. Returns false if the player has none. */
    public boolean take(UUID id, String enchantId) {
        int n = count(id, enchantId);
        if (n <= 0) {
            return false;
        }
        tokens.get(id).put(enchantId, n - 1);
        save();
        return true;
    }
}

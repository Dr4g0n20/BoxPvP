package com.boxpvp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cosmetic labels (ranks or clans). Only admins can create them and give/take them; they grant no permissions. */
public class RankManager {

    public static class Rank {
        public final String name;
        public volatile String prefix;

        Rank(String name, String prefix) {
            this.name = name;
            this.prefix = prefix;
        }
    }

    private final BoxPvP plugin;
    private final File file;
    // Concurrent because chat events are handled off the main thread.
    private final Map<String, Rank> ranks = new ConcurrentHashMap<String, Rank>();
    private final Map<UUID, String> assigned = new ConcurrentHashMap<UUID, String>();

    /** Used for both ranks (ranks.yml) and clans (clans.yml) - they work identically. */
    public RankManager(BoxPvP plugin, String fileName) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), fileName);
    }

    // ------------------------------------------------------------ persistence

    public void load() {
        ranks.clear();
        assigned.clear();
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection rs = c.getConfigurationSection("ranks");
        if (rs != null) {
            for (String key : rs.getKeys(false)) {
                String prefix = rs.getString(key + ".prefix");
                String name = rs.getString(key + ".name", key);
                if (prefix != null) {
                    ranks.put(key.toLowerCase(), new Rank(name, prefix));
                }
            }
        }
        ConfigurationSection ps = c.getConfigurationSection("players");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                String rank = ps.getString(key);
                try {
                    if (rank != null && ranks.containsKey(rank.toLowerCase())) {
                        assigned.put(UUID.fromString(key), rank.toLowerCase());
                    }
                } catch (IllegalArgumentException ignored) {
                    // bad uuid key
                }
            }
        }
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<String, Rank> en : ranks.entrySet()) {
            c.set("ranks." + en.getKey() + ".name", en.getValue().name);
            c.set("ranks." + en.getKey() + ".prefix", en.getValue().prefix);
        }
        for (Map.Entry<UUID, String> en : assigned.entrySet()) {
            c.set("players." + en.getKey().toString(), en.getValue());
        }
        try {
            Util.saveSafely(c, file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save ranks.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ ranks

    public Rank get(String name) {
        return ranks.get(name.toLowerCase());
    }

    public Collection<Rank> all() {
        return ranks.values();
    }

    public List<String> names() {
        List<String> out = new ArrayList<String>();
        for (Rank r : ranks.values()) {
            out.add(r.name);
        }
        return out;
    }

    public Rank create(String name, String prefix) {
        Rank r = new Rank(name, prefix);
        ranks.put(name.toLowerCase(), r);
        save();
        return r;
    }

    public void setPrefix(Rank r, String prefix) {
        r.prefix = prefix;
        save();
    }

    public boolean delete(String name) {
        String key = name.toLowerCase();
        if (ranks.remove(key) == null) {
            return false;
        }
        for (Map.Entry<UUID, String> en : assigned.entrySet()) {
            if (en.getValue().equals(key)) {
                assigned.remove(en.getKey());
            }
        }
        save();
        return true;
    }

    public int holders(Rank r) {
        int n = 0;
        for (String v : assigned.values()) {
            if (v.equals(r.name.toLowerCase())) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------ assignment

    public void give(UUID id, Rank r) {
        assigned.put(id, r.name.toLowerCase());
        save();
    }

    public boolean take(UUID id) {
        boolean had = assigned.remove(id) != null;
        if (had) {
            save();
        }
        return had;
    }

    public Rank rankOf(UUID id) {
        String key = assigned.get(id);
        return key == null ? null : ranks.get(key);
    }

    /** The coloured prefix for this player, or null if they have no rank. */
    public String prefixOf(UUID id) {
        Rank r = rankOf(id);
        return r == null ? null : Util.color(r.prefix);
    }
}

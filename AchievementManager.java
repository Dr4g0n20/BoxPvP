package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Achievement titles: cosmetic ranks earned automatically from stats (ores mined, kills, assists, best streak,
 * items sold). Each track has tiers; hit a tier's amount and you earn its title. Players choose which earned
 * title is shown with /title. A title is shown where a rank would be, unless an admin gave the player a rank.
 */
public class AchievementManager {

    public static class Tier {
        public final long amount;
        public final String name;
        public final String tag;

        Tier(long amount, String name, String tag) {
            this.amount = amount;
            this.name = name;
            this.tag = tag;
        }
    }

    public static class Track {
        public final String id;
        public final String stat;
        public final List<Tier> tiers = new ArrayList<Tier>();

        Track(String id, String stat) {
            this.id = id;
            this.stat = stat;
        }
    }

    private static final List<String> STATS = Arrays.asList("ores", "kills", "assists", "streak", "sold");

    private final BoxPvP plugin;
    private final File file;
    private final List<Track> tracks = new CopyOnWriteArrayList<Track>();
    // Read from the async chat thread, so these only hold plain values and are concurrent.
    private final Map<UUID, String> selected = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, Map<String, Integer>> earnedTiers = new ConcurrentHashMap<UUID, Map<String, Integer>>();

    public AchievementManager(BoxPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "titles.yml");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("achievements.enabled", true);
    }

    // ------------------------------------------------------------ loading / saving

    public void load() {
        tracks.clear();
        selected.clear();
        earnedTiers.clear();

        ConfigurationSection ts = plugin.sectionOrDefault("achievements.tracks");
        if (ts != null) {
            for (String id : ts.getKeys(false)) {
                String stat = ts.getString(id + ".stat", "");
                if (!STATS.contains(stat)) {
                    plugin.getLogger().warning("achievements." + id + ": unknown stat '" + stat + "' (use " + STATS + ")");
                    continue;
                }
                Track t = new Track(id.toLowerCase(), stat);
                for (Map<?, ?> m : ts.getMapList(id + ".tiers")) {
                    Object amount = m.get("amount");
                    Object name = m.get("name");
                    if (!(amount instanceof Number) || name == null) {
                        continue;
                    }
                    Object tag = m.get("tag");
                    t.tiers.add(new Tier(((Number) amount).longValue(), String.valueOf(name),
                            tag == null ? String.valueOf(name) : String.valueOf(tag)));
                }
                Collections.sort(t.tiers, new Comparator<Tier>() {
                    @Override
                    public int compare(Tier a, Tier b) {
                        return Long.compare(a.amount, b.amount);
                    }
                });
                if (!t.tiers.isEmpty()) {
                    tracks.add(t);
                }
            }
        }

        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ps = c.getConfigurationSection("players");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String sel = ps.getString(key + ".selected");
                    if (sel != null) {
                        selected.put(id, sel);
                    }
                    ConfigurationSection tiers = ps.getConfigurationSection(key + ".tiers");
                    if (tiers != null) {
                        for (String trackId : tiers.getKeys(false)) {
                            setTier(id, trackId, tiers.getInt(trackId));
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // bad uuid key
                }
            }
        }
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<UUID, String> en : selected.entrySet()) {
            c.set("players." + en.getKey() + ".selected", en.getValue());
        }
        for (Map.Entry<UUID, Map<String, Integer>> en : earnedTiers.entrySet()) {
            for (Map.Entry<String, Integer> t : en.getValue().entrySet()) {
                c.set("players." + en.getKey() + ".tiers." + t.getKey(), t.getValue());
            }
        }
        try {
            c.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save titles.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ helpers

    private Track track(String id) {
        for (Track t : tracks) {
            if (t.id.equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    private static long valueOf(PlayerStats ps, String stat) {
        switch (stat) {
            case "ores":
                return ps.ores;
            case "kills":
                return ps.kills;
            case "assists":
                return ps.assists;
            case "streak":
                return ps.bestStreak;
            case "sold":
                return ps.sold;
            default:
                return 0;
        }
    }

    private static String statLabel(String stat) {
        switch (stat) {
            case "ores":
                return "ores mined";
            case "streak":
                return "best streak";
            case "sold":
                return "items sold";
            default:
                return stat;
        }
    }

    /** How many tiers of this track a stat value has reached. */
    private int earned(Track t, long value) {
        int n = 0;
        for (Tier tier : t.tiers) {
            if (value >= tier.amount) {
                n++;
            }
        }
        return n;
    }

    private int tierOf(UUID id, String trackId) {
        Map<String, Integer> m = earnedTiers.get(id);
        if (m == null) {
            return 0;
        }
        Integer v = m.get(trackId);
        return v == null ? 0 : v;
    }

    private void setTier(UUID id, String trackId, int tier) {
        Map<String, Integer> m = earnedTiers.get(id);
        if (m == null) {
            m = new ConcurrentHashMap<String, Integer>();
            earnedTiers.put(id, m);
        }
        m.put(trackId.toLowerCase(), tier);
    }

    private Tier selectedTier(UUID id) {
        if (!enabled()) {
            return null;
        }
        String sel = selected.get(id);
        if (sel == null || sel.equals("none")) {
            return null;
        }
        Track t = track(sel);
        if (t == null) {
            return null;
        }
        int n = tierOf(id, t.id);
        return n <= 0 ? null : t.tiers.get(Math.min(n, t.tiers.size()) - 1);
    }

    /** Full title text for chat (coloured), or null if the player shows no title. */
    public String chatText(UUID id) {
        Tier t = selectedTier(id);
        return t == null ? null : Util.color(t.name);
    }

    /** Short title text for name tags / tab list (coloured), or null. */
    public String tagText(UUID id) {
        Tier t = selectedTier(id);
        return t == null ? null : Util.color(t.tag);
    }

    // ------------------------------------------------------------ progress

    /** Silently brings a player's earned tiers up to date (join, stat reset). Never announces anything. */
    public void sync(Player p) {
        if (!enabled()) {
            return;
        }
        UUID id = p.getUniqueId();
        PlayerStats ps = plugin.stats().get(p);
        String best = null;
        int bestTier = 0;
        for (Track t : tracks) {
            int n = earned(t, valueOf(ps, t.stat));
            setTier(id, t.id, n);
            if (n > bestTier) {
                bestTier = n;
                best = t.id;
            }
        }
        if (!selected.containsKey(id) && best != null) {
            selected.put(id, best);
        }
    }

    /** Call after a stat of this kind went up. Announces and selects any newly earned title. */
    public void check(Player p, String stat) {
        if (!enabled()) {
            return;
        }
        UUID id = p.getUniqueId();
        PlayerStats ps = plugin.stats().get(p);
        int broadcastMin = plugin.getConfig().getInt("achievements.broadcast-min-tier", 3);
        boolean changed = false;
        for (Track t : tracks) {
            if (!t.stat.equals(stat)) {
                continue;
            }
            int now = earned(t, valueOf(ps, t.stat));
            if (now <= tierOf(id, t.id)) {
                continue;
            }
            setTier(id, t.id, now);
            Tier tier = t.tiers.get(now - 1);
            selected.put(id, t.id);
            changed = true;
            p.sendMessage(Util.color("&6&lACHIEVEMENT! &7You earned the title " + tier.name + " &8(/title to change)"));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            if (broadcastMin > 0 && now >= broadcastMin) {
                Bukkit.broadcastMessage(Util.color("&6" + p.getName() + " &7earned the title " + tier.name + "&7!"));
            }
        }
        if (changed) {
            save();
            plugin.nameTags().refreshTarget(p);
            plugin.board().update(p);
        }
    }

    // ------------------------------------------------------------ /title

    /** Shows an earned title (by track name). Returns false if the track doesn't exist or isn't earned yet. */
    public boolean select(Player p, String trackId) {
        Track t = track(trackId);
        if (t == null || tierOf(p.getUniqueId(), t.id) <= 0) {
            return false;
        }
        selected.put(p.getUniqueId(), t.id);
        save();
        plugin.nameTags().refreshTarget(p);
        plugin.board().update(p);
        return true;
    }

    public void hide(Player p) {
        selected.put(p.getUniqueId(), "none");
        save();
        plugin.nameTags().refreshTarget(p);
        plugin.board().update(p);
    }

    /** Names of the tracks this player has earned at least one tier in (for tab completion). */
    public List<String> earnedTracks(Player p) {
        List<String> out = new ArrayList<String>();
        for (Track t : tracks) {
            if (tierOf(p.getUniqueId(), t.id) > 0) {
                out.add(t.id);
            }
        }
        return out;
    }

    public void sendList(Player p) {
        UUID id = p.getUniqueId();
        PlayerStats ps = plugin.stats().get(p);
        String sel = selected.get(id);
        p.sendMessage(Util.color("&8&m      &r &6Your titles &8&m      "));
        if (tracks.isEmpty() || !enabled()) {
            p.sendMessage(Util.color("&7Achievement titles are turned off."));
            return;
        }
        for (Track t : tracks) {
            int n = tierOf(id, t.id);
            long value = valueOf(ps, t.stat);
            String line = "&7- &f" + t.id + "&7: " + (n > 0 ? t.tiers.get(n - 1).name : "&8none yet");
            if (n < t.tiers.size()) {
                line += " &7(" + Util.fmt(value) + "/" + Util.fmt(t.tiers.get(n).amount) + " " + statLabel(t.stat) + " for next)";
            } else {
                line += " &7(maxed)";
            }
            if (n > 0 && t.id.equals(sel)) {
                line += " &e<- shown";
            }
            p.sendMessage(Util.color(line));
        }
        p.sendMessage(Util.color("&7Show one with &f/title <name>&7, or hide yours with &f/title off&7."));
    }
}

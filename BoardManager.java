package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player sidebar scoreboard. */
public class BoardManager {

    private final BoxPvP plugin;
    private final Map<UUID, List<String>> lines = new HashMap<UUID, List<String>>();

    public BoardManager(BoxPvP plugin) {
        this.plugin = plugin;
    }

    public void update(Player p) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true) || !p.isOnline()) {
            return;
        }
        PlayerStats s = plugin.stats().get(p);
        Scoreboard sb = scoreboard(p);
        Objective obj = sb.getObjective("boxpvp");
        if (obj == null) {
            obj = sb.registerNewObjective("boxpvp", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        obj.setDisplayName(Util.color(plugin.getConfig().getString("scoreboard.title", "&6&lBOX PVP")));

        List<String> cfg = plugin.getConfig().getStringList("scoreboard.lines");
        List<String> fresh = new ArrayList<String>();
        for (int i = 0; i < cfg.size() && i < 15; i++) {
            String line = cfg.get(i)
                    .replace("%kills%", String.valueOf(s.kills))
                    .replace("%deaths%", String.valueOf(s.deaths))
                    .replace("%kdr%", Util.kdrStr(s.kills, s.deaths))
                    .replace("%streak%", String.valueOf(s.streak))
                    .replace("%best%", String.valueOf(s.bestStreak))
                    .replace("%assists%", String.valueOf(s.assists))
                    .replace("%ores%", String.valueOf(s.ores))
                    .replace("%title%", titleText(p))
                    .replace("%coins%", Util.fmt(s.coins))
                    .replace("%name%", p.getName())
                    .replace("%rank%", rankText(p));
            // Trailing colour code makes every line unique (blank lines too) and is invisible.
            line = Util.color(line) + ChatColor.COLOR_CHAR + Integer.toHexString(i);
            if (line.length() > 40) {
                line = line.substring(0, 40);
                if (line.endsWith(String.valueOf(ChatColor.COLOR_CHAR))) {
                    line = line.substring(0, 39);
                }
            }
            fresh.add(line);
        }
        int score = fresh.size();
        for (String l : fresh) {
            obj.getScore(l).setScore(score--);
        }
        List<String> old = lines.get(p.getUniqueId());
        if (old != null) {
            for (String o : old) {
                if (!fresh.contains(o)) {
                    sb.resetScores(o);
                }
            }
        }
        lines.put(p.getUniqueId(), fresh);
    }

    /** The player's own scoreboard, creating a private one if they are still on the shared main board. */
    public Scoreboard scoreboard(Player p) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        Scoreboard sb = p.getScoreboard();
        if (sb == null || sb == sm.getMainScoreboard()) {
            sb = sm.getNewScoreboard();
            p.setScoreboard(sb);
        }
        return sb;
    }

    private String titleText(Player p) {
        String title = plugin.achievements().chatText(p.getUniqueId());
        return title == null ? "" : title;
    }

    private String rankText(Player p) {
        String prefix = plugin.ranks().prefixOf(p.getUniqueId());
        return prefix == null ? "" : prefix;
    }

    public void remove(Player p) {
        lines.remove(p.getUniqueId());
    }
}

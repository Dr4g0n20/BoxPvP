package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Shows "[rank] username [clan]" above players' heads and in the tab list.
 *
 * Done with scoreboard teams: every player has their own scoreboard (for the sidebar), so each viewer's scoreboard
 * gets one team per player whose prefix is the rank and whose suffix is the clan. 1.12 limits team prefixes and
 * suffixes to 16 characters (colour codes included), so long ones are trimmed.
 */
public class NameTagManager {

    private final BoxPvP plugin;

    public NameTagManager(BoxPvP plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("nametags.enabled", true);
    }

    private static String teamName(Player target) {
        return "bp" + target.getUniqueId().toString().replace("-", "").substring(0, 12);
    }

    private static String trim(String s, int max) {
        if (s.length() > max) {
            s = s.substring(0, max);
            if (s.endsWith("\u00a7")) {
                s = s.substring(0, max - 1);
            }
        }
        return s;
    }

    private String prefixFor(Player target) {
        String rank = plugin.ranks().prefixOf(target.getUniqueId());
        if (rank == null) {
            rank = plugin.achievements().tagText(target.getUniqueId()); // earned title when there's no admin rank
        }
        if (rank == null) {
            return "";
        }
        // Leave room for a reset code so the player's name itself isn't tinted by the rank colour.
        return trim(rank + " ", 14) + "\u00a7r";
    }

    private String suffixFor(Player target) {
        String clan = plugin.clans().prefixOf(target.getUniqueId());
        return clan == null ? "" : trim(" " + clan, 16);
    }

    /** Creates / updates / removes target's team on one viewer's scoreboard. */
    private void apply(Player viewer, Player target) {
        Scoreboard sb = plugin.board().scoreboard(viewer);
        String name = teamName(target);
        Team team = sb.getTeam(name);
        String prefix = prefixFor(target);
        String suffix = suffixFor(target);
        if (prefix.isEmpty() && suffix.isEmpty()) {
            if (team != null) {
                team.unregister();
            }
            return;
        }
        try {
            if (team == null) {
                team = sb.registerNewTeam(name);
            }
            team.setPrefix(prefix);
            team.setSuffix(suffix);
            if (!team.hasEntry(target.getName())) {
                team.addEntry(target.getName());
            }
        } catch (IllegalArgumentException ignored) {
            // a prefix/suffix the server refused; leave the default name
        }
    }

    /** Show this target's rank/clan to everybody. */
    public void refreshTarget(Player target) {
        if (!enabled()) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            apply(viewer, target);
        }
    }

    /** Show everybody's rank/clan to this viewer. */
    public void refreshViewer(Player viewer) {
        if (!enabled()) {
            return;
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            apply(viewer, target);
        }
    }

    public void refreshAll() {
        if (!enabled()) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                apply(viewer, target);
            }
        }
    }

    public void onJoin(Player p) {
        if (!enabled()) {
            return;
        }
        try {
            p.setPlayerListName(null); // the team prefix/suffix now decides how the tab list looks
        } catch (IllegalArgumentException ignored) {
            // nothing to reset
        }
        refreshViewer(p);
        refreshTarget(p);
    }

    public void onQuit(Player p) {
        String name = teamName(p);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(p)) {
                continue;
            }
            Team team = plugin.board().scoreboard(viewer).getTeam(name);
            if (team != null) {
                team.unregister();
            }
        }
    }
}

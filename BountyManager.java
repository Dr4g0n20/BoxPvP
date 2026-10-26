package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Bounties: once a player's killstreak reaches bounty.min-streak they carry a bounty that grows with every extra kill.
 * Whoever ends the streak collects it (paid in GameListener.reward). Nothing is stored - it's worked out from the streak.
 */
public class BountyManager {

    public static class Bounty {
        public final String name;
        public final int streak;
        public final long coins;

        Bounty(String name, int streak, long coins) {
            this.name = name;
            this.streak = streak;
            this.coins = coins;
        }
    }

    private final BoxPvP plugin;

    public BountyManager(BoxPvP plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("bounty.enabled", true);
    }

    private int minStreak() {
        return Math.max(1, plugin.getConfig().getInt("bounty.min-streak", 5));
    }

    /** The bounty on a player who is on this streak (0 if they don't have one). */
    public long bountyFor(int streak) {
        if (!enabled() || streak < minStreak()) {
            return 0L;
        }
        long base = plugin.getConfig().getLong("bounty.base", 50L);
        long perExtra = plugin.getConfig().getLong("bounty.per-extra-kill", 25L);
        long max = plugin.getConfig().getLong("bounty.max", 1000L);
        return Math.min(max, base + perExtra * (streak - minStreak()));
    }

    /** Tells the server when a bounty appears, and every few kills after that when it grows. */
    public void announce(Player p, int streak) {
        if (!enabled() || streak < minStreak()) {
            return;
        }
        int every = Math.max(1, plugin.getConfig().getInt("bounty.announce-every", 5));
        if (streak == minStreak() || (streak - minStreak()) % every == 0) {
            Bukkit.broadcastMessage(Util.color("&6&lBOUNTY! &e" + p.getName() + " &7is on a &c" + streak
                    + " &7kill streak - &6" + Util.fmt(bountyFor(streak)) + " coins &7to whoever ends it!"));
        }
    }

    /** Everyone online who currently has a bounty, biggest first. */
    public List<Bounty> current() {
        List<Bounty> out = new ArrayList<Bounty>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            int streak = plugin.stats().get(p).streak;
            long coins = bountyFor(streak);
            if (coins > 0) {
                out.add(new Bounty(p.getName(), streak, coins));
            }
        }
        Collections.sort(out, new Comparator<Bounty>() {
            @Override
            public int compare(Bounty a, Bounty b) {
                return Long.compare(b.coins, a.coins);
            }
        });
        return out;
    }
}

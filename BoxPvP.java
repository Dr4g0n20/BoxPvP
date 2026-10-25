package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BoxPvP extends JavaPlugin {

    private StatsManager stats;
    private BoardManager board;
    private RegionManager regions;
    private MineManager mines;
    private ShopManager shop;
    private LaunchPadManager pads;
    private DummyManager dummies;
    private RankManager ranks;
    private RankManager clans;
    private NameTagManager nameTags;
    private AchievementManager achievements;
    private UpgraderManager upgrader;
    private EnchantSettings enchantSettings;
    private EnchantStore enchantStore;
    private EnchantEffects enchantEffects;
    private EnchanterManager enchanter;
    private Location spawn;
    private final Map<UUID, Tag> combat = new HashMap<UUID, Tag>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSpawn();

        stats = new StatsManager(this);
        board = new BoardManager(this);
        regions = new RegionManager(this);
        mines = new MineManager(this);
        shop = new ShopManager(this);
        pads = new LaunchPadManager(this);
        dummies = new DummyManager(this);
        ranks = new RankManager(this, "ranks.yml");
        clans = new RankManager(this, "clans.yml");
        nameTags = new NameTagManager(this);
        achievements = new AchievementManager(this);
        upgrader = new UpgraderManager(this);
        enchantSettings = new EnchantSettings(this);
        enchantStore = new EnchantStore(this);
        enchantEffects = new EnchantEffects(this);
        enchanter = new EnchanterManager(this);

        stats.load();
        regions.load();
        mines.load();
        shop.load();
        pads.load();
        ranks.load();
        clans.load();
        achievements.load();
        upgrader.load();
        enchantStore.load();
        pads.cleanupLeftovers();

        Bukkit.getPluginManager().registerEvents(new GameListener(this), this);
        Bukkit.getPluginManager().registerEvents(shop, this);
        Bukkit.getPluginManager().registerEvents(pads, this);
        Bukkit.getPluginManager().registerEvents(dummies, this);
        Bukkit.getPluginManager().registerEvents(upgrader, this);
        Bukkit.getPluginManager().registerEvents(enchantEffects, this);
        Bukkit.getPluginManager().registerEvents(enchanter, this);
        dummies.start();

        Commands cmds = new Commands(this);
        String[] names = {"stats", "top", "spawn", "setspawn", "setcoins", "boxpvp", "region", "mine", "npc", "launchpad", "dummy", "rank", "clan", "pay", "resetstats", "title"};
        for (String name : names) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(cmds);
                pc.setTabCompleter(cmds);
            }
        }

        regions.startRain();

        long period = Math.max(1L, getConfig().getLong("autosave-minutes", 5L)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                stats.save();
            }
        }, period, period);

        for (Player p : Bukkit.getOnlinePlayers()) {
            stats.get(p);
            board.update(p);
            achievements.sync(p);
            nameTags.onJoin(p);
        }

        // Combat timer above the hotbar (and expiry of old tags).
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                tickCombat();
            }
        }, 10L, 10L);
        getLogger().info("BoxPvP enabled.");
    }

    @Override
    public void onDisable() {
        if (stats != null) {
            stats.save();
        }
        if (achievements != null) {
            achievements.save();
        }
        if (enchantStore != null) {
            enchantStore.save();
        }
        if (pads != null) {
            pads.shutdown();
        }
        if (mines != null) {
            mines.restorePending();
        }
        if (regions != null) {
            regions.stopRain();
            regions.clearDecaying();
        }
    }

    public void reloadAll() {
        reloadConfig();
        regions.load();
        mines.load();
        shop.load();
        pads.load();
        ranks.load();
        clans.load();
        achievements.load();
        upgrader.load();
        regions.startRain();
        for (Player p : Bukkit.getOnlinePlayers()) {
            board.update(p);
            achievements.sync(p);
        }
        nameTags.refreshAll();
    }

    // ------------------------------------------------------------ accessors

    public StatsManager stats() {
        return stats;
    }

    public BoardManager board() {
        return board;
    }

    public RegionManager regions() {
        return regions;
    }

    public MineManager mines() {
        return mines;
    }

    public ShopManager shop() {
        return shop;
    }

    public LaunchPadManager pads() {
        return pads;
    }

    public DummyManager dummies() {
        return dummies;
    }

    public RankManager ranks() {
        return ranks;
    }

    public RankManager clans() {
        return clans;
    }

    public NameTagManager nameTags() {
        return nameTags;
    }

    public AchievementManager achievements() {
        return achievements;
    }

    public UpgraderManager upgrader() {
        return upgrader;
    }

    public EnchantSettings enchantSettings() {
        return enchantSettings;
    }

    public EnchantStore enchantStore() {
        return enchantStore;
    }

    public EnchantEffects enchantEffects() {
        return enchantEffects;
    }

    public EnchanterManager enchanter() {
        return enchanter;
    }

    /** A config section from the server's config.yml, or from the built-in defaults if the file doesn't have it yet. */
    public ConfigurationSection sectionOrDefault(String path) {
        ConfigurationSection s = getConfig().getConfigurationSection(path);
        if (s != null && !s.getKeys(false).isEmpty()) {
            return s;
        }
        InputStream in = getResource("config.yml");
        if (in == null) {
            return s;
        }
        YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        return def.getConfigurationSection(path);
    }

    // ------------------------------------------------------------ spawn

    private File dataFile() {
        return new File(getDataFolder(), "data.yml");
    }

    private void loadSpawn() {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(dataFile());
        spawn = Util.loadLoc(c, "spawn");
    }

    public Location spawn() {
        return spawn == null ? null : spawn.clone();
    }

    public void setSpawn(Location l) {
        spawn = l.clone();
        YamlConfiguration c = new YamlConfiguration();
        Util.saveLoc(c, "spawn", spawn);
        try {
            c.save(dataFile());
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ combat tag

    private static class Tag {
        final long time;
        final UUID opponent;

        Tag(long time, UUID opponent) {
            this.time = time;
            this.opponent = opponent;
        }
    }

    /** Marks p as in combat, remembering who they were fighting. */
    public void tagCombat(Player p, Player opponent) {
        combat.put(p.getUniqueId(), new Tag(System.currentTimeMillis(), opponent == null ? null : opponent.getUniqueId()));
    }

    public boolean inCombat(Player p) {
        Tag t = combat.get(p.getUniqueId());
        if (t == null) {
            return false;
        }
        return System.currentTimeMillis() - t.time < getConfig().getLong("combat-tag-seconds", 10L) * 1000L;
    }

    /** Who p was last fighting (only while still in combat), or null. */
    public UUID combatOpponent(Player p) {
        Tag t = combat.get(p.getUniqueId());
        return inCombat(p) && t != null ? t.opponent : null;
    }

    public void clearCombat(Player p) {
        combat.remove(p.getUniqueId());
    }

    /** Runs every half second: shows the combat countdown and drops expired tags. */
    private void tickCombat() {
        long tagMs = getConfig().getLong("combat-tag-seconds", 10L) * 1000L;
        boolean bar = getConfig().getBoolean("combat-timer.actionbar", true);
        long now = System.currentTimeMillis();
        java.util.Iterator<Map.Entry<UUID, Tag>> it = combat.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Tag> en = it.next();
            long left = tagMs - (now - en.getValue().time);
            Player p = Bukkit.getPlayer(en.getKey());
            boolean show = bar && p != null && p.isOnline();
            if (left > 0) {
                if (show) {
                    Util.actionBar(p, "&cIn combat: &f" + ((left + 999L) / 1000L) + "s");
                }
            } else {
                if (show) {
                    Util.actionBar(p, "&aYou are no longer in combat");
                }
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------ messaging

    public void msg(CommandSender to, String text) {
        to.sendMessage(Util.color(getConfig().getString("prefix", "&8[&6BoxPvP&8] &7") + text));
    }
}

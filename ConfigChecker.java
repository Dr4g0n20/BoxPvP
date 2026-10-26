package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** /boxpvp check: scans the config and your saved zones for mistakes that would otherwise fail quietly. */
public class ConfigChecker {

    private final BoxPvP plugin;

    public ConfigChecker(BoxPvP plugin) {
        this.plugin = plugin;
    }

    public List<String> run() {
        List<String> out = new ArrayList<String>();
        FileConfiguration cfg = plugin.getConfig();

        checkShop(out, cfg);
        checkMines(out, cfg);
        checkUpgrader(out, cfg, "upgrader.enchants");
        checkUpgrader(out, cfg, "upgrader.sword-enchants");
        checkUpgrader(out, cfg, "upgrader.axe-enchants");
        checkCustomEnchants(out, cfg);
        checkAchievements(out);
        checkKillstreaks(out, cfg);
        checkZones(out, cfg);
        return out;
    }

    private void checkShop(List<String> out, FileConfiguration cfg) {
        for (Map<?, ?> m : cfg.getMapList("shop.sell")) {
            if (ItemKey.parse(String.valueOf(m.get("item"))) == null) {
                out.add("shop.sell: unknown item '" + m.get("item") + "'");
            }
            if (!(m.get("price") instanceof Number)) {
                out.add("shop.sell: item '" + m.get("item") + "' has no numeric price");
            }
        }
        for (Map<?, ?> m : cfg.getMapList("shop.buy")) {
            if (ItemKey.parse(String.valueOf(m.get("item"))) == null) {
                out.add("shop.buy: unknown item '" + m.get("item") + "'");
            }
            if (!(m.get("price") instanceof Number)) {
                out.add("shop.buy: item '" + m.get("item") + "' has no numeric price");
            }
            if (m.get("potion") != null) {
                try {
                    PotionType.valueOf(String.valueOf(m.get("potion")).toUpperCase());
                } catch (IllegalArgumentException ex) {
                    out.add("shop.buy: unknown potion type '" + m.get("potion") + "'");
                }
            }
        }
        if (cfg.getMapList("shop.sell").size() > 18) {
            out.add("shop.sell has more than 18 items: extra pages work, but check the menu looks right");
        }
    }

    private void checkMines(List<String> out, FileConfiguration cfg) {
        ConfigurationSection d = cfg.getConfigurationSection("mines.default-blocks");
        if (d == null || d.getKeys(false).isEmpty()) {
            out.add("mines.default-blocks is empty - mines would fill with stone only");
        } else {
            for (String k : d.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m == null || !m.isBlock()) {
                    out.add("mines.default-blocks: '" + k + "' is not a block material");
                }
            }
        }
        ConfigurationSection sm = cfg.getConfigurationSection("mines.smelt");
        if (sm != null) {
            for (String k : sm.getKeys(false)) {
                if (Material.matchMaterial(k) == null) {
                    out.add("mines.smelt: unknown material '" + k + "'");
                }
                if (ItemKey.parse(sm.getString(k)) == null) {
                    out.add("mines.smelt: '" + k + "' turns into unknown item '" + sm.getString(k) + "'");
                }
            }
        }
    }

    private void checkUpgrader(List<String> out, FileConfiguration cfg, String path) {
        for (Map<?, ?> m : cfg.getMapList(path)) {
            String name = String.valueOf(m.get("enchant"));
            if (Enchantment.getByName(name.toUpperCase()) == null) {
                out.add(path + ": unknown enchantment '" + name + "'");
                continue;
            }
            Object pr = m.get("prices");
            if (!(pr instanceof List) || ((List<?>) pr).isEmpty()) {
                out.add(path + ": " + name + " has no prices list");
            } else if (m.get("max") instanceof Number && ((List<?>) pr).size() < ((Number) m.get("max")).intValue()) {
                out.add(path + ": " + name + " has fewer prices than levels (the last price is reused)");
            }
        }
    }

    private void checkCustomEnchants(List<String> out, FileConfiguration cfg) {
        EnchantSettings s = plugin.enchantSettings();
        if (s.maxSlots() < s.baseSlots()) {
            out.add("custom-enchants: max-slots is lower than base-slots");
        }
        int upgrades = s.maxSlots() - s.baseSlots();
        if (cfg.getLongList("custom-enchants.slot-prices").size() < upgrades) {
            out.add("custom-enchants.slot-prices has fewer prices (" + cfg.getLongList("custom-enchants.slot-prices").size()
                    + ") than slot upgrades (" + upgrades + "); the last price is reused");
        }
        for (String which : Arrays.asList("random", "tool", "choose")) {
            if (s.pullPrice(which) <= 0) {
                out.add("custom-enchants.pull-prices." + which + " should be above 0");
            }
        }
        for (CustomEnchant ce : CustomEnchant.ALL) {
            String base = "custom-enchants.enchants." + ce.id;
            if (cfg.getConfigurationSection(base) == null) {
                out.add(base + " is missing from the config");
                continue;
            }
            int max = s.max(ce.id);
            int prices = cfg.getLongList(base + ".prices").size();
            if (prices < max - 1) {
                out.add(base + ": max is " + max + " but only " + prices + " upgrade prices (need " + (max - 1) + ")");
            }
        }
        for (String item : cfg.getStringList("custom-enchants.enchants.jackpot.items")) {
            if (ItemKey.parseStack(item) == null) {
                out.add("custom-enchants jackpot item '" + item + "' is invalid");
            }
        }
    }

    private void checkAchievements(List<String> out) {
        ConfigurationSection ts = plugin.sectionOrDefault("achievements.tracks");
        if (ts == null) {
            return;
        }
        List<String> stats = Arrays.asList("ores", "kills", "assists", "streak", "sold");
        for (String id : ts.getKeys(false)) {
            if (!stats.contains(ts.getString(id + ".stat", ""))) {
                out.add("achievements." + id + ": unknown stat '" + ts.getString(id + ".stat") + "' (use " + stats + ")");
            }
            if (ts.getMapList(id + ".tiers").isEmpty()) {
                out.add("achievements." + id + " has no tiers");
            }
        }
    }

    private void checkKillstreaks(List<String> out, FileConfiguration cfg) {
        ConfigurationSection rs = cfg.getConfigurationSection("killstreak.rewards");
        if (rs == null) {
            return;
        }
        for (String key : rs.getKeys(false)) {
            if (Util.parseInt(key) == null) {
                out.add("killstreak.rewards: '" + key + "' should be a streak number");
            }
            for (String item : rs.getStringList(key + ".items")) {
                if (ItemKey.parseStack(item) == null) {
                    out.add("killstreak.rewards." + key + ": invalid item '" + item + "'");
                }
            }
        }
    }

    private void checkZones(List<String> out, FileConfiguration cfg) {
        if (plugin.spawn() == null) {
            out.add("No spawn set - use /setspawn (players respawn at the world spawn until you do)");
        }
        if (Material.matchMaterial(cfg.getString("launchpad.block", "GOLD_BLOCK")) == null) {
            out.add("launchpad.block is not a valid material");
        }
        for (Region r : plugin.regions().all()) {
            if (Bukkit.getWorld(r.world) == null) {
                out.add("Region '" + r.name + "' is in world '" + r.world + "' which isn't loaded");
            }
            if (r.flags.isEmpty() && r.decaySeconds <= 0) {
                out.add("Region '" + r.name + "' has no flags or decay set - it does nothing");
            }
        }
        for (Mine m : plugin.mines().all()) {
            if (Bukkit.getWorld(m.world) == null) {
                out.add("Mine '" + m.name + "' is in world '" + m.world + "' which isn't loaded");
            }
            if (m.volume() > cfg.getLong("mines.max-fill-blocks", 200000L)) {
                out.add("Mine '" + m.name + "' is bigger than mines.max-fill-blocks, so /mine fill won't work on it");
            }
        }
        Material padBlock = plugin.pads().padMaterial();
        for (LaunchPadManager.Pad pad : plugin.pads().all()) {
            if (Bukkit.getWorld(pad.world) == null) {
                out.add("Launch pad '" + pad.name + "' is in world '" + pad.world + "' which isn't loaded");
                continue;
            }
            if (pad.landing == null) {
                out.add("Launch pad '" + pad.name + "' has no landing point - use /launchpad setlanding " + pad.name);
            }
            if (Bukkit.getWorld(pad.world).getBlockAt(pad.x, pad.y, pad.z).getType() != padBlock) {
                out.add("Launch pad '" + pad.name + "': the block at " + pad.x + "," + pad.y + "," + pad.z + " is no longer " + padBlock);
            }
        }
    }
}

package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Commands implements CommandExecutor, TabCompleter {

    private static final List<String> TOP_TYPES = Arrays.asList("kills", "deaths", "assists", "ores", "streak", "coins", "kdr");

    private final BoxPvP plugin;
    private final Map<UUID, Long> payCooldown = new HashMap<UUID, Long>();

    public Commands(BoxPvP plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String m) {
        plugin.msg(s, m);
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("boxpvp.admin")) {
            return true;
        }
        msg(s, "&cYou don't have permission to do that.");
        return false;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player) {
            return (Player) s;
        }
        msg(s, "&cThis command can only be used by a player.");
        return null;
    }

    private boolean validName(String n) {
        return n.matches("[A-Za-z0-9_\\-]{1,32}");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        switch (cmd.getName().toLowerCase()) {
            case "stats":
                stats(sender, a);
                return true;
            case "top":
                top(sender, a);
                return true;
            case "spawn":
                spawn(sender);
                return true;
            case "setspawn":
                setSpawn(sender);
                return true;
            case "setcoins":
                setCoins(sender, a);
                return true;
            case "boxpvp":
                boxpvp(sender, a);
                return true;
            case "region":
                region(sender, a);
                return true;
            case "mine":
                mine(sender, a);
                return true;
            case "npc":
                npc(sender, a);
                return true;
            case "launchpad":
                launchpad(sender, a);
                return true;
            case "dummy":
                dummy(sender, a);
                return true;
            case "rank":
                rank(sender, a);
                return true;
            case "clan":
                clan(sender, a);
                return true;
            case "pay":
                pay(sender, a);
                return true;
            case "title":
                title(sender, a);
                return true;
            case "resetstats":
                resetStats(sender, a);
                return true;
            default:
                return false;
        }
    }

    // ============================================================ player commands

    private void stats(CommandSender sender, String[] a) {
        PlayerStats ps;
        if (a.length > 0) {
            ps = plugin.stats().byName(a[0]);
            if (ps == null) {
                msg(sender, "&cNo stats found for &f" + a[0]);
                return;
            }
        } else if (sender instanceof Player) {
            ps = plugin.stats().get((Player) sender);
        } else {
            msg(sender, "&cUsage: /stats <player>");
            return;
        }
        sender.sendMessage(Util.color("&8&m      &r &6" + ps.name + "&7's stats &8&m      "));
        sender.sendMessage(Util.color("&7Kills: &f" + ps.kills + "   &7Deaths: &f" + ps.deaths + "   &7K/D: &f" + Util.kdrStr(ps.kills, ps.deaths) + "   &7Assists: &f" + ps.assists));
        sender.sendMessage(Util.color("&7Streak: &e" + ps.streak + "   &7Best streak: &e" + ps.bestStreak));
        sender.sendMessage(Util.color("&7Coins: &6" + Util.fmt(ps.coins)));
        sender.sendMessage(Util.color("&7Ores mined: &f" + Util.fmt(ps.ores) + "   &7Items sold: &f" + Util.fmt(ps.sold)));
    }

    private void top(CommandSender sender, String[] a) {
        String type = a.length > 0 ? a[0].toLowerCase() : "kills";
        if (!TOP_TYPES.contains(type)) {
            msg(sender, "&cUsage: /top <" + String.join("|", TOP_TYPES) + ">");
            return;
        }
        List<PlayerStats> list = plugin.stats().top(type, 10);
        sender.sendMessage(Util.color("&8&m      &r &6Top 10 - " + type + " &8&m      "));
        int i = 1;
        for (PlayerStats ps : list) {
            sender.sendMessage(Util.color("&e" + i + ". &f" + ps.name + " &7- &6" + plugin.stats().value(ps, type)));
            i++;
        }
        if (list.isEmpty()) {
            sender.sendMessage(Util.color("&7Nobody on the board yet."));
        }
    }

    private void spawn(CommandSender sender) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        if (plugin.spawn() == null) {
            msg(p, "&cSpawn hasn't been set yet.");
            return;
        }
        if (plugin.inCombat(p)) {
            msg(p, "&cYou can't use /spawn while in combat!");
            return;
        }
        p.teleport(plugin.spawn());
    }

    // ============================================================ admin: simple

    private void setSpawn(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        Player p = player(sender);
        if (p == null) {
            return;
        }
        plugin.setSpawn(p.getLocation());
        msg(p, "&aSpawn set to your current location. Players respawn here.");
    }

    private void setCoins(CommandSender sender, String[] a) {
        if (!admin(sender)) {
            return;
        }
        if (a.length < 2) {
            msg(sender, "&cUsage: /setcoins <player> <amount>");
            return;
        }
        PlayerStats ps = plugin.stats().byName(a[0]);
        Integer amt = Util.parseInt(a[1]);
        if (ps == null || amt == null) {
            msg(sender, "&cUnknown player or bad number.");
            return;
        }
        ps.coins = Math.max(0, amt);
        Player online = Bukkit.getPlayerExact(a[0]);
        if (online != null) {
            plugin.board().update(online);
        }
        msg(sender, "&a" + ps.name + " now has &6" + Util.fmt(ps.coins) + " &acoins.");
    }

    private void boxpvp(CommandSender sender, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
            if (!admin(sender)) {
                return;
            }
            plugin.reloadAll();
            msg(sender, "&aConfig, regions, mines, shop and launch pads reloaded.");
            return;
        }
        msg(sender, "&6BoxPvP &7commands: /stats, /top, /spawn, /pay, /title");
        if (sender.hasPermission("boxpvp.admin")) {
            sender.sendMessage(Util.color("&7Admin: /setspawn, /region, /mine, /npc, /dummy, /launchpad, /rank, /clan, /resetstats, /setcoins, /boxpvp reload"));
        }
    }

    // ============================================================ /region

    private void region(CommandSender sender, String[] a) {
        if (!admin(sender)) {
            return;
        }
        Player p = player(sender);
        if (p == null) {
            return;
        }
        RegionManager rm = plugin.regions();
        if (a.length == 0) {
            msg(p, "&7/region wand | pos1 | pos2 | create <name> [priority] | delete <name> | list | info <name>");
            msg(p, "&7/region flag <name> <" + String.join("|", RegionManager.FLAGS) + "> <allow|deny|none>");
            msg(p, "&7/region priority <name> <number>   |   /region decay <name> <seconds|off>");
            return;
        }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "wand": {
                p.getInventory().addItem(rm.wand());
                msg(p, "&aYou got the region wand. Left-click = corner 1, right-click = corner 2.");
                break;
            }
            case "pos1":
            case "pos2": {
                rm.setPos(p, sub.equals("pos1") ? 0 : 1, p.getLocation());
                msg(p, "&aCorner " + (sub.equals("pos1") ? 1 : 2) + " set to your position.");
                break;
            }
            case "create": {
                if (a.length < 2 || !validName(a[1])) {
                    msg(p, "&cUsage: /region create <name> [priority]  (letters, numbers, _ and - only)");
                    break;
                }
                Location[] sel = rm.selection(p);
                if (sel == null || sel[0] == null || sel[1] == null) {
                    msg(p, "&cSelect two corners first (/region wand).");
                    break;
                }
                if (!sel[0].getWorld().equals(sel[1].getWorld())) {
                    msg(p, "&cBoth corners must be in the same world.");
                    break;
                }
                if (rm.get(a[1]) != null) {
                    msg(p, "&cA region called &f" + a[1] + " &calready exists.");
                    break;
                }
                Region r = rm.create(a[1], sel[0], sel[1]);
                if (a.length > 2) {
                    Integer pr = Util.parseInt(a[2]);
                    if (pr != null) {
                        r.priority = pr;
                        rm.save();
                    }
                }
                msg(p, "&aRegion &f" + r.name + " &acreated. Set rules with /region flag " + r.name + " <flag> <allow|deny>");
                break;
            }
            case "delete": {
                if (a.length < 2) {
                    msg(p, "&cUsage: /region delete <name>");
                    break;
                }
                msg(p, rm.delete(a[1]) ? "&aRegion deleted." : "&cNo such region.");
                break;
            }
            case "list": {
                if (rm.all().isEmpty()) {
                    msg(p, "&7No regions yet.");
                } else {
                    msg(p, "&7Regions: &f" + String.join("&7, &f", rm.names()));
                }
                break;
            }
            case "info": {
                Region r = a.length > 1 ? rm.get(a[1]) : null;
                if (r == null) {
                    msg(p, "&cUsage: /region info <name>");
                    break;
                }
                msg(p, "&6" + r.name + " &7(world " + r.world + ", priority " + r.priority + ")");
                if (r.decaySeconds > 0) {
                    msg(p, "&7Block decay: placed blocks vanish after &f" + r.decaySeconds + "s");
                }
                msg(p, "&7From &f" + r.minX + "," + r.minY + "," + r.minZ + " &7to &f" + r.maxX + "," + r.maxY + "," + r.maxZ);
                if (r.flags.isEmpty()) {
                    msg(p, "&7Flags: none set (defaults apply)");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, Boolean> en : r.flags.entrySet()) {
                        sb.append(en.getValue() ? "&a" : "&c").append(en.getKey()).append("&7, ");
                    }
                    msg(p, "&7Flags: " + sb);
                }
                break;
            }
            case "flag": {
                if (a.length < 4) {
                    msg(p, "&cUsage: /region flag <name> <flag> <allow|deny|none>");
                    break;
                }
                Region r = rm.get(a[1]);
                String flag = a[2].toLowerCase();
                if (r == null) {
                    msg(p, "&cNo such region.");
                    break;
                }
                if (!RegionManager.FLAGS.contains(flag)) {
                    msg(p, "&cUnknown flag. Flags: &f" + String.join(", ", RegionManager.FLAGS));
                    break;
                }
                String val = a[3].toLowerCase();
                if (val.equals("allow") || val.equals("true")) {
                    r.flags.put(flag, Boolean.TRUE);
                } else if (val.equals("deny") || val.equals("false")) {
                    r.flags.put(flag, Boolean.FALSE);
                } else if (val.equals("none") || val.equals("clear")) {
                    r.flags.remove(flag);
                } else {
                    msg(p, "&cValue must be allow, deny or none.");
                    break;
                }
                rm.save();
                msg(p, "&aFlag &f" + flag + " &aon &f" + r.name + " &aset to &f" + val + "&a.");
                break;
            }
            case "priority": {
                Region r = a.length > 1 ? rm.get(a[1]) : null;
                Integer pr = a.length > 2 ? Util.parseInt(a[2]) : null;
                if (r == null || pr == null) {
                    msg(p, "&cUsage: /region priority <name> <number>");
                    break;
                }
                r.priority = pr;
                rm.save();
                msg(p, "&aPriority of &f" + r.name + " &aset to &f" + pr + "&a. Higher numbers win where regions overlap.");
                break;
            }
            case "decay": {
                Region r = a.length > 1 ? rm.get(a[1]) : null;
                if (r == null || a.length < 3) {
                    msg(p, "&cUsage: /region decay <name> <seconds|off>");
                    break;
                }
                if (a[2].equalsIgnoreCase("off") || a[2].equals("0")) {
                    r.decaySeconds = 0;
                    rm.save();
                    msg(p, "&aBlock decay turned off for &f" + r.name + "&a.");
                    break;
                }
                Integer secs = Util.parseInt(a[2]);
                if (secs == null || secs < 1 || secs > 3600) {
                    msg(p, "&cSeconds must be between 1 and 3600 (or 'off').");
                    break;
                }
                r.decaySeconds = secs;
                rm.save();
                msg(p, "&aBlocks placed in &f" + r.name + " &awill now vanish after &f" + secs + "s&a. Make sure its build flag is allow (or unset).");
                break;
            }
            default:
                msg(p, "&cUnknown subcommand. Use /region for help.");
        }
    }

    // ============================================================ /mine

    private void mine(CommandSender sender, String[] a) {
        if (!admin(sender)) {
            return;
        }
        Player p = player(sender);
        if (p == null) {
            return;
        }
        MineManager mm = plugin.mines();
        if (a.length == 0) {
            msg(p, "&7/mine create <name> | delete <name> | list | info <name> | fill <name>");
            msg(p, "&7/mine block <name> <MATERIAL> <weight>  (weight 0 removes)  | /mine regen <name> <seconds>");
            return;
        }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "create": {
                if (a.length < 2 || !validName(a[1])) {
                    msg(p, "&cUsage: /mine create <name>  (select two corners with /region wand first)");
                    break;
                }
                Location[] sel = plugin.regions().selection(p);
                if (sel == null || sel[0] == null || sel[1] == null) {
                    msg(p, "&cSelect two corners first (/region wand).");
                    break;
                }
                if (!sel[0].getWorld().equals(sel[1].getWorld())) {
                    msg(p, "&cBoth corners must be in the same world.");
                    break;
                }
                if (mm.get(a[1]) != null) {
                    msg(p, "&cA mine called &f" + a[1] + " &calready exists.");
                    break;
                }
                Mine m = mm.create(a[1], sel[0], sel[1]);
                msg(p, "&aMine &f" + m.name + " &acreated (" + Util.fmt(m.volume()) + " blocks). Use &f/mine fill " + m.name + " &ato fill it.");
                break;
            }
            case "delete": {
                if (a.length < 2) {
                    msg(p, "&cUsage: /mine delete <name>");
                    break;
                }
                msg(p, mm.delete(a[1]) ? "&aMine deleted (its blocks are left as they are)." : "&cNo such mine.");
                break;
            }
            case "list": {
                if (mm.all().isEmpty()) {
                    msg(p, "&7No mines yet.");
                } else {
                    msg(p, "&7Mines: &f" + String.join("&7, &f", mm.names()));
                }
                break;
            }
            case "info": {
                Mine m = a.length > 1 ? mm.get(a[1]) : null;
                if (m == null) {
                    msg(p, "&cUsage: /mine info <name>");
                    break;
                }
                msg(p, "&6" + m.name + " &7(world " + m.world + ", " + Util.fmt(m.volume()) + " blocks)");
                msg(p, "&7Regen: &f" + (m.regenSeconds > 0 ? m.regenSeconds : plugin.getConfig().getInt("mines.regen-seconds", 8)) + "s"
                        + (m.regenSeconds > 0 ? "" : " &7(default)"));
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Material, Integer> en : mm.effectiveWeights(m).entrySet()) {
                    sb.append("&f").append(en.getKey().name().toLowerCase()).append(" &7x").append(en.getValue()).append(", ");
                }
                msg(p, "&7Blocks" + (m.weights.isEmpty() ? " (defaults)" : "") + ": " + sb);
                break;
            }
            case "fill": {
                Mine m = a.length > 1 ? mm.get(a[1]) : null;
                if (m == null) {
                    msg(p, "&cUsage: /mine fill <name>");
                    break;
                }
                msg(p, mm.fill(m) ? "&aMine filled." : "&cCouldn't fill (world not loaded, or bigger than mines.max-fill-blocks).");
                break;
            }
            case "block": {
                Mine m = a.length > 1 ? mm.get(a[1]) : null;
                Material mat = a.length > 2 ? Material.matchMaterial(a[2]) : null;
                Integer w = a.length > 3 ? Util.parseInt(a[3]) : null;
                if (m == null || mat == null || w == null || !mat.isBlock()) {
                    msg(p, "&cUsage: /mine block <name> <BLOCK_MATERIAL> <weight>");
                    break;
                }
                if (w <= 0) {
                    m.weights.remove(mat);
                } else {
                    if (m.weights.isEmpty()) {
                        // Start from the defaults so adding one block doesn't wipe the rest.
                        m.weights.putAll(mm.effectiveWeights(m));
                    }
                    m.weights.put(mat, w);
                }
                mm.save();
                msg(p, "&aUpdated &f" + m.name + "&a: " + mat.name().toLowerCase() + " weight " + Math.max(0, w) + ".");
                break;
            }
            case "regen": {
                Mine m = a.length > 1 ? mm.get(a[1]) : null;
                Integer secs = a.length > 2 ? Util.parseInt(a[2]) : null;
                if (m == null || secs == null || secs < 0) {
                    msg(p, "&cUsage: /mine regen <name> <seconds>  (0 = config default)");
                    break;
                }
                m.regenSeconds = secs;
                mm.save();
                msg(p, "&aRegen time for &f" + m.name + " &aset to &f" + secs + "s&a.");
                break;
            }
            default:
                msg(p, "&cUnknown subcommand. Use /mine for help.");
        }
    }

    // ============================================================ /npc

    private void npc(CommandSender sender, String[] a) {
        if (!admin(sender)) {
            return;
        }
        Player p = player(sender);
        if (p == null) {
            return;
        }
        if (a.length == 0) {
            msg(p, "&7/npc create <seller|buyer>   |   /npc remove  (removes the nearest shop NPC within 6 blocks)");
            msg(p, "&7seller = players BUY from it   |   buyer = players SELL to it   |   upgrader = enchants tools   |   enchanter = custom enchant pulls");
            return;
        }
        if (a[0].equalsIgnoreCase("create")) {
            if (a.length >= 2 && a[1].equalsIgnoreCase("enchanter")) {
                plugin.shop().spawnEnchanter(p);
                msg(p, "&aSpawned the enchanter (players spend coins to pull custom enchants) where you stand.");
                return;
            }
            if (a.length >= 2 && a[1].equalsIgnoreCase("upgrader")) {
                plugin.shop().spawnUpgrader(p);
                msg(p, "&aSpawned the upgrader (players enchant the pickaxe, sword or axe they hold, for coins) where you stand.");
                return;
            }
            if (a.length < 2 || !(a[1].equalsIgnoreCase("seller") || a[1].equalsIgnoreCase("buyer"))) {
                msg(p, "&cUsage: /npc create <seller|buyer|upgrader|enchanter>");
                return;
            }
            boolean seller = a[1].equalsIgnoreCase("seller");
            plugin.shop().spawnNpc(p, seller);
            msg(p, "&aSpawned a " + (seller ? "seller (players buy from it)" : "buyer (players sell to it)") + " NPC where you stand.");
        } else if (a[0].equalsIgnoreCase("remove")) {
            msg(p, plugin.shop().removeNearest(p) ? "&aNPC removed." : "&cNo shop NPC within 6 blocks.");
        } else {
            msg(p, "&cUnknown subcommand. Use /npc for help.");
        }
    }

    // ============================================================ /dummy

    private void dummy(CommandSender sender, String[] a) {
        if (!admin(sender)) {
            return;
        }
        Player p = player(sender);
        if (p == null) {
            return;
        }
        if (a.length == 0) {
            msg(p, "&7/dummy create   |   /dummy remove  (removes the nearest dummy within 6 blocks)");
            return;
        }
        if (a[0].equalsIgnoreCase("create")) {
            plugin.dummies().spawn(p);
            msg(p, "&aTraining dummy spawned. Hit it to see your DPS.");
        } else if (a[0].equalsIgnoreCase("remove")) {
            msg(p, plugin.dummies().removeNearest(p) ? "&aDummy removed." : "&cNo dummy within 6 blocks.");
        } else {
            msg(p, "&cUnknown subcommand. Use /dummy for help.");
        }
    }

    // ============================================================ /rank and /clan (same system, two labels)

    private String joinFrom(String[] a, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < a.length; i++) {
            if (i > from) {
                sb.append(' ');
            }
            sb.append(a[i]);
        }
        return sb.toString();
    }

    private String cap(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void rank(CommandSender sender, String[] a) {
        labels(sender, a, plugin.ranks(), "rank");
    }

    private void clan(CommandSender sender, String[] a) {
        labels(sender, a, plugin.clans(), "clan");
    }

    /** Looks a player up by name: online first, then anyone who has joined before. Returns null if unknown. */
    private UUID resolveId(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        PlayerStats ps = plugin.stats().byName(name);
        return ps == null ? null : ps.id;
    }

    private void labels(CommandSender sender, String[] a, RankManager rm, String noun) {
        if (!admin(sender)) {
            return;
        }
        String example = noun.equals("rank") ? "vip &a[VIP]" : "wolves &b[WOLF]";
        if (a.length == 0) {
            msg(sender, "&7/" + noun + " create <name> <display>   e.g. /" + noun + " create " + example);
            msg(sender, "&7/" + noun + " setprefix <name> <display> | delete <name> | list");
            msg(sender, "&7/" + noun + " give <player> <" + noun + "> | take <player> | check <player>");
            return;
        }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "create": {
                if (a.length < 3 || !validName(a[1])) {
                    msg(sender, "&cUsage: /" + noun + " create <name> <display>   e.g. /" + noun + " create " + example);
                    break;
                }
                if (rm.get(a[1]) != null) {
                    msg(sender, "&cA " + noun + " called &f" + a[1] + " &calready exists.");
                    break;
                }
                String display = joinFrom(a, 2);
                if (display.length() > 16) {
                    msg(sender, "&cThe display text is too long (max 16 characters including colour codes - Minecraft 1.12 limits name tags).");
                    break;
                }
                rm.create(a[1], display);
                msg(sender, "&a" + cap(noun) + " &f" + a[1] + " &acreated: " + display + " &7- give it with /" + noun + " give <player> " + a[1]);
                break;
            }
            case "setprefix":
            case "settag": {
                RankManager.Rank r = a.length > 2 ? rm.get(a[1]) : null;
                if (r == null) {
                    msg(sender, "&cUsage: /" + noun + " setprefix <name> <display>");
                    break;
                }
                String display = joinFrom(a, 2);
                if (display.length() > 16) {
                    msg(sender, "&cThe display text is too long (max 16 characters including colour codes).");
                    break;
                }
                rm.setPrefix(r, display);
                plugin.nameTags().refreshAll();
                msg(sender, "&a" + cap(noun) + " &f" + r.name + " &anow shows as " + display);
                break;
            }
            case "delete": {
                if (a.length < 2) {
                    msg(sender, "&cUsage: /" + noun + " delete <name>");
                    break;
                }
                boolean ok = rm.delete(a[1]);
                plugin.nameTags().refreshAll();
                msg(sender, ok ? "&a" + cap(noun) + " deleted (anyone who had it now has none)." : "&cNo such " + noun + ".");
                break;
            }
            case "list": {
                if (rm.all().isEmpty()) {
                    msg(sender, "&7No " + noun + "s yet. Make one with /" + noun + " create <name> <display>");
                    break;
                }
                for (RankManager.Rank r : rm.all()) {
                    sender.sendMessage(Util.color("&7- &f" + r.name + " &7shows as " + r.prefix + " &7(" + rm.holders(r) + " players)"));
                }
                break;
            }
            case "give":
            case "set": {
                RankManager.Rank r = a.length > 2 ? rm.get(a[2]) : null;
                if (a.length < 3 || r == null) {
                    msg(sender, "&cUsage: /" + noun + " give <player> <" + noun + ">   (/" + noun + " list shows them)");
                    break;
                }
                UUID id = resolveId(a[1]);
                if (id == null) {
                    msg(sender, "&cUnknown player. They must have joined the server at least once.");
                    break;
                }
                rm.give(id, r);
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    plugin.nameTags().refreshTarget(online);
                    msg(online, "&aYou now have the " + noun + " " + r.prefix);
                }
                msg(sender, "&a" + a[1] + " now has the " + noun + " &f" + r.name + " &a(" + r.prefix + "&a).");
                break;
            }
            case "take":
            case "remove": {
                if (a.length < 2) {
                    msg(sender, "&cUsage: /" + noun + " take <player>");
                    break;
                }
                UUID id = resolveId(a[1]);
                if (id == null) {
                    msg(sender, "&cUnknown player.");
                    break;
                }
                boolean had = rm.take(id);
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    plugin.nameTags().refreshTarget(online);
                }
                msg(sender, had ? "&a" + cap(noun) + " removed from &f" + a[1] + "&a." : "&7That player had no " + noun + ".");
                break;
            }
            case "check": {
                if (a.length < 2) {
                    msg(sender, "&cUsage: /" + noun + " check <player>");
                    break;
                }
                UUID id = resolveId(a[1]);
                RankManager.Rank r = id == null ? null : rm.rankOf(id);
                msg(sender, r == null ? "&7" + a[1] + " has no " + noun + "." : "&f" + a[1] + " &7has the " + noun + " &f" + r.name + " &7(" + r.prefix + "&7).");
                break;
            }
            default:
                msg(sender, "&cUnknown subcommand. Use /" + noun + " for help.");
        }
    }

    // ============================================================ /title

    private void title(CommandSender sender, String[] a) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        AchievementManager am = plugin.achievements();
        if (a.length == 0) {
            am.sendList(p);
            return;
        }
        if (a[0].equalsIgnoreCase("off") || a[0].equalsIgnoreCase("hide")) {
            am.hide(p);
            msg(p, "&aYour title is hidden. Show one again with /title <name>.");
            return;
        }
        if (!am.select(p, a[0])) {
            msg(p, "&cYou haven't earned a title in &f" + a[0] + "&c. Type /title to see yours.");
            return;
        }
        msg(p, "&aTitle shown: " + am.chatText(p.getUniqueId()));
    }

    // ============================================================ /pay

    private void pay(CommandSender sender, String[] a) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("pay.enabled", true)) {
            msg(p, "&cPaying other players is turned off.");
            return;
        }
        if (a.length < 2) {
            msg(p, "&cUsage: /pay <player> <amount>");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[0]);
        if (target == null) {
            msg(p, "&cThat player isn't online.");
            return;
        }
        if (target.equals(p)) {
            msg(p, "&cYou can't pay yourself.");
            return;
        }
        long min = Math.max(1L, plugin.getConfig().getLong("pay.min-amount", 1L));
        Integer amount = Util.parseInt(a[1]);
        if (amount == null || amount < min) {
            msg(p, "&cThe amount must be a whole number of at least " + min + ".");
            return;
        }
        long cooldown = (long) (plugin.getConfig().getDouble("pay.cooldown-seconds", 3.0) * 1000.0);
        long now = System.currentTimeMillis();
        Long last = payCooldown.get(p.getUniqueId());
        if (last != null && now - last < cooldown) {
            msg(p, "&cSlow down - you can pay again in &f" + String.format("%.1f", (cooldown - (now - last)) / 1000.0) + "s&c.");
            return;
        }
        PlayerStats from = plugin.stats().get(p);
        if (from.coins < amount) {
            msg(p, "&cYou only have &6" + Util.fmt(from.coins) + " &ccoins.");
            return;
        }
        PlayerStats to = plugin.stats().get(target);
        from.coins -= amount;
        to.coins += amount;
        payCooldown.put(p.getUniqueId(), now);
        plugin.board().update(p);
        plugin.board().update(target);
        msg(p, "&aYou paid &f" + target.getName() + " &6" + Util.fmt(amount) + " coins&a.");
        msg(target, "&f" + p.getName() + " &apaid you &6" + Util.fmt(amount) + " coins&a.");
        plugin.getLogger().info(p.getName() + " paid " + target.getName() + " " + amount + " coins");
    }

    // ============================================================ /resetstats

    private void resetStats(CommandSender sender, String[] a) {
        if (!admin(sender)) {
            return;
        }
        if (a.length == 0) {
            msg(sender, "&7/resetstats <player> [stats|coins|all]");
            msg(sender, "&7/resetstats all [stats|coins|all] confirm   &8(wipes EVERY player)");
            msg(sender, "&7stats = kills, deaths, streaks, assists   coins = coin balance   all = both (default)");
            return;
        }
        boolean confirm = false;
        String what = "all";
        for (int i = 1; i < a.length; i++) {
            String arg = a[i].toLowerCase();
            if (arg.equals("confirm")) {
                confirm = true;
            } else if (arg.equals("stats") || arg.equals("coins") || arg.equals("all")) {
                what = arg;
            } else {
                msg(sender, "&cUnknown option &f" + a[i] + "&c. Use stats, coins, all or confirm.");
                return;
            }
        }
        boolean doStats = what.equals("stats") || what.equals("all");
        boolean doCoins = what.equals("coins") || what.equals("all");

        if (a[0].equalsIgnoreCase("all")) {
            if (!confirm) {
                msg(sender, "&cThis wipes &lEVERY &cplayer's " + what + ". If you're sure, run: &f/resetstats all " + what + " confirm");
                return;
            }
            int n = 0;
            for (PlayerStats ps : plugin.stats().all()) {
                plugin.stats().reset(ps, doStats, doCoins);
                n++;
            }
            plugin.stats().save();
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.achievements().sync(online);
                plugin.board().update(online);
            }
            plugin.nameTags().refreshAll();
            plugin.achievements().save();
            msg(sender, "&aReset " + what + " for &f" + n + " &aplayers.");
            plugin.getLogger().info(sender.getName() + " reset " + what + " for ALL players (" + n + ")");
            return;
        }

        PlayerStats ps = plugin.stats().byName(a[0]);
        if (ps == null) {
            msg(sender, "&cUnknown player. They must have joined the server at least once.");
            return;
        }
        plugin.stats().reset(ps, doStats, doCoins);
        plugin.stats().save();
        Player online = Bukkit.getPlayerExact(a[0]);
        if (online != null) {
            plugin.achievements().sync(online);
            plugin.board().update(online);
            plugin.nameTags().refreshTarget(online);
        }
        msg(sender, "&aReset " + what + " for &f" + ps.name + "&a.");
        plugin.getLogger().info(sender.getName() + " reset " + what + " for " + ps.name);
    }

    // ============================================================ /launchpad

    private void launchpad(CommandSender sender, String[] a) {
        if (!admin(sender)) {
            return;
        }
        Player p = player(sender);
        if (p == null) {
            return;
        }
        LaunchPadManager lm = plugin.pads();
        if (a.length == 0) {
            msg(p, "&7/launchpad create <name>   (stand on a " + lm.padMaterial().name().toLowerCase().replace('_', ' ') + ")");
            msg(p, "&7/launchpad setlanding <name>   (stand where players should land)");
            msg(p, "&7/launchpad delete <name> | list | info <name>");
            msg(p, "&7/launchpad arc <name> <blocks|default>   (0 = dead straight line, higher = curved path)");
            return;
        }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "create": {
                if (a.length < 2 || !validName(a[1])) {
                    msg(p, "&cUsage: /launchpad create <name>");
                    break;
                }
                if (lm.get(a[1]) != null) {
                    msg(p, "&cA launch pad called &f" + a[1] + " &calready exists.");
                    break;
                }
                Block below = p.getLocation().subtract(0, 0.5, 0).getBlock();
                if (below.getType() != lm.padMaterial()) {
                    msg(p, "&cYou must stand on a &f" + lm.padMaterial().name().toLowerCase().replace('_', ' ') + " &cto make a launch pad.");
                    break;
                }
                if (lm.existsAt(below)) {
                    msg(p, "&cThat block is already a launch pad.");
                    break;
                }
                lm.create(a[1], below);
                msg(p, "&aLaunch pad &f" + a[1] + " &acreated. Now stand at the landing spot and run &f/launchpad setlanding " + a[1]);
                break;
            }
            case "setlanding": {
                LaunchPadManager.Pad pad = a.length > 1 ? lm.get(a[1]) : null;
                if (pad == null) {
                    msg(p, "&cUsage: /launchpad setlanding <name>");
                    break;
                }
                pad.landing = p.getLocation();
                lm.save();
                msg(p, "&aLanding point for &f" + pad.name + " &aset to your position.");
                break;
            }
            case "delete": {
                if (a.length < 2) {
                    msg(p, "&cUsage: /launchpad delete <name>");
                    break;
                }
                msg(p, lm.delete(a[1]) ? "&aLaunch pad deleted (the block itself is untouched)." : "&cNo such launch pad.");
                break;
            }
            case "list": {
                if (lm.names().isEmpty()) {
                    msg(p, "&7No launch pads yet.");
                } else {
                    msg(p, "&7Launch pads: &f" + String.join("&7, &f", lm.names()));
                }
                break;
            }
            case "info": {
                LaunchPadManager.Pad pad = a.length > 1 ? lm.get(a[1]) : null;
                if (pad == null) {
                    msg(p, "&cUsage: /launchpad info <name>");
                    break;
                }
                msg(p, "&6" + pad.name + " &7at &f" + pad.x + "," + pad.y + "," + pad.z + " &7(" + pad.world + "), arc &f" + lm.effectiveArc(pad)
                        + (pad.arc < 0 ? " &7(default)" : ""));
                if (pad.landing == null) {
                    msg(p, "&cLanding point not set.");
                } else {
                    msg(p, "&7Lands at &f" + pad.landing.getBlockX() + "," + pad.landing.getBlockY() + "," + pad.landing.getBlockZ());
                }
                break;
            }
            case "arc": {
                LaunchPadManager.Pad pad = a.length > 1 ? lm.get(a[1]) : null;
                if (pad == null || a.length < 3) {
                    msg(p, "&cUsage: /launchpad arc <name> <blocks|default>   (0 = straight line)");
                    break;
                }
                if (a[2].equalsIgnoreCase("default")) {
                    pad.arc = -1;
                    lm.save();
                    msg(p, "&aPad &f" + pad.name + " &anow uses the config default arc (&f" + lm.effectiveArc(pad) + "&a).");
                    break;
                }
                Double blocks = Util.parseDouble(a[2]);
                if (blocks == null || blocks < 0 || blocks > 50) {
                    msg(p, "&cArc must be 0-50 blocks (0 = straight line), or 'default'.");
                    break;
                }
                pad.arc = blocks;
                lm.save();
                msg(p, "&aPad &f" + pad.name + " &anow rises &f" + blocks + " &ablocks at the middle of the flight"
                        + (blocks == 0 ? " &7(straight line)" : "") + "&a.");
                break;
            }
            default:
                msg(p, "&cUnknown subcommand. Use /launchpad for help.");
        }
    }

    // ============================================================ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] a) {
        List<String> opts = new ArrayList<String>();
        String n = cmd.getName().toLowerCase();
        if (a.length == 1) {
            switch (n) {
                case "region":
                    opts = Arrays.asList("wand", "pos1", "pos2", "create", "delete", "list", "info", "flag", "priority", "decay");
                    break;
                case "mine":
                    opts = Arrays.asList("create", "delete", "list", "info", "fill", "block", "regen");
                    break;
                case "npc":
                case "dummy":
                    opts = Arrays.asList("create", "remove");
                    break;
                case "rank":
                case "clan":
                    opts = Arrays.asList("create", "setprefix", "delete", "list", "give", "take", "check");
                    break;
                case "resetstats":
                    opts = Arrays.asList("all");
                    break;
                case "title":
                    if (sender instanceof Player) {
                        opts = new ArrayList<String>(plugin.achievements().earnedTracks((Player) sender));
                        opts.add("off");
                    }
                    break;
                case "launchpad":
                    opts = Arrays.asList("create", "setlanding", "delete", "list", "info", "arc");
                    break;
                case "top":
                    opts = TOP_TYPES;
                    break;
                case "boxpvp":
                    opts = Arrays.asList("reload", "help");
                    break;
                default:
                    break;
            }
        } else if (a.length == 2) {
            String sub = a[0].toLowerCase();
            if (n.equals("region") && !sub.equals("create")) {
                opts = plugin.regions().names();
            } else if (n.equals("mine") && !sub.equals("create")) {
                opts = plugin.mines().names();
            } else if (n.equals("launchpad") && !sub.equals("create")) {
                opts = plugin.pads().names();
            } else if (n.equals("npc") && sub.equals("create")) {
                opts = Arrays.asList("seller", "buyer", "upgrader", "enchanter");
            } else if ((n.equals("rank") || n.equals("clan")) && (sub.equals("setprefix") || sub.equals("delete"))) {
                opts = n.equals("rank") ? plugin.ranks().names() : plugin.clans().names();
            } else if (n.equals("resetstats")) {
                opts = Arrays.asList("stats", "coins", "all", "confirm");
            }
        } else if (a.length == 3 && (n.equals("rank") || n.equals("clan"))
                && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("set"))) {
            opts = n.equals("rank") ? plugin.ranks().names() : plugin.clans().names();
        } else if (a.length == 3 && n.equals("resetstats")) {
            opts = Arrays.asList("stats", "coins", "all", "confirm");
        } else if (a.length == 3 && n.equals("region") && a[0].equalsIgnoreCase("flag")) {
            opts = RegionManager.FLAGS;
        } else if (a.length == 4 && n.equals("region") && a[0].equalsIgnoreCase("flag")) {
            opts = Arrays.asList("allow", "deny", "none");
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        List<String> out = new ArrayList<String>();
        for (String o : opts) {
            if (o.toLowerCase().startsWith(last)) {
                out.add(o);
            }
        }
        return out;
    }
}

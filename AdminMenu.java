package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * /boxpvp menu - the admin overview. Lists every region, mine, launch pad, NPC and dummy with its coordinates,
 * lets you teleport to them, and lets you change region flags / priority / decay with a click.
 */
public class AdminMenu implements Listener {

    private enum View {
        HUB, REGIONS, MINES, PADS, NPCS, FLAGS
    }

    private static class Entry {
        final ItemStack icon;
        final Location left;
        final Location right;
        final String rightRegion;

        Entry(ItemStack icon, Location left, Location right, String rightRegion) {
            this.icon = icon;
            this.left = left;
            this.right = right;
            this.rightRegion = rightRegion;
        }
    }

    private static class Holder implements InventoryHolder {
        View view = View.HUB;
        int page = 0;
        String region;
        String[] act = new String[0];
        List<Entry> entries = new ArrayList<Entry>();

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final int PAGE_SIZE = 45;
    private static final int[] DECAY_PRESETS = {0, 10, 20, 30, 60, 120};

    private final BoxPvP plugin;

    public AdminMenu(BoxPvP plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ opening

    public void open(Player p) {
        show(p, View.HUB, 0, null);
    }

    private void showLater(final Player p, final View view, final int page, final String region) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    show(p, view, page, region);
                }
            }
        });
    }

    private void show(Player p, View view, int page, String region) {
        Holder h = new Holder();
        h.view = view;
        h.page = page;
        h.region = region;
        boolean small = view == View.HUB || view == View.FLAGS;
        String title = view == View.HUB ? "&8Admin menu" : view == View.FLAGS ? "&8Region: &f" + region : "&8Admin: &f" + cap(view.name().toLowerCase());
        title = Util.color(title);
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        Inventory inv = Bukkit.createInventory(h, small ? 27 : 54, title);
        render(inv, h, p);
        p.openInventory(inv);
    }

    private String cap(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ------------------------------------------------------------ drawing

    private void render(Inventory inv, Holder h, Player p) {
        h.act = new String[inv.getSize()];
        ItemStack pane = pane();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
        if (h.view == View.HUB) {
            renderHub(inv, h);
        } else if (h.view == View.FLAGS) {
            renderFlags(inv, h);
        } else {
            renderList(inv, h);
        }
    }

    private void renderHub(Inventory inv, Holder h) {
        inv.setItem(10, icon(Material.PAPER, "&e&lRegions", "&7Zones with rules (PvP, building, decay...)",
                "&7Total: &f" + plugin.regions().all().size(), "", "&eClick to view"));
        h.act[10] = "view:REGIONS";
        inv.setItem(11, icon(Material.IRON_PICKAXE, "&e&lMines", "&7Regenerating mines",
                "&7Total: &f" + plugin.mines().all().size(), "", "&eClick to view"));
        h.act[11] = "view:MINES";
        inv.setItem(12, icon(Material.GOLD_BLOCK, "&e&lLaunch pads", "&7Pads and where they land",
                "&7Total: &f" + plugin.pads().all().size(), "", "&eClick to view"));
        h.act[12] = "view:PADS";
        inv.setItem(13, icon(Material.NAME_TAG, "&e&lNPCs & dummies", "&7Shop, upgrader and enchanter NPCs",
                "&7and training dummies.", "&8(only ones in loaded chunks)", "", "&eClick to view"));
        h.act[13] = "view:NPCS";

        Location s = plugin.spawn();
        inv.setItem(14, icon(Material.COMPASS, "&e&lSpawn", s == null ? "&cNot set yet" : "&7World: &f" + s.getWorld().getName(),
                s == null ? "" : "&7At: &f" + coords(s), "", "&eLeft-click: teleport", "&eRight-click: set spawn where you stand"));
        h.act[14] = "spawn";
        inv.setItem(15, icon(Material.REDSTONE, "&e&lReload config", "&7Re-reads config.yml, regions,", "&7mines, shop, pads...", "", "&eClick to reload"));
        h.act[15] = "reload";
        inv.setItem(16, icon(Material.BOOK_AND_QUILL, "&e&lCheck config", "&7Looks for mistakes in the config", "&7and in your zones.", "", "&eClick to run"));
        h.act[16] = "check";
        inv.setItem(22, icon(Material.CHEST, "&e&lBack up now", "&7Copies every data file into", "&7plugins/BoxPvP/backups/.", "", "&eClick to back up"));
        h.act[22] = "backup";
    }

    private void renderList(Inventory inv, Holder h) {
        List<Entry> all = entriesFor(h.view);
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        int start = h.page * PAGE_SIZE;
        h.entries = new ArrayList<Entry>();
        for (int i = 0; i < PAGE_SIZE && start + i < all.size(); i++) {
            Entry en = all.get(start + i);
            h.entries.add(en);
            inv.setItem(i, en.icon);
        }
        if (all.isEmpty()) {
            inv.setItem(22, icon(Material.BARRIER, "&cNothing here yet"));
        }
        inv.setItem(49, icon(Material.ARROW, "&e<< Back to menu"));
        h.act[49] = "view:HUB";
        if (h.page > 0) {
            inv.setItem(45, icon(Material.ARROW, "&ePrevious page"));
            h.act[45] = "page:-1";
        }
        if (h.page < pages - 1) {
            inv.setItem(53, icon(Material.ARROW, "&eNext page"));
            h.act[53] = "page:1";
        }
        inv.setItem(50, icon(Material.PAPER, "&fPage " + (h.page + 1) + "/" + pages, "&7" + all.size() + " total"));
    }

    private List<Entry> entriesFor(View view) {
        List<Entry> out = new ArrayList<Entry>();
        if (view == View.REGIONS) {
            List<Region> list = new ArrayList<Region>(plugin.regions().all());
            Collections.sort(list, new Comparator<Region>() {
                @Override
                public int compare(Region a, Region b) {
                    return a.name.compareToIgnoreCase(b.name);
                }
            });
            for (Region r : list) {
                out.add(regionEntry(r));
            }
        } else if (view == View.MINES) {
            List<Mine> list = new ArrayList<Mine>(plugin.mines().all());
            Collections.sort(list, new Comparator<Mine>() {
                @Override
                public int compare(Mine a, Mine b) {
                    return a.name.compareToIgnoreCase(b.name);
                }
            });
            for (Mine m : list) {
                out.add(mineEntry(m));
            }
        } else if (view == View.PADS) {
            List<LaunchPadManager.Pad> list = new ArrayList<LaunchPadManager.Pad>(plugin.pads().all());
            Collections.sort(list, new Comparator<LaunchPadManager.Pad>() {
                @Override
                public int compare(LaunchPadManager.Pad a, LaunchPadManager.Pad b) {
                    return a.name.compareToIgnoreCase(b.name);
                }
            });
            for (LaunchPadManager.Pad pad : list) {
                out.add(padEntry(pad));
            }
        } else if (view == View.NPCS) {
            out.addAll(npcEntries());
        }
        return out;
    }

    private List<String> regionInfo(Region r) {
        List<String> lore = new ArrayList<String>();
        lore.add("&7World: &f" + r.world);
        lore.add("&7From: &f" + r.minX + ", " + r.minY + ", " + r.minZ);
        lore.add("&7To: &f" + r.maxX + ", " + r.maxY + ", " + r.maxZ);
        lore.add("&7Size: &f" + (r.maxX - r.minX + 1) + " x " + (r.maxY - r.minY + 1) + " x " + (r.maxZ - r.minZ + 1));
        lore.add("&7Priority: &f" + r.priority);
        if (r.decaySeconds > 0) {
            lore.add("&7Block decay: &f" + r.decaySeconds + "s");
        }
        boolean any = false;
        for (String flag : RegionManager.FLAGS) {
            Boolean b = r.flags.get(flag);
            if (b != null) {
                lore.add((b ? "&a" : "&c") + "  " + flag + ": " + (b ? "allow" : "deny"));
                any = true;
            }
        }
        if (!any) {
            lore.add("&7No flags set (defaults apply)");
        }
        return lore;
    }

    private Entry regionEntry(Region r) {
        List<String> lore = regionInfo(r);
        lore.add("");
        lore.add("&eLeft-click: teleport");
        lore.add("&eRight-click: edit flags / priority / decay");
        World w = Bukkit.getWorld(r.world);
        Location tp = w == null ? null
                : new Location(w, (r.minX + r.maxX + 1) / 2.0, r.minY + 1, (r.minZ + r.maxZ + 1) / 2.0);
        return new Entry(iconList(Material.PAPER, "&e" + r.name, lore), tp, null, r.name);
    }

    private Entry mineEntry(Mine m) {
        List<String> lore = new ArrayList<String>();
        lore.add("&7World: &f" + m.world);
        lore.add("&7From: &f" + m.minX + ", " + m.minY + ", " + m.minZ);
        lore.add("&7To: &f" + m.maxX + ", " + m.maxY + ", " + m.maxZ);
        lore.add("&7Blocks: &f" + Util.fmt(m.volume()));
        lore.add("&7Regen: &f" + (m.regenSeconds > 0 ? m.regenSeconds + "s" : "default (" + plugin.getConfig().getInt("mines.regen-seconds", 8) + "s)"));
        lore.add(m.weights.isEmpty() ? "&7Block mix: &fdefault" : "&7Block mix: &fcustom");
        int shown = 0;
        for (Map.Entry<Material, Integer> en : plugin.mines().effectiveWeights(m).entrySet()) {
            if (shown++ >= 6) {
                lore.add("&8  ...");
                break;
            }
            lore.add("&8  " + en.getKey().name().toLowerCase() + " x" + en.getValue());
        }
        lore.add("");
        lore.add("&eLeft-click: teleport above it");
        World w = Bukkit.getWorld(m.world);
        Location tp = w == null ? null
                : new Location(w, (m.minX + m.maxX + 1) / 2.0, m.maxY + 2, (m.minZ + m.maxZ + 1) / 2.0);
        return new Entry(iconList(Material.IRON_PICKAXE, "&e" + m.name, lore), tp, null, null);
    }

    private Entry padEntry(LaunchPadManager.Pad pad) {
        List<String> lore = new ArrayList<String>();
        lore.add("&7World: &f" + pad.world);
        lore.add("&7Pad block: &f" + pad.x + ", " + pad.y + ", " + pad.z);
        if (pad.landing == null) {
            lore.add("&cLanding point not set!");
        } else {
            lore.add("&7Lands at: &f" + coords(pad.landing));
        }
        lore.add("&7Arc: &f" + plugin.pads().effectiveArc(pad) + (pad.arc < 0 ? " &8(default)" : ""));
        World w = Bukkit.getWorld(pad.world);
        if (w != null && w.getBlockAt(pad.x, pad.y, pad.z).getType() != plugin.pads().padMaterial()) {
            lore.add("&cThe pad block is missing - pad is disabled!");
        }
        lore.add("");
        lore.add("&eLeft-click: teleport to the pad");
        lore.add("&eRight-click: teleport to the landing point");
        Location tp = w == null ? null : new Location(w, pad.x + 0.5, pad.y + 1, pad.z + 0.5);
        return new Entry(iconList(Material.GOLD_BLOCK, "&e" + pad.name, lore), tp, pad.landing, null);
    }

    private List<Entry> npcEntries() {
        List<Entry> out = new ArrayList<Entry>();
        for (World w : Bukkit.getWorlds()) {
            for (Villager v : w.getEntitiesByClass(Villager.class)) {
                String type = plugin.shop().npcType(v);
                if (type == null) {
                    continue;
                }
                Location l = v.getLocation();
                out.add(new Entry(icon(Material.NAME_TAG, "&e" + cap(type) + " NPC", "&7World: &f" + w.getName(),
                        "&7At: &f" + coords(l), "", "&eLeft-click: teleport next to it"), l.clone().add(1.5, 0, 0), null, null));
            }
            for (ArmorStand a : w.getEntitiesByClass(ArmorStand.class)) {
                // A dummy is two stands; the named one (the pumpkin head) represents it.
                if (!plugin.dummies().isDummy(a) || a.getCustomName() == null) {
                    continue;
                }
                Location l = a.getLocation();
                out.add(new Entry(icon(Material.ARMOR_STAND, "&eTraining dummy", "&7World: &f" + w.getName(),
                        "&7Near: &f" + coords(l), "", "&eLeft-click: teleport next to it"), l.clone().add(1.5, 1.75, 0), null, null));
            }
        }
        return out;
    }

    private void renderFlags(Inventory inv, Holder h) {
        Region r = plugin.regions().get(h.region == null ? "" : h.region);
        inv.setItem(18, icon(Material.ARROW, "&e<< Back to regions"));
        h.act[18] = "view:REGIONS";
        if (r == null) {
            inv.setItem(13, icon(Material.BARRIER, "&cThat region no longer exists"));
            return;
        }
        inv.setItem(4, iconList(Material.PAPER, "&e" + r.name, regionInfo(r)));

        Material[] icons = {Material.DIAMOND_SWORD, Material.BRICK, Material.FEATHER, Material.ROTTEN_FLESH,
                Material.DROPPER, Material.EXP_BOTTLE, Material.COOKED_BEEF};
        for (int i = 0; i < RegionManager.FLAGS.size() && i < 7; i++) {
            String flag = RegionManager.FLAGS.get(i);
            Boolean b = r.flags.get(flag);
            boolean def = plugin.getConfig().getBoolean("default-flags." + flag);
            String state = b == null ? "&7not set &8(default: " + (def ? "allow" : "deny") + ")" : b ? "&aALLOW" : "&cDENY";
            String color = b == null ? "&7" : b ? "&a" : "&c";
            inv.setItem(10 + i, icon(icons[i], color + flag, "&7State: " + state, "",
                    "&eClick to cycle: not set -> allow -> deny"));
            h.act[10 + i] = "flag:" + flag;
        }
        inv.setItem(21, icon(Material.NETHER_STAR, "&6Priority: &f" + r.priority, "&7Higher wins where regions overlap.", "",
                "&eLeft-click: +1   Right-click: -1", "&eHold shift for 5 at a time"));
        h.act[21] = "prio";
        inv.setItem(23, icon(Material.WATCH, "&6Block decay: &f" + (r.decaySeconds > 0 ? r.decaySeconds + "s" : "off"),
                "&7Blocks players place here vanish", "&7after this many seconds.", "",
                "&eLeft-click: next preset (off,10,20,30,60,120)", "&eRight-click: turn off"));
        h.act[23] = "decay";
        inv.setItem(25, icon(Material.COMPASS, "&6Teleport here", "&7Takes you to the middle of the region."));
        h.act[25] = "tp:region";
    }

    // ------------------------------------------------------------ items

    private String coords(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    @SuppressWarnings("deprecation")
    private ItemStack pane() {
        ItemStack it = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(" ");
        it.setItemMeta(m);
        return it;
    }

    private ItemStack icon(Material mat, String name, String... lore) {
        return iconList(mat, name, Arrays.asList(lore));
    }

    private ItemStack iconList(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Util.color(name));
        List<String> l = new ArrayList<String>();
        for (String s : lore) {
            l.add(Util.color(s));
        }
        m.setLore(l);
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------ clicks

    private void teleport(final Player p, final Location loc) {
        if (loc == null || loc.getWorld() == null) {
            p.sendMessage(Util.color("&cThat location isn't available (is its world loaded?)."));
            return;
        }
        p.closeInventory();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                p.teleport(loc);
                p.sendMessage(Util.color("&aTeleported to &f" + coords(loc) + "&a."));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        if (!p.hasPermission("boxpvp.admin")) {
            p.closeInventory();
            return;
        }
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        Holder h = (Holder) top.getHolder();
        String action = h.act[slot];

        if (action == null) {
            // A list entry?
            if (h.view != View.HUB && h.view != View.FLAGS && slot < h.entries.size()) {
                Entry en = h.entries.get(slot);
                if (e.isRightClick() && en.rightRegion != null) {
                    showLater(p, View.FLAGS, 0, en.rightRegion);
                } else if (e.isRightClick() && en.right != null) {
                    teleport(p, en.right);
                } else if (!e.isRightClick()) {
                    teleport(p, en.left);
                }
            }
            return;
        }

        if (action.startsWith("view:")) {
            showLater(p, View.valueOf(action.substring(5)), 0, null);
            return;
        }
        if (action.startsWith("page:")) {
            h.page += Integer.parseInt(action.substring(5));
        } else if (action.equals("spawn")) {
            if (e.isRightClick()) {
                plugin.setSpawn(p.getLocation());
                p.sendMessage(Util.color("&aSpawn set to where you're standing."));
            } else if (plugin.spawn() == null) {
                p.sendMessage(Util.color("&cNo spawn set yet - right-click to set it here."));
            } else {
                teleport(p, plugin.spawn());
                return;
            }
        } else if (action.equals("reload")) {
            plugin.reloadAll();
            p.sendMessage(Util.color("&aReloaded config, regions, mines, shop and launch pads."));
        } else if (action.equals("check")) {
            p.closeInventory();
            List<String> problems = new ConfigChecker(plugin).run();
            if (problems.isEmpty()) {
                p.sendMessage(Util.color("&aConfig check: no problems found."));
            } else {
                p.sendMessage(Util.color("&eConfig check found &f" + problems.size() + " &eproblem(s):"));
                for (String s : problems) {
                    p.sendMessage(Util.color("&c- " + s));
                }
            }
            return;
        } else if (action.equals("backup")) {
            String name = plugin.backups().backupNow();
            p.sendMessage(Util.color(name == null ? "&cBackup failed - see the server console."
                    : "&aBackup saved: &fplugins/BoxPvP/backups/" + name));
        } else {
            Region r = plugin.regions().get(h.region == null ? "" : h.region);
            if (r == null) {
                return;
            }
            if (action.startsWith("flag:")) {
                String flag = action.substring(5);
                Boolean b = r.flags.get(flag);
                if (b == null) {
                    r.flags.put(flag, Boolean.TRUE);
                } else if (b) {
                    r.flags.put(flag, Boolean.FALSE);
                } else {
                    r.flags.remove(flag);
                }
                plugin.regions().save();
            } else if (action.equals("prio")) {
                int delta = e.isShiftClick() ? 5 : 1;
                r.priority += e.isRightClick() ? -delta : delta;
                plugin.regions().save();
            } else if (action.equals("decay")) {
                int next = DECAY_PRESETS[0];
                if (!e.isRightClick()) {
                    for (int v : DECAY_PRESETS) {
                        if (v > r.decaySeconds) {
                            next = v;
                            break;
                        }
                    }
                }
                r.decaySeconds = next;
                plugin.regions().save();
            } else if (action.equals("tp:region")) {
                World w = Bukkit.getWorld(r.world);
                teleport(p, w == null ? null
                        : new Location(w, (r.minX + r.maxX + 1) / 2.0, r.minY + 1, (r.minZ + r.maxZ + 1) / 2.0));
                return;
            }
        }
        render(top, h, p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}

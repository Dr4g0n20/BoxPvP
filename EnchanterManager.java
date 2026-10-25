package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
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
import java.util.List;
import java.util.Random;

/**
 * The Enchanter NPC: spend coins to pull custom enchants into your collection.
 *   random pull  - random tool, random enchant          (pull-prices.random)
 *   choose tool  - you pick the tool, enchant is random (pull-prices.tool)
 *   choose       - you pick the exact enchant           (pull-prices.choose)
 * Every pull gives the enchant at level 1. Apply it to a tool at the Upgrader.
 */
public class EnchanterManager implements Listener {

    private enum View {
        MAIN, TOOL, CHOOSE_TOOL, LIST
    }

    private static class Holder implements InventoryHolder {
        View view = View.MAIN;
        ToolKind kind;
        String[] act = new String[0];

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final BoxPvP plugin;
    private final Random rnd = new Random();

    public EnchanterManager(BoxPvP plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ opening / drawing

    public void open(Player p) {
        show(p, View.MAIN, null);
    }

    private void showLater(final Player p, final View view, final ToolKind kind) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    show(p, view, kind);
                }
            }
        });
    }

    private void show(Player p, View view, ToolKind kind) {
        String title = Util.color(plugin.getConfig().getString("enchanter.title", "&d&lEnchanter"));
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        Holder h = new Holder();
        h.view = view;
        h.kind = kind;
        Inventory inv = Bukkit.createInventory(h, 27, title);
        render(inv, h, p);
        p.openInventory(inv);
    }

    private long price(String which) {
        return plugin.enchantSettings().pullPrice(which);
    }

    private void render(Inventory inv, Holder h, Player p) {
        h.act = new String[inv.getSize()];
        ItemStack pane = pane();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
        PlayerStats stats = plugin.stats().get(p);

        if (h.view == View.MAIN) {
            inv.setItem(2, icon(Material.ENCHANTED_BOOK, "&d&lRandom Pull",
                    "&7A random enchant for a random tool.", "", "&7Cost: &6" + Util.fmt(price("random")) + " coins"));
            inv.setItem(11, buyButton(price("random"), stats, "PULL"));
            h.act[11] = "pull:random";

            inv.setItem(4, icon(Material.COMPASS, "&b&lChoose the Tool",
                    "&7Pick pickaxe, sword or axe;", "&7the enchant is random.", "", "&7Cost: &6" + Util.fmt(price("tool")) + " coins"));
            inv.setItem(13, openButton("CHOOSE TOOL"));
            h.act[13] = "view:tool";

            inv.setItem(6, icon(Material.NETHER_STAR, "&6&lChoose the Enchant",
                    "&7Pick the tool and the exact", "&7enchant you want.", "", "&7Cost: &6" + Util.fmt(price("choose")) + " coins"));
            inv.setItem(15, openButton("CHOOSE ENCHANT"));
            h.act[15] = "view:choose";
        } else if (h.view == View.TOOL || h.view == View.CHOOSE_TOOL) {
            boolean pulling = h.view == View.TOOL;
            ToolKind[] kinds = ToolKind.values();
            Material[] icons = {Material.DIAMOND_PICKAXE, Material.DIAMOND_SWORD, Material.DIAMOND_AXE};
            int[] topSlots = {2, 4, 6};
            int[] btnSlots = {11, 13, 15};
            for (int i = 0; i < kinds.length; i++) {
                ToolKind k = kinds[i];
                List<String> names = new ArrayList<String>();
                for (CustomEnchant ce : CustomEnchant.forKind(k)) {
                    names.add("&7- &f" + ce.name);
                }
                names.add(0, "&7Possible enchants:");
                inv.setItem(topSlots[i], iconList(icons[i], "&d&l" + cap(k.label), names));
                if (pulling) {
                    inv.setItem(btnSlots[i], buyButton(price("tool"), stats, "PULL"));
                    h.act[btnSlots[i]] = "pull:" + k.name();
                } else {
                    inv.setItem(btnSlots[i], openButton("PICK"));
                    h.act[btnSlots[i]] = "list:" + k.name();
                }
            }
        } else { // LIST
            List<CustomEnchant> list = CustomEnchant.forKind(h.kind);
            int n = Math.min(list.size(), 9);
            int offset = (9 - n) / 2;
            for (int i = 0; i < n; i++) {
                CustomEnchant ce = list.get(i);
                int slot = offset + i;
                inv.setItem(slot, icon(Material.ENCHANTED_BOOK, "&d" + ce.name, "&7" + ce.description, "",
                        "&7Max level: &f" + plugin.enchantSettings().max(ce.id), "&7You own: &f" + plugin.enchantStore().count(p.getUniqueId(), ce.id)));
                inv.setItem(9 + slot, buyButton(price("choose"), stats, "BUY"));
                h.act[9 + slot] = "buy:" + ce.id;
            }
        }

        if (h.view != View.MAIN) {
            inv.setItem(18, icon(Material.ARROW, "&e<< Back"));
            h.act[18] = "view:main";
        }
        inv.setItem(21, icon(Material.GOLD_NUGGET, "&6Your coins: &e" + Util.fmt(stats.coins)));
        inv.setItem(23, collectionIcon(p));
    }

    private String cap(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private ItemStack collectionIcon(Player p) {
        List<String> lore = new ArrayList<String>();
        boolean any = false;
        for (CustomEnchant ce : CustomEnchant.ALL) {
            int n = plugin.enchantStore().count(p.getUniqueId(), ce.id);
            if (n > 0) {
                lore.add("&7- &f" + ce.name + " &8(" + ce.kind.label + ") &7x" + n);
                any = true;
            }
        }
        if (!any) {
            lore.add("&7You don't own any yet.");
        }
        lore.add("");
        lore.add("&7Apply them to a tool at the Upgrader.");
        return iconList(Material.BOOK, "&5Your enchants", lore);
    }

    // ------------------------------------------------------------ items

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

    @SuppressWarnings("deprecation")
    private ItemStack clay(int data, String name, String... lore) {
        ItemStack it = new ItemStack(Material.STAINED_CLAY, 1, (short) data);
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

    private ItemStack openButton(String label) {
        return clay(5, "&a&l" + label, "&eClick to continue");
    }

    private ItemStack buyButton(long cost, PlayerStats stats, String label) {
        if (stats.coins >= cost) {
            return clay(5, "&a&l" + label, "&7Cost: &6" + Util.fmt(cost) + " coins", "&eClick to buy");
        }
        return clay(14, "&c&lNOT ENOUGH COINS", "&7Costs &6" + Util.fmt(cost) + "&7, you have &e" + Util.fmt(stats.coins));
    }

    // ------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) {
            return;
        }
        e.setCancelled(true); // nothing can be moved in or out of the menu
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        Holder h = (Holder) top.getHolder();
        String action = h.act[slot];
        if (action == null) {
            return;
        }

        if (action.equals("view:main")) {
            showLater(p, View.MAIN, null);
            return;
        }
        if (action.equals("view:tool")) {
            showLater(p, View.TOOL, null);
            return;
        }
        if (action.equals("view:choose")) {
            showLater(p, View.CHOOSE_TOOL, null);
            return;
        }
        if (action.startsWith("list:")) {
            showLater(p, View.LIST, ToolKind.valueOf(action.substring(5)));
            return;
        }

        if (action.equals("pull:random")) {
            if (charge(p, price("random"))) {
                ToolKind kind = ToolKind.values()[rnd.nextInt(ToolKind.values().length)];
                give(p, randomFor(kind));
            }
        } else if (action.startsWith("pull:")) {
            if (charge(p, price("tool"))) {
                give(p, randomFor(ToolKind.valueOf(action.substring(5))));
            }
        } else if (action.startsWith("buy:")) {
            CustomEnchant ce = CustomEnchant.byId(action.substring(4));
            if (ce != null && charge(p, price("choose"))) {
                give(p, ce);
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

    // ------------------------------------------------------------ buying

    private CustomEnchant randomFor(ToolKind kind) {
        List<CustomEnchant> list = CustomEnchant.forKind(kind);
        return list.get(rnd.nextInt(list.size()));
    }

    private boolean charge(Player p, long cost) {
        if (!plugin.enchantSettings().enabled()) {
            p.sendMessage(Util.color("&cCustom enchants are turned off."));
            return false;
        }
        PlayerStats stats = plugin.stats().get(p);
        if (stats.coins < cost) {
            p.sendMessage(Util.color("&cYou need &6" + Util.fmt(cost) + " coins &c(you have " + Util.fmt(stats.coins) + ")."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        stats.coins -= cost;
        plugin.board().update(p);
        return true;
    }

    private void give(Player p, CustomEnchant ce) {
        plugin.enchantStore().add(p.getUniqueId(), ce.id, 1);
        p.sendMessage(Util.color("&d&lNEW ENCHANT! &7You got &f" + ce.name + " &7(" + ce.kind.label + ") at level 1. Apply it at the Upgrader."));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }
}

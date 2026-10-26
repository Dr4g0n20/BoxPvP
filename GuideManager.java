package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.List;
import java.util.Map;

/** /guide: a menu of topics (all text lives in the config) plus the welcome message new players get on first join. */
public class GuideManager implements Listener {

    private static class Topic {
        final Material icon;
        final String name;
        final List<String> lines;

        Topic(Material icon, String name, List<String> lines) {
            this.icon = icon;
            this.name = name;
            this.lines = lines;
        }
    }

    private static class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final BoxPvP plugin;

    public GuideManager(BoxPvP plugin) {
        this.plugin = plugin;
    }

    private List<Topic> topics() {
        List<Topic> out = new ArrayList<Topic>();
        for (Map<?, ?> m : plugin.getConfig().getMapList("guide.topics")) {
            Material icon = Material.matchMaterial(String.valueOf(m.get("icon")));
            if (icon == null) {
                icon = Material.BOOK;
            }
            String name = m.get("name") == null ? "Topic" : String.valueOf(m.get("name"));
            List<String> lines = new ArrayList<String>();
            if (m.get("lines") instanceof List) {
                for (Object o : (List<?>) m.get("lines")) {
                    lines.add(String.valueOf(o));
                }
            }
            out.add(new Topic(icon, name, lines));
            if (out.size() >= 9) {
                break;
            }
        }
        return out;
    }

    public void open(Player p) {
        List<Topic> topics = topics();
        Inventory inv = Bukkit.createInventory(new Holder(), 27, Util.color("&6&lServer Guide"));
        ItemStack pane = pane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }
        int offset = (9 - topics.size()) / 2;
        for (int i = 0; i < topics.size(); i++) {
            Topic t = topics.get(i);
            ItemStack it = new ItemStack(t.icon);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(Util.color(t.name));
            List<String> lore = new ArrayList<String>();
            for (String line : t.lines) {
                lore.add(Util.color(line));
            }
            lore.add("");
            lore.add(Util.color("&eClick to print this in chat"));
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(9 + offset + i, it);
        }
        p.openInventory(inv);
    }

    /** Title + chat messages for a player's very first join. */
    public void welcome(final Player p) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    return;
                }
                try {
                    p.sendTitle(Util.color(plugin.getConfig().getString("guide.first-join.title", "&6&lBOX PVP")),
                            Util.color(plugin.getConfig().getString("guide.first-join.subtitle", "&7Type &e/guide &7to get started")),
                            10, 70, 20);
                } catch (Throwable ignored) {
                    // titles not supported by this client/server
                }
                for (String line : plugin.getConfig().getStringList("guide.first-join.messages")) {
                    p.sendMessage(Util.color(line));
                }
            }
        }, 30L);
    }

    @SuppressWarnings("deprecation")
    private ItemStack pane() {
        ItemStack it = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(" ");
        it.setItemMeta(m);
        return it;
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
        int slot = e.getRawSlot();
        if (slot < 9 || slot >= 18) {
            return;
        }
        List<Topic> topics = topics();
        int offset = (9 - topics.size()) / 2;
        int idx = slot - 9 - offset;
        if (idx < 0 || idx >= topics.size()) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        Topic t = topics.get(idx);
        p.sendMessage(Util.color("&8&m      &r " + t.name + " &8&m      "));
        for (String line : t.lines) {
            p.sendMessage(Util.color(line));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}

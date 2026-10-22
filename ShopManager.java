package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Seller / buyer NPCs and their chest GUIs. */
public class ShopManager implements Listener {

    public static final String TAG_SELLER = "boxpvp_seller";
    public static final String TAG_BUYER = "boxpvp_buyer";

    private static class SellEntry {
        final ItemKey key;
        final long price;
        final String name;

        SellEntry(ItemKey key, long price, String name) {
            this.key = key;
            this.price = price;
            this.name = name;
        }

        String display() {
            return name != null ? name : key.niceName();
        }
    }

    private static class BuyEntry {
        final ItemStack stack;
        final long price;
        final String name;

        BuyEntry(ItemStack stack, long price, String name) {
            this.stack = stack;
            this.price = price;
            this.name = name;
        }

        String display() {
            return name != null ? name : stack.getType().name().toLowerCase().replace('_', ' ');
        }
    }

    private static class Holder implements InventoryHolder {
        final boolean seller;

        Holder(boolean seller) {
            this.seller = seller;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final BoxPvP plugin;
    private final List<SellEntry> sell = new ArrayList<SellEntry>();
    private final List<BuyEntry> buy = new ArrayList<BuyEntry>();

    public ShopManager(BoxPvP plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ config

    public void load() {
        sell.clear();
        buy.clear();

        for (Map<?, ?> m : plugin.getConfig().getMapList("shop.sell")) {
            ItemKey k = ItemKey.parse(String.valueOf(m.get("item")));
            Object price = m.get("price");
            if (k == null || !(price instanceof Number)) {
                plugin.getLogger().warning("Bad shop.sell entry: " + m);
                continue;
            }
            Object name = m.get("name");
            sell.add(new SellEntry(k, ((Number) price).longValue(), name == null ? null : String.valueOf(name)));
        }

        for (Map<?, ?> m : plugin.getConfig().getMapList("shop.buy")) {
            ItemKey k = ItemKey.parse(String.valueOf(m.get("item")));
            Object price = m.get("price");
            if (k == null || !(price instanceof Number)) {
                plugin.getLogger().warning("Bad shop.buy entry: " + m);
                continue;
            }
            int amount = 1;
            if (m.get("amount") instanceof Number) {
                amount = Math.max(1, ((Number) m.get("amount")).intValue());
            }
            ItemStack stack = k.stack(amount);
            Object potion = m.get("potion");
            if (potion != null) {
                try {
                    PotionMeta pm = (PotionMeta) stack.getItemMeta();
                    boolean upgraded = Boolean.parseBoolean(String.valueOf(m.get("upgraded")));
                    boolean extended = Boolean.parseBoolean(String.valueOf(m.get("extended")));
                    pm.setBasePotionData(new PotionData(
                            PotionType.valueOf(String.valueOf(potion).toUpperCase()), extended, upgraded));
                    stack.setItemMeta(pm);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Bad potion in shop.buy entry: " + m + " (" + ex.getMessage() + ")");
                    continue;
                }
            }
            Object name = m.get("name");
            buy.add(new BuyEntry(stack, ((Number) price).longValue(), name == null ? null : String.valueOf(name)));
        }
    }

    // ------------------------------------------------------------ NPCs

    public String npcType(Entity en) {
        if (!(en instanceof Villager)) {
            return null;
        }
        for (String t : en.getScoreboardTags()) {
            if (t.equals(TAG_SELLER)) {
                return "seller";
            }
            if (t.equals(TAG_BUYER)) {
                return "buyer";
            }
        }
        return null;
    }

    public void spawnNpc(Player p, boolean seller) {
        Location loc = p.getLocation();
        loc.setPitch(0f);
        loc.setYaw(loc.getYaw() + 180f);
        Villager v = loc.getWorld().spawn(loc, Villager.class);
        v.setAI(false);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setCollidable(false);
        v.setRemoveWhenFarAway(false);
        v.setProfession(seller ? Villager.Profession.FARMER : Villager.Profession.BLACKSMITH);
        v.setCustomName(Util.color(plugin.getConfig().getString(
                seller ? "shop.seller-npc-name" : "shop.buyer-npc-name", seller ? "&6&lSeller" : "&b&lBuyer")));
        v.setCustomNameVisible(true);
        v.addScoreboardTag(seller ? TAG_SELLER : TAG_BUYER);
    }

    /** Removes the nearest shop NPC within 6 blocks. Returns false if none found. */
    public boolean removeNearest(Player p) {
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity en : p.getNearbyEntities(6, 6, 6)) {
            if (npcType(en) == null) {
                continue;
            }
            double d = en.getLocation().distanceSquared(p.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = en;
            }
        }
        if (best == null) {
            return false;
        }
        best.remove();
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        String type = npcType(e.getRightClicked());
        if (type == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        open(e.getPlayer(), type.equals("seller"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcDamage(EntityDamageEvent e) {
        if (npcType(e.getEntity()) != null) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ GUI

    public void open(Player p, boolean seller) {
        String title = Util.color(plugin.getConfig().getString(
                seller ? "shop.seller-title" : "shop.buyer-title", seller ? "&6Seller" : "&bBuyer"));
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        Inventory inv = Bukkit.createInventory(new Holder(seller), 54, title);
        render(inv, p, seller);
        p.openInventory(inv);
    }

    private void render(Inventory inv, Player p, boolean seller) {
        inv.clear();
        if (seller) {
            int slot = 0;
            for (SellEntry se : sell) {
                if (slot >= 45) {
                    break;
                }
                inv.setItem(slot++, sellIcon(se, p));
            }
            long total = 0;
            for (SellEntry se : sell) {
                total += count(p, se.key) * se.price;
            }
            inv.setItem(49, icon(Material.EMERALD, "&a&lSell All",
                    "&7Sells every sellable resource", "&7in your inventory.", "",
                    "&7You would receive: &6" + Util.fmt(total) + " coins"));
        } else {
            int slot = 0;
            for (BuyEntry be : buy) {
                if (slot >= 45) {
                    break;
                }
                inv.setItem(slot++, buyIcon(be));
            }
        }
        inv.setItem(45, icon(Material.GOLD_NUGGET, "&6Your coins: &e" + Util.fmt(plugin.stats().get(p).coins)));
    }

    private ItemStack sellIcon(SellEntry se, Player p) {
        ItemStack it = se.key.stack(1);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Util.color("&f" + se.display()));
        m.setLore(Arrays.asList(
                Util.color("&7Sells for: &6" + Util.fmt(se.price) + " coins &7each"),
                Util.color("&7You have: &f" + count(p, se.key)),
                "",
                Util.color("&eClick: &7sell 1"),
                Util.color("&eShift-click: &7sell all")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack buyIcon(BuyEntry be) {
        ItemStack it = be.stack.clone();
        ItemMeta m = it.getItemMeta();
        if (be.name != null) {
            m.setDisplayName(Util.color("&f" + be.name));
        }
        List<String> lore = new ArrayList<String>();
        lore.add(Util.color("&7Price: &6" + Util.fmt(be.price) + " coins"));
        if (be.stack.getAmount() > 1) {
            lore.add(Util.color("&7You get: &f" + be.stack.getAmount()));
        }
        lore.add("");
        lore.add(Util.color("&eClick to buy"));
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack icon(Material mat, String name, String... lore) {
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
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        boolean seller = ((Holder) top.getHolder()).seller;
        if (seller) {
            if (slot == 49) {
                sellAll(p);
            } else if (slot < 45 && slot < sell.size()) {
                sellItem(p, sell.get(slot), e.isShiftClick());
            }
        } else if (slot < 45 && slot < buy.size()) {
            buyItem(p, buy.get(slot));
        }
        render(top, p, seller);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ transactions

    private boolean plain(ItemStack it) {
        return it != null && !it.hasItemMeta();
    }

    private int count(Player p, ItemKey k) {
        int c = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (plain(it) && k.matches(it)) {
                c += it.getAmount();
            }
        }
        return c;
    }

    private void take(Player p, ItemKey k, int amount) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack it = contents[i];
            if (plain(it) && k.matches(it)) {
                int n = Math.min(amount, it.getAmount());
                if (n >= it.getAmount()) {
                    contents[i] = null;
                } else {
                    it.setAmount(it.getAmount() - n);
                }
                amount -= n;
            }
        }
        p.getInventory().setStorageContents(contents);
    }

    private void sellItem(Player p, SellEntry se, boolean all) {
        int have = count(p, se.key);
        if (have <= 0) {
            p.sendMessage(Util.color("&cYou don't have any " + se.display() + "."));
            return;
        }
        int amount = all ? have : 1;
        take(p, se.key, amount);
        long gain = se.price * amount;
        plugin.stats().get(p).coins += gain;
        plugin.board().update(p);
        p.sendMessage(Util.color("&aSold &f" + amount + "x " + se.display() + " &afor &6" + Util.fmt(gain) + " coins&a."));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    private void sellAll(Player p) {
        long gain = 0;
        int items = 0;
        for (SellEntry se : sell) {
            int have = count(p, se.key);
            if (have > 0) {
                take(p, se.key, have);
                gain += have * se.price;
                items += have;
            }
        }
        if (items == 0) {
            p.sendMessage(Util.color("&cYou have nothing to sell."));
            return;
        }
        plugin.stats().get(p).coins += gain;
        plugin.board().update(p);
        p.sendMessage(Util.color("&aSold &f" + items + " &aitems for &6" + Util.fmt(gain) + " coins&a."));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f);
    }

    private boolean canFit(PlayerInventory inv, ItemStack st) {
        int need = st.getAmount();
        for (ItemStack it : inv.getStorageContents()) {
            if (it == null || it.getType() == Material.AIR) {
                need -= st.getMaxStackSize();
            } else if (it.isSimilar(st)) {
                need -= Math.max(0, it.getMaxStackSize() - it.getAmount());
            }
            if (need <= 0) {
                return true;
            }
        }
        return false;
    }

    private void buyItem(Player p, BuyEntry be) {
        PlayerStats s = plugin.stats().get(p);
        if (s.coins < be.price) {
            p.sendMessage(Util.color("&cYou need &6" + Util.fmt(be.price) + " coins &c(you have " + Util.fmt(s.coins) + ")."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (!canFit(p.getInventory(), be.stack)) {
            p.sendMessage(Util.color("&cYour inventory is full."));
            return;
        }
        s.coins -= be.price;
        p.getInventory().addItem(be.stack.clone());
        plugin.board().update(p);
        p.sendMessage(Util.color("&aBought &f" + be.stack.getAmount() + "x " + be.display() + " &afor &6" + Util.fmt(be.price) + " coins&a."));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
    }
}

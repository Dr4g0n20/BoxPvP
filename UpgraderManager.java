package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
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
import java.util.Map;
import java.util.UUID;

/**
 * The Upgrader NPC. Hold a pickaxe, sword or axe and right-click it:
 *   Vanilla Enchants  - pay coins to add Efficiency, Sharpness, ... to the tool in your hand
 *   Custom Enchants   - apply enchants you pulled at the Enchanter, level them up, remove them, and buy more slots
 * The tool never leaves your hand, so there is nothing that can be lost or duplicated in the menu.
 */
public class UpgraderManager implements Listener {

    private enum View {
        MAIN, VANILLA, CUSTOM
    }

    private static class Option {
        final Enchantment enchant;
        final String name;
        final int max;
        final long[] prices;

        Option(Enchantment enchant, String name, int max, long[] prices) {
            this.enchant = enchant;
            this.name = name;
            this.max = max;
            this.prices = prices;
        }

        /** Cost of going from currentLevel to currentLevel + 1. */
        long priceFor(int currentLevel) {
            return prices[Math.min(currentLevel, prices.length - 1)];
        }
    }

    private static class Holder implements InventoryHolder {
        View view = View.MAIN;
        ToolKind kind;
        String[] act = new String[0];
        List<Option> shown = new ArrayList<Option>();

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final BoxPvP plugin;
    private final List<Option> pickaxeOptions = new ArrayList<Option>();
    private final List<Option> swordOptions = new ArrayList<Option>();
    private final List<Option> axeOptions = new ArrayList<Option>();

    public UpgraderManager(BoxPvP plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ config (vanilla enchant lists)

    public void load() {
        pickaxeOptions.clear();
        swordOptions.clear();
        axeOptions.clear();
        parse("upgrader.enchants", pickaxeOptions);        // pickaxes (the original key)
        parse("upgrader.sword-enchants", swordOptions);
        parse("upgrader.axe-enchants", axeOptions);
    }

    private void parse(String path, List<Option> into) {
        for (Map<?, ?> m : plugin.getConfig().getMapList(path)) {
            Enchantment e = Enchantment.getByName(String.valueOf(m.get("enchant")).toUpperCase());
            Object pr = m.get("prices");
            if (e == null || !(pr instanceof List) || ((List<?>) pr).isEmpty()) {
                plugin.getLogger().warning("Bad " + path + " entry: " + m);
                continue;
            }
            List<?> list = (List<?>) pr;
            long[] prices = new long[list.size()];
            boolean ok = true;
            for (int i = 0; i < prices.length; i++) {
                if (list.get(i) instanceof Number) {
                    prices[i] = ((Number) list.get(i)).longValue();
                } else {
                    ok = false;
                }
            }
            if (!ok) {
                plugin.getLogger().warning("Bad prices in " + path + " entry: " + m);
                continue;
            }
            int max = m.get("max") instanceof Number ? ((Number) m.get("max")).intValue() : prices.length;
            String name = m.get("name") == null ? e.getName() : String.valueOf(m.get("name"));
            into.add(new Option(e, name, Math.max(1, max), prices));
        }
        if (into.size() > 9) {
            plugin.getLogger().warning(path + " has more than 9 entries; only the first 9 are shown.");
        }
    }

    private List<Option> optionsFor(ToolKind k) {
        if (k == ToolKind.PICKAXE) {
            return pickaxeOptions;
        }
        if (k == ToolKind.SWORD) {
            return swordOptions;
        }
        if (k == ToolKind.AXE) {
            return axeOptions;
        }
        return new ArrayList<Option>();
    }

    private EnchantSettings cs() {
        return plugin.enchantSettings();
    }

    // ------------------------------------------------------------ opening / drawing

    public void open(Player p) {
        show(p, View.MAIN);
    }

    private void showLater(final Player p, final View view) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    show(p, view);
                }
            }
        });
    }

    private void show(Player p, View view) {
        String title = Util.color(plugin.getConfig().getString("upgrader.title", "&5&lUpgrader"));
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        Holder h = new Holder();
        h.view = view;
        Inventory inv = Bukkit.createInventory(h, view == View.CUSTOM ? 45 : 27, title);
        render(inv, h, p);
        p.openInventory(inv);
    }

    private void render(Inventory inv, Holder h, Player p) {
        h.act = new String[inv.getSize()];
        ItemStack pane = pane();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        ToolKind kind = ToolKind.of(hand);
        h.kind = kind;
        PlayerStats stats = plugin.stats().get(p);

        int controlRow = inv.getSize() - 9;
        if (h.view != View.MAIN) {
            inv.setItem(controlRow, icon(Material.ARROW, "&e<< Back"));
            h.act[controlRow] = "view:main";
        }
        inv.setItem(controlRow + 3, icon(Material.GOLD_NUGGET, "&6Your coins: &e" + Util.fmt(stats.coins)));
        if (kind != null) {
            inv.setItem(controlRow + 4, hand.clone());
        } else {
            inv.setItem(controlRow + 4, icon(Material.BARRIER, "&cNothing to upgrade",
                    "&7Close this menu, hold a pickaxe,", "&7sword or axe in your main hand,", "&7and right-click the Upgrader again."));
        }
        if (kind == null) {
            return;
        }

        if (h.view == View.MAIN) {
            renderMain(inv, h, hand);
        } else if (h.view == View.VANILLA) {
            renderVanilla(inv, h, hand, kind, stats);
        } else {
            renderCustom(inv, h, p, hand, kind, stats);
        }
    }

    private void renderMain(Inventory inv, Holder h, ItemStack hand) {
        inv.setItem(2, icon(Material.ENCHANTED_BOOK, "&d&lVanilla Enchants",
                "&7Efficiency, Unbreaking,", "&7Sharpness, Mending and more."));
        inv.setItem(11, coloured(5, "&a&lOPEN", "&eClick to open"));
        h.act[11] = "view:vanilla";

        if (cs().enabled()) {
            EnchantData.Info info = EnchantData.read(hand, cs().baseSlots());
            List<String> lore = new ArrayList<String>();
            lore.add("&7Slots used: &f" + info.applied.size() + "&7/&f" + info.slots);
            for (EnchantData.Applied a : info.applied) {
                lore.add("&7- &d" + a.enchant.name + " " + EnchantData.roman(a.level));
            }
            lore.add("");
            lore.add("&7Apply enchants from the Enchanter,");
            lore.add("&7level them up and unlock slots.");
            inv.setItem(6, iconList(Material.NETHER_STAR, "&5&lCustom Enchants", lore));
            inv.setItem(15, coloured(5, "&a&lOPEN", "&eClick to open"));
            h.act[15] = "view:custom";
        }
    }

    // ------------------------------------------------------------ vanilla view

    private void renderVanilla(Inventory inv, Holder h, ItemStack hand, ToolKind kind, PlayerStats stats) {
        List<Option> opts = optionsFor(kind);
        h.shown = opts;
        int n = Math.min(opts.size(), 9);
        int offset = (9 - n) / 2;
        for (int i = 0; i < n; i++) {
            Option o = opts.get(i);
            int cur = hand.getEnchantmentLevel(o.enchant);
            int slot = offset + i;
            inv.setItem(slot, icon(Material.ENCHANTED_BOOK, "&d" + o.name, "&7Level: &f" + cur + "&7/&f" + o.max));
            inv.setItem(9 + slot, vanillaButton(o, cur, stats));
            h.act[9 + slot] = "van:" + i;
        }
    }

    private ItemStack vanillaButton(Option o, int cur, PlayerStats stats) {
        if (cur >= o.max) {
            return coloured(8, "&7&lMAX LEVEL", "&7" + o.name + " " + cur + " is the highest.");
        }
        long price = o.priceFor(cur);
        if (stats.coins >= price) {
            return coloured(5, "&a&lUPGRADE", "&7" + o.name + " " + cur + " -> " + (cur + 1),
                    "&7Cost: &6" + Util.fmt(price) + " coins", "&eClick to upgrade");
        }
        return coloured(14, "&c&lNOT ENOUGH COINS", "&7Costs &6" + Util.fmt(price) + "&7, you have &e" + Util.fmt(stats.coins));
    }

    // ------------------------------------------------------------ custom view

    private void renderCustom(Inventory inv, Holder h, Player p, ItemStack hand, ToolKind kind, PlayerStats stats) {
        EnchantData.Info info = EnchantData.read(hand, cs().baseSlots());
        int maxSlots = cs().maxSlots();
        int offset = (9 - maxSlots) / 2;

        // Rows 0-1: the slots on this tool, with a button under each.
        for (int i = 0; i < maxSlots; i++) {
            int top = offset + i;
            int btn = 9 + offset + i;
            if (i < info.applied.size()) {
                EnchantData.Applied a = info.applied.get(i);
                int max = cs().max(a.enchant.id);
                inv.setItem(top, iconList(Material.ENCHANTED_BOOK, "&d" + a.enchant.name + " " + EnchantData.roman(a.level),
                        enchantLore(a.enchant, a.level, max)));
                inv.setItem(btn, upgradeButton(a, max, stats));
                h.act[btn] = "app:" + a.enchant.id;
            } else if (i < info.slots) {
                inv.setItem(top, icon(Material.PAPER, "&fEmpty slot", "&7Apply an enchant from", "&7the row below."));
            } else {
                inv.setItem(top, icon(Material.IRON_FENCE, "&cLocked slot", "&7Unlock it with the button below."));
                if (i == info.slots) {
                    long price = cs().slotPrice(info.slots);
                    inv.setItem(btn, stats.coins >= price
                            ? coloured(5, "&a&lUNLOCK SLOT", "&7Cost: &6" + Util.fmt(price) + " coins", "&eClick to unlock")
                            : coloured(14, "&c&lNOT ENOUGH COINS", "&7Costs &6" + Util.fmt(price) + "&7, you have &e" + Util.fmt(stats.coins)));
                    h.act[btn] = "slot";
                } else {
                    inv.setItem(btn, coloured(8, "&7&lLOCKED", "&7Unlock the earlier slots first."));
                }
            }
        }

        // Rows 2-3: enchants you own for this kind of tool, with APPLY buttons.
        UUID id = p.getUniqueId();
        List<CustomEnchant> owned = new ArrayList<CustomEnchant>();
        for (CustomEnchant ce : CustomEnchant.forKind(kind)) {
            if (plugin.enchantStore().count(id, ce.id) > 0) {
                owned.add(ce);
            }
        }
        if (owned.isEmpty()) {
            inv.setItem(22, icon(Material.BARRIER, "&cNo enchants for this tool",
                    "&7You don't own any " + kind.label + " enchants.", "&7Pull some from the Enchanter."));
        }
        int n = Math.min(owned.size(), 9);
        int off2 = (9 - n) / 2;
        for (int i = 0; i < n; i++) {
            CustomEnchant ce = owned.get(i);
            int slot = 18 + off2 + i;
            int count = plugin.enchantStore().count(id, ce.id);
            inv.setItem(slot, iconList(Material.ENCHANTED_BOOK, "&d" + ce.name + " &7x" + count,
                    enchantLore(ce, 1, cs().max(ce.id))));
            if (info.level(ce.id) > 0) {
                inv.setItem(slot + 9, coloured(14, "&c&lALREADY ON TOOL", "&7A tool can't carry the same", "&7enchant twice."));
            } else if (info.applied.size() >= info.slots) {
                inv.setItem(slot + 9, coloured(14, "&c&lNO FREE SLOT", "&7Unlock another slot above,", "&7or remove an enchant."));
            } else {
                inv.setItem(slot + 9, coloured(5, "&a&lAPPLY", "&7Adds " + ce.name + " I to this " + kind.label,
                        "&7and uses up one copy.", "&eClick to apply"));
            }
            h.act[slot + 9] = "apply:" + ce.id;
        }
    }

    private List<String> enchantLore(CustomEnchant ce, int level, int max) {
        List<String> lore = new ArrayList<String>();
        lore.add("&7" + ce.description);
        lore.add("&f" + plugin.enchantEffects().describe(ce.id, level));
        lore.add("");
        lore.add("&7Level: &f" + level + "&7/&f" + max);
        return lore;
    }

    private ItemStack upgradeButton(EnchantData.Applied a, int max, PlayerStats stats) {
        if (a.level >= max) {
            return coloured(8, "&7&lMAX LEVEL", "&7Right-click: remove (returns it", "&7to your collection at level 1)");
        }
        long price = cs().priceFor(a.enchant.id, a.level);
        if (stats.coins >= price) {
            return coloured(5, "&a&lUPGRADE", "&7" + a.enchant.name + " " + a.level + " -> " + (a.level + 1),
                    "&7Cost: &6" + Util.fmt(price) + " coins", "&eLeft-click: upgrade",
                    "&eRight-click: remove (returns it", "&eto your collection at level 1)");
        }
        return coloured(14, "&c&lNOT ENOUGH COINS", "&7Costs &6" + Util.fmt(price) + "&7, you have &e" + Util.fmt(stats.coins),
                "&eRight-click: remove (returns it", "&eto your collection at level 1)");
    }

    // ------------------------------------------------------------ items

    @SuppressWarnings("deprecation")
    private ItemStack coloured(int data, String name, String... lore) {
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
            showLater(p, View.MAIN);
            return;
        }
        if (action.equals("view:vanilla")) {
            showLater(p, View.VANILLA);
            return;
        }
        if (action.equals("view:custom")) {
            showLater(p, View.CUSTOM);
            return;
        }

        if (action.startsWith("van:")) {
            int idx = Integer.parseInt(action.substring(4));
            if (h.kind != null && idx >= 0 && idx < h.shown.size()) {
                upgradeVanilla(p, h.kind, h.shown.get(idx));
            }
        } else if (action.startsWith("app:")) {
            String id = action.substring(4);
            if (e.isRightClick()) {
                removeCustom(p, id);
            } else {
                upgradeCustom(p, id);
            }
        } else if (action.startsWith("apply:")) {
            applyCustom(p, action.substring(6));
        } else if (action.equals("slot")) {
            unlockSlot(p);
        }
        render(top, h, p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ vanilla upgrade

    private void upgradeVanilla(Player p, ToolKind expected, Option o) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        ToolKind kind = ToolKind.of(hand);
        if (kind == null) {
            p.sendMessage(Util.color("&cHold a pickaxe, sword or axe in your main hand first."));
            return;
        }
        if (kind != expected) {
            p.sendMessage(Util.color("&cThe item in your hand changed - the menu has been refreshed."));
            return;
        }
        int cur = hand.getEnchantmentLevel(o.enchant);
        if (cur >= o.max) {
            p.sendMessage(Util.color("&cYour " + kind.label + " already has " + o.name + " at the maximum level."));
            return;
        }
        long price = o.priceFor(cur);
        PlayerStats stats = plugin.stats().get(p);
        if (stats.coins < price) {
            notEnough(p, price, stats);
            return;
        }
        stats.coins -= price;
        hand.addUnsafeEnchantment(o.enchant, cur + 1);
        p.getInventory().setItemInMainHand(hand);
        plugin.board().update(p);
        p.sendMessage(Util.color("&aUpgraded your " + kind.label + ": &d" + o.name + " " + (cur + 1) + " &afor &6" + Util.fmt(price) + " coins&a."));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
    }

    private void notEnough(Player p, long price, PlayerStats stats) {
        p.sendMessage(Util.color("&cYou need &6" + Util.fmt(price) + " coins &c(you have " + Util.fmt(stats.coins) + ")."));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    // ------------------------------------------------------------ custom enchant actions

    private boolean customAllowed(Player p) {
        if (!cs().enabled()) {
            p.sendMessage(Util.color("&cCustom enchants are turned off."));
            return false;
        }
        if (ToolKind.of(p.getInventory().getItemInMainHand()) == null) {
            p.sendMessage(Util.color("&cHold a pickaxe, sword or axe in your main hand first."));
            return false;
        }
        return true;
    }

    private void applyCustom(Player p, String id) {
        if (!customAllowed(p)) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        ToolKind kind = ToolKind.of(hand);
        CustomEnchant ce = CustomEnchant.byId(id);
        if (ce == null || ce.kind != kind) {
            p.sendMessage(Util.color("&cThat enchant doesn't fit the tool in your hand."));
            return;
        }
        EnchantData.Info info = EnchantData.read(hand, cs().baseSlots());
        if (info.level(id) > 0) {
            p.sendMessage(Util.color("&cYour " + kind.label + " already has " + ce.name + "."));
            return;
        }
        if (info.applied.size() >= info.slots) {
            p.sendMessage(Util.color("&cNo free custom-enchant slot - unlock another or remove an enchant."));
            return;
        }
        if (!plugin.enchantStore().take(p.getUniqueId(), id)) {
            p.sendMessage(Util.color("&cYou don't own " + ce.name + "."));
            return;
        }
        info.applied.add(new EnchantData.Applied(ce, 1));
        EnchantData.write(hand, info, cs().baseSlots());
        p.getInventory().setItemInMainHand(hand);
        p.sendMessage(Util.color("&aApplied &d" + ce.name + " I &ato your " + kind.label + "."));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
    }

    private void upgradeCustom(Player p, String id) {
        if (!customAllowed(p)) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        EnchantData.Info info = EnchantData.read(hand, cs().baseSlots());
        EnchantData.Applied a = info.find(id);
        if (a == null) {
            p.sendMessage(Util.color("&cThat enchant isn't on the tool in your hand any more."));
            return;
        }
        int max = cs().max(id);
        if (a.level >= max) {
            p.sendMessage(Util.color("&c" + a.enchant.name + " is already at the maximum level."));
            return;
        }
        long price = cs().priceFor(id, a.level);
        PlayerStats stats = plugin.stats().get(p);
        if (stats.coins < price) {
            notEnough(p, price, stats);
            return;
        }
        stats.coins -= price;
        a.level++;
        EnchantData.write(hand, info, cs().baseSlots());
        p.getInventory().setItemInMainHand(hand);
        plugin.board().update(p);
        p.sendMessage(Util.color("&aUpgraded &d" + a.enchant.name + " &ato level &f" + a.level + " &afor &6" + Util.fmt(price) + " coins&a."));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
    }

    private void removeCustom(Player p, String id) {
        if (!customAllowed(p)) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        EnchantData.Info info = EnchantData.read(hand, cs().baseSlots());
        EnchantData.Applied a = info.find(id);
        if (a == null) {
            return;
        }
        info.applied.remove(a);
        EnchantData.write(hand, info, cs().baseSlots());
        p.getInventory().setItemInMainHand(hand);
        plugin.enchantStore().add(p.getUniqueId(), id, 1);
        p.sendMessage(Util.color("&aRemoved &d" + a.enchant.name + " &afrom your tool. It's back in your collection at level 1."));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 0.8f);
    }

    private void unlockSlot(Player p) {
        if (!customAllowed(p)) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        EnchantData.Info info = EnchantData.read(hand, cs().baseSlots());
        if (info.slots >= cs().maxSlots()) {
            p.sendMessage(Util.color("&cThis tool already has the maximum number of slots."));
            return;
        }
        long price = cs().slotPrice(info.slots);
        PlayerStats stats = plugin.stats().get(p);
        if (stats.coins < price) {
            notEnough(p, price, stats);
            return;
        }
        stats.coins -= price;
        info.slots++;
        EnchantData.write(hand, info, cs().baseSlots());
        p.getInventory().setItemInMainHand(hand);
        plugin.board().update(p);
        p.sendMessage(Util.color("&aUnlocked a custom-enchant slot (&f" + info.slots + " &atotal) for &6" + Util.fmt(price) + " coins&a."));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.0f);
    }
}

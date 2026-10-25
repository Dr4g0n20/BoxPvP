package com.boxpvp;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom enchants live on the tool as lore lines, e.g.
 *   Custom slots: 1/2
 *   Explosion III
 * (colours are ignored when reading). This class reads and writes those lines and leaves any other lore alone.
 */
public final class EnchantData {

    private EnchantData() {
    }

    public static final class Applied {
        public final CustomEnchant enchant;
        public int level;

        public Applied(CustomEnchant enchant, int level) {
            this.enchant = enchant;
            this.level = level;
        }
    }

    public static final class Info {
        /** Total custom-enchant slots on this tool. */
        public int slots;
        public final List<Applied> applied = new ArrayList<Applied>();

        public int level(String id) {
            for (Applied a : applied) {
                if (a.enchant.id.equals(id)) {
                    return a.level;
                }
            }
            return 0;
        }

        public Applied find(String id) {
            for (Applied a : applied) {
                if (a.enchant.id.equals(id)) {
                    return a;
                }
            }
            return null;
        }
    }

    private static final Pattern SLOTS = Pattern.compile("^Custom slots: (\\d+)/(\\d+)$");
    private static final Pattern ENCH = Pattern.compile("^(.+) (I|II|III|IV|V|VI|VII|VIII|IX|X)$");
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    public static String roman(int n) {
        return n >= 1 && n < ROMAN.length ? ROMAN[n] : String.valueOf(n);
    }

    private static int fromRoman(String s) {
        for (int i = 1; i < ROMAN.length; i++) {
            if (ROMAN[i].equals(s)) {
                return i;
            }
        }
        return 1;
    }

    private static boolean isCustomLine(String line) {
        String s = ChatColor.stripColor(line);
        if (s == null) {
            return false;
        }
        s = s.trim();
        if (SLOTS.matcher(s).matches()) {
            return true;
        }
        Matcher m = ENCH.matcher(s);
        return m.matches() && CustomEnchant.byName(m.group(1)) != null;
    }

    /** Reads the custom enchants on an item. A tool with no slot line has baseSlots slots. */
    public static Info read(ItemStack item, int baseSlots) {
        Info info = new Info();
        info.slots = baseSlots;
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return info;
        }
        for (String line : item.getItemMeta().getLore()) {
            String s = ChatColor.stripColor(line);
            if (s == null) {
                continue;
            }
            s = s.trim();
            Matcher sm = SLOTS.matcher(s);
            if (sm.matches()) {
                info.slots = Math.max(baseSlots, Integer.parseInt(sm.group(2)));
                continue;
            }
            Matcher em = ENCH.matcher(s);
            if (em.matches()) {
                CustomEnchant ce = CustomEnchant.byName(em.group(1));
                if (ce != null && info.find(ce.id) == null) {
                    info.applied.add(new Applied(ce, fromRoman(em.group(2))));
                }
            }
        }
        return info;
    }

    /** Writes the slot + enchant lines onto the item (replacing any old ones, keeping all other lore). */
    public static void write(ItemStack item, Info info, int baseSlots) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = new ArrayList<String>();
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (!isCustomLine(line)) {
                    lore.add(line);
                }
            }
        }
        // Nothing custom on a plain tool: keep the lore clean.
        if (!info.applied.isEmpty() || info.slots > baseSlots) {
            lore.add(ChatColor.GRAY + "Custom slots: " + ChatColor.WHITE + info.applied.size()
                    + ChatColor.GRAY + "/" + ChatColor.WHITE + info.slots);
            for (Applied a : info.applied) {
                lore.add(ChatColor.LIGHT_PURPLE + a.enchant.name + " " + ChatColor.GRAY + roman(a.level));
            }
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }
}

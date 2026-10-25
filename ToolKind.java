package com.boxpvp;

import org.bukkit.inventory.ItemStack;

/** The three tool types that can carry upgrades and custom enchants. */
public enum ToolKind {
    PICKAXE("pickaxe"),
    SWORD("sword"),
    AXE("axe");

    public final String label;

    ToolKind(String label) {
        this.label = label;
    }

    /** What kind of tool this is, or null if it isn't a pickaxe, sword or axe. */
    public static ToolKind of(ItemStack it) {
        if (it == null) {
            return null;
        }
        String n = it.getType().name();
        if (n.endsWith("_PICKAXE")) {
            return PICKAXE;
        }
        if (n.endsWith("_SWORD")) {
            return SWORD;
        }
        if (n.endsWith("_AXE")) { // "_PICKAXE" never matches this, it has no underscore before AXE
            return AXE;
        }
        return null;
    }
}

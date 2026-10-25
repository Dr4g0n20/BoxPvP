package com.boxpvp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The list of custom enchants. What each one does lives in EnchantEffects; numbers live in the config. */
public final class CustomEnchant {

    public final String id;
    public final String name;
    public final ToolKind kind;
    public final String description;

    private CustomEnchant(String id, String name, ToolKind kind, String description) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.description = description;
    }

    public static final List<CustomEnchant> ALL;

    static {
        List<CustomEnchant> l = new ArrayList<CustomEnchant>();
        // pickaxe
        l.add(new CustomEnchant("explosion", "Explosion", ToolKind.PICKAXE, "Chance to blast away a sphere of blocks."));
        l.add(new CustomEnchant("vein_miner", "Vein Miner", ToolKind.PICKAXE, "Chance to mine the whole ore vein."));
        l.add(new CustomEnchant("jackpot", "Jackpot", ToolKind.PICKAXE, "Chance for a bonus diamond or emerald."));
        l.add(new CustomEnchant("midas", "Midas", ToolKind.PICKAXE, "Chance for bonus coins when you mine."));
        l.add(new CustomEnchant("momentum", "Momentum", ToolKind.PICKAXE, "Mine quickly to build up Haste."));
        l.add(new CustomEnchant("quick_regen", "Quick Regen", ToolKind.PICKAXE, "Blocks you mine come back sooner."));
        // sword
        l.add(new CustomEnchant("lifesteal", "Lifesteal", ToolKind.SWORD, "Heal a part of the damage you deal."));
        l.add(new CustomEnchant("execute", "Execute", ToolKind.SWORD, "Extra damage against low-health targets."));
        l.add(new CustomEnchant("frenzy", "Frenzy", ToolKind.SWORD, "Chain hits to build bonus damage."));
        l.add(new CustomEnchant("streak_surge", "Streak Surge", ToolKind.SWORD, "More damage the bigger your killstreak."));
        l.add(new CustomEnchant("frostbite", "Frostbite", ToolKind.SWORD, "Chance to slow your target."));
        l.add(new CustomEnchant("thunderstrike", "Thunderstrike", ToolKind.SWORD, "Chance for lightning and bonus damage."));
        l.add(new CustomEnchant("hunters_mark", "Hunter's Mark", ToolKind.SWORD, "Your hits make the target glow."));
        // axe
        l.add(new CustomEnchant("cleave", "Cleave", ToolKind.AXE, "Your hits also hurt nearby enemies."));
        l.add(new CustomEnchant("shield_breaker", "Shield Breaker", ToolKind.AXE, "Shields stay disabled for longer."));
        l.add(new CustomEnchant("berserk", "Berserk", ToolKind.AXE, "More damage while you are hurt."));
        l.add(new CustomEnchant("hamstring", "Hamstring", ToolKind.AXE, "Chance to slow your target."));
        l.add(new CustomEnchant("timber", "Timber", ToolKind.AXE, "Chance to fell a whole tree."));
        ALL = Collections.unmodifiableList(l);
    }

    public static CustomEnchant byId(String id) {
        if (id == null) {
            return null;
        }
        for (CustomEnchant c : ALL) {
            if (c.id.equalsIgnoreCase(id)) {
                return c;
            }
        }
        return null;
    }

    public static CustomEnchant byName(String name) {
        if (name == null) {
            return null;
        }
        for (CustomEnchant c : ALL) {
            if (c.name.equalsIgnoreCase(name.trim())) {
                return c;
            }
        }
        return null;
    }

    public static List<CustomEnchant> forKind(ToolKind kind) {
        List<CustomEnchant> out = new ArrayList<CustomEnchant>();
        for (CustomEnchant c : ALL) {
            if (c.kind == kind) {
                out.add(c);
            }
        }
        return out;
    }
}

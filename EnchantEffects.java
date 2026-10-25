package com.boxpvp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** What each custom enchant actually does. All the numbers come from the config (custom-enchants.enchants.<id>). */
public class EnchantEffects implements Listener {

    private static class Frenzy {
        int stacks;
        long last;
    }

    private static class Momentum {
        int blocks;
        long last;
    }

    private final BoxPvP plugin;
    private final Random rnd = new Random();
    private final Map<UUID, Frenzy> frenzy = new HashMap<UUID, Frenzy>();
    private final Map<UUID, Momentum> momentum = new HashMap<UUID, Momentum>();
    private boolean inCleave = false;

    public EnchantEffects(BoxPvP plugin) {
        this.plugin = plugin;
    }

    private EnchantSettings cfg() {
        return plugin.enchantSettings();
    }

    private boolean enabled() {
        return cfg().enabled();
    }

    private double d(String id, String key) {
        return cfg().d(id, key);
    }

    private boolean roll(double percent) {
        return rnd.nextDouble() * 100.0 < percent;
    }

    private EnchantData.Info data(ItemStack it) {
        return EnchantData.read(it, cfg().baseSlots());
    }

    // ------------------------------------------------------------ text for the menus

    /** A one-line, level-specific explanation of what an enchant does (shown in the Upgrader). */
    public String describe(String id, int level) {
        switch (id) {
            case "explosion":
                return String.format("%.1f%% chance to mine a %d-block radius sphere", d(id, "chance-per-level") * level, (int) d(id, "radius"));
            case "vein_miner":
                return String.format("%.0f%% chance to mine up to %d ore blocks of a vein", d(id, "chance-per-level") * level,
                        (int) (d(id, "base-blocks") + d(id, "blocks-per-level") * level));
            case "jackpot":
                return String.format("%.1f%% chance of a bonus gem per block", d(id, "chance-per-level") * level);
            case "midas":
                return String.format("%.0f%% chance of up to %d bonus coins per block", d(id, "chance-per-level") * level, (int) (d(id, "coins-per-level") * level));
            case "momentum":
                return String.format("Haste up to level %d while you keep mining", level);
            case "quick_regen":
                return String.format("Mined blocks regenerate %.0f%% faster", Math.min(80.0, d(id, "reduction-percent-per-level") * level));
            case "lifesteal":
                return String.format("Heals %.0f%% of the damage you deal", d(id, "percent-per-level") * level);
            case "execute":
                return String.format("+%.0f%% damage to targets under %.0f%% health", d(id, "bonus-percent-per-level") * level, d(id, "threshold-percent"));
            case "frenzy":
                return String.format("+%.0f%% damage per stacked hit (up to %d)", d(id, "percent-per-stack-per-level") * level, (int) d(id, "max-stacks"));
            case "streak_surge":
                return String.format("+%.1f%% damage per kill in your streak (max +%.0f%%)", d(id, "percent-per-streak-per-level") * level, d(id, "cap-percent"));
            case "frostbite":
                return String.format("%.0f%% chance to slow your target for %.0fs", d(id, "chance-per-level") * level, d(id, "seconds"));
            case "thunderstrike":
                return String.format("%.0f%% chance for lightning and +%.1f damage", d(id, "chance-per-level") * level, d(id, "bonus-damage"));
            case "hunters_mark":
                return String.format("Your hits make the target glow for %.0fs", d(id, "seconds-per-level") * level);
            case "cleave":
                return String.format("Hits nearby enemies for %.0f%% of your damage", d(id, "percent-per-level") * level);
            case "shield_breaker":
                return String.format("Shields stay disabled for %.1fs", (d(id, "base-ticks") + d(id, "ticks-per-level") * level) / 20.0);
            case "berserk":
                return String.format("+%.0f%% damage below %.0f%% health", d(id, "bonus-percent-per-level") * level, d(id, "health-threshold-percent"));
            case "hamstring":
                return String.format("%.0f%% chance to slow your target for %.0fs", d(id, "chance-per-level") * level, d(id, "seconds"));
            case "timber":
                return String.format("%.0f%% chance to fell the whole tree", d(id, "chance-per-level") * level);
            default:
                return "";
        }
    }

    // ------------------------------------------------------------ pickaxe: called by the mines

    /** Block regeneration speed-up from Quick Regen (1.0 = normal, lower = faster). */
    public double regenMultiplier(ItemStack hand) {
        if (!enabled() || ToolKind.of(hand) != ToolKind.PICKAXE) {
            return 1.0;
        }
        int l = data(hand).level("quick_regen");
        if (l <= 0) {
            return 1.0;
        }
        return Math.max(0.2, 1.0 - l * d("quick_regen", "reduction-percent-per-level") / 100.0);
    }

    /** Called after a player mined origin (now a bedrock placeholder; its original type was type). */
    public void onMined(Player p, Mine m, Block origin, Material type) {
        if (!enabled()) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (ToolKind.of(hand) != ToolKind.PICKAXE) {
            return;
        }
        EnchantData.Info info = data(hand);
        if (info.applied.isEmpty()) {
            return;
        }

        int l = info.level("midas");
        if (l > 0 && roll(d("midas", "chance-per-level") * l)) {
            int max = Math.max(1, (int) (d("midas", "coins-per-level") * l));
            int coins = 1 + rnd.nextInt(max);
            plugin.stats().get(p).coins += coins;
            plugin.board().update(p);
            Util.actionBar(p, "&6Midas! &e+" + coins + " coins");
        }

        l = info.level("jackpot");
        if (l > 0 && roll(d("jackpot", "chance-per-level") * l)) {
            List<String> items = plugin.getConfig().getStringList("custom-enchants.enchants.jackpot.items");
            if (!items.isEmpty()) {
                ItemStack st = ItemKey.parseStack(items.get(rnd.nextInt(items.size())));
                if (st != null) {
                    giveOrDrop(p, st);
                    p.sendMessage(Util.color("&6&lJACKPOT! &7You found a bonus &f" + st.getAmount() + "x " + st.getType().name().toLowerCase().replace('_', ' ') + "&7!"));
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                }
            }
        }

        l = info.level("momentum");
        if (l > 0) {
            Momentum mo = momentum.get(p.getUniqueId());
            long now = System.currentTimeMillis();
            if (mo == null) {
                mo = new Momentum();
                momentum.put(p.getUniqueId(), mo);
            }
            if (now - mo.last > (long) (d("momentum", "window-seconds") * 1000.0)) {
                mo.blocks = 0;
            }
            mo.blocks++;
            mo.last = now;
            int stage = mo.blocks / Math.max(1, (int) d("momentum", "blocks-per-stage"));
            int amp = Math.min(l, stage) - 1;
            if (amp >= 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, (int) d("momentum", "duration-ticks"), amp, true, false), true);
            }
        }

        l = info.level("explosion");
        if (l > 0 && roll(d("explosion", "chance-per-level") * l)) {
            explode(p, m, origin);
        }

        l = info.level("vein_miner");
        if (l > 0 && roll(d("vein_miner", "chance-per-level") * l)) {
            int cap = (int) (d("vein_miner", "base-blocks") + d("vein_miner", "blocks-per-level") * l);
            vein(p, m, origin, type, cap);
        }
    }

    private void giveOrDrop(Player p, ItemStack st) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(st);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    /** Cosmetic explosion: particles + sound only, but it mines every mine block in the sphere. */
    private void explode(Player p, Mine m, Block center) {
        int r = Math.max(1, (int) d("explosion", "radius"));
        int cap = Math.max(1, (int) d("explosion", "max-blocks"));
        World w = center.getWorld();
        Location c = center.getLocation().add(0.5, 0.5, 0.5);
        w.spawnParticle(Particle.EXPLOSION_LARGE, c, 3, 0.4, 0.4, 0.4, 0);
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        int mined = 0;
        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r || (dx == 0 && dy == 0 && dz == 0)) {
                        continue;
                    }
                    if (mined >= cap) {
                        break outer;
                    }
                    if (plugin.mines().mineBonus(p, m, center.getRelative(dx, dy, dz))) {
                        mined++;
                    }
                }
            }
        }
        if (mined > 0) {
            Util.actionBar(p, "&c&lEXPLOSION! &7+" + mined + " blocks");
        }
    }

    /** Mines the connected blocks of the same ore as the one just broken (up to cap). */
    private void vein(Player p, Mine m, Block origin, Material type, int cap) {
        Deque<Block> queue = new ArrayDeque<Block>();
        Set<Block> seen = new HashSet<Block>();
        queue.add(origin);
        seen.add(origin);
        int mined = 0;
        while (!queue.isEmpty() && mined < cap) {
            Block cur = queue.poll();
            for (int dx = -1; dx <= 1 && mined < cap; dx++) {
                for (int dy = -1; dy <= 1 && mined < cap; dy++) {
                    for (int dz = -1; dz <= 1 && mined < cap; dz++) {
                        Block n = cur.getRelative(dx, dy, dz);
                        if (!seen.add(n) || n.getType() != type || !m.contains(n)) {
                            continue;
                        }
                        if (plugin.mines().mineBonus(p, m, n)) {
                            mined++;
                            queue.add(n);
                        }
                    }
                }
            }
        }
        if (mined > 0) {
            origin.getWorld().spawnParticle(Particle.CRIT, origin.getLocation().add(0.5, 0.5, 0.5), 20, 0.5, 0.5, 0.5, 0.1);
            Util.actionBar(p, "&b&lVEIN MINER! &7+" + mined + " blocks");
        }
    }

    // ------------------------------------------------------------ sword / axe: damage dealt

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (inCleave || !enabled() || !(e.getDamager() instanceof Player)) {
            return;
        }
        Player a = (Player) e.getDamager();
        Entity victim = e.getEntity();
        if (victim.equals(a) || !(victim instanceof LivingEntity)) {
            return;
        }
        boolean isPlayer = victim instanceof Player;
        if (!isPlayer && !plugin.dummies().isDummy(victim)) {
            return;
        }
        ItemStack hand = a.getInventory().getItemInMainHand();
        ToolKind kind = ToolKind.of(hand);
        if (kind != ToolKind.SWORD && kind != ToolKind.AXE) {
            return;
        }
        EnchantData.Info info = data(hand);
        if (info.applied.isEmpty()) {
            return;
        }

        LivingEntity target = (LivingEntity) victim;
        double mult = 1.0;
        double flat = 0.0;
        long now = System.currentTimeMillis();

        int l = info.level("execute");
        if (l > 0 && healthFraction(target) <= d("execute", "threshold-percent") / 100.0) {
            mult += d("execute", "bonus-percent-per-level") * l / 100.0;
        }

        l = info.level("frenzy");
        if (l > 0) {
            Frenzy f = frenzy.get(a.getUniqueId());
            if (f == null) {
                f = new Frenzy();
                frenzy.put(a.getUniqueId(), f);
            }
            if (now - f.last > (long) (d("frenzy", "window-seconds") * 1000.0)) {
                f.stacks = 0;
            }
            mult += f.stacks * l * d("frenzy", "percent-per-stack-per-level") / 100.0;
            f.stacks = Math.min((int) d("frenzy", "max-stacks"), f.stacks + 1);
            f.last = now;
        }

        l = info.level("streak_surge");
        if (l > 0) {
            double bonus = Math.min(d("streak_surge", "cap-percent"),
                    plugin.stats().get(a).streak * l * d("streak_surge", "percent-per-streak-per-level"));
            mult += bonus / 100.0;
        }

        l = info.level("berserk");
        if (l > 0 && healthFraction(a) <= d("berserk", "health-threshold-percent") / 100.0) {
            mult += d("berserk", "bonus-percent-per-level") * l / 100.0;
        }

        l = info.level("thunderstrike");
        if (l > 0 && roll(d("thunderstrike", "chance-per-level") * l)) {
            flat += d("thunderstrike", "bonus-damage");
            target.getWorld().strikeLightningEffect(target.getLocation()); // visual only - no fire, no extra damage
        }

        double damage = e.getDamage() * mult + flat;

        // Cleave: the same hit also hurts other players standing close to the target.
        l = info.level("cleave");
        if (l > 0) {
            cleave(a, target, damage * d("cleave", "percent-per-level") * l / 100.0);
        }

        if (damage != e.getDamage()) {
            e.setDamage(damage);
        }

        // Effects on the target itself (players only).
        if (!isPlayer) {
            return;
        }
        Player t = (Player) victim;

        l = info.level("shield_breaker");
        if (l > 0 && t.isBlocking()) {
            t.setCooldown(Material.SHIELD, (int) (d("shield_breaker", "base-ticks") + d("shield_breaker", "ticks-per-level") * l));
        }

        l = info.level("frostbite");
        if (l > 0 && roll(d("frostbite", "chance-per-level") * l)) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, (int) (d("frostbite", "seconds") * 20),
                    (int) d("frostbite", "slow-amplifier"), false, true), true);
            t.getWorld().spawnParticle(Particle.SNOW_SHOVEL, t.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
        }

        l = info.level("hamstring");
        if (l > 0 && roll(d("hamstring", "chance-per-level") * l)) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, (int) (d("hamstring", "seconds") * 20),
                    (int) d("hamstring", "slow-amplifier"), false, true), true);
        }

        l = info.level("hunters_mark");
        if (l > 0) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, (int) (d("hunters_mark", "seconds-per-level") * l * 20), 0, false, false), true);
        }
    }

    @SuppressWarnings("deprecation")
    private double healthFraction(LivingEntity le) {
        double max = le.getMaxHealth();
        return max <= 0 ? 1.0 : le.getHealth() / max;
    }

    private void cleave(Player attacker, LivingEntity target, double amount) {
        if (amount <= 0) {
            return;
        }
        double r = d("cleave", "radius");
        boolean hit = false;
        for (Entity n : target.getNearbyEntities(r, r, r)) {
            if (!(n instanceof Player) || n.equals(attacker) || n.equals(target)) {
                continue;
            }
            inCleave = true; // so this extra damage doesn't trigger enchants again
            try {
                ((Player) n).damage(amount, attacker);
            } finally {
                inCleave = false;
            }
            hit = true;
        }
        if (hit) {
            attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
        }
    }

    // Lifesteal runs after everything else, on the damage that really went through.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onHit(EntityDamageByEntityEvent e) {
        if (inCleave || !enabled() || !(e.getDamager() instanceof Player) || !(e.getEntity() instanceof Player)) {
            return;
        }
        Player a = (Player) e.getDamager();
        if (a.equals(e.getEntity())) {
            return;
        }
        ItemStack hand = a.getInventory().getItemInMainHand();
        if (ToolKind.of(hand) != ToolKind.SWORD) {
            return;
        }
        int l = data(hand).level("lifesteal");
        if (l <= 0) {
            return;
        }
        double heal = e.getFinalDamage() * l * d("lifesteal", "percent-per-level") / 100.0;
        if (heal > 0 && !a.isDead()) {
            a.setHealth(Math.min(a.getMaxHealth(), a.getHealth() + heal));
        }
    }

    // ------------------------------------------------------------ axe: Timber

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!enabled()) {
            return;
        }
        Block b = e.getBlock();
        if (b.getType() != Material.LOG && b.getType() != Material.LOG_2) {
            return;
        }
        Player p = e.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (ToolKind.of(hand) != ToolKind.AXE) {
            return;
        }
        int l = data(hand).level("timber");
        if (l <= 0 || !roll(d("timber", "chance-per-level") * l)) {
            return;
        }

        int cap = Math.max(1, (int) d("timber", "max-blocks"));
        Deque<Block> queue = new ArrayDeque<Block>();
        Set<Block> seen = new HashSet<Block>();
        List<Block> logs = new ArrayList<Block>();
        queue.add(b);
        seen.add(b);
        while (!queue.isEmpty() && logs.size() < cap) {
            Block cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = cur.getRelative(dx, dy, dz);
                        if (!seen.add(n) || (n.getType() != Material.LOG && n.getType() != Material.LOG_2)) {
                            continue;
                        }
                        if (logs.size() >= cap) {
                            continue;
                        }
                        // Respect the same protections as breaking it by hand.
                        boolean ok = plugin.mines().at(n) == null && plugin.regions().allowed(n.getLocation(), "build")
                                && !(plugin.regions().decayAt(n.getLocation()) > 0 && !plugin.regions().isDecayTracked(n));
                        if (ok) {
                            logs.add(n);
                            queue.add(n);
                        }
                    }
                }
            }
        }
        for (Block log : logs) {
            plugin.regions().untrack(log);
            log.breakNaturally(hand);
        }
        if (!logs.isEmpty()) {
            p.getWorld().playSound(b.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 0.8f);
            Util.actionBar(p, "&2&lTIMBER! &7+" + logs.size() + " logs");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        frenzy.remove(e.getPlayer().getUniqueId());
        momentum.remove(e.getPlayer().getUniqueId());
    }
}

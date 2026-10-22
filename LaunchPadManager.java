package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Launch pads: step on a registered gold block and you are flown along an arc to the pad's landing point.
 * While flying (and briefly after landing) you are immune to damage - the immunity ends the moment you attack.
 */
public class LaunchPadManager implements Listener {

    public static class Pad {
        public final String name;
        public final String world;
        public final int x, y, z;
        public Location landing;
        public double power = 1.0;

        Pad(String name, String world, int x, int y, int z) {
            this.name = name;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final BoxPvP plugin;
    private final File file;
    private final Map<String, Pad> pads = new LinkedHashMap<String, Pad>();
    private final Map<String, Pad> byBlock = new HashMap<String, Pad>();
    private final Map<UUID, Long> immuneUntil = new HashMap<UUID, Long>();
    private final Map<UUID, Long> noFallUntil = new HashMap<UUID, Long>();
    private final Map<UUID, Long> lastLaunch = new HashMap<UUID, Long>();
    private final Map<UUID, Flight> flights = new HashMap<UUID, Flight>();

    public LaunchPadManager(BoxPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "launchpads.yml");
    }

    // ------------------------------------------------------------ persistence

    private static String key(String world, int x, int y, int z) {
        return world + "," + x + "," + y + "," + z;
    }

    public void load() {
        pads.clear();
        byBlock.clear();
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        for (String name : c.getKeys(false)) {
            ConfigurationSection s = c.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            Pad pad = new Pad(name, s.getString("world"), s.getInt("x"), s.getInt("y"), s.getInt("z"));
            pad.power = s.getDouble("power", 1.0);
            pad.landing = Util.loadLoc(s, "landing");
            register(pad);
        }
    }

    private void register(Pad pad) {
        pads.put(pad.name.toLowerCase(), pad);
        byBlock.put(key(pad.world, pad.x, pad.y, pad.z), pad);
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        for (Pad pad : pads.values()) {
            String p = pad.name + ".";
            c.set(p + "world", pad.world);
            c.set(p + "x", pad.x);
            c.set(p + "y", pad.y);
            c.set(p + "z", pad.z);
            c.set(p + "power", pad.power);
            if (pad.landing != null) {
                Util.saveLoc(c, p + "landing", pad.landing);
            }
        }
        try {
            c.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save launchpads.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ CRUD

    public Material padMaterial() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("launchpad.block", "GOLD_BLOCK"));
        return m == null ? Material.GOLD_BLOCK : m;
    }

    public Pad get(String name) {
        return pads.get(name.toLowerCase());
    }

    public Pad create(String name, Block b) {
        Pad pad = new Pad(name, b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        register(pad);
        save();
        return pad;
    }

    public boolean delete(String name) {
        Pad pad = pads.remove(name.toLowerCase());
        if (pad == null) {
            return false;
        }
        byBlock.remove(key(pad.world, pad.x, pad.y, pad.z));
        save();
        return true;
    }

    public boolean existsAt(Block b) {
        return byBlock.containsKey(key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
    }

    public List<String> names() {
        List<String> out = new ArrayList<String>();
        for (Pad p : pads.values()) {
            out.add(p.name);
        }
        return out;
    }

    // ------------------------------------------------------------ immunity

    public boolean isImmune(Player p) {
        Long t = immuneUntil.get(p.getUniqueId());
        if (t == null) {
            return false;
        }
        if (t < System.currentTimeMillis()) {
            immuneUntil.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    public void endImmunity(Player p) {
        immuneUntil.remove(p.getUniqueId());
    }

    public boolean hasNoFall(Player p) {
        if (flights.containsKey(p.getUniqueId())) {
            return true;
        }
        Long t = noFallUntil.get(p.getUniqueId());
        return t != null && t > System.currentTimeMillis();
    }

    public void forget(Player p) {
        immuneUntil.remove(p.getUniqueId());
        noFallUntil.remove(p.getUniqueId());
        lastLaunch.remove(p.getUniqueId());
        Flight f = flights.remove(p.getUniqueId());
        if (f != null) {
            f.cancel();
        }
    }

    // ------------------------------------------------------------ triggering

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (pads.isEmpty()) {
            return;
        }
        Location to = e.getTo();
        // Players standing on a block have an exact whole-number Y; skip everything mid-air.
        if (to == null || to.getY() % 1.0 != 0.0) {
            return;
        }
        Block below = to.clone().subtract(0, 1, 0).getBlock();
        Pad pad = byBlock.get(key(below.getWorld().getName(), below.getX(), below.getY(), below.getZ()));
        if (pad == null) {
            return;
        }
        Player p = e.getPlayer();
        if (flights.containsKey(p.getUniqueId())) {
            return;
        }
        // A pad only works while its block is still the pad block type (broken/replaced = disabled).
        if (below.getType() != padMaterial()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastLaunch.get(p.getUniqueId());
        if (last != null && now - last < plugin.getConfig().getLong("launchpad.cooldown-ms", 1500L)) {
            return;
        }
        if (pad.landing == null || pad.landing.getWorld() == null || !pad.landing.getWorld().equals(p.getWorld())) {
            if (p.hasPermission("boxpvp.admin")) {
                p.sendMessage(Util.color("&cLaunch pad '" + pad.name + "' has no valid landing point. Use /launchpad setlanding " + pad.name));
            }
            return;
        }
        lastLaunch.put(p.getUniqueId(), now);
        launch(p, pad);
    }

    private void launch(Player p, Pad pad) {
        FileConfiguration cfg = plugin.getConfig();
        Location start = p.getLocation();
        Location land = pad.landing;

        double dx = land.getX() - start.getX();
        double dz = land.getZ() - start.getZ();
        double dy = land.getY() - start.getY();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        double speed = Math.max(0.2, cfg.getDouble("launchpad.flight-speed", 1.1));
        int total = (int) Math.round(Math.sqrt(horiz * horiz + dy * dy * 0.25) / speed);
        total = Math.max(cfg.getInt("launchpad.min-ticks", 14), total);
        total = Math.min(cfg.getInt("launchpad.max-ticks", 120), total);

        double arc = cfg.getDouble("launchpad.arc-base", 2.5) + horiz * cfg.getDouble("launchpad.arc-per-block", 0.12);
        arc = Math.min(cfg.getDouble("launchpad.max-arc", 20.0), arc) * pad.power;

        immuneUntil.put(p.getUniqueId(), Long.MAX_VALUE);
        Flight f = new Flight(p, start, land, total, arc);
        flights.put(p.getUniqueId(), f);
        f.runTaskTimer(plugin, 1L, 1L);

        World w = p.getWorld();
        w.playSound(start, Sound.ENTITY_FIREWORK_LAUNCH, 1f, 1f);
        w.spawnParticle(Particle.CLOUD, start, 25, 0.4, 0.1, 0.4, 0.05);
    }

    private class Flight extends BukkitRunnable {
        private final Player player;
        private final Location start;
        private final Location target;
        private final int total;
        private final double arc;
        private int tick = 0;

        Flight(Player player, Location start, Location target, int total, double arc) {
            this.player = player;
            this.start = start.clone();
            this.target = target.clone();
            this.total = total;
            this.arc = arc;
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.isDead()) {
                finish();
                return;
            }
            tick++;
            double s = Math.min(1.0, (double) tick / (double) total);
            double x = start.getX() + (target.getX() - start.getX()) * s;
            double z = start.getZ() + (target.getZ() - start.getZ()) * s;
            double y = start.getY() + (target.getY() - start.getY()) * s + arc * 4.0 * s * (1.0 - s);

            Vector v = new Vector(x, y, z).subtract(player.getLocation().toVector());
            double len = v.length();
            if (len > 3.5) {
                v.multiply(3.5 / len);
            }
            player.setVelocity(v);
            player.setFallDistance(0f);

            if (tick >= total || (tick > 8 && player.isOnGround())) {
                finish();
            }
        }

        private void finish() {
            cancel();
            UUID id = player.getUniqueId();
            flights.remove(id);
            long now = System.currentTimeMillis();
            noFallUntil.put(id, now + 3000L);
            // Only keep immunity if the player hasn't already ended it by attacking.
            if (immuneUntil.containsKey(id)) {
                long ms = (long) (plugin.getConfig().getDouble("launchpad.immunity-seconds", 4.0) * 1000.0);
                immuneUntil.put(id, now + ms);
            }
            if (player.isOnline()) {
                player.setFallDistance(0f);
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.4, 0.1, 0.4, 0.03);
            }
        }
    }
}

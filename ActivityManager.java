package com.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AFK handling. Anyone who hasn't moved, mined, fought, chatted or clicked for a while is marked AFK; if they are
 * out in a PvP area (and not in combat) they are moved to spawn, so AFK players aren't free kills. Optionally kicked.
 */
public class ActivityManager implements Listener {

    private final BoxPvP plugin;
    // Chat events come from another thread, so these are concurrent.
    private final Map<UUID, Long> last = new ConcurrentHashMap<UUID, Long>();
    private final Set<UUID> afk = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    public ActivityManager(BoxPvP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                check();
            }
        }, 100L, 100L);
    }

    public boolean isAfk(Player p) {
        return afk.contains(p.getUniqueId());
    }

    /** Call whenever the player does something. */
    public void touch(Player p) {
        last.put(p.getUniqueId(), System.currentTimeMillis());
        if (afk.remove(p.getUniqueId())) {
            p.sendMessage(Util.color("&aYou're no longer AFK."));
        }
    }

    private void check() {
        if (!plugin.getConfig().getBoolean("afk.enabled", true)) {
            return;
        }
        long now = System.currentTimeMillis();
        long after = (long) (plugin.getConfig().getDouble("afk.after-seconds", 180.0) * 1000.0);
        long kickExtra = (long) (plugin.getConfig().getDouble("afk.kick-after-seconds", 0.0) * 1000.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (p.getGameMode() == GameMode.CREATIVE || p.hasPermission("boxpvp.afk.bypass")) {
                continue;
            }
            Long l = last.get(id);
            if (l == null) {
                last.put(id, now);
                continue;
            }
            long idle = now - l;
            if (idle >= after && !afk.contains(id)) {
                afk.add(id);
                Location spawn = plugin.spawn();
                boolean outInTheArena = plugin.regions().allowed(p.getLocation(), "pvp");
                if (spawn != null && outInTheArena && !plugin.inCombat(p) && !p.isInsideVehicle()) {
                    p.teleport(spawn);
                    p.sendMessage(Util.color("&eYou were AFK, so you were moved to spawn. Move to come back."));
                } else {
                    p.sendMessage(Util.color("&7You're now AFK."));
                }
            }
            if (kickExtra > 0 && idle >= after + kickExtra) {
                p.kickPlayer(Util.color("&cKicked for being AFK."));
            }
        }
    }

    // ------------------------------------------------------------ things that count as being active

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        last.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        last.remove(e.getPlayer().getUniqueId());
        afk.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location a = e.getFrom();
        Location b = e.getTo();
        if (b == null || (a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ())) {
            return; // only turning their head doesn't count
        }
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player) {
            touch((Player) e.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            touch((Player) e.getDamager());
        }
    }
}

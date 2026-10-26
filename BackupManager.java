package com.boxpvp;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * Keeps rolling copies of every data file in plugins/BoxPvP/backups/<date_time>/. To restore: stop the server and copy
 * the files from a backup folder back into plugins/BoxPvP/.
 */
public class BackupManager {

    private static final String[] FILES = {
            "stats.yml", "regions.yml", "mines.yml", "launchpads.yml", "data.yml",
            "ranks.yml", "clans.yml", "titles.yml", "enchants.yml", "config.yml"
    };

    private final BoxPvP plugin;

    public BackupManager(BoxPvP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("backups.enabled", true)) {
            return;
        }
        long minutes = Math.max(5L, plugin.getConfig().getLong("backups.interval-minutes", 60L));
        long ticks = minutes * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                backupNow();
            }
        }, ticks, ticks);
    }

    /** Copies every data file into a new dated folder. Returns the folder name, or null if nothing could be copied. */
    public String backupNow() {
        // Make sure the files on disk are current first.
        if (plugin.stats() != null) {
            plugin.stats().save();
        }
        if (plugin.achievements() != null) {
            plugin.achievements().save();
        }
        File root = new File(plugin.getDataFolder(), "backups");
        String name = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File dir = new File(root, name);
        if (!dir.mkdirs() && !dir.exists()) {
            plugin.getLogger().warning("Backup failed: couldn't create " + dir);
            return null;
        }
        int copied = 0;
        for (String f : FILES) {
            File src = new File(plugin.getDataFolder(), f);
            if (!src.exists()) {
                continue;
            }
            try {
                Files.copy(src.toPath(), new File(dir, f).toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (IOException e) {
                plugin.getLogger().warning("Backup of " + f + " failed: " + e.getMessage());
            }
        }
        if (copied == 0) {
            dir.delete();
            return null;
        }
        prune(root);
        return name;
    }

    /** Deletes the oldest backups beyond the configured number to keep. */
    private void prune(File root) {
        int keep = Math.max(1, plugin.getConfig().getInt("backups.keep", 24));
        File[] dirs = root.listFiles();
        if (dirs == null || dirs.length <= keep) {
            return;
        }
        Arrays.sort(dirs); // folder names are timestamps, so alphabetical = oldest first
        for (int i = 0; i < dirs.length - keep; i++) {
            File[] inside = dirs[i].listFiles();
            if (inside != null) {
                for (File f : inside) {
                    f.delete();
                }
            }
            dirs[i].delete();
        }
    }
}

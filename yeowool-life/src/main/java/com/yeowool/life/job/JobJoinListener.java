package com.yeowool.life.job;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Loads a player's job data into {@link JobManager}'s cache on join and unloads it after a short grace period on quit, mirroring core's PlayerConnectionListener. */
public final class JobJoinListener implements Listener {

    private static final long UNLOAD_DELAY_TICKS = 20L * 15;

    private final JavaPlugin plugin;
    private final JobManager jobManager;

    public JobJoinListener(JavaPlugin plugin, JobManager jobManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        jobManager.load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getServer().getPlayer(uuid) == null) {
                    jobManager.unload(uuid);
                }
            }
        }.runTaskLater(plugin, UNLOAD_DELAY_TICKS);
    }
}

package com.yeowool.teleport.home;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Loads a player's homes into {@link HomeManager}'s cache on join and unloads them after a short grace period on quit. */
public final class HomeJoinListener implements Listener {

    private static final long UNLOAD_DELAY_TICKS = 20L * 15;

    private final JavaPlugin plugin;
    private final HomeManager homeManager;

    public HomeJoinListener(JavaPlugin plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        homeManager.load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getServer().getPlayer(uuid) == null) {
                    homeManager.unload(uuid);
                }
            }
        }.runTaskLater(plugin, UNLOAD_DELAY_TICKS);
    }
}

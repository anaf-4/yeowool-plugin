package com.yeowool.life.autofarm;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Re-applies (or clears) the level-number hijack on join — if a player
 * logged out mid-charge, their {@code .dat} file's vanilla level is whatever
 * count was showing at the time, not their real level, so this must run
 * again once {@link com.yeowool.core.api.YeowoolCoreAPI#playerData()} has
 * them loaded.
 */
public final class AutoFarmJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final AutoFarmManager manager;

    public AutoFarmJoinListener(JavaPlugin plugin, AutoFarmManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            manager.core().playerData().getIfLoaded(player.getUniqueId())
                    .ifPresent(data -> manager.refreshDisplay(player, data));
        }, 5L);
    }
}

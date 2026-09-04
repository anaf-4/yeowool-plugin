package com.yeowool.life.bag;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads a player's 4 bags into {@link BagManager}'s cache on join, flushes and evicts them on quit. */
public final class BagPlayerListener implements Listener {

    private final BagManager manager;

    public BagPlayerListener(BagManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.loadAsync(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.unloadAsync(event.getPlayer().getUniqueId());
    }
}

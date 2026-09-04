package com.yeowool.community.display;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Applies a joining player's rank icon / title / Korean nickname to their
 * tab-list name right away, and drops their cached icon+title prefix (see
 * {@link PlayerIdentityService}) on quit so it doesn't linger forever.
 */
public final class PlayerIdentityJoinListener implements Listener {

    private final PlayerIdentityService identityService;

    public PlayerIdentityJoinListener(PlayerIdentityService identityService) {
        this.identityService = identityService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        identityService.refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        identityService.clearCache(event.getPlayer().getUniqueId());
    }
}

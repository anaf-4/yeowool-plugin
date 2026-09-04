package com.yeowool.community.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Fakes a "Shift+F" keybind: vanilla clients don't send arbitrary key presses to the server, but
 * F is the default "swap hands" key, so sneaking + swap-hands is the closest thing Bukkit can
 * actually observe. Not used anywhere else in this project, so repurposing it here doesn't
 * collide with any other feature.
 */
public final class MenuKeybindListener implements Listener {

    private final MenuContext ctx;

    public MenuKeybindListener(MenuContext ctx) {
        this.ctx = ctx;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!event.getPlayer().isSneaking()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        new MenuHubGui(ctx, player).open(player);
    }
}

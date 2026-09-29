package com.yeowool.enhance;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Rebuilds every enhanced item a player carries shortly after join, so gear enhanced before the
 * base-stat fix (which lost its material's default attack damage/speed/armor) gets them back
 * without having to be enhanced again. Idempotent — re-running it changes nothing.
 */
public final class EnhanceRepairListener implements Listener {

    private final JavaPlugin plugin;
    private final EnhanceItemData itemData;

    public EnhanceRepairListener(JavaPlugin plugin, EnhanceItemData itemData) {
        this.plugin = plugin;
        this.itemData = itemData;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null) {
                    itemData.repair(item);
                }
            }
        }, 40L);
    }
}

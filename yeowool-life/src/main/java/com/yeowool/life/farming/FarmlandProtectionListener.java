package com.yeowool.life.farming;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/**
 * Stops farmland from reverting to dirt when a player, animal, or any other
 * entity jumps/lands on it — vanilla's trampling mechanic would otherwise
 * pop the crop growing on top of it, which is the actual complaint ("작물이
 * 부서지지 않도록"): the crop isn't directly broken, the farmland under it
 * turning to dirt breaks it as a side effect.
 */
public final class FarmlandProtectionListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onTrample(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND && event.getTo() == Material.DIRT) {
            event.setCancelled(true);
        }
    }
}

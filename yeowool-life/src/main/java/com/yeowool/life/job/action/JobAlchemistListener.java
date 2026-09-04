package com.yeowool.life.job.action;

import com.yeowool.life.job.JobManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * Grants "alchemist" job XP when a player takes a finished (non-water)
 * potion out of a brewing stand's output slots (0-2). Taking the output is
 * used as a proxy for "brewed a potion" since Bukkit's {@code BrewEvent}
 * doesn't carry a player reference.
 */
public final class JobAlchemistListener implements Listener {

    private final JobManager jobManager;
    private final long xpPerBrew;

    public JobAlchemistListener(JobManager jobManager, long xpPerBrew) {
        this.jobManager = jobManager;
        this.xpPerBrew = xpPerBrew;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof BrewerInventory) || event.getSlot() < 0 || event.getSlot() > 2) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || !isFinishedPotion(current)) {
            return;
        }
        jobManager.grantXp(player, "alchemist", xpPerBrew);
    }

    private boolean isFinishedPotion(ItemStack item) {
        if (item.getType() != Material.POTION && item.getType() != Material.SPLASH_POTION && item.getType() != Material.LINGERING_POTION) {
            return false;
        }
        if (!(item.getItemMeta() instanceof PotionMeta potionMeta) || !potionMeta.hasBasePotionType()) {
            return false;
        }
        return potionMeta.getBasePotionType() != PotionType.WATER;
    }
}

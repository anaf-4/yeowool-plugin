package com.yeowool.life.job.action;

import com.yeowool.life.job.JobManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** Grants "blacksmith" job XP when a player takes a smelted metal ingot out of a furnace/blast furnace. */
public final class JobBlacksmithListener implements Listener {

    private static final Set<Material> INGOTS = Set.of(
            Material.IRON_INGOT, Material.GOLD_INGOT, Material.COPPER_INGOT, Material.NETHERITE_SCRAP
    );

    private final JobManager jobManager;
    private final long xpPerSmelt;

    public JobBlacksmithListener(JobManager jobManager, long xpPerSmelt) {
        this.jobManager = jobManager;
        this.xpPerSmelt = xpPerSmelt;
    }

    @EventHandler(ignoreCancelled = true)
    public void onExtract(FurnaceExtractEvent event) {
        if (!INGOTS.contains(event.getItemType())) {
            return;
        }
        Player player = event.getPlayer();
        jobManager.grantXp(player, "blacksmith", xpPerSmelt * event.getItemAmount());
        if (jobManager.rollExtraYield(player, "blacksmith")) {
            var leftover = player.getInventory().addItem(new ItemStack(event.getItemType(), 1));
            leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        }
    }
}

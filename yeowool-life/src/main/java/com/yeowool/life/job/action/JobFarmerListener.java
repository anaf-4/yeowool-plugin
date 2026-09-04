package com.yeowool.life.job.action;

import com.yeowool.life.job.JobManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;

/**
 * Grants "farmer" job XP on harvesting a fully-grown crop — the same
 * materials/fully-grown check as {@link com.yeowool.life.farming.FarmingListener}'s
 * land-XP harvest, kept as a separate listener so the two XP systems
 * (land vs. job) stay independent.
 */
public final class JobFarmerListener implements Listener {

    private static final Set<Material> HARVEST_MATERIALS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.COCOA, Material.NETHER_WART, Material.SWEET_BERRY_BUSH,
            Material.PUMPKIN, Material.MELON, Material.SUGAR_CANE
    );

    private static final Map<Material, Material> BONUS_DROP = Map.of(
            Material.WHEAT, Material.WHEAT,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT,
            Material.COCOA, Material.COCOA_BEANS,
            Material.NETHER_WART, Material.NETHER_WART,
            Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES,
            Material.PUMPKIN, Material.PUMPKIN,
            Material.MELON, Material.MELON_SLICE,
            Material.SUGAR_CANE, Material.SUGAR_CANE
    );

    private final JobManager jobManager;
    private final long xpPerHarvest;

    public JobFarmerListener(JobManager jobManager, long xpPerHarvest) {
        this.jobManager = jobManager;
        this.xpPerHarvest = xpPerHarvest;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (!HARVEST_MATERIALS.contains(type)) {
            return;
        }
        if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }

        Player player = event.getPlayer();
        jobManager.grantXp(player, "farmer", xpPerHarvest);
        if (jobManager.rollExtraYield(player, "farmer")) {
            Material bonus = BONUS_DROP.get(type);
            if (bonus != null) {
                player.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(bonus, 1));
            }
        }
    }
}

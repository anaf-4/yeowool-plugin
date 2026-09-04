package com.yeowool.life.job.action;

import com.yeowool.life.job.JobManager;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/** Grants "builder" job XP for placing any wooden building block (planks/stairs/slabs/fences/doors/trapdoors — vanilla material tags, so every wood type is covered automatically). */
public final class JobBuilderListener implements Listener {

    private final JobManager jobManager;
    private final long xpPerPlace;

    public JobBuilderListener(JobManager jobManager, long xpPerPlace) {
        this.jobManager = jobManager;
        this.xpPerPlace = xpPerPlace;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (!isWoodenBuildingBlock(type)) {
            return;
        }
        jobManager.grantXp(event.getPlayer(), "builder", xpPerPlace);
        if (jobManager.rollExtraYield(event.getPlayer(), "builder")) {
            event.getPlayer().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(type, 1));
        }
    }

    private boolean isWoodenBuildingBlock(Material type) {
        return Tag.PLANKS.isTagged(type) || Tag.WOODEN_STAIRS.isTagged(type) || Tag.WOODEN_SLABS.isTagged(type)
                || Tag.WOODEN_FENCES.isTagged(type) || Tag.WOODEN_DOORS.isTagged(type) || Tag.WOODEN_TRAPDOORS.isTagged(type);
    }
}

package com.yeowool.life.logging.tree;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Wires sapling placement/breaking into {@link TreeTimerService}. Natural
 * random-tick growth is cancelled while a sapling is pending (both the
 * "stage" bump via {@link BlockGrowEvent} and the actual tree-from-sapling
 * conversion via {@link StructureGrowEvent}) so the fixed timer is the only
 * thing that grows it — except bonemeal, which is still allowed to finish
 * it instantly as a legitimate speed-up, same as farming crops.
 */
public final class SaplingListener implements Listener {

    private final TreeTimerService timerService;

    public SaplingListener(TreeTimerService timerService) {
        this.timerService = timerService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlant(BlockPlaceEvent event) {
        if (TreeTimerService.isSapling(event.getBlock().getType())) {
            timerService.startTimer(event.getBlock());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (timerService.isPending(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (!timerService.isPending(event.getLocation().getBlock())) {
            return;
        }
        if (event.isFromBonemeal()) {
            timerService.cancel(event.getLocation().getBlock());
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (TreeTimerService.isSapling(event.getBlock().getType())) {
            timerService.cancel(event.getBlock());
        }
    }
}

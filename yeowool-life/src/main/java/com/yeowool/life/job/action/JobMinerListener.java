package com.yeowool.life.job.action;

import com.yeowool.life.job.JobManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/** Grants "miner" job XP for breaking an ore block or ancient debris — same detection as the separate {@code MiningListener} dex-tracker, just feeding the job system instead of land XP/statistics. */
public final class JobMinerListener implements Listener {

    private final JobManager jobManager;
    private final long xpPerOre;

    public JobMinerListener(JobManager jobManager, long xpPerOre) {
        this.jobManager = jobManager;
        this.xpPerOre = xpPerOre;
    }

    private boolean isOre(Material material) {
        return material == Material.ANCIENT_DEBRIS || material.name().endsWith("_ORE");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (!isOre(type)) {
            return;
        }
        Player player = event.getPlayer();
        jobManager.grantXp(player, "miner", xpPerOre);
        if (jobManager.rollExtraYield(player, "miner")) {
            player.getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(type, 1));
        }
    }
}

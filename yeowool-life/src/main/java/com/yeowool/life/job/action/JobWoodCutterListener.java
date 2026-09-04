package com.yeowool.life.job.action;

import com.yeowool.life.job.JobManager;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grants "wood_cutter" (목수) job XP for chopping a naturally-grown log —
 * same player-placed-log exclusion as the separate {@code LoggingListener}
 * dex-tracker, kept as its own tracking set here since that one's is
 * private, feeding the job system instead of land XP/statistics.
 */
public final class JobWoodCutterListener implements Listener {

    private final JobManager jobManager;
    private final long xpPerLog;
    private final Set<Location> playerPlacedLogs = ConcurrentHashMap.newKeySet();

    public JobWoodCutterListener(JobManager jobManager, long xpPerLog) {
        this.jobManager = jobManager;
        this.xpPerLog = xpPerLog;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (Tag.LOGS.isTagged(event.getBlock().getType())) {
            playerPlacedLogs.add(event.getBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        var type = event.getBlock().getType();
        if (!Tag.LOGS.isTagged(type)) {
            return;
        }
        Location location = event.getBlock().getLocation();
        if (playerPlacedLogs.remove(location)) {
            return;
        }

        Player player = event.getPlayer();
        jobManager.grantXp(player, "wood_cutter", xpPerLog);
        if (jobManager.rollExtraYield(player, "wood_cutter")) {
            player.getWorld().dropItemNaturally(location, new ItemStack(type, 1));
        }
    }
}

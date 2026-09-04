package com.yeowool.life.job.action;

import com.yeowool.life.job.JobManager;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

/** Grants "fisherman" job XP on every successful catch. */
public final class JobFishermanListener implements Listener {

    private final JobManager jobManager;
    private final long xpPerCatch;

    public JobFishermanListener(JobManager jobManager, long xpPerCatch) {
        this.jobManager = jobManager;
        this.xpPerCatch = xpPerCatch;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        jobManager.grantXp(player, "fisherman", xpPerCatch);
        if (jobManager.rollExtraYield(player, "fisherman") && event.getCaught() instanceof Item item) {
            item.getWorld().dropItemNaturally(item.getLocation(), item.getItemStack().clone());
        }
    }
}

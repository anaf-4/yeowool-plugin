package com.yeowool.life.job.action;

import com.yeowool.life.job.JobManager;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Grants "hunter" job XP for killing a hostile mob ({@link Monster} —
 * zombies/skeletons/creepers/spiders/endermen/etc.), unlike the separate
 * {@code HuntingListener} dex-tracker which counts every non-player kill
 * including passive animals — a job literally named 사냥꾼 (hunter) should
 * only reward actual monster hunting.
 */
public final class JobHunterListener implements Listener {

    private final JobManager jobManager;
    private final long xpPerKill;

    public JobHunterListener(JobManager jobManager, long xpPerKill) {
        this.jobManager = jobManager;
        this.xpPerKill = xpPerKill;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        if (!(event.getEntity().getKiller() instanceof Player player)) {
            return;
        }
        jobManager.grantXp(player, "hunter", xpPerKill);
        if (jobManager.rollExtraYield(player, "hunter") && !event.getDrops().isEmpty()) {
            var bonus = event.getDrops().get(0).clone();
            bonus.setAmount(1);
            player.getWorld().dropItemNaturally(event.getEntity().getLocation(), bonus);
        }
    }
}

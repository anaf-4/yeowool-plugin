package com.yeowool.life.job.action;

import com.yeowool.life.job.JobManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

/** Grants "enchanter" job XP whenever a player enchants an item at an enchanting table. No extra-yield roll — there's no clean "one more enchant" bonus to give, same as the alchemist job. */
public final class JobEnchanterListener implements Listener {

    private final JobManager jobManager;
    private final long xpPerEnchant;

    public JobEnchanterListener(JobManager jobManager, long xpPerEnchant) {
        this.jobManager = jobManager;
        this.xpPerEnchant = xpPerEnchant;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        jobManager.grantXp(event.getEnchanter(), "enchanter", xpPerEnchant);
    }
}

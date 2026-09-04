package com.yeowool.life.job.action;

import com.yeowool.life.job.JobManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;

/**
 * Grants "digger" (도굴꾼) job XP the moment loot is generated for a
 * container a player opens for the first time (dungeon/temple/shipwreck
 * chests etc. — {@link LootGenerateEvent} only fires once, on first open,
 * before the items are placed). Restricted to actual containers
 * ({@code getInventoryHolder() != null}) so mob-kill loot and fishing loot
 * — which also route through this event — don't double-count against
 * Hunter/Fisherman.
 */
public final class JobDiggerListener implements Listener {

    private final JobManager jobManager;
    private final long xpPerLoot;

    public JobDiggerListener(JobManager jobManager, long xpPerLoot) {
        this.jobManager = jobManager;
        this.xpPerLoot = xpPerLoot;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (event.getInventoryHolder() == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        jobManager.grantXp(player, "digger", xpPerLoot);
    }
}

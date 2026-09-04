package com.yeowool.life.hunting;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.event.PlayerRepeatableActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Section 6.6-ish (not in the original plugin plan, added for the 사냥 도감):
 * tracks hostile-mob kills the same lightweight way mining/farming/ranch
 * track their own statistic — no combat changes, vanilla fighting stays
 * vanilla. Player kills (PvP) are excluded since this is a collection
 * mechanic, not a bounty system.
 */
public final class HuntingListener implements Listener {

    private final YeowoolCoreAPI core;
    private final long xpPerKill;

    public HuntingListener(YeowoolCoreAPI core, long xpPerKill) {
        this.core = core;
        this.xpPerKill = xpPerKill;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        if (!(event.getEntity().getKiller() instanceof Player player)) {
            return;
        }

        String type = event.getEntity().getType().name();
        core.landStats().addLandXp(player.getUniqueId(), xpPerKill);
        core.playerData().getIfLoaded(player.getUniqueId()).ifPresent(data -> {
            data.addStatistic("life.hunting.killed", 1);
            data.addStatistic("dex.hunting." + type, 1);
        });

        Bukkit.getPluginManager().callEvent(new PlayerRepeatableActionEvent(player.getUniqueId(), "hunting"));
    }
}

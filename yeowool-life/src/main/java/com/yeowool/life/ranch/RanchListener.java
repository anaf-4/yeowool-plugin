package com.yeowool.life.ranch;

import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;

import java.util.Arrays;

/**
 * Section 6.3 (YeowoolRanch): breeding XP/statistics, plus a per-chunk cap on
 * animals of the same type so unattended breeding can't runaway-spawn
 * entities server-wide (plugin plan's "최적화" goals: 개체수 제한, 과도한
 * 번식 제한, 불필요한 AI 부하 최소화). The cap is per-chunk rather than
 * per-land since this module doesn't depend on YeowoolLand.
 */
public final class RanchListener implements Listener {

    private final YeowoolCoreAPI core;
    private final long xpPerBreed;
    private final int maxAnimalsPerChunk;

    public RanchListener(YeowoolCoreAPI core, long xpPerBreed, int maxAnimalsPerChunk) {
        this.core = core;
        this.xpPerBreed = xpPerBreed;
        this.maxAnimalsPerChunk = maxAnimalsPerChunk;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getEntity() instanceof Animals)) {
            return;
        }

        long sameTypeCount = Arrays.stream(event.getEntity().getChunk().getEntities())
                .filter(e -> e.getType() == event.getEntity().getType())
                .count();
        if (sameTypeCount > maxAnimalsPerChunk) {
            event.setCancelled(true);
            return;
        }

        Entity breeder = event.getBreeder();
        if (breeder instanceof Player player) {
            core.landStats().addLandXp(player.getUniqueId(), xpPerBreed);
            core.playerData().getIfLoaded(player.getUniqueId())
                    .ifPresent(data -> data.addStatistic("life.ranch.bred", 1));
        }
    }
}

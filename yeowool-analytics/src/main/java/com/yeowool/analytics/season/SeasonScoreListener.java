package com.yeowool.analytics.season;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.event.PlayerLandXpChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Feeds the {@code season.score} statistic {@link SeasonManager} ranks and
 * rewards from every land-XP gain (farming/mining/fishing/hunting/ranch
 * actions, land level-ups) already flowing through {@code core.landStats()}.
 * Reusing this existing signal means a season reset only has to zero one
 * resettable statistic instead of touching real economy/land data, and no
 * new per-activity listeners are needed just to measure "how active was
 * this player this season".
 */
public final class SeasonScoreListener implements Listener {

    private final YeowoolCoreAPI core;

    public SeasonScoreListener(YeowoolCoreAPI core) {
        this.core = core;
    }

    @EventHandler
    public void onLandXpChange(PlayerLandXpChangeEvent event) {
        if (event.getDelta() <= 0) {
            return;
        }
        core.playerData().getIfLoaded(event.getUuid())
                .ifPresent(data -> data.addStatistic("season.score", event.getDelta()));
    }
}

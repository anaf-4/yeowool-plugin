package com.yeowool.community.profile;

import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * YeowoolCore doesn't track total playtime on its own (see plugin plan's
 * Player struct — it isn't listed), so Community adds it as a statistic,
 * ticked once per online player per minute.
 */
public final class PlaytimeTracker extends BukkitRunnable {

    public static final String STAT_KEY = "profile.playtime_minutes";

    private final YeowoolCoreAPI core;

    public PlaytimeTracker(YeowoolCoreAPI core) {
        this.core = core;
    }

    @Override
    public void run() {
        for (var player : Bukkit.getOnlinePlayers()) {
            core.playerData().getIfLoaded(player.getUniqueId())
                    .ifPresent(data -> data.addStatistic(STAT_KEY, 1));
        }
    }
}

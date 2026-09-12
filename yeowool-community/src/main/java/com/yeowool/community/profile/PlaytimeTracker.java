package com.yeowool.community.profile;

import com.yeowool.community.playtime.PlaytimeManager;
import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * YeowoolCore doesn't track total playtime on its own (see plugin plan's
 * Player struct — it isn't listed), so Community adds it as a statistic,
 * ticked once per online player per minute. Also drives {@link
 * PlaytimeManager#tickDaily} (today-only playtime for the {@code /플레이타임}
 * reward tiers) off this same once-a-minute loop instead of a second
 * scheduler — the two counters use separate settings/statistics keys and
 * never interfere with each other.
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
                    .ifPresent(data -> {
                        data.addStatistic(STAT_KEY, 1);
                        PlaytimeManager.tickDaily(data);
                    });
        }
    }
}

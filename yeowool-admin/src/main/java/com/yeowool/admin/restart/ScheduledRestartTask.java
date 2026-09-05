package com.yeowool.admin.restart;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Drives {@link RestartScheduleStore}: every second, finds this server's next
 * scheduled restart instant and fires 10분/5분/1분 chat warnings as the
 * countdown crosses each threshold, then a clean {@link Bukkit#shutdown()} at
 * 0. At 1분 also kicks off {@link YeowoolCoreAPI#playerData()}'s
 * {@code saveAll()} in the background (not blocking the main thread — the
 * full minute left is plenty of headroom) as a belt-and-suspenders flush on
 * top of the blocking {@code saveAll().join()} YeowoolCore's own
 * {@code onDisable} already runs during the shutdown this triggers, so a
 * scheduled restart never loses progress to a "백섭" (rollback to stale data).
 *
 * <p>Only handles the shutdown half — Paper can't relaunch its own JVM, so
 * something outside the process (a watchdog script/scheduled task) needs to
 * bring the server back up once it exits for this to be a full "reboot"
 * rather than just a scheduled stop.
 */
public final class ScheduledRestartTask extends BukkitRunnable {

    private static final int[] THRESHOLD_SECONDS = {600, 300, 60, 0};

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final RestartScheduleStore store;

    private LocalDateTime lastTarget;
    private final Set<Integer> firedThresholds = new HashSet<>();

    public ScheduledRestartTask(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, RestartScheduleStore store) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.store = store;
    }

    @Override
    public void run() {
        List<LocalTime> times = store.times();
        if (times.isEmpty()) {
            lastTarget = null;
            firedThresholds.clear();
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = nextOccurrence(now, times);
        if (!target.equals(lastTarget)) {
            lastTarget = target;
            firedThresholds.clear();
        }

        long secondsUntil = Duration.between(now, target).getSeconds();
        for (int threshold : THRESHOLD_SECONDS) {
            if (secondsUntil <= threshold && firedThresholds.add(threshold)) {
                onThreshold(threshold);
            }
        }
    }

    private LocalDateTime nextOccurrence(LocalDateTime now, List<LocalTime> times) {
        LocalDateTime best = null;
        for (LocalTime time : times) {
            LocalDateTime candidate = now.toLocalDate().atTime(time);
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusDays(1);
            }
            if (best == null || candidate.isBefore(best)) {
                best = candidate;
            }
        }
        return best;
    }

    private void onThreshold(int secondsRemaining) {
        switch (secondsRemaining) {
            case 600 -> messages.broadcast("restart.warning-10");
            case 300 -> messages.broadcast("restart.warning-5");
            case 60 -> {
                messages.broadcast("restart.warning-1");
                messages.broadcast("restart.saving");
                core.playerData().saveAll();
            }
            case 0 -> {
                messages.broadcast("restart.now");
                plugin.getLogger().info("예약된 자동 재부팅 시각이 되어 서버를 종료합니다.");
                Bukkit.getScheduler().runTask(plugin, Bukkit::shutdown);
            }
            default -> {
            }
        }
    }
}

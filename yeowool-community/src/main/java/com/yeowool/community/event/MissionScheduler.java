package com.yeowool.community.event;

import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-starts a {@link MissionEventManager} mission at configured times of
 * day ({@code mission.schedule}) instead of relying on an admin remembering
 * to run {@code /이벤트 미션시작} — the recurring "World boss at 8pm" style
 * content this was built for needs to show up on its own to matter for
 * retention. Never overrides a mission an admin (or a previous schedule
 * entry) already started; each entry fires at most once per server-local
 * day so a slow tick or a missed check doesn't retrigger it later that
 * same day.
 */
public final class MissionScheduler {

    public record ScheduledMission(String time, MissionEventManager.Kind kind, String targetId, int goal, int minutes) {
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final MissionEventManager missionManager;
    private final List<ScheduledMission> schedule;
    private final Map<Integer, String> lastTriggeredDate = new HashMap<>();

    public MissionScheduler(MissionEventManager missionManager, List<ScheduledMission> schedule) {
        this.missionManager = missionManager;
        this.schedule = schedule;
    }

    public static List<ScheduledMission> load(JavaPlugin plugin) {
        List<ScheduledMission> parsed = new ArrayList<>();
        for (Map<?, ?> entry : plugin.getConfig().getMapList("mission.schedule")) {
            try {
                String time = entry.get("time").toString();
                LocalTime.parse(time, TIME_FORMAT); // validate format eagerly
                MissionEventManager.Kind kind = MissionEventManager.Kind.valueOf(entry.get("kind").toString().toUpperCase());
                String targetId = entry.get("target").toString();
                int goal = ((Number) entry.get("goal")).intValue();
                int minutes = ((Number) entry.get("minutes")).intValue();
                parsed.add(new ScheduledMission(time, kind, targetId, goal, minutes));
            } catch (Exception e) {
                plugin.getLogger().warning("mission.schedule 설정 항목이 잘못되었습니다: " + entry);
            }
        }
        return List.copyOf(parsed);
    }

    /** Call roughly once a minute — checks the current server-local time against the schedule. */
    public void checkNow() {
        if (missionManager.isActive() || schedule.isEmpty()) {
            return;
        }
        String nowHHmm = TIME_FORMAT.format(LocalTime.now());
        String today = LocalDate.now().toString();

        for (int i = 0; i < schedule.size(); i++) {
            ScheduledMission entry = schedule.get(i);
            if (!entry.time().equals(nowHHmm) || today.equals(lastTriggeredDate.get(i))) {
                continue;
            }
            lastTriggeredDate.put(i, today);
            missionManager.start(entry.kind(), entry.targetId(), entry.goal(), entry.minutes());
            return;
        }
    }
}

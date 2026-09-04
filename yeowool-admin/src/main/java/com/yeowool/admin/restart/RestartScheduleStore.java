package com.yeowool.admin.restart;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * This server's daily auto-restart times (see {@link com.yeowool.admin.restart.ScheduledRestartTask}) —
 * one row per {@code restart.this-server-id} in {@code yw_server_restart_schedule}, so lobby/town/wild
 * can each run their own schedule. Starts from {@code config.yml}'s {@code restart.default-times} until
 * an admin sets something through {@code /서버재부팅설정}, same "config default, DB override" pattern as
 * {@code AttendanceRewardStore}.
 */
public final class RestartScheduleStore {

    public enum SetResult { SUCCESS, INVALID_FORMAT }

    private final JavaPlugin plugin;
    private final RestartScheduleRepository repository;
    private final ExecutorService executor;
    private final String serverId;
    private volatile List<LocalTime> times;

    public RestartScheduleStore(JavaPlugin plugin, RestartScheduleRepository repository, ExecutorService executor,
                                 String serverId, String defaultTimesCsv) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
        this.serverId = serverId;
        this.times = parse(defaultTimesCsv);
    }

    public void loadIntoCache() throws SQLException {
        String stored = repository.load(serverId);
        if (stored != null) {
            times = parse(stored);
        }
    }

    public List<LocalTime> times() {
        return times;
    }

    /** {@code csv}: one or more "HH:mm" times separated by commas. */
    public SetResult set(String csv) {
        List<LocalTime> parsed;
        try {
            parsed = parse(csv);
        } catch (DateTimeParseException e) {
            return SetResult.INVALID_FORMAT;
        }
        if (parsed.isEmpty()) {
            return SetResult.INVALID_FORMAT;
        }
        times = parsed;
        persist(csv);
        return SetResult.SUCCESS;
    }

    public void clear() {
        times = List.of();
        persist("");
    }

    private void persist(String csv) {
        executor.execute(() -> {
            try {
                repository.save(serverId, csv);
            } catch (SQLException e) {
                plugin.getLogger().severe("자동 재부팅 일정 저장 실패 (" + serverId + "): " + e.getMessage());
            }
        });
    }

    private List<LocalTime> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<LocalTime> parsed = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parsed.add(LocalTime.parse(trimmed));
            }
        }
        return parsed;
    }
}

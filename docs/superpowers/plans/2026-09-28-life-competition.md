# 생활 대회 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the per-server daily fishing competition with a cross-server daily life competition that rotates fishing → mining → hunting → farming and ranks by combined action count.

**Architecture:** New package `com.yeowool.life.competition`. A pure `CompetitionSchedule` (tested) derives today's window and activity from the clock, so all servers agree without coordination. Each server buffers +1 scores in memory and upserts them every 10s; after the window ends (+30s grace) one server wins a conditional UPDATE and queues rewards through `core.payouts()`; every server announces results by polling paid competitions.

**Tech Stack:** Paper 1.21.4, Java 21, MySQL via core `DataSource`, core `PayoutService`, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-28-dex-competition-worldboss-mounts-design.md` (section 2)

## Global Constraints

- Build from `C:/Users/apple/OneDrive/Desktop/YEOWOOL_PLUGIN`: `./gradlew :yeowool-life:build` (Task 3 also `:yeowool-community:build`). OneDrive lock → `rm -rf yeowool-*/build/test-results/test/binary` and rerun.
- JDBC only on worker threads; Bukkit API on the main thread. `core.payouts().enqueue(...)` is blocking JDBC — worker thread only.
- Player-visible text Korean via `MessageService` keys (MiniMessage; placeholder names use `_`, not `-`); user values via `Placeholder.unparsed`.
- Commits: stage only the task's files (never `git add -A`/`.`, never `homepage-plan.md`), never `--amend`, message ends with a blank line then exactly `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Do not dispatch subagents.

---

### Task 1: CompetitionActivity + CompetitionSchedule + tests

**Files:**
- Create: `yeowool-life/src/main/java/com/yeowool/life/competition/CompetitionActivity.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/competition/CompetitionSchedule.java`
- Test: `yeowool-life/src/test/java/com/yeowool/life/competition/CompetitionScheduleTest.java`

**Interfaces:**
- Produces: `enum CompetitionActivity { FISHING, MINING, HUNTING, FARMING }` with `key()`, `label()`, `scoreLabel()`, `unit()`, `static Optional<CompetitionActivity> byKey(String)`.
- `CompetitionSchedule(ZoneId zone, int startHour, int durationMinutes, List<CompetitionActivity> rotation)`; `record Window(LocalDate date, CompetitionActivity activity, ZonedDateTime start, ZonedDateTime end)` with `String dateKey()`, `boolean contains(ZonedDateTime)`; `Window windowFor(LocalDate)`, `Optional<Window> activeAt(ZonedDateTime)`, `Window nextAfter(ZonedDateTime)`.

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.life.competition;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompetitionScheduleTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final List<CompetitionActivity> ROTATION = List.of(
            CompetitionActivity.FISHING, CompetitionActivity.MINING, CompetitionActivity.HUNTING, CompetitionActivity.FARMING);

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE);
    }

    @Test
    void activityRotatesByEpochDay() {
        CompetitionSchedule schedule = new CompetitionSchedule(ZONE, 18, 60, ROTATION);
        LocalDate day = LocalDate.of(2026, 9, 28);
        for (int i = 0; i < 8; i++) {
            LocalDate date = day.plusDays(i);
            assertEquals(ROTATION.get((int) Math.floorMod(date.toEpochDay(), 4L)), schedule.windowFor(date).activity());
        }
        assertEquals(schedule.windowFor(day).activity(), schedule.windowFor(day.plusDays(4)).activity());
    }

    @Test
    void activeOnlyInsideTheWindow() {
        CompetitionSchedule schedule = new CompetitionSchedule(ZONE, 18, 60, ROTATION);
        assertTrue(schedule.activeAt(at(2026, 9, 28, 17, 59)).isEmpty());
        Optional<CompetitionSchedule.Window> active = schedule.activeAt(at(2026, 9, 28, 18, 0));
        assertTrue(active.isPresent());
        assertEquals("2026-09-28", active.get().dateKey());
        assertTrue(schedule.activeAt(at(2026, 9, 28, 18, 59)).isPresent());
        assertTrue(schedule.activeAt(at(2026, 9, 28, 19, 0)).isEmpty());
    }

    @Test
    void windowCrossingMidnightBelongsToItsStartDate() {
        CompetitionSchedule schedule = new CompetitionSchedule(ZONE, 23, 120, ROTATION);
        Optional<CompetitionSchedule.Window> active = schedule.activeAt(at(2026, 9, 29, 0, 30));
        assertTrue(active.isPresent());
        assertEquals("2026-09-28", active.get().dateKey());
    }

    @Test
    void nextAfterIsTodayBeforeStartOtherwiseTomorrow() {
        CompetitionSchedule schedule = new CompetitionSchedule(ZONE, 18, 60, ROTATION);
        assertEquals(LocalDate.of(2026, 9, 28), schedule.nextAfter(at(2026, 9, 28, 10, 0)).date());
        assertEquals(LocalDate.of(2026, 9, 29), schedule.nextAfter(at(2026, 9, 28, 18, 30)).date());
        assertEquals(LocalDate.of(2026, 9, 29), schedule.nextAfter(at(2026, 9, 28, 20, 0)).date());
    }

    @Test
    void emptyRotationFallsBackToAllActivitiesAndBadNumbersAreClamped() {
        CompetitionSchedule schedule = new CompetitionSchedule(ZONE, 99, 0, List.of());
        CompetitionSchedule.Window window = schedule.windowFor(LocalDate.of(2026, 9, 28));
        assertEquals(23, window.start().getHour());
        assertEquals(1, java.time.Duration.between(window.start(), window.end()).toMinutes());
    }

    @Test
    void activityKeysParse() {
        assertEquals(Optional.of(CompetitionActivity.MINING), CompetitionActivity.byKey("mining"));
        assertEquals(Optional.of(CompetitionActivity.FARMING), CompetitionActivity.byKey(" FARMING "));
        assertEquals(Optional.empty(), CompetitionActivity.byKey("cooking"));
    }
}
```

- [ ] **Step 2: Run** `./gradlew :yeowool-life:test --tests "com.yeowool.life.competition.CompetitionScheduleTest"` → FAIL (classes missing).

- [ ] **Step 3: Create `CompetitionActivity.java`**

```java
package com.yeowool.life.competition;

import java.util.Locale;
import java.util.Optional;

public enum CompetitionActivity {
    FISHING("fishing", "낚시", "잡은 물고기", "마리"),
    MINING("mining", "채광", "캔 광석", "개"),
    HUNTING("hunting", "사냥", "처치한 몹", "마리"),
    FARMING("farming", "농사", "수확한 작물", "개");

    private final String key;
    private final String label;
    private final String scoreLabel;
    private final String unit;

    CompetitionActivity(String key, String label, String scoreLabel, String unit) {
        this.key = key;
        this.label = label;
        this.scoreLabel = scoreLabel;
        this.unit = unit;
    }

    /** Same tag {@code PlayerRepeatableActionEvent} uses for mining/fishing/hunting, and the config key. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String scoreLabel() {
        return scoreLabel;
    }

    public String unit() {
        return unit;
    }

    public static Optional<CompetitionActivity> byKey(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (CompetitionActivity activity : values()) {
            if (activity.key.equals(normalized)) {
                return Optional.of(activity);
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Create `CompetitionSchedule.java`**

```java
package com.yeowool.life.competition;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Derives each day's competition purely from the clock and config — every
 * server computes the same window and activity, so nothing has to be
 * coordinated to start one. The activity rotates by epoch day.
 */
public final class CompetitionSchedule {

    public record Window(LocalDate date, CompetitionActivity activity, ZonedDateTime start, ZonedDateTime end) {

        /** yyyy-MM-dd of the start date — the competition's DB key. */
        public String dateKey() {
            return date.toString();
        }

        public boolean contains(ZonedDateTime time) {
            return !time.isBefore(start) && time.isBefore(end);
        }
    }

    private final ZoneId zone;
    private final int startHour;
    private final int durationMinutes;
    private final List<CompetitionActivity> rotation;

    public CompetitionSchedule(ZoneId zone, int startHour, int durationMinutes, List<CompetitionActivity> rotation) {
        this.zone = zone;
        this.startHour = Math.max(0, Math.min(23, startHour));
        this.durationMinutes = Math.max(1, Math.min(1439, durationMinutes));
        this.rotation = rotation.isEmpty() ? List.of(CompetitionActivity.values()) : List.copyOf(rotation);
    }

    public Window windowFor(LocalDate date) {
        CompetitionActivity activity = rotation.get((int) Math.floorMod(date.toEpochDay(), (long) rotation.size()));
        ZonedDateTime start = date.atTime(startHour, 0).atZone(zone);
        return new Window(date, activity, start, start.plusMinutes(durationMinutes));
    }

    public Optional<Window> activeAt(ZonedDateTime now) {
        LocalDate today = now.withZoneSameInstant(zone).toLocalDate();
        for (LocalDate date : List.of(today, today.minusDays(1))) {
            Window window = windowFor(date);
            if (window.contains(now)) {
                return Optional.of(window);
            }
        }
        return Optional.empty();
    }

    /** The next window that hasn't started yet. */
    public Window nextAfter(ZonedDateTime now) {
        LocalDate today = now.withZoneSameInstant(zone).toLocalDate();
        Window window = windowFor(today);
        return window.start().isAfter(now) ? window : windowFor(today.plusDays(1));
    }
}
```

- [ ] **Step 5: Run** the same test → PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add yeowool-life/src/main/java/com/yeowool/life/competition/CompetitionActivity.java yeowool-life/src/main/java/com/yeowool/life/competition/CompetitionSchedule.java yeowool-life/src/test/java/com/yeowool/life/competition/CompetitionScheduleTest.java
git commit -m "Add life competition activities and clock-derived schedule

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: CompetitionRepository + LifeCompetitionService + listener + harvest event

**Files:**
- Create: `yeowool-life/src/main/java/com/yeowool/life/competition/CompetitionRepository.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/competition/LifeCompetitionService.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/competition/LifeCompetitionListener.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/farming/LifeHarvestEvent.java`

**Interfaces:**
- Consumes: Task 1; `core.payouts().enqueue(UUID, long, String, String) throws SQLException` (worker thread); `com.yeowool.core.api.event.PlayerRepeatableActionEvent` (`getUuid()`, `getActionType()`); `com.yeowool.core.util.DurationFormat.humanize(long)`.
- Produces: `LifeCompetitionService(JavaPlugin, YeowoolCoreAPI, MessageService, CompetitionRepository, CompetitionSchedule, Map<Integer, Long> rewardsByRank, Executor)`, `record(Player, CompetitionActivity)` (main), `tick()` (main, every 10s), `settleAndAnnounce()` (worker, every minute), `showStatus(Player)` (main); `LifeHarvestEvent(Player)` with `getPlayer()`.

- [ ] **Step 1: Create `LifeHarvestEvent.java`**

```java
package com.yeowool.life.farming;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired once for every fully grown crop a player harvests — vanilla crops,
 * ItemsAdder custom crops and CustomCrops alike — so things like the life
 * competition can count harvests without each farming listener knowing them.
 */
public final class LifeHarvestEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public LifeHarvestEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
```

- [ ] **Step 2: Create `CompetitionRepository.java`**

```java
package com.yeowool.life.competition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Shared competition rows and cross-server scores. Payout is claimed by one conditional UPDATE. */
public final class CompetitionRepository {

    public record Standing(UUID player, String name, long score) {
    }

    public record Competition(String dateKey, String activity) {
    }

    public record Finished(String dateKey, String activity, long paidAt) {
    }

    public record ScoreDelta(String dateKey, UUID player, String name, long delta) {
    }

    private static final String COMPETITIONS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_life_competitions (
                comp_date VARCHAR(10) NOT NULL PRIMARY KEY,
                activity VARCHAR(16) NOT NULL,
                ends_at BIGINT NOT NULL,
                paid TINYINT(1) NOT NULL DEFAULT 0,
                paid_at BIGINT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String SCORES_DDL = """
            CREATE TABLE IF NOT EXISTS yw_life_competition_scores (
                comp_date VARCHAR(10) NOT NULL,
                player CHAR(36) NOT NULL,
                name VARCHAR(16) NOT NULL,
                score BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (comp_date, player),
                INDEX idx_date_score (comp_date, score)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final DataSource dataSource;

    public CompetitionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(COMPETITIONS_DDL);
            statement.executeUpdate(SCORES_DDL);
        }
    }

    public void ensureCompetition(String dateKey, String activity, long endsAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT IGNORE INTO yw_life_competitions (comp_date, activity, ends_at) VALUES (?, ?, ?)")) {
            ps.setString(1, dateKey);
            ps.setString(2, activity);
            ps.setLong(3, endsAt);
            ps.executeUpdate();
        }
    }

    public void addScores(List<ScoreDelta> deltas) throws SQLException {
        if (deltas.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_life_competition_scores (comp_date, player, name, score) VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE score = score + VALUES(score), name = VALUES(name)")) {
            for (ScoreDelta delta : deltas) {
                ps.setString(1, delta.dateKey());
                ps.setString(2, delta.player().toString());
                ps.setString(3, delta.name());
                ps.setLong(4, delta.delta());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<Standing> top(String dateKey, int limit) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT player, name, score FROM yw_life_competition_scores WHERE comp_date = ? "
                             + "ORDER BY score DESC, player LIMIT ?")) {
            ps.setString(1, dateKey);
            ps.setInt(2, limit);
            List<Standing> standings = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    standings.add(new Standing(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)));
                }
            }
            return standings;
        }
    }

    public long scoreOf(String dateKey, UUID player) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT score FROM yw_life_competition_scores WHERE comp_date = ? AND player = ?")) {
            ps.setString(1, dateKey);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public List<Competition> unpaidEnded(long endedBefore) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT comp_date, activity FROM yw_life_competitions WHERE paid = 0 AND ends_at <= ?")) {
            ps.setLong(1, endedBefore);
            List<Competition> competitions = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    competitions.add(new Competition(rs.getString(1), rs.getString(2)));
                }
            }
            return competitions;
        }
    }

    /** True only for the one caller that flips this ended competition to paid. */
    public boolean claimPayout(String dateKey, long endedBefore, long now) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_life_competitions SET paid = 1, paid_at = ? WHERE comp_date = ? AND paid = 0 AND ends_at <= ?")) {
            ps.setLong(1, now);
            ps.setString(2, dateKey);
            ps.setLong(3, endedBefore);
            return ps.executeUpdate() == 1;
        }
    }

    public List<Finished> paidSince(long since) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT comp_date, activity, paid_at FROM yw_life_competitions WHERE paid = 1 AND paid_at > ? ORDER BY paid_at")) {
            ps.setLong(1, since);
            List<Finished> finished = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    finished.add(new Finished(rs.getString(1), rs.getString(2), rs.getLong(3)));
                }
            }
            return finished;
        }
    }
}
```

- [ ] **Step 3: Create `LifeCompetitionService.java`**

```java
package com.yeowool.life.competition;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Daily cross-server life competition. Scores are buffered per server and
 * added to the shared table every 10 seconds; once a window has ended (plus
 * a grace period for the last buffers) exactly one server claims the payout
 * and queues rewards through the core payout ledger, and every server
 * announces results it hasn't announced yet.
 */
public final class LifeCompetitionService {

    private record ScoreKey(String dateKey, UUID player) {
    }

    private static final long SETTLE_GRACE_MILLIS = 30_000;
    private static final String SOURCE = "YeowoolLife";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final CompetitionRepository repository;
    private final CompetitionSchedule schedule;
    private final Map<Integer, Long> rewardsByRank;
    private final Executor executor;

    // main thread only
    private Map<ScoreKey, Long> pending = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<String, CompetitionSchedule.Window> pendingWindows = new HashMap<>();
    private String announcedStart;

    private volatile long resultsAnnouncedUntil = System.currentTimeMillis();

    public LifeCompetitionService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, CompetitionRepository repository,
                                  CompetitionSchedule schedule, Map<Integer, Long> rewardsByRank, Executor executor) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.schedule = schedule;
        this.rewardsByRank = rewardsByRank;
        this.executor = executor;
    }

    /** Main thread: one completed action; counted only while that activity's competition is running. */
    public void record(Player player, CompetitionActivity activity) {
        Optional<CompetitionSchedule.Window> window = schedule.activeAt(ZonedDateTime.now());
        if (window.isEmpty() || window.get().activity() != activity) {
            return;
        }
        pending.merge(new ScoreKey(window.get().dateKey(), player.getUniqueId()), 1L, Long::sum);
        names.put(player.getUniqueId(), player.getName());
        pendingWindows.put(window.get().dateKey(), window.get());
    }

    /** Main thread, every 10 seconds: start announcement and handing buffered scores to the DB. */
    public void tick() {
        ZonedDateTime now = ZonedDateTime.now();
        schedule.activeAt(now).ifPresent(window -> {
            if (window.dateKey().equals(announcedStart)) {
                return;
            }
            announcedStart = window.dateKey();
            // Only announce right at the start — a server restarted mid-competition stays quiet.
            if (Duration.between(window.start(), now).toSeconds() < 60) {
                messages.broadcast("competition.started",
                        Placeholder.unparsed("activity", window.activity().label()),
                        Placeholder.unparsed("minutes", String.valueOf(Duration.between(window.start(), window.end()).toMinutes())),
                        Placeholder.unparsed("score_label", window.activity().scoreLabel()));
            }
        });
        flush();
    }

    private void flush() {
        if (pending.isEmpty()) {
            return;
        }
        Map<ScoreKey, Long> batch = pending;
        pending = new HashMap<>();
        Map<UUID, String> batchNames = new HashMap<>(names);
        names.clear();
        List<CompetitionSchedule.Window> windows = new ArrayList<>(pendingWindows.values());
        pendingWindows.clear();
        List<CompetitionRepository.ScoreDelta> deltas = new ArrayList<>();
        batch.forEach((key, delta) -> deltas.add(new CompetitionRepository.ScoreDelta(
                key.dateKey(), key.player(), batchNames.getOrDefault(key.player(), "?"), delta)));
        executor.execute(() -> {
            try {
                for (CompetitionSchedule.Window window : windows) {
                    repository.ensureCompetition(window.dateKey(), window.activity().key(), window.end().toInstant().toEpochMilli());
                }
                repository.addScores(deltas);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "생활 대회 점수 저장 실패 — 이번 묶음 " + deltas.size() + "건 유실", e);
            }
        });
    }

    /** Worker thread, every minute on every server: pay out ended competitions once, then announce new results. */
    public void settleAndAnnounce() {
        long now = System.currentTimeMillis();
        try {
            for (CompetitionRepository.Competition competition : repository.unpaidEnded(now - SETTLE_GRACE_MILLIS)) {
                if (!repository.claimPayout(competition.dateKey(), now - SETTLE_GRACE_MILLIS, now)) {
                    continue;
                }
                String label = CompetitionActivity.byKey(competition.activity()).map(CompetitionActivity::label).orElse(competition.activity());
                int ranks = rewardsByRank.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
                List<CompetitionRepository.Standing> top = repository.top(competition.dateKey(), ranks);
                // ponytail: the claim and these inserts aren't one transaction — a crash in between loses the rewards; each is logged to pay by hand.
                for (int i = 0; i < top.size(); i++) {
                    long reward = rewardsByRank.getOrDefault(i + 1, 0L);
                    CompetitionRepository.Standing standing = top.get(i);
                    String reason = "생활 대회(" + label + ") " + (i + 1) + "위";
                    try {
                        core.payouts().enqueue(standing.player(), reward, SOURCE, reason);
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "생활 대회 보상 장부 기록 실패 — 수동 지급 필요: "
                                + standing.player() + " " + reward + "온 (" + reason + ")", e);
                    }
                }
            }
            List<CompetitionRepository.Finished> finished = repository.paidSince(resultsAnnouncedUntil);
            if (finished.isEmpty()) {
                return;
            }
            resultsAnnouncedUntil = finished.get(finished.size() - 1).paidAt();
            Map<CompetitionRepository.Finished, List<CompetitionRepository.Standing>> results = new LinkedHashMap<>();
            for (CompetitionRepository.Finished competition : finished) {
                results.put(competition, repository.top(competition.dateKey(), 3));
            }
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> results.forEach(this::announceResult));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "생활 대회 정산/공지 처리 실패", e);
        }
    }

    private void announceResult(CompetitionRepository.Finished competition, List<CompetitionRepository.Standing> top) {
        CompetitionActivity activity = CompetitionActivity.byKey(competition.activity()).orElse(CompetitionActivity.FISHING);
        if (top.isEmpty()) {
            messages.broadcast("competition.result-empty", Placeholder.unparsed("activity", activity.label()));
            return;
        }
        messages.broadcast("competition.result-header", Placeholder.unparsed("activity", activity.label()));
        for (int i = 0; i < top.size(); i++) {
            CompetitionRepository.Standing standing = top.get(i);
            messages.broadcast("competition.result-line",
                    Placeholder.unparsed("rank", String.valueOf(i + 1)),
                    Placeholder.unparsed("player", standing.name()),
                    Placeholder.unparsed("score", String.format("%,d", standing.score())),
                    Placeholder.unparsed("unit", activity.unit()));
        }
    }

    /** Main thread: {@code /생활대회}. */
    public void showStatus(Player player) {
        ZonedDateTime now = ZonedDateTime.now();
        Optional<CompetitionSchedule.Window> active = schedule.activeAt(now);
        if (active.isEmpty()) {
            CompetitionSchedule.Window next = schedule.nextAfter(now);
            messages.send(player, "competition.next",
                    Placeholder.unparsed("time", TIME.format(next.start())),
                    Placeholder.unparsed("activity", next.activity().label()),
                    Placeholder.unparsed("score_label", next.activity().scoreLabel()));
            return;
        }
        CompetitionSchedule.Window window = active.get();
        UUID uuid = player.getUniqueId();
        long unsaved = pending.getOrDefault(new ScoreKey(window.dateKey(), uuid), 0L);
        String remaining = DurationFormat.humanize(Duration.between(now, window.end()).toMillis());
        executor.execute(() -> {
            List<CompetitionRepository.Standing> top;
            long mine;
            try {
                top = repository.top(window.dateKey(), 5);
                mine = repository.scoreOf(window.dateKey(), uuid) + unsaved;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "생활 대회 순위 조회 실패", e);
                reply(uuid, p -> messages.send(p, "competition.error"));
                return;
            }
            reply(uuid, p -> {
                messages.send(p, "competition.status-header",
                        Placeholder.unparsed("activity", window.activity().label()),
                        Placeholder.unparsed("remaining", remaining));
                if (top.isEmpty()) {
                    messages.send(p, "competition.status-empty");
                }
                for (int i = 0; i < top.size(); i++) {
                    messages.send(p, "competition.status-line",
                            Placeholder.unparsed("rank", String.valueOf(i + 1)),
                            Placeholder.unparsed("player", top.get(i).name()),
                            Placeholder.unparsed("score", String.format("%,d", top.get(i).score())),
                            Placeholder.unparsed("unit", window.activity().unit()));
                }
                messages.send(p, "competition.status-mine",
                        Placeholder.unparsed("score", String.format("%,d", mine)),
                        Placeholder.unparsed("unit", window.activity().unit()),
                        Placeholder.unparsed("score_label", window.activity().scoreLabel()));
            });
        });
    }

    private void reply(UUID uuid, java.util.function.Consumer<Player> action) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                action.accept(player);
            }
        });
    }
}
```

- [ ] **Step 4: Create `LifeCompetitionListener.java`**

```java
package com.yeowool.life.competition;

import com.yeowool.core.api.event.PlayerRepeatableActionEvent;
import com.yeowool.life.farming.LifeHarvestEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Mining/fishing/hunting arrive as {@link PlayerRepeatableActionEvent} (tag = activity key); harvests as {@link LifeHarvestEvent}. */
public final class LifeCompetitionListener implements Listener {

    private final LifeCompetitionService service;

    public LifeCompetitionListener(LifeCompetitionService service) {
        this.service = service;
    }

    @EventHandler
    public void onAction(PlayerRepeatableActionEvent event) {
        CompetitionActivity.byKey(event.getActionType()).ifPresent(activity -> {
            Player player = Bukkit.getPlayer(event.getUuid());
            if (player != null) {
                service.record(player, activity);
            }
        });
    }

    @EventHandler
    public void onHarvest(LifeHarvestEvent event) {
        service.record(event.getPlayer(), CompetitionActivity.FARMING);
    }
}
```

- [ ] **Step 5: Build** — `./gradlew :yeowool-life:build` → BUILD SUCCESSFUL (nothing wires these yet).

- [ ] **Step 6: Commit**

```bash
git add yeowool-life/src/main/java/com/yeowool/life/farming/LifeHarvestEvent.java yeowool-life/src/main/java/com/yeowool/life/competition/CompetitionRepository.java yeowool-life/src/main/java/com/yeowool/life/competition/LifeCompetitionService.java yeowool-life/src/main/java/com/yeowool/life/competition/LifeCompetitionListener.java
git commit -m "Add cross-server life competition service and repository

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Wire it in, fire harvest events, remove the old fishing competition

**Files:**
- Modify: `yeowool-life/src/main/java/com/yeowool/life/farming/FarmingListener.java`
- Modify: `yeowool-life/src/main/java/com/yeowool/life/farming/custom/CustomFarmingListener.java`
- Modify: `yeowool-life/src/main/java/com/yeowool/life/farming/customcrops/CustomCropsHarvestListener.java`
- Modify: `yeowool-life/src/main/java/com/yeowool/life/fishing/FishingListener.java`
- Modify: `yeowool-life/src/main/java/com/yeowool/life/fishing/customfishing/CustomFishingCatchListener.java`
- Delete: `yeowool-life/src/main/java/com/yeowool/life/fishing/FishingCompetitionManager.java`
- Delete: `yeowool-life/src/main/java/com/yeowool/life/fishing/FishingCompetitionCommand.java`
- Modify: `yeowool-life/src/main/java/com/yeowool/life/YeowoolLife.java`
- Modify: `yeowool-life/src/main/resources/config.yml`, `messages.yml`, `plugin.yml`
- Modify: `yeowool-community/src/main/java/com/yeowool/community/menu/MenuHubGui.java`
- Modify: `yeowool-core/src/main/resources/help.yml`

**Interfaces:**
- Consumes: Tasks 1–2.

- [ ] **Step 1: Fire `LifeHarvestEvent`** — in each of the three farming listeners, right after the line that adds `"life.farming.harvested"` (the `getIfLoaded(...).ifPresent(...)` statement that follows the fully-grown check and where a `Player player` variable is in scope), add:

```java
        Bukkit.getPluginManager().callEvent(new LifeHarvestEvent(player));
```

Import `org.bukkit.Bukkit` if missing and `com.yeowool.life.farming.LifeHarvestEvent` in the two sub-packages (`farming.custom`, `farming.customcrops`). In `FarmingListener` the statement is inside `onBreak`-style handler after `core.playerData().getIfLoaded(player.getUniqueId()).ifPresent(playerData -> { ... });`.

- [ ] **Step 2: Remove the old fishing competition from the fishing listeners**
  - `FishingListener.java`: remove the `FishingCompetitionManager competitionManager` constructor parameter (last parameter), its field and assignment, the `competitionManager.recordCatch(player, species.name(), sizeCm);` line, and any javadoc sentence that mentions `competitionManager` (keep the rest of the doc).
  - `CustomFishingCatchListener.java`: same — remove the import, field, last constructor parameter and assignment, the `competitionManager.recordCatch(...)` line, and the javadoc mention of "the daily 낚시대회" (reword that sentence without it). Keep the size-record statistic lines.
  - Delete `FishingCompetitionManager.java` and `FishingCompetitionCommand.java` (`git rm`).

- [ ] **Step 3: `YeowoolLife.java`**
  - Remove the imports of `FishingCompetitionCommand` and `FishingCompetitionManager`.
  - Remove the whole block from `Map<Integer, Long> competitionRewards = new HashMap<>();` through `competitionManager.scheduleNextStart();`.
  - Remove `, competitionManager` from the `new FishingListener(...)` and `new CustomFishingCatchListener(...)` calls.
  - Remove the `var competitionCommand = getCommand("낚시대회"); if (...) {...}` block.
  - Change the CustomFishing info log text `"... 어부 XP/땅 XP/도감/낚시대회는 그대로 연결됨."` to `"... 어부 XP/땅 XP/도감/생활 대회는 그대로 연결됨."`.
  - Keep `HashMap`/`Map` imports only if still used elsewhere (compiler will tell).
  - Add imports `com.yeowool.life.competition.CompetitionActivity`, `CompetitionRepository`, `CompetitionSchedule`, `LifeCompetitionListener`, `LifeCompetitionService`, `java.time.ZoneId` (and `java.util.HashMap`, `java.util.ArrayList`, `org.bukkit.entity.Player`, `org.bukkit.configuration.ConfigurationSection` if missing).
  - Call `enableLifeCompetition(core, messages);` right after `enableTreasureMaps(core, messages);`, and add this method below `enableTreasureMaps`:

```java
    /** 생활 대회 — 매일 같은 시각, 세 서버 합산 행동 횟수로 순위 (종목은 날짜별 순환). */
    private void enableLifeCompetition(YeowoolCoreAPI core, MessageManager messages) {
        var config = getConfig();
        if (!config.getBoolean("life-competition.enabled", true)) {
            return;
        }
        CompetitionRepository repository = new CompetitionRepository(core.dataSource());
        try {
            repository.createTables();
        } catch (Exception e) {
            getLogger().severe("생활 대회 데이터베이스 초기화 실패 — 생활 대회를 끕니다: " + e.getMessage());
            return;
        }
        List<CompetitionActivity> rotation = new ArrayList<>();
        for (String key : config.getStringList("life-competition.rotation")) {
            CompetitionActivity.byKey(key).ifPresentOrElse(rotation::add, () -> getLogger().warning(
                    "life-competition.rotation의 '" + key + "'는 fishing/mining/hunting/farming 중 하나여야 합니다 — 건너뜁니다."));
        }
        Map<Integer, Long> rewards = new HashMap<>();
        ConfigurationSection rewardSection = config.getConfigurationSection("life-competition.rewards");
        if (rewardSection != null) {
            for (String rank : rewardSection.getKeys(false)) {
                try {
                    rewards.put(Integer.parseInt(rank), rewardSection.getLong(rank));
                } catch (NumberFormatException e) {
                    getLogger().warning("life-competition.rewards의 '" + rank + "'는 순위 숫자여야 합니다 — 건너뜁니다.");
                }
            }
        }
        CompetitionSchedule schedule = new CompetitionSchedule(ZoneId.systemDefault(),
                config.getInt("life-competition.start-hour", 18),
                config.getInt("life-competition.duration-minutes", 60),
                rotation);
        LifeCompetitionService service = new LifeCompetitionService(this, core, messages, repository, schedule, rewards, executor);
        getServer().getPluginManager().registerEvents(new LifeCompetitionListener(service), this);
        var command = getCommand("생활대회");
        if (command != null) {
            command.setExecutor((sender, cmd, label, args) -> {
                if (sender instanceof Player player) {
                    service.showStatus(player);
                } else {
                    messages.send(sender, "general.player-only");
                }
                return true;
            });
        }
        getServer().getScheduler().runTaskTimer(this, service::tick, 20L * 10, 20L * 10);
        getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::settleAndAnnounce), 20L * 60, 20L * 60);
    }
```

- [ ] **Step 4: `config.yml`** — delete the `competition:` block under `fishing:` together with its 3 comment lines above it (the lines starting `# 매일 start-hour(서버 시간, 24시 기준)에 자동 시작해서...` through `rewards: 3: 15000`). Append at the end of the file:

```yaml

# 생활 대회 — 매일 start-hour시(서버 시간)부터 duration-minutes분 동안, 세 서버 합산 행동 횟수로 순위를 겨룹니다.
# 종목은 날짜마다 rotation 순서로 바뀝니다(fishing=잡은 물고기, mining=캔 광석, hunting=처치한 몹, farming=수확한 작물).
# 1~3위(rewards)에게 온 지급 — 오프라인/다른 서버여도 다음 접속 때 받습니다. 세 서버 설정을 똑같이 맞춰주세요.
life-competition:
  enabled: true
  start-hour: 18
  duration-minutes: 60
  rotation: [fishing, mining, hunting, farming]
  rewards:
    1: 50000
    2: 30000
    3: 15000
```

- [ ] **Step 5: `messages.yml`** — delete the `competition-reward:` line under `fishing:`; append:

```yaml

competition:
  started: "<gold>[생활 대회]</gold> <yellow><activity> 대회가 시작되었습니다! <minutes>분 동안 세 서버 합산 <score_label> 수로 순위를 겨룹니다. (/생활대회)</yellow>"
  status-header: "<gold>[생활 대회]</gold> <yellow><activity> 대회 진행 중 — 남은 시간 <remaining></yellow>"
  status-line: "<gray><rank>위</gray> <white><player></white> <yellow><score><unit></yellow>"
  status-empty: "<gray>아직 기록이 없습니다.</gray>"
  status-mine: "<aqua>내 기록: <score><unit> (<score_label>)</aqua>"
  next: "<gold>[생활 대회]</gold> <gray>다음 대회: <time> <activity> (<score_label> 수 경쟁)</gray>"
  result-header: "<gold>[생활 대회]</gold> <yellow><activity> 대회 결과</yellow>"
  result-line: "<gray><rank>위</gray> <white><player></white> <yellow><score><unit></yellow>"
  result-empty: "<gold>[생활 대회]</gold> <gray><activity> 대회가 참가자 없이 끝났습니다.</gray>"
  error: "<red>처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.</red>"
```

- [ ] **Step 6: `plugin.yml`** — replace the `낚시대회:` command entry with:

```yaml
  생활대회:
    description: 오늘의 생활 대회(낚시/채광/사냥/농사) 현황과 순위를 확인합니다
```

- [ ] **Step 7: Community menu** — in `MenuHubGui.java` `lifeCategory`, change `dispatchEntry("낚시대회", "낚시대회", "fishing_expansion:golden_fishing_rod")` to `dispatchEntry("생활대회", "생활대회", "fishing_expansion:golden_fishing_rod")`.

- [ ] **Step 8: Help** — in `yeowool-core/src/main/resources/help.yml`, replace the line `      - "/낚시대회 - 진행 중인 낚시 대회 현황"` with `      - "/생활대회 - 매일 18시 생활 대회(낚시·채광·사냥·농사 순환, 세 서버 합산) 현황과 순위"`.

- [ ] **Step 9: Build** — `grep -rn "낚시대회\|FishingCompetition" --include=*.java --include=*.yml yeowool-*/src` should print nothing; then `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 10: Commit** (stage the modified files and the two deletions explicitly)

```bash
git add yeowool-life/src/main/java/com/yeowool/life/farming/FarmingListener.java yeowool-life/src/main/java/com/yeowool/life/farming/custom/CustomFarmingListener.java yeowool-life/src/main/java/com/yeowool/life/farming/customcrops/CustomCropsHarvestListener.java yeowool-life/src/main/java/com/yeowool/life/fishing/FishingListener.java yeowool-life/src/main/java/com/yeowool/life/fishing/customfishing/CustomFishingCatchListener.java yeowool-life/src/main/java/com/yeowool/life/fishing/FishingCompetitionManager.java yeowool-life/src/main/java/com/yeowool/life/fishing/FishingCompetitionCommand.java yeowool-life/src/main/java/com/yeowool/life/YeowoolLife.java yeowool-life/src/main/resources/config.yml yeowool-life/src/main/resources/messages.yml yeowool-life/src/main/resources/plugin.yml yeowool-community/src/main/java/com/yeowool/community/menu/MenuHubGui.java yeowool-core/src/main/resources/help.yml
git commit -m "Replace per-server fishing competition with daily cross-server life competition

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

# 마을 연합 4단계 — 연합 랭킹 · 연합대항 · 레벨업 알림 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a federation ranking (`/연합 랭킹`), a timed federation competition (`/연합대항`, manual + weekly auto, top-3 rewards to federation banks) that works identically across 3 servers, and a cross-server level-up notice to all federation members.

**Architecture:** Competition state lives in two new DB tables (event row + per-federation activity snapshot taken at start); progress = current activity − snapshot, so any server computes the same standings. A single conditional `UPDATE ... WHERE ended = 0` lets exactly one server finish an event and pay rewards; a UNIQUE `week_key` makes the weekly auto-start happen once. Each server polls once a minute (inside the existing 60 s federation task) and announces start/results to its own players. Level-up notices reuse the phase-2 chat delivery (local + proxy `yeowool:targeted`).

**Tech Stack:** Paper 1.21.4 API, raw JDBC via `YeowoolCoreAPI.dataSource()` (MySQL), JUnit 5, `java.time`.

**Spec:** `docs/superpowers/specs/2026-09-26-village-federation-ranking-event-design.md`

## Global Constraints

- Ranking: `ORDER BY level DESC, activity DESC`, TOP 10, line format `순위. 이름 Lv.N (활동량 X)`.
- Competition score = `yw_federations.activity − COALESCE(snapshot activity, 0)`; federations with score ≤ 0 are never ranked/rewarded.
- Rewards: `event.rewards` (default `[100000, 50000, 30000]`) deposited into the 1st/2nd/3rd federation banks via `FederationManager.deposit(UUID, long)`.
- Weekly auto: `event.auto.enabled: true`, `day-of-week: SATURDAY`, `start-time: "20:00"`, `duration-minutes: 1440`, server time zone; week key format `YYYY-Www` (ISO week-based year + 2-digit ISO week).
- Manual: `/연합대항 시작 <분>` and `/연합대항 종료` need permission `yeowool.event.manage`; `/연합대항 정보` (or no args) is for everyone. Starting while an event is active is refused.
- Exactly-once: event end = `UPDATE yw_federation_events SET ended = 1, ended_at = ? WHERE id = ? AND ended = 0` (only the server whose update returns 1 pays and stores `result`); weekly start = INSERT with UNIQUE `week_key` (MySQL duplicate-key error 1062 means "already started", not a failure).
- Announcements: each server broadcasts to its own players via `MessageService.broadcast` on the main thread; a server marks the latest event id as already announced at startup so restarts don't repeat.
- Level-up notice goes to every owner/resident of the federation via `FederationChatService.deliverToFederation(Player via, UUID federationId, Component message)`.
- JDBC only on the federation executor; Bukkit messages/broadcasts on the main thread. DB errors: `Level.SEVERE` with the exception.
- Only zero-Bukkit/zero-JDBC classes get JUnit tests. Money shown as `String.format("%,d", n)` + `온`.
- Git: stage only named files (never `git add -A`/`git add .`), never amend, end commit messages with a blank line + `Co-Authored-By: <your model> <noreply@anthropic.com>`. Never start/stop servers or send RCON commands. Only Task 6 deploys.
- OneDrive lock: if gradle fails with `Unable to delete directory '...\build\test-results\test\binary'`, `rm -rf` that folder and re-run.

---

### Task 1: `FederationEventSchedule` — weekly window (pure, TDD)

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/event/FederationEventSchedule.java`
- Test: `yeowool-federation/src/test/java/com/yeowool/federation/event/FederationEventScheduleTest.java`

**Interfaces:**
- Produces: `record FederationEventSchedule(DayOfWeek dayOfWeek, LocalTime startTime, int durationMinutes)` with nested `record Window(String weekKey, long startMillis, long endMillis)` and `Optional<Window> activeWindow(ZonedDateTime now)`.

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.federation.event;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationEventScheduleTest {

    // Saturday 20:00 for 24 hours. 2026-09-26 is a Saturday in ISO week 2026-W39.
    private final FederationEventSchedule schedule =
            new FederationEventSchedule(DayOfWeek.SATURDAY, LocalTime.of(20, 0), 1440);

    private static ZonedDateTime at(int month, int day, int hour) {
        return ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, ZoneOffset.UTC);
    }

    @Test
    void insideTheWindowOnTheStartDay() {
        var window = schedule.activeWindow(at(9, 26, 21)).orElseThrow();

        assertEquals("2026-W39", window.weekKey());
        assertEquals(at(9, 26, 20).toInstant().toEpochMilli(), window.startMillis());
        assertEquals(at(9, 27, 20).toInstant().toEpochMilli(), window.endMillis());
    }

    @Test
    void windowSpanningMidnightStillBelongsToTheStartWeek() {
        var window = schedule.activeWindow(at(9, 27, 10)).orElseThrow();

        assertEquals("2026-W39", window.weekKey());
    }

    @Test
    void beforeTheStartTimeOnTheStartDayIsOutside() {
        assertTrue(schedule.activeWindow(at(9, 26, 19)).isEmpty());
    }

    @Test
    void endIsExclusive() {
        assertTrue(schedule.activeWindow(at(9, 27, 20)).isEmpty());
    }

    @Test
    void midweekIsOutside() {
        assertTrue(schedule.activeWindow(at(9, 30, 12)).isEmpty());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :yeowool-federation:test`
Expected: FAIL — `FederationEventSchedule` does not exist.

- [ ] **Step 3: Implement**

```java
package com.yeowool.federation.event;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/** Weekly 연합대항 auto-start window (server time zone). Pure — unit-tested. */
public record FederationEventSchedule(DayOfWeek dayOfWeek, LocalTime startTime, int durationMinutes) {

    /** {@code weekKey} is unique per scheduled week, so 3 servers racing to auto-start create one event. */
    public record Window(String weekKey, long startMillis, long endMillis) {
    }

    /** The scheduled window that contains {@code now}, if any. */
    public Optional<Window> activeWindow(ZonedDateTime now) {
        LocalDate startDate = now.toLocalDate().with(TemporalAdjusters.previousOrSame(dayOfWeek));
        ZonedDateTime start = startDate.atTime(startTime).atZone(now.getZone());
        if (now.isBefore(start)) {
            start = start.minusWeeks(1);
        }
        ZonedDateTime end = start.plusMinutes(durationMinutes);
        if (now.isBefore(start) || !now.isBefore(end)) {
            return Optional.empty();
        }
        String weekKey = String.format("%d-W%02d",
                start.get(IsoFields.WEEK_BASED_YEAR), start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        return Optional.of(new Window(weekKey, start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli()));
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :yeowool-federation:test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/event/FederationEventSchedule.java yeowool-federation/src/test/java/com/yeowool/federation/event/FederationEventScheduleTest.java
git commit -m "Add weekly federation event schedule window"
```

---

### Task 2: DB layer — event tables, event repository, ranking query

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/event/FederationEvent.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/event/EventStanding.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/event/FederationEventRepository.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/FederationRankingEntry.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/database/FederationSchemaInitializer.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/database/FederationRepository.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/FederationManager.java`

**Interfaces:**
- Produces:
  - `record FederationEvent(long id, long startsAt, long endsAt, boolean ended, Long endedAt, String result)` (`com.yeowool.federation.event`)
  - `record EventStanding(UUID federationId, String name, long gained)` (`com.yeowool.federation.event`)
  - `FederationEventRepository(DataSource)`: `OptionalLong startEvent(long startsAt, long endsAt, String weekKey)` (empty = duplicate week key), `Optional<FederationEvent> findActive()`, `Optional<FederationEvent> findLatest()`, `boolean claimEnd(long eventId, long endedAt)`, `void saveResult(long eventId, String result)`, `List<EventStanding> standings(long eventId, int limit)` (only `gained > 0`) — all `throws SQLException`.
  - `record FederationRankingEntry(String name, int level, long activity)` (`com.yeowool.federation`)
  - `FederationRepository.topByLevel(int limit) -> List<FederationRankingEntry>`; `FederationManager.ranking(int limit) -> List<FederationRankingEntry>` (both `throws SQLException`).

- [ ] **Step 1: Tables.** In `FederationSchemaInitializer`, append two entries to the `DDL` list (after the applications table):

```java
            """
            CREATE TABLE IF NOT EXISTS yw_federation_events (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                starts_at BIGINT NOT NULL,
                ends_at BIGINT NOT NULL,
                ended TINYINT(1) NOT NULL DEFAULT 0,
                ended_at BIGINT NULL,
                result TEXT NULL,
                week_key VARCHAR(16) NULL,
                UNIQUE KEY uniq_week (week_key)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_federation_event_baselines (
                event_id BIGINT NOT NULL,
                federation_id CHAR(36) NOT NULL,
                activity BIGINT NOT NULL,
                PRIMARY KEY (event_id, federation_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
```
(Mind the commas between list entries.)

- [ ] **Step 2: Records.**

`FederationEvent.java`:
```java
package com.yeowool.federation.event;

/** One 연합대항 row. {@code result} is the announcement text, set once by the server that finished it. */
public record FederationEvent(long id, long startsAt, long endsAt, boolean ended, Long endedAt, String result) {
}
```

`EventStanding.java`:
```java
package com.yeowool.federation.event;

import java.util.UUID;

/** A federation's activity gained since the event's snapshot. */
public record EventStanding(UUID federationId, String name, long gained) {
}
```

`FederationRankingEntry.java`:
```java
package com.yeowool.federation;

public record FederationRankingEntry(String name, int level, long activity) {
}
```

- [ ] **Step 3: `FederationEventRepository.java`**

```java
package com.yeowool.federation.event;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * 연합대항 persistence. Shared by all 3 servers: start is one transaction (event row + activity snapshot),
 * end is claimed by a single conditional UPDATE so only one server pays rewards. Blocking — executor only.
 */
public final class FederationEventRepository {

    private static final int MYSQL_DUPLICATE_KEY = 1062;
    private static final String EVENT_COLUMNS = "id, starts_at, ends_at, ended, ended_at, result";

    private final DataSource dataSource;

    public FederationEventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** @return the new event id, or empty if {@code weekKey} was already used (another server started this week's event). */
    public OptionalLong startEvent(long startsAt, long endsAt, String weekKey) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long eventId;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO yw_federation_events (starts_at, ends_at, week_key) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, startsAt);
                    insert.setLong(2, endsAt);
                    insert.setString(3, weekKey);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        keys.next();
                        eventId = keys.getLong(1);
                    }
                }
                try (PreparedStatement snapshot = connection.prepareStatement(
                        "INSERT INTO yw_federation_event_baselines (event_id, federation_id, activity) " +
                                "SELECT ?, id, activity FROM yw_federations")) {
                    snapshot.setLong(1, eventId);
                    snapshot.executeUpdate();
                }
                connection.commit();
                return OptionalLong.of(eventId);
            } catch (SQLException e) {
                connection.rollback();
                if (e.getErrorCode() == MYSQL_DUPLICATE_KEY) {
                    return OptionalLong.empty();
                }
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public Optional<FederationEvent> findActive() throws SQLException {
        return querySingle("SELECT " + EVENT_COLUMNS + " FROM yw_federation_events WHERE ended = 0 ORDER BY id DESC LIMIT 1");
    }

    public Optional<FederationEvent> findLatest() throws SQLException {
        return querySingle("SELECT " + EVENT_COLUMNS + " FROM yw_federation_events ORDER BY id DESC LIMIT 1");
    }

    /** @return true for exactly one caller across all servers — that caller pays rewards and saves the result. */
    public boolean claimEnd(long eventId, long endedAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_federation_events SET ended = 1, ended_at = ? WHERE id = ? AND ended = 0")) {
            update.setLong(1, endedAt);
            update.setLong(2, eventId);
            return update.executeUpdate() == 1;
        }
    }

    public void saveResult(long eventId, String result) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_federation_events SET result = ? WHERE id = ?")) {
            update.setString(1, result);
            update.setLong(2, eventId);
            update.executeUpdate();
        }
    }

    /** Federations ordered by activity gained since the snapshot; only positive gains. */
    public List<EventStanding> standings(long eventId, int limit) throws SQLException {
        List<EventStanding> standings = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT f.id, f.name, f.activity - COALESCE(b.activity, 0) AS gained " +
                             "FROM yw_federations f " +
                             "LEFT JOIN yw_federation_event_baselines b ON b.federation_id = f.id AND b.event_id = ? " +
                             "WHERE f.activity - COALESCE(b.activity, 0) > 0 " +
                             "ORDER BY gained DESC LIMIT ?")) {
            select.setLong(1, eventId);
            select.setInt(2, limit);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    standings.add(new EventStanding(UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getLong("gained")));
                }
            }
        }
        return standings;
    }

    private Optional<FederationEvent> querySingle(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql);
             ResultSet rs = select.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            long endedAt = rs.getLong("ended_at");
            Long endedAtOrNull = rs.wasNull() ? null : endedAt;
            return Optional.of(new FederationEvent(
                    rs.getLong("id"),
                    rs.getLong("starts_at"),
                    rs.getLong("ends_at"),
                    rs.getBoolean("ended"),
                    endedAtOrNull,
                    rs.getString("result")));
        }
    }
}
```

- [ ] **Step 4: Ranking query.** Add to `FederationRepository`:

```java
    public List<FederationRankingEntry> topByLevel(int limit) throws SQLException {
        List<FederationRankingEntry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT name, level, activity FROM yw_federations ORDER BY level DESC, activity DESC LIMIT ?")) {
            select.setInt(1, limit);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    entries.add(new FederationRankingEntry(rs.getString("name"), rs.getInt("level"), rs.getLong("activity")));
                }
            }
        }
        return entries;
    }
```
(add `import com.yeowool.federation.FederationRankingEntry;`; `ArrayList`/`List` are already imported there).

And to `FederationManager`:
```java
    public List<FederationRankingEntry> ranking(int limit) throws SQLException {
        return repository.topByLevel(limit);
    }
```

- [ ] **Step 5: Build**

Run: `./gradlew :yeowool-federation:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/event/FederationEvent.java yeowool-federation/src/main/java/com/yeowool/federation/event/EventStanding.java yeowool-federation/src/main/java/com/yeowool/federation/event/FederationEventRepository.java yeowool-federation/src/main/java/com/yeowool/federation/FederationRankingEntry.java yeowool-federation/src/main/java/com/yeowool/federation/database/FederationSchemaInitializer.java yeowool-federation/src/main/java/com/yeowool/federation/database/FederationRepository.java yeowool-federation/src/main/java/com/yeowool/federation/FederationManager.java
git commit -m "Add federation event tables, event repository and ranking query"
```

---

### Task 3: `FederationEventService` — start, end, weekly tick, announcements

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/event/Announcement.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/event/FederationEventService.java`

**Interfaces:**
- Consumes: Task 1 `FederationEventSchedule`; Task 2 `FederationEventRepository`, `FederationEvent`, `EventStanding`; `FederationManager.deposit(UUID, long) -> boolean` (existing); `MessageService.broadcast(String key, TagResolver...)`.
- Produces:
  - `record Announcement(String messageKey, String placeholder, String value)`
  - `FederationEventService(JavaPlugin plugin, MessageService messages, FederationEventRepository events, FederationManager manager, List<Long> rewards, Optional<FederationEventSchedule> schedule)`
  - `void init() throws SQLException` (blocking; marks the latest event as already announced)
  - `enum StartResult { STARTED, ALREADY_RUNNING }` / `StartResult start(int minutes) throws SQLException`
  - `boolean endNow() throws SQLException` (false = nothing running)
  - `List<Announcement> tick(ZonedDateTime now) throws SQLException` (blocking; weekly auto-start, auto-end, announcements)
  - `record EventStatus(long remainingMillis, List<EventStanding> top)` / `Optional<EventStatus> status() throws SQLException`
  - `void broadcast(List<Announcement> announcements)` — MAIN THREAD ONLY.

- [ ] **Step 1: `Announcement.java`**

```java
package com.yeowool.federation.event;

/** A server-wide broadcast to make on the main thread: one messages.yml key with one placeholder. */
public record Announcement(String messageKey, String placeholder, String value) {
}
```

- [ ] **Step 2: `FederationEventService.java`**

```java
package com.yeowool.federation.event;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.federation.FederationManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * 연합대항. All state is in the DB so the 3 servers agree; every method except {@link #broadcast} is
 * blocking and runs on the federation executor. Each server remembers (in memory) which event it already
 * announced, so every server announces once to its own players.
 */
public final class FederationEventService {

    private static final int STATUS_TOP = 5;

    public enum StartResult { STARTED, ALREADY_RUNNING }

    public record EventStatus(long remainingMillis, List<EventStanding> top) {
    }

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final FederationEventRepository events;
    private final FederationManager manager;
    private final List<Long> rewards;
    private final Optional<FederationEventSchedule> schedule;

    private volatile long announcedStartId = -1;
    private volatile long announcedEndId = -1;

    public FederationEventService(JavaPlugin plugin, MessageService messages, FederationEventRepository events,
                                  FederationManager manager, List<Long> rewards, Optional<FederationEventSchedule> schedule) {
        this.plugin = plugin;
        this.messages = messages;
        this.events = events;
        this.manager = manager;
        this.rewards = List.copyOf(rewards);
        this.schedule = schedule;
    }

    /** Don't re-announce whatever already happened before this server (re)started. */
    public void init() throws SQLException {
        Optional<FederationEvent> latest = events.findLatest();
        if (latest.isPresent()) {
            announcedStartId = latest.get().id();
            if (latest.get().result() != null) {
                announcedEndId = latest.get().id();
            }
        }
    }

    public StartResult start(int minutes) throws SQLException {
        if (events.findActive().isPresent()) {
            return StartResult.ALREADY_RUNNING;
        }
        long now = System.currentTimeMillis();
        events.startEvent(now, now + minutes * 60_000L, null);
        return StartResult.STARTED;
    }

    public boolean endNow() throws SQLException {
        Optional<FederationEvent> active = events.findActive();
        if (active.isEmpty()) {
            return false;
        }
        finish(active.get());
        return true;
    }

    public List<Announcement> tick(ZonedDateTime now) throws SQLException {
        long nowMillis = now.toInstant().toEpochMilli();
        if (schedule.isPresent()) {
            Optional<FederationEventSchedule.Window> window = schedule.get().activeWindow(now);
            if (window.isPresent() && events.findActive().isEmpty()) {
                events.startEvent(window.get().startMillis(), window.get().endMillis(), window.get().weekKey());
            }
        }

        Optional<FederationEvent> active = events.findActive();
        if (active.isPresent() && active.get().endsAt() <= nowMillis) {
            finish(active.get());
        }

        List<Announcement> announcements = new ArrayList<>();
        Optional<FederationEvent> latest = events.findLatest();
        if (latest.isPresent()) {
            FederationEvent event = latest.get();
            if (!event.ended() && event.id() != announcedStartId) {
                announcedStartId = event.id();
                long minutesLeft = Math.max(1, (event.endsAt() - nowMillis + 59_999) / 60_000);
                announcements.add(new Announcement("federation.event-started", "minutes", String.valueOf(minutesLeft)));
            }
            if (event.ended() && event.result() != null && event.id() != announcedEndId) {
                announcedEndId = event.id();
                announcedStartId = event.id();
                announcements.add(new Announcement("federation.event-ended", "results", event.result()));
            }
        }
        return announcements;
    }

    public Optional<EventStatus> status() throws SQLException {
        Optional<FederationEvent> active = events.findActive();
        if (active.isEmpty()) {
            return Optional.empty();
        }
        long remaining = Math.max(0, active.get().endsAt() - System.currentTimeMillis());
        return Optional.of(new EventStatus(remaining, events.standings(active.get().id(), STATUS_TOP)));
    }

    /** Main thread only. */
    public void broadcast(List<Announcement> announcements) {
        for (Announcement announcement : announcements) {
            messages.broadcast(announcement.messageKey(), Placeholder.unparsed(announcement.placeholder(), announcement.value()));
        }
    }

    /** Only the server whose claim succeeds pays and stores the result. */
    private void finish(FederationEvent event) throws SQLException {
        if (!events.claimEnd(event.id(), System.currentTimeMillis())) {
            return;
        }
        List<EventStanding> top = events.standings(event.id(), rewards.size());
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            EventStanding standing = top.get(i);
            long reward = rewards.get(i);
            try {
                manager.deposit(standing.federationId(), reward);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합대항 보상 지급 실패 — 수동 지급 필요: " + standing.name() + " / " + reward + "온", e);
            }
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(i + 1).append(". ").append(standing.name())
                    .append(" (+").append(String.format("%,d", standing.gained())).append(") — 보상 ")
                    .append(String.format("%,d", reward)).append("온");
        }
        events.saveResult(event.id(), result.length() == 0 ? "참가한 연합이 없습니다." : result.toString());
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :yeowool-federation:build`
Expected: `BUILD SUCCESSFUL` (not wired up until Task 4).

- [ ] **Step 4: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/event/Announcement.java yeowool-federation/src/main/java/com/yeowool/federation/event/FederationEventService.java
git commit -m "Add federation event service: start, end, weekly tick, announcements"
```

---

### Task 4: `/연합대항` command, config, messages, wiring into the 60 s task

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/event/FederationEventCommand.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java`
- Modify: `yeowool-federation/src/main/resources/plugin.yml`
- Modify: `yeowool-federation/src/main/resources/config.yml`
- Modify: `yeowool-federation/src/main/resources/messages.yml`

**Interfaces:**
- Consumes: Task 3 `FederationEventService` (constructor, `init`, `start`/`StartResult`, `endNow`, `tick`, `status`/`EventStatus`, `broadcast`), Task 2 `FederationEventRepository(DataSource)`, `EventStanding`, Task 1 `FederationEventSchedule`.
- Produces: `FederationEventCommand(JavaPlugin plugin, MessageService messages, FederationEventService service, ExecutorService executor)` registered for `/연합대항`.

- [ ] **Step 1: `plugin.yml`** — append under `commands:` (same indentation as `연합:`):
```yaml
  연합대항:
    description: 연합대항(기간 동안 활동량 경쟁) 현황 확인, 관리진은 시작/종료
```

- [ ] **Step 2: `config.yml`** — append:
```yaml

event:
  # 연합대항 1~3등 보상 (각 연합 은행으로 지급, 온). 기간 동안 활동량을 못 쌓은 연합은 순위에서 제외됩니다.
  rewards: [100000, 50000, 30000]
  auto:
    # 매주 자동 시작 (서버 시간 기준). false 면 관리진이 /연합대항 시작 <분> 으로만 시작합니다.
    enabled: true
    day-of-week: SATURDAY
    start-time: "20:00"
    duration-minutes: 1440
```

- [ ] **Step 3: `messages.yml`** — append under `federation:` (2-space indent):
```yaml
  event-usage: "§c사용법: /연합대항 [정보|시작 <분>|종료]"
  event-start-usage: "§c사용법: /연합대항 시작 <분> (1 이상)"
  event-no-permission: "§c연합대항을 시작/종료할 권한이 없습니다."
  event-none: "§7진행 중인 연합대항이 없습니다."
  event-already-running: "§c이미 진행 중인 연합대항이 있습니다. (/연합대항 종료 후 다시 시작)"
  event-ended-by-admin: "§a연합대항을 종료하고 보상을 지급했습니다."
  event-status-header: "§6[연합대항] §7남은 시간 <minutes>분"
  event-status-line: "§7<rank>. §e<name> §7(+<gained>)"
  event-status-empty: "§7아직 활동량을 쌓은 연합이 없습니다."
  event-started: "§6[연합대항] §e연합대항이 진행 중입니다! 남은 시간 <minutes>분 — 활동량을 가장 많이 쌓은 연합이 우승합니다. §7(/연합대항 정보)"
  event-ended: "§6[연합대항] §e연합대항이 끝났습니다!\n<results>"
```

- [ ] **Step 4: `FederationEventCommand.java`**

```java
package com.yeowool.federation.event;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/** {@code /연합대항 [정보|시작 <분>|종료]} — 정보 for everyone, 시작/종료 need yeowool.event.manage (same as /마을대항). */
public final class FederationEventCommand implements CommandExecutor {

    private static final String MANAGE_PERMISSION = "yeowool.event.manage";

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final FederationEventService service;
    private final ExecutorService executor;

    public FederationEventCommand(JavaPlugin plugin, MessageService messages, FederationEventService service,
                                  ExecutorService executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equals("정보")) {
            showStatus(sender);
            return true;
        }
        switch (args[0]) {
            case "시작" -> start(sender, args);
            case "종료" -> end(sender);
            default -> messages.send(sender, "federation.event-usage");
        }
        return true;
    }

    private void showStatus(CommandSender sender) {
        executor.execute(() -> {
            try {
                var status = service.status();
                runOnMain(() -> {
                    if (status.isEmpty()) {
                        messages.send(sender, "federation.event-none");
                        return;
                    }
                    long minutes = (status.get().remainingMillis() + 59_999) / 60_000;
                    messages.send(sender, "federation.event-status-header", Placeholder.unparsed("minutes", String.valueOf(minutes)));
                    List<EventStanding> top = status.get().top();
                    if (top.isEmpty()) {
                        messages.send(sender, "federation.event-status-empty");
                        return;
                    }
                    for (int i = 0; i < top.size(); i++) {
                        messages.send(sender, "federation.event-status-line",
                                Placeholder.unparsed("rank", String.valueOf(i + 1)),
                                Placeholder.unparsed("name", top.get(i).name()),
                                Placeholder.unparsed("gained", String.format("%,d", top.get(i).gained())));
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합대항 현황 조회 실패", e);
            }
        });
    }

    private void start(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            messages.send(sender, "federation.event-no-permission");
            return;
        }
        int minutes;
        try {
            minutes = args.length == 2 ? Integer.parseInt(args[1]) : -1;
        } catch (NumberFormatException e) {
            minutes = -1;
        }
        if (minutes < 1) {
            messages.send(sender, "federation.event-start-usage");
            return;
        }
        int duration = minutes;
        executor.execute(() -> {
            try {
                if (service.start(duration) == FederationEventService.StartResult.ALREADY_RUNNING) {
                    runOnMain(() -> messages.send(sender, "federation.event-already-running"));
                    return;
                }
                List<Announcement> announcements = service.tick(ZonedDateTime.now());
                runOnMain(() -> service.broadcast(announcements));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합대항 시작 실패", e);
            }
        });
    }

    private void end(CommandSender sender) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            messages.send(sender, "federation.event-no-permission");
            return;
        }
        executor.execute(() -> {
            try {
                if (!service.endNow()) {
                    runOnMain(() -> messages.send(sender, "federation.event-none"));
                    return;
                }
                List<Announcement> announcements = service.tick(ZonedDateTime.now());
                runOnMain(() -> {
                    messages.send(sender, "federation.event-ended-by-admin");
                    service.broadcast(announcements);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합대항 종료 실패", e);
            }
        });
    }

    private void runOnMain(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }
}
```

- [ ] **Step 5: Wiring in `YeowoolFederation.onEnable`.**

Immediately after the `FederationManager manager = new FederationManager(repository, levelConfig);` line, add:
```java
        Optional<FederationEventSchedule> eventSchedule = Optional.empty();
        if (getConfig().getBoolean("event.auto.enabled", true)) {
            try {
                eventSchedule = Optional.of(new FederationEventSchedule(
                        DayOfWeek.valueOf(getConfig().getString("event.auto.day-of-week", "SATURDAY").trim().toUpperCase(Locale.ROOT)),
                        LocalTime.parse(getConfig().getString("event.auto.start-time", "20:00").trim()),
                        getConfig().getInt("event.auto.duration-minutes", 1440)));
            } catch (IllegalArgumentException | DateTimeException e) {
                getLogger().warning("연합대항 자동 시작 설정을 읽지 못해 자동 시작을 끕니다: " + e.getMessage());
            }
        }
        List<Long> eventRewards = getConfig().getLongList("event.rewards");
        FederationEventService eventService = new FederationEventService(this, messages,
                new FederationEventRepository(core.dataSource()), manager, eventRewards, eventSchedule);
        executor.execute(() -> {
            try {
                eventService.init();
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "연합대항 상태 불러오기 실패", e);
            }
        });
        var eventCommand = getCommand("연합대항");
        if (eventCommand != null) {
            eventCommand.setExecutor(new FederationEventCommand(this, messages, eventService, executor));
        }
```

Inside the existing 60 s `runTaskTimer` lambda's `executor.execute(() -> { ... })`, after the `for (UUID playerUuid : online) { ... }` loop, add:
```java
                try {
                    List<Announcement> announcements = eventService.tick(ZonedDateTime.now());
                    if (!announcements.isEmpty()) {
                        getServer().getScheduler().runTask(this, () -> eventService.broadcast(announcements));
                    }
                } catch (SQLException e) {
                    getLogger().log(Level.SEVERE, "연합대항 주기 처리 실패", e);
                }
```
(Inside that lambda `this` is still the plugin — it's a lambda, not an anonymous class.)

Add imports: `com.yeowool.federation.event.Announcement`, `com.yeowool.federation.event.FederationEventCommand`, `com.yeowool.federation.event.FederationEventRepository`, `com.yeowool.federation.event.FederationEventSchedule`, `com.yeowool.federation.event.FederationEventService`, `java.time.DateTimeException`, `java.time.DayOfWeek`, `java.time.LocalTime`, `java.time.ZonedDateTime`, `java.util.Locale`, `java.util.Optional`.

- [ ] **Step 6: Build**

Run: `./gradlew :yeowool-federation:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/event/FederationEventCommand.java yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java yeowool-federation/src/main/resources/plugin.yml yeowool-federation/src/main/resources/config.yml yeowool-federation/src/main/resources/messages.yml
git commit -m "Add /연합대항 with weekly auto-start and cross-server announcements"
```

---

### Task 5: `/연합 랭킹` + cross-server level-up notice

**Files:**
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/chat/FederationChatService.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java`
- Modify: `yeowool-federation/src/main/resources/messages.yml`

**Interfaces:**
- Consumes: Task 2 `FederationManager.ranking(int)`, `FederationRankingEntry`; existing `FederationManager.findByLandId(UUID) -> Optional<Federation>`, `FederationManager.upgrade`/`UpgradeResult`.
- Produces:
  - `FederationChatService.deliverToFederation(Player via, UUID federationId, Component message) throws SQLException` (blocking; local recipients directly, others via proxy through `via`'s connection).
  - `FederationCommand` constructor gains a trailing-before-executor param: `FederationCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, FederationManager manager, LandLookup landLookup, PlayerFederationResolver resolver, FederationLevelCache levelCache, List<FederationShop> shops, FederationChatService chatService, ExecutorService executor)`.

- [ ] **Step 1: Extract delivery in `FederationChatService`.** Add this public method and make `send` use it — replace everything in `send` from `List<UUID> remote = new ArrayList<>();` through the end of the `if (!remote.isEmpty()) { ... }` block with a single call `deliverToFederation(sender, federationId.get(), message);` (keep `return SendResult.SENT;`):

```java
    /**
     * Delivers {@code message} to every owner/resident of the federation: directly to those online here,
     * and through {@code via}'s connection to the proxy for the rest. Blocking — call off the main thread.
     */
    public void deliverToFederation(Player via, UUID federationId, Component message) throws SQLException {
        List<UUID> remote = new ArrayList<>();
        for (UUID recipient : findRecipients(federationId)) {
            Player online = Bukkit.getPlayer(recipient);
            if (online != null) {
                online.sendMessage(message);
            } else {
                remote.add(recipient);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);

        if (remote.isEmpty() || !via.isOnline()) {
            return;
        }
        if (!via.getListeningPluginChannels().contains(CHANNEL)) {
            plugin.getLogger().warning("프록시가 yeowool:targeted 채널을 받지 않습니다 — 다른 서버 연합원에게 연합 메시지가 전달되지 않았습니다 (프록시 재시작 필요?)");
            return;
        }
        String json = GsonComponentSerializer.gson().serialize(message);
        via.sendPluginMessage(plugin, CHANNEL, TargetedPayload.encode(remote, json));
    }
```

- [ ] **Step 2: Constructor + wiring order.** In `FederationCommand` add field `private final FederationChatService chatService;`, add the `FederationChatService chatService` parameter right before `ExecutorService executor` in the constructor and assign it; add `import com.yeowool.federation.chat.FederationChatService;` and `import net.kyori.adventure.text.Component;`.

In `YeowoolFederation.onEnable`, MOVE the line
```java
        FederationChatService chatService = new FederationChatService(
                this, core, core.dataSource(), getConfig().getLong("chat.cooldown-ms", 1500L));
```
up so it comes BEFORE `var federationCommand = ...`, and change the construction to
```java
        var federationCommand = new FederationCommand(this, core, messages, manager, landLookup, resolver, levelCache, shops, chatService, executor);
```
(The `FederationChatCommand`/`FederationChatListener` lines stay where they are and keep using the same `chatService`.)

- [ ] **Step 3: `/연합 랭킹`.** In `FederationCommand.onCommand`'s switch add before `default`:
```java
            case "랭킹" -> handleRanking(player);
```
and add the handler (above `runOnMain`):
```java
    private void handleRanking(Player player) {
        executor.execute(() -> {
            try {
                List<FederationRankingEntry> top = manager.ranking(10);
                runOnMain(() -> {
                    if (top.isEmpty()) {
                        messages.send(player, "federation.list-empty");
                        return;
                    }
                    messages.send(player, "federation.ranking-header");
                    for (int i = 0; i < top.size(); i++) {
                        FederationRankingEntry entry = top.get(i);
                        messages.send(player, "federation.ranking-line",
                                Placeholder.unparsed("rank", String.valueOf(i + 1)),
                                Placeholder.unparsed("name", entry.name()),
                                Placeholder.unparsed("level", String.valueOf(entry.level())),
                                Placeholder.unparsed("activity", formatAmount(entry.activity())));
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 랭킹 조회 실패", e);
            }
        });
    }
```

- [ ] **Step 4: Level-up notice.** In `handleUpgrade`, right after the line `int cap = manager.levelConfig().memberCap(outcome.level());`, insert:
```java
                if (outcome.result() == FederationManager.UpgradeResult.SUCCESS) {
                    Optional<Federation> upgraded = manager.findByLandId(land.get().id());
                    if (upgraded.isPresent()) {
                        Component notice = messages.resolve("federation.levelup-broadcast",
                                Placeholder.unparsed("name", upgraded.get().name()),
                                Placeholder.unparsed("level", String.valueOf(outcome.level())),
                                Placeholder.unparsed("cap", String.valueOf(cap)));
                        try {
                            chatService.deliverToFederation(player, upgraded.get().id(), notice);
                            return;
                        } catch (java.sql.SQLException e) {
                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 레벨업 알림 전달 실패", e);
                        }
                    }
                }
```
(On success the notice reaches the leader too — they own a member land — so the personal `upgrade-success` message is only the fallback when the notice couldn't be delivered.)

- [ ] **Step 5: Messages.** In `messages.yml` change `usage` so it ends with `|은행|업그레이드|상점|랭킹>` and append:
```yaml
  ranking-header: "§6[연합 랭킹]"
  ranking-line: "§7<rank>. §e<name> §7Lv.<level> (활동량 <activity>)"
  levelup-broadcast: "§6[연합] §e<name> 연합이 Lv.<level>이 되었습니다! §7(마을 정원 <cap>개)"
```

- [ ] **Step 6: Build**

Run: `./gradlew :yeowool-federation:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/chat/FederationChatService.java yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java yeowool-federation/src/main/resources/messages.yml
git commit -m "Add /연합 랭킹 and cross-server federation level-up notice"
```

---

### Task 6: Help, changelog, full build, deploy

**Files:**
- Modify: `yeowool-core/src/main/resources/help.yml`
- Modify: `update.md`

- [ ] **Step 1: Help.** In `help.yml` → `help.categories.마을연합`, append:
```yaml
      - "/연합 랭킹 - 연합 레벨·활동량 TOP 10"
      - "/연합대항 [정보] - 진행 중인 연합대항 순위와 남은 시간"
```
and in `help.categories.운영(관리자)`, change the line starting with `"/이벤트 시작|종료|미션시작|미션종료` so it also lists `/연합대항 시작 <분>|종료` — i.e. replace it with:
```yaml
      - "/이벤트 시작|종료|미션시작|미션종료, /서버이벤트, /마을대항 시작|종료, /연합대항 시작 <분>|종료 (yeowool.event.manage)"
```

- [ ] **Step 2: Changelog.** In `update.md`, under the existing `## 2026-09-26` heading, insert this section as the FIRST entry of that day (directly below the heading line):

```markdown
### 연합 랭킹·연합대항·레벨업 알림 추가 — 마을 연합 4단계 (yeowool-federation, yeowool-core)
- **`/연합 랭킹`**: 연합 레벨 높은 순(같으면 누적 활동량 순) TOP 10.
- **연합대항**: 정해진 기간 동안 **활동량을 가장 많이 늘린** 연합이 우승 — 1등 10만 / 2등 5만 / 3등 3만 온을 각 연합 은행으로 지급(`plugins/YeowoolFederation/config.yml`의 `event.rewards`). 기간 동안 활동량이 0인 연합은 순위 제외.
  - **매주 자동**: 기본 **토요일 20:00부터 24시간** (`event.auto`에서 요일·시각·기간 변경, `enabled: false`로 끄기).
  - **수동**: 관리진 `/연합대항 시작 <분>`, `/연합대항 종료` (`yeowool.event.manage`). 누구나 `/연합대항`(또는 `/연합대항 정보`)으로 남은 시간과 TOP 5 확인.
  - 세 서버가 같은 대항을 공유합니다(DB 기반) — 서버를 재시작해도 진행 중인 대항은 그대로 이어지고, 보상은 딱 한 번만 지급됩니다. 시작/결과 공지는 서버마다 최대 1분 늦게 뜰 수 있습니다.
- **레벨업 알림**: `/연합 업그레이드`에 성공하면 연합원 전원(다른 서버 포함)에게 "○○ 연합이 Lv.N이 되었습니다!" 알림.

세 서버(lobby/town/wild) `yeowool-federation`/`yeowool-core` jar 배포 완료 — **재시작 필요**. 서버 파일 삭제 필요 없음(새 테이블·설정 키 자동 추가).
```

- [ ] **Step 3: Full build + tests**

Run: `./gradlew test :yeowool-core:build :yeowool-federation:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Deploy** — copy `yeowool-core/build/libs/yeowool-core-1.0.0-SNAPSHOT.jar` and `yeowool-federation/build/libs/yeowool-federation-1.0.0-SNAPSHOT.jar` into each of `C:/YEOWOOL/lobby/plugins/`, `C:/YEOWOOL/town/plugins/`, `C:/YEOWOOL/wild/plugins/` (overwrite). Do not start/stop any server.

- [ ] **Step 5: Commit**

```bash
git add yeowool-core/src/main/resources/help.yml update.md
git commit -m "Document federation phase 4 in help and changelog"
```

- [ ] **Step 6: Manual verification (user, after restarting the 3 servers)** — `/연합 랭킹` lists federations; `/연합대항 시작 5` as admin → start announcement on all 3 servers (others within a minute); earn land XP in two federations; `/연합대항 정보` shows TOP 5; after 5 min (or `/연합대항 종료`) results announced once per server and 1st–3rd federation banks increased (`/연합 은행`); restart a server → no repeated announcement; `/연합 업그레이드` success → notice reaches a member on another server.

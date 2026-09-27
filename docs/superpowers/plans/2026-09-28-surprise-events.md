# 깜짝 이벤트 자동화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatic 30-minute boost events every 60–120 minutes on the town and wild servers (land XP ×2, crop drops ×2, treasure-map drops ×3, job XP ×2), announced with a boss bar, synced through one DB state row.

**Architecture:** New package `com.yeowool.life.surprise` in `yeowool-life`. Participating servers (those that have a world listed in `surprise-event.worlds`) poll a single state row every 20 s; start/end transitions are conditional UPDATEs on `seq` so exactly one server makes each. Each participating server applies the active boost locally: core `landStats` multipliers (only when no manual `/이벤트` owns them) or the new static `LifeBoosts` for treasure drops and job XP. Pure picking/formatting rules are unit tested.

**Tech Stack:** Paper 1.21.4, Java 21, MySQL via core `DataSource`, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-28-surprise-events-design.md`

## Global Constraints

- Build from `C:/Users/apple/OneDrive/Desktop/YEOWOOL_PLUGIN`: `./gradlew :yeowool-life:build`. OneDrive lock (`Unable to delete directory ...` or `Cannot snapshot ...binary/output.bin`) → `rm -rf yeowool-life/build/test-results` and rerun.
- JDBC only on the life executor; Bukkit API (boss bars, broadcasts, landStats setters) on the main thread.
- Player-visible text Korean via `MessageService` keys in `yeowool-life/src/main/resources/messages.yml` (MiniMessage, placeholders with `_`); values via `Placeholder.unparsed`.
- Commits: stage only the task's files (never `git add -A`/`.`, never `homepage-plan.md`), never `--amend`, message ends with a blank line then exactly `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Do not dispatch subagents.

---

### Task 1: Types, rules, boosts + tests

**Files:**
- Create: `yeowool-life/src/main/java/com/yeowool/life/surprise/SurpriseEventType.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/surprise/SurpriseEventRules.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/surprise/LifeBoosts.java`
- Test: `yeowool-life/src/test/java/com/yeowool/life/surprise/SurpriseEventRulesTest.java`

**Interfaces:**
- Produces: `enum SurpriseEventType { LAND_XP, CROP_DROP, TREASURE_DROP, JOB_XP }` with `key()`, `label()`, `static Optional<SurpriseEventType> byKey(String)`, `static Optional<SurpriseEventType> parse(String)` (key, label without spaces, or alias); `SurpriseEventRules.pickType(Random, List<SurpriseEventType>, SurpriseEventType previous) -> Optional<SurpriseEventType>`, `nextDelayMillis(Random, int, int) -> long`, `stillOurs(double current, double applied) -> boolean`, `formatMultiplier(double) -> String`; `LifeBoosts.treasureDropMultiplier()/setTreasureDropMultiplier(double)`, `jobXpMultiplier()/setJobXpMultiplier(double)`.

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.life.surprise;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurpriseEventRulesTest {

    private static final List<SurpriseEventType> ALL = List.of(SurpriseEventType.values());

    @Test
    void pickTypeNeverRepeatsThePreviousWhenThereIsAChoice() {
        for (int seed = 0; seed < 200; seed++) {
            SurpriseEventType picked = SurpriseEventRules.pickType(new Random(seed), ALL, SurpriseEventType.CROP_DROP).orElseThrow();
            assertNotEquals(SurpriseEventType.CROP_DROP, picked);
        }
    }

    @Test
    void pickTypeWithSingleOptionOrNoPreviousStillPicks() {
        assertEquals(Optional.of(SurpriseEventType.JOB_XP),
                SurpriseEventRules.pickType(new Random(1), List.of(SurpriseEventType.JOB_XP), SurpriseEventType.JOB_XP));
        assertTrue(SurpriseEventRules.pickType(new Random(1), ALL, null).isPresent());
        assertEquals(Optional.empty(), SurpriseEventRules.pickType(new Random(1), List.of(), null));
    }

    @Test
    void delayIsWithinBoundsInWholeMinutes() {
        Random random = new Random(3);
        for (int i = 0; i < 500; i++) {
            long delay = SurpriseEventRules.nextDelayMillis(random, 60, 120);
            assertTrue(delay >= 60 * 60_000L && delay <= 120 * 60_000L);
            assertEquals(0, delay % 60_000L);
        }
        assertEquals(60 * 60_000L, SurpriseEventRules.nextDelayMillis(new Random(1), 60, 10));
    }

    @Test
    void stillOursComparesWithTolerance() {
        assertTrue(SurpriseEventRules.stillOurs(2.0, 2.0));
        assertFalse(SurpriseEventRules.stillOurs(3.0, 2.0));
    }

    @Test
    void multipliersFormatWithoutTrailingZero() {
        assertEquals("2", SurpriseEventRules.formatMultiplier(2.0));
        assertEquals("1.5", SurpriseEventRules.formatMultiplier(1.5));
    }

    @Test
    void typesParseFromKeysLabelsAndAliases() {
        assertEquals(Optional.of(SurpriseEventType.TREASURE_DROP), SurpriseEventType.byKey("treasure_drop"));
        assertEquals(Optional.of(SurpriseEventType.CROP_DROP), SurpriseEventType.parse("작물"));
        assertEquals(Optional.of(SurpriseEventType.LAND_XP), SurpriseEventType.parse("토지경험치"));
        assertEquals(Optional.of(SurpriseEventType.JOB_XP), SurpriseEventType.parse("JOB_XP"));
        assertEquals(Optional.empty(), SurpriseEventType.parse("낚시"));
    }
}
```

- [ ] **Step 2: Run** `./gradlew :yeowool-life:test --tests "com.yeowool.life.surprise.SurpriseEventRulesTest"` → FAIL (classes missing).

- [ ] **Step 3: Create `SurpriseEventType.java`**

```java
package com.yeowool.life.surprise;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum SurpriseEventType {
    LAND_XP("land_xp", "토지 경험치", List.of("경험치")),
    CROP_DROP("crop_drop", "작물 수확량", List.of("작물")),
    TREASURE_DROP("treasure_drop", "보물지도 발견", List.of("보물지도")),
    JOB_XP("job_xp", "직업 경험치", List.of("직업"));

    private final String key;
    private final String label;
    private final List<String> aliases;

    SurpriseEventType(String key, String label, List<String> aliases) {
        this.key = key;
        this.label = label;
        this.aliases = aliases;
    }

    /** DB/config key. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static Optional<SurpriseEventType> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (SurpriseEventType type : values()) {
            if (type.key.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** Command input: config key, the label without spaces (e.g. 토지경험치), an alias (e.g. 작물) or the enum name. */
    public static Optional<SurpriseEventType> parse(String input) {
        String trimmed = input.trim();
        for (SurpriseEventType type : values()) {
            if (type.key.equalsIgnoreCase(trimmed) || type.name().equalsIgnoreCase(trimmed)
                    || type.label.replace(" ", "").equals(trimmed) || type.aliases.contains(trimmed)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Create `SurpriseEventRules.java`**

```java
package com.yeowool.life.surprise;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Pure decisions for surprise events, kept free of Bukkit so they can be unit tested. */
public final class SurpriseEventRules {

    private SurpriseEventRules() {
    }

    /** Random enabled type, avoiding {@code previous} when another is available; empty when none are enabled. */
    public static Optional<SurpriseEventType> pickType(Random random, List<SurpriseEventType> enabled, SurpriseEventType previous) {
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        List<SurpriseEventType> choices = enabled.stream().filter(type -> type != previous).toList();
        if (choices.isEmpty()) {
            choices = enabled;
        }
        return Optional.of(choices.get(random.nextInt(choices.size())));
    }

    /** Whole minutes in [min, max]; an inverted range collapses to min, negatives to 0. */
    public static long nextDelayMillis(Random random, int minMinutes, int maxMinutes) {
        int min = Math.max(0, minMinutes);
        int max = Math.max(min, maxMinutes);
        return (min + (long) random.nextInt(max - min + 1)) * 60_000L;
    }

    /** Whether a shared multiplier still holds the value we set — if not, someone else (a manual event) owns it now. */
    public static boolean stillOurs(double current, double applied) {
        return Math.abs(current - applied) < 1e-9;
    }

    public static String formatMultiplier(double multiplier) {
        return multiplier == Math.rint(multiplier) ? String.valueOf((long) multiplier) : String.valueOf(multiplier);
    }
}
```

- [ ] **Step 5: Create `LifeBoosts.java`**

```java
package com.yeowool.life.surprise;

/**
 * YeowoolLife-internal boost multipliers (1.0 = normal) that the surprise event turns up for a
 * while. Written on the main thread, read by the treasure-map and job XP code.
 */
public final class LifeBoosts {

    private static volatile double treasureDrop = 1.0;
    private static volatile double jobXp = 1.0;

    private LifeBoosts() {
    }

    public static double treasureDropMultiplier() {
        return treasureDrop;
    }

    public static void setTreasureDropMultiplier(double multiplier) {
        treasureDrop = multiplier;
    }

    public static double jobXpMultiplier() {
        return jobXp;
    }

    public static void setJobXpMultiplier(double multiplier) {
        jobXp = multiplier;
    }
}
```

- [ ] **Step 6: Run** the same test → PASS (6 tests).

- [ ] **Step 7: Commit**

```bash
git add yeowool-life/src/main/java/com/yeowool/life/surprise/SurpriseEventType.java yeowool-life/src/main/java/com/yeowool/life/surprise/SurpriseEventRules.java yeowool-life/src/main/java/com/yeowool/life/surprise/LifeBoosts.java yeowool-life/src/test/java/com/yeowool/life/surprise/SurpriseEventRulesTest.java
git commit -m "Add surprise event types, rules and life boost multipliers

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Repository, service, command, hooks, wiring

**Files:**
- Create: `yeowool-life/src/main/java/com/yeowool/life/surprise/SurpriseEventRepository.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/surprise/SurpriseEventService.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/surprise/SurpriseEventCommand.java`
- Modify: `yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureService.java` (drop chance × boost)
- Modify: `yeowool-life/src/main/java/com/yeowool/life/job/JobManager.java` (XP × boost)
- Modify: `yeowool-life/src/main/java/com/yeowool/life/YeowoolLife.java`
- Modify: `yeowool-life/src/main/resources/config.yml`, `messages.yml`, `plugin.yml`
- Modify: `yeowool-core/src/main/resources/help.yml`

**Interfaces:**
- Consumes: Task 1; `core.landStats().getXpMultiplier()/setXpMultiplier(double)/getCropDropMultiplier()/setCropDropMultiplier(double)`; `com.yeowool.core.util.DurationFormat.humanize(long)`; `MessageService.broadcast/send`.
- Produces: `SurpriseEventRepository(DataSource)` with `record State(long seq, boolean active, SurpriseEventType type, double multiplier, long endsAt, long nextAt, SurpriseEventType lastType)`, `createTables(long firstAt)`, `state()`, `start(long expectedSeq, SurpriseEventType, double, long endsAt) -> boolean`, `end(long expectedSeq, long nextAt) -> boolean`. `SurpriseEventService(JavaPlugin, YeowoolCoreAPI, MessageService, SurpriseEventRepository, Settings)` with `record Settings(int durationMinutes, int intervalMinMinutes, int intervalMaxMinutes, Map<SurpriseEventType, Double> multipliers)`, `tick()` (worker), `forceStart(Optional<SurpriseEventType>) -> boolean` and `forceEnd() -> boolean` (worker, throw SQLException), `updateBossBar()`, `status(CommandSender)`, `shutdown()` (main).

- [ ] **Step 1: Create `SurpriseEventRepository.java`**

```java
package com.yeowool.life.surprise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The single shared surprise-event row (id = 1). Start/end are UPDATEs conditional on the
 * {@code seq} the caller read and bump it, so when both participating servers notice the same
 * due transition exactly one of them makes it.
 */
public final class SurpriseEventRepository {

    /** {@code type} stays set after an event ends (it's what ended); {@code lastType} is the one to avoid next. */
    public record State(long seq, boolean active, SurpriseEventType type, double multiplier, long endsAt, long nextAt,
                        SurpriseEventType lastType) {
    }

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_surprise_event_state (
                id TINYINT NOT NULL PRIMARY KEY,
                seq BIGINT NOT NULL DEFAULT 0,
                active TINYINT(1) NOT NULL DEFAULT 0,
                type VARCHAR(16) NULL,
                multiplier DOUBLE NOT NULL DEFAULT 1,
                ends_at BIGINT NOT NULL DEFAULT 0,
                next_at BIGINT NOT NULL DEFAULT 0,
                last_type VARCHAR(16) NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final DataSource dataSource;

    public SurpriseEventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Creates the table and, only the very first time, the row with the first event at {@code firstAt}. */
    public void createTables(long firstAt) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(DDL);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT IGNORE INTO yw_surprise_event_state (id, next_at) VALUES (1, ?)")) {
                ps.setLong(1, firstAt);
                ps.executeUpdate();
            }
        }
    }

    public State state() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT seq, active, type, multiplier, ends_at, next_at, last_type FROM yw_surprise_event_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("yw_surprise_event_state 행이 없습니다");
            }
            return new State(rs.getLong(1), rs.getBoolean(2), SurpriseEventType.byKey(rs.getString(3)).orElse(null),
                    rs.getDouble(4), rs.getLong(5), rs.getLong(6), SurpriseEventType.byKey(rs.getString(7)).orElse(null));
        }
    }

    public boolean start(long expectedSeq, SurpriseEventType type, double multiplier, long endsAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_surprise_event_state SET seq = seq + 1, active = 1, type = ?, multiplier = ?, ends_at = ? "
                             + "WHERE id = 1 AND seq = ? AND active = 0")) {
            ps.setString(1, type.key());
            ps.setDouble(2, multiplier);
            ps.setLong(3, endsAt);
            ps.setLong(4, expectedSeq);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean end(long expectedSeq, long nextAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_surprise_event_state SET seq = seq + 1, active = 0, last_type = type, next_at = ? "
                             + "WHERE id = 1 AND seq = ? AND active = 1")) {
            ps.setLong(1, nextAt);
            ps.setLong(2, expectedSeq);
            return ps.executeUpdate() == 1;
        }
    }
}
```

- [ ] **Step 2: Create `SurpriseEventService.java`**

```java
package com.yeowool.life.surprise;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;

/**
 * Automatic surprise boost events shared by the participating servers (town and wild). Every
 * participating server polls the shared row; due transitions are claimed by conditional UPDATEs,
 * and each server applies the active boost locally, shows a boss bar and announces each new
 * {@code seq} once. Land XP / crop multipliers are left alone while a manual {@code /이벤트}
 * owns them.
 */
public final class SurpriseEventService {

    public record Settings(int durationMinutes, int intervalMinMinutes, int intervalMaxMinutes,
                           Map<SurpriseEventType, Double> multipliers) {
    }

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final SurpriseEventRepository repository;
    private final Settings settings;
    private final Random random = new Random();

    // main thread only
    private SurpriseEventRepository.State current;
    private long announcedSeq = -1;
    private SurpriseEventType appliedType;
    private double appliedValue;
    private BossBar bossBar;

    public SurpriseEventService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                                SurpriseEventRepository repository, Settings settings) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.settings = settings;
    }

    // ---- worker thread ----

    /** Every 20 seconds: advance the shared state if a transition is due, then apply the latest on the main thread. */
    public void tick() {
        try {
            long now = System.currentTimeMillis();
            SurpriseEventRepository.State state = repository.state();
            if (!state.active() && now >= state.nextAt()) {
                Optional<SurpriseEventType> type = SurpriseEventRules.pickType(random,
                        List.copyOf(settings.multipliers().keySet()), state.lastType());
                if (type.isPresent()) {
                    repository.start(state.seq(), type.get(), settings.multipliers().get(type.get()),
                            now + settings.durationMinutes() * 60_000L);
                    state = repository.state();
                }
            } else if (state.active() && now >= state.endsAt()) {
                repository.end(state.seq(), now + nextDelay());
                state = repository.state();
            }
            SurpriseEventRepository.State latest = state;
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> apply(latest));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "깜짝 이벤트 상태 처리 실패", e);
        }
    }

    /** Staff start; {@code requested} empty = random. False if an event is already running. */
    public boolean forceStart(Optional<SurpriseEventType> requested) throws SQLException {
        SurpriseEventRepository.State state = repository.state();
        if (state.active()) {
            return false;
        }
        Optional<SurpriseEventType> type = requested.isPresent() ? requested
                : SurpriseEventRules.pickType(random, List.copyOf(settings.multipliers().keySet()), state.lastType());
        if (type.isEmpty()) {
            return false;
        }
        double multiplier = settings.multipliers().getOrDefault(type.get(), type.get() == SurpriseEventType.TREASURE_DROP ? 3.0 : 2.0);
        boolean started = repository.start(state.seq(), type.get(), multiplier,
                System.currentTimeMillis() + settings.durationMinutes() * 60_000L);
        if (started) {
            tick();
        }
        return started;
    }

    /** Staff end. False if nothing is running. */
    public boolean forceEnd() throws SQLException {
        SurpriseEventRepository.State state = repository.state();
        if (!state.active()) {
            return false;
        }
        boolean ended = repository.end(state.seq(), System.currentTimeMillis() + nextDelay());
        if (ended) {
            tick();
        }
        return ended;
    }

    private long nextDelay() {
        return SurpriseEventRules.nextDelayMillis(random, settings.intervalMinMinutes(), settings.intervalMaxMinutes());
    }

    // ---- main thread ----

    private void apply(SurpriseEventRepository.State state) {
        if (current != null && state.seq() < current.seq()) {
            return; // an older read finished late
        }
        current = state;
        SurpriseEventType wanted = state.active() ? state.type() : null;
        if (appliedType != wanted) {
            revert();
            if (wanted != null) {
                applyBoost(wanted, state.multiplier());
            }
        }
        if (announcedSeq < 0) {
            announcedSeq = state.seq(); // first read after startup isn't news
        } else if (state.seq() != announcedSeq) {
            announcedSeq = state.seq();
            announce(state);
        }
        updateBossBar();
    }

    /** Sets the boost; for shared landStats multipliers only when they're at 1.0 (a manual /이벤트 otherwise owns them — retried next tick). */
    private void applyBoost(SurpriseEventType type, double multiplier) {
        switch (type) {
            case LAND_XP -> {
                if (!SurpriseEventRules.stillOurs(core.landStats().getXpMultiplier(), 1.0)) {
                    return;
                }
                core.landStats().setXpMultiplier(multiplier);
            }
            case CROP_DROP -> {
                if (!SurpriseEventRules.stillOurs(core.landStats().getCropDropMultiplier(), 1.0)) {
                    return;
                }
                core.landStats().setCropDropMultiplier(multiplier);
            }
            case TREASURE_DROP -> LifeBoosts.setTreasureDropMultiplier(multiplier);
            case JOB_XP -> LifeBoosts.setJobXpMultiplier(multiplier);
        }
        appliedType = type;
        appliedValue = multiplier;
    }

    private void revert() {
        if (appliedType == null) {
            return;
        }
        switch (appliedType) {
            case LAND_XP -> {
                if (SurpriseEventRules.stillOurs(core.landStats().getXpMultiplier(), appliedValue)) {
                    core.landStats().setXpMultiplier(1.0);
                }
            }
            case CROP_DROP -> {
                if (SurpriseEventRules.stillOurs(core.landStats().getCropDropMultiplier(), appliedValue)) {
                    core.landStats().setCropDropMultiplier(1.0);
                }
            }
            case TREASURE_DROP -> LifeBoosts.setTreasureDropMultiplier(1.0);
            case JOB_XP -> LifeBoosts.setJobXpMultiplier(1.0);
        }
        appliedType = null;
    }

    private void announce(SurpriseEventRepository.State state) {
        if (state.type() == null) {
            return;
        }
        String multiplier = SurpriseEventRules.formatMultiplier(state.multiplier());
        if (state.active()) {
            long minutes = Math.max(1, (state.endsAt() - System.currentTimeMillis() + 59_999) / 60_000);
            messages.broadcast("surprise.started",
                    Placeholder.unparsed("type", state.type().label()),
                    Placeholder.unparsed("multiplier", multiplier),
                    Placeholder.unparsed("minutes", String.valueOf(minutes)));
        } else {
            messages.broadcast("surprise.ended",
                    Placeholder.unparsed("type", state.type().label()),
                    Placeholder.unparsed("multiplier", multiplier));
        }
    }

    /** Main thread, every second: boss bar while our boost is applied, removed otherwise. */
    public void updateBossBar() {
        if (current == null || !current.active() || appliedType == null) {
            if (bossBar != null) {
                bossBar.removeAll();
                bossBar = null;
            }
            return;
        }
        long remaining = Math.max(0, current.endsAt() - System.currentTimeMillis());
        String title = "🎉 깜짝 이벤트: " + appliedType.label() + " " + SurpriseEventRules.formatMultiplier(appliedValue)
                + "배 — 남은 시간 " + DurationFormat.humanize(remaining);
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(title, BarColor.PINK, BarStyle.SOLID);
        } else {
            bossBar.setTitle(title);
        }
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, remaining / (settings.durationMinutes() * 60_000.0))));
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
    }

    public void status(CommandSender sender) {
        SurpriseEventRepository.State state = current;
        long now = System.currentTimeMillis();
        if (state != null && state.active() && state.type() != null) {
            messages.send(sender, "surprise.status-active",
                    Placeholder.unparsed("type", state.type().label()),
                    Placeholder.unparsed("multiplier", SurpriseEventRules.formatMultiplier(state.multiplier())),
                    Placeholder.unparsed("remaining", DurationFormat.humanize(Math.max(0, state.endsAt() - now))));
        } else if (state != null && state.nextAt() > now) {
            messages.send(sender, "surprise.status-idle",
                    Placeholder.unparsed("remaining", DurationFormat.humanize(state.nextAt() - now)));
        } else {
            messages.send(sender, "surprise.status-soon");
        }
    }

    /** Main thread, from onDisable: put every multiplier back and drop the boss bar. */
    public void shutdown() {
        revert();
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }
}
```

- [ ] **Step 3: Create `SurpriseEventCommand.java`**

```java
package com.yeowool.life.surprise;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/** {@code /깜짝이벤트}: status for everyone; start/end for staff on participating servers. */
public final class SurpriseEventCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "yeowool.event.manage";
    private static final List<String> TYPE_INPUTS = Arrays.stream(SurpriseEventType.values())
            .map(type -> type.label().replace(" ", "")).toList();

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final SurpriseEventService service;
    private final Executor executor;
    private final boolean participating;

    public SurpriseEventCommand(JavaPlugin plugin, MessageService messages, SurpriseEventService service,
                                Executor executor, boolean participating) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
        this.participating = participating;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!participating) {
            messages.send(sender, "surprise.not-here");
            return true;
        }
        if (args.length == 0) {
            service.status(sender);
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        switch (args[0]) {
            case "시작" -> {
                Optional<SurpriseEventType> requested = Optional.empty();
                if (args.length >= 2) {
                    requested = SurpriseEventType.parse(args[1]);
                    if (requested.isEmpty()) {
                        messages.send(sender, "surprise.unknown-type");
                        return true;
                    }
                }
                Optional<SurpriseEventType> type = requested;
                async(sender, () -> reply(sender, service.forceStart(type) ? "surprise.admin-started" : "surprise.already-active"));
            }
            case "종료" -> async(sender, () -> reply(sender, service.forceEnd() ? "surprise.admin-ended" : "surprise.none"));
            default -> messages.send(sender, "surprise.usage");
        }
        return true;
    }

    @FunctionalInterface
    private interface SqlTask {
        void run() throws SQLException;
    }

    private void async(CommandSender sender, SqlTask task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "깜짝 이벤트 명령 처리 실패", e);
                reply(sender, "surprise.error");
            }
        });
    }

    private void reply(CommandSender sender, String key) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!participating || !sender.hasPermission(ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("시작", "종료").stream().filter(s -> s.startsWith(args[0])).toList();
        }
        if (args.length == 2 && args[0].equals("시작")) {
            return TYPE_INPUTS.stream().filter(s -> s.startsWith(args[1])).toList();
        }
        return List.of();
    }
}
```

- [ ] **Step 4: Boost hooks**
  - `TreasureService.onAction`: change `double chance = settings.dropChances().getOrDefault(activity, 0.0);` to
    `double chance = settings.dropChances().getOrDefault(activity, 0.0) * LifeBoosts.treasureDropMultiplier();` and import `com.yeowool.life.surprise.LifeBoosts`.
  - `JobManager.grantXp`: change `long amount = Math.round(effectiveBase * (1 + bonusPercent / 100.0));` to
    `long amount = Math.round(effectiveBase * (1 + bonusPercent / 100.0) * LifeBoosts.jobXpMultiplier());` and import `com.yeowool.life.surprise.LifeBoosts`.

- [ ] **Step 5: Wire into `YeowoolLife.java`**

Add imports `com.yeowool.life.surprise.SurpriseEventCommand`, `SurpriseEventRepository`, `SurpriseEventRules`, `SurpriseEventService`, `SurpriseEventType`, `java.util.LinkedHashMap`, `java.util.Random` (skip any already present). Add a field `private SurpriseEventService surpriseEvents;`. Call `enableSurpriseEvents(core, messages);` right after `enableMounts(core, messages);` and add:

```java
    /** 깜짝 이벤트 — surprise-event.worlds의 월드가 있는 서버(마을·야생)끼리 같은 짧은 버프 이벤트를 자동으로 연다. */
    private void enableSurpriseEvents(YeowoolCoreAPI core, MessageManager messages) {
        var config = getConfig();
        if (!config.getBoolean("surprise-event.enabled", true)) {
            return;
        }
        boolean participating = config.getStringList("surprise-event.worlds").stream().anyMatch(world -> Bukkit.getWorld(world) != null);
        Map<SurpriseEventType, Double> multipliers = new LinkedHashMap<>();
        for (SurpriseEventType type : SurpriseEventType.values()) {
            String path = "surprise-event.types." + type.key();
            if (config.getBoolean(path + ".enabled", true)) {
                multipliers.put(type, config.getDouble(path + ".multiplier", type == SurpriseEventType.TREASURE_DROP ? 3.0 : 2.0));
            }
        }
        SurpriseEventService.Settings settings = new SurpriseEventService.Settings(
                Math.max(1, config.getInt("surprise-event.duration-minutes", 30)),
                config.getInt("surprise-event.interval-min-minutes", 60),
                config.getInt("surprise-event.interval-max-minutes", 120),
                multipliers);
        SurpriseEventRepository repository = new SurpriseEventRepository(core.dataSource());
        try {
            repository.createTables(System.currentTimeMillis()
                    + SurpriseEventRules.nextDelayMillis(new Random(), settings.intervalMinMinutes(), settings.intervalMaxMinutes()));
        } catch (Exception e) {
            getLogger().severe("깜짝 이벤트 데이터베이스 초기화 실패 — 깜짝 이벤트를 끕니다: " + e.getMessage());
            return;
        }
        SurpriseEventService service = new SurpriseEventService(this, core, messages, repository, settings);
        var command = getCommand("깜짝이벤트");
        if (command != null) {
            var executorCmd = new SurpriseEventCommand(this, messages, service, executor, participating);
            command.setExecutor(executorCmd);
            command.setTabCompleter(executorCmd);
        }
        if (!participating) {
            return;
        }
        this.surpriseEvents = service;
        getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::tick), 20L * 5, 20L * 20);
        getServer().getScheduler().runTaskTimer(this, service::updateBossBar, 20L, 20L);
        getLogger().info("깜짝 이벤트 참여 서버입니다. 종류: " + multipliers.keySet());
    }
```

In `onDisable`, right after the `lifeCompetition.flushNow()` block (before `executor.shutdown()`):

```java
        if (surpriseEvents != null) {
            surpriseEvents.shutdown();
        }
```

- [ ] **Step 6: `config.yml`** — append:

```yaml

# 깜짝 이벤트 — 아래 worlds 중 하나가 있는 서버(마을·야생)끼리 같은 짧은 버프 이벤트를 자동으로 엽니다(로비는 자동 제외).
# 끝난 뒤 interval-min~max분 사이 무작위로 다음 이벤트, duration-minutes분 진행. 직전과 같은 종류는 피합니다.
# 관리진이 /이벤트·/서버이벤트로 켠 수동 이벤트가 있으면 겹치는 배율(토지 경험치·작물)은 수동 이벤트가 우선입니다.
# 세 서버 설정을 똑같이 맞춰주세요.
surprise-event:
  enabled: true
  worlds: [town_world, wild_world]
  duration-minutes: 30
  interval-min-minutes: 60
  interval-max-minutes: 120
  types:
    land_xp:
      enabled: true
      multiplier: 2.0
    crop_drop:
      enabled: true
      multiplier: 2.0
    treasure_drop:
      enabled: true
      multiplier: 3.0
    job_xp:
      enabled: true
      multiplier: 2.0
```

- [ ] **Step 7: `messages.yml`** — append:

```yaml

surprise:
  started: "<gold>🎉 깜짝 이벤트!</gold> <yellow><type> <multiplier>배 — <minutes>분 동안 (마을·야생)</yellow>"
  ended: "<gray>깜짝 이벤트(<type> <multiplier>배)가 끝났습니다. 다음 이벤트도 곧 찾아옵니다!</gray>"
  status-active: "<gold>깜짝 이벤트 진행 중: <type> <multiplier>배 — 남은 시간 <remaining></gold>"
  status-idle: "<gray>다음 깜짝 이벤트까지 약 <remaining></gray>"
  status-soon: "<gray>곧 다음 깜짝 이벤트가 시작됩니다.</gray>"
  not-here: "<gray>깜짝 이벤트는 마을·야생 서버에서 열립니다.</gray>"
  usage: "<gray>/깜짝이벤트 [시작 [토지경험치|작물수확량|보물지도발견|직업경험치] | 종료]</gray>"
  unknown-type: "<red>종류는 토지경험치, 작물수확량, 보물지도발견, 직업경험치 중 하나입니다.</red>"
  admin-started: "<green>깜짝 이벤트를 시작했습니다.</green>"
  already-active: "<red>이미 깜짝 이벤트가 진행 중입니다.</red>"
  admin-ended: "<green>깜짝 이벤트를 종료했습니다.</green>"
  none: "<gray>진행 중인 깜짝 이벤트가 없습니다.</gray>"
  error: "<red>처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.</red>"
```

- [ ] **Step 8: `plugin.yml`** — add under `commands:` (after the last command):

```yaml
  깜짝이벤트:
    description: 진행 중인 깜짝 이벤트 확인 (관리진은 시작/종료)
```

- [ ] **Step 9: Help** — in `yeowool-core/src/main/resources/help.yml`, add after the `/탈것 - ...` player line:

```yaml
      - "/깜짝이벤트 - 마을·야생에서 1~2시간마다 열리는 30분 버프 이벤트 확인"
```

and after the admin line starting with `      - "/탈것이용권`:

```yaml
      - "/깜짝이벤트 시작 [종류], /깜짝이벤트 종료 (yeowool.event.manage, 마을·야생에서)"
```

- [ ] **Step 10: Build** — `./gradlew :yeowool-life:build :yeowool-core:build` → BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
git add yeowool-life/src/main/java/com/yeowool/life/surprise/SurpriseEventRepository.java yeowool-life/src/main/java/com/yeowool/life/surprise/SurpriseEventService.java yeowool-life/src/main/java/com/yeowool/life/surprise/SurpriseEventCommand.java yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureService.java yeowool-life/src/main/java/com/yeowool/life/job/JobManager.java yeowool-life/src/main/java/com/yeowool/life/YeowoolLife.java yeowool-life/src/main/resources/config.yml yeowool-life/src/main/resources/messages.yml yeowool-life/src/main/resources/plugin.yml yeowool-core/src/main/resources/help.yml
git commit -m "Add automatic surprise boost events for town and wild

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

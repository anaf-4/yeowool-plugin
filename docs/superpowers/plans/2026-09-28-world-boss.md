# 필드 월드보스 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A daily MythicMobs world boss on the wild server: announced 10 minutes ahead on every server, spawned at a random registered spot, damage-ranked rewards through `core.payouts()`, escapes after 30 minutes.

**Architecture:** New package `com.yeowool.raid.worldboss` in `yeowool-raid`. The server whose worlds include `world-boss.world` is the *owner*: it alone drives the state machine (IDLE → ANNOUNCED → ACTIVE → IDLE) on its main thread, tracks damage, and writes the single state row; every other server polls that row every 30 s and announces each new `seq` once. Pure reward/schedule math lives in `WorldBossRules` (tested).

**Tech Stack:** Paper 1.21.4, Java 21, MythicMobs API (`libs/MythicMobs.jar`, compileOnly, already used by raids), MySQL via core `DataSource`, core `PayoutService`, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-28-dex-competition-worldboss-mounts-design.md` (section 3)

## Global Constraints

- Build from `C:/Users/apple/OneDrive/Desktop/YEOWOOL_PLUGIN`: `./gradlew :yeowool-raid:build`. OneDrive lock → `rm -rf yeowool-*/build/test-results/test/binary` and rerun.
- JDBC only on the raid executor; Bukkit/MythicMobs on the main thread. `core.payouts().enqueue(...)` is blocking JDBC — executor only.
- Player-visible text Korean via `MessageService` keys in a new `yeowool-raid/src/main/resources/messages.yml` (MiniMessage, placeholders with `_`); user values via `Placeholder.unparsed`.
- Commits: stage only the task's files (never `git add -A`/`.`, never `homepage-plan.md`), never `--amend`, message ends with a blank line then exactly `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Do not dispatch subagents.

---

### Task 1: WorldBossRules + tests

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/worldboss/WorldBossRules.java`
- Test: `yeowool-raid/src/test/java/com/yeowool/raid/worldboss/WorldBossRulesTest.java`

**Interfaces:**
- Produces: `record WorldBossRules.Reward(UUID player, int rank, long amount)` (rank 0 = participation only); `rewards(Map<UUID, Double> damage, double maxHealth, List<Long> rankRewards, long participationReward, double minDamageShare) -> List<Reward>` (highest damage first); `topByDamage(Map<UUID, Double>, int limit) -> List<UUID>`; `announceDue(ZonedDateTime now, LocalTime spawnTime, int announceMinutes, int fightMinutes, String lastEventDate) -> Optional<ZonedDateTime>` (today's spawn time if its announce window is open and today's event hasn't happened).

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.raid.worldboss;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBossRulesTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    private static final UUID E = UUID.fromString("00000000-0000-0000-0000-00000000000e");

    @Test
    void topThreeGetRankPlusParticipationAndSmallHittersGetNothing() {
        Map<UUID, Double> damage = new LinkedHashMap<>();
        damage.put(A, 50.0);
        damage.put(B, 300.0);
        damage.put(C, 100.0);
        damage.put(D, 40.0);
        damage.put(E, 4.0); // below 1% of 500
        List<WorldBossRules.Reward> rewards = WorldBossRules.rewards(damage, 500, List.of(100_000L, 50_000L, 30_000L), 5_000, 0.01);
        assertEquals(4, rewards.size());
        assertEquals(new WorldBossRules.Reward(B, 1, 105_000), rewards.get(0));
        assertEquals(new WorldBossRules.Reward(C, 2, 55_000), rewards.get(1));
        assertEquals(new WorldBossRules.Reward(A, 3, 35_000), rewards.get(2));
        assertEquals(new WorldBossRules.Reward(D, 0, 5_000), rewards.get(3));
    }

    @Test
    void emptyDamageGivesNoRewards() {
        assertTrue(WorldBossRules.rewards(Map.of(), 500, List.of(100_000L), 5_000, 0.01).isEmpty());
    }

    @Test
    void topByDamageOrdersAndLimits() {
        Map<UUID, Double> damage = Map.of(A, 1.0, B, 3.0, C, 2.0);
        assertEquals(List.of(B, C), WorldBossRules.topByDamage(damage, 2));
    }

    @Test
    void announceWindowOpensAheadAndClosesAfterTheFight() {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        LocalTime spawn = LocalTime.of(21, 0);
        ZonedDateTime early = ZonedDateTime.of(2026, 9, 28, 20, 49, 0, 0, zone);
        ZonedDateTime open = ZonedDateTime.of(2026, 9, 28, 20, 50, 0, 0, zone);
        ZonedDateTime fighting = ZonedDateTime.of(2026, 9, 28, 21, 29, 0, 0, zone);
        ZonedDateTime over = ZonedDateTime.of(2026, 9, 28, 21, 30, 0, 0, zone);
        assertTrue(WorldBossRules.announceDue(early, spawn, 10, 30, null).isEmpty());
        assertEquals(ZonedDateTime.of(2026, 9, 28, 21, 0, 0, 0, zone), WorldBossRules.announceDue(open, spawn, 10, 30, null).orElseThrow());
        assertTrue(WorldBossRules.announceDue(fighting, spawn, 10, 30, "2026-09-27").isPresent());
        assertTrue(WorldBossRules.announceDue(fighting, spawn, 10, 30, "2026-09-28").isEmpty());
        assertTrue(WorldBossRules.announceDue(over, spawn, 10, 30, null).isEmpty());
    }
}
```

- [ ] **Step 2: Run** `./gradlew :yeowool-raid:test --tests "com.yeowool.raid.worldboss.WorldBossRulesTest"` → FAIL (class missing).

- [ ] **Step 3: Create `WorldBossRules.java`**

```java
package com.yeowool.raid.worldboss;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** World boss reward and schedule math, kept free of Bukkit so it can be unit tested. */
public final class WorldBossRules {

    /** {@code rank} is 1.. for ranked rewards, 0 for participation only; {@code amount} includes participation. */
    public record Reward(UUID player, int rank, long amount) {
    }

    private static final Comparator<Map.Entry<UUID, Double>> BY_DAMAGE =
            Map.Entry.<UUID, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey());

    private WorldBossRules() {
    }

    /**
     * Everyone who dealt at least {@code minDamageShare} of the boss's max health gets the
     * participation reward; the top {@code rankRewards.size()} of them also get their rank reward.
     */
    public static List<Reward> rewards(Map<UUID, Double> damage, double maxHealth, List<Long> rankRewards,
                                       long participationReward, double minDamageShare) {
        double threshold = maxHealth * minDamageShare;
        List<Map.Entry<UUID, Double>> qualified = damage.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && entry.getValue() >= threshold)
                .sorted(BY_DAMAGE)
                .toList();
        List<Reward> rewards = new ArrayList<>();
        for (int i = 0; i < qualified.size(); i++) {
            boolean ranked = i < rankRewards.size();
            long amount = participationReward + (ranked ? rankRewards.get(i) : 0);
            rewards.add(new Reward(qualified.get(i).getKey(), ranked ? i + 1 : 0, amount));
        }
        return rewards;
    }

    public static List<UUID> topByDamage(Map<UUID, Double> damage, int limit) {
        return damage.entrySet().stream().sorted(BY_DAMAGE).limit(limit).map(Map.Entry::getKey).toList();
    }

    /**
     * Today's spawn time, if today's event hasn't happened yet ({@code lastEventDate} ≠ today) and
     * {@code now} is inside [spawn − announce, spawn + fight). Spawn time must be at least
     * {@code announceMinutes} after midnight.
     */
    public static Optional<ZonedDateTime> announceDue(ZonedDateTime now, LocalTime spawnTime, int announceMinutes,
                                                     int fightMinutes, String lastEventDate) {
        ZonedDateTime spawnAt = now.toLocalDate().atTime(spawnTime).atZone(now.getZone());
        if (spawnAt.toLocalDate().toString().equals(lastEventDate)) {
            return Optional.empty();
        }
        boolean open = !now.isBefore(spawnAt.minusMinutes(announceMinutes)) && now.isBefore(spawnAt.plusMinutes(fightMinutes));
        return open ? Optional.of(spawnAt) : Optional.empty();
    }
}
```

- [ ] **Step 4: Run** the same test → PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/worldboss/WorldBossRules.java yeowool-raid/src/test/java/com/yeowool/raid/worldboss/WorldBossRulesTest.java
git commit -m "Add world boss reward and schedule rules

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

(If `yeowool-raid` has no test source set configured yet, it does — its `build.gradle.kts` already has `testImplementation` entries; JUnit comes from the root build like other modules.)

---

### Task 2: WorldBossRepository + WorldBossService

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/worldboss/WorldBossRepository.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/worldboss/WorldBossService.java`

**Interfaces:**
- Consumes: Task 1; `core.payouts().enqueue(UUID, long, String, String) throws SQLException`; MythicMobs `io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().getMythicMob(String) -> Optional<MythicMob>`, `MythicMob.spawn(AbstractLocation, double) -> ActiveMob`, `io.lumine.mythic.bukkit.BukkitAdapter.adapt(Location)`, `ActiveMob.getUniqueId()`, `ActiveMob.getEntity().getBukkitEntity()`.
- Produces: `WorldBossRepository(DataSource)`, records `Spot(String name, String world, double x, double y, double z)` and `State(long seq, String phase, Spot spot, String eventDate, long spawnAt, long despawnAt, String bossUuid, String lastOutcome, String lastTop)`; `createTables()`, `saveSpot(Spot)`, `deleteSpot(String) -> boolean`, `spots() -> List<Spot>`, `state() -> State`, `saveState(State)`. `WorldBossService(JavaPlugin, YeowoolCoreAPI, MessageService, WorldBossRepository, Executor, Settings)` with `record Settings(String world, String mobId, LocalTime spawnTime, int announceMinutes, int fightMinutes, List<Long> rankRewards, long participationReward, double minDamageShare)`; `isOwner()`, `start()`, `ownerTick()` (main), `poll()` (executor, non-owner), `summon(CommandSender)`, `dismiss(CommandSender)` (main, owner), `showStatus(CommandSender)` (main), `settings()`, `repository()`, `reloadSpots()`.

- [ ] **Step 1: Create `WorldBossRepository.java`**

```java
package com.yeowool.raid.worldboss;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Boss spots and the single shared state row (id = 1) — only the owner server writes the state. */
public final class WorldBossRepository {

    public record Spot(String name, String world, double x, double y, double z) {
    }

    /**
     * {@code phase}: IDLE / ANNOUNCED / ACTIVE. {@code lastOutcome}: KILLED / ESCAPED / FAILED (meaningful
     * while IDLE after an event). {@code lastTop}: up to three top damage dealers' names joined by '|'.
     */
    public record State(long seq, String phase, Spot spot, String eventDate, long spawnAt, long despawnAt,
                        String bossUuid, String lastOutcome, String lastTop) {
    }

    private static final String SPOTS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_worldboss_spots (
                name VARCHAR(32) NOT NULL PRIMARY KEY,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String STATE_DDL = """
            CREATE TABLE IF NOT EXISTS yw_worldboss_state (
                id TINYINT NOT NULL PRIMARY KEY,
                seq BIGINT NOT NULL DEFAULT 0,
                phase VARCHAR(16) NOT NULL DEFAULT 'IDLE',
                spot_name VARCHAR(32) NULL,
                world VARCHAR(64) NULL,
                x DOUBLE NULL,
                y DOUBLE NULL,
                z DOUBLE NULL,
                event_date VARCHAR(10) NULL,
                spawn_at BIGINT NOT NULL DEFAULT 0,
                despawn_at BIGINT NOT NULL DEFAULT 0,
                boss_uuid CHAR(36) NULL,
                last_outcome VARCHAR(16) NULL,
                last_top VARCHAR(255) NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final DataSource dataSource;

    public WorldBossRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(SPOTS_DDL);
            statement.executeUpdate(STATE_DDL);
            statement.executeUpdate("INSERT IGNORE INTO yw_worldboss_state (id) VALUES (1)");
        }
    }

    public void saveSpot(Spot spot) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_worldboss_spots (name, world, x, y, z) VALUES (?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z)")) {
            ps.setString(1, spot.name());
            ps.setString(2, spot.world());
            ps.setDouble(3, spot.x());
            ps.setDouble(4, spot.y());
            ps.setDouble(5, spot.z());
            ps.executeUpdate();
        }
    }

    public boolean deleteSpot(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM yw_worldboss_spots WHERE name = ?")) {
            ps.setString(1, name);
            return ps.executeUpdate() == 1;
        }
    }

    public List<Spot> spots() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT name, world, x, y, z FROM yw_worldboss_spots ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            List<Spot> spots = new ArrayList<>();
            while (rs.next()) {
                spots.add(new Spot(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5)));
            }
            return spots;
        }
    }

    public State state() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT seq, phase, spot_name, world, x, y, z, event_date, spawn_at, despawn_at, boss_uuid, last_outcome, last_top "
                             + "FROM yw_worldboss_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("yw_worldboss_state 행이 없습니다");
            }
            String spotName = rs.getString(3);
            Spot spot = spotName == null ? null : new Spot(spotName, rs.getString(4), rs.getDouble(5), rs.getDouble(6), rs.getDouble(7));
            return new State(rs.getLong(1), rs.getString(2), spot, rs.getString(8), rs.getLong(9), rs.getLong(10),
                    rs.getString(11), rs.getString(12), rs.getString(13));
        }
    }

    public void saveState(State state) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_worldboss_state SET seq = ?, phase = ?, spot_name = ?, world = ?, x = ?, y = ?, z = ?, event_date = ?, "
                             + "spawn_at = ?, despawn_at = ?, boss_uuid = ?, last_outcome = ?, last_top = ? WHERE id = 1")) {
            Spot spot = state.spot();
            ps.setLong(1, state.seq());
            ps.setString(2, state.phase());
            ps.setString(3, spot == null ? null : spot.name());
            ps.setString(4, spot == null ? null : spot.world());
            ps.setObject(5, spot == null ? null : spot.x());
            ps.setObject(6, spot == null ? null : spot.y());
            ps.setObject(7, spot == null ? null : spot.z());
            ps.setString(8, state.eventDate());
            ps.setLong(9, state.spawnAt());
            ps.setLong(10, state.despawnAt());
            ps.setString(11, state.bossUuid());
            ps.setString(12, state.lastOutcome());
            ps.setString(13, state.lastTop());
            ps.executeUpdate();
        }
    }
}
```

- [ ] **Step 2: Create `WorldBossService.java`**

```java
package com.yeowool.raid.worldboss;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Daily field world boss. The owner server (the one that has {@code world-boss.world}) runs the
 * whole state machine on its main thread — IDLE → ANNOUNCED → ACTIVE → IDLE — and persists every
 * transition (bumping {@code seq}) to the shared state row; every other server polls that row and
 * announces each new {@code seq} once. Rewards go through the core payout ledger, so winners on
 * other servers or offline get paid on their next join.
 */
public final class WorldBossService implements Listener {

    public record Settings(String world, String mobId, LocalTime spawnTime, int announceMinutes, int fightMinutes,
                           List<Long> rankRewards, long participationReward, double minDamageShare) {
    }

    private static final String SOURCE = "YeowoolRaid";
    private static final String IDLE = "IDLE";
    private static final String ANNOUNCED = "ANNOUNCED";
    private static final String ACTIVE = "ACTIVE";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final WorldBossRepository repository;
    private final Executor executor;
    private final Settings settings;
    private final boolean owner;
    private final Random random = new Random();

    // main thread only
    private WorldBossRepository.State state;
    private long announcedSeq = -1;
    private boolean choosingSpot;
    private String warnedNoSpotsDate;
    private final Map<UUID, Double> damage = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private double bossMaxHealth = 1;
    private Chunk ticketChunk;

    public WorldBossService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, WorldBossRepository repository,
                            Executor executor, Settings settings) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.executor = executor;
        this.settings = settings;
        this.owner = Bukkit.getWorld(settings.world()) != null;
    }

    public boolean isOwner() {
        return owner;
    }

    public Settings settings() {
        return settings;
    }

    public WorldBossRepository repository() {
        return repository;
    }

    /** Main thread, once from onEnable: loads the shared state; the owner cleans up a fight cut short by a restart. */
    public void start() {
        executor.execute(() -> {
            WorldBossRepository.State loaded;
            try {
                loaded = repository.state();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "월드보스 상태 불러오기 실패", e);
                return;
            }
            runOnMain(() -> {
                state = loaded;
                announcedSeq = loaded.seq();
                if (owner && ACTIVE.equals(loaded.phase())) {
                    removeBossEntity(loaded.bossUuid());
                    transition(IDLE, loaded.spot(), loaded.eventDate(), 0, 0, null, "ESCAPED", null);
                }
            });
        });
    }

    // ---- owner state machine (main thread) ----

    /** Main thread, every 5 seconds on the owner. */
    public void ownerTick() {
        if (!owner || state == null) {
            return;
        }
        long now = System.currentTimeMillis();
        switch (state.phase()) {
            case IDLE -> WorldBossRules.announceDue(ZonedDateTime.now(), settings.spawnTime(), settings.announceMinutes(),
                            settings.fightMinutes(), state.eventDate())
                    .ifPresent(spawnAt -> chooseSpotAndAnnounce(spawnAt.toLocalDate().toString(), spawnAt.toInstant().toEpochMilli(), null));
            case ANNOUNCED -> {
                if (now >= state.spawnAt()) {
                    spawnBoss();
                }
            }
            case ACTIVE -> {
                if (now >= state.despawnAt()) {
                    removeBossEntity(state.bossUuid());
                    endFight("ESCAPED", null);
                }
            }
            default -> {
            }
        }
    }

    /** Loads spots off-thread, then (main thread) announces at a random one — or skips today if none exist. */
    private void chooseSpotAndAnnounce(String eventDate, long spawnAt, CommandSender requester) {
        if (choosingSpot) {
            return;
        }
        choosingSpot = true;
        executor.execute(() -> {
            List<WorldBossRepository.Spot> spots;
            try {
                spots = repository.spots();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "월드보스 위치 불러오기 실패", e);
                spots = null;
            }
            List<WorldBossRepository.Spot> loaded = spots;
            runOnMain(() -> {
                choosingSpot = false;
                if (loaded == null || !IDLE.equals(state.phase())) {
                    return;
                }
                List<WorldBossRepository.Spot> usable = loaded.stream().filter(s -> s.world().equals(settings.world())).toList();
                if (usable.isEmpty()) {
                    if (requester != null) {
                        messages.send(requester, "worldboss.no-spots");
                    } else if (!eventDate.equals(warnedNoSpotsDate)) {
                        warnedNoSpotsDate = eventDate;
                        plugin.getLogger().warning("월드보스 위치가 없어 오늘 월드보스를 건너뜁니다 — /월드보스 위치추가 <이름>으로 등록하세요.");
                    }
                    return;
                }
                WorldBossRepository.Spot spot = usable.get(random.nextInt(usable.size()));
                transition(ANNOUNCED, spot, eventDate, spawnAt, 0, null, null, null);
                if (requester != null) {
                    messages.send(requester, "worldboss.summoned");
                }
            });
        });
    }

    private void spawnBoss() {
        WorldBossRepository.Spot spot = state.spot();
        World world = spot == null ? null : Bukkit.getWorld(spot.world());
        var mythicMob = MythicBukkit.inst().getMobManager().getMythicMob(settings.mobId());
        if (world == null || mythicMob.isEmpty()) {
            plugin.getLogger().severe("월드보스 소환 실패 — 월드(" + (spot == null ? "?" : spot.world()) + ") 또는 MythicMobs 몹(" + settings.mobId() + ")을 찾을 수 없습니다.");
            endFight("FAILED", null);
            return;
        }
        Location location = new Location(world, spot.x(), spot.y(), spot.z());
        Chunk chunk = location.getChunk();
        chunk.addPluginChunkTicket(plugin);
        ticketChunk = chunk;
        ActiveMob activeMob;
        try {
            activeMob = mythicMob.get().spawn(BukkitAdapter.adapt(location), 1.0);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "월드보스 소환 실패", e);
            activeMob = null;
        }
        if (activeMob == null) {
            endFight("FAILED", null);
            return;
        }
        Entity entity = activeMob.getEntity().getBukkitEntity();
        AttributeInstance maxHealth = entity instanceof LivingEntity living ? living.getAttribute(Attribute.MAX_HEALTH) : null;
        bossMaxHealth = maxHealth == null ? 1 : Math.max(1, maxHealth.getValue());
        damage.clear();
        names.clear();
        transition(ACTIVE, spot, state.eventDate(), state.spawnAt(),
                System.currentTimeMillis() + settings.fightMinutes() * 60_000L, activeMob.getUniqueId().toString(), null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isBoss(event.getEntity())) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        double health = event.getEntity() instanceof LivingEntity living ? living.getHealth() : event.getFinalDamage();
        damage.merge(attacker.getUniqueId(), Math.min(event.getFinalDamage(), health), Double::sum);
        names.put(attacker.getUniqueId(), attacker.getName());
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!isBoss(event.getEntity())) {
            return;
        }
        List<WorldBossRules.Reward> rewards = WorldBossRules.rewards(damage, bossMaxHealth, settings.rankRewards(),
                settings.participationReward(), settings.minDamageShare());
        List<String> topNames = new ArrayList<>();
        for (UUID uuid : WorldBossRules.topByDamage(damage, 3)) {
            topNames.add(names.getOrDefault(uuid, "?"));
        }
        executor.execute(() -> {
            for (WorldBossRules.Reward reward : rewards) {
                String reason = reward.rank() > 0 ? "월드보스 " + reward.rank() + "위" : "월드보스 참여";
                try {
                    core.payouts().enqueue(reward.player(), reward.amount(), SOURCE, reason);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "월드보스 보상 장부 기록 실패 — 수동 지급 필요: "
                            + reward.player() + " " + reward.amount() + "온 (" + reason + ")", e);
                }
            }
        });
        endFight("KILLED", String.join("|", topNames));
    }

    private boolean isBoss(Entity entity) {
        return owner && state != null && ACTIVE.equals(state.phase())
                && entity.getUniqueId().toString().equals(state.bossUuid());
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void endFight(String outcome, String top) {
        if (ticketChunk != null) {
            ticketChunk.removePluginChunkTicket(plugin);
            ticketChunk = null;
        }
        damage.clear();
        names.clear();
        transition(IDLE, state.spot(), state.eventDate(), 0, 0, null, outcome, top);
    }

    private void removeBossEntity(String bossUuid) {
        if (bossUuid == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(UUID.fromString(bossUuid));
        if (entity != null) {
            entity.remove();
        }
    }

    /** Owner, main thread: bump seq, remember and announce locally, persist asynchronously (single-thread executor keeps order). */
    private void transition(String phase, WorldBossRepository.Spot spot, String eventDate, long spawnAt, long despawnAt,
                            String bossUuid, String outcome, String top) {
        state = new WorldBossRepository.State(state.seq() + 1, phase, spot, eventDate, spawnAt, despawnAt, bossUuid, outcome, top);
        WorldBossRepository.State snapshot = state;
        announcedSeq = snapshot.seq();
        announce(snapshot);
        executor.execute(() -> {
            try {
                repository.saveState(snapshot);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "월드보스 상태 저장 실패", e);
            }
        });
    }

    // ---- other servers ----

    /** Executor, every 30 seconds on non-owner servers: pick up the owner's transitions and announce each once. */
    public void poll() {
        if (owner) {
            return;
        }
        WorldBossRepository.State loaded;
        try {
            loaded = repository.state();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "월드보스 상태 조회 실패", e);
            return;
        }
        runOnMain(() -> {
            if (state != null && loaded.seq() < state.seq()) {
                return;
            }
            state = loaded;
            if (announcedSeq < 0) {
                announcedSeq = loaded.seq();
            } else if (loaded.seq() != announcedSeq) {
                announcedSeq = loaded.seq();
                announce(loaded);
            }
        });
    }

    private void announce(WorldBossRepository.State s) {
        String spotName = s.spot() == null ? "?" : s.spot().name();
        switch (s.phase()) {
            case ANNOUNCED -> messages.broadcast("worldboss.announced",
                    Placeholder.unparsed("spot", spotName),
                    Placeholder.unparsed("minutes", String.valueOf(Math.max(0, (s.spawnAt() - System.currentTimeMillis() + 59_999) / 60_000))));
            case ACTIVE -> messages.broadcast("worldboss.spawned",
                    Placeholder.unparsed("spot", spotName),
                    Placeholder.unparsed("x", String.valueOf((long) Math.floor(s.spot().x()))),
                    Placeholder.unparsed("z", String.valueOf((long) Math.floor(s.spot().z()))),
                    Placeholder.unparsed("minutes", String.valueOf(settings.fightMinutes())));
            case IDLE -> {
                if ("KILLED".equals(s.lastOutcome())) {
                    messages.broadcast("worldboss.killed");
                    String[] top = s.lastTop() == null || s.lastTop().isEmpty() ? new String[0] : s.lastTop().split("\\|");
                    for (int i = 0; i < top.length; i++) {
                        messages.broadcast("worldboss.rank-line",
                                Placeholder.unparsed("rank", String.valueOf(i + 1)),
                                Placeholder.unparsed("player", top[i]));
                    }
                } else if ("ESCAPED".equals(s.lastOutcome())) {
                    messages.broadcast("worldboss.escaped");
                }
            }
            default -> {
            }
        }
    }

    // ---- commands (main thread) ----

    public void summon(CommandSender sender) {
        if (!owner) {
            messages.send(sender, "worldboss.owner-only", Placeholder.unparsed("world", settings.world()));
            return;
        }
        if (state == null || !IDLE.equals(state.phase())) {
            messages.send(sender, "worldboss.already-active");
            return;
        }
        chooseSpotAndAnnounce(state.eventDate(), System.currentTimeMillis(), sender);
    }

    public void dismiss(CommandSender sender) {
        if (!owner) {
            messages.send(sender, "worldboss.owner-only", Placeholder.unparsed("world", settings.world()));
            return;
        }
        if (state == null || IDLE.equals(state.phase())) {
            messages.send(sender, "worldboss.none");
            return;
        }
        removeBossEntity(state.bossUuid());
        endFight("ESCAPED", null);
        messages.send(sender, "worldboss.dismissed");
    }

    public void showStatus(CommandSender sender) {
        WorldBossRepository.State s = state;
        long now = System.currentTimeMillis();
        if (s == null || IDLE.equals(s.phase())) {
            messages.send(sender, "worldboss.status-idle",
                    Placeholder.unparsed("time", String.format("%02d:%02d", settings.spawnTime().getHour(), settings.spawnTime().getMinute())));
        } else if (ANNOUNCED.equals(s.phase())) {
            messages.send(sender, "worldboss.status-announced",
                    Placeholder.unparsed("spot", s.spot() == null ? "?" : s.spot().name()),
                    Placeholder.unparsed("remaining", DurationFormat.humanize(Math.max(0, s.spawnAt() - now))));
        } else {
            messages.send(sender, "worldboss.status-active",
                    Placeholder.unparsed("spot", s.spot() == null ? "?" : s.spot().name()),
                    Placeholder.unparsed("x", String.valueOf(s.spot() == null ? 0 : (long) Math.floor(s.spot().x()))),
                    Placeholder.unparsed("z", String.valueOf(s.spot() == null ? 0 : (long) Math.floor(s.spot().z()))),
                    Placeholder.unparsed("remaining", DurationFormat.humanize(Math.max(0, s.despawnAt() - now))));
        }
    }

    /** Main thread, from onDisable: an ongoing fight is left to start() on the next boot, which cleans it up. */
    public void shutdown() {
        if (ticketChunk != null) {
            ticketChunk.removePluginChunkTicket(plugin);
            ticketChunk = null;
        }
    }

    private void runOnMain(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
```

- [ ] **Step 3: Build** — `./gradlew :yeowool-raid:build` → BUILD SUCCESSFUL. If a MythicMobs API name differs in `libs/MythicMobs.jar` (check with `"/c/Program Files/Java/jdk-21.0.11/bin/javap.exe" -cp "$(cygpath -w yeowool-raid/libs/MythicMobs.jar)" io.lumine.mythic.core.mobs.ActiveMob`), match what `RaidEntryService` already uses. If `Attribute.MAX_HEALTH` doesn't exist in the Paper API version, use `Attribute.GENERIC_MAX_HEALTH`.

- [ ] **Step 4: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/worldboss/WorldBossRepository.java yeowool-raid/src/main/java/com/yeowool/raid/worldboss/WorldBossService.java
git commit -m "Add world boss state machine, damage tracking and rewards

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: `/월드보스` command, wiring, resources, help

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/worldboss/WorldBossCommand.java`
- Create: `yeowool-raid/src/main/resources/messages.yml`
- Modify: `yeowool-raid/src/main/java/com/yeowool/raid/YeowoolRaid.java`
- Modify: `yeowool-raid/src/main/resources/config.yml` (append), `plugin.yml`
- Modify: `yeowool-core/src/main/resources/help.yml`

**Interfaces:**
- Consumes: Task 2. `com.yeowool.core.message.MessageManager(JavaPlugin)` (merges defaults from the jar's `messages.yml`).

- [ ] **Step 1: Create `WorldBossCommand.java`**

```java
package com.yeowool.raid.worldboss;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** {@code /월드보스}: status for everyone; spot management and summon/dismiss for staff. */
public final class WorldBossCommand implements CommandExecutor, TabCompleter {

    @FunctionalInterface
    private interface SqlTask {
        void run() throws SQLException;
    }

    private static final String ADMIN = "yeowool.event.manage";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9가-힣_]{1,32}");
    private static final List<String> SUBCOMMANDS = List.of("위치추가", "위치제거", "위치목록", "소환", "제거");

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final WorldBossService service;
    private final Executor executor;

    public WorldBossCommand(JavaPlugin plugin, MessageService messages, WorldBossService service, Executor executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            service.showStatus(sender);
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "worldboss.no-permission");
            return true;
        }
        switch (args[0]) {
            case "위치추가" -> addSpot(sender, args);
            case "위치제거" -> removeSpot(sender, args);
            case "위치목록" -> listSpots(sender);
            case "소환" -> service.summon(sender);
            case "제거" -> service.dismiss(sender);
            default -> messages.send(sender, "worldboss.usage");
        }
        return true;
    }

    private void addSpot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "worldboss.player-only");
            return;
        }
        if (!player.getWorld().getName().equals(service.settings().world())) {
            messages.send(sender, "worldboss.wrong-world", Placeholder.unparsed("world", service.settings().world()));
            return;
        }
        if (args.length < 2 || !NAME.matcher(args[1]).matches()) {
            messages.send(sender, "worldboss.invalid-name");
            return;
        }
        Location loc = player.getLocation();
        WorldBossRepository.Spot spot = new WorldBossRepository.Spot(args[1], loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        async(sender, () -> {
            service.repository().saveSpot(spot);
            reply(sender, "worldboss.spot-added", Placeholder.unparsed("name", spot.name()));
        });
    }

    private void removeSpot(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "worldboss.usage");
            return;
        }
        String name = args[1];
        async(sender, () -> reply(sender, service.repository().deleteSpot(name) ? "worldboss.spot-removed" : "worldboss.spot-not-found",
                Placeholder.unparsed("name", name)));
    }

    private void listSpots(CommandSender sender) {
        async(sender, () -> {
            List<WorldBossRepository.Spot> spots = service.repository().spots();
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (spots.isEmpty()) {
                    messages.send(sender, "worldboss.spot-list-empty");
                    return;
                }
                for (WorldBossRepository.Spot spot : spots) {
                    messages.send(sender, "worldboss.spot-line",
                            Placeholder.unparsed("name", spot.name()),
                            Placeholder.unparsed("world", spot.world()),
                            Placeholder.unparsed("x", String.valueOf((long) Math.floor(spot.x()))),
                            Placeholder.unparsed("y", String.valueOf((long) Math.floor(spot.y()))),
                            Placeholder.unparsed("z", String.valueOf((long) Math.floor(spot.z()))));
                }
            });
        });
    }

    private void async(CommandSender sender, SqlTask task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "월드보스 명령 처리 실패", e);
                reply(sender, "worldboss.error");
            }
        });
    }

    private void reply(CommandSender sender, String key, TagResolver... placeholders) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key, placeholders));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(ADMIN)) {
            return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(args[0])).toList();
        }
        return List.of();
    }
}
```

- [ ] **Step 2: Create `yeowool-raid/src/main/resources/messages.yml`**

```yaml
prefix: "<gray>[<green>여울</green>]</gray> "

worldboss:
  announced: "<red>[월드보스]</red> <yellow><minutes>분 뒤 야생 '<spot>'에 월드보스가 나타납니다! (/월드보스)</yellow>"
  spawned: "<red>[월드보스]</red> <gold>야생 '<spot>' (X <x>, Z <z>)에 월드보스가 나타났습니다! <minutes>분 안에 쓰러뜨리세요.</gold>"
  killed: "<red>[월드보스]</red> <green>월드보스를 쓰러뜨렸습니다! 보상은 곧 지급됩니다.</green>"
  rank-line: "<gray><rank>위</gray> <white><player></white>"
  escaped: "<red>[월드보스]</red> <gray>월드보스가 도망쳤습니다.</gray>"
  status-idle: "<gray>월드보스는 매일 <time>에 야생에 나타납니다.</gray>"
  status-announced: "<yellow>곧 야생 '<spot>'에 월드보스가 나타납니다 — <remaining> 남음</yellow>"
  status-active: "<gold>월드보스 전투 중: 야생 '<spot>' (X <x>, Z <z>) — 남은 시간 <remaining></gold>"
  usage: "<gray>/월드보스 [위치추가 이름 | 위치제거 이름 | 위치목록 | 소환 | 제거]</gray>"
  no-permission: "<red>권한이 없습니다.</red>"
  player-only: "<red>플레이어만 사용할 수 있는 명령어입니다.</red>"
  wrong-world: "<red>월드보스 위치는 <world> 월드에서만 등록할 수 있습니다.</red>"
  owner-only: "<red>이 명령은 <world> 월드가 있는 서버(야생)에서 실행하세요.</red>"
  invalid-name: "<red>위치 이름은 1~32자의 한글·영문·숫자·_ 만 쓸 수 있습니다.</red>"
  spot-added: "<green>월드보스 위치 '<name>'을(를) 지금 자리로 등록했습니다.</green>"
  spot-removed: "<green>월드보스 위치 '<name>'을(를) 제거했습니다.</green>"
  spot-not-found: "<red>'<name>' 위치를 찾을 수 없습니다.</red>"
  spot-list-empty: "<gray>등록된 월드보스 위치가 없습니다.</gray>"
  spot-line: "<gray>- <name>: <world> (<x>, <y>, <z>)</gray>"
  no-spots: "<red>등록된 월드보스 위치가 없어 소환할 수 없습니다.</red>"
  summoned: "<green>월드보스를 곧바로 소환합니다.</green>"
  already-active: "<red>이미 월드보스가 예고되었거나 전투 중입니다.</red>"
  none: "<gray>지금은 월드보스가 없습니다.</gray>"
  dismissed: "<green>월드보스를 제거했습니다.</green>"
  error: "<red>처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.</red>"
```

- [ ] **Step 3: Append to `yeowool-raid/src/main/resources/config.yml`**

```yaml

# 필드 월드보스 — 매일 spawn-time에 world의 등록된 위치(/월드보스 위치추가) 중 한 곳에 MythicMobs 보스가 나타납니다.
# world가 있는 서버(야생)가 진행을 맡고, 다른 서버는 공지만 받습니다. announce-minutes분 전 예고, fight-minutes분 안에 못 잡으면 도망.
# 보스 최대 체력의 min-damage-percent% 이상 피해를 준 사람 모두 participation-reward, 그중 피해량 1~3위는 rank-rewards를 추가로 받습니다.
# 아이템 드롭은 MythicMobs 보스 설정의 드롭표를 그대로 씁니다. 세 서버 설정을 똑같이 맞춰주세요.
world-boss:
  enabled: true
  world: wild_world
  mythic-mob: alocTheDemonicMech
  spawn-time: "21:00"
  announce-minutes: 10
  fight-minutes: 30
  rank-rewards: [100000, 50000, 30000]
  participation-reward: 5000
  min-damage-percent: 1
```

- [ ] **Step 4: `plugin.yml`** — add under `commands:` (after `레이드:`):

```yaml
  월드보스:
    description: 월드보스 등장 예정/진행 상황 (관리진은 위치추가/위치제거/위치목록/소환/제거)
```

- [ ] **Step 5: Wire into `YeowoolRaid.java`**

Add imports `com.yeowool.core.message.MessageManager`, `com.yeowool.raid.worldboss.WorldBossCommand`, `WorldBossRepository`, `WorldBossService`, `java.time.LocalTime`, `java.time.format.DateTimeParseException`, `java.util.List`. Add a field `private WorldBossService worldBoss;`.

Right before `boolean betterHudEnabled = ...` in `onEnable`, add:

```java
        if (getConfig().getBoolean("world-boss.enabled", true)) {
            enableWorldBoss(core);
        }
```

Add the method:

```java
    private void enableWorldBoss(YeowoolCoreAPI core) {
        WorldBossRepository repository = new WorldBossRepository(core.dataSource());
        try {
            repository.createTables();
        } catch (Exception e) {
            getLogger().severe("월드보스 데이터베이스 초기화 실패 — 월드보스를 끕니다: " + e.getMessage());
            return;
        }
        LocalTime spawnTime;
        try {
            spawnTime = LocalTime.parse(getConfig().getString("world-boss.spawn-time", "21:00").trim());
        } catch (DateTimeParseException e) {
            getLogger().warning("world-boss.spawn-time을 읽지 못해 21:00으로 설정합니다: " + e.getMessage());
            spawnTime = LocalTime.of(21, 0);
        }
        List<Long> rankRewards = getConfig().getLongList("world-boss.rank-rewards");
        WorldBossService.Settings settings = new WorldBossService.Settings(
                getConfig().getString("world-boss.world", "wild_world"),
                getConfig().getString("world-boss.mythic-mob", "alocTheDemonicMech"),
                spawnTime,
                Math.max(0, getConfig().getInt("world-boss.announce-minutes", 10)),
                Math.max(1, getConfig().getInt("world-boss.fight-minutes", 30)),
                rankRewards,
                getConfig().getLong("world-boss.participation-reward", 5000),
                getConfig().getDouble("world-boss.min-damage-percent", 1) / 100.0);
        MessageManager messages = new MessageManager(this);
        this.worldBoss = new WorldBossService(this, core, messages, repository, executor, settings);
        getServer().getPluginManager().registerEvents(worldBoss, this);
        var command = getCommand("월드보스");
        if (command != null) {
            var executorCmd = new WorldBossCommand(this, messages, worldBoss, executor);
            command.setExecutor(executorCmd);
            command.setTabCompleter(executorCmd);
        }
        worldBoss.start();
        WorldBossService service = worldBoss;
        if (service.isOwner()) {
            getServer().getScheduler().runTaskTimer(this, service::ownerTick, 20L * 5, 20L * 5);
            getLogger().info("월드보스 진행 서버입니다 (" + settings.world() + ").");
        } else {
            getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::poll), 20L * 30, 20L * 30);
        }
    }
```

In `onDisable`, before `if (executor != null)`, add:

```java
        if (worldBoss != null) {
            worldBoss.shutdown();
        }
```

- [ ] **Step 6: Help** — in `yeowool-core/src/main/resources/help.yml`, add after the line that mentions `/생활대회`:

```yaml
      - "/월드보스 - 매일 21시 야생에 나타나는 월드보스 예정/진행 상황 (피해량 순위 보상)"
```

and after the admin line that starts with `      - "/레이드 생성|` add:

```yaml
      - "/월드보스 위치추가|위치제거 <이름>, 위치목록, 소환, 제거 (yeowool.event.manage, 소환·제거는 야생 서버에서)"
```

- [ ] **Step 7: Build** — `./gradlew :yeowool-raid:build :yeowool-core:build` → BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/worldboss/WorldBossCommand.java yeowool-raid/src/main/resources/messages.yml yeowool-raid/src/main/java/com/yeowool/raid/YeowoolRaid.java yeowool-raid/src/main/resources/config.yml yeowool-raid/src/main/resources/plugin.yml yeowool-core/src/main/resources/help.yml
git commit -m "Add /월드보스 command and wire the daily world boss

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

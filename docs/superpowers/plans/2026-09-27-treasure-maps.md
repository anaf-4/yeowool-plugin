# 보물지도 탐험 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Treasure maps drop from mining/fishing/hunting on any server; holding one on the wild server shows direction/distance; sneak-right-clicking at the spot digs a virtual treasure with tiered rewards.

**Architecture:** New package `com.yeowool.life.treasure` in `yeowool-life`. Pure math in `TreasureRules` (unit tested); map data lives in the item's PDC and a MySQL row (`yw_treasure_maps`) whose conditional UPDATE makes each map diggable exactly once across servers; reward item pools are in `yw_treasure_rewards`, edited in-game with core's `ItemGridEditorGui`.

**Tech Stack:** Paper 1.21.4 API, Java 21, MySQL via YeowoolCore `DataSource`, ItemsAdder API (optional `CustomStack`), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-27-treasure-maps-design.md`

## Global Constraints

- Java 21, Paper API only. Build: `./gradlew :yeowool-life:build` from `C:/Users/apple/OneDrive/Desktop/YEOWOOL_PLUGIN`. OneDrive lock (`Unable to delete directory ...build/test-results/test/binary`) → `rm -rf yeowool-*/build/test-results/test/binary` and rerun. `yeowool-life` compiles against `libs/AddCook-3.8.2.jar` and `libs/MCPets-4.1.6.jar`, which already exist locally.
- JDBC only on executor threads; Bukkit API, inventories, `core.economyData()`, `core.mailbox()` only on the main thread. Wallet changes only for a player online on this server, checked in the same tick.
- Player-visible text Korean via `MessageService` keys in `yeowool-life/src/main/resources/messages.yml` (MiniMessage; no raw `<...>` in literal text except tags/placeholders). User-controlled values via `Placeholder.unparsed`.
- Currency unit `온`, numbers `String.format("%,d", n)`.
- Commits: stage only the task's files (never `git add -A`/`.`, never `homepage-plan.md`), never `--amend`, message ends with a blank line then exactly `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Do not dispatch subagents.

---

### Task 1: TreasureTier + TreasureRules + tests

**Files:**
- Create: `yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureTier.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureRules.java`
- Test: `yeowool-life/src/test/java/com/yeowool/life/treasure/TreasureRulesTest.java`

**Interfaces:**
- Produces: `enum TreasureTier { COMMON, RARE, LEGENDARY }` with `configKey()`, `label()`, `color()` (`NamedTextColor`), `static Optional<TreasureTier> parse(String)` (matches config key, Korean label, or enum name, case-insensitive).
- `TreasureRules.direction(double dx, double dz) -> String`, `roughDistance(double) -> long`, `randomSpot(Random, int centerX, int centerZ, int minRadius, int maxRadius) -> int[] {x, z}`, `pickTier(Random, Map<TreasureTier,Integer>) -> TreasureTier`, `band(int) -> String`, `<T> pickItems(List<T>, int, Random) -> List<T>`, `randomMoney(Random, long min, long max) -> long`.

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.life.treasure;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasureRulesTest {

    @Test
    void directionUsesMinecraftCompass() {
        assertEquals("북쪽", TreasureRules.direction(0, -10));
        assertEquals("동쪽", TreasureRules.direction(10, 0));
        assertEquals("남쪽", TreasureRules.direction(0, 10));
        assertEquals("서쪽", TreasureRules.direction(-10, 0));
        assertEquals("북동쪽", TreasureRules.direction(10, -10));
        assertEquals("남서쪽", TreasureRules.direction(-10, 10));
        assertEquals("북서쪽", TreasureRules.direction(-10, -10));
        assertEquals("남동쪽", TreasureRules.direction(10, 10));
    }

    @Test
    void roughDistanceIsCoarserFarAway() {
        assertEquals(350, TreasureRules.roughDistance(362));
        assertEquals(1000, TreasureRules.roughDistance(987));
        assertEquals(130, TreasureRules.roughDistance(127));
        assertEquals(10, TreasureRules.roughDistance(3));
    }

    @Test
    void randomSpotStaysInsideTheRing() {
        Random random = new Random(7);
        for (int i = 0; i < 1000; i++) {
            int[] spot = TreasureRules.randomSpot(random, 100, -200, 500, 3000);
            double distance = Math.hypot(spot[0] - 100, spot[1] + 200);
            assertTrue(distance >= 499 && distance <= 3001, "distance " + distance);
        }
    }

    @Test
    void pickTierFollowsWeights() {
        Map<TreasureTier, Integer> onlyRare = new EnumMap<>(TreasureTier.class);
        onlyRare.put(TreasureTier.COMMON, 0);
        onlyRare.put(TreasureTier.RARE, 5);
        onlyRare.put(TreasureTier.LEGENDARY, 0);
        for (int i = 0; i < 100; i++) {
            assertEquals(TreasureTier.RARE, TreasureRules.pickTier(new Random(i), onlyRare));
        }
        assertEquals(TreasureTier.COMMON, TreasureRules.pickTier(new Random(1), new EnumMap<>(TreasureTier.class)));
    }

    @Test
    void bandIsThe200BlockCellContainingTheCoordinate() {
        assertEquals("1200~1400", TreasureRules.band(1234));
        assertEquals("-200~0", TreasureRules.band(-1));
        assertEquals("0~200", TreasureRules.band(0));
    }

    @Test
    void pickItemsReturnsDistinctElementsUpToCount() {
        List<String> pool = List.of("a", "b", "c");
        List<String> picked = TreasureRules.pickItems(pool, 2, new Random(3));
        assertEquals(2, picked.size());
        assertTrue(pool.containsAll(picked));
        assertTrue(!picked.get(0).equals(picked.get(1)));
        assertEquals(3, TreasureRules.pickItems(pool, 10, new Random(3)).size());
        assertEquals(0, TreasureRules.pickItems(List.of(), 2, new Random(3)).size());
    }

    @Test
    void randomMoneyStaysInRange() {
        Random random = new Random(11);
        for (int i = 0; i < 1000; i++) {
            long money = TreasureRules.randomMoney(random, 1000, 5000);
            assertTrue(money >= 1000 && money <= 5000, "money " + money);
        }
        assertEquals(700, TreasureRules.randomMoney(random, 700, 100));
    }

    @Test
    void tierParsesKeysLabelsAndNames() {
        assertEquals(Optional.of(TreasureTier.LEGENDARY), TreasureTier.parse("전설"));
        assertEquals(Optional.of(TreasureTier.RARE), TreasureTier.parse("rare"));
        assertEquals(Optional.of(TreasureTier.COMMON), TreasureTier.parse("COMMON"));
        assertEquals(Optional.empty(), TreasureTier.parse("신화"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :yeowool-life:test --tests "com.yeowool.life.treasure.TreasureRulesTest"`
Expected: FAIL — compilation error (classes don't exist).

- [ ] **Step 3: Create `TreasureTier.java`**

```java
package com.yeowool.life.treasure;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;
import java.util.Optional;

public enum TreasureTier {
    COMMON("common", "일반", NamedTextColor.WHITE),
    RARE("rare", "희귀", NamedTextColor.AQUA),
    LEGENDARY("legendary", "전설", NamedTextColor.GOLD);

    private final String configKey;
    private final String label;
    private final NamedTextColor color;

    TreasureTier(String configKey, String label, NamedTextColor color) {
        this.configKey = configKey;
        this.label = label;
        this.color = color;
    }

    public String configKey() {
        return configKey;
    }

    public String label() {
        return label;
    }

    public NamedTextColor color() {
        return color;
    }

    /** Accepts the config key ({@code rare}), the Korean label ({@code 희귀}) or the enum name, ignoring case. */
    public static Optional<TreasureTier> parse(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (TreasureTier tier : values()) {
            if (tier.configKey.equals(normalized) || tier.label.equals(input.trim()) || tier.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Create `TreasureRules.java`**

```java
package com.yeowool.life.treasure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Treasure map math kept free of Bukkit so it can be unit tested. */
public final class TreasureRules {

    private static final String[] COMPASS = {"북쪽", "북동쪽", "동쪽", "남동쪽", "남쪽", "남서쪽", "서쪽", "북서쪽"};
    private static final int BAND = 200;

    private TreasureRules() {
    }

    /** 8-way compass for the vector (dx, dz) in Minecraft coordinates: +X is east, -Z is north. */
    public static String direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) {
            angle += 360;
        }
        return COMPASS[(int) Math.round(angle / 45.0) % 8];
    }

    /** Distance shown to the player: 50-block steps beyond 200 blocks, 10-block steps within, never 0. */
    public static long roughDistance(double distance) {
        long step = distance > 200 ? 50 : 10;
        return Math.max(step, Math.round(distance / step) * step);
    }

    /** Uniform angle and uniform radius in [minRadius, maxRadius] around the center; returns {x, z}. */
    public static int[] randomSpot(Random random, int centerX, int centerZ, int minRadius, int maxRadius) {
        int min = Math.max(0, minRadius);
        int max = Math.max(min, maxRadius);
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = min + random.nextDouble() * (max - min);
        return new int[]{
                centerX + (int) Math.round(Math.cos(angle) * radius),
                centerZ + (int) Math.round(Math.sin(angle) * radius)
        };
    }

    /** Weighted pick; missing, negative or all-zero weights fall back to COMMON. */
    public static TreasureTier pickTier(Random random, Map<TreasureTier, Integer> weights) {
        int total = 0;
        for (TreasureTier tier : TreasureTier.values()) {
            total += Math.max(0, weights.getOrDefault(tier, 0));
        }
        if (total <= 0) {
            return TreasureTier.COMMON;
        }
        int roll = random.nextInt(total);
        for (TreasureTier tier : TreasureTier.values()) {
            int weight = Math.max(0, weights.getOrDefault(tier, 0));
            if (roll < weight) {
                return tier;
            }
            roll -= weight;
        }
        return TreasureTier.COMMON;
    }

    /** The 200-block band containing {@code coord}, e.g. 1234 → "1200~1400" — the rough area written on the map. */
    public static String band(int coord) {
        int low = Math.floorDiv(coord, BAND) * BAND;
        return low + "~" + (low + BAND);
    }

    /** Up to {@code count} distinct elements of {@code pool} in random order. */
    public static <T> List<T> pickItems(List<T> pool, int count, Random random) {
        List<T> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, random);
        return new ArrayList<>(copy.subList(0, Math.max(0, Math.min(count, copy.size()))));
    }

    /** Uniform in [min, max]; max ≤ min returns max(0, min). */
    public static long randomMoney(Random random, long min, long max) {
        if (max <= min) {
            return Math.max(0, min);
        }
        return min + (long) Math.floor(random.nextDouble() * (max - min + 1));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :yeowool-life:test --tests "com.yeowool.life.treasure.TreasureRulesTest"`
Expected: PASS (8 tests).

- [ ] **Step 6: Commit**

```bash
git add yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureTier.java yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureRules.java yeowool-life/src/test/java/com/yeowool/life/treasure/TreasureRulesTest.java
git commit -m "Add treasure map tiers and pure rules

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: TreasureRepository + TreasureMapItem

**Files:**
- Create: `yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureRepository.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureMapItem.java`

**Interfaces:**
- Consumes: `TreasureTier`, `TreasureRules.band(int)` (Task 1); `com.yeowool.core.util.ItemStackSerializer.serialize(ItemStack) -> String` / `deserialize(String) -> ItemStack`; ItemsAdder `dev.lone.itemsadder.api.CustomStack.getInstance(String) -> CustomStack` (`getItemStack()`).
- Produces:
  - `TreasureRepository(DataSource)`; `record LegendaryDig(long id, String diggerName, long dugAt)`; `void createTables()`, `int countFoundSince(UUID finder, long since)`, `long insertMap(UUID finder, TreasureTier tier, int x, int z, long now, long expiresAt)`, `boolean claim(long id, UUID digger, String diggerName, long now)`, `List<LegendaryDig> legendaryDugSince(long since)`, `List<ItemStack> loadRewards(TreasureTier)`, `void saveRewards(TreasureTier, List<ItemStack>)` — all `throws SQLException`.
  - `TreasureMapItem(JavaPlugin, Map<TreasureTier, String> itemsAdderIds)`; `record TreasureMapItem.MapData(long id, TreasureTier tier, int x, int z, long expiresAt)` with `boolean expired(long now)`; `ItemStack create(MapData)` (main thread), `Optional<MapData> read(ItemStack)` (null-safe).

- [ ] **Step 1: Create `TreasureRepository.java`**

```java
package com.yeowool.life.treasure;

import com.yeowool.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Treasure map rows and per-tier reward pools. A map is dug by a single
 * UPDATE that only matches while nobody has dug it and it hasn't expired, so
 * a copied map item or two servers digging at once still pay out once.
 */
public final class TreasureRepository {

    public record LegendaryDig(long id, String diggerName, long dugAt) {
    }

    private static final String MAPS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_treasure_maps (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                finder CHAR(36) NOT NULL,
                tier VARCHAR(16) NOT NULL,
                x INT NOT NULL,
                z INT NOT NULL,
                created_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                dug_by CHAR(36) NULL,
                dug_by_name VARCHAR(16) NULL,
                dug_at BIGINT NULL,
                INDEX idx_finder_created (finder, created_at),
                INDEX idx_dug_at (dug_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String REWARDS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_treasure_rewards (
                tier VARCHAR(16) NOT NULL,
                slot INT NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                PRIMARY KEY (tier, slot)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final DataSource dataSource;

    public TreasureRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(MAPS_DDL);
            statement.executeUpdate(REWARDS_DDL);
        }
    }

    public int countFoundSince(UUID finder, long since) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM yw_treasure_maps WHERE finder = ? AND created_at >= ?")) {
            ps.setString(1, finder.toString());
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public long insertMap(UUID finder, TreasureTier tier, int x, int z, long now, long expiresAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_treasure_maps (finder, tier, x, z, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, finder.toString());
            ps.setString(2, tier.name());
            ps.setInt(3, x);
            ps.setInt(4, z);
            ps.setLong(5, now);
            ps.setLong(6, expiresAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("보물지도 id를 받지 못했습니다");
                }
                return keys.getLong(1);
            }
        }
    }

    /** True only for the one caller that marks this unexpired, undug map as dug. */
    public boolean claim(long id, UUID digger, String diggerName, long now) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_treasure_maps SET dug_by = ?, dug_by_name = ?, dug_at = ? "
                             + "WHERE id = ? AND dug_by IS NULL AND expires_at > ?")) {
            ps.setString(1, digger.toString());
            ps.setString(2, diggerName);
            ps.setLong(3, now);
            ps.setLong(4, id);
            ps.setLong(5, now);
            return ps.executeUpdate() == 1;
        }
    }

    public List<LegendaryDig> legendaryDugSince(long since) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, dug_by_name, dug_at FROM yw_treasure_maps "
                             + "WHERE tier = 'LEGENDARY' AND dug_at > ? ORDER BY dug_at")) {
            ps.setLong(1, since);
            List<LegendaryDig> digs = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    digs.add(new LegendaryDig(rs.getLong(1), rs.getString(2), rs.getLong(3)));
                }
            }
            return digs;
        }
    }

    public List<ItemStack> loadRewards(TreasureTier tier) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT item_data FROM yw_treasure_rewards WHERE tier = ? ORDER BY slot")) {
            ps.setString(1, tier.name());
            List<ItemStack> items = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(ItemStackSerializer.deserialize(rs.getString(1)));
                }
            }
            return items;
        }
    }

    /** Replaces the tier's whole pool in one transaction. */
    public void saveRewards(TreasureTier tier, List<ItemStack> items) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM yw_treasure_rewards WHERE tier = ?")) {
                    ps.setString(1, tier.name());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO yw_treasure_rewards (tier, slot, item_data) VALUES (?, ?, ?)")) {
                    for (int slot = 0; slot < items.size(); slot++) {
                        ps.setString(1, tier.name());
                        ps.setInt(2, slot);
                        ps.setString(3, ItemStackSerializer.serialize(items.get(slot)));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // the pool resets it on return anyway
                }
            }
        }
    }
}
```

- [ ] **Step 2: Create `TreasureMapItem.java`**

```java
package com.yeowool.life.treasure;

import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds and reads treasure map items. Everything the hint needs (id, tier,
 * target x/z, expiry) is stored in the item's PDC so the per-second action
 * bar never touches the database.
 */
public final class TreasureMapItem {

    public record MapData(long id, TreasureTier tier, int x, int z, long expiresAt) {
        public boolean expired(long now) {
            return now >= expiresAt;
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Map<TreasureTier, String> itemsAdderIds;
    private final NamespacedKey idKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey xKey;
    private final NamespacedKey zKey;
    private final NamespacedKey expiresKey;

    public TreasureMapItem(JavaPlugin plugin, Map<TreasureTier, String> itemsAdderIds) {
        this.itemsAdderIds = itemsAdderIds;
        this.idKey = new NamespacedKey(plugin, "treasure_map_id");
        this.tierKey = new NamespacedKey(plugin, "treasure_map_tier");
        this.xKey = new NamespacedKey(plugin, "treasure_map_x");
        this.zKey = new NamespacedKey(plugin, "treasure_map_z");
        this.expiresKey = new NamespacedKey(plugin, "treasure_map_expires");
    }

    public ItemStack create(MapData data) {
        ItemStack stack = baseItem(data.tier());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(data.tier().label() + " 보물지도", data.tier().color()));
        meta.lore(List.of(
                line("야생 X " + TreasureRules.band(data.x()) + ", Z " + TreasureRules.band(data.z()) + " 부근", NamedTextColor.GRAY),
                line("야생 서버에서 손에 들면 방향과 거리를 알려줍니다", NamedTextColor.GRAY),
                line("보물 위치에서 웅크리고 땅을 우클릭하면 발굴", NamedTextColor.YELLOW),
                line("만료: " + DATE.format(Instant.ofEpochMilli(data.expiresAt())), NamedTextColor.DARK_GRAY),
                line("지도 #" + data.id(), NamedTextColor.DARK_GRAY)));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(idKey, PersistentDataType.LONG, data.id());
        pdc.set(tierKey, PersistentDataType.STRING, data.tier().name());
        pdc.set(xKey, PersistentDataType.INTEGER, data.x());
        pdc.set(zKey, PersistentDataType.INTEGER, data.z());
        pdc.set(expiresKey, PersistentDataType.LONG, data.expiresAt());
        stack.setItemMeta(meta);
        return stack;
    }

    public Optional<MapData> read(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        Long id = pdc.get(idKey, PersistentDataType.LONG);
        String tierName = pdc.get(tierKey, PersistentDataType.STRING);
        Integer x = pdc.get(xKey, PersistentDataType.INTEGER);
        Integer z = pdc.get(zKey, PersistentDataType.INTEGER);
        Long expiresAt = pdc.get(expiresKey, PersistentDataType.LONG);
        if (id == null || tierName == null || x == null || z == null || expiresAt == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new MapData(id, TreasureTier.valueOf(tierName), x, z, expiresAt));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The configured ItemsAdder item for this tier when available, otherwise plain paper. */
    private ItemStack baseItem(TreasureTier tier) {
        String id = itemsAdderIds.getOrDefault(tier, "");
        if (id != null && !id.isBlank() && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(id);
            if (custom != null) {
                return custom.getItemStack();
            }
        }
        return new ItemStack(Material.PAPER);
    }

    private static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :yeowool-life:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureRepository.java yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureMapItem.java
git commit -m "Add treasure map repository and map item

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: TreasureService, listener, command, wiring

**Files:**
- Create: `yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureService.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureListener.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureCommand.java`
- Modify: `yeowool-life/src/main/java/com/yeowool/life/fishing/customfishing/CustomFishingCatchListener.java` (fire `PlayerRepeatableActionEvent`)
- Modify: `yeowool-life/src/main/java/com/yeowool/life/YeowoolLife.java`
- Modify: `yeowool-life/src/main/resources/config.yml` (append), `messages.yml` (append), `plugin.yml` (command + permission)

**Interfaces:**
- Consumes: Tasks 1–2. Core: `YeowoolCoreAPI.economyData().modifyBalance(UUID, long, String, String) -> boolean`, `mailbox().deliverOrStore(UUID, ItemStack, String, String)` (main thread), `MessageService.send/resolveRaw/broadcast`, `com.yeowool.core.api.gui.ItemGridEditorGui(String title, List<ItemStack> existing, Consumer<List<ItemStack>> onSave)` (onSave runs on main thread when closed), `com.yeowool.core.api.event.PlayerRepeatableActionEvent` (`getUuid()`, `getActionType()`).
- Produces: `TreasureService.onAction(Player, String)`, `grant(Player, TreasureTier, boolean natural)`, `dig(Player, TreasureMapItem.MapData)`, `showHints()` (main), `announceLegendaryDigs()` (executor), `repository()`, `mapItem()`.

- [ ] **Step 1: Create `TreasureService.java`**

```java
package com.yeowool.life.treasure;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Treasure map flows: rolling a map drop, the per-second direction hint,
 * digging, and the cross-server legendary announcement. Maps are real items
 * (tradeable); the DB row decides whether a map can still be dug.
 */
public final class TreasureService {

    public record TierReward(long moneyMin, long moneyMax, int itemRolls) {
    }

    public record Settings(String digWorld, int centerX, int centerZ, int minRadius, int maxRadius, long expireMillis,
                           int dailyLimit, double digRadius, Map<String, Double> dropChances,
                           Map<TreasureTier, Integer> tierWeights, Map<TreasureTier, TierReward> rewards) {
    }

    private static final String SOURCE = "YeowoolLife";
    private static final double NEAR_DISTANCE = 20;

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final TreasureRepository repository;
    private final TreasureMapItem mapItem;
    private final Executor executor;
    private final Settings settings;
    private final Random random = new Random();
    private final Set<Long> digging = ConcurrentHashMap.newKeySet();
    private volatile long announcedUntil = System.currentTimeMillis();

    public TreasureService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, TreasureRepository repository,
                           TreasureMapItem mapItem, Executor executor, Settings settings) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.mapItem = mapItem;
        this.executor = executor;
        this.settings = settings;
    }

    public TreasureRepository repository() {
        return repository;
    }

    public TreasureMapItem mapItem() {
        return mapItem;
    }

    // ---- getting a map ----

    /** Main thread: a mining/fishing/hunting action just finished. */
    public void onAction(Player player, String activity) {
        double chance = settings.dropChances().getOrDefault(activity, 0.0);
        if (chance <= 0 || random.nextDouble() >= chance) {
            return;
        }
        grant(player, TreasureRules.pickTier(random, settings.tierWeights()), true);
    }

    /**
     * Main thread. Creates the map row and hands the item over (inventory, or
     * mailbox if full). Natural drops respect the daily limit; admin grants don't.
     */
    public void grant(Player player, TreasureTier tier, boolean natural) {
        UUID uuid = player.getUniqueId();
        int[] spot = TreasureRules.randomSpot(random, settings.centerX(), settings.centerZ(), settings.minRadius(), settings.maxRadius());
        long now = System.currentTimeMillis();
        long expiresAt = now + settings.expireMillis();
        long startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        executor.execute(() -> {
            long id;
            try {
                // ponytail: count and insert aren't atomic — two simultaneous drops can exceed the daily limit by one; drops are rare.
                if (natural && repository.countFoundSince(uuid, startOfDay) >= settings.dailyLimit()) {
                    return;
                }
                id = repository.insertMap(uuid, tier, spot[0], spot[1], now, expiresAt);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "보물지도 생성 실패 (" + uuid + ")", e);
                return;
            }
            TreasureMapItem.MapData data = new TreasureMapItem.MapData(id, tier, spot[0], spot[1], expiresAt);
            runOnMain(() -> {
                core.mailbox().deliverOrStore(uuid, mapItem.create(data), SOURCE, "보물지도");
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    messages.send(online, "treasure.found", Placeholder.unparsed("tier", tier.label()));
                    online.playSound(online.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
                }
            });
        });
    }

    // ---- hint (main thread, every second) ----

    public void showHints() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<TreasureMapItem.MapData> data = mapItem.read(player.getInventory().getItemInMainHand());
            if (data.isPresent()) {
                player.sendActionBar(hint(player, data.get(), now));
            }
        }
    }

    private Component hint(Player player, TreasureMapItem.MapData data, long now) {
        if (data.expired(now)) {
            return messages.resolveRaw("treasure.hint-expired");
        }
        if (!player.getWorld().getName().equals(settings.digWorld())) {
            return messages.resolveRaw("treasure.hint-other-world");
        }
        Location loc = player.getLocation();
        double dx = data.x() + 0.5 - loc.getX();
        double dz = data.z() + 0.5 - loc.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance <= settings.digRadius()) {
            return messages.resolveRaw("treasure.hint-here");
        }
        String rough = String.format("%,d", TreasureRules.roughDistance(distance));
        if (distance <= NEAR_DISTANCE) {
            return messages.resolveRaw("treasure.hint-near", Placeholder.unparsed("distance", rough));
        }
        return messages.resolveRaw("treasure.hint",
                Placeholder.unparsed("direction", TreasureRules.direction(dx, dz)),
                Placeholder.unparsed("distance", rough));
    }

    // ---- digging ----

    /** Main thread: the player sneak-right-clicked a block while holding {@code data}'s map. */
    public void dig(Player player, TreasureMapItem.MapData data) {
        if (!player.getWorld().getName().equals(settings.digWorld())) {
            messages.send(player, "treasure.wrong-world");
            return;
        }
        long now = System.currentTimeMillis();
        if (data.expired(now)) {
            messages.send(player, "treasure.expired");
            return;
        }
        Location loc = player.getLocation();
        if (Math.hypot(data.x() + 0.5 - loc.getX(), data.z() + 0.5 - loc.getZ()) > settings.digRadius()) {
            messages.send(player, "treasure.nothing-here");
            return;
        }
        if (!digging.add(data.id())) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        executor.execute(() -> {
            boolean claimed;
            try {
                claimed = repository.claim(data.id(), uuid, name, now);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "보물 발굴 처리 실패 (지도 #" + data.id() + ")", e);
                digging.remove(data.id());
                runOnMain(() -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        messages.send(online, "treasure.error");
                    }
                });
                return;
            }
            List<ItemStack> pool = List.of();
            if (claimed) {
                try {
                    pool = repository.loadRewards(data.tier());
                } catch (SQLException | RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "보물 보상 후보 불러오기 실패 (" + data.tier() + ") — 온만 지급", e);
                }
            }
            List<ItemStack> rewardPool = pool;
            runOnMain(() -> {
                digging.remove(data.id());
                finishDig(uuid, data, claimed, rewardPool);
            });
        });
    }

    private void finishDig(UUID uuid, TreasureMapItem.MapData data, boolean claimed, List<ItemStack> pool) {
        Player player = Bukkit.getPlayer(uuid);
        if (!claimed) {
            if (player != null) {
                messages.send(player, "treasure.already-dug");
            }
            return;
        }
        TierReward reward = settings.rewards().get(data.tier());
        long money = TreasureRules.randomMoney(random, reward.moneyMin(), reward.moneyMax());
        List<ItemStack> items = TreasureRules.pickItems(pool, reward.itemRolls(), random);
        for (ItemStack item : items) {
            core.mailbox().deliverOrStore(uuid, item.clone(), SOURCE, "보물 발굴 (" + data.tier().label() + ")");
        }
        if (player == null) {
            // ponytail: sub-tick window between the claim and here — money can't be credited offline, so leave a trail for staff.
            plugin.getLogger().warning("보물 발굴 직후 접속 종료로 온 보상 미지급 — 수동 지급 필요: " + uuid + " " + money + "온 (지도 #" + data.id() + ")");
            return;
        }
        removeMap(player.getInventory(), data.id());
        if (money > 0 && !core.economyData().modifyBalance(uuid, money, SOURCE, "보물 발굴 (" + data.tier().label() + ")")) {
            plugin.getLogger().warning("보물 온 보상 지급 실패 — 수동 지급 필요: " + uuid + " " + money + "온 (지도 #" + data.id() + ")");
        }
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 0.5, 0), 40, 0.8, 0.5, 0.8);
        player.playSound(loc, data.tier() == TreasureTier.LEGENDARY ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        messages.send(player, "treasure.dug",
                Placeholder.unparsed("tier", data.tier().label()),
                Placeholder.unparsed("money", String.format("%,d", money)),
                Placeholder.unparsed("items", String.valueOf(items.size())));
    }

    private void removeMap(PlayerInventory inventory, long mapId) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (mapItem.read(stack).map(d -> d.id() == mapId).orElse(false)) {
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    inventory.setItem(slot, null);
                }
                return;
            }
        }
    }

    // ---- legendary announcement (executor, every minute on every server) ----

    public void announceLegendaryDigs() {
        try {
            List<TreasureRepository.LegendaryDig> digs = repository.legendaryDugSince(announcedUntil);
            if (digs.isEmpty()) {
                return;
            }
            announcedUntil = digs.get(digs.size() - 1).dugAt();
            runOnMain(() -> {
                for (TreasureRepository.LegendaryDig dig : digs) {
                    messages.broadcast("treasure.legendary-broadcast",
                            Placeholder.unparsed("player", dig.diggerName() == null ? "누군가" : dig.diggerName()));
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "전설 보물 공지 조회 실패", e);
        }
    }

    private void runOnMain(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
```

- [ ] **Step 2: Create `TreasureListener.java`**

```java
package com.yeowool.life.treasure;

import com.yeowool.core.api.event.PlayerRepeatableActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Map drops ride on {@link PlayerRepeatableActionEvent}, which mining,
 * fishing (both fishing paths) and hunting already fire once per completed
 * action. Digging is a sneak + right-click on any block; cancelled events
 * are still handled because digging never changes a block, so land
 * protection that cancels interactions doesn't apply.
 */
public final class TreasureListener implements Listener {

    private final TreasureService service;

    public TreasureListener(TreasureService service) {
        this.service = service;
    }

    @EventHandler
    public void onAction(PlayerRepeatableActionEvent event) {
        Player player = Bukkit.getPlayer(event.getUuid());
        if (player != null) {
            service.onAction(player, event.getActionType());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || !event.getPlayer().isSneaking()) {
            return;
        }
        service.mapItem().read(event.getItem()).ifPresent(data -> {
            event.setCancelled(true);
            service.dig(event.getPlayer(), data);
        });
    }
}
```

- [ ] **Step 3: Create `TreasureCommand.java`**

```java
package com.yeowool.life.treasure;

import com.yeowool.core.api.gui.ItemGridEditorGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/** {@code /보물지도}: held map info for everyone; reward pool editing and grants for staff. */
public final class TreasureCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "yeowool.life.treasure.manage";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final List<String> TIER_LABELS = Arrays.stream(TreasureTier.values()).map(TreasureTier::label).toList();

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final TreasureService service;
    private final Executor executor;

    public TreasureCommand(JavaPlugin plugin, MessageService messages, TreasureService service, Executor executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            info(sender);
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        switch (args[0]) {
            case "보상설정" -> editRewards(sender, args);
            case "지급" -> grant(sender, args);
            default -> messages.send(sender, "treasure.admin-usage");
        }
        return true;
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "treasure.usage");
            return;
        }
        Optional<TreasureMapItem.MapData> held = service.mapItem().read(player.getInventory().getItemInMainHand());
        if (held.isEmpty()) {
            messages.send(player, "treasure.usage");
            if (player.hasPermission(ADMIN)) {
                messages.send(player, "treasure.admin-usage");
            }
            return;
        }
        TreasureMapItem.MapData data = held.get();
        messages.send(player, "treasure.info",
                Placeholder.unparsed("tier", data.tier().label()),
                Placeholder.unparsed("id", String.valueOf(data.id())),
                Placeholder.unparsed("xband", TreasureRules.band(data.x())),
                Placeholder.unparsed("zband", TreasureRules.band(data.z())),
                Placeholder.unparsed("expires", DATE.format(Instant.ofEpochMilli(data.expiresAt()))));
    }

    private void editRewards(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        Optional<TreasureTier> tier = args.length < 2 ? Optional.empty() : TreasureTier.parse(args[1]);
        if (tier.isEmpty()) {
            messages.send(sender, "treasure.unknown-tier");
            return;
        }
        executor.execute(() -> {
            List<ItemStack> existing;
            try {
                existing = service.repository().loadRewards(tier.get());
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "보물 보상 후보 불러오기 실패", e);
                reply(sender, "treasure.error");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                new ItemGridEditorGui("보물 보상 - " + tier.get().label(), existing, items -> save(player, tier.get(), items)).open(player);
            });
        });
    }

    private void save(Player player, TreasureTier tier, List<ItemStack> items) {
        List<ItemStack> copies = items.stream().map(ItemStack::clone).toList();
        executor.execute(() -> {
            try {
                service.repository().saveRewards(tier, copies);
                reply(player, "treasure.rewards-saved",
                        Placeholder.unparsed("tier", tier.label()),
                        Placeholder.unparsed("count", String.valueOf(copies.size())));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "보물 보상 후보 저장 실패 (" + tier + ")", e);
                reply(player, "treasure.error");
            }
        });
    }

    private void grant(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "treasure.admin-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "treasure.target-offline");
            return;
        }
        Optional<TreasureTier> tier = TreasureTier.parse(args[2]);
        if (tier.isEmpty()) {
            messages.send(sender, "treasure.unknown-tier");
            return;
        }
        service.grant(target, tier.get(), false);
        messages.send(sender, "treasure.granted",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("tier", tier.get().label()));
    }

    private void reply(CommandSender sender, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key, placeholders));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("보상설정", "지급").stream().filter(s -> s.startsWith(args[0])).toList();
        }
        if (args.length == 2 && args[0].equals("보상설정")) {
            return TIER_LABELS.stream().filter(s -> s.startsWith(args[1])).toList();
        }
        if (args.length == 2 && args[0].equals("지급")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.startsWith(args[1])).toList();
        }
        if (args.length == 3 && args[0].equals("지급")) {
            return TIER_LABELS.stream().filter(s -> s.startsWith(args[2])).toList();
        }
        return List.of();
    }
}
```

- [ ] **Step 4: Fire the repeatable-action event from CustomFishing catches**

In `yeowool-life/src/main/java/com/yeowool/life/fishing/customfishing/CustomFishingCatchListener.java`, add imports `com.yeowool.core.api.event.PlayerRepeatableActionEvent` and `org.bukkit.Bukkit` (if missing), and append as the last statement of `onLootSpawn` (after the size/competition block):

```java
        Bukkit.getPluginManager().callEvent(new PlayerRepeatableActionEvent(player.getUniqueId(), "fishing"));
```

(Our own `FishingListener` already fires it; this makes CustomFishing catches — the path live servers use — count for treasure drops and macro-timing detection too.)

- [ ] **Step 5: Wire into `YeowoolLife.java`**

Add imports:

```java
import com.yeowool.life.treasure.TreasureCommand;
import com.yeowool.life.treasure.TreasureListener;
import com.yeowool.life.treasure.TreasureMapItem;
import com.yeowool.life.treasure.TreasureRepository;
import com.yeowool.life.treasure.TreasureService;
import com.yeowool.life.treasure.TreasureTier;
import org.bukkit.configuration.ConfigurationSection;
import java.util.EnumMap;
import java.util.HashMap;
```
(skip any already imported; `java.util.Map` is already imported.)

In `onEnable`, right after `enableScrapyard(core, messages);` add:

```java
        enableTreasureMaps(core, messages);
```

Add this private method below `enableScrapyard`:

```java
    /** 보물지도 — 채광·낚시·사냥 중 지도 드롭, 야생 월드에서 발굴 (세 서버 공통, 발굴은 dig-world가 있는 서버에서만). */
    private void enableTreasureMaps(YeowoolCoreAPI core, MessageManager messages) {
        var config = getConfig();
        if (!config.getBoolean("treasure.enabled", true)) {
            return;
        }
        TreasureRepository repository = new TreasureRepository(core.dataSource());
        try {
            repository.createTables();
        } catch (Exception e) {
            getLogger().severe("보물지도 데이터베이스 초기화 실패 — 보물지도를 끕니다: " + e.getMessage());
            return;
        }
        Map<String, Double> dropChances = new HashMap<>();
        ConfigurationSection dropSection = config.getConfigurationSection("treasure.drop-chance");
        if (dropSection != null) {
            for (String key : dropSection.getKeys(false)) {
                dropChances.put(key, dropSection.getDouble(key));
            }
        }
        Map<TreasureTier, Integer> tierWeights = new EnumMap<>(TreasureTier.class);
        Map<TreasureTier, TreasureService.TierReward> rewards = new EnumMap<>(TreasureTier.class);
        Map<TreasureTier, String> itemIds = new EnumMap<>(TreasureTier.class);
        for (TreasureTier tier : TreasureTier.values()) {
            String key = tier.configKey();
            tierWeights.put(tier, config.getInt("treasure.tier-weights." + key, 0));
            rewards.put(tier, new TreasureService.TierReward(
                    config.getLong("treasure.tiers." + key + ".money-min", 0),
                    config.getLong("treasure.tiers." + key + ".money-max", 0),
                    config.getInt("treasure.tiers." + key + ".item-rolls", 0)));
            itemIds.put(tier, config.getString("treasure.tiers." + key + ".item-id", ""));
        }
        TreasureService.Settings settings = new TreasureService.Settings(
                config.getString("treasure.dig-world", "wild_world"),
                config.getInt("treasure.center-x", 0),
                config.getInt("treasure.center-z", 0),
                config.getInt("treasure.min-radius", 500),
                config.getInt("treasure.max-radius", 3000),
                config.getLong("treasure.expire-days", 7) * 86_400_000L,
                config.getInt("treasure.daily-limit", 3),
                config.getDouble("treasure.dig-radius", 4),
                dropChances, tierWeights, rewards);
        TreasureService service = new TreasureService(this, core, messages, repository,
                new TreasureMapItem(this, itemIds), executor, settings);
        getServer().getPluginManager().registerEvents(new TreasureListener(service), this);
        var treasureCommand = getCommand("보물지도");
        if (treasureCommand != null) {
            var executorCmd = new TreasureCommand(this, messages, service, executor);
            treasureCommand.setExecutor(executorCmd);
            treasureCommand.setTabCompleter(executorCmd);
        }
        getServer().getScheduler().runTaskTimer(this, service::showHints, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::announceLegendaryDigs), 20L * 60, 20L * 60);
    }
```

- [ ] **Step 6: Append to `config.yml`**

```yaml

# 보물지도 — 채광·낚시·사냥 중 가끔 지도가 나오고, 야생 월드에서 지도를 따라가 웅크리고 땅을 우클릭하면 발굴.
# 보물은 블록이 아닌 가상 보물이라 지형·토지를 건드리지 않으며, 야생을 초기화해도 좌표는 그대로 유효합니다.
# 세 서버 모두 같은 값으로 맞춰주세요.
treasure:
  enabled: true
  # 보물이 묻히는 월드 (야생 서버의 월드 이름)
  dig-world: wild_world
  # 보물 좌표를 뽑는 중심과 반경(블록)
  center-x: 0
  center-z: 0
  min-radius: 500
  max-radius: 3000
  # 지도 유효 기간(일)
  expire-days: 7
  # 한 사람이 하루(서버 시간 자정 기준)에 자연 획득할 수 있는 지도 수
  daily-limit: 3
  # 목표 지점에서 이 거리(블록) 안이면 발굴 가능
  dig-radius: 4
  # 행동 1회당 지도가 나올 확률 (0.01 = 1%)
  drop-chance:
    mining: 0.002
    fishing: 0.01
    hunting: 0.005
  # 등급 가중치
  tier-weights:
    common: 80
    rare: 17
    legendary: 3
  # 등급별 보상: 온(범위 내 무작위) + 아이템 후보 중 item-rolls개 (/보물지도 보상설정으로 후보 편집)
  # item-id: 지도 아이템으로 쓸 ItemsAdder 아이템 ID (비우면 종이)
  tiers:
    common:
      money-min: 1000
      money-max: 5000
      item-rolls: 1
      item-id: ""
    rare:
      money-min: 10000
      money-max: 30000
      item-rolls: 2
      item-id: ""
    legendary:
      money-min: 50000
      money-max: 150000
      item-rolls: 3
      item-id: ""
```

- [ ] **Step 7: Append to `messages.yml`**

```yaml

treasure:
  found: "<gold>보물지도를 발견했습니다!</gold> <white><tier> 보물지도</white><gray>를 얻었습니다. 야생 서버에서 손에 들고 따라가 보세요. (인벤토리가 가득 차면 우편함)</gray>"
  hint: "<yellow>보물: <direction> 약 <distance>블록</yellow>"
  hint-near: "<gold>보물이 가까이 있습니다! 약 <distance>블록</gold>"
  hint-here: "<green>바로 여기! 웅크리고 땅을 우클릭해 파보세요</green>"
  hint-other-world: "<gray>야생 서버(지상)에서 찾을 수 있는 보물지도입니다</gray>"
  hint-expired: "<dark_gray>낡아서 더 이상 읽을 수 없는 지도입니다</dark_gray>"
  wrong-world: "<red>이 보물은 야생 서버 지상에 묻혀 있습니다.</red>"
  expired: "<red>기한이 지난 낡은 지도입니다.</red>"
  nothing-here: "<red>여기에는 아무것도 없는 것 같습니다. 지도가 가리키는 곳으로 더 가보세요.</red>"
  already-dug: "<red>이미 누군가 파낸 보물입니다.</red>"
  dug: "<gold><tier> 보물을 발굴했습니다!</gold> <yellow><money>온</yellow><gray>과 아이템 <items>개를 얻었습니다. (인벤토리가 가득 차면 우편함)</gray>"
  legendary-broadcast: "<gold>[보물]</gold> <yellow><player>님이 전설 보물을 발굴했습니다!</yellow>"
  info: "<yellow><tier> 보물지도 #<id></yellow><gray> — 야생 X <xband>, Z <zband> 부근, 만료 <expires></gray>"
  usage: "<gray>보물지도는 채광·낚시·사냥 중 가끔 발견됩니다. 야생 서버에서 손에 들고 안내를 따라가, 웅크리고 땅을 우클릭하면 발굴합니다.</gray>"
  admin-usage: "<gray>/보물지도 보상설정 [일반|희귀|전설], /보물지도 지급 [닉네임] [일반|희귀|전설]</gray>"
  unknown-tier: "<red>등급은 일반, 희귀, 전설 중 하나입니다.</red>"
  target-offline: "<red>이 서버에 접속 중인 플레이어가 아닙니다.</red>"
  granted: "<green><player>님에게 <tier> 보물지도를 지급했습니다.</green>"
  rewards-saved: "<green><tier> 보물 보상 후보 <count>개를 저장했습니다.</green>"
  error: "<red>처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.</red>"
```

- [ ] **Step 8: `plugin.yml`** — add under `commands:` (after the last existing command):

```yaml
  보물지도:
    description: 들고 있는 보물지도 정보 (관리진은 보상설정/지급)
```

and add at the end of the file (the file has no `permissions:` section yet):

```yaml

permissions:
  yeowool.life.treasure.manage:
    description: 보물지도 보상 후보 편집 및 지급
    default: op
```

- [ ] **Step 9: Build**

Run: `./gradlew :yeowool-life:build`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 10: Commit**

```bash
git add yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureService.java yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureListener.java yeowool-life/src/main/java/com/yeowool/life/treasure/TreasureCommand.java yeowool-life/src/main/java/com/yeowool/life/fishing/customfishing/CustomFishingCatchListener.java yeowool-life/src/main/java/com/yeowool/life/YeowoolLife.java yeowool-life/src/main/resources/config.yml yeowool-life/src/main/resources/messages.yml yeowool-life/src/main/resources/plugin.yml
git commit -m "Add treasure map drops, hints, digging and admin command

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

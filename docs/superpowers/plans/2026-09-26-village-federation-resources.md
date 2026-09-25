# 마을 연합 3단계 — 은행 · 활동량 · 레벨업 · 연합 상점 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give federations a shared bank, an activity score fed by members' land XP, leader-triggered level-ups that raise the member-land cap, and level-gated federation-only shops built on the existing `/상점` system.

**Architecture:** Bank/activity live as two new columns on `yw_federations`, changed only by single conditional atomic SQL statements so 3 servers can't race. Activity is buffered in memory per server from core's `PlayerLandXpChangeEvent` and flushed once a minute. Shop gating uses a new cancellable core event `ShopOpenEvent` that yeowool-market fires at every shop entry point and yeowool-federation vetoes using an in-memory player→federation-level cache — market never learns about federations.

**Tech Stack:** Paper 1.21.4 API, raw JDBC via `YeowoolCoreAPI.dataSource()` (MySQL), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-26-village-federation-resources-design.md`

## Global Constraints

- Bank deposit: any owner/resident of a land in the federation. Bank withdraw and `/연합 업그레이드`: leader only (player's OWNED land has role LEADER — same as existing leader commands).
- Player→federation resolution: federation of a land the player OWNS; otherwise the first (by `yw_federations.name`) federation among lands the player is a RESIDENT of (`yw_land_members`).
- Level N→N+1: cumulative `activity >= activityPerLevel × N` AND bank `>= costPerLevel × N`; the cost is DEDUCTED, activity is not. No level cap.
- Member-land cap = `memberBase + memberPerLevel × (N − 1)`, leader's land included; enforced when ACCEPTING an application (applying stays free).
- Defaults (federation `config.yml`): `level-up.activity-per-level: 20000`, `level-up.cost-per-level: 30000`, `member-cap.base: 3`, `member-cap.per-level: 1`, `shops: []`.
- Deputy cap `floor(level/10)` unchanged.
- Activity = positive `PlayerLandXpChangeEvent` deltas of the federation's owners+residents, buffered in memory, flushed every 60 s (1200 ticks) and once more in `onDisable`.
- Every bank/activity/level change is ONE conditional atomic `UPDATE` (see Task 3) — never read-modify-write.
- `ShopOpenEvent(Player player, String shopId)` in `com.yeowool.core.api.event`, `Cancellable`, fired on the main thread by yeowool-market at the 3 shop ENTRY points only (`/상점 [ID]`, main-menu click, Citizens NPC right-click) — not page flips or returning from amount selection.
- Shop gate reads a per-player federation-level cache (0 = no federation, absent = not loaded yet → deny with "try again"); refreshed on join, every 60 s for online players, and whenever `/연합 상점` opens. Up to 1 minute staleness is accepted.
- `/연합 상점` opens a list GUI; an unlocked shop click runs `player.performCommand("상점 " + shopId)`.
- All JDBC runs on the federation executor; player messages and wallet changes (`core.economyData()`) run on the main thread (`runOnMain`). DB errors: `Level.SEVERE` log with the exception.
- Wallet changes use `core.economyData().modifyBalance(uuid, delta, "YeowoolFederation", reason)` with reasons `"연합 은행 입금"`, `"연합 은행 출금"`, `"연합 은행 입금 실패 환불"`.
- Only zero-Bukkit/zero-JDBC classes get JUnit tests. Money shown with `String.format("%,d", amount)` + `온`.
- Git: stage only named files (never `git add -A`/`git add .`), never amend, end commit messages with a blank line + `Co-Authored-By: <your model> <noreply@anthropic.com>`.
- Never start/stop servers or send RCON commands. Only Task 8 deploys.
- OneDrive lock: if gradle fails with `Unable to delete directory '...\build\test-results\test\binary'`, `rm -rf` that folder and re-run.

---

### Task 1: `ShopOpenEvent` (core) + fire it at market's shop entry points

**Files:**
- Create: `yeowool-core/src/main/java/com/yeowool/core/api/event/ShopOpenEvent.java`
- Create: `yeowool-market/src/main/java/com/yeowool/market/npcshop/ShopOpenGate.java`
- Modify: `yeowool-market/src/main/java/com/yeowool/market/command/NPCShopCommand.java` (2 `new NPCShopGui(...)` lines, ~61 and ~73)
- Modify: `yeowool-market/src/main/java/com/yeowool/market/npcshop/ShopMainMenuGui.java` (~line 60)
- Modify: `yeowool-market/src/main/java/com/yeowool/market/citizens/CitizensShopListener.java` (~line 53)

**Interfaces:**
- Produces: `com.yeowool.core.api.event.ShopOpenEvent` with `getPlayer()`, `getShopId()`, `isCancelled()`, `setCancelled(boolean)`; `com.yeowool.market.npcshop.ShopOpenGate.allows(Player, String) -> boolean`.

- [ ] **Step 1: Write `ShopOpenEvent.java`**

```java
package com.yeowool.core.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread by YeowoolMarket right before a player enters a
 * shop, so other plugins (e.g. YeowoolFederation's level-gated shops) can
 * veto it without YeowoolMarket knowing why.
 */
public final class ShopOpenEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String shopId;
    private boolean cancelled;

    public ShopOpenEvent(Player player, String shopId) {
        this.player = player;
        this.shopId = shopId;
    }

    public Player getPlayer() {
        return player;
    }

    public String getShopId() {
        return shopId;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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

- [ ] **Step 2: Write `ShopOpenGate.java`**

```java
package com.yeowool.market.npcshop;

import com.yeowool.core.api.event.ShopOpenEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Every shop ENTRY point asks this first; another plugin cancelling {@link ShopOpenEvent} keeps the shop closed. */
public final class ShopOpenGate {

    private ShopOpenGate() {
    }

    public static boolean allows(Player player, String shopId) {
        ShopOpenEvent event = new ShopOpenEvent(player, shopId);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }
}
```

- [ ] **Step 3: Gate the entry points.** Wrap each of these existing lines so the GUI only opens when allowed (keep everything else in those methods unchanged):

In `NPCShopCommand.onCommand`, the default-shop line:
```java
                new NPCShopGui(plugin, core, messages, shops, defaultShop, rotationManager, 0).open(player);
```
becomes
```java
                if (ShopOpenGate.allows(player, defaultShop.id())) {
                    new NPCShopGui(plugin, core, messages, shops, defaultShop, rotationManager, 0).open(player);
                }
```
and the by-id line:
```java
        new NPCShopGui(plugin, core, messages, shops, shop, rotationManager, 0).open(player);
```
becomes
```java
        if (ShopOpenGate.allows(player, shop.id())) {
            new NPCShopGui(plugin, core, messages, shops, shop, rotationManager, 0).open(player);
        }
```
Add `import com.yeowool.market.npcshop.ShopOpenGate;` to `NPCShopCommand`.

In `ShopMainMenuGui`, inside the click handler's `else` branch:
```java
                    new NPCShopGui(plugin, core, messages, allShops, shop, rotationManager, 0).open(player);
```
becomes
```java
                    if (ShopOpenGate.allows(player, shop.id())) {
                        new NPCShopGui(plugin, core, messages, allShops, shop, rotationManager, 0).open(player);
                    }
```
(same package — no import needed).

In `CitizensShopListener.onRightClick`, the last line:
```java
        new NPCShopGui(plugin, core, messages, shops, shop, rotationManager, 0).open(player);
```
becomes
```java
        if (ShopOpenGate.allows(player, shop.id())) {
            new NPCShopGui(plugin, core, messages, shops, shop, rotationManager, 0).open(player);
        }
```
Add `import com.yeowool.market.npcshop.ShopOpenGate;`.

Do NOT touch `NPCShopGui` (page flips) or `AmountSelectionGui` (return to shop) — those are already inside a shop.

- [ ] **Step 4: Build**

Run: `./gradlew :yeowool-core:build :yeowool-market:build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add yeowool-core/src/main/java/com/yeowool/core/api/event/ShopOpenEvent.java yeowool-market/src/main/java/com/yeowool/market/npcshop/ShopOpenGate.java yeowool-market/src/main/java/com/yeowool/market/command/NPCShopCommand.java yeowool-market/src/main/java/com/yeowool/market/npcshop/ShopMainMenuGui.java yeowool-market/src/main/java/com/yeowool/market/citizens/CitizensShopListener.java
git commit -m "Add ShopOpenEvent and fire it at every shop entry point"
```

---

### Task 2: `FederationLevelConfig` — level/cap formulas (pure, TDD)

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/FederationLevelConfig.java`
- Test: `yeowool-federation/src/test/java/com/yeowool/federation/FederationLevelConfigTest.java`

**Interfaces:**
- Produces: `record FederationLevelConfig(long activityPerLevel, long costPerLevel, int memberBase, int memberPerLevel)` with `long activityForNextLevel(int level)`, `long costForNextLevel(int level)`, `int memberCap(int level)`.

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.federation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FederationLevelConfigTest {

    private final FederationLevelConfig config = new FederationLevelConfig(20000, 30000, 3, 1);

    @Test
    void nextLevelRequirementsScaleWithCurrentLevel() {
        assertEquals(20000, config.activityForNextLevel(1));
        assertEquals(30000, config.costForNextLevel(1));
        assertEquals(100000, config.activityForNextLevel(5));
        assertEquals(150000, config.costForNextLevel(5));
    }

    @Test
    void memberCapStartsAtBaseAndGrowsPerLevel() {
        assertEquals(3, config.memberCap(1));
        assertEquals(4, config.memberCap(2));
        assertEquals(7, config.memberCap(5));
    }

    @Test
    void veryHighLevelsDoNotOverflowIntoNegativeRequirements() {
        assertEquals(20000L * 1_000_000, config.activityForNextLevel(1_000_000));
        assertEquals(30000L * 1_000_000, config.costForNextLevel(1_000_000));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :yeowool-federation:test`
Expected: FAIL — compilation error, `FederationLevelConfig` does not exist.

- [ ] **Step 3: Implement**

```java
package com.yeowool.federation;

/** Level-up and member-cap formulas, read from config.yml's level-up / member-cap sections. Pure — unit-tested. */
public record FederationLevelConfig(long activityPerLevel, long costPerLevel, int memberBase, int memberPerLevel) {

    /** Cumulative activity needed to go from {@code level} to {@code level + 1}. */
    public long activityForNextLevel(int level) {
        return activityPerLevel * level;
    }

    /** Bank cost (deducted) to go from {@code level} to {@code level + 1}. */
    public long costForNextLevel(int level) {
        return costPerLevel * level;
    }

    /** How many lands (leader's included) a federation of this level may hold. */
    public int memberCap(int level) {
        return memberBase + memberPerLevel * (level - 1);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :yeowool-federation:test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/FederationLevelConfig.java yeowool-federation/src/test/java/com/yeowool/federation/FederationLevelConfigTest.java
git commit -m "Add federation level-up and member-cap formulas"
```

---

### Task 3: DB layer — bank/activity columns, atomic updates, player→federation resolver

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/FederationProgress.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/land/PlayerFederationResolver.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/database/FederationSchemaInitializer.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/database/FederationRepository.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/chat/FederationChatService.java`

**Interfaces:**
- Produces:
  - `record FederationProgress(long bankBalance, long activity)` (package `com.yeowool.federation`)
  - `PlayerFederationResolver(DataSource)` with `Optional<UUID> findFederationId(UUID playerUuid) throws SQLException`
  - `FederationRepository`: `Optional<FederationProgress> findProgress(UUID federationId)`, `boolean depositToBank(UUID federationId, long amount)`, `boolean withdrawFromBank(UUID federationId, long amount)`, `void addActivity(UUID federationId, long amount)`, `boolean tryLevelUp(UUID federationId, int currentLevel, long cost, long requiredActivity)`, `int countMembers(UUID federationId)` — all `throws SQLException`.
- `FederationChatService`'s public API is unchanged (its `findFederationId` now delegates to the resolver).

- [ ] **Step 1: `FederationProgress.java`**

```java
package com.yeowool.federation;

/** A federation's bank balance and cumulative activity (yw_federations.bank_balance / activity). */
public record FederationProgress(long bankBalance, long activity) {
}
```

- [ ] **Step 2: Add the columns.** In `FederationSchemaInitializer.initialize`, after the `for (String ddl : DDL)` loop (still inside the try-with-resources), add:

```java
            addColumnIfMissing(connection, "yw_federations", "bank_balance", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "yw_federations", "activity", "BIGINT NOT NULL DEFAULT 0");
```

and add this method to the class (same metadata approach as `PartySchemaInitializer` — `ADD COLUMN IF NOT EXISTS` needs MySQL 8.0.29+):

```java
    private static void addColumnIfMissing(Connection connection, String table, String column, String definition) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
```

Add `import java.sql.ResultSet;`.

- [ ] **Step 3: Repository methods.** Add to `FederationRepository` (it already has a `dataSource` field and imports `Connection`, `PreparedStatement`, `ResultSet`, `SQLException`, `Optional`, `UUID`):

```java
    public Optional<FederationProgress> findProgress(UUID federationId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT bank_balance, activity FROM yw_federations WHERE id = ?")) {
            select.setString(1, federationId.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new FederationProgress(rs.getLong("bank_balance"), rs.getLong("activity")));
            }
        }
    }

    /** @return false if the federation no longer exists. */
    public boolean depositToBank(UUID federationId, long amount) throws SQLException {
        return executeUpdate("UPDATE yw_federations SET bank_balance = bank_balance + ? WHERE id = ?",
                amount, federationId.toString()) == 1;
    }

    /** Atomic "subtract only if enough" — @return false if the balance was insufficient (or the federation is gone). */
    public boolean withdrawFromBank(UUID federationId, long amount) throws SQLException {
        return executeUpdate("UPDATE yw_federations SET bank_balance = bank_balance - ? WHERE id = ? AND bank_balance >= ?",
                amount, federationId.toString(), amount) == 1;
    }

    public void addActivity(UUID federationId, long amount) throws SQLException {
        executeUpdate("UPDATE yw_federations SET activity = activity + ? WHERE id = ?", amount, federationId.toString());
    }

    /**
     * Raises the level by one and deducts the cost in one statement, only if the federation is still at
     * {@code currentLevel} with enough bank and activity — so a double click or a second server can't level twice.
     */
    public boolean tryLevelUp(UUID federationId, int currentLevel, long cost, long requiredActivity) throws SQLException {
        return executeUpdate("UPDATE yw_federations SET level = level + 1, bank_balance = bank_balance - ? " +
                        "WHERE id = ? AND level = ? AND bank_balance >= ? AND activity >= ?",
                cost, federationId.toString(), currentLevel, cost, requiredActivity) == 1;
    }

    public int countMembers(UUID federationId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT COUNT(*) FROM yw_federation_members WHERE federation_id = ?")) {
            select.setString(1, federationId.toString());
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            return statement.executeUpdate();
        }
    }
```

Add `import com.yeowool.federation.FederationProgress;` if the repository is in a different package (it is: `com.yeowool.federation.database`).

- [ ] **Step 4: `PlayerFederationResolver.java`** (moves the two lookup queries out of `FederationChatService` so chat, bank, activity and shops share one definition)

```java
package com.yeowool.federation.land;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Which federation a PLAYER belongs to: the federation of a land they own; otherwise the first
 * (by federation name) federation among lands they are a resident of. Raw JDBC over yeowool-land's
 * tables, no compile dependency (same pattern as {@link LandLookup}). Blocking — call off the main thread.
 */
public final class PlayerFederationResolver {

    private static final String OWNED_LAND_FEDERATION =
            "SELECT m.federation_id FROM yw_federation_members m " +
                    "JOIN yw_lands l ON l.id = m.land_id WHERE l.owner_uuid = ?";
    private static final String RESIDENT_LAND_FEDERATION =
            "SELECT m.federation_id FROM yw_federation_members m " +
                    "JOIN yw_land_members lm ON lm.land_id = m.land_id " +
                    "JOIN yw_federations f ON f.id = m.federation_id " +
                    "WHERE lm.member_uuid = ? ORDER BY f.name LIMIT 1";

    private final DataSource dataSource;

    public PlayerFederationResolver(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<UUID> findFederationId(UUID playerUuid) throws SQLException {
        Optional<UUID> owned = query(OWNED_LAND_FEDERATION, playerUuid);
        return owned.isPresent() ? owned : query(RESIDENT_LAND_FEDERATION, playerUuid);
    }

    private Optional<UUID> query(String sql, UUID playerUuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, playerUuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        }
    }
}
```

- [ ] **Step 5: Make `FederationChatService` delegate.** In `FederationChatService`:
  - delete the constants `OWNED_LAND_FEDERATION` and `RESIDENT_LAND_FEDERATION` and the private method `queryFederationId(String, UUID)`;
  - add a field `private final PlayerFederationResolver resolver;` and in the constructor `this.resolver = new PlayerFederationResolver(dataSource);`;
  - replace the body of `findFederationId` with `return resolver.findFederationId(playerUuid);`;
  - add `import com.yeowool.federation.land.PlayerFederationResolver;`. Keep everything else (constructor signature, `send`, `RECIPIENTS`, `findRecipients`) unchanged.

- [ ] **Step 6: Build**

Run: `./gradlew :yeowool-federation:build`
Expected: `BUILD SUCCESSFUL`, existing tests pass.

- [ ] **Step 7: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/FederationProgress.java yeowool-federation/src/main/java/com/yeowool/federation/land/PlayerFederationResolver.java yeowool-federation/src/main/java/com/yeowool/federation/database/FederationSchemaInitializer.java yeowool-federation/src/main/java/com/yeowool/federation/database/FederationRepository.java yeowool-federation/src/main/java/com/yeowool/federation/chat/FederationChatService.java
git commit -m "Add federation bank/activity columns, atomic updates, shared player-federation resolver"
```

---

### Task 4: `FederationManager` — bank, upgrade, activity, member cap

**Files:**
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/FederationManager.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java`
- Modify: `yeowool-federation/src/main/resources/config.yml`

**Interfaces:**
- Consumes: `FederationLevelConfig` (Task 2); `FederationProgress`, repository methods from Task 3.
- Produces (all `throws SQLException` except `levelConfig()`):
  - constructor `FederationManager(FederationRepository repository, FederationLevelConfig levelConfig)`
  - `ApprovalResult` gains `MEMBER_CAP_REACHED` (returned by `approve` when the federation already holds `memberCap(level)` lands)
  - `FederationLevelConfig levelConfig()`
  - `Optional<Federation> findById(UUID federationId)`
  - `Optional<FederationProgress> progressOf(UUID federationId)`
  - `boolean deposit(UUID federationId, long amount)`
  - `enum WithdrawResult { SUCCESS, NOT_LEADER, INSUFFICIENT_BANK }` / `WithdrawResult withdraw(UUID actingLandId, long amount)`
  - `void addActivity(UUID federationId, long amount)`
  - `enum UpgradeResult { SUCCESS, NOT_LEADER, NOT_ENOUGH_ACTIVITY, NOT_ENOUGH_BANK, CHANGED }`, `record UpgradeOutcome(UpgradeResult result, int level, long activity, long requiredActivity, long bank, long cost)` / `UpgradeOutcome upgrade(UUID actingLandId)` — on SUCCESS `level` is the NEW level.

- [ ] **Step 1: Constructor + field.** Replace

```java
    private final FederationRepository repository;

    public FederationManager(FederationRepository repository) {
        this.repository = repository;
    }
```
with
```java
    private final FederationRepository repository;
    private final FederationLevelConfig levelConfig;

    public FederationManager(FederationRepository repository, FederationLevelConfig levelConfig) {
        this.repository = repository;
        this.levelConfig = levelConfig;
    }
```

- [ ] **Step 2: Member cap in `approve`.** Change the enum to
```java
    public enum ApprovalResult { SUCCESS, NOT_AUTHORIZED, APPLICATION_NOT_FOUND, ALREADY_MEMBER, MEMBER_CAP_REACHED }
```
and in `approve`, right after the `ALREADY_MEMBER` check and before `repository.deleteAllApplicationsForLand(applicantLandId);`, insert:
```java
        Optional<Federation> federation = repository.findById(federationId);
        if (federation.isEmpty()) {
            return ApprovalResult.NOT_AUTHORIZED;
        }
        if (repository.countMembers(federationId) >= levelConfig.memberCap(federation.get().level())) {
            return ApprovalResult.MEMBER_CAP_REACHED;
        }
```

- [ ] **Step 3: New methods.** Add (e.g. after `updateDescription`):

```java
    public FederationLevelConfig levelConfig() {
        return levelConfig;
    }

    public Optional<Federation> findById(UUID federationId) throws SQLException {
        return repository.findById(federationId);
    }

    public Optional<FederationProgress> progressOf(UUID federationId) throws SQLException {
        return repository.findProgress(federationId);
    }

    /** @return false if the federation no longer exists (caller refunds the wallet). */
    public boolean deposit(UUID federationId, long amount) throws SQLException {
        return repository.depositToBank(federationId, amount);
    }

    public enum WithdrawResult { SUCCESS, NOT_LEADER, INSUFFICIENT_BANK }

    public WithdrawResult withdraw(UUID actingLandId, long amount) throws SQLException {
        Optional<Federation> federation = requireLeaderFederation(actingLandId);
        if (federation.isEmpty()) {
            return WithdrawResult.NOT_LEADER;
        }
        return repository.withdrawFromBank(federation.get().id(), amount)
                ? WithdrawResult.SUCCESS
                : WithdrawResult.INSUFFICIENT_BANK;
    }

    public void addActivity(UUID federationId, long amount) throws SQLException {
        repository.addActivity(federationId, amount);
    }

    public enum UpgradeResult { SUCCESS, NOT_LEADER, NOT_ENOUGH_ACTIVITY, NOT_ENOUGH_BANK, CHANGED }

    /** {@code level} is the new level on SUCCESS, otherwise the current one. */
    public record UpgradeOutcome(UpgradeResult result, int level, long activity, long requiredActivity, long bank, long cost) {
    }

    public UpgradeOutcome upgrade(UUID actingLandId) throws SQLException {
        Optional<Federation> federation = requireLeaderFederation(actingLandId);
        if (federation.isEmpty()) {
            return new UpgradeOutcome(UpgradeResult.NOT_LEADER, 0, 0, 0, 0, 0);
        }
        UUID federationId = federation.get().id();
        int level = federation.get().level();
        FederationProgress progress = repository.findProgress(federationId).orElse(new FederationProgress(0, 0));
        long requiredActivity = levelConfig.activityForNextLevel(level);
        long cost = levelConfig.costForNextLevel(level);

        UpgradeResult result;
        if (progress.activity() < requiredActivity) {
            result = UpgradeResult.NOT_ENOUGH_ACTIVITY;
        } else if (progress.bankBalance() < cost) {
            result = UpgradeResult.NOT_ENOUGH_BANK;
        } else if (repository.tryLevelUp(federationId, level, cost, requiredActivity)) {
            result = UpgradeResult.SUCCESS;
            level = level + 1;
        } else {
            result = UpgradeResult.CHANGED;
        }
        return new UpgradeOutcome(result, level, progress.activity(), requiredActivity, progress.bankBalance(), cost);
    }
```

- [ ] **Step 4: Config.** Append to `yeowool-federation/src/main/resources/config.yml`:

```yaml

level-up:
  # 현재 레벨 N → N+1 : 누적 활동량이 activity-per-level × N 이상이고,
  # 연합 은행에서 cost-per-level × N 온을 차감합니다. (활동량은 차감되지 않고 계속 쌓임)
  activity-per-level: 20000
  cost-per-level: 30000

member-cap:
  # 연합에 속할 수 있는 마을(토지) 수 = base + per-level × (레벨 - 1)  (연합장 토지 포함)
  base: 3
  per-level: 1
```

- [ ] **Step 5: Wire the config into the manager.** In `YeowoolFederation.onEnable`, replace
```java
        FederationManager manager = new FederationManager(repository);
```
with
```java
        FederationLevelConfig levelConfig = new FederationLevelConfig(
                getConfig().getLong("level-up.activity-per-level", 20000L),
                getConfig().getLong("level-up.cost-per-level", 30000L),
                getConfig().getInt("member-cap.base", 3),
                getConfig().getInt("member-cap.per-level", 1));
        FederationManager manager = new FederationManager(repository, levelConfig);
```

- [ ] **Step 6: Build**

Run: `./gradlew :yeowool-federation:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/FederationManager.java yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java yeowool-federation/src/main/resources/config.yml
git commit -m "Add federation bank, upgrade and member-cap logic to FederationManager"
```

---

### Task 5: Activity tracking + player federation-level cache

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/activity/ActivityTracker.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/shop/FederationLevelCache.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java`

**Interfaces:**
- Consumes: `FederationManager.addActivity`, `FederationManager.findById` (Task 4); `PlayerFederationResolver` (Task 3); core's `com.yeowool.core.api.event.PlayerLandXpChangeEvent` (`getUuid()`, `getDelta()`, fired on the main thread).
- Produces:
  - `ActivityTracker(JavaPlugin, FederationManager, PlayerFederationResolver)` — `Listener`; `void flush()` (blocking).
  - `FederationLevelCache(JavaPlugin, FederationManager, PlayerFederationResolver, ExecutorService)` — `Listener`; `OptionalInt get(UUID)`, `void put(UUID, int)`, `int lookupLevel(UUID) throws SQLException` (blocking, 0 = no federation), `void refresh(UUID) throws SQLException` (blocking).
  - In `YeowoolFederation.onEnable`: local `PlayerFederationResolver resolver` and `FederationLevelCache levelCache` exist BEFORE the `FederationCommand` is constructed (Tasks 6/7 pass them in).

- [ ] **Step 1: `ActivityTracker.java`**

```java
package com.yeowool.federation.activity;

import com.yeowool.core.api.event.PlayerLandXpChangeEvent;
import com.yeowool.federation.FederationManager;
import com.yeowool.federation.land.PlayerFederationResolver;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Federation activity = members' positive land-XP gains. XP events fire on every harvest/ore, so they're
 * summed per player in memory and {@link #flush()}ed to the DB once a minute (atomic "+=" per federation,
 * so all 3 servers flushing independently still add up correctly).
 */
public final class ActivityTracker implements Listener {

    private final JavaPlugin plugin;
    private final FederationManager manager;
    private final PlayerFederationResolver resolver;
    private final Map<UUID, Long> pending = new ConcurrentHashMap<>();

    public ActivityTracker(JavaPlugin plugin, FederationManager manager, PlayerFederationResolver resolver) {
        this.plugin = plugin;
        this.manager = manager;
        this.resolver = resolver;
    }

    @EventHandler
    public void onLandXp(PlayerLandXpChangeEvent event) {
        if (event.getDelta() > 0) {
            pending.merge(event.getUuid(), event.getDelta(), Long::sum);
        }
    }

    /** Blocking — call on the federation executor (or once at shutdown). Players with no federation are dropped. */
    public void flush() {
        for (UUID playerUuid : List.copyOf(pending.keySet())) {
            Long amount = pending.remove(playerUuid);
            if (amount == null || amount <= 0) {
                continue;
            }
            try {
                Optional<UUID> federationId = resolver.findFederationId(playerUuid);
                if (federationId.isPresent()) {
                    manager.addActivity(federationId.get(), amount);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합 활동량 반영 실패 (" + playerUuid + ")", e);
            }
        }
    }
}
```

- [ ] **Step 2: `FederationLevelCache.java`**

```java
package com.yeowool.federation.shop;

import com.yeowool.federation.Federation;
import com.yeowool.federation.FederationManager;
import com.yeowool.federation.land.PlayerFederationResolver;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * Online players' federation level (0 = no federation), for checks that run on the main thread and can't
 * hit the DB there (the shop gate). Loaded on join, refreshed every minute and whenever /연합 상점 opens;
 * up to a minute of staleness is accepted.
 */
public final class FederationLevelCache implements Listener {

    private final JavaPlugin plugin;
    private final FederationManager manager;
    private final PlayerFederationResolver resolver;
    private final ExecutorService executor;
    private final Map<UUID, Integer> levels = new ConcurrentHashMap<>();

    public FederationLevelCache(JavaPlugin plugin, FederationManager manager, PlayerFederationResolver resolver,
                                ExecutorService executor) {
        this.plugin = plugin;
        this.manager = manager;
        this.resolver = resolver;
        this.executor = executor;
    }

    /** Empty = not loaded yet. */
    public OptionalInt get(UUID playerUuid) {
        Integer level = levels.get(playerUuid);
        return level == null ? OptionalInt.empty() : OptionalInt.of(level);
    }

    public void put(UUID playerUuid, int level) {
        levels.put(playerUuid, level);
    }

    /** Blocking. 0 = no federation. */
    public int lookupLevel(UUID playerUuid) throws SQLException {
        Optional<UUID> federationId = resolver.findFederationId(playerUuid);
        if (federationId.isEmpty()) {
            return 0;
        }
        return manager.findById(federationId.get()).map(Federation::level).orElse(0);
    }

    /** Blocking. Skips players who logged off meanwhile so the map doesn't keep offline entries. */
    public void refresh(UUID playerUuid) throws SQLException {
        int level = lookupLevel(playerUuid);
        if (plugin.getServer().getPlayer(playerUuid) != null) {
            levels.put(playerUuid, level);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        executor.execute(() -> {
            try {
                refresh(playerUuid);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합 레벨 캐시 로딩 실패 (" + playerUuid + ")", e);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        levels.remove(event.getPlayer().getUniqueId());
    }
}
```

- [ ] **Step 3: Wire it up in `YeowoolFederation`.**

Add a field next to `executor`:
```java
    private ActivityTracker activityTracker;
```

In `onEnable`, immediately after the `FederationManager manager = new FederationManager(repository, levelConfig);` line (and therefore BEFORE `var federationCommand = ...`), add:
```java
        PlayerFederationResolver resolver = new PlayerFederationResolver(core.dataSource());
        this.activityTracker = new ActivityTracker(this, manager, resolver);
        FederationLevelCache levelCache = new FederationLevelCache(this, manager, resolver, executor);
        getServer().getPluginManager().registerEvents(activityTracker, this);
        getServer().getPluginManager().registerEvents(levelCache, this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            List<UUID> online = getServer().getOnlinePlayers().stream().map(Player::getUniqueId).toList();
            executor.execute(() -> {
                activityTracker.flush();
                for (UUID playerUuid : online) {
                    try {
                        levelCache.refresh(playerUuid);
                    } catch (SQLException e) {
                        getLogger().log(Level.SEVERE, "연합 레벨 캐시 갱신 실패 (" + playerUuid + ")", e);
                    }
                }
            });
        }, 1200L, 1200L);
```

Replace `onDisable` with:
```java
    @Override
    public void onDisable() {
        if (activityTracker != null) {
            activityTracker.flush();
        }
        if (executor != null) {
            executor.shutdown();
        }
    }
```

Add imports: `com.yeowool.federation.activity.ActivityTracker`, `com.yeowool.federation.land.PlayerFederationResolver`, `com.yeowool.federation.shop.FederationLevelCache`, `org.bukkit.entity.Player`, `java.util.List`, `java.util.UUID` (keep existing ones).

- [ ] **Step 4: Build**

Run: `./gradlew :yeowool-federation:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/activity/ActivityTracker.java yeowool-federation/src/main/java/com/yeowool/federation/shop/FederationLevelCache.java yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java
git commit -m "Track federation activity from land XP and cache players' federation level"
```

---

### Task 6: `/연합 은행`, `/연합 업그레이드`, `/연합 정보` progress, cap message

**Files:**
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java` (constructor call)
- Modify: `yeowool-federation/src/main/resources/messages.yml`

**Interfaces:**
- Consumes: Task 4's manager API (`progressOf`, `deposit`, `withdraw`/`WithdrawResult`, `upgrade`/`UpgradeOutcome`/`UpgradeResult`, `levelConfig()`, `ApprovalResult.MEMBER_CAP_REACHED`); `FederationProgress`; `PlayerFederationResolver` (the `resolver` local from Task 5); `core.economyData().hasBalance(UUID, long)` / `modifyBalance(UUID, long, String, String)`.
- Produces: `FederationCommand` constructor becomes `FederationCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, FederationManager manager, LandLookup landLookup, PlayerFederationResolver resolver, ExecutorService executor)`.

- [ ] **Step 1: Constructor.** Add fields `private final YeowoolCoreAPI core;` and `private final PlayerFederationResolver resolver;` and change the constructor to:

```java
    public FederationCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, FederationManager manager,
                              LandLookup landLookup, PlayerFederationResolver resolver, ExecutorService executor) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.manager = manager;
        this.landLookup = landLookup;
        this.resolver = resolver;
        this.executor = executor;
    }
```
Add imports: `com.yeowool.core.api.YeowoolCoreAPI`, `com.yeowool.federation.land.PlayerFederationResolver`.

In `YeowoolFederation.onEnable` change the construction to:
```java
        var federationCommand = new FederationCommand(this, core, messages, manager, landLookup, resolver, executor);
```

- [ ] **Step 2: Switch cases.** Add before the `default` case:
```java
            case "은행" -> handleBank(player, args);
            case "업그레이드" -> handleUpgrade(player);
```

- [ ] **Step 3: Handlers.** Add these methods (above `runOnMain`):

```java
    private void handleBank(Player player, String[] args) {
        if (args.length == 1) {
            showBankBalance(player);
            return;
        }
        if (args.length != 3 || (!args[1].equals("입금") && !args[1].equals("출금"))) {
            messages.send(player, "federation.bank-usage");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            messages.send(player, "federation.bank-invalid-amount");
            return;
        }
        if (amount <= 0) {
            messages.send(player, "federation.bank-invalid-amount");
            return;
        }
        if (args[1].equals("입금")) {
            deposit(player, amount);
        } else {
            withdraw(player, amount);
        }
    }

    private void showBankBalance(Player player) {
        executor.execute(() -> {
            try {
                Optional<UUID> federationId = resolver.findFederationId(player.getUniqueId());
                if (federationId.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-federation"));
                    return;
                }
                long balance = manager.progressOf(federationId.get()).map(FederationProgress::bankBalance).orElse(0L);
                runOnMain(() -> messages.send(player, "federation.bank-balance",
                        Placeholder.unparsed("amount", formatAmount(balance))));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 은행 조회 실패", e);
            }
        });
    }

    /** Wallet first (main thread), then the DB; a failed DB deposit refunds the wallet. */
    private void deposit(Player player, long amount) {
        executor.execute(() -> {
            try {
                Optional<UUID> federationId = resolver.findFederationId(player.getUniqueId());
                if (federationId.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.no-federation"));
                    return;
                }
                UUID targetFederation = federationId.get();
                runOnMain(() -> {
                    if (!core.economyData().hasBalance(player.getUniqueId(), amount)) {
                        messages.send(player, "federation.bank-insufficient-wallet");
                        return;
                    }
                    core.economyData().modifyBalance(player.getUniqueId(), -amount, "YeowoolFederation", "연합 은행 입금");
                    executor.execute(() -> {
                        boolean deposited;
                        try {
                            deposited = manager.deposit(targetFederation, amount);
                        } catch (java.sql.SQLException e) {
                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 은행 입금 실패", e);
                            deposited = false;
                        }
                        boolean success = deposited;
                        runOnMain(() -> {
                            if (success) {
                                messages.send(player, "federation.bank-deposit-success",
                                        Placeholder.unparsed("amount", formatAmount(amount)));
                            } else {
                                core.economyData().modifyBalance(player.getUniqueId(), amount, "YeowoolFederation", "연합 은행 입금 실패 환불");
                                messages.send(player, "federation.bank-deposit-failed");
                            }
                        });
                    });
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 은행 입금 준비 실패", e);
            }
        });
    }

    /** DB first (atomic "subtract only if enough"), wallet only after it succeeded. */
    private void withdraw(Player player, long amount) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.leader-only"));
                    return;
                }
                var result = manager.withdraw(land.get().id(), amount);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> {
                            core.economyData().modifyBalance(player.getUniqueId(), amount, "YeowoolFederation", "연합 은행 출금");
                            messages.send(player, "federation.bank-withdraw-success",
                                    Placeholder.unparsed("amount", formatAmount(amount)));
                        }
                        case NOT_LEADER -> messages.send(player, "federation.leader-only");
                        case INSUFFICIENT_BANK -> messages.send(player, "federation.bank-insufficient-bank");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 은행 출금 실패", e);
            }
        });
    }

    private void handleUpgrade(Player player) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> messages.send(player, "federation.leader-only"));
                    return;
                }
                var outcome = manager.upgrade(land.get().id());
                int cap = manager.levelConfig().memberCap(outcome.level());
                runOnMain(() -> {
                    switch (outcome.result()) {
                        case SUCCESS -> messages.send(player, "federation.upgrade-success",
                                Placeholder.unparsed("level", String.valueOf(outcome.level())),
                                Placeholder.unparsed("cap", String.valueOf(cap)));
                        case NOT_LEADER -> messages.send(player, "federation.leader-only");
                        case NOT_ENOUGH_ACTIVITY -> messages.send(player, "federation.upgrade-not-enough-activity",
                                Placeholder.unparsed("current", formatAmount(outcome.activity())),
                                Placeholder.unparsed("required", formatAmount(outcome.requiredActivity())));
                        case NOT_ENOUGH_BANK -> messages.send(player, "federation.upgrade-not-enough-bank",
                                Placeholder.unparsed("cost", formatAmount(outcome.cost())),
                                Placeholder.unparsed("bank", formatAmount(outcome.bank())));
                        case CHANGED -> messages.send(player, "federation.upgrade-changed");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 업그레이드 실패", e);
            }
        });
    }

    private static String formatAmount(long amount) {
        return String.format("%,d", amount);
    }
```

- [ ] **Step 4: `/연합 정보` progress line.** In `handleInfo`, replace the final
```java
                runOnMain(() -> player.sendMessage("§6[" + f.name() + "] §7Lv." + f.level() + " · 소개: " + description + memberLines));
```
with
```java
                FederationProgress progress = manager.progressOf(f.id()).orElse(new FederationProgress(0, 0));
                FederationLevelConfig levelConfig = manager.levelConfig();
                String progressLine = "\n§7은행: §e" + formatAmount(progress.bankBalance()) + "온"
                        + " §7· 활동량: §e" + formatAmount(progress.activity())
                        + "§7/" + formatAmount(levelConfig.activityForNextLevel(f.level()))
                        + " §7· 다음 레벨 비용: §e" + formatAmount(levelConfig.costForNextLevel(f.level())) + "온"
                        + " §7· 마을: §e" + members.size() + "§7/" + levelConfig.memberCap(f.level());
                runOnMain(() -> player.sendMessage("§6[" + f.name() + "] §7Lv." + f.level() + " · 소개: " + description + progressLine + memberLines));
```

- [ ] **Step 5: Cap message on accept.** In `handleApprovalDecision`'s `switch (result)`, add:
```java
                        case MEMBER_CAP_REACHED -> messages.send(player, "federation.member-cap-reached");
```

- [ ] **Step 6: Messages.** In `messages.yml` change the `usage` line to
```yaml
  usage: "§c사용법: /연합 <생성|가입신청|신청목록|수락|거절|탈퇴|추방|부연합장임명|부연합장해임|위임|폐쇄|소개글|정보|목록|은행|업그레이드>"
```
and append under `federation:` (2-space indent like the rest):
```yaml
  no-federation: "§c소속된 연합이 없습니다."
  bank-balance: "§6연합 은행 잔액: §e<amount>온"
  bank-usage: "§c사용법: /연합 은행 [입금|출금 <금액>]"
  bank-invalid-amount: "§c금액은 1 이상의 숫자로 입력해주세요."
  bank-insufficient-wallet: "§c지갑 잔액이 부족합니다."
  bank-insufficient-bank: "§c연합 은행 잔액이 부족합니다."
  bank-deposit-success: "§a연합 은행에 <amount>온을 입금했습니다."
  bank-deposit-failed: "§c입금에 실패해서 지갑으로 돌려드렸습니다."
  bank-withdraw-success: "§a연합 은행에서 <amount>온을 출금했습니다."
  upgrade-success: "§a연합이 Lv.<level>이 되었습니다! (마을 정원 <cap>개)"
  upgrade-not-enough-activity: "§c활동량이 부족합니다. (<current> / <required>)"
  upgrade-not-enough-bank: "§c연합 은행 잔액이 부족합니다. (필요 <cost>온, 보유 <bank>온)"
  upgrade-changed: "§c연합 상태가 방금 바뀌었습니다. 다시 시도해주세요."
  member-cap-reached: "§c연합 마을 정원이 꽉 찼습니다. 연합 레벨을 올리면 정원이 늘어납니다. (/연합 정보 에서 확인)"
```
(Note: live servers only receive NEW keys, so the changed `usage` text reaches only fresh installs — that's accepted; `/여울도움말` carries the real command list.)

- [ ] **Step 7: Build**

Run: `./gradlew :yeowool-federation:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java yeowool-federation/src/main/resources/messages.yml
git commit -m "Add /연합 은행, /연합 업그레이드, progress in /연합 정보, member-cap message"
```

---

### Task 7: Federation shops — config, gate, `/연합 상점` GUI

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/shop/FederationShop.java`
- Test: `yeowool-federation/src/test/java/com/yeowool/federation/shop/FederationShopTest.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/shop/FederationShopGate.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/shop/FederationShopGui.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java`
- Modify: `yeowool-federation/src/main/resources/config.yml`
- Modify: `yeowool-federation/src/main/resources/messages.yml`

**Interfaces:**
- Consumes: `ShopOpenEvent` (Task 1); `FederationLevelCache` (Task 5: `get`, `put`, `lookupLevel`); core GUI API `com.yeowool.core.api.gui.YeowoolGui` (`protected YeowoolGui(int size, Component title)`, `protected void setButton(int, GuiButton)`, `public void open(Player)`) and `GuiButton.of(ItemStack, Consumer<InventoryClickEvent>)`.
- Produces:
  - `record FederationShop(String shopId, String name, int minLevel)` + `static List<FederationShop> fromConfig(List<? extends Map<?, ?>> entries)` (pure)
  - `FederationCommand` constructor becomes `FederationCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, FederationManager manager, LandLookup landLookup, PlayerFederationResolver resolver, FederationLevelCache levelCache, List<FederationShop> shops, ExecutorService executor)`.

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.federation.shop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FederationShopTest {

    @Test
    void readsIdNameAndMinLevel() {
        List<FederationShop> shops = FederationShop.fromConfig(List.of(
                Map.of("shop-id", "federation_basic", "name", "연합 기본 상점", "min-level", 1),
                Map.of("shop-id", "federation_rare", "name", "연합 희귀 상점", "min-level", 5)));

        assertEquals(List.of(
                new FederationShop("federation_basic", "연합 기본 상점", 1),
                new FederationShop("federation_rare", "연합 희귀 상점", 5)), shops);
    }

    @Test
    void nameDefaultsToIdAndMinLevelDefaultsToOne() {
        List<FederationShop> shops = FederationShop.fromConfig(List.of(Map.of("shop-id", "federation_basic")));

        assertEquals(List.of(new FederationShop("federation_basic", "federation_basic", 1)), shops);
    }

    @Test
    void entriesWithoutShopIdAreSkipped() {
        List<FederationShop> shops = FederationShop.fromConfig(List.of(Map.of("name", "이름만 있음", "min-level", 3)));

        assertEquals(List.of(), shops);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :yeowool-federation:test`
Expected: FAIL — `FederationShop` does not exist.

- [ ] **Step 3: `FederationShop.java`**

```java
package com.yeowool.federation.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One config.yml `shops:` entry — an existing /상점 shop id that only federations of minLevel+ may open. */
public record FederationShop(String shopId, String name, int minLevel) {

    public static List<FederationShop> fromConfig(List<? extends Map<?, ?>> entries) {
        List<FederationShop> shops = new ArrayList<>();
        for (Map<?, ?> entry : entries) {
            Object id = entry.get("shop-id");
            if (id == null) {
                continue;
            }
            Object name = entry.get("name");
            Object minLevel = entry.get("min-level");
            shops.add(new FederationShop(
                    id.toString(),
                    name == null ? id.toString() : name.toString(),
                    minLevel instanceof Number number ? number.intValue() : 1));
        }
        return List.copyOf(shops);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :yeowool-federation:test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: `FederationShopGate.java`**

```java
package com.yeowool.federation.shop;

import com.yeowool.core.api.event.ShopOpenEvent;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.OptionalInt;

/** Vetoes entering a federation shop unless the player's (cached) federation level is high enough. */
public final class FederationShopGate implements Listener {

    private final MessageService messages;
    private final List<FederationShop> shops;
    private final FederationLevelCache levelCache;

    public FederationShopGate(MessageService messages, List<FederationShop> shops, FederationLevelCache levelCache) {
        this.messages = messages;
        this.shops = shops;
        this.levelCache = levelCache;
    }

    @EventHandler(ignoreCancelled = true)
    public void onShopOpen(ShopOpenEvent event) {
        FederationShop shop = shops.stream()
                .filter(candidate -> candidate.shopId().equals(event.getShopId()))
                .findFirst()
                .orElse(null);
        if (shop == null) {
            return;
        }
        OptionalInt level = levelCache.get(event.getPlayer().getUniqueId());
        if (level.isEmpty()) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "federation.shop-loading");
            return;
        }
        if (level.getAsInt() < shop.minLevel()) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "federation.shop-locked",
                    Placeholder.unparsed("level", String.valueOf(shop.minLevel())));
        }
    }
}
```

- [ ] **Step 6: `FederationShopGui.java`**

```java
package com.yeowool.federation.shop;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** /연합 상점 — lists federation shops; unlocked ones open through the normal /상점 path (so the gate and payment behave as usual). */
public final class FederationShopGui extends YeowoolGui {

    private static final int MAX_SLOTS = 54;

    public FederationShopGui(List<FederationShop> shops, int federationLevel) {
        super(sizeFor(shops.size()), Component.text("연합 상점", NamedTextColor.DARK_GREEN));
        for (int i = 0; i < shops.size() && i < MAX_SLOTS; i++) {
            FederationShop shop = shops.get(i);
            boolean unlocked = federationLevel >= shop.minLevel();
            setButton(i, GuiButton.of(icon(shop, unlocked), event -> {
                if (!unlocked) {
                    return;
                }
                Player player = (Player) event.getWhoClicked();
                player.closeInventory();
                player.performCommand("상점 " + shop.shopId());
            }));
        }
    }

    private static int sizeFor(int shopCount) {
        int rows = Math.max(1, (Math.min(shopCount, MAX_SLOTS) + 8) / 9);
        return rows * 9;
    }

    private static ItemStack icon(FederationShop shop, boolean unlocked) {
        ItemStack stack = new ItemStack(unlocked ? Material.CHEST : Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(shop.name(), unlocked ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(unlocked ? "클릭해서 열기" : "연합 Lv." + shop.minLevel() + " 필요", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }
}
```

Before relying on the GUI API, open `yeowool-core/src/main/java/com/yeowool/core/api/gui/YeowoolGui.java` and `GuiButton.java` to confirm those signatures; adapt minimally if they differ and say so in your report.

- [ ] **Step 7: `/연합 상점` in `FederationCommand`.** Add fields `private final FederationLevelCache levelCache;` and `private final List<FederationShop> shops;`, extend the constructor to

```java
    public FederationCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, FederationManager manager,
                              LandLookup landLookup, PlayerFederationResolver resolver, FederationLevelCache levelCache,
                              List<FederationShop> shops, ExecutorService executor) {
```
(assign the two new fields; keep the existing assignments), add the switch case
```java
            case "상점" -> handleShop(player);
```
and the handler:
```java
    private void handleShop(Player player) {
        if (shops.isEmpty()) {
            messages.send(player, "federation.shop-none");
            return;
        }
        executor.execute(() -> {
            try {
                int level = levelCache.lookupLevel(player.getUniqueId());
                levelCache.put(player.getUniqueId(), level);
                if (level == 0) {
                    runOnMain(() -> messages.send(player, "federation.no-federation"));
                    return;
                }
                runOnMain(() -> new FederationShopGui(shops, level).open(player));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "연합 상점 열기 실패", e);
            }
        });
    }
```
Add imports `com.yeowool.federation.shop.FederationLevelCache`, `com.yeowool.federation.shop.FederationShop`, `com.yeowool.federation.shop.FederationShopGui`.

- [ ] **Step 8: Wire in `YeowoolFederation`.** Before the `FederationCommand` construction add
```java
        List<FederationShop> shops = FederationShop.fromConfig(getConfig().getMapList("shops"));
        getServer().getPluginManager().registerEvents(new FederationShopGate(messages, shops, levelCache), this);
```
and change the construction to
```java
        var federationCommand = new FederationCommand(this, core, messages, manager, landLookup, resolver, levelCache, shops, executor);
```
Add imports `com.yeowool.federation.shop.FederationShop`, `com.yeowool.federation.shop.FederationShopGate`.

- [ ] **Step 9: Config + messages.** Append to `config.yml`:
```yaml

# 연합 전용 상점 — /상점생성 으로 만든 상점의 ID를 적으면, 그 레벨 이상 연합의 연합원만 열 수 있습니다.
# 연합원은 /연합 상점 으로 목록을 열고, 결제는 일반 상점처럼 개인 지갑(온)으로 합니다. 예:
#   - shop-id: federation_basic
#     name: "연합 기본 상점"
#     min-level: 1
shops: []
```
In `messages.yml`, change `usage` to end with `|은행|업그레이드|상점>` and append:
```yaml
  shop-none: "§7등록된 연합 상점이 없습니다."
  shop-locked: "§c이 상점은 연합 Lv.<level> 이상 연합원만 이용할 수 있습니다."
  shop-loading: "§7연합 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요."
```

- [ ] **Step 10: Build**

Run: `./gradlew :yeowool-federation:build`
Expected: `BUILD SUCCESSFUL` (all tests pass).

- [ ] **Step 11: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/shop/FederationShop.java yeowool-federation/src/test/java/com/yeowool/federation/shop/FederationShopTest.java yeowool-federation/src/main/java/com/yeowool/federation/shop/FederationShopGate.java yeowool-federation/src/main/java/com/yeowool/federation/shop/FederationShopGui.java yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java yeowool-federation/src/main/resources/config.yml yeowool-federation/src/main/resources/messages.yml
git commit -m "Add level-gated federation shops with /연합 상점"
```

---

### Task 8: Help, changelog, full build, deploy

**Files:**
- Modify: `yeowool-core/src/main/resources/help.yml`
- Modify: `update.md`

- [ ] **Step 1: Help.** In `help.yml` → `help.categories.마을연합`, append:
```yaml
      - "/연합 은행 [입금|출금 <금액>] - 연합 공동 자금 (입금은 연합원 누구나, 출금은 연합장만)"
      - "/연합 업그레이드 - 연합 레벨업 (연합장, 활동량 + 연합 은행 비용 필요 → 마을 정원 증가)"
      - "/연합 상점 - 연합 레벨별 전용 상점"
```

- [ ] **Step 2: Changelog.** In `update.md`, add a `## 2026-09-26` heading directly under the file's intro block (above `## 2026-09-24`) with this section:

```markdown
### 연합 은행·활동량·레벨업·연합 상점 추가 — 마을 연합 3단계 (yeowool-federation, yeowool-market, yeowool-core)
- **연합 은행**: `/연합 은행` 잔액 확인, `/연합 은행 입금 <금액>`(연합 소속 마을의 소유주·주민 누구나), `/연합 은행 출금 <금액>`(연합장만). 여러 서버에서 동시에 출금해도 잔액이 마이너스가 되지 않습니다.
- **활동량**: 연합원(소유주+주민)이 농사·채광·낚시·사냥 등으로 토지 경험치를 얻으면 그만큼 연합 활동량이 쌓입니다(1분마다 반영).
- **레벨업**: 연합장이 `/연합 업그레이드` — 현재 레벨 N에서 누적 활동량 `2만 × N` 이상 + 연합 은행에서 `3만 × N`온 차감. 레벨 상한 없음. 수치는 `plugins/YeowoolFederation/config.yml`의 `level-up`에서 조정.
- **마을 정원**: 연합에 속할 수 있는 마을 수 = 3 + (레벨 − 1) (연합장 마을 포함, `member-cap`에서 조정). 정원이 차 있으면 가입 수락이 거부됩니다. 부연합장 정원(10레벨당 1명)은 그대로.
- `/연합 정보`에 은행 잔액, 활동량(현재/다음 레벨 필요), 다음 레벨 비용, 마을 수(현재/정원)가 표시됩니다.
- **연합 전용 상점**: 기존 `/상점생성`으로 상점을 만든 뒤 `plugins/YeowoolFederation/config.yml`의 `shops`에 상점 ID·표시 이름·필요 레벨을 적으면, 그 레벨 이상 연합원만 열 수 있습니다. 연합원은 `/연합 상점`으로 목록을 열고(잠긴 상점은 필요 레벨 표시), 결제는 일반 상점처럼 개인 지갑(온). `/상점 <ID>` 직접 입력이나 NPC로 열어도 똑같이 막힙니다. 가입·탈퇴·레벨업이 상점 이용 가능 여부에 반영되기까지 최대 1분 걸릴 수 있습니다. 연합 상점은 `/상점` 메인 메뉴에는 두지 않는 것을 권장합니다.

세 서버(lobby/town/wild) `yeowool-core`/`yeowool-market`/`yeowool-federation` jar 배포 완료 — **재시작 필요**. 서버 파일 삭제 필요 없음(DB 컬럼·설정 키는 자동 추가).
```

- [ ] **Step 3: Full build + tests**

Run: `./gradlew test :yeowool-core:build :yeowool-market:build :yeowool-federation:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Deploy** — copy `yeowool-core/build/libs/yeowool-core-1.0.0-SNAPSHOT.jar`, `yeowool-market/build/libs/yeowool-market-1.0.0-SNAPSHOT.jar`, `yeowool-federation/build/libs/yeowool-federation-1.0.0-SNAPSHOT.jar` into each of `C:/YEOWOOL/lobby/plugins/`, `C:/YEOWOOL/town/plugins/`, `C:/YEOWOOL/wild/plugins/` (overwrite). Do not start/stop any server.

- [ ] **Step 5: Commit**

```bash
git add yeowool-core/src/main/resources/help.yml update.md
git commit -m "Document federation phase 3 in help and changelog"
```

- [ ] **Step 6: Manual verification (user, after restarting the 3 servers)** — deposit as a resident, withdraw as non-leader (denied) and as leader; earn land XP and see activity rise in `/연합 정보` after ~1 min; `/연합 업그레이드` below/at thresholds; accept an application at the member cap (denied); create a shop with `/상점생성`, register it in `shops` with `min-level: 2`, confirm `/연합 상점` shows it locked at Lv.1 and `/상점 <ID>` is blocked for non-members.

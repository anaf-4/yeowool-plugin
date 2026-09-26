# 의뢰 게시판 + 떠돌이 상인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a player-to-player item delivery request board (opened from the W6 Quest Board furniture) and a cross-server wandering merchant event to `yeowool-market`.

**Architecture:** Both features live in `yeowool-market` in new packages `com.yeowool.market.questboard` and `com.yeowool.market.merchant`. Shared MySQL tables coordinate the three backend servers: every money payout goes through a DB ledger (`yw_quest_payouts`) that only the player's current server credits; the merchant is one singleton state row advanced by conditional `seq` UPDATEs. JDBC runs on the market executor, Bukkit/wallet calls on the main thread.

**Tech Stack:** Paper 1.21.4 API, Java 21, MySQL (HikariCP `DataSource` from YeowoolCore), Citizens API 2.0.43 (anonymous NPC registry), ItemsAdder API 3.6.1 (`FurnitureInteractEvent`), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-27-quest-board-design.md`, `docs/superpowers/specs/2026-09-27-wandering-merchant-design.md`

## Global Constraints

- Java 21, Paper API only (no NMS). Build with `./gradlew :yeowool-market:build` from repo root (`C:/Users/apple/OneDrive/Desktop/YEOWOOL_PLUGIN`). If Gradle fails with `Unable to delete directory ...build/test-results/test/binary` (OneDrive lock), run `rm -rf yeowool-*/build/test-results/test/binary` and rerun.
- JDBC only on executor threads; Bukkit API, inventories, and `core.economyData()` calls only on the main thread.
- A wallet may only be changed on the main thread of the server where the player is currently online (`Bukkit.getPlayer(uuid) != null` checked in the same tick). Money owed to anyone else goes into `yw_quest_payouts`.
- Player-visible text is Korean, via `MessageService` keys in `yeowool-market/src/main/resources/messages.yml` (MiniMessage). User-controlled values go in with `Placeholder.unparsed`.
- Currency unit shown to players: `온`. Numbers formatted with `String.format("%,d", n)`.
- Commits: stage only the files the task touches (never `git add -A` / `git add .`; never stage `homepage-plan.md`), never `--amend`, message ends with a blank line then `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Do not dispatch subagents.

---

### Task 1: QuestBoardRules (pure money math) + tests

**Files:**
- Create: `yeowool-market/src/main/java/com/yeowool/market/questboard/QuestBoardRules.java`
- Test: `yeowool-market/src/test/java/com/yeowool/market/questboard/QuestBoardRulesTest.java`

**Interfaces:**
- Produces: `QuestBoardRules.totalReward(int quantity, long rewardPerItem) -> long` (-1 on overflow), `fee(long total, int feePercent) -> long` (-1 on overflow), `upfrontCost(int quantity, long rewardPerItem, int feePercent) -> long` (-1 on overflow), `refund(int quantity, int delivered, long rewardPerItem) -> long`, `deliverable(int remaining, int held) -> int`, `validQuantity(long quantity, int max) -> boolean`.

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.market.questboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestBoardRulesTest {

    @Test
    void totalRewardMultipliesAndFlagsOverflow() {
        assertEquals(6400, QuestBoardRules.totalReward(64, 100));
        assertEquals(-1, QuestBoardRules.totalReward(100_000, Long.MAX_VALUE / 2));
    }

    @Test
    void feeIsFloorOfPercent() {
        assertEquals(320, QuestBoardRules.fee(6400, 5));
        assertEquals(0, QuestBoardRules.fee(19, 5));
        assertEquals(5, QuestBoardRules.fee(101, 5));
        assertEquals(0, QuestBoardRules.fee(6400, 0));
        assertEquals(-1, QuestBoardRules.fee(Long.MAX_VALUE, 200));
    }

    @Test
    void upfrontCostIsTotalPlusFee() {
        assertEquals(6720, QuestBoardRules.upfrontCost(64, 100, 5));
        assertEquals(-1, QuestBoardRules.upfrontCost(2, Long.MAX_VALUE / 2, 5));
    }

    @Test
    void refundCoversOnlyUndeliveredItems() {
        assertEquals(3000, QuestBoardRules.refund(64, 34, 100));
        assertEquals(0, QuestBoardRules.refund(64, 64, 100));
        assertEquals(0, QuestBoardRules.refund(10, 12, 100));
    }

    @Test
    void deliverableIsCappedByRemainingAndHeld() {
        assertEquals(10, QuestBoardRules.deliverable(10, 64));
        assertEquals(5, QuestBoardRules.deliverable(64, 5));
        assertEquals(0, QuestBoardRules.deliverable(0, 5));
        assertEquals(0, QuestBoardRules.deliverable(-3, 5));
    }

    @Test
    void validQuantityRespectsBounds() {
        assertTrue(QuestBoardRules.validQuantity(1, 100));
        assertTrue(QuestBoardRules.validQuantity(100, 100));
        assertFalse(QuestBoardRules.validQuantity(0, 100));
        assertFalse(QuestBoardRules.validQuantity(101, 100));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :yeowool-market:test --tests "com.yeowool.market.questboard.QuestBoardRulesTest"`
Expected: FAIL — compilation error, `QuestBoardRules` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.yeowool.market.questboard;

/**
 * Money math for the quest board, kept free of Bukkit so it can be unit
 * tested. Every method that multiplies returns -1 instead of overflowing so
 * callers can reject absurd requests before any money moves.
 */
public final class QuestBoardRules {

    private QuestBoardRules() {
    }

    /** quantity × rewardPerItem, or -1 when it overflows a long. */
    public static long totalReward(int quantity, long rewardPerItem) {
        try {
            return Math.multiplyExact((long) quantity, rewardPerItem);
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    /** floor(total × feePercent / 100), or -1 when it overflows a long. */
    public static long fee(long total, int feePercent) {
        if (feePercent <= 0) {
            return 0;
        }
        try {
            return Math.addExact(Math.multiplyExact(total / 100, (long) feePercent), (total % 100) * feePercent / 100);
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    /** What registering takes from the requester up front (total reward + burned fee), or -1 on overflow. */
    public static long upfrontCost(int quantity, long rewardPerItem, int feePercent) {
        long total = totalReward(quantity, rewardPerItem);
        long fee = total < 0 ? -1 : fee(total, feePercent);
        if (fee < 0) {
            return -1;
        }
        try {
            return Math.addExact(total, fee);
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    /** Reward still held for items nobody delivered — paid back on cancel/expiry (the fee is not refunded). */
    public static long refund(int quantity, int delivered, long rewardPerItem) {
        return (long) Math.max(0, quantity - delivered) * rewardPerItem;
    }

    /** How many items one delivery takes: never more than the request still needs or the player holds. */
    public static int deliverable(int remaining, int held) {
        return Math.max(0, Math.min(remaining, held));
    }

    public static boolean validQuantity(long quantity, int max) {
        return quantity >= 1 && quantity <= max;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :yeowool-market:test --tests "com.yeowool.market.questboard.QuestBoardRulesTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add yeowool-market/src/main/java/com/yeowool/market/questboard/QuestBoardRules.java yeowool-market/src/test/java/com/yeowool/market/questboard/QuestBoardRulesTest.java
git commit -m "Add quest board money rules

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: QuestRequest + QuestRepository (schema, transactions, payout ledger)

**Files:**
- Create: `yeowool-market/src/main/java/com/yeowool/market/questboard/QuestRequest.java`
- Create: `yeowool-market/src/main/java/com/yeowool/market/questboard/QuestRepository.java`

**Interfaces:**
- Consumes: `QuestBoardRules.refund(int,int,long)` (Task 1); `com.yeowool.core.util.ItemStackSerializer.serialize(ItemStack) -> String`, `deserialize(String) -> ItemStack`.
- Produces:
  - `record QuestRequest(long id, UUID requester, String requesterName, ItemStack sample, String itemLabel, int quantity, int delivered, long rewardPerItem, String status, long createdAt, long expiresAt)` with `int remaining()`, `boolean isOpen()`.
  - `QuestRepository(DataSource)`; `record QuestRepository.Payout(long id, UUID player, long amount, String reason)`.
  - `void createTables() throws SQLException`
  - `long insert(UUID requester, String requesterName, ItemStack sample, String itemLabel, int quantity, long rewardPerItem, long now, long expiresAt) throws SQLException` → new id
  - `int countOpen(UUID requester) throws SQLException`
  - `List<QuestRequest> listOpen(long now, int offset, int limit) throws SQLException` (newest first, unexpired only)
  - `List<QuestRequest> listByRequester(UUID requester, int limit) throws SQLException` (open first, then newest)
  - `Optional<QuestRequest> find(long id) throws SQLException`
  - `long deliver(long id, UUID deliverer, int amount, long now) throws SQLException` → reward ledgered for the deliverer, or -1 if the request can't take `amount` now
  - `long cancel(long id, UUID requester) throws SQLException` → refund ledgered, or -1 if not open / not theirs
  - `int expireDue(long now) throws SQLException` → number closed by this call
  - `void insertPayout(UUID player, long amount, String reason) throws SQLException`
  - `List<Payout> pendingPayouts(UUID player) throws SQLException`
  - `boolean deletePayout(long id) throws SQLException` → true only for the one caller whose DELETE removed the row

No unit test (JDBC against MySQL); verified by compiling here and by in-game testing after Task 3.

- [ ] **Step 1: Create `QuestRequest.java`**

```java
package com.yeowool.market.questboard;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** One row of {@code yw_quest_requests}; {@code sample} is a 1-item copy of what the requester wants. */
public record QuestRequest(long id, UUID requester, String requesterName, ItemStack sample, String itemLabel,
                           int quantity, int delivered, long rewardPerItem, String status, long createdAt, long expiresAt) {

    public static final String OPEN = "OPEN";

    public int remaining() {
        return quantity - delivered;
    }

    public boolean isOpen() {
        return OPEN.equals(status);
    }
}
```

- [ ] **Step 2: Create `QuestRepository.java`**

```java
package com.yeowool.market.questboard;

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
import java.util.Optional;
import java.util.UUID;

/**
 * All quest-board SQL. Every state change that owes someone money writes the
 * matching {@code yw_quest_payouts} row in the same transaction, and every
 * change is conditional on the row still being OPEN, so three servers
 * running the same action at once still move money exactly once.
 */
public final class QuestRepository {

    public record Payout(long id, UUID player, long amount, String reason) {
    }

    private static final String REQUESTS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_quest_requests (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                requester CHAR(36) NOT NULL,
                requester_name VARCHAR(16) NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                item_label VARCHAR(64) NOT NULL,
                quantity INT NOT NULL,
                delivered INT NOT NULL DEFAULT 0,
                reward_per_item BIGINT NOT NULL,
                status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
                created_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                INDEX idx_status (status),
                INDEX idx_requester (requester)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String PAYOUTS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_quest_payouts (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                player CHAR(36) NOT NULL,
                amount BIGINT NOT NULL,
                reason VARCHAR(128) NOT NULL,
                created_at BIGINT NOT NULL,
                INDEX idx_player (player)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String COLUMNS = "id, requester, requester_name, item_data, item_label, quantity, delivered, "
            + "reward_per_item, status, created_at, expires_at";

    private final DataSource dataSource;

    public QuestRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(REQUESTS_DDL);
            statement.executeUpdate(PAYOUTS_DDL);
        }
    }

    public long insert(UUID requester, String requesterName, ItemStack sample, String itemLabel, int quantity,
                       long rewardPerItem, long now, long expiresAt) throws SQLException {
        String sql = "INSERT INTO yw_quest_requests (requester, requester_name, item_data, item_label, quantity, "
                + "reward_per_item, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, requester.toString());
            ps.setString(2, requesterName);
            ps.setString(3, ItemStackSerializer.serialize(sample));
            ps.setString(4, itemLabel);
            ps.setInt(5, quantity);
            ps.setLong(6, rewardPerItem);
            ps.setLong(7, now);
            ps.setLong(8, expiresAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("의뢰 id를 받지 못했습니다");
                }
                return keys.getLong(1);
            }
        }
    }

    public int countOpen(UUID requester) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM yw_quest_requests WHERE requester = ? AND status = 'OPEN'")) {
            ps.setString(1, requester.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public List<QuestRequest> listOpen(long now, int offset, int limit) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT " + COLUMNS
                     + " FROM yw_quest_requests WHERE status = 'OPEN' AND expires_at > ? ORDER BY id DESC LIMIT ? OFFSET ?")) {
            ps.setLong(1, now);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            return readAll(ps);
        }
    }

    public List<QuestRequest> listByRequester(UUID requester, int limit) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT " + COLUMNS
                     + " FROM yw_quest_requests WHERE requester = ? ORDER BY (status = 'OPEN') DESC, id DESC LIMIT ?")) {
            ps.setString(1, requester.toString());
            ps.setInt(2, limit);
            return readAll(ps);
        }
    }

    public Optional<QuestRequest> find(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT " + COLUMNS + " FROM yw_quest_requests WHERE id = ?")) {
            ps.setLong(1, id);
            List<QuestRequest> found = readAll(ps);
            return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
        }
    }

    /**
     * Records {@code amount} delivered items and ledgers the deliverer's reward
     * in one transaction. The UPDATE only matches while the request is OPEN,
     * unexpired and still needs at least {@code amount} — so concurrent
     * deliveries from different servers can never over-fill it. The status
     * assignment comes first on purpose: MySQL evaluates SET left to right, so
     * it must read {@code delivered} before it is incremented.
     *
     * @return the reward ledgered for the deliverer, or -1 if nothing was recorded
     */
    public long deliver(long id, UUID deliverer, int amount, long now) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE yw_quest_requests SET status = IF(delivered + ? >= quantity, 'COMPLETED', 'OPEN'), "
                                + "delivered = delivered + ? "
                                + "WHERE id = ? AND status = 'OPEN' AND expires_at > ? AND delivered + ? <= quantity")) {
                    ps.setInt(1, amount);
                    ps.setInt(2, amount);
                    ps.setLong(3, id);
                    ps.setLong(4, now);
                    ps.setInt(5, amount);
                    if (ps.executeUpdate() != 1) {
                        connection.rollback();
                        return -1;
                    }
                }
                long reward;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT reward_per_item FROM yw_quest_requests WHERE id = ?")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        reward = rs.getLong(1) * amount;
                    }
                }
                insertPayout(connection, deliverer, reward, "의뢰 #" + id + " 납품 " + amount + "개");
                connection.commit();
                return reward;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Cancels an OPEN request owned by {@code requester} and ledgers the refund; -1 if it isn't open or isn't theirs. */
    public long cancel(long id, UUID requester) throws SQLException {
        return close(id, requester, "CANCELLED", "의뢰 #" + id + " 취소 환불");
    }

    /** Expires up to 50 overdue OPEN requests, ledgering each refund; returns how many this call closed. */
    public int expireDue(long now) throws SQLException {
        List<Long> due = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id FROM yw_quest_requests WHERE status = 'OPEN' AND expires_at <= ? LIMIT 50")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    due.add(rs.getLong(1));
                }
            }
        }
        int closed = 0;
        for (long id : due) {
            if (close(id, null, "EXPIRED", "의뢰 #" + id + " 만료 환불") >= 0) {
                closed++;
            }
        }
        return closed;
    }

    /** Locks the row, and only if it is still OPEN (and owned by {@code requester} when given) closes it and ledgers the refund. */
    private long close(long id, UUID requester, String newStatus, String reason) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UUID owner;
                long refund;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT requester, quantity, delivered, reward_per_item FROM yw_quest_requests "
                                + "WHERE id = ? AND status = 'OPEN' FOR UPDATE")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            return -1;
                        }
                        owner = UUID.fromString(rs.getString(1));
                        refund = QuestBoardRules.refund(rs.getInt(2), rs.getInt(3), rs.getLong(4));
                    }
                }
                if (requester != null && !requester.equals(owner)) {
                    connection.rollback();
                    return -1;
                }
                try (PreparedStatement ps = connection.prepareStatement("UPDATE yw_quest_requests SET status = ? WHERE id = ?")) {
                    ps.setString(1, newStatus);
                    ps.setLong(2, id);
                    ps.executeUpdate();
                }
                if (refund > 0) {
                    insertPayout(connection, owner, refund, reason);
                }
                connection.commit();
                return refund;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void insertPayout(UUID player, long amount, String reason) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            insertPayout(connection, player, amount, reason);
        }
    }

    private static void insertPayout(Connection connection, UUID player, long amount, String reason) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO yw_quest_payouts (player, amount, reason, created_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, player.toString());
            ps.setLong(2, amount);
            ps.setString(3, reason);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<Payout> pendingPayouts(UUID player) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, player, amount, reason FROM yw_quest_payouts WHERE player = ? ORDER BY id")) {
            ps.setString(1, player.toString());
            List<Payout> payouts = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payouts.add(new Payout(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getLong(3), rs.getString(4)));
                }
            }
            return payouts;
        }
    }

    /** True only for the single caller whose DELETE actually removed the row — that caller owns the payout. */
    public boolean deletePayout(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM yw_quest_payouts WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    private static List<QuestRequest> readAll(PreparedStatement ps) throws SQLException {
        List<QuestRequest> requests = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                requests.add(new QuestRequest(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("requester")),
                        rs.getString("requester_name"),
                        ItemStackSerializer.deserialize(rs.getString("item_data")),
                        rs.getString("item_label"),
                        rs.getInt("quantity"),
                        rs.getInt("delivered"),
                        rs.getLong("reward_per_item"),
                        rs.getString("status"),
                        rs.getLong("created_at"),
                        rs.getLong("expires_at")));
            }
        }
        return requests;
    }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :yeowool-market:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add yeowool-market/src/main/java/com/yeowool/market/questboard/QuestRequest.java yeowool-market/src/main/java/com/yeowool/market/questboard/QuestRepository.java
git commit -m "Add quest board repository with payout ledger

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Quest board service, GUIs, listeners, wiring

**Files:**
- Create: `yeowool-market/src/main/java/com/yeowool/market/questboard/QuestPayoutClaimer.java`
- Create: `yeowool-market/src/main/java/com/yeowool/market/questboard/QuestBoardService.java`
- Create: `yeowool-market/src/main/java/com/yeowool/market/questboard/QuestBoardGui.java`
- Create: `yeowool-market/src/main/java/com/yeowool/market/questboard/MyQuestsGui.java`
- Create: `yeowool-market/src/main/java/com/yeowool/market/questboard/QuestBoardListener.java`
- Create: `yeowool-market/src/main/java/com/yeowool/market/questboard/QuestBoardFurnitureListener.java`
- Modify: `yeowool-market/src/main/java/com/yeowool/market/YeowoolMarket.java`
- Modify: `yeowool-market/src/main/resources/config.yml` (append)
- Modify: `yeowool-market/src/main/resources/messages.yml` (append)
- Modify: `yeowool-market/src/main/resources/plugin.yml` (add command)

**Interfaces:**
- Consumes: Task 1 `QuestBoardRules`; Task 2 `QuestRequest`, `QuestRepository` (all methods listed there). Core: `YeowoolCoreAPI.economyData().modifyBalance(UUID, long, String, String) -> boolean` (false and no change if it would go negative), `YeowoolCoreAPI.mailbox().deliverOrStore(UUID, ItemStack, String, String)` (main thread), `MessageService.send(CommandSender, String, TagResolver...)`, `YeowoolGui(int, Component)`, `setButton(int, GuiButton)`, `GuiButton.of(ItemStack, Consumer<InventoryClickEvent>)`, `GuiButton.display(ItemStack)`, `com.yeowool.core.util.DurationFormat.humanize(long ms)`. ItemsAdder: `dev.lone.itemsadder.api.Events.FurnitureInteractEvent` (`getNamespacedID()`, `getPlayer()`, `setCancelled`).
- Produces: `QuestBoardService.openBoard(Player, int page)`, `openMine(Player)`, `beginRegister(Player)`, `consumeChat(Player, String) -> boolean`, `forget(UUID)`, `deliver(Player, QuestRequest, int page)`, `cancel(Player, long)`, `expireDue()`, `settings()`; `QuestPayoutClaimer.claim(UUID)`, `claimAllOnline()`.

- [ ] **Step 1: Create `QuestPayoutClaimer.java`**

```java
package com.yeowool.market.questboard;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Pays out {@code yw_quest_payouts} rows to players online on THIS server.
 * A row is claimed by deleting it (only one server's DELETE can win), then
 * credited on the main thread if the player is still here; if they left in
 * between, the row is written back for whichever server they're on next.
 */
public final class QuestPayoutClaimer {

    private static final String SOURCE = "YeowoolMarket";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final QuestRepository repository;
    private final Executor executor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public QuestPayoutClaimer(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                              QuestRepository repository, Executor executor) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.executor = executor;
    }

    /** Main thread. */
    public void claimAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            claim(player.getUniqueId());
        }
    }

    /** Main thread. No-op if the player isn't online here or a claim for them is already running. */
    public void claim(UUID uuid) {
        if (Bukkit.getPlayer(uuid) == null || !inFlight.add(uuid)) {
            return;
        }
        executor.execute(() -> {
            List<QuestRepository.Payout> claimed = new ArrayList<>();
            try {
                for (QuestRepository.Payout payout : repository.pendingPayouts(uuid)) {
                    if (repository.deletePayout(payout.id())) {
                        claimed.add(payout);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "의뢰 정산 장부 조회 실패 (" + uuid + ")", e);
            }
            runOnMain(() -> {
                inFlight.remove(uuid);
                credit(uuid, claimed);
            }, uuid, claimed);
        });
    }

    private void credit(UUID uuid, List<QuestRepository.Payout> claimed) {
        if (claimed.isEmpty()) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        List<QuestRepository.Payout> unpaid = new ArrayList<>();
        long total = 0;
        for (QuestRepository.Payout payout : claimed) {
            if (player != null && core.economyData().modifyBalance(uuid, payout.amount(), SOURCE, payout.reason())) {
                total += payout.amount();
            } else {
                unpaid.add(payout);
            }
        }
        if (total > 0) {
            messages.send(player, "questboard.payout-received", Placeholder.unparsed("amount", String.format("%,d", total)));
        }
        if (!unpaid.isEmpty()) {
            executor.execute(() -> restore(unpaid));
        }
    }

    /** Puts claimed-but-unpaid rows back so the player gets them on their next server. */
    private void restore(List<QuestRepository.Payout> unpaid) {
        for (QuestRepository.Payout payout : unpaid) {
            try {
                repository.insertPayout(payout.player(), payout.amount(), payout.reason());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "의뢰 정산 복구 실패 — 수동 지급 필요: " + payout.player()
                        + " " + payout.amount() + "온 (" + payout.reason() + ")", e);
            }
        }
    }

    /** Schedules on the main thread; if the plugin is already disabled, restores the rows instead of losing them. */
    private void runOnMain(Runnable task, UUID uuid, List<QuestRepository.Payout> claimed) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else {
            inFlight.remove(uuid);
            restore(claimed);
        }
    }
}
```

- [ ] **Step 2: Create `QuestBoardService.java`**

```java
package com.yeowool.market.questboard;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The quest board's flows: opening the GUIs, the two-step chat prompt that
 * registers a request, delivering, cancelling and expiring. Money only ever
 * leaves a wallet here (registration, main thread, player online); money
 * owed to anyone goes through {@link QuestRepository}'s payout ledger and
 * {@link QuestPayoutClaimer}.
 */
public final class QuestBoardService {

    public record Settings(String furnitureId, int feePercent, long durationMillis, int maxOpenPerPlayer, int maxQuantity) {
    }

    /** A player partway through the chat prompts; quantity 0 means we're still asking for the quantity. */
    private record PendingInput(ItemStack sample, int quantity) {
    }

    @FunctionalInterface
    private interface SqlCall<T> {
        T call() throws SQLException;
    }

    static final int PAGE_SIZE = 45;
    private static final String SOURCE = "YeowoolMarket";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final QuestRepository repository;
    private final QuestPayoutClaimer claimer;
    private final Executor executor;
    private final Settings settings;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public QuestBoardService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, QuestRepository repository,
                             QuestPayoutClaimer claimer, Executor executor, Settings settings) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.claimer = claimer;
        this.executor = executor;
        this.settings = settings;
    }

    public Settings settings() {
        return settings;
    }

    // ---- GUIs (main thread) ----

    public void openBoard(Player player, int page) {
        long now = System.currentTimeMillis();
        async(player.getUniqueId(), () -> repository.listOpen(now, page * PAGE_SIZE, PAGE_SIZE + 1), requests -> {
            if (!player.isOnline()) {
                return;
            }
            boolean hasNext = requests.size() > PAGE_SIZE;
            new QuestBoardGui(this, hasNext ? requests.subList(0, PAGE_SIZE) : requests, page, hasNext).open(player);
        });
    }

    public void openMine(Player player) {
        async(player.getUniqueId(), () -> repository.listByRequester(player.getUniqueId(), MyQuestsGui.SIZE), requests -> {
            if (player.isOnline()) {
                new MyQuestsGui(this, requests).open(player);
            }
        });
    }

    // ---- registering (main thread unless noted) ----

    public void beginRegister(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            messages.send(player, "questboard.hold-item");
            return;
        }
        pending.put(player.getUniqueId(), new PendingInput(hand.asOne(), 0));
        player.closeInventory();
        askQuantity(player);
    }

    /** Chat thread. True when the message answered one of our prompts — the caller then cancels the chat event. */
    public boolean consumeChat(Player player, String raw) {
        PendingInput input = pending.remove(player.getUniqueId());
        if (input == null) {
            return false;
        }
        Bukkit.getScheduler().runTask(plugin, () -> handleInput(player, input, raw.trim()));
        return true;
    }

    public void forget(UUID uuid) {
        pending.remove(uuid);
    }

    private void handleInput(Player player, PendingInput input, String raw) {
        if (!player.isOnline()) {
            return;
        }
        if (raw.equals("취소")) {
            messages.send(player, "questboard.input-cancelled");
            return;
        }
        long value;
        try {
            value = Long.parseLong(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            value = -1;
        }
        UUID uuid = player.getUniqueId();
        if (input.quantity() == 0) {
            if (!QuestBoardRules.validQuantity(value, settings.maxQuantity())) {
                pending.put(uuid, input);
                messages.send(player, "questboard.invalid-number");
                askQuantity(player);
                return;
            }
            pending.put(uuid, new PendingInput(input.sample(), (int) value));
            messages.send(player, "questboard.ask-reward");
            return;
        }
        if (value < 1) {
            pending.put(uuid, input);
            messages.send(player, "questboard.invalid-number");
            messages.send(player, "questboard.ask-reward");
            return;
        }
        register(player, input.sample(), input.quantity(), value);
    }

    private void askQuantity(Player player) {
        messages.send(player, "questboard.ask-quantity", Placeholder.unparsed("max", String.format("%,d", settings.maxQuantity())));
    }

    private void register(Player player, ItemStack sample, int quantity, long rewardPerItem) {
        long total = QuestBoardRules.totalReward(quantity, rewardPerItem);
        long cost = QuestBoardRules.upfrontCost(quantity, rewardPerItem, settings.feePercent());
        if (total < 0 || cost < 0) {
            messages.send(player, "questboard.too-expensive");
            return;
        }
        long fee = cost - total;
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String label = itemLabel(sample);
        async(uuid, () -> repository.countOpen(uuid), open -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                return;
            }
            // ponytail: the count and the insert aren't atomic, so two instant registrations can exceed the cap by one — fine for a soft limit.
            if (open >= settings.maxOpenPerPlayer()) {
                messages.send(online, "questboard.too-many-open", Placeholder.unparsed("max", String.valueOf(settings.maxOpenPerPlayer())));
                return;
            }
            if (!core.economyData().modifyBalance(uuid, -cost, SOURCE, "의뢰 등록 (" + label + " " + quantity + "개)")) {
                messages.send(online, "questboard.insufficient-funds",
                        Placeholder.unparsed("total", String.format("%,d", total)),
                        Placeholder.unparsed("fee", String.format("%,d", fee)));
                return;
            }
            long now = System.currentTimeMillis();
            executor.execute(() -> {
                try {
                    long id = repository.insert(uuid, name, sample, label, quantity, rewardPerItem, now, now + settings.durationMillis());
                    sync(uuid, p -> messages.send(p, "questboard.registered",
                            Placeholder.unparsed("id", String.valueOf(id)),
                            Placeholder.unparsed("item", label),
                            Placeholder.unparsed("quantity", String.format("%,d", quantity)),
                            Placeholder.unparsed("reward", String.format("%,d", rewardPerItem)),
                            Placeholder.unparsed("fee", String.format("%,d", fee))));
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "의뢰 등록 실패 (" + uuid + ", " + cost + "온 환불 예정)", e);
                    try {
                        repository.insertPayout(uuid, cost, "의뢰 등록 실패 환불");
                    } catch (SQLException e2) {
                        plugin.getLogger().log(Level.SEVERE, "의뢰 등록 환불 장부 기록 실패 — 수동 환불 필요: " + uuid + " " + cost + "온", e2);
                    }
                    sync(uuid, p -> {
                        messages.send(p, "questboard.register-failed");
                        claimer.claim(uuid);
                    });
                }
            });
        });
    }

    // ---- delivering (main thread) ----

    public void deliver(Player player, QuestRequest request, int page) {
        UUID uuid = player.getUniqueId();
        if (request.requester().equals(uuid)) {
            messages.send(player, "questboard.own-request");
            return;
        }
        async(uuid, () -> repository.find(request.id()), found -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                return;
            }
            if (found.isEmpty() || !found.get().isOpen()) {
                messages.send(online, "questboard.not-found");
                openBoard(online, page);
                return;
            }
            QuestRequest fresh = found.get();
            int amount = QuestBoardRules.deliverable(fresh.remaining(), countSimilar(online, fresh.sample()));
            if (amount == 0) {
                messages.send(online, "questboard.no-matching-items");
                return;
            }
            int leftover = online.getInventory().removeItem(fresh.sample().asQuantity(amount)).values().stream()
                    .mapToInt(ItemStack::getAmount).sum();
            int removed = amount - leftover;
            if (removed <= 0) {
                messages.send(online, "questboard.no-matching-items");
                return;
            }
            long now = System.currentTimeMillis();
            executor.execute(() -> {
                long reward;
                try {
                    reward = repository.deliver(fresh.id(), uuid, removed, now);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "의뢰 납품 실패 (#" + fresh.id() + ", " + uuid + ") — 아이템 반환", e);
                    reward = -1;
                }
                long result = reward;
                Bukkit.getScheduler().runTask(plugin, () -> finishDelivery(uuid, fresh, removed, result, page));
            });
        });
    }

    private void finishDelivery(UUID deliverer, QuestRequest request, int amount, long reward, int page) {
        Player player = Bukkit.getPlayer(deliverer);
        if (reward < 0) {
            giveStacks(deliverer, request.sample(), amount, "의뢰 #" + request.id() + " 납품 반환");
            if (player != null) {
                messages.send(player, "questboard.deliver-failed");
            }
            return;
        }
        giveStacks(request.requester(), request.sample(), amount, "의뢰 #" + request.id() + " 납품품");
        if (player != null) {
            messages.send(player, "questboard.delivered",
                    Placeholder.unparsed("item", request.itemLabel()),
                    Placeholder.unparsed("amount", String.format("%,d", amount)),
                    Placeholder.unparsed("reward", String.format("%,d", reward)));
            claimer.claim(deliverer);
            openBoard(player, page);
        }
    }

    /** Hands {@code amount} copies of {@code sample} to {@code recipient} in max-size stacks (inventory if online here and it fits, otherwise mailbox). */
    private void giveStacks(UUID recipient, ItemStack sample, int amount, String note) {
        int max = sample.getMaxStackSize();
        for (int left = amount; left > 0; left -= max) {
            core.mailbox().deliverOrStore(recipient, sample.asQuantity(Math.min(max, left)), SOURCE, note);
        }
    }

    private static int countSimilar(Player player, ItemStack sample) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.isSimilar(sample)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    // ---- cancel / expire ----

    /** Main thread. */
    public void cancel(Player player, long requestId) {
        UUID uuid = player.getUniqueId();
        async(uuid, () -> repository.cancel(requestId, uuid), refund -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                return;
            }
            if (refund < 0) {
                messages.send(online, "questboard.not-found");
            } else {
                messages.send(online, "questboard.cancelled",
                        Placeholder.unparsed("id", String.valueOf(requestId)),
                        Placeholder.unparsed("refund", String.format("%,d", refund)));
                claimer.claim(uuid);
            }
            openMine(online);
        });
    }

    /** Executor thread, every minute on every server — the conditional close in the repository makes each refund happen once. */
    public void expireDue() {
        try {
            repository.expireDue(System.currentTimeMillis());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "만료된 의뢰 처리 실패", e);
        }
    }

    // ---- helpers ----

    static String itemLabel(ItemStack sample) {
        ItemMeta meta = sample.getItemMeta();
        String label = meta != null && meta.hasDisplayName() && meta.displayName() != null
                ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                : sample.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return label.length() > 64 ? label.substring(0, 64) : label;
    }

    /** Runs {@code call} on the executor, then {@code then} on the main thread; SQL errors are logged and reported to the player. */
    private <T> void async(UUID uuid, SqlCall<T> call, Consumer<T> then) {
        executor.execute(() -> {
            T result;
            try {
                result = call.call();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "의뢰 게시판 DB 작업 실패 (" + uuid + ")", e);
                sync(uuid, p -> messages.send(p, "questboard.error"));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> then.accept(result));
        });
    }

    private void sync(UUID uuid, Consumer<Player> action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                action.accept(player);
            }
        });
    }
}
```

- [ ] **Step 3: Create `QuestBoardGui.java`**

```java
package com.yeowool.market.questboard;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.util.DurationFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** The board itself (opened from the quest board furniture): open requests newest first, click one to deliver what you hold. */
public final class QuestBoardGui extends YeowoolGui {

    private static final int SLOT_PREV = 45;
    private static final int SLOT_REGISTER = 49;
    private static final int SLOT_MINE = 50;
    private static final int SLOT_NEXT = 53;

    public QuestBoardGui(QuestBoardService service, List<QuestRequest> requests, int page, boolean hasNext) {
        super(54, Component.text("의뢰 게시판", NamedTextColor.DARK_GREEN));
        for (int i = 0; i < requests.size() && i < QuestBoardService.PAGE_SIZE; i++) {
            QuestRequest request = requests.get(i);
            setButton(i, GuiButton.of(icon(request, "클릭: 가진 만큼 납품"),
                    event -> service.deliver((Player) event.getWhoClicked(), request, page)));
        }
        if (page > 0) {
            setButton(SLOT_PREV, GuiButton.of(named(Material.ARROW, "이전 페이지", null),
                    event -> service.openBoard((Player) event.getWhoClicked(), page - 1)));
        }
        setButton(SLOT_REGISTER, GuiButton.of(named(Material.WRITABLE_BOOK, "의뢰 등록", "손에 든 아이템으로 의뢰를 올립니다"),
                event -> service.beginRegister((Player) event.getWhoClicked())));
        setButton(SLOT_MINE, GuiButton.of(named(Material.CHEST, "내 의뢰", "내가 올린 의뢰 확인·취소"),
                event -> service.openMine((Player) event.getWhoClicked())));
        if (hasNext) {
            setButton(SLOT_NEXT, GuiButton.of(named(Material.ARROW, "다음 페이지", null),
                    event -> service.openBoard((Player) event.getWhoClicked(), page + 1)));
        }
    }

    /** A copy of the requested item with the request's details appended to its lore; {@code action} may be null. */
    static ItemStack icon(QuestRequest request, String action) {
        ItemStack stack = request.sample().clone();
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(line("의뢰 #" + request.id() + " · " + request.requesterName(), NamedTextColor.GRAY));
        lore.add(line("납품 " + String.format("%,d", request.delivered()) + " / " + String.format("%,d", request.quantity()), NamedTextColor.YELLOW));
        lore.add(line("개당 보상 " + String.format("%,d", request.rewardPerItem()) + "온", NamedTextColor.GOLD));
        if (request.isOpen()) {
            lore.add(line("남은 시간 " + DurationFormat.humanize(Math.max(0, request.expiresAt() - System.currentTimeMillis())), NamedTextColor.AQUA));
        } else {
            lore.add(line(statusLabel(request.status()), NamedTextColor.DARK_GRAY));
        }
        if (action != null) {
            lore.add(line(action, NamedTextColor.GREEN));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    static ItemStack named(Material material, String name, String description) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(name, NamedTextColor.WHITE));
        if (description != null) {
            meta.lore(List.of(line(description, NamedTextColor.GRAY)));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "COMPLETED" -> "완료됨";
            case "CANCELLED" -> "취소됨";
            case "EXPIRED" -> "만료됨";
            default -> status;
        };
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
```

- [ ] **Step 4: Create `MyQuestsGui.java`**

```java
package com.yeowool.market.questboard;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /의뢰} or the board's "내 의뢰" button: the viewer's latest requests, open ones first; click an open one to cancel it. */
public final class MyQuestsGui extends YeowoolGui {

    static final int SIZE = 27;

    public MyQuestsGui(QuestBoardService service, List<QuestRequest> requests) {
        super(SIZE, Component.text("내 의뢰", NamedTextColor.DARK_GREEN));
        for (int i = 0; i < requests.size() && i < SIZE; i++) {
            QuestRequest request = requests.get(i);
            if (request.isOpen()) {
                setButton(i, GuiButton.of(QuestBoardGui.icon(request, "클릭: 의뢰 취소 (남은 보상 환불, 수수료 제외)"),
                        event -> service.cancel((Player) event.getWhoClicked(), request.id())));
            } else {
                setButton(i, GuiButton.display(QuestBoardGui.icon(request, null)));
            }
        }
    }
}
```

- [ ] **Step 5: Create `QuestBoardListener.java`**

```java
package com.yeowool.market.questboard;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Chat answers to the registration prompts (LOWEST so we see them before
 * other chat handlers like federation chat mode), dropping a half-finished
 * prompt on quit, and paying out ledgered money shortly after join.
 */
public final class QuestBoardListener implements Listener {

    private final JavaPlugin plugin;
    private final QuestBoardService service;
    private final QuestPayoutClaimer claimer;

    public QuestBoardListener(JavaPlugin plugin, QuestBoardService service, QuestPayoutClaimer claimer) {
        this.plugin = plugin;
        this.service = service;
        this.claimer = claimer;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (service.consumeChat(event.getPlayer(), raw)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> claimer.claim(uuid), 60L);
    }
}
```

- [ ] **Step 6: Create `QuestBoardFurnitureListener.java`**

```java
package com.yeowool.market.questboard;

import dev.lone.itemsadder.api.Events.FurnitureInteractEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Right-clicking the configured ItemsAdder furniture (W6 Quest Board by
 * default) opens the board. Only registered when ItemsAdder is enabled —
 * this class must not be loaded otherwise.
 */
public final class QuestBoardFurnitureListener implements Listener {

    private final QuestBoardService service;

    public QuestBoardFurnitureListener(QuestBoardService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        if (!service.settings().furnitureId().equals(event.getNamespacedID())) {
            return;
        }
        event.setCancelled(true);
        service.openBoard(event.getPlayer(), 0);
    }
}
```

- [ ] **Step 7: Wire into `YeowoolMarket.java`**

Add imports:

```java
import com.yeowool.market.questboard.QuestBoardFurnitureListener;
import com.yeowool.market.questboard.QuestBoardListener;
import com.yeowool.market.questboard.QuestBoardService;
import com.yeowool.market.questboard.QuestPayoutClaimer;
import com.yeowool.market.questboard.QuestRepository;
import org.bukkit.entity.Player;
```

Insert immediately before `getLogger().info("YeowoolMarket이 활성화되었습니다.");`:

```java
        // 의뢰 게시판 — 실패해도 나머지 상점 기능은 그대로 켜 둠
        try {
            QuestRepository questRepository = new QuestRepository(core.dataSource());
            questRepository.createTables();
            QuestBoardService.Settings questSettings = new QuestBoardService.Settings(
                    getConfig().getString("quest-board.furniture-id", "workshop_six:quest_board"),
                    getConfig().getInt("quest-board.fee-percent", 5),
                    getConfig().getLong("quest-board.duration-hours", 72L) * 3_600_000L,
                    getConfig().getInt("quest-board.max-open-per-player", 5),
                    getConfig().getInt("quest-board.max-quantity", 100000));
            QuestPayoutClaimer payoutClaimer = new QuestPayoutClaimer(this, core, messages, questRepository, executor);
            QuestBoardService questBoard = new QuestBoardService(this, core, messages, questRepository, payoutClaimer, executor, questSettings);
            getServer().getPluginManager().registerEvents(new QuestBoardListener(this, questBoard, payoutClaimer), this);
            if (getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
                getServer().getPluginManager().registerEvents(new QuestBoardFurnitureListener(questBoard), this);
            } else {
                getLogger().warning("ItemsAdder가 없어 의뢰 게시판 가구를 쓸 수 없습니다 (/의뢰로 내 의뢰만 확인 가능).");
            }
            bindCommand("의뢰", (sender, command, label, args) -> {
                if (sender instanceof Player player) {
                    questBoard.openMine(player);
                } else {
                    messages.send(sender, "general.player-only");
                }
                return true;
            });
            getServer().getScheduler().runTaskTimer(this, () -> {
                executor.execute(questBoard::expireDue);
                payoutClaimer.claimAllOnline();
            }, 20L * 60, 20L * 60);
        } catch (Exception e) {
            getLogger().severe("의뢰 게시판 초기화 실패 — 의뢰 게시판을 끕니다: " + e.getMessage());
        }
```

- [ ] **Step 8: Append to `config.yml`**

```yaml

# 의뢰 게시판 — 플레이어가 "아이템 N개 구함, 개당 P온" 의뢰를 올리고 다른 플레이어가 나눠서 납품.
# 게시판은 아래 ItemsAdder 가구를 우클릭해야만 열리고, /의뢰는 어디서나 "내 의뢰"만 엽니다.
quest-board:
  # 게시판으로 쓸 ItemsAdder 가구 ID (W6 Quest Board 팩)
  furniture-id: "workshop_six:quest_board"
  # 등록할 때 총 보상에 더해 추가로 내는 수수료(%) — 환불되지 않고 소각
  fee-percent: 5
  # 의뢰 유지 시간(시간). 지나면 남은 보상이 의뢰자에게 자동 환불
  duration-hours: 72
  # 한 사람이 동시에 올릴 수 있는 진행 중 의뢰 수
  max-open-per-player: 5
  # 의뢰 하나의 최대 수량
  max-quantity: 100000
```

- [ ] **Step 9: Append to `messages.yml`**

```yaml

questboard:
  hold-item: "<red>의뢰할 아이템을 손에 들고 눌러주세요.</red>"
  ask-quantity: "<yellow>필요한 수량을 채팅으로 입력하세요. (1~<max>, 그만두려면 '취소')</yellow>"
  ask-reward: "<yellow>개당 보상(온)을 채팅으로 입력하세요. (그만두려면 '취소')</yellow>"
  invalid-number: "<red>올바른 숫자를 입력하세요.</red>"
  input-cancelled: "<gray>의뢰 등록을 취소했습니다.</gray>"
  too-many-open: "<red>진행 중인 의뢰는 최대 <max>개까지 올릴 수 있습니다.</red>"
  too-expensive: "<red>보상 총액이 너무 큽니다.</red>"
  insufficient-funds: "<red>보상 총액 <total>온과 수수료 <fee>온이 필요합니다.</red>"
  registered: "<green>의뢰 #<id>을(를) 올렸습니다: <item> <quantity>개, 개당 <reward>온 (수수료 <fee>온)</green>"
  register-failed: "<red>의뢰 등록에 실패했습니다. 차감된 금액은 곧 환불됩니다.</red>"
  own-request: "<red>자신의 의뢰에는 납품할 수 없습니다.</red>"
  no-matching-items: "<red>인벤토리에 이 의뢰에 맞는 아이템이 없습니다.</red>"
  not-found: "<red>이미 마감되었거나 찾을 수 없는 의뢰입니다.</red>"
  delivered: "<green><item> <amount>개를 납품했습니다. 보상 <reward>온이 곧 지급됩니다.</green>"
  deliver-failed: "<red>그 사이 의뢰가 마감되었거나 수량이 바뀌었습니다. 아이템을 돌려드렸습니다.</red>"
  cancelled: "<green>의뢰 #<id>을(를) 취소했습니다. 환불 <refund>온이 곧 지급됩니다.</green>"
  payout-received: "<green>의뢰 게시판 정산으로 <amount>온을 받았습니다.</green>"
  error: "<red>처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.</red>"
```

- [ ] **Step 10: Add command to `plugin.yml`** (after the `경매:` command entry, before `permissions:`)

```yaml
  의뢰:
    description: 내가 올린 의뢰 목록을 엽니다 (의뢰 게시판은 게시판 가구를 우클릭)
```

- [ ] **Step 11: Build**

Run: `./gradlew :yeowool-market:build`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 12: Commit**

```bash
git add yeowool-market/src/main/java/com/yeowool/market/questboard/QuestPayoutClaimer.java yeowool-market/src/main/java/com/yeowool/market/questboard/QuestBoardService.java yeowool-market/src/main/java/com/yeowool/market/questboard/QuestBoardGui.java yeowool-market/src/main/java/com/yeowool/market/questboard/MyQuestsGui.java yeowool-market/src/main/java/com/yeowool/market/questboard/QuestBoardListener.java yeowool-market/src/main/java/com/yeowool/market/questboard/QuestBoardFurnitureListener.java yeowool-market/src/main/java/com/yeowool/market/YeowoolMarket.java yeowool-market/src/main/resources/config.yml yeowool-market/src/main/resources/messages.yml yeowool-market/src/main/resources/plugin.yml
git commit -m "Add quest board GUI, delivery flow and furniture entry point

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: MerchantRules + MerchantRepository

**Files:**
- Create: `yeowool-market/src/main/java/com/yeowool/market/merchant/MerchantRules.java`
- Test: `yeowool-market/src/test/java/com/yeowool/market/merchant/MerchantRulesTest.java`
- Create: `yeowool-market/src/main/java/com/yeowool/market/merchant/MerchantRepository.java`

**Interfaces:**
- Produces:
  - `MerchantRules.nextDelayMillis(Random random, int minMinutes, int maxMinutes) -> long` (uniform whole minutes in [min, max]; min>max → min; negatives → 0)
  - `MerchantRules.<T>pick(List<T> options, Random random) -> Optional<T>`
  - `MerchantRepository(DataSource)`; `record Spot(String name, String serverId, String world, double x, double y, double z, float yaw, float pitch)`; `record State(long seq, boolean active, Spot spot, long despawnAt, long nextSpawnAt)` (spot null when inactive)
  - `void createTables(long firstSpawnAt) throws SQLException`
  - `void saveSpot(Spot) throws SQLException` (upsert by name), `boolean deleteSpot(String name)`, `List<Spot> spots()`
  - `State state() throws SQLException`
  - `boolean spawn(long expectedSeq, Spot spot, long despawnAt)`, `boolean despawn(long expectedSeq, long nextSpawnAt)` — conditional on seq, bump seq
  - `boolean requestSpawnNow()` (only while inactive), `boolean requestDespawnNow()` (only while active)

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.market.merchant;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantRulesTest {

    @Test
    void delayStaysWithinBoundsInWholeMinutes() {
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            long delay = MerchantRules.nextDelayMillis(random, 180, 300);
            assertTrue(delay >= 180 * 60_000L && delay <= 300 * 60_000L, "out of range: " + delay);
            assertEquals(0, delay % 60_000L);
        }
    }

    @Test
    void delayUsesMinWhenBoundsAreInverted() {
        assertEquals(180 * 60_000L, MerchantRules.nextDelayMillis(new Random(1), 180, 100));
    }

    @Test
    void delayClampsNegativeMinToZero() {
        assertEquals(0, MerchantRules.nextDelayMillis(new Random(1), -5, -1));
    }

    @Test
    void pickReturnsEmptyForNoOptions() {
        assertEquals(Optional.empty(), MerchantRules.pick(List.of(), new Random(1)));
    }

    @Test
    void pickReturnsAnElementOfTheList() {
        List<String> spots = List.of("a", "b", "c");
        for (int i = 0; i < 50; i++) {
            assertTrue(spots.contains(MerchantRules.pick(spots, new Random(i)).orElseThrow()));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :yeowool-market:test --tests "com.yeowool.market.merchant.MerchantRulesTest"`
Expected: FAIL — `MerchantRules` does not exist.

- [ ] **Step 3: Create `MerchantRules.java`**

```java
package com.yeowool.market.merchant;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Pure scheduling picks for the wandering merchant, kept out of the service so they can be unit tested. */
public final class MerchantRules {

    private MerchantRules() {
    }

    /** Random whole-minute delay in [minMinutes, maxMinutes]; an inverted range collapses to minMinutes, negatives to 0. */
    public static long nextDelayMillis(Random random, int minMinutes, int maxMinutes) {
        int min = Math.max(0, minMinutes);
        int max = Math.max(min, maxMinutes);
        return (min + (long) random.nextInt(max - min + 1)) * 60_000L;
    }

    public static <T> Optional<T> pick(List<T> options, Random random) {
        return options.isEmpty() ? Optional.empty() : Optional.of(options.get(random.nextInt(options.size())));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :yeowool-market:test --tests "com.yeowool.market.merchant.MerchantRulesTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Create `MerchantRepository.java`**

```java
package com.yeowool.market.merchant;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Candidate spots plus one shared state row (id = 1) for the wandering
 * merchant. Spawn/despawn transitions are UPDATEs conditional on the
 * {@code seq} the caller read, and each bumps it — so when all three servers
 * notice the same due transition, exactly one of them makes it.
 */
public final class MerchantRepository {

    public record Spot(String name, String serverId, String world, double x, double y, double z, float yaw, float pitch) {
    }

    /** {@code spot} is null while no merchant is out. */
    public record State(long seq, boolean active, Spot spot, long despawnAt, long nextSpawnAt) {
    }

    private static final String SPOTS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_merchant_spots (
                name VARCHAR(32) NOT NULL PRIMARY KEY,
                server_id VARCHAR(32) NOT NULL,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String STATE_DDL = """
            CREATE TABLE IF NOT EXISTS yw_merchant_state (
                id TINYINT NOT NULL PRIMARY KEY,
                seq BIGINT NOT NULL DEFAULT 0,
                active TINYINT(1) NOT NULL DEFAULT 0,
                spot_name VARCHAR(32) NULL,
                server_id VARCHAR(32) NULL,
                world VARCHAR(64) NULL,
                x DOUBLE NULL,
                y DOUBLE NULL,
                z DOUBLE NULL,
                yaw FLOAT NULL,
                pitch FLOAT NULL,
                despawn_at BIGINT NOT NULL DEFAULT 0,
                next_spawn_at BIGINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final DataSource dataSource;

    public MerchantRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Creates the tables and, only the very first time, the state row with the first appearance at {@code firstSpawnAt}. */
    public void createTables(long firstSpawnAt) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(SPOTS_DDL);
                statement.executeUpdate(STATE_DDL);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT IGNORE INTO yw_merchant_state (id, next_spawn_at) VALUES (1, ?)")) {
                ps.setLong(1, firstSpawnAt);
                ps.executeUpdate();
            }
        }
    }

    public void saveSpot(Spot spot) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_merchant_spots (name, server_id, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE server_id = VALUES(server_id), world = VALUES(world), x = VALUES(x), "
                             + "y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)")) {
            ps.setString(1, spot.name());
            ps.setString(2, spot.serverId());
            ps.setString(3, spot.world());
            ps.setDouble(4, spot.x());
            ps.setDouble(5, spot.y());
            ps.setDouble(6, spot.z());
            ps.setFloat(7, spot.yaw());
            ps.setFloat(8, spot.pitch());
            ps.executeUpdate();
        }
    }

    public boolean deleteSpot(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM yw_merchant_spots WHERE name = ?")) {
            ps.setString(1, name);
            return ps.executeUpdate() == 1;
        }
    }

    public List<Spot> spots() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT name, server_id, world, x, y, z, yaw, pitch FROM yw_merchant_spots ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            List<Spot> spots = new ArrayList<>();
            while (rs.next()) {
                spots.add(new Spot(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getDouble(4), rs.getDouble(5), rs.getDouble(6), rs.getFloat(7), rs.getFloat(8)));
            }
            return spots;
        }
    }

    public State state() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT seq, active, spot_name, server_id, world, x, y, z, yaw, pitch, despawn_at, next_spawn_at "
                             + "FROM yw_merchant_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("yw_merchant_state 행이 없습니다");
            }
            boolean active = rs.getBoolean(2);
            Spot spot = active
                    ? new Spot(rs.getString(3), rs.getString(4), rs.getString(5),
                    rs.getDouble(6), rs.getDouble(7), rs.getDouble(8), rs.getFloat(9), rs.getFloat(10))
                    : null;
            return new State(rs.getLong(1), active, spot, rs.getLong(11), rs.getLong(12));
        }
    }

    public boolean spawn(long expectedSeq, Spot spot, long despawnAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_merchant_state SET seq = seq + 1, active = 1, spot_name = ?, server_id = ?, world = ?, "
                             + "x = ?, y = ?, z = ?, yaw = ?, pitch = ?, despawn_at = ? WHERE id = 1 AND seq = ? AND active = 0")) {
            ps.setString(1, spot.name());
            ps.setString(2, spot.serverId());
            ps.setString(3, spot.world());
            ps.setDouble(4, spot.x());
            ps.setDouble(5, spot.y());
            ps.setDouble(6, spot.z());
            ps.setFloat(7, spot.yaw());
            ps.setFloat(8, spot.pitch());
            ps.setLong(9, despawnAt);
            ps.setLong(10, expectedSeq);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean despawn(long expectedSeq, long nextSpawnAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_merchant_state SET seq = seq + 1, active = 0, next_spawn_at = ? WHERE id = 1 AND seq = ? AND active = 1")) {
            ps.setLong(1, nextSpawnAt);
            ps.setLong(2, expectedSeq);
            return ps.executeUpdate() == 1;
        }
    }

    /** Makes the next tick spawn a merchant; false if one is already out. */
    public boolean requestSpawnNow() throws SQLException {
        return executeOnState("UPDATE yw_merchant_state SET next_spawn_at = 0 WHERE id = 1 AND active = 0");
    }

    /** Makes the next tick send the merchant away; false if none is out. */
    public boolean requestDespawnNow() throws SQLException {
        return executeOnState("UPDATE yw_merchant_state SET despawn_at = 0 WHERE id = 1 AND active = 1");
    }

    private boolean executeOnState(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            return ps.executeUpdate() == 1;
        }
    }
}
```

- [ ] **Step 6: Build**

Run: `./gradlew :yeowool-market:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add yeowool-market/src/main/java/com/yeowool/market/merchant/MerchantRules.java yeowool-market/src/test/java/com/yeowool/market/merchant/MerchantRulesTest.java yeowool-market/src/main/java/com/yeowool/market/merchant/MerchantRepository.java
git commit -m "Add wandering merchant rules and shared state repository

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: MerchantService, /떠돌이상인, wiring

**Files:**
- Create: `yeowool-market/src/main/java/com/yeowool/market/merchant/MerchantService.java`
- Create: `yeowool-market/src/main/java/com/yeowool/market/merchant/MerchantCommand.java`
- Modify: `yeowool-market/src/main/java/com/yeowool/market/YeowoolMarket.java`
- Modify: `yeowool-market/src/main/resources/config.yml` (append)
- Modify: `yeowool-market/src/main/resources/messages.yml` (append)
- Modify: `yeowool-market/src/main/resources/plugin.yml` (add command)

**Interfaces:**
- Consumes: Task 4 `MerchantRules`, `MerchantRepository` (+ `Spot`, `State`). Market: `ShopOpenGate.allows(Player, String) -> boolean`, `new NPCShopGui(JavaPlugin, YeowoolCoreAPI, MessageService, Map<String, ShopDefinition>, ShopDefinition, ShopRotationManager, int page).open(Player)`, `ShopDefinition.id()`. Citizens: `CitizensAPI.createAnonymousNPCRegistry(NPCDataStore) -> NPCRegistry`, `new MemoryNPCDataStore()`, `NPCRegistry.createNPC(EntityType, String) -> NPC`, `NPC.spawn(Location) -> boolean`, `NPC.destroy()`, `NPCRightClickEvent.getNPC()/getClicker()`. Core: `MessageService.broadcast(String, TagResolver...)`, `DurationFormat.humanize(long)`.
- Produces: `MerchantService.tick()` (executor thread), `current()`, `settings()`, `repository()`, `removeNpc()` (main thread); `MerchantService.Settings.serverName(String id)`.

- [ ] **Step 1: Create `MerchantService.java`**

```java
package com.yeowool.market.merchant;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.market.npcshop.NPCShopGui;
import com.yeowool.market.npcshop.ShopDefinition;
import com.yeowool.market.npcshop.ShopOpenGate;
import com.yeowool.market.npcshop.ShopRotationManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;

/**
 * Drives the one wandering merchant shared by all servers. Every server runs
 * {@link #tick()} each minute: it advances the shared state when a spawn or
 * despawn is due (only one server's conditional UPDATE wins), then on the
 * main thread keeps a Citizens NPC standing here only while the merchant's
 * spot is on this server, and announces every state change once. NPCs live
 * in an anonymous in-memory registry, so nothing is saved to Citizens'
 * saves.yml and a restart simply respawns it on the next tick.
 */
public final class MerchantService implements Listener {

    public record Settings(String thisServerId, String shopId, String npcName, EntityType entityType, int stayMinutes,
                           int intervalMinMinutes, int intervalMaxMinutes, Map<String, String> serverNames) {

        public String serverName(String serverId) {
            return serverNames.getOrDefault(serverId, serverId);
        }
    }

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final MerchantRepository repository;
    private final Settings settings;
    private final Map<String, ShopDefinition> shops;
    private final ShopRotationManager rotationManager;
    private final Random random = new Random();
    private volatile boolean warnedNoSpots;

    // main thread only
    private MerchantRepository.State current;
    private long announcedSeq = -1;
    private NPCRegistry registry;
    private NPC npc;
    private long npcSeq = -1;
    private long warnedWorldSeq = -1;

    public MerchantService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, MerchantRepository repository,
                           Settings settings, Map<String, ShopDefinition> shops, ShopRotationManager rotationManager) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.settings = settings;
        this.shops = shops;
        this.rotationManager = rotationManager;
    }

    public Settings settings() {
        return settings;
    }

    public MerchantRepository repository() {
        return repository;
    }

    /** Main thread: the last state this server saw, null until the first tick finishes. */
    public MerchantRepository.State current() {
        return current;
    }

    /** Executor thread: advance the shared state if a transition is due, then apply the latest state on the main thread. */
    public void tick() {
        try {
            long now = System.currentTimeMillis();
            MerchantRepository.State state = repository.state();
            if (!state.active() && now >= state.nextSpawnAt()) {
                Optional<MerchantRepository.Spot> spot = MerchantRules.pick(repository.spots(), random);
                if (spot.isEmpty()) {
                    if (!warnedNoSpots) {
                        warnedNoSpots = true;
                        plugin.getLogger().warning("떠돌이 상인 후보 지점이 없습니다 — /떠돌이상인 위치추가 <이름>으로 등록하세요.");
                    }
                } else {
                    warnedNoSpots = false;
                    repository.spawn(state.seq(), spot.get(), now + settings.stayMinutes() * 60_000L);
                    state = repository.state();
                }
            } else if (state.active() && now >= state.despawnAt()) {
                long next = now + MerchantRules.nextDelayMillis(random, settings.intervalMinMinutes(), settings.intervalMaxMinutes());
                repository.despawn(state.seq(), next);
                state = repository.state();
            }
            MerchantRepository.State latest = state;
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> apply(latest));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "떠돌이 상인 상태 처리 실패", e);
        }
    }

    private void apply(MerchantRepository.State state) {
        if (current != null && state.seq() < current.seq()) {
            return; // an older read finished after a newer one
        }
        current = state;
        boolean here = state.active() && settings.thisServerId().equals(state.spot().serverId());
        if (!here || npcSeq != state.seq()) {
            removeNpc();
        }
        if (here && npc == null) {
            spawnNpc(state);
        }
        if (announcedSeq < 0) {
            announcedSeq = state.seq(); // first read after startup: whatever happened before isn't news
        } else if (state.seq() != announcedSeq) {
            announcedSeq = state.seq();
            announce(state);
        }
    }

    private void spawnNpc(MerchantRepository.State state) {
        MerchantRepository.Spot spot = state.spot();
        World world = Bukkit.getWorld(spot.world());
        if (world == null) {
            if (warnedWorldSeq != state.seq()) {
                warnedWorldSeq = state.seq();
                plugin.getLogger().warning("떠돌이 상인 지점 '" + spot.name() + "'의 월드 '" + spot.world() + "'를 찾을 수 없어 소환하지 못했습니다.");
            }
            return;
        }
        if (registry == null) {
            registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
        }
        npc = registry.createNPC(settings.entityType(), settings.npcName());
        npc.spawn(new Location(world, spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch()));
        npcSeq = state.seq();
    }

    /** Main thread. Also called from onDisable. */
    public void removeNpc() {
        if (npc != null) {
            npc.destroy();
            npc = null;
        }
        npcSeq = -1;
    }

    private void announce(MerchantRepository.State state) {
        if (!state.active()) {
            messages.broadcast("merchant.left");
            return;
        }
        long minutes = Math.max(1, (state.despawnAt() - System.currentTimeMillis() + 59_999) / 60_000);
        messages.broadcast("merchant.appeared",
                Placeholder.unparsed("server", settings.serverName(state.spot().serverId())),
                Placeholder.unparsed("spot", state.spot().name()),
                Placeholder.unparsed("minutes", String.valueOf(minutes)));
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        if (npc == null || event.getNPC() != npc) {
            return;
        }
        Player player = event.getClicker();
        ShopDefinition shop = shops.get(settings.shopId());
        if (shop == null) {
            messages.send(player, "merchant.shop-missing");
            return;
        }
        if (ShopOpenGate.allows(player, shop.id())) {
            new NPCShopGui(plugin, core, messages, shops, shop, rotationManager, 0).open(player);
        }
    }
}
```

- [ ] **Step 2: Create `MerchantCommand.java`**

```java
package com.yeowool.market.merchant;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
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

/**
 * {@code /떠돌이상인}: anyone sees where the merchant is; staff
 * ({@code yeowool.event.manage}) manage candidate spots and can force it in
 * or out. DB work runs on the executor, replies come back on the main thread.
 */
public final class MerchantCommand implements CommandExecutor, TabCompleter {

    @FunctionalInterface
    private interface SqlTask {
        void run() throws SQLException;
    }

    private static final String ADMIN = "yeowool.event.manage";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9가-힣_]{1,32}");
    private static final List<String> SUBCOMMANDS = List.of("위치추가", "위치제거", "위치목록", "소환", "퇴장");

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final MerchantService service;
    private final Executor executor;

    public MerchantCommand(JavaPlugin plugin, MessageService messages, MerchantService service, Executor executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            status(sender);
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "merchant.no-permission");
            return true;
        }
        switch (args[0]) {
            case "위치추가" -> addSpot(sender, args);
            case "위치제거" -> removeSpot(sender, args);
            case "위치목록" -> listSpots(sender);
            case "소환" -> summon(sender);
            case "퇴장" -> dismiss(sender);
            default -> messages.send(sender, "merchant.usage");
        }
        return true;
    }

    private void status(CommandSender sender) {
        MerchantRepository.State state = service.current();
        if (state == null || !state.active()) {
            messages.send(sender, "merchant.none");
            return;
        }
        MerchantRepository.Spot spot = state.spot();
        messages.send(sender, "merchant.status",
                Placeholder.unparsed("server", service.settings().serverName(spot.serverId())),
                Placeholder.unparsed("spot", spot.name()),
                Placeholder.unparsed("x", String.valueOf((long) Math.floor(spot.x()))),
                Placeholder.unparsed("y", String.valueOf((long) Math.floor(spot.y()))),
                Placeholder.unparsed("z", String.valueOf((long) Math.floor(spot.z()))),
                Placeholder.unparsed("remaining", DurationFormat.humanize(Math.max(0, state.despawnAt() - System.currentTimeMillis()))));
    }

    private void addSpot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (args.length < 2 || !NAME.matcher(args[1]).matches()) {
            messages.send(sender, "merchant.invalid-name");
            return;
        }
        Location loc = player.getLocation();
        MerchantRepository.Spot spot = new MerchantRepository.Spot(args[1], service.settings().thisServerId(),
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        async(sender, () -> {
            service.repository().saveSpot(spot);
            reply(sender, "merchant.spot-added", Placeholder.unparsed("name", spot.name()));
        });
    }

    private void removeSpot(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "merchant.usage");
            return;
        }
        String name = args[1];
        async(sender, () -> reply(sender, service.repository().deleteSpot(name) ? "merchant.spot-removed" : "merchant.spot-not-found",
                Placeholder.unparsed("name", name)));
    }

    private void listSpots(CommandSender sender) {
        async(sender, () -> {
            List<MerchantRepository.Spot> spots = service.repository().spots();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (spots.isEmpty()) {
                    messages.send(sender, "merchant.spot-list-empty");
                    return;
                }
                for (MerchantRepository.Spot spot : spots) {
                    messages.send(sender, "merchant.spot-line",
                            Placeholder.unparsed("name", spot.name()),
                            Placeholder.unparsed("server", service.settings().serverName(spot.serverId())),
                            Placeholder.unparsed("world", spot.world()),
                            Placeholder.unparsed("x", String.valueOf((long) Math.floor(spot.x()))),
                            Placeholder.unparsed("y", String.valueOf((long) Math.floor(spot.y()))),
                            Placeholder.unparsed("z", String.valueOf((long) Math.floor(spot.z()))));
                }
            });
        });
    }

    private void summon(CommandSender sender) {
        async(sender, () -> {
            if (service.repository().spots().isEmpty()) {
                reply(sender, "merchant.no-spots");
            } else if (service.repository().requestSpawnNow()) {
                service.tick();
                reply(sender, "merchant.summoned");
            } else {
                reply(sender, "merchant.already-active");
            }
        });
    }

    private void dismiss(CommandSender sender) {
        async(sender, () -> {
            if (service.repository().requestDespawnNow()) {
                service.tick();
                reply(sender, "merchant.dismissed");
            } else {
                reply(sender, "merchant.none");
            }
        });
    }

    private void async(CommandSender sender, SqlTask task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "떠돌이 상인 명령 처리 실패", e);
                reply(sender, "merchant.error");
            }
        });
    }

    private void reply(CommandSender sender, String key, TagResolver... placeholders) {
        Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key, placeholders));
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

- [ ] **Step 2b: Verify `NPCShopGui` constructor and `ShopDefinition.id()`**

Run: `grep -n "public NPCShopGui(" yeowool-market/src/main/java/com/yeowool/market/npcshop/NPCShopGui.java` and `grep -n "record ShopDefinition\|String id()" yeowool-market/src/main/java/com/yeowool/market/npcshop/ShopDefinition.java`
Expected: constructor `(JavaPlugin, YeowoolCoreAPI, MessageService, Map<String, ShopDefinition>, ShopDefinition, ShopRotationManager, int)` and an `id()` accessor (both already used by `CitizensShopListener`). If the parameter types differ, match `CitizensShopListener`'s call exactly.

- [ ] **Step 3: Wire into `YeowoolMarket.java`**

Add imports:

```java
import com.yeowool.market.merchant.MerchantCommand;
import com.yeowool.market.merchant.MerchantRepository;
import com.yeowool.market.merchant.MerchantRules;
import com.yeowool.market.merchant.MerchantService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;
```

Add a field next to `executor`:

```java
    private MerchantService merchantService;
```

Insert immediately after the `/상점이동` block (right after the `if (thisServerId.equals(npcServerId)) { ... }` block, before `// 직접 거래`), so `thisServerId`, `shops`, `rotationManager` are in scope:

```java
        // 떠돌이 상인 — Citizens가 있는 서버에서만 (NPC가 필요함). 실패해도 나머지 기능은 그대로.
        if (getConfig().getBoolean("wandering-merchant.enabled", true)) {
            if (getServer().getPluginManager().isPluginEnabled("Citizens")) {
                try {
                    enableWanderingMerchant(core, messages, shops, rotationManager, thisServerId);
                } catch (Exception e) {
                    getLogger().severe("떠돌이 상인 초기화 실패 — 떠돌이 상인을 끕니다: " + e.getMessage());
                }
            } else {
                getLogger().warning("Citizens가 없어 이 서버에서는 떠돌이 상인을 끕니다.");
            }
        }
```

Add this private method below `onEnable()`:

```java
    private void enableWanderingMerchant(YeowoolCoreAPI core, MessageManager messages, Map<String, ShopDefinition> shops,
                                         ShopRotationManager rotationManager, String thisServerId) throws java.sql.SQLException {
        EntityType entityType;
        String typeName = getConfig().getString("wandering-merchant.entity-type", "WANDERING_TRADER");
        try {
            entityType = EntityType.valueOf(typeName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            getLogger().warning("wandering-merchant.entity-type '" + typeName + "'을(를) 알 수 없어 WANDERING_TRADER를 씁니다.");
            entityType = EntityType.WANDERING_TRADER;
        }
        Map<String, String> serverNames = new HashMap<>();
        ConfigurationSection namesSection = getConfig().getConfigurationSection("wandering-merchant.server-names");
        if (namesSection != null) {
            for (String key : namesSection.getKeys(false)) {
                serverNames.put(key, namesSection.getString(key, key));
            }
        }
        MerchantService.Settings settings = new MerchantService.Settings(
                thisServerId,
                getConfig().getString("wandering-merchant.shop-id", "wandering_merchant"),
                getConfig().getString("wandering-merchant.npc-name", "&6떠돌이 상인"),
                entityType,
                getConfig().getInt("wandering-merchant.stay-minutes", 30),
                getConfig().getInt("wandering-merchant.interval-min-minutes", 180),
                getConfig().getInt("wandering-merchant.interval-max-minutes", 300),
                serverNames);
        MerchantRepository repository = new MerchantRepository(core.dataSource());
        repository.createTables(System.currentTimeMillis()
                + MerchantRules.nextDelayMillis(new Random(), settings.intervalMinMinutes(), settings.intervalMaxMinutes()));
        this.merchantService = new MerchantService(this, core, messages, repository, settings, shops, rotationManager);
        getServer().getPluginManager().registerEvents(merchantService, this);
        var merchantCommand = new MerchantCommand(this, messages, merchantService, executor);
        bindCommand("떠돌이상인", merchantCommand, merchantCommand);
        MerchantService service = merchantService;
        getServer().getScheduler().runTaskTimer(this, () -> executor.execute(service::tick), 20L * 10, 20L * 60);
    }
```

In `onDisable()`, before `if (executor != null)`:

```java
        if (merchantService != null) {
            merchantService.removeNpc();
        }
```

- [ ] **Step 4: Append to `config.yml`**

```yaml

# 떠돌이 상인 — 등록한 후보 지점(세 서버 어디든) 중 한 곳에 가끔 나타나 정해진 시간 동안 상점을 엽니다.
# 세 서버에 한 명만 존재하며, 세 서버 설정을 똑같이 맞춰주세요. 서버 식별은 위 npc-shop.this-server-id를 씁니다.
wandering-merchant:
  enabled: true
  # 우클릭하면 열리는 상점 ID — /상점생성으로 만든 상점 또는 위 npc-shop 상점 (로테이션 설정 추천)
  shop-id: "wandering_merchant"
  # NPC 이름 (Citizens 색상 코드 & 사용)
  npc-name: "&6떠돌이 상인"
  entity-type: WANDERING_TRADER
  # 한 번 나타나면 머무는 시간(분)
  stay-minutes: 30
  # 떠난 뒤 다음 등장까지 대기 시간(분) — 이 범위에서 무작위
  interval-min-minutes: 180
  interval-max-minutes: 300
  # 공지에 쓸 서버 표시 이름 (velocity.toml 서버 이름 → 표시 이름)
  server-names:
    lobby: "로비"
    town: "마을"
    wild: "야생"
```

- [ ] **Step 5: Append to `messages.yml`**

```yaml

merchant:
  appeared: "<gold>[떠돌이 상인]</gold> <yellow><server> '<spot>' 근처에 떠돌이 상인이 나타났습니다! (<minutes>분간)</yellow>"
  left: "<gold>[떠돌이 상인]</gold> <gray>떠돌이 상인이 떠났습니다.</gray>"
  none: "<gray>지금은 떠돌이 상인이 없습니다.</gray>"
  status: "<yellow>떠돌이 상인: <server> '<spot>' (<x>, <y>, <z>) — 남은 시간 <remaining></yellow>"
  shop-missing: "<red>떠돌이 상인의 상점이 아직 준비되지 않았습니다.</red>"
  usage: "<gray>/떠돌이상인 [위치추가 <이름> | 위치제거 <이름> | 위치목록 | 소환 | 퇴장]</gray>"
  no-permission: "<red>권한이 없습니다.</red>"
  invalid-name: "<red>지점 이름은 1~32자의 한글·영문·숫자·_ 만 쓸 수 있습니다.</red>"
  spot-added: "<green>후보 지점 '<name>'을(를) 지금 위치로 등록했습니다.</green>"
  spot-removed: "<green>후보 지점 '<name>'을(를) 제거했습니다.</green>"
  spot-not-found: "<red>'<name>' 지점을 찾을 수 없습니다.</red>"
  spot-list-empty: "<gray>등록된 후보 지점이 없습니다.</gray>"
  spot-line: "<gray>- <name>: <server> <world> (<x>, <y>, <z>)</gray>"
  no-spots: "<red>등록된 후보 지점이 없어 소환할 수 없습니다.</red>"
  summoned: "<green>떠돌이 상인을 소환했습니다.</green>"
  already-active: "<red>이미 떠돌이 상인이 나와 있습니다.</red>"
  dismissed: "<green>떠돌이 상인을 떠나보냈습니다.</green>"
  error: "<red>처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.</red>"
```

- [ ] **Step 6: Add command to `plugin.yml`** (after the `의뢰:` entry)

```yaml
  떠돌이상인:
    description: 떠돌이 상인의 현재 위치를 확인합니다 (관리진은 후보 지점 관리·소환·퇴장)
```

- [ ] **Step 7: Build**

Run: `./gradlew :yeowool-market:build`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add yeowool-market/src/main/java/com/yeowool/market/merchant/MerchantService.java yeowool-market/src/main/java/com/yeowool/market/merchant/MerchantCommand.java yeowool-market/src/main/java/com/yeowool/market/YeowoolMarket.java yeowool-market/src/main/resources/config.yml yeowool-market/src/main/resources/messages.yml yeowool-market/src/main/resources/plugin.yml
git commit -m "Add cross-server wandering merchant event

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

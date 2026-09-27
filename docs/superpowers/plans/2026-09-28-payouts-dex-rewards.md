# 공용 지급 장부 + 도감 완성 보상 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a core cross-server payout ledger (`core.payouts()`) for later features, and automatic dex-completion milestone rewards in `yeowool-life`.

**Architecture:** Core gets `PayoutService` backed by `yw_payouts` with a claim-by-DELETE claimer (same proven pattern as the market quest board). Life gets pure `DexRewardRules` (tested) and a `DexRewardService` that checks every online player each minute, flags granted milestones in PlayerData statistics, and feeds progress lines to the `/도감` menu.

**Tech Stack:** Paper 1.21.4, Java 21, MySQL via core `DataSource`, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-28-dex-competition-worldboss-mounts-design.md` (sections 0 and 1)

## Global Constraints

- Build from `C:/Users/apple/OneDrive/Desktop/YEOWOOL_PLUGIN`: `./gradlew :yeowool-core:build :yeowool-life:build`. OneDrive lock (`Unable to delete directory ...build/test-results/test/binary`) → `rm -rf yeowool-*/build/test-results/test/binary` and rerun.
- JDBC only on worker threads; Bukkit API, wallet, dispatchCommand on the main thread. Wallet changes only for a player online on this server, checked in the same tick.
- Player-visible text is Korean via `MessageService` keys (MiniMessage); user/config values via `Placeholder.unparsed`.
- Currency unit `온`, numbers `String.format("%,d", n)`.
- Commits: stage only the task's files (never `git add -A`/`.`, never `homepage-plan.md`), never `--amend`, message ends with a blank line then exactly `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Do not dispatch subagents.

---

### Task 1: Core payout ledger (`core.payouts()`)

**Files:**
- Create: `yeowool-core/src/main/java/com/yeowool/core/api/service/PayoutService.java`
- Create: `yeowool-core/src/main/java/com/yeowool/core/data/repository/PayoutRepository.java`
- Create: `yeowool-core/src/main/java/com/yeowool/core/payout/PayoutManager.java`
- Modify: `yeowool-core/src/main/java/com/yeowool/core/api/YeowoolCoreAPI.java`
- Modify: `yeowool-core/src/main/java/com/yeowool/core/YeowoolCoreAPIImpl.java`
- Modify: `yeowool-core/src/main/java/com/yeowool/core/database/SchemaInitializer.java`
- Modify: `yeowool-core/src/main/java/com/yeowool/core/YeowoolCore.java`
- Modify: `yeowool-core/src/main/resources/messages.yml`

**Interfaces:**
- Produces: `YeowoolCoreAPI.payouts() -> PayoutService`; `PayoutService.enqueue(UUID player, long amount, String sourcePlugin, String reason) throws SQLException` (worker thread only; amount ≤ 0 ignored); `PayoutService.claimNow(UUID player)` (main thread).

- [ ] **Step 1: Create `PayoutService.java`**

```java
package com.yeowool.core.api.service;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Money owed to a player who may be offline or on another server. Rows go
 * into {@code yw_payouts}; whichever server the player is online on credits
 * them (shortly after join, every minute, or on {@link #claimNow}), so a
 * wallet is never written from a server that doesn't hold the player.
 */
public interface PayoutService {

    /** Records a payout. Blocking JDBC — call from a worker thread only. Amounts ≤ 0 are ignored. */
    void enqueue(UUID player, long amount, String sourcePlugin, String reason) throws SQLException;

    /** Main thread: pays out anything pending for {@code player} right away if they're online on this server. */
    void claimNow(UUID player);
}
```

- [ ] **Step 2: Add the table to `SchemaInitializer.java`** — append one more text block to the `DDL` list (after the `yw_punishments` block, separated by a comma):

```java
            """
            CREATE TABLE IF NOT EXISTS yw_payouts (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                player CHAR(36) NOT NULL,
                amount BIGINT NOT NULL,
                source VARCHAR(32) NOT NULL,
                reason VARCHAR(128) NOT NULL,
                created_at BIGINT NOT NULL,
                INDEX idx_player (player)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
```

- [ ] **Step 3: Create `PayoutRepository.java`**

```java
package com.yeowool.core.data.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PayoutRepository {

    public record Payout(long id, UUID player, long amount, String source, String reason) {
    }

    private final DataSource dataSource;

    public PayoutRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(UUID player, long amount, String source, String reason) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_payouts (player, amount, source, reason, created_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, player.toString());
            ps.setLong(2, amount);
            ps.setString(3, truncate(source, 32));
            ps.setString(4, truncate(reason, 128));
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<Payout> pending(UUID player) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, player, amount, source, reason FROM yw_payouts WHERE player = ? ORDER BY id")) {
            ps.setString(1, player.toString());
            List<Payout> payouts = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payouts.add(new Payout(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getLong(3), rs.getString(4), rs.getString(5)));
                }
            }
            return payouts;
        }
    }

    /** True only for the one caller whose DELETE removed the row — that caller owns the payout. */
    public boolean delete(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM yw_payouts WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
```

- [ ] **Step 4: Create `PayoutManager.java`**

```java
package com.yeowool.core.payout;

import com.yeowool.core.api.service.EconomyDataService;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.api.service.PayoutService;
import com.yeowool.core.data.repository.PayoutRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * {@link PayoutService} implementation. A pending row is claimed by deleting
 * it (only one server's DELETE wins), then credited on the main thread if the
 * player is still online here; otherwise the row is written back for their
 * next server. Claimed-but-unpaid batches are tracked so {@link #restoreUnpaid}
 * can put them back when the plugin shuts down mid-claim.
 */
public final class PayoutManager implements PayoutService, Listener {

    private final JavaPlugin plugin;
    private final PayoutRepository repository;
    private final EconomyDataService economy;
    private final MessageService messages;
    private final Executor executor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<PayoutRepository.Payout>> claimedUnpaid = new ConcurrentHashMap<>();

    public PayoutManager(JavaPlugin plugin, PayoutRepository repository, EconomyDataService economy,
                         MessageService messages, Executor executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.economy = economy;
        this.messages = messages;
        this.executor = executor;
    }

    @Override
    public void enqueue(UUID player, long amount, String sourcePlugin, String reason) throws SQLException {
        if (amount <= 0) {
            return;
        }
        repository.insert(player, amount, sourcePlugin, reason);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> claimNow(uuid), 60L);
    }

    /** Main thread, every minute. */
    public void claimAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            claimNow(player.getUniqueId());
        }
    }

    @Override
    public void claimNow(UUID uuid) {
        if (Bukkit.getPlayer(uuid) == null || !inFlight.add(uuid)) {
            return;
        }
        executor.execute(() -> {
            List<PayoutRepository.Payout> claimed = new ArrayList<>();
            try {
                for (PayoutRepository.Payout payout : repository.pending(uuid)) {
                    if (repository.delete(payout.id())) {
                        claimed.add(payout);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "지급 장부 조회 실패 (" + uuid + ")", e);
            }
            if (!claimed.isEmpty()) {
                claimedUnpaid.put(uuid, claimed);
            }
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    inFlight.remove(uuid);
                    List<PayoutRepository.Payout> batch = claimedUnpaid.remove(uuid);
                    if (batch != null) {
                        credit(uuid, batch);
                    }
                });
            } else {
                inFlight.remove(uuid); // restoreUnpaid() in onDisable puts the batch back
            }
        });
    }

    private void credit(UUID uuid, List<PayoutRepository.Payout> batch) {
        Player player = Bukkit.getPlayer(uuid);
        List<PayoutRepository.Payout> unpaid = new ArrayList<>();
        for (PayoutRepository.Payout payout : batch) {
            if (player != null && economy.modifyBalance(uuid, payout.amount(), payout.source(), payout.reason())) {
                messages.send(player, "payout.received",
                        Placeholder.unparsed("reason", payout.reason()),
                        Placeholder.unparsed("amount", String.format("%,d", payout.amount())));
            } else {
                unpaid.add(payout);
            }
        }
        if (!unpaid.isEmpty()) {
            executor.execute(() -> restore(unpaid));
        }
    }

    /** Main thread, from onDisable after the executor drained: puts back every claimed-but-unpaid payout. */
    public void restoreUnpaid() {
        for (UUID uuid : List.copyOf(claimedUnpaid.keySet())) {
            List<PayoutRepository.Payout> batch = claimedUnpaid.remove(uuid);
            if (batch != null) {
                restore(batch);
            }
        }
    }

    private void restore(List<PayoutRepository.Payout> payouts) {
        for (PayoutRepository.Payout payout : payouts) {
            try {
                repository.insert(payout.player(), payout.amount(), payout.source(), payout.reason());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "지급 장부 복구 실패 — 수동 지급 필요: " + payout.player()
                        + " " + payout.amount() + "온 (" + payout.reason() + ")", e);
            }
        }
    }
}
```

- [ ] **Step 5: Expose it on the API**

`YeowoolCoreAPI.java`: add import `com.yeowool.core.api.service.PayoutService;` and, after `PunishmentService punishments();`:

```java
    /** Cross-server money payouts for players who may be offline or elsewhere — see {@link PayoutService}. */
    PayoutService payouts();
```

`YeowoolCoreAPIImpl.java`: add import, a field `private final PayoutService payoutService;`, a constructor parameter `PayoutService payoutService` placed right after `PunishmentService punishmentService` (assign it), and:

```java
    @Override
    public PayoutService payouts() {
        return payoutService;
    }
```

- [ ] **Step 6: Wire in `YeowoolCore.java`**

Add imports `com.yeowool.core.data.repository.PayoutRepository`, `com.yeowool.core.payout.PayoutManager`, `java.util.concurrent.TimeUnit`. Add a field `private PayoutManager payoutManager;`.

In `onEnable`, right after `this.soundManager = new SoundManager(this);` add:

```java
        this.payoutManager = new PayoutManager(this, new PayoutRepository(databaseManager.getDataSource()),
                economyDataService, messageManager, databaseManager.getExecutor());
```

Pass `payoutManager` to the `YeowoolCoreAPIImpl` constructor right after `punishmentManager,`. After the `MailboxJoinListener` registration add:

```java
        getServer().getPluginManager().registerEvents(payoutManager, this);
        getServer().getScheduler().runTaskTimer(this, payoutManager::claimAllOnline, 20L * 60, 20L * 60);
```

Replace `onDisable` with:

```java
    @Override
    public void onDisable() {
        if (playerDataService != null) {
            playerDataService.saveAll().join();
        }
        if (databaseManager != null && payoutManager != null) {
            var executor = databaseManager.getExecutor();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    getLogger().warning("DB 작업이 5초 안에 끝나지 않았습니다 — 일부 작업이 중단되었을 수 있습니다.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            payoutManager.restoreUnpaid();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("YeowoolCore가 비활성화되었습니다.");
    }
```

(If `getExecutor()` returns a type without `awaitTermination`, check `DatabaseManager` — it returns `ExecutorService`.)

- [ ] **Step 7: `messages.yml`** — append:

```yaml

payout:
  received: "<green><reason>: <amount>온을 받았습니다.</green>"
```

- [ ] **Step 8: Build** — `./gradlew :yeowool-core:build` → BUILD SUCCESSFUL. Then also `./gradlew build` for the whole project (every module compiles against `YeowoolCoreAPI`; none implement it, so they should still compile) → BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add yeowool-core/src/main/java/com/yeowool/core/api/service/PayoutService.java yeowool-core/src/main/java/com/yeowool/core/data/repository/PayoutRepository.java yeowool-core/src/main/java/com/yeowool/core/payout/PayoutManager.java yeowool-core/src/main/java/com/yeowool/core/api/YeowoolCoreAPI.java yeowool-core/src/main/java/com/yeowool/core/YeowoolCoreAPIImpl.java yeowool-core/src/main/java/com/yeowool/core/database/SchemaInitializer.java yeowool-core/src/main/java/com/yeowool/core/YeowoolCore.java yeowool-core/src/main/resources/messages.yml
git commit -m "Add core cross-server payout ledger

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: DexRewardRules + tests

**Files:**
- Create: `yeowool-life/src/main/java/com/yeowool/life/dex/DexRewardRules.java`
- Test: `yeowool-life/src/test/java/com/yeowool/life/dex/DexRewardRulesTest.java`

**Interfaces:**
- Produces: `DexRewardRules.percent(int owned, int total) -> int` (floor; total ≤ 0 → 0), `nextMilestone(int percent, List<Integer> sortedMilestones) -> int` (-1 if none), `remainingFor(int milestone, int owned, int total) -> int`.

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.life.dex;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DexRewardRulesTest {

    private static final List<Integer> MILESTONES = List.of(25, 50, 75, 100);

    @Test
    void percentIsFlooredAndSafeForEmptyCategories() {
        assertEquals(57, DexRewardRules.percent(4, 7));
        assertEquals(100, DexRewardRules.percent(7, 7));
        assertEquals(99, DexRewardRules.percent(99, 100));
        assertEquals(0, DexRewardRules.percent(0, 0));
    }

    @Test
    void nextMilestoneIsTheFirstNotYetReached() {
        assertEquals(25, DexRewardRules.nextMilestone(0, MILESTONES));
        assertEquals(50, DexRewardRules.nextMilestone(25, MILESTONES));
        assertEquals(100, DexRewardRules.nextMilestone(99, MILESTONES));
        assertEquals(-1, DexRewardRules.nextMilestone(100, MILESTONES));
    }

    @Test
    void remainingForMatchesPercentThreshold() {
        assertEquals(1, DexRewardRules.remainingFor(50, 3, 7));
        assertEquals(0, DexRewardRules.remainingFor(50, 4, 7));
        assertEquals(2, DexRewardRules.remainingFor(100, 5, 7));
        assertEquals(1, DexRewardRules.remainingFor(25, 0, 3));
        for (int total = 1; total <= 40; total++) {
            for (int owned = 0; owned <= total; owned++) {
                for (int milestone : MILESTONES) {
                    boolean reached = DexRewardRules.percent(owned, total) >= milestone;
                    assertEquals(reached, DexRewardRules.remainingFor(milestone, owned, total) == 0,
                            "owned=" + owned + " total=" + total + " milestone=" + milestone);
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run** `./gradlew :yeowool-life:test --tests "com.yeowool.life.dex.DexRewardRulesTest"` → FAIL (class missing).

- [ ] **Step 3: Create `DexRewardRules.java`**

```java
package com.yeowool.life.dex;

import java.util.List;

/** Dex completion math, kept free of Bukkit so it can be unit tested. */
public final class DexRewardRules {

    private DexRewardRules() {
    }

    /** floor(owned × 100 / total); an empty category is 0% so it never pays out. */
    public static int percent(int owned, int total) {
        if (total <= 0) {
            return 0;
        }
        return (int) ((long) Math.max(0, owned) * 100 / total);
    }

    /** First milestone above {@code percent} in an ascending list, or -1 when all are reached. */
    public static int nextMilestone(int percent, List<Integer> sortedMilestones) {
        for (int milestone : sortedMilestones) {
            if (percent < milestone) {
                return milestone;
            }
        }
        return -1;
    }

    /** Entries still needed so that {@link #percent} reaches {@code milestone}: ceil(milestone × total / 100) − owned, never negative. */
    public static int remainingFor(int milestone, int owned, int total) {
        long needed = ((long) milestone * total + 99) / 100;
        return (int) Math.max(0, needed - owned);
    }
}
```

- [ ] **Step 4: Run** the same test → PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add yeowool-life/src/main/java/com/yeowool/life/dex/DexRewardRules.java yeowool-life/src/test/java/com/yeowool/life/dex/DexRewardRulesTest.java
git commit -m "Add dex completion reward rules

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: DexRewardService, `/도감` progress, wiring

**Files:**
- Create: `yeowool-life/src/main/java/com/yeowool/life/dex/DexRewardService.java`
- Modify: `yeowool-life/src/main/java/com/yeowool/life/dex/DexCommand.java`
- Modify: `yeowool-life/src/main/java/com/yeowool/life/dex/DexMenuGui.java`
- Modify: `yeowool-life/src/main/java/com/yeowool/life/YeowoolLife.java`
- Modify: `yeowool-life/src/main/resources/config.yml`, `yeowool-life/src/main/resources/messages.yml`

**Interfaces:**
- Consumes: Task 2 `DexRewardRules`; `FishRarity(String name, int weight, NamedTextColor color, List<FishSpecies> species)`, `FishSpecies.statisticKey()`, `DexEntry(String id, String display, Material icon)`, `CustomFishingBridge.buildRarity() -> FishRarity`, `core.playerData().getIfLoaded(UUID) -> Optional<PlayerData>`, `PlayerData.getStatistic(String) -> long`, `PlayerData.addStatistic(String, long)`.
- Produces: `DexRewardService.checkAll()` (main), `check(Player)` (main), `progressFor(Player) -> Map<String, Progress>` keyed `fishing|mining|hunting|farming`; `record Progress(int owned, int total, int percent, int nextMilestone, int remaining)`; `record Milestone(int percent, long money, List<String> commands)`.

- [ ] **Step 1: Create `DexRewardService.java`**

```java
package com.yeowool.life.dex;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.fishing.FishRarity;
import com.yeowool.life.fishing.FishSpecies;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Pays dex completion milestones (e.g. 25/50/75/100% of a category) once per
 * player. Checked for every online player each minute and shortly after join;
 * a granted milestone is flagged in PlayerData statistics
 * ({@code dex.reward.<category>.<percent>}) so it is never paid twice, and a
 * player already past several milestones gets them all on the next check.
 */
public final class DexRewardService {

    public record Milestone(int percent, long money, List<String> commands) {
    }

    public record Progress(int owned, int total, int percent, int nextMilestone, int remaining) {
    }

    /** One dex category: config/flag key, Korean label, and the statistic keys counted as its entries. */
    private record Category(String key, String label, List<String> statKeys) {
    }

    private static final String SOURCE = "YeowoolLife";

    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final List<Milestone> milestones;
    private final List<Integer> milestonePercents;
    private final Supplier<List<FishRarity>> fishRarities;
    private final List<DexEntry> mining;
    private final List<DexEntry> hunting;
    private final List<DexEntry> farming;

    public DexRewardService(YeowoolCoreAPI core, MessageService messages, List<Milestone> milestones,
                            Supplier<List<FishRarity>> fishRarities, List<DexEntry> mining, List<DexEntry> hunting, List<DexEntry> farming) {
        this.core = core;
        this.messages = messages;
        this.milestones = milestones.stream().sorted((a, b) -> Integer.compare(a.percent(), b.percent())).toList();
        this.milestonePercents = this.milestones.stream().map(Milestone::percent).toList();
        this.fishRarities = fishRarities;
        this.mining = mining;
        this.hunting = hunting;
        this.farming = farming;
    }

    /** Main thread. */
    public void checkAll() {
        List<Category> categories = categories();
        for (Player player : Bukkit.getOnlinePlayers()) {
            check(player, categories);
        }
    }

    /** Main thread. */
    public void check(Player player) {
        check(player, categories());
    }

    /** Main thread: progress per category key (fishing/mining/hunting/farming); empty if the player's data isn't loaded. */
    public Map<String, Progress> progressFor(Player player) {
        Map<String, Progress> result = new LinkedHashMap<>();
        Optional<PlayerData> data = core.playerData().getIfLoaded(player.getUniqueId());
        if (data.isEmpty()) {
            return result;
        }
        for (Category category : categories()) {
            result.put(category.key(), progress(data.get(), category));
        }
        return result;
    }

    private void check(Player player, List<Category> categories) {
        Optional<PlayerData> loaded = core.playerData().getIfLoaded(player.getUniqueId());
        if (loaded.isEmpty()) {
            return;
        }
        PlayerData data = loaded.get();
        for (Category category : categories) {
            int percent = progress(data, category).percent();
            for (Milestone milestone : milestones) {
                if (percent < milestone.percent()) {
                    break;
                }
                String flag = "dex.reward." + category.key() + "." + milestone.percent();
                if (data.getStatistic(flag) > 0) {
                    continue;
                }
                data.addStatistic(flag, 1);
                grant(player, category, milestone);
            }
        }
    }

    private void grant(Player player, Category category, Milestone milestone) {
        String reason = "도감 보상 (" + category.label() + " " + milestone.percent() + "%)";
        if (milestone.money() > 0) {
            core.economyData().modifyBalance(player.getUniqueId(), milestone.money(), SOURCE, reason);
        }
        for (String command : milestone.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                    .replace("{player}", player.getName())
                    .replace("{category}", category.label())
                    .replace("{category_key}", category.key()));
        }
        messages.send(player, "dex.reward",
                Placeholder.unparsed("category", category.label()),
                Placeholder.unparsed("percent", String.valueOf(milestone.percent())),
                Placeholder.unparsed("money", String.format("%,d", milestone.money())));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    private Progress progress(PlayerData data, Category category) {
        int total = category.statKeys().size();
        int owned = 0;
        for (String key : category.statKeys()) {
            if (data.getStatistic(key) > 0) {
                owned++;
            }
        }
        int percent = DexRewardRules.percent(owned, total);
        int next = DexRewardRules.nextMilestone(percent, milestonePercents);
        return new Progress(owned, total, percent, next, next < 0 ? 0 : DexRewardRules.remainingFor(next, owned, total));
    }

    private List<Category> categories() {
        List<String> fishKeys = new ArrayList<>();
        for (FishRarity rarity : fishRarities.get()) {
            for (FishSpecies species : rarity.species()) {
                fishKeys.add(species.statisticKey());
            }
        }
        return List.of(
                new Category("fishing", "물고기", fishKeys),
                new Category("mining", "광물", statKeys("dex.mining.", mining)),
                new Category("hunting", "사냥", statKeys("dex.hunting.", hunting)),
                new Category("farming", "작물", statKeys("dex.farming.", farming)));
    }

    private static List<String> statKeys(String prefix, List<DexEntry> entries) {
        return entries.stream().map(entry -> prefix + entry.id()).toList();
    }
}
```

- [ ] **Step 2: Rewrite `DexCommand.java`** (the fish rarity merge moves into a supplier built once in `YeowoolLife`, shared with the reward service):

```java
package com.yeowool.life.dex;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.fishing.FishRarity;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** {@code /도감} — opens {@link DexMenuGui}, the category picker for 물고기/광물/사냥/작물. */
public final class DexCommand implements CommandExecutor {

    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final Supplier<List<FishRarity>> fishRarities;
    private final List<DexEntry> mining;
    private final List<DexEntry> hunting;
    private final List<DexEntry> farming;
    private final int fishBackgroundOffsetPx;
    private final DexRewardService rewards;

    /**
     * {@code fishRarities} is re-read on every open (it merges CustomFishing's loot table, which may
     * not be loaded yet at enable time and can change on {@code /cfishing reload}); {@code rewards}
     * is null when dex rewards are disabled.
     */
    public DexCommand(YeowoolCoreAPI core, MessageService messages, Supplier<List<FishRarity>> fishRarities,
                      List<DexEntry> mining, List<DexEntry> hunting, List<DexEntry> farming, int fishBackgroundOffsetPx,
                      DexRewardService rewards) {
        this.core = core;
        this.messages = messages;
        this.fishRarities = fishRarities;
        this.mining = mining;
        this.hunting = hunting;
        this.farming = farming;
        this.fishBackgroundOffsetPx = fishBackgroundOffsetPx;
        this.rewards = rewards;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        Map<String, DexRewardService.Progress> progress = rewards == null ? Map.of() : rewards.progressFor(player);
        new DexMenuGui(core, fishRarities.get(), mining, hunting, farming, fishBackgroundOffsetPx, progress).open(player);
        return true;
    }
}
```

- [ ] **Step 3: Update `DexMenuGui.java`** — add a `Map<String, DexRewardService.Progress> progress` constructor parameter (last), pass the category key into each icon, and show progress lore. Replace the class body with:

```java
public final class DexMenuGui extends YeowoolGui {

    public DexMenuGui(YeowoolCoreAPI core, List<FishRarity> fishRarities, List<DexEntry> mining, List<DexEntry> hunting, List<DexEntry> farming,
                      int fishBackgroundOffsetPx, Map<String, DexRewardService.Progress> progress) {
        super(27, Component.text("여울 도감", NamedTextColor.DARK_AQUA));

        setButton(11, GuiButton.of(categoryIcon(Material.TROPICAL_FISH, "물고기 도감", progress.get("fishing")), event ->
                new FishCatalogGui(core, (Player) event.getWhoClicked(), fishRarities, 0, fishBackgroundOffsetPx).open((Player) event.getWhoClicked())));
        setButton(12, GuiButton.of(categoryIcon(Material.DIAMOND_ORE, "광물 도감", progress.get("mining")), event ->
                new DexCatalogGui(core, (Player) event.getWhoClicked(), "광물 도감", "dex.mining.", mining).open((Player) event.getWhoClicked())));
        setButton(14, GuiButton.of(categoryIcon(Material.ZOMBIE_HEAD, "사냥 도감", progress.get("hunting")), event ->
                new DexCatalogGui(core, (Player) event.getWhoClicked(), "사냥 도감", "dex.hunting.", hunting).open((Player) event.getWhoClicked())));
        setButton(15, GuiButton.of(categoryIcon(Material.WHEAT, "작물 도감", progress.get("farming")), event ->
                new DexCatalogGui(core, (Player) event.getWhoClicked(), "작물 도감", "dex.farming.", farming).open((Player) event.getWhoClicked())));
    }

    /** {@code progress} is null when rewards are off or the viewer's data isn't loaded — then the icon has no lore. */
    private ItemStack categoryIcon(Material material, String name, DexRewardService.Progress progress) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        if (progress != null) {
            meta.lore(List.of(
                    Component.text("수집 " + progress.owned() + "/" + progress.total() + " (" + progress.percent() + "%)", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    (progress.nextMilestone() < 0
                            ? Component.text("모든 보상 획득", NamedTextColor.GREEN)
                            : Component.text("다음 보상(" + progress.nextMilestone() + "%)까지 " + progress.remaining() + "종", NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false)));
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
```

Add imports `net.kyori.adventure.text.format.TextDecoration` and `java.util.Map`.

- [ ] **Step 4: Wire into `YeowoolLife.java`**

Add imports `com.yeowool.life.dex.DexRewardService`, `java.util.function.Supplier`, and `java.util.ArrayList` if missing (and `org.bukkit.configuration.ConfigurationSection` if missing). Replace this block:

```java
        var catalogCommand = getCommand("도감");
        if (catalogCommand != null) {
            int fishBackgroundOffset = config.getInt("fishing.gui-background-offset", -46);
            catalogCommand.setExecutor(new DexCommand(core, messages, fishRarities, customFishingEnabled, miningDex, huntingDex, farmingDex, fishBackgroundOffset));
        }
```

with:

```java
        Supplier<List<FishRarity>> fishRaritySupplier = () -> {
            if (!customFishingEnabled) {
                return fishRarities;
            }
            List<FishRarity> combined = new ArrayList<>(fishRarities);
            combined.add(CustomFishingBridge.buildRarity());
            return combined;
        };
        DexRewardService dexRewards = null;
        if (config.getBoolean("dex-rewards.enabled", true)) {
            List<DexRewardService.Milestone> milestones = new ArrayList<>();
            ConfigurationSection milestoneSection = config.getConfigurationSection("dex-rewards.milestones");
            if (milestoneSection != null) {
                for (String key : milestoneSection.getKeys(false)) {
                    try {
                        milestones.add(new DexRewardService.Milestone(Integer.parseInt(key),
                                milestoneSection.getLong(key + ".money", 0),
                                milestoneSection.getStringList(key + ".commands")));
                    } catch (NumberFormatException e) {
                        getLogger().warning("dex-rewards.milestones의 '" + key + "'는 숫자(퍼센트)여야 합니다 — 건너뜁니다.");
                    }
                }
            }
            DexRewardService service = new DexRewardService(core, messages, milestones, fishRaritySupplier, miningDex, huntingDex, farmingDex);
            getServer().getScheduler().runTaskTimer(this, service::checkAll, 20L * 60, 20L * 60);
            getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                    var player = event.getPlayer();
                    getServer().getScheduler().runTaskLater(YeowoolLife.this, () -> {
                        if (player.isOnline()) {
                            service.check(player);
                        }
                    }, 100L);
                }
            }, this);
            dexRewards = service;
        }
        var catalogCommand = getCommand("도감");
        if (catalogCommand != null) {
            int fishBackgroundOffset = config.getInt("fishing.gui-background-offset", -46);
            catalogCommand.setExecutor(new DexCommand(core, messages, fishRaritySupplier, miningDex, huntingDex, farmingDex, fishBackgroundOffset, dexRewards));
        }
```

(`fishRarities` must be effectively final for the lambda — it is assigned once at `List<FishRarity> fishRarities = loadRarities();`. If the compiler says otherwise, copy it into a `final List<FishRarity> baseFishRarities` first.)

- [ ] **Step 5: Append to `config.yml`**

```yaml

# 도감 완성 보상 — 물고기/광물/사냥/작물 도감마다 수집률이 아래 퍼센트를 넘으면 자동 지급(각 단계 한 번씩).
# 접속 5초 뒤와 1분마다 확인합니다. commands는 콘솔로 실행되며 {player}, {category}(한글), {category_key}(fishing/mining/hunting/farming) 치환.
# 예) commands: ["칭호 지급 {player} dex_{category_key}_master"]  (칭호는 /칭호생성으로 미리 만들어 두세요)
dex-rewards:
  enabled: true
  milestones:
    25:
      money: 5000
      commands: []
    50:
      money: 15000
      commands: []
    75:
      money: 30000
      commands: []
    100:
      money: 100000
      commands: []
```

- [ ] **Step 6: Append to `messages.yml`**

```yaml

dex:
  reward: "<gold>[도감]</gold> <yellow><category> 도감 <percent>% 달성! 보상으로 <money>온을 받았습니다.</yellow>"
```

(If a `dex:` section already exists in messages.yml, add the `reward` key inside it instead of a second `dex:` section.)

- [ ] **Step 7: Build** — `./gradlew :yeowool-life:build` → BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add yeowool-life/src/main/java/com/yeowool/life/dex/DexRewardService.java yeowool-life/src/main/java/com/yeowool/life/dex/DexCommand.java yeowool-life/src/main/java/com/yeowool/life/dex/DexMenuGui.java yeowool-life/src/main/java/com/yeowool/life/YeowoolLife.java yeowool-life/src/main/resources/config.yml yeowool-life/src/main/resources/messages.yml
git commit -m "Add dex completion milestone rewards and progress in /도감

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

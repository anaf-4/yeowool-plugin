# Boss Raid System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `yeowool-raid`, a party-scoped, admin-extensible boss raid framework: fixed instance-slot pools, Citizens NPC entry with ticket consumption, MythicMobs-driven win/lose judging, GUI-configured individual rewards delivered by mailbox.

**Architecture:** New Paper plugin module following the exact conventions of `yeowool-quest` (built earlier this session): a cached-definition `RaidManager` (small admin-defined dataset, fully cached, like `QuestManager`/`CouponManager`), MySQL-backed definitions/instance-slots via the shared `YeowoolCoreAPI.dataSource()`, in-memory-only session state (not persisted — matches Scrapyard's session model), Citizens for the entry NPC, and BetterHud relayed via the same reflection pattern `QuestDialogueService` already established (no compile dependency on BetterHud).

**Tech Stack:** Java 21, Paper 1.21.4 API, Gradle Kotlin DSL, MySQL/HikariCP (via `yeowool-core`), Citizens API, MythicMobs Bukkit API (`io.lumine.mythic.bukkit.events`), ItemsAdder API (`dev.lone.itemsadder.api.CustomStack`), JUnit 5 (pure-logic classes only — see Testing Convention below).

**Spec:** `docs/superpowers/specs/2026-09-17-boss-raid-system-design.md`

## Global Constraints

- New module name: `yeowool-raid`, package root `com.yeowool.raid`.
- Deploy target: lobby, town, and wild — every build/copy step in this plan targets all three `C:\YEOWOOL\{lobby,town,wild}\plugins\` directories (standing project rule).
- Definitions (`yw_raid_definition`, `yw_raid_instance`) are MySQL-backed and fully cached in memory on load (same trade-off as `QuestManager`/`CouponManager`). Sessions (`RaidSession`) are **in-memory only, never persisted** — a server restart during an active raid loses that session (matches Scrapyard's existing session model; documented in the spec as an accepted edge case).
- Party membership is read directly from `yw_party`/`yw_party_member` (yeowool-community's existing schema) via a **read-only raw-JDBC query in the raid module** — no Gradle dependency on `yeowool-community`, matching how `yeowool-quest` avoided a compile dependency on BetterHud.
- BetterHud is invoked only via reflection (`Class.forName("kr.toxicity.hud.api.bukkit.event.CustomPopupEvent")`), never as a compile or runtime hard dependency. Every call site must have a chat-message fallback.
- Ticket items are existing ItemsAdder custom items, identified by their ItemsAdder namespaced id string (`dev.lone.itemsadder.api.CustomStack#getNamespacedID()`), not a new custom voucher item type — the raid module never mints tickets, only checks/consumes them.
- Reward roll: each winning participant receives **exactly one item, chosen uniformly at random** from the admin-configured reward pool (a flat `List<ItemStack>` edited via the existing `ItemGridEditorGui`, identical storage format to coupons/cash packages via `ItemStackSerializer`).

## Testing Convention (adaptation note for this codebase)

This repo's existing test suite (`yeowool-*/src/test/java/...`) only unit-tests **pure logic classes with no Bukkit/JDBC dependency** — parsers, formatters, small calculators (e.g. `CouponDateParserTest`, `RtpConfigTest`). Repositories, listeners, commands, GUIs, and the main plugin class are never JUnit-tested in this codebase; they're verified by building, deploying to the three live servers, and exercising the feature in-game (exactly how `yeowool-quest` was verified this session). This plan follows that established split:

- **JUnit-tested (Task 3):** `RaidInstanceAllocator`, `RaidEntryValidator`, `RaidRewardRoller` — pure logic, zero Bukkit/JDBC imports.
- **Build + manual verify (all other tasks):** everything touching Bukkit, JDBC, Citizens, MythicMobs, or ItemsAdder APIs.

---

### Task 1: Module scaffold

**Files:**
- Modify: `settings.gradle.kts`
- Create: `yeowool-raid/build.gradle.kts`
- Create: `yeowool-raid/src/main/resources/plugin.yml`
- Create: `yeowool-raid/src/main/resources/config.yml`

**Interfaces:**
- Produces: a buildable, empty `yeowool-raid` module other tasks add classes into.

- [ ] **Step 1: Add the module to the build**

Edit `settings.gradle.kts`, add `"yeowool-raid"` to the `include(...)` list (same list `"yeowool-quest"` was added to earlier this session).

- [ ] **Step 2: Write `yeowool-raid/build.gradle.kts`**

```kotlin
dependencies {
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly(project(":yeowool-core"))
    compileOnly("net.citizensnpcs:citizensapi:${rootProject.property("citizensVersion")}")
    compileOnly("me.clip:placeholderapi:${rootProject.property("placeholderApiVersion")}")
    // MythicMobs and ItemsAdder: no reliable Maven artifacts either — drop the matching jars from
    // the live servers' plugins/ folder into libs/ before building (same pattern as yeowool-life's
    // AddCook/MCPets jars).
    compileOnly(files("libs/MythicMobs.jar"))
    compileOnly(files("libs/ItemsAdder.jar"))
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation(project(":yeowool-core"))
}
```

- [ ] **Step 3: Add the libs jars to `.gitignore`**

Append to `.gitignore` (same comment style as the existing `yeowool-life/libs/*.jar` entry):

```
yeowool-raid/libs/*.jar
```

- [ ] **Step 4: Copy the two jars from a live server into `yeowool-raid/libs/`**

```bash
mkdir -p "yeowool-raid/libs"
cp "C:/YEOWOOL/lobby/plugins/MythicMobs.jar" "yeowool-raid/libs/MythicMobs.jar"
cp "C:/YEOWOOL/lobby/plugins/ItemsAdder_4.0.17.jar" "yeowool-raid/libs/ItemsAdder.jar"
```

(Check the exact filenames under `C:\YEOWOOL\lobby\plugins\` first — ItemsAdder's jar name carries its version, e.g. `ItemsAdder_4.0.17.jar`; MythicMobs' may too.)

- [ ] **Step 5: Write `yeowool-raid/src/main/resources/plugin.yml`**

```yaml
name: YeowoolRaid
version: '${version}'
main: com.yeowool.raid.YeowoolRaid
api-version: '1.21'
author: Yeowool
description: 파티 단위 보스 레이드 (고정 인스턴스, 티켓 입장, MythicMobs 연동)
depend: [YeowoolCore, Citizens, MythicMobs]
softdepend: [PlaceholderAPI, BetterHud, ItemsAdder]

commands:
  레이드:
    description: 레이드 정의 생성/설정/삭제 (관리자, 시티즌 NPC 필요 없음)
    permission: yeowool.raid.manage
    default: op

permissions:
  yeowool.raid.manage:
    description: 레이드를 생성/수정/삭제할 수 있는 권한
    default: op
```

- [ ] **Step 6: Write `yeowool-raid/src/main/resources/config.yml`**

```yaml
betterhud:
  # BetterHud 설치 후 이 이름으로 팝업(YAML) 파일을 만들어주세요.
  boss-warning-popup: yeowool_raid_boss_warning
  result-popup: yeowool_raid_result
```

- [ ] **Step 7: Verify the empty module builds**

Run: `./gradlew.bat :yeowool-raid:build -x test --console=plain`
Expected: `BUILD SUCCESSFUL` (no source files yet, just an empty jar with the two resources).

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts yeowool-raid/build.gradle.kts yeowool-raid/src/main/resources/plugin.yml yeowool-raid/src/main/resources/config.yml .gitignore
git commit -m "Scaffold yeowool-raid module"
```

---

### Task 2: Data model (records + enums)

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidDefinition.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidInstanceSlot.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidSessionState.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidSession.java`

**Interfaces:**
- Produces: `RaidDefinition(long id, String name, String mythicMobId, String ticketItemId, int ticketAmount, int minPartySize, int maxPartySize, int timeLimitSeconds, int sharedLives, int instanceCount, List<ItemStack> rewardItems)` with `withRewardItems`/`withMythicMob`/`withTicket`/`withPartySize`/`withTimeLimit`/`withSharedLives`/`withInstanceCount` wither methods (mirrors `Quest`'s wither pattern).
- Produces: `RaidInstanceSlot(long id, long raidId, int slotIndex, Location entry, Location bossSpawn, Location exit, Location boundMin, Location boundMax)`.
- Produces: `RaidSessionState { IN_PROGRESS, WON, LOST }`.
- Produces: `RaidSession` (mutable): `getRaidId()`, `getSlotIndex()`, `getPartyMembers() -> Set<UUID>`, `getBossEntityId() -> UUID`, `getState()`/`setState(RaidSessionState)`, `getRemainingLives()`/`decrementLives() -> int`, `getStartedAtMillis()`, `isExpired(int timeLimitSeconds) -> boolean`.

- [ ] **Step 1: Write `RaidDefinition.java`**

```java
package com.yeowool.raid;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record RaidDefinition(
        long id,
        String name,
        int npcId,
        String mythicMobId,
        String ticketItemId,
        int ticketAmount,
        int minPartySize,
        int maxPartySize,
        int timeLimitSeconds,
        int sharedLives,
        int instanceCount,
        List<ItemStack> rewardItems
) {
    public RaidDefinition withNpcId(int npcId) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withMythicMob(String mythicMobId) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withTicket(String ticketItemId, int ticketAmount) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withPartySize(int minPartySize, int maxPartySize) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withTimeLimit(int timeLimitSeconds) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withSharedLives(int sharedLives) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withInstanceCount(int instanceCount) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }

    public RaidDefinition withRewardItems(List<ItemStack> rewardItems) {
        return new RaidDefinition(id, name, npcId, mythicMobId, ticketItemId, ticketAmount, minPartySize, maxPartySize, timeLimitSeconds, sharedLives, instanceCount, rewardItems);
    }
}
```

`npcId` defaults to `-1` (unset) at creation, matching `Quest`'s `npcId` field — the same established mechanism `yeowool-quest` uses to associate a Citizens NPC with admin-defined content (`QuestManager.byNpc(int)`), verified by reading `yeowool-quest`'s actual source this session rather than inventing a new Citizens-metadata-tagging mechanism (an earlier draft of this plan guessed at `NPC.data().has(...)`; this is the correction).

- [ ] **Step 2: Write `RaidInstanceSlot.java`**

```java
package com.yeowool.raid;

import org.bukkit.Location;

public record RaidInstanceSlot(
        long id,
        long raidId,
        int slotIndex,
        Location entry,
        Location bossSpawn,
        Location exit,
        Location boundMin,
        Location boundMax
) {
    public boolean contains(Location location) {
        if (!location.getWorld().equals(boundMin.getWorld())) {
            return false;
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= Math.min(boundMin.getX(), boundMax.getX()) && x <= Math.max(boundMin.getX(), boundMax.getX())
                && y >= Math.min(boundMin.getY(), boundMax.getY()) && y <= Math.max(boundMin.getY(), boundMax.getY())
                && z >= Math.min(boundMin.getZ(), boundMax.getZ()) && z <= Math.max(boundMin.getZ(), boundMax.getZ());
    }
}
```

- [ ] **Step 3: Write `RaidSessionState.java`**

```java
package com.yeowool.raid;

public enum RaidSessionState {
    IN_PROGRESS, WON, LOST
}
```

- [ ] **Step 4: Write `RaidSession.java`**

```java
package com.yeowool.raid;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One active raid run. In-memory only — never persisted, matching Scrapyard's session model. */
public final class RaidSession {

    private final long raidId;
    private final int slotIndex;
    private final Set<UUID> partyMembers;
    private final UUID bossEntityId;
    private final long startedAtMillis;
    private RaidSessionState state = RaidSessionState.IN_PROGRESS;
    private int remainingLives;

    public RaidSession(long raidId, int slotIndex, Set<UUID> partyMembers, UUID bossEntityId, int sharedLives) {
        this.raidId = raidId;
        this.slotIndex = slotIndex;
        this.partyMembers = new HashSet<>(partyMembers);
        this.bossEntityId = bossEntityId;
        this.remainingLives = sharedLives;
        this.startedAtMillis = System.currentTimeMillis();
    }

    public long getRaidId() {
        return raidId;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public Set<UUID> getPartyMembers() {
        return partyMembers;
    }

    public UUID getBossEntityId() {
        return bossEntityId;
    }

    public RaidSessionState getState() {
        return state;
    }

    public void setState(RaidSessionState state) {
        this.state = state;
    }

    public int getRemainingLives() {
        return remainingLives;
    }

    /** Returns the new remaining-lives count. */
    public int decrementLives() {
        remainingLives = Math.max(0, remainingLives - 1);
        return remainingLives;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public boolean isExpired(int timeLimitSeconds) {
        return System.currentTimeMillis() - startedAtMillis >= timeLimitSeconds * 1000L;
    }
}
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew.bat :yeowool-raid:compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/RaidDefinition.java yeowool-raid/src/main/java/com/yeowool/raid/RaidInstanceSlot.java yeowool-raid/src/main/java/com/yeowool/raid/RaidSessionState.java yeowool-raid/src/main/java/com/yeowool/raid/RaidSession.java
git commit -m "Add raid data model"
```

---

### Task 3: Pure-logic classes (TDD, JUnit-tested)

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidInstanceAllocator.java`
- Test: `yeowool-raid/src/test/java/com/yeowool/raid/RaidInstanceAllocatorTest.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidEntryValidator.java`
- Test: `yeowool-raid/src/test/java/com/yeowool/raid/RaidEntryValidatorTest.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidRewardRoller.java`
- Test: `yeowool-raid/src/test/java/com/yeowool/raid/RaidRewardRollerTest.java`

**Interfaces:**
- Consumes: none (pure logic, no dependency on Task 1/2 classes except plain data).
- Produces: `RaidInstanceAllocator(int slotCount)` with `allocate() -> OptionalInt`, `release(int slotIndex)`, `isFull() -> boolean`.
- Produces: `RaidEntryValidator` static method `validate(int partySize, int minPartySize, int maxPartySize, boolean partyAlreadyInRaid, boolean hasEnoughTickets, boolean instanceAvailable) -> Optional<RaidEntryDenialReason>` and enum `RaidEntryDenialReason { PARTY_TOO_SMALL, PARTY_TOO_LARGE, PARTY_ALREADY_IN_RAID, NOT_ENOUGH_TICKETS, NO_FREE_INSTANCE }`.
- Produces: `RaidRewardRoller` static method `roll(List<ItemStack> pool, Random random) -> Optional<ItemStack>` (empty if pool is empty; otherwise one uniformly-random **clone** of an entry from the pool).

- [ ] **Step 1: Write the failing test for `RaidInstanceAllocator`**

```java
package com.yeowool.raid;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class RaidInstanceAllocatorTest {

    @Test
    void allocatesSlotsInOrderAndTracksFullness() {
        RaidInstanceAllocator allocator = new RaidInstanceAllocator(2);
        assertFalse(allocator.isFull());

        OptionalInt first = allocator.allocate();
        assertEquals(OptionalInt.of(0), first);

        OptionalInt second = allocator.allocate();
        assertEquals(OptionalInt.of(1), second);
        assertTrue(allocator.isFull());

        assertEquals(OptionalInt.empty(), allocator.allocate());
    }

    @Test
    void releasingFreesTheSlotForReuse() {
        RaidInstanceAllocator allocator = new RaidInstanceAllocator(1);
        int slot = allocator.allocate().orElseThrow();
        assertTrue(allocator.isFull());

        allocator.release(slot);
        assertFalse(allocator.isFull());
        assertEquals(OptionalInt.of(0), allocator.allocate());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew.bat :yeowool-raid:test --tests "com.yeowool.raid.RaidInstanceAllocatorTest" --console=plain`
Expected: FAIL — `RaidInstanceAllocator` does not exist (compile error).

- [ ] **Step 3: Write `RaidInstanceAllocator.java`**

```java
package com.yeowool.raid;

import java.util.BitSet;
import java.util.OptionalInt;

/** Tracks which of a fixed N instance slots are occupied. Pure in-memory bookkeeping, no I/O. */
public final class RaidInstanceAllocator {

    private final int slotCount;
    private final BitSet occupied;

    public RaidInstanceAllocator(int slotCount) {
        this.slotCount = slotCount;
        this.occupied = new BitSet(slotCount);
    }

    public synchronized OptionalInt allocate() {
        int free = occupied.nextClearBit(0);
        if (free >= slotCount) {
            return OptionalInt.empty();
        }
        occupied.set(free);
        return OptionalInt.of(free);
    }

    public synchronized void release(int slotIndex) {
        occupied.clear(slotIndex);
    }

    public synchronized boolean isFull() {
        return occupied.cardinality() >= slotCount;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew.bat :yeowool-raid:test --tests "com.yeowool.raid.RaidInstanceAllocatorTest" --console=plain`
Expected: PASS (2 tests)

- [ ] **Step 5: Write the failing test for `RaidEntryValidator`**

```java
package com.yeowool.raid;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaidEntryValidatorTest {

    @Test
    void passesWhenEverythingIsFine() {
        assertEquals(Optional.empty(), RaidEntryValidator.validate(3, 1, 6, false, true, true));
    }

    @Test
    void rejectsPartyBelowMinimum() {
        assertEquals(Optional.of(RaidEntryDenialReason.PARTY_TOO_SMALL),
                RaidEntryValidator.validate(0, 1, 6, false, true, true));
    }

    @Test
    void rejectsPartyAboveMaximum() {
        assertEquals(Optional.of(RaidEntryDenialReason.PARTY_TOO_LARGE),
                RaidEntryValidator.validate(7, 1, 6, false, true, true));
    }

    @Test
    void rejectsWhenPartyAlreadyInARaid() {
        assertEquals(Optional.of(RaidEntryDenialReason.PARTY_ALREADY_IN_RAID),
                RaidEntryValidator.validate(3, 1, 6, true, true, true));
    }

    @Test
    void rejectsWhenNotEnoughTickets() {
        assertEquals(Optional.of(RaidEntryDenialReason.NOT_ENOUGH_TICKETS),
                RaidEntryValidator.validate(3, 1, 6, false, false, true));
    }

    @Test
    void rejectsWhenNoInstanceIsFree() {
        assertEquals(Optional.of(RaidEntryDenialReason.NO_FREE_INSTANCE),
                RaidEntryValidator.validate(3, 1, 6, false, true, false));
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew.bat :yeowool-raid:test --tests "com.yeowool.raid.RaidEntryValidatorTest" --console=plain`
Expected: FAIL — `RaidEntryValidator`/`RaidEntryDenialReason` do not exist.

- [ ] **Step 7: Write `RaidEntryDenialReason.java` and `RaidEntryValidator.java`**

```java
package com.yeowool.raid;

public enum RaidEntryDenialReason {
    PARTY_TOO_SMALL, PARTY_TOO_LARGE, PARTY_ALREADY_IN_RAID, NOT_ENOUGH_TICKETS, NO_FREE_INSTANCE
}
```

```java
package com.yeowool.raid;

import java.util.Optional;

/** Checked in this exact order so the denial message matches the most relevant reason first. */
public final class RaidEntryValidator {

    private RaidEntryValidator() {
    }

    public static Optional<RaidEntryDenialReason> validate(
            int partySize, int minPartySize, int maxPartySize,
            boolean partyAlreadyInRaid, boolean hasEnoughTickets, boolean instanceAvailable) {
        if (partySize < minPartySize) {
            return Optional.of(RaidEntryDenialReason.PARTY_TOO_SMALL);
        }
        if (partySize > maxPartySize) {
            return Optional.of(RaidEntryDenialReason.PARTY_TOO_LARGE);
        }
        if (partyAlreadyInRaid) {
            return Optional.of(RaidEntryDenialReason.PARTY_ALREADY_IN_RAID);
        }
        if (!hasEnoughTickets) {
            return Optional.of(RaidEntryDenialReason.NOT_ENOUGH_TICKETS);
        }
        if (!instanceAvailable) {
            return Optional.of(RaidEntryDenialReason.NO_FREE_INSTANCE);
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew.bat :yeowool-raid:test --tests "com.yeowool.raid.RaidEntryValidatorTest" --console=plain`
Expected: PASS (6 tests)

- [ ] **Step 9: Write the failing test for `RaidRewardRoller`**

```java
package com.yeowool.raid;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RaidRewardRollerTest {

    @Test
    void emptyPoolYieldsNoReward() {
        assertEquals(Optional.empty(), RaidRewardRoller.roll(List.of(), new Random(1)));
    }

    @Test
    void singleItemPoolAlwaysReturnsThatItem() {
        ItemStack diamond = new ItemStack(Material.DIAMOND, 3);
        Optional<ItemStack> rolled = RaidRewardRoller.roll(List.of(diamond), new Random(1));
        assertTrue(rolled.isPresent());
        assertEquals(Material.DIAMOND, rolled.get().getType());
        assertEquals(3, rolled.get().getAmount());
    }

    @Test
    void rolledItemIsAClonedIndependentInstance() {
        ItemStack diamond = new ItemStack(Material.DIAMOND, 3);
        ItemStack rolled = RaidRewardRoller.roll(List.of(diamond), new Random(1)).orElseThrow();
        rolled.setAmount(99);
        assertEquals(3, diamond.getAmount(), "rolling must not mutate the pool's own item");
    }

    @Test
    void withAFixedSeedThePickIsDeterministic() {
        List<ItemStack> pool = List.of(
                new ItemStack(Material.DIAMOND),
                new ItemStack(Material.EMERALD),
                new ItemStack(Material.NETHERITE_INGOT)
        );
        Optional<ItemStack> first = RaidRewardRoller.roll(pool, new Random(42));
        Optional<ItemStack> second = RaidRewardRoller.roll(pool, new Random(42));
        assertEquals(first.map(ItemStack::getType), second.map(ItemStack::getType));
    }
}
```

- [ ] **Step 10: Run it to verify it fails**

Run: `./gradlew.bat :yeowool-raid:test --tests "com.yeowool.raid.RaidRewardRollerTest" --console=plain`
Expected: FAIL — `RaidRewardRoller` does not exist.

- [ ] **Step 11: Write `RaidRewardRoller.java`**

```java
package com.yeowool.raid;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Each winning participant rolls their own single item, uniformly at random, from the reward pool. */
public final class RaidRewardRoller {

    private RaidRewardRoller() {
    }

    public static Optional<ItemStack> roll(List<ItemStack> pool, Random random) {
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        ItemStack picked = pool.get(random.nextInt(pool.size()));
        return Optional.of(picked.clone());
    }
}
```

- [ ] **Step 12: Run the test to verify it passes**

Run: `./gradlew.bat :yeowool-raid:test --tests "com.yeowool.raid.RaidRewardRollerTest" --console=plain`
Expected: PASS (4 tests)

- [ ] **Step 13: Run the whole module's test suite once**

Run: `./gradlew.bat :yeowool-raid:test --console=plain`
Expected: `BUILD SUCCESSFUL`, 12 tests passing across the three classes.

- [ ] **Step 14: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/RaidInstanceAllocator.java yeowool-raid/src/main/java/com/yeowool/raid/RaidEntryValidator.java yeowool-raid/src/main/java/com/yeowool/raid/RaidEntryDenialReason.java yeowool-raid/src/main/java/com/yeowool/raid/RaidRewardRoller.java yeowool-raid/src/test/java/com/yeowool/raid/RaidInstanceAllocatorTest.java yeowool-raid/src/test/java/com/yeowool/raid/RaidEntryValidatorTest.java yeowool-raid/src/test/java/com/yeowool/raid/RaidRewardRollerTest.java
git commit -m "Add raid instance allocator, entry validator, and reward roller with tests"
```

---

### Task 4: Schema and repositories

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/database/RaidSchemaInitializer.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/database/RaidDefinitionRepository.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/database/RaidInstanceRepository.java`

**Interfaces:**
- Consumes: `RaidDefinition`, `RaidInstanceSlot` (Task 2); `com.yeowool.core.util.ItemStackSerializer` (existing, `yeowool-core`).
- Produces: `RaidSchemaInitializer.initialize(DataSource)` (static, throws `SQLException`).
- Produces: `RaidDefinitionRepository(DataSource)` with `loadAll() -> Map<Long, RaidDefinition>`, `insert(String name) -> long`, `delete(long id)`, `updateNpcId(long id, int npcId)`, `updateMythicMob(long id, String mythicMobId)`, `updateTicket(long id, String ticketItemId, int ticketAmount)`, `updatePartySize(long id, int min, int max)`, `updateTimeLimit(long id, int seconds)`, `updateSharedLives(long id, int lives)`, `updateInstanceCount(long id, int count)`, `updateRewardItems(long id, List<ItemStack> items)`.
- Produces: `RaidInstanceRepository(DataSource)` with `loadAll() -> Map<Long, List<RaidInstanceSlot>>` (keyed by `raidId`), `upsertSlot(long raidId, int slotIndex, String field, Location location)` where `field` is one of `"entry"`, `"boss_spawn"`, `"exit"`, `"bound_min"`, `"bound_max"`.

- [ ] **Step 1: Write `RaidSchemaInitializer.java`**

```java
package com.yeowool.raid.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class RaidSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_raid_definition (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(64) NOT NULL UNIQUE,
                npc_id INT NOT NULL DEFAULT -1,
                mythic_mob_id VARCHAR(64) NOT NULL DEFAULT '',
                ticket_item_id VARCHAR(64) NOT NULL DEFAULT '',
                ticket_amount INT NOT NULL DEFAULT 1,
                min_party_size INT NOT NULL DEFAULT 1,
                max_party_size INT NOT NULL DEFAULT 6,
                time_limit_seconds INT NOT NULL DEFAULT 1200,
                shared_lives INT NOT NULL DEFAULT 5,
                instance_count INT NOT NULL DEFAULT 3,
                reward_items MEDIUMTEXT,
                created_at BIGINT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_raid_instance (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                raid_id BIGINT NOT NULL,
                slot_index INT NOT NULL,
                world VARCHAR(64) NOT NULL DEFAULT '',
                entry_x DOUBLE, entry_y DOUBLE, entry_z DOUBLE, entry_yaw FLOAT, entry_pitch FLOAT,
                boss_spawn_x DOUBLE, boss_spawn_y DOUBLE, boss_spawn_z DOUBLE,
                exit_x DOUBLE, exit_y DOUBLE, exit_z DOUBLE, exit_yaw FLOAT, exit_pitch FLOAT,
                bound_min_x DOUBLE, bound_min_y DOUBLE, bound_min_z DOUBLE,
                bound_max_x DOUBLE, bound_max_y DOUBLE, bound_max_z DOUBLE,
                UNIQUE (raid_id, slot_index),
                FOREIGN KEY (raid_id) REFERENCES yw_raid_definition(id) ON DELETE CASCADE
            )
            """
    );

    private RaidSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.execute(ddl);
            }
        }
    }
}
```

- [ ] **Step 2: Write `RaidDefinitionRepository.java`**

```java
package com.yeowool.raid.database;

import com.yeowool.core.util.ItemStackSerializer;
import com.yeowool.raid.RaidDefinition;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RaidDefinitionRepository {

    private final DataSource dataSource;

    public RaidDefinitionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<Long, RaidDefinition> loadAll() throws SQLException {
        Map<Long, RaidDefinition> result = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM yw_raid_definition")) {
            while (rs.next()) {
                List<ItemStack> rewardItems = ItemStackSerializer.deserializeArray(rs.getString("reward_items"));
                RaidDefinition definition = new RaidDefinition(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("npc_id"),
                        rs.getString("mythic_mob_id"),
                        rs.getString("ticket_item_id"),
                        rs.getInt("ticket_amount"),
                        rs.getInt("min_party_size"),
                        rs.getInt("max_party_size"),
                        rs.getInt("time_limit_seconds"),
                        rs.getInt("shared_lives"),
                        rs.getInt("instance_count"),
                        rewardItems
                );
                result.put(definition.id(), definition);
            }
        }
        return result;
    }

    public long insert(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO yw_raid_definition (name, created_at) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setLong(2, System.currentTimeMillis());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void delete(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM yw_raid_definition WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    public void updateNpcId(long id, int npcId) throws SQLException {
        updateIntColumn("npc_id", id, npcId);
    }

    public void updateMythicMob(long id, String mythicMobId) throws SQLException {
        updateColumn("mythic_mob_id", id, mythicMobId);
    }

    public void updateTicket(long id, String ticketItemId, int ticketAmount) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE yw_raid_definition SET ticket_item_id = ?, ticket_amount = ? WHERE id = ?")) {
            statement.setString(1, ticketItemId);
            statement.setInt(2, ticketAmount);
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    public void updatePartySize(long id, int min, int max) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE yw_raid_definition SET min_party_size = ?, max_party_size = ? WHERE id = ?")) {
            statement.setInt(1, min);
            statement.setInt(2, max);
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    public void updateTimeLimit(long id, int seconds) throws SQLException {
        updateIntColumn("time_limit_seconds", id, seconds);
    }

    public void updateSharedLives(long id, int lives) throws SQLException {
        updateIntColumn("shared_lives", id, lives);
    }

    public void updateInstanceCount(long id, int count) throws SQLException {
        updateIntColumn("instance_count", id, count);
    }

    public void updateRewardItems(long id, List<ItemStack> items) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE yw_raid_definition SET reward_items = ? WHERE id = ?")) {
            statement.setString(1, ItemStackSerializer.serializeArray(new ArrayList<>(items)));
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private void updateColumn(String column, long id, String value) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE yw_raid_definition SET " + column + " = ? WHERE id = ?")) {
            statement.setString(1, value);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private void updateIntColumn(String column, long id, int value) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE yw_raid_definition SET " + column + " = ? WHERE id = ?")) {
            statement.setInt(1, value);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }
}
```

Note: `column` in `updateColumn`/`updateIntColumn` is only ever called with a fixed literal from this same file (never user input), so the string-built SQL is safe — same pattern used elsewhere in this codebase for single-column update helpers.

- [ ] **Step 3: Write `RaidInstanceRepository.java`**

```java
package com.yeowool.raid.database;

import com.yeowool.raid.RaidInstanceSlot;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RaidInstanceRepository {

    private final DataSource dataSource;

    public RaidInstanceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<Long, List<RaidInstanceSlot>> loadAll() throws SQLException {
        Map<Long, List<RaidInstanceSlot>> result = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM yw_raid_instance ORDER BY raid_id, slot_index")) {
            while (rs.next()) {
                String worldName = rs.getString("world");
                var world = Bukkit.getWorld(worldName);
                if (world == null) {
                    continue; // world not loaded yet on this boot; skipped, re-read on next reload
                }
                long raidId = rs.getLong("raid_id");
                RaidInstanceSlot slot = new RaidInstanceSlot(
                        rs.getLong("id"),
                        raidId,
                        rs.getInt("slot_index"),
                        new Location(world, rs.getDouble("entry_x"), rs.getDouble("entry_y"), rs.getDouble("entry_z"), rs.getFloat("entry_yaw"), rs.getFloat("entry_pitch")),
                        new Location(world, rs.getDouble("boss_spawn_x"), rs.getDouble("boss_spawn_y"), rs.getDouble("boss_spawn_z")),
                        new Location(world, rs.getDouble("exit_x"), rs.getDouble("exit_y"), rs.getDouble("exit_z"), rs.getFloat("exit_yaw"), rs.getFloat("exit_pitch")),
                        new Location(world, rs.getDouble("bound_min_x"), rs.getDouble("bound_min_y"), rs.getDouble("bound_min_z")),
                        new Location(world, rs.getDouble("bound_max_x"), rs.getDouble("bound_max_y"), rs.getDouble("bound_max_z"))
                );
                result.computeIfAbsent(raidId, k -> new ArrayList<>()).add(slot);
            }
        }
        return result;
    }

    /** field is one of "entry", "boss_spawn", "exit", "bound_min", "bound_max". */
    public void upsertSlot(long raidId, int slotIndex, String field, Location location) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean exists;
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM yw_raid_instance WHERE raid_id = ? AND slot_index = ?")) {
                check.setLong(1, raidId);
                check.setInt(2, slotIndex);
                try (ResultSet rs = check.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (!exists) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO yw_raid_instance (raid_id, slot_index, world) VALUES (?, ?, ?)")) {
                    insert.setLong(1, raidId);
                    insert.setInt(2, slotIndex);
                    insert.setString(3, location.getWorld().getName());
                    insert.executeUpdate();
                }
            }
            boolean hasYawPitch = field.equals("entry") || field.equals("exit");
            String sql = hasYawPitch
                    ? "UPDATE yw_raid_instance SET world = ?, " + field + "_x = ?, " + field + "_y = ?, " + field + "_z = ?, " + field + "_yaw = ?, " + field + "_pitch = ? WHERE raid_id = ? AND slot_index = ?"
                    : "UPDATE yw_raid_instance SET world = ?, " + field + "_x = ?, " + field + "_y = ?, " + field + "_z = ? WHERE raid_id = ? AND slot_index = ?";
            try (PreparedStatement update = connection.prepareStatement(sql)) {
                int i = 1;
                update.setString(i++, location.getWorld().getName());
                update.setDouble(i++, location.getX());
                update.setDouble(i++, location.getY());
                update.setDouble(i++, location.getZ());
                if (hasYawPitch) {
                    update.setFloat(i++, location.getYaw());
                    update.setFloat(i++, location.getPitch());
                }
                update.setLong(i++, raidId);
                update.setInt(i, slotIndex);
                update.executeUpdate();
            }
        }
    }
}
```

`field` is restricted to the five literal values documented above and is only ever passed by `RaidAdminCommand` (Task 9) from its own fixed subcommand switch — never from unsanitized free text — so building the column name into the SQL string is safe here, matching `RaidDefinitionRepository`'s single-column-update helpers.

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew.bat :yeowool-raid:compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/database/
git commit -m "Add raid schema initializer and repositories"
```

---

### Task 5: Party lookup and ticket check

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/party/RaidPartyLookup.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/item/RaidTicketUtil.java`

**Interfaces:**
- Produces: `RaidPartyLookup(DataSource)` with `findPartyMembers(UUID player) -> Optional<PartyMembers>` where `PartyMembers(long partyId, UUID leader, Set<UUID> members)` is a nested record; reads `yw_party`/`yw_party_member` directly (schema confirmed against `yeowool-community`'s actual `PartySchemaInitializer` source: `yw_party(id, name, leader_uuid, max_size, created_at)`, `yw_party_member(uuid, party_id, name, joined_at)`).
- Produces: `RaidTicketUtil` static methods `hasEnough(Player player, String itemsAdderId, int amount) -> boolean`, `remove(Player player, String itemsAdderId, int amount)` (assumes `hasEnough` was already checked; throws `IllegalStateException` if not enough found, to fail loudly rather than silently under-consume).

- [ ] **Step 1: Write `RaidPartyLookup.java`**

Column names below are copied verbatim from `yeowool-community/src/main/java/com/yeowool/community/party/PartySchemaInitializer.java` (read directly while writing this plan): `yw_party(id, name, leader_uuid, max_size, created_at)` and `yw_party_member(uuid, party_id, name, joined_at)` — note the member table's player-id column is `uuid`, **not** `player_uuid`.

```java
package com.yeowool.raid.party;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only lookup against yeowool-community's party tables. No compile dependency on yeowool-community. */
public final class RaidPartyLookup {

    public record PartyMembers(long partyId, UUID leader, Set<UUID> members) {
    }

    private final DataSource dataSource;

    public RaidPartyLookup(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<PartyMembers> findPartyMembers(UUID player) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            long partyId;
            UUID leader;
            try (PreparedStatement findParty = connection.prepareStatement(
                    "SELECT p.id, p.leader_uuid FROM yw_party p " +
                            "JOIN yw_party_member m ON m.party_id = p.id " +
                            "WHERE m.uuid = ?")) {
                findParty.setString(1, player.toString());
                try (ResultSet rs = findParty.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    partyId = rs.getLong("id");
                    leader = UUID.fromString(rs.getString("leader_uuid"));
                }
            }
            Set<UUID> members = new HashSet<>();
            try (PreparedStatement findMembers = connection.prepareStatement(
                    "SELECT uuid FROM yw_party_member WHERE party_id = ?")) {
                findMembers.setLong(1, partyId);
                try (ResultSet rs = findMembers.executeQuery()) {
                    while (rs.next()) {
                        members.add(UUID.fromString(rs.getString("uuid")));
                    }
                }
            }
            return Optional.of(new PartyMembers(partyId, leader, members));
        }
    }
}
```

If Step 1 found different column names, adjust the two SQL strings above accordingly before moving on.

- [ ] **Step 3: Write `RaidTicketUtil.java`**

```java
package com.yeowool.raid.item;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class RaidTicketUtil {

    private RaidTicketUtil() {
    }

    public static boolean hasEnough(Player player, String itemsAdderId, int amount) {
        return countHeld(player, itemsAdderId) >= amount;
    }

    /** Call only after hasEnough(player, itemsAdderId, amount) returned true. */
    public static void remove(Player player, String itemsAdderId, int amount) {
        PlayerInventory inventory = player.getInventory();
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || !matches(stack, itemsAdderId)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            if (take == stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
            remaining -= take;
        }
        if (remaining > 0) {
            throw new IllegalStateException("Tried to remove " + amount + "x " + itemsAdderId
                    + " from " + player.getName() + " but only found " + (amount - remaining));
        }
    }

    private static int countHeld(Player player, String itemsAdderId) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && matches(stack, itemsAdderId)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private static boolean matches(ItemStack stack, String itemsAdderId) {
        CustomStack custom = CustomStack.byItemStack(stack);
        return custom != null && itemsAdderId.equals(custom.getNamespacedID());
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew.bat :yeowool-raid:compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/party/ yeowool-raid/src/main/java/com/yeowool/raid/item/
git commit -m "Add party lookup and ticket check/consume utility"
```

---

### Task 6: RaidManager

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidManager.java`

**Interfaces:**
- Consumes: `RaidDefinition`, `RaidInstanceSlot`, `RaidSession`, `RaidSessionState` (Task 2); `RaidInstanceAllocator`, `RaidEntryValidator`, `RaidEntryDenialReason`, `RaidRewardRoller` (Task 3); `RaidDefinitionRepository`, `RaidInstanceRepository` (Task 4); `RaidPartyLookup` (Task 5).
- Produces: `RaidManager(JavaPlugin, RaidDefinitionRepository, RaidInstanceRepository, ExecutorService)` with: `loadAll()` (throws `SQLException`), `all() -> Collection<RaidDefinition>`, `find(String name) -> Optional<RaidDefinition>`, `byNpc(int npcId) -> List<RaidDefinition>` (mirrors `QuestManager.byNpc`), definition mutators mirroring `QuestManager`'s shape (`create`, `delete`, `setNpcId`, `setMythicMob`, `setTicket`, `setPartySize`, `setTimeLimit`, `setSharedLives`, `setInstanceCount`, `setRewardItems`, `setInstanceSlotLocation` — all blocking, `SQLException`-throwing, meant to be called inside the caller's own `executor.execute()`), the three-step entry dance `peekEntryDenial(RaidDefinition, RaidPartyLookup.PartyMembers, boolean) -> Optional<RaidEntryDenialReason>` / `reserveSlot(long raidId) -> int` / `releaseReservedSlot(long raidId, int slotIndex)` / `startSession(long raidId, int slotIndex, long partyId, Set<UUID> members, UUID bossEntityId, int sharedLives)` (see Task 9 for why entry needs three steps instead of one), `activeSessionFor(UUID player) -> Optional<RaidSession>`, `sessionByBossEntity(UUID) -> Optional<RaidSession>`, `activeSessions() -> Collection<RaidSession>`, `endSession(RaidSession session, RaidSessionState finalState)` (releases the instance slot).

- [ ] **Step 1: Write `RaidManager.java`**

```java
package com.yeowool.raid;

import com.yeowool.raid.database.RaidDefinitionRepository;
import com.yeowool.raid.database.RaidInstanceRepository;
import com.yeowool.raid.party.RaidPartyLookup;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Owns raid definitions (small admin-defined dataset, fully cached — same trade-off as
 * {@code QuestManager}) and every currently-active {@link RaidSession} (in-memory only).
 */
public final class RaidManager {

    private final JavaPlugin plugin;
    private final RaidDefinitionRepository definitionRepository;
    private final RaidInstanceRepository instanceRepository;
    private final ExecutorService executor;

    private final ConcurrentHashMap<Long, RaidDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<RaidInstanceSlot>> instanceSlots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RaidInstanceAllocator> allocators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RaidSession> sessionsByPartyId = new ConcurrentHashMap<>();

    public RaidManager(JavaPlugin plugin, RaidDefinitionRepository definitionRepository,
                        RaidInstanceRepository instanceRepository, ExecutorService executor) {
        this.plugin = plugin;
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.executor = executor;
    }

    public void loadAll() throws SQLException {
        definitions.putAll(definitionRepository.loadAll());
        instanceSlots.putAll(instanceRepository.loadAll());
        for (RaidDefinition definition : definitions.values()) {
            allocators.put(definition.id(), new RaidInstanceAllocator(definition.instanceCount()));
        }
        plugin.getLogger().info("보스 레이드 " + definitions.size() + "개를 불러왔습니다.");
    }

    // ---- definitions ----

    public Collection<RaidDefinition> all() {
        return definitions.values();
    }

    public Optional<RaidDefinition> find(String name) {
        return definitions.values().stream().filter(d -> d.name().equalsIgnoreCase(name)).findFirst();
    }

    /** Mirrors QuestManager.byNpc — the mechanism a Citizens NPC uses to find which raid(s) it offers. */
    public List<RaidDefinition> byNpc(int npcId) {
        return definitions.values().stream().filter(d -> d.npcId() == npcId).toList();
    }

    public enum CreateResult { SUCCESS, ALREADY_EXISTS }

    /** Blocking — call off the main thread. */
    public CreateResult create(String name) throws SQLException {
        if (find(name).isPresent()) {
            return CreateResult.ALREADY_EXISTS;
        }
        long id = definitionRepository.insert(name);
        RaidDefinition definition = new RaidDefinition(id, name, -1, "", "", 1, 1, 6, 1200, 5, 3, List.of());
        definitions.put(id, definition);
        allocators.put(id, new RaidInstanceAllocator(definition.instanceCount()));
        return CreateResult.SUCCESS;
    }

    /** Blocking — call off the main thread. */
    public boolean delete(String name) throws SQLException {
        var definition = find(name);
        if (definition.isEmpty()) {
            return false;
        }
        definitionRepository.delete(definition.get().id());
        definitions.remove(definition.get().id());
        allocators.remove(definition.get().id());
        instanceSlots.remove(definition.get().id());
        return true;
    }

    /** Blocking — call off the main thread. */
    public boolean setNpcId(String name, int npcId) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updateNpcId(d.id(), npcId);
            return d.withNpcId(npcId);
        });
    }

    /** Blocking — call off the main thread. */
    public boolean setMythicMob(String name, String mythicMobId) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updateMythicMob(d.id(), mythicMobId);
            return d.withMythicMob(mythicMobId);
        });
    }

    /** Blocking — call off the main thread. */
    public boolean setTicket(String name, String ticketItemId, int ticketAmount) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updateTicket(d.id(), ticketItemId, ticketAmount);
            return d.withTicket(ticketItemId, ticketAmount);
        });
    }

    /** Blocking — call off the main thread. */
    public boolean setPartySize(String name, int min, int max) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updatePartySize(d.id(), min, max);
            return d.withPartySize(min, max);
        });
    }

    /** Blocking — call off the main thread. */
    public boolean setTimeLimit(String name, int seconds) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updateTimeLimit(d.id(), seconds);
            return d.withTimeLimit(seconds);
        });
    }

    /** Blocking — call off the main thread. */
    public boolean setSharedLives(String name, int lives) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updateSharedLives(d.id(), lives);
            return d.withSharedLives(lives);
        });
    }

    /** Blocking — call off the main thread. Also resizes the in-memory allocator (existing sessions in
     * now-removed slots keep running; the smaller pool takes effect for the next entry). */
    public boolean setInstanceCount(String name, int count) throws SQLException {
        var definition = find(name);
        if (definition.isEmpty()) {
            return false;
        }
        definitionRepository.updateInstanceCount(definition.get().id(), count);
        RaidDefinition updated = definition.get().withInstanceCount(count);
        definitions.put(updated.id(), updated);
        allocators.put(updated.id(), new RaidInstanceAllocator(count));
        return true;
    }

    /** Blocking — call off the main thread. */
    public boolean setRewardItems(String name, List<ItemStack> items) throws SQLException {
        return mutate(name, d -> {
            definitionRepository.updateRewardItems(d.id(), items);
            return d.withRewardItems(items);
        });
    }

    /** Blocking — call off the main thread. field is "entry", "boss_spawn", "exit", "bound_min", or "bound_max". */
    public boolean setInstanceSlotLocation(long raidId, int slotIndex, String field, Location location) throws SQLException {
        if (!definitions.containsKey(raidId)) {
            return false;
        }
        instanceRepository.upsertSlot(raidId, slotIndex, field, location);
        instanceSlots.put(raidId, instanceRepository.loadAll().getOrDefault(raidId, new ArrayList<>()));
        return true;
    }

    public List<RaidInstanceSlot> instanceSlotsFor(long raidId) {
        return instanceSlots.getOrDefault(raidId, List.of());
    }

    private interface Mutation {
        RaidDefinition apply(RaidDefinition current) throws SQLException;
    }

    private boolean mutate(String name, Mutation mutation) throws SQLException {
        var definition = find(name);
        if (definition.isEmpty()) {
            return false;
        }
        RaidDefinition updated = mutation.apply(definition.get());
        definitions.put(updated.id(), updated);
        return true;
    }

    // ---- sessions (in-memory, hot path) ----
    //
    // Entry is a three-step dance because the boss's real entity UUID is only known *after* it
    // spawns, but we must not spawn a boss unless we're sure the party can actually enter:
    //   1. peekEntryDenial   — read-only validation (party size / not-already-in-raid / tickets)
    //   2. reserveSlot       — atomically claims an instance slot (or throws if none free)
    //   3. startSession      — called once the boss has been spawned; creates the RaidSession
    // If step 2 succeeds but the caller can't finish (e.g. missing slot coordinates), it must call
    // releaseReservedSlot to give the slot back.

    /** Read-only — does not reserve anything. Checked again racily by reserveSlot's own isFull() check. */
    public Optional<RaidEntryDenialReason> peekEntryDenial(RaidDefinition raid, RaidPartyLookup.PartyMembers party, boolean hasEnoughTickets) {
        boolean partyAlreadyInRaid = sessionsByPartyId.containsKey(party.partyId());
        RaidInstanceAllocator allocator = allocators.computeIfAbsent(raid.id(), id -> new RaidInstanceAllocator(raid.instanceCount()));
        return RaidEntryValidator.validate(party.members().size(), raid.minPartySize(), raid.maxPartySize(),
                partyAlreadyInRaid, hasEnoughTickets, !allocator.isFull());
    }

    /** Throws IllegalStateException if no slot is free — caller must have just checked peekEntryDenial. */
    public int reserveSlot(long raidId) {
        RaidInstanceAllocator allocator = allocators.computeIfAbsent(raidId, id -> new RaidInstanceAllocator(1));
        OptionalInt slot = allocator.allocate();
        if (slot.isEmpty()) {
            throw new IllegalStateException("reserveSlot called with no free slot for raid " + raidId);
        }
        return slot.getAsInt();
    }

    public void releaseReservedSlot(long raidId, int slotIndex) {
        allocators.computeIfAbsent(raidId, id -> new RaidInstanceAllocator(1)).release(slotIndex);
    }

    /** Call once the boss has actually been spawned and its real entity UUID is known. */
    public void startSession(long raidId, int slotIndex, long partyId, java.util.Set<UUID> members, UUID bossEntityId, int sharedLives) {
        RaidSession session = new RaidSession(raidId, slotIndex, members, bossEntityId, sharedLives);
        sessionsByPartyId.put(partyId, session);
    }

    public Optional<RaidSession> activeSessionFor(UUID player) {
        return sessionsByPartyId.values().stream()
                .filter(session -> session.getState() == RaidSessionState.IN_PROGRESS && session.getPartyMembers().contains(player))
                .findFirst();
    }

    public Optional<RaidSession> sessionByBossEntity(UUID bossEntityId) {
        return sessionsByPartyId.values().stream()
                .filter(session -> session.getBossEntityId().equals(bossEntityId))
                .findFirst();
    }

    public Collection<RaidSession> activeSessions() {
        return sessionsByPartyId.values();
    }

    public void endSession(RaidSession session, RaidSessionState finalState) {
        session.setState(finalState);
        allocators.computeIfAbsent(session.getRaidId(), id -> new RaidInstanceAllocator(1)).release(session.getSlotIndex());
        sessionsByPartyId.values().remove(session);
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew.bat :yeowool-raid:compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/RaidManager.java
git commit -m "Add RaidManager tying definitions, instances, and sessions together"
```

---

### Task 7: MythicMobs and party-death listeners, time-limit task

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidBossDeathListener.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidPartyDeathListener.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidTimeoutTask.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidRewardService.java`

**Interfaces:**
- Consumes: `RaidManager` (Task 6); `RaidRewardRoller` (Task 3); `YeowoolCoreAPI.mailbox()` (existing, `yeowool-core`); `io.lumine.mythic.bukkit.events.MythicMobDeathEvent` (MythicMobs API); `RaidHudService` (Task 8 — forward-referenced; `RaidBossDeathListener`/`RaidTimeoutTask` call `RaidHudService.notifyResult(...)`, defined in Task 8).
- Produces: `RaidRewardService.completeVictory(RaidManager, RaidSession, RaidDefinition, YeowoolCoreAPI)` (package-visible static method — mirrors `QuestRewardService`'s shape): mails each participant one rolled item, then calls `raidManager.endSession(session, RaidSessionState.WON)`.

- [ ] **Step 1: Write `RaidRewardService.java`**

```java
package com.yeowool.raid;

import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

final class RaidRewardService {

    private static final Random RANDOM = new Random();

    private RaidRewardService() {
    }

    static void completeVictory(RaidManager raidManager, RaidSession session, RaidDefinition raid, YeowoolCoreAPI core) {
        for (UUID member : session.getPartyMembers()) {
            Optional<ItemStack> reward = RaidRewardRoller.roll(raid.rewardItems(), RANDOM);
            reward.ifPresent(item -> core.mailbox().deliverOrStore(member, item, "YeowoolRaid", raid.name() + " 클리어 보상"));
        }
        raidManager.endSession(session, RaidSessionState.WON);
    }
}
```

`YeowoolCoreAPI.mailbox()`'s real method is `void deliverOrStore(UUID recipient, ItemStack item, String sourcePlugin, String note)` (verified by reading `yeowool-core/src/main/java/com/yeowool/core/api/service/MailboxService.java` directly — not the `send(...)` shape this plan originally guessed). Its Javadoc also notes it **must be called from the main server thread**; both call sites that reach `completeVictory` (`RaidBossDeathListener`'s `MythicMobDeathEvent` handler, and `RaidTimeoutTask.run()` via `runTaskTimer`) already run on the main thread, so no extra scheduling is needed here.

- [ ] **Step 2: Write `RaidBossDeathListener.java`**

```java
package com.yeowool.raid;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class RaidBossDeathListener implements Listener {

    private final RaidManager raidManager;
    private final YeowoolCoreAPI core;
    private final RaidHudService hudService;

    public RaidBossDeathListener(RaidManager raidManager, YeowoolCoreAPI core, RaidHudService hudService) {
        this.raidManager = raidManager;
        this.core = core;
        this.hudService = hudService;
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        var session = raidManager.sessionByBossEntity(event.getEntity().getUniqueId());
        if (session.isEmpty() || session.get().getState() != RaidSessionState.IN_PROGRESS) {
            return;
        }
        var raid = raidManager.all().stream().filter(r -> r.id() == session.get().getRaidId()).findFirst();
        if (raid.isEmpty()) {
            return;
        }
        hudService.notifyResult(session.get(), true);
        RaidRewardService.completeVictory(raidManager, session.get(), raid.get(), core);
    }
}
```

- [ ] **Step 3: Write `RaidPartyDeathListener.java`**

```java
package com.yeowool.raid;

import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class RaidPartyDeathListener implements org.bukkit.event.Listener {

    private final RaidManager raidManager;
    private final RaidHudService hudService;

    public RaidPartyDeathListener(RaidManager raidManager, RaidHudService hudService) {
        this.raidManager = raidManager;
        this.hudService = hudService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        var session = raidManager.activeSessionFor(event.getEntity().getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        int remaining = session.get().decrementLives();
        if (remaining <= 0) {
            hudService.notifyResult(session.get(), false);
            raidManager.endSession(session.get(), RaidSessionState.LOST);
        } else {
            hudService.notifyLifeLost(session.get(), remaining);
        }
    }
}
```

- [ ] **Step 4: Write `RaidTimeoutTask.java`**

```java
package com.yeowool.raid;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs once a minute; ends any session whose raid.timeLimitSeconds() has elapsed as a loss. */
public final class RaidTimeoutTask implements Runnable {

    private final RaidManager raidManager;
    private final RaidHudService hudService;

    public RaidTimeoutTask(RaidManager raidManager, RaidHudService hudService) {
        this.raidManager = raidManager;
        this.hudService = hudService;
    }

    public void start(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this, 20L * 60, 20L * 60);
    }

    @Override
    public void run() {
        for (RaidSession session : java.util.List.copyOf(raidManager.activeSessions())) {
            if (session.getState() != RaidSessionState.IN_PROGRESS) {
                continue;
            }
            var raid = raidManager.all().stream().filter(r -> r.id() == session.getRaidId()).findFirst();
            if (raid.isEmpty() || !session.isExpired(raid.get().timeLimitSeconds())) {
                continue;
            }
            hudService.notifyResult(session, false);
            raidManager.endSession(session, RaidSessionState.LOST);
        }
    }
}
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew.bat :yeowool-raid:compileJava --console=plain`
Expected: FAIL — `RaidHudService` doesn't exist yet (Task 8). This is expected; move on.

- [ ] **Step 6: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/RaidBossDeathListener.java yeowool-raid/src/main/java/com/yeowool/raid/RaidPartyDeathListener.java yeowool-raid/src/main/java/com/yeowool/raid/RaidTimeoutTask.java yeowool-raid/src/main/java/com/yeowool/raid/RaidRewardService.java
git commit -m "Add raid boss-death, party-death, and timeout handling (depends on Task 8's RaidHudService)"
```

---

### Task 8: BetterHud relay

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidHudService.java`

**Interfaces:**
- Consumes: `RaidSession` (Task 2).
- Produces: `RaidHudService(String bossWarningPopup, String resultPopup)` with `notifyLifeLost(RaidSession, int remainingLives)`, `notifyResult(RaidSession, boolean won)` — both send a chat fallback to every party member and fire BetterHud's `CustomPopupEvent` via reflection if present, exactly mirroring `yeowool-quest`'s `QuestDialogueService.fireCustomPopup`.

- [ ] **Step 1: Write `RaidHudService.java`**

```java
package com.yeowool.raid;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.lang.reflect.Constructor;
import java.util.UUID;

/**
 * Relays raid life/result updates to BetterHud, via reflection — same seam as
 * {@code yeowool-quest}'s {@code QuestDialogueService}: BetterHud has no reliable Maven artifact
 * and this plugin must keep working (chat fallback) even when BetterHud isn't installed.
 */
public final class RaidHudService {

    private static final String CUSTOM_POPUP_EVENT_CLASS = "kr.toxicity.hud.api.bukkit.event.CustomPopupEvent";

    private final String bossWarningPopup;
    private final String resultPopup;

    public RaidHudService(String bossWarningPopup, String resultPopup) {
        this.bossWarningPopup = bossWarningPopup;
        this.resultPopup = resultPopup;
    }

    public void notifyLifeLost(RaidSession session, int remainingLives) {
        forEachOnlineMember(session, player -> {
            player.sendMessage(Component.text("파티 부활 횟수 " + remainingLives + "회 남음", NamedTextColor.RED));
            fireCustomPopup(player, bossWarningPopup);
        });
    }

    public void notifyResult(RaidSession session, boolean won) {
        forEachOnlineMember(session, player -> {
            player.sendMessage(won
                    ? Component.text("레이드 클리어! 보상이 우편함으로 발송되었습니다.", NamedTextColor.GOLD)
                    : Component.text("레이드 실패했습니다.", NamedTextColor.RED));
            fireCustomPopup(player, resultPopup);
        });
    }

    private void forEachOnlineMember(RaidSession session, java.util.function.Consumer<Player> action) {
        for (UUID member : session.getPartyMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                action.accept(player);
            }
        }
    }

    private static void fireCustomPopup(Player player, String popupName) {
        try {
            Class<?> eventClass = Class.forName(CUSTOM_POPUP_EVENT_CLASS);
            Constructor<?> constructor = eventClass.getConstructor(Player.class, String.class);
            Bukkit.getPluginManager().callEvent((Event) constructor.newInstance(player, popupName));
        } catch (ClassNotFoundException ignored) {
            // BetterHud not installed - chat fallback above already covers this player.
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().warning("BetterHud 팝업 호출 실패 (" + popupName + "): " + e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Verify Task 7 + Task 8 compile together**

Run: `./gradlew.bat :yeowool-raid:compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/RaidHudService.java
git commit -m "Add BetterHud relay for raid life/result notifications"
```

---

### Task 9: Citizens NPC entry flow and raid-list GUI

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidListGui.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidNpcListener.java`
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidEntryService.java`

**Interfaces:**
- Consumes: `RaidManager`, `RaidDefinition` (Task 6/2); `RaidPartyLookup` (Task 5); `RaidTicketUtil` (Task 5); `RaidHudService` (Task 8); `com.yeowool.core.api.gui.YeowoolGui`, `com.yeowool.core.api.gui.GuiButton` (existing, `yeowool-core` — reuse the same base class `QuestListGui` used).
- Produces: `RaidListGui(Collection<RaidDefinition>, Consumer<RaidDefinition> onPick)` (mirrors `QuestListGui`'s shape). `RaidEntryService(RaidManager, RaidPartyLookup, RaidHudService, ExecutorService)` with `attemptEntry(Player leader, RaidDefinition raid)` (blocking work wrapped in `executor.execute()` internally, replies via `Bukkit.getScheduler().runTask`). `RaidNpcListener(RaidManager, RaidEntryService)` listening to `net.citizensnpcs.api.event.NPCRightClickEvent`, filtered to NPCs tagged as raid-entry NPCs (see Step 3 for the tagging mechanism).

- [ ] **Step 1: Write `RaidListGui.java`**

```java
package com.yeowool.raid;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public final class RaidListGui extends YeowoolGui {

    public RaidListGui(Collection<RaidDefinition> raids, Consumer<RaidDefinition> onPick) {
        super(54, Component.text("보스 레이드 선택", NamedTextColor.DARK_RED));
        int slot = 0;
        for (RaidDefinition raid : raids) {
            if (slot >= 54) {
                break;
            }
            setButton(slot++, GuiButton.of(icon(raid), event -> onPick.accept(raid)));
        }
    }

    private static ItemStack icon(RaidDefinition raid) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(raid.name(), NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("인원 " + raid.minPartySize() + "~" + raid.maxPartySize() + "명", NamedTextColor.GRAY),
                Component.text("제한시간 " + (raid.timeLimitSeconds() / 60) + "분", NamedTextColor.GRAY),
                Component.text("입장권 " + raid.ticketAmount() + "개 필요", NamedTextColor.GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }
}
```

`GuiButton`'s real constructor is `GuiButton(ItemStack item, Consumer<InventoryClickEvent> onClick)` (verified by reading `yeowool-core/src/main/java/com/yeowool/core/api/gui/GuiButton.java` directly, along with its `GuiButton.of(...)` factory and `YeowoolGui`'s `setButton(int, GuiButton)`/`open(Player)` methods, all confirmed to match this plan's usage) — the icon needs to be a real `ItemStack` with meta set, not raw `Material`/`Component`/`List` passed straight to the constructor.

- [ ] **Step 2: Write `RaidEntryService.java`**

```java
package com.yeowool.raid;

import com.yeowool.raid.item.RaidTicketUtil;
import com.yeowool.raid.party.RaidPartyLookup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public final class RaidEntryService {

    private final JavaPlugin plugin;
    private final RaidManager raidManager;
    private final RaidPartyLookup partyLookup;
    private final RaidHudService hudService;
    private final ExecutorService executor;

    public RaidEntryService(JavaPlugin plugin, RaidManager raidManager, RaidPartyLookup partyLookup,
                             RaidHudService hudService, ExecutorService executor) {
        this.plugin = plugin;
        this.raidManager = raidManager;
        this.partyLookup = partyLookup;
        this.hudService = hudService;
        this.executor = executor;
    }

    public void attemptEntry(Player leader, RaidDefinition raid) {
        executor.execute(() -> {
            Optional<RaidPartyLookup.PartyMembers> party;
            try {
                party = partyLookup.findPartyMembers(leader.getUniqueId());
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("파티 조회 실패 (" + leader.getName() + "): " + e.getMessage());
                return;
            }
            if (party.isEmpty() || !party.get().leader().equals(leader.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        leader.sendMessage(Component.text("파티장만 레이드에 입장할 수 있습니다.", NamedTextColor.RED)));
                return;
            }
            boolean hasEnoughTickets = RaidTicketUtil.hasEnough(leader, raid.ticketItemId(), raid.ticketAmount());
            List<RaidInstanceSlot> slots = raidManager.instanceSlotsFor(raid.id());

            Bukkit.getScheduler().runTask(plugin, () -> completeEntry(leader, raid, party.get(), hasEnoughTickets, slots));
        });
    }

    /**
     * Runs on the main thread: finds a free instance slot first (without allocating it), spawns the
     * boss and reads its real UUID from the returned {@code ActiveMob}, and only then calls
     * {@code raidManager.tryEnter(...)} with that real UUID — so a session is never created keyed to
     * a UUID nothing was actually spawned with.
     */
    private void completeEntry(Player leader, RaidDefinition raid, RaidPartyLookup.PartyMembers party,
                                boolean hasEnoughTickets, List<RaidInstanceSlot> slots) {
        var mythicMob = io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().getMythicMob(raid.mythicMobId());
        if (mythicMob.isEmpty()) {
            leader.sendMessage(Component.text("이 레이드의 몹 설정이 잘못되었습니다. 관리자에게 문의하세요.", NamedTextColor.RED));
            return;
        }
        var denial = raidManager.peekEntryDenial(raid, party, hasEnoughTickets);
        if (denial.isPresent()) {
            leader.sendMessage(Component.text(denialMessage(denial.get()), NamedTextColor.RED));
            return;
        }
        int slotIndex = raidManager.reserveSlot(raid.id());
        var slot = slots.stream().filter(s -> s.slotIndex() == slotIndex).findFirst();
        if (slot.isEmpty()) {
            plugin.getLogger().severe("레이드 " + raid.name() + " 슬롯 " + slotIndex + " 좌표가 설정되지 않았습니다.");
            raidManager.releaseReservedSlot(raid.id(), slotIndex);
            return;
        }

        RaidTicketUtil.remove(leader, raid.ticketItemId(), raid.ticketAmount());
        for (UUID memberId : party.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.teleport(slot.get().entry());
            }
        }

        var activeMob = mythicMob.get().spawn(
                io.lumine.mythic.bukkit.BukkitAdapter.adapt(slot.get().bossSpawn()), 1.0,
                io.lumine.mythic.api.mobs.entities.SpawnReason.CUSTOM);
        UUID bossEntityId = activeMob.getUniqueId();

        raidManager.startSession(raid.id(), slotIndex, party.partyId(), party.members(), bossEntityId, raid.sharedLives());
    }

    private String denialMessage(RaidEntryDenialReason reason) {
        return switch (reason) {
            case PARTY_TOO_SMALL -> "레이드 최소 인원을 채우지 못했습니다.";
            case PARTY_TOO_LARGE -> "레이드 최대 인원을 초과했습니다.";
            case PARTY_ALREADY_IN_RAID -> "이미 레이드를 진행 중인 파티입니다.";
            case NOT_ENOUGH_TICKETS -> "입장권이 부족합니다.";
            case NO_FREE_INSTANCE -> "모든 인스턴스가 사용 중입니다. 잠시 후 다시 시도해주세요.";
        };
    }
}
```

The MythicMobs calls above (`MythicBukkit.inst().getMobManager().getMythicMob(String)`, `MythicMob#spawn(AbstractLocation, double, SpawnReason)`, `ActiveMob#getUniqueId()`) were verified by decompiling the actual `MythicMobs-5.12.1.jar` installed on lobby (`javap -p` against `io.lumine.mythic.api.mobs.MobManager`, `io.lumine.mythic.api.mobs.MythicMob`, and `io.lumine.mythic.core.mobs.ActiveMob`) while writing this plan — not guessed. There is no public `spawnMob(String, Location)`-shaped convenience method on `MobManager`; the real path is look up the `MythicMob` by id, then call `.spawn(...)` on it directly, which is what Step 2 does. `MythicMobDeathEvent#getEntity()` (used in Task 7) was verified the same way and returns a plain `org.bukkit.entity.Entity` directly, confirming `event.getEntity().getUniqueId()` in `RaidBossDeathListener` is correct as written.

- [ ] **Step 3: Write `RaidNpcListener.java`**

```java
package com.yeowool.raid;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Mirrors QuestNpcListener exactly: a right-clicked NPC opens the list of raids whose npcId matches it. */
public final class RaidNpcListener implements Listener {

    private final RaidManager raidManager;
    private final RaidEntryService entryService;

    public RaidNpcListener(RaidManager raidManager, RaidEntryService entryService) {
        this.raidManager = raidManager;
        this.entryService = entryService;
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        var raids = raidManager.byNpc(event.getNPC().getId());
        if (raids.isEmpty()) {
            return;
        }
        Player player = event.getClicker();
        new RaidListGui(raids, raid -> entryService.attemptEntry(player, raid)).open(player);
    }
}
```

This intentionally mirrors `yeowool-quest`'s `QuestNpcListener` (verified against its actual source earlier this session) instead of Citizens metadata tagging: a raid is linked to a Citizens NPC via its own `npcId` field (set with `/레이드 npc설정`, Task 10), and any NPC right-click looks up raids by that NPC's numeric id via `RaidManager.byNpc(int)` — the same pattern already proven to work for quests in this codebase.

- [ ] **Step 4: Compile**

Run: `./gradlew.bat :yeowool-raid:compileJava --console=plain`
Expected: `BUILD SUCCESSFUL` — both the MythicMobs spawn call and the `GuiButton`/`YeowoolGui` usage above were written against the real, decompiled/read API surfaces (see the notes under Steps 1 and 2), not guessed, so this should compile clean on the first try. If it doesn't, re-check the exact package names in `yeowool-raid/libs/MythicMobs.jar` (Citizens' `NPC.data()` metadata call from Step 3 is the one piece of this task not independently verified against source — see that step's note).

- [ ] **Step 6: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/RaidListGui.java yeowool-raid/src/main/java/com/yeowool/raid/RaidNpcListener.java yeowool-raid/src/main/java/com/yeowool/raid/RaidEntryService.java
git commit -m "Add raid NPC entry flow: list GUI, entry validation, boss spawn"
```

---

### Task 10: Admin command

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/RaidAdminCommand.java`

**Interfaces:**
- Consumes: `RaidManager` (Task 6); `com.yeowool.core.api.gui.ItemGridEditorGui` (existing, `yeowool-core`).
- Produces: registers itself as the executor/tab-completer for the `레이드` command declared in Task 1's `plugin.yml`.

- [ ] **Step 1: Write `RaidAdminCommand.java`**

```java
package com.yeowool.raid;

import com.yeowool.core.api.gui.ItemGridEditorGui;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public final class RaidAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "생성", "npc설정", "몹설정", "티켓설정", "인원설정", "제한시간설정", "부활횟수설정",
            "보상설정", "인스턴스설정", "목록", "정보", "삭제");
    private static final List<String> SLOT_FIELDS = List.of("입장", "보스스폰", "퇴장", "경계1", "경계2");

    private final JavaPlugin plugin;
    private final RaidManager raidManager;
    private final ExecutorService executor;

    public RaidAdminCommand(JavaPlugin plugin, RaidManager raidManager, ExecutorService executor) {
        this.plugin = plugin;
        this.raidManager = raidManager;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§c사용법: /레이드 <생성|몹설정|티켓설정|인원설정|제한시간설정|부활횟수설정|보상설정|인스턴스설정|목록|정보|삭제>");
            return true;
        }
        switch (args[0]) {
            case "생성" -> handleCreate(sender, args);
            case "npc설정" -> handleSetNpc(sender, args);
            case "몹설정" -> handleSetMythicMob(sender, args);
            case "티켓설정" -> handleSetTicket(sender, args);
            case "인원설정" -> handleSetPartySize(sender, args);
            case "제한시간설정" -> handleSetTimeLimit(sender, args);
            case "부활횟수설정" -> handleSetSharedLives(sender, args);
            case "보상설정" -> handleSetRewards(sender, args);
            case "인스턴스설정" -> handleSetInstance(sender, args);
            case "목록" -> handleList(sender);
            case "정보" -> handleInfo(sender, args);
            case "삭제" -> handleDelete(sender, args);
            default -> sender.sendMessage("§c알 수 없는 하위 명령어입니다.");
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /레이드 생성 <이름>");
            return;
        }
        String name = args[1];
        executor.execute(() -> {
            try {
                var result = raidManager.create(name);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        result == RaidManager.CreateResult.SUCCESS
                                ? "§a레이드 '" + name + "'를 생성했습니다."
                                : "§c이미 존재하는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("레이드 생성 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c레이드 생성 중 오류가 발생했습니다."));
            }
        });
    }

    private void handleSetNpc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /레이드 npc설정 <이름> (Citizens에서 NPC를 먼저 선택하세요)");
            return;
        }
        String name = args[1];
        var selected = net.citizensnpcs.api.CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (selected == null) {
            sender.sendMessage("§cCitizens에서 NPC를 먼저 선택해주세요 (/npc select).");
            return;
        }
        int npcId = selected.getId();
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setNpcId(name, npcId);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§aNPC를 연결했습니다: " + selected.getName() : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("NPC 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleSetMythicMob(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§c사용법: /레이드 몹설정 <이름> <mythicMobId>");
            return;
        }
        String name = args[1];
        String mythicMobId = args[2];
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setMythicMob(name, mythicMobId);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§a몹을 설정했습니다: " + mythicMobId : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("몹 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleSetTicket(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage("§c사용법: /레이드 티켓설정 <이름> <아이템즈어더ID> <수량>");
            return;
        }
        String name = args[1];
        String ticketItemId = args[2];
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c수량은 숫자여야 합니다.");
            return;
        }
        int finalAmount = amount;
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setTicket(name, ticketItemId, finalAmount);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§a입장권을 설정했습니다." : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("티켓 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleSetPartySize(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage("§c사용법: /레이드 인원설정 <이름> <최소> <최대>");
            return;
        }
        String name = args[1];
        int min, max;
        try {
            min = Integer.parseInt(args[2]);
            max = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c최소/최대는 숫자여야 합니다.");
            return;
        }
        int finalMin = min;
        int finalMax = max;
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setPartySize(name, finalMin, finalMax);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§a인원을 설정했습니다." : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("인원 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleSetTimeLimit(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§c사용법: /레이드 제한시간설정 <이름> <초>");
            return;
        }
        String name = args[1];
        int seconds;
        try {
            seconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c초는 숫자여야 합니다.");
            return;
        }
        int finalSeconds = seconds;
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setTimeLimit(name, finalSeconds);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§a제한시간을 설정했습니다." : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("제한시간 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleSetSharedLives(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§c사용법: /레이드 부활횟수설정 <이름> <횟수>");
            return;
        }
        String name = args[1];
        int lives;
        try {
            lives = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c횟수는 숫자여야 합니다.");
            return;
        }
        int finalLives = lives;
        executor.execute(() -> {
            try {
                boolean updated = raidManager.setSharedLives(name, finalLives);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        updated ? "§a부활 횟수를 설정했습니다." : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("부활 횟수 설정 실패: " + e.getMessage());
            }
        });
    }

    private void handleSetRewards(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /레이드 보상설정 <이름>");
            return;
        }
        String name = args[1];
        var raid = raidManager.find(name);
        if (raid.isEmpty()) {
            sender.sendMessage("§c존재하지 않는 레이드입니다: " + name);
            return;
        }
        new ItemGridEditorGui("레이드 보상 - " + name, raid.get().rewardItems(), items -> executor.execute(() -> {
            try {
                raidManager.setRewardItems(name, items);
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("보상 설정 실패: " + e.getMessage());
            }
        })).open(player);
    }

    private void handleSetInstance(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length != 4) {
            sender.sendMessage("§c사용법: /레이드 인스턴스설정 <이름> <슬롯번호> <입장|보스스폰|퇴장|경계1|경계2>");
            return;
        }
        var raid = raidManager.find(args[1]);
        if (raid.isEmpty()) {
            sender.sendMessage("§c존재하지 않는 레이드입니다: " + args[1]);
            return;
        }
        int slotIndex;
        try {
            slotIndex = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c슬롯 번호는 숫자여야 합니다.");
            return;
        }
        String field = switch (args[3]) {
            case "입장" -> "entry";
            case "보스스폰" -> "boss_spawn";
            case "퇴장" -> "exit";
            case "경계1" -> "bound_min";
            case "경계2" -> "bound_max";
            default -> null;
        };
        if (field == null) {
            sender.sendMessage("§c위치 종류는 입장/보스스폰/퇴장/경계1/경계2 중 하나여야 합니다.");
            return;
        }
        Location location = player.getLocation();
        long raidId = raid.get().id();
        String finalField = field;
        executor.execute(() -> {
            try {
                raidManager.setInstanceSlotLocation(raidId, slotIndex, finalField, location);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§a" + args[3] + " 위치를 저장했습니다."));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("인스턴스 좌표 저장 실패: " + e.getMessage());
            }
        });
    }

    private void handleList(CommandSender sender) {
        if (raidManager.all().isEmpty()) {
            sender.sendMessage("§7등록된 레이드가 없습니다.");
            return;
        }
        sender.sendMessage("§6등록된 레이드 목록:");
        for (RaidDefinition raid : raidManager.all()) {
            sender.sendMessage("§7- " + raid.name());
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /레이드 정보 <이름>");
            return;
        }
        var raid = raidManager.find(args[1]);
        if (raid.isEmpty()) {
            sender.sendMessage("§c존재하지 않는 레이드입니다: " + args[1]);
            return;
        }
        RaidDefinition r = raid.get();
        sender.sendMessage("§6[" + r.name() + "] §7NPC ID: " + (r.npcId() < 0 ? "미설정" : r.npcId())
                + " / 몹: " + r.mythicMobId()
                + " / 티켓: " + r.ticketItemId() + " x" + r.ticketAmount()
                + " / 인원: " + r.minPartySize() + "~" + r.maxPartySize()
                + " / 제한시간: " + r.timeLimitSeconds() + "초"
                + " / 부활: " + r.sharedLives() + "회"
                + " / 인스턴스: " + r.instanceCount() + "개"
                + " / 보상: " + r.rewardItems().size() + "종");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§c사용법: /레이드 삭제 <이름>");
            return;
        }
        String name = args[1];
        executor.execute(() -> {
            try {
                boolean deleted = raidManager.delete(name);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                        deleted ? "§a레이드를 삭제했습니다: " + name : "§c존재하지 않는 레이드입니다: " + name));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("레이드 삭제 실패: " + e.getMessage());
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0])) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && !args[0].equals("생성")) {
            for (RaidDefinition raid : raidManager.all()) {
                if (raid.name().startsWith(args[1])) {
                    completions.add(raid.name());
                }
            }
        } else if (args.length == 4 && args[0].equals("인스턴스설정")) {
            for (String field : SLOT_FIELDS) {
                if (field.startsWith(args[3])) {
                    completions.add(field);
                }
            }
        }
        return completions;
    }
}
```

Check `com.yeowool.core.api.gui.ItemGridEditorGui`'s exact constructor parameter order (`String title, List<ItemStack> existingItems, Consumer<List<ItemStack>> onSave)` — confirmed in this session when the quest module used it — before this step; the call above assumes that exact shape.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew.bat :yeowool-raid:compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/RaidAdminCommand.java
git commit -m "Add /레이드 admin command"
```

---

### Task 11: Main plugin class and wiring

**Files:**
- Create: `yeowool-raid/src/main/java/com/yeowool/raid/YeowoolRaid.java`

**Interfaces:**
- Consumes: every class from Tasks 2-10.
- Produces: the loadable plugin jar.

- [ ] **Step 1: Write `YeowoolRaid.java`**

```java
package com.yeowool.raid;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.raid.database.RaidDefinitionRepository;
import com.yeowool.raid.database.RaidInstanceRepository;
import com.yeowool.raid.database.RaidSchemaInitializer;
import com.yeowool.raid.party.RaidPartyLookup;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class YeowoolRaid extends JavaPlugin {

    private ExecutorService executor;

    @Override
    public void onEnable() {
        YeowoolCoreAPI core = Bukkit.getServicesManager().load(YeowoolCoreAPI.class);
        if (core == null) {
            getLogger().severe("YeowoolCore API를 찾을 수 없습니다. YeowoolCore가 먼저 로드되어야 합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            getLogger().severe("Citizens가 설치되어 있지 않습니다. YeowoolRaid는 Citizens 없이 동작할 수 없습니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            getLogger().severe("MythicMobs가 설치되어 있지 않습니다. YeowoolRaid는 MythicMobs 없이 동작할 수 없습니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ConfigMerger.mergeDefaults(this, "config.yml");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "YeowoolRaid-Worker");
            thread.setDaemon(true);
            return thread;
        });

        try {
            RaidSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("레이드 데이터베이스 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        RaidDefinitionRepository definitionRepository = new RaidDefinitionRepository(core.dataSource());
        RaidInstanceRepository instanceRepository = new RaidInstanceRepository(core.dataSource());
        RaidManager raidManager = new RaidManager(this, definitionRepository, instanceRepository, executor);
        try {
            raidManager.loadAll();
        } catch (Exception e) {
            getLogger().severe("레이드 로드 실패: " + e.getMessage());
        }

        RaidHudService hudService = new RaidHudService(
                getConfig().getString("betterhud.boss-warning-popup", "yeowool_raid_boss_warning"),
                getConfig().getString("betterhud.result-popup", "yeowool_raid_result"));
        RaidPartyLookup partyLookup = new RaidPartyLookup(core.dataSource());
        RaidEntryService entryService = new RaidEntryService(this, raidManager, partyLookup, hudService, executor);

        getServer().getPluginManager().registerEvents(new RaidNpcListener(raidManager, entryService), this);
        getServer().getPluginManager().registerEvents(new RaidBossDeathListener(raidManager, core, hudService), this);
        getServer().getPluginManager().registerEvents(new RaidPartyDeathListener(raidManager, hudService), this);
        new RaidTimeoutTask(raidManager, hudService).start(this);

        var command = getCommand("레이드");
        if (command != null) {
            RaidAdminCommand adminCommand = new RaidAdminCommand(this, raidManager, executor);
            command.setExecutor(adminCommand);
            command.setTabCompleter(adminCommand);
        }

        boolean betterHudEnabled = Bukkit.getPluginManager().isPluginEnabled("BetterHud");
        getLogger().info("YeowoolRaid가 활성화되었습니다." + (betterHudEnabled ? "" : " (BetterHud 없이 채팅 폴백 모드)"));
    }

    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
```

`ConfigMerger.mergeDefaults(this, "config.yml")` (`yeowool-core/src/main/java/com/yeowool/core/util/ConfigMerger.java`, signature `static void mergeDefaults(JavaPlugin, String)`) is this codebase's established config-loading idiom — `yeowool-quest`'s `YeowoolQuest.onEnable()` already uses it instead of Paper's vanilla `saveDefaultConfig()`, and the code above follows the same pattern.

- [ ] **Step 2: Build the whole module**

Run: `./gradlew.bat :yeowool-raid:build -x test --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Build the entire suite to check for regressions**

Run: `./gradlew.bat build -x test --console=plain`
Expected: `BUILD SUCCESSFUL` across all modules.

- [ ] **Step 4: Commit**

```bash
git add yeowool-raid/src/main/java/com/yeowool/raid/YeowoolRaid.java
git commit -m "Wire up YeowoolRaid main plugin class"
```

---

### Task 12: Deploy and manual verification

**Files:**
- None created — this task copies the built jar and exercises the feature live.

- [ ] **Step 1: Copy the built jar to all three servers**

```bash
for srv in lobby town wild; do
  cp "yeowool-raid/build/libs/yeowool-raid-1.0.0-SNAPSHOT.jar" "/c/YEOWOOL/$srv/plugins/yeowool-raid-1.0.0-SNAPSHOT.jar"
done
```

- [ ] **Step 2: Restart all three servers**

(Manual — the user restarts servers themselves per this project's standing convention; do not use RCON `stop`.)

- [ ] **Step 3: Confirm clean enable on all three**

After restart, check each server's `logs/latest.log` for `YeowoolRaid가 활성화되었습니다.` with no preceding `SEVERE` lines from `YeowoolRaid`.

- [ ] **Step 4: Create a test raid definition in-game**

```
/레이드 생성 테스트레이드
/레이드 몹설정 테스트레이드 <실제로 설치된 MythicMobs 몹 ID>
/레이드 티켓설정 테스트레이드 <실제 ItemsAdder 아이템 ID> 1
/레이드 인원설정 테스트레이드 1 6
/레이드 제한시간설정 테스트레이드 1200
/레이드 부활횟수설정 테스트레이드 5
/레이드 보상설정 테스트레이드   # GUI에 아이템 몇 개 넣고 닫기
```

Stand at the arena's entry point, run `/레이드 인스턴스설정 테스트레이드 0 입장`; repeat at the boss-spawn point, exit point, and the two corners of the arena's bounding box (`보스스폰`/`퇴장`/`경계1`/`경계2`).

- [ ] **Step 5: Place a Citizens NPC and link it to the test raid**

Spawn a Citizens NPC (`/npc create <이름>`), select it (Citizens auto-selects on create, or `/npc select`), then run `/레이드 npc설정 테스트레이드`.

- [ ] **Step 6: Form a party and enter the raid**

With a party (or solo, since `min_party_size` was set to 1 above) holding the required ticket item, right-click the NPC, pick the test raid from the GUI, and confirm: teleport to the entry point, boss spawns at the boss-spawn point, ticket is consumed.

- [ ] **Step 7: Verify win path**

Kill the boss (or have an admin do so). Confirm: chat message + (if BetterHud installed) popup fires, mailbox contains one rolled reward item, party is teleported to the exit point, and a second party can now enter the same instance slot (`/레이드 인스턴스설정`'s slot 0 becomes available again — verify by entering with a second test party).

- [ ] **Step 8: Verify loss paths**

Run a second test: let the shared lives hit 0 (or lower `부활횟수설정` to 1 for a fast test) and confirm defeat is announced and no reward is mailed. Separately, lower `제한시간설정` to a short value (e.g. 30) and confirm the timeout task ends the session as a loss without requiring anyone to die.

- [ ] **Step 9: Log the feature in `update.md` and commit**

Follow this project's established `update.md` convention (see the entries written earlier this session for the quest module and the port-conflict fix) — date-stamped section, plain-language summary of what was built and what still needs BetterHud popup files / a real purchased MythicMobs boss pack from the user.

```bash
git add update.md
git commit -m "Log boss raid system deployment"
```

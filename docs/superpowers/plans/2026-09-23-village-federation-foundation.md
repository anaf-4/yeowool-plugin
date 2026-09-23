# 마을 연합 시스템 (1단계: 기반) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new `yeowool-federation` module letting land owners create/join/leave village federations, with leader/deputy roles, leadership transfer, and a confirmed disband flow.

**Architecture:** New standalone Gradle module, same shape as `yeowool-raid`. No compile dependency on `yeowool-land` — reads `yw_lands`/`yw_players` via raw JDBC through `YeowoolCoreAPI.dataSource()`, exactly like `yeowool-raid` reads `yw_party`/`yw_party_member` without depending on `yeowool-community`. Own schema (3 tables), own in-memory-cached manager backed by the DB, own commands under `/연합`.

**Tech Stack:** Java 21, Paper 1.21.4 API, raw JDBC (HikariCP pool via core), JUnit 5 for pure-logic classes only.

**Spec:** `docs/superpowers/specs/2026-09-23-village-federation-foundation-design.md`

## Global Constraints

- Land ownership is 1:1 — `yw_lands.owner_uuid` — a player owns at most one land (confirmed against `yeowool-land`'s existing `/토지` singular design).
- A land belongs to at most one federation — enforced by `yw_federation_members.land_id` being the table's own PRIMARY KEY, not a composite key.
- No compile dependency on `yeowool-land` or `yeowool-community` — all cross-module reads are raw JDBC against their tables (`yw_lands`, `yw_players`).
- All blocking JDBC calls run off the main thread via an `ExecutorService`, with results marshalled back via `Bukkit.getScheduler().runTask(plugin, ...)` before touching any Bukkit API — same pattern as `yeowool-raid`'s `RaidManager`/`RaidAdminCommand`.
- Deputy cap = `floor(federation.level / 10)`. Level always starts at 1 and this plan never changes it (leveling is phase 3) — so the cap is always 0 for every federation this phase creates. The command to appoint a deputy must still exist and correctly report "정원이 찼습니다" when the cap is 0.
- Only pure-logic classes with zero Bukkit/JDBC imports get JUnit tests (established convention from the raid system build) — repositories, the manager, and commands are not unit-tested.
- Git hygiene: never `git add -A`/`git add .`, stage only the files a task actually touches. Never amend, always new commits.

---

### Task 1: Module scaffold + database schema

**Files:**
- Create: `yeowool-federation/build.gradle.kts`
- Create: `yeowool-federation/src/main/resources/plugin.yml`
- Create: `yeowool-federation/src/main/resources/config.yml`
- Create: `yeowool-federation/src/main/resources/messages.yml`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/database/FederationSchemaInitializer.java`
- Modify: `settings.gradle.kts:1-40` (add `include("yeowool-federation")`)

**Interfaces:**
- Produces: `FederationSchemaInitializer.initialize(DataSource dataSource) throws SQLException` — creates `yw_federations`, `yw_federation_members`, `yw_federation_applications` if missing.

- [ ] **Step 1: Check `settings.gradle.kts` for the existing module list**

Run: read `settings.gradle.kts` and find the `include(...)` calls for `yeowool-raid`/`yeowool-market` to copy the exact style.

- [ ] **Step 2: Add the new module to `settings.gradle.kts`**

Add `include("yeowool-federation")` alongside the existing module includes, same line style as the others.

- [ ] **Step 3: Create `yeowool-federation/build.gradle.kts`**

Copy `yeowool-raid/build.gradle.kts` verbatim except the project name/description, since it has the exact same dependency shape needed here (core `compileOnly`, no other Yeowool module dependency, Paper API, JUnit 5 for the `test` source set). Read `yeowool-raid/build.gradle.kts` first and adapt it — do not guess the Paper/JUnit versions, copy them.

- [ ] **Step 4: Create `yeowool-federation/src/main/resources/plugin.yml`**

```yaml
name: YeowoolFederation
version: '${version}'
main: com.yeowool.federation.YeowoolFederation
api-version: '1.21'
depend: [YeowoolCore]
load: POSTWORLD

commands:
  연합:
    description: 마을 연합을 생성/관리합니다
```

- [ ] **Step 5: Create `yeowool-federation/src/main/resources/config.yml`**

```yaml
# YeowoolFederation 설정
# 지금은 별도 설정값이 없습니다 — 2~3단계(채팅/공유자원)에서 여기에 값이 추가됩니다.
```

- [ ] **Step 6: Create `yeowool-federation/src/main/resources/messages.yml`**

```yaml
federation:
  player-only: "§c플레이어만 사용할 수 있습니다."
  usage: "§c사용법: /연합 <생성|가입신청|신청목록|수락|거절|탈퇴|추방|부연합장임명|부연합장해임|위임|폐쇄|소개글|정보|목록>"
  no-land: "§c소유한 토지가 없습니다."
  already-in-federation: "§c이미 다른 연합에 소속되어 있습니다."
  name-taken: "§c이미 사용 중인 연합 이름입니다: <name>"
  create-success: "§a연합 '<name>'을 생성했습니다."
  not-found: "§c존재하지 않는 연합입니다: <name>"
  not-your-federation: "§c그 연합의 연합장/부연합장이 아닙니다."
  leader-only: "§c연합장만 할 수 있습니다."
  already-member: "§c이미 그 연합에 소속되어 있습니다."
  already-applied: "§c이미 그 연합에 가입 신청을 넣었습니다."
  apply-success: "§a'<name>' 연합에 가입 신청을 보냈습니다."
  no-applications: "§7대기 중인 가입 신청이 없습니다."
  application-list-header: "§6대기 중인 가입 신청:"
  application-list-line: "§7- <land>"
  application-not-found: "§c그 토지의 가입 신청을 찾을 수 없습니다."
  accept-success: "§a'<land>'의 가입을 승인했습니다."
  reject-success: "§7'<land>'의 가입 신청을 거절했습니다."
  leader-must-transfer-first: "§c탈퇴하기 전에 먼저 연합장을 다른 사람에게 위임해주세요."
  leave-success: "§7연합에서 탈퇴했습니다."
  target-no-land: "§c그 플레이어는 소유한 토지가 없습니다."
  target-not-member: "§c그 토지는 이 연합 소속이 아닙니다."
  cannot-kick-deputy: "§c부연합장은 연합장만 추방할 수 있습니다."
  kick-success: "§7'<land>'을(를) 연합에서 추방했습니다."
  deputy-cap-reached: "§c부연합장 정원이 꽉 찼습니다. (연합 레벨 <level>, 정원 <cap>명)"
  already-deputy: "§c이미 부연합장입니다."
  appoint-deputy-success: "§a'<land>'을(를) 부연합장으로 임명했습니다."
  not-deputy: "§c부연합장이 아닙니다."
  dismiss-deputy-success: "§7'<land>'의 부연합장 지위를 해제했습니다."
  transfer-target-not-member: "§c연합에 소속된 토지의 소유주에게만 위임할 수 있습니다."
  transfer-success: "§a연합장을 '<land>'에게 위임했습니다."
  disband-confirm-title: "연합 폐쇄 확인"
  disband-success: "§c연합 '<name>'을(를) 폐쇄했습니다."
  description-too-long: "§c소개글은 255자를 넘을 수 없습니다."
  description-success: "§a연합 소개글을 수정했습니다."
  info-not-found: "§c소속된 연합이 없고, 연합 이름도 입력하지 않았습니다."
  list-empty: "§7생성된 연합이 없습니다."
  list-header: "§6전체 연합 목록:"
  list-line: "§7- <name> (Lv.<level>, <count>개 마을)"
```

- [ ] **Step 7: Write `FederationSchemaInitializer.java`**

```java
package com.yeowool.federation.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Creates YeowoolFederation's own tables on the shared database. Idempotent DDL, same approach as every other Yeowool module's schema initializer. */
public final class FederationSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_federations (
                id CHAR(36) NOT NULL PRIMARY KEY,
                name VARCHAR(32) NOT NULL UNIQUE,
                description VARCHAR(255) NULL,
                level INT NOT NULL DEFAULT 1,
                leader_land_id CHAR(36) NOT NULL,
                created_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_federation_members (
                federation_id CHAR(36) NOT NULL,
                land_id CHAR(36) NOT NULL PRIMARY KEY,
                role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
                joined_at BIGINT NOT NULL,
                INDEX idx_federation (federation_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_federation_applications (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                federation_id CHAR(36) NOT NULL,
                land_id CHAR(36) NOT NULL,
                applied_at BIGINT NOT NULL,
                UNIQUE KEY uniq_pending (federation_id, land_id),
                INDEX idx_federation (federation_id),
                INDEX idx_land (land_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private FederationSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.executeUpdate(ddl);
            }
        }
    }
}
```

Note: `role` is stored as `VARCHAR(16)` rather than SQL `ENUM(...)` — MySQL's `ENUM` type is awkward to alter later (phase 2/3 might not need to, but plain strings read back via Java's own enum are simpler and match how `yw_auction_listings.currency` already stores `CurrencyType` as a plain string). This deliberately diverges from the design doc's SQL sketch, which used `ENUM` — the design doc described intent, not literal DDL to copy verbatim.

- [ ] **Step 8: Build to confirm the module compiles with just this skeleton**

Run: `./gradlew :yeowool-federation:build -x test`
Expected: `BUILD SUCCESSFUL` (nothing calls `FederationSchemaInitializer` yet, but it must compile standalone).

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts yeowool-federation/build.gradle.kts yeowool-federation/src/main/resources/plugin.yml yeowool-federation/src/main/resources/config.yml yeowool-federation/src/main/resources/messages.yml yeowool-federation/src/main/java/com/yeowool/federation/database/FederationSchemaInitializer.java
git commit -m "Scaffold yeowool-federation module with DB schema"
```

---

### Task 2: Domain model + pure-logic rules (with JUnit tests)

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/FederationRole.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/Federation.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/FederationMember.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/FederationRules.java`
- Test: `yeowool-federation/src/test/java/com/yeowool/federation/FederationRulesTest.java`

**Interfaces:**
- Produces: `FederationRole` enum (`LEADER, DEPUTY, MEMBER`); `Federation` record; `FederationMember` record; `FederationRules.deputyCap(int level) -> int`; `FederationRules.canApprove(FederationRole) -> boolean`; `FederationRules.canKick(FederationRole actor, FederationRole target) -> boolean`.

- [ ] **Step 1: Write `FederationRole.java`**

```java
package com.yeowool.federation;

public enum FederationRole {
    LEADER,
    DEPUTY,
    MEMBER
}
```

- [ ] **Step 2: Write `Federation.java`**

```java
package com.yeowool.federation;

import java.util.UUID;

public record Federation(
        UUID id,
        String name,
        String description,
        int level,
        UUID leaderLandId,
        long createdAt
) {
    public Federation withDescription(String newDescription) {
        return new Federation(id, name, newDescription, level, leaderLandId, createdAt);
    }

    public Federation withLeaderLandId(UUID newLeaderLandId) {
        return new Federation(id, name, description, level, newLeaderLandId, createdAt);
    }
}
```

- [ ] **Step 3: Write `FederationMember.java`**

```java
package com.yeowool.federation;

import java.util.UUID;

public record FederationMember(
        UUID federationId,
        UUID landId,
        FederationRole role,
        long joinedAt
) {
}
```

- [ ] **Step 4: Write the failing test for `FederationRules`**

```java
package com.yeowool.federation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationRulesTest {

    @Test
    void deputyCapIsLevelDividedByTenFlooredDown() {
        assertEquals(0, FederationRules.deputyCap(1));
        assertEquals(0, FederationRules.deputyCap(9));
        assertEquals(1, FederationRules.deputyCap(10));
        assertEquals(1, FederationRules.deputyCap(19));
        assertEquals(2, FederationRules.deputyCap(25));
        assertEquals(3, FederationRules.deputyCap(30));
    }

    @Test
    void leaderAndDeputyCanApprove() {
        assertTrue(FederationRules.canApprove(FederationRole.LEADER));
        assertTrue(FederationRules.canApprove(FederationRole.DEPUTY));
        assertFalse(FederationRules.canApprove(FederationRole.MEMBER));
    }

    @Test
    void onlyLeaderCanKickADeputy() {
        assertTrue(FederationRules.canKick(FederationRole.LEADER, FederationRole.DEPUTY));
        assertFalse(FederationRules.canKick(FederationRole.DEPUTY, FederationRole.DEPUTY));
    }

    @Test
    void leaderAndDeputyCanKickAPlainMember() {
        assertTrue(FederationRules.canKick(FederationRole.LEADER, FederationRole.MEMBER));
        assertTrue(FederationRules.canKick(FederationRole.DEPUTY, FederationRole.MEMBER));
        assertFalse(FederationRules.canKick(FederationRole.MEMBER, FederationRole.MEMBER));
    }

    @Test
    void noOneCanKickTheLeader() {
        assertFalse(FederationRules.canKick(FederationRole.LEADER, FederationRole.LEADER));
        assertFalse(FederationRules.canKick(FederationRole.DEPUTY, FederationRole.LEADER));
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `./gradlew :yeowool-federation:test --tests FederationRulesTest`
Expected: FAIL — `FederationRules` does not exist yet.

- [ ] **Step 6: Write `FederationRules.java`**

```java
package com.yeowool.federation;

/**
 * Pure permission/limit logic, zero Bukkit/JDBC — kept separate from
 * {@link FederationManager} specifically so it's unit-testable (established
 * convention: only zero-dependency classes get JUnit tests in this project).
 */
public final class FederationRules {

    private FederationRules() {
    }

    public static int deputyCap(int level) {
        return level / 10;
    }

    public static boolean canApprove(FederationRole role) {
        return role == FederationRole.LEADER || role == FederationRole.DEPUTY;
    }

    /** Deputies can kick plain members but not each other or the leader; only the leader can kick a deputy. */
    public static boolean canKick(FederationRole actor, FederationRole target) {
        if (target == FederationRole.LEADER) {
            return false;
        }
        if (target == FederationRole.DEPUTY) {
            return actor == FederationRole.LEADER;
        }
        return actor == FederationRole.LEADER || actor == FederationRole.DEPUTY;
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :yeowool-federation:test --tests FederationRulesTest`
Expected: PASS, all 5 tests green.

- [ ] **Step 8: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/FederationRole.java yeowool-federation/src/main/java/com/yeowool/federation/Federation.java yeowool-federation/src/main/java/com/yeowool/federation/FederationMember.java yeowool-federation/src/main/java/com/yeowool/federation/FederationRules.java yeowool-federation/src/test/java/com/yeowool/federation/FederationRulesTest.java
git commit -m "Add federation domain model and pure permission rules"
```

---

### Task 3: Land lookup (cross-module raw JDBC)

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/land/LandLookup.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/land/LandInfo.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `LandInfo` record (`UUID id, UUID ownerUuid, String ownerUsername, String landName, int landLevel`); `LandLookup(DataSource)` with `findByOwnerUuid(UUID) throws SQLException -> Optional<LandInfo>`, `findByOwnerUsername(String) throws SQLException -> Optional<LandInfo>`, `findById(UUID) throws SQLException -> Optional<LandInfo>`. Blocking — callers run these off the main thread, same as every other repository in this codebase.

- [ ] **Step 1: Write `LandInfo.java`**

```java
package com.yeowool.federation.land;

import java.util.UUID;

/** A read-only snapshot of a `yeowool-land` land, fetched via raw JDBC (no compile dependency on yeowool-land). */
public record LandInfo(
        UUID id,
        UUID ownerUuid,
        String ownerUsername,
        String landName,
        int landLevel
) {
}
```

- [ ] **Step 2: Write `LandLookup.java`**

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
 * Reads land ownership straight off `yw_lands`/`yw_players` (yeowool-land's
 * and yeowool-core's own tables) without a compile dependency on
 * yeowool-land — same cross-module pattern yeowool-raid uses for
 * `yw_party`/`yw_party_member`. Land ownership is 1:1 (a player owns at most
 * one land), and land level lives on the OWNER's `yw_players.land_level`
 * row, not on `yw_lands` itself.
 */
public final class LandLookup {

    private static final String SELECT_BASE =
            "SELECT l.id, l.owner_uuid, p.username, l.name, p.land_level " +
                    "FROM yw_lands l JOIN yw_players p ON p.uuid = l.owner_uuid ";

    private final DataSource dataSource;

    public LandLookup(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<LandInfo> findByOwnerUuid(UUID ownerUuid) throws SQLException {
        return querySingle(SELECT_BASE + "WHERE l.owner_uuid = ?", ownerUuid.toString());
    }

    public Optional<LandInfo> findByOwnerUsername(String username) throws SQLException {
        return querySingle(SELECT_BASE + "WHERE p.username = ?", username);
    }

    public Optional<LandInfo> findById(UUID landId) throws SQLException {
        return querySingle(SELECT_BASE + "WHERE l.id = ?", landId.toString());
    }

    private Optional<LandInfo> querySingle(String sql, String param) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, param);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new LandInfo(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("username"),
                        rs.getString("name"),
                        rs.getInt("land_level")
                ));
            }
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :yeowool-federation:build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/land/LandLookup.java yeowool-federation/src/main/java/com/yeowool/federation/land/LandInfo.java
git commit -m "Add cross-module land lookup via raw JDBC"
```

---

### Task 4: Federation repository

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/database/FederationRepository.java`

**Interfaces:**
- Consumes: `Federation`, `FederationMember`, `FederationRole` (Task 2).
- Produces: `FederationRepository(DataSource)` with:
  - `insert(Federation) throws SQLException`
  - `update(Federation) throws SQLException` (name is immutable after creation — only `description`/`leaderLandId` ever change; `level` is untouched this phase)
  - `delete(UUID federationId) throws SQLException` (cascades members + applications for that federation)
  - `findById(UUID) throws SQLException -> Optional<Federation>`
  - `findByName(String) throws SQLException -> Optional<Federation>`
  - `loadAll() throws SQLException -> List<Federation>`
  - `insertMember(FederationMember) throws SQLException`
  - `updateMemberRole(UUID landId, FederationRole newRole) throws SQLException`
  - `deleteMember(UUID landId) throws SQLException`
  - `findMemberByLandId(UUID landId) throws SQLException -> Optional<FederationMember>`
  - `loadMembers(UUID federationId) throws SQLException -> List<FederationMember>`
  - `insertApplication(UUID federationId, UUID landId, long appliedAt) throws SQLException`
  - `deleteApplication(UUID federationId, UUID landId) throws SQLException`
  - `deleteAllApplicationsForLand(UUID landId) throws SQLException` (used when a land's application to one federation is accepted — its applications to every *other* federation are cancelled)
  - `applicationExists(UUID federationId, UUID landId) throws SQLException -> boolean`
  - `loadApplicantLandIds(UUID federationId) throws SQLException -> List<UUID>`

- [ ] **Step 1: Write `FederationRepository.java`**

```java
package com.yeowool.federation.database;

import com.yeowool.federation.Federation;
import com.yeowool.federation.FederationMember;
import com.yeowool.federation.FederationRole;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Blocking JDBC access to `yw_federations`/`yw_federation_members`/`yw_federation_applications`. Must only be called off the main thread. */
public final class FederationRepository {

    private final DataSource dataSource;

    public FederationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(Federation federation) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_federations (id, name, description, level, leader_land_id, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, federation.id().toString());
            insert.setString(2, federation.name());
            insert.setString(3, federation.description());
            insert.setInt(4, federation.level());
            insert.setString(5, federation.leaderLandId().toString());
            insert.setLong(6, federation.createdAt());
            insert.executeUpdate();
        }
    }

    public void update(Federation federation) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_federations SET description = ?, leader_land_id = ? WHERE id = ?")) {
            update.setString(1, federation.description());
            update.setString(2, federation.leaderLandId().toString());
            update.setString(3, federation.id().toString());
            update.executeUpdate();
        }
    }

    public void delete(UUID federationId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement deleteMembers = connection.prepareStatement(
                    "DELETE FROM yw_federation_members WHERE federation_id = ?")) {
                deleteMembers.setString(1, federationId.toString());
                deleteMembers.executeUpdate();
            }
            try (PreparedStatement deleteApplications = connection.prepareStatement(
                    "DELETE FROM yw_federation_applications WHERE federation_id = ?")) {
                deleteApplications.setString(1, federationId.toString());
                deleteApplications.executeUpdate();
            }
            try (PreparedStatement deleteFederation = connection.prepareStatement(
                    "DELETE FROM yw_federations WHERE id = ?")) {
                deleteFederation.setString(1, federationId.toString());
                deleteFederation.executeUpdate();
            }
        }
    }

    public Optional<Federation> findById(UUID federationId) throws SQLException {
        return querySingleFederation("SELECT id, name, description, level, leader_land_id, created_at FROM yw_federations WHERE id = ?", federationId.toString());
    }

    public Optional<Federation> findByName(String name) throws SQLException {
        return querySingleFederation("SELECT id, name, description, level, leader_land_id, created_at FROM yw_federations WHERE name = ?", name);
    }

    public List<Federation> loadAll() throws SQLException {
        List<Federation> federations = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, name, description, level, leader_land_id, created_at FROM yw_federations");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                federations.add(readFederation(rs));
            }
        }
        return federations;
    }

    private Optional<Federation> querySingleFederation(String sql, String param) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, param);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readFederation(rs));
            }
        }
    }

    private Federation readFederation(ResultSet rs) throws SQLException {
        return new Federation(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("level"),
                UUID.fromString(rs.getString("leader_land_id")),
                rs.getLong("created_at")
        );
    }

    public void insertMember(FederationMember member) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_federation_members (federation_id, land_id, role, joined_at) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, member.federationId().toString());
            insert.setString(2, member.landId().toString());
            insert.setString(3, member.role().name());
            insert.setLong(4, member.joinedAt());
            insert.executeUpdate();
        }
    }

    public void updateMemberRole(UUID landId, FederationRole newRole) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_federation_members SET role = ? WHERE land_id = ?")) {
            update.setString(1, newRole.name());
            update.setString(2, landId.toString());
            update.executeUpdate();
        }
    }

    public void deleteMember(UUID landId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_federation_members WHERE land_id = ?")) {
            delete.setString(1, landId.toString());
            delete.executeUpdate();
        }
    }

    public Optional<FederationMember> findMemberByLandId(UUID landId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT federation_id, land_id, role, joined_at FROM yw_federation_members WHERE land_id = ?")) {
            select.setString(1, landId.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readMember(rs));
            }
        }
    }

    public List<FederationMember> loadMembers(UUID federationId) throws SQLException {
        List<FederationMember> members = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT federation_id, land_id, role, joined_at FROM yw_federation_members WHERE federation_id = ?")) {
            select.setString(1, federationId.toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    members.add(readMember(rs));
                }
            }
        }
        return members;
    }

    private FederationMember readMember(ResultSet rs) throws SQLException {
        return new FederationMember(
                UUID.fromString(rs.getString("federation_id")),
                UUID.fromString(rs.getString("land_id")),
                FederationRole.valueOf(rs.getString("role")),
                rs.getLong("joined_at")
        );
    }

    public void insertApplication(UUID federationId, UUID landId, long appliedAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_federation_applications (federation_id, land_id, applied_at) VALUES (?, ?, ?)")) {
            insert.setString(1, federationId.toString());
            insert.setString(2, landId.toString());
            insert.setLong(3, appliedAt);
            insert.executeUpdate();
        }
    }

    public void deleteApplication(UUID federationId, UUID landId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_federation_applications WHERE federation_id = ? AND land_id = ?")) {
            delete.setString(1, federationId.toString());
            delete.setString(2, landId.toString());
            delete.executeUpdate();
        }
    }

    public void deleteAllApplicationsForLand(UUID landId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_federation_applications WHERE land_id = ?")) {
            delete.setString(1, landId.toString());
            delete.executeUpdate();
        }
    }

    public boolean applicationExists(UUID federationId, UUID landId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT 1 FROM yw_federation_applications WHERE federation_id = ? AND land_id = ?")) {
            select.setString(1, federationId.toString());
            select.setString(2, landId.toString());
            try (ResultSet rs = select.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<UUID> loadApplicantLandIds(UUID federationId) throws SQLException {
        List<UUID> landIds = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT land_id FROM yw_federation_applications WHERE federation_id = ?")) {
            select.setString(1, federationId.toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    landIds.add(UUID.fromString(rs.getString("land_id")));
                }
            }
        }
        return landIds;
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :yeowool-federation:build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/database/FederationRepository.java
git commit -m "Add federation repository (raw JDBC CRUD)"
```

---

### Task 5: FederationManager (business logic orchestration)

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/FederationManager.java`

**Interfaces:**
- Consumes: `Federation`, `FederationMember`, `FederationRole`, `FederationRules` (Task 2); `FederationRepository` (Task 4). (`LandLookup`/`LandInfo` from Task 3 are NOT consumed here — land resolution happens in the command layer, which passes plain `UUID landId` values in. A first pass of this task shipped an unused `LandLookup` constructor parameter; Task 5's fix round removed it — see ledger.)
- Produces: `FederationManager(FederationRepository)` with the methods below. **Every method is blocking (does real JDBC I/O) — callers must run these off the main thread**, same convention as `RaidManager`. Each returns a small sealed-style result so the caller can map it straight to a message key without re-deriving state.

```java
CreateResult create(UUID leaderLandId, String federationName) throws SQLException;
// CreateResult: SUCCESS, LAND_ALREADY_IN_FEDERATION, NAME_TAKEN

ApplyResult applyToJoin(UUID applicantLandId, String federationName) throws SQLException;
// ApplyResult: SUCCESS, FEDERATION_NOT_FOUND, ALREADY_MEMBER, ALREADY_APPLIED

List<UUID> listApplicants(UUID actingLandId) throws SQLException;
// throws IllegalStateException("not a member") / IllegalStateException("cannot approve") the same
// way as approve/reject below, so callers all funnel through one permission-denied message.

ApprovalResult approve(UUID actingLandId, UUID applicantLandId) throws SQLException;
// ApprovalResult: SUCCESS, NOT_AUTHORIZED, APPLICATION_NOT_FOUND

ApprovalResult reject(UUID actingLandId, UUID applicantLandId) throws SQLException;
// same enum as approve — REJECT never fails differently from APPROVE

LeaveResult leave(UUID landId) throws SQLException;
// LeaveResult: SUCCESS, NOT_A_MEMBER, LEADER_MUST_TRANSFER_FIRST

KickResult kick(UUID actingLandId, UUID targetLandId) throws SQLException;
// KickResult: SUCCESS, NOT_AUTHORIZED, TARGET_NOT_MEMBER, TARGET_NOT_KICKABLE

AppointResult appointDeputy(UUID actingLandId, UUID targetLandId) throws SQLException;
// AppointResult: SUCCESS, NOT_LEADER, TARGET_NOT_MEMBER, ALREADY_DEPUTY, CAP_REACHED

DismissResult dismissDeputy(UUID actingLandId, UUID targetLandId) throws SQLException;
// DismissResult: SUCCESS, NOT_LEADER, TARGET_NOT_DEPUTY

TransferResult transferLeadership(UUID actingLandId, UUID targetLandId) throws SQLException;
// TransferResult: SUCCESS, NOT_LEADER, TARGET_NOT_MEMBER

DisbandResult disband(UUID actingLandId) throws SQLException;
// DisbandResult: SUCCESS, NOT_LEADER

DescriptionResult updateDescription(UUID actingLandId, String newDescription) throws SQLException;
// DescriptionResult: SUCCESS, NOT_LEADER, TOO_LONG

Optional<Federation> findByName(String name) throws SQLException;
Optional<Federation> findByLandId(UUID landId) throws SQLException;
List<Federation> listAll() throws SQLException;
List<FederationMember> membersOf(UUID federationId) throws SQLException;
```

- [ ] **Step 1: Write `FederationManager.java`**

```java
package com.yeowool.federation;

import com.yeowool.federation.database.FederationRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All business logic for federations, backed directly by {@link FederationRepository}
 * (no in-memory cache — federations are edited rarely enough, and read
 * rarely enough per-command, that a straight DB round trip per call is
 * simpler than keeping a cache consistent across 3 servers sharing one DB,
 * the way {@code yeowool-raid}'s RaidManager has to for its much hotter
 * session state). Every method here does blocking JDBC I/O — call off the
 * main thread only.
 */
public final class FederationManager {

    private static final int DESCRIPTION_MAX_LENGTH = 255;

    private final FederationRepository repository;

    public FederationManager(FederationRepository repository) {
        this.repository = repository;
    }

    public enum CreateResult { SUCCESS, LAND_ALREADY_IN_FEDERATION, NAME_TAKEN }

    public CreateResult create(UUID leaderLandId, String federationName) throws SQLException {
        if (repository.findMemberByLandId(leaderLandId).isPresent()) {
            return CreateResult.LAND_ALREADY_IN_FEDERATION;
        }
        if (repository.findByName(federationName).isPresent()) {
            return CreateResult.NAME_TAKEN;
        }
        UUID federationId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        repository.insert(new Federation(federationId, federationName, null, 1, leaderLandId, now));
        repository.insertMember(new FederationMember(federationId, leaderLandId, FederationRole.LEADER, now));
        return CreateResult.SUCCESS;
    }

    public enum ApplyResult { SUCCESS, FEDERATION_NOT_FOUND, ALREADY_MEMBER, ALREADY_APPLIED }

    public ApplyResult applyToJoin(UUID applicantLandId, String federationName) throws SQLException {
        Optional<Federation> federation = repository.findByName(federationName);
        if (federation.isEmpty()) {
            return ApplyResult.FEDERATION_NOT_FOUND;
        }
        if (repository.findMemberByLandId(applicantLandId).isPresent()) {
            return ApplyResult.ALREADY_MEMBER;
        }
        UUID federationId = federation.get().id();
        if (repository.applicationExists(federationId, applicantLandId)) {
            return ApplyResult.ALREADY_APPLIED;
        }
        repository.insertApplication(federationId, applicantLandId, System.currentTimeMillis());
        return ApplyResult.SUCCESS;
    }

    /** @throws IllegalStateException if actingLandId isn't a member, or is a plain member with no approval rights. */
    public List<UUID> listApplicants(UUID actingLandId) throws SQLException {
        FederationMember actingMember = requireApprover(actingLandId);
        return repository.loadApplicantLandIds(actingMember.federationId());
    }

    public enum ApprovalResult { SUCCESS, NOT_AUTHORIZED, APPLICATION_NOT_FOUND }

    public ApprovalResult approve(UUID actingLandId, UUID applicantLandId) throws SQLException {
        Optional<FederationMember> actingMember = repository.findMemberByLandId(actingLandId);
        if (actingMember.isEmpty() || !FederationRules.canApprove(actingMember.get().role())) {
            return ApprovalResult.NOT_AUTHORIZED;
        }
        UUID federationId = actingMember.get().federationId();
        if (!repository.applicationExists(federationId, applicantLandId)) {
            return ApprovalResult.APPLICATION_NOT_FOUND;
        }
        repository.deleteAllApplicationsForLand(applicantLandId);
        repository.insertMember(new FederationMember(federationId, applicantLandId, FederationRole.MEMBER, System.currentTimeMillis()));
        return ApprovalResult.SUCCESS;
    }

    public ApprovalResult reject(UUID actingLandId, UUID applicantLandId) throws SQLException {
        Optional<FederationMember> actingMember = repository.findMemberByLandId(actingLandId);
        if (actingMember.isEmpty() || !FederationRules.canApprove(actingMember.get().role())) {
            return ApprovalResult.NOT_AUTHORIZED;
        }
        UUID federationId = actingMember.get().federationId();
        if (!repository.applicationExists(federationId, applicantLandId)) {
            return ApprovalResult.APPLICATION_NOT_FOUND;
        }
        repository.deleteApplication(federationId, applicantLandId);
        return ApprovalResult.SUCCESS;
    }

    public enum LeaveResult { SUCCESS, NOT_A_MEMBER, LEADER_MUST_TRANSFER_FIRST }

    public LeaveResult leave(UUID landId) throws SQLException {
        Optional<FederationMember> member = repository.findMemberByLandId(landId);
        if (member.isEmpty()) {
            return LeaveResult.NOT_A_MEMBER;
        }
        if (member.get().role() == FederationRole.LEADER) {
            return LeaveResult.LEADER_MUST_TRANSFER_FIRST;
        }
        repository.deleteMember(landId);
        return LeaveResult.SUCCESS;
    }

    public enum KickResult { SUCCESS, NOT_AUTHORIZED, TARGET_NOT_MEMBER, TARGET_NOT_KICKABLE }

    public KickResult kick(UUID actingLandId, UUID targetLandId) throws SQLException {
        Optional<FederationMember> actingMember = repository.findMemberByLandId(actingLandId);
        if (actingMember.isEmpty()) {
            return KickResult.NOT_AUTHORIZED;
        }
        Optional<FederationMember> targetMember = repository.findMemberByLandId(targetLandId);
        if (targetMember.isEmpty() || !targetMember.get().federationId().equals(actingMember.get().federationId())) {
            return KickResult.TARGET_NOT_MEMBER;
        }
        if (!FederationRules.canKick(actingMember.get().role(), targetMember.get().role())) {
            return KickResult.TARGET_NOT_KICKABLE;
        }
        repository.deleteMember(targetLandId);
        return KickResult.SUCCESS;
    }

    public enum AppointResult { SUCCESS, NOT_LEADER, TARGET_NOT_MEMBER, ALREADY_DEPUTY, CAP_REACHED }

    public AppointResult appointDeputy(UUID actingLandId, UUID targetLandId) throws SQLException {
        Optional<Federation> federation = requireLeaderFederation(actingLandId);
        if (federation.isEmpty()) {
            return AppointResult.NOT_LEADER;
        }
        Optional<FederationMember> targetMember = repository.findMemberByLandId(targetLandId);
        if (targetMember.isEmpty() || !targetMember.get().federationId().equals(federation.get().id())) {
            return AppointResult.TARGET_NOT_MEMBER;
        }
        if (targetMember.get().role() == FederationRole.DEPUTY) {
            return AppointResult.ALREADY_DEPUTY;
        }
        long currentDeputyCount = repository.loadMembers(federation.get().id()).stream()
                .filter(m -> m.role() == FederationRole.DEPUTY)
                .count();
        if (currentDeputyCount >= FederationRules.deputyCap(federation.get().level())) {
            return AppointResult.CAP_REACHED;
        }
        repository.updateMemberRole(targetLandId, FederationRole.DEPUTY);
        return AppointResult.SUCCESS;
    }

    public enum DismissResult { SUCCESS, NOT_LEADER, TARGET_NOT_DEPUTY }

    public DismissResult dismissDeputy(UUID actingLandId, UUID targetLandId) throws SQLException {
        Optional<Federation> federation = requireLeaderFederation(actingLandId);
        if (federation.isEmpty()) {
            return DismissResult.NOT_LEADER;
        }
        Optional<FederationMember> targetMember = repository.findMemberByLandId(targetLandId);
        if (targetMember.isEmpty() || !targetMember.get().federationId().equals(federation.get().id())
                || targetMember.get().role() != FederationRole.DEPUTY) {
            return DismissResult.TARGET_NOT_DEPUTY;
        }
        repository.updateMemberRole(targetLandId, FederationRole.MEMBER);
        return DismissResult.SUCCESS;
    }

    public enum TransferResult { SUCCESS, NOT_LEADER, TARGET_NOT_MEMBER }

    public TransferResult transferLeadership(UUID actingLandId, UUID targetLandId) throws SQLException {
        Optional<Federation> federationOpt = requireLeaderFederation(actingLandId);
        if (federationOpt.isEmpty()) {
            return TransferResult.NOT_LEADER;
        }
        Optional<FederationMember> targetMember = repository.findMemberByLandId(targetLandId);
        if (targetMember.isEmpty() || !targetMember.get().federationId().equals(federationOpt.get().id())) {
            return TransferResult.TARGET_NOT_MEMBER;
        }
        repository.updateMemberRole(actingLandId, FederationRole.MEMBER);
        repository.updateMemberRole(targetLandId, FederationRole.LEADER);
        repository.update(federationOpt.get().withLeaderLandId(targetLandId));
        return TransferResult.SUCCESS;
    }

    public enum DisbandResult { SUCCESS, NOT_LEADER }

    public DisbandResult disband(UUID actingLandId) throws SQLException {
        Optional<Federation> federation = requireLeaderFederation(actingLandId);
        if (federation.isEmpty()) {
            return DisbandResult.NOT_LEADER;
        }
        repository.delete(federation.get().id());
        return DisbandResult.SUCCESS;
    }

    public enum DescriptionResult { SUCCESS, NOT_LEADER, TOO_LONG }

    public DescriptionResult updateDescription(UUID actingLandId, String newDescription) throws SQLException {
        if (newDescription.length() > DESCRIPTION_MAX_LENGTH) {
            return DescriptionResult.TOO_LONG;
        }
        Optional<Federation> federation = requireLeaderFederation(actingLandId);
        if (federation.isEmpty()) {
            return DescriptionResult.NOT_LEADER;
        }
        repository.update(federation.get().withDescription(newDescription));
        return DescriptionResult.SUCCESS;
    }

    public Optional<Federation> findByName(String name) throws SQLException {
        return repository.findByName(name);
    }

    public Optional<Federation> findByLandId(UUID landId) throws SQLException {
        Optional<FederationMember> member = repository.findMemberByLandId(landId);
        if (member.isEmpty()) {
            return Optional.empty();
        }
        return repository.findById(member.get().federationId());
    }

    public List<Federation> listAll() throws SQLException {
        return repository.loadAll();
    }

    public List<FederationMember> membersOf(UUID federationId) throws SQLException {
        return repository.loadMembers(federationId);
    }

    private FederationMember requireApprover(UUID actingLandId) throws SQLException {
        FederationMember member = repository.findMemberByLandId(actingLandId)
                .orElseThrow(() -> new IllegalStateException("not a member of any federation"));
        if (!FederationRules.canApprove(member.role())) {
            throw new IllegalStateException("no approval rights");
        }
        return member;
    }

    private Optional<Federation> requireLeaderFederation(UUID actingLandId) throws SQLException {
        Optional<FederationMember> member = repository.findMemberByLandId(actingLandId);
        if (member.isEmpty() || member.get().role() != FederationRole.LEADER) {
            return Optional.empty();
        }
        return repository.findById(member.get().federationId());
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :yeowool-federation:build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/FederationManager.java
git commit -m "Add FederationManager business logic"
```

---

### Task 6: Main plugin class + `/연합 생성|정보|목록`

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java`

**Interfaces:**
- Consumes: `FederationManager`, all its result enums (Task 5); `LandLookup`/`LandInfo` (Task 3); `FederationSchemaInitializer` (Task 1).
- Produces: `FederationCommand implements CommandExecutor` registered against `/연합`, handling only `생성`/`정보`/`목록` this task (later tasks add more `case`s to the same `switch`, so this file grows across tasks 7-10 — that's expected, matching how `RaidAdminCommand` grew one subcommand group at a time).

- [ ] **Step 1: Write `YeowoolFederation.java`**

```java
package com.yeowool.federation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.federation.database.FederationRepository;
import com.yeowool.federation.database.FederationSchemaInitializer;
import com.yeowool.federation.land.LandLookup;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class YeowoolFederation extends JavaPlugin {

    private ExecutorService executor;

    @Override
    public void onEnable() {
        YeowoolCoreAPI core = Bukkit.getServicesManager().load(YeowoolCoreAPI.class);
        if (core == null) {
            getLogger().severe("YeowoolCore를 찾을 수 없습니다. 비활성화합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            FederationSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("데이터베이스 초기화에 실패했습니다. 서버를 비활성화합니다: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "YeowoolFederation-DB");
            thread.setDaemon(true);
            return thread;
        });

        FederationRepository repository = new FederationRepository(core.dataSource());
        LandLookup landLookup = new LandLookup(core.dataSource());
        FederationManager manager = new FederationManager(repository);

        var federationCommand = new FederationCommand(this, core, manager, landLookup, executor);
        var command = getCommand("연합");
        if (command != null) {
            command.setExecutor(federationCommand);
        }
    }

    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
```

- [ ] **Step 2: Write `FederationCommand.java`**

```java
package com.yeowool.federation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.federation.land.LandInfo;
import com.yeowool.federation.land.LandLookup;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/** {@code /연합 <하위명령어>} — see messages.yml's federation.usage for the full subcommand list. */
public final class FederationCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final FederationManager manager;
    private final LandLookup landLookup;
    private final ExecutorService executor;

    public FederationCommand(JavaPlugin plugin, YeowoolCoreAPI core, FederationManager manager,
                              LandLookup landLookup, ExecutorService executor) {
        this.plugin = plugin;
        this.core = core;
        this.manager = manager;
        this.landLookup = landLookup;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            core.messages().send(sender, "federation.player-only");
            return true;
        }
        if (args.length == 0) {
            core.messages().send(player, "federation.usage");
            return true;
        }

        switch (args[0]) {
            case "생성" -> handleCreate(player, args);
            case "정보" -> handleInfo(player, args);
            case "목록" -> handleList(player);
            default -> core.messages().send(player, "federation.usage");
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String name = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                var result = manager.create(land.get().id(), name);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.create-success", Placeholder.unparsed("name", name));
                        case LAND_ALREADY_IN_FEDERATION -> core.messages().send(player, "federation.already-in-federation");
                        case NAME_TAKEN -> core.messages().send(player, "federation.name-taken", Placeholder.unparsed("name", name));
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 생성 실패: " + e.getMessage());
            }
        });
    }

    private void handleInfo(Player player, String[] args) {
        executor.execute(() -> {
            try {
                Optional<Federation> federation;
                if (args.length >= 2) {
                    federation = manager.findByName(args[1]);
                } else {
                    Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                    federation = land.isPresent() ? manager.findByLandId(land.get().id()) : Optional.empty();
                }
                if (federation.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.info-not-found"));
                    return;
                }
                Federation f = federation.get();
                List<FederationMember> members = manager.membersOf(f.id());
                StringBuilder memberLines = new StringBuilder();
                for (FederationMember member : members) {
                    Optional<LandInfo> landInfo = landLookup.findById(member.landId());
                    String landName = landInfo.map(LandInfo::landName).orElse("?");
                    memberLines.append("\n§7- ").append(landName).append(" (").append(member.role()).append(")");
                }
                String description = f.description() == null ? "(없음)" : f.description();
                runOnMain(() -> player.sendMessage("§6[" + f.name() + "] §7Lv." + f.level() + " · 소개: " + description + memberLines);
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 정보 조회 실패: " + e.getMessage());
            }
        });
    }

    private void handleList(Player player) {
        executor.execute(() -> {
            try {
                List<Federation> federations = manager.listAll();
                if (federations.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.list-empty"));
                    return;
                }
                runOnMain(() -> core.messages().send(player, "federation.list-header"));
                for (Federation f : federations) {
                    int memberCount;
                    try {
                        memberCount = manager.membersOf(f.id()).size();
                    } catch (java.sql.SQLException e) {
                        memberCount = 0;
                    }
                    int finalMemberCount = memberCount;
                    runOnMain(() -> core.messages().send(player, "federation.list-line",
                            Placeholder.unparsed("name", f.name()),
                            Placeholder.unparsed("level", String.valueOf(f.level())),
                            Placeholder.unparsed("count", String.valueOf(finalMemberCount))));
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 목록 조회 실패: " + e.getMessage());
            }
        });
    }

    private void runOnMain(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :yeowool-federation:build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Deploy and manually verify `/연합 생성`, `/연합 정보`, `/연합 목록` in-game**

Copy `yeowool-federation/build/libs/yeowool-federation-1.0.0-SNAPSHOT.jar` to `C:/YEOWOOL/lobby/plugins/`, `C:/YEOWOOL/town/plugins/`, `C:/YEOWOOL/wild/plugins/`, restart (or reload) the lobby server, and confirm via RCON or in-game: `/연합 생성 테스트연합` succeeds, `/연합 정보` shows it back with the creating land as LEADER, `/연합 목록` lists it.

- [ ] **Step 5: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java
git commit -m "Wire up federation plugin main class and /연합 생성|정보|목록"
```

---

### Task 7: `/연합 가입신청|신청목록|수락|거절`

**Files:**
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java`

**Interfaces:**
- Consumes: `FederationManager.applyToJoin/listApplicants/approve/reject` (Task 5).

- [ ] **Step 1: Add the new `case`s to the `switch` in `onCommand`**

```java
        switch (args[0]) {
            case "생성" -> handleCreate(player, args);
            case "정보" -> handleInfo(player, args);
            case "목록" -> handleList(player);
            case "가입신청" -> handleApply(player, args);
            case "신청목록" -> handleApplicationList(player);
            case "수락" -> handleApprove(player, args);
            case "거절" -> handleReject(player, args);
            default -> core.messages().send(player, "federation.usage");
        }
```

- [ ] **Step 2: Add the handler methods**

```java
    private void handleApply(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String federationName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                var result = manager.applyToJoin(land.get().id(), federationName);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.apply-success", Placeholder.unparsed("name", federationName));
                        case FEDERATION_NOT_FOUND -> core.messages().send(player, "federation.not-found", Placeholder.unparsed("name", federationName));
                        case ALREADY_MEMBER -> core.messages().send(player, "federation.already-member");
                        case ALREADY_APPLIED -> core.messages().send(player, "federation.already-applied");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 가입 신청 실패: " + e.getMessage());
            }
        });
    }

    private void handleApplicationList(Player player) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                List<java.util.UUID> applicantLandIds;
                try {
                    applicantLandIds = manager.listApplicants(land.get().id());
                } catch (IllegalStateException e) {
                    runOnMain(() -> core.messages().send(player, "federation.not-your-federation"));
                    return;
                }
                if (applicantLandIds.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-applications"));
                    return;
                }
                runOnMain(() -> core.messages().send(player, "federation.application-list-header"));
                for (java.util.UUID applicantLandId : applicantLandIds) {
                    Optional<LandInfo> applicantLand = landLookup.findById(applicantLandId);
                    String landName = applicantLand.map(LandInfo::landName).orElse("?");
                    runOnMain(() -> core.messages().send(player, "federation.application-list-line", Placeholder.unparsed("land", landName)));
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 신청 목록 조회 실패: " + e.getMessage());
            }
        });
    }

    private void handleApprove(Player player, String[] args) {
        handleApprovalDecision(player, args, true);
    }

    private void handleReject(Player player, String[] args) {
        handleApprovalDecision(player, args, false);
    }

    private void handleApprovalDecision(Player player, String[] args, boolean approve) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String targetLandOwnerName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetLandOwnerName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.target-no-land"));
                    return;
                }
                var result = approve
                        ? manager.approve(actingLand.get().id(), targetLand.get().id())
                        : manager.reject(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, approve ? "federation.accept-success" : "federation.reject-success", Placeholder.unparsed("land", landName));
                        case NOT_AUTHORIZED -> core.messages().send(player, "federation.not-your-federation");
                        case APPLICATION_NOT_FOUND -> core.messages().send(player, "federation.application-not-found");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 가입 승인/거절 실패: " + e.getMessage());
            }
        });
    }
```

Note: `수락`/`거절` take the **applicant's land-owner player name** as the argument (not a land name) — matches how every other command in this plan identifies a land (`/연합 가입신청 <연합이름>` is the only command that takes a federation name; everything else that names a *target* takes a player name and resolves their land via `LandLookup.findByOwnerUsername`). This is a deliberate, consistent convention across the whole command set.

- [ ] **Step 3: Build**

Run: `./gradlew :yeowool-federation:build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Deploy and manually verify**

Redeploy the jar to all 3 servers (same copy step as Task 6). With two test land owners: land B applies to land A's federation (`/연합 가입신청 <A의 연합이름>`), land A runs `/연합 신청목록` and sees B listed, `/연합 수락 <B의 닉네임>` succeeds, `/연합 정보`로 B가 MEMBER로 들어간 것 확인.

- [ ] **Step 5: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java
git commit -m "Add /연합 가입신청|신청목록|수락|거절"
```

---

### Task 8: `/연합 탈퇴|추방`

**Files:**
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java`

**Interfaces:**
- Consumes: `FederationManager.leave/kick` (Task 5).

- [ ] **Step 1: Add the new `case`s**

```java
            case "탈퇴" -> handleLeave(player);
            case "추방" -> handleKick(player, args);
```

- [ ] **Step 2: Add the handler methods**

```java
    private void handleLeave(Player player) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                var result = manager.leave(land.get().id());
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.leave-success");
                        case NOT_A_MEMBER -> core.messages().send(player, "federation.not-your-federation");
                        case LEADER_MUST_TRANSFER_FIRST -> core.messages().send(player, "federation.leader-must-transfer-first");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 탈퇴 실패: " + e.getMessage());
            }
        });
    }

    private void handleKick(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String targetName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.target-no-land"));
                    return;
                }
                var result = manager.kick(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.kick-success", Placeholder.unparsed("land", landName));
                        case NOT_AUTHORIZED -> core.messages().send(player, "federation.not-your-federation");
                        case TARGET_NOT_MEMBER -> core.messages().send(player, "federation.target-not-member");
                        case TARGET_NOT_KICKABLE -> core.messages().send(player, "federation.cannot-kick-deputy");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 추방 실패: " + e.getMessage());
            }
        });
    }
```

- [ ] **Step 3: Build**

Run: `./gradlew :yeowool-federation:build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Deploy and manually verify**

Redeploy to all 3 servers. Confirm a plain member can `/연합 탈퇴` freely; confirm the leader gets `federation.leader-must-transfer-first` on `/연합 탈퇴`; confirm `/연합 추방 <닉네임>` removes a member.

- [ ] **Step 5: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java
git commit -m "Add /연합 탈퇴|추방"
```

---

### Task 9: `/연합 부연합장임명|부연합장해임|위임`

**Files:**
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java`

**Interfaces:**
- Consumes: `FederationManager.appointDeputy/dismissDeputy/transferLeadership` (Task 5); `FederationRules.deputyCap` (Task 2, for the cap-reached message's placeholder).

- [ ] **Step 1: Add the new `case`s**

```java
            case "부연합장임명" -> handleAppointDeputy(player, args);
            case "부연합장해임" -> handleDismissDeputy(player, args);
            case "위임" -> handleTransfer(player, args);
```

- [ ] **Step 2: Add the handler methods**

```java
    private void handleAppointDeputy(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String targetName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.target-no-land"));
                    return;
                }
                var result = manager.appointDeputy(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                Optional<Federation> federation = manager.findByLandId(actingLand.get().id());
                int level = federation.map(Federation::level).orElse(1);
                int cap = FederationRules.deputyCap(level);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.appoint-deputy-success", Placeholder.unparsed("land", landName));
                        case NOT_LEADER -> core.messages().send(player, "federation.leader-only");
                        case TARGET_NOT_MEMBER -> core.messages().send(player, "federation.target-not-member");
                        case ALREADY_DEPUTY -> core.messages().send(player, "federation.already-deputy");
                        case CAP_REACHED -> core.messages().send(player, "federation.deputy-cap-reached",
                                Placeholder.unparsed("level", String.valueOf(level)), Placeholder.unparsed("cap", String.valueOf(cap)));
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("부연합장 임명 실패: " + e.getMessage());
            }
        });
    }

    private void handleDismissDeputy(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String targetName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.target-no-land"));
                    return;
                }
                var result = manager.dismissDeputy(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.dismiss-deputy-success", Placeholder.unparsed("land", landName));
                        case NOT_LEADER -> core.messages().send(player, "federation.leader-only");
                        case TARGET_NOT_DEPUTY -> core.messages().send(player, "federation.not-deputy");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("부연합장 해임 실패: " + e.getMessage());
            }
        });
    }

    private void handleTransfer(Player player, String[] args) {
        if (args.length != 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String targetName = args[1];
        executor.execute(() -> {
            try {
                Optional<LandInfo> actingLand = landLookup.findByOwnerUuid(player.getUniqueId());
                Optional<LandInfo> targetLand = landLookup.findByOwnerUsername(targetName);
                if (actingLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                if (targetLand.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.target-no-land"));
                    return;
                }
                var result = manager.transferLeadership(actingLand.get().id(), targetLand.get().id());
                String landName = targetLand.get().landName();
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.transfer-success", Placeholder.unparsed("land", landName));
                        case NOT_LEADER -> core.messages().send(player, "federation.leader-only");
                        case TARGET_NOT_MEMBER -> core.messages().send(player, "federation.transfer-target-not-member");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합장 위임 실패: " + e.getMessage());
            }
        });
    }
```

- [ ] **Step 3: Build**

Run: `./gradlew :yeowool-federation:build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Deploy and manually verify**

Redeploy to all 3 servers. Since every federation starts at level 1 (deputy cap 0), confirm `/연합 부연합장임명 <닉네임>` correctly replies with the "정원이 꽉 찼습니다 (레벨 1, 정원 0명)" message rather than succeeding — that's the *expected* outcome this phase, not a bug. Confirm `/연합 위임 <닉네임>` moves leadership and the old leader becomes a plain member (verify via `/연합 정보`).

- [ ] **Step 5: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java
git commit -m "Add /연합 부연합장임명|부연합장해임|위임"
```

---

### Task 10: `/연합 폐쇄` (with confirmation) + `/연합 소개글`

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/FederationDisbandConfirmGui.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java`

**Interfaces:**
- Consumes: `FederationManager.disband/updateDescription` (Task 5); `GuiButton`/`YeowoolGui` (core API, verify exact signatures against an existing GUI like `yeowool-market`'s `ConfirmPurchaseGui` before writing — same shape as that file, not the auction-specific one).

- [ ] **Step 1: Write `FederationDisbandConfirmGui.java`**

```java
package com.yeowool.federation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/** {@code /연합 폐쇄}'s one-more-step confirmation — same accept/deny pattern as yeowool-market's ConfirmPurchaseGui. */
public final class FederationDisbandConfirmGui extends YeowoolGui {

    private static final int SLOT_ACCEPT = 11;
    private static final int SLOT_DENY = 15;

    public FederationDisbandConfirmGui(JavaPlugin plugin, YeowoolCoreAPI core, FederationManager manager,
                                        ExecutorService executor, UUID actingLandId, String federationName) {
        super(27, Component.text("연합 폐쇄 확인", NamedTextColor.RED));

        setButton(SLOT_ACCEPT, GuiButton.of(acceptIcon(federationName), event -> {
            Player player = (Player) event.getWhoClicked();
            player.closeInventory();
            executor.execute(() -> {
                try {
                    var result = manager.disband(actingLandId);
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        if (result == FederationManager.DisbandResult.SUCCESS) {
                            core.messages().send(player, "federation.disband-success",
                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("name", federationName));
                        } else {
                            core.messages().send(player, "federation.leader-only");
                        }
                    });
                } catch (SQLException e) {
                    plugin.getLogger().severe("연합 폐쇄 실패: " + e.getMessage());
                }
            });
        }));

        setButton(SLOT_DENY, GuiButton.of(denyIcon(), event -> ((Player) event.getWhoClicked()).closeInventory()));
    }

    private ItemStack acceptIcon(String federationName) {
        ItemStack stack = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("폐쇄 확정", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("'" + federationName + "' 연합을 폐쇄합니다. 되돌릴 수 없습니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack denyIcon() {
        ItemStack stack = new ItemStack(Material.RED_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("취소", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
```

Before writing this file, read `yeowool-core`'s `GuiButton`/`YeowoolGui` classes to confirm the constructor signatures (`YeowoolGui(int size, Component title)`, `GuiButton.of(ItemStack, Consumer<InventoryClickEvent>)`) still match what earlier modules (`yeowool-raid`, `yeowool-market`) used — this plan assumes they haven't changed, but verify rather than trust blindly.

- [ ] **Step 2: Add the `폐쇄`/`소개글` cases to the `switch`**

```java
            case "폐쇄" -> handleDisband(player);
            case "소개글" -> handleDescription(player, args);
```

- [ ] **Step 3: Add the handler methods**

```java
    private void handleDisband(Player player) {
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                Optional<Federation> federation = manager.findByLandId(land.get().id());
                if (federation.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.not-your-federation"));
                    return;
                }
                if (!federation.get().leaderLandId().equals(land.get().id())) {
                    runOnMain(() -> core.messages().send(player, "federation.leader-only"));
                    return;
                }
                String federationName = federation.get().name();
                UUID landId = land.get().id();
                runOnMain(() -> new FederationDisbandConfirmGui(plugin, core, manager, executor, landId, federationName).open(player));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 폐쇄 확인 준비 실패: " + e.getMessage());
            }
        });
    }

    private void handleDescription(Player player, String[] args) {
        if (args.length < 2) {
            core.messages().send(player, "federation.usage");
            return;
        }
        String description = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        executor.execute(() -> {
            try {
                Optional<LandInfo> land = landLookup.findByOwnerUuid(player.getUniqueId());
                if (land.isEmpty()) {
                    runOnMain(() -> core.messages().send(player, "federation.no-land"));
                    return;
                }
                var result = manager.updateDescription(land.get().id(), description);
                runOnMain(() -> {
                    switch (result) {
                        case SUCCESS -> core.messages().send(player, "federation.description-success");
                        case NOT_LEADER -> core.messages().send(player, "federation.leader-only");
                        case TOO_LONG -> core.messages().send(player, "federation.description-too-long");
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("연합 소개글 수정 실패: " + e.getMessage());
            }
        });
    }
```

- [ ] **Step 4: Add the `import java.util.UUID;` (and confirm `Optional`/`List` are already imported from earlier tasks) at the top of `FederationCommand.java`**

- [ ] **Step 5: Build**

Run: `./gradlew :yeowool-federation:build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Deploy and manually verify**

Redeploy to all 3 servers. Confirm `/연합 소개글 <내용>` updates what `/연합 정보` shows. Confirm `/연합 폐쇄` opens a GUI (not an instant action), clicking 취소 leaves the federation intact, clicking 폐쇄 확정 actually deletes it (`/연합 정보` now shows "소속된 연합이 없습니다").

- [ ] **Step 7: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/FederationDisbandConfirmGui.java yeowool-federation/src/main/java/com/yeowool/federation/FederationCommand.java
git commit -m "Add /연합 폐쇄 (with confirmation GUI) and /연합 소개글"
```

---

### Task 11: Help index entry + update.md

**Files:**
- Modify: `yeowool-core/src/main/resources/config.yml` (the `help.categories` section every other command list is registered in — add a `마을연합` entry so `/여울도움말` lists it, and `HelpIndexChecker`'s startup warning doesn't fire for `/연합`)
- Modify: `update.md`

**Interfaces:** none (docs/config only).

- [ ] **Step 1: Read `yeowool-core/src/main/resources/config.yml`'s `help.categories` section**

Find where `토지` and `커뮤니티` categories are listed (same file edited earlier this session for `/앱연동`) to copy the exact list-item format.

- [ ] **Step 2: Add a new category**

```yaml
    마을연합:
      - "/연합 생성 <이름>"
      - "/연합 가입신청 <연합이름>, /연합 신청목록, /연합 수락|거절 <닉네임>"
      - "/연합 탈퇴, /연합 추방 <닉네임>"
      - "/연합 부연합장임명|부연합장해임 <닉네임>, /연합 위임 <닉네임>"
      - "/연합 폐쇄, /연합 소개글 <내용>"
      - "/연합 정보 [이름], /연합 목록"
```

Insert it alongside the other categories (matching indentation exactly — this file has two `categories:` sections, one under `admin-only-categories`'s sibling `categories:` for the plain text list and one under a second `categories:` for icons/descriptions per `HelpCommand`'s needs; check both and add to both, following the same two-place pattern every existing category already uses).

- [ ] **Step 3: Build yeowool-core to confirm the YAML is still valid**

Run: `./gradlew :yeowool-core:build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Add an update.md entry**

Read `update.md`'s top few lines to match the exact header style, then insert a new dated section above the current top entry (today's date) summarizing: new `yeowool-federation` module, what it does (연합 생성/가입/탈퇴/리더십/폐쇄), that levels/부연합장 정원/실제 업그레이드는 2~3단계에서 나온다는 점, and that this needs a restart on all 3 servers (new plugin jar).

- [ ] **Step 5: Deploy yeowool-core's updated config.yml pattern to live servers**

`ConfigMerger.mergeDefaults` only adds keys the live file is missing — since `help.categories.마을연합` is a brand new key, it'll be picked up automatically on next restart of the already-deployed `yeowool-core` jar without any manual server file editing needed (unlike Task 1's `menu-teleport-npc-id` situation from the earlier market change, this is a pure addition, nothing to remove).

- [ ] **Step 6: Commit**

```bash
git add yeowool-core/src/main/resources/config.yml update.md
git commit -m "Register /연합 in the help index, log to update.md"
```

---

## Self-Review Notes

- **Spec coverage:** create (Task 6), join/apply/approve/reject (Task 7), leave/kick (Task 8), deputy appoint/dismiss + leadership transfer (Task 9), disband-with-confirmation + description (Task 10), info/list (Task 6), permission matrix (`FederationRules`, Task 2), 1-federation-per-land constraint (schema PK, Task 1), deputy cap formula (Task 2, exercised via Task 9's real behavior at level 1). All spec sections have a task.
- **Deferred to phases 2-4 (explicitly out of scope here, not gaps):** `/연합채팅`, 연합 은행, 연합 전용 상점, 실제 레벨업 로직, 연합 랭킹/이벤트.
- **Type consistency check:** every `FederationManager` method signature used in `FederationCommand` (Tasks 6-10) matches what Task 5 defines — result enum names (`CreateResult.SUCCESS`, `ApplyResult.ALREADY_MEMBER`, etc.) are used consistently across both files, and `LandInfo.landName()`/`.id()`/`.ownerUsername()` accessor names match the record defined in Task 3 throughout.

# 요리 주문 NPC Implementation Plan

> **For agentic workers:** implement task-by-task; steps use checkbox (`- [ ]`) syntax.

**Goal:** Restaurant NPC with daily personal cooking orders, VIP customers, a cross-server group order and cook fame, in `yeowool-life`.

**Spec:** `docs/superpowers/specs/2026-09-30-cooking-orders-design.md` — binding for all numbers, rules, commands, config keys, tables and GUI.

**Architecture:** New package `com.yeowool.life.cooking.orders`. Pure rules (difficulty, drawing orders, reward math, fame levels, group schedule) in a Bukkit-free `CookingOrderRules` class with JUnit tests. A `CookingOrderRepository` (JDBC, worker thread only) owns all tables from spec §6 plus an announcements table for cross-server broadcasts. A `CookingOrderService` coordinates main-thread inventory/wallet work and worker-thread DB work (spec §7), a `CookingOrderGui` (extends core `YeowoolGui`), `CookingOrderCommand` (`/요리주문관리`, also the Citizens NPC binding like 교환소), and a Citizens listener class isolated so the plugin loads without Citizens.

**Tech Stack:** Paper 1.21.4, Java 21, MySQL via `core.dataSource()`, Citizens API (compileOnly — check `yeowool-life/build.gradle(.kts)`; `yeowool-market` already depends on it, copy that dependency line), ItemsAdder API (already used in life), JUnit 5.

## Global Constraints

- Build from repo root: `./gradlew :yeowool-life:build` (and `./gradlew build` at the end). OneDrive lock errors (`Unable to delete directory`, `Cannot snapshot ...output.bin`) → `rm -rf yeowool-*/build/test-results` and rerun.
- JDBC only on worker threads (the plugin's `executor` in `YeowoolLife`); Bukkit API, inventories and `core.economyData().modifyBalance` only on the main thread; `core.mailbox().deliverOrStore` main thread.
- Cross-server exactly-once via conditional UPDATEs (see spec §6). Never trust a GUI snapshot: re-check in the DB step and re-check the inventory on the main thread before removing items / paying (spec §7), rolling back the DB progress if the items vanished.
- 별조각 via `core.stardust().grant(...)` / `grantCapped(uuid, amount, "YeowoolLife", reason, "cooking", dailyCap)`; offline/other-server money via `core.payouts().enqueue(uuid, amount, "YeowoolLife", reason)` (throws SQLException, worker thread).
- Cross-server announcements: insert rows into an announcements table; every server polls it every ~20 s from the max id seen at enable and broadcasts with `messages.broadcast(key, placeholders)` (same idea as `TreasureService.announceLegendaryDigs`). Group-order start/end handling must happen on exactly one server (conditional UPDATE / INSERT on a unique key).
- Learned recipes: player has permission `addcook.recipe.<id>` (`AddCookRecipeIndex.RecipeEntry#permission()`); recipes come from `AddCookRecipeIndex.load()`. Dish quality: `RecipeEntry.results()` index 0 normal, 1 silver, 2 golden (ItemsAdder ids with `ia:` prefix — match inventory items with `CustomStack.byItemStack(stack).getNamespacedID()`).
- Player-visible text Korean via `messages.yml` keys (MiniMessage; values via `Placeholder.unparsed`/`component`). Add a `cooking-orders:` block to `config.yml` exactly as in spec §9 (ConfigMerger adds it on live servers). Add `Citizens` to `softdepend` in life `plugin.yml`, the `/요리주문관리` command and permission `yeowool.life.cooking.manage` (default op). Add help lines in `yeowool-core/src/main/resources/help.yml` (player: 식당 NPC 우클릭 설명; admin: `/요리주문관리 ...`).
- Follow surrounding code style (see `yeowool-life/.../treasure/*`, `yeowool-market/.../exchange/*` for NPC binding, GUI and async patterns; `yeowool-life/.../competition/CompetitionSchedule.java` for time math). Match comment density; no speculative abstractions.
- Commits: stage only files you changed (never `git add -A`/`.`, never `homepage-plan.md`), never `--amend`, message ends with a blank line then exactly `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Do not dispatch subagents.

---

### Task 1: Rules + repository + tests
- [ ] `CookingOrderRules` (pure): difficulty from stage count + overrides; draw N distinct orders from learned recipes (repeat only if fewer learned than N), VIP chance only among recipes with a golden tier; amount ranges; per-dish money = base × quality multiplier × fame multiplier (VIP: base × vip multiplier × fame); fame level lookup; next group start time from days/time (ZonedDateTime); group target by difficulty.
- [ ] JUnit tests for each rule (spec §11), injected `Random`.
- [ ] `CookingOrderRepository`: DDL (spec §6 + announcements table), all queries with conditional updates. Commit.

### Task 2: Service, GUI, command, NPC, wiring
- [ ] `CookingOrderService` per spec §1–§4, §7, §10; group scheduler tick (every minute on every server; start/finish/settle exactly once), announcements poll.
- [ ] `CookingOrderGui` per spec §5, click debounce.
- [ ] `CookingOrderCommand` per spec §8 + Citizens listener + `cooking-npcs.yml` (per server) like 교환소.
- [ ] Wire `enableCookingOrders(core, messages)` in `YeowoolLife` (skip with a warning when AddCook or `cooking-orders.enabled: false`; NPC listener only with Citizens).
- [ ] messages.yml, config.yml, plugin.yml, help.yml. `./gradlew build` green. Commit.

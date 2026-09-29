# 어부 주문 (수산시장) Implementation Plan

> **For agentic workers:** implement task-by-task; steps use checkbox (`- [ ]`) syntax.

**Goal:** Fishing orders NPC (personal orders with size conditions, VIP 수족관 관장, cross-server 수산시장 대목, 어부 명성) built on a shared order engine extracted from the existing cooking orders.

**Spec:** `docs/superpowers/specs/2026-09-30-fishing-orders-design.md` — binding for numbers, rules, config keys, tables, GUI, commands. The "확인이 필요한 항목" rows are decided as the 추천 column (size condition/bonus ON, fisherman job XP ON, shared engine).

**Architecture:** Extract the generic flow of `yeowool-life/.../cooking/orders/*` (order drawing, deliver/clamp/rollback, reroll, VIP, group order + schedule + settlement, fame, announcements polling, NPC binding yml, GUI, admin command) into a shared engine package (e.g. `com.yeowool.life.orders`). The engine is parameterised by a small catalog/strategy object per system:
- which item keys a player may be offered (cooking: learned recipes; fishing: species with `life.fishing.catalog.<id>` > 0 — also legacy `yw_` key)
- difficulty per key, VIP-eligible keys + VIP condition
- matching inventory stacks for an order (with optional min size / golden-only), per-stack reward multiplier (cooking: quality; fishing: size bonus × star multiplier × size-condition multiplier), take order (cooking: gold → silver → normal; fishing: smallest first, stars last)
- display name/icon for a key
- table prefix (`yw_cook_` unchanged / `yw_fish_`), message-key prefix (`cooking-orders.` / `fishing-orders.`), config path, stardust cap key (`cooking` / `fishing`), command name, NPC yml name, source/job hooks
Cooking must keep identical behavior, config, messages, command, tables and passing tests. Fishing adds `min_size_mm` and `vip_kind` columns in its own tables.

**Tech Stack:** Paper 1.21.4, Java 21, MySQL via `core.dataSource()`, Citizens (compileOnly, already in life), CustomFishing API (already used in `fishing/customfishing/*` — look up the item → loot id method in the CustomFishing jar on the life compile classpath, e.g. `BukkitCustomFishingPlugin.getInstance().getItemManager()`), ItemsAdder API, JUnit 5.

## Global Constraints

- Build from repo root: `./gradlew build`. OneDrive lock errors (`Unable to delete directory`, `Cannot snapshot ...output.bin`) → `rm -rf yeowool-*/build/test-results` and rerun.
- JDBC only on worker threads; Bukkit API, inventories, `modifyBalance`, `JobManager` XP, `dispatchCommand` on the main thread. Keep every safety property the cooking code already has (row-locked clamp, conditional UPDATEs for completion/bonus/reroll/settle, full-group tick, busy lock always released, rollback on RuntimeException).
- Species data: CustomFishing loots of type ITEM from `BukkitCustomFishingPlugin.getInstance().getLootManager().getRegisteredLoots()`; weight → rarity per spec §1/§9 (`rarity-weight`); silver/golden variants of default fish (`<id>_silver_star`, `<id>_golden_star`) count as the base species; `yw_` ids map to native ids via `CustomFishingNativeFishExporter.nativeId`. Size range per species: from CustomFishing loot/item config where available, otherwise from the life fish species config (`FishSpecies.minSizeCm/maxSizeCm`); species without a size range get no size condition, no big-fish VIP and size bonus ×1. Exclude list from config.
- Fisherman job XP via the existing `JobManager#grantXp(player, "fisherman", xp)` on completion (online player on this server).
- Group schedule default Tue/Fri 19:00, 72 h; cooking stays Mon/Thu.
- Player-visible text Korean via `messages.yml` keys under `fishing-orders.`; config block `fishing-orders:` exactly as spec §9; `/어부주문관리` command + permission `yeowool.life.fishing-orders.manage` (default op) in life `plugin.yml`; help lines in `yeowool-core/src/main/resources/help.yml`; entry at the top of today's section in `update.md` (Korean, like existing entries).
- Follow surrounding style; no speculative abstractions beyond what the two catalogs need.
- Commits: stage only files you changed (never `git add -A`/`.`, never `homepage-plan.md`), never `--amend`, message ends with a blank line then exactly `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Do not dispatch subagents. Do not deploy, push or merge.

---

### Task 1: Extract the shared order engine (cooking unchanged)
- [ ] Move generic code to the engine package; cooking becomes a catalog + thin wiring. Same tables, config, messages, command, NPC file.
- [ ] Existing `CookingOrderRulesTest` (move/adapt as needed) all green; `./gradlew build` green. Commit.

### Task 2: Fishing catalog + wiring
- [ ] Fishing catalog per spec §1–§4, §7, §11 (size condition, size bonus, star multiplier, smallest-first take, VIP big/golden, job XP).
- [ ] Tables `yw_fish_*` with `min_size_mm`, `vip_kind`; GUI shows size condition / VIP kind.
- [ ] `/어부주문관리`, Citizens NPC binding (`fishing-npcs.yml`), wiring in `YeowoolLife` (skip with warning without CustomFishing or `fishing-orders.enabled: false`).
- [ ] JUnit tests per spec §12. messages/config/plugin.yml/help.yml/update.md. `./gradlew build` green. Commit.

# 여울 추천 보상 (마인리스트 NuVotifier) Implementation Plan

**Goal:** Reward players who vote for the server on 마인리스트. NuVotifier (plugin name `Votifier`, event `com.vexsoftware.votifier.model.VotifierEvent`, `Vote#getUsername()/getServiceName()/getTimeStamp()`) is installed on the **lobby** server only. Rewards are fully editable in game by staff.

**Module:** `yeowool-community` (next to 출석 보상 — reuse its patterns: `quest/AttendanceReward*` store/repository/editor GUI/amount input).

## Behaviour
1. **Receiving (lobby only):** a listener class isolated from the rest (only registered when `Votifier` is enabled, so community still loads on town/wild). On VotifierEvent (main thread): resolve the username → UUID from the shared DB (core's player table — find how core stores usernames; fall back to `Bukkit.getOfflinePlayerIfCached`). Record the vote on a worker thread.
2. **One rewarded vote per player per day** (server-local date): table `yw_votes` with `UNIQUE(uuid, day)`; a duplicate vote that day is logged and ignored. Unknown names (never joined): store as pending by lowercase name (`uuid` NULL) and grant when that name joins any server (community join listener on every server; claim with a conditional UPDATE so it pays once).
3. **Rewards (all editable in game, stored in DB like attendance):**
   - **매 추천 보상** (every counted vote): 온, 별조각, items.
   - **누적 추천 보상**: any number of milestones (e.g. 10, 30, 100 votes) each with 온, 별조각, items; paid once when the player's total reaches it.
   - Delivery works for online/offline/other-server players: 온 via `core.payouts().enqueue` (worker), 별조각 via `core.stardust().grant`, items via `core.mailbox().deliverOrStore` (main thread).
   - Defaults seeded on first run: every vote 5,000온 + 별조각 3; milestones 10 → 별조각 20, 30 → 별조각 50, 100 → 별조각 150 (no items).
4. **Announcement on all three servers:** "[추천] OO님이 마인리스트에서 여울을 추천했습니다! (/추천)" — cross-server via a DB announcements table polled every ~20 s by community on each server (same idea as the order engine's announcements), or another existing cross-server mechanism if simpler and reliable when the lobby has no players online.
5. **Player command `/추천`:** shows the 마인리스트 vote link (config `vote.url`, clickable), whether I already voted today, my total votes, next milestone and its reward summary. `/추천 순위`: this month's top 10 voters.
6. **Staff command `/추천보상설정` (permission `yeowool.community.vote.manage`, default op):**
   - no args → menu GUI: "매 추천 보상" + one entry per milestone → editor GUI per reward (27 free item slots saved on close like attendance; buttons for 온 amount and 별조각 amount — input via the same mechanism attendance uses for amounts; a delete button on milestone editors).
   - `누적추가 <횟수>`, `누적삭제 <횟수>`, `테스트 <닉네임>` (simulates a vote through the same code path, counts toward limits), `리로드`.
7. Config (`config.yml`, `vote:` block): `enabled`, `url`, `announce: true`. Messages in community `messages.yml` under `vote.`. Commands/permission in community `plugin.yml`, softdepend `Votifier`. Help lines in `yeowool-core/src/main/resources/help.yml`. Entry at the top of today's section in `update.md` (Korean) including setup notes.

## Global Constraints
- Build from repo root `./gradlew build`; OneDrive lock errors → `rm -rf yeowool-*/build/test-results` and rerun.
- NuVotifier API: compile against the jar from the lobby server — copy `C:/YEOWOOL/lobby/plugins/nuvotifier.jar` to `yeowool-community/libs/nuvotifier.jar` and `compileOnly(files("libs/nuvotifier.jar"))` (same pattern as yeowool-life's AddCook; check `.gitignore` covers `libs/`).
- JDBC only on worker threads; Bukkit/inventory/mailbox on the main thread. Exactly-once: daily unique key, conditional UPDATEs for pending claims and milestone payouts (e.g. `yw_vote_milestones_paid(uuid, threshold)` primary key / INSERT IGNORE + affected-rows check done correctly for MySQL Connector/J found-rows semantics).
- Korean player text via messages.yml; follow surrounding style; small focused classes; no speculative abstractions.
- Commits: stage only files you changed (never `git add -A`/`.`, never `homepage-plan.md`), never `--amend`, message ends with a blank line then exactly `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not deploy, push or merge. Do not dispatch subagents.
- Add JUnit tests for the pure logic (next milestone, milestones crossed by a new total, month range, name normalisation).

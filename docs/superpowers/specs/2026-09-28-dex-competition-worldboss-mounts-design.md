# 공용 지급 장부 · 도감 완성 보상 · 생활 대회 · 필드 월드보스 · 탈것 이용권 설계

네 기능은 서로 독립이며 순서대로(0 → 1 → 2 → 3 → 4) 각자 계획·브랜치로 구현한다.

## 0. 공용 지급 장부 (`yeowool-core`)

다른 서버에 있거나 오프라인인 플레이어에게 온을 안전하게 주는 공용 기능. 의뢰 게시판(`yw_quest_payouts`)과 같은 방식이지만 코어에 둬서 생활 대회·월드보스가 같이 쓴다(의뢰 게시판은 운영 데이터가 있어 그대로 둠).

- 테이블 `yw_payouts(id, player CHAR(36), amount, source VARCHAR(32), reason VARCHAR(128), created_at, INDEX player)`.
- API `YeowoolCoreAPI.payouts()` → `PayoutService`:
  - `enqueue(UUID, long amount, String source, String reason) throws SQLException` — 블로킹 JDBC, **워커 스레드에서만** 호출.
  - `claimNow(UUID)` — main 스레드, 이 서버에 접속 중이면 바로 정산 시도.
- 정산: 접속 3초 뒤, 1분마다 전원, `claimNow` 호출 시. 행을 DELETE로 선점(한 서버만 성공) → main 스레드에서 접속 중이면 `modifyBalance` + 메시지("<reason>: N온을 받았습니다"), 아니면 행 복구. 종료 시 executor를 비운 뒤 선점했지만 못 준 행을 복구.

## 1. 도감 완성 보상 (`yeowool-life`)

- 도감 4종(물고기/광물/사냥/작물) 각각, 도감 화면에 보이는 항목 중 1회 이상 모은 비율이 25/50/75/100%를 넘으면 자동 지급.
  - 물고기: 설정 등급 + (CustomFishing 사용 시) CustomFishing 물고기 목록, 통계 `life.fishing.catalog.<id>`.
  - 광물/사냥/작물: `collection-dex.<분류>` 목록, 통계 `dex.<분류>.<id>`.
- 보상: 단계별 온(직접 지급 — 대상은 이 서버 접속자) + 콘솔 명령어 목록(`{player}`, `{category}` 한글명, `{category_key}` 영문키 치환; 칭호 지급 등).
- 받은 단계는 PlayerData 통계 `dex.reward.<분류키>.<단계>`=1로 표시(재지급 없음). 이미 넘은 기존 플레이어는 다음 확인 때 한꺼번에.
- 확인 시점: 접속 5초 뒤 + 1분마다 전원(최대 1분 지연).
- `/도감` 메뉴 아이콘 설명에 "수집 X/Y (Z%)", "다음 보상(50%)까지 N종" 또는 "모든 보상 획득".
- 설정 `dex-rewards.enabled`, `dex-rewards.milestones.<25|50|75|100>.money|commands`.

## 2. 생활 대회 (`yeowool-life`, 기존 서버별 낚시대회 대체)

- 매일 `start-hour`(기본 18)시부터 `duration-minutes`(60)분. 종목은 날짜(에폭 일수) % 목록 길이로 `낚시 → 채광 → 사냥 → 농사` 순환. 세 서버가 같은 시계·설정으로 같은 대회를 연다.
- 점수 = 대회 시간 동안 세 서버 합산 행동 횟수(잡은 물고기/캔 광석/처치한 몹/수확한 작물). 서버별 메모리에 쌓아 10초마다 `yw_life_competition_scores(comp_date, player, name, score)`에 upsert 합산.
- 종료 후 `yw_life_competitions(comp_date PK, activity, paid)`를 조건부 UPDATE로 한 서버만 정산 → 1~3등 공용 장부 지급(기본 5만/3만/1만). 각 서버는 1분마다 정산된 대회 중 아직 공지 안 한 것의 TOP 3를 공지(서버 시작 후 대회만).
- 시작 공지는 각 서버가 자기 시계로. `/생활대회`: 진행 중이면 종목·남은 시간·내 점수·TOP 5, 아니면 다음 대회 시각·종목.
- 기존 `FishingCompetitionManager`, `/낚시대회`, `fishing.competition` 설정 제거.

## 3. 필드 월드보스 (`yeowool-raid`, MythicMobs)

- 보스 몹 ID 설정(기본 `alocTheDemonicMech` 팩의 보스 ID — 배포 시 실제 ID 확인). 보스 위치는 관리진이 등록(`/월드보스 위치추가 <이름>`, DB 공유).
- 매일 `spawn-time`(기본 21:00)에 **보스 월드가 있는 서버**가 등록 위치 중 하나에 소환(MythicMobs API로 ActiveMob UUID 확보). 10분 전 예고. 30분 안에 못 잡으면 제거하고 "도망".
- 상태는 DB 한 행(seq, 상태, 위치, 시각)으로 공유 → 모든 서버가 1분마다 읽어 예고/등장/처치/도망을 한 번씩 공지.
- 피해 집계: 보스 UUID에 대한 `EntityDamageByEntityEvent`(플레이어 직접 + 투사체 발사자). 처치 시 1~3등(기본 10만/5만/3만) + 보스 최대 체력 1% 이상 준 참여자 전원(기본 5천) 공용 장부 지급. 아이템 드롭은 MythicMobs 드롭표.
- 서버가 전투 중 재시작되면 시작 시 진행 중 상태를 "도망"으로 정리하고 남은 보스 엔티티 제거 시도.
- `/월드보스`(다음 등장/진행 상황), 관리진 `위치추가|위치제거|위치목록|소환|제거` (`yeowool.event.manage`).

## 4. 탈것 이용권 (`yeowool-life`, MCPets)

- 탈것 목록: `plugins/MCPets/Pets/*.yml` 중 `Mountable: true`인 펫(Id, Permission, 표시 이름)을 시작 시 읽음.
- `/탈것이용권 <펫ID> [수량]`(관리진, `yeowool.life.mount.manage`): 이용권 아이템(PDC에 펫 ID) 발급.
- 이용권 우클릭: 이미 권한이 있으면 사용 안 함 + 안내. 없으면 콘솔 `lp user <이름> permission set <권한> true`로 영구 부여(LuckPerms MySQL 공유라 세 서버 동기화) 후 이용권 1개 소모.
- `/탈것`: 보유한 탈것 목록 GUI, 클릭하면 MCPets 메뉴(`/mcpets`)를 열어 소환·탑승.

## 테스트

각 기능의 순수 로직(완성률·단계 계산, 종목 순환·대회 시간 계산, 보스 순위·참여 기준, MCPets 파일 파싱)은 JUnit. 나머지는 빌드 + 인게임.

## 배포

기능마다 해당 모듈 jar(+ help용 `yeowool-core`)를 세 서버에. 재시작 필요. 서버 파일 삭제 필요 없음.

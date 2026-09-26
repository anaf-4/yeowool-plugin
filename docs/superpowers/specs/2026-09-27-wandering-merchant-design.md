# 떠돌이 상인 이벤트 설계

## 목표

정해지지 않은 시간에, 미리 등록된 후보 지점 중 한 곳에 떠돌이 상인이 30분간 나타난다. 3서버 전체에 한 명만 존재하고, 등장하면 전 서버에 공지된다. 상인은 기존 `/상점` 시스템으로 만든 상점을 열어준다(로테이션으로 매번 다른 품목).

## 결정 사항

| 항목 | 결정 |
|---|---|
| 위치 | 관리자가 등록한 후보 지점(3서버 어디든) 중 랜덤 |
| 거래 | 기존 상점 재사용 — config `wandering-merchant.shop-id`(기본 `wandering_merchant`) |
| 주기 | 등장 후 30분 체류, 다음 등장은 퇴장 후 3~5시간 사이 랜덤 |
| 범위 | 3서버 전체에 한 명 |
| 모듈 | `yeowool-market` (`com.yeowool.market.merchant`) |
| NPC | Citizens 임시 NPC(저장 안 되는 익명 레지스트리 + `MemoryNPCDataStore`), 기본 엔티티 `WANDERING_TRADER` |

## 데이터

```sql
CREATE TABLE IF NOT EXISTS yw_merchant_spots (
    name VARCHAR(32) NOT NULL PRIMARY KEY,
    server_id VARCHAR(32) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL, pitch FLOAT NOT NULL
);

CREATE TABLE IF NOT EXISTS yw_merchant_state (
    id TINYINT NOT NULL PRIMARY KEY,         -- 항상 1
    seq BIGINT NOT NULL DEFAULT 0,           -- 상태가 바뀔 때마다 +1 (낙관적 잠금)
    active TINYINT(1) NOT NULL DEFAULT 0,
    spot_name VARCHAR(32) NULL,
    server_id VARCHAR(32) NULL,
    world VARCHAR(64) NULL,
    x DOUBLE NULL, y DOUBLE NULL, z DOUBLE NULL,
    yaw FLOAT NULL, pitch FLOAT NULL,
    despawn_at BIGINT NOT NULL DEFAULT 0,
    next_spawn_at BIGINT NOT NULL DEFAULT 0
);
```
시작 시 `INSERT IGNORE`로 id=1 행 생성(첫 등장 = 지금부터 3~5시간 뒤).

## 동작 (각 서버 1분 주기, market executor)

1. 상태 읽기.
2. **등장 결정**: `active=0`이고 `now ≥ next_spawn_at`이면 전체 후보 지점 중 랜덤 1곳을 골라 `UPDATE ... SET seq=seq+1, active=1, <지점>, despawn_at=now+30분 WHERE id=1 AND seq=? AND active=0`. 성공한 서버 하나만 결정. 후보가 없으면 경고 로그(한 번)만.
3. **퇴장 결정**: `active=1`이고 `now ≥ despawn_at`이면 `UPDATE ... SET seq=seq+1, active=0, next_spawn_at=now+랜덤(3~5시간) WHERE id=1 AND seq=? AND active=1`.
4. **이 서버의 NPC 맞추기**(메인 스레드): 상태가 active이고 `server_id == 이 서버`인데 NPC가 없으면 그 좌표에 소환(월드가 없으면 건너뜀). 그 외인데 NPC가 있으면 제거.
5. **공지**(메인 스레드, 이 서버 플레이어에게): 이 서버가 마지막으로 공지한 `seq`와 다르면 — active면 "떠돌이 상인이 <서버 표시명> '<지점>' 근처에 나타났습니다! (N분간)", 아니면 "떠돌이 상인이 떠났습니다." 서버 시작 시 현재 `seq`를 "공지함"으로 기록해 재시작 반복 공지 방지. 다른 서버에선 최대 1분 늦게 뜰 수 있음.

서버가 재시작되면 NPC(메모리)는 사라지지만 4단계가 1분 내에 다시 소환. 플러그인 종료 시 NPC 제거.

## 플레이어 / 명령어

- 상인 NPC 우클릭 → `ShopOpenGate.allows(...)`를 거쳐 설정된 상점 GUI. 상점 ID가 없으면 안내 메시지.
- `/떠돌이상인` — 현재 위치(서버 표시명 + 지점 이름 + 좌표)와 남은 시간, 없으면 "현재 떠돌이 상인이 없습니다".
- 관리자(`yeowool.event.manage`): `/떠돌이상인 위치추가 <이름>`(지금 선 자리, 이 서버), `위치제거 <이름>`, `위치목록`, `소환`(없을 때 지금 등장 — `next_spawn_at=0` 후 즉시 한 번 처리), `퇴장`(있을 때 `despawn_at=now` 후 즉시 처리).

## 설정 (`yeowool-market` config.yml)

```yaml
wandering-merchant:
  enabled: true
  shop-id: "wandering_merchant"
  npc-name: "<gold>떠돌이 상인"
  entity-type: WANDERING_TRADER
  stay-minutes: 30
  interval-min-minutes: 180
  interval-max-minutes: 300
  server-names:
    lobby: "로비"
    town: "마을"
    wild: "야생"
```
서버 식별은 기존 `npc-shop.this-server-id`를 그대로 사용. 세 서버 설정을 똑같이 맞춰야 함(주기 계산은 결정을 내린 서버 값 사용).

## 순수 로직 (테스트)

`MerchantRules.nextDelayMillis(Random, min, max)`(min ≤ 결과 ≤ max, min>max면 min 사용), `pick(List, Random)`(빈 목록 → empty).

## 테스트 / 배포

JUnit: `MerchantRules`. 인게임: 위치 2곳 등록(다른 서버 포함) → `/떠돌이상인 소환` → 해당 서버에 NPC + 3서버 공지 → 우클릭 시 상점 → `/떠돌이상인 퇴장` → 사라짐 + 공지 → 재시작해도 반복 공지 없음, 진행 중이면 재소환.
`yeowool-market` jar → 3서버. 재시작 필요. 서버 파일 삭제 필요 없음. 관리자가 `/상점생성 wandering_merchant ...`로 상점을 만들어야 거래가 열림.

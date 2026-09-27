# 보물지도 탐험 설계

## 목표

채광·낚시·사냥 중 가끔 보물지도가 나오고, 지도를 들고 야생 서버를 돌아다니며 보물을 찾아 파내면 등급별 보상을 받는다. 야생을 탐험할 이유를 만들고, 지도 자체가 거래 가능한 재화가 된다.

## 결정 사항

| 항목 | 결정 |
|---|---|
| 보물 위치 | 야생 월드(`wild_world`)에만. 지도는 세 서버 어디서든 얻음 |
| 힌트 | 지도를 손에 들면 액션바에 방향(8방위) + 대략 거리. 지도 설명에 200블록 단위 좌표 범위 |
| 보물 방식 | **가상 보물** — 월드에 블록을 놓지 않음. 목표 4블록 안에서 웅크리고 땅 우클릭 = 발굴 |
| 등급/보상 | 일반/희귀/전설. 등급별 온(범위 무작위) + 아이템 후보 중 N개. 아이템 후보는 관리진이 GUI로 편집(DB 저장, 세 서버 공유) |
| 거래/만료 | 지도는 일반 아이템(거래·경매 가능), 지도를 든 사람이 발굴. 7일 만료. 한 지도는 한 번만 발굴 |
| 모듈 | `yeowool-life` (`com.yeowool.life.treasure`) |

## 데이터

```sql
CREATE TABLE IF NOT EXISTS yw_treasure_maps (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    finder CHAR(36) NOT NULL,
    tier VARCHAR(16) NOT NULL,          -- COMMON / RARE / LEGENDARY
    x INT NOT NULL, z INT NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    dug_by CHAR(36) NULL, dug_by_name VARCHAR(16) NULL, dug_at BIGINT NULL,
    INDEX idx_finder_created (finder, created_at),
    INDEX idx_dug_at (dug_at)
);
CREATE TABLE IF NOT EXISTS yw_treasure_rewards (
    tier VARCHAR(16) NOT NULL, slot INT NOT NULL, item_data MEDIUMTEXT NOT NULL,
    PRIMARY KEY (tier, slot)
);
```
지도 아이템의 PDC에 지도 id·등급·x·z·만료 시각을 넣어 힌트 계산에 DB 조회가 필요 없게 한다.

## 흐름

1. **획득**: 채광·낚시·사냥 완료 시 이미 발행되는 `PlayerRepeatableActionEvent`(`mining`/`fishing`/`hunting`)를 듣는다. CustomFishing 사용 중에는 이 이벤트가 안 나가므로 `CustomFishingCatchListener`에서도 발행하게 한다(매크로 탐지도 같이 적용됨). 확률(기본 0.2%/1%/0.5%) → 등급(80/17/3) → executor에서 오늘 얻은 지도 수가 하루 한도(3) 미만이면 좌표(중심 0,0 기준 반경 500~3000 무작위) 뽑아 DB 삽입 → main에서 `mailbox.deliverOrStore`로 지도 지급 + 메시지.
2. **힌트**: 매초 main에서 손에 지도를 든 플레이어에게 액션바 — 야생 월드가 아니면 "야생 서버(지상)에서 찾는 지도", 만료면 "낡은 지도", 발굴 반경 안이면 "바로 여기! 웅크리고 우클릭", 20블록 안이면 "가까이 있음", 그 외 "북동쪽 약 350블록"(200블록 초과는 50 단위, 이하는 10 단위).
3. **발굴**: 웅크린 채 지도를 들고 블록 우클릭(다른 플러그인이 취소한 이벤트도 받음 — 블록을 건드리지 않으므로 토지 보호와 무관). 야생 월드·만료 전·반경 안 확인 → executor에서 `UPDATE ... SET dug_by=?, dug_by_name=?, dug_at=? WHERE id=? AND dug_by IS NULL AND expires_at > ?`(한 번만 성공) + 보상 후보 로드 → main에서 지도 1장 제거, 온 지급(`modifyBalance`, 접속 중 확인), 아이템 `deliverOrStore`, 파티클·소리, 메시지.
4. **전설 공지**: 각 서버가 1분마다 `tier='LEGENDARY' AND dug_at > 마지막 확인 시각`을 조회해 "○○님이 전설 보물을 발굴했습니다!" 방송(서버 시작 시각부터).

## 명령어

- `/보물지도` — 손에 든 지도 정보(등급, 좌표 범위, 만료) 또는 사용법.
- 관리진(`yeowool.life.treasure.manage`, 기본 op): `/보물지도 보상설정 <일반|희귀|전설>`(아이템 넣고 닫으면 저장), `/보물지도 지급 <닉네임> <등급>`(이 서버 접속자, 하루 한도 무시).

## 설정 (`yeowool-life` config.yml)

```yaml
treasure:
  enabled: true
  dig-world: wild_world
  center-x: 0
  center-z: 0
  min-radius: 500
  max-radius: 3000
  expire-days: 7
  daily-limit: 3
  dig-radius: 4
  drop-chance: { mining: 0.002, fishing: 0.01, hunting: 0.005 }
  tier-weights: { common: 80, rare: 17, legendary: 3 }
  tiers:
    common:    { money-min: 1000,  money-max: 5000,   item-rolls: 1, item-id: "" }
    rare:      { money-min: 10000, money-max: 30000,  item-rolls: 2, item-id: "" }
    legendary: { money-min: 50000, money-max: 150000, item-rolls: 3, item-id: "" }
```
`item-id`는 ItemsAdder 아이템 ID(비우면 종이).

## 알려진 한계 (의도적 단순화)

- 오늘 얻은 수 확인과 삽입이 원자적이지 않아 동시에 두 장이 나오면 한도를 1 넘을 수 있음(드롭이 드물어 무시).
- 보상 계산은 지도 아이템의 등급·좌표를 믿음(아이템 NBT 편집은 관리자만 가능).
- 발굴 성공 직후 1틱 사이 접속 종료 시 온 보상은 로그에 남기고 수동 지급(아이템은 우편함).

## 테스트

JUnit: `TreasureRules`(8방위, 거리 반올림, 좌표 범위, 등급 가중치, 200블록 구간, 아이템 뽑기, 금액 범위). 인게임: 지급 → 액션바 안내 → 발굴 → 같은 지도 재발굴 거부 → 만료 거부 → 보상설정 GUI → 전설 공지.

## 배포

`yeowool-life`(+ help용 `yeowool-core`) jar → 세 서버. 재시작 필요. 서버 파일 삭제 필요 없음.

# 깜짝 이벤트 자동화 설계

## 목표

관리진이 켜지 않아도 1~2시간마다 30분짜리 짧은 버프 이벤트가 마을·야생 서버에 동시에 열려, 수시로 접속할 이유를 만든다.

## 결정 사항

| 항목 | 결정 |
|---|---|
| 종류 | 토지 경험치 2배 / 작물 수확량 2배 / 보물지도 발견 3배 / 직업 경험치 2배 (직전과 같은 종류는 피함) |
| 주기 | 끝난 뒤 60~120분 무작위 대기, 30분 진행 |
| 범위 | 마을·야생만 (로비 제외). 참여 여부 = 설정 `surprise-event.worlds`의 월드가 이 서버에 있는지 |
| 동기화 | DB 상태 한 줄(`seq`), 참여 서버들이 조건부 UPDATE로 한 서버만 시작/종료 결정 |
| 모듈 | `yeowool-life` (`com.yeowool.life.surprise`) |

## 데이터

```sql
CREATE TABLE IF NOT EXISTS yw_surprise_event_state (
    id TINYINT NOT NULL PRIMARY KEY,         -- 항상 1
    seq BIGINT NOT NULL DEFAULT 0,
    active TINYINT(1) NOT NULL DEFAULT 0,
    type VARCHAR(16) NULL,                    -- land_xp / crop_drop / treasure_drop / job_xp
    multiplier DOUBLE NOT NULL DEFAULT 1,
    ends_at BIGINT NOT NULL DEFAULT 0,
    next_at BIGINT NOT NULL DEFAULT 0,
    last_type VARCHAR(16) NULL
);
```
시작 시 `INSERT IGNORE`(첫 이벤트 = 지금부터 60~120분 뒤).

## 동작

- 참여 서버마다 20초마다(워커 스레드) 상태를 읽고, 비활성 + `now ≥ next_at`이면 `last_type`을 뺀 켜진 종류 중 무작위로 `start`(조건: `seq` 일치 + `active=0`, `seq+1`), 활성 + `now ≥ ends_at`이면 `end`(조건: `seq` 일치 + `active=1`, `seq+1`, `last_type`=방금 종류, `next_at`=지금+무작위 대기). 다시 읽은 상태를 main 스레드에 적용.
- 적용(main): 더 오래된 `seq`는 무시. 활성이면 종류에 맞게 배율 적용 — 토지 경험치/작물은 core `landStats` 배율이 1.0일 때만(아니면 수동 `/이벤트`가 우선이라 건드리지 않음) 설정하고 "내가 설정한 값"을 기억, 보물지도/직업 경험치는 `LifeBoosts`(life 내부 정적 배율). 비활성이 되거나 종류가 바뀌면 되돌림 — landStats는 현재 값이 내가 설정한 값과 같을 때만 1.0으로.
- 공지: 처음 읽은 `seq`는 기준만 잡고, 이후 바뀔 때마다 시작/종료 공지(참여 서버만).
- 보스바: 활성 동안 참여 서버의 모든 플레이어에게 "🎉 깜짝 이벤트: 작물 수확량 2배 — 남은 시간 24분 30초", 1초마다 갱신.
- 보물지도: 드롭 확률 × 배율(하루 한도 그대로). 직업 경험치: 획득량 × 배율.
- 종료/비활성화 시 적용한 배율을 모두 되돌리고 보스바 제거.

## 명령어

- `/깜짝이벤트`: 진행 중이면 종류·배율·남은 시간, 아니면 다음 이벤트까지 대략 남은 시간. 로비에서는 "마을·야생에서 열립니다".
- 관리진(`yeowool.event.manage`, 참여 서버에서): `/깜짝이벤트 시작 [토지경험치|작물|보물지도|직업경험치]`(없으면 무작위), `/깜짝이벤트 종료`.

## 설정 (`yeowool-life` config.yml)

```yaml
surprise-event:
  enabled: true
  worlds: [town_world, wild_world]
  duration-minutes: 30
  interval-min-minutes: 60
  interval-max-minutes: 120
  types:
    land_xp:       { enabled: true, multiplier: 2.0 }
    crop_drop:     { enabled: true, multiplier: 2.0 }
    treasure_drop: { enabled: true, multiplier: 3.0 }
    job_xp:        { enabled: true, multiplier: 2.0 }
```

## 테스트

JUnit `SurpriseEventRules`: 종류 추첨(직전 제외, 하나뿐이면 그대로, 없으면 empty), 대기 시간 범위, 되돌리기 판단(현재 값 == 적용 값), 배율 표기("2", "1.5"). 인게임: 두 서버 동시 시작·공지·보스바, 수동 이벤트 우선, 재시작 후 이어짐.

## 배포

`yeowool-life`(+ help용 `yeowool-core`) jar → 세 서버. 재시작 필요. 서버 파일 삭제 필요 없음.

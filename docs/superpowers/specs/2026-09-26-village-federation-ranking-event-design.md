# 마을 연합 시스템 — 4단계(연합 랭킹 · 연합대항 · 레벨업 알림) 설계

## 배경

1~3단계(기반, 연합 채팅, 은행·활동량·레벨업·연합 상점) 완료. 4단계는 연합끼리 경쟁할 거리를 만든다: 상시 **연합 랭킹**, 기간제 **연합대항**(기존 `/마을대항`의 연합 버전), 3단계에서 미룬 **연합 레벨업 알림**.

## 결정 사항

| 항목 | 결정 |
|---|---|
| 랭킹 기준 | 레벨 높은 순 → 같은 레벨이면 누적 활동량 많은 순, TOP 10 |
| 이벤트 형태 | 연합대항: 기간 동안 **늘어난 활동량**이 많은 연합이 우승 |
| 보상 | 1~3등 연합 은행에 지급 (기본 100,000 / 50,000 / 30,000온, config). 기간 중 활동량이 0인 연합은 제외 |
| 시작 | 관리자 수동(`/연합대항 시작 <분>`, `/연합대항 종료`) + 매주 자동(요일·시각·기간 config) |
| 표시 | 채팅 명령어(`/연합 랭킹`, `/연합대항 정보`) + 방송 알림. PlaceholderAPI 없음 |
| 서버 간 공유 | **DB 기반** — 대항 상태와 시작 시점 활동량 스냅샷을 DB에 저장 (재시작해도 이어짐) |
| 레벨업 알림 | 연합원 전원(소유주+주민)에게 즉시, 다른 서버 포함 (`yeowool:targeted`) |

## 데이터

```sql
CREATE TABLE IF NOT EXISTS yw_federation_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    starts_at BIGINT NOT NULL,
    ends_at BIGINT NOT NULL,
    ended TINYINT(1) NOT NULL DEFAULT 0,
    ended_at BIGINT NULL,
    result TEXT NULL,
    week_key VARCHAR(16) NULL,
    UNIQUE KEY uniq_week (week_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS yw_federation_event_baselines (
    event_id BIGINT NOT NULL,
    federation_id CHAR(36) NOT NULL,
    activity BIGINT NOT NULL,
    PRIMARY KEY (event_id, federation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- 시작: 이벤트 행 삽입 + `INSERT ... SELECT`로 모든 연합의 현재 활동량을 스냅샷 — 한 트랜잭션.
- 진행 점수: `yw_federations.activity − COALESCE(스냅샷, 0)` (대항 중 새로 생긴 연합은 스냅샷 없음 → 0부터).
- 자동 시작은 `week_key`(예: `2026-W40`)를 넣는다. 고유 키라 3개 서버가 동시에 시도해도 한 번만 생성(중복 키 오류는 "이미 시작됨"으로 처리). 수동 시작은 `week_key = NULL`(MySQL UNIQUE는 NULL 여러 개 허용).
- 종료: `UPDATE ... SET ended = 1, ended_at = ? WHERE id = ? AND ended = 0` — 이 문장이 성공한 서버 **하나만** 순위를 계산해 보상을 지급하고 `result`(공지용 결과 문자열)를 저장.

## 동작

**1분 주기 작업**(기존 federation 60초 작업에 이어서, executor에서):
1. 매주 자동: 설정된 요일·시각의 기간 안이고 진행 중인 대항이 없으면 그 주 `week_key`로 시작 시도.
2. 자동 종료: 진행 중 대항의 `ends_at`이 지났으면 종료 처리(위 조건부 UPDATE).
3. 공지: 가장 최근 대항을 읽어, 이 서버가 아직 공지 안 한 "진행 중" 대항이면 시작 공지, 결과가 저장된 종료 대항이면 결과 공지 — **각 서버가 자기 서버에 방송**(`MessageService.broadcast`, 메인 스레드). 서버 시작 시 가장 최근 대항 id를 "이미 공지함"으로 기록해 재시작마다 반복 공지하지 않는다. 다른 서버의 공지는 최대 1분 늦을 수 있다.

**명령어**:
- `/연합 랭킹` — TOP 10: `순위. 이름 Lv.N (활동량 X)`.
- `/연합대항 정보` — 진행 중이면 남은 시간 + 현재 TOP 5(늘어난 활동량), 없으면 안내.
- `/연합대항 시작 <분>` / `/연합대항 종료` — `yeowool.event.manage` 권한(기존 `/마을대항`과 동일). 이미 진행 중이면 시작 거부. 명령을 실행한 서버는 바로 1분 주기 로직을 한 번 돌려 즉시 공지.

**레벨업 알림**: `/연합 업그레이드` 성공 시 "○○ 연합이 Lv.N이 되었습니다! (마을 정원 M개)"를 연합원 전원에게 — 2단계 연합 채팅의 전달 로직(같은 서버는 직접, 나머지는 프록시 `yeowool:targeted`)을 `FederationChatService.deliverToFederation(...)`으로 분리해 재사용.

## 설정 (federation `config.yml`)

```yaml
event:
  rewards: [100000, 50000, 30000]
  auto:
    enabled: true
    day-of-week: SATURDAY
    start-time: "20:00"
    duration-minutes: 1440
```
요일·시각 해석 실패 시 경고 로그 후 자동 시작만 끔(수동은 동작). 시각은 서버 시간대.

## 순수 로직 (테스트 대상)

`FederationEventSchedule(DayOfWeek, LocalTime, int durationMinutes)` — `activeWindow(ZonedDateTime now) -> Optional<Window(weekKey, startMillis, endMillis)>`: 이번 주(해당 요일이 오늘 이전·당일이면 그 날짜) 시작 시각 ≤ now < 시작 + 기간이면 그 창. 자정을 넘기는 기간(토 20:00 ~ 일 20:00) 포함. week key 형식 `YYYY-Www`(ISO 주차, 2자리).

## 에러 처리

- DB 오류: SEVERE + 스택트레이스. 주기 작업은 다음 분에 다시 시도.
- 보상 지급 중 일부 연합 입금 실패: 로그 남기고 나머지 계속 — 결과 문자열에는 계산된 순위를 그대로 기록.
- 대항 종료 시점의 마지막 1분 활동량은 아직 반영 전일 수 있음(활동량이 1분마다 반영되므로) — 수용.

## 테스트

- `FederationEventSchedule` JUnit: 창 안/시작 전/종료 후/자정 넘김/week key 형식.
- 나머지(DB·Bukkit)는 빌드 + 인게임: 수동 시작 → 3서버 공지, `/연합대항 정보` 순위, 종료 → 1~3등 연합 은행 입금·결과 공지 1회, 재시작 후 반복 공지 없음, 레벨업 시 다른 서버 연합원에게 알림.

## 배포

`yeowool-federation`(+ help.yml 때문에 `yeowool-core`) → lobby/town/wild. 재시작 필요. 서버 파일 삭제 필요 없음(새 테이블·설정 키 자동 추가).

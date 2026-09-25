# 마을 연합 시스템 — 3단계(공유 자원: 은행 · 활동량 · 레벨업 · 연합 상점) 설계

## 배경

1단계(기반, `2026-09-23-village-federation-foundation-design.md`), 2단계(연합 채팅, `2026-09-24-village-federation-chat-design.md`) 완료. 이 문서는 3단계 전체 — 연합 은행, 연합 활동량, 레벨업(마을 수 정원 증가), 레벨별 연합 전용 상점 — 를 한 스펙으로 다룬다(사용자 결정: 나누지 않고 한 번에). 4단계(랭킹/이벤트, 레벨업 알림)는 별도.

## 결정 사항 요약

| 항목 | 결정 |
|---|---|
| 은행 입금 | 연합 소속 토지의 소유주·주민 누구나 |
| 은행 출금 | 연합장만 |
| 활동량 | 연합원(소유주+주민)이 얻는 **토지 경험치 증가분**의 누적 합 |
| 레벨업 | 연합장이 `/연합 업그레이드` — 누적 활동량이 기준 이상 + 은행에서 비용 **차감**. 활동량은 차감 없음. 레벨 상한 없음 |
| 레벨 효과 | 가입 가능한 마을 수 정원 증가 + 부연합장 정원(기존 `floor(level/10)`, 변경 없음) |
| 연합 상점 | 기존 `/상점` 시스템으로 만든 상점을 "연합 Lv.N 이상 전용"으로 지정, 개인 온으로 결제 |
| 상점 차단 방식 | core 이벤트 `ShopOpenEvent` — market이 발생, federation이 판정 (market은 연합을 모름) |
| 활동량 저장 | 서버별 메모리 누적 → 1분마다 일괄 DB 반영 |

## 데이터

`yw_federations`에 컬럼 2개 추가 (기존 서버는 메타데이터로 존재 여부 확인 후 `ALTER TABLE ... ADD COLUMN` — `PartySchemaInitializer.addColumnIfMissing`과 같은 방식):
- `bank_balance BIGINT NOT NULL DEFAULT 0`
- `activity BIGINT NOT NULL DEFAULT 0`

기존 `Federation` 레코드는 바꾸지 않는다. 은행/활동량은 별도 레코드 `FederationProgress(long bankBalance, long activity)`로 읽고, 모든 변경은 **조건부 원자적 SQL 한 문장**으로 처리해 3개 서버가 동시에 써도 잔액이 음수가 되거나 레벨이 두 번 오르지 않게 한다:
- 입금: `UPDATE ... SET bank_balance = bank_balance + ? WHERE id = ?`
- 출금: `UPDATE ... SET bank_balance = bank_balance - ? WHERE id = ? AND bank_balance >= ?`
- 활동량: `UPDATE ... SET activity = activity + ? WHERE id = ?`
- 레벨업: `UPDATE ... SET level = level + 1, bank_balance = bank_balance - ? WHERE id = ? AND level = ? AND bank_balance >= ? AND activity >= ?`

## 소속 연합 판정 (공통)

플레이어 → 연합은 2단계 채팅과 같은 기준(소유 토지의 연합 우선, 없으면 주민으로 속한 토지들 중 연합 이름순 첫 번째). 지금 `FederationChatService` 안에 있는 이 조회를 별도 클래스 `PlayerFederationResolver`로 빼서 채팅·은행 입금·활동량·상점 판정이 함께 쓴다.

연합장 전용 동작(출금, 업그레이드)은 기존 1단계 명령어와 같이 **플레이어가 소유한 토지의 역할이 LEADER**인지로 판정한다.

## 레벨 공식 (순수 로직, 테스트 대상)

`FederationLevelConfig(long activityPerLevel, long costPerLevel, int memberBase, int memberPerLevel)` — federation `config.yml`에서 읽음:
- 현재 레벨 N → N+1 조건: 누적 활동량 ≥ `activityPerLevel × N`, 은행 ≥ `costPerLevel × N` (비용 차감)
- 마을 수 정원: `memberBase + memberPerLevel × (N − 1)` (연합장 토지 포함)
- 기본값: `activity-per-level: 20000`, `cost-per-level: 30000`, `member-cap.base: 3`, `member-cap.per-level: 1` → Lv.1 = 3개, Lv.5 = 7개. Lv.1→2 = 활동량 2만 + 3만 온.

## 명령어

- `/연합 은행` — 소속 연합 은행 잔액 (연합원 누구나)
- `/연합 은행 입금 <금액>` — 연합원 누구나. 지갑에서 먼저 차감 → DB 입금, DB 실패 시 지갑 환불.
- `/연합 은행 출금 <금액>` — 연합장만. DB에서 조건부 차감 성공 시에만 지갑 지급.
- `/연합 업그레이드` — 연합장만. 조건 미달이면 무엇이 얼마나 부족한지 안내.
- `/연합 상점` — 연합 상점 목록 GUI. 해금된 상점 클릭 → 그 상점 열림, 잠긴 상점은 배리어 + "연합 Lv.N 필요".
- `/연합 정보` 추가 표시: 은행 잔액, 누적 활동량, 다음 레벨 조건(활동량 현재/필요, 비용), 마을 수(현재/정원).
- 가입 **수락** 시 마을 수가 정원 이상이면 거부 + 안내(가입신청 자체는 자유).

입출금 로그: `core.economyData().modifyBalance(..., "YeowoolFederation", "연합 은행 입금|출금")` — 기존 토지 은행과 같은 방식으로 잔액 변경 로그에 자동 기록.

## 활동량 누적

- `PlayerLandXpChangeEvent`(core, 메인 스레드)에서 `delta > 0`인 경우만 플레이어별로 메모리 맵에 더한다.
- 1분마다 federation executor에서: 맵을 비우며 플레이어마다 소속 연합을 판정해 활동량에 더함(연합 없으면 버림).
- 플러그인 종료(`onDisable`) 시 남은 분량을 한 번 더 반영.

## 연합 상점 차단

- core에 `ShopOpenEvent(Player player, String shopId)` — `Cancellable`, 메인 스레드에서 발생.
- market은 상점 **진입** 3경로(`/상점 [ID]` — 기본 상점 포함, `/상점` 메인 메뉴 클릭, Citizens NPC 우클릭)에서 GUI를 열기 직전에 이 이벤트를 발생시키고, 취소되면 열지 않는다. 같은 상점 안의 페이지 이동·수량 선택 후 복귀는 대상 아님.
- federation `config.yml`:
  ```yaml
  shops:
    - shop-id: federation_basic
      name: "연합 기본 상점"
      min-level: 1
  ```
  (기본값은 빈 목록 `shops: []` — 운영자가 `/상점생성`으로 상점을 만든 뒤 여기에 등록)
- federation 리스너: 이벤트의 상점 ID가 목록에 있으면, **플레이어별 연합 레벨 캐시**(메모리, 0 = 연합 없음)로 판정해 부족하면 취소 + "이 상점은 연합 Lv.N 이상 연합원만 이용할 수 있습니다". 캐시가 아직 없으면(접속 직후) 취소 + "잠시 후 다시 시도해주세요".
- 캐시 갱신: 접속 시(비동기 조회), 1분 주기 작업에서 접속 중인 전원, `/연합 상점`을 열 때(그 자리에서 조회한 값). 퇴장 시 제거. → 가입/탈퇴/레벨업 반영에 **최대 1분** 지연(수용).
- `/연합 상점`의 해금 상점 클릭은 `player.performCommand("상점 " + shopId)` — market의 기존 열기 경로를 그대로 타므로 결제·GUI 동작이 일반 상점과 동일.

## 에러 처리

- 모든 DB 작업은 federation executor에서, 플레이어 메시지·지갑 조작은 메인 스레드로(`runOnMain`) — 1단계 관례.
- DB 오류: SEVERE + 스택트레이스 로그. 입금 도중 DB 실패 시 지갑 환불.
- 금액: 1 이상의 정수만(`/토지 은행`과 동일한 검증).

## 테스트

- 순수 로직 `FederationLevelConfig`(다음 레벨 활동량/비용, 마을 수 정원) — JUnit, 경계값 포함.
- 나머지(DB·Bukkit)는 빌드 + 인게임 검증: 입금→잔액, 비연합원 출금 거부, 연합장 출금, 활동량 증가(1분 후 `/연합 정보`), 업그레이드 조건 미달/충족, 정원 초과 수락 거부, 연합 상점 잠금/해금, `/상점 <연합상점ID>` 직접 입력 차단.

## 배포

- `yeowool-core`(ShopOpenEvent), `yeowool-market`(이벤트 발생), `yeowool-federation` → lobby/town/wild 3서버. 재시작 필요. 서버 파일 삭제 필요 없음(`config.yml` 새 키는 `ConfigMerger`가 추가, DB 컬럼은 자동 추가).

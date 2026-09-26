# 의뢰 게시판 (플레이어 간 아이템 납품 의뢰) 설계

## 목표

플레이어가 "아이템 X를 N개 구함, 개당 보상 P온" 의뢰를 올리고, 다른 플레이어들이 나눠서 납품하면 자동으로 정산된다. 생활·직업 콘텐츠로 모은 자원이 플레이어 사이에서 돈으로 도는 통로를 만든다. 경매(판매자 중심)와 반대로 구매자 중심.

## 결정 사항

| 항목 | 결정 |
|---|---|
| 의뢰 종류 | 아이템 납품만 (자동 검증 가능) |
| 납품 | 여러 명이 나눠서 납품, 납품한 개수 × 개당 보상을 각자 받음 |
| 만료/수수료 | 3일 만료, 등록 시 총 보상의 5% 수수료 소각, 의뢰자 언제든 취소 가능 — 취소/만료 시 남은 수량 × 개당 보상 환불(수수료 제외) |
| 여는 곳 | W6 Quest Board 가구(ItemsAdder `workshop_six:quest_board`) 우클릭만. 내 의뢰는 `/의뢰`로 어디서나 |
| 위치 | `yeowool-market` 모듈 (`com.yeowool.market.questboard`) |
| 서버 | 3서버가 DB 공유 |

## 데이터

```sql
CREATE TABLE IF NOT EXISTS yw_quest_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requester CHAR(36) NOT NULL,
    requester_name VARCHAR(16) NOT NULL,
    item_data MEDIUMTEXT NOT NULL,          -- ItemStackSerializer, 수량 1개짜리 견본
    item_label VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    delivered INT NOT NULL DEFAULT 0,
    reward_per_item BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',   -- OPEN / COMPLETED / CANCELLED / EXPIRED
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    INDEX idx_status (status),
    INDEX idx_requester (requester)
);

CREATE TABLE IF NOT EXISTS yw_quest_payouts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    player CHAR(36) NOT NULL,
    amount BIGINT NOT NULL,
    reason VARCHAR(128) NOT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_player (player)
);
```

## 돈과 아이템의 안전한 흐름 (핵심)

오프라인/다른 서버 플레이어의 지갑을 직접 건드리면 서버 간 저장이 겹쳐 돈이 사라질 수 있다(기존 경매의 약점). 그래서:

- **플레이어에게 줄 돈은 전부 `yw_quest_payouts` 장부에 먼저 기록**(DB 트랜잭션 안에서 의뢰 상태 변경과 함께). 그 플레이어가 **접속해 있는 서버의 메인 스레드**가 지갑에 넣고 장부 행을 지운다(접속 직후 3초, 1분마다, 행동 직후). 같은 서버 안에서 두 번 지급되지 않게 처리 중인 행 id를 메모리에 둔다.
- **등록**: 메인 스레드에서 `modifyBalance(-(총보상+수수료))` (반환값 확인) → DB 삽입. 삽입 실패 시 같은 금액을 장부로 환불.
- **납품**: 메인 스레드에서 일치하는 아이템을 인벤토리에서 먼저 뺌 → DB에서 조건부로 `delivered += n` (진행 중 + 만료 전 + 수량 초과 안 함) + 납품자 보상 장부 기록 + 다 찼으면 COMPLETED — 한 트랜잭션. 실패하면 뺀 아이템을 돌려줌(인벤토리, 접속 끊겼으면 우편함). 성공하면 아이템은 **의뢰자 우편함**으로(오프라인 안전).
- **취소**: `status='OPEN'`일 때만 CANCELLED로 바꾸고 `(수량 − 납품) × 개당 보상`을 의뢰자 장부에 — 한 트랜잭션.
- **만료**: 1분마다 만료 시각이 지난 OPEN 의뢰를 하나씩 조건부로 EXPIRED 처리 + 환불 장부 기록 — 3서버가 동시에 돌아도 한 번만.

## 아이템 판정

등록 시 손에 든 아이템을 수량 1개 견본으로 저장. 납품은 `ItemStack.isSimilar(견본)`인 것만 — 이름/인챈트/ItemsAdder 커스텀 데이터까지 완전히 같아야 함. 표시 이름은 아이템의 표시 이름(있으면) 아니면 Material 이름.

## 화면과 명령어

- **게시판 GUI**(가구 우클릭, 54칸): 0~44칸에 진행 중 의뢰(만료 임박 순? → 최신 등록 순), 아이콘 = 견본 아이템 + 설명(요청자, 납품 `delivered/quantity`, 개당 보상, 남은 시간, "클릭: 가진 만큼 납품"). 45 이전 페이지, 49 의뢰 등록, 50 내 의뢰, 53 다음 페이지.
- **의뢰 등록**: 버튼 → GUI 닫고 채팅으로 수량 입력 → 개당 보상 입력 → 등록(총 보상+수수료 안내). "취소" 입력 시 중단. 손에 아무것도 없으면 거부. 채팅 입력은 `EventPriority.LOWEST`로 받아 다른 채팅(연합 채팅 모드 등)보다 먼저 가로챈다.
- **내 의뢰 GUI**(`/의뢰` 또는 게시판의 버튼): 내 의뢰 최근 27개(진행 중 먼저), 진행 중 의뢰 클릭 = 취소.
- 제한(config): 1인당 진행 중 의뢰 5개, 수량 1~100,000, 개당 보상 ≥ 1, 총 보상이 `Long` 범위를 넘지 않게.

## 설정 (`yeowool-market` config.yml)

```yaml
quest-board:
  furniture-id: "workshop_six:quest_board"
  fee-percent: 5
  duration-hours: 72
  max-open-per-player: 5
  max-quantity: 100000
```

## 테스트

순수 로직 `QuestBoardRules`(총 보상·수수료·환불 계산, 입력 검증, 납품 가능 수량) JUnit. 나머지는 빌드 + 인게임: 등록(돈 차감), 두 명이 나눠 납품(각자 보상, 의뢰자 우편함), 초과 납품 없음, 취소 환불, 만료 환불(1분 내), 오프라인 납품자/의뢰자가 접속하면 장부 정산.

## 배포

- ItemsAdder: 팩의 `ItemsAdder/contents/workshop_six/**`를 3서버 `plugins/ItemsAdder/contents/workshop_six`에 합치고 `/iazip`. 관리자가 `/iaget workshop_six:quest_board`로 받아 설치.
- `yeowool-market` jar → lobby/town/wild. 재시작 필요. 서버 파일 삭제 필요 없음.

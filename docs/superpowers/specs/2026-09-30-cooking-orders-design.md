# 요리 주문 NPC 설계

## 목표

AddCook 요리에 쓸모를 준다. 식당 NPC가 플레이어마다 매일 요리 주문을 내고, 요리를 납품하면 온·별조각·명성을 받는다. 가끔 금 요리만 받는 VIP 손님이 오고, 일주일에 두 번은 세 서버가 함께 채우는 단체 주문이 열린다.

## 전제 (현재 서버 확인 결과)

| 항목 | 내용 |
|---|---|
| 요리 수 | 48종 — 도마 11, 튀김기 10, 프라이팬 18, 냄비 10 (`plugins/AddCook/contents/recipes/*.yml`) |
| 난이도 기준 | 재료 단계(stage) 수 — 1단계 37개, 2단계 6개, 3단계 5개 |
| 요리 아이템 | ItemsAdder `addcook:addcook_food_<이름>` / `_silver` / `_golden` (일반 75% · 은 20% · 금 5%) |
| 품질 판별 | 레시피 `result` 목록 순서 — 1번째 일반, 2번째 은, 3번째 금. 결과가 하나뿐인 요리는 일반만 존재 |
| 배운 레시피 | LuckPerms 권한 `addcook.recipe.<레시피ID>` (MySQL → 세 서버 공통) |
| 기존 코드 | `yeowool-life`의 `AddCookRecipeIndex`(레시피·결과 파싱), `/레시피` |

## 결정 사항

| 항목 | 결정 |
|---|---|
| 주문 방식 | 개인 주문 (사람마다 따로) |
| VIP 손님 | 포함 |
| 단체 주문 | 포함 (세 서버 공동) |
| 여는 방법 | 식당 NPC(Citizens) 우클릭만 |
| 모듈 | `yeowool-life` (AddCook 연동이 이미 있음) |
| 저장 | 공용 MySQL — 세 서버 어디의 식당 NPC에서든 이어서 진행 |

---

## 1. 개인 주문

### 주문 뽑기
- 하루(서버 시간 자정 기준)에 처음 식당을 열 때 **내가 배운 레시피 중에서** 3개를 뽑는다. 같은 요리는 겹치지 않게 뽑는다 (배운 레시피가 3개 미만이면 겹칠 수 있음).
- 배운 레시피가 하나도 없으면 "레시피북으로 요리를 배워 오세요" 안내만 표시한다.
- 그날 주문은 DB에 저장되어 다시 열어도, 다른 서버에서 열어도 같다.
- 하루 중간에 새 레시피를 배워도 그날 주문은 바뀌지 않는다 (다음 날부터 반영).

### 난이도·수량·보상 (기본값, 설정 가능)

| 난이도 | 재료 단계 | 수량 | 요리 1개당 온 | 완료 시 별조각 | 명성 |
|---|---|---|---|---|---|
| 쉬움 | 1 | 3~6 | 1,500 | 1 | +1 |
| 보통 | 2 | 2~4 | 4,000 | 2 | +2 |
| 어려움 | 3 이상 | 1~3 | 10,000 | 3 | +3 |

- 요리별로 난이도·1개당 온을 설정에서 덮어쓸 수 있다 (`cooking-orders.overrides.<레시피ID>`).

### 납품
- 주문 칸을 클릭하면 인벤토리에서 해당 요리를 **필요한 만큼만** 가져간다. 모자라면 가진 만큼만 넣고 진행도가 오른다 (여러 번 나눠 납품 가능).
- 품질이 섞여 있으면 **금 → 은 → 일반** 순서로 쓴다 (금·은을 아끼고 싶으면 인벤토리에서 빼 두고 납품).
- 요리 1개마다 보상을 바로 계산한다: `1개당 온 × 품질 배율 × 명성 배율`
  - 품질 배율: 일반 ×1, 은 ×1.5, 금 ×3
- 주문을 채우면: 완료 별조각 + 명성 지급, "주문 완료" 표시.
- **3개 모두 완료**하면 추가로 5,000온 + 별조각 3개 (하루 1번).

### 주문 교체
- 하루 1번, 5,000온을 내고 **아직 납품을 시작하지 않은** 주문 하나를 다시 뽑는다 (VIP 주문은 교체 불가).

### 별조각 상한
- 요리 주문(개인·VIP·완료 보너스)으로 받는 별조각은 **하루 최대 20개** (`grantCapped`, cap key `cooking`). 단체 주문 보상은 상한과 별개.

---

## 2. VIP 손님

- 주문을 뽑을 때 **10% 확률**로 3개 중 하나가 VIP 주문이 된다.
- 금 요리가 있는 레시피(결과 3단계)에서만 뽑는다. 해당 레시피를 하나도 안 배웠으면 VIP는 나오지 않는다.
- **금 요리만** 받는다. 수량 1~2개.
- 보상: 요리 1개당 `1개당 온 × 5 × 명성 배율` (금 배율 ×3을 따로 곱하지 않음) + 완료 시 별조각 5 + 명성 +5.
- 완료하면 전 서버 공지: "OO님이 VIP 손님의 주문을 완수했습니다!"
- GUI에서 금색 테두리·"VIP" 표시.

---

## 3. 단체 주문 (세 서버 공동)

### 열림
- **매주 월·목 19:00**에 자동으로 시작해 **72시간** 진행 (요일·시각·기간 설정 가능). 동시에 하나만 진행.
- 요리: 쉬움·보통 난이도 중 무작위. 목표 수량: 쉬움 300개 / 보통 150개 (설정 가능).
- 관리진: `/요리주문관리 단체시작 [레시피ID] [수량] [시간]`, `/요리주문관리 단체종료`.
- 시작·50%·90%·달성·실패 시 전 서버 공지. 세 서버 중 한 곳만 시작 처리 (DB 조건부 UPDATE로 한 번만).

### 납품
- 누구나(레시피를 몰라도) 식당 NPC에서 납품 가능. 목표를 넘는 수량은 받지 않는다.
- 요리 1개마다 **1개당 온의 절반 × 품질 배율**을 바로 지급 (명성 배율 미적용). 명성은 오르지 않는다.
- 진행도·내 기여량은 DB에 저장하고, 수량 증가는 조건부 UPDATE로 목표를 넘지 않게 처리 (여러 서버 동시 납품 안전).

### 달성 보상 (달성 순간 한 번, 오프라인·다른 서버 포함)
- 5개 이상 납품한 참여자 전원: 별조각 5
- 기여도 1·2·3위: 별조각 +15 / +10 / +5
- 전 서버 공지 (상위 3명 이름 포함)

### 실패
- 기한 안에 못 채우면 이미 받은 온은 그대로, 달성 보상 없음. 실패 공지.

---

## 4. 요리사 명성

| 단계 | 필요 명성 | 온 보상 배율 |
|---|---|---|
| 견습 | 0 | ×1.0 |
| 요리사 | 30 | ×1.1 |
| 숙련 | 100 | ×1.2 |
| 명셰프 | 250 | ×1.35 |
| 여울 명장 | 500 | ×1.5 |

- 단계가 오르면 축하 메시지 + 설정한 콘솔 명령어 실행 (`{player}` 치환, 예: 칭호 지급).
- GUI에 현재 단계·다음 단계까지 남은 명성 표시.

---

## 5. GUI (식당 창, 54칸)

```
[ 명성 ][     ][     ][     ][ 안내 ][     ][     ][     ][ 교체 ]
[     ][     ][주문1][     ][주문2][     ][주문3][     ][     ]
[     ][     ][     ][     ][     ][     ][     ][     ][     ]
[     ][     ][     ][     ][단체주문][     ][     ][     ][     ]
[     ][     ][     ][     ][     ][     ][     ][     ][     ]
[     ][     ][     ][     ][ 닫기 ][     ][     ][     ][     ]
```

- **주문 칸**: 요리 아이콘 + 진행도(3/5), 1개당 보상, 품질 배율, 완료 보상. 완료되면 초록 표시. VIP는 금색 이름 + "금 요리만".
- **교체 버튼**: 남은 교체 횟수·비용 표시 → 클릭 후 교체할 주문 클릭.
- **단체 주문 칸**: 요리·진행도(214/300)·남은 시간·내 기여량. 클릭하면 납품. 진행 중이 아니면 "다음 단체 주문: 목요일 19:00".
- **명성 칸**: 단계·명성·다음 단계까지.
- 클릭 연타 방지 (처리 중 표시).

---

## 6. 데이터 (공용 MySQL)

```sql
yw_cook_orders (
  uuid CHAR(36), day VARCHAR(10), slot TINYINT,
  recipe_id VARCHAR(64), difficulty VARCHAR(8), vip TINYINT(1),
  required INT, delivered INT DEFAULT 0, completed TINYINT(1) DEFAULT 0,
  PRIMARY KEY (uuid, day, slot)
)
yw_cook_daily (
  uuid CHAR(36), day VARCHAR(10),
  rerolled TINYINT(1) DEFAULT 0, bonus_paid TINYINT(1) DEFAULT 0,
  PRIMARY KEY (uuid, day)
)
yw_cook_fame (uuid CHAR(36) PRIMARY KEY, fame INT DEFAULT 0)
yw_cook_group (
  id INT AUTO_INCREMENT PRIMARY KEY, recipe_id VARCHAR(64),
  target INT, progress INT DEFAULT 0,
  starts_at BIGINT, ends_at BIGINT,
  status VARCHAR(10)  -- ACTIVE / DONE / FAILED
  , settled TINYINT(1) DEFAULT 0
)
yw_cook_group_contrib (group_id INT, uuid CHAR(36), name VARCHAR(16), amount INT,
  PRIMARY KEY (group_id, uuid))
```

- 개인 주문 납품: `UPDATE ... SET delivered = delivered + ? WHERE ... AND delivered + ? <= required` (다른 서버에서 동시에 열어도 초과 납품 없음).
- 완료 판정·보너스·교체는 조건부 UPDATE로 한 번만 (`completed = 0`, `bonus_paid = 0`, `rerolled = 0`).
- 단체 주문 진행: `UPDATE yw_cook_group SET progress = progress + ? WHERE id = ? AND status = 'ACTIVE' AND progress + ? <= target`.
- 단체 주문 정산: `UPDATE ... SET settled = 1 WHERE id = ? AND settled = 0` 성공한 서버만 보상 지급 → 온은 `payouts().enqueue`, 별조각은 `stardust().grant` (오프라인·다른 서버 안전).

## 7. 처리 순서 (납품 1회)

1. (메인) 인벤토리에서 해당 요리 개수·품질 확인, 넣을 수량 계산 → 클릭 잠금
2. (DB 작업 스레드) 조건부 UPDATE로 진행도 증가 (실패 시 "이미 다 찼어요")
3. (메인) 인벤토리를 다시 확인해 요리 제거 → 온 지급(`modifyBalance`) → 완료면 별조각·명성·공지
   - 3단계에서 요리가 사라졌으면 진행도를 되돌림
4. GUI 새로고침

## 8. 명령어·권한

| 명령어 | 설명 | 권한 |
|---|---|---|
| (NPC 우클릭) | 식당 창 열기 | 모두 |
| `/요리주문관리 npc` | `/npc select`로 고른 NPC를 식당으로 연결·해제 (서버별) | `yeowool.life.cooking.manage` (op) |
| `/요리주문관리 열기` | 테스트용으로 창 열기 | 〃 |
| `/요리주문관리 단체시작 [레시피ID] [수량] [시간]` | 단체 주문 수동 시작 (생략 시 무작위·기본값) | 〃 |
| `/요리주문관리 단체종료` | 진행 중 단체 주문 실패 처리 | 〃 |
| `/요리주문관리 초기화 <닉네임>` | 그 플레이어의 오늘 주문 다시 뽑기 | 〃 |
| `/요리주문관리 명성 <닉네임> <값>` | 명성 설정 | 〃 |

## 9. 설정 (`yeowool-life/config.yml`)

```yaml
cooking-orders:
  enabled: true
  orders-per-day: 3
  difficulty:
    easy:   { amount-min: 3, amount-max: 6, money-per-dish: 1500,  stardust: 1, fame: 1 }
    normal: { amount-min: 2, amount-max: 4, money-per-dish: 4000,  stardust: 2, fame: 2 }
    hard:   { amount-min: 1, amount-max: 3, money-per-dish: 10000, stardust: 3, fame: 3 }
  quality-multiplier: { normal: 1.0, silver: 1.5, golden: 3.0 }
  all-done-bonus: { money: 5000, stardust: 3 }
  reroll-cost: 5000
  stardust-daily-cap: 20
  vip:
    chance-percent: 10
    amount-min: 1
    amount-max: 2
    money-multiplier: 5
    stardust: 5
    fame: 5
  group:
    days: [MONDAY, THURSDAY]
    start-time: "19:00"
    duration-hours: 72
    target: { easy: 300, normal: 150 }
    money-share: 0.5
    min-contribution: 5
    participation-stardust: 5
    rank-stardust: [15, 10, 5]
  fame-levels:
    - { name: 견습, fame: 0, multiplier: 1.0 }
    - { name: 요리사, fame: 30, multiplier: 1.1 }
    - { name: 숙련, fame: 100, multiplier: 1.2 }
    - { name: 명셰프, fame: 250, multiplier: 1.35 }
    - { name: 여울 명장, fame: 500, multiplier: 1.5 }
  level-up-commands: {}   # 예: { "명셰프": ["칭호 지급 {player} 명셰프"] }
  overrides: {}           # 예: { ramen: { difficulty: normal, money-per-dish: 3000 } }
```

## 10. 예외·안전

- 레시피 파일에서 사라진 요리가 오늘 주문에 있으면 그 주문은 "준비 중단"으로 표시하고 교체를 무료로 허용.
- AddCook/ItemsAdder/Citizens가 없는 서버에서는 기능을 끄고 경고 로그만 남긴다.
- 모든 DB 작업은 작업 스레드, 인벤토리·지갑은 메인 스레드.
- 별조각 지급은 `core.stardust()`, 오프라인 온 지급은 `core.payouts().enqueue`.
- 자정을 넘겨 창을 열어 둔 채 납품하면 "날짜가 바뀌었어요, 다시 열어 주세요".

## 11. 테스트

- JUnit: 난이도 판정(단계 수 → 쉬움/보통/어려움, overrides), 주문 뽑기(배운 레시피만·중복 없음·VIP 확률·금 요리 있는 레시피만), 보상 계산(품질·명성·VIP 배율), 명성 단계 계산, 단체 주문 시작 시각 계산.
- 인게임: 레시피 없는 계정 안내 → 레시피 배운 뒤 주문 3개 → 나눠서 납품 → 금·은 섞어 납품 → 3개 완료 보너스 → 교체 → VIP(관리 명령으로 확률 100% 테스트) → 단체 주문 수동 시작·두 서버에서 동시 납품·달성 정산 → 명성 단계 상승 명령어.

## 12. 배포

- `yeowool-life`, 도움말용 `yeowool-core` jar → 세 서버 (lobby, town, wild).
- 재시작 후 `/npc select` → `/요리주문관리 npc`로 식당 NPC 연결.
- 서버 파일 삭제 필요 없음. ItemsAdder 변경 없음.

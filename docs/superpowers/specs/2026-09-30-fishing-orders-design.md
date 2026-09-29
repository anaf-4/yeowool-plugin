# 어부 주문 NPC (수산시장) 설계

## 목표

잡은 물고기에 쓸모를 준다. 수산시장 NPC가 플레이어마다 매일 물고기 주문을 내고, 물고기를 납품하면 온·별조각·명성을 받는다. 요리 주문과 같은 틀(개인 주문 · VIP 손님 · 세 서버 단체 주문 · 명성)에 낚시만의 요소인 **크기**를 더한다.

## 전제 (현재 서버 확인 결과)

| 항목 | 내용 |
|---|---|
| 여울 물고기 | 60종 (`CustomFishing/contents/item/yeowool_native.yml`, id `yw_<이름>`, ItemsAdder `fishing_expansion` 모델). 가중치 60(흔함) 9종 · 25(보통) 15종 · 12(희귀) 11종 · 3(전설) 25종 — 전설에는 네더 물고기 포함 |
| CustomFishing 기본 물고기 | 16종 × 일반/은별/금별 (`contents/item/default.yml`, 예: `tuna_fish`, `tuna_fish_silver_star`, `tuna_fish_golden_star`) |
| 제외 | `vanilla`(바닐라 전리품 표시), 막대기·신발 같은 잡동사니, 낚싯대·미끼, `fishing_pack.yml`의 비물고기 아이템 (설정 목록으로 제외) |
| 물고기 판별 | CustomFishing 아이템 id (CustomFishing ItemManager로 아이템 → loot id), `yw_` 접두사는 떼고 비교 |
| 크기 | CustomFishing `ItemManager#getFishSize(item)` (cm). 종별 최소·최대 크기는 CustomFishing 설정/여울 물고기 설정에서 읽음 |
| 잡은 기록 | 도감 통계 `life.fishing.catalog.<id>` (잡은 수), `life.fishing.size.<id>` (최대 크기, mm) |
| 기존 코드 | `yeowool-life`의 `fishing/customfishing/*` (CustomFishing 연동), 요리 주문 `cooking/orders/*` |

## 결정 사항

| 항목 | 결정 |
|---|---|
| 주문 방식 | 개인 주문 + VIP 손님 + 세 서버 단체 주문 + 명성 (요리와 같음) |
| 주문 대상 | **도감에 한 번이라도 잡은 기록이 있는 물고기**만 |
| 여는 방법 | 수산시장 NPC(Citizens) 우클릭만 |
| 모듈 | `yeowool-life` |
| 저장 | 공용 MySQL, 요리와 별도 테이블 (`yw_fish_*`) |

### 확인이 필요한 항목 (추천안으로 작성됨)

| 항목 | 추천 | 다른 선택지 |
|---|---|---|
| 크기 조건·크기 보너스 | 넣음 | 빼고 종·수량만 |
| 어부 직업 경험치 보상 | 넣음 (쉬움 20 / 보통 40 / 어려움 80 / VIP 150) | 빼기 |
| 구현 방식 | 요리 주문 코드를 **공용 주문 엔진**으로 정리하고 요리·낚시는 품목 규칙만 제공 | 요리 코드를 복사해 낚시 전용으로 따로 만들기 |

---

## 1. 개인 주문

### 주문 뽑기
- 하루(서버 시간 자정 기준)에 처음 수산시장을 열 때 **도감에 잡은 기록이 있는 물고기 중에서** 3개를 뽑는다. 같은 종은 겹치지 않게 (잡아 본 종이 3개 미만이면 겹칠 수 있음).
- 잡아 본 물고기가 없으면 "먼저 물고기를 잡아 도감에 등록해 오세요" 안내만 표시.
- 전설 등급은 개인 주문에 나오지 않는다 (VIP 전용).
- 그날 주문은 DB에 저장 — 다시 열어도, 다른 서버에서 열어도 같다.

### 난이도·수량·보상 (기본값, 설정 가능)

| 난이도 | 물고기 등급 (가중치) | 수량 | 1마리당 온 | 완료 시 별조각 | 명성 |
|---|---|---|---|---|---|
| 쉬움 | 흔함 (≥ 40) | 4~8 | 800 | 1 | +1 |
| 보통 | 보통 (20~39) | 2~5 | 2,500 | 2 | +2 |
| 어려움 | 희귀 (5~19) | 1~3 | 7,000 | 3 | +3 |
| (VIP 전용) | 전설 (< 5) | — | — | — | — |

- 등급은 CustomFishing 가중치로 자동 판정. 물고기별로 난이도·1마리당 온을 설정에서 덮어쓸 수 있다 (`fishing-orders.overrides.<물고기id>`).
- CustomFishing 기본 물고기의 은별·금별 변종은 같은 종으로 취급한다 (주문은 종 단위).

### 크기 조건 (추천안)
- 주문의 **30%**에 최소 크기 조건이 붙는다: 그 종 크기 범위의 **중간값 이상** (예: 20~50cm 종이면 "35cm 이상").
- 조건이 붙은 주문은 1마리당 온 **×1.3**.
- 크기 정보가 없는 물고기(크기 범위가 설정되지 않은 종)에는 조건을 붙이지 않는다.

### 납품
- 주문 칸을 클릭하면 인벤토리에서 **조건을 만족하는** 해당 종을 **필요한 만큼만** 가져간다. 모자라면 가진 만큼만 넣는다 (나눠 납품 가능).
- 납품 순서: 조건을 만족하는 것 중 **작은 것부터** (기록용 대어 보호). 별 변종은 일반 → 은별 → 금별 순서로 마지막에 쓴다.
- 1마리마다 보상을 바로 계산: `1마리당 온 × 크기 보너스 × 별 배율 × 크기조건 배율 × 명성 배율`
  - 크기 보너스: `1 + 0.5 × (크기 − 종 최소) / (종 최대 − 종 최소)` → ×1.0 ~ ×1.5 (크기 정보가 없으면 ×1)
  - 별 배율: 일반 ×1, 은별 ×1.5, 금별 ×3
- 주문을 채우면: 완료 별조각 + 명성 (+ 어부 직업 경험치, 추천안).
- **3개 모두 완료**하면 추가로 3,000온 + 별조각 3 (하루 1번).

### 주문 교체
- 하루 1번, 3,000온을 내고 **아직 납품을 시작하지 않은** 주문 하나를 다시 뽑는다 (VIP 주문은 교체 불가).

### 별조각 상한
- 어부 주문(개인·VIP·완료 보너스)으로 받는 별조각은 **하루 최대 20개** (`grantCapped`, cap key `fishing`). 요리 주문 상한(`cooking`)과 별개. 단체 주문 보상은 상한 없음.

---

## 2. VIP 손님 — 수족관 관장

- 주문을 뽑을 때 **10% 확률**로 3개 중 하나가 VIP 주문이 된다.
- 대상: 잡아 본 **희귀·전설** 물고기 중 하나. 없으면 VIP는 나오지 않는다.
- 조건 (둘 중 하나로 뽑힘):
  - **대어**: 그 종 크기 범위 **상위 25% 이상** 1마리 (크기 정보가 있는 종만)
  - **금별**: 금별 변종 1마리 (금별 변종이 있는 종만)
- 보상: `1마리당 온(어려움 기준) × 5 × 명성 배율` + 별조각 5 + 명성 +5 (+ 직업 경험치 150).
- 완료하면 전 서버 공지: "OO님이 수족관 관장에게 42.3cm 참다랑어를 납품했습니다!"
- GUI에서 파란 테두리·"VIP" 표시.

---

## 3. 단체 주문 — 수산시장 대목 (세 서버 공동)

### 열림
- **매주 화·금 19:00**에 자동으로 시작해 **72시간** 진행 (요리 단체 주문 월·목과 겹치지 않음, 설정 가능). 동시에 하나만.
- 물고기: 흔함·보통 등급 중 무작위 한 종. 목표: 흔함 400마리 / 보통 200마리.
- 크기·별 조건 없음 (아무 크기나).
- 관리진: `/어부주문관리 단체시작 [물고기id] [수량] [시간]`, `/어부주문관리 단체종료`.
- 시작·50%·90%·달성·실패 시 전 서버 공지. 세 서버 중 한 곳만 시작·정산 처리.

### 납품
- 누구나(도감 기록 없어도) 납품 가능. 목표를 넘는 수량은 받지 않는다.
- 1마리마다 **1마리당 온의 절반 × 별 배율**을 바로 지급 (명성 배율·크기 보너스 미적용). 명성은 오르지 않는다.

### 달성 보상 (한 번만, 오프라인·다른 서버 포함)
- 5마리 이상 낸 참여자 전원: 별조각 5
- 기여도 1·2·3위: 별조각 +15 / +10 / +5
- 전 서버 공지 (상위 3명)

### 실패
- 기한 안에 못 채우면 이미 받은 온은 그대로, 달성 보상 없음. 실패 공지.

---

## 4. 어부 명성

| 단계 | 필요 명성 | 온 보상 배율 |
|---|---|---|
| 초보 낚시꾼 | 0 | ×1.0 |
| 낚시꾼 | 30 | ×1.1 |
| 베테랑 | 100 | ×1.2 |
| 명인 | 250 | ×1.35 |
| 여울 강태공 | 500 | ×1.5 |

- 요리 명성과 별개로 쌓인다.
- 단계가 오르면 축하 메시지 + 설정한 콘솔 명령어 실행 (`{player}` 치환, 예: 칭호 지급).

---

## 5. GUI (수산시장 창, 54칸)

```
[ 명성 ][     ][     ][     ][ 안내 ][     ][     ][     ][ 교체 ]
[     ][     ][주문1][     ][주문2][     ][주문3][     ][     ]
[     ][     ][     ][     ][     ][     ][     ][     ][     ]
[     ][     ][     ][     ][단체주문][     ][     ][     ][     ]
[     ][     ][     ][     ][     ][     ][     ][     ][     ]
[     ][     ][     ][     ][ 닫기 ][     ][     ][     ][     ]
```

- **주문 칸**: 물고기 아이콘 + 진행도(2/5), 크기 조건("35cm 이상"), 1마리당 보상, 크기 보너스 안내, 완료 보상. 완료되면 초록 표시. VIP는 파란 이름 + 조건(대어/금별).
- **교체 / 단체 주문 / 명성 / 안내**: 요리 주문 창과 같은 동작.
- 클릭 연타 방지.

---

## 6. 데이터 (공용 MySQL, 요리와 별도)

요리 주문 테이블과 같은 구조에 크기 조건 컬럼만 추가:

```sql
yw_fish_orders (
  uuid CHAR(36), day VARCHAR(10), slot TINYINT,
  fish_id VARCHAR(64), difficulty VARCHAR(8), vip TINYINT(1),
  vip_kind VARCHAR(8) NULL,          -- BIG / GOLDEN
  min_size_mm INT DEFAULT 0,         -- 0 = 조건 없음
  required INT, delivered INT DEFAULT 0, completed TINYINT(1) DEFAULT 0,
  PRIMARY KEY (uuid, day, slot)
)
yw_fish_daily (uuid, day, rerolled, bonus_paid, PRIMARY KEY (uuid, day))
yw_fish_fame (uuid PRIMARY KEY, fame INT)
yw_fish_group (id, fish_id, target, progress, starts_at, ends_at, status, settled, active_lock,
               UNIQUE(starts_at), UNIQUE(active_lock))
yw_fish_group_contrib (group_id, uuid, name, amount, PRIMARY KEY (group_id, uuid))
yw_fish_announcements (id AUTO_INCREMENT, message_key, data, created_at)
```

- 요리 주문과 같은 안전 규칙: 진행도는 행을 잠근 뒤 남은 수량만큼만 증가, 완료·보너스·교체·정산은 조건부 UPDATE로 한 번만, 목표를 채운 단체 주문은 1분 점검에서도 달성 처리.

## 7. 처리 순서 (납품 1회)

1. (메인) 인벤토리에서 해당 종·조건(크기/별)을 만족하는 물고기 수 확인 → 클릭 잠금
2. (DB 작업 스레드) 남은 수량을 다시 읽고 `min(가진 수, 남은 수)`만큼 진행도 증가
3. (메인) 인벤토리를 다시 확인해 그 수만큼 작은 것부터 제거 → 제거한 물고기 각각의 크기·별로 보상 계산 → 온 지급 → 완료면 별조각·명성·직업 경험치·공지
   - 3단계에서 물고기가 사라졌으면 진행도를 되돌림
4. GUI 새로고침

## 8. 명령어·권한

| 명령어 | 설명 | 권한 |
|---|---|---|
| (NPC 우클릭) | 수산시장 창 열기 | 모두 |
| `/어부주문관리 npc` | `/npc select`로 고른 NPC를 수산시장으로 연결·해제 (서버별) | `yeowool.life.fishing-orders.manage` (op) |
| `/어부주문관리 열기` | 테스트용으로 창 열기 | 〃 |
| `/어부주문관리 단체시작 [물고기id] [수량] [시간]` | 단체 주문 수동 시작 | 〃 |
| `/어부주문관리 단체종료` | 진행 중 단체 주문 실패 처리 | 〃 |
| `/어부주문관리 초기화 <닉네임>` | 그 플레이어의 오늘 주문 다시 뽑기 | 〃 |
| `/어부주문관리 명성 <닉네임> <값>` | 어부 명성 설정 | 〃 |

## 9. 설정 (`yeowool-life/config.yml`)

```yaml
fishing-orders:
  enabled: true
  orders-per-day: 3
  rarity-weight: { normal-min: 20, easy-min: 40, legendary-below: 5 }
  difficulty:
    easy:   { amount-min: 4, amount-max: 8, money-per-fish: 800,  stardust: 1, fame: 1, job-xp: 20 }
    normal: { amount-min: 2, amount-max: 5, money-per-fish: 2500, stardust: 2, fame: 2, job-xp: 40 }
    hard:   { amount-min: 1, amount-max: 3, money-per-fish: 7000, stardust: 3, fame: 3, job-xp: 80 }
  size-condition:
    chance-percent: 30
    money-multiplier: 1.3
  size-bonus-max: 0.5
  star-multiplier: { normal: 1.0, silver: 1.5, golden: 3.0 }
  all-done-bonus: { money: 3000, stardust: 3 }
  reroll-cost: 3000
  stardust-daily-cap: 20
  vip:
    chance-percent: 10
    big-fish-top-percent: 25
    money-multiplier: 5
    stardust: 5
    fame: 5
    job-xp: 150
  group:
    days: [TUESDAY, FRIDAY]
    start-time: "19:00"
    duration-hours: 72
    target: { easy: 400, normal: 200 }
    money-share: 0.5
    min-contribution: 5
    participation-stardust: 5
    rank-stardust: [15, 10, 5]
  fame-levels:
    - { name: 초보 낚시꾼, fame: 0, multiplier: 1.0 }
    - { name: 낚시꾼, fame: 30, multiplier: 1.1 }
    - { name: 베테랑, fame: 100, multiplier: 1.2 }
    - { name: 명인, fame: 250, multiplier: 1.35 }
    - { name: 여울 강태공, fame: 500, multiplier: 1.5 }
  level-up-commands: {}
  exclude: [vanilla, stick, shoes, seagrass]   # 주문에 안 나올 CustomFishing id
  overrides: {}          # 예: { yw_tuna: { difficulty: hard, money-per-fish: 9000 } }
```

## 10. 구현 방식 (추천: 공용 주문 엔진)

- 요리 주문(`cooking/orders/*`)의 주문 뽑기·납품·교체·VIP·단체 주문·명성·공지·NPC·GUI 흐름을 **공용 엔진**으로 옮긴다.
- 요리·낚시는 각각 "품목 카탈로그"만 제공한다:
  - 뽑을 수 있는 품목 목록(요리: 배운 레시피 / 낚시: 도감 기록)
  - 난이도 판정, 아이템이 주문 조건에 맞는지, 아이템 1개의 보상 배율(요리: 품질 / 낚시: 크기·별), 납품 순서
  - 테이블 이름 접두사(`yw_cook_` / `yw_fish_`), 메시지 키 접두사, 설정 경로, 명령어 이름
- 요리 주문의 동작·설정·DB 테이블은 **그대로** (이미 쌓인 데이터 유지). 요리 테스트가 그대로 통과해야 한다.
- 버그 수정·기능 개선이 두 시스템에 함께 적용된다.

## 11. 예외·안전

- 설정에서 사라진 물고기가 오늘 주문에 있으면 "준비 중단" 표시 + 무료 교체 허용.
- CustomFishing/Citizens가 없는 서버에서는 기능을 끄고 경고 로그만.
- 모든 DB 작업은 작업 스레드, 인벤토리·지갑은 메인 스레드. 별조각은 `core.stardust()`, 오프라인 온은 `core.payouts().enqueue`.
- 자정을 넘겨 창을 열어 둔 채 납품하면 "날짜가 바뀌었어요, 다시 열어 주세요".
- 크기를 읽을 수 없는 물고기는 크기 조건이 있는 주문에 쓰지 않는다 (조건 없는 주문에는 크기 보너스 ×1로 사용).

## 12. 테스트

- JUnit: 등급 → 난이도 판정(가중치 경계, overrides), 주문 뽑기(도감 기록만·중복 없음·전설 제외·VIP 대상/조건 선택), 크기 조건 계산(중간값), 크기 보너스·별 배율·보상 계산, 납품 순서(작은 것부터·별은 마지막), 명성 단계, 단체 주문 시작 시각. 공용 엔진으로 옮긴 뒤 기존 요리 테스트 전부 통과.
- 인게임: 도감 기록 없는 계정 안내 → 물고기 잡은 뒤 주문 3개 → 크기 조건 주문에 작은 물고기는 안 들어가는지 → 대어 보너스·은별·금별 보상 → 3개 완료 보너스 → 교체 → VIP(관리 명령으로 확률 100% 테스트, 대어/금별) → 단체 주문 수동 시작·두 서버 동시 납품·달성 정산 → 명성 단계 명령어 → 요리 주문이 예전처럼 동작하는지.

## 13. 배포

- `yeowool-life`, 도움말용 `yeowool-core` jar → 세 서버 (lobby, town, wild).
- 재시작 후 `/npc select` → `/어부주문관리 npc`로 수산시장 NPC 연결 (서버별).
- 서버 파일 삭제 필요 없음. ItemsAdder·CustomFishing 설정 변경 없음.

# 보스 레이드 시스템 설계 문서

- 날짜: 2026-09-17
- 상태: 승인됨 (구현 대기)
- 모듈: `yeowool-raid` (신규)

## 1. 배경 및 목표

직업/생활/퀘스트 중심 콘텐츠에 협동형 PVE 엔드 콘텐츠를 추가한다. 파티 단위로 격리된 인스턴스에서
보스를 상대하고, 티켓 재화 소모 + 개인별 보상으로 경제 안정성과 전투 재미를 함께 확보한다.

**범위**: 특정 보스 1마리 전용이 아니라, 관리자가 이후에도 보스/맵을 계속 추가할 수 있는 범용 레이드
프레임워크. 이번 구현에서 실제 보스 MythicMobs 팩(3D 모델링, 페이즈 스킬)은 별도로 구매/설치되며,
이 시스템은 그 보스를 "꽂아서" 세션을 관리하는 레이어만 담당한다.

**비목표 (이번 구현에서 하지 않는 것)**:
- 보스 자체의 전투 AI/페이즈 기믹 스킬 제작 (구매하는 MythicMobs 팩이 자체적으로 가지고 있다고 가정)
- 런타임 월드 생성/복사 (인스턴스는 관리자가 WorldEdit로 미리 만들어둔 고정 좌표 슬롯)
- 매치메이킹/대기열 큐 (슬롯이 다 차있으면 입장 거부 메시지만 표시, 예약/대기열은 없음)

## 2. 기존 자산 재사용

이번 조사에서 확인한, 새로 만들지 않고 그대로 재사용하는 부분:

- **파티 조회**: `yeowool-community`의 `yw_party`/`yw_party_member` 테이블. 레이드 모듈은 이 테이블을
  직접 읽기 전용으로 조회한다(파티장 = `yw_party.leader_uuid`, 멤버 = `yw_party_member` 조인).
  `yeowool-community`에 대한 컴파일 의존성을 추가하지 않고 SQL로 직접 읽어, 모듈 간 결합을 피한다
  (퀘스트 모듈이 BetterHud를 리플렉션으로 호출해 컴파일 의존성을 피한 것과 같은 이유).
- **티켓 아이템**: `AutoFarmVoucherItem`/`NicknameVoucherItem` 패턴 — ItemsAdder `CustomStack` 아이콘 +
  `PersistentDataType` 마커, 없으면 `Material.PAPER` 폴백.
- **보상 설정 GUI**: `com.yeowool.core.api.gui.ItemGridEditorGui` (쿠폰/캐시패키지에서 이미 재사용 중).
- **보상 발송**: `YeowoolCoreAPI.mailbox()` — 오프라인이어도 안전하게 수령, 분쟁 소지 없음.
- **BetterHud 연동**: 퀘스트 모듈의 리플렉션 패턴(`Class.forName("kr.toxicity.hud.api.bukkit.event.CustomPopupEvent")`)을 그대로 재사용해 보스바/경고 팝업을 쏜다. BetterHud가 없어도 채팅 폴백으로 동작.
- **NPC**: Citizens `NPCRightClickEvent` (퀘스트/명예의전당과 동일 패턴).
- **인스턴스 좌표 관리**: 폐기장(`ScrapyardLocationStore`, `/폐기장설정`)과 동일한 구조 — 관리자가
  명령어로 좌표를 등록하는 방식.

## 3. 데이터 모델

MySQL, `yeowool-core`의 공유 `DataSource` 사용. 스키마 초기화는 `RaidSchemaInitializer`
(다른 모듈의 `*SchemaInitializer` 패턴과 동일).

```sql
CREATE TABLE yw_raid_definition (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    mythic_mob_id VARCHAR(64) NOT NULL,
    ticket_item_id VARCHAR(64) NOT NULL,       -- ItemsAdder 커스텀 아이템 id
    ticket_amount INT NOT NULL DEFAULT 1,
    min_party_size INT NOT NULL DEFAULT 1,
    max_party_size INT NOT NULL DEFAULT 6,
    time_limit_seconds INT NOT NULL DEFAULT 1200,
    shared_lives INT NOT NULL DEFAULT 5,
    instance_count INT NOT NULL DEFAULT 3,
    reward_items MEDIUMTEXT,                    -- ItemStackSerializer 직렬화, 개인별 보상 풀
    created_at BIGINT NOT NULL
);

CREATE TABLE yw_raid_instance (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    raid_id BIGINT NOT NULL,
    slot_index INT NOT NULL,                    -- 0..instance_count-1
    world VARCHAR(64) NOT NULL,
    entry_x DOUBLE, entry_y DOUBLE, entry_z DOUBLE, entry_yaw FLOAT, entry_pitch FLOAT,
    boss_spawn_x DOUBLE, boss_spawn_y DOUBLE, boss_spawn_z DOUBLE,
    exit_x DOUBLE, exit_y DOUBLE, exit_z DOUBLE, exit_yaw FLOAT, exit_pitch FLOAT,
    bound_min_x DOUBLE, bound_min_y DOUBLE, bound_min_z DOUBLE,  -- 이탈/판정용 경계 박스
    bound_max_x DOUBLE, bound_max_y DOUBLE, bound_max_z DOUBLE,
    UNIQUE (raid_id, slot_index)
);
```

`yw_raid_session`(진행 중인 런)과 `yw_raid_cooldown`(개인/파티 재입장 쿨다운, 기획안에 있던
"일일 제한" 성격을 나중에 넣을 수 있도록 자리만 마련)은 구현 계획 단계에서 정확한 컬럼을 확정한다 —
세션 상태(진행중/승리/패배), 남은 목숨, 시작 시각, 슬롯 참조가 핵심 컬럼이라는 점만 이 시점에 고정한다.

세션 자체는 **DB에 영속화하지 않고 메모리에서만 관리**한다(서버 재시작 시 진행 중이던 레이드는
소실 — 폐기장 세션 관리와 동일한 선택). DB에는 정의(`yw_raid_definition`)와 슬롯 좌표
(`yw_raid_instance`)만 저장한다.

## 4. 관리자 명령어

권한 `yeowool.raid.manage` (기본 op), `/레이드`:

- `/레이드 생성 <이름>` — 이름/기본값으로 레이드 정의 생성
- `/레이드 몹설정 <이름> <mythicMobId>`
- `/레이드 티켓설정 <이름> <itemsAdderId> <수량>`
- `/레이드 인원설정 <이름> <최소> <최대>`
- `/레이드 제한시간설정 <이름> <초>`
- `/레이드 부활횟수설정 <이름> <횟수>`
- `/레이드 보상설정 <이름>` — `ItemGridEditorGui` 오픈
- `/레이드 인스턴스설정 <이름> <슬롯#> <입장|보스스폰|퇴장|경계1|경계2>` — 현재 위치 저장
  (`/폐기장설정`과 동일한 상호작용 방식)
- `/레이드 목록`, `/레이드 정보 <이름>`, `/레이드 삭제 <이름>`

## 5. 플레이 흐름

1. 파티장이 Citizens NPC 우클릭 → 등록된 레이드 목록 GUI (레이드별로 현재 빈 슬롯 수 표시)
2. 레이드 선택 → 서버 측에서 검증:
   - 호출자가 파티장인가
   - 파티 인원이 `min_party_size`~`max_party_size` 범위인가
   - 파티장이 티켓을 `ticket_amount` 이상 보유했는가 (파티장 소지분만 소모 — 기획안의
     "파티원 전원 또는 파티장" 중 후자로 확정, 분쟁 소지 없이 가장 단순)
   - 빈 슬롯이 있는가 (없으면 "모든 인스턴스가 사용 중입니다" 안내 후 종료)
3. 통과 시: 티켓 차감 → 슬롯 점유 표시 → 파티 전원을 해당 슬롯 `entry` 좌표로 텔레포트 →
   `boss_spawn` 좌표에 `mythicMobId` 스폰 → `RaidSession` 시작(제한시간 타이머, 공유 목숨 = `shared_lives`)
4. 진행 중:
   - `MythicMobDeathEvent`로 스폰한 보스 개체 사망 감지 → **승리**
   - 파티원이 인스턴스 경계 안에서 사망(바닐라 `PlayerDeathEvent`, 위치가 슬롯 bound 내부인 세션만)
     → 공유 목숨 1 차감, 0이 되면 **패배**
   - 제한시간 초과 → **패배** (보스팩에 광폭화 스킬이 있다면 그쪽에서 자체 처리하므로, 이 모듈은
     생존/시간만 판정한다)
   - 매 판정 변화(체력 임계값 통과는 보스팩 자체 이벤트라 이 모듈이 직접 다루지 않음)마다
     BetterHud 팝업/보스바를 리플렉션으로 트리거, 실패 시 채팅 폴백
5. 종료:
   - 승리: 참가자 전원에게 `reward_items` 풀에서 개인별로 굴려 우편함 발송
   - 패배/시간초과: 보상 없음
   - 양쪽 다: 파티 전원 `exit` 좌표로 텔레포트, 스폰된 보스/잔여 몹 제거, 슬롯 반납

## 6. 엣지 케이스

- **진행 중 접속 종료**: 그 플레이어는 그대로 카운트에서 빠짐(공유 목숨에는 영향 없음, 재접속 시
  레이드 밖(로그아웃 위치가 인스턴스 안이었다면 안전 지점)으로 처리). 파티 전체가 나가지 않는 한
  세션은 계속 진행.
- **파티장이 레이드 중 파티 해체**: 세션 자체는 계속 진행(이미 시작된 런은 인스턴스 슬롯 기준으로
  판정), 파티 해체는 다음 입장부터 영향.
- **서버 재시작 중 레이드 진행 중**: 세션은 메모리에만 있으므로 소실. 재시작 후 슬롯은 자동으로
  빈 상태로 초기화(플레이어 위치 복구는 다루지 않음 — 재시작 자체가 드문 이벤트이므로 범위 밖).
- **한 플레이어가 여러 레이드에 동시 참가 시도**: 파티 단위로 슬롯을 점유하므로, 이미 세션 중인
  파티(또는 그 멤버가 속한 파티)는 새 레이드 입장 시도 시 거부.

## 7. 배포

`yeowool-raid`는 lobby/town/wild 세 서버 모두에 배포한다(다른 신규 모듈과 동일 원칙). NPC는
관리자가 원하는 서버에만 배치하면 되므로, 배포 자체는 표준 3서버 규칙을 따른다.

# 마을 연합 시스템 — 1단계(기반) 설계

## 배경

길드/클랜이 "개인이 모여서 만드는" 그룹이라면, 마을 연합은 **비슷한 성향의 마을(Land)들이 모여서 만드는** 그룹이다. 전체 구상은 4단계로 나눠 진행한다:

1. **기반** (이 문서) — 연합 생성/가입/탈퇴, 리더십, 폐쇄, 소개글
2. 연합 전용 채팅 채널 (`/연합채팅`, 크로스서버)
3. 연합 공유 자원 — 연합 은행, 연합 전용 상점, 레벨업(연합은행 금액 + 활동량 → 최대 인원 증가)
4. 연합 랭킹/이벤트

이 문서는 **1단계만** 다룬다. 2~4단계는 1단계가 실제로 동작한 뒤 각각 별도 스펙으로 설계한다.

## 목표

- 마을 소유주가 자유롭게 새 연합을 만들 수 있다.
- 다른 마을 소유주가 가입 신청 → 연합장(또는 부연합장) 승인으로 가입한다.
- 연합장이 부연합장을 임명/해임하고, 리더십을 다른 연합원에게 위임할 수 있다.
- 연합장이 확인 절차를 거쳐 연합을 폐쇄할 수 있다.
- 연합마다 이름/소개글/레벨을 가진다 (레벨을 실제로 올리는 방법은 3단계에서 만듦 — 1단계에서는 항상 1로 시작해서 안 오르는 상태로 둠).

## 비목표 (이 단계에서 안 만드는 것)

- 연합 채팅, 연합 은행, 연합 상점, 연합 레벨업 실제 로직, 연합 랭킹/이벤트 — 전부 2~4단계.
- 연합 간 외교/전쟁 같은 관계 개념 — 요청받지 않음, 필요해지면 별도 논의.

## 아키텍처

새 모듈 `yeowool-federation`을 만든다. `yeowool-raid`가 `yeowool-community`의 파티 테이블을 컴파일 의존 없이 JDBC로 직접 읽는 것과 같은 방식으로, `yeowool-federation`도 토지 정보(`yw_lands`, `yw_players`)를 `YeowoolCoreAPI.dataSource()`를 통해 raw JDBC로 직접 읽는다 — `yeowool-land` 모듈에 새 컴파일 의존을 추가하지 않는다. 기존 토지 시스템 코드는 전혀 건드리지 않는다.

## 데이터 모델

```sql
CREATE TABLE IF NOT EXISTS yw_federations (
    id CHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    description VARCHAR(255) NULL,
    level INT NOT NULL DEFAULT 1,
    leader_land_id CHAR(36) NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS yw_federation_members (
    federation_id CHAR(36) NOT NULL,
    land_id CHAR(36) NOT NULL PRIMARY KEY,  -- 토지 하나는 연합 하나에만: land_id 자체가 PK
    role ENUM('LEADER', 'DEPUTY', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
    joined_at BIGINT NOT NULL,
    INDEX idx_federation (federation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS yw_federation_applications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    federation_id CHAR(36) NOT NULL,
    land_id CHAR(36) NOT NULL,
    applied_at BIGINT NOT NULL,
    UNIQUE KEY uniq_pending (federation_id, land_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`land_id`가 `yw_federation_members`의 PK라서 "토지 하나는 연합 하나에만 소속" 제약이 스키마 레벨에서 보장된다. `yw_federation_applications`는 같은 토지가 같은 연합에 중복 신청하는 것만 막고, 여러 연합에 동시에 신청하는 것 자체는 막지 않는다(연합마다 별도로 승인/거절되고, 한 곳에서 승인되면 다른 신청들은 자동 취소).

## 권한

| 동작 | 연합장 | 부연합장 | 일반원 |
|---|---|---|---|
| 가입 신청 승인/거절 | O | O | X |
| 추방 | O | O | X |
| 부연합장 임명/해임 | O | X | X |
| 연합장 위임 | O | X | X |
| 연합 폐쇄 | O | X | X |
| 소개글 수정 | O | X | X |
| 자진 탈퇴 | - | O (탈퇴 시 일반원으로 처리) | O |

부연합장 정원 = `floor(연합 레벨 / 10)`명. 레벨 1~9는 0명이라 사실상 부연합장을 못 만든다 — 1단계에서는 레벨이 항상 1이므로, 부연합장 기능 자체는 코드로는 만들어두되 3단계(레벨업)가 나오기 전까지는 실질적으로 못 쓴다. 이 사실을 관리자 안내 메시지나 문서에 명시한다.

## 핵심 흐름

**생성**: 마을 소유주가 `/연합 생성 <이름>` — 자기 토지가 이미 다른 연합 소속이면 거부. 이름 중복 체크. 생성한 사람의 토지가 자동으로 LEADER가 됨.

**가입 신청 → 승인**: 무소속 토지 소유주가 `/연합 가입신청 <연합이름>` → 대상 연합에 신청 등록. 연합장/부연합장이 `/연합 신청목록`으로 확인, `/연합 수락 <토지 또는 플레이어>` / `/연합 거절 <...>`. 수락되면 해당 신청은 물론 그 토지가 넣어둔 다른 모든 신청도 함께 삭제.

**탈퇴**: 일반원/부연합장이 `/연합 탈퇴` — 즉시 무소속이 됨. 연합장은 먼저 위임하지 않으면 탈퇴 불가(리더 없는 연합 방지).

**추방**: 연합장/부연합장이 `/연합 추방 <대상>` — 부연합장은 일반원만 추방 가능, 다른 부연합장은 추방 불가(연합장만 가능, 해임을 통해).

**부연합장 임명/해임**: 연합장이 `/연합 부연합장임명 <대상>` — 이미 정원이 찼으면 거부. `/연합 부연합장해임 <대상>`.

**리더십 위임**: 연합장이 `/연합 위임 <대상>` — 대상은 반드시 같은 연합 소속 토지 소유주여야 함. 기존 연합장은 일반원으로 강등(부연합장이었다면 그 지위도 해제). 새 연합장이 부연합장이었다면 LEADER로 승격.

**폐쇄**: 연합장이 `/연합 폐쇄` → 확인 GUI(또는 채팅 확인 메시지) 표시 → 한 번 더 확정해야 실제로 삭제. 삭제되면 모든 소속 토지가 무소속이 되고, 대기 중이던 가입 신청도 전부 삭제.

**소개글 수정**: 연합장이 `/연합 소개글 <내용>`.

**조회**: `/연합 정보 [이름]` (소속 토지 목록/레벨/소개글), `/연합 목록` (전체 연합 이름+레벨+인원수).

## 명령어 목록

`/연합 생성|가입신청|신청목록|수락|거절|탈퇴|추방|부연합장임명|부연합장해임|위임|폐쇄|소개글|정보|목록`

## 테스트

순수 로직(정원 계산 `floor(level/10)`, 권한 판정 등)은 Bukkit/JDBC 의존이 없는 별도 클래스로 뽑아서 JUnit 테스트. DB/Bukkit 의존 코드(레포지토리, 매니저, 명령어)는 이 세션의 기존 관례대로 테스트하지 않는다.

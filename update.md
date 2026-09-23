# 업데이트 내역

> 날짜별로 새로 추가되거나 수정된 사항을 기록합니다. 새 작업을 할 때마다 맨 위(최신 날짜)에 이어서 추가합니다.

## 2026-09-23

### 연합 폐쇄(확인 GUI)/소개글 수정 명령어 추가 (yeowool-federation)
`/연합` 명령어에 `폐쇄`, `소개글` 2개 하위명령어를 추가했습니다.
- `/연합 폐쇄` — 연합장 권한으로 연합을 완전히 삭제. 바로 삭제되지 않고 확인용 GUI(폐쇄 확정/취소 버튼)가 먼저 뜨며, 취소를 누르거나 그냥 창을 닫으면 연합은 그대로 남습니다. 폐쇄 확정을 눌러야 실제로 삭제됩니다.
- `/연합 소개글 <내용>` — 연합장 권한으로 연합 소개글을 수정(255자 제한). `/연합 정보`에서 바로 확인 가능.

세 서버(lobby/town/wild) 다 jar 배포 완료 — **재시작 필요**. 재시작 후 테스트: 연합장이 `/연합 소개글 <내용>` → `/연합 정보`에 반영되는지 확인 / `/연합 폐쇄` → GUI가 뜨는지(바로 삭제되지 않는지) → 취소 버튼 누르고 `/연합 정보`로 연합이 그대로인지 확인 → 다시 `/연합 폐쇄` → 폐쇄 확정 버튼 누르고 `/연합 정보`로 "소속된 연합이 없습니다"가 나오는지 확인.

### 연합 부연합장 관리 및 위임 명령어 추가 (yeowool-federation)
`/연합` 명령어에 `부연합장임명`, `부연합장해임`, `위임` 3개 하위명령어를 추가했습니다.
- `/연합 부연합장임명 <닉네임>` — 연합장 권한으로 지정한 토지의 소유자를 부연합장으로 임명. 부연합장 정원은 연합 레벨별로 제한되며, 현재 모든 연합이 레벨 1이라 정원 0명(부연합장 불가) 상태입니다.
- `/연합 부연합장해임 <닉네임>` — 연합장 권한으로 부연합장을 일반 멤버로 강등.
- `/연합 위임 <닉네임>` — 현재 연합장이 다른 멤버에게 연합장 자리를 위임. 자신이 속한 연합의 멤버에게만 위임 가능.

세 서버(lobby/town/wild) 다 jar 배포 완료 — **재시작 필요**. 재시작 후 테스트: 현 상태에서 모든 신규 연합은 레벨 1(부연합장 정원 0명)이므로, `/연합 부연합장임명 <닉네임>` 시도 시 "정원이 꽉 찼습니다 (연합 레벨 1, 정원 0명)" 메시지가 나오는 게 **정상**입니다 — 레벨 업그레이드 이후 정원이 늘어나면 가능해집니다.

### 연합 탈퇴/추방 명령어 추가 (yeowool-federation)
`/연합` 명령어에 `탈퇴`, `추방` 2개 하위명령어를 추가했습니다.
- `/연합 탈퇴` — 자신이 속한 연합에서 탈퇴. 일반 멤버는 자유롭게 탈퇴 가능하지만, 연합장(리더)은 `federation.leader-must-transfer-first` 메시지와 함께 탈퇴 불가 — 먼저 연합장 자리를 다른 사람에게 넘겨야 함.
- `/연합 추방 <닉네임>` — 연합장/부연합장 권한으로 지정한 멤버를 연합에서 제거. 연합장 자리는 추방 불가.

세 서버(lobby/town/wild) 다 jar 배포 완료 — **재시작 필요**. 재시작 후 테스트: 일반 멤버가 `/연합 탈퇴` → 성공 / 연합장이 `/연합 탈퇴` → `federation.leader-must-transfer-first` 오류 / 연합장이 `/연합 추방 <일반멤버닉네임>` → 해당 멤버 제거 확인.

### 연합 가입신청/수락/거절 명령어 추가 (yeowool-federation)
`/연합` 명령어에 `가입신청`, `신청목록`, `수락`, `거절` 4개 하위명령어를 추가했습니다(기존 `생성`/`정보`/`목록`은 그대로).
- `/연합 가입신청 <연합이름>` — 내 토지로 해당 연합에 가입 신청
- `/연합 신청목록` — 내 연합(연합장/부연합장)에 들어온 대기 중인 가입 신청 목록 확인
- `/연합 수락 <닉네임>` / `/연합 거절 <닉네임>` — 신청자의 **플레이어 닉네임**으로 승인/거절 (다른 대상 지정 명령어들과 동일하게 땅 이름이 아니라 소유주 닉네임 기준)

세 서버(lobby/town/wild) 다 jar 배포는 완료했지만, RCON stop은 안 쓰는 규칙이라 **재시작은 아직 안 했습니다** — 사장님이 수동으로 재시작하셔야 새 명령어가 실제로 동작합니다. 재시작 후 테스트: 토지 소유자 B가 `/연합 가입신청 <A의 연합이름>`으로 신청 → A가 `/연합 신청목록`으로 B 확인 → `/연합 수락 <B닉네임>` → `/연합 정보`로 B가 멤버로 들어갔는지 확인.

### 상점 이동/NPC 연동 방식 변경 (YeowoolMarket)
`/상점이동`과 `/상점` 메인 메뉴의 이동 방식을 손봤습니다.
- **`/상점이동`**: 원래 특정 NPC 위치로 이동하는 방식이었는데, NPC 조회 없이 **좌표를 직접 설정**하는 방식으로 바꿨습니다. `config.yml`의 `npc-shop.location.{world,x,y,z,yaw,pitch}`에 상점가 좌표를 넣어두면 그대로 이동합니다(기존 `menu-teleport-npc-id` 설정은 삭제됨). 다른 서버에서 실행해도 상점가가 있는 서버(기본 lobby)로 자동으로 보내주는 기능은 그대로입니다.
- **`/상점` 메인 메뉴**: 지금까지는 상점 아이콘을 클릭하면 바로 그 상점 GUI가 열렸는데, 이제 그 상점에 Citizens NPC가 연결되어 있으면 **그 NPC 위치로 순간이동**만 시켜주고, NPC를 직접 우클릭해야 상점이 열립니다(다른 서버에 있어도 자동으로 넘어감). NPC가 아직 연결 안 된 상점은 예전처럼 바로 GUI가 열립니다.
- 상점 개수 자체는 원래도 제한이 없었고(메인 메뉴에만 12칸 제한 있음), 이번 변경과는 무관합니다.

세 서버 다 빌드 배포 완료, config.yml의 옛 설정값(`menu-teleport-npc-id`)도 정리했습니다 — **재시작 필요**. 재시작 후 상점가 좌표(`npc-shop.location`)를 아직 안 넣으셨으면 `/상점이동`은 "위치 미설정"으로 나옵니다.

## 2026-09-22

### 서버 동반 앱용 API 서버 신규 제작 (yeowool-app-api)
Electron/APK로 만드실 서버 동반 앱이 붙을 조회 전용 REST API를 별도 프로젝트(Node.js/Express)로 만들어 채팅으로 전달했습니다 — 이 저장소에는 포함되지 않고, 사장님이 말씀하신 다른 서버컴에서 독립적으로 돌리는 구조입니다.
- 기능: 잔액 조회(온/은행/캐시 — **충전 기능 없음, 조회만**), 우편함 목록, 진행 중인 경매 목록, 서버별(lobby/town/wild) 온라인 인원, 공지사항, 출석 상태.
- **로그인 연동을 위해 yeowool-core에 `/앱연동` 명령어를 새로 추가**했습니다 — 인게임에서 치면 5분짜리 6자리 코드가 나오고, 앱에서 그 코드를 입력하면 로그인됩니다(별도 회원가입 없음). `yw_player_settings` 테이블을 재사용해서 새 테이블 없이 구현. 세 서버 다 빌드 배포 완료 — **재시작 필요**.
- 우편함/경매 아이템은 서버 DB에 Java 전용 바이너리로 저장돼 있어서 API가 이름/아이콘까지는 못 읽음(발신처·가격·시간만 표시) — 자세한 내용과 해결 방법은 넘겨드린 코드의 README에 적어뒀습니다.
- API 서버 쪽 보안 체크리스트(읽기 전용 DB 계정 만들기, 방화벽, HTTPS 등)도 README에 정리해서 같이 보냈습니다.

### 레이드 티켓설정 명령어 변경 — 손에 든 아이템으로 자동 지정
`/레이드 티켓설정 <이름> <아이템즈어더ID> <수량>`이던 걸 `/레이드 티켓설정 <이름> <수량>`으로 바꿨습니다. 아이템즈어더 ID를 직접 타이핑할 필요 없이, 입장권으로 쓸 아이템을 손에 들고 명령어를 치면 그 아이템의 ID를 자동으로 읽어서 등록합니다(오타로 잘못된 ID가 등록되는 실수 방지). 손이 비어있거나 아이템즈어더 아이템이 아니면 오류 메시지가 뜹니다.
세 서버 다 빌드 배포 완료 — **재시작 필요**.

## 2026-09-19

### 레이드 맵 월드 재시작 후 안 불러와지는 문제 수정 (em_dark_spire)
어제 설치한 `em_dark_spire_1/2/3` 월드가 재시작 후 "월드가 로드되지 않음" 오류가 났던 문제를 고쳤습니다.
- **진짜 원인**: 어제 방식은 빈 오버월드 월드를 만들고 그 안에 `DIM-1`(네더) 폴더만 끼워 넣는 식이었는데, 이러면 MultiWorld/Paper가 그 안의 던전 데이터를 아예 못 읽습니다(오버월드 자체는 텅 빈 새 월드로 생성되고, 진짜 던전 건물은 접근 불가능한 상태로 묻혀있었음). 재시작 여부와 무관하게 애초에 잘못된 구조였습니다.
- **수정**: 던전 데이터(`DIM-1/region`, `DIM-1/data`)를 월드 폴더 최상위로 옮기고, 월드 자체를 **네더 환경(Environment: NETHER)으로 직접 임포트**하도록 다시 만들었습니다. 이제 `em_dark_spire_1/2/3` 월드에 입장하면 바로 던전 건물이 보입니다(별도로 네더 차원으로 들어갈 필요 없음 — 이 월드 자체가 네더 타입입니다).
- MultiWorld의 `auto_load_enabled` 플래그도 3개 월드 전부 켜서, 앞으로는 서버 재시작해도 자동으로 다시 로드됩니다.
- **인스턴스 좌표 등록 시 참고**: `/레이드 인스턴스설정 <이름> 0 입장` 등은 이제 `em_dark_spire_1` 월드에 들어가서 그냥 바로 찍으시면 됩니다(어제 안내와 달리 "네더로 한번 더 들어가라"는 절차 불필요).
- **추가 수정**: 월드 기본 스폰(0,0 근처)이 실제로는 건설 안 된 빈 구역이라 처음엔 "그냥 지옥"처럼 보였음 — 던전 건물은 스폰이 아니라 (x: -66~49, z: -53~119) 범위에 있었습니다. 3개 월드 전부 스폰을 실제 던전 위치(-66, 73, 50)로 옮겨서, 이제 `/world tp`만 해도 바로 건물이 보입니다.
- **최종 결론 — em_dark_spire 맵 폐기**: 파일을 직접 열어 확인해보니 이 팩은 벽으로 막힌 던전 건물이 아니라 자연 네더 지형 위에 발판/보물상자 몇 개만 드문드문 흩어진 "미니던전"(EliteMobs 용어) 구조였음. 레이드 아레나로 쓰기엔 부적합하다고 판단, 사장님 요청으로 **em_dark_spire_1/2/3 월드 3개 전부 삭제**했습니다.

### 레이드 아레나 스키매틱 교체 (arena_nether)
em_dark_spire 대신 새로 보내주신 `arena_nether.zip`(WorldEdit 스키매틱, 92×83×90 크기)을 세 서버 다 `plugins/WorldEdit/schematics/arena_nether.schem`로 배포했습니다. aloc_boss 아레나와 같은 방식 — 원하시는 위치에 `//schem load arena_nether` → `//paste`로 직접 붙여넣으신 다음 `/레이드 인스턴스설정`으로 좌표 등록하시면 됩니다.

## 2026-09-18

### 보스 레이드 시스템 신규 추가 (YeowoolRaid)
파티 단위로 들어가는 보스 레이드 시스템을 새 모듈 `yeowool-raid`로 만듦. 보스는 계속 추가할 수 있는 범용 구조 — 특정 보스 1개 전용이 아니라 관리자가 `/레이드` 명령어로 새 레이드를 계속 등록할 수 있음.
- **인스턴스**: 런타임에 월드를 새로 만들지 않고, 관리자가 WorldEdit로 미리 만들어둔 고정 좌표 슬롯(레이드당 기본 3개)을 씀 — 폐기장(`/폐기장설정`)과 같은 방식. 3개 다 차있으면 입장 거부.
- **입장**: Citizens NPC 우클릭 → 등록된 레이드 목록 GUI → 파티장 소지 입장권(ItemsAdder 아이템) 소모 → 빈 슬롯에 파티 전원 텔레포트 → 보스 스폰.
- **판정**: MythicMobs 보스 사망 = 승리, 파티 공유 부활 횟수(기본 5회) 소진 또는 제한시간(기본 20분) 초과 = 패배. 승리 시 참가자 전원에게 보상 풀(GUI로 등록, 쿠폰/캐시패키지와 같은 아이템 그리드 편집기 재사용)에서 개인별로 하나씩 굴려서 **우편함**으로 발송.
- **레이드 종료 시 정리**: 파티 전원 퇴장 좌표로 텔레포트 + 남은 보스 개체 제거까지 자동 처리(승리/패배/시간초과 전부).
- BetterHud 보스바/경고는 퀘스트 시스템과 같은 방식(리플렉션)으로 연동 — 안 깔려있어도 채팅 폴백으로 정상 동작.
- 관리자 명령어: `/레이드 생성|npc설정|몹설정|티켓설정|인원설정|제한시간설정|부활횟수설정|보상설정|인스턴스설정|인스턴스개수설정|새로고침|목록|정보|삭제`. 3서버가 같은 DB를 보므로, 한 서버에서 레이드를 수정하면 다른 두 서버는 `/레이드 새로고침`으로 갱신해줘야 함(재시작해도 반영됨).
- **테스트용 보스**: 보내주신 `aloc_boss_0.1`("Aloc The Demonic Mech") 팩을 세 서버 다 설치해뒀음 — MythicMobs 몹 ID는 `alocTheDemonicMech`. **아레나 스키매틱(`arenaAlocBoss.schem`)을 WorldEdit로 원하는 위치에 직접 붙여넣기(`//schem load arenaAlocBoss` → `//paste`)하고, Citizens NPC 배치 + `/레이드` 명령어로 실제 연결하는 건 사장님이 인게임에서 직접 해주셔야 함** — 좌표는 사장님만 정할 수 있어서 제가 대신 할 수 없는 부분.

세 서버 다 **재시작 필요**(신규 테이블 `yw_raid_definition`/`yw_raid_instance` 생성 + 새 MythicMobs/ModelEngine 콘텐츠 로드).

### 자수정 드래곤 탑승 몬스터 추가 (AmethystDragonMount)
보내주신 `AmethystDragonMount-1.4` 팩을 세 서버 다 설치함 — MCPets 탑승 펫으로 소환/승마 가능.
- MythicMobs 몹(`AmethystDragonPet`), MCPets 펫 정의, ModelEngine 모델(`amethystdragon.bbmodel`) 설치 완료.
- `/mcpets` 메뉴에 뜨게 하려고 새 카테고리(`amethystdragon-category.yml`)도 같이 만들어둠 — 안 만들면 아까 호버라이드 때처럼 메뉴에 안 뜸.
- 리소스팩 쪽은 이 팩에 같이 들어있던 자수정 무기/방어구 스킨(`amethystweaponsguide.txt`에 있던 부분)은 **일부러 제외**했습니다 — 넷헤라이트 도구/활/방패/낚싯대/가죽방어구처럼 서버에서 이미 다른 기능들이 쓰고 있는 아이템의 모델을 덮어씌울 위험이 있어서, 드래곤 탑승 기능에 필요한 부분(몹 텍스처, 사운드, 소환 이펙트, 신호봉 아이콘)만 설치했습니다. 무기/방어구 스킨은 따로 검토가 필요합니다.
- **확인 필요**: ModelEngine 모델이 실제로 인게임에서 제대로 보이는지는 재시작 후 직접 확인해봐야 합니다 — 이 플러그인이 리소스팩에 텍스처를 정확히 어떤 경로로 반영하는지 100% 검증하지 못했습니다(aloc_boss도 마찬가지 상황).

### 레이드 맵 월드 3개 설치 (em_dark_spire, 로비 서버)
보내주신 `em_dark_spire_v8` 팩을 확인해보니 MythicMobs용이 아니라 **EliteMobs**라는 별도 던전 플러그인용 콘텐츠였습니다(보스/아이템/보물상자가 EliteMobs 자체 형식). EliteMobs는 안 설치하고, `worldcontainer` 안의 **월드(건물 구조)만 추출**해서 레이드 인스턴스로 씀 — 보스/보상은 그대로 yeowool-raid + MythicMobs 조합을 씀.
- 인스턴스 슬롯 3개 = 월드 3개(`em_dark_spire_1`, `em_dark_spire_2`, `em_dark_spire_3`)로 구현. 좌표만 다른 게 아니라 아예 독립된 월드라서, 한 파티가 뭘 부수거나 죽여도 다른 파티 인스턴스에 전혀 영향 없음 — 기존 계획의 "한 월드 안에 여러 좌표 슬롯" 방식보다 더 확실하게 분리됨.
- 로비 서버에 MultiWorld로 1개 임포트 + 2개 클론해서 3개 다 로드 완료.
- **중요**: 이 월드는 오버월드가 아니라 **네더 차원 안에 실제 건물이 있습니다** (`DIM-1`에만 region 데이터가 있음). `/레이드 인스턴스설정`으로 좌표 등록하실 때 반드시 해당 월드의 **네더**로 이동한 상태에서 찍어주셔야 합니다.
- `/레이드 인스턴스설정 <이름> 0 입장` 등을 em_dark_spire_1 네더에서, `1`은 em_dark_spire_2 네더에서, `2`는 em_dark_spire_3 네더에서 찍으시면 됩니다.

## 2026-09-16

### Citizens 연동 퀘스트 시스템 신규 추가 (YeowoolQuest)
유튜브 영상(BetterHud + Citizens 조합)을 참고해서 요청주신 퀘스트 시스템을 새 모듈 `yeowool-quest`로 만듦. 원래는 Skript 스크립트로 구상하셨지만 "플러그인 형식으로 만들어 줘도 돼"라고 하셔서 순수 Java 플러그인으로 구현.
- NPC를 우클릭하면 그 NPC가 가진 퀘스트 목록이 뜨고(1개면 바로 시작, 여러 개면 GUI 목록), 대사가 순서대로 나온 뒤 수락/거절 선택. 대사 줄 수 제한 없음.
- 목표 종류: **몹 처치**, **아이템 수집**, **대사만 있고 목표 없음**(즉시 완료) — 요청하신 3가지 그대로.
- 보상은 아이템만 (기존 쿠폰/캐시패키지에서 쓰던 54칸 GUI 아이템 편집기 재사용 — `ItemGridEditorGui`를 `yeowool-core`로 옮겨서 3번째로 공용화함).
- 관리자 명령어: `/퀘스트 생성|대사|대사초기화|목표|보상|목록|정보|삭제` (Citizens에서 NPC를 먼저 선택해야 `생성` 가능), `/퀘스트수락`, `/퀘스트거절`, `/퀘스트대사다음`.
- 플레이스홀더(`%yeowool_quest_이름/대사/진행도%` 등) 제공 — BetterHud 팝업 설정에서 이 값들을 읽어서 화면에 그리는 용도.
- **BetterHud 연동은 절반만 자동화됨**: 대사/수락선택 타이밍마다 BetterHud의 `CustomPopupEvent`를 쏴주는 것까지는 만들어놨지만(리플렉션으로 호출해서 BetterHud 없어도 플러그인은 정상 동작 — 안 깔려있으면 채팅으로만 표시됨), 그 팝업이 실제로 어떻게 생겼는지(대사창 UI, "SHIFT▷ 계속" 표시, 수락/거절 버튼)는 BetterHud를 직접 설치하신 뒤 BetterHud 자체 설정 파일(`config.yml`의 `betterhud.dialogue-popup`/`decision-popup`에 적힌 이름의 팝업 2개)로 사장님이 만드셔야 함. 버튼 입력은 `/퀘스트대사다음`, `/퀘스트수락`, `/퀘스트거절` 명령어를 실행하도록 걸어주시면 됨. BetterHud가 없는 지금 상태에서도 대사/수락/거절/목표진행은 채팅으로 전부 정상 동작.
- Citizens는 필수 의존(`depend`) — 세 서버 모두 이미 설치되어 있는 것 확인함.

lobby, town, wild 세 곳 모두 `yeowool-quest` 신규 jar + `yeowool-core`/`yeowool-admin` jar(ItemGridEditorGui 이동으로 같이 바뀜) 배포 완료. **재시작 필요** (신규 테이블 `yw_quests`/`yw_quest_dialogue`/`yw_quest_progress` 생성).

### 디스코드 인증 완료 DM 추가
역할 지급이 끝나면 봇이 개인 DM으로 "인증이 완료되었습니다. 마인크래프트 계정(닉네임)과 연동이 되었습니다."를 보내도록 함. `yw_discord_role_grant_queue`에 `minecraft_username` 컬럼 추가(기존 서버는 자동 마이그레이션). JS 봇 쪽 변경은 파일로 안 보내고 메시지로 diff만 전달함(사장님 요청).

로비 서버 **재시작 필요**. 봇 쪽은 `src/relays/roleGrantQueue.js` 수정 후 재시작 필요.


### 디스코드 계정 인증 연동 (/인증 → /인증코드 → 역할 자동 지급)
디스코드에서 `/인증`을 치면 본인에게만 보이는 4자리 코드를 받고, 게임에서 `/인증코드 <코드>`를 입력하면 계정이 연동되고 지정된 디스코드 역할(1540326988894441562)이 자동으로 부여되는 기능.
- **[YeowoolDiscord]** 새 테이블 `yw_discord_verify_codes`(1회용 코드), `yw_discord_role_grant_queue`(역할 부여 요청 큐) — 기존 `yw_discord_warn_queue`와 같은 패턴. 계정 연동 자체는 이미 있던 `yw_account_links` 테이블(웹사이트 /mypage 연동과 동일한 테이블)을 그대로 재사용.
- `/인증코드 <4자리>` 신규 명령어 — 코드 검증 → `yw_account_links`에 연동 기록 → `yw_discord_role_grant_queue`에 역할 부여 요청 적재. 이미 다른 플레이어와 연동된 디스코드 계정이면 거부.
- `config.yml`에 `verify-role-id` 추가, 로비 라이브 config에도 반영 완료(1540326988894441562).
- **JS 봇 쪽**(별도 배포, 이 git 저장소 밖): `/인증` 슬래시 명령어(4자리 코드 발급, ephemeral 응답, 5분 만료) + `yw_discord_role_grant_queue`를 폴링해서 실제로 역할을 지급하는 릴레이 추가. 봇 코드는 별도 zip으로 전달함(같은 LAN 다른 PC에서 도는 별도 프로세스라 이 저장소에는 포함 안 함).
- **YeowoolDiscord는 로비에만 설치되어 있던 단일 서비스**라는 걸 뒤늦게 확인 — 원래 습관대로 town/wild에도 jar를 복사했다가, config.yml/로그 어디에도 흔적이 없어서 원래 안 깔려있던 게 맞다는 걸 확인하고 되돌림. town/wild에는 배포 안 함(같은 DB 큐를 여러 인스턴스가 동시에 폴링하면 중복 처리될 위험).

로비 서버 **재시작 필요**. 별도 PC의 디스코드 봇은 전달된 zip으로 갱신 후 재시작 필요.


### 명예의 전당 시스템 신규 추가 (순위 + Citizens 동상 + 플레이스홀더)
돈/마을/접속시간 3개 부문 TOP 10 순위 시스템.
- `/명예의전당 돈|마을|접속시간` — 채팅으로 TOP 10 확인. 돈은 온 지갑+은행 합산, 마을은 토지 레벨(기존 마을대항 이벤트와 같은 기준), 접속시간은 분 단위 누적.
- **OP 권한을 가진 플레이어는 순위에서 자동 제외**됨(여유분을 더 조회한 뒤 필터링).
- **PlaceholderAPI**: `%yeowool_rank_<money|land|playtime>_<1~10>_<name|value>%` — 플레이어 컨텍스트 없이도 동작해서 표지판/홀로그램 등 아무 위치에나 배치 가능.
- **Citizens 동상** (Citizens 설치 시에만 동작): `/명예의전당 동상설정 <돈|마을|접속시간> <1|2|3>`을 원하는 위치에 서서 실행하면, 그 자리에 실제 1~3위 플레이어의 스킨을 입힌 NPC가 세워지고 닉네임이 이름표로 뜸. 순위가 바뀌면 스킨/이름표가 자동으로 갱신됨(5분마다 갱신, `ranking.refresh-interval-minutes`로 조절 가능).
- 새 테이블: `yw_ranking_statues`(동상 위치+Citizens NPC id 저장). 순위 자체는 기존 `yw_players`/`yw_player_statistics`를 직접 쿼리(캐시 레이어 우회 — 오프라인 플레이어도 순위에 포함되어야 해서).
- Citizens의 `citizens-main`(SkinTrait이 들어있는 본체 jar)을 신규 의존성으로 추가 — POM의 전이 의존성 일부가 우리 저장소 목록에서 안 받아져서 `isTransitive = false`로 처리(컴파일 전용이라 문제없음).

세 서버 모두 배포함. 세 서버 모두 **재시작 필요**. Citizens가 설치되어 있어야 동상 기능이 동작합니다(순위/플레이스홀더는 Citizens 없어도 동작).

### 명예의 전당 — 채팅용 순위 조회 명령어 제거
`/명예의전당 돈|마을|접속시간`(채팅으로 TOP10 보여주는 기능)은 필요 없다고 하셔서 제거. `/명예의전당 동상설정 ...`(관리자 전용, Citizens 동상 위치 등록)만 남김 — 순위 자체는 플레이스홀더로만 확인.

세 서버 모두 배포함. 세 서버 모두 **재시작 필요**.


### 캐시 패키지 시스템 신규 추가 (무기/방어구 세트를 하나의 상품으로 판매)
deepsea_relics 같은 아이템 세트를 낱개가 아니라 "패키지" 하나로 캐시상점에 팔기 위한 시스템.
- `/패키지생성 [이름]` — 54칸 GUI(`ItemGridEditorGui`, 쿠폰 시스템에서 만든 것 재사용)가 열리고, 담을 아이템을 넣은 뒤 닫으면 그 구성으로 저장.
- `/패키지관리 목록|[이름] 정보|삭제|수정` — 쿠폰관리와 동일한 패턴.
- `/패키지지급 [닉네임] [패키지이름]` — 패키지 안의 아이템 전부를 지급. **콘솔에서도 실행 가능**하고, 대상이 오프라인이면 우편함으로 보내짐(`core.mailbox()`) — Tebex 같은 결제 대행사가 구매 시점에 콘솔 명령으로 이 명령어를 그대로 호출하도록 게임서버커맨드로 등록하면 됨. 기존 `/캐시지급`(캐시 화폐 전용)과 같은 역할을 아이템 묶음에 대해 하는 것.
- DB: 새 테이블 `yw_cash_packages` (기존 `ItemStackSerializer.serializeArray`로 여러 아이템 직렬화, 쿠폰과 동일한 방식).
- 쿠폰 시스템의 아이템-그리드 편집 GUI를 `CouponRewardGui`에서 재사용 가능한 `com.yeowool.admin.gui.ItemGridEditorGui`로 옮겨서 패키지 시스템과 공유.

세 서버 모두 배포함. 세 서버 모두 **재시작 필요**.


### 쿠폰 다중 보상을 인벤토리 스냅샷 대신 전용 GUI 방식으로 변경
방금 만든 "인벤토리 전체를 스냅샷" 방식 대신, 요청에 따라 `/여울관리 기본템`(StarterKitEditorGui)과 같은 패턴의 6x9(54칸) 전용 GUI로 교체. `/쿠폰생성 [이름] [만료일]`을 실행하면 [CouponRewardGui.java](yeowool-admin/src/main/java/com/yeowool/admin/coupon/CouponRewardGui.java)(모든 칸이 편집 가능한 GUI)가 열리고, 아이템을 넣은 뒤 GUI를 닫으면 그 내용이 그대로 보상으로 저장됨. `/쿠폰관리 [이름] 수정 아이템`도 동일한 GUI를 기존 보상으로 미리 채운 채로 열어서 수정. 아무것도 안 넣고 닫으면 저장되지 않음. 인벤토리를 그대로 가져가던 이전 방식(`CouponInventoryPayload`)은 삭제.

세 서버 모두 배포함. 세 서버 모두 **재시작 필요**.

## 2026-09-14

### 감사(audit) 결과 후속 조치 — 폐기장 몹, 코스메틱 제거, 랭크아이콘 전체화, 쿠폰 다중보상, MCPets 연동
지난 감사에서 나온 항목들을 처리함.

- **[YeowoolLife] 폐기장 일반 몹 설정**: `scrapyard.mob.ids`가 빈 배열이었는데, 로비 `MythicMobs/packs/goblin_mobs-amonde/`에 이미 있던 고블린 5종(am_goblin_brute/mage/melee/ranger/whip)으로 채움. repo 기본값 + lobby 라이브 config.yml 둘 다 반영.
- **[YeowoolCommunity] `/코스메틱` 완전 제거**: 파티클 오라 3종 + 채팅색 2종짜리 자체 상점(`cosmetic` 패키지 전체, `CosmeticCommand/Manager/Definition/ShopGui/ParticleTrailTask`)을 삭제. 채팅 이름색을 코스메틱에서 가져오던 `ChatChannelService`의 로직도 제거하고 기본 흰색으로 되돌림. plugin.yml/config.yml의 관련 항목도 제거 (repo 기본값 + 세 서버 라이브 config.yml 전부). CosmeticsCore(설치는 돼있지만 우리 코드와 무관한 별도 플러그인)는 그대로 둠 — 이번엔 우리 자체 미니 코스메틱 상점만 없앤 것.
- **[YeowoolCommunity] 랭크아이콘 전체 목록화**: `rank-icons`가 beautiful_ranks.yml의 "_icon" 계열 14개만 있었는데, "exemple"(팩 제작사 튜토리얼용)을 제외한 전체 — 기본형 14개 + 유튜브/트위치/틱톡 크리에이터 뱃지 9개까지 총 37개로 늘림. repo 기본값 + 세 서버 라이브 config.yml 전부 반영.
- **[YeowoolAdmin] 쿠폰 다중 보상 지원**: 지금까지 `/쿠폰생성`이 주손 아이템 1개만 등록 가능했는데, 이제 **인벤토리(핫바+27칸)에 든 아이템 전부**를 보상으로 등록/수정할 수 있도록 [Coupon.java](yeowool-admin/src/main/java/com/yeowool/admin/coupon/Coupon.java) 등 관련 클래스 전체(`CouponManager`, `CouponRepository`, `CouponCreateCommand`, `CouponManageCommand`, `CouponRedeemListener`)를 `ItemStack` → `List<ItemStack>`로 변경. DB 저장은 기존 `ItemStackSerializer.serializeArray`(cross-server 인벤토리 동기화에 이미 쓰던 것) 재사용이라 스키마 변경 없음. 모루 미리보기는 한 칸만 있어서 첫 번째 아이템만 보여주고 "외 N종 추가 지급" 문구를 붙임.
- **[YeowoolLife] MCPets 연동**: 길들이기(`PetTamedByPlayerEvent`)와 레벨업(`PetLevelUpEvent`) 시 land XP 지급하도록 [MCPetsXpListener.java](yeowool-life/src/main/java/com/yeowool/life/pets/MCPetsXpListener.java) 신규 작성 (`pets.xp-per-tame: 20`, `pets.xp-per-levelup: 5`). AddCook과 마찬가지로 MCPets도 공개 Maven 저장소가 없어서 `yeowool-life/libs/MCPets-4.1.6.jar`을 로컬 참조로 추가(gitignore 처리, 빌드 전 `plugins/MCPets*.jar` 복사 필요).
- **커스텀 작물(ItemsAdder) 연동은 보류**: `custom-farming.crops`에 실제로 등록할 수 있는 "성장 단계가 있는 심는 작물" 콘텐츠가 서버에 전혀 없음(moafarm_items는 성장 없는 단순 보상용 아이템, customcrops 폴더는 별도 CustomCrops 플러그인용 리소스팩). 실제 자산이 생기면 등록 가능 — 코드는 이미 완성되어 있음.
- SkBee(사장님이 아직 Sk 스크립트 안 만드심)와 NPC 상점(추후 예정)은 이번엔 손대지 않음.

lobby/town/wild 세 서버 모두 배포함. 세 서버 모두 **재시작 필요**.


### 야생 서버 — 커스텀 광물(W6 Custom Mining) 자연 스폰 활성화
`/광석소환`으로 수동 소환은 됐지만 자연 스폰이 전혀 안 되던 문제. 원인은 `[MythicMobs]` `config/config-spawning.yml`의 `RandomSpawning.GenerateSpawnPoints: false` — 이게 꺼져 있으면 `randomspawns/workshop_six/w6_custom_mining_spawns.yml`에 광물 몹 스폰 규칙(석탄/구리/철/금/다이아/에메랄드/청금석/레드스톤/자수정/쿼츠/네더라이트, 총 11종)이 이미 다 있어도 MythicMobs가 스폰 위치 생성 자체를 안 함. `GenerateSpawnPoints: true`로 켜고, 기존 설정이 쓰던 필드(SpawnRadiusPerPlayer 등)에 맞는 생성 방식인 `Generator: LEGACY`로 지정(MythicMobs JAR 안 `GeneratorType` enum 확인: NONE/CLUSTER/REGIONAL/LEGACY 중 기존 필드 구성과 일치하는 값). **야생 서버에만** 적용(사장님이 야생만 요청).

야생 서버 **재시작 필요**.

### 야생 서버 — 네더라이트/석영 광물은 자연 스폰에서 제외
`[MythicMobs]` `randomspawns/workshop_six/w6_custom_mining_spawns.yml`에서 `quartz_ore`/`netherite_ore` 항목을 주석 처리해서 자연 스폰 목록에서 뺌(나머지 9종은 그대로 자연 스폰). `/광석소환 석영`, `/광석소환 네더라이트`로 수동 소환은 그대로 가능.

야생 서버 **재시작 필요**.


### 작물 드랍 2배 이벤트를 CustomCrops(외부 플러그인)에도 적용, 요리/커스텀 작물 XP 추가
- **작물 드랍 배율이 CustomCrops 플러그인 작물에도 적용**: 기존엔 바닐라 작물(밀/당근 등)에만 적용됐음. CustomCrops의 공식 API(`net.momirealms:custom-crops`, momirealms 저장소에서 가져옴)의 `DropItemActionEvent`를 새로 걸어서 [CustomCropsHarvestListener.java](yeowool-life/src/main/java/com/yeowool/life/farming/customcrops/CustomCropsHarvestListener.java)에서 같은 배율을 적용하도록 함.
- **CustomCrops 작물도 이제 수확 시 XP를 줌**: 지금까지 계절 동기화만 있고 XP는 전혀 안 주고 있었음 — CustomCrops의 `CropBreakEvent`(플레이어가 직접 부순 + 다 자란 마지막 단계일 때만)로 새로 연결. `config.yml`에 `customcrops.xp-per-harvest: 5` 추가.
- **요리(AddCook)도 XP를 주도록 추가**: 지금까지 요리는 XP를 전혀 안 주고 있었음(레시피 조회 GUI만 있었음) — AddCook의 `CookCompleteEvent`로 새로 연결한 [CookXpListener.java](yeowool-life/src/main/java/com/yeowool/life/cooking/addcook/CookXpListener.java) 추가. `config.yml`에 `cooking.xp-per-cook: 5` 추가. AddCook은 공개 Maven 저장소가 없는 유료 플러그인이라, 컴파일용으로 `yeowool-life/libs/AddCook-3.8.2.jar`(git에는 안 올라감, `.gitignore` 추가 — 빌드하려면 `plugins/AddCook-*.jar`을 그 경로에 복사해둬야 함)를 로컬 참조로 추가.
- 참고: 우리 자체 ItemsAdder 기반 커스텀 작물 시스템(`custom-farming` 설정, [CustomFarmingListener.java](yeowool-life/src/main/java/com/yeowool/life/farming/custom/CustomFarmingListener.java))은 원래부터 수확 시 XP를 이미 주고 있었음 — 이번에 새로 손댄 건 CustomCrops(별도 외부 플러그인)와 AddCook 두 가지.

세 서버 모두 배포함. 세 서버 모두 **재시작 필요**.


### Portal Core 아이템 설명 한글화
`[MythicMobs]` `packs/PortalCore/items/portals_portalcore.yml`의 Display/Lore가 원본(Nexo 팩) 그대로 영어였던 것을 한글로 변경 — "Portal Core" → "포탈 코어", 우클릭 안내 문구도 한글화. lobby/town/wild 세 서버 모두 반영.

세 서버 모두 **재시작 필요**(또는 `/mm reload`).

### 서버 이벤트(XP/작물 드랍 배율)를 운영자가 직접 켤 수 있도록 `/서버이벤트` 명령 추가
기존 `/이벤트 시작 <이름> <배율> <분>`은 XP 배율만 지원하고 채팅 공지만 있었음. 이번에 추가:
- `EventManager`에 이벤트 종류(XP/작물 드랍) 개념 추가, 작물 드랍 배율을 core에 새로 추가한 `LandStatService.setCropDropMultiplier`로 저장하고 [FarmingListener.java](yeowool-life/src/main/java/com/yeowool/life/farming/FarmingListener.java)의 `BlockDropItemEvent`에서 밀/당근/감자/비트/코코아/호박/수박/사탕수수 드랍량에 곱해서 적용.
- 이벤트 진행 중에는 보스바(남은 시간 실시간 갱신)가 모든 플레이어 화면에 뜨도록 [EventManager.java](yeowool-community/src/main/java/com/yeowool/community/event/EventManager.java)에 추가.
- 새 명령어 `/서버이벤트 <XP|작물드랍> <배율>` (예: `/서버이벤트 XP 2배`, `/서버이벤트 작물드랍 2배`) — 지속시간 1시간 고정, `/서버이벤트 종료`·`/서버이벤트 정보`도 지원. 플레이어 전용 체크가 없어서 **콘솔에서도 그대로 사용 가능**. [ServerEventCommand.java](yeowool-community/src/main/java/com/yeowool/community/event/ServerEventCommand.java) 신규 작성.
- 기존 `/이벤트` 명령(이름/배율/분 자유 설정 + 보상받기)은 그대로 유지 — 두 명령이 같은 EventManager를 공유해서 한쪽으로 시작하면 다른 쪽 정보로도 조회됨.

lobby/town/wild 세 서버 모두 배포함. 세 서버 모두 **재시작 필요**.


### Portal Core 아이템팩 — Nexo → ItemsAdder 변환 + DeluxeMenus 대신 자체 GUI로 구현
구매하신 "Portals - The Portal Core" 아이템팩(`E:\라테르에 쓸것들\Portals The Portal Core`)을 우리 서버 구성으로 변환.
- **리소스팩**: Nexo용 `Pack: {texture, custom_model_data}` 정의 7개(코어 아이템 + 화살표 2개 + 방향 아이콘 4개)를 ItemsAdder 콘텐츠 팩으로 변환 — `[ItemsAdder]` `plugins/ItemsAdder/contents/portals_portalcore/resourcepack/assets/minecraft/{textures/portals_texture, models/item}` 아래에 COAL 기반 vanilla model override(`coal.json`의 `overrides` 목록, CustomModelData 100001~100007은 원본과 동일하게 유지)로 배치. 메뉴 제목에 쓰이던 커스텀 글리프(폰트 이미지)는 우리 GUI가 일반 텍스트 제목을 쓰므로 변환하지 않고 생략.
- **아이템/스킬**: `[MythicMobs]` `plugins/MythicMobs/packs/PortalCore/` 그대로 이식(아이템 정의는 Nexo와 무관하게 raw CustomModelData라 수정 불필요). 메뉴를 여는 스킬(`Portals_Core_MenuOpen`)만 `dm open portals_portalcore_menu`(DeluxeMenus) → `포탈코어`(우리 명령어)로 변경.
- **GUI**: DeluxeMenus가 서버에 없어서(원본 GUI는 DeluxeMenus 전용) 새로 설치하는 대신, 기존 `YeowoolGui`/`GuiButton` 프레임워크로 [PortalCoreGui.java](yeowool-teleport/src/main/java/com/yeowool/teleport/portalcore/PortalCoreGui.java) + [PortalCoreCommand.java](yeowool-teleport/src/main/java/com/yeowool/teleport/portalcore/PortalCoreCommand.java)를 새로 작성, `yeowool-teleport`에 `/포탈코어` 명령으로 추가. 원본 구성 그대로 5페이지(1페이지는 다음만, 2~4페이지는 이전/다음만 있는 빈 틀, 5페이지는 이전만) — 2~4페이지는 원본 템플릿부터 내용이 비어 있어 그대로 유지.
- MythicCrucible은 사장님이 직접 다운로드해서 설치하기로 함(현재 `plugins/MythicCrucible-5.12.0.jar`는 이미 있으나 아직 구동된 적 없어 설정 폴더 미생성 — 재시작하면 생성됨).
- **lobby/town/wild 세 서버 모두** 배포함.

세 서버 모두 **재시작 필요** (ItemsAdder 리소스팩 재빌드 + MythicMobs/MythicCrucible 리로드).

### Portal Core 텍스처가 흑자홍(누락 텍스처)으로 보이던 문제 수정
재시작 후 아이템/GUI 화살표 아이콘이 전부 마인크래프트 기본 "텍스처 없음" 패턴(검정-보라 체크무늬)으로 보임. 원인은 1.21.4부터 바뀐 아이템 모델 시스템 — 예전 방식(`models/item/coal.json`의 `overrides` 목록)은 더 이상 읽히지 않고, `assets/minecraft/items/coal.json`에 새 `range_dispatch` 형식으로 정의해야 함. 이 서버에 이미 설치된 ModelEngine이 정확히 이 방식(`items/leather_horse_armor.json`)을 쓰고 있는 걸 확인하고 동일한 구조로 [ItemsAdder] `contents/portals_portalcore/resourcepack/assets/minecraft/items/coal.json`을 새로 작성(구식 `models/item/coal.json`은 삭제). lobby/town/wild 세 서버 모두 반영.

세 서버 모두 **재시작 필요**(또는 `/iareload` → `/iazip` 후 리소스팩 재적용).

### Portal Core 텍스처가 계속 깨지는 진짜 원인 — MythicMobs 자체 리소스팩 배포 기능과 충돌
MythicMobs를 5.9.5 → 5.12.1로 올린 뒤부터 완전 재접속을 해도 텍스처가 다시 깨지길래 로그를 봤더니, MythicMobs/Crucible이 자체 리소스팩 생성·배포 기능(`config-generation.yml`의 `Generation.Deployment`)으로 mcpacks.dev에 자기 팩을 업로드하고 `AutoSend`로 플레이어에게 자동 전송하고 있었음 — 이게 ItemsAdder가 만든 우리 리소스팩과 같은 `assets/minecraft/items/coal.json` 경로를 두고 충돌해서, COAL 기반인 Portal Core 아이템/아이콘만 골라서 깨지고 있었던 것(다른 ItemsAdder 콘텐츠는 COAL을 안 써서 멀쩡했음). `[MythicMobs]` `config/config-generation.yml`의 `Generation.Deployment.Enabled: true → false`로 꺼서 자체 배포를 막음. lobby/town/wild 세 서버 모두 반영. (이 수정만으로는 해결 안 됨 — 아래 항목 참고)

세 서버 모두 **재시작 필요**.

### Portal Core 텍스처 정상 확인
아틀라스 등록 수정 후 재시작 → 아이템/GUI 화살표 전부 정상 표시 확인됨. Portal Core 아이템팩 작업 완료.

### Portal Core 텍스처가 계속 깨지던 진짜 원인 — 텍스처 아틀라스 미등록
MythicMobs 배포 기능을 꺼도 여전히 깨져서 다시 확인해보니, 우리가 만든 팩에는 `assets/minecraft/atlases/blocks.json`(어떤 텍스처 폴더를 아틀라스에 포함시킬지 정하는 파일)이 아예 없었음. 바닐라 기본 아틀라스 설정은 `textures/item/`, `textures/block/` 같은 표준 폴더만 인식하는데, 텍스처를 임의로 만든 `textures/portals_texture/` 폴더에 넣어서 아틀라스에 전혀 등록이 안 되고 있었던 것 — 그래서 모델이 텍스처를 참조해도 찾지 못해 아이템/GUI 화살표 전부 깨진 텍스처로 보였음. `[ItemsAdder]` `contents/portals_portalcore/resourcepack/assets/minecraft/textures/portals_texture/` → 표준 폴더인 `textures/item/`으로 옮기고, 7개 모델 파일의 텍스처 참조도 그에 맞게 수정. lobby/town/wild 세 서버 모두 반영.

세 서버 모두 **재시작 필요**.


### MythicHUD 플러그인 완전 비활성화 (파티 HUD 미표시 문제 — 대체 플러그인으로 전환)
netty 패킷 주입 비활성화까지 해봤지만 여전히 화면에 아무것도 안 떠서, MythicHUD 자체의 문제로 결론 내리고 플러그인을 껐음. **[MythicHUD]** `plugins/MythicHUD-1.3.1-SNAPSHOT-all 76 .jar` → `plugins/_disabled/MythicHUD-1.3.1-SNAPSHOT-all 76 .jar.bak`로 이동, 설정 폴더 `plugins/MythicHUD/` → `plugins/_disabled/MythicHUD_config/`로 이동. **lobby/town/wild 세 서버 모두** 적용함 (앞으로 빌드/배포/제거는 세 서버 모두에 적용하기로 함). ItemsAdder의 `huds.enabled: false`는 lobby에서만 MythicHUD 전용 충돌 방지로 꺼뒀던 설정이라 그대로 둠(town/wild는 원래 true였고 그대로 둬도 충돌 없음). 파티 HUD는 다른 플러그인으로 대체 예정(다음 작업).

세 서버 모두 **재시작 필요**.


### MythicHUD — "리소스팩 손상" 결론 철회, netty 패킷 주입 충돌로 방향 전환
아까 zip 손상 결론은 철회함 — 리소스팩이 정말 깨졌다면 다른 ItemsAdder 콘텐츠(상점 아이템 등)도 다 안 보여야 하는데 그건 멀쩡했음(사장님이 직접 지적). 대신 **일반 보스몹(ent_keeper_boss)의 보스바는 정상적으로 보이는데 MythicHUD만 안 뜬다**는 걸 확인 — 표준 Bukkit BossBar API는 되는데 MythicHUD 고유 렌더링만 안 되는 것이므로, MythicHUD가 표준 API 대신 쓰는 저수준 netty 패킷 주입이 이 서버의 ProtocolLib과 충돌하고 있을 가능성으로 좁힘. [MythicHUD/config.yml](C:\YEOWOOL\lobby\plugins\MythicHUD\config.yml)의 `disable-netty-injection: false → true`로 변경해서 주입 방식을 꺼봄.

로비 서버 **재시작 필요**.


### MythicHUD 문제 — 진짜 마지막 원인: 옛날 수동 병합 폴더와 충돌
`copy-pack` 활성화 후에도 여전히 안 떠서 로그를 다시 보니, 방금 자동 생성된 `ItemsAdder/contents/mythichud/`와 9/7에 수동으로 넣어뒀던 `ItemsAdder/contents/yeowool_party/`가 **완전히 같은 경로에 수백 개씩 "Duplicate found" 충돌**을 일으키고 있었음. 옛날 폴더엔 바닐라 유니코드 폰트 텍스처 256장을 통째로 덮어쓰는 파일까지 있어서, 이 충돌이 리소스팩 빌드 자체를 깨뜨려 아까 발견한 `pack.mcmeta` 손상까지 설명됨. 옛날 `yeowool_party` 폴더를 제거함(`_disabled`로 이동, 이제 자동 동기화되는 `mythichud` 폴더 하나만 남음).

**정리하면 총 4가지 문제가 겹쳐 있었음**: ① MythicHUD.jar 중복 설치 ② `nexo-hook`이 미설치 플러그인을 가리키던 것 ③ ItemsAdder 자체 `huds` 기능과 충돌 ④ 옛날 수동 병합 폴더와 새 자동 동기화 폴더의 리소스팩 경로 충돌. 전부 수정 완료.

로비 서버 **재시작 필요**.


### MythicHUD가 화면에 아예 안 뜨던 문제 — 진짜 원인 발견 (ItemsAdder 자체 HUD 기능과 충돌)
파티 HUD만이 아니라 **MythicHUD가 렌더링하는 모든 것(기본/칼 레이아웃 포함)이 화면에 전혀 안 뜨는** 문제였음. 여러 단계로 원인을 좁혀나감:
1. `plugins/MythicHUD.jar`와 `plugins/MythicHUD-1.3.1-SNAPSHOT-all 76 .jar`이 완전히 동일한 파일로 중복 설치되어 있던 것 발견 → 중복 제거(`_disabled`로 이동). 효과 없었음(다른 원인이었음).
2. `nexo-hook: true`(Nexo 미설치 상태에서 켜져 있던 설정) → `false`로 변경 시도. 효과 없었음.
3. 서버가 실제로 배포 중인 리소스팩(`generated.zip`) 안의 `pack.mcmeta`가 압축 해제 시 오류가 나는 것 발견 — 진짜 원인 후보였으나, 사장님이 MythicHUD 공식 위키에서 **진짜 원인**을 찾아주심.
4. **최종 원인**: ItemsAdder에 자체 HUD 기능(`huds.enabled: true`)이 있는데 이게 켜져 있어서 MythicHUD의 보스바 렌더링과 충돌하고 있었음. 두 가지 수정:
   - [MythicHUD/config.yml](C:\YEOWOOL\lobby\plugins\MythicHUD\config.yml) — `copy-pack.enabled: true`, `path: ItemsAdder/contents/mythichud/resourcepack`로 설정해서 MythicHUD가 빌드한 리소스팩 에셋을 ItemsAdder 쪽으로 자동 내보내도록 함(지금까지는 이 자동화가 없어서 9/7에 수동으로 한 번 병합한 뒤로 전혀 갱신이 안 되고 있었음).
   - [ItemsAdder/config.yml](C:\YEOWOOL\lobby\plugins\ItemsAdder\config.yml) — `huds.enabled: false`로 꺼서 MythicHUD와의 충돌 제거.

로비 서버 **완전 재시작 필요** (ItemsAdder가 새 콘텐츠 인식 + huds 비활성화 둘 다 재시작 필요).


### MCPets 펫 모델 2종 추가 (Cubees Dragons / Space)
구매하신 "Cubees" 팩 2개(`Cubees_v20-Dragons.zip`, `Cubees-Space.zip`)를 서버에 설치함. 각 zip 안에 ItemsAdder/Oraxen/Nexo 세 가지 버전이 같이 들어있었는데, 이 서버는 ItemsAdder를 쓰므로 **ItemsAdder 버전만 설치하고 Oraxen/Nexo는 건너뜀**.
- [ItemsAdder/contents/cubees](C:\YEOWOOL\lobby\plugins\ItemsAdder\contents\cubees) — 아이콘 아이템 + 텍스처/사운드 (드래곤 8종 `dragons/`, 우주 8종 `space-i/`)
- [MCPets/Pets](C:\YEOWOOL\lobby\plugins\MCPets\Pets) — "Cubee v20 Dragons"(8마리), "Cubee Space I"(8마리) 펫 정의, [MCPets/Categories](C:\YEOWOOL\lobby\plugins\MCPets\Categories)에 각각 카테고리 등록
- [ModelEngine/blueprints/Cubees](C:\YEOWOOL\lobby\plugins\ModelEngine\blueprints\Cubees) — 3D 모델 블루프린트
- [MythicMobs/packs](C:\YEOWOOL\lobby\plugins\MythicMobs\packs) — `Cubees-MainConfig`(두 팩이 공유하는 이동/상호작용 스킬, 한 번만 설치) + 팩별 몹 정의

로비 서버 **재시작 필요**(새 콘텐츠 폴더라 리로드로는 인식 안 됨 — 이 프로젝트에서 계속 봐온 패턴과 동일).


### 파티 생성 시 자유가입/신청승인 선택 가능
지금까지 `/파티 가입 <이름>`은 항상 승인 없이 즉시 가입되는 방식뿐이었는데, 파티를 만들 때 가입 방식을 고를 수 있게 함:
- **GUI**: `/파티 생성` → 이름(모루) → 인원 선택 → **새로 추가된 가입방식 선택**(자유가입/신청승인) 순서로 진행.
- **명령어**: `/파티 <이름> <최대인원> [자유가입|신청승인]` (생략하면 기존과 동일하게 자유가입).
- 신청승인 파티는 `/파티 가입 <이름>`을 치면 즉시 들어가지지 않고 리더에게 알림이 가며, 리더가 `/파티 수락 <닉네임>` / `/파티 거절 <닉네임>`으로 처리. `/파티 정보`에서 리더에게는 대기 중인 신청 목록도 같이 보임.
- DB: `yw_party`에 `join_mode` 컬럼 추가(기존 파티는 전부 자동으로 `FREE`로 마이그레이션됨, MySQL 구버전 호환 방식으로), 신청 대기열용 `yw_party_join_request` 테이블 신설.

로비 서버에 yeowool-community jar 배포 완료, **재시작 필요**(재시작 시 DB 마이그레이션 자동 적용).


### `/내설정`에 귓속말 차단 / 거래 요청 차단 추가
[PlayerSettingsGui](yeowool-community/src/main/java/com/yeowool/community/settings/PlayerSettingsGui.java)에 두 토글 추가 — `PlayerData`에 `block.whisper`/`block.trade`로 저장하고, 실제 발송 지점에서 확인:
- [WhisperCommand.sendWhisper](yeowool-community/src/main/java/com/yeowool/community/chat/WhisperCommand.java) — `/귓속말`·`/답장` 둘 다 이 메서드를 거치므로 한 곳만 고치면 됨.
- [TradeManager.sendRequest](yeowool-market/src/main/java/com/yeowool/market/trade/TradeManager.java) — `/거래 <닉네임>` 요청 시점에 확인.

**파티 초대 차단은 뺐습니다** — `/파티 가입 <이름>`은 승인 없이 자유 가입하는 구조라 애초에 "초대"라는 개념이 없어서(README 8절에도 명시됨), 차단할 대상이 없습니다. 확인 후 진행 필요.

로비 서버에 yeowool-community/yeowool-market jar 배포 완료, **재시작 필요**.


### `/내설정` — 개인 설정 GUI 신설 (yeowool-community)
로비 브금은 클라이언트 음악 볼륨을 서버가 전혀 알 수 없어서, 볼륨을 0으로 줄였다가 다시 올려도 다음 재생 주기(최대 곡 길이만큼)까지 기다려야 하는 한계가 있었음 — 이를 해결하기 위해 서버가 직접 감지할 수 있는 온/오프 스위치를 `/내설정` GUI로 만듦. 켜면 즉시 재생 시작, 끄면 즉시 정지(`Player#stopSound`)해서 볼륨 슬라이더보다 훨씬 반응이 빠름. 설정값은 `PlayerData`에 저장되어 서버를 옮겨도 유지됨.
- [PlayerSettingsGui](yeowool-community/src/main/java/com/yeowool/community/settings/PlayerSettingsGui.java)/[PlayerSettingsCommand](yeowool-community/src/main/java/com/yeowool/community/settings/PlayerSettingsCommand.java) 신설 — 지금은 로비 브금 토글 하나뿐이지만, 앞으로 다른 온/오프 설정이 생기면 여기 버튼만 추가하면 되는 구조로 만들어둠(공용 "설정 API"를 미리 만들지는 않음 — 아직 이거 하나뿐이라 과한 추상화라고 판단).
- [LobbyBgmListener](yeowool-community/src/main/java/com/yeowool/community/ambience/LobbyBgmListener.java)에 `isEnabled`/`setEnabled` 추가 — 접속 시에도 꺼져있으면(설정이 `PlayerData`에 저장돼있으면) 아예 재생을 시작 안 함.

로비 서버에 yeowool-community jar 배포 완료, **재시작 필요**. 사장님이 다른 온/오프 설정 원하시는 게 있으면 알려주시면 이 GUI에 계속 추가하겠습니다.


### 로비 배경음악 추가
사장님이 만드신 브금(`yeowool_lobby.mp3`, 2분 56초)을 마인크래프트 리소스팩이 요구하는 OGG Vorbis로 변환(ffmpeg 없어서 winget으로 새로 설치 후 변환)해서 ItemsAdder 커스텀 사운드로 등록함([contents/yeowool_lobby_bgm](C:\YEOWOOL\lobby\plugins\ItemsAdder\contents\yeowool_lobby_bgm), `yeowool:lobby_bgm`).
- 새 [LobbyBgmListener](yeowool-community/src/main/java/com/yeowool/community/ambience/LobbyBgmListener.java) — 접속 시 재생 시작, 곡 길이(176초)에 맞춘 반복 작업으로 이어붙여서 계속 틀어줌(네이티브 루프 기능이 없어서 매번 다시 트는 방식 — 루프 지점에서 아주 살짝 안 맞을 수 있음), 접속 종료 시 작업 정리.
- `lobby-bgm.enabled`(기본 false, 여러 서버가 config.yml 공유)를 **로비 서버 배포본에서만 true로 설정** — 타운/야생에서는 안 나옴.
- 이 김에 발견한 `server-name: 야생`(로비 서버인데 잘못 설정됨) → `로비`로 수정함.

로비 서버에 yeowool-community jar 배포 완료, **재시작 필요**(새 리소스팩 콘텐츠 폴더는 재시작해야 인식됨).


### `/마을대항` — 마을간 경쟁 이벤트 신설 (yeowool-land)
`/마을대항 시작 <분>`(관리진, `yeowool.event.manage` 재사용)으로 정해진 시간 동안 소유자의 토지 XP 획득량이 가장 많은 마을을 겨루는 이벤트를 새로 만듦 — `/마을랭킹`이 이미 쓰고 있는 "소유자의 토지 레벨/XP = 마을 강함" 정의를 그대로 재사용해서, 멤버별 기여도 집계 같은 새 개념을 안 만들어도 됨. `SeasonScoreListener`(시즌랭킹)와 같은 방식으로 실제 토지 XP는 전혀 건드리지 않고 이벤트 진행 중에만 메모리에서 별도로 집계(`VillageEventManager`) — DB 테이블도 새로 안 만듦, 이벤트가 끝나면 집계값은 그냥 버려짐. 종료 시(자동 또는 `/마을대항 종료`) 우승 마을의 마을 은행에 보너스 온 지급(`village-event.reward-on`, 기본 50000) + 서버 전체 방송. `/마을대항 정보`(누구나)로 진행 중 실시간 순위 TOP 5 확인 가능.
- `LandCommand.displayName(Land)`를 패키지 전용 → `public`으로 완화(마을대항 커맨드에서도 "이름없는 마을" 폴백을 재사용하기 위함).

### 파티 HUD 안 뜨는 문제 — 서버 쪽 원인 배제, 진단 실험 진행 중
DB 직접 조회로 확인: 파티 생성/가입/`PartyPresenceTask`의 체력 기록까지 전부 정상 작동 중이었음(`yw_party`/`yw_party_member`/`yw_party_presence` 데이터 정상). ItemsAdder에 병합된 리소스팩 파일과 MythicHUD가 새로 빌드한 파일도 diff해서 완전히 동일함을 확인 — 리소스팩 문제도 아님. 바닐라 체력/허기/경험치 바(같은 MythicHUD가 렌더링)는 평소처럼 보인다는 것도 확인해서, MythicHUD 자체/리소스팩 수신은 정상이고 **파티 HUD 전용 조건 로직**(`hide` 조건)만 의심되는 상황. 원인을 좁히기 위해 [partyhud-p.yml](C:\YEOWOOL\lobby\plugins\MythicHUD\hud_assets\hud\partyhud-p.yml)의 `hide` 조건 4개를 임시로 제거함(체력 0일 때 회색으로 표시하는 조건은 남겨둠) — 재시작 후 파티가 아닐 때도 빈 칸이 회색으로라도 보이면 조건문 문법 문제로 확정, 그래도 안 보이면 더 깊게 봐야 함.

로비 서버에 yeowool-land jar 배포 완료(마을대항), MythicHUD 설정 변경(파티 HUD 진단)도 포함 — **재시작 필요**.

## 2026-09-13

### 메인 스레드 블로킹 제거: 이름으로 오프라인 플레이어 찾는 곳 전부 비동기화
`Bukkit.getOfflinePlayer(String 닉네임)`은 로컬 캐시에 없는 이름이면 Mojang API에 동기 HTTP 요청을 날려서, 접속한 적 없는 닉네임으로 `/정지`·`/음소거`·`/친구 추가`·`/토지 초대` 등을 실행하는 순간 서버 전체가 멈추는 문제가 있었음(TPS 급락). 새 공용 유틸 [OfflinePlayerResolver](yeowool-core/src/main/java/com/yeowool/core/util/OfflinePlayerResolver.java)(core)를 만들어서, 이 조회를 항상 비동기 스레드에서 하고 결과만 메인 스레드로 돌려주도록 통일함.
- 기존 공용 리졸버 [PlayerDataResolver](yeowool-core/src/main/java/com/yeowool/core/util/PlayerDataResolver.java)도 똑같은 문제가 있었어서 근본적으로 고침 — 이걸 이미 쓰던 `TitleCommand`/`RankIconCommand`/`SeasonManager`도 같이 고쳐짐.
- 직접 `Bukkit.getOfflinePlayer(String)`을 호출하던 20개 커맨드 클래스(정지/음소거/정지해제/음소거해제/경고/제재기록/여울관리/캐시지급/신고, 친구/커플/프로필/한글닉네임설정권, 토지 권한/추방/관리이전, 폐기장설정 초기화, 플레이어워프 소유권이전)를 전부 이 유틸로 교체.
- `/캐시지급`도 포함됨 — 방금 연동한 Tebex 결제가 실제로 호출하는 명령어라 결제 흐름에 영향 없는지 재배포 후 확인 필요.

### 저장소를 실제 배포 상태와 동기화 (git stash 복구)
[이전 항목](#2026-09-13) 참고 — 폐기장/파티/플레이타임/서버간 인벤토리 동기화 등 37개 파일을 stash에서 복구해 커밋 완료.

### 테스트 커버리지 추가 (enhance/market/community/discord/teleport)
지금까지 테스트가 0개였던 5개 모듈에 순수 로직 위주로 단위 테스트 추가:
- `EnhanceConfig`(강화 등급/성공확률/파괴위험 판정) — [yeowool-enhance](yeowool-enhance/src/test/java/com/yeowool/enhance/EnhanceConfigTest.java)
- `AuctionListing`/`AuctionManager.minIncrement`(경매 최소 입찰가 계산) — [yeowool-market](yeowool-market/src/test/java/com/yeowool/market/auction/AuctionListingTest.java)
- `BattlePassManager`의 CSV 직렬화(수령한 보상 목록 저장 형식) — [yeowool-community](yeowool-community/src/test/java/com/yeowool/community/battlepass/BattlePassManagerTest.java) (테스트 접근을 위해 `splitCsv`/`joinCsv`를 `private` → 패키지 전용으로 완화)
- `ConfigCrypto`(디스코드 봇 토큰 암호화, AES-256-GCM) — [yeowool-discord](yeowool-discord/src/test/java/com/yeowool/discord/ConfigCryptoTest.java)
- `RtpConfig`(무작위 순간이동 반경/쿨다운 설정 파싱) — [yeowool-teleport](yeowool-teleport/src/test/java/com/yeowool/teleport/rtp/RtpConfigTest.java)

`./gradlew test` 전체 통과. yeowool-enhance/market/teleport는 `FileConfiguration`/`ItemStack` 등 Bukkit 타입을 테스트에서 써야 해서 `testImplementation`으로 paper-api(+core) 의존성 추가.

### 권한 구조(`default: op`) 검토 — 코드 문제 아님, 조치 보류
`yeowool.*` 권한 126개 노드 전부 `default: op`인 건 버그가 아니라 `luckperms-setup.txt`에 이미 명시된 의도된 설계(총관리진 제외 전원 deop + LuckPerms 그룹으로 등급 분리) — 코드에서 `default: op`를 지우면 지금 관리자 계정도 즉시 모든 명령어를 못 쓰게 되는 회귀라 손대지 않음. 실제 등급 분리는 운영 작업(LuckPerms 스크립트 적용 + deop) 필요, 사장님 확인 대기 중.

로비 서버에 core/admin/community/land/life/teleport 6개 jar 배포 완료, **재시작 필요**.


### ModelEngine 4.1.0 ↔ ItemsAdder 충돌 근본 해결, 폐기장 보스를 ent_keeper로 복귀
[config.yml:38-42](C:\YEOWOOL\lobby\plugins\ModelEngine\config.yml)의 `Model-Generator.Register-Post-Server`/`Assets-Post-Server`/`Compile-Post-Server`를 전부 `false`로 변경 — ModelEngine이 리소스팩 에셋을 서버 기동 후 비동기로 늦게 만들던 것을 기동 중 동기적으로 먼저 만들도록 바꿔서, ItemsAdder가 `ModelEngine/resource pack/assets/cosmetics`를 조립 시점에 못 찾던 경쟁 상태가 해소됨(사장님이 직접 재시작해서 확인). 이제 ModelEngine 4.1.0으로 올려도 ItemsAdder 리소스팩 파이프라인이 정상 동작하고, `ent_keeper` 보스 모델도 정상 소환/렌더링됨.
- 폐기장 보스를 임시로 대체했던 `am_goblin_brute`에서 원래 계획한 `ent_keeper_boss`로 되돌림 (`scrapyard.boss.mob-id`, [yeowool-life/config.yml](yeowool-life/src/main/resources/config.yml) + 로비 서버 배포본 둘 다 수정).
- `ent_keeper_boss.yml`(MythicMobs) — `Options.Invincible: false → true`(플레이어가 못 죽이게, am_goblin_brute 때와 동일하게), `Display: 'Ent Keeper Boss' → '폐기장의 괴물'`(보스 이름 한글화). 보스바(`BossBar.Title`)는 전용 텍스처 폰트 이미지라 손대지 않고 그대로 둠.

로비 서버 재시작 필요(config.yml `mob-id` 변경 반영), MythicMobs는 `/mm reload`로도 반영 가능.

### 폐기장 보스 전용 보스바 중복 제거, 무적 이중화
`ScrapyardTickTask`가 MythicMobs 자체 보스바(전용 텍스처, "ENT KEEPER")와 별개로 YeowoolLife 코드가 직접 Adventure `BossBar`를 하나 더 띄우고 있었음(구버전 이름 "고블린 습격대장" 하드코딩) — 두 보스바가 겹쳐서 뜨는 문제라 후자를 완전히 제거함 (`ScrapyardSessionManager`의 `bossBars`/`showBossBar`/`hideBossBar`, `ScrapyardTickTask`의 호출부 삭제). `messages.yml`의 `scrapyard.boss-name`도 "폐기장의 괴물"로 갱신(더는 안 쓰이지만 다른 용도로 재사용될 수 있어 값만 정리).
- `Options.Invincible: true`인데도 보스가 대미지 누적으로 죽는 문제 확인 — `~onAttack`(공격 시 CancelEvent로 넉백/피격 이벤트 취소)과 같은 방식을 `~onDamaged`에도 추가해 맞는 순간 자체를 이중으로 차단함.

### 저장소를 실제 배포 상태와 동기화 (git stash 복구)
이 브랜치가 최근 세션에서 작업한 폐기장/서버간 인벤토리 동기화/CustomCrops 계절 동기화/AddCook 레시피북 GUI/CustomFishing 메뉴/파티 시스템/플레이타임 보상 시스템의 자바 소스를 커밋하지 않은 채로 있었음 — 실제로는 라이브 서버에 전부 배포되어 동작 중인데, `git stash`(브랜치 전환 전 임시 저장)에만 남아있고 워킹 트리엔 없는 상태였음. `git stash`의 untracked-files 커밋에서 총 37개 파일을 복구해 커밋함 — 이제 저장소가 실제 배포 상태와 일치함. 전체 모듈 `./gradlew compileJava` 정상 확인.

## 2026-09-09

### 폐기장 보스를 ent_keeper → am_goblin_brute로 교체, 던전/로비 월드 건축 금지, 보스 무적 설정
ent_keeper 보스 모델은 ModelEngine 4.1.0에서만 정상 동작하는데 4.1.0은 ItemsAdder를 통째로 깨뜨리는 버그가 있어 4.0.8로 되돌린 상태라 계속 안 보이는 문제였음 — 근본 해결(ItemsAdder의 ModelEngine 'Post-Server' 플래그 끄기, 아래 항목 참고) 대신 당장은 **`am_goblin_brute`(신규 설치한 goblin_mobs-amonde 팩의 몹)로 보스를 교체**하기로 함.
- `scrapyard.boss.mob-id`를 `ent_keeper_boss` → `am_goblin_brute`로 변경 ([config.yml](yeowool-life/src/main/resources/config.yml), 로비 서버 배포본 config.yml 둘 다 수정). 코드 변경 없음 — 보스 스폰은 원래 config 값을 그대로 `/mm mobs spawn`에 넘기는 구조.
- 보스가 소환은 되는데 즉시 사라지는 문제 발생 → 원인은 `zombie_dungeon` 월드의 난이도가 **평화로움(Peaceful, Difficulty 바이트 0)** 으로 개별 설정돼 있었던 것 (서버 기본값 `easy`와 무관하게 월드별로 따로 저장돼 있었음). 평화로움에서는 바닐라 규칙상 적대 몹이 스폰 즉시 자동 제거됨. `/execute in minecraft:zombie_dungeon run difficulty easy`로 해결.
- `am_goblin_brute`(보스로 쓰는 개체)에 `Options.Invincible: true` 추가해서 플레이어가 못 죽이게 설정 ([am_goblin_mobs.yml](C:\YEOWOOL\lobby\plugins\MythicMobs\Packs\goblin_mobs-amonde\mobs\am_goblin_mobs.yml), `/mm reload`로 적용).
- WorldGuard 글로벌 리전(`__global__`) `build` 플래그를 `lobby_world`, `zombie_dungeon` 두 월드 모두 `DENY`로 설정 — 두 월드에서 블록 설치/파괴 전면 금지 (콘솔/OP의 WorldGuard 우회 권한은 그대로 유지됨).

### 보스 모델 — ModelEngine 4.1.0 업데이트로 "Unknown format" 에러는 해결됨, 순서 문제 하나 남음
로그 확인 결과 4.1.0에서는 `ent_keeper`/`ent_spike`/`ent_stomp_vfx` 세 모델 전부 에러 없이 정상적으로 임포트되고, 실제 모델 파츠 파일(머리/팔/다리/가시 등 12개 부위)도 리소스팩 폴더에 다 만들어졌음 — 지난번 "Unknown format" 문제는 완전히 해결됨.
남은 문제는 타이밍: 서버가 켜질 때 ItemsAdder가 자기 리소스팩(`generated.zip`, 실제로 플레이어에게 배포되는 파일)을 조립하는 시점이, ModelEngine이 ent_keeper 모델을 성공적으로 다 만들어내는 시점보다 먼저였음(로그상 ItemsAdder 조립 01:04:34, ent_keeper 성공적으로 다시 만들어진 건 그 이후 `/meg reload` 이후인 01:07:20~26) — 그래서 지금 배포된 리소스팩엔 여전히 ent_keeper 텍스처가 없음.
서버가 지금 꺼져 있어서(정상 종료 확인됨) 마저 확인은 못 했음 — **재시작 후 `/iazip`을 한 번 실행**하면 지금 폴더에 이미 만들어져 있는 ent_keeper 모델을 포함해서 리소스팩을 다시 조립하고 배포할 것으로 예상됨. 재시작하시면 알려주세요, 확인하고 필요하면 `/iazip`까지 대신 실행해드리겠습니다.

### 보스 모델 후속 — ModelEngine 4.1.0이 진짜 원인으로 확인됨 (ItemsAdder가 아예 로딩이 안 되는 별개의 심각한 버그)
`/iazip`을 시도하기도 전에 더 근본적인 문제가 발견됨: ModelEngine을 4.1.0으로 올린 이후로 **재시작할 때마다 ItemsAdder가 자체 리소스팩 조립 태스크(task 206)에서 예외를 던지며 멈추고, 그 뒤로 "Plugin is still loading..."에 영원히 갇혀서 `/iareload`/`/iazip`이 전혀 안 먹힘**을 확인함. 에러는:
```
NoSuchFileException: .../ModelEngine/resource pack/assets/cosmetics
```
ItemsAdder가 ModelEngine이 만든 `assets/cosmetics` 폴더(CosmeticsCore 코스메틱용 모델 출력 경로)를 읽으려는 순간, ModelEngine이 리소스팩을 통째로 지웠다가 다시 만드는 타이밍이랑 겹쳐서 그 폴더가 잠깐 없는 순간을 잡아버리는 것으로 보임.

처음엔 "ent_keeper 모델이 이제 온전히 만들어지느라 리소스팩 조립이 오래 걸려서 타이밍 경쟁에서 진다"는 가설을 세웠으나, **`ent_keeper_boss` 블루프린트를 통째로 빼고 재시작해도 여전히 똑같이 멈추는 것을 확인** → 이 가설은 기각됨. 이어서 **ModelEngine을 4.0.8로 되돌리자 ItemsAdder가 다시 정상적으로 로딩됨**을 사용자가 직접 확인함 — 즉 **원인은 ModelEngine 4.1.0 버전 자체가 ItemsAdder와 상호작용하는 방식이 바뀐 것**임이 확실해짐 (ent_keeper 무게 문제가 아니었음).

**현재 트레이드오프 상황**:
- ModelEngine 4.0.8 → ItemsAdder/리소스팩 파이프라인 정상, 하지만 ent_keeper 보스 모델은 "Unknown format" 에러로 여전히 안 보임 (원래 문제로 회귀)
- ModelEngine 4.1.0 → ent_keeper 보스 모델은 정상적으로 만들어지지만, ItemsAdder 전체 리소스팩 조립이 매 부팅마다 깨짐 (서버 전체 커스텀 아이템/텍스처에 영향 — 훨씬 심각한 문제)

지금은 4.0.8로 되돌려서 서버 전체는 정상화된 상태. ent_keeper 보스를 보이게 하려면 ModelEngine의 더 최신 패치 버전이 있는지 확인하거나, ItemsAdder 쪽 업데이트/버그 리포트가 필요해 보임 — 다음에 이어서 확인 예정.

### 폐기장 안에서 접속 종료 후 재접속 시 자동으로 귀환지점(로비)으로 이동
접속 종료 시에도 이미 귀환지점으로 순간이동을 시도하긴 하지만, 연결이 끊기는 도중의 순간이동은 실제 저장 위치에 반영이 안 될 때가 있음(마인크래프트 자체의 알려진 동작 — 접속 종료 이벤트 처리 중 순간이동은 잘 저장이 안 됨). 그래서 재접속했을 때 아직 폐기장 구역(등록한 아레나 경계) 안이면 한 번 더 귀환지점으로 보내도록 추가해서 확실하게 처리함.

로비/타운/야생 3곳 배포 완료, 재시작 필요.

### 보스가 소환은 됐는데 안 보이는 문제 — ModelEngine 블루프린트 중복 파일 정리
로그 확인 결과 `[Mythic] Spawned 1x ent_keeper_boss!`로 몹 소환 자체는 정상이었음(좌표도 아레나 안, 문제 없음) — 안 보이는 이유는 ModelEngine이 이 몹에 씌울 3D 모델(ent_keeper.bbmodel)을 리소스팩으로 제대로 만들어내지 못해서(ModelEngine 몹은 커스텀 모델이 안 뜨면 기본 몸체가 화면에 그냥 안 보이는 방식이라 완전히 투명한 것처럼 보임). 원인은 `plugins/ModelEngine/blueprints/` 폴더 바로 밑에 예전부터 있던 `ent_keeper.bbmodel`(9/3일자, 이번에 새로 넣어드린 것과 완전히 동일한 파일)과, 이번에 새로 넣은 `blueprints/ent_keeper_boss/ent_keeper.bbmodel`이 같은 이름으로 중복 존재하면서 ModelEngine이 이 둘을 동시에 임포트하려다 충돌한 것으로 보임(로그에 "Importing ent_keeper.bbmodel"이 정확히 같은 시각에 두 번 찍힘, 결과물은 리소스팩 어디에도 안 만들어짐). 오래된 중복 파일을 삭제해서 이제 새로 넣은 것 하나만 남김.

재시작하시면 ModelEngine이 다시 임포트+리소스팩 생성을 할 텐데, 그 후에 확인 부탁드립니다 — 여전히 안 보이면 ItemsAdder 리소스팩도 다시 압축(`/iazip`)해야 최신 모델이 실제로 배포될 수 있어서 그것도 같이 확인하겠습니다.

### 폐기장 진입점 — 원인은 버그가 아니라 우클릭이 필요했던 것 (넷헤르 포탈처럼 닿기만 해도 입장되게 변경)
버그가 아니라 우클릭을 안 하고 그냥 블록에 걸어서 닿기만 하고 계셨던 것이었음. 기획서의 "포탈을 통하여 입장"이라는 표현이 넷헤르 포탈처럼 걸어서 닿기만 해도 되는 걸 의미하는 게 맞아서, 우클릭 방식은 그대로 두고 **블록에 닿기만 해도 입장되는 방식을 추가**함 — 이제 둘 다 됨. 진단용으로 넣었던 임시 디버그 로그는 다시 뺐음.

로비/타운/야생 3곳 배포 완료, 재시작 필요.

### 폐기장 진입 이동 문제 — 임시 디버그 로그 추가 (원인 아직 미확정)
DB를 직접 확인해서 "오늘 이미 입장 처리돼서 잠긴 상태"는 아닌 것으로 확인함(`scrapyard.last-enter-date`가 비어있음) — 즉 `enter()` 로직이 아직 한 번도 끝까지 성공한 적이 없다는 뜻. 우클릭이 아예 다른 블록을 맞추고 있는 건지, 조건 체크(진입목적지/귀환지점/구역 미설정)에서 막히는 건지, 순간이동 자체가 조용히 실패하는 건지 로그만으로는 구분이 안 돼서, 원인을 정확히 잡기 위해 우클릭할 때마다 콘솔에 상세 정보가 찍히도록 임시 디버그 로그를 넣어둠(`[폐기장-디버그]` 접두사) — 문제 해결되면 다시 뺄 예정.

로비/타운/야생 3곳 배포 완료, 재시작 후 진입점 다시 우클릭해보시고 콘솔 로그(`[폐기장-디버그]`로 검색) 캡처해서 보내주시면 바로 원인 확인 가능합니다.

### 폐기장 진입 이동 안 되던 진짜 원인 발견 — MultiWorld의 월드 재로드로 인한 "낡은 월드 참조" 버그
로그/DB를 다시 파보니 좌표도, 보호 시스템 취소 여부도 문제가 아니었음. 진짜 원인: 서버가 켜질 때 YeowoolLife가 폐기장 지점(진입점/진입목적지/출구/구역/상자/몹스폰)을 DB에서 읽어와 각 지점의 월드(`World`) 객체까지 미리 찾아서 메모리에 캐싱해뒀는데, 그 직후에 MultiWorld가 자기 관리 목록에 zombie_dungeon(과 lobby_world)을 "가져오기(import)"하면서 해당 월드를 다시 로드함 — 이때 새로 만들어지는 `World` 객체는 YeowoolLife가 이미 캐싱해둔 것과 다른 인스턴스가 됨. 그래서 진입점 클릭 자체는 정상 처리됐지만(메시지도 떴을 것), 실제 순간이동은 "낡은" 월드 객체를 대상으로 시도되면서 조용히 실패했던 것으로 보임(예외도 안 남기고 그냥 안 됨).
- 근본적으로 고침: 이제 폐기장 지점들을 메모리에 캐싱할 때 월드 객체를 미리 찾아두지 않고, 월드 "이름"과 좌표만 저장해뒀다가 실제로 필요한 순간(우클릭 판정, 순간이동, 구역 판정)마다 그때그때 `Bukkit.getWorld(이름)`으로 새로 찾아옴 — 어떤 플러그인이 나중에 월드를 다시 로드하든 항상 그 순간의 최신 월드를 참조하게 됨.
- 참고: `/폐기장설정 초기화` 추가 시점이랑 겹쳐서 그것 때문인가 싶으셨을 텐데, 실제로는 그 명령어와는 무관하고 MultiWorld가 재시작마다 월드를 다시 임포트하는 과정에서 생기는 문제였음 — MultiWorld를 설치한 이후로 계속 존재했던 버그로 보임.

로비/타운/야생 3곳 배포 완료, 재시작 후 다시 테스트 부탁드립니다.

### 폐기장 진입점 우클릭해도 이동 안 되던 문제 — 보호 시스템 충돌 가능성 수정
DB에 저장된 좌표를 직접 확인해봤는데 진입점/진입목적지/출구/귀환지점/구역 전부 정상적으로 등록되어 있었음 — 좌표 계산 로직 문제는 아님. 대신 이 프로젝트의 다른 모든 우클릭 관련 리스너(토지 보호 포함)처럼 `ignoreCancelled = true`로 되어 있던 게 원인일 가능성이 높음: 진입점으로 쓰신 블록이 상자류이거나, 그 위치가 아무도 소유하지 않은/권한 없는 토지 위라면 `yeowool-land`의 보호 리스너(또는 WorldGuard)가 먼저 상호작용을 취소시켜서 폐기장 로직까지 아예 도달을 못 했을 수 있음. 진입점/출구/상자는 관리자가 일부러 등록한 특수 지점이라 토지 보호와 무관하게 항상 작동해야 하므로, 이 이벤트는 다른 플러그인이 먼저 취소하더라도 항상 실행되도록 수정함.

로비/타운/야생 3곳 배포 완료, 재시작 후 다시 테스트 부탁드립니다.

### 폐기장 "진입목적지" 저장 실패 버그 수정 (DB 컬럼 길이 부족)
`yw_scrapyard_point` 테이블의 `category` 컬럼이 VARCHAR(16)이었는데 "entry_destination"이 18자라 저장이 안 되고 있었음(`Data too long for column 'category'`). VARCHAR(32)로 늘림 — 코드도 고치고, 이미 만들어져 있던 실제 DB 테이블도 지금 바로 직접 수정해뒀어서 재시작 없이 바로 `/폐기장설정 진입목적지` 다시 시도하시면 됩니다.

로비/타운/야생 3곳 jar 배포 완료(다음 재시작 때 자동으로도 한번 더 맞춰짐).

### `/폐기장설정 초기화 <이름>` 추가 — 오늘 입장 기록 리셋
관리진이 오늘 이미 폐기장에 갔다온 사람의 입장 기록을 지워서 같은 날 다시 들어갈 수 있게 해주는 명령어. 탭 자동완성에는 온라인 플레이어 중 "오늘 이미 입장한" 사람만 뜨고, 아직 입장 안 한 사람 이름을 적으면 "아직 입장 안 했다"는 안내만 뜨고 아무 것도 안 바뀜. 오프라인 플레이어 이름도 직접 입력하면 동작함(닉네임 캐시로 조회). 위치 등록이 필요 없는 유일한 하위 명령어라 콘솔에서도 실행 가능.

로비/타운/야생 3곳 배포 완료, 재시작 필요.

### 로비에 MultiWorld + FacilisCommon 설치, 폐기장 던전 월드(zombie_dungeon) 정상 등록 확인
사장님이 직접 설치하신 MultiWorld(+필수 의존 플러그인 FacilisCommon)가 재시작 후 정상적으로 켜졌고, 이미 로드되어 있던 zombie_dungeon 월드를 시작할 때 자동으로 인식해서 자체 관리 목록에 등록함(`/world import` 명령어를 따로 안 쳐도 됐음). `/world teleport zombie_dungeon`(별칭 `/world tp`)으로 던전에 들어갈 수 있음.

### `/플레이타임` 보상 기준을 "평생 누적" → "하루 누적"으로 변경 (yeowool-community)
1/6/12/24시간 보상 판정 기준을 매일 자정에 초기화되는 "오늘 하루 총 접속 시간"으로 바꿈 — 로비+타운+야생 합산은 그대로 유지. 받은 기록(`playtime.claimed.*`)도 날짜를 저장하는 방식으로 바꿔서 매일 다시 받을 수 있게 됨. `/내정보`에 뜨는 평생 누적 플레이타임(`profile.playtime_minutes`)은 이번 변경과 별개의 값이라 그대로 유지됨 — 완전히 분리된 카운터라서 서로 영향 없음.

로비/타운/야생 3곳 배포 완료, 재시작 필요.

### 폐기장 보스를 "1회 등장" 방식으로 변경
`ent_keeper_boss`를 일반 몹 반복 소환 목록(`scrapyard.mob.ids`)에서 빼고, 별도의 `scrapyard.boss` 섹션(`mob-id`, `spawn-delay-seconds`)으로 옮김 — 세션(한 판)당 딱 한 번만 소환됨. 입장 후 설정한 지연 시간(기본 60초)이 지나면 `/폐기장설정 보스스폰`으로 등록한 위치에 자동으로 등장하고, 등장 순간 액션바 알림이 뜸. 탈출/사망/시간초과로 세션이 끝나면 "이번 판엔 이미 나왔다"는 기록도 같이 초기화되어 다음 입장 때 다시 나올 수 있음.

로비/타운/야생 3곳 배포 완료, 재시작 필요.

### 폐기장용 몹 "Ent Keeper Boss" 로비 서버에 설치
- MythicMobs 몹/스킬 파일(`ent_keeper_boss`, `ent_keeper_spikes`, `ent_stomp` + 관련 스킬 3개) → `plugins/MythicMobs/mobs/skills` 에 설치.
- ModelEngine 모델 3개(ent_keeper, ent_spike, ent_stomp_vfx) → `plugins/ModelEngine/blueprints/ent_keeper_boss/`에 설치 — 재시작하면 자체적으로 리소스팩을 다시 빌드하고, 그 결과물은 이미 ItemsAdder가 자동으로 병합해가는 경로라(로그에서 확인) 별도 작업 불필요.
- 보스바 전용 텍스처(체력바 이미지 + 폰트)는 `plugins/ItemsAdder/contents/ent_keeper_boss/resourcepack/`에 새 네임스페이스로 병합해둠 — 파티 HUD 때와 같은 이유로 완전히 새 폴더라 리로드로는 못 잡고 재시작해야 인식됨.
- `scrapyard.mob.ids`에 `ent_keeper_boss`를 기본값으로 추가(체력 200, 발밑 가시/지진 스킬 있는 보스급 몹) — 지금 설정대로면 폐기장 몹 스폰 시스템이 이 보스를 "일반 몹"처럼 주기적으로 여러 마리 반복 소환할 수 있음(간격/최대 마리수는 `scrapyard.mob.spawn-interval-seconds`/`max-alive`로 조절 가능, 너무 부담스러우면 이 몹만 따로 빼서 1회성 보스 등장 방식으로 바꿀 수도 있음 — 필요하면 말씀해주세요).

로비 서버 배포 완료, 재시작 필요.

### 폐기장 던전 맵("Zombie City - Dungeon") 로비 서버에 설치 + 진입목적지 개념 추가
보내주신 압축파일 안에 전체 월드 저장파일과 WorldEdit 스키메틱(.schem) 둘 다 있었는데, 별도 월드로 로드하는 쪽으로 진행하기로 하셔서 `C:\YEOWOOL\lobby\zombie_dungeon`에 설치했음(폴더명은 원래 "Zombie City - Dungeon"이었는데 띄어쓰기 없이 `zombie_dungeon`으로 정리). `yeowool-life`가 서버 시작 시 이 폴더가 있으면 자동으로 월드를 로드함(`scrapyard.world` 설정값).
- 던전이 별도 월드가 되면서 "진입점"(포탈 트리거 블록, 원래 로비 월드)과 실제로 순간이동할 위치가 달라져야 해서 `/폐기장설정 진입목적지`(던전 월드 안에서 서서 등록)를 새로 추가함 — 진입점을 우클릭하면 이제 진입목적지로 순간이동하도록 고침. 출구/구역1/구역2/상자/몹스폰은 전부 던전 월드(zombie_dungeon) 안에서 등록해야 함.

로비/타운/야생 3곳 배포 완료(타운/야생엔 해당 월드 폴더가 없어서 폐기장 자체는 로비에서만 실제로 동작 — 폴더 없으면 경고만 찍고 넘어감, 안전).

### 여울 폐기장 미니게임 신설 (yeowool-life, 맵은 아직 필요 — 아래 참고)
보내주신 "여울 폐기장.txt" 기획서대로 하루 1회 입장 제한 루팅 미니게임을 새로 만듦. 실제 던전 맵(포탈/상자 위치/출구/괴물 구역)은 코드로 직접 지을 수 없어서, `/폐기장설정`으로 관리자가 인게임에서 직접 위치를 등록해야 실제로 작동함 — 등록 전까지는 안전하게 아무 동작도 안 함.
- **`/폐기장설정`** (관리진 전용): `진입점`/`출구`(바라보는 블록 등록), `귀환지점`/`구역1`/`구역2`(서 있는 위치 등록, 구역1·2는 아레나 전체를 감싸는 상자의 두 모서리), `상자추가`/`상자제거`(바라보는 블록), `몹스폰추가`/`몹스폰제거`(바라보는 블록), `정보`(현재 등록 현황 확인).
- **진행 방식**: 진입점 블록을 우클릭하면 입장(그 순간 바로 오늘 하루치 도전 기회를 소모 — 재접속해도 안 돌아옴, 분할서버 공유). 10분(설정 가능) 카운트다운이 액션바에 표시됨. 등록된 상자를 우클릭하면 확률적으로 폐기물 하나를 획득(빈 상자 확률도 설정 가능) — 상자는 하루에 한 번만 열림(자정 기준 초기화). 폐기물마다 무게가 있고, 소지 총 무게가 설정한 구간을 넘으면 둔화 효과가 걸림(구간별로 세기 조절 가능). 등록된 몹 스폰 지점에서 MythicMobs 몹이 주기적으로 소환됨(관리자가 몹 ID만 config에 적으면 됨, `/광석소환`과 같은 방식으로 명령어 위임 실행이라 별도 API 의존성 없음).
- **사망/시간초과/구역이탈**: 셋 다 실패 처리 — 소지 중이던 폐기물이 전부 사라지고(바닥에 드롭도 안 됨) 귀환지점으로 강제 이동, 오늘 재입장 불가.
- **출구 도달**: 성공 처리 — 폐기물 그대로 유지한 채 귀환지점으로 이동, 오늘 재입장 불가.
- **폐기물 종류/무게/등장확률**: `config.yml`의 `scrapyard.scrap-items`에서 관리자가 직접 정의(재질 또는 ItemsAdder id, 이름, 무게, 등장 가중치) — 예시 5종 채워둠, 실제 아이템으로 교체/추가 필요.
- **판매상인/구매상인은 새로 안 만들고 기존 상점 시스템 그대로 활용 권장**: 폐기물을 팔거나 횃불 등을 사는 상인은 이미 있는 `/상점생성` + Citizens NPC로 그냥 새 상점을 하나 만들어서 폐기장 안(또는 입구 쪽)에 NPC를 배치하면 됨 — 폐기물도 그냥 아이템이라 기존 상점 아이템 등록 방식(`/상점수정`) 그대로 팔 수 있음. 새 코드가 필요 없어서 따로 안 만듦.

**남은 일**: (1) 실제 맵/던전을 지어야 함(사장님 쪽 작업), (2) 다 지으신 후 `/폐기장설정`으로 위치 등록, (3) `scrapyard.scrap-items`에 실제 폐기물 아이템 채우기, (4) `scrapyard.mob.ids`에 MythicMobs 몹 ID 채우기, (5) 판매/구매 상인은 기존 상점 명령어로 배치.

로비/타운/야생 3곳 배포 완료.

### 파티 시스템 신설 (yeowool-community, 분할서버용) + Volya's Party HUD 연동 (진행 중, 재시작 필요)
`/파티 <이름> <최대인원>`(생성, 최대 4명까지) / `/파티 탈퇴` / `/파티 삭제`(파티장 전용) / `/파티 가입 <이름>`(승인 없이 자유 가입) / `/파티 정보`. 소속 정보는 항상 공유 DB(`yw_party`/`yw_party_member`)에서 즉시 조회해서 로비/타운/야생 어디서 실행해도 항상 정확함 — 커플/친구 시스템처럼 "부팅 시 한 번 캐싱"이 아니라 매 명령마다 최신 데이터를 읽음(파티는 활동 중 계속 바뀌기 때문). 파티장이 나가면 다음으로 오래된 멤버가 자동으로 파티장이 되고, 마지막 인원이 나가면 파티가 자동 해체됨.
- **Volya's Party HUD**(구매해서 보내주신 자료) + 새로 구하신 MythicHUD 플러그인을 이용해서, 파티원 체력바+닉네임이 화면에 뜨는 HUD도 같이 만듦. 원본은 "Parties"라는 별도 플러그인 + ParseOther/Math PAPI 확장을 전제로 만들어져 있어서, 우리 자체 파티 시스템에 맞게 완전히 새로 배선함:
  - 마나바, 직업 아이콘, 파티장 아이콘 레이어는 요청하신 대로 전부 제거 — 체력바 + 닉네임만 남김.
  - 체력 데이터는 `%yeowool_party_slot_1~4_health%` 같은 새 플레이스홀더(YeowoolCommunity가 PlaceholderAPI에 새로 등록)로 가져오는데, 이게 다른 서버에 있는 파티원 것까지 보여줘야 해서 — 매초 각 서버가 자기 서버에 있는 파티원 체력을 DB(`yw_party_presence`)에 기록하고, 다른 서버는 그걸 읽어와서 캐시에 반영하는 방식으로 만듦(약 1초 지연, HUD 용도로는 충분).
  - MythicHUD/PlaceholderAPI/ItemsAdder 세 플러그인 로비·타운·야생 모두에 이미 설치되어 있는 것 확인(MythicHUD는 이미 새로 넣어주신 jar로 한 번 재시작되어 기본 설정이 생성된 상태였음).
  - `plugins/MythicHUD/config.yml`의 `layout.default`에 `partyhud-p-layout`을 추가해서, 파티 HUD가 모든 플레이어에게 자동으로 적용되게 해둠.
- **남은 작업(재시작 후 마저 진행)**: MythicHUD는 자기가 쓸 텍스처/폰트를 자체적으로 "빌드"하는데, 지금 서버에 있는 빌드 결과물은 이번에 새로 넣은 파티 HUD 설정이 반영되기 전 것임(재시작해야 새로 반영됨). 재시작 후 새로 빌드된 결과물을 ItemsAdder 리소스팩에 병합해서(`/iazip`) 실제로 텍스처가 보이게 하는 작업이 남아있음 — 재시작해주시면 이어서 마무리하겠음.
- 파티장 아이콘은 요청하신 대로 다시 넣음 — 슬롯 1(항상 파티장)에만 표시.
- **글자가 □□□(네모)로 뜨던 문제 원인**: 재시작 후 MythicHUD가 파티 HUD용 텍스처/폰트를 자체적으로 다 빌드해뒀는데(`plugins/MythicHUD/built-pack/`), 그 결과물을 ItemsAdder 리소스팩에 실제로 병합하는 작업을 제가 아직 안 해놓은 상태에서 `/iazip`·`/iareload`를 먼저 하신 것 — 그래서 클라이언트가 받은 리소스팩엔 이 폰트 글자 모양이 아예 없어서 네모로 보인 것. 이제 `plugins/ItemsAdder/contents/yeowool_party/resourcepack/`에 그 빌드 결과물을 병합해뒀음. RCON으로 `/iazip`·`/iareload`를 다시 걸어봤는데, 로그에 이 새 폴더(`yeowool_party`)가 전혀 언급이 안 되는 걸 보니 ItemsAdder가 "새로 생긴 콘텐츠 폴더"는 reload로는 못 찾고 완전 재시작 때만 인식하는 것으로 보임 — 한 번 더 재시작해주시면 정상적으로 텍스처가 뜰 것으로 예상됨.
- **"/파티 테스트 4"를 치면 빨갛게 "알 수 없거나 불완전한 명령어"로 뜨던 버그 수정**: `PartyCommand`가 `TabCompleter`를 구현 안 해놨던 게 원인 — 다른 다중 인자 명령어(`/친구` 등)는 전부 `CommandExecutor`+`TabCompleter`를 같이 구현하는데, `/파티`만 빠뜨려서 클라이언트가 이 명령어를 "인자 없는 명령어"로 인식하고 그 뒤에 뭘 입력하든 빨갛게 표시한 것(실제로 서버에 보내면 정상 작동은 했었음, 화면 표시만 잘못된 것). `TabCompleter` 추가해서 수정.
- **`/파티 생성` 추가 — 모루+GUI로 파티 만들기**: 치면 모루가 열려서 파티 이름을 입력하고(2~16자, 한글/영문/숫자), 클릭하면 이어서 최대 인원(2/3/4명)을 고르는 작은 GUI가 열림 — 고르면 바로 파티가 만들어짐. 기존 `/파티 <이름> <최대인원>`으로 인자를 바로 주는 방법도 그대로 남아있음(둘 다 지원).

로비/타운/야생 3곳 배포 완료.

로비/타운/야생 3곳 모두 배포 완료(파티 시스템 코드 + MythicHUD 파티 HUD 설정), 재시작 필요(콘솔에서 직접 `/stop`).

### `/플레이타임` — 누적 플레이타임 보상 시스템 (yeowool-community)
새 `/플레이타임` 명령어 — 1×9 GUI가 열리고 1/3/5/7번 슬롯에 1시간·6시간·12시간·24시간 보상이 배치됨. 플레이타임은 로비/타운/야생 세 서버 접속 시간을 전부 합산한 값(이미 있던 `PlaytimeTracker`가 1분마다 쌓는 공용 통계, DB에 저장되는 값이라 서버를 옮겨도 계속 누적됨)을 기준으로 하고, 각 시간대는 한 번 받으면 다시 못 받음.
- 보상(화폐 금액/온·캐시/보너스 아이템)은 `/출석체크`(출석 보상)와 완전히 동일한 방식으로 관리자가 직접 인게임에서 수정 가능: `/플레이타임보상설정`으로 열리는 GUI에서 시간대를 고르면, 아이템을 놓는 슬롯 + "금액 설정"(모루로 숫자 입력) + "화폐" 전환 버튼이 있는 편집 화면이 열림. 서버 재시작 없이 언제든 바꿀 수 있음.
- 아직 아무도 금액을 설정 안 했으면 `config.yml`의 `playtime.reward-1h/6h/12h/24h` 기본값(1,000/5,000/10,000/20,000온)이 적용됨 — 이후 GUI로 한 번이라도 설정하면 그 값이 우선함.

로비/타운/야생 3곳 배포 완료, 재시작 필요(콘솔에서 직접 `/stop`).

### `/레시피` — 보유중인 AddCook 레시피 목록 (yeowool-life)
AddCook 소스를 디컴파일해서 확인해본 결과: 배포된 3.8.2 버전의 공개 API(`com.github.teamhungry22.addcook.api`, `.api.event`)에는 아이템/엔티티/가구 관련 기능만 있고 레시피 보유 여부를 다루는 기능이 전혀 없었음 — 요청하신 `.api.line`/`.api.model` 패키지는 이 버전에 아예 존재하지 않음(더 최신 문서를 보신 것 같음). 대신 내부(비공개 API) 코드를 확인해서 실제 동작 방식을 알아냄: 레시피북 아이템을 사용하면 AddCook이 LuckPerms로 `addcook.recipe.<레시피ID>` 권한을 플레이어에게 영구적으로 부여함(따로 "보유 레시피 목록" 같은 저장소는 없고, 이 권한 자체가 곧 "이 레시피를 안다"는 기록임).
- 새 `/레시피` 명령어 — AddCook 자체 레시피 설정 파일(`contents/recipes/*.yml`)에서 전체 레시피 id/이름을 읽어와서, 그중 플레이어가 `addcook.recipe.<id>` 권한을 가진(=배운) 것만 도구별로 묶어서 채팅으로 보여줌.
- AddCook의 API jar 의존성을 새로 추가하지 않고, CustomFishing/CustomCrops 연동 때처럼 설정 파일을 직접 읽는 방식으로 구현함.

### `/레시피`를 채팅 목록 → GUI로 전환, 클릭하면 재료·완성품 표시 (yeowool-life)
`/레시피` 실행 결과를 채팅 텍스트 대신 GUI로 바꿈 — 보유 레시피를 도구별로 아이콘 그리드에 보여주고(`MyRecipesGui`), 하나를 클릭하면 그 요리에 실제로 들어가는 재료(여러 개 중 아무거나 되는 경우 "이 중 하나만 있으면 됩니다" 표시)와 나올 수 있는 완성품(일반/은별/금별 등급별 수량·확률)을 보여주는 상세 화면(`RecipeDetailGui`)이 열리도록 함. 재료/완성품 아이콘은 AddCook 레시피 파일에 적힌 아이템 id(ItemsAdder `ia:` 접두사 또는 바닐라 재질명)를 그대로 해석해서 실제 아이템 모양으로 보여줌.

### 관리자 상점(/상점수정)에서 산 아이템이 다른 플러그인 태그를 잃어버리던 버그 수정 (yeowool-market)
"레시피북을 들고 우클릭 하는데 사용이 안 된다" 문제를 조사하다가 발견: `/상점수정`으로 관리자가 슬롯에 놓은 아이템을 저장할 땐 전체 NBT가 그대로 보존되지만(`AdminShopStore`가 원본 `ItemStack`을 그대로 DB에 저장), 실제로 판매용 `ShopItem`으로 변환할 땐 재질(또는 ItemsAdder id)·커스텀 이름 정도만 남기고 나머지 NBT/PDC는 전부 버려지고 있었음(`toShopItem`/`ItemResolver.build`). 그래서 플레이어가 상점에서 구매하면 겉모습은 똑같지만 원본이 갖고 있던 다른 플러그인의 식별 태그가 없는 "속 빈" 사본을 받게 됨.
- AddCook은 레시피북을 PDC 태그(`addcook;id`/`addcook;type`)로 식별하는데(디컴파일로 확인), 상점에서 산 레시피북은 이 태그가 없어서 우클릭해도 `PlayerInteractListener`가 아예 인식을 못 하고 아무 반응이 없었던 것 — AddCook 자체 `/recipe <가구>`(=`/addcook:recipe`) 명령으로 직접 받은 레시피북은 이 경로를 안 거치니 정상 작동했던 것이었음.
- CustomFishing 낚싯대/미끼도 동일한 방식(`CustomFishing` PDC 태그)으로 효과를 식별하므로, 지금 `/상점수정 fishing`으로 직접 채우고 계신 낚시 상점도 같은 문제가 있었을 것으로 보임(효과가 실제로 안 붙는 "가짜" 낚싯대/미끼가 지급됨).
- `ShopItem`에 원본 아이템 전체를 담는 `templateItem` 필드를 추가하고(가격표시용 PDC/설명 문구는 제거), `ItemResolver.build()`가 있으면 이걸 그대로 복제해서 지급하도록 수정 — 이제 상점에 놓인 원본 아이템의 NBT/PDC가 구매 시에도 그대로 유지됨.
- 이미 상점에 놓아둔 아이템은 원래 놓을 때 태그가 있었다면(예: `/recipe`로 직접 뽑은 레시피북) 재시작 후 자동으로 정상 지급됨 — 다시 넣을 필요 없음. 다만 혹시 다른 경로(`/addcook`의 아이템 목록 GUI 등)로 얻은 사본을 넣으셨다면 그 사본 자체에 태그가 없을 수 있으니, 재시작 후에도 안 되면 `/recipe <가구>`로 새로 뽑아서 다시 넣어주세요.

로비/타운/야생 3곳 배포 완료, 재시작 필요(콘솔에서 직접 `/stop`).

### `/레시피` 페이지 이동 버튼 위치 조정
레시피가 총 39개라 한 페이지(21개)에 다 안 들어가서 2페이지로 나뉘는데, 페이지 이동 버튼이 잘 안 보이는 위치(38·39·41·42번 칸)에 있어서 다음 페이지로 못 넘어가 "레시피가 다 안 보인다"는 문제가 있었던 것으로 보임 — 45번 칸(이전 페이지)·53번 칸(다음 페이지)으로 옮김(닫기는 그 사이 49번 칸).

로비/타운/야생 3곳 배포 완료, 재시작 필요(콘솔에서 직접 `/stop`).

### CustomCrops 계절 동기화 + 토마토 계절 제한 제거
로비/타운/야생이 각자 독립적으로 계절이 흘러서(동기화 설정이 없어서 각 서버가 따로 시작한 시점부터 28일 주기로 도는 중) 확인해보니 로비=겨울, 타운=여름, 야생=가을로 전부 달랐음 — `/customcrops season set`으로 3서버 전부 현재(9월) 실제 계절에 맞춰 **가을**로 맞춰둠. 다만 CustomCrops 자체에 서버 간 계절 동기화 기능은 없어서, 이후 서버들이 서로 다른 시점에 재시작되면 다시 어긋날 수 있음(계속 자동으로 맞춰주는 기능이 필요하면 별도로 만들어야 함).

CustomCrops에 실제로 설정된 작물은 토마토 하나뿐인데, 봄/가을에만 심고 자랄 수 있고 겨울엔 죽는 계절 제한이 걸려있던 걸 전부 제거 — 이제 계절 상관없이 아무 때나 심고 기를 수 있음(`contents/crops/default.yml`에서 심기 요구조건의 `season`, 생장 조건의 `suitable-season`, 사망 조건의 `unsuitable-season` 항목 삭제).

로비/타운/야생 3곳 배포 완료, `/customcrops reload`로 반영(재시작 불필요).

### CustomCrops 한국어 번역 + 계절 자동 동기화 기능 (yeowool-life)
- `translations/ko_kr.yml` 신규 작성(명령어 응답/오류 메시지 전체 번역) + `config.yml`의 `force-locale`을 `ko_kr`로 설정. 토마토 등 아이템 이름은 ItemsAdder `customcrops` 팩에 이미 한국어 사전(`_dictionaries/ko.yml`)이 있어서 그대로 잘 나옴 — 이번엔 CustomCrops 플러그인 자체(명령어/오류 메시지)만 번역하면 되는 상태였음.
- 새 `CustomCropsSeasonSyncTask` — 서버끼리 계절이 따로 흘러 어긋나는 문제를 근본적으로 해결하기 위해, 실제 시각을 기준으로 "지금 몇 번째 계절인지"를 계산해서 주기적으로(기본 60초마다, `customcrops.season-sync.check-interval-seconds`) 맞춰줌. 서버 간 통신이 전혀 필요 없음 — 로비/타운/야생이 각자 똑같은 공식으로 계산하니 자연히 같은 결과가 나옴. 계절 하나당 실제 560분(=CustomCrops 기본 설정인 인게임 28일 × 바닐라 하루 20분)으로 계산. 단, 이 자동 계산은 실제 지구 계절과는 무관한 자체 주기라, 이 기능이 켜지는 순간 아까 수동으로 맞춰둔 "가을"에서 다른 계절로 바로 넘어갈 수 있음(자연스러운 전환, 이후로는 3서버가 계속 같은 계절을 유지함).
- `customcrops.season-sync.enable`로 끌 수 있음(기본 true), CustomCrops 미설치 시 자동으로 비활성화.

로비/타운/야생 3곳 배포 완료, 재시작 필요(콘솔에서 직접 `/stop`).

### CustomCrops 작물 11종 추가 (yeowool-life 아님, CustomCrops 자체 설정)
"온실유리를 설치해도 토마토 말고 다른 씨앗은 안 심어지는 버그" 신고 확인 — 실제로는 버그가 아니라, ItemsAdder `customcrops` 팩에 텍스쳐/아이템은 다 있는데 CustomCrops 쪽 작물 설정(`contents/crops/default.yml`)엔 **토마토 하나만** 등록되어 있어서 다른 씨앗들은 애초에 심을 수 있는 작물로 인식된 적이 없었음. 씨앗이 있는 나머지 11종(양배추/배추/옥수수/가지/마늘/포도/홉/피망/파인애플/피타야/복주머니)을 전부 토마토와 같은 방식(성장 단계, 물 조건, 씨앗 드롭 확률, 일반/은별/골드별 수확)으로 새로 등록함 — 계절 제한은 토마토처럼 없음. 복주머니는 은/골드별 텍스쳐가 따로 없어서 그 등급 없이 기본 수확만 있음.
- 사과(apple)는 씨앗 아이템 자체가 없고 나무에서 저절로 열리는 다른 방식(트리형)이라 이번엔 제외 — 필요하면 별도로 설계해야 함.
- "거대~"(gigantic) 특수 변종이나 골드호미 전용 스테이지 같은 토마토의 보너스 연출은 이번엔 안 넣고 기본 재배/수확만 맞춤(작물마다 있는 텍스쳐가 달라서 통일성 있게 단순화함) — 나중에 필요하면 개별로 추가 가능.

로비/타운/야생 3곳 배포 완료, `/customcrops reload`로 반영(재시작 불필요).

### 새 작물 11종 뼛가루 지원 + 거대 변종 복원
"토마토는 뼛가루로 키울 수 있는데 다른 애들은 안 된다"는 신고 확인 — 새로 등록한 11종 전부 토마토의 `custom-bone-meal` 설정이 빠져있었어서 추가함(뼛가루 사용 시 1개 소모로 66%, 2개 소모로 20% 확률로 한 단계 성장).
- 양배추(cabbage)·파인애플(pineapple)은 ItemsAdder에 "거대(gigantic)" 변종 텍스쳐가 실제로 있어서, 토마토처럼 다 자란 후 2% 확률로 거대 버전이 되는 보너스 연출을 복원함(성장 단계 +1칸 추가).
- 나머지 9종(배추/옥수수/가지/마늘/포도/홉/피망/피타야/복주머니)은 애초에 거대 변종이나 골드호미 전용 단계에 쓸 텍스쳐 자체가 없어서 그대로 기본 재배만 유지.

로비/타운/야생 3곳 배포 완료, `/customcrops reload`로 반영(재시작 불필요).

### 작물 수확량 차등화 (홉/옥수수/배추/가지/마늘/양배추 1~4개, 토마토/파인애플/포도/고추/용과 2~6개)
토마토/파인애플/포도/고추(pepper)/용과(pitaya) 5종의 수확량을 기존 1~4개에서 2~6개로 올림 — 나머지 6종(홉/옥수수/배추/가지/마늘/양배추)과 복주머니는 그대로 1~4개 유지.

로비/타운/야생 3곳 배포 완료, `/customcrops reload`로 반영(재시작 불필요).

## 2026-09-06

### 서버 간 인벤토리 동기화 (yeowool-core)
로비/타운/야생이 각자 독립된 Paper 서버라 원래는 인벤토리가 서버마다 따로 저장됐는데, 이제 하나로 합쳐지도록 만듦:
- 새 테이블 `yw_player_inventory`(메인 인벤토리/방어구/보조손/엔더상자, 전부 직렬화된 텍스트로 저장) 추가.
- 접속 직전(`AsyncPlayerPreLoginEvent`)에 저장된 스냅샷을 미리 불러와두고, 완전히 접속한 시점(`PlayerJoinEvent`)에 그 서버가 로컬로 준 인벤토리를 덮어씀. 퇴장 시(`PlayerQuitEvent`) 그 순간의 인벤토리를 다시 저장.
- 기존 경제/통계 동기화랑 같은 방식(서버 간 잠금 없음, 마지막에 저장한 게 이김) — 이 프로젝트가 이미 온/뱅크 잔액 등에 쓰던 위험 감수 수준과 동일하게 맞춤. 서버를 아주 빠르게 옮겨다니면 이론상 경합이 생길 수 있지만, Velocity의 접속 해제→재접속 과정 자체에 어느 정도 시간이 걸려서 실제로는 거의 문제 안 됨.
- **처음 켜졌을 때 동작**: 저장된 스냅샷이 아직 없는 플레이어는 그 서버에 원래 있던 로컬 인벤토리를 그대로 유지함(비우거나 덮어쓰지 않음) — 그 플레이어가 다음에 퇴장할 때 처음으로 DB에 저장되고, 그 이후 접속하는 서버부터 동기화가 시작됨.
- XP/체력/허기/포션 효과 등은 이번에 안 건드림 — 요청하신 "인벤토리"(메인+방어구+보조손+엔더상자)만 대상.

로비/타운/야생 3개 다 재시작 필요(콘솔에서 직접 `/stop`) — yeowool-core 코드 변경이라 셋 다 같이 재시작하는 게 깔끔함.

## 2026-09-06 (계속 2)

### XP/체력/허기도 서버 간 동기화 (yeowool-core)
위 인벤토리 동기화에서 빠졌던 XP(레벨+진행도), 체력, 허기(포만감+포화도)도 같이 동기화되도록 확장:
- `yw_player_inventory` 테이블에 `exp_level`, `exp_progress`, `health`, `food_level`, `saturation` 컬럼 추가(테이블이 아직 라이브 DB에 생성 전이라 별도 마이그레이션 없이 바로 반영).
- 퇴장 시 인벤토리와 함께 이 값들도 같이 저장, 접속 시 같이 복원. 체력은 그 서버의 최대 체력(`Attribute.MAX_HEALTH`, 강화/직업 등으로 최대 체력이 달라질 수 있음)을 넘지 않게 클램프해서 적용.

### 기본 CustomFishing 물고기 16종 텍스쳐 교체 + 한국풍 설명으로 변경
"이런 걸로 텍스쳐팩이 나오게 해주고 설명이 중국풍인데 한국풍으로 바꿔달라"는 요청 반영:
- CustomFishing 기본 물고기 16종(참치/파이크/금붕어/퍼치/숭어/정어리/잉어/메기/문어/선피시/붉돔/연어(공허)/우드스킵/철갑상어/파란해파리/분홍해파리, 각각 일반/은별/금별 3단계)이 원래 `material: cod` + 아무 리소스팩도 없는 미사용 `custom-model-data`(50001~50048)를 쓰고 있어서 실제로는 그냥 민짜 대구 텍스쳐로 보였던 문제를 발견 — `material: paper` + 이미 서버에 있는 "Fishing_Pack_v1.0.1"(fishing_pack 네임스페이스) 리소스팩의 실제 물고기 모델 ID로 매칭해서 교체.
- 대구 설명 중 중국 전통 의학, 중국 원산지 언급 등 중국풍 문구 2곳을 포함해 16종 설명 전체를 한국풍 문구로 재작성(예: 숭어 → "겨울 진미", 금붕어 → "복을 부르는 물고기", 참치 → "김밥과 찌개에 빠지지 않는 생선" 등).
- 로비/타운/야생 3곳 모두 적용 후 `/cfishing reload`로 즉시 반영 완료.

### "Fishing_Pack_v1.0.1" 물고기/낚싯대/미끼 전부 추가 + 한국어화, `/커스텀물고기 열기` GUI 신규 (yeowool-life)
기존에 텍스쳐 매칭용으로만 참고했던 "Fishing_Pack_v1.0.1"의 CustomFishing 설정(`item/fishing_pack.yml` 등)에 실제로 들어있던 물고기 30종 + 낚싯대 16종 + 미끼 8종을 전부 정식으로 추가하고 한국어로 번역:
- 물고기 30종(진주/불가사리/해파리/송어/참치/개복치/가오리 등, 네더 전용 8종 포함): 영문 이름 → 한국어 이름 + 한 줄 설명 추가.
- 낚싯대 16종(얼음/구리/철/황금/다이아몬드/네더라이트/엔드/스컬크/프리즈머린/네더/자수정/마그마/보석/슬라임/용암/산호): 이름/설명 한국어화 + `material: fishing_rod` 명시(원본 팩 설정에 재질이 누락되어 있었음 — 다른 커스텀 낚싯대들과 동일하게 맞춤). 네더/마그마/용암 낚싯대는 용암 낚시가 가능함을 설명에 명시.
- 미끼 8종(옥수수/지렁이/해파리 촉수/반죽 경단/일반/희귀/영웅/전설): 이름/효과 설명 한국어화.
- 새 `/커스텀물고기 열기` 명령어(관리진 전용) 추가 — CustomFishing에 등록된 물고기+낚싯대+미끼를 한 GUI에서 클릭 한 번으로 지급받을 수 있음(`/물고기지급`은 우리 자체 물고기+CustomFishing 물고기만 섞어서 보여주는 것과 달리, 이건 CustomFishing 컨텐츠 전체 대상). 낚싯대/미끼는 CustomFishing에 물고기처럼 등록되는 방식(LootManager)이 아니라서, `contents/rod`·`contents/bait` 설정 파일을 직접 읽어 목록을 만듦.

로비/타운/야생 3곳 모두 설정 파일 배포 완료, yeowool-life 코드 변경(새 명령어)이 있어서 재시작 필요(콘솔에서 직접 `/stop`).

### 버그 수정: CustomFishing 아이템이 전부 빈 종이(paper)로 나오던 문제 (yeowool-life)
`/커스텀물고기 열기`로 확인해보니 물고기·낚싯대가 전부 흰 종이 아이템으로 뜨고, 낚싯대는 콘솔에 `material XXX not exists` 에러까지 뿜는 걸 발견 — 원인은 `CustomFishingBridge.buildItem()`이 `ItemManager.buildAny(context, id)`를 쓰고 있었는데, 이 메서드는 이름과 달리 우리가 등록한 물고기/낚싯대/미끼를 id로 찾아주는 게 아니라 **순수 바닐라 재질 이름**(또는 `provider:id` 형식)만 해석하는 완전히 다른 용도의 메서드였음(CustomFishing 소스 디컴파일로 확인). 우리 id를 넘기면 바닐라 재질로 착각해서 매칭 실패 → 조용히 종이로 대체되거나(물고기), 예외를 던짐(낚싯대). 실제로 등록된 아이템을 id로 빌드하는 진짜 메서드는 `buildInternal(context, id)`— 이걸로 교체.
- 이 버그는 `/커스텀물고기` 신규 명령어뿐 아니라 기존 `/물고기지급`, `/도감`의 CustomFishing 물고기 표시에도 처음부터 있었던 문제라, 이번 수정으로 전부 같이 해결됨.

로비/타운/야생 3곳 다시 배포 완료, 재시작 필요(콘솔에서 직접 `/stop`).

### 버그 수정: `/커스텀물고기 열기`에 물고기가 아예 안 뜨던 문제 (yeowool-life)
낚싯대/미끼는 잘 나오는데 물고기만 하나도 안 보이는 문제 — `CustomFishingBridge.buildRarity()`(CustomFishing의 `LootManager`에서 물고기 목록을 읽어옴)를 `YeowoolLife` 플러그인이 켜지는 시점에 딱 한 번만 호출해서 그 결과를 캐싱해뒀는데, 그 순간 CustomFishing 쪽 물고기 loot 등록이 아직 다 안 끝나 있어서 빈 목록으로 굳어버린 것으로 보임(낚싯대/미끼는 우리 쪽에서 파일을 직접 읽는 방식이라 이 문제와 무관해서 정상 표시됨). `/도감`, `/낚시관리 물고기`, `/물고기지급`도 같은 캐싱 방식을 썼어서 똑같이 물고기가 비어 보였을 가능성이 높음.
- 수정: 위 4개 명령어 전부 CustomFishing 물고기 목록을 플러그인 시작 시점에 캐싱하지 않고, **명령어를 실제로 칠 때마다 그 순간의 CustomFishing 상태를 새로 읽도록** 변경(`DexCommand`/`FishAdminCommand`/`FishGiveCommand`/`CustomFishingMenuCommand`). 부수적으로 `/cfishing reload`로 물고기 구성을 바꾼 뒤에도 서버 재시작 없이 바로 반영됨.

### 신규: `/미끼 빼기` (yeowool-life)
CustomFishing은 미끼를 별도로 "장착"해두는 상태가 없고, 낚싯대를 든 손 반대쪽(보조 손)에 들고 있는 미끼를 캐스팅할 때마다 그때그때 확인해서 씀(CustomFishing 소스 확인). 그래서 "장착된 미끼를 뺀다"는 건 실질적으로 보조 손에 든 미끼 아이템을 인벤토리로 되돌리는 것 — `/미끼 빼기` 명령어를 새로 만들어서 한 번에 처리되도록 함(보조 손이 미끼가 아니면 "현재 장착된 미끼가 없습니다" 안내).

로비/타운/야생 3곳 배포 완료, 재시작 필요(콘솔에서 직접 `/stop`).

### 버그 수정: 인벤토리 동기화가 가장 최근 상태로 안 되던 문제 (yeowool-core)
"서버 이동/퇴장 시 가장 최근 인벤토리와 연동이 안 되는 것 같다"는 신고 확인 — 원인은 [PlayerInventorySyncListener.java](yeowool-core/src/main/java/com/yeowool/core/listener/PlayerInventorySyncListener.java)의 퇴장 시 저장이 **비동기(fire-and-forget)**였던 것. 플레이어가 서버를 나가면 그 순간 인벤토리를 캡처해서 DB 저장은 백그라운드 스레드에 던져두고 바로 퇴장 처리를 끝내는데, Velocity가 다음 서버로 거의 즉시 연결시켜버리면 그 다음 서버의 접속 시점 로드가 이 저장이 실제로 DB에 반영되기 *전에* 먼저 실행돼서 방금 나간 시점 이전(구버전) 인벤토리를 불러오는 경합이 생길 수 있었음.
- 수정: 퇴장 시 저장을 **동기(블로킹) 처리**로 변경 — 로컬 MySQL에 UPDATE 한 번 날리는 정도라 몇 ms 정도만 더 걸리고, 그 대신 "저장이 실제로 끝난 뒤에 퇴장 처리가 끝난다"가 보장돼서 경합 창이 크게 줄어듦(단, 서버 이동이 "이전 서버에서 완전히 나간 뒤 다음 서버에 들어간다"가 아니라 겹쳐서 처리될 가능성 자체는 Velocity 쪽 동작이라 100% 없앨 수는 없음 — 그래도 기존 비동기 방식보다는 훨씬 안전함).

로비/타운/야생 3곳 배포 완료, 재시작 필요(콘솔에서 직접 `/stop`).

### 버그 수정: `/커스텀물고기 열기`에서 페이지 넘길 때 서버가 오류를 뿜던 문제 (yeowool-life)
`/커스텀물고기 열기`에서 다음 페이지로 넘길 때 `NullPointerException`(`meta.hasLore()` — meta가 null) 발생 — CustomFishing 자체의 `vanilla`라는 특수 항목("실제 아이템이 아니라 그냥 바닐라 낚시 결과로 넘긴다"는 내부 표시용 id, 재질이 아예 없음) 때문에, 그걸 실제 아이템으로 만들려고 하면 빈 공기(AIR) 아이템이 나와서 거기서 이름표(ItemMeta)를 못 붙이고 터진 것.
- `CustomFishingBridge.buildRarity()`에서 이 `vanilla` 항목을 목록에서 제외.
- 혹시 모를 다른 항목에도 같은 문제가 생기지 않도록, `CustomFishingBridge.buildItem()` 자체에서 결과가 빈 공기면 흰 종이로 대체하는 안전장치도 추가.

### 기존 우리 물고기들을 CustomFishing 낚시에서도 실제로 잡히도록 연동 (yeowool-life)
낚시 플레이 자체가 CustomFishing으로 완전히 넘어가면서, config.yml에 있던 우리 자체 물고기(fishing_expansion 팩 아이콘 — 송어/정어리/쥐치 등 60종)는 도감/지급 GUI에는 여전히 보였지만 실제로는 낚을 방법이 없는 장식용 목록으로 전락해있던 문제를 발견 — 사용자 요청으로 수정:
- 새 `CustomFishingNativeFishExporter`가 서버 시작 시마다(CustomFishing이 켜져 있을 때만) config.yml의 우리 물고기 목록을 CustomFishing 자신의 아이템 설정 파일(`contents/item/yeowool_native.yml`, id는 `yw_` 접두사)로 통째로 내보내고 `/cfishing reload`를 걸어 즉시 실제 낚시 loot 풀에 편입시킴.
- 아이콘은 CustomFishing의 ItemsAdder 연동(`material: "ItemsAdder:fishing_expansion:trout"` 형식)을 그대로 써서 fishing_expansion 팩 텍스쳐를 재사용 — 텍스쳐를 새로 안 만들어도 됨. 등급별 확률은 그 등급의 weight 값을 그대로 씀.
- config.yml 쪽 물고기 목록을 나중에 고치면 재시작할 때마다 이 파일도 같이 재생성되므로 두 군데를 따로 관리할 필요 없음. `/도감`·`/물고기지급`에는 이미 우리 쪽 목록으로 보이고 있어서, CustomFishing 쪽 병합 목록(`buildRarity()`)에서는 이 내보낸 항목들(`yw_` 접두사)을 걸러내서 중복으로 안 보이게 함.

로비/타운/야생 3곳 배포 완료, 재시작 필요(콘솔에서 직접 `/stop`).

### CustomFishing 미니게임/포획 메시지 한국어 번역
낚시 미니게임 중 뜨는 문구("So close, stay cool!" 등)와 물고기를 낚았을 때 뜨는 문구(신기록/크기 안내, 성공/실패 타이틀)가 번역 시스템(translations/*.yml)이 아니라 별도 설정 파일에 영어로 하드코딩되어 있던 걸 발견해서 번역:
- `contents/minigame/default.yml`: 정확한 타이밍(accurate_click) 미니게임의 색상 이름(RED!/ORANGE!/... → 빨강!/주황!/...), 진행 문구("So close, stay cool!" 등 3종), hold/tension 미니게임의 "시작하려면 ○○키를 누르세요" 안내 문구.
- `config.yml`의 `mechanics.global-events.loot`: 신기록 갱신 액션바, 일반 포획 액션바(크기/최고기록 안내), 포획 성공/실패 시 뜨는 타이틀·서브타이틀 문구.

로비/타운/야생 3곳 배포 완료, `/cfishing reload`로 반영(재시작 불필요 — 설정 파일만 바뀜).

### 버그 수정: Fishing_Pack 낚싯대/미끼의 효과가 전부 조용히 무시되고 있던 문제 발견
"미끼를 들고 낚시해도 안 줄어든다"는 신고를 조사하다가, 이번 세션에서 번역한 `rod/fishing_pack.yml`·`bait/fishing_pack.yml`에 원본 팩이 쓰던 `modifier:` 키를 그대로 옮겼는데, CustomFishing이 실제로 인식하는 키는 `effects:`뿐이라는 걸 CustomFishing 소스 디컴파일로 확인함 — `modifier:`는 아무 키에도 안 걸려서 조용히 무시되고, 미끼의 "확률/시간 보정"과 네더·마그마·용암 낚싯대의 "용암 낚시 가능" 효과가 전부 적용이 안 되고 있었음. `effects:` 형식(+ "weight-multiply" 같은 존재하지 않는 타입도 실제 타입인 `group-mod`로) 맞춰서 재작성.
- 다만 확인 결과 "미끼가 줄어드는지"(소모 로직)는 이 `effects:` 키와 무관한 별도 코드 경로라, 이 수정과는 별개 문제일 가능성이 높음 — 미끼가 정상적으로 소모되는지는 이 수정 반영 후 다시 확인이 필요함(어떤 미끼인지, 소모는 안 되지만 효과는 붙는지 등 구체적으로 알려주면 더 파볼 수 있음).

로비/타운/야생 3곳 배포 완료, `/cfishing reload`로 반영(재시작 불필요).

### CustomFishing "Fish Market"(물고기 자동판매) 한국어 번역
`config.yml`의 `market:` 섹션(물고기를 인벤토리/가방에서 바로 판매하는 내장 GUI, Vault 연동으로 실제 "온"이 지급됨, 하루 판매 한도 있음)이 전부 영어였던 걸 발견해서 번역: 제목("물고기 시장"), 판매 버튼("물고기 판매"), 판매 성공 메시지("~온을 벌었습니다! 오늘 ~온을 더 벌 수 있습니다"), 실패 아이콘("거래 불가" — 팔 아이템 없음/하루 한도 초과), 닫기 버튼. "coins"라는 표현은 전부 "온"으로 통일.

로비/타운/야생 3곳 배포 완료, `/cfishing reload`로 반영(재시작 불필요).

### 낚시(어부) 관리상점을 구매 전용으로 전환
CustomFishing으로 실제 낚시가 넘어가면서, 잡은 물고기마다 크기가 제각각이라(스택 불가) 우리 관리상점에서 한 번에 판매가 안 되고 인벤토리만 낭비하는 문제 + CustomFishing 자체 Fish Market(크기별 가격, 인벤토리+가방 통째 판매)이 이미 그 역할을 대체하고 있어서, "낚시" 관리상점(`yw_admin_shops` 테이블, id=`fishing`)의 판매 기능을 제거하고 구매 전용으로 전환(`mode: BOTH` → `BUY_ONLY`) — 기존에 설정해둔 75개 아이템(3페이지)은 그대로 유지, 구매만 가능. DB만 바꿔둔 상태라 다음 재시작(또는 `AdminShopStore` 캐시 재로딩) 시 반영됨. 기존에 채워져 있던 아이템 75개도 전부 제거(요청) — 상점 틀(3페이지, 구매 전용)만 남기고 비워둠, 낚싯대는 사용자가 직접 추가 예정.

### 버그 수정: `/커스텀물고기`·`/물고기지급`으로 받은 아이템에 "클릭하여 지급받기"가 영구히 남던 문제 (yeowool-life)
`FishAdminGui`가 GUI 슬롯에 보여줄 아이콘(안내 문구 "클릭하여 지급받기" 필요)과 클릭했을 때 실제로 인벤토리에 주는 아이템을 **같은 메서드로 만들고 있어서**, 실제로 받은 미끼/낚싯대/물고기에도 이 안내 문구가 그대로 붙어서 나갔던 버그. GUI 표시용(`buildDisplayIcon`)과 실제 지급용(`buildGivenItem`)을 분리해서 해결 — `/낚시관리 물고기`, `/물고기지급`, `/커스텀물고기 열기` 전부 이 GUI를 공유해서 다 같이 고쳐짐.

로비/타운/야생 3곳 배포 완료, 재시작 필요(콘솔에서 직접 `/stop`).

### "미끼가 안 줄어드는" 신고 — 버그 아니었음 (크리에이티브 모드 정상 동작)
CustomFishing의 `debug: true`를 잠깐 켜서 실제 캐스팅 로그를 직접 확인한 결과, 미끼 인식·소모 로직 자체는 정상적으로 호출되고 있었음(`bait | legendary_fishing_bait | CAST` 로그로 확인). 다만 테스트하던 계정이 크리에이티브 모드였는데, CustomFishing은 바닐라처럼 **크리에이티브 모드에서는 의도적으로 미끼를 소모하지 않도록** 설계되어 있어서 정상 동작이었음 — 서바이벌 모드에서는 정상적으로 줄어듦. 확인 후 `debug`는 다시 `false`로 되돌림.

## 2026-09-05 (계속 12)

### 낚시 플레이 자체를 CustomFishing으로 전환 (yeowool-life)
"다른 건 다 병합하고 낚시 플레이 자체만 CustomFishing으로 바꿔달라"는 요청에 따라 실제 낚시 메커닉(입질 타이밍, 릴링 등)을 CustomFishing 쪽으로 완전히 넘김:
- CustomFishing이 설치되어 있으면 `YeowoolLife`가 자체 `FishingListener`(우리가 만든 입질 타이밍 미니게임)를 **아예 등록하지 않음** — 없을 때만 대체 수단으로 켜짐.
- 대신 새 `FishingLootSpawnEvent` 기반 리스너가 CustomFishing으로 물고기를 잡을 때마다 우리 쪽 부가 시스템을 그대로 이어줌: 어부 직업 XP, 땅 XP, 도감 포획 통계(+최고 크기 기록, `getFishSize` API로 실제 크기 가져옴), 낚시대회 기록까지 전부 CustomFishing 낚시에도 동일하게 적용됨.
- CustomFishing 공식 메이븐(`repo.momirealms.net`)에 2.3.26이 아직 안 올라와있어서(최신 배포본은 2.3.24) 2.3.24로 컴파일 — 실제 서버엔 2.3.26이 깔려있지만 이번에 쓴 API(`ItemManager.getFishSize` 등)는 두 버전에 공통으로 있어서 문제없음.

**주의할 점**: 우리가 직접 만든 낚싯대/미끼 아이템(FishRod/FishBait, 등급 확률 보정용)은 이제 아무 효과가 없음 — 우리 자체 미니게임이 더 이상 안 돌아서 그 보정 로직 자체가 실행되지 않음. 기존에 나눠준 낚싯대/미끼를 가진 유저가 있으면 알려줘야 할 수도 있음(회수하거나, CustomFishing 쪽 낚싯대/미끼로 안내하거나).

재시작 필요(콘솔에서 직접 `/stop`).

## 2026-09-05 (계속 11)

### CustomFishing 도감 병합 + `/물고기지급` GUI 명령어 (yeowool-life)
CustomFishing의 공식 API(`net.momirealms:custom-fishing:2.3.7`, compileOnly로 추가 — `repo.momirealms.net` 메이븐 저장소 등록)를 연동해서 두 가지를 구현:

1. **`/도감` 물고기 탭에 CustomFishing 물고기 병합**: 새 `CustomFishingBridge`가 서버 시작 시 CustomFishing의 `LootManager.getRegisteredLoots()`에서 아이템 타입 전리품만 뽑아 "커스텀 낚시"라는 가상 등급으로 묶어 기존 `FishCatalogGui`에 그대로 얹음(우리 자체 물고기 목록은 그대로 두고 추가만 함). 아이콘/이름은 매번 `ItemManager.buildAny(...)`로 실제 아이템을 그대로 가져와서 씀 — 그라디언트 색상까지 그대로 보임. 신규 `CustomFishingCatchListener`가 `FishingResultEvent`를 받아 우리 쪽 `life.fishing.catalog.<id>` 통계에 기록해서, CustomFishing으로 잡은 물고기도 우리 도감의 "???" 미포획 처리와 똑같이 동작함(크기 최고기록은 CustomFishing 쪽 통계가 따로 있어서 이번엔 안 가져옴).
2. **`/물고기지급` 신규 명령어**: 기존에 이미 있던 `/낚시관리 물고기`(전체 물고기 GUI에서 클릭 한 번으로 즉시 지급)와 완전히 동일한 화면을 인자 없이 바로 여는 관리자 전용 명령어 — 이것도 위 병합된 목록(우리 물고기 + CustomFishing 물고기)을 그대로 보여줌.

**주의**: `FishingListener`(우리 자체 낚시 미니게임)가 굴리는 목록은 그대로 원래 목록만 사용 — CustomFishing 물고기가 우리 낚싯대로 잡히거나 우리 물고기가 CustomFishing 낚시로 잡히는 일은 없음(두 낚시 시스템은 서로 독립적으로 동작, 도감/지급 화면에서만 하나로 합쳐 보여줌).

새 CustomFishing 의존성 때문에 재시작 필요(콘솔에서 직접 `/stop`).

## 2026-09-05 (계속 10)

### `/상점자동채우기` 명령어 완전 제거
자동 채우기가 원치 않는 아이템까지 넣을 수 있다는 우려로 명령어 자체를 삭제 요청받음 — `ShopBulkFillCommand.java` 파일 삭제, `YeowoolMarket.java`의 import/생성/`bindCommand` 호출 제거, `plugin.yml`의 `상점자동채우기:` 엔트리 제거. DB 확인 결과 광물/목축 상점 모두 page 1이 실제로 만들어진 적 없어서(둘 다 여전히 page_count=1) 되돌릴 데이터는 없었음 — 순수 명령어 제거만 진행. 이제 상점 아이템은 전부 `/상점아이템설정` + `/상점아이템가격`으로만 수동 관리됨.

## 2026-09-05 (계속 9)

### `/상점아이템가격` 가격 설정 후 같은 GUI로 자동 복귀
`ShopPriceAnvilListener`가 구매가→판매가 입력을 마치고 저장한 뒤 그냥 인벤토리를 닫기만 했던 걸, 저장 직후 같은 상점/페이지의 `AdminShopPriceGui`를 바로 다시 열도록 변경 — 여러 아이템 가격을 연달아 설정할 때마다 `/상점아이템가격 <상점ID>`를 매번 다시 칠 필요 없이 우클릭만 반복하면 됨. 위 광물/목축 가격 작업과 같은 jar에 포함되어 있어서 재시작 한 번으로 둘 다 반영됨.

## 2026-09-05 (계속 8)

### 광물/목축 상점 가격 추가 (yeowool-market)
`/상점자동채우기`가 상점 ID 하나만 지정해서 실행할 수 있도록 확장(`/상점자동채우기 <상점ID>`) — 기존엔 인자 없이 실행하면 building/livestock/enhance/misc 4개를 한꺼번에 채웠는데, enhance/misc는 지금 운영진이 직접 채우려고 일부러 비워둔 상태라 그대로 다시 돌리면 안 됨. 그래서 이번엔 목축·광물만 콕 집어서 채울 수 있게 함:
- **광물(ore) 상점**: 신규로 page 1 추가 — W6 채굴 몹이 드랍하는 원석/압축 블록(석탄/구리/철/금/레드스톤/청금석/자수정/쿼츠/다이아/에메랄드/고대 잔해/네더라이트 조각) 가격 설정. page 0(운영진이 이미 채운 것)은 그대로 둠.
- **목축(livestock) 상점**: page 1(소고기/돼지고기/양고기/닭고기/토끼고기 등)을 되살림 — 예전에 자동 채우기 했다가 지운 것과 같은 목록, page 0(운영진이 다시 채운 10개)은 그대로 둠.
잡화/도구 상점 코드도 이대로 재사용 가능. 배포는 완료했지만 **서버 재시작이 필요함**(RCON stop은 셧다운 행 버그 때문에 이제 사용 안 함 — 콘솔에서 직접 `/stop`) — 재시작 후 `/상점자동채우기 livestock`, `/상점자동채우기 ore`를 각각 실행하면 반영됨(인자 없이 그냥 실행하면 안 됨 — enhance/misc까지 다시 채워짐).

## 2026-09-05 (계속 7)

### 셧다운 행 버그 원인 특정: RCON으로 보내는 stop 자체가 문제
사용자가 서버 콘솔 창에 직접 `stop`을 치면 멀쩡히 꺼진다고 알려줘서 결정적인 단서가 됨 — 오늘 하루 종일 재현된 행이 전부 RCON으로 명령을 보낼 때만 발생했다는 뜻. 오늘 한 번도 RCON stop을 시도한 적 없던 큐 서버로 다시 검증: RCON으로 stop을 보내되 **응답을 아예 안 읽고 소켓만 바로 닫아도** 똑같이 "MoonriseCommon Awaiting termination" 지점에서 멈춤 — 즉 스크립트가 응답을 기다리는 것도 원인이 아니고, RCON 경로로 stop이 들어가는 것 자체가 문제. 콘솔 직접 입력은 이 경로를 안 타서 항상 정상 작동.
**결론**: RCON으로 서버를 내릴 때는 매번 이 행이 재현될 걸로 보임 — 당분간은 재시작이 필요할 때 콘솔에서 직접 `stop`을 치거나(권장), RCON으로 내렸다가 멈추면 좀비 프로세스를 강제 종료하고 재기동하는 방식으로 우회. RCON stop 경로의 근본적인 코드 레벨 원인(아마 Paper의 RCON 커넥션 핸들러 스레드가 종료 시퀀스 중 정리가 안 되는 문제로 추정)은 아직 미해결.

## 2026-09-05 (계속 6)

### 셧다운 행 버그 원인 조사: spigot.yml `restart-on-crash`는 아님 (기각)
사용자가 어제 손댄 `restart-on-crash`/`restart-script` 설정이 원인일 수 있다고 의심해서 직접 실험함 — 로비에서 `restart-on-crash: false`로 바꾸고 재부팅한 뒤 다시 `/stop`을 걸어봤는데, **완전히 동일한 지점("MoonriseCommon Awaiting termination of worker pool for up to 60s")에서 똑같이 멈춤**. 즉 watchdog의 크래시 자동 재시작 기능과는 무관하다는 게 확인됨 — 설정을 다시 `true`로 원복. 근본 원인은 여전히 미궁 상태(다음 후보: Skript/SkBee, ModelEngine, MythicMobs RandomSpawns 등 최근에 추가된 서드파티 플러그인들의 셧다운 훅 — 하나씩 꺼가면서 재현 테스트 필요).

## 2026-09-05 (계속 5)

### `/광석소환` 명령어 추가 (yeowool-admin)
W6 Custom Mining 팩의 위장 채굴 몹(석탄/구리/철/금/다이아/에메랄드/청금석/레드스톤/자수정/쿼츠/네더라이트)을 OP가 자기 위치에 바로 소환할 수 있는 명령어 추가 — `/광석소환 <종류> [개수, 최대 10]`. 별도 드랍 로직 없이 `mm mobs spawn`을 그대로 위임 실행하는 얇은 래퍼라, 랜덤 스폰으로 자연 발생한 것과 완전히 동일하게 캐면 광석이 드롭됨. 몹 자체가 타운/야생에만 등록돼 있어서 로비에서는 자동으로 동작 안 함(따로 막을 필요 없음).

배포 중 셧다운 행 버그가 **4번째로** 재현됨(세 서버 다 동일 지점에서 멈춤) — 좀비 프로세스 강제 종료 후 재기동으로 정상화, 새 명령어 정상 로드 확인. 근본 원인은 여전히 미해결.

## 2026-09-05 (계속 4)

### W6 Custom Mining & Ores 팩 추가 (타운/야생 전용)
구매한 "W6 - Custom Mining & Ores"(MythicMobs + ModelEngine 기반, 광물 블록처럼 위장한 채굴 가능 몹 11종: 석탄/구리/철/금/다이아/에메랄드/청금석/레드스톤/자수정/네더 쿼츠/네더라이트)를 타운·야생에만 배포(로비엔 안 넣음, 확인 완료). 팩 자체의 `Worlds: world`/`world_nether` 기본값을 서버별 실제 월드 이름(`town_world`/`town_world_nether`, `wild_world`/`wild_world_nether`)으로 고쳐서 각 서버 전용 랜덤스폰 설정 파일을 따로 만듦 — 안 고치면 아무 데도 안 스폰됨. `/meg reload` + `/mm reload`로 반영, 몹 개수 37→48로 정상 증가 확인.
**주의**: 제작사 README에 랜덤스폰 확률(`Chance`)을 프로덕션에 바로 쓰지 말고 테스트 후 조정하라는 경고가 있음 — 기본값 그대로 배포했으니 스폰 밀도를 지켜보고 필요하면 `plugins/MythicMobs/randomspawns/workshop_six/w6_custom_mining_spawns.yml`의 `Chance` 값을 조정할 것.

## 2026-09-05 (계속 3)

### 장애 (3차): 18:00에 로비/타운/야생/큐 4개 서버 전부 다운
- **로비**: 언젠가 `/서버재부팅설정 06:00,18:00`으로 예약이 걸려있었고, 18:00 정각에 그 예약이 실제로 발동 → `Bukkit.shutdown()`이 호출됐는데 오늘 낮에 두 번이나 겪은 것과 동일한 "MoonriseCommon Awaiting termination" 지점에서 멈춤. 즉 RCON `/stop`이 아니라 **정상적인 플러그인발 종료 경로에서도 셧다운 행 버그가 그대로 재현됨**이 이번에 확인됨 — RCON 명령 자체의 문제가 아니라 더 근본적인 셧다운 훅 문제.
- **타운/야생/큐**: 같은 시각에 로그에 종료 시도 흔적이 전혀 없이(경고 방송도, MoonriseCommon 로그도 없이) 그냥 프로세스 자체가 사라짐. Windows 이벤트 로그(System/Application)에도 재부팅·전원·크래시 관련 기록이 전혀 없어서 원인 특정 실패 — 예약 재부팅 대상도 아니었음(DB엔 로비만 등록되어 있었음). 외부 요인(작업 스케줄러, 백신 등) 의심되지만 확증 못함.
- **조치**: 4개 서버 전부 재기동해서 정상화(에러 없음 확인). 재발 방지를 위해 **로비의 예약 재부팅을 일단 해제**함(`/서버재부팅설정 해제`) — 셧다운 행 버그의 근본 원인을 못 고친 상태에서 자동 재부팅을 계속 켜두면 매번 이런 다운타임을 유발하기 때문. 셧다운 행 버그 자체는 여전히 미해결.

## 2026-09-05 (계속 2)

### 버그 수정: 강화석 등급 태그가 `:tag_artifact:` 원문 그대로 표시되던 문제
`yeowool_tooltips` 콘텐츠팩 하나에 `minecraft` 네임스페이스(툴팁 배경/프레임)와 `yeowool_tooltips` 네임스페이스(등급 태그 글리프)를 동시에 `resourcepack/assets/`로 섞어 넣었더니, 빌드된 zip 안에 태그 글리프 경로가 `assets/yeowool_tooltips/resourcepack/assets/yeowool_tooltips/...`처럼 이중으로 겹쳐 들어가면서 실제 텍스트 치환은 계속 실패하고 있었음(로그도 "Image not found" 반복). 태그 글리프 6종을 완전히 별도의 단일 네임스페이스 콘텐츠팩(`yeowool_tags`)으로 분리하니 바로 해결 — 세 서버 다 확인 완료(에러 없음, 리소스팩에 6개 파일 정상 포함).

## 2026-09-05 (계속)

### 정정: 직업별 소득 차등화는 상점 판매가 기준이었음 — 잘못 만든 직업 소득 코드 되돌림
농사/광질/벌목/목축/낚시 소득 차등화 요청이 "상점에 파는 아이템 가격" 기준이었다는 걸 뒤늦게 확인 — 아래(첫 번째 09-05 항목)에서 만든 `jobs.income-per-action`(직업 행동 1회당 직접 온 지급)과 `RancherIncomeListener`(목축 전용 직접 지급)는 요청과 다른 방향이라 전부 되돌림(`JobManager`/`YeowoolLife`/config.yml 원상복구, `RancherIncomeListener.java` 삭제). DB 조회 결과 farming/fishing/ore/tool/weapon/dye/livestock 상점은 이미 유저가 직접 아이템+가격을 다 채워둔 상태였고(목축도 이미 10개 채워져 있음), 벌목(원목)만 전용 상점 id가 아직 없음 — 이 부분은 추가 확인 필요.

### 커스텀 농사 품질 등급(일반/은별/금별) 추가
대신 커스텀 농사(아직 실제 작물 미설정)에 요청하신 품질 차등을 반영: 수확마다 `CustomFarmingQualityConfig`가 금별(3%)→은별(15%)→일반 순으로 굴려서, 기본 수확 소득(`income-per-harvest`)에 금별 3배/은별 1.5배를 곱해 지급. 은별/금별 수확 시엔 채팅 메시지도 뜸. 실제 작물을 채운 뒤 확률/배율은 config.yml `custom-farming.quality`에서 바로 조정 가능.

### 장애 (2차): 재배포 재시작 중 로비/타운/야생이 또 멈춤 + 프록시 중복 실행 발견
같은 코드를 재배포하려고 재시작을 걸었는데 또 세 서버 모두 "MoonriseCommon Awaiting termination" 지점에서 응답이 끊김 — 이번엔 좀비로 남지 않고 아예 프로세스 자체가 죽어버림(재시작할 때마다 재현되는 걸 보면 오늘 새로 만든 코드보단 기존에 깔려있던 무언가—Skript/SkBee 등—의 셧다운 훅 문제일 가능성이 큼, 원인 미특정). 확인 과정에서 별개로 **프록시(Velocity)가 3개 중복 실행 중이었던 것도 발견**함 — 15:49:39~40에 정상 프록시(새벽 1시45분부터 계속 떠있던 것) 외에 똑같은 `velocity-3.4.0-566.jar` 프로세스 2개가 같은 초에 추가로 뜸(포트 25565 충돌로 둘 다 바인딩 실패, 게임엔 영향 없었지만 메모리만 낭비 중이었음) — 이 시점에 누가/무엇이 프록시 `start.bat`을 두 번 실행했는지는 특정 못함, 이번 작업으로 발생한 건 아님. 중복 프로세스 강제 종료 + 로비/타운/야생 재기동으로 정상화, 콘솔 에러 없음 확인.

## 2026-09-05

### 강화석(초급~태초) 6종에 tooltip_style + 등급 태그 글리프 적용 (ItemsAdder)
`moafarm_items:magic_ore_1`~`magic_ore_6`(강화 재료로 쓰이는 원석, `/강화설정`에서 강화 구간별로 요구됨)에 그동안 없던 한글 표시 이름·설명 로어·`minecraft:tooltip_style`을 직접 심었습니다 — 지금까지는 `/관리자아이템` GUI 안에서만 "초급 강화석" 등으로 보였을 뿐, 아이템 자체의 실제 이름은 영어 "Magic Ore 1" 그대로였음.
- 초급→common, 중급→uncommon, 고급→rare, 정예→epic, 신비→legendary, 태초→artifact — 강화 장비와 동일한 Ultimate Tooltips 등급 6종에 정확히 1:1로 대응(사용자가 언급한 5개 + 이미 존재하던 6번째 "태초" 등급까지 포함해 완성).
- 추가로 "tags" 요청에 맞춰 Ultimate Tooltips 팩의 등급 글리프 아이콘(`common.png`~`artifact.png`) 6개를 `yeowool_tooltips` 콘텐츠팩에 `font_images`로 등록(`tag_common`~`tag_artifact`)하고, 각 강화석 로어 마지막 줄에 해당 등급 글리프를 붙임.
- **삽질 기록**: 글리프 PNG를 처음엔 콘텐츠팩 자기 네임스페이스 축약 경로(`resourcepack/yeowool_tooltips/textures/...`)에 뒀더니 `font_images`가 "Image not found"로 계속 실패함 — 아이템 텍스처(`resource.texture`)에서는 통하는 축약 경로가 `font_images`의 이미지 탐색에는 안 통하는 것으로 보임. 이미 검증된 명시적 경로(`resourcepack/assets/yeowool_tooltips/textures/...`, 강화 장비 tooltip_style 배경/프레임과 동일한 패턴)로 옮기니 바로 해결됨. 또한 `/iareload`만으로는 설정만 다시 읽고 zip을 재압축하지 않는다는 것도 재확인 — `/iazip`을 따로 한 번 더 호출해야 실제로 리소스팩에 반영됨.

### 채집 직업별 시간당 소득 차등화 (yeowool-life)
"시간당 온 얼마나 벌게 할지"의 기준선을 5,000온/h 안팎으로 잡고, 농사/광물/벌목/목축/커스텀 농사가 서로 조금씩 다른 소득을 갖도록 새 소득 체계를 추가했습니다. 기존엔 직업 시스템이 경험치만 주고(`jobs.xp-per-action`), 실제 수입은 전부 관리자 상점에 내다 파는 것에만 의존했는데(그마저도 목축/벌목은 상점가가 아예 없거나 유저가 직접 채우는 중), 그와 별개로 행동 1회당 바로 지급되는 기본 온 소득을 추가한 것 — 상점 판매 수입은 그대로 유지되고 이건 그 위에 더해집니다.
- `JobManager.grantXp()`에 `jobs.income-per-action`(직업ID→온) 맵을 추가해 farmer(3온)/miner(4온)/wood_cutter(2온) 행동마다 즉시 지급 — 기존 `bonus-currency-per-proc`(부수입 스킬 확률 발동)와는 별개로 항상 지급되며, 레벨 상한에 도달해도 계속 지급됨(XP만 막힘).
- 목축은 대응하는 직업/경험치 트랙이 아예 없었어서, 최소 구현으로 신규 `RancherIncomeListener`(양털 깎기·소/무시룸 우유 짜기 시 `ranching.income-per-action` = 5온 지급, XP·레벨 없이 소득만)를 추가.
- 커스텀 농사(`custom-farming`, 아직 실제 작물은 config에 미설정 상태)는 수확 시점(`CustomFarmingListener.onBreak`)에 `custom-farming.income-per-harvest`(30온) 지급을 추가 — 성장 시간이 행동 속도가 아니라 수확 빈도를 결정하는 구조라 farmer보다 단가를 높게 잡음. 실제 작물을 채워 넣은 뒤 성장 시간에 맞춰 재조정 필요.
- 모든 수치는 "시간당 대략 몇 번 행동 가능한지"를 가정해서 역산한 것이라 config.yml 주석에 근거를 남겨뒀고, 실제 플레이 데이터로 언제든 재조정 가능.

### 장애: 로비/타운/야생 재시작 중 전부 멈춰서 약 13시간 다운
위 두 작업(강화석 tooltip/태그, 직업 소득) 배포를 위해 세 서버에 `/stop`을 보냈는데, 셋 다 정확히 같은 지점(`[MoonriseCommon] Awaiting termination of worker pool for up to 60s...`)에서 멈춰서 그대로 다시 안 올라옴 — 원래 이 단계는 최대 60초 대기 후 자동으로 넘어가야 하는데 그러지 못하고 JVM이 좀비 상태로 남았음(큐 서버로는 접속이 계속 가능해서 플레이어는 로비/타운/야생에만 못 들어가는 상태로 방치됨). 원인은 아직 특정 못함 — 세 서버가 동시에 똑같이 멈춘 걸 보면 이번 배포 코드보다는 공용 플러그인(사용자가 직접 추가한 Skript-2.11.0/SkBee-3.10.1 포함) 쪽 셧다운 훅 문제일 가능성이 있음. 좀비 프로세스 강제 종료 후 재기동해서 정상화됨(월드 데이터는 멈추기 직전에 이미 전부 저장 완료된 상태였어서 데이터 손실은 없음). 같은 현상이 재발하면 어느 플러그인이 원인인지 좁혀볼 필요 있음.

## 2026-09-04

### 강화 장비 tooltip_style 적용 + spigot.yml 크래시 자동 재시작 복구 + plugin.yml 버그 수정
세 가지가 한 번에 얽힌 작업:

**1. 마인크래프트 1.21.2+ 바닐라 `minecraft:tooltip_style` 적용 (Ultimate Tooltips 리소스팩)**
구매한 "Ultimate Tooltips (Animated)" 팩의 `assets/minecraft/textures/gui/sprites/tooltip/<등급>_{background,frame}.png`(9-slice + 애니메이션 mcmeta)를 신규 ItemsAdder 콘텐츠팩 `yeowool_tooltips`(세 서버 공통, `resourcepack/assets/minecraft/...`로 바닐라 minecraft 네임스페이스를 직접 덮어씀 — 기존 `cosmetics` 팩의 `assets/minecraft/atlases`/`models/item` 오버라이드와 동일한 방식)로 병합. `/iareload`+`/iazip`은 비동기라 즉시 zip을 열어보면 반영 전일 수 있음 — 몇 초 폴링 후 재확인 필요(이번에 실제로 그래서 처음엔 "안 들어간 줄" 착각했었음).

강화(`/강화`) 장비에 실제로 적용: `EnhanceItemData.applyLevel()`이 레벨 변경마다 `item.setData(DataComponentTypes.TOOLTIP_STYLE, ...)`로 현재 등급(`EnhanceConfig.tierFor`)에 맞는 스타일을 심음 — 일반→common, 희귀→rare, 에픽→epic, (향후 4·5번째 등급 추가 시) legendary→artifact 순으로 자동 승격(`EnhanceConfig.tiers()`에서 해당 등급의 서수 위치로 스타일 배열 인덱싱). +0강(미강화)이면 `unsetData`로 바닐라 기본 툴팁으로 되돌림.

**2. spigot.yml `restart-on-crash` 복구**: 로비/타운/야생/큐 4개 서버 전부 `restart-on-crash: true`는 켜져 있었지만 `restart-script: ./start.sh`가 이 윈도우 환경에 존재하지 않는 경로라 크래시/행 감지 시 재시작이 실질적으로 무의미했음 — `start.bat`으로 수정. 크래시-재시작 시 새로 뜨는 cmd 창이 `pause`에 멈춰 좀비로 남는 것도 방지하려고 각 `start.bat`의 마지막 `pause` 줄 제거. (참고: 이 기능은 워치독이 감지하는 행/크래시에만 반응하고, `/서버재부팅설정`의 정상적인 `/stop` 종료는 트리거하지 않음 — 둘은 서로 다른 안전망으로 공존.)

**3. yeowool-admin `plugin.yml` YAML 문법 버그**: `서버재부팅설정` 명령어 설명에 콜론+공백(`예: /...`)이 따옴표 없이 들어가 YAML 파싱이 깨짐 — YeowoolAdmin 전체가 조용히 로드 실패(콘솔에 에러도 안 뜸, YeowoolDiscord처럼 그걸 의존하는 플러그인이 있어야만 `UnknownDependencyException`으로 간접 발각됨). 세 서버 다 `/여울관리`·`/경고`·`/추방`·`/쿠폰` 등 관리 명령어 전체가 먹통이었던 상태 — description을 큰따옴표로 감싸서 수정.

### 예약 자동 재부팅 시스템 (yeowool-admin)
운영진이 지정한 시각에 서버가 자동으로 재부팅되도록 요청받아 신규 `restart` 패키지 추가:
- `/서버재부팅설정 <HH:mm[,HH:mm...]|해제>` — 이 서버(`restart.this-server-id`, 로비/타운/야생 배포본마다 다르게 설정)의 자동 재부팅 시각을 인게임에서 즉시 설정/해제(`yw_server_restart_schedule` DB 테이블에 저장, 재시작 없이 바로 반영). 인자 없이 실행하면 현재 예약 조회. 여러 시각을 콤마로 지정 가능(예: 하루 2번).
- `ScheduledRestartTask`가 1초마다 다음 예약 시각까지 남은 시간을 확인해 10분/5분/1분 전 채팅 경고(`MessageService.broadcast`)를 보내고, 1분 전에는 `YeowoolCoreAPI.playerData().saveAll()`을 백그라운드로 한 번 더 트리거해 재부팅으로 인한 "백섭"(저장 안 된 최근 데이터가 롤백되는 현상)을 방지 — 재부팅 자체는 `Bukkit.shutdown()`으로 정상 종료시켜, YeowoolCore의 `onDisable`에 이미 있던 `saveAll().join()` 안전망도 그대로 한 번 더 걸림.
- **한계**: Paper 플러그인은 자기 자신의 JVM을 재실행할 수 없어서, 이 기능은 "경고 후 안전하게 종료"까지만 담당함. 종료된 프로세스를 실제로 다시 띄우는 건 서버 밖의 워치독/작업 스케줄러가 따로 필요함(아직 미구현 — 필요시 추가 요청).

### 출석체크 보상 기본값 채우기 (yeowool-community)
출석 보상(일일/주간/월간)이 비어있어서 신규 1회성 명령어 `/출석보상초기화` 추가: 일일 1,500온 + 자동줍기권/자동심기권 각 50회, 주간 15,000온 + 각 500회, 월간 100,000온 + 각 1,000회로 채움. 이미 아이템이 있는 티어는 건너뛰어 재실행해도 안전. 자동줍기권/자동심기권 아이템은 yeowool-life의 `AutoFarmVoucherItem`이 쓰는 것과 동일한 PDC 키(`yeowoollife:autofarm_voucher_type`/`_charges`)를 직접 만들어 사용 — yeowool-community가 yeowool-life에 의존하지 않고도(그 모듈은 로비에 안 깔림) 완전히 호환되는 아이템을 만들기 위함.

### 강화 파괴 보호권 아이콘 교체 + /강화설정 GUI화
"파괴 보호권"(강화 실패로 하락/파괴될 때 대신 소모되는 보호 아이템, `enhance.protection-item`)을 기존 바닐라 네더라이트 주괴에서 `moafarm_items:easypoint`(이미 리소스팩에 있던 미사용 아이콘, PAPER 베이스)로 교체. 아이템 자체의 표시 이름도 "Easypoint" → "파괴 보호권"으로 변경(`moafarm_items.yml`) — 다른 시스템에서 이 아이템을 참조하는 곳이 없어서 안전하게 재활용.

`/강화설정`을 텍스트 명령어(`/강화설정 <레벨> 온|재료|정보 ...`)에서 GUI 기반으로 전환:
- `/강화설정`(인자 없음) → `EnhanceSettingsGui` — 0강부터 최대 레벨까지 페이지네이션된 목록, 클릭하면 해당 레벨 상세 화면으로.
- `/강화설정 <레벨>` → 그 레벨 상세 화면(`EnhanceSettingsDetailGui`)으로 바로 이동. 필요 온/재료 슬롯을 클릭하면 모루(anvil) 입력창이 뜨고(기존 `ShopPriceAnvilListener`와 같은 "이름 변경란에 숫자 입력 → 클릭으로 확정" 트릭, `EnhanceSettingsAnvilListener`로 신규 구현) 값을 저장한 뒤 같은 레벨 화면을 다시 열어서 연속으로 다음 값을 조정할 수 있음. 재료 슬롯은 빈손 클릭 시 개수만, 아이템을 들고 클릭하면 재료+개수를 함께 변경.
- 레벨 상세 화면에 이전/다음 강 버튼이 있어서 목록으로 안 돌아가고도 1강씩 죽 이동하며 설정 가능.
- 기존 텍스트 명령어(`온`/`재료`/`정보` 서브커맨드)는 콘솔/RCON에서도 쓸 수 있도록 그대로 남겨둠 — GUI는 플레이어 전용이라 콘솔에서 인자 없이 치면 "플레이어만 사용할 수 있습니다" 메시지.

### 자동 채우기 되돌림
유저가 직접 채우기로 결정, 자동으로 넣었던 4개 상점 아이템을 원상복구해달라고 요청. `ShopBulkFillCommand`에 `/상점자동채우기 초기화` 모드 추가 — 각 상점을 1페이지 초과분 전부 제거 후 남은 페이지도 비워서 `/상점생성` 직후와 동일한 빈 상태로 되돌림. 세 서버 모두 재배포·재시작 후 각 서버에서 실행해 DB와 서버별 인메모리 캐시를 함께 정리.

### 나머지 관리자 상점 일괄 채우기 (건축/목축/강화재료/잡화) — 이후 되돌림
유저가 광물/낚시/도구/염료/무기/농사/블록 상점은 직접 채웠고, 요리 상점은 보류 상태로 남겨둔 채 건축·목축·강화재료·잡화 4개 상점을 대신 채워달라고 요청. `/상점아이템설정`은 실제로 아이템을 들고 GUI에 놓아야 하는 방식이라 에이전트가 직접 조작할 수 없어서, 새 1회성 관리자 명령어 `/상점자동채우기`(`ShopBulkFillCommand`, yeowool-market)를 추가 — 상점별로 큐레이션한 바닐라 아이템 목록과 등급별(흔함~신화) 기본 가격을 `AdminShopStore.savePage()`로 직접 DB에 저장. 페이지가 이미 채워진 곳은 건너뛰어 재실행해도 안전.

- 건축(조명/문/카펫/장식식물/배너 등 56개), 목축(사료/마구/원육/양모 56개), 강화재료(다이아몬드·네더라이트 등 강화 소재 + 대장장이 문양 50개 — `YeowoolEnhance`의 `material-cost-item: DIAMOND` 설정과 맞춤), 잡화(양동이/도구류 등 28개) 총 4개 상점, 190개 아이템 채움.
- 첫 실행 때 `building` 상점 2페이지 저장 중 MySQL 데드락(`Deadlock found when trying to get lock`)으로 실패 — 원인은 4개 상점 최대 7개 페이지 저장 요청이 2-스레드 executor에서 동시에 DB에 DELETE+INSERT 트랜잭션을 날리며 충돌한 것. `ShopBulkFillCommand`를 상점 단위가 아니라 **페이지 단위**로 이미 채워진 페이지는 건너뛰도록 고쳐서 재실행 시 실패한 페이지만 재시도되게 함.
- 재배포 과정에서 lobby/town/wild 세 서버 모두 `/stop` 후 셧다운이 8분 넘게 멈춰있는(MoonriseCommon 워커풀 종료 대기 단계에서 로그가 더 안 찍힘) 현상 발생 — 원인 불명(플러그인 변경과는 무관해 보임, 청크 저장까지는 이미 완료된 상태였음). RCON 포트가 이미 닫혀 있어 새 프로세스를 시작해도 안전하다고 판단, `start.bat` 대신 PowerShell `Start-Process`로 새 인스턴스를 직접 띄워서 정상 재기동 확인.
- 관리자 상점 DB(`yw_admin_shop_items`)는 MySQL로 세 서버가 공유하지만, 각 서버는 부팅 시점에만 `AdminShopStore.loadIntoCache()`로 읽어오므로 DB에 새로 채운 뒤에는 town/wild도 재시작해서 캐시를 갱신해야 했음.

## 2026-09-03

### 로비/타운/와일드 서버 분리 (멀티서버 전환)
지금까지 전부 lobby 서버 하나에만 몰려있던 플러그인들을 역할에 맞게 lobby/town/wild 세 서버로 나눔. Velocity 프록시(`C:/YEOWOOL/proxy`)에는 이미 세 백엔드가 등록만 돼있고 실제로는 lobby만 쓰이고 있던 상태였음.

**서버 역할**: 로비=허브(메뉴/코스메틱/NPC), 타운=토지·경제·상점·커뮤니티(월드 초기화 안 됨), 와일드=생존·전투·RTP(월드 매주 초기화). 농사/벌목/낚시/채광(YeowoolLife)은 타운·와일드 둘 다.

- **월드 이름 유니크화**: lobby/town/wild 전부 기본 월드 이름이 "world"로 겹쳐있어서, 세 서버가 같은 MySQL DB를 공유하는 이 구조에서 홈/워프/토지 데이터가 뒤섞일 위험이 있었음(DB엔 world 이름만 저장하고 서버 구분 컬럼이 없음). `lobby_world`/`town_world`/`wild_world`로 각각 이름을 바꿈(당시 세 테이블 다 0행이라 유실 데이터 없음). RCON 포트도 세 서버가 전부 25575로 겹쳐있던 걸 25575/25576/25577로 분리, town의 RCON 비밀번호 오타도 고침.
- **LuckPerms를 MySQL로 전환**: 원래 로비 로컬 H2 파일로 저장되고 있어서 서버마다 권한이 따로 놀 뻔했음. `/lp export`로 백업 후 `luckperms` 스키마로 옮기고, RCON이 비동기 응답이라 `/lp import`가 안 먹혀서 그룹/권한을 명령어로 하나씩 재구성 → MySQL 직접 조회로 검증.
- **ItemsAdder 리소스팩 호스팅 포트 충돌 발견/수정**: 세 서버 다 8163으로 겹쳐있던 것을 8163/8164/8165로 분리(안 그러면 세 서버 동시 구동 시 포트 바인딩 실패).
- **RTP 로비 차단**: `RtpConfig`에 `enabled` 필드 추가, 로비 배포본만 `false`로 설정 — 로비에서 `/rtp` 시도 시 "로비에서는 사용 할 수 없는 명령어입니다!" 메시지.
- **크로스서버 전체 채팅 신설**: `yeowool-community`가 전체(GLOBAL) 채널 메시지를 완성된 포맷 그대로(닉네임 색상/칭호 등 유지) `yeowool:chat` 커스텀 채널로 프록시에 전달하고, 기존에 이미 있던 `yeowool-proxy`(Velocity, `/서버` 이동+대기열 담당하던 모듈)에 수신 핸들러를 추가해서 보낸 서버를 제외한 나머지 서버 플레이어들에게 그대로 전달. 지역/마을 채널은 원래도 서버 로컬 개념이라 그대로 둠. (버그: 새 설정 키를 `server-name`으로 지었다가 기존에 탭리스트 표시용으로 이미 쓰이고 있던 키와 겹쳐서 `proxy-server-id`로 이름 바꿈 — 값이 서로 다른 용도라 혼선 있었음, 재배포로 수정.)
- **와일드 주간 초기화 스크립트** (`C:/YEOWOOL/wild/reset_wild.ps1`): 월드 폴더만 통째로 지웠다 새로 생성하되, 그 전에 `playerdata`/`stats`/`advancements`를 백업해뒀다가 새 월드에 복원 — 지형은 완전히 새로 생성되지만 인벤토리/경험치/도전과제는 유지됨. Windows 작업 스케줄러에 매주 일요일 새벽 4시 실행되도록 등록 완료(`YeowoolWildReset`).
- 6단계(월드 rename → LuckPerms MySQL → 플러그인 분배 → RTP 차단 → 채팅 브릿지 → 주간 초기화) 전부 실제로 서버 재시작해가며 직접 테스트 완료. town/wild 둘 다 처음부터 끝까지 플러그인 에러 없이 정상 기동 확인.
- 크로스서버 채팅은 실제로 두 계정으로 라이브 테스트 완료 — 정상 동작 확인됨.

### Queue(대기열) 전용 서버 신설
`yeowool-proxy`의 대기열 로직(`QueueManager`)은 이미 있었지만, 대기 중인 플레이어가 실제로 머물 서버가 없어서 그냥 원래 있던 서버에 남은 채로 메시지만 받고 있었음. lobby/town/wild처럼 4번째 백엔드 서버 `queue`(포트 25569, RCON 25578)를 새로 만들어서, `/서버 <이름>` 했을 때 대상 서버가 꽉 차서 대기열에 등록되면 이 서버로 실제로 이동시키도록 함(자리가 나면 `QueueManager.tick()`이 목적지로 자동 이동).
- `queue` 서버는 플러그인 없이 순수 바닐라 Paper — 슈퍼플랫 월드, adventure 모드 고정(`force-gamemode`), pvp/몬스터 꺼짐, 아무것도 못 하는 순수 대기 공간.
- `velocity.toml` `[servers]`에 `queue = "127.0.0.1:25569"` 등록.
- **버그**: 큐 서버를 새로 만들 때 `config/paper-global.yml`(Velocity 포워딩 설정이 들어있는 파일)을 안 옮겨서, 실제로 접속해보니 "서버가 프록시에 포워딩 요청을 보내지 않았습니다"로 튕기는 문제 발생 — 큐 서버가 Velocity 모던 포워딩을 신뢰하도록 설정돼있지 않았던 것. `proxies.velocity.enabled: true` + `secret`을 `forwarding.secret`과 동일하게 맞춰서 해결.
- 처음부터 끝까지(프록시+큐 서버 기동, 에러 없음) 직접 테스트 완료. 다만 실제로 서버를 꽉 채워서 대기열 진입→큐 서버 이동→자리 나면 자동 이동까지의 전체 흐름은 라이브 테스트 필요.
- 라이브 테스트 완료 후 실제 이동까지 확인됨.

### Queue - 서버가 재부팅/점검 중일 때도 대기열 적용
기존 대기열은 "서버가 꽉 찼을 때"만 동작했음. 이제 로비 등 서버가 재부팅/점검 중이라 아예 응답이 없는 경우에도 대기열로 빠지도록 확장:
- `/서버 <이름>` 명령으로 접속 시도했다가 실패하면(그 서버가 다운돼있는 거의 유일한 경우) 에러 메시지 대신 대기열에 등록
- **처음 접속할 때도 적용**: `PlayerChooseInitialServerEvent`를 가로채서, velocity.toml의 `try`(보통 lobby)에 ping이 안 가면 처음부터 queue 서버로 보내고 대기열에 등록 - 로비가 켜질 때까지 자동으로 대기
- `QueueManager.tick()`이 대상 서버에 실제로 ping을 보내서 살아있는 걸 확인한 뒤에만 대기열을 진행시키도록 수정 - 안 그러면 서버가 다운된 동안 계속 연결 시도→실패→맨 뒤로 재등록이 반복돼서 순번이 안 지켜짐
- 순번 변경 알림(1번이 들어가면 2번→1번으로 당겨지는 것)은 기존 로직 그대로 재사용됨
- **버그 발견/수정**: 처음엔 "최초 접속 시 로비가 꺼져있는 경우"만 처리해뒀는데, 실제 테스트해보니 로비에 접속해있는 도중에 로비를 끄면 그냥 "lobby에서 추방됐습니다: Server closed"로 프록시에서까지 통째로 튕겨나가는 문제가 있었음 — `PlayerChooseInitialServerEvent`(최초 접속)와 `KickedFromServerEvent`(접속 중 서버가 죽음)는 서로 다른 이벤트라 따로 처리해야 했음. `KickedFromServerEvent`도 잡아서, 원래 있던 서버가 ping이 안 가면(진짜로 다운된 것) queue로 리다이렉트하고 대기열에 등록하도록 추가 — 밴/친목킥처럼 서버는 멀쩡한데 그냥 쫓겨난 경우는 ping이 성공하므로 그대로 원래대로 끊김.
- 실제로 접속 중에 로비를 끄고 켜보는 라이브 테스트까지 완료 — 정상 동작 확인됨.

### 시스템 라이브 점검 + 관리자 상점 12개 신설
전체 12개 yeowool-* 모듈의 명령어(~80개)를 전부 RCON으로 직접 실행해보며 크래시 여부 점검. 전부 정상(크래시 없음)이었지만, DB를 직접 까보니 **핵심 콘텐츠가 거의 다 비어있는 것**을 발견함 — 배틀패스 보상 0개, 출석체크 보상 0개, 신규 유저 시작 키트 0개, 서버 워프 0개, 쿠폰 0개, 관리자 상점(테스트용 1개, 상품 0개), 직업 스킬/농작물·나무 정의 0개 등. 플러그인 코드는 다 살아있지만 실제 게임 콘텐츠를 채우는 작업이 안 된 상태 — 새 기능보다 이걸 채우는 게 우선순위로 판단.
- **버그 발견/수정**: AddCook(요리 플러그인)이 MythicMobs API 클래스를 참조하는데 타운에는 MythicLib이 없어서 리로드마다 `NoClassDefFoundError`가 나던 문제 — 타운에 MythicLib 설치해서 해결(완전히는 안 없어짐 — MythicLib만으론 부족하고 MythicMobs 본체가 더 필요해 보이는 클래스도 있어서 낮은 우선순위 이슈로 남겨둠, 크래시는 아니고 경고 로그만 남음).
- **관리자 상점 12개 신설**: 테스트 상점 삭제 후, 블록/건축/농사/목축/광물/무기/도구/요리/염료/강화재료/낚시/잡화 12개를 전부 "모두"(구매+판매) 모드로 생성 — 메인 메뉴 슬롯이 정확히 3×4=12칸이라 딱 맞음.
- **상점 아이템 관리 UX 개선**: 기존에는 아이템을 손에 들고 `/상점아이템설정 [상점ID] [구매가] [판매가] [화폐]`처럼 명령줄에 가격을 타이핑해야 했음 → GUI 기반으로 전면 교체:
  - `/상점아이템설정 <상점ID>` — 아이템을 드래그해서 상점 그리드에 배치하는 화면(기존 `/상점수정`과 동일, 이름만 추가)
  - `/상점아이템가격 <상점ID>` (신설) — 배치된 아이템을 우클릭하면 모루 UI로 구매가→판매가를 순서대로 입력받아 가격을 설정 (`ShopPriceAnvilListener`, 배틀패스 보상 편집기의 모루 입력 트릭 재사용)
  - 옛 명령줄 방식(`ShopItemSetCommand`)은 완전히 제거하고 정리함.
- 빌드 확인 후 배포, 재시작해서 에러 없이 뜨는 것까지 확인. 실제 아이템 배치/가격 설정은 사용자가 인게임에서 직접 진행 예정.

### 버그: 로비 정리 시 town/wild 전용 플러그인을 실제로 안 지웠던 것 발견/수정
Phase 3(멀티서버 분배) 때 town/wild에 플러그인을 복사만 하고 로비 원본은 안 지워서, 로비가 계속 예전(분리 전) 플러그인 세트를 그대로 들고 있었음 — 그래서 로비의 YeowoolMarket이 자기만의 오래된 상점 캐시(테스트 상점)를 계속 보여주고 있었던 것. WorldGuard/WorldEdit/Citizens/YeowoolLand/YeowoolMarket/AddCook(타운 전용), MythicMobs/MythicLib/ModelEngine/YeowoolEnhance(와일드 전용), YeowoolLife/CustomCrops(타운+와일드 전용)를 로비에서 제거.
- **부수 발견**: HQModeledNPC(로비의 장식 NPC 플러그인)가 MythicMobs+ModelEngine에 하드 의존하고 있어서, 지웠더니 로드 자체가 실패함 → 그 세 개(MythicLib 포함)는 로비에도 다시 설치.

### 상점을 로비 중심으로 재설계 + 크로스서버 이동
사용자가 "상점 NPC는 로비에 있어야 하고, 로비/타운/와일드 어디서 `/상점이동`을 치든 로비의 그 NPC 앞으로 이동해야 한다"고 요청 — Citizens NPC는 서버 하나에만 물리적으로 존재할 수 있어서, 다른 서버에서 명령어를 치면 서버 자체를 넘겨야 함:
- `ShopLocationCommand`가 지금 서버(`npc-shop.this-server-id`)와 NPC가 있는 서버(`npc-shop.npc-server-id`, 기본 lobby)를 비교 — 같으면 기존처럼 바로 NPC 앞으로 텔레포트, 다르면 PlayerData에 "대기 중" 플래그를 남기고 Velocity의 BungeeCord 호환 "Connect" 채널로 그 서버로 전송.
- NPC가 있는 서버에만 등록되는 `ShopTeleportJoinListener`가 그 플래그를 보고 있다가, 플레이어가 도착하면(클라이언트가 완전히 로드되도록 1초 뒤) NPC 앞으로 텔레포트를 마무리.
- YeowoolMarket 전체를 로비/타운/와일드 세 서버 다 설치(DB 공유라 어디서 실행해도 같은 상점 데이터를 봄) — Citizens는 NPC가 실제로 있는 로비에만.
- 세 서버 다 재시작해서 에러 없이 뜨는 것, `/상점이동`이 세 서버 다 정상 등록된 것까지 확인. 실제 서버 전환+NPC 도착까지 이어지는 전체 흐름은 라이브 테스트 필요(NPC가 아직 안 만들어져 있어서 "위치 미설정"까지만 확인 가능).

### 낚시 확장팩 물고기/미끼/낚싯대 76개 한글화
`fishing_expansion` ItemsAdder 팩의 아이템 이름이 전부 영문(Trout, Sardine, Hellpuffer...)이었던 것을 실존 물고기(송어/정어리/참치/도미 등)와 네더 테마 판타지 물고기(지옥복어/용암등불고기 등), 미끼/낚싯대까지 76개 전부 자연스러운 한글로 번역. 로비/타운/와일드 세 서버 다 동일하게 적용, `/iareload`+`/iazip`으로 반영 확인.

### 모든 시스템을 멀티서버(로비/타운/와일드)로 통일
사용자가 "지금 시스템들 다 멀티서버형식으로 변경해줘"라고 요청 — 그동안 서버별로 플러그인을 나눠 배포했던 것(타운=토지/상점, 와일드=강화/전투 등)을 전부 세 서버 공통으로 통일. 디스코드 연동(YeowoolDiscord, 중복 알림 방지를 위해 로비 전용 유지)만 예외.
- 로비에 새로 추가: YeowoolLand, YeowoolLife, YeowoolEnhance, CustomCrops, AddCook, WorldGuard, WorldEdit (+ Citizens/MythicMobs/MythicLib/ModelEngine는 이미 있던 HQModeledNPC 의존성 복구 작업 때 먼저 추가됨)
- 타운에 새로 추가: YeowoolEnhance, MythicMobs, ModelEngine
- 와일드에 새로 추가: YeowoolLand, AddCook, WorldGuard, WorldEdit, Citizens
- **버그**: 로비에 YeowoolLife의 설정 폴더만 복사하고 정작 jar 파일 자체를 빠뜨려서 플러그인이 아예 로드 안 되고 있었음 — 재확인 중 발견, jar 추가 후 재시작해서 정상 활성화 확인.
- **`/토지이동` 신설** — `/상점이동`과 완전히 동일한 크로스서버 패턴. 토지가 실제로 있는 서버(`land-teleport.land-server-id`, 기본 town)와 다른 서버에서 실행하면 그 서버로 먼저 보내고, `LandTeleportJoinListener`(토지 서버에만 등록)가 도착 즉시 내 토지(소유한 토지의 청크 중 하나, 그 자리 최고 블록 위)로 텔레포트를 마무리. 토지가 없으면 기존 "소유한 토지가 없습니다" 메시지 그대로 재사용.
- 세 서버 전부 재시작해서 에러 없이 뜨는 것, `/토지이동`·`/도감`을 포함해 모든 시스템 명령어가 세 서버 다 정상 등록된 것까지 확인.

### `/낚시관리 물고기` 신설 - 관리진용 물고기 지급 GUI
`config.yml`에 이미 등록돼있던 63종 물고기(일반/희귀/특수/전설 4개 등급)를 전부 한 화면에서 보여주고 클릭하면 바로 인벤토리로 지급하는 관리자 전용 GUI. 기존 `/도감`(플레이어용, 안 잡아본 물고기는 "???"로 가려짐)과 배경/레이아웃은 같은 `FishRarity`/`FishSpecies` 데이터를 그대로 재사용하되, 여기는 전부 공개되고 클릭 시 실제로 아이템을 준다는 점만 다름 — `yeowool.admin` 권한 필요. 로비/타운/와일드 세 서버 다 배포, 재시작 후 에러 없이 뜨는 것과 명령어 등록까지 확인.

### 버그: 상점에 넣은 아이템 이름/등급 색상이 사라지던 문제
`/낚시관리`로 지급받은 물고기(등급별 색상 있음)를 `/상점아이템설정`으로 배치할 땐 색상까지 잘 보이는데, 실제 `/상점`에서 보면 전부 흰색 기본 이름으로 뜨는 문제 — 상점 시스템이 아이템을 저장할 때 재료 종류(자재/아이템에더 ID)만 기억하고 이름·색상 자체는 버린 뒤, 손님용 화면에서 재료 종류만 가지고 아이템을 다시 그리고 있었음(그래서 아이템에더 기본 이름으로만 뜸). 배치된 아이템에 커스텀 이름이 있으면 `ShopItem`에 그 이름(색상 포함, 직렬화해서)을 같이 저장해뒀다가 화면에 그릴 때 그대로 입혀주도록 수정 — 이미 넣어둔 물고기들도 재시작하면 DB에서 다시 읽어올 때 자동으로 정상화됨(따로 재배치 안 해도 됨).
- 세 서버 재시작, 에러 없이 뜨는 것 확인.

### 메뉴 - 카테고리 화면 항목 정리 + 뒤로가기 버튼이 항목을 가리던 버그 발견/수정
**버그 발견**: `강화` 카테고리에서 "인챈트강화" 항목이 안 보인다는 제보를 조사하다가, `MenuCategoryGui`의 슬롯 배치 계산식이 잘못돼서 뒤로가기 버튼이 항목 버튼을 덮어쓰고 있었던 걸 발견함. 2줄짜리 카테고리는 항목과 뒤로가기 버튼이 같은 줄(줄1)에 배치되는데, 항목이 4번째 칸(가운데)까지 걸치면 그 자리가 뒤로가기 버튼 자리와 겹쳐서 나중에 그려지는 뒤로가기 버튼이 항목을 조용히 덮어써버림. 영향받은 카테고리: 강화(인챈트강화), 경제(은행), 생활(도감), 상점/시장(거래), 토지(마을랭킹), 텔레포트(플레이어워프), 퀘스트/이벤트(출석체크) — 전부 이 버그로 항목 하나씩이 안 보이고 있었음. 항목을 뒤로가기 버튼과 다른 줄(줄0)에 배치하도록 수정.
- 코스메틱/랭크아이콘/채널 항목 삭제, 프로필/내정보/칭호/친구/커플에 아이콘 추가(칭호→`daily_quest:rookie_badge`, 나머지는 바닐라)
- 신고/쿠폰/도움말/가방/경매/토지 관리에도 바닐라 아이템으로 아이콘 표시
- 허브 상단 정보 패널(닉네임/재화/토지) 완전히 삭제 — 카테고리 버튼 9개만 0~8번에 한 줄로 표시
- 빌드 확인 완료. 재배포/재시작은 대기 중

### 메뉴 - 허브 상단 정보 패널 정리
`/메뉴` 허브 상단 2줄(닉네임/재화/토지 3개 패널, 각 6칸씩 총 18칸)을 1줄(0~8번 슬롯, 각 3칸씩)로 줄임. 닉네임 패널의 이름("Lulo8331")과 "환영합니다!" 문구, 재화/토지 패널의 제목("내 재화"/"내 토지")을 전부 없애고 빈 칸(공백)으로 처리 — 재화/토지의 실제 수치(온/은행/캐시/토지 레벨)는 툴팁(lore)에 그대로 남아있음.
- 빌드 확인 완료. 재배포/재시작은 대기 중

### 메뉴 - 나머지 8개 카테고리 항목에도 전용 아이콘 추가
경제/생활/상점·시장/토지/텔레포트/강화/기타 카테고리의 세부 항목(투명 아이콘만 쓰던 것들)에도 서버에 이미 깔려있는 다른 팩에서 어울리는 아이콘을 찾아 적용함(전부 RCON `/iagive`로 실제 등록 확인 후 반영). 새로 뒤진 팩: `moafarm_items`(코인/가방/상점 등 잡화 데코 아이템 팩), `medival_jobs`, `fishing_expansion`, `playerwarps_gui`, `daily_quest`, `yeowool_enhance`, `yeowool_mailbox`, `yeowool_rtp`.
- 경제: 돈→`moafarm_items:eventcoin`, 은행→`moafarm_items:money_sack`, 내캐시→`moafarm_items:content1_coin`
- 생활: 직업→`medival_jobs:medival_jobs_miner`, 낚시대회→`fishing_expansion:golden_fishing_rod` (가방/도감은 어울리는 아이콘을 못 찾아서 투명 아이콘 유지)
- 상점/시장: 상점→`playerwarps_gui:pwarp_shop`, 거래→`moafarm_items:shop_normal` (경매는 못 찾음)
- 토지: 마을랭킹→`daily_quest:button_leaderboard` (토지 관리는 못 찾음)
- 텔레포트: 홈→`playerwarps_gui:pwarp_home`, 워프→`playerwarps_gui:default_warpitem`, 플레이어워프→`playerwarps_gui:all_warpsicon`, 무작위 순간이동→`yeowool_rtp:rtp_pin`
- 강화: 강화→`yeowool_enhance:book_unique`, 인챈트강화→`yeowool_enhance:book_elite`
- 기타: 우편함→`yeowool_mailbox:unopened_box`, 랭킹→`daily_quest:button_leaderboard`, 길라잡이→`moafarm_items:recipe_book` (신고/쿠폰/도움말은 못 찾음)
- 커뮤니티 카테고리(프로필/내정보/칭호/친구/커플/코스메틱/랭크아이콘/채널)는 관련 팩(`beautiful_ranks`, `cosmetics`)이 전부 폰트이미지·엔티티 전용이라 인벤토리 아이콘으로 못 씀 — 8개 다 투명 아이콘 유지
- `MenuEntry`에 선택적 `iconId` 필드 추가(없으면 기존처럼 투명 아이콘 fallback), `MenuHubGui.dispatchEntry`에 아이콘 지정 오버로드 추가
- 빌드 확인 완료. 재배포/재시작은 대기 중

### 메뉴 - 퀘스트/이벤트 카테고리 배경을 빈 프레임으로 변경 + 전용 아이콘 5개 추가
`/메뉴` → 퀘스트/이벤트 카테고리만 유독 "REWARDS/DAILY/WEEKLY/MONTHLY"가 그려진 전용 템플릿(`menu_bg_rewards_v2`)을 쓰고 있었음(다른 8개 카테고리는 빈 프레임). 원래는 이 카테고리 내용(일일/주간 퀘스트, 출석체크 등)과 라벨이 잘 맞아서 일부러 골랐던 건데, 통일감을 위해 다른 카테고리들처럼 빈 프레임(`menu_bg_18_v2`)으로 바꿈. 대신 그동안 투명 아이콘만 쓰던 5개 항목에 전용 아이콘을 새로 찾아 넣음(RCON `/iagive`로 실제 등록 확인 후 적용):
- 일일 퀘스트 → `rewards:rewards_daily`
- 주간 퀘스트 → `rewards:rewards_weekly`
- 출석체크 → `daily_quest:reward`
- 이벤트 → `yeowool_battlepass:bp_reward_unclaimed`
- 배틀패스 → `yeowool_battlepass:bp_questbook`
- `MenuEntry`에 선택적 `iconId` 필드 추가(없으면 기존처럼 투명 아이콘 fallback), `MenuHubGui.dispatchEntry`에 아이콘 지정 오버로드 추가
- 빌드 확인 후 재배포, 서버 재시작함(재시작 전 접속자 있어서 미리 물어보고 진행)

### RTP(무작위 순간이동) 신설
- `/rtp` 명령어 신설 (yeowool-teleport, `rtp` 패키지). "animated_rtp_0.8" ItemsAdder 팩(원래 BetterRTP+DeluxeMenus용 참고 자료)에서 배경 3장(오버월드/네더/엔드)과 아이콘 2개(핀=이동가능, 자물쇠=쿨다운중)만 가져다 새 네임스페이스 `yeowool_rtp`로 등록해서 씀 — 참고 팩의 유료 다단계 반경/쿨다운 구조와 `model_path` 기반 애니메이션 아이템은 이번엔 안 쓰고, 단일 등급 쿨다운 방식으로 단순화함
- GUI(`RtpGui`, 27칸)는 플레이어가 있는 차원에 맞는 배경을 자동으로 골라 보여주고, 가운데 버튼을 누르면 스폰 기준 반경 내 무작위 좌표를 뽑아 안전한 지점(용암/낙사 아님, 기존 `PlayerWarpSafety` 재사용)을 찾아 이동시킴. 이동 자체는 기존 `TeleportService`(지연시간/이동취소/일반 쿨다운 공유)를 그대로 재사용하되, RTP 전용의 더 긴 쿨다운(기본 5분)을 별도로 관리
- `config.yml`의 `rtp:` 섹션에서 쿨다운, 차원별 반경(오버월드 5000/네더 1000/엔드 2000), GUI 배경 오프셋 조정 가능
- `/메뉴` → 텔레포트 카테고리에도 "무작위 순간이동" 버튼 추가
- ItemsAdder 쪽은 새 네임스페이스라 배틀패스/메뉴 때 겪은 "이름 재사용 시 등록 실패 캐싱" 문제가 없었고, offset도 처음부터 -8로 넣어서 한 번에 정상 등록됨(RCON `/iainfo`로 검증)
- 빌드 확인 후 재배포, 새 jar 반영을 위해 서버 재시작함(재시작 전 접속자 없는 것 확인 후 진행)

### 메뉴 카테고리 아이콘 - 서버에 이미 깔려있는 다른 팩에서 전용 아이콘 찾아서 적용
투명 아이콘 대신 실제 아이콘을 쓰기 위해, 이 서버에 이미 설치된 21개 ItemsAdder 콘텐츠 팩을 뒤져서 어울리는 아이콘을 찾음 — RCON으로 `/iagive`를 직접 실행해 후보마다 실제로 등록된 아이템인지 하나하나 검증한 뒤 확정.
- 생활 → `medival_jobs:medival_jobs_farmer` (직업 아이콘 팩)
- 텔레포트 → `playerwarps_gui:pwarp_home`
- 퀘스트/이벤트 → `daily_quest:reward`
- 강화 → `yeowool_enhance:book_simple`
- 기타 → `yeowool_mailbox:unopened_box`
- 경제/상점·시장/토지/커뮤니티는 어울리는 전용 아이콘을 못 찾아서 바닐라 아이템(금괴/에메랄드/잔디 블록/플레이어 머리)으로 대체. 정보 패널 3개(닉네임/재화/토지)도 마찬가지로 바닐라 아이템으로 바꿈
- 빌드 확인 후 재배포 완료. 검증 중 테스트로 지급했던 아이템들은 RCON `/iaremove`로 정리함

### 메뉴 배경 안 보이던 문제 원인 발견 및 수정
`/메뉴`를 열어도 배경/아이콘이 하나도 안 보이던 문제 — `scale_ratio` 추가로도 안 고쳐져서, RCON으로 서버에 직접 접속해 `/iainfo`(ItemsAdder 폰트이미지 등록 개수), `/iagive`(아이템 실제 등록 여부), `/iareload` 반복 실행을 통해 직접 원인을 추적함.

**진짜 원인**: ItemsAdder가 폰트이미지를 **키 이름 기준으로 실패 상태를 캐싱**하고 있었음 — `scale_ratio`를 안 넣은 첫 시도에서 `menu_bg_hub` 등 7개 이름으로 등록을 시도했다가 실패했는데, 그 뒤 설정을 고쳐도 **같은 이름**으로는 계속 실패한 상태로 남아있고(재시작을 여러 번 해도 그대로), **새 이름**으로 넣으면 바로 정상 등록되는 것을 실험으로 확인함(`/iainfo`의 폰트이미지 개수가 딱 새 이름 개수만큼만 올라가는 것으로 검증).

**해결**: 7개 폰트이미지 키 이름을 전부 `_v2`를 붙여서 새로 등록(`menu_bg_hub_v2` 등), 자바 코드의 참조도 맞춰서 수정. 리네임 전 이름으로 쓰던 텍스처 파일은 정리함. 최종적으로 ItemsAdder 폰트이미지 등록 개수가 200/6608로 정상 확인됨(`/iazip`으로 재배포 완료).

**앞으로 참고**: ItemsAdder 폰트이미지 설정이 처음에 잘못돼서 등록 실패하면, 나중에 설정을 고쳐도 **같은 이름**으로는 재시도가 안 먹히는 것으로 보임 — 폰트이미지 이름을 한 번 정했으면 처음부터 정확하게 넣는 게 안전하고, 문제가 생기면 재시작 대신 이름을 바꿔서 다시 시도하는 게 더 빠름.

**+추가로 발견된 두 번째 원인**: 이름을 고친 뒤에도(`/iainfo` 등록 개수는 정상으로 올라갔는데도) 여전히 화면에 안 보였음 — 실제 원인은 메뉴 화면 코드가 좌우 위치 보정값(`offset`)을 전부 `0`으로 넘기고 있었던 것. `:offset_0:`이 ItemsAdder에 실제로 등록된 placeholder가 아니었던 것으로 보이며, 이 프로젝트에서 실제로 작동 확인된 화면(배틀패스 등)은 전부 0이 아닌 값(-8, -32 등)을 쓰고 있었음. 메뉴 화면들과 배틀패스의 작은 로고 아이콘(`bp_logo`, 이것도 offset 0으로 미확인 상태였음)을 전부 기본값 `-8`로 변경해서 최종 해결됨. **결론: ItemsAdder 배경/오프셋 관련 코드에서 offset 값은 절대 0으로 넘기지 말 것 — 항상 -8 같은 0이 아닌 값을 기본으로 쓸 것.**

### 통합 메뉴 시스템 신설
- `/메뉴` 명령어, 그리고 웅크린 채로 손 바꾸기(F) 키를 누르면(Shift+F 흉내 — 클라이언트가 임의의 키 입력을 서버로 안 보내서, 서버가 감지 가능한 것 중 F키에 대응하는 "손 바꾸기" 이벤트+웅크림 여부로 구현) 열리는 허브 GUI 신설. "UltimateGUI_steampunk_pack" ItemsAdder 애드온(namespace: `yeowool_menu`) 사용
- 메인 허브(54칸): 상단 두 줄에 플레이어 정보 패널 3개(닉네임 / 온·은행·캐시 / 토지 레벨), 하단에 카테고리 9개(경제/생활/상점·시장/토지/텔레포트/커뮤니티/퀘스트·이벤트/강화/기타)를 3×3 배치
- 각 카테고리는 재사용 가능한 `MenuCategoryGui`(하나의 범용 클래스, 배경/줄 수/버튼 목록만 다르게 넘김) 서브 화면으로 연결됨 — 버튼을 누르면 대부분 그 기능의 실제 명령어를 그대로 실행(`player.performCommand(...)`)하는 방식이라, 각 모듈의 기존 GUI/명령어를 중복 구현하지 않음. 예외 2개: **배틀패스**는 이미 자체 허브라 `BattlePassPortalGui`를 바로 열고, **상점**은 GUI를 안 열고 `/상점이동`을 실행함
- **`/상점이동` 신설** (yeowool-market) — 상점 NPC(Citizens)를 즉시 여는 대신, NPC가 있는 위치로 텔레포트만 하고 실제로 상점을 열려면 플레이어가 직접 NPC를 우클릭해야 하도록 요청받아 만듦. NPC ID는 `config.yml`의 `npc-shop.menu-teleport-npc-id`(기본값 -1 = 미설정)에서 읽어오고, 좌표를 저장해두는 대신 클릭할 때마다 Citizens API로 NPC의 현재 위치를 실시간 조회해서 텔레포트함(나중에 NPC를 옮겨도 코드를 안 건드려도 됨). **아직 실제 상점 NPC를 안 만드셔서 NPC ID가 미설정 상태 — NPC 만드신 뒤 ID 알려주시면 config에 반영 예정**
- 배경 이미지(`menu_bg_hub`, `menu_bg_jobs`, `menu_bg_rewards`, `menu_bg_profile`, `menu_bg_18/27/36`)는 팩 자체 설정에 `scale_ratio`가 명시돼 있지 않아 원본 그대로(기본값=원본 이미지 높이) 적용해뒀음 — 배틀패스 때처럼 실제로 인게임에서 보면서 좌우 위치(offset)나 크기를 조정해야 할 가능성이 높음
- 카테고리 서브 화면들의 정확한 슬롯 배치(버튼 위치)는 초안이라, 실제 화면 보면서 배틀패스 때처럼 같이 조정할 예정
- 빌드 확인 후 `C:\YEOWOOL\lobby\plugins\`에 재배포 완료. **새 명령어(`/메뉴`, `/상점이동`)가 추가됐고 새 리소스팩 콘텐츠도 추가됐으니 서버 재시작 필요**

## 2026-09-02

### 배틀패스 UI 버그 수정 및 리소스팩 활용 확대
- **버그 수정**: 배틀패스 GUI(포탈/보상 목록)에 배경 이미지가 전혀 안 보이던 문제 — 배경 이미지 ID를 `"yeowool_battlepass:bp_portal_bg"`처럼 네임스페이스를 붙여서 넘기고 있었는데, 이 프로젝트의 폰트이미지 트릭은 네임스페이스 없는 순수 ID(`"bp_portal_bg"`)를 써야 함(실제 잘 동작하는 퀘스트/출석 화면들과 대조해서 확인). 그동안 에러 없이 조용히 평범한 텍스트 제목으로 대체되고 있었음
- **리소스팩 미사용 자산 활용** — 구매한 "Immersive_BattlePass_UI" 팩에서 배경 2개/아이콘 7개만 쓰고 있던 걸, 남아있던 자산(아이콘 2개 + 배경 3개 + 인라인 이미지 2개)까지 전부 실제 화면에 연결함
  - `bp_questbook`/`bp_questbook_gray` 아이콘 신규 등록 — `/배틀패스` 포탈에 새 "퀘스트" 버튼(bp_questbook 아이콘) 추가
  - 새 화면 `BattlePassQuestOverviewGui`(배경 `bp_quest_overview_bg`) — 일일/주간 중 선택하는 작은 허브. 배틀패스 자체 퀘스트 목록을 새로 만들진 않고(기존 `/일일퀘스트`·`/주간퀘스트`에서 포인트를 받아오는 구조라), 그 두 화면으로 안내하는 역할
  - 새 화면 `BattlePassQuestSummaryGui`(배경 `bp_daily_quests_bg`/`bp_week_bg`, 기간별로 다른 배경) — 완료 개수를 보여주고 실제 퀘스트 보드(`QuestBoardGui`)로 연결
  - `bp_progress_bar`(2x9 타일)로 다음 티어까지 진행도를 보여주는 실제 프로그레스 바를 포탈 화면 통계 아이템 설명에 추가
  - `bp_logo`(작은 로고 이미지)를 포탈 화면 통계 아이템 이름 앞에 아이콘으로 붙임
  - 이제 팩에 있는 자산(아이콘 9개 + 배경 5개 + 인라인 이미지 2개) 전부 실제로 사용 중
- 빌드 확인 후 `C:\YEOWOOL\lobby\plugins\`에 재배포 완료. **아이템/폰트이미지가 새로 추가됐으니 서버를 재시작해서 ItemsAdder 리소스팩이 다시 만들어지고 배포되게 해야 함** (플레이어들도 재접속해야 새 리소스팩을 받음)

### 추가된 기능
- **배틀패스 시스템 신설** (`yeowool-community`, `com.yeowool.community.battlepass`) — "Immersive_BattlePass_UI" ItemsAdder 애드온(namespace: `yeowool_battlepass`)을 이용한 시즌제 무료/프리미엄 보상 트랙
  - FREE 트랙은 기본으로 열려있고, PREMIUM 트랙은 캐시로 구매(기본 9,900캐시, `config.yml`의 `battlepass.premium-price-cash`로 조정 가능)하거나 관리자가 `/배틀패스 프리미엄지급 [닉네임]`으로 무상 지급하기 전까지 잠겨있음
  - 포인트는 기존 `/일일퀘스트`·`/주간퀘스트` 완료 시 난이도(쉬움/보통/어려움)별로 자동 적립됨(`config.yml`의 `battlepass.points-per-quest`) — 새 이벤트 리스너 없이 `QuestManager.claimQuest()`에 한 줄만 추가해서 연결
  - `/배틀패스` — 시즌/포인트/현재 티어/프리미엄 상태 요약 GUI, 보상 목록(9티어씩 페이지 이동), 프리미엄 구매 버튼(두 번 클릭해야 실제 구매되는 오조작 방지 방식)
  - `/배틀패스 [무료|유료] 보상설정` (관리자 전용, `yeowool.community.battlepass.manage` 권한) — 티어 1부터 GUI가 열리고, GUI 안의 ◀▶ 버튼으로 다른 티어로 직접 이동하면서 필요 포인트/지급액/화폐(온·캐시)/보너스 아이템을 편집. 출석 보상 설정(`/출석보상설정`)과 동일한 방식(가상 모루로 숫자 입력, 아이템은 슬롯에 놓고 GUI를 닫으면 저장)이라 운영 방식이 통일됨
  - 보상 설정(관리자가 정한 값)은 DB(`yw_battlepass_reward_config`, `yw_battlepass_reward_items`)에, 플레이어별 진행 상태(포인트/프리미엄 여부/수령한 티어)는 새 테이블 없이 기존 `PlayerData`(통계/설정)에 저장 — 시즌 번호(`battlepass.season`)를 올리면 모든 플레이어가 자동으로 새 시즌으로 초기화됨(리셋 로직 불필요)
  - 빌드 및 서버(`C:\YEOWOOL\lobby\plugins\`) 배포 완료. **`plugin.yml`에 새 명령어(`/배틀패스`)가 추가되어서 `/reload`가 아니라 서버 재시작이 필요함**

### 전체 시스템 버그 점검 및 수정
지금까지 만든 모든 플러그인(12개 모듈)을 6개 그룹으로 나눠 코드 리뷰를 진행하고, 발견된 문제를 심각도 순(치명적 → 높음 → 중간 → 낮음)으로 전부 수정함. 모든 수정 사항은 `./gradlew build`로 컴파일 확인 후 `C:\YEOWOOL\lobby\plugins\`(프록시는 `C:\YEOWOOL\proxy\plugins\`)에 재배포 완료.

**치명적**
- **배틀패스/출석보상 "금액·필요포인트 설정" 버튼이 항상 무반응이던 버그 수정** — `BattlePassAmountListener`/`AttendanceRewardAmountListener`의 `onClose`가 모루가 아닌 인벤토리(모루를 열며 자동으로 닫히는 기존 GUI)가 닫힐 때도 입력 대기 상태를 지워버려서, 관리자가 숫자를 입력하고 확인해도 항상 저장이 안 되던 문제. 모루 자신이 닫힐 때만 반응하도록 수정
- **우편함 아이템 복제 가능하던 버그 수정** — `MailboxManager.claim()`이 "조회 후 삭제"를 원자적이지 않게 처리해서 같은 항목을 빠르게 두 번 클릭하면 두 번 지급될 수 있었음. `MailboxRepository`에 삭제된 행 수를 확인하는 원자적 `claim()` 메서드 추가
- `/추방` 권한 누락 의심 항목은 실제 확인 결과 오탐(이미 정상적으로 `yeowool.admin` 권한이 걸려 있었음) — 수정 없음

**높음**
- **GUI 빈 슬롯으로 아이템이 증발하던 버그 수정** — 공용 `GuiListener`가 플레이어 자기 인벤토리→GUI 방향 시프트클릭을 검사하지 않아서, 거래(`/거래`)·관리자 상점 편집·배틀패스/출석보상 편집 GUI 전부에서 지정 슬롯이 다 차면 안 보이는 빈 슬롯에 아이템이 들어가 그대로 사라질 수 있었음. 시프트클릭이 지정된(editable) 슬롯에만 들어가도록 직접 처리하게 수정 — 이 GUI 프레임워크를 쓰는 모든 화면에 공통 적용됨
- **경매 정산 실패해도 "낙찰됐다"고 잘못 안내하던 버그 수정** — `AuctionManager.settle()`의 판매자 대금 지급/밀려난 입찰자 환불이 실패해도 조용히 넘어가고, 인게임 메시지·디스코드 DM은 무조건 "낙찰 완료"로 발송되던 문제. 실패 시 로그를 남기도록 수정, 안내 메시지도 지급 성공 후에만 발송되도록 순서 변경. 캐시 경매인데 메시지에 화폐가 항상 "온"으로 잘못 표시되던 것도 함께 수정
- **호박/수박/사탕수수로 경험치 무한 파밍 가능하던 버그 수정** — 이 3종은 성장 시간 강제 대기(10분)가 적용 안 되고 있어서, 심고 바로 캐고 다시 심는 걸 반복하면 무한으로 경험치·통계를 얻을 수 있었음. 다른 작물과 동일하게 강제 성장 타이머를 적용하도록 수정
- **가방 GUI 열어놓은 채로 자동수집되면 아이템이 사라지던 버그 수정** — 가방 GUI를 닫을 때 GUI를 열었던 시점의 스냅샷으로 덮어써서, GUI가 열려있는 동안 자동수집(인벤토리 꽉 찼을 때)된 아이템이 사라졌음. GUI가 열려있는 동안에는 해당 가방으로 자동수집이 안 되도록(바닥에 남아있다가 GUI 닫으면 다시 수집됨) 수정

**중간**
- 배틀패스 보상 아이템 저장(삭제 후 삽입)이 트랜잭션으로 묶여있지 않아서 중간에 실패하면 해당 티어 아이템이 DB에서 통째로 사라질 수 있던 문제 — 트랜잭션으로 묶어서 실패 시 롤백되도록 수정
- 관리자 추첨(`/추첨`) 도중 남은 후보 전원이 오프라인이 되면 당첨자 수를 못 채우고 조용히 종료되던 문제 — 온라인 후보가 없어도(연출용 이름 넘기기만 생략하고) 당첨자를 끝까지 뽑도록 수정
- 토지 소유권 이전 시 새 소유자가 이전에 멤버였던 경우 DB에 멤버 기록이 안 지워지던 문제(멤버 목록에 소유자가 중복 표시됨, 권한 오류는 없었음) — 수정

**낮음 / 기타**
- `PlayerCache` 클래스 설명이 실제 동작(접속 중인 플레이어를 별도로 고정하지 않음)과 다르게 되어 있던 문서 오류 수정
- 정지(밴)된 계정의 데이터를 접속 거부 전에 불필요하게 미리 불러오던 것을 순서 변경(밴 확인을 먼저 하도록)해서 DB 조회 낭비와 캐시 소모를 줄임
- `yw_discord_dm_queue` 테이블이 `yeowool-discord` 플러그인에서만 생성되고 있어서, `yeowool-core`(우편함)·`yeowool-market`(경매)이 그 플러그인 로드 순서에 암묵적으로 의존하고 있던 문제 — `yeowool-core`도 동일한 테이블을 직접 생성하도록 수정(디스코드 플러그인이 없어도 안전하게 동작)
- `yeowool-proxy`의 서버 대기열이 여러 스레드(명령어/접속종료/주기 틱)에서 동기화 없이 동시에 접근되던 문제 — 동기화 추가
- 배틀패스 보상 수령 실패 시(포인트 부족/이미 수령/설정 안 됨) 아무 안내 없이 무반응이던 것 — 안내 메시지 추가
- 배틀패스 보상 목록 페이지가 설정된 티어를 넘어서도 계속 다음 페이지로 넘어가지던 것 — 마지막 페이지 이후에는 다음 버튼이 안 뜨도록 수정
- 지갑 송금(`/돈 보내기`)·거래(`/거래`)의 잔액 이체가 실패 여부를 확인하지 않고 넘어가던 것 — 실패 시 이미 차감된 금액을 환불하도록 방어 코드 추가(실제로는 거의 발생 불가능한 경로였음)
- 오래 켜둔 서버에서 플레이어가 직접 놓은 원목 위치 기록이 계속 쌓이기만 하던 것(캐지 않고 다른 방식으로 없어진 경우) — 주기적으로 정리하는 작업 추가
- **디스코드 봇 토큰 DB 평문 저장 문제 해결** — `yeowool-discord`에 `config-encryption-key`를 설정하면 `bot-token`을 AES-256-GCM으로 암호화해서 DB에 저장하도록 `ConfigCrypto.java`/`DiscordConfigSync.java` 추가(키를 안 넣으면 예전처럼 평문 저장, 기존 서버는 그대로 동작). 처음엔 이 프로젝트(yeowool-community 등과 별개인 Gradle 저장소) 밖이라 손댈 수 없다고 안내했으나, 사용자가 실제 봇 소스 위치(`C:\Users\apple\OneDrive\Desktop\yeowool-discord-bot`)를 알려줘서 그 프로젝트도 직접 확인·수정함 — `src/configCrypto.js`(복호화, 키 없으면 평문 그대로 통과) 신설, `src/db.js`의 `loadRemoteConfig()`가 `bot-token`을 자동으로 복호화하도록 연결, `.env.example`/`README.md`에 `CONFIG_ENCRYPTION_KEY` 설정법 문서화. Node로 암/복호화 라운드트립 직접 테스트해서 자바 쪽(`iv || ciphertext+tag` 형식)과 바이트 단위로 호환되는 것 확인. 실제 키 발급(`openssl rand -base64 32`)과 양쪽 설정 파일에 넣는 것은 사용자가 직접 진행해야 함(비밀키라 대신 만들어 넣지 않음)

## 2026-09-01

### 추가된 기능
- `yeowool-web` — YEOWOOL 서버 공식 웹사이트 틀 신설 (Next.js + TypeScript, 이 마인크래프트 서버와 같은 컴퓨터에서 함께 운영). 같은 제작자의 [moafarm.cloud](https://moafarm.cloud)와 같은 레이아웃 포맷(고정 헤더, 다크 히어로+통계바+기능 카드 그리드+공지+4단 푸터)을 참고해 만듦
  - `/` — 실시간 접속자 수(Server List Ping), 등록된 마을/활동 직업인 수(DB 실시간 조회), 6개 콘텐츠 소개 카드(경매장/마을/직업/인챈트강화/낚시/퀘스트&가방)
  - `/rankings` — 재산 랭킹 (읽기 전용 DB 조회)
  - `/admin` — 디스코드 로그인(허용된 계정 ID만) 필요, 캐시 지급 폼(RCON으로 실제 `/캐시지급` 명령 실행, 닉네임/금액 형식 검증 포함)
  - 웹 전용 읽기 전용 MySQL 계정(`web_readonly`) 생성 완료 — root 계정 재사용 안 함
  - 모바일 화면(`lg` 미만)에서 상단 메뉴가 햄버거 버튼 + 드롭다운으로 전환되도록 추가
  - 로그인을 디스코드 계정 아무나 가능하도록 변경 — 로그인 직후 봇 토큰으로 여울 디스코드 서버의 역할을 조회해서, 서버장/개발자/관리자 역할이 있으면 `/admin`으로, 없으면 일반 회원용 `/mypage`로 자동 분기(기존의 "허용된 계정 ID만 로그인 가능" 방식에서 "역할 기반 분기" 방식으로 전환)
  - 상단 내비게이션/푸터를 moafarm.cloud와 동일한 구성(홈/공지사항/패치노트/개발 노트/업데이트 예정/월드 소개/콘텐츠 가이드/사전예약/랭킹/후원하기/로그인, 푸터 빠른 링크+서버 정보+개인정보 보호·이용약관)으로 맞춤 — `/notices`·`/patch-notes`·`/dev-notes`는 이후 실제 게시글 시스템으로 연결됨(아래 참고), 나머지 자리 페이지들은 아래 항목에서 순차적으로 채워짐
  - `webService.md`에 구축 계획, `yeowool-web/README.md`에 실제 설정 방법 정리
  - 디스코드 OAuth 앱(여울 봇과 같은 애플리케이션) 연동 완료 — Client ID/Secret, Redirect URI 등록까지 마쳐서 실제 로그인 확인됨
  - 관리자 전용 게시글 시스템(`/admin/posts`) 신설 — 공지사항/패치노트/개발 노트를 마크다운(굵게/목록/인용/취소선 등)으로 직접 작성·삭제 가능. 새 테이블 `yw_web_posts`를 만들고 웹 DB 계정에 그 테이블에 한해서만 쓰기 권한을 추가로 부여(다른 플러그인 테이블은 여전히 읽기 전용). `/notices`, `/patch-notes`, `/dev-notes`(신규), 홈 화면 공지 미리보기가 전부 이 데이터를 보여주도록 연결
  - 관리자 전용 재화·거래 현황(`/admin/economy`) 신설 — 온(무료 재화)/캐시(유료 재화) 누적 지급·사용량과 최근 NPC 상점 구매·경매 거래 내역을 보여줌. 새 테이블 없이 기존 `yw_logs`(모든 플러그인이 공용으로 쓰는 이벤트 로그) 데이터만 읽어서 계산
  - `/terms`에 실제 이용약관 전문 반영, `/privacy`(개인정보 처리방침)는 서버가 실제로 수집·처리하는 정보(마인크래프트/디스코드 계정, 게임 내 재화·거래 기록, 접속 로그, 후원 결제 등)를 기준으로 직접 작성해 반영(법률 자문은 아니라는 안내 포함)
- `/캐시지급`으로 캐시를 받으면(웹사이트 RCON 경로 포함) 받는 플레이어가 온라인이면 인게임에 "[여울] 관리진에게 N캐시를 지급 받았습니다" 안내 메시지가 뜨도록 추가(차감 시에는 "차감되었습니다") — 지금까지는 지급한 사람(콘솔/관리자)에게만 결과가 보이고 받는 사람에게는 아무 알림이 없었음
- **디스코드 DM 알림 시스템 신설** — 우편함에 아이템이 도착하거나 경매가 팔렸을 때, 그리고 관리자가 특정 플레이어에게 메시지를 보내고 싶을 때 실제 디스코드 개인 DM으로 전달되도록 함
  - `yeowool-discord`에 새 테이블 2개 추가: `yw_account_links`(마인크래프트 계정 ↔ 디스코드 계정 연동), `yw_discord_dm_queue`(발송 대기 알림 큐)
  - `yeowool-core`의 `MailboxManager`가 우편함에 아이템을 쌓을 때(온라인이 아니라 실제로 저장될 때만 — 바로 인벤토리로 받은 경우는 이미 확인했으니 제외) 알림 큐에 적재하도록 수정. `yeowool-market`의 `AuctionManager.settle()`도 경매가 팔렸을 때 판매자에게(대금은 우편이 아니라 잔액으로 바로 들어가서 별도 알림 필요) 알림 큐에 적재하도록 수정 — 낙찰자 쪽은 아이템이 우편함으로 갈 때 이미 알림이 가므로 중복 발송 안 함
  - **중요한 설계 판단**: YeowoolDiscord는 원래 "디스코드에 직접 접속하지 않는다"(실제 봇 접속은 별도 JS 봇이 전담)는 원칙으로 만들어져 있었는데, 그 JS 봇 코드는 이 프로젝트 밖에 있어서 손댈 수가 없었음. 그래서 이번엔 예외적으로 **웹사이트(`yeowool-web`)가 봇 토큰으로 직접 디스코드 DM을 발송**하도록 함(`src/lib/discordDm.ts`) — `src/instrumentation.ts`로 서버 시작 시 5초 간격 백그라운드 폴러를 띄워서 큐를 처리. 즉 이제 이 알림 기능은 웹사이트가 켜져 있어야만 동작함(꺼져 있어도 게임 자체나 우편함/경매 기능엔 지장 없고, 알림만 큐에 쌓여 있다가 웹사이트가 다시 켜지면 처리됨)
  - `/mypage`에 "마인크래프트 계정 연동" 기능 추가 — 닉네임을 입력하면 Mojang API로 실존 여부를 확인한 뒤 로그인한 디스코드 계정과 연동. 연동 안 한 플레이어는 알림이 조용히 건너뛰어짐(재시도 없음)
  - `/admin/messages` 신설 — 관리자가 닉네임+메시지를 입력하면 같은 큐를 통해 디스코드 DM 발송, 최근 발송 내역(성공/미연동/실패 사유) 조회 가능
  - 실제 디스코드 REST API까지 호출해서 전체 파이프라인(큐 적재 → 폴러가 읽음 → 연동 조회 → API 호출 → 결과 기록)이 정상 동작하는 것을 확인함(가짜 디스코드 ID로 테스트해서 실제 낯선 사람에게 오발송되지 않게 함)
- 그동안 밀려있던 나머지 TODO 항목들도 정리
  - 랭킹 페이지에 토지 레벨/퀘스트 완료 수 탭 추가(재산/토지 레벨/퀘스트 완료 3개 탭)
  - 관리자 대시보드(`/admin`)에 현재 접속자 목록(Server List Ping의 플레이어 샘플 목록 이용, 별도 설정 불필요)과 최근 제재 내역 조회 추가
  - 캐시 지급 시 Mojang API로 실존 닉네임인지 먼저 확인하도록 강화(Mojang API 자체가 잠깐 안 될 때는 관리자 작업을 막지 않고 그냥 진행)
  - `/world`에 실제 게임 시스템 소개 내용 채움. `/roadmap`·`/preregister`·`/donate`는 실제 일정/이벤트/후원 등급처럼 운영진만 아는 내용이 필요해 임의로 지어내지 않고 "준비 중"으로 남겨둠
  - `/mypage`에 실제 기능 채움 — 계정 연동(위 참고), 내 잔액(온/은행/캐시), 현재 등록 중인 내 경매, 우편함 미리보기(단, 경매는 정산되면 DB에서 삭제되는 구조라 과거 이력까지는 못 보여줌 — README에 한계 명시)
  - `/admin/economy`에 순유입(지급 − 사용) 수치 추가

### 수정된 사항
- **버그 수정**: `/admin/economy`에서 "Unknown column 'amount' in 'field list'" 오류로 페이지가 아예 안 열리던 문제 — `yw_logs` 테이블에는 실제로 `amount` 컬럼이 없고 `data` 컬럼 안에 "amount=...;newBalance=..." 형태의 문자열로 들어있는데, SQL에서 바로 `SUM(amount)`를 하려 한 게 원인. 거래 내역 파싱과 동일한 방식(정규식으로 값 추출)으로 고쳐서 정상적으로 집계되도록 수정
- **버그 수정**: 게시글 마크다운에서 문단 맨 앞에 오는 `**굵게**`가 굵게 처리 안 되고 별표(`**`)가 그대로 텍스트로 보이던 문제(개인정보 처리방침 초안 작성 중 발견) — 문단 중간에 오는 굵게는 정상 동작해서, 문장을 살짝 다듬어(굵게 표시를 문단 맨 앞이 아니라 한 단어 뒤로) 우회. 앞으로 게시글 작성 시에도 문단을 굵은 글씨로 바로 시작하지 않는 게 안전함

> 자동줍기권/자동심기권 아이콘 교체·스택형 재설계, 가방 아이콘 위치 조정, 작물 성장 타이머 복원 버그 수정은 날짜 표시 오류로 아래 2026-08-31 항목에 함께 기록되어 있음(실제로는 09-01 작업).

## 2026-08-31

### 추가된 기능
- `/인챈트강화` — AdvancedEnchantments UI 팩 기반의 완전히 새로운 강화서 시스템 신설(`com.yeowool.enchant`, 기존 `/강화`와는 별개 시스템). 온으로 등급별(simple~fabled) 인챈트북 구매 → Tinkerer GUI에 책을 드래그해 넣으면 마법가루+온 환불로 분해 → Alchemist GUI에 같은 등급 책 2개를 넣으면 상위 등급 책 1개로 합성. 실제 바닐라 `ENCHANTED_BOOK`(인챈트 저장) 아이템이라 모루로 그대로 장비에 적용 가능. Tinkerer(54칸)/Alchemist(27칸) 모두 책을 실제로 드래그해서 넣는 방식이며, 확정하지 않고 창을 닫으면 넣었던 책을 자동으로 돌려줌
- `/강화`는 AE 스타일 개편 이전의 원래 +N강 단일 시스템으로 되돌림(Tinkerer/Alchemist 미포함)
- `/일일퀘스트`, `/주간퀘스트` — 기존 `/퀘스트`를 DailyQuest UI 팩 기반으로 완전히 재설계해 분리. 난이도(쉬움/보통/어려움)별 퀘스트 게시판, 리더보드(총 완료 퀘스트 수 기준 TOP 10, 포디움 표시), 뱃지 화면으로 구성
- 경매장(`/경매`)을 v0id AuctionHouse 2.0 팩 기반으로 전면 개편 — 입찰 금액을 직접 조정할 수 있는 입찰 GUI, 즉시구매 확인 GUI, 내 경매 목록/취소 GUI, 카테고리(무기/도구/방어구/블록/기타)·정렬 필터 추가. 기존 플레이어상점(`/거래소`)은 완전히 제거되고 경매장으로 대체됨
- `/경매 내경매`에서 아직 입찰이 없는 내 경매는 취소하고 아이템을 돌려받을 수 있음(입찰이 붙은 뒤에는 취소 불가)
- 우편함(`DailyRewards` 팩 기반)을 아이템이 바로 보이지 않는 "봉인된 상자" 방식으로 변경 — 우클릭하면 미리보기 GUI로 내용물만 확인 가능, 좌클릭해야 실제로 수령됨
- 자동줍기권/자동심기권 신설 — `/자동줍기권 지급 <횟수>`, `/자동심기권 지급 <횟수>`(관리자 전용, 횟수에 -1 입력 시 무제한 아이템)로만 지급되는 아이템. 아이콘은 각각 `roll_exp.png`/`roll_level.png`이고, 아이템 1개당 지정한 충전량(예: 1000회)이 고정으로 내장되어 있어(로어에 "1개당 충전량: 1,000회" 식으로 표시) 같은 충전량끼리는 인벤토리에서 자연스럽게 스택됨. 우클릭하면 보유한 개수와 상관없이 딱 1개만 소모해 그 충전량만큼 충전, 쉬프트+우클릭하면 들고 있는 스택 전체(4개든 10개든)를 한 번에 소모해서 한꺼번에 충전됨. 충전된 잔여 횟수만큼 작물 수확 시 자동으로 인벤토리에 줍거나(자동줍기) 수확 즉시 자동으로 재파종(자동심기)됨. 잔여 횟수는 바닐라 레벨 숫자 자리에 그대로 표시(원래 레벨은 저장해뒀다가 횟수가 0이 되면 복원). 사이드바 스코어보드에도 자동줍기/자동심기 잔여 횟수 표시
- 작물/광물/낚시/목축 가방 시스템 신설(`/가방`) — 모든 플레이어가 4종 가방(작물/광물/낚시/목축)을 기본 18칸씩 보유. 인벤토리가 완전히 꽉 찼을 때만 해당 카테고리 아이템이 자동으로 가방에 들어감(줍기 자체는 그대로 진행되고, 정말 넣을 공간이 없을 때만 가방으로 우회). 작물=CustomCrops+바닐라 작물, 광물=바닐라 광물(원석/광석 블록 포함, 실크터치 대응), 낚시=낚시 확장팩 물고기+바닐라 생선, 목축=소/돼지/양/닭/토끼 고기류+가죽·깃털·달걀·우유 등. `ticket_chest.png`(가방 확장권)을 우클릭하면 4종 중 어느 가방을 늘릴지 선택하는 창이 뜨고, 선택하면 그 가방이 1칸 확장(티켓 1개 소모). 가방 한 종류당 최대 54칸(더블 상자 크기)까지 확장 가능. `/가방확장권 지급 <개수>`(관리자 전용)로 확장권 아이템을 직접 지급
- CustomCrops(작물) 아이템 이름을 한글로 번역 — ItemsAdder `customcrops` 네임스페이스에 한글 딕셔너리(`_dictionaries/ko.yml`) 추가, 전역 `dictionaries-lang`을 `en`→`ko`로 변경(다른 네임스페이스는 딕셔너리를 쓰지 않아 영향 없음). **적용하려면 ItemsAdder 리로드 또는 서버 재시작 필요**

### 수정된 사항
- 경매장 메인 화면의 `gui-background-offset`를 조정해도 배경 위치가 좌우로 안 움직이던 버그 수정 — 기본값이 실수로 세로 정렬용 값(24)이었던 것을 다른 모든 화면과 동일한 가로 오프셋 기본값(-8)으로 수정
- 일일 퀘스트 보드가 텅 비어 보이던 버그 수정 — 퀘스트 풀 구조를 바꾼 뒤에도 예전에 뽑혔던 퀘스트 id가 남아있어 더 이상 존재하지 않는 퀘스트를 참조하고 있었음(더 이상 유효하지 않은 id면 자동으로 다시 뽑도록 처리)
- 주간 퀘스트 개수를 설정에서 늘려도(3→4개) 이미 뽑은 적이 있는 플레이어에게는 반영되지 않던 버그 수정
- 가방 확인 창(`/가방`)·가방 확장 선택 창의 아이콘 위치를 10/12/14/16번 칸으로 조정
- **버그 수정**: 서버를 껐다 켜면 성장 중이던 작물(밀/당근/감자 등)의 성장 타이머가 복원되지 않던 문제 — 원인은 `YeowoolLife`가 작물/묘목 등의 DB 저장 작업을 처리하는 백그라운드 스레드 2개를 데몬 스레드로 띄워두고, 서버 종료 시(`onDisable`) `executor.shutdown()`만 호출하고 그 작업들이 실제로 끝날 때까지 기다리지 않았던 것. 데몬 스레드는 JVM이 종료되는 순간 진행 중이던 작업까지 강제로 끊겨버려서, 서버를 끄기 직전에 심어졌거나 마침 수확된 작물의 DB 기록이 저장 도중 유실될 수 있었음(유실되면 그 작물은 다음 시작 때 복원할 기록 자체가 없어서 타이머가 영영 멈춘 것처럼 보임). `onDisable`에서 `executor.shutdown()` 이후 최대 10초까지 `awaitTermination`으로 실제 저장이 끝나길 기다리도록 수정 — 작물/묘목 타이머뿐 아니라 이 스레드풀을 같이 쓰는 직업 진행도 저장 등도 함께 안전해짐

## 2026-08-27

### 추가된 기능
- `/상점로테이션설정 <상점ID> <간격분> <슬롯...>` — 관리자 상점(`/상점생성`)에도 "한정 판매"(로테이션) 기능 추가
- `/상점로테이션추가 <상점ID>` — 손에 든 아이템을 상점의 한정 판매 풀에 추가
- `/상점로테이션제거 <상점ID>` — 상점의 로테이션 설정/풀 초기화
- `/상점가져오기 <원본상점ID> <대상상점ID>` — 한 상점의 아이템을 다른 상점으로 일괄 복사(상점 아이템 일괄 등록 도구)
- `/상점` (인자 없음) 실행 시 열리는 위치를 `config.yml`의 `npc-shop.default-open`으로 지정 가능
- 메인 상점 메뉴에 상점이 12개 이상 등록되면 "상점 갯수가 12개 이상이면 더이상 상점을 추가할 수 없습니다." 경고 메시지 출력 (`/상점생성`, 서버 시작 시 둘 다 체크)
- `config.yml`으로 정의된 상점도 `/상점수정`으로 직접 편집 가능하도록 변경 (기존엔 관리자 생성 상점만 편집 가능했음)
- `/상점아이템설정`에 5번째 선택 인자 `[엄격매칭:true|false]` 추가 — 강화/이름/로어가 붙은 아이템이 원본과 같은 가격에 판매되는 것을 막는 옵션(악용 방지)
- `/캐시지급 <닉네임> <금액>` (YeowoolAdmin) — 관리자가 캐시를 즉시 지급/차감(음수 입력 시 차감, 잔액 부족하면 실패 메시지). 콘솔에서도 실행 가능해서 추후 결제 대행사 웹훅을 콘솔 명령으로 연동할 캐시 충전 경로로 그대로 사용 가능
- 거래소(`/거래소 등록`)·경매장(`/경매 등록`)에 캐시 화폐 지원 추가 — 등록 시 마지막에 `[온|캐시]`를 생략 가능한 선택 인자로 추가(생략 시 온), 구매/입찰/즉시구매/수수료 정산이 모두 등록된 화폐 기준으로 처리되고 GUI 표시 문구도 화폐에 맞게 바뀜
- `/경고` 명령어를 지급/회수/기록 체계로 전면 개편
  - `/경고 지급 <닉네임> <사유> <경고 수> [만료기간]` — N회 경고 지급. 마지막에 `7d`, `24h`, `영구` 같은 만료기간을 선택적으로 붙일 수 있고, 생략하면 영구(운영진이 직접 만료 시점을 정할 수 있음)
  - `/경고 회수 <닉네임> <사유> <경고 수>` — N회 경고 회수(현재 누적보다 많이 회수 요청하면 거부)
  - `/경고 기록 <닉네임>` — 지급/회수 내역을 시간순으로 모두 조회(각 항목에 +/- 경고 수와 사유, 집행자, 만료 시점 표시), 상단에 현재 누적(만료된 건 제외) 경고 수 표시
  - 매 지급/회수가 각각 하나의 영구 기록으로 DB에 남는 방식이라(단일 누적 카운터를 덮어쓰지 않음) 기존 `/제재기록`에서도 그대로 조회 가능
  - 경고를 받은/회수받은 온라인 대상에게 보이는 알림을 2줄 형식으로 변경: "[경고] 경고 N회가 지급되었습니다." / "사유: ..." (회수 시 "회수되었습니다."로 문구만 달라짐)
  - 누적 경고가 `config.yml`(`moderation.warning.auto-ban-threshold`, 기본 10) 이상이 되면 단순 퇴장이 아니라 실제 정지(BAN)를 기록 — 정지 기간은 `auto-ban-duration-minutes`(기본 1440분=24시간, 0 이하면 영구)로 설정 가능. 온라인 상태면 즉시 접속을 끊고, 오프라인 상태여도 정지 자체는 바로 기록되므로 다음 접속 시 기존 `/정지`와 동일한 로그인 차단 로직에 걸려 재접속이 막힘(이전엔 지급 시점에 온라인일 때만 1회성 퇴장만 했고, 오프라인 대상은 다음 접속 시 아무 제재가 없었음 — 이번에 같이 해결)
- 부정거래 탐지(`AntiExploitListener`)에 캐시 전용 임계치 추가(`anti-exploit.cash-alert-threshold`, 기본 50,000) — 기존엔 온/은행/캐시가 같은 임계치(`balance-alert-threshold`, 기본 1,000,000)를 공유해서 소액 단위로 거래되는 캐시의 이상 거래를 놓치기 쉬웠음
- 부정거래 탐지에 "구조화(structuring)" 탐지 추가 — 단일 거래 임계치 미만인 금액을 짧은 시간(`anti-exploit.structuring-window-seconds`, 기본 300초) 안에 반복 획득해 합계가 임계치를 넘기면 별도로 경고
- `/토지 관리 목록`을 채팅 텍스트 10줄 출력 대신 페이지네이션 GUI(`LandListGui`)로 변경 — 청크 수 내림차순 정렬, 클릭하면 삭제/이전 명령어를 안내(원클릭 삭제/이전은 되돌릴 수 없어서 의도적으로 넣지 않음)
- `/친구 목록`을 채팅 한 줄 콤마 나열 대신 페이지네이션 GUI(`FriendListGui`)로 변경 — 온라인 우선 정렬, 플레이어 헤드 아이콘, 클릭하면 바로 친구 삭제(친구 추가/삭제는 되돌리기 쉬운 가벼운 동작이라 원클릭으로 처리)
- `/칭호북 생성|제거|목록` (YeowoolCommunity) 추가 — 기존 `/칭호생성`·`/칭호삭제`·`/칭호 지급`은 그대로 유지됨
  - `/칭호북 생성 <칭호 표시 텍스트>` — 표시 텍스트 하나만 입력하면 id를 자동으로 만들어 칭호 생성(예: `<red>[STAFF]</red>` → id `staff`)
  - `/칭호북 제거 <id>` — 생성된 칭호 삭제(`/칭호삭제`와 동일하게 config.yml에 정의된 칭호는 명령어로 삭제 불가)
  - `/칭호북 목록` — 칭호 목록 GUI가 열리고, 칭호를 클릭하면 온라인 플레이어 목록이 뜨고 그 중 한 명을 클릭하면 즉시 지급됨(오프라인 대상 지급은 기존 `/칭호 지급 <닉네임> <id>` 사용)
- 사이드바 스코어보드에 `<channel>` 토큰 추가 — 기본 템플릿에 "채팅: 전체/지역/마을" 줄이 새로 표시됨 (플레이어가 `/채널`로 설정한 현재 채팅 채널을 그대로 보여줌). 이미 설치된 서버는 `config.yml`의 `scoreboard.lines`가 이미 존재해 자동으로 줄이 추가되지 않으므로, 보고 싶으면 그 목록에 `"<gray>채팅: <yellow><channel></yellow></gray>"` 줄을 직접 추가해야 함
- `yeowool-admin`·`yeowool-teleport`·`yeowool-analytics` 3개 모듈에 없던 `messages.yml`을 새로 만들어 모든 채팅 메시지를 이관 (기존엔 관리자/모더레이션/텔레포트/통계 명령어 전부 한글이 코드에 박혀 있어서 문구를 바꾸려면 재빌드해야 했음). GUI 아이템 이름/로어/제목은 원래 방식대로 코드에 남겨둠 (yeowool-market과 동일한 경계)
- `YeowoolProxy` 대기열(`/서버 <이름>`으로 가득 찬 서버에 접속 시도할 때)이 등록 시점에만 순번을 알려주던 것을, 앞사람이 서버에 들어가거나 프록시에서 완전히 나가면 남은 사람들에게 "대기 순번이 N번으로 당겨졌습니다"를 다시 알려주도록 변경. 대기 중에 프록시 자체에서 나가버린 사람이 대기열에 계속 남아있던 문제도 같이 고침
- 서버 시작 시 `/여울도움말`(`help.categories`)에 등록은 됐는데 언급이 안 된 명령어가 있으면 콘솔에 자동으로 경고하도록 함(`HelpIndexChecker`) — 오늘 실제로 이 검사로 기존에 누락돼 있던 명령어 14개(`/내정보`, `/내캐시`, `/랭크아이콘`, `/마을랭킹`, `/상점페이지추가`, `/상점페이지제거`, `/수표`, `/유료수표`, `/시즌랭킹`, `/여울통계`, `/여울코어`, `/출석보상설정`, `/출석체크`, `/퀘스트`, `/한글닉네임설정권`)를 찾아서 같이 채워 넣음. 하위 명령어(예: `/경고`의 지급/회수/기록)까지는 plugin.yml만으로는 구분이 안 돼서 잡아내지 못하니, 새 하위 명령어를 추가할 땐 여전히 직접 챙겨야 함
- `/여울도움말`에서 관리자용 카테고리("운영(관리자)")를 뺐음 — 기본 카테고리 목록에 안 뜨고, OP가 아니면 `/여울도움말 운영(관리자)`로 직접 열어봐도 거부됨. `config.yml`의 `help.admin-only-categories`에 카테고리 이름을 추가하면 다른 카테고리도 같은 방식으로 숨길 수 있음(카테고리 자체는 `help.categories`에 그대로 있어야 `HelpIndexChecker`가 그 안에 적힌 관리자 명령어들을 계속 "문서화됨"으로 인식함)

### 수정된 사항
- 상점 메인 메뉴 "뒤로가기" 버튼을 `home_button` 커스텀 아이콘으로 교체하고 5번째 칸(슬롯 4)에 배치, 배경 이미지를 가리지 않도록 처리
- `/상점수정` 편집기에서도 배경 이미지(`shop_item_display`)가 보이도록 하여 아이템을 어느 위치에 놓아야 할지 알 수 있게 함
- `shop_gui_confirmation` 화면(구매/판매 수량 선택 창)의 버튼들도 배경 이미지 뒤에 숨겨지도록 투명 처리
- 구매/판매 수량 선택 화면 슬롯 레이아웃 재배치 (감소 18/19/20, 아이템 22, 증가 24/25/26, 최대(64) 49, 취소 38·39, 확정 41·42)
- 페이지 이동 버튼 위치를 이전 페이지 45번, 다음 페이지 53번 슬롯으로 고정
- NO/YES/최대(64) 버튼을 투명 아이콘(`shop_empty_slot`)으로 교체해 텍스트만 보이도록 하고, NO/YES는 2칸을 모두 차지하도록 복원(중복 텍스처 노출 문제 해결)
- `/상점제거`가 `config.yml`에 정의된 상점(예: 농작물/잡화 상점)도 제거할 수 있도록 확장
- 메인 메뉴에 상점이 추가되는 칸을 12, 13, 14 / 21, 22, 23 / 30, 31, 32 / 39, 40, 41로 고정
- 상점 아이템 로어의 색상 코드가 `&r`/`&7`/`&f` 형태로 그대로 노출되던 버그 수정 (`legacySection()` → `legacyAmpersand()` 파싱 방식 변경)
- `config.yml`에서 예제 상점(농작물/잡화)을 삭제해도 서버 재시작 시 자동으로 재생성되던 버그 수정 — 원인은 `ConfigMerger`가 패키지 기본값에 남아있던 예제 상점을 "누락된 키"로 판단해 되살렸던 것이라, 기본 `config.yml`에서 예제 상점 자체를 제거함
- 상점 구매 시 인벤토리 공간 체크를 기존 방식(완전히 빈 슬롯 유무만 확인)에서 기존 부분 스택에 들어갈 수 있는 공간까지 정확히 계산하도록 개선
- `/칭호생성`·`/칭호삭제`의 사용법 안내 메시지가 실제로 존재하지 않는 값을 `<id>`/`<표시>` 형태로 참조하고 있어, 인자를 빠뜨리고 명령어를 치면 MiniMessage가 그 태그를 해석하지 못해 오류가 나던 버그 수정(다른 안내 메시지처럼 `[id]`/`[표시]`로 변경)
- **버그 수정**: 콘솔에서 `/경고 회수`로 누적 경고를 임계치 미만으로 줄여도 서버 접속이 계속 막히던 문제 — 원인은 경고 누적으로 자동 정지(BAN)를 걸 때 그 정지 기록 자체는 별도로 남는데, 회수는 경고 점수만 줄일 뿐 이미 걸린 정지는 그대로 두고 있었기 때문. 이제 회수 후 누적이 임계치 미만이 되면, 그 자동 정지가 남아 있는지 확인해서(관리자가 직접 건 `/정지`는 건드리지 않고, 경고 누적으로 자동으로 걸린 정지만 식별) 자동으로 해제함
- `/여울도움말`(`config.yml`의 `help.categories`)에 이번에 추가된 명령어들이 빠져 있던 것을 보완 — `/캐시지급`, `/칭호북`, `/상점생성`류 관리자 상점 명령어, `/칭호생성`·`/칭호삭제`·`/칭호 지급`·`/칭호 회수`를 목록에 추가하고 `/경고` 항목도 새 지급/회수/기록 형태로 갱신 (이 도움말 색인은 자동 수집이 아니라 수동으로 관리되는 목록이라, 앞으로도 새 명령어를 추가할 때 같이 챙겨야 함 — 다만 위 `HelpIndexChecker`가 이제 이런 누락을 시작 시점에 자동으로 잡아줌)
- 경고 자동정지 버그와 같은 "파생 기록을 되돌릴 때 안 챙기는" 패턴이 다른 곳에도 있는지 점검 — 시즌 종료 보상 지급(`SeasonManager`)과 쿠폰 사용 기록(`CouponManager`)을 직접 확인한 결과, 둘 다 애초에 "되돌리는" 동작 자체가 없거나(시즌 종료는 일회성이라 undo 개념이 없음) 이미 파생 기록까지 같이 정리하고 있어서(쿠폰 삭제 시 그 쿠폰의 사용 기록도 함께 삭제됨) 같은 버그는 발견되지 않음

### 최적화
- 로테이션 상점마다 개별 타이머를 돌리던 것을 서버 전체에서 공유하는 타이머 1개로 통합 (`ShopRotationManager.tick()`이 매분 각 상점의 간격을 체크)
- 구매/판매 수량 선택 GUI(`AmountSelectionGui`)가 수량 버튼을 클릭할 때마다 아이템을 매번 새로 조회(ItemsAdder 커스텀 아이템 재조회 포함)하던 것을, 창을 열 때 한 번만 조회한 뒤 복제해서 재사용하도록 변경 — 클릭 반응성 개선
- 경매장(`AuctionManager`)이 목록을 보여주거나 만료를 검사할 때마다 전체 경매를 매번 새로 정렬하던 것을, 만료 시각 기준으로 미리 정렬된 인덱스를 유지하도록 변경 — 특히 30초마다 도는 만료 검사가 이제 실제로 만료된 것만 확인하고 나머지 살아있는 경매는 건드리지 않음

## 2026-08-28

### 추가된 기능
- `/길라잡이` (YeowoolCore) — 신규 플레이어를 위한 서버 시스템 안내 GUI 추가. 첫 접속 시 자동으로 한 번 열리고(재접속해도 다시 뜨지 않음), 이후엔 `/길라잡이`로 언제든 다시 볼 수 있음. 경제/토지/생활/직업/상점·거래/순간이동/채팅/커뮤니티 카테고리를 아이콘으로 보여주고, 클릭하면 짧은 설명과 관련 명령어 목록을 채팅으로 안내(명령어 목록은 `/여울도움말`의 같은 카테고리 내용을 그대로 재사용해서 둘이 따로 관리되지 않음). `config.yml`의 `guide.categories`에서 카테고리별 아이콘/설명을 직접 수정 가능
- `/길라잡이`에 운영진이 직접 관리하는 번호 매긴 "시작 미션" 기능 추가 (`yeowool.core.guide.manage` 권한)
  - `/길라잡이 추가 <번호> <제목> <해야할 행동>` — 미션 추가(제목은 띄어쓰기 없이 한 단어, 해야할 행동은 남은 단어 전부를 사용 — `/칭호생성 <id> <표시>`와 같은 방식). 이미 있는 번호면 거부
  - `/길라잡이 수정 <번호> <제목> <해야할 행동>` — 기존 미션 수정. 없는 번호면 거부
  - `/길라잡이 제거 <번호>` — 미션 삭제
  - `/길라잡이 목록` — 등록된 미션을 GUI로 확인(클릭하면 수정/제거 명령어를 안내, 원클릭 삭제는 아님)
  - 미션이 하나라도 있으면 일반 플레이어의 `/길라잡이` GUI에도 "시작 미션" 아이콘이 추가로 나타나서 번호순으로 확인 가능
- **직업 시스템(yeowool-life) 전면 개편** — 목수/농부/어부/요리사/연금술사 5개 → 연금술사/대장장이/건축가/도굴꾼/인챈터/농부/어부/사냥꾼/광부/목수 10개로 교체. 아이콘은 새로 설치한 ItemsAdder `medival_jobs` 팩(`medival_jobs:medival_jobs_<직업>`)을 우선 사용하고, ItemsAdder가 없거나 아이템을 못 찾으면 기존처럼 바닐라 재질로 자동 대체됨(`JobDefinition`에 `custom-icon` 필드 추가, `JobIconFactory`가 `yeowool-market`의 `ItemResolver`와 같은 방식으로 처리)
  - 요리사는 완전히 제거(관련 리스너 삭제), 기존 목수는 "건축가"로 대체(나무 블록 설치 경험치는 건축가가 이어받음)하고, 목수라는 이름은 원목 채벌(로깅)에 재활용 — 기존 목수/요리사의 DB 진행도는 마이그레이션하지 않고 새로 시작(사용자가 "완전 처음부터 다시 시작" 선택)
  - 새 직업 5개에 새 활동 감지 리스너 추가: 광부(광석 채굴, 기존 `MiningListener`와 동일 조건), 목수(원목 채벌, 기존 `LoggingListener`와 동일 조건), 사냥꾼(적대적 몬스터만 처치 — 기존 `HuntingListener`는 동물도 포함이라 직업은 더 좁게 잡음), 대장장이(화로/용광로에서 주괴 제련 완료 시), 인챈터(마법부여 테이블 사용 시), 도굴꾼(구조물 상자를 처음 열어 전리품이 생성되는 순간 — `LootGenerateEvent`)
  - `/직업 목록`·`/직업 선택` GUI를 27칸→45칸으로 넓혀 10개 직업이 여유 있게 들어가도록 조정
  - 참고: 도굴꾼/대장장이/인챈터/사냥꾼은 기존에 추적하던 시스템이 없어서 새로 설계한 조건이라, 실제로 써보고 감지 조건(예: 광부처럼 보너스 드랍이 적용되는지, 도굴꾼이 몹 처치/낚시 전리품과 안 겹치는지)이 의도와 다르면 바로 조정 가능
- `/직업 스킬` 화면에 `medival_jobs` 팩의 직업별 전체 배경 이미지(양피지 패널 + 초상화 + 직업명 배너)를 적용 — 처음엔 작은 아이콘만 가져다 썼는데, 팩에 있던 큰 배경 이미지를 놓치고 있었음. `/직업 목록`·`/직업 선택`은 여러 직업을 한 화면에 동시에 보여주는 화면이라 배경 하나만 못 씀 → 그대로 두고, 한 번에 하나의 직업만 보여주는 `/직업 스킬` 화면에만 적용(NPC상점/출석체크가 쓰는 것과 같은 폰트이미지 배경 합성 방식, `JobBackgroundImages`). 배경이 좌우로 살짝 어긋나 보이면 `config.yml`의 `jobs.gui-background-offset`(기본 -46)을 조절하면 됨 — 정확한 값은 인게임에서 직접 보면서 맞춰야 함
- `/직업 선택`에서 아이콘을 클릭하면 바로 직업이 정해지던 것을, 그 직업의 배경화면이 있는 미리보기 화면(`JobSelectPreviewGui`)이 먼저 뜨고 거기서 확정해야 실제로 정해지도록 변경 — 마우스를 올리면 배경이 바로 바뀌는 방식은 Bukkit에 슬롯 호버 이벤트 자체가 없어서 만들 수 없어, 클릭 후 미리보고 확정하는 방식으로 대신함. 확인은 41번 슬롯(YES, 초록색), 거절은 37번 슬롯(NO, 빨간색)

### 수정된 사항
- **버그 수정**: 경고에 만료기간을 걸어둔 경우, 그 경고가 만료돼 누적이 임계치 밑으로 떨어져도 이미 걸린 자동 정지는 그대로 남아있던 문제 — 자동 정지는 자기 자신의 별도 만료 기간(기본 24시간)을 갖고 있어서, 경고 포인트가 그보다 먼저 만료되면 누적이 0이 되고, 그러면 `/경고 회수`가 "회수할 경고가 부족합니다"로 거부되면서 정작 자동 정지 해제 로직에는 도달하지 못했음(실제로 이 문제로 한 플레이어가 접속이 막혔던 걸 확인). 이제 회수가 그렇게 거부되는 경우에도 자동 정지 여부를 확인해서 풀어주고, 추가로 아무도 회수 명령을 치지 않아도 스스로 정리되도록 활성 자동정지를 주기적으로(기본 5분, `moderation.warning.auto-ban-review-interval-ticks`) 재검사해 임계치 밑으로 떨어진 건 자동으로 해제하는 백그라운드 작업(`WarnAutoBanReviewTask`)을 추가함
- `/여울도움말`의 "커뮤니티" 카테고리에 있던 `/칭호`·`/랭크아이콘`이 플레이어용 서브커맨드와 관리자 전용 서브커맨드(지급/회수, 부여/제거)를 한 줄에 같이 보여주고 있어서, 일반 플레이어에게도 관리자 서브커맨드가 텍스트로 노출되던 것을 수정 — `/토지`가 이미 하던 방식대로 관리자 서브커맨드는 "운영(관리자)" 카테고리로 따로 옮김(실제 권한 체크는 원래도 명령어 자체에서 하고 있어서 보안 문제는 아니었음)

### 최적화
- `WarnAutoBanReviewTask`(경고 자동정지 주기 재검사)가 활성 자동정지 하나마다 누적 경고를 따로 조회하던 것(N+1 조회)을, 모든 대상의 누적을 한 번의 쿼리로 묶어 가져오도록 변경(`sumWarningPointsForTargets`)

## 2026-08-29

### 수정된 사항
- **버그 수정(서버 설정, 플러그인 코드 아님)**: 일반 유저(비OP)가 `/한글닉네임설정권`·`/수표` 아이템을 정확히 손에 들고 우클릭해도 아무 반응이 없던 문제 — 플러그인 리스너 자체는 정상이었고(디버그 로그로 직접 확인), 원인은 `server.properties`의 바닐라 `spawn-protection=16` 설정이었음. 스폰 반경 16블록 안에서는 비OP 플레이어의 블록 우클릭을 서버 코어가 플러그인 단계 이전에 강제로 취소시켜서, `PlayerInteractEvent`가 아예 취소된 채로 들어와 있었음(OP는 스폰 보호를 항상 우회하기 때문에 OP로는 재현이 안 됐음). 이 서버는 `YeowoolLand`로 자체 토지보호를 이미 하고 있어서 바닐라 스폰 보호가 필요 없기도 하고, 오히려 스폰 근처 정상 기능을 막는 부작용이 있었으므로 `spawn-protection=0`으로 변경. **서버 재시작이 있어야 적용됨** (관련 플러그인: 없음 — 서버 자체 설정 파일 수정)

## 2026-08-30

### 추가된 기능
- **낚시 콘텐츠 확장(yeowool-life)** — ItemsAdder "Fishing Expansion" 리소스팩(`fishing_expansion` 네임스페이스)을 서버에 추가하고(관련 플러그인: `ItemsAdder`, 콘텐츠 폴더 `plugins/ItemsAdder/contents/fishing_expansion` 신규 생성), 여울라이프의 기존 `/도감` 낚시 등급 시스템(일반/희귀/특수/전설)을 이 팩의 물고기 60종으로 전면 교체함. 네더/용암 테마 물고기(애쉬마우, 헬퍼퍼, 몰튼조 등)는 별도 차원 판정 없이 전설 등급에 그대로 포함(사용자 확인 완료). 각 물고기의 `/도감` 아이콘은 이 팩의 실제 텍스처를 사용(`FishSpecies.customIconId`, `JobDefinition.customIconId`와 동일한 ItemsAdder 우선·미설치 시 대체 방식)
- **낚싯대/미끼 등급 시스템 신규 추가** — 같은 팩의 등급별 낚싯대 5종(구리/철/황금/다이아몬드/네더라이트, `config.yml`의 `fishing.rods`)과 미끼 10종(`fishing.baits`)을 실제 게임플레이에 연결함. 메인핸드에 낚싯대를 들고 낚시하면 "일반"을 제외한 모든 등급의 확률 가중치가 낚싯대별 배율만큼 올라가고, 오프핸드에 미끼를 들고 있으면 잡을 때마다 1개 소모되며 그 배율이 낚싯대 배율과 곱해져 함께 적용됨(`FishingListener.rollRarity`)
- 물고기 60종에 한글 설명(`FishSpecies.description`) 추가 — `/도감`의 물고기 항목과 실제로 낚였을 때 인벤토리에 들어오는 아이템 양쪽 모두에 표시됨
- `/도감`의 물고기 도감(`FishCatalogGui`)에 fishing_expansion 팩의 `fish_codex` 전용 배경 이미지 적용(`FishBackgroundImages`, 잡·상점 배경과 동일한 폰트이미지 합성 방식). 배경이 좌우로 어긋나 보이면 `config.yml`의 `fishing.gui-background-offset`(기본 -46)을 조절
- **낚싯대·미끼를 `/관리자아이템`(yeowool-admin) 카탈로그에 추가** — 지금까지 ItemsAdder 자체 명령어(`/iagive`)로만 꺼낼 수 있던(=사실상 OP 전용이던) 도구였는데, `/관리자아이템`으로 다른 관리자도 명령어 없이 GUI에서 바로 꺼낼 수 있게 함. 이를 위해 `/관리자아이템` 카탈로그(`CatalogEntry`)가 ItemsAdder 커스텀 아이템도 지원하도록 확장(`custom-icon` 필드, `yeowool-market`의 `ItemResolver`와 동일한 우선·대체 패턴). 관련 플러그인: `YeowoolAdmin`(빌드 의존성에 ItemsAdder API 추가 필요해서 `build.gradle.kts`도 함께 수정됨)
- **낚시 콘텐츠 확장 2탄 — CustomFishing 플러그인(설치 안 함) 대신 그 기능 일부를 직접 구현**:
  - **물고기 크기(사이즈) 기록** — 잡을 때마다 그 물고기 종의 `min-size-cm`~`max-size-cm` 범위 안에서 랜덤 크기가 정해지고, 잡은 아이템 설명과 채팅 메시지에 표시됨. 개인 최고 기록은 `PlayerData`에 새로 추가한 `recordMaxStatistic`(기존 `addStatistic`처럼 그냥 더하는 게 아니라 "더 크면만 갱신"하는 버전, yeowool-core)으로 저장되고 `/도감`에도 표시됨. 신기록을 세우면 별도 메시지도 뜸
  - **낚시 대기시간 커스터마이징** — 바닐라 낚시 대기시간 대신 `fishing.wait-time`(기본 5~30초)을 강제 적용(`FishHook#setMinWaitTime/setMaxWaitTime`, Paper API)
  - **낚시 타이밍 미니게임** (아래에서 실제 타이밍 바 방식으로 다시 구현됨 — 처음엔 반응속도 기반으로 만들었다가 사용자가 원한 게 아니라고 해서 교체)
  - **낚시 대회** — 매일 오후 6시(`fishing.competition.start-hour`)에 자동 시작해서 1시간(`duration-minutes`) 동안 진행, 대회 중 낚은 물고기 중 가장 큰 걸 기준으로 순위를 매겨 종료 시 1~3위를 서버 전체에 발표하고 온 보상(`fishing.competition.rewards`, 기본 5만/3만/1.5만) 지급. 현황은 `/낚시대회`로 확인 가능. 서버를 매일 껐다 켜도 다음 시작 시각을 다시 계산해서 스스로 재예약함
  - 낚시 가방(별도 저장공간)은 이번엔 빠짐 — 나중에 "가방" 시스템을 새로 만들 때 같이 연동하기로 함(사용자 확인)
- **미끼 장착 방식 변경** — 오프핸드에 들고 있어야만 적용되던 것을, 손에 들고 우클릭하면 "장착"되는 방식으로 변경(`BaitEquipListener`). 최대 64개까지 장착 가능하고, 장착한 개수는 로그아웃해도 유지됨(`PlayerData`의 기존 설정/통계 맵을 그대로 재사용 — 새 컬럼 없이 저장). 이미 다른 미끼가 남아있으면 그걸 다 쓸 때까지 새 미끼로 교체 불가(실수로 버려지는 일 방지)
- **낚시 타이밍 미니게임을 진짜 타이밍 바 방식으로 재구현** — 처음엔 "입질 후 릴 감기까지 걸린 반응속도"로 보너스만 주는 방식으로 만들었는데, 사용자가 원한 건 입질 순간 실제로 화면에 뜨는 미니게임이었음(용암/허공 낚시·어시장·토템은 불필요하다고 확인되어 전부 제거함 — 아래 "제거된 기능" 참고). 이제 입질이 오면 보스바가 뜨고 `fishing.minigame.total-duration-ms`(기본 2.5초) 동안 차오르며, 그 안 무작위 위치에 생기는 "스위트 스팟" 구간에 들어왔을 때(보스바가 초록색으로 바뀜) 우클릭해서 릴을 감으면 완벽한 타이밍, 그 앞뒤 여유 구간(`good-buffer-ms`)이면 좋은 타이밍 — 각각 등급 확률·크기에 보너스가 붙음. 그보다 멀리 벗어나서 감으면 `miss-fail-chance-percent`(기본 40%) 확률로 아예 물고기를 놓침("타이밍이 맞지 않아 물고기를 놓쳤습니다..."). Bukkit API로는 릴 감기 자체를 지연시킬 방법이 없어서(NMS 없이는 불가), 입질~실제 릴감기 사이의 시간 자체를 보스바로 시각화하는 방식으로 구현함

### 제거된 기능
- 위 CustomFishing 기반 확장 중 **용암/허공 낚시**, **어시장(`/어시장`)**, **낚시 토템(`/낚시토템`)** 은 필요 없다는 사용자 확인에 따라 코드·명령어·설정을 전부 되돌림(`FishingTotemManager`/`Item`/`Listener`/`Command`, `FishMarketGui`/`Command`, `FishItemData` 파일 삭제, `plugin.yml`·`config.yml`·`/여울도움말` 항목 원복)

### 수정된 사항 (2)
- **버그 수정**: 낚시 타이밍 보스바가 시간이 지나도 안 사라지고 계속 남아있던 문제 — 입질이 왔는데 플레이어가 아예 반응하지 않으면(우클릭을 안 하면) 바닐라가 아무 이벤트 없이 조용히 "다음 입질 대기" 상태로 돌아가는데, 그 경우 보스바를 정리해줄 트리거(CAUGHT_FISH 이벤트)가 영원히 안 왔던 것이 원인. 이제 보스바 자신의 반복 작업이 `총 시간 + 1초`가 지나도 아무 처리가 없으면 스스로 정리하도록 함 — 겸사겸사 사용자가 요청한 "실패 후 1초 뒤에 보스바가 사라지는" 동작도 이걸로 구현됨(타이밍이 안 맞아 놓쳤을 때도 보스바가 빨간색으로 1초간 멈춰 있다가 사라짐)
- 물고기를 놓치면 채팅에 "물고기가 도망갔습니다!"가 뜨도록 메시지 문구 변경(`fishing.missed`)
- **미끼 없이는 낚시 자체가 불가능하도록 변경** — 지금까지는 미끼가 없어도 낚시는 되고 보너스만 안 붙었는데, 이제 미끼를 장착하지 않은 상태로 낚싯대를 던지면 아예 캐스팅이 취소되고 "미끼를 장착해야 낚시를 할 수 있습니다!" 메시지가 뜸
- **물고기 "별" 등급 추가** — 새로운 물고기 종류를 따로 만든 게 아니라, 정상적으로 낚은 아무 물고기에나 독립적으로 2%(설정 가능) 확률로 붙는 "반짝이는" 변종으로 구현함(원본 CustomFishing의 실버/골드 스타와 같은 개념 — 확인해보니 새 물고기 종이 아니라 기존 물고기에 붙는 히든 등급이었음). 별 등급이 뜨면 이름이 "★ OO ★"로 바뀌고 크기가 40%(설정 가능) 커지며 전용 메시지가 뜸

### 수정된 사항
- **버그 수정**: 위 낚시 물고기를 60종으로 늘리자마자 `/도감`의 물고기 도감을 열면 콘솔에 `IllegalArgumentException: Size for custom inventory must be a multiple of 9 between 9 and 54 slots (got 63)` 에러가 나면서 GUI가 아예 안 열리던 문제 — `FishCatalogGui`가 물고기 수에 맞춰 인벤토리 크기를 직접 계산했는데(9칸 단위로 올림), 종 수가 늘면서 그 계산값이 바닐라 인벤토리 최대 크기(54칸)를 넘어버렸음. `/친구 목록`(`FriendListGui`)이 이미 쓰던 것과 같은 페이지네이션 방식(한 페이지 45칸, 하단에 이전/닫기/다음 버튼)으로 바꿔서 해결함
- **버그 수정**: 낚시로 "[가재]를 낚았습니다" 같은 메시지가 뜨는데 실제로 인벤토리엔 그냥 익히지 않은 대구/연어(바닐라 낚시 전리품)가 들어오던 문제 — 원래 낚시 시스템이 도감 통계·메시지만 처리하고 실제로 낚인 아이템은 건드리지 않는 "감성 요소"용으로 설계돼 있었는데(물고기 아이콘이 없던 시절엔 문제없었음), 위에서 진짜 물고기 아이템을 추가하면서 이 부분을 놓쳤음. 이제 `FishingListener`가 낚인 아이템 엔티티의 실제 스택을 그 물고기의 아이콘(`FishSpecies.customIconId`)으로 교체해서, 메시지·도감 항목·실제로 받는 아이템이 전부 일치하도록 수정

### 추가된 기능 (2) — 디스코드 연동
- **`yeowool-discord`(파이퍼 플러그인, 이 프로젝트에 새로 추가) + 별도 JS 봇(새 폴더)** 두 부분으로 구성. 처음엔 `yeowool-discord`를 독립 자바(JDA) 프로그램으로 만들었었는데, 사용자가 "yeowool-discord는 플러그인이어야 하고, 실제 디스코드 접속 코드는 JS로 별도 폴더에 새로 만들어달라"고 정정해서 이 구조로 다시 만듦
  - **`yeowool-discord`(파이퍼 플러그인, 게임 서버에 설치)**: 디스코드에 직접 접속하지 않음. `config.yml`에 봇 토큰/길드 ID/채널 ID들/운영진 역할 ID/티켓 카테고리 목록을 적으면, 그 값을 DB(`yw_discord_config` 테이블, 단순 key-value)에 그대로 써둠 — 이게 실제 설정의 유일한 원본이라 관리자는 이 파일 하나만 만지면 됨. `/디스코드리로드` 명령어로 재시작 없이 다시 반영 가능. 그리고 `yw_discord_warn_queue`(디스코드→게임 경고 요청)를 5초마다(설정 가능) 확인해서 실제 `/경고 지급`과 완전히 같은 코드 경로로 처리하는 `DiscordWarnQueuePoller`도 포함
  - **JS 봇 본체(신규 폴더 `yeowool-discord-bot`, 바탕화면에 `YEOWOOL_PLUGIN`과 나란히 생성됨, Node.js/discord.js)**: 실제로 디스코드에 접속하는 프로그램. 게임 서버와 다른 PC에서 `npm install` 후 `npm start`로 실행. 로컬 설정은 MySQL 접속 정보(`.env`)뿐이고, 봇 토큰을 포함한 나머지 설정은 전부 위 `yw_discord_config`에서 읽어옴(30초마다 새로고침)
    - **경고(양방향, 지급·회수 둘 다 지원)**: `yw_punishments`(게임의 `/경고 지급`·`회수`가 쌓는 테이블)를 폴링해서 지정 채널에 자동 로그. 디스코드 `/경고 지급|회수 <닉네임> <점수> <사유>`(운영진 역할만, 서브커맨드로 분리)는 `yw_discord_warn_queue`에 적재만 하고, 실제 반영은 게임 서버의 `DiscordWarnQueuePoller`가 함
    - **공지(게임→디스코드 단방향)**: `/여울관리 공지` 실행 시 `yw_discord_announcements`에 같이 기록되는 걸 폴링해서 채널에 올림
    - **티켓**: `/티켓`을 치면 문의 유형 드롭다운이 뜨고(카테고리 여러 개 지원 — `유저 신고`/`버그 제보`/`문의` 기본 3종, `config.yml`에서 추가 가능), 고른 유형에 맞는 디스코드 카테고리 아래에 신청자+운영진만 보는 채널이 생성됨. "🔒 닫기" 버튼으로 운영진이 닫음. 디스코드 안에서만 동작하고 게임 서버와는 무관
  - 관련 플러그인: `YeowoolCore`(변경 없음), `YeowoolAdmin`(`WarnCommand.AUTOBAN_REASON_PREFIX`를 다른 모듈에서도 쓸 수 있게 public으로 변경, `AdminCommand.announce()`가 공지 큐에 기록), `yeowool-discord`(신규 파이퍼 플러그인)
  - **직접 해주셔야 하는 것**: (1) `yeowool-discord-bot` 폴더(바탕화면)를 봇을 돌릴 PC로 복사 → `npm install` → `.env.example`을 `.env`로 복사해서 MySQL 접속 정보만 채움 → `npm start`. (2) 게임 서버의 `yeowool-discord` 플러그인 `config.yml`에 봇 토큰/채널 ID들/역할 ID/티켓 카테고리를 채우고 서버 재시작(또는 `/디스코드리로드`) — 이게 실제 설정 원본. (3) MySQL 쪽 접속 확인 필요 — 실제로 확인해보니 MySQL은 이미 `0.0.0.0`(모든 인터페이스)에서 리스닝 중이었고 방화벽 3306 인바운드 규칙도 이미 있었음. 봇 PC가 게임 서버와 같은 공유기(같은 LAN)라면 `.env`의 `DB_HOST`에 서버의 **내부 IP**(예: `192.168.x.x`)를 쓰면 됨 — 공인 IP/포트포워딩은 필요 없음(오히려 헤어핀 NAT 미지원 공유기에서는 안 될 수 있음). 완전히 다른 네트워크라면 포트포워딩과 공인 IP가 필요함. (4) 디스코드 개발자 포털 봇 설정에서 "Server Members Intent"를 켜야 함(운영진 역할 확인에 필요, 기본 꺼져있어서 안 켜면 봇이 시작할 때 오류가 남)
- **버그 수정**: 디스코드에서 지급한 경고는 게임에 반영됐지만 회수는 아예 지원되지 않았고, 지급/회수 모두 게임 내 자동 정지 임계치 판단·해제 로직이 빠져있던 문제 — `WarnCommand`에 있던 자동 정지 로직을 `AutoBanEscalation`(신규, yeowool-admin)으로 추출해서 게임 내 `/경고`와 `DiscordWarnQueuePoller` 양쪽이 완전히 같은 코드를 쓰도록 통일함. 디스코드 `/경고` 명령어도 `/경고 지급 ...`/`/경고 회수 ...` 서브커맨드로 나눠서 게임 내 명령어 구조와 맞춤(`yw_discord_warn_queue`에 `action` 컬럼 추가)
- **버그 수정(중요)**: 접속 중인 플레이어의 스코어보드·탭리스트·`/도감` GUI 등이 갑자기 전부 깨지면서 콘솔에 `PlayerData for ... is not loaded; is the player online?` 에러가 계속 쌓이던 문제 — 원인은 `PlayerDataResolver`(관리자 명령어가 오프라인 대상도 처리할 수 있게 해주는 공용 유틸리티)의 경쟁 조건이었음. 관리자가 어떤 플레이어를 대상으로 명령어를 실행할 때 그 플레이어가 오프라인이면 DB에서 비동기로 데이터를 불러온 뒤 처리하고 저장·언로드하는데, 그 짧은 로딩 시간 동안 그 플레이어가 실제로 접속해버리면 로딩이 끝난 뒤 "이미 접속했는지" 재확인 없이 그냥 캐시에서 데이터를 내려버렸음 — 그러면 실제로 접속 중인 플레이어의 데이터가 캐시에서 사라져서 `getOnline()`을 쓰는 모든 기능이 그 플레이어에게만 재접속 전까지 계속 깨졌음. 이제 비동기 로딩이 끝난 시점에 다시 온라인 여부를 확인해서, 그사이 접속했다면 언로드하지 않도록 수정. 겸사겸사 `FishCatalogGui`/`DexCatalogGui`도 `getOnline()`(못 찾으면 예외) 대신 `getIfLoaded()`(못 찾으면 안전하게 "미발견"으로 표시)를 쓰도록 방어적으로 바꿔서, 비슷한 타이밍 문제가 또 생겨도 GUI 자체가 죽지는 않게 함
- **버그 수정**: `/도감` 물고기 도감 배경 이미지 위치가 `config.yml`의 `fishing.gui-background-offset`을 여러 값(-46/-48/-52/-38 등)으로 바꾸고 서버 재시작·`/iareload`·`/iazip`을 다 해봐도 전혀 안 움직이던 문제 — 원인 두 가지였음. (1) `ItemsAdder/contents/fishing_expansion/configs/fishing_expansion.yml`의 `font_images.fish_codex`가 `show_in_gui: true`로 돼 있었는데, 이 값이 켜져 있으면 이미지가 수동 오프셋 글리프(`:offset_N:`)를 무시하는 별도 자동 배치 경로로 렌더링됨 — `false`로 수정. (2) 그것만으론 안 고쳐졌는데, 시험해본 오프셋 값들이 전부 이 팩과 무관한 다른 이미지(medival_jobs, `-46` 관용값)를 기준으로 추측한 값이었음 — 팩 원본의 `DeluxeMenus/gui_menus/fish_codex.yml`의 `menu_title: '&f%img_offset_-8%%img_fish_codex%'`를 다시 확인해서 정확한 값이 **-8**이라는 걸 찾아 `gui-background-offset: -8`로 수정하고 나서야 해결됨
- **`/도감` 물고기 도감(`FishCatalogGui`) 칸 배치 재설계** — 기존 45칸 그리드+하단 이전/닫기/다음 페이지(45/49/53번 칸) 방식을 없애고, `fish_codex` 배경의 실제 "어항 유리" 그림 영역인 10~16 / 19~25 / 28~34번 칸(3줄 × 7칸, 한 페이지 21칸)에만 물고기가 채워지도록 변경 — 그 외 칸은 전부 완전히 비워둠(필러 아이콘조차 없음). 페이지 이동·닫기 버튼은 38~39번(이전 페이지) / 40번(닫기) / 41~42번(다음 페이지) 칸으로 옮기고, 팩 원본 DeluxeMenus 버전과 똑같이 팩 자체의 투명 아이템(`fishing_expansion:invisible_item`)을 써서 배경에 이미 그려진 버튼 그림만 보이도록 함

### 추가된 기능 (3) — 아이템밴/조합밴, 커플 시스템, 강화 시스템, 거래소 배경 이미지
- **아이템밴 / 조합밴** (`YeowoolAdmin`, `yeowool.admin` 권한): 손에 든 아이템 기준으로 등록. `/조합밴 추가|제거|목록`은 그 아이템의 제작(조합)만 막고, 이미 갖고 있는 건 그대로 둠. `/아이템밴 추가|제거|목록`은 더 강한 버전 — 제작뿐 아니라 땅에 떨어진 걸 줍는 것도 막고, 인벤토리 클릭·접속 시점마다(기존 아이템 복제 탐지와 같은 방식) 검사해서 이미 갖고 있는 것도 발견 즉시 자동으로 회수함(회수 시 본인 채팅 안내 + 온라인 관리진 알림 + 로그 기록). `yeowool.admin.super`(총관리진) 보유자는 둘 다 우회 가능(조사/스폰용). DB(`yw_banned_items`) 기반이라 서버를 껐다 켜도 목록이 유지됨
- **커플 시스템** (`YeowoolCommunity`) — 요청하신 대로 정식 결혼 시스템 대신 더 가벼운 커플 개념으로 구현: `/커플 신청|수락|거절|해제|정보 [닉네임]`(신청은 60초 후 만료, 커플은 항상 1:1 배타적이라 이미 커플인 상대에게는 신청 불가). `/커플채팅 [메시지]`로 이름 입력 없이 바로 내 커플 상대에게만 보이는 메시지 전송 가능(음소거 상태면 `/귓속말`처럼 막힘). 커플 상대가 접속하면 알림 뜸(`/친구` 접속 알림과 동일한 방식). DB(`yw_couples`)에 저장되어 서버 재시작에도 유지됨
- **강화 시스템** (`yeowool-enhance`, 신규 플러그인, `/강화` GUI): 무기(검/도끼/활/석궁/삼지창)와 방어구(투구/흉갑/각반/신발)만 강화 가능. 말씀하신 대로 **9강까지는 "일반" 등급, 10강 성공 시 "희귀", 15강 성공 시 "에픽"**으로 등급이 자동 승급되도록 구현했고, 그 뒤로 20강 "전설"/25강 "신화"까지 기본으로 미리 넣어뒀음(`config.yml`의 `enhance.tiers`에 계속 추가 가능, level 오름차순 목록이라 몇 단계든 확장 가능). **등급이 오를수록 성능도 같이 오름** — 무기는 공격력, 방어구는 방어력이 "강화 수치 × 등급별 배율"만큼 실제 Attribute로 붙어서 진짜 전투력에 반영됨(아이템 설명에도 표시됨). 강화마다 온 + 다이아몬드(기본값, config로 변경 가능)가 소모되고, 강화 수치가 오를수록 비용도 올라감. 성공 확률은 등급 승급 시점(9→10, 14→15 등)에 더 낮게 설정해뒀고, **15강 이상부터는 실패 시 등급 하락 또는 아이템 파괴 위험**이 생김(그 미만은 실패해도 그냥 수치 유지) — 네더라이트 주괴(기본값)를 갖고 있으면 그 위험한 실패를 대신 막아주는 보호 아이템으로 자동 소모됨. `/강화`를 치면 손에 든 아이템을 실시간으로 보여주면서 현재 등급/다음 비용/성공 확률/승급 여부를 GUI로 미리 확인하고 바로 강화할 수 있음
- **거래소(`/거래소`) 배경 이미지를 베드락 에디션 스타일로 교체** — 제공해주신 베드락용 리소스팩(`moafarm` 팩)의 `ui_images/exchange.png`(정확히 스크린샷과 동일한 "거래소" 이미지)를 찾아서 ItemsAdder 팩(`yeowool_market` 네임스페이스, 서버의 `ItemsAdder/contents/yeowool_market`에 새로 생성)으로 변환하고, `PlayerShopGui`(`/거래소`)의 배경으로 적용함 — `/도감` 물고기 도감에 썼던 것과 동일한 폰트이미지 합성 기법(`MarketBackgroundImages`). 관련 플러그인: `YeowoolMarket`, `ItemsAdder`(새 콘텐츠 폴더 `yeowool_market` 추가 후 `/iazip` 필요)
- **버그 수정**: 위 거래소 배경 이미지가 적용 안 된다는 확인 후, 실제로 서버가 생성한 리소스팩(`ItemsAdder/output/generated.zip`)을 직접 열어 확인해보니 `exchange` 글리프 자체는 등록돼 있었지만(`assets/minecraft/font/default.json`) 높이(height)가 352로 잘못 들어가 있었음 — `fishing_expansion.yml`의 `font_images.fish_codex`는 정사각형(256x256) 이미지라 `scale_ratio`에 넓이를 넣으나 높이를 넣으나 우연히 같은 값이었는데, `exchange.png`는 정사각형이 아닌 352x437 이미지라 이번엔 그 차이가 실제로 문제가 됨 — `yeowool_market.yml`의 `scale_ratio`에 넓이(352)를 넣었던 걸 실제 높이인 **437**로 수정. `/iazip` 후 다시 확인 필요
- **베드락 2D 아이템 텍스처 549개 일괄 등록** — `moafarm` 리소스팩의 `textures/items` 폴더에 있던 모든 `.png`(하위 폴더 제외, 순수 파일 549개)를 ItemsAdder 신규 콘텐츠 폴더(`moafarm_items` 네임스페이스, 서버의 `ItemsAdder/contents/moafarm_items`)로 일괄 변환. 파일 하나하나 손으로 정의하는 대신 스크립트로 생성함 — 파일명 접미사로 종류를 추정해서(`_sword`→다이아몬드 검, `_axe`/`_pickaxe`/`_shovel`/`_hoe`→해당 도구, `_bow`→활, `_helmet`/`_chestplate`/`_leggings`/`_boots`→가죽 방어구(해당 부위에 장착 가능), 스튜/빵/파이/쿠키 등 음식 관련 키워드가 들어간 이름→구운 고기 베이스(허기 회복 가능)) 알맞은 바닐라 베이스 아이템에 `generate: true`로 텍스처만 입힘, 그 외 전부(장식품/보석/펫 아이콘 등)는 기본적으로 종이(PAPER) 베이스로 등록함. `moafarm_items:<파일명>` 형태로 `/iagive`나 기존 상점(`YeowoolMarket`)·관리자 아이템 카탈로그(`YeowoolAdmin`)의 `item-id`/`custom-icon` 필드 등 어디서든 바로 참조 가능. **분류는 파일명 기반 추정이라 완벽하지 않을 수 있음** — 특정 아이템의 베이스 재질이 이상하면 `ItemsAdder/contents/moafarm_items/configs/moafarm_items.yml`에서 그 항목의 `material` 값만 직접 고치면 됨. 관련 플러그인: `ItemsAdder`(새 콘텐츠 폴더 `moafarm_items` 추가 후 `/iazip` 필요, model_id는 IA가 첫 로드 때 자동 할당)

### 수정된 사항 (3) — 강화 재료를 마법 원석으로 변경 + 인게임 설정
- **강화 재료를 `magic_ore_1`~`magic_ore_6`(위 `moafarm_items`) 순서대로 사용하도록 변경** — 기존에는 강화 재료가 다이아몬드 고정이었는데, 이제 5레벨 구간마다 순서대로 다른 원석을 요구함: +0~+4는 `magic_ore_1`, +5~+9는 `magic_ore_2`, ... +25~+29는 `magic_ore_6`까지. 6개 원석 전부 `/관리자아이템`에 "초급/중급/고급/정예/신비/태초 강화석"이라는 이름으로 추가해서 관리진이 명령어 없이 GUI에서 바로 꺼낼 수 있음(각 아이템 설명에 어느 강화 구간에서 쓰이는지도 표시됨)
- **강화 단계별 필요 온/재료 개수를 인게임에서 직접 설정 가능하도록 변경** — 기존엔 `config.yml`의 공식(비용 = 기준값 × (강화수치+1)^지수)으로 자동 계산됐는데, 요청하신 대로 이제 완전히 자유롭게 설정 가능하도록 DB(`yw_enhance_costs`, 레벨별 1행)로 옮기고 새 명령어 `/강화설정`을 추가함: `/강화설정 <레벨> 온 <금액>`으로 그 강화 시도에 필요한 온을, `/강화설정 <레벨> 재료 <개수>`로 필요 개수를 바로 설정(재료 종류 자체는 명령어 실행 시 손에 들고 있는 아이템으로 자동 지정됨 — 바닐라 아이템이든 `moafarm_items:magic_ore_3` 같은 ItemsAdder 커스텀 아이템이든 다 가능), `/강화설정 <레벨> 정보`로 현재 값 확인. 서버를 처음 켤 때는 위 원석 순서 + 완만하게 증가하는 온 값으로 자동 시드되고, 그 이후로는 DB 값이 그대로 유지되므로 재시작해도 설정한 값이 안 사라짐
- **버그 수정**: 거래소 배경 이미지가 재접속·서버 재시작·`/iareload`·`/iazip`을 전부 해도 계속 적용 안 되던 진짜 원인을 찾음 — `scale_ratio` 수정은 실제로 필요했던 게 맞지만 그것만으론 부족했음. `PlayerShopGui`가 이미지 자리표시자를 만들 때 아이템(`CustomStack`)처럼 `"yeowool_market:exchange"`(네임스페이스 포함) 형태로 넘기고 있었는데, `/도감`의 `fish_codex`나 `/상점`의 `shop_item_display` 등 이미 작동 중인 모든 배경 이미지 코드를 다시 대조해보니 전부 네임스페이스 없이 `"fish_codex"`처럼 **키 이름만** 넘기고 있었음 — font_image 자리표시자는 아이템 ID와 달리 네임스페이스 없이 키 이름만으로 찾는 방식이었던 것. `"exchange"`로 수정
- 위 수정 후에도 여전히 안 보인다는 확인(이번엔 평문 대체 텍스트조차 없이 완전히 빈 제목 — 자리표시자 치환 자체는 성공했지만 이미지가 화면 밖으로 밀려난 것으로 추정)이 와서, `fish_codex`(세로 256픽셀) 기준값이던 `y_position: 19`를 `exchange`의 실제 세로 크기(437픽셀)에 비례해 `32`로 조정(19 × 437/256). 다만 이 값도 실제로 맞다는 보장은 없어서, ItemsAdder 자체 미리보기 명령어인 `/iaimage`로 직접 맞추는 걸 우선 시도해보기로 함(추후 재확인 예정)

### 추가된 기능 (4) — 추첨 시스템
- **`/추첨 <당첨 인원 수>`** (`YeowoolAdmin`, `yeowool.admin`) — 손에 든 아이템이 상품(아이템 자체는 소모되지 않음, `/쿠폰생성`과 같은 규칙). 실행하면 접속 중인 전체 플레이어에게 화면 정중앙에 "추첨 진행중..." 타이틀이 뜨면서 그 아래 부제목에 후보자 이름이 빠르게 랜덤으로 바뀌다가(약 2.5초) 실제 당첨자 이름으로 멈추는 연출이 나옴 — 당첨자 수만큼 이 과정을 반복하고, 한 번의 추첨 안에서는 같은 사람이 두 번 뽑히지 않음(매번 남은 인원 풀에서 뽑음). 당첨자에게는 즉시 우편함으로 상품이 발송됨(`core.mailbox()`, 오프라인이어도 안전)
  - **대상 인원**: 요청하신 대로 접속 중인 플레이어 중 **OP는 제외**하고 전원 대상
  - **확률**: 말씀하신 대로 처음엔 전원 균등 확률로 시작하고, **같은 아이템**으로 이전에 이미 당첨된 적 있는 사람은 그 다음 추첨부터 확률이 줄어듦(완전히 배제는 아니고 `raffle.repeat-weight-multiplier`(기본 0.5)만큼 당첨 횟수마다 계속 절반씩 — `yw_raffle_wins` DB에 아이템별·플레이어별 당첨 횟수로 저장되어 서버를 껐다 켜도 유지됨). **다른 아이템**을 손에 들고 `/추첨`하면 그 아이템은 당첨 이력이 없는 상태라 자동으로 다시 전원 균등한 확률로 시작함(아이템은 바닐라 재질뿐 아니라 ItemsAdder 커스텀 아이템도 서로 다른 아이템으로 정확히 구분됨)

### 추가된 기능 (5) — 웰컴 UI, 플레이어 워프 전용 UI
- **접속 시 환영 GUI** (`YeowoolCommunity`) — 제공해주신 "welcomui" 팩(ItemsAdder, namespace: `custom_menu`, 이미지: `welcome`)을 서버에 추가하고(`ItemsAdder/contents/welcome`), 접속 1초 후 자동으로 뜨도록 구현함. 원본 팩의 DeluxeMenus 설정을 그대로 참고해서 순수 장식용(클릭 가능한 버튼 없음, 플레이어가 직접 닫음)으로 만들었고, `config.yml`의 `welcome.enabled`로 아예 끌 수도 있음. 원본 팩에는 GUI 전체를 검게 덮는 `blackbg` 배경 레이어도 같이 들어있었는데, 이번엔 `welcome` 이미지 하나만 우선 적용함(레이어를 두 개 겹치면 그만큼 위치를 두 번 맞춰야 해서 오프셋 튜닝 부담이 두 배가 됨 — 필요하시면 추가로 요청해주세요)
- **`/플레이어워프`(플레이어 간 공개 워프) 신규 추가** (`YeowoolTeleport`) — 제공해주신 "PlayerWarps GUI" 팩(ItemsAdder, namespace: `playerwarps_gui`)의 배경/아이콘을 사용해서 자체 구현함(실제 PlayerWarps 플러그인 자체를 설치하는 게 아니라, 그 팩의 그림만 가져다 저희 코드로 새로 만든 것 — 원본 플러그인은 카테고리/평점/방문 수수료 등 훨씬 방대한 유료 플러그인이라, 이번엔 핵심 기능만 구현했고 필요하시면 더 확장 가능): `/플레이어워프 생성 [이름]`으로 현재 위치에 공개 워프 등록(기본 1인당 1개, `teleport.max-player-warps`로 조정), `/플레이어워프 삭제 [이름]`, `/플레이어워프 내워프`(내 워프 목록), `/플레이어워프` 또는 `/플레이어워프 목록`으로 서버의 모든 플레이어 워프를 `playerwarps_gui:playerwarps` 배경의 GUI로 둘러보고 클릭해서 이동(기존 `/홈`·`/워프`와 동일한 `TeleportService` 사용 — 이동 전 대기시간·이동 취소·쿨다운 규칙 동일하게 적용됨). `/워프`(관리자 전용 고정 지점)와는 완전히 별개 시스템
- **참고**: 두 기능 모두 배경 이미지 오프셋을 일단 -8로 시작 값만 넣어뒀음 — `/거래소`·`/도감` 때처럼 실제로 보면서 조정이 필요할 가능성이 높음. `ItemsAdder/contents/welcome`, `ItemsAdder/contents/playerwarps_gui` 새 콘텐츠 폴더가 추가됐으니 `/iazip` 필요
- (2026-08-30 후속) `/플레이어워프` 배경이 너무 오른쪽에 있다는 확인 후 `player-warp-gui.gui-background-offset`을 -8 → -50으로 조정함(직접 조정하신 값)

### 수정된 사항 (4) — 웰컴 UI 핫바 겹침, 플레이어워프 아이콘, 도움말 단축키
- **버그 수정**: 웰컴 GUI가 뜬 상태에서 핫바에 든 아이템이 배경 이미지 위에 그대로 겹쳐 보이던 문제 — 원인은 배경 이미지가 인벤토리 "제목" 영역에만 그려지는 트릭이라, 그 아래 플레이어 본인의 인벤토리(핫바 포함)는 항상 원래 그대로 보이기 때문. 원본 "welcomui" 팩도 이 문제를 알고 있었는지, GUI를 열기 직전에 `blackbg`라는 화면 전체를 덮는 새까만 이미지를 **인벤토리 제목이 아니라 일반 타이틀(`/title` 명령어에 해당하는 것)로 먼저 띄워서** 핫바까지 포함한 화면 전체를 가리는 방식을 쓰고 있었음(원본 Skript 파일에서 발견) — 이 방식을 그대로 재현: 웰컴 GUI를 열기 0.25초 전에 `blackbg` 전체화면 타이틀을 먼저 띄우고, GUI를 닫으면(`WelcomeGui.onClose`) 그 타이틀을 정리함
- **`/플레이어워프` 아이콘 교체**: 닫기 버튼을 배리어 대신 "PlayerWarps GUI" 팩 자체의 투명 아이콘(`playerwarps_gui:blank`)으로 변경. 등록된 플레이어 워프가 하나도 없을 때 뜨던 빨간 배리어 아이콘도, 그 팩이 원래 갖고 있던 전용 "워프 없음" 아이콘(`playerwarps_gui:no_warpsicon`)으로 교체 — "아직 등록된 플레이어 워프가 없습니다" 라는 이름이 붙어 있어서 훨씬 명확하게 보임(이전 스크린샷에 보였던 두 번째 빨간 아이콘의 정체가 바로 이거였음)
- **Shift+F 단축키로 서버 도움말 열기** — 원본 팩에 같이 들어있던 "InstantCommand" 플러그인 설정(Shift+F → help)을 재현. 별도 플러그인이나 커스텀 키바인드 없이도, 바닐라 F키(주손/보조손 아이템 교체)는 이미 서버가 감지할 수 있는 진짜 이벤트라서, 웅크린 상태로 F를 누르면 실제 교체는 취소하고 대신 `/여울도움말`을 실행하도록 구현함(`welcome.shift-f-help-enabled`/`shift-f-help-command`로 켜고 끄거나 다른 명령어로 변경 가능, 웰컴 GUI 여부와 무관하게 항상 동작)

### 제거된 기능 (2)
- **환영 GUI, Shift+F 도움말 단축키 전부 삭제** — 사용자 요청에 따라 되돌림. `YeowoolCommunity`의 `welcome` 패키지(`WelcomeGui`/`WelcomeJoinListener`/`WelcomeBackgroundImages`/`ShiftSwapHelpListener`) 전체와 `config.yml`의 `welcome` 섹션 삭제. **직접 지우셔도 되는 것**: 이제 안 쓰는 서버의 `ItemsAdder/contents/welcome` 폴더(`ItemsAdder` 플러그인 콘텐츠) — 코드에서 더 이상 참조하지 않아서 남겨둬도 해는 없지만, 정리하고 싶으시면 지우고 `/iazip` 하시면 됩니다

### 추가된 기능 (6) — 플레이어 워프 전체 기능 (실제 "PlayerWarps" 플러그인 수준으로 확장)
"완벽한 서버를 만들고 싶다"는 요청에 따라, 지난번에 핵심만 구현했던 `/플레이어워프`를 실제 PlayerWarps 플러그인(유료)의 `config.yml`을 그대로 참고해서 요금·카테고리·검색·정렬·즐겨찾기·평점·입장료·편집 화면까지 전부 갖추도록 확장함. 보내주신 스크린샷 8장(거래소 확인/워프 편집/내 워프/플레이어워프/평가/즐겨찾기/카테고리/공개상태) 전부 실제 화면으로 사용됨.

- **DB 확장**: `yw_player_warps`에 표시이름/설명/카테고리/공개상태/미리보기아이콘/입장료/방문수 컬럼 추가, 평점용 `yw_player_warp_ratings`·즐겨찾기용 `yw_player_warp_favorites` 테이블 신규 추가
- **`/플레이어워프` (browse, `playerwarps` 배경)** — 상단 제어줄(정렬·검색·카테고리·내워프·즐겨찾기·도움말), 클릭=이동(입장료 있으면 확인창), Shift+클릭=즐겨찾기 토글, 우클릭=평가. 정렬은 이름순/방문순/최신순/평점순 순환, 검색은 가상 모루로 텍스트 입력(`/쿠폰`과 같은 방식), 카테고리는 config.yml에서 얼마든지 추가 가능(기본 농장/건축/이벤트, 아이콘은 팩에 이미 있는 것 사용)
- **`이동 확인` (confirm_warp)** — 입장료가 있는 워프에 처음 방문할 때만 뜸(수락/취소 슬롯 위치는 실제 플러그인 설정값 그대로: 9~12/14~17). 수락하면 입장료가 워프 주인에게 그대로 지급됨(본인 워프 방문은 무료)
- **`내 워프` (my_warps) → `워프 설정` (edit_warp)** — 내 워프 목록에서 하나를 클릭하면 실제 플러그인과 동일한 배치(11 입장료/12 카테고리/13 표시이름/14 미리보기아이템/15 설명/20 삭제/21 이름변경/22 공개상태/23 현재위치로 이전/24 소유권 이전)로 편집 가능. 미리보기 아이템은 손에 든 아이템으로 바로 교체(밴 목록에 있는 아이템은 거부), 이름·표시이름·설명·소유권 이전은 가상 모루로 텍스트 입력
- **`즐겨찾기` (saved_warps)** — 즐겨찾기한 워프만 모아서 보여줌, Shift+클릭으로 해제
- **`평가` (review_warp)** — 별 1~5개 클릭, 플레이어당 워프 하나에 한 번(다시 평가하면 이전 점수 덮어씀), 평균 별점이 목록 정렬(평점순)과 아이콘 설명에 반영됨
- **`카테고리 선택`/`공개 상태 설정` (warp_categorytitle/warp_statustitle)** — 목록 필터링과 워프 편집 양쪽에서 재사용되는 공용 선택 화면
- **요금 체계**: 생성/이름변경/현재위치로 이전/소유권 이전/입장료·카테고리·미리보기·설명·표시이름·공개상태 설정 요금 + 삭제 시 환불까지, 전부 실제 플러그인 기본값을 그대로 가져와 `config.yml`의 `player-warp` 섹션에 넣어뒀음(0으로 설정하면 그 요금은 비활성화)
- **범위에서 제외한 것**: dynmap 마커 연동 — 필요하시면 추가로 말씀해주세요
- **(후속) 이동 전 안전지점 자동 체크 추가** — 요청하신 대로 `PlayerWarpSafety` 신규 추가: 워프로 이동하기 직전, 저장된 좌표가 용암/불/선인장 등 위험한 블록이거나 발밑이 허공(공허로 떨어질 위험)이면 그 자리에서 위/아래로 12칸까지 훑어서 가장 가까운 안전한 자리를 찾아 그쪽으로 대신 이동시킴 — 워프를 만든 뒤 누가 그 자리에 용암을 붓는 등 지형이 바뀌어도 안전. 12칸 안에서도 안전한 곳을 못 찾으면 이동 자체를 취소하고 안내 메시지만 보냄(요금은 차감되지 않음). `player-warp.check-safe-teleport`(기본 true)로 끌 수 있음. `/워프`(관리자)·`/홈`·`/tpa`에는 적용 안 함(관리자가 직접 고른 지점, 본인이 설정한 홈이라 스코프 밖으로 판단)
- **(후속) 실제 화면을 보면서 알려주신 정확한 칸 배치로 전면 재배치** — `playerwarps`/`warps_title`이 사실 두 개의 서로 다른 화면(허브 화면과 전체 목록 화면)이라는 걸 알려주셔서 구조를 다시 나눔:
  - **`/플레이어워프` 진입 화면을 새 `PlayerWarpHubGui`(`playerwarps` 배경, "PLAYERWARPS" 제목)로 분리** — 20 이벤트/21 건축/23 농장 카테고리 바로가기, 22·24·31은 전체 보기, 39 닫기/40 내 워프/41 즐겨찾기. 기존엔 목록 화면 하나에 다 몰려있었는데 이제 허브에서 먼저 고르고 들어가는 구조
  - **전체 목록 화면은 `warps_title` 배경으로 분리** — 그림 전체가 목록 칸이라 0~44번 전부 워프로 채움(페이지당 45개, 기존 36개보다 늘어남), 45 이전 페이지/46 정렬/48 허브로/49 내 워프/50 즐겨찾기/52 검색/53 다음 페이지 (47·51은 비워둠)
  - **평가 화면(`review_warp`)에 32번 칸 닫기 버튼 추가** (기존엔 별점 5개만 있고 닫는 버튼이 없었음)
- **(후속) 칸 배치 재조정** — 직접 보시고 잘못 말씀하신 부분 정정 반영:
  - 허브 화면의 39/40/41(닫기/내 워프/즐겨찾기)을 48/49/50으로 이동
  - 목록 화면(`warps_title`)의 45번(이전 페이지)·53번(다음 페이지) 아이콘이 페이지가 없을 때(워프가 45개 이하일 때) 아예 안 보이던 문제 — 이제 항상 표시하고, 갈 페이지가 없을 때만 클릭이 안 먹도록 변경
  - 평가 화면(`review_warp`)의 닫기 버튼을 32번 → 31번으로 이동
  - 즐겨찾기 화면(`saved_warps`)도 목록 화면과 똑같이 0~44번 전체를 워프 칸으로 변경(기존엔 9~35번 27칸만 사용), 닫기 버튼을 40번 → 49번으로 이동
  - 즐겨찾기 화면의 워프 아이콘이 평범한 종이 아이템으로 뜨던 것을 목록 화면과 같은 `default_warpitem` 아이콘으로 통일
- **버그 수정(중요)**: 즐겨찾기 화면을 열려고 하면 콘솔에 `ArrayIndexOutOfBoundsException: Index 49 out of bounds for length 45`가 뜨면서 GUI가 아예 안 열리던 문제 — 닫기 버튼을 40번에서 49번 칸으로 옮겨달라고 하셨는데, 정작 그 GUI들(즐겨찾기/내 워프/워프 설정) 자체는 여전히 45칸(5줄)짜리로 만들어져 있어서 49번 칸이 애초에 존재하지 않았던 것. 세 화면 모두 54칸(6줄)으로 늘림. 겸사겸사 요청하신 대로 내 워프 화면의 닫기도 40→49로, 워프 설정 화면의 닫기도 40→49로 옮기고, 내 워프 화면의 워프 아이콘도 즐겨찾기 화면과 똑같이 `default_warpitem`으로 통일함

### 추가된 기능 (7) — NPC 머리 위 태그 아이콘
- 제공해주신 "scottgb_npc_tags" 팩(대화/영업중/정보/상점/제작, 각각 검정/파랑/초록/보라/빨강 5색 = 총 25종)을 서버에 추가함(`ItemsAdder/contents/npc_tags`, namespace `npc_tags`, `<종류>_<색상>` id) — 확인해보니 순수 바닐라 리소스팩 형식(`assets/minecraft/font/default.json`)이었고, 이미 잘 작동 중인 `beautiful_ranks`(랭크아이콘) 팩과 완전히 같은 방식(`scale_ratio: 8`, `y_position: 8` — 인라인 글자 크기, GUI 배경 트릭과 달리 오프셋 조정이 필요 없는 방식)으로 등록해서 이번엔 위치 튜닝 없이 바로 될 가능성이 높음
- **`/npc태그 설정 <타입> <색상>` / `/npc태그 제거`** (`YeowoolAdmin`, `yeowool.admin`) — 바라보고 있는 엔티티(6블록 이내)의 바닐라 이름표에 태그 아이콘을 붙이거나 뗌(랭크아이콘과 같은 원리, `FontImageWrapper.replaceFontImages`). 일반 엔티티에는 바로 적용됨
- **`/npc태그 모델생성 <타입> <색상> <이름> <모델ID> [추가인자...]`** — HQModeledNPC(디컴파일로 확인: `plugin.yml`에는 `/motion`만 선언돼 있지만 실제로는 `modelednpc create`라는 별도 명령어가 존재함, HQFramework가 동적으로 등록하는 것으로 보임)의 `modelednpc create` 명령어를 감싸서, 태그 아이콘을 이름 맨 앞에 붙인 채로 새 NPC를 생성해줌
- **주의(HQModeledNPC의 한계)**: 디컴파일해서 확인해보니 HQModeledNPC에는 `create`/`delete`/`list`만 있고 **기존에 이미 만들어둔 NPC의 이름을 바꾸는 명령어 자체가 없음** — 그리고 ModelEngine 기반 NPC는 이름표를 바닐라 방식이 아니라 ModelEngine 자체 저장값으로 그릴 가능성이 높아서, `/npc태그 설정`으로 기존 ModeledNPC에 태그를 붙여도 실제로 반영이 안 될 수 있음(일반 몹/엔티티에는 문제없이 적용됨). **이미 만들어둔 ModeledNPC에 태그를 붙이고 싶으시면, 현재로선 그 NPC를 지우고 `/npc태그 모델생성`으로 태그가 포함된 이름으로 다시 만드는 것 외에 다른 방법이 없어 보입니다** — 직접 테스트해보시고 `/npc태그 설정`이 기존 NPC에도 통하는지, 혹은 안 통하는지 알려주시면 그에 맞춰 더 조정하겠습니다


## 2026-08-31

### 강화(/강화) GUI 이미지 교체 — AdvancedEnchantments UI 팩 기반
- 제공해주신 "AdvancedEnchantments_UI" 팩(실제 유료 플러그인 AdvancedEnchantments의 GUI 리소스팩)에서 배경(enchanter.png, 220×170)과 확인/취소 아이콘(confirm.png/unconfirm.png)만 가져와서 `/강화` GUI를 새로 꾸밈(`ItemsAdder/contents/yeowool_enhance`, namespace `yeowool_enhance`) — 나머지(인챈트북 뽑기, 티커러/알케미스트 등 AdvancedEnchantments 자체 기능)는 우리 강화 시스템과 성격이 달라서 가져오지 않고 배경/아이콘만 재사용함
- GUI를 27칸→36칸(4줄)으로 키워서 배경 이미지 비율에 맞춤(미리보기 13번/강화하기 22번/닫기 31번, 가운데 열로 정렬)
- 강화하기 버튼 아이콘이 기존 단순 모루(ANVIL)에서, 손에 강화 가능한 아이템을 들었는지에 따라 팩의 confirm(초록 체크)/unconfirm(빨간 X) 아이콘으로 바뀌도록 변경
- 배경 세로 위치는 `config.yml`의 `enhance.gui-background-offset`(기본 -8)로 조정 가능 — 인게임에서 보면서 미세조정 필요할 수 있음(이전 거래소 GUI 때처럼)

### 퀘스트(/퀘스트) GUI 이미지 교체 — DailyQuest-1.8.3 팩 기반
- 제공해주신 "DailyQuest-1.8.3" 폴더(실제 플러그인 ODailyQuests 번들, ItemsAdder 리소스 포함)에서 배경(daily_quest.png)과 아이콘 일부(easy_quest/hard_quest/arrow_left)만 가져와서 `/퀘스트` 관련 GUI 두 개를 새로 꾸밈(`ItemsAdder/contents/daily_quest`, namespace `daily_quest`) — 뱃지/리더보드/경험치바 등은 우리 쪽에 해당 시스템(등급·랭킹)이 없어서 이번엔 가져오지 않음(필요하시면 별도로 말씀해주세요)
- **일일/주간 선택 화면**: 배경 교체, 일일 버튼 아이콘을 easy_quest, 주간 버튼 아이콘을 hard_quest로 교체(기존 시계/해바라기 대신)
- **퀘스트 목록 화면**: 배경 교체, 기존엔 "닫기"만 있던 하단 버튼을 arrow_left 아이콘의 "뒤로가기"로 바꿔서 일일/주간 선택 화면으로 돌아가도록 개선(기존엔 그냥 인벤토리를 닫기만 했음)
- 퀘스트 칸 자체의 완료/수령완료/진행중 아이콘(책/상자/회색염료)은 상태를 직관적으로 보여주는 용도라 그대로 유지함
- 배경 세로 위치는 `config.yml`의 `quest.gui-background-offset`(기본 -8)로 조정 가능

두 기능 모두 서버 재시작(또는 `/iazip` + 플러그인 리로드) 후 확인 가능합니다. 배경 위치가 어긋나 보이면 이전 거래소/플레이어워프 GUI 때처럼 오프셋 값을 알려주시면 바로 조정하겠습니다.

### (후속) 강화 시스템 3화면 구조로 전면 재설계 — AdvancedEnchantments UI 그대로
- "강화도 이 폴더에 맞춰서 시스템을 완전히 재설계해달라"는 요청에 따라, 단순 배경 교체를 넘어서 AdvancedEnchantments의 화면 구조(Enchanter/Tinkerer/Alchemist 3화면)를 그대로 가져옴. 단, 여쭤봤을 때 "+N강 방식(성공/실패, 등급 승급)은 그대로 유지하고 화면 구조만 AE처럼" 해달라고 하셔서, 인챈트북 뽑기 같은 AE 고유 로직은 가져오지 않고 우리 +N강 로직을 3화면에 나눠 담는 방식으로 설계함
- **엔챈터 화면 (`/강화`, 기존 화면)** — 그대로 강화 시도(미리보기 13번/강화하기 22번). 여기에 20번(틴커러)·24번(알케미스트) 칸에 이동 버튼을 새로 추가해서 허브 역할을 겸함
- **틴커러 화면 (신규) — 강화 해체**: 강화한 무기/방어구를 손에 들고 "해체하기"를 누르면 강화 수치가 1단계 내려가면서, 그 단계에서 들었던 온/재료 비용의 일부(기본 50%, `enhance.dismantle.refund-percent`)를 돌려받음. 100%가 아니라 강화→해체를 반복해서 재료를 무한정 만들어내는 악용을 막음
- **알케미스트 화면 (신규) — 재료 조합**: magic_ore_1~6을 낮은 등급부터 높은 등급으로 조합(기본 5개 → 상위 1개, `enhance.alchemy.combine-ratio`). 5개 조합 버튼이 한 줄에 나열되고, 보유 개수가 실시간으로 표시됨. 어떤 재료를 조합할지는 `enhance.alchemy.materials` 목록으로 바뀌므로 magic ore가 아닌 다른 재료 체계로도 나중에 바꿀 수 있음
- 세 화면 모두 AE 리소스팩의 enchanter.png/tinkerer.png/alchemist.png를 배경으로 사용(`ItemsAdder/contents/yeowool_enhance`), 서로 뒤로가기 버튼(4번 칸)으로 오갈 수 있음

### 퀘스트 시스템 전면 재설계 — DailyQuest-1.8.3 구조 그대로 (리더보드/뱃지/난이도 3단 추가)
- 보내주신 리더보드/뱃지/데일리퀘스트 화면 3장을 기준으로, 단순 배경 교체를 넘어서 실제 기능까지 그 구조에 맞춰 새로 만듦
- **난이도 3단 (쉬움/보통/어려움) 구조로 전환**: 기존엔 일일/주간 각각 6종 중 3개만 랜덤으로 뽑혔는데, 이제 각 활동(채광/농사/낚시/벌목/목축/사냥)마다 쉬움·보통·어려움 3단계 목표가 따로 있고, 하루엔 이 중 4+4+4=12개(스샷 "12 different quests per day"와 동일), 주는 3+3+3=9개가 뽑힘. 개수는 `daily.easy-count`/`medium-count`/`hard-count`(주간도 동일 키)로 조정 가능
- **퀘스트 보드 화면 재구성** — 팩의 `playerInterface.yml`에 있던 실제 칸 배치(1번 프로필 머리/9번 리더보드 버튼/18번 뱃지 버튼/4·5·6·7=쉬움/13·14·15·16=보통/22·23·24·25=어려움)를 그대로 가져옴. 프로필 머리에 마우스를 올리면 총 완료 수·현재 뱃지·오늘(이번 주) 진행도·다음 초기화까지 남은 시간이 표시됨
- **리더보드 (신규)** — "총 완료 퀘스트 수"(일일+주간 합산, 새로 추가한 통계 `community.quest.total-completed`) 기준 TOP 10과 내 순위를 보여줌. 서버 전체 플레이어를 대상으로 하는 DB 조회라서 `yeowool-analytics`의 `/랭킹` 명령어가 쓰는 것과 같은 방식(플레이어 머리 아이콘 + 클릭 시 비동기 조회)으로 만듦
- **뱃지 (신규)** — 총 완료 수 기준 8단계(Rookie 0회 → Beginner 10 → Skilled 25 → Pro 50 → Advanced 100 → Expert 150 → Master 200 → Legend 300, `quest.badges`에서 이름/기준/아이콘 자유롭게 수정 가능), 잠긴 뱃지는 회색 자물쇠 아이콘 + 진행도(예: 75/200)로 표시됨. 정확한 단계 이름/기준은 제가 임의로 정한 값이라 마음에 안 드시면 바로 조정해드릴 수 있음
- 새로 추가된 텍스처: 뱃지 9종(rookie~locked), 리더보드/뱃지 화면 배경 2장 — 전부 `ItemsAdder/contents/daily_quest`에 심어둠

### (후속) 퀘스트 3화면 칸 배치 직접 보시고 정정 반영
- **퀘스트 보드**: 0·1·9·10 칸 전부 프로필(유저 얼굴)로, 8=리더보드 버튼, 17=뱃지 버튼, 쉬움=3·4·5·6, 보통=12·13·14·15, 어려움=21·22·23·24로 재배치. 퀘스트 칸 아이콘도 상태별 바닐라 아이템(책/상자/염료) 대신 난이도 아이콘(easy_quest/medium_quest/hard_quest)을 실제로 쓰도록 수정(전엔 텍스처를 가져왔지만 실제 아이콘 표시엔 안 쓰고 있었음). 닫기/빈 상태 배리어 버튼 전부 제거(뒤로가기도 슬롯이 겹쳐서 같이 제거 — ESC로 닫으면 됨)
- **리더보드**: 4=1등, 3=2등, 5=3등(포디움 배치), 10~16=4~10등 순서대로. 배리어 닫기·뒤로가기 버튼 전부 제거, 내 순위 아이콘은 22번으로 이동
- **뱃지**: 배리어 닫기 버튼 제거, 0번 칸에 arrow_left 뒤로가기 버튼 추가

### 강화 시스템 분리 — /강화(기존 +N강 유지)와 /인챈트강화(신규, 완전히 별도)
- 지난번엔 "+N강 유지, 화면 구조만 AE처럼"로 진행했었는데, "그냥 완전히 별도의 시스템으로 새로 만들어달라"는 요청에 따라 되돌리고 아예 명령어를 나눔
- **`/강화`는 원래대로 복구** — 틴커러/알케미스트로 가는 버튼과 그 기능(해체/재료조합) 전부 제거하고, 미리보기/강화하기/닫기 3버튼짜리 원래 화면으로 돌아감. 배경(enchanter.png)은 그대로 유지
- **`/인챈트강화` (완전 신규)** — AdvancedEnchantments 원작 그대로 엔챈터→틴커러→알케미스트 3화면 구조 + 적용 화면까지 총 4화면:
  - **엔챈터**: 심플/유니크/엘리트/얼티밋/레전더리/페이블드 6등급 인챈트북을 온으로 구매 (가격은 원작 그대로 400/800/2500/5000/25000/40000, 다만 원작은 경험치 소모인데 저희는 온 경제와 일관되게 온으로 바꿈)
  - **틴커러**: 안 쓰는 인챈트북을 분해해서 마법 가루(등급별 랜덤량) + 구매가의 30% 온 환급
  - **알케미스트**: 같은 등급 책 3개 + 마법 가루 10개를 모아 상위 등급 책 1개로 조합
  - **적용**: 주손에 인챈트북, 보조손에 무기/방어구를 들고 적용하면 책이 소모되며 등급에 맞는 레벨(예: 심플 1~2강, 페이블드 6~10강 — 바닐라 최대치를 넘는 값도 그대로 적용됨)의 무작위 인챈트가 부여됨. 무기/방어구 인챈트 후보 목록은 `inchant.weapon-enchant-pool`/`armor-enchant-pool`에서 자유롭게 수정 가능
  - AE 원작의 인챈트북 텍스처(심플~페이블드) 6종을 새로 가져옴, 배경은 이미 있던 tinkerer.png/alchemist.png를 그대로 재사용(적용 화면은 원작에 해당 화면이 없어서 일반 제목만 사용)
- `/강화`와 `/인챈트강화`는 데이터·아이템·재화 소모 방식까지 완전히 독립적입니다 (인챈트북은 `/강화`의 +N강 재료로 쓸 수 없고, 그 반대도 마찬가지)

### (후속) 퀘스트 명령어 분리 + 빈 칸 버그 수정 + 리더보드 정리
- **`/퀘스트`(일일·주간 선택 화면) 대신 `/일일퀘스트`·`/주간퀘스트`로 명령어 자체를 분리** — 선택 화면(QuestMenuGui) 없이 각 명령어가 바로 해당 퀘스트 보드를 엽니다
- **버그 수정: 일일 퀘스트 칸이 전부 비어있던 문제** — 오늘 안에 난이도 3단 구조로 바꾸는 작업을 하면서 퀘스트 id들이 바뀌었는데(mining→mining_easy/medium/hard 등), 이미 그날 퀘스트를 한 번 뽑았던 플레이어는 "오늘은 이미 뽑았음" 처리가 남아있어서 옛날 id를 계속 참조하다가 사라진 id라 전부 빈 칸으로 보였던 것. 이제 저장된 id 중 하나라도 현재 목록에 없으면(관리자가 나중에 퀘스트 목록을 바꿔도 마찬가지) 자동으로 다시 뽑도록 방어 로직 추가 — 재접속/재입장 없이 바로 정상화됨
- **주간 퀘스트 4번째 칸(6·15·24)이 비어있던 문제** — 버그가 아니라 주간은 난이도별 3개씩만 뽑도록 되어있었던 것(화면은 4칸씩). 일일과 똑같이 4개씩 뽑도록 변경
- **리더보드 22번 칸의 "내 순위" 머리 제거** — 애초에 알려주신 배치(1~10등만)에 없던 걸 제가 임의로 추가했던 것이라 그냥 뺐습니다

### (후속) 주간 퀘스트 4개씩 안 채워지던 문제 추가 수정
- config.yml은 이미 4/4/4로 바꿔뒀지만, 이번 주에 이미 한 번 뽑았던 플레이어는 그때 저장된 3개짜리 목록을 계속 들고 있어서 설정만 바꿔서는 소용이 없었음(id 자체는 멀쩡해서 지난번 "죽은 id면 다시 뽑기" 로직에도 안 걸림)
- `ensureRolled`에 "난이도별로 뽑힌 개수가 지금 설정된 개수보다 적으면" 조건을 추가로 넣어서, 설정에서 개수를 늘리면 이미 뽑은 주/일이라도 재접속 없이 바로 다시 뽑히도록 함

### (후속) 인챈트강화 — 원작 실제 화면/설치 가이드 기준으로 재정비
- 보내주신 원작 스크린샷("SERVER ENCHANTER" 타이틀, TINKERER/ALCHEMIST 큼직한 버튼 두 개)과 설치 가이드(wiki.advancedplugins.net)를 확인하고, 처음에 제가 임의로 배치했던 슬롯 대신 AdvancedEnchantments 원작의 실제 설정 파일(menus/enchanter.yml)에 있는 정확한 슬롯을 그대로 가져옴:
  - 등급별 책 구매 버튼: 2·3·4·5·6·13번 칸 (원작 그대로)
  - 틴커러 버튼: 18·19·20·21·27·28·29·30 (2x4 블록 전체가 같은 버튼 — 스샷에서 "빨간 큰 버튼"처럼 보이는 이유였음)
  - 알케미스트 버튼: 23·24·25·26·32·33·34·35 (마찬가지로 2x4 블록)
  - 배경 세로 위치도 원작 설정값 그대로 -32로 고정(추측값 -8 대신)
  - 원작에 닫기 버튼이 없어서 저희도 뺐음
- **더 중요한 수정 — "적용" 화면을 아예 없앰**: 원작을 다시 확인해보니 "우클릭으로 즉시 랜덤 인챈트북을 받는다"는 게 핵심이라, 구매한 책을 실제로 "가짜 아이템"이 아니라 **진짜 바닐라 인챈트북**(모루에 넣으면 바로 무기/방어구에 조합되는)으로 바꿈. 그래서 예전에 만들었던 "주손 책+보조손 장비 → 적용" 화면 자체가 필요 없어져서 완전히 삭제하고, 그냥 일반 모루를 쓰면 됨(등급이 높을수록 바닐라 최대 레벨을 넘는 인챈트도 그대로 붙음 — 예: 페이블드는 6~10레벨)
- 무기/방어구 인챈트 후보를 따로 나눴던 것도 하나로 합침 — 모루 자체가 "방어구엔 방어 인챈트만" 같은 규칙을 알아서 걸러주기 때문에 목록을 나눌 필요가 없었음(`inchant.enchant-pool`)
- 인챈트북이 이제 진짜 바닐라 인챈트북이라, ItemsAdder 아이템 베이스도 BOOK → ENCHANTED_BOOK으로 바꿈(모루가 인식하려면 이게 맞아야 함)

### (후속) 틴커러/알케미스트 버튼 투명 아이콘 + 화면 크기 수정
- 엔챈터 화면의 TINKERER/ALCHEMIST 버튼(2x4 블록 16칸)을 모루/양조대 아이콘 대신 투명 아이콘(AE 팩 자체의 air.png, `yeowool_enhance:enhance_invisible`)으로 교체 — 배경 이미지에 이미 "TINKERER"/"ALCHEMIST" 글자가 그려져 있어서 그 위에 진짜 아이템 아이콘을 겹쳐 놓으면 지저분해 보였던 문제
- **틴커러 화면 크기를 9x6(54칸)으로 수정** — 다시 확인해보니 원작 tinkerer.yml의 player-slots/tinkerer-slots가 53번 칸까지 쓰고 있어서 원래 6줄짜리 화면이었음(36칸으로 잘못 만들었었음)
- **알케미스트 화면 크기를 9x3(27칸)으로 수정** — 원작 alchemist.yml은 슬롯이 22번을 넘지 않아서 3줄이 맞음
- 두 화면 다 내부 버튼(미리보기/분해·조합/뒤로가기/닫기) 위치도 새 화면 크기에 맞춰 재배치함

### (후속) 틴커러/알케미스트를 진짜 "아이템을 넣는" 방식으로 재구현
- 지금까지는 손에 든 책 1개를 버튼 클릭으로 처리하는 방식이었는데, 요청하신 대로 실제로 GUI 안에 아이템을 드래그해서 넣는 방식으로 다시 만듦(우리 GUI 프레임워크에 이미 있던 "편집 가능한 칸" 기능을 사용 — 거래소(`/거래`)에서 이미 쓰던 것과 같은 방식)
- **알케미스트**: 3번·5번 칸에만 인챈트북을 넣을 수 있음(다른 아이템을 넣으면 바로 튕겨져 나옴). 같은 등급 책 2개를 넣으면 13번 칸에 합쳐진 결과물(다음 등급 책) 미리보기가 뜨고, 22번 칸의 confirm.png를 누르면 두 책이 사라지고 결과물이 인벤토리로 들어옴. 등급이 다르거나 인챈트북이 아니면 미리보기가 안 뜨고 확인해도 아무 일 없음
- **틴커러**: 23개 칸(각 줄 왼쪽 3~4칸)에 인챈트북을 넣으면, 넣은 개수만큼 오른쪽 24개 칸에 앞에서부터 마법 가루가 하나씩 채워짐(등급별로 양이 다름). 0번 칸은 처음엔 unconfirm.png인데, 한 번 누르면 confirm.png로 바뀌고(아직 거래 안 됨), 그 상태에서 한 번 더 누르면 넣은 책이 전부 사라지고 보여준 마법 가루 + 온 환급을 받음. 넣은 책 목록이 바뀌면 다시 unconfirm 상태로 돌아감(엉뚱한 걸 확인하는 사고 방지)
- 두 화면 다 인챈트북이 아닌 아이템을 넣으면 즉시 튕겨나가고, GUI를 그냥 닫아버려도(확인 안 누르고) 넣어뒀던 책은 인벤토리로 돌아옴(사라지지 않음)
- 엔챈터의 틴커러/알케미스트 버튼은 요청하신 대로 투명 아이콘, 네더의 별/배리어/숫돌 아이콘은 전부 제거
- 엔챈터에서 사는 책은 이제 ItemsAdder 커스텀 텍스처 없이 완전히 평범한 바닐라 인챈트북 모양 그대로 나갑니다(내용물—등급/인챈트—은 그대로 랜덤)

### (후속) 알케미스트 빈 칸 채우기 + 인챈트북 이미지 원복
- **알케미스트**: 3·5·13·22번 칸을 뺀 나머지 전부를 투명 아이콘(AE의 air.png)으로 채워서, 다른 칸에는 아예 아무것도 못 넣게(시프트클릭으로 잘못 들어가는 것도 방지) 함
- **바닐라 인챈트북 모양으로 바꿨던 것 되돌림** — 요청하신 대로, 구매한 책이 다시 AE 팩의 커스텀 텍스처(simple_book~fabled_book)로 보이도록 복구함. 단, 내부적으로는 여전히 진짜 바닐라 인챈트북(모루에서 바로 조합 가능)이라 기능은 그대로 유지됨 — 겉모습만 원래대로 돌아간 것
- 엔챈터 화면의 구매 버튼 아이콘도 등급별로 같은 커스텀 이미지(simple_book/unique_book/elite_book/ultimate_book/legend_book/fabled_book 순)를 쓰도록 통일함

## 2026-08-31 (계속) — 플레이어 상점(거래소)을 경매장으로 완전 교체
- "GUI/이미지/아이콘은 내가 줄게"라며 주신 "v0id AuctionHouse 2.0" 팩(실제 유료 플러그인 PlayerAuctions의 GUI 애드온)을 기준으로, 기존 즉시구매 전용 `/거래소`(플레이어 상점)를 완전히 삭제하고 이미 만들어져 있던 `/경매` 시스템 하나로 통합했습니다 — 마침 `/경매`는 처음부터 "입찰 + 즉시구매가(선택)"를 동시에 지원하도록 설계되어 있어서, 즉시구매가만 설정하면 예전 거래소와 똑같이 쓸 수 있습니다
- **`/경매` 메인 화면** — 배경을 팩의 auctionhouse.png로 교체, "내 경매"·"우편함"(낙찰/유찰 아이템 수령) 버튼 추가. 이제 클릭해도 바로 입찰/구매되지 않고 확인 화면으로 넘어감
- **입찰 화면 (신규, bidding.png)** — 예전엔 클릭하면 최소 입찰가로 자동 입찰됐는데, 이제 +1,000/+10,000/+100,000온 버튼으로 원하는 금액을 맞춘 뒤 확정하는 화면으로 바뀜(금액은 `auction.bid-increments`에서 조정 가능)
- **구매 확인 화면 (신규, confirmpurchase.png)** — 즉시구매가가 있는 경매를 Shift+클릭하면 뜨는 확인 화면. 원작은 수량도 조절할 수 있는데, 저희 경매는 한 묶음을 통째로 팔고 사는 방식(부분 구매 없음)이라 수량 조절 없이 확인/취소만 있음
- **내 경매 화면 (신규, myauctions.png)** — 자기가 등록한 경매 목록을 보고 취소 가능. 단, 이미 입찰이 들어온 경매는 입찰자 보호를 위해 취소 불가(원작의 "cancel-bid" 옵션과 비슷한 취지)
- **만료/낙찰 아이템 수령** — 원작엔 별도의 "Expired Items" 화면이 있지만, 저희는 이미 잘 만들어져 있는 우편함 시스템(`/우편함`)이 낙찰 아이템·유찰 시 반환 아이템을 전부 처리하고 있어서 그걸 그대로 재사용했습니다(별도 화면 새로 안 만듦)
- **⚠️ 확인 필요**: 기존 `/거래소`에 등록되어 있던 판매글(`yw_market_listings` 테이블)은 이제 그 어떤 명령어로도 접근할 수 없게 되었습니다. 서버에 그 상점에 등록해둔 아이템이 남아있는 플레이어가 있다면, 데이터베이스에서 직접 확인해서 돌려드려야 할 수도 있습니다 — 확인이 필요하시면 말씀해주세요.

### (후속) 경매장 아이콘 배치를 참고 이미지 기준으로 재조정
- 보내주신 참고 이미지(Pages: Auction House/My Auctions/Expired Items, Bidding/Confirm Purchase) 기준으로 칸 배치를 다시 맞춤
- **입찰/구매확인 화면**: 이미 원작 설정 파일 그대로 만들어져 있던 배치(19·20·21=빼기, 22=미리보기, 23·24·25=더하기, 39=확인, 41=취소)가 참고 이미지와 정확히 일치해서 그대로 유지
- **경매장 메인 화면**: 이미지 속 우측 세로 아이콘 줄(카테고리/홈/정렬/검색/목록)을 반영해서 그리드를 9칸 폭 전체 대신 8칸(0~7열)만 쓰도록 줄이고, 오른쪽 끝 칸(8번째 열)에 세로로 아이콘을 배치함: 8=내 경매, 17=우편함, 26=정렬(마감임박순→최신순→가격낮은순→가격높은순 순환), 35=검색 안내(명령어 사용법 표시, 실제 검색은 여전히 `/경매 검색 <키워드>`). 하단 페이지 이동 버튼도 가장자리(45/53) 대신 가운데 쪽(48=이전/49=닫기/50=다음)으로 옮김
- **내 경매 화면**도 메인 화면과 같은 8칸 그리드 + 48/49/50 하단 배치로 통일

### (후속) 경매장 배경 좌우 offset이 안 먹히던 버그 수정
- 원인: `auction.gui-background-offset` 기본값을 실수로 24(양수)로 넣어놨었는데, 이건 사실 좌우 offset이 아니라 v0id 팩 자체 설정의 세로 위치값(y_position)을 잘못 복사해온 값이었음. 24는 다른 GUI들이 쓰는 범위(-8~-50 정도, 전부 음수)와 완전히 다른 값이라 이미지가 화면 밖으로 많이 밀려나 있었고, 그 근처에서 값을 이리저리 바꿔봐도 여전히 화면 밖이라 "안 바뀐다"처럼 보였던 것
- 기본값을 다른 GUI들과 동일하게 -8로 수정 (config.yml 주석도 "세로"→"좌우"로 오타 수정)

### (후속) 경매장 아이콘 투명화 + 카테고리 필터 추가 + 배리어 제거
- 내 경매/우편함/정렬/검색/카테고리 5개 우측 아이콘을 전부 투명 아이콘으로 변경(배경에 이미 그려져 있는 그림 위에 아이템이 겹쳐 보이지 않도록)
- **카테고리 필터 신규 추가** — 44번 칸 클릭할 때마다 전체→무기→도구→방어구→블록→기타 순으로 순환하며 목록이 걸러짐(원작처럼 별도 화면이 아니라 정렬 버튼과 같은 방식으로 클릭 순환)
- **경매장 메인 화면**: 닫기 버튼(배리어)과 "표시할 경매가 없습니다" 문구 제거, 페이지 이동을 47(이전)/50(다음)으로 이동
- **내 경매 화면**: "돌아가기" 배리어 버튼과 "등록한 경매가 없습니다" 문구 제거, 26번 칸에 투명 아이콘으로 뒤로가기 추가, 페이지 이동을 47(이전)/50(다음)으로 이동

### 우편함(/우편함) GUI 재구성 — DailyRewards-0.2.3 팩 기반
- 우편함(YeowoolCore 공용 기능, 낙찰/유찰 경매 아이템·기타 오프라인 배송 등 여러 시스템이 같이 쓰는 그 우편함)에 GUI를 새로 입힘
- **받지 않은 우편이 unopened_box.png로 표시**됨(실제 아이템이 뭔지 바로 안 보이고 닫힌 상자로만 보임)
- **우클릭 = 미리보기** — 작은 화면이 뜨면서 실제로 뭐가 들어있는지 보여줌(수령은 안 됨, 그냥 구경만)
- **좌클릭 = 수령** — 기존처럼 바로 인벤토리로 들어옴
- 배경도 팩의 daily_reward_ui.png로 교체, 페이지 이동 버튼도 팩의 화살표 아이콘(daily_reward_previous/next)으로 교체
- `yeowool-core`가 이제 ItemsAdder API에 (선택적으로) 의존하게 됨 — ItemsAdder가 없어도 기존처럼 평범한 상자 아이콘+제목으로 정상 작동함

### (후속) 우편함 배리어 제거
- "받을 아이템이 없습니다"·"닫기" 배리어 아이콘 둘 다 제거 (그냥 ESC로 닫으면 됨)

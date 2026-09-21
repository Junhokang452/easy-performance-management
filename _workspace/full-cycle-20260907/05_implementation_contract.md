# 전체 평가 구현 계약 — 작업 소유권 및 필수 경계

## 공통

제품 패키지 `com.easyperformance`, HTTP `/api/v1`. 기존 ActorAccess.requireActor로 계정/직원/tenant 판정, HR_ADMIN/SUPER_ADMIN 운영자. MANAGER 직함만으로 모든 직원열람 금지: 배정/관계자/공개범위로 권한. 기존 API/보안/데이터 보존. 새 프로그램 도메인과 새 화면이 주 진입점. 원문 요구는 01~04_sources + ../member-cycle-analysis-20260907 참조.

BE 기록은 JPA 타입 엔티티·UUIDv7·TenantAwareAuditEntity·낙관잠금·tenant scoped repository. JSON은 타입이 있는 구성 스냅샷/항목응답/이력 등에만 사용하며 임의 문서 저장 API로 대체하지 않는다. 기존 com.fasterxml Jackson2 유지. ApiException/공유 error shape 사용. 새 enum은 별도 product error enum 가능(공통 core 수정 불필요). DTO 계약을 먼저 작성하고 FE에 경로 전달, 실제 최종 DTO를 기준으로 shape를 맞춘다.

## A — 평가 엔진 / backend_flow 소유

`backend/src/main/java/com/easyperformance/program/**`, 대응 tests, Flyway `V20260907_004__evaluation_programs.sql`.

- `/evaluation-programs`: 관리자 초안등록/목록, 배정된 구성원 OPEN 이후 목록, 프로그램 설정/복사/개요.
- 정의: 성과/역량 구분, 단계별 사용/기간, 목표합의·자기신고(중간OFF), 양식, 차수1..3, 항목의견설정, 입력척도와 결과척도 별개, 계산정책/조정계수/기준집단/부서성과, 공개정책/평균제한/조정등급제한.
- 그룹: 조건/우선순위, 절대·상대, 지정항목·합의목표, 그룹별 단계/차수 가중치(실제 평가자수별+부서성과합100), 공통항목/재반영, 부서성과그룹, 모집단크기별 정확 인원배분표.
- 피평가자 생성/추가/삭제/제외, 그룹조건매칭과배정, 평가자변경, 1..3차+조정자+최종피드백 담당. 조직·직원 검색은 기존 directory 재사용.
- 운영: OPEN/단계 시작·완료/차수전진, 개별 예외상태변경과 사유/이력, 알림은 실제 영속 알림함, 미완료자는 일괄에서 제외하고 처리/제외명세 반환.
- 목표: 부서·카탈로그 연결/달성수준/가중치100, 수정·의견임시저장·합의·반려·재요청·이력, 자기신고 확정.
- 중간: 과제/활동 근거 연결, 의견저장/완료, 점수미입력.
- 평가: 본인·평가자별 차수/항목응답 저장/완료/잠금, 이전차수공개설정 준수, 점수·등급척도·필수의견·가중치 검증.
- 계산/보정: 유형/차수/부서가중/조정계수 산식과 내역, 분모0/결측/동점 명시처리, 상대등급정원, 조정초안/완료/제한, 계산rev.
- 피드백/이의: 최종담당/HR작성, 구성원합의·이의, 정정·재계산·재발행 버전/감사, 완료후잠금.
- 마감과 공개 분리: FINALIZED 이후 결과별공개. 단순상태되돌림은마감차단, 감사사유있는마감취소/집계제거→재계산→재마감 별도command로 증거유지.
- 분석: 결과목록/피드백, 두평가등급매트릭스, 선택평가 맞춤집계, 결과현황, 평가자성향, 본인5년리포트. 허용된확정/공개결과만 대상, 비공개점수 DTO에서제거.
- 프로그램가이드첨부: private파일·다운로드 권한·크기제한. HTTP 상세 path/DTO는 program/ProgramDtos.java 등에서 먼저 고정.

## B — 목표 카탈로그·협업 기록 / auth_boundary_review 소유

`backend/src/main/java/com/easyperformance/resources/**`, 대응 tests, Flyway `V20260907_005__performance_resources.sql`.

- `/evaluation-resources/catalogs`: 성과/역량 항목, 분류/직무·부서할당, 정의, 달성수준, 복사/사용여부·순서. CRUD관리자, 구성원조회.
- `/evaluation-resources/department-goals`: 연도/기간/부서별목표, 항목/정의/가중치/수준/척도, 이관/복사/실적/달성현황. 구성원허용범위조회, 관리자편집.
- `/evaluation-resources/tasks`: 과제 보드+상세, 계획/진행/완료/폐기, CHECKLIST/ACTUAL 진척방식, 목표연계, 관계자(담당/관리/협업), 라벨, 활동/체크리스트/진척이력, 파일, 별점메시지피드백. 관계자외목록/상세/파일차단. 완료/폐기 재개는명시동작·기록. 과제자동평가점수화금지.
- `/evaluation-resources/interviews`: 대상/참조직원검색·기록, 대상자공개/참조자공개 별도, 작성/나의/직원별/참조된조회, 수정/공개변경 감사. 평가자가란이유로비공개면담조회불가.
- 파일은 opaque id로private DB bytea 또는 private path에저장, tenant/parent ACL검사후응답. clientfilename path사용금지, MIME/size검증, attachment+nosniff. 원격파일외부전송없음.
- typed integration service로 프로그램에서 카탈로그/부서목표선택 유효성/권한, 과제근거·공개면담 조회 지원. 인터페이스는A/B가직접즉시합의.

## C — 모든 제품 FE / frontend_completion 소유

frontend-vite/ 내부 새features/evaluation-programs, api, i18n, App routes/menu. root는FE파일수정안함. 5locale 새동일schema/키필수, 오류raw노출없음. 기존workflow코드보존, legacy흐름은구분된경로로유지.

- 내평가목록·사이클상세·목표편집/합의이력·실적근거·자기/차수평가·중간·보정·피드백/이의·결과.
- 관리자등록목록→설정탭(기본/단계/계산/척도/그룹/항목/부서성과/배분/공개)→운영업무함/직원미리보기→집계/통계.
- 별도항목라이브러리/부서목표/성과과제보드·상세/면담4탭/개인리포트/과거피드백. 기능별탭/route 분리. 원시UUID입력과JSON설정텍스트박스금지.
- 실제ReactQuery API 저장/재조회, 저장완료와제출완료분리, 불가행동설명, 빈값/오류/로딩, 모바일리플로, keyboard·labels.
- 전체FE를한genericform으로대체하지않는다. 기준/입력/근거비교화면과설정편집화면을실제업무에맞게구성한다.

## root — 통합·공유UI·검증

제품-local lib의 MasterDetailWorkspace 자산(토큰CSS+Storybook+export+사용계약), 필요시 중앙 같은신규asset만동기화. 백엔드prod/local프로파일 tenant마이그기대버전 _005 정합과 실행/시드/독립합성DB E2E는 root소유. 완료된자산과빌드후FE소비알림. 동시빌드/서버재시작은root만.

큰작업은하위슬라이스체크포인트로계속수행한다. 명세추가가필요하면서로통신, 허위완료/미연결버튼/권한없는모든데이터다운로드금지. 요구미구현을완료로세지않고수정·검증을이어간다.

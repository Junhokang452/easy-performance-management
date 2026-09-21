# S1 평가라인 자동화 — PA backend 구현/검증

작성: 2026-09-08  
상태: PA 제품 코드 구현 완료, 정적/단위/전체 빌드 통과. 최종 PostgreSQL/HTTP 결과 요약은 root 소유 산출물에 별도 기록.

## 구현 결과

- `POST /api/v1/evaluation-programs/{programId}/reviewer-line:preview`
- `POST /api/v1/evaluation-programs/{programId}/reviewer-line:apply`
- participant IDs 1~100 명시/중복 거부, roles 명시, `SELF`/`ADJUSTER` 422 차단.
- 프로그램 as-of date와 참가자 frozen assignment를 기준으로 양끝 inclusive 유효기간을 확인한다.
- 정확히 한 개의 유효 assignment, 인증된 `HCM` source, positive opaque sourceVersion, `deleted=false`, active direct manager, self/cycle/ambiguity 없음, REVIEWER 계획 1명·round 1·100%일 때만 READY다.
- 기존 non-REVOKED reviewer가 하나라도 있으면 참가자 전체 `SKIPPED_EXISTING`; 수동/Excel row는 revoke/update하지 않는다.
- preview hash에 tenant/program/as-of/definition revision, participant row version, roles, source ID/version/delete/manager, proposal/status/issues를 포함하고 apply에서 fresh revalidation한다.
- 동일 tenant/program/previewHash는 `program_reviewer_line_run`의 저장 응답을 반환한다. 프로그램 pessimistic write lock을 manual/Excel과 공유하며, run을 먼저 `saveAndFlush`한 뒤 reviewer FK를 생성한다.
- reviewer provenance는 `MANUAL | XLSX | HCM_MANAGER`와 source assignment/version/as-of/run ID로 저장한다.

## HCM read-model 수신 경계

- signed batch `tenantId`, assignment `managerEmployeeId`, nullable Boolean `deleted`를 additive하게 수용한다.
- opaque bearer 환경에서는 `performance.s2s.hcm.tenant-id` allowlist와 header/body tenant를 먼저 일치시킨 뒤에만 요청 범위 TenantContext를 설치한다.
- Model B가 켜졌으면 signed configured allowlist tenant가 control plane에서 ACTIVE여야 한다. 기존 동일-ID route도 `setByActiveId`로 ACTIVE를 재검증하고, context/route 불일치는 mutation 전 403/401로 차단한다. 설치/갱신한 route는 `finally`에서 기존 ID/code로 복원한다. 별도 approval flag 검사는 현재 lib API에 없다고 명시한다.
- legacy null body tenant는 기존 인증 tenant context가 있을 때만 기존 9필드 동기화를 유지하고 manager/delete capability는 켜지 않는다.
- 신규 rm row는 `JpaRepository.save` merge가 아니라 `EntityManager.persist` INSERT-only로 생성한다. 따라서 타 tenant 동일 global PK가 있으면 update가 아니라 PK 충돌/transaction rollback이다.
- tenant-scoped miss 뒤 global `existsById`가 이미 true면 세 rm entity 모두 typed `SYNC_TENANT_MISMATCH` 403으로 사전 거부한다. guard와 insert 사이 race는 여전히 `persist` PK 충돌/transaction rollback으로 안전하다.
- 기존 authenticated HCM row는 더 높은 sourceVersion만 update하며 legacy payload로 downgrade하지 않는다.
- 두 sync 진입 transaction은 PostgreSQL `REPEATABLE_READ`다. 동시 v2/v3 update에서 stale writer는 serialization conflict로 rollback되며 자동 HTTP 재시도는 하지 않는다.

## 변경 파일

PA main:

- `program/ProgramReviewerLineController.java`, `ProgramReviewerLineDtos.java`, `ProgramReviewerLineService.java`
- `program/ProgramReviewerLineRun.java`, `ProgramReviewerLineRunRepository.java`
- `program/ProgramReviewerAssignment.java`, `ProgramRosterService.java`, `ProgramTypes.java`, `ProgramErrorCode.java`, `ProgramJson.java`, `ProgramReviewSubmissionRepository.java`
- `sync/dto/SyncDtos.java`, `sync/controller/SyncReceiveController.java`, `sync/service/ReadModelSyncService.java`
- `readmodel/entity/RmAssignment.java`, `readmodel/repository/RmAssignmentRepository.java`
- `error/PerformanceErrorCode.java`, `application.yml`
- `db/migration/V20260908_001__reviewer_line_automation.sql`

Tests:

- `program/ProgramReviewerLineServiceTest.java` — 10
- `program/ProgramRosterConcurrencyTest.java` — 1
- `sync/controller/SyncReceiveControllerTest.java` — 12 total
- `sync/service/ReadModelSyncServiceTest.java` — 9 total

## Windows isolated verification

검증 사본:

`C:\Users\SAMSUNG\AppData\Local\Temp\easy-performance-validation-b1ce4a863a3c41ffb3bd65cfa5fe87a3\easy-performance-management\backend`

- Eclipse Temurin 21.0.12.1+1, Gradle 8.14.5.
- S1 targeted classes in final full run: **36/36 PASS** (reviewer-line 10, manual shared-lock 1, receiver 13, read-model sync 12).
- 전체 backend test(final ACTIVE-route 재검증 포함): **294/294 PASS**, failures 0, errors 0, skipped 0, test suites 47.
- `bootJar`: PASS, 72,533,389 bytes.
- JAR SHA-256: `1D3AABF5DCFEC0EFE13FF9DF39355FA185701F9CAA5990BF5E4C83F84483F454`.
- 원본 ↔ 검증 temp 변경 파일 SHA-256: **23/23 exact match** (full test/bootJar 입력과 현재 원본 일치).

## 12규칙 자기 점검

- tenant finder만 사용하고 신규 row는 persist-only: cross-tenant merge update 차단.
- 신규 인덱스는 모두 `tenant_id` 선두.
- employee assignment 이력 조회는 명시 participant 최대 100에 종속된 bounded 조회.
- 외부 호출 없음; controller context 설정 후 service transaction 진입.
- 엔티티 직접 HTTP 노출 없음; record DTO만 노출.
- 기존 수동/Excel `replaceReviewers`는 유지하고 origin만 가산했다.

## 별도 runtime 인계

- 전용 fresh PostgreSQL/HTTP 재검증과 browser/OpenAPI 증거의 최종 집계는 root/Terra 산출물에서 관리한다. 본 문서는 PA source/test/JAR 검증만 판정한다.

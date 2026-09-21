# S1 평가라인 자동화 — backend 계약 조사

작성: 2026-09-08  
상태: 읽기 전용 조사/권고 계약. 제품 코드는 변경하지 않음.

## 결론

현재 데이터만으로 HCM 관리자를 평가자로 안전하게 자동 배정할 수 없다. HCM의 `EmployeeAssignment`에는 `managerEmployeeId`, `effectiveFrom`, `effectiveTo`가 실제 존재하지만, 기존 HCM core-master 송신 DTO와 Performance의 `AssignmentUpsert`/`rm_assignment`에는 manager가 없다. 또한 HCM→Performance 송신기 자체가 아직 없다.

S1은 다음 두 경계를 함께 완성해야 한다.

1. HCM의 유효일자 기준 관리자 관계를 가산형 S2S 필드로 Performance read model에 전달한다.
2. Performance는 읽기 전용 preview와 명시적 apply를 분리하고, 기존 수동/Excel 평가자를 절대 덮어쓰지 않는다.

## 1. 실제 현재 계약

### Performance roster/reviewer

- `ProgramRosterService.generate`는 프로그램의 `asOfDate`에 유효한 발령을 찾는다. 조건은 `effectiveFrom <= asOfDate` 및 `effectiveTo == null || effectiveTo >= asOfDate`이고, 여러 건이면 `effectiveFrom`이 가장 늦은 한 건을 고른다 (`ProgramRosterService.java` 24, 45).
- 유효 발령이 없으면 참가자는 사원의 기본 조직으로도 생성될 수 있다. 참가자 snapshot에는 employee/assignment/org/position/grade/job/employmentType만 있고 manager는 없다 (`ProgramDtos.ParticipantAttributes`, `ProgramRosterService.java` 38, 46).
- 현재 평가자 쓰기 API는 `PUT /api/v1/evaluation-programs/participants/{id}/reviewers`이다. `replaceReviewers`는 기존 non-REVOKED 평가자를 전부 REVOKED로 바꾼 뒤 새 목록을 저장한다 (`ProgramRosterService.java` 28). 트랜잭션이어서 validation 실패 시 rollback되지만, 자동화에서 재사용하면 수동 배정을 지우게 된다.
- Excel import도 participant별로 같은 `replaceReviewers`를 호출한다 (`ProgramRosterService.java` 35). 수동 PUT과 Excel의 출처를 DB에서 구분하는 필드는 없다.
- 역할은 `SELF`, `AGREEMENT_REVIEWER`, `CHECKER`, `REVIEWER`, `ADJUSTER`, `FINAL_FEEDBACK`이다. REVIEWER는 1~3 round 연속성과 그룹별 weight plan이 필요하고, 나머지 역할은 round 0/weight 0이다 (`ProgramTypes.java` 20, `ProgramRosterService.java` 41).
- 현재 기본 구성은 direct manager 한 명으로 충족 가능한 REVIEWER 1명/100% 계획이다. 그러나 사용자 정의 그룹은 2~3명의 reviewer plan일 수 있으므로 direct manager 한 명만으로 임의 완성하면 안 된다 (`ProgramConfigurationFactory.java` 23~32).
- 기존 audit event `REVIEWERS_CHANGED`가 있고 수동 교체 시 count를 기록한다. 조회 projection은 민감한 details JSON을 내보내지 않는다.

### Performance HCM read model

현재 `SyncDtos.AssignmentUpsert`와 `RmAssignment` 필드는 다음뿐이다.

```text
id, employeeId, orgUnitId, positionCode, gradeCode, jobCode,
effectiveFrom, effectiveTo, sourceVersion
```

- manager, 삭제/tombstone, 관계 상태가 없다.
- `sourceVersion`이 null이거나 id가 null이면 row를 skip하고, 기존보다 큰 버전만 적용한다.
- 기존 row 조회가 `findById`라 tenant 조건이 없다. 글로벌 UUID라는 암묵 가정에 기대므로 신규 구현에서는 `findByIdAndTenantId`로 닫아야 한다.
- employeeId/date range/relation 참조를 row 단위로 검증하지 않는다. non-null id/version 외의 불량 row는 DB 제약으로 전체 트랜잭션을 실패시킬 수 있다.
- 삭제 전달이 없다. 송신측 soft-delete row가 snapshot에서 사라져도 Performance의 과거 read model은 남을 수 있다.

### HCM 원본과 실제 송신

- `EmployeeAssignment`에는 `employeeId`, `departmentId`, `jobId`, `gradeId`, **`managerEmployeeId`**, `title`, `roleCode`, **non-null `effectiveFrom`**, nullable `effectiveTo`, `legalEntityId`가 존재한다.
- HCM의 유효일자 의미도 양 끝 inclusive다: `effectiveFrom <= date`, `effectiveTo == null || effectiveTo >= date`. 조회는 `effectiveFrom DESC`; 서비스는 첫 행을 반환한다 (`EmployeeAssignmentRepository.findCurrentAtDate`, `EmployeeAssignmentService.java` 60~75).
- create/update는 date range와 같은 employee의 기간 중첩을 서비스에서 차단한다 (`EmployeeAssignmentService.java` 88~118, 310~317). DB exclusion constraint는 확인되지 않았다.
- manager는 nullable이며, manager 존재/활성, self-manager, 순환 관계를 검증하는 코드나 FK는 확인되지 않았다. 따라서 소비자가 신뢰하면 안 된다.
- 실제 `EasyTalentCoreMasterPushService`와 `StoreHrCoreMasterPushService`의 `AssignmentUpsertDto`는 Performance와 동일한 9필드이고 manager를 보내지 않는다. 매핑도 `managerEmployeeId`를 읽지 않는다 (`EasyTalentCoreMasterPushService.java` 247~258, 302~311; StoreHr 동형).
- HCM backend에서 `easyperformance`, `easy-performance`, `performance.s2s` 송신 설정/클래스는 검색 결과 0건이다. Performance 수신측 주석에도 송신 타깃은 별도 슬라이스라고 명시돼 있다.
- 기존 HCM sender는 `updatedAt.toEpochMilli()`를 `sourceVersion`으로 쓰고, 설정이 없으면 호출하지 않는 fail-closed 플러그인 패턴이다. S2S 호출은 snapshot 작성과 분리되어 있다.
- `EmployeeAssignment`는 soft-delete entity이고 repository `findAll()`은 `@SQLRestriction`에 의해 삭제 row를 숨긴다. 현재 snapshot 방식만 복사하면 tombstone이 전송되지 않는다.

## 2. 권고 S2S 계약

기존 wire를 깨지 않는 가산 변경으로 고정한다.

```json
{
  "tenantId": "authenticated tenant UUID",
  "assignments": [{
  "id": "assignment UUID",
  "employeeId": "employee UUID",
  "orgUnitId": "department UUID or null",
  "positionCode": "roleCode or null",
  "gradeCode": null,
  "jobCode": null,
  "managerEmployeeId": "direct manager employee UUID or null",
  "effectiveFrom": "YYYY-MM-DD",
  "effectiveTo": "YYYY-MM-DD or null",
  "deleted": false,
  "sourceVersion": 1788796800000000
  }]
}
```

- batch body에 `tenantId`를 가산하고, 기존 assignment 9필드에는 `managerEmployeeId`와 nullable `Boolean deleted`를 추가한다. legacy payload의 null tenant/null deleted는 기존 동기화 호환만 유지하고 auto-line capability는 false다.
- `sourceVersion`은 manager/date/delete 변경까지 반영하는 HCM assignment `updatedAt` epoch micros다. Performance는 이를 단조 증가 opaque `Long`으로만 비교하고 자체 timestamp로 재계산하지 않는다. 0/null 버전은 신규 자동화 입력으로 사용하지 않는다.
- `effectiveTo`는 inclusive. `asOfDate` 당일 종료 발령도 유효하다.
- soft delete는 `deleted=true` tombstone으로 보내야 한다. tombstone이 준비되기 전에는 stale active 관계를 구별할 수 없으므로 운영 자동 apply를 활성화하지 않는 것이 안전하다.
- HCM에는 기존 sender와 분리된 `EasyPerformanceCoreMasterPushService`/controller를 추가한다. 자동 scheduler는 S1 범위 밖이다. 설정 namespace는 `hcm.s2s.easyperformance.*`, target은 기존 Performance `POST /api/internal/sync/core-master`, `enabled=false` 기본이며 base-url/bearer-token/hmac-secret/tenant-id가 모두 있어야 수동 push한다.
- Performance 수신은 `manager_employee_id`, `deleted`, `source_version`을 tenant-bound upsert한다. `findByIdAndTenantId`를 사용하며 lower/equal version은 skip한다.
- read model에는 body가 주장하는 값이 아니라 인증된 수신 채널이 정한 `source_system`을 저장한다. 운영 HCM endpoint는 `HCM`, 로컬 fixture 전용 경로/시드는 `LOCAL_DEMO`다.
- HCM은 Model B per-tenant DB이므로 configured tenant-id가 인증된 `TenantContext`와 현재 Route에 일치할 때만 그 tenant DB snapshot을 보낸다. `X-Tenant-Uuid`와 서명된 body `tenantId`가 같아야 한다.
- Performance는 bearer/HMAC raw-body 검증 후 body `tenantId`와 `TenantSupport.currentTenantId()`가 같을 때만 확장 manager/delete capability를 켠다. current tenant 부재는 401, mismatch는 403이며 mutation은 0이다.
- HCM employee soft delete도 사라진 row로 누락하지 않고 employee snapshot `status=INACTIVE`로 전송하여 삭제된 관리자가 PA에 active로 남지 않게 한다.

### 로컬 관계 입력 원칙

- HCM sender가 없는 현재 상태에서 로컬 SQL/fixture로 manager 관계를 넣을 수는 있지만 이를 HCM 원본이라고 표시하면 안 된다.
- 로컬 자동화 시연 데이터는 `source_system=LOCAL_DEMO`와 별도 fixture sourceVersion을 저장하고 UI/감사에도 “로컬 데모 관계”로 표시한다.
- 사용자가 직접 지정한 관계는 read model을 위조하지 말고 기존 수동 reviewer PUT/Excel 경로를 사용한다. 즉 임의 manager 입력을 HCM automation preview의 정상 원본으로 승격하지 않는다.
- production apply는 `source_system=HCM`이며 tombstone까지 지원하는 row만 허용한다. LOCAL_DEMO는 local/dev profile에서만 허용한다.

## 3. 권고 preview/apply API

아래 계약으로 S1 구현 경계를 고정한다. 클라이언트는 `roles`를 명시하며 UI 기본 선택은 `REVIEWER` 하나다. 허용 자동 역할은 `AGREEMENT_REVIEWER`, `CHECKER`, `REVIEWER`, `FINAL_FEEDBACK`이고 `SELF`/`ADJUSTER`는 서버가 422로 거부한다. 한 요청의 모든 선택 역할에는 동일한 1차 직속상사만 제안하며, `REVIEWER`는 정확히 1명/round 1/100%인 프로그램 계획에서만 `READY`다.

### Preview

`POST /api/v1/evaluation-programs/{programId}/reviewer-line:preview`

```java
record ReviewerLinePreviewRequest(
    @NotEmpty @Size(max = 100) List<UUID> participantIds,
    @NotEmpty Set<ReviewerRole> roles
) {}

record ReviewerLinePreviewResponse(
    UUID programId,
    LocalDate asOfDate,
    int definitionRevision,
    String previewHash,
    Instant generatedAt,
    ReviewerLinePreviewSummary summary,
    List<ReviewerLinePreviewRow> rows
) {}

record ReviewerLinePreviewSummary(
    int total,
    int ready,
    int sourceMissing,
    int blocked,
    int skippedExisting
) {}

record ReviewerLinePreviewRow(
    UUID participantId,
    UUID participantEmployeeId,
    UUID participantAssignmentId,
    long participantRowVersion,
    UUID sourceAssignmentId,
    Long sourceVersion,
    String sourceSystem,
    LocalDate sourceEffectiveFrom,
    LocalDate sourceEffectiveTo,
    Boolean sourceDeleted,
    UUID proposedReviewerEmployeeId,
    String proposedReviewerName,
    int currentReviewerCount,
    List<ReviewerLineProposal> proposals,
    ReviewerLinePreviewStatus status,
    List<ReviewerLineIssue> issues
) {}

record ReviewerLineProposal(
    ReviewerRole role,
    int round,
    BigDecimal weightPercent
) {}

enum ReviewerLinePreviewStatus { READY, SOURCE_MISSING, BLOCKED, SKIPPED_EXISTING }
record ReviewerLineIssue(String code, String message) {}
```

- client가 as-of date를 정하지 않는다. 프로그램 `asOfDate`가 SSOT다.
- `participantIds`는 1~100개를 반드시 명시한다. 중복 ID, 타 tenant/program ID는 거부한다. 무제한 전체 preview는 제공하지 않는다.
- `roles`는 비어 있으면 안 된다. UI는 `[REVIEWER]`를 기본 전송한다. client는 round/weight/as-of date를 보내지 않으며 프로그램 `asOfDate`와 정의가 SSOT다.
- 원본 관계가 아직 수신되지 않은 경우 `SOURCE_MISSING`이며, 정상 후보나 일반 validation 실패로 바꾸지 않는다.
- row별 결손/불일치는 전체 4xx가 아니라 상태와 명시 사유로 반환한다. 단 요청 자체/권한/program 경계 오류는 일반 HTTP 오류다.
- 별도 preview token/version/expiry는 없다. `previewHash`와 `definitionRevision`/participant row version/source version이 버전 계약이며 apply 시 전부 재조회한다. 따라서 시간 만료가 아니라 데이터 변경이 stale 조건이다.

필수 blocker:

```text
NO_PARTICIPANT_ASSIGNMENT
PARTICIPANT_ASSIGNMENT_STALE
NO_EFFECTIVE_ASSIGNMENT
AMBIGUOUS_EFFECTIVE_ASSIGNMENT
MANAGER_MISSING
MANAGER_NOT_FOUND_OR_INACTIVE
SELF_MANAGER
SOURCE_TOMBSTONE_OR_STALE
SOURCE_LEGACY_UNSUPPORTED
EXISTING_REVIEWER_ASSIGNMENT
REVIEWER_WEIGHT_PLAN_INCOMPLETE
MANAGER_CYCLE
WORK_ALREADY_STARTED
```

발령 선택 규칙:

1. 참가자의 frozen `assignmentId`가 employee와 program `asOfDate`에 정확히 일치하는지 확인한다.
2. 같은 employee에 as-of 유효 row가 0건이면 block, 2건 이상이면 block한다. 현재 roster처럼 “최신 한 건”을 조용히 고르지 않는다.
3. manager가 null/self/missing/inactive이거나 manager chain에 cycle이 있으면 block한다.
4. 참가자 snapshot assignment와 현재 source assignment가 다르면 자동으로 갈아끼우지 않고 stale blocker를 낸다.
5. 어떤 non-REVOKED reviewer assignment라도 이미 있으면 출처/slot과 무관하게 참가자 전체를 `SKIPPED_EXISTING`으로 둔다. 수동/Excel/기존 자동 row를 섞거나 덮어쓰지 않는다.
6. `REVIEWER`는 participant group의 계획이 정확히 1명/round 1/100%일 때만 사용한다. 2~3 round/인원 계획이면 부분 weight를 만들지 않고 `REVIEWER_WEIGHT_PLAN_INCOMPLETE`로 block한다. non-reviewer 역할은 기존 규칙대로 round 0/weight 0이다.
7. source row의 `deleted`가 true면 tombstone blocker, null이면 구 wire에서 온 관계로 보고 `SOURCE_LEGACY_UNSUPPORTED`로 block한다. false만 자동화 입력으로 허용한다.

### Apply

`POST /api/v1/evaluation-programs/{programId}/reviewer-line:apply`

```java
record ReviewerLineApplyRequest(
    @NotBlank String previewHash,
    @NotEmpty List<UUID> participantIds,
    @NotEmpty Set<ReviewerRole> roles,
    @NotBlank @Size(max = 500) String reason
) {}

record ReviewerLineApplyResponse(
    UUID programId,
    LocalDate asOfDate,
    UUID automationRunId,
    int applied,
    int skipped,
    List<ReviewerLineApplyRow> rows
) {}

record ReviewerLineApplyRow(
    UUID participantId,
    ReviewerLineApplyStatus status,
    List<UUID> reviewerAssignmentIds,
    List<ReviewerLineIssue> issues
) {}

enum ReviewerLineApplyStatus { APPLIED, SKIPPED_EXISTING, SOURCE_MISSING, BLOCKED }
```

- operator의 명시 apply만 mutation한다. scheduler나 program open 시 암묵 실행하지 않는다.
- `previewHash`는 tenant(서버 내부), program ID/as-of/definition revision, 정렬된 participant IDs와 각 row version, 정렬된 roles, source assignment ID/source version/deleted/manager ID, 산출된 round/weight의 canonical serialization을 SHA-256으로 계산한다. tenant나 원본 payload 전체를 응답에 노출하지 않는다.
- apply는 참가자와 read-model row를 다시 읽고 위 요소를 전부 재검증한다. 하나라도 달라졌으면 mutation 전 요청 전체를 409 stale preview로 종료한다.
- apply mode는 **participant 단위 FILL_EMPTY**다. 어떤 non-REVOKED assignment라도 있으면 그 참가자는 `SKIPPED_EXISTING`; 기존 `replaceReviewers`는 호출하지 않는다.
- work가 시작됐거나 submission이 연결된 참가자는 `BLOCKED`이며 변경하지 않는다. `READY`만 명시적 apply에서 생성한다.
- 동일 `previewHash`+participant 집합 재호출은 같은 결과를 반환하는 idempotency key를 둔다. 동시 apply는 tenant/program/participant 잠금과 tenant-leading unique guard로 중복 생성을 차단한다.
- 성공 후 FE invalidation 대상은 program participants/reviewers, reviewer-line preview, program audit query다. 감사 조회 응답 shape는 기존 six-field projection을 그대로 사용하며 신규 event type만 `REVIEWER_LINE_APPLIED`로 추가한다.

## 4. 출처와 감사

현재 reviewer row에는 provenance가 없으므로 가산 컬럼이 필요하다.

```text
assignment_origin: MANUAL | XLSX | HCM_MANAGER
source_assignment_id: UUID nullable
source_version: bigint nullable
source_as_of_date: date nullable
automation_run_id: UUID nullable
```

- 기존 row는 migration default `MANUAL`; Excel import는 이후 `XLSX`; 자동 생성만 `HCM_MANAGER`다.
- 기존 active row는 origin과 무관하게 참가자 전체 skip한다. S1 자동화는 어떤 reviewer row도 revoke/update하지 않으며 새 `HCM_MANAGER` row만 빈 참가자에 생성한다.
- 신규 tenant-leading index와 slot concurrency guard를 둔다.
- apply 시 audit에는 run ID, as-of date, source watermark, applied/skipped counts와 reason을 저장한다. 조회 API의 six-field projection과 `detailsJson` 비노출은 유지한다.
- event type은 `REVIEWER_LINE_APPLIED`를 가산하는 편이 수동 `REVIEWERS_CHANGED`와 명확히 구분된다.

## 5. 오류/HTTP 정책

- preview의 데이터 결손/불일치는 200 + row blocker다.
- invalid role/config/request는 기존 domain validation 정책대로 422.
- stale preview/동시 변경은 409.
- 권한 없음 403, tenant-scoped program/participant 부재 404.
- HCM 채널 미설정은 기존 503, 인증/HMAC 실패는 기존 401을 유지한다.
- source payload parse 실패와 tenant mismatch는 적용 0이어야 하며 partial silent mutation을 허용하지 않는다.
- stale 전용 코드는 `REVIEWER_LINE_STALE("E9804941", 409)`를 권고한다. body는 공유 `ApiError` 표준 shape를 그대로 사용하고 `details.reason`에 `PREVIEW_HASH_MISMATCH`, `PARTICIPANT_VERSION_CHANGED`, `SOURCE_VERSION_CHANGED`, `PROGRAM_DEFINITION_CHANGED` 중 하나를 넣는다. 역할/participant 개수/중복 요청은 기존 `PROGRAM_INVALID("E9804256", 422)`다.

## 6. 구현 게이트와 테스트

S1 구현 전 게이트 상태:

1. HCM sender/tombstone 별도 저장소 소유권: 승인됨(root 소유).
2. 역할 입력: 명시 roles, UI 기본 `REVIEWER`, `SELF`/`ADJUSTER` 거부로 확정.
3. legacy reviewer row: migration에서 `MANUAL`로 보존하고 자동화가 변경하지 않는 것으로 확정.
4. 코드 착수: Luna 표준 게이트 PASS 통보 대기.

최소 회귀 테스트:

- as-of 시작일/종료일 inclusive, 종료 다음 날 제외.
- 0건/중첩 2건 발령은 blocker이고 apply 0.
- manager null/self/missing/inactive/tombstone 각각 blocker.
- 참가자 frozen assignment mismatch blocker.
- MANUAL/XLSX slot 무변경, HCM_MANAGER empty slot만 생성.
- 1명/100% plan 성공, 2~3명 plan은 불완전 blocker.
- preview 후 sourceVersion/rowVersion 변경 시 apply 409.
- 동일 apply 재시도 멱등, 동시 apply 중복 0.
- tenant A preview/apply로 tenant B read model/reviewer 접근 불가.
- HCM 설정 미존재 시 외부 호출 0; 설정 시 manager/date/delete/sourceVersion exact DTO 계약.
- 기존 manual PUT, participant/reviewer Excel import/export, reviewer 조회, audit 조회 계약 회귀 통과.

## 범위 판정

- Performance 단독 구현만으로는 안전한 S1 완료가 불가능하다.
- 최소 변경 범위는 HCM sender DTO/송신기 + Performance read model/receiver + Performance preview/apply/provenance다.
- 기존 수동/Excel 경로는 삭제·대체하지 않고 그대로 유지한다.

### 구현 파일 경계

Performance 소유 변경 후보:

- 수정: `sync/dto/SyncDtos.java`, `sync/controller/SyncReceiveController.java`, `sync/service/ReadModelSyncService.java`
- 수정: `readmodel/entity/RmAssignment.java`, `readmodel/repository/RmAssignmentRepository.java`, `readmodel/entity/RmEmployee.java`(필요 시 source capability/status 보강)
- 신규: `program/ProgramReviewerLineController.java`, `program/ProgramReviewerLineService.java`, `program/ProgramReviewerLineDtos.java`
- 수정: `program/ProgramReviewerAssignment.java`, repository, `ProgramTypes.java`, `ProgramErrorCode.java`; 기존 `ProgramRosterService.replaceReviewers`는 변경하지 않는다.
- forward-only Flyway: rm assignment manager/delete/source-system/capability와 reviewer provenance/idempotency/concurrency를 위한 신규 migration 1개.
- 테스트: `ReadModelSyncServiceTest`, `SyncReceiveControllerTest`, 신규 `ProgramReviewerLineServiceTest`/controller test.

HCM 소유 변경 후보(별도 병렬 소유, 본 문서 작업에서는 미수정):

- 신규 `backend/src/main/java/com/easyhcm/backend/sync/EasyPerformanceCoreMasterPushService.java`
- 신규 `backend/src/main/java/com/easyhcm/backend/sync/EasyPerformanceCoreMasterPushController.java`
- 수정 S2S HMAC/config와 `application.yml`의 `hcm.s2s.easyperformance.*` 기본 OFF 설정.
- soft-deleted assignment tombstone과 employee inactive snapshot을 읽는 명시 repository query 또는 outbox/watermark seam. 일반 `findAll()`로 삭제 row를 읽지 않는다.
- 대응 service/controller/security/tenant-route 테스트. 외부 발신과 자동 scheduler는 S1 검증에서 활성화하지 않는다.

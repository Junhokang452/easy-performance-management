# S2 KPI 연계 백엔드 계약 초안

작성: 2026-09-08  
상태: Phase 3a 설계 게이트용 — 제품 코드 미수정

## 1. 결론

S2의 최소 안전 범위는 **평가 프로그램의 한 참가자 목표와 기존 KPI assignment를 운영자가 명시적으로 연결하고, 선택한 기준일의 KPI 원본·실적 leaf·서버 산식 결과를 불변 snapshot으로 적용**하는 것이다.

- apply는 KPI 원본(`kpi_node`, `kpi_assignment`, `kpi_actual`)을 수정하지 않는다.
- apply는 `ProgramGoal.weightPercent/targetValue/achievementLevels/achievedLevelCode`를 덮어쓰지 않는다.
- apply는 `ProgramCalculation`을 만들거나 참가자 stage/status를 전이하지 않는다.
- 계산된 `autoScore`는 **추천/근거 값**이다. 기존 최종 점수에 암묵 반영하지 않는다.
- 기존 수동/Excel 참가자·평가자 배정, 목표 입력, 리뷰 제출, 계산/조정/확정 API 계약은 그대로 보존한다.

점수 반영은 아래 §9의 정책 결정 전에는 금지한다. 단순 `goal.kpiAssignmentId` 추가만으로는 현재 score path와 연결되지 않으므로 S2 목표를 충족하지 못한다.

## 2. 실제 코드 근거와 발견한 단절

### KPI SoT

- `domain/kpi/entity/KpiAssignment.java`: KPI node × employee 배정. 개인 `weight`, `targetOverride`; null이면 node 값을 승계한다. `@Version`은 없고 audit `updatedAt`만 있다.
- `domain/kpi/entity/KpiActual.java`: `asOfDate`, `actualValue`, `source`, `evidenceUrl`, `comment`, `supersedesId`를 가진 append-only 이력이다.
- `domain/kpi/service/KpiService.java`: 현재 유효 target/weight와 실적으로 `achievementRate = actual / target`을 소수 6자리 `HALF_UP`으로 계산한다.
- `domain/review/service/ReviewService.java`: 기존 서버 산식은 `autoScore = clamp(round(achievementRate × 100, 2), 0, 100)`이고, manager 제출 때 target/weight/actual/산식 결과를 `kpi_score_detail`에 동결한다. KPI 전체 점수는 값이 있는 항목만 `Σ(itemScore×weight)/Σ(weight)`이다.

### Program 쪽 실제 연결

- `program/EvaluationProgram.java`: 조직 스냅샷용 `asOfDate`, 운영 기간 `startsOn/endsOn`, `definitionRevision`, `rowVersion`이 있다.
- `program/ProgramParticipant.java`: employee/assignment 스냅샷과 `rowVersion`이 있다.
- `program/ProgramGoal.java`: `catalogItemId`, `departmentGoalId`만 있고 KPI assignment 연결은 없다. goal 자체에는 `revision`과 `rowVersion`이 있다.
- `program/ProgramExecutionService.raw()`: 리뷰어 제출 점수와 선택적 부서성과만 `ScoreContribution`으로 만든다. `ProgramGoal`은 이 계산 경로에서 사용되지 않는다.
- `program/ProgramCalculation.java`: revision별 append-only 결과와 contributions/formula/warnings를 이미 동결하지만 KPI 원본 근거 필드는 없다.
- `program/EvaluationProgramRepository.findLocked`: 프로그램 단위 `PESSIMISTIC_WRITE`가 존재한다. S2 apply의 멱등/동시성 직렬화에 재사용한다.

### 선행 결함

현재 `KpiService.latestActual()`은 `supersedes_id IS NULL`인 root만 조회한 뒤 다른 행이 그 root를 supersede했으면 제외한다. successor는 처음부터 조회 집합에 없으므로 **정정본 leaf가 latest가 될 수 없다**. S2는 이 메서드를 그대로 호출하지 않는다.

S2의 bounded dependency로 모든 actual 이력에서 leaf를 고르는 공용 selector를 추가하고, `/kpi-assignments/my`도 같은 selector를 사용하도록 바꾸는 것을 권고한다. 회귀 테스트는 `root → successor` 정정본이 latest가 되는 1건 이상을 포함한다. KPI 전체 리팩터링은 하지 않는다.

## 3. 기준일과 실적 선택 규칙

`EvaluationProgram.asOfDate`는 참가자 조직/발령 스냅샷 기준일이므로 KPI 실적 cutoff로 재사용하지 않는다.

클라이언트는 `actualCutoffDate`를 반드시 명시한다.

1. `actualCutoffDate`는 `program.startsOn..program.endsOn`, 선택한 `EvaluationCycle.periodStart..periodEnd`, 서버 business date(`LocalDate.now(injectedClock)`) 이하의 교집합 안이어야 한다(inclusive). 미래 실적 cutoff는 허용하지 않는다.
2. KPI assignment는 같은 tenant, 참가자의 frozen `employeeId`, 요청 `cycleId`에 속해야 한다.
3. assignment별 **전체 actual 이력**에서 다른 행이 `supersedesId`로 가리키지 않는 current leaf를 먼저 판정한다.
4. 그 current leaf 중 `asOfDate <= actualCutoffDate`인 행만 eligible이다. 미래 날짜 successor가 있는 chain의 과거 root를 cutoff 때문에 되살리지 않는다.
5. eligible인 독립 leaf가 여러 개면 `asOfDate DESC, createdAt DESC, id DESC` 첫 행을 선택한다.
6. actual이 없으면 `SOURCE_MISSING`; target이 null 또는 0이면 `BLOCKED`이다. 음수 actual은 기존 산식대로 계산 후 0으로 clamp한다.

이 규칙은 **apply 시점에 현재 알려진 정정 이력 중 업무 기준일 이하 값**을 고른다. 과거 시점에 실제로 무엇이 알려져 있었는지를 재현하는 bitemporal 계약은 아니다. node/assignment의 target/weight 이력도 없으므로 “기준일 당시 target/weight”라고 표현하지 않고, `capturedAt` 시점의 current 값을 고정한다.

## 4. API 계약

base path는 기존 controller와 같은 `/api/v1/evaluation-programs`다. 전부 JWT actor와 tenant를 사용한다. 후보/preview/apply는 HR_ADMIN/SUPER_ADMIN 운영자 전용이고 history는 self/active assigned reviewer도 읽을 수 있다. arbitrary `employeeId` 입력은 받지 않는다.

### 4.1 후보 조회

`GET /api/v1/evaluation-programs/{programId}/participants/{participantId}/kpi-candidates?cycleId={uuid}&actualCutoffDate=YYYY-MM-DD&page=0&size=50`

- `size`: 1..100. tenant/program/participant/employee 범위를 서버가 고정한다.
- 응답: Spring `Page<KpiCandidateResponse>`.

```text
KpiCandidateResponse {
  kpiAssignmentId: UUID
  kpiNodeId: UUID
  nodeLabel: string
  treeId: UUID
  treeName: string
  cycleId: UUID
  cycleName: string
  employeeId: UUID
  effectiveWeight: decimal
  effectiveTarget: decimal|null
  unit: string|null
  nodeSource: MANUAL|HCM|EXTERNAL
  latestActualId: UUID|null
  latestActualAsOfDate: date|null
  latestActualValue: decimal|null
  latestActualSource: MANUAL|AUTO|IMPORT|null
  achievementRate: decimal|null
  autoScore: decimal|null
  availability: READY|SOURCE_MISSING|BLOCKED
  reasonCode: string|null
}
```

후보 조회는 선택 편의용 live read일 뿐 snapshot이나 stale 보증이 아니다.

### 4.2 미리보기

`POST /api/v1/evaluation-programs/{programId}/participants/{participantId}/goals/{goalId}/kpi-link:preview`

```text
KpiLinkPreviewRequest {
  cycleId: UUID                            // required; 자동 추론 금지
  actualCutoffDate: date                   // required
  kpiAssignmentId: UUID                    // required
}
```

- 한 요청은 한 goal↔한 KPI assignment만 다룬다. goal A refresh가 goal B의 active evidence를 바꾸지 않는다.

```text
KpiLinkPreviewResponse {
  programId: UUID
  participantId: UUID
  cycleId: UUID
  programAsOfDate: date                    // 조직 기준일, 표시 전용
  actualCutoffDate: date
  programDefinitionRevision: int
  programRowVersion: long
  participantRowVersion: long
  previewHash: string                      // SHA-256 lowercase hex
  capturedAt: instant
  row: KpiLinkPreviewRow
}

KpiLinkPreviewRow {
  goalId: UUID
  goalRevision: int
  goalRowVersion: long
  goalTitle: string
  goalWeightPercent: decimal
  kpiAssignmentId: UUID
  kpiAssignmentUpdatedAt: instant
  kpiNodeId: UUID
  kpiNodeUpdatedAt: instant
  nodeLabel: string
  treeId: UUID
  treeName: string
  cycleId: UUID
  employeeId: UUID
  effectiveWeight: decimal
  effectiveTarget: decimal|null
  unit: string|null
  nodeSource: MANUAL|HCM|EXTERNAL
  actualId: UUID|null
  actualAsOfDate: date|null
  actualValue: decimal|null
  actualSource: MANUAL|AUTO|IMPORT|null
  actualCreatedAt: instant|null
  actualSupersedesId: UUID|null
  achievementRate: decimal|null            // actual/effectiveTarget, 6dp HALF_UP
  autoScore: decimal|null                  // rate*100, 2dp HALF_UP, clamp 0..100
  formulaVersion: "KPI_ACHIEVEMENT_V1"
  formula: "clamp(round(round(actualValue/effectiveTarget,6)*100,2),0,100)"
  status: READY|SOURCE_MISSING|BLOCKED
  reasonCode: string|null
}
```

`previewHash`에는 tenant/program/participant/goal/cycle/cutoff, **현재 goal evidence ID**, program definition+row version, participant row version, goal revision+row version, assignment/node updatedAt와 값, 선택 actual id/asOf/value/source/createdAt/supersedesId, formulaVersion, status/reason을 모두 넣는다. 현재 evidence ID가 들어가므로 A→B→A 재연결은 새 hash/revision이 된다. 표시명만 바뀌어도 근거 snapshot이 달라지므로 node/goal 제목도 포함한다.

### 4.3 명시 적용

`POST /api/v1/evaluation-programs/{programId}/participants/{participantId}/goals/{goalId}/kpi-link:apply`

```text
KpiLinkApplyRequest {
  cycleId: UUID
  actualCutoffDate: date
  kpiAssignmentId: UUID
  previewHash: string                      // 64-char lowercase hex
  reason: string                           // 1..500
}
```

apply는 같은 program row를 `findLocked`로 잠그고 participant/goal/assignment/node를 tenant-bound로 다시 읽은 뒤 preview를 재생성한다. hash가 다르면 아무것도 쓰지 않고 409 `PROGRAM_KPI_LINK_STALE`이다. row가 READY일 때만 적용하고 missing/blocked는 422로 거부한다.

```text
KpiLinkApplyResponse {
  evidenceId: UUID
  programId: UUID
  participantId: UUID
  goalId: UUID
  cycleId: UUID
  revision: int
  supersedesEvidenceId: UUID|null
  actualCutoffDate: date
  previewHash: string
  appliedAt: instant
  appliedByEmployeeId: UUID|null
  active: boolean
  evidence: KpiLinkPreviewRow
  sourceSnapshot: KpiSourceSnapshot
  reason: string
}

KpiSourceSnapshot {
  programDefinitionRevision: int
  programRowVersion: long
  participantRowVersion: long
  goalRevision: int
  goalRowVersion: long
  kpiAssignmentUpdatedAt: instant
  kpiNodeUpdatedAt: instant
  actualId: UUID
  actualCreatedAt: instant
}
```

저장된 `previewHash`의 apply 재요청은 fresh hash 검증 전에 기존 evidence ID를 그대로 반환한다. 그 evidence가 더 이상 current가 아니면 `active=false`이며 재활성화하지 않는다. 새 hash의 명시 apply는 그 goal에만 새 immutable revision을 만들고 이전 current evidence를 `supersedesEvidenceId`로 가리킨다. 원본 evidence는 UPDATE/DELETE하지 않는다. 따라서 실적 정정 뒤 refresh와 goal↔assignment 교체는 반드시 새 preview + reason + apply를 거치며 다른 goal link는 유지된다.

### 4.4 적용 이력 조회

`GET /api/v1/evaluation-programs/{programId}/participants/{participantId}/goals/{goalId}/kpi-links?page=0&size=20`

응답은 revision 내림차순 `Page<KpiLinkApplyResponse>`이고 `size`는 1..100이다. 최신 row만 `active=true`다. HR/SUPER와 participant self/active assigned reviewer가 읽을 수 있고, 다른 참가자·다른 program·다른 tenant에는 404/403 경계를 유지한다.

## 5. 저장 모델

신규 goal별 append-only `program_kpi_evidence` 모델을 권고한다.

- UUIDv7 PK, tenant/program/participant/goal/cycle/cutoff/KPI IDs와 scalar snapshot 필드, `source_snapshot jsonb`, formula/version, revision, previewHash, reason, actor, capturedAt, `supersedes_evidence_id`.
- `UNIQUE(tenant_id,goal_id,revision)`, `UNIQUE(tenant_id,goal_id,preview_hash)`, `UNIQUE(supersedes_evidence_id)`로 goal별 선형 revision과 retry를 보장한다.
- active evidence는 “다른 evidence의 `supersedes_evidence_id`가 가리키지 않는 leaf”로 판정한다. apply는 program lock 아래 goal revision을 증가시켜 동시 retry를 직렬화한다.
- UUIDv7 PK와 tenant_id 선두 인덱스를 사용한다.
- program/participant/goal FK는 cascade 가능하나 KPI assignment/actual에는 FK를 두지 않는다. KPI 원본 삭제 뒤에도 동결 근거 ID와 값이 남아야 한다.
- UPDATE/DELETE repository 메서드를 제공하지 않는다.

KPI 원본에는 신규 연결 컬럼을 추가하지 않는다. 기존 데이터 이력은 그대로 유지한다.

## 6. 권한·상태·오류

- 후보/preview/apply는 `ActorAccess.requireActor` + `ProgramAccess.requireOperator`.
- history read는 `ProgramAccess.participant`로 HR/SUPER 또는 participant self/active assigned reviewer만 허용한다. 같은 DTO를 반환하되 이는 이미 동결된 평가 근거이며 tenant/program/participant 소속 검증을 선행한다.
- 모든 조회는 `tenant_id`와 program/participant 소속을 함께 확인한다. 다른 tenant 또는 다른 program participant는 404로 숨긴다.
- apply/refresh 허용: program `OPEN`, participant `ACTIVE`, 선택한 goal이 `AGREED` 또는 `SELF_REPORTED`, 아직 `REVIEWER` 역할의 `COMPLETED` submission이 없고 `ProgramCalculation`도 없는 동안이다. 실적이 목표 합의 이후 생기는 정상 흐름을 지원하며, 자기평가 완료만으로는 막지 않는다.
- 목표가 DRAFT/RETURNED/AGREEMENT_REQUESTED이면 row `BLOCKED/GOAL_NOT_FROZEN`; apply는 422.
- 첫 reviewer 제출 완료 뒤에는 해당 reviewer가 본 근거를 바꾸지 않도록 409 `PROGRAM_LOCKED`이다. 기존 submit/goal 상태전이 계약은 변경하지 않는다.
- 기존 `PROGRAM_FORBIDDEN(E9804302)`, `PROGRAM_INVALID(E9804256)`, `PROGRAM_LOCKED(E9804937)`는 유지한다.
- 신규 제안: `PROGRAM_KPI_LINK_NOT_FOUND(E9804459,404)`, `PROGRAM_KPI_LINK_STALE(E9804942,409)`, `PROGRAM_KPI_LINK_CONFLICT(E9804943,409)`.
- audit: `ProgramEventType.KPI_LINK_APPLIED`; programId/participantId, reason, snapshot IDs, cycleId, cutoffDate, previewHash만 기록한다. 민감한 evidence/comment 원문은 audit detail에 복제하지 않는다.

## 7. 동시성·재현성

- apply는 program pessimistic lock을 먼저 얻고 그 안에서 stored-hash replay를 재확인해 동일 동시 요청이 같은 evidence를 받는다. revision/active 판정은 goal별이므로 다른 goal evidence는 유지된다.
- reviewer submission `complete=true` 경로도 같은 program lock을 먼저 얻는다. 따라서 apply가 먼저면 근거가 확정된 뒤 reviewer 완료가 진행되고, reviewer 완료가 먼저면 apply가 `KPI_EVIDENCE_FROZEN`으로 차단된다. draft save는 이 freeze를 만들지 않는다.
- apply 시 goal/assignment/node도 locked finder로 읽는다. assignment lock은 동시 actual insert/supersede FK 작업과 순서를 형성한 후 전체 actual을 다시 읽는다.
- KPI entity에 새 `@Version`을 광범위하게 추가하지 않고, locked read + canonical hash 재검증으로 bounded 처리한다.
- snapshot은 계산에 필요한 값을 모두 보관하므로 이후 KPI target/weight/actual 정정이 기존 snapshot을 바꾸지 않는다.
- 기존 KpiActual leaf selector는 tenant+assignment bounded 전체 이력만 읽는다. 무제한 tenant 전체 조회는 금지한다.

## 8. 기존 산식 재사용 방식

중복 계산을 피하려고 순수 `KpiScorePolicy`(이름 제안)를 추출한다.

- `achievementRate(actual,target)`: target null/0 또는 actual null이면 null; 6dp HALF_UP.
- `autoScore(rate)`: null 유지; ×100, 2dp HALF_UP, [0,100] clamp.
- 필요 시 `weightedAverage(items)`: score가 있는 항목만 분모 포함, 2dp HALF_UP.

`KpiService`, legacy `ReviewService`, 신규 program KPI service가 이 순수 정책을 호출한다. 기존 manager override와 submit/finalize 상태 전이는 재사용하거나 호출하지 않는다.

## 9. 확정한 점수 경계

이번 S2는 evidence-only로 확정한다. `autoScore`를 configurable program의 최종 `ProgramCalculation`에 넣지 않는다. 현재 `componentWeights`가 validation/JSON에는 존재하지만 `ProgramExecutionService.raw()`에서 실제로 사용되지 않고, 기본 `PERFORMANCE=100`의 의미도 리뷰 점수와 KPI 점수 중 무엇인지 확정돼 있지 않기 때문이다. 임의로 KPI를 더하거나 reviewer 점수를 대체하면 기존 프로그램 결과를 바꾼다.

추후 점수 반영을 원하면 별도 명시 정책으로 다음을 먼저 고정해야 한다.

- KPI vs reviewer component의 weight 합계와 기존 프로그램 migration/default.
- KPI goal 여러 개의 집계 weight(ProgramGoal weight인지 KPI effective weight인지).
- missing KPI의 분모 제외/계산 차단 정책.
- 상대평가 normalization 전/후 어느 단계에 KPI component를 넣는지.

별도 정책 결정 전에는 FE도 `autoScore`를 “KPI 자동 산출 근거”로만 표시하고 final score로 표시하지 않는다. 현재 S2 구현을 위해 추가 사용자 결정은 없다.

## 10. 예상 변경 파일(승인 후)

제품 Java/DB:

- `program/ProgramKpiEvidence.java`, repository, controller/service, DTOs
- `program/ProgramTypes.java`, `ProgramErrorCode.java`
- `domain/kpi/service/KpiScorePolicy.java` 및 bounded leaf selector
- `domain/kpi/repository/KpiActualRepository.java`, `KpiAssignmentRepository.java`, `KpiNodeRepository.java`
- `domain/kpi/service/KpiService.java`, `domain/review/service/ReviewService.java`(공용 산식 위임만)
- `db/migration/V20260908_002__program_kpi_evidence.sql`

테스트:

- preview/apply tenant·권한·program/participant/employee/cycle/date 검증
- missing target/actual, superseded leaf, cutoff 경계/tie-break
- stale hash, 동일 retry, 동시 apply, cross-program/cross-tenant
- goal/assignment 중복 차단, 원본 불변, calculation/stage 불변
- legacy `/kpi-assignments/my` successor latest 회귀
- existing ReviewService 산식 회귀

S1/HCM/easy-platform-core, 알림, XLSX, PDF, frontend 코드는 이 백엔드 소유 범위에서 수정하지 않는다.

## 11. 12개 규칙 자기 점검

- #1: nullable date JPQL을 쓰지 않고 cutoff 필수 non-null query로 분리한다.
- #2: 신규 인덱스는 tenant_id 선두다.
- #3: 후보는 Pageable(size≤100), preview/apply는 단일 goal/assignment라 bounded다.
- #4: DTO mapping은 transaction 안에서 완료하며 lazy entity를 controller에 노출하지 않는다.
- #10: 모든 repository 변형은 tenant 조건을 포함한다.
- #12: Controller → Service → Repository, entity 직접 노출 없음.

Boot 4.1.1/Java 21/Gradle Kotlin DSL과 현재 S1 dirty tree는 그대로 유지한다.

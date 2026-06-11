# P0-S4 계약서 — RatingDistribution + CalibrationSession + 강제 분포 시뮬레이터

> 작성: 2026-06-11 / 작성자: easy-suite-orchestrator / BE·FE 에이전트 공동 SoT — **이탈 금지**
> ERD SoT: `_workspace/evaluation_research_2026-06-11/03_domain_model_and_screens.md` Entity 16/17 (line 103~112) + 화면 #19/#24/#28 (line 252/262/266) + 시나리오 C.5 (line 545~554)
> 결정 정합: G_PERF_E1 = HYBRID 옵션 (FORCED/HYBRID 만 강제 적용, ABSOLUTE 거부) + G_PERF_E6 = 분담 (performance Calibration / talent 9-Box — talent 채널 비접촉)
> 선행 의존: P0-S1 policy (distributionMode + forcedDistribution + ratingScale) + P0-S3 reviews (CALIBRATION 상태군 + finalGrade + 밴드)
> **스코프 한정**: org 단위 분포는 P0-S6(rm_org_unit 수신) 이후 — **P0-S4 는 전사(orgUnitId NULL) 만**. 드래그 등급 이동은 P1 보류 (Select/Menu 로 대체). 화면 번호 정정: slices.md "#19/#28" → 실제 카탈로그 #19 + #24 + #28 (3 라우트).

## 0. 설계 결정 3건 (checkpoint 질문 고정)

1. **시뮬레이션 저장 정책**: `simulate` = 무저장 순수 계산 endpoint. `apply` 시점에만 RatingDistribution upsert + `simulation_log` jsonb append (entry: `{at, actorEmployeeId, targetDistribution, appliedCount, skippedCount, resultingDistribution}`).
2. **forced 적용 잠금**: `cycle.status == CALIBRATION` 한정 + 대상 review 는 `status == CALIBRATION` 인 행만 (그 외 skip 카운트). 재적용 허용 (각 apply 가 log append — 멱등 아님·이력 보존). cycle FINALIZED 후엔 stage mismatch 거부.
3. **UNIQUE 정책**: RatingDistribution = partial unique 2개 — `(tenant_id, cycle_id) WHERE org_unit_id IS NULL` + `(tenant_id, cycle_id, org_unit_id) WHERE org_unit_id IS NOT NULL`. CalibrationSession = UNIQUE 없음 (cycle 당 다중 세션 허용), index `(tenant_id, cycle_id, owner_org_unit_id)`.

## 1. Entity 2건

### CalibrationSession (`calibration_session`)
- `id` UUID PK (UuidV7) / `tenant_id` (TenantAwareAuditEntity)
- `cycle_id` UUID NOT NULL FK → evaluation_cycle **ON DELETE CASCADE**
- `owner_org_unit_id` UUID NULL (P0-S6 전 plain UUID)
- `status` varchar(20) NOT NULL enum `CalibrationStatus`: PLANNED | IN_SESSION | ADJUSTED | CONFIRMED | CLOSED
- `scheduled_at` timestamptz NULL
- `participant_ids` jsonb NULL — UUID 배열 (P0-S1 D2 JSONB 패턴)
- `adjustment_log` jsonb NULL — append 배열, entry shape = `{at, actorEmployeeId, reviewId, employeeId, fromGrade, toGrade, reason}`
- `confirmed_at` timestamptz NULL / `confirmed_by` UUID NULL

### RatingDistribution (`rating_distribution`)
- `id` / `tenant_id` / `cycle_id` UUID NOT NULL FK → evaluation_cycle **ON DELETE CASCADE**
- `org_unit_id` UUID NULL (NULL = 전사 — P0-S4 는 NULL 행만 생성)
- `policy_distribution` jsonb NULL (마지막 apply 에 사용된 target `{S:0.1,A:0.25,...}`)
- `actual_distribution` jsonb NULL (마지막 apply 결과 분포 `{S:5,A:12,...}` — 건수)
- `forced_applied` boolean NOT NULL DEFAULT false
- `applied_at` timestamptz NULL / `applied_by` UUID NULL (ERD 외 additive — audit)
- `simulation_log` jsonb NULL — append 배열 (§0-1 entry shape)

## 2. Flyway

`backend/src/main/resources/db/migration/V20260611_004__calibration_distribution.sql` — V20260611_003 의 audit 컬럼·jsonb DDL 패턴 복제 + §0-3 partial unique 2개.

## 3. 상태기계 + 게이트

### CalibrationSession 전이 (transition endpoint)
| 전이 | 필요 cycle.status | 비고 |
|------|------------------|------|
| PLANNED → IN_SESSION | CALIBRATION | 회의 시작 |
| IN_SESSION → CONFIRMED | CALIBRATION | confirm endpoint 전용 (transition 으로 불가 — E9804246) |
| ADJUSTED → CONFIRMED | CALIBRATION | confirm endpoint 전용 (동일) |
| CONFIRMED → CLOSED | 무관 | 종결 |
- IN_SESSION → ADJUSTED 는 **명시 전이 불가** — adjustments API 첫 호출 시 서비스가 자동 승격
- 그 외 전이 → E9804246 (422)

### cycle 단계 게이트
| 동작 | 필요 cycle.status |
|------|------------------|
| 세션 생성 / PATCH / 목록·상세 조회 | 생성·PATCH 는 FINALIZED·CANCELLED 외 전부 허용 (사전 일정 등록), 조회 무관 |
| IN_SESSION 진입 / adjustments / confirm / simulate / apply | **CALIBRATION 한정** (위반 E9804250) |
- PATCH 는 session.status == PLANNED 한정 (위반 E9804931)

## 4. 검증 규칙 + ErrorCode 10건

| 규칙 | ErrorCode | HTTP |
|------|-----------|------|
| session 없음 | CALIBRATION_SESSION_NOT_FOUND `E9804448` | 404 |
| 허용 외 session 전이 (confirm 전용 전이를 transition 으로 시도 포함) | CALIBRATION_INVALID_STATUS_TRANSITION `E9804246` | 422 |
| target 분포 무효 (키 ∉ {S,A,B,C,D} 또는 합 ≠ 1.0±0.001 또는 음수) | DISTRIBUTION_INVALID_TARGET `E9804247` | 422 |
| policy.distributionMode == ABSOLUTE 에서 simulate/apply | DISTRIBUTION_MODE_NOT_FORCED `E9804248` | 422 |
| policy.ratingScale != S_A_B_C_D 에서 simulate/apply/adjust | DISTRIBUTION_SCALE_NOT_SUPPORTED `E9804249` | 422 |
| cycle 단계 게이트 위반 (§3) | CALIBRATION_CYCLE_STAGE_MISMATCH `E9804250` | 422 |
| 조정 무효 (toGrade ∉ {S,A,B,C,D} / review 가 해당 cycle 소속 아님) | CALIBRATION_ADJUSTMENT_INVALID `E9804251` | 422 |
| CONFIRMED/CLOSED session 에 adjust/PATCH/confirm 재시도 | CALIBRATION_SESSION_LOCKED `E9804931` | 409 |
| PLANNED 외 delete | CALIBRATION_SESSION_CANNOT_DELETE `E9804932` | 409 |
| adjust 대상 review.status != CALIBRATION | CALIBRATION_REVIEW_NOT_READY `E9804933` | 409 |
- reviewId 미존재 = 기존 `REVIEW_NOT_FOUND E9804447` 재사용 (도메인 교차 재사용 명시 — FE errorMapping 에 포함)
- policy 미존재 = 기존 `POLICY_NOT_FOUND E9804442` 재사용

## 5. 산식 SoT (BE 유일 계산자)

### 유효 등급 (effectiveGrade)
`review.finalGrade ?? bandGrade(review.kpiScore)` — bandGrade = P0-S3 밴드 (S≥90/A≥80/B≥70/C≥60/D<60, kpiScore NULL → `UNRATED` 버킷). **ReviewService 의 밴드 로직을 public 메서드로 재사용** (중복 구현 금지 — P0-S1 D1 함정).

### 현재 분포 (GET distribution)
cycle 의 review 중 `status ∈ {CALIBRATION, FINALIZED}` 전체 — effectiveGrade 로 버킷 카운트 `{S,A,B,C,D,UNRATED}`. 순수 계산 (RatingDistribution 행 불요).

### 강제 배분 알고리즘 (simulate/apply 공통)
1. 대상 = `status == CALIBRATION` AND `kpiScore != null` 인 review, `kpiScore DESC, employeeId ASC`(동점 안정) 정렬. N = 대상 수
2. target = `request.targetDistribution ?? policy.forcedDistribution` (둘 다 없으면 E9804247)
3. quota = **largest remainder method**: `floor(N × ratio_g)` 우선 배분 → 잔여 = N − Σfloor 를 소수부 큰 순(동률 시 S→A→B→C→D 순) 으로 +1. **Σquota == N 보장**
4. 정렬 순서대로 S quota → A quota → … 할당 = proposedGrade
- `simulate`: proposed 목록 반환만 (저장 0)
- `apply`: 대상 review 의 `finalGrade = proposedGrade` 일괄 UPDATE (status 는 CALIBRATION 유지 — FINALIZED 전이는 별도) + RatingDistribution upsert (§0-1) + appliedCount = 대상 수 / skippedCount = cycle 의 CALIBRATION 상태 중 kpiScore NULL 수

### 개별 조정 (adjustments)
`review.finalGrade = toGrade` + session.adjustment_log append (fromGrade = 조정 전 effectiveGrade) + session IN_SESSION 이면 ADJUSTED 자동 승격.

### confirm (+ 일괄 finalize 옵션)
`confirmed_at/by` 세팅 + CONFIRMED 전이. `finalizeReviews == true` 면 cycle 의 `status == CALIBRATION` review 전부에 **P0-S3 FINALIZED 전이를 행 단위 적용** (kpiScore NULL 행은 skip — 카운트 보고). 응답 `{session, finalizedCount, skippedCount}`.

### ReviewService 수정 1건 (P0-S3 보강 — 본 계약 명시 변경)
FINALIZED 전이 시 `finalGrade = (기존 finalGrade != null ? 유지 : bandGrade(kpiScore))` — 캘리브레이션 조정·강제 배분 결과 보존. 기존 P0-S3 테스트 (null → 밴드) 는 그대로 통과해야 함 + 보존 케이스 신규 테스트.

## 6. REST 계약 (base `/api/v1`)

| HTTP | Path | Body | 응답 |
|------|------|------|------|
| GET | `/cycles/{cycleId}/calibration-sessions` | — | `List<CalibrationSessionResponse>` |
| POST | `/cycles/{cycleId}/calibration-sessions` | `CalibrationSessionCreateRequest {ownerOrgUnitId?, scheduledAt?, participantIds?}` | 201 `CalibrationSessionResponse` (status=PLANNED) |
| GET | `/calibration-sessions/{sessionId}` | — | `CalibrationSessionResponse` |
| PATCH | `/calibration-sessions/{sessionId}` | `CalibrationSessionUpdateRequest {ownerOrgUnitId?, scheduledAt?, participantIds?}` | `CalibrationSessionResponse` (PLANNED 한정) |
| POST | `/calibration-sessions/{sessionId}/transition` | `CalibrationTransitionRequest {targetStatus, actorEmployeeId?}` | `CalibrationSessionResponse` (§3 매트릭스) |
| POST | `/calibration-sessions/{sessionId}/adjustments` | `CalibrationAdjustmentRequest {reviewId, toGrade, reason?, actorEmployeeId?}` | `CalibrationSessionResponse` |
| POST | `/calibration-sessions/{sessionId}/confirm` | `CalibrationConfirmRequest {actorEmployeeId?, finalizeReviews?}` | `CalibrationConfirmResponse {session, finalizedCount, skippedCount}` |
| DELETE | `/calibration-sessions/{sessionId}` | — | 204 (PLANNED 한정) |
| GET | `/cycles/{cycleId}/distribution` | — | `DistributionResponse` |
| POST | `/cycles/{cycleId}/distribution/simulate` | `DistributionSimulateRequest {targetDistribution?}` | `DistributionSimulationResponse` |
| POST | `/cycles/{cycleId}/distribution/apply` | `DistributionApplyRequest {targetDistribution?, actorEmployeeId?}` | `DistributionApplyResponse` |

### Response shape (camelCase JSON)

```
CalibrationSessionResponse  {id, cycleId, ownerOrgUnitId, status, scheduledAt, participantIds: string[] | null,
                             adjustmentLog: AdjustmentEntry[] | null, confirmedAt, confirmedBy, createdAt, updatedAt}
AdjustmentEntry             {at, actorEmployeeId, reviewId, employeeId, fromGrade, toGrade, reason}
DistributionResponse        {cycleId, distributionMode, ratingScale, targetDistribution: {S,A,B,C,D} | null,
                             currentDistribution: {S,A,B,C,D,UNRATED}, totalReviews, calibrationReadyCount,
                             forcedApplied, appliedAt, appliedBy, simulationLog: SimulationEntry[] | null}
SimulationEntry             {at, actorEmployeeId, targetDistribution, appliedCount, skippedCount, resultingDistribution}
DistributionSimulationResponse {proposed: ProposedGradeRow[], resultingDistribution: {S,A,B,C,D}, targetDistribution}
ProposedGradeRow            {reviewId, employeeId, kpiScore, currentGrade, proposedGrade}   // currentGrade = effectiveGrade
DistributionApplyResponse   {appliedCount, skippedCount, resultingDistribution: {S,A,B,C,D}}
```
- 분포 객체 키: targetDistribution = 비율 (0~1 number) / currentDistribution·resultingDistribution·actualDistribution = 건수 (int)
- `calibrationReadyCount` = status==CALIBRATION AND kpiScore != null 수 (강제 배분 대상 N)

## 7. FE 화면 3종 (#28/#19/#24)

| 화면 | 라우트 | 내용 |
|------|--------|------|
| Calibration 세션 관리 (#28) | `/hr/calibration-sessions` | cycle Select(기존 CycleSelect 재사용) → 세션 목록 테이블(상태 Badge·일정·참가자 수·orgUnit·조정 건수) + 생성/수정 모달(participantIds = UUID 다중 입력 — TagsInput 또는 Textarea 줄단위) + transition 메뉴(P0-S1 패턴) + **confirm 모달**(finalizeReviews Switch + 결과 finalized/skipped 카운트 notification) + PLANNED 한정 삭제 confirm |
| 본부 Calibration grid (#19) | `/director/calibration` | cycle Select + session Select(해당 cycle 의 세션 — IN_SESSION/ADJUSTED 만 조정 가능 안내) → **분포 막대**(현재 vs 목표 — `@mantine/charts` 미설치: Progress/Box 수동 가로 막대, UNRATED 회색) + review 행 테이블(employeeId·kpiScore·effectiveGrade Badge) + **등급 이동 = 행별 Select/Menu** (S~D, 드래그는 P1 보류 박제) → adjustments API + reason 입력 인라인 + 조정 이력 패널(adjustmentLog 역순 리스트: from→to·reason·시각) |
| HR 분포 시뮬레이터 (#24) | `/hr/distribution` | cycle Select → 분포 비교 막대(현재 vs 목표) + 정책 정보(distributionMode·ratingScale·forcedApplied Badge) + **시뮬레이션 실행** 버튼 → proposed 테이블(현재≠제안 행 강조) + **강제 적용** 버튼(확인 모달 — "review 등급 일괄 변경" 경고 + applied/skipped 카운트 결과) + simulationLog 이력 리스트 |

공유 컴포넌트: `pages/calibration/` 아래 DistributionBars(현재/목표 겹침 막대, 비율·건수 듀얼 표기), GradeBadge(S~D+UNRATED 색상), SessionStatusBadge(5 상태), SessionFormModal, SessionTransitionMenu, ConfirmSessionModal, AdjustGradeMenu, errorMapping.ts (E9804 10종 + 재사용 447/442 매핑 포함 12 키). API 모듈 `src/api/calibration.ts` (calibrationQueryKeys + RQ 훅 — reviews.ts 패턴 정합). 등급 셀 등에서 reviews.ts 의 기존 타입 재사용 가능 (import).

i18n: ko + en 풀 — `nav.*` 3키 + `calibration.*` + `distribution.*` + `error.E9804xxx` 신규 10키 parity.

주의: simulate/apply/adjust 의 등급·분포 계산은 **BE 응답 표시만** (FE 재계산 금지 — DistributionBars 의 % 환산 렌더만 허용).

## 8. 게이트

- BE: `gradlew compileJava` + `gradlew test` 전체 (기존 98 회귀 0 — ReviewService finalize 보강 후에도 기존 테스트 무수정 통과 + 신규 ≥15)
- FE: `npm run typecheck` + `npm run build` (lazy 3 chunk 분리 확인)
- 표준: STD-FE-LAZY/STRICT/RQ/NEST/ERROR-BOUNDARY + ADR-026 명명 + ADR-027 i18n
- talent 9-Box 채널 비접촉 (E6 분담) + legacy selfevaluation 비접촉

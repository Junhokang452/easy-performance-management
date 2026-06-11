# P0-S3 계약서 — PerformanceReview (자기/매니저 평가 + 자동 점수 계산 + Self↔Manager 비교)

> 작성: 2026-06-11 / 작성자: easy-suite-orchestrator / BE·FE 에이전트 공동 SoT — **이탈 금지**
> ERD SoT: `_workspace/evaluation_research_2026-06-11/03_domain_model_and_screens.md` Entity 15 (line 97~101) + Wireframe 2 (line 300~) + 화면 #4/#12
> 선행 의존: P0-S1 cycle 상태기계 (`EvaluationCycle.status`) + P0-S2 KPI (`KpiService.listMyAssignments(cycleId, employeeId)` public — 자동 점수 산식 입력)
> **범위 제외**: MBO / Competency / 360(mra) 는 P1 — `mbo_score`/`competency_score`/`mra_score` 컬럼만 박제(NULL). 기존 legacy `selfevaluation` 도메인 비접촉 (P0-S8 흡수 매핑 별도).

## 1. Entity 1건

### PerformanceReview (`performance_review`)
- `id` UUID PK (UuidV7) / `tenant_id` (TenantAwareAuditEntity)
- `cycle_id` UUID NOT NULL FK → evaluation_cycle **ON DELETE CASCADE**
- `employee_id` UUID NOT NULL (rm_employee 는 P0-S6 — plain UUID)
- `status` varchar(30) NOT NULL enum `ReviewStatus`: DRAFT | SELF_PENDING | SELF_SUBMITTED | MANAGER_PENDING | MANAGER_SUBMITTED | CALIBRATION | FINALIZED | APPEAL_REQUESTED | APPEAL_RESOLVED | ARCHIVED — **P0-S3 전이는 §3 매트릭스의 4개만, APPEAL_*/ARCHIVED 는 enum 박제만** (P0-S10/P2-S3)
- `kpi_score` numeric(5,2) NULL (0~100, submit-manager 시 산출)
- `mbo_score` / `competency_score` / `mra_score` numeric(5,2) NULL — **P0 미사용 박제** (P1 채움)
- `final_score` numeric(5,2) NULL / `final_grade` varchar(10) NULL (FINALIZED 전이 시 산출)
- `self_comment` text NULL / `manager_comment` text NULL
- `kpi_score_detail` jsonb NULL — 스냅샷 배열 (P0-S1 D2 JSONB 패턴: `@JdbcTypeCode(SqlTypes.JSON)` + String + ObjectMapper USE_BIG_DECIMAL_FOR_FLOATS). shape = `[{assignmentId, nodeLabel, treeName, weight, target, unit, latestActualValue, achievementRate, autoScore, managerScore, itemScore}]`
- `finalized_at` timestamptz NULL / `finalized_by` UUID NULL
- UNIQUE `(tenant_id, cycle_id, employee_id)` → `uq_performance_review_cycle_employee` (위반 E9804928)

## 2. Flyway

`backend/src/main/resources/db/migration/V20260611_003__performance_review.sql` — V20260611_002 의 audit 컬럼 DDL 패턴 복제.

## 3. 상태기계 + cycle 단계 게이트 (P0-S3 허용 전이 4개)

| 전이 | 트리거 | 필요 cycle.status | 부수효과 |
|------|--------|------------------|----------|
| DRAFT → SELF_PENDING | transition | SELF_REVIEW | — |
| SELF_SUBMITTED → MANAGER_PENDING | transition | MANAGER_REVIEW | — |
| MANAGER_SUBMITTED → CALIBRATION | transition | CALIBRATION | — |
| CALIBRATION → FINALIZED | transition | CALIBRATION | `finalScore = kpiScore` (P0 단순 — MBO 등 P1 가중 확장 박제) + `finalGrade` = §5 밴드 + `finalizedAt = now` + `finalizedBy = actorEmployeeId`. **kpiScore NULL 이면 E9804244 거부** |
| (SELF_PENDING → SELF_SUBMITTED) | **submit-self 전용** (transition 으로 불가) | SELF_REVIEW | selfComment 저장 |
| (MANAGER_PENDING → MANAGER_SUBMITTED) | **submit-manager 전용** (transition 으로 불가) | MANAGER_REVIEW | §5 스냅샷 + kpiScore 산출 |

- 그 외 모든 전이 요청 → E9804240 (422)
- cycle.status 가 요구값과 다르면 → E9804241 (422) — cycle FINALIZED/CANCELLED 포함 전부 이 코드로 통일

## 4. 검증 규칙 + ErrorCode 10건

| 규칙 | ErrorCode | HTTP |
|------|-----------|------|
| review 없음 | REVIEW_NOT_FOUND `E9804447` | 404 |
| 허용 외 상태 전이 | REVIEW_INVALID_STATUS_TRANSITION `E9804240` | 422 |
| cycle 단계 게이트 위반 | REVIEW_CYCLE_STAGE_MISMATCH `E9804241` | 422 |
| managerScore ∉ [0,100] | REVIEW_SCORE_OUT_OF_RANGE `E9804242` | 422 |
| itemScores 의 assignmentId 가 해당 (cycle×employee) 소속 아님 | REVIEW_ITEM_ASSIGNMENT_MISMATCH `E9804243` | 422 |
| FINALIZED 전이 시 kpiScore NULL | REVIEW_SCORE_INCOMPLETE `E9804244` | 422 |
| 현재 상태에서 수정 불가 섹션 PATCH (예: SELF_PENDING 에 managerComment) | REVIEW_SECTION_NOT_EDITABLE `E9804245` | 422 |
| (cycle×employee) 중복 생성 | REVIEW_DUPLICATE `E9804928` | 409 |
| 제출 후 불변 위반 (SELF_SUBMITTED 이후 selfComment 변경 / MANAGER_SUBMITTED 이후 manager 섹션 변경 / 종결 상태 일체 PATCH) | REVIEW_LOCKED `E9804929` | 409 |
| DRAFT 외 delete | REVIEW_CANNOT_DELETE `E9804930` | 409 |

## 5. 자동 점수 계산 (산식 SoT — BE 가 유일 계산자, FE 는 표시만)

- 입력: `KpiService.listMyAssignments(cycleId, employeeId)` (P0-S2 public — effective weight/target + achievementRate)
- per-item `autoScore` = `achievementRate == null ? null : clamp(round(achievementRate × 100, 2), 0, 100)`
- per-item `itemScore` = `managerScore ?? autoScore` (managerScore 는 submit-manager/PATCH 의 itemScores 입력, 0~100)
- `kpiScore` = `Σ(itemScore × effectiveWeight) / Σ(effectiveWeight)` — **itemScore 비-NULL 항목만** 분자·분모 포함 (점진 입력 허용), round 2. 비-NULL 항목 0개면 kpiScore = NULL
- effectiveWeight NULL(assignment override 없고 node weight 사용) 은 P0-S2 계약상 발생 불가 (node.weight NOT NULL) — assignment.weight ?? node.weight
- **스냅샷 시점**: `submit-manager` 에서 §1 shape 로 `kpi_score_detail` 동결 + kpiScore 산출·저장. 이후 KpiActual 추가돼도 review 점수 불변 (FINALIZED 는 저장된 kpiScore 사용). PATCH(MANAGER_PENDING) 의 itemScores 는 draft 저장만 — kpiScore 는 NULL 유지
- `finalGrade` 밴드 (policy.ratingScale == `S_A_B_C_D` 일 때만): S ≥ 90 / A ≥ 80 / B ≥ 70 / C ≥ 60 / D < 60. 다른 scale 은 finalGrade = NULL 박제 (P0-S4 Calibration 보강). **밴드는 P0-S3 기본값 박제** — policy 설정화는 P0-S4
- `GET /reviews/{id}/kpi-items` = 항상 **live 계산** (assignment 현재값) + 저장된 managerScore 를 assignmentId 로 merge — 폼 렌더용. 단 FINALIZED/MANAGER_SUBMITTED 이후엔 저장 스냅샷을 그대로 반환 (동결 우선)

## 6. REST 계약 (base `/api/v1`)

| HTTP | Path | Body | 응답 |
|------|------|------|------|
| GET | `/cycles/{cycleId}/reviews?employeeId=` | — (employeeId 옵션 필터) | `List<ReviewResponse>` |
| POST | `/cycles/{cycleId}/reviews` | `ReviewCreateRequest {employeeId}` | 201 `ReviewResponse` (status=DRAFT) |
| POST | `/cycles/{cycleId}/reviews/bulk` | `ReviewBulkCreateRequest {employeeIds: UUID[]}` | 201 `ReviewBulkCreateResponse {createdCount, skippedCount, created: ReviewResponse[]}` (기존 (cycle×employee) 존재 시 skip — 에러 아님) |
| GET | `/reviews/{reviewId}` | — | `ReviewResponse` |
| GET | `/reviews/my?cycleId=&employeeId=` | — (둘 다 필수) | `ReviewResponse` (없으면 404 E9804447) |
| GET | `/reviews/{reviewId}/kpi-items` | — | `List<ReviewKpiItemResponse>` |
| PATCH | `/reviews/{reviewId}` | `ReviewUpdateRequest {selfComment?, managerComment?, itemScores?: [{assignmentId, managerScore}]}` | `ReviewResponse` — 섹션 가드: SELF_PENDING = selfComment 만 / MANAGER_PENDING = managerComment+itemScores 만 (위반 E9804245) / DRAFT·그 외 상태 = E9804929 |
| POST | `/reviews/{reviewId}/submit-self` | `ReviewSubmitSelfRequest {selfComment?}` | `ReviewResponse` (SELF_PENDING 한정 + cycle=SELF_REVIEW) |
| POST | `/reviews/{reviewId}/submit-manager` | `ReviewSubmitManagerRequest {managerComment?, itemScores: [{assignmentId, managerScore?}]}` | `ReviewResponse` (MANAGER_PENDING 한정 + cycle=MANAGER_REVIEW + §5 스냅샷·산출) |
| POST | `/reviews/{reviewId}/transition` | `ReviewTransitionRequest {targetStatus, actorEmployeeId?}` | `ReviewResponse` (§3 매트릭스) |
| DELETE | `/reviews/{reviewId}` | — | 204 (DRAFT 한정, 위반 E9804930) |

### Response shape (camelCase JSON)

```
ReviewResponse        {id, cycleId, employeeId, status, kpiScore, mboScore, competencyScore, mraScore,
                       finalScore, finalGrade, selfComment, managerComment,
                       kpiScoreDetail: ReviewKpiItemResponse[] | null,  // 저장 스냅샷 (submit-manager 전 null)
                       finalizedAt, finalizedBy, createdAt, updatedAt}
ReviewKpiItemResponse {assignmentId, nodeLabel, treeName, weight, target, unit,
                       latestActualValue, achievementRate, autoScore, managerScore, itemScore}
                       (weight/target = effective 값, 숫자는 number|null)
```

## 7. FE 화면 (카탈로그 #4/#12 + Self↔Manager 비교)

| 화면 | 라우트 | 내용 |
|------|--------|------|
| 자기평가 폼 (#4) | `/my/self-review` | cycle Select + employeeId 입력(MyKpiPage 패턴) → `GET /reviews/my` → 상태 Badge + KPI 자체 점검 표(read-only: kpi-items 의 weight/target/actual/achievementRate/autoScore) + selfComment Textarea + 임시저장(PATCH, SELF_PENDING 한정) + 제출(submit-self, 확인 모달) → SELF_SUBMITTED 이후 read-only 잠금 표시. review 없으면(404) 빈 상태 안내("HR/매니저가 평가를 생성하면 시작됩니다") |
| 매니저 평가 폼 (#12) | `/manager/review` | cycle Select → `GET /cycles/{id}/reviews` 목록(employeeId·status Badge·kpiScore 컬럼) + 개별/일괄 생성 모달 + transition 메뉴(P0-S1 CyclesPage 상태 전이 메뉴 패턴 — §3 매트릭스 박제) → 행 선택 시 평가 패널: **Tabs 2개** — [KPI 채점] kpi-items 표 + per-item managerScore NumberInput(0~100) + autoScore 폴백 표시 + 가중 합산 프리뷰(§5 산식과 동일 — FE 프리뷰는 표시용, 제출 후 BE kpiScore 가 SoT) + managerComment + 임시저장(PATCH)·제출(submit-manager) / [Self ↔ Manager 비교] selfComment ↔ managerComment 나란히 + per-item autoScore ↔ managerScore 비교 표 |
| (비교 view) | — | 별도 라우트 없음 — 매니저 폼 내 탭으로 구현 (Wireframe 2 Tabs 정합) |

공유 컴포넌트: `pages/review/` 아래 ReviewStatusBadge(10 상태 색상), ReviewKpiItemsTable(readOnly/score-input 모드), ReviewTransitionMenu, ReviewCreateModal(개별+일괄), errorMapping.ts (E9804 10종). API 모듈 `src/api/reviews.ts` (reviewsQueryKeys + RQ 훅 — kpi.ts 패턴 정합, CycleSelect 재사용).

i18n: ko + en 풀 — `nav.*` 2키(`/my/self-review`, `/manager/review`) + `review.*` + `error.E9804xxx` 10키 parity.

## 8. 게이트

- BE: `gradlew compileJava` + `gradlew test` 전체 (기존 64 회귀 0 + 신규 ≥15)
- FE: `npm run typecheck` + `npm run build` (lazy chunk 분리 확인)
- 표준: STD-FE-LAZY/STRICT/RQ/NEST/ERROR-BOUNDARY + ADR-026 명명 + ADR-027 i18n 네임스페이스
- legacy `selfevaluation` 도메인 (BE `domain/selfevaluation` + FE `/self-evaluations`) **비접촉** 확인

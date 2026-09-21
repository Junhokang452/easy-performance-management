# Evaluation workspace backend implementation

## Delivered behavior

- Added the authenticated `/api/v1/evaluation-workspace` facade as the workflow boundary.
- Bound every member and manager action to `PerformanceUser.employeeId`; a missing binding fails closed.
- Added tenant-scoped employee directory, cycle discovery/detail, participant roster replacement, and primary-manager assignment.
- Added participant, reviewer assignment, goal agreement, intermediate performance review, and formal feedback entities with optimistic versions.
- Added goal draft → submit → manager approve/reject → employee revise/resubmit, plus numeric KPI actual check-ins.
- Added guarded phase progression: roster → approved goals → actuals and completed intermediate review → self review → manager review → calibration → report → feedback/appeal → close.
- Added manager task, calibration task, feedback task, member workspace, and result-summary projections.
- Added actor-scoped review detail and KPI item endpoints. Employee responses mask manager comments, scores, score detail, and final grade until report publication.
- Added individual calibration adjustment with a required reason, session confirmation, report publication while the cycle remains in calibration, formal feedback, employee accept/appeal, HR appeal resolution, acknowledgement, and final close.
- Added scoped cycle discovery: HR/SUPER/DIRECTOR can see tenant cycles; employees see active-participant cycles; managers see assigned-reviewer cycles.
- Added organization names to employee projections and organization-level result aggregation.
- Denied the raw legacy lifecycle mutation routes, including cycle transition, review submit/transition, calibration mutation, and report publication, so HR cannot bypass the facade gates.
- Changed unauthenticated API handling to 401 while authenticated permission denial remains 403.
- Added migration `V20260907_002__evaluation_workspace.sql` with tenant-leading indexes, foreign keys, uniqueness constraints, enum checks, and `row_version` columns.

## Workflow invariants

- Roster replacement has true replacement semantics: omitted participants become `EXCLUDED`, their reviewer assignments become `REVOKED`, an empty roster is valid, and re-adding a participant reactivates the existing row.
- A cycle cannot open without a policy, at least one active participant, and one active primary manager per participant.
- Every active participant must have at least one goal, and every goal must be approved before mid-cycle review opens.
- Every approved goal must have a numeric actual/check-in, and the employee and manager must complete the intermediate performance review before self review opens.
- Self and manager review stages must be complete for every active participant before the next stage opens.
- A cycle can close only after calibration finalizes each review, an active report exists, the employee acknowledges it, and feedback is accepted or an appeal is resolved.
- Goal and feedback mutations reject changes outside their allowed cycle phase. Optimistic versions prevent silent overwrites on mutable workflow rows.

## API contract additions

- `GET /api/v1/evaluation-workspace/cycles`
- `GET /api/v1/evaluation-workspace/cycles/{cycleId}`
- `GET /api/v1/evaluation-workspace/cycles/{cycleId}/me`
- `GET /api/v1/evaluation-workspace/manager/tasks?cycleId=...`
- `GET /api/v1/evaluation-workspace/cycles/{cycleId}/calibration/tasks`
- `GET /api/v1/evaluation-workspace/cycles/{cycleId}/feedback`
- `GET /api/v1/evaluation-workspace/cycles/{cycleId}/results/summary`
- `POST /api/v1/evaluation-workspace/cycles/{cycleId}/close`

`POST /cycles/{cycleId}/advance` with `targetStatus=FINALIZED` is also a compatibility alias for the same guarded close operation. `WorkspaceResponse.feedback` and server-computed `allowedActions` are the client source of truth. Actions now cover goal edit/submit, actual check-in, manager review edit/submit, feedback edit/complete, feedback accept/appeal, and appeal resolution.

## Verification

- Final full backend suite: **212 tests, 0 failures, 0 errors, 0 skipped** across 29 suites, after UUID reuse and internal admin guard fixes.
- Fresh PostgreSQL migration and Hibernate schema validation passed.
- Final local real-API lifecycle verifier: **97/97 passed**, including assigned-cycle discovery, actor binding, tenant-ID access guards, rejected-goal resubmission with retained numeric values, actual propagation, intermediate review, manager scoring, unpublished-field masking, calibration adjustment, report publication, feedback appeal/resolution, acknowledgement, close, invalid input and post-close mutation rejection. Neon physical DB routing was not exercised in this local run.
- HR raw legacy cycle transition and report publish requests both returned **403**.

## Explicit limits

- The current workflow assigns one primary manager (`MANAGER`, round 1, weight 1). The schema supports reviewer type, round, and weight, but peer/360 aggregation and multiple review rounds are deferred.
- `FeedbackResolution.ADJUSTMENT_REQUIRED` is reserved for a later versioned correction flow and is rejected with 422. The current complete appeal-resolution path accepts only `UPHELD`; it never closes an appeal while leaving a promised grade adjustment unapplied.
- Organization result rows aggregate participants with a known `rm_employee.org_unit_id`; employees without an organization remain in tenant totals and are omitted from organization rows.
- Report supersede remains an internal legacy service operation and is not exposed through the workspace facade.


## 진행률 영속화 보완

브라우저 전체 흐름 점검에서 실적의 progressPercent가 POST 응답에만 있고 GET 재조회에서는 null로 사라지는 결함을 발견했다. 신규 forward V20260907_003은 KPI actual 계약을 변경하지 않고 evaluation_goal_check_in 메타데이터 테이블을 추가한다. 실제 실적 ID 기준 1:1 저장·조회하며, 기존 실적에 기록되지 않은 진행률은 null로 유지한다. DTO와 DB 모두 0~100을 검증한다. 저장→재조회 및 범위 회귀 포함 전체 백엔드 214/214.

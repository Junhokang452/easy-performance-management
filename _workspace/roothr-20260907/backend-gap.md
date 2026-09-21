# Backend gap and first implementation contract

Date: 2026-09-07  
Baseline: clean `1d9d282` before this document  
Scope: `easy-performance-management` backend only. No database, service, or sibling-repository mutation was performed.

## Decision

The existing cycle, KPI, review, calibration, and report services contain useful calculations and state guards, but their public HTTP surface is not safe or coherent enough for a usable evaluation workflow. The first implementation slice should add a secure `evaluation-workspace` application facade, principal-to-employee binding, and explicit participant/reviewer assignments. Existing services remain internal calculation engines. Legacy domain endpoints must be limited to HR/system operators so they cannot bypass actor and assignment checks.

This is consistent with the RootHR reference flow already captured in `reference-flows.md`: prepare roster and evaluators, explicitly open a cycle, gate goals/check-ins/reviews by stage, calculate/calibrate, publish per subject, then close. It is also consistent with the local backend rules: controller → service → repository, DTO-only HTTP contracts, tenant-filtered queries, `tenant_id`-leading indexes, UUIDv7 product IDs, and transaction-local writes.

## What exists now

All `/api/v1/**` routes below require a valid JWT, but there is no domain authorization after authentication.

| Area | Existing HTTP contract | Current behavior |
|---|---|---|
| Cycle/policy | `GET/POST /api/v1/cycles`, `GET/PATCH/DELETE /api/v1/cycles/{id}`, `POST /api/v1/cycles/{id}/transition`, `GET/PUT /api/v1/cycles/{id}/policy` | Pageable cycle list; create may include policy; linear `PLANNED → ACTIVE → GOAL_SETTING → MID_REVIEW → SELF_REVIEW → MANAGER_REVIEW → CALIBRATION → FINALIZED`, with `CANCELLED` exits. |
| KPI trees | `GET/POST /api/v1/cycles/{cycleId}/kpi-trees`, `GET/PATCH/DELETE /api/v1/kpi-trees/{treeId}`, `POST /api/v1/kpi-trees/{treeId}/nodes`, `PATCH/DELETE /api/v1/kpi-nodes/{nodeId}` | Corporate/division/team/individual trees and weighted nodes. Cycle lock is checked for writes. |
| KPI assignment/check-in primitive | `GET/POST /api/v1/kpi-nodes/{nodeId}/assignments`, `PATCH/DELETE /api/v1/kpi-assignments/{assignmentId}`, `GET /api/v1/kpi-assignments/my?cycleId&employeeId`, `GET/POST /api/v1/kpi-assignments/{assignmentId}/actuals`, `POST /api/v1/kpi-actuals/{actualId}/supersede` | `KpiAssignment` can represent a person's goal and append-only `KpiActual` can represent progress/check-ins. The API still trusts caller-provided employee IDs and has no goal approval/check-in stage contract. |
| Review | `GET/POST /api/v1/cycles/{cycleId}/reviews`, `POST .../reviews/bulk`, `GET/PATCH/DELETE /api/v1/reviews/{reviewId}`, `GET /api/v1/reviews/my?cycleId&employeeId`, `GET .../{reviewId}/kpi-items`, `POST .../{reviewId}/submit-self`, `POST .../{reviewId}/submit-manager`, `POST .../{reviewId}/transition` | One review per cycle/employee. The record has no reviewer. Self/manager sections share one row. The request can supply any employee ID and transition/finalize actor ID. |
| Calibration | `GET/POST /api/v1/cycles/{cycleId}/calibration-sessions`, `GET/PATCH/DELETE /api/v1/calibration-sessions/{sessionId}`, `POST .../transition`, `POST .../adjustments`, `POST .../confirm`, `GET /api/v1/cycles/{cycleId}/distribution`, `POST .../distribution/simulate`, `POST .../distribution/apply` | Target/current distribution and deterministic largest-remainder allocation exist. Adjustment, confirm, and apply bodies trust `actorEmployeeId`; participant IDs are unvalidated JSON. |
| Report | `GET /api/v1/cycles/{cycleId}/reports`, `POST .../reports/publish`, `GET /api/v1/reports/{reportId}`, `GET /api/v1/reports/my?cycleId&employeeId`, `POST .../view`, `POST .../acknowledge`, `POST .../supersede` | Append-only/supersede report content exists. `my` trusts the employee ID. Publish/acknowledge/supersede trust actor IDs. Publish currently requires the cycle already be `FINALIZED`. |
| Legacy employee content | CRUD under `/api/v1/personal-okrs`, `/self-evaluations`, `/reflection-journals`, `/mentor-feedbacks` | Separate stage-1 models. `PersonalOkr` has no cycle ID; none form the authoritative evaluation aggregate. They also trust employee/mentor/mentee IDs. |
| Authentication/tenant | `POST /api/auth/login|refresh|logout`; JWT subject is `user_account.id`, TID is tenant, authorities are unprefixed role strings | `PerformanceUser.employeeId` exists but is nullable. No shared actor resolver enforces it. `TenantSupport.currentTenantId()` still falls back to all-zero-plus-one when context is missing. |

Useful invariants to retain:

- Tenant-scoped repository lookups and cycle stage checks exist across the five main services.
- KPI actuals, calibration logs, and reports already use append-only/snapshot behavior.
- Manager submission freezes KPI inputs and computes a deterministic score.
- Calibration distribution uses a tested deterministic allocation.
- Report superseding preserves history.

## Concrete blockers and security gaps

1. **Any authenticated user is effectively an administrator.** `SecurityConfig` only distinguishes `/api/admin/**`; all `/api/v1/**` routes use `authenticated()`. An `EMPLOYEE` can create/delete cycles, bulk-create reviews, change any review, apply calibration, publish all reports, or read the tenant's full result set.
2. **Horizontal authorization is absent.** “My” endpoints accept `employeeId`; direct-ID endpoints never compare the principal with the target employee or assigned reviewer. This is tenant-contained IDOR.
3. **Actor identity is client-authored.** Review transition, calibration adjustment/confirm/apply, and report publish/acknowledge/supersede accept nullable `actorEmployeeId`. Audit/finalizer identity can be forged or omitted.
4. **There is no participant roster.** A review or KPI assignment accepts any UUID. The code does not verify an active `rm_employee`, exclusion state, membership period, or that the employee belongs to the cycle.
5. **There is no reviewer assignment.** The single `PerformanceReview` row has `employeeId` only. A manager relationship, reviewer round/type, access scope, reassignment, and completion status cannot be represented or enforced.
6. **Principal-to-employee binding is optional.** `user_account.employee_id` is nullable and dev seeding does not bind it. A secure self/manager workflow cannot derive an employee actor reliably. Account-ID fallback must not be used for workflow authorization.
7. **Goals/check-ins are implementation primitives, not a workflow.** KPI node/assignment/actual can carry the data, but there is no employee goal draft/submission, manager approval/rejection, check-in availability, or actor ownership check. The older `personal_okr` is not cycle-bound and should not become the evaluation SoT.
8. **Cycle transitions validate only adjacency.** They do not check participant/reviewer readiness, required goal approval, completion counts, review state, calibration confirmation, report publication, or close locks. Every person can be stranded in a different state while the cycle advances.
9. **The current report order conflicts with the desired flow.** `ReportService.publish` requires `cycle=FINALIZED`, while the requested/reference flow is calibration → publish results → close/lock. Closure and publication need separate semantics.
10. **No single workspace read model exists.** The UI must manually stitch cycles, KPI assignments, reviews, calibration, and reports while supplying identity IDs. This makes security errors and phase drift likely.
11. **Read-model validation is missing.** `rm_employee` exists, but participant, KPI assignment, review creation, and calibration participant IDs do not require an active matching row. `rm_assignment` is not used to suggest/validate the manager reviewer.
12. **Fallback tenancy weakens fail-closed behavior.** `TenantSupport` silently selects `00000000-...-0001` when request context is missing. Workflow services should require the authenticated tenant. Model-B routing remains the outer boundary; workflow ownership is an additional inner boundary.

## First implementation slice: secure evaluation workspace

### Ownership boundary

This slice may change `SecurityConfig`, add workflow/domain classes and a migration, and add tests. The parent owns `AuthController`, `AuthDtos`, `AuthService`, `JwtAuthFilter`, frontend auth/session files, and local demo/profile/runtime scripts. Coordinate before changing those parent-owned paths. The parent may seed `PerformanceUser.employeeId`; this slice only consumes and strictly validates the binding.

### New persistence

Add one forward migration, with all indexes led by `tenant_id`:

```text
evaluation_participant
  id UUIDv7 PK
  tenant_id UUID NOT NULL
  cycle_id UUID NOT NULL FK evaluation_cycle
  employee_id UUID NOT NULL
  status ACTIVE|EXCLUDED NOT NULL
  exclusion_reason TEXT NULL
  created/updated audit columns
  UNIQUE (tenant_id, cycle_id, employee_id)

evaluation_reviewer_assignment
  id UUIDv7 PK
  tenant_id UUID NOT NULL
  cycle_id UUID NOT NULL FK evaluation_cycle
  participant_id UUID NOT NULL FK evaluation_participant
  reviewer_employee_id UUID NOT NULL
  reviewer_type MANAGER|PEER|HR NOT NULL
  round SMALLINT NOT NULL DEFAULT 1
  weight NUMERIC(5,4) NOT NULL DEFAULT 1
  status ASSIGNED|IN_PROGRESS|SUBMITTED|REVOKED NOT NULL
  created/updated audit columns
  UNIQUE (tenant_id, participant_id, reviewer_employee_id, reviewer_type, round)
```

For this first usable slice, require exactly one active `MANAGER` reviewer with round `1` per active participant. Preserve the normalized shape so peer/multi-round reviews can be added without schema replacement. Do not expose peer review submission until separate per-reviewer response persistence exists.

Goals and check-ins should initially reuse `KpiTree/KpiNode/KpiAssignment/KpiActual`: one individual tree per cycle/person is created by the facade, the assigned node is the public goal, and an actual is an append-only check-in. Do not use `personal_okr` as the workflow source.

### Actor access service

Add `ActorAccess` (or `CurrentActor`) as the single authorization seam:

```text
Actor requireActor()
  userId       = UUID(SecurityContext.authentication.name)
  tenantId     = TenantContext.requireTenantId()
  account      = user_account by (userId, tenantId), active required
  employeeId   = account.employeeId, required for every employee/manager action
  role         = account.role

requireAnyRole(actor, ...)
requireSelf(actor, employeeId)
requireParticipant(actor, cycleId, employeeId)
requireAssignedReviewer(actor, participantId, MANAGER, 1)
```

Missing `employeeId` must return a stable 422/409 domain error such as `ACTOR_EMPLOYEE_BINDING_REQUIRED`; it must not fall back to account ID. Actor IDs disappear from request DTOs and are set from this service.

### New HTTP contract

Base path: `/api/v1/evaluation-workspace`.

| Method/path | Authority | Request → response |
|---|---|---|
| `GET /me` | authenticated | none → `ActorResponse(userId, tenantId, employeeId, displayName, role)` |
| `GET /directory?query&page&size` | HR/SUPER/DIRECTOR/MANAGER | query → `Page<EmployeeOption(id, employeeNo, name, orgUnitId, active, hasUserBinding)>`; bounded pageable, no `findAll()` |
| `PUT /cycles/{cycleId}/participants` | HR/SUPER | `ParticipantReplaceRequest(List<ParticipantInput(employeeId, managerEmployeeId)>)` → `ParticipantRosterResponse(cycleId, activeCount, excludedCount, missingManagerCount, items)`; idempotent upsert/replace while cycle is `PLANNED` or `ACTIVE` |
| `GET /cycles/{cycleId}/participants?page&size` | HR/SUPER/DIRECTOR/MANAGER | role-scoped page → `Page<ParticipantResponse(id, employee, status, manager, reviewId, reviewStatus)>` |
| `POST /cycles/{cycleId}/open` | HR/SUPER | empty → `CycleLaunchResponse(cycle, createdReviews, blockers)`; reject unless policy exists, active participant count > 0, every participant and reviewer exists/active, exactly one manager each; create missing reviews idempotently and move `PLANNED/ACTIVE` to `GOAL_SETTING` atomically |
| `GET /cycles/{cycleId}/me` | bound employee | none → `WorkspaceResponse(cycle, participant, goals, review, report, allowedActions, blockers)`; employee identity is derived, never queried |
| `POST /cycles/{cycleId}/goals` | participant, or assigned manager for participant | `GoalCreateRequest(employeeId?, title, description, weight, target, unit)` → `GoalResponse`; employee may omit employeeId and may only create self; manager may supply only an assigned subject |
| `POST /goals/{goalId}/submit` | goal owner | empty → `GoalResponse(status=PENDING_APPROVAL)` |
| `POST /goals/{goalId}/decision` | assigned manager | `GoalDecisionRequest(APPROVE|REJECT, comment)` → `GoalResponse`; approval required before leaving goal stage |
| `POST /goals/{goalId}/check-ins` | goal owner | `CheckInCreateRequest(asOfDate, actualValue, progressPercent, note, evidenceUrl)` → `CheckInResponse`; append-only; only `MID_REVIEW` (or configured open check-in window) |
| `POST /reviews/{reviewId}/self/draft` | target participant | `SelfDraftRequest(comment)` → existing `ReviewResponse` |
| `POST /reviews/{reviewId}/self/submit` | target participant | `SelfSubmitRequest(comment)` → existing `ReviewResponse`; actor derived |
| `POST /reviews/{reviewId}/manager/draft` | assigned manager | `ManagerDraftRequest(comment, itemScores)` → existing `ReviewResponse` |
| `POST /reviews/{reviewId}/manager/submit` | assigned manager | `ManagerSubmitRequest(comment, itemScores)` → existing `ReviewResponse`; reviewer assignment becomes `SUBMITTED` in same transaction |
| `POST /cycles/{cycleId}/advance` | HR/SUPER | `CycleAdvanceRequest(targetStatus)` → `CycleAdvanceResponse(cycle, blockers, affectedCount)`; validates phase-specific completion and performs required per-review transitions atomically |
| `POST /cycles/{cycleId}/calibration/apply` | HR/SUPER/DIRECTOR | `CalibrationApplyRequest(targetDistribution)` → existing distribution result; actor derived |
| `POST /cycles/{cycleId}/calibration/confirm` | HR/SUPER/DIRECTOR | no actor ID → finalize ready reviews and return counts; actor derived |
| `POST /cycles/{cycleId}/reports/publish` | HR/SUPER | `ReportPublishRequest(employeeIds optional)` → publish active per-person reports after finalized reviews while cycle is `CALIBRATION`; actor derived |
| `GET /cycles/{cycleId}/report` | participant | none → active report for derived employee only |
| `POST /reports/{reportId}/acknowledge` | report owner | empty → report; actor derived and ownership checked |
| `POST /cycles/{cycleId}/close` | HR/SUPER | empty → final cycle; reject unless all active participants have finalized reviews, required calibration is confirmed, and active reports are published; lock workflow writes |

`WorkspaceResponse.allowedActions` must be server-derived; examples are `EDIT_GOAL`, `SUBMIT_GOAL`, `ADD_CHECK_IN`, `EDIT_SELF_REVIEW`, `SUBMIT_SELF_REVIEW`, `EDIT_MANAGER_REVIEW`, `SUBMIT_MANAGER_REVIEW`, `VIEW_REPORT`, `ACKNOWLEDGE_REPORT`. The UI should never infer authorization from status alone.

### Phase invariants

| Advance | Required checks/actions |
|---|---|
| open → `GOAL_SETTING` | policy, participants, employee existence, one manager reviewer, reviews created |
| `GOAL_SETTING → MID_REVIEW` | every active participant has at least one goal; every goal approved; lock goal definition except explicit reopen |
| `MID_REVIEW → SELF_REVIEW` | at least one check-in per required goal; transition all active reviews `DRAFT → SELF_PENDING` |
| `SELF_REVIEW → MANAGER_REVIEW` | all active reviews `SELF_SUBMITTED`; transition them to `MANAGER_PENDING` |
| `MANAGER_REVIEW → CALIBRATION` | all active manager assignments submitted and reviews `MANAGER_SUBMITTED`; transition them to `CALIBRATION` |
| calibration confirm | all included reviews have complete score; append calibration actor/time and finalize reviews |
| publish | review is finalized; create active append-only report while cycle remains `CALIBRATION` |
| close → `FINALIZED` | all active participants have finalized review and active published report; then hard-lock goals/check-ins/reviews/calibration |

### Legacy route guard

Enable method security or add precise matcher rules. The facade is the normal user path. Until every legacy endpoint has object authorization:

- `HR_ADMIN`/`SUPER_ADMIN`: existing cycle, policy, KPI, review, calibration, report, and legacy CRUD routes.
- `DIRECTOR`: read-only cycle/distribution/report lists and facade calibration actions only.
- `MANAGER`/`EMPLOYEE`: no direct legacy domain mutations or direct-ID reads; use the facade, where employee and reviewer ownership are checked.
- Keep `/api/internal/**` on its existing Bearer+HMAC guard and `/api/admin/**` on `SUPER_ADMIN`.

This coarse guard should land in the same first slice; otherwise the secure facade can be bypassed immediately.

## Recommended implementation order and tests

1. Migration + participant/reviewer entities/repositories, with tenant-leading indexes and uniqueness tests.
2. `ActorAccess` with active account, strict employee binding, role, self, participant, and assigned-reviewer checks.
3. Coarse legacy route guards in `SecurityConfig`; add authorization tests proving EMPLOYEE cannot call raw cycle/review/report/calibration mutations.
4. Roster/directory/open endpoints and launch validation; use existing review bulk/create internals only after validated active employee IDs.
5. Workspace composite read and self/manager review facade endpoints; remove actor IDs at the facade boundary.
6. Goal/check-in facade over KPI primitives and approval state. If adding approval fields to KPI is too invasive for the first pass, add a small `evaluation_goal_workflow` row keyed by assignment ID rather than mutating old Flyway files.
7. Phase advance, calibration facade, publication in `CALIBRATION`, and close checklist.

Minimum meaningful integration test is one tenant with bound HR, manager, and employee accounts plus matching `rm_employee` rows:

```text
HR creates cycle/policy
→ HR uploads employee + manager roster
→ open creates one review
→ employee creates/submits goal
→ manager approves
→ employee adds check-in
→ HR advances to self review
→ employee submits self review
→ HR advances to manager review
→ assigned manager submits score
→ HR advances to calibration and confirms
→ HR publishes report
→ employee reads and acknowledges own report
→ HR closes cycle
```

Negative tests must cover cross-employee read/write, unassigned manager submission, forged actor fields (fields no longer exist), missing employee binding, inactive/nonexistent roster employee, advance with blockers, direct legacy endpoint access by EMPLOYEE, and any write after close.

## Material scope limit

This first slice intentionally supports one primary manager round. Multi-rater/360 requires independent reviewer response rows, weighting, anonymity/visibility policy, and separate calculation snapshots. The reviewer-assignment schema above leaves that extension open, but pretending the existing single `managerComment` and single score are 360-capable would corrupt the domain model.

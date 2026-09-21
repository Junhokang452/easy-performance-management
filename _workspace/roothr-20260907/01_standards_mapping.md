# Performance evaluation workspace — standards map and frontend audit

Date: 2026-09-07  
Scope: read-only audit of `easy-performance-management`; no product code changed.

## Applicable, non-negotiable constraints

| Concern | Constraint | Evidence | Implementation consequence |
|---|---|---|
| Tenant isolation | Every resource lookup and mutation must be scoped server-side to the authenticated tenant; failures default closed. A visible button or route is not authorization. | `02-security-owasp.md` §§1, 2, 4 (lines 14, 22, 47-50) | Employee directory, cycle workspace, participant assignment, self tasks, manager tasks, and result viewing must derive tenant from JWT/security context. Do not accept a client-provided tenant UUID as authority. |
| ID and actor identity | Product keys and cross-product identifiers are UUID; the actor comes from the security context (`AuditorAware<UUID>`). `employee_no` is display/business data, not the PK. | `09-database.md` §§2, 4 (lines 39-41, 71-74); `11-suite-architecture.md` §1 (lines 17-18) | Hide UUIDs from normal workflows. Pass selected employee UUID only as an opaque value from a tenant-scoped picker. The API must resolve “me” from principal → employee mapping, not ask the employee to type an ID. |
| Employee and org source of truth | Core Master owns employee, organization, and assignment. Performance may only consume its local read models; those copies are read-only. | `11-suite-architecture.md` §1 (lines 10-18), §3-4 (lines 81, 100-103) | Build a read-only `rm_employee`/`rm_org_unit` query surface inside Performance; never make a performance UI write employee or organization records, and never query another product database. Keep `sourceVersion`/`syncedAt` as S2S responsibilities. |
| Model B / browser boundary | Neon Model B is the target. The browser must use API DTOs and must never receive DB connection material or access the DB directly. | `09-database.md` §1 (lines 10-14, 27-33) | New UI uses the current authenticated HTTP client only. No tenant connection, RLS variable, or direct database access enters frontend code. |
| Role and object authorization | Object-, function-, and field-level server authorization must protect ID-based endpoints, including manager and HR actions. | `02-security-owasp.md` §§2-4 (lines 22, 35-38, 47-50) | A participant picker is convenience only. Server checks must still ensure HR can assign participants, a manager can see only their authorized queue, and an employee can only retrieve their own task/result. |
| Frontend stack and state | React 19.2+, Mantine v9, React Router 7 with lazy routes and Suspense, React Query as server-state SSOT; no fetch-result copies in component/global state. | `07-frontend.md` §§1-3 (lines 13-16, 43-45, 52-53, 74-79); `frontend-mantine-patterns` §§1-4 | Add domain API/hook/component files under a cohesive feature boundary. Queries and mutations own refetch/invalidation; only transient form/filter state is local. Reuse `@easy/ui-components` and tokens; no raw colors. |
| Localisation and shared UI | `ko.ts` is the typed schema; all five locales must be updated together. Cross-product UI belongs in the shared package, not duplicated in the product. | `07-frontend.md` lines 43-45, 53, 79; root `CLAUDE.md` Design System and component rules | Keep performance-specific workspace components local. If the eventual employee picker/card is broadly reusable, propose it to `@easy/ui-components` with the four-file SoT component structure; do not add a repo-local Storybook. |
| Boundary verification | API DTOs, hooks, routes/links, state transitions, and entity→DB→DTO→UI must be read on both sides before declaring a slice complete. | `boundary-qa` §§1-4, 7 | The feature is not done when TypeScript passes. Verify query keys, response names/nullability, route links, and server-side transition permissions together. |

## Current frontend audit

### What is already sound

- `App.tsx` lazily loads all current pages and wraps protected routes in `PageBoundary`; the existing shell is an appropriate base for a smaller role-aware navigation hierarchy.
- HTTP calls use `@easy/http-client`, with authorization and tenant ID set by `AuthProvider` (`api/client.ts:15-28`, `auth/AuthProvider.tsx:51-59`).
- Existing cycle, review, calibration, and report APIs already use React Query mutations and invalidate relevant cache entries. This should remain the server-state model.
- The backend has a correctly bounded Core Master consumer: `rm_employee`, `rm_org_unit`, and `rm_assignment` are tenant-aware, read-only copies written only by S2S (`backend/.../readmodel/entity/RmEmployee.java:17-23`; `SyncReceiveController.java:65-74`).

### Blocking usability gaps

| Priority | Gap and evidence | User impact | Required direction |
|---|---|---|---|
| P0 | The “my” pages require an employee UUID: `MySelfReviewPage.tsx:52-98` and `MyKpiPage.tsx:41-90`. Their API contracts also require `employeeId` query parameters (`api/reviews.ts:172-175`, `api/kpi.ts:259-264`). | An employee cannot use “My KPI” or “My Review” without knowing a database identifier. The user may also read another employee’s task if backend authorization is incomplete. | Replace with principal-derived `/me` endpoints (or a server-resolved actor endpoint) and leave cycle selection as the only normal user choice. |
| P0 | HR review creation is a text UUID workflow, including pasted lists: `ReviewCreateModal.tsx:48-160`. | Participant assignment is impractical and error-prone. | Add tenant-scoped employee search/selection backed by the local read model. Support one or many selected employees with human labels; submit UUIDs only as opaque selected values. |
| P0 | KPI assignment also asks for an employee UUID in `kpi/AssignmentModal.tsx:55-67` and the resulting display is raw ID. | The same blocker appears in goal assignment. | Reuse the same picker and `EmployeeSummary` display mapping rather than create separate lookup logic. |
| P0 | Calibration session setup uses `TagsInput` UUIDs for participants and a text UUID for organization: `calibration/SessionFormModal.tsx:55-82,124-147`. | Check-in/attendee management cannot be used by HR without database knowledge; the ownership organization is unreadable. | Defer rich check-in until participant identity exists, then replace both fields with org and employee selectors. Do not treat free-form tags as confirmed attendance. |
| P1 | Review, calibration, distribution, and reports render opaque `employeeId` values in queues and tables (`ManagerReviewPage.tsx:133-151,276-278`; `DirectorCalibrationPage.tsx:212-229`; `HrReportsPage.tsx:450-462`). | HR and managers cannot reliably identify people. | Return/cross-reference a display-safe summary (`id`, employeeNo, name, orgUnitName, status) and render it consistently. |
| P1 | The router has 19 flat, separately discoverable pages (`App.tsx:178-292`, routes `297-450`), but no operational cycle-detail route that carries users through setup → participants → self/manager review → calibration → publish. | Controls work in isolated screens but users do not have an end-to-end operating path. | Add one cycle workspace route as the coordinating shell; keep the existing specialized pages as linked role destinations during transition. |
| P1 | There is no frontend endpoint or hook for `rm_employee`, while the repository explicitly says search/selector support is a separate slice (`RmEmployeeRepository.java:15-20`). | The frontend cannot remove UUID entry without a narrow backend read API. | Add the directory contract before or with the first frontend redesign slice. It must be tenant-scoped, paginated/searchable, and read-only. |
| P2 | The App Shell only gates the admin tenant link visually (`App.tsx:129-132,286-293`); most HR/manager menus are visible to every authenticated user. | Role intent is unclear and UI exposes actions that will predictably fail. | Use session roles to show role-appropriate navigation, but retain server-side authorization for every API. |

## Proposed narrow, coherent FE slice

The first slice should make a real evaluation cycle usable without attempting a wholesale rewrite.

### Slice: Cycle workspace + participant assignment

1. Add a role-aware route: `/hr/cycles/:cycleId/workspace`.
2. In the workspace, show a fixed progression: **Setup → Participants → Self review → Manager review → Calibration → Results**. Each step displays cycle status, an actionable count, and a link to its focused working view.
3. Put participant management in the workspace: searchable employee directory, selected-person chips/table, individual and bulk review creation, duplicate/skipped outcome feedback. The stored contract remains the existing review creation UUID API, but UUIDs are never typed or displayed.
4. Replace raw `employeeId` in the manager review queue with `EmployeeSummary` labels. The query may be an ID batch lookup initially; it must not duplicate Core Master data in frontend state.
5. Add role-aware top navigation: HR sees Cycle workspace and Results; manager sees Team reviews; employee sees My tasks and My result. Hide unavailable entries without claiming that this is authorization.

### Explicitly out of the first slice

- Live meeting attendance/check-in. `CalibrationSession.participantIds` currently has no attendance state or authenticated check-in contract, so a visual check-in control would be disconnected.
- An organization editor or employee CRUD. Both belong to Core Master.
- Reworking scoring, state transitions, or report generation. Existing server APIs are the source of truth and should be linked into the workspace first.
- A client-side principal-to-employee mapping. This must be server resolved to prevent IDOR.

## Required API seam before frontend implementation

The smallest viable backend companion is an authenticated, read-only selector API over the local read model, for example:

```text
GET /api/v1/read-model/employees?q=&orgUnitId=&status=ACTIVE&page=&size=
→ { content: EmployeeSummary[], totalElements, page, size }

GET /api/v1/me/performance-context?cycleId=
→ { employee: EmployeeSummary, review?: ReviewSummary, assignments: ... }
```

`EmployeeSummary` should contain `id`, `employeeNo`, `name`, `orgUnitId`, `orgUnitName`, and `status`; UUID `id` stays an opaque selection key. The server must apply tenant scope and role/ownership filtering, reject an unbound principal instead of accepting a supplied employee ID, paginate and bound the search input, and return standard `ApiError` responses.

This seam allows the existing review creation endpoints to remain stable initially. A subsequent safe simplification can replace `GET /reviews/my?cycleId=&employeeId=` and `GET /kpi-assignments/my?cycleId=&employeeId=` with principal-resolved endpoints.

## Code paths for assigned implementation ownership

### Frontend ownership

- `frontend-vite/src/App.tsx` — role-aware navigation and lazy workspace route.
- New `frontend-vite/src/features/evaluation-workspace/` — API adapter/hooks, workspace page, progression component, and employee picker integration. Keep React Query keys in this feature.
- `frontend-vite/src/pages/review/ReviewCreateModal.tsx` — replace UUID text/paste controls with selected employees once the directory API exists.
- `frontend-vite/src/pages/MySelfReviewPage.tsx` and `frontend-vite/src/pages/MyKpiPage.tsx` — remove employee-ID input only after `/me` API is available.
- `frontend-vite/src/pages/kpi/AssignmentModal.tsx` and `frontend-vite/src/pages/calibration/SessionFormModal.tsx` — reuse the selector; do not independently reinvent it.
- `frontend-vite/src/i18n/{ko,en,ja,vi,zh-CN}.ts` — update all locales from the Korean schema in the same change.

### Backend ownership / dependency

- `backend/src/main/java/com/easyperformance/readmodel/*` — add the read-only directory query service/controller/DTOs using tenant-scoped repository methods.
- Authentication/principal bridge — provide the server-side user→employee resolution used by the “my” endpoints.
- Existing review, KPI, and calibration services — enforce role/object authorization on the new selector and principal-resolved calls; no frontend role logic is a substitute.

## Required boundary QA for the slice

- Directory response DTO ↔ `EmployeeSummary` TypeScript type: names, nullability, pagination wrapper, active-status filtering.
- Cycle workspace route ↔ every navigation link and quick action.
- Selected employee `id` → review creation payload; confirm batch `createdCount`/`skippedCount` maps to feedback.
- Principal-resolved “my” API ↔ session/JWT mapping; prove a caller cannot fetch a different employee by query parameter.
- Cycle and review transitions ↔ the status action actually offered by the workspace.
- Tenant A directory/member/result must not appear with Tenant B credentials.

## Audit conclusion

The existing product has substantial domain controls, but its user journey remains disconnected because it exposes Core Master UUIDs as form inputs and has no participant directory or cycle-centred progression. The narrow slice above creates an operational path while preserving Core Master ownership, tenant isolation, React Query SSOT, i18n, and existing server state machines.

# Evaluation workspace frontend implementation

## Delivered surface

- Default route is the role-aware evaluation workspace; legacy pages remain routable for their focused operations but are removed from primary navigation.
- HR/SUPER: cycle progression, server gate blockers, named participant and reviewer assignment, open/advance/publish/close controls, server-provided results analysis.
- Manager: server-scoped team queue; selected-member goal approval/return, intermediate progress feedback completion, KPI-item scoring draft/submit.
- Member: principal-derived workspace only (no employee UUID input), goal creation, mid-cycle performance progress, self-review, published-result acknowledgement and appeal.
- Calibration: native workspace calibration-task query, named employee and session selection, target/current distribution, and required adjustment reason.
- Goal authoring includes target and unit in addition to the weighted agreement fields. The workflow adapter exposes update/resubmit operations for rejected goals.

## New files

- `frontend-vite/src/api/evaluationWorkspace.ts` — React Query API contract adapter.
- `frontend-vite/src/features/evaluation-workspace/EvaluationWorkspacePage.tsx` — role workspace UI.
- `frontend-vite/src/features/evaluation-workspace/phaseMap.ts` — status-to-operation phase map.
- `frontend-vite/scripts/evaluation-workspace-phase.test.ts` — Node test-first phase mapping check.

## Validation

- `node --experimental-strip-types --test scripts/evaluation-workspace-phase.test.ts` — pass (two phase-gate behaviours; first execution failed before implementation).
- `npm run typecheck` — pass.
- `npm run design:check` — pass; no raw hex or inline styles.
- `npm run local-ui:check` — pass.

## Pending integration evidence

The workflow API is being added concurrently. Browser verification must run after `/api/v1/evaluation-workspace/*` is available and the synthetic demo fixture creates named participants with manager assignments.

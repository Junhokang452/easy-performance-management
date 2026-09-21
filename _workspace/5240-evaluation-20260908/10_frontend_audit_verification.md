# Frontend audit verification status

Date: 2026-09-08 (Asia/Seoul)  
Owner: frontend-vite audit API, i18n, `ProgramAuditTimeline`, `ProgramOperationsPage`, and FE verification evidence.

## Static contract result — PASS

The existing frontend implementation was compared directly with the currently present backend audit endpoint. No source change was needed in the frontend-owned files.

| Contract | Frontend | Backend | Result |
|---|---|---|---|
| Endpoint | `GET /v1/evaluation-programs/{programId}/audit-events` | `GET /api/v1/evaluation-programs/{programId}/audit-events` (the shared client supplies `/api`) | Match |
| Query | `page`, fixed `size: 25`, optional `eventType`, optional `participantId` | Same optional filters; page default 0, size default 25, size 1–100 | Match |
| Page envelope | `content`, `totalPages` | Spring `Page<AuditRow>` | Match |
| Row projection | `id`, `participantId`, `eventType`, `reason`, `actorEmployeeId`, `createdAt` | `AuditRow` has exactly those six fields | Match |
| Sensitive snapshot | no `detailsJson` type or rendering | query service deliberately excludes raw snapshot data | Match |
| Access scope | administrative Program Operations surface only | `requireOperator`, then tenant-scoped program lookup and tenant+program repository predicate | Match |

`ProgramAuditTimeline` resets pagination when either filter changes, retains the current filters for explicit refresh, and disables invalid previous/next actions while the query is in flight. Its 18 event labels are defined once in `auditI18n.ts` and wired into ko/en/ja/zh-CN/vi through `programI18n.ts`; saved static checking already reports all five locale shapes and the BE/FE 18-event order as matching.

## Storybook static output — present

The shared UI package is `lib/easy-platform/easy-platform-core/packages/ui-components`, and the pre-existing static output is `storybook-static/` beneath it. Earlier read-only inspection found 151 output files (including `index.html`, `iframe.html`, and `index.json`) and the preserved build log ends in `Storybook build completed successfully`. No long-running Storybook or Python HTTP server was started for this verification.

## Execution-gate status — not evaluated after WSL freeze

Root directed the frontend agent to stop new WSL invocations while WSL recovery is assessed. Therefore the following are deliberately **not passed** for the current working tree:

| Required check | Status | Reason |
|---|---|---|
| `npm run typecheck` | Not evaluated | UNC working directory invokes Windows `cmd`, which cannot execute the Linux `node_modules/.bin/tsc`; a direct TypeScript retry produced no result before it was stopped under the WSL freeze instruction. This is not a TypeScript source failure. |
| `npm run test:i18n` | Not evaluated | Same runtime boundary. Historical evidence is available, but is not claimed for the current tree. |
| `npm run design:check` / `npm run local-ui:check` | Not evaluated | Same runtime boundary. |
| Production build | Not evaluated | Would run the package prebuild and shared core build; withheld while runtime is frozen. |
| OpenAPI generation/drift | Not evaluated | `openapi:types` targets `http://localhost:8087/v3/api-docs`; no product API listener was available. |
| Browser E2E | Not evaluated | No product frontend/API listener on 5174/8087; required admin and ordinary-user paths cannot be exercised. |

No source fix was applied because no code-originated gate failure was obtained. Do not convert the Windows UNC command-resolution failure into a dependency or lockfile change.

## Browser evidence still required after recovery

With the final backend running, capture JSON and screenshots for:

1. HR/SUPER_ADMIN: audit timeline at 1440px and 390px; filter reset, next/previous pagination, explicit refresh, page errors and horizontal overflow.
2. Ordinary user: endpoint and route access are rejected; no audit data is rendered.
3. ko/en/ja/zh-CN/vi: heading, filters, event labels, actor/reason fallback, and pagination labels.
4. Latest OpenAPI schema generation and a response-shape comparison with `api/audit.ts`.

Keep those artifacts in this directory. The frontend must not be marked fully verified until these runtime gates pass against the final intended worktree.

## Files in frontend ownership

- `frontend-vite/src/features/evaluation-programs/api/audit.ts`
- `frontend-vite/src/features/evaluation-programs/auditI18n.ts`
- `frontend-vite/src/features/evaluation-programs/programI18n.ts`
- `frontend-vite/src/features/evaluation-programs/components/ProgramAuditTimeline.tsx`
- `frontend-vite/src/features/evaluation-programs/pages/ProgramOperationsPage.tsx`

No existing change from other agents was reverted or modified.

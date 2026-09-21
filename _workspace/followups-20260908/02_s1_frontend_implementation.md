# S1 frontend implementation — automatic reviewer line

Date: 2026-09-08  
Scope owner: `frontend-vite/src/features/evaluation-programs/**`

## Implemented boundary

- Added `api/reviewerLineAutomation.ts` with React Query preview/apply mutations for the backend contract:
  - `POST /v1/evaluation-programs/{programId}/reviewer-line:preview`
  - `POST /v1/evaluation-programs/{programId}/reviewer-line:apply`
- The only client-selectable automatic roles are `AGREEMENT_REVIEWER`, `CHECKER`, `REVIEWER`, and `FINAL_FEEDBACK`; the default is `REVIEWER`. `SELF` and `ADJUSTER` are never inferred or offered.
- Added program-level `ReviewerLineAutomationTools` after roster/Excel tools and before notifications/audit in `ProgramOperationsPage`.
- The UI requires 1–100 selected active participants, uses the program-held as-of date (no client as-of input), previews first, displays source system/effective period/deletion state/existing reviewer count/proposals/blocker issues, then requires an explicit reason in a confirmation modal before apply.
- Apply echoes the preview hash, exactly previewed participant IDs/roles, and the explicit reason. A 409 stale response visibly directs the user to preview again. Success invalidates the evaluation-program React Query namespace, covering program, participants, reviewer, audit, and preview consumers.
- Apply rows use the final multi-role contract field `reviewerAssignmentIds` (plural array); the current UI intentionally reports aggregate applied/skipped counts rather than exposing assignment UUIDs.
- Existing manual reviewer editing, participant/reviewer Excel import/export, and generic notification tools are unchanged.
- Added the `program.automation` namespace to ko/en/ja/zh-CN/vi in `programI18n.ts`.

## Files

- `frontend-vite/src/features/evaluation-programs/api/reviewerLineAutomation.ts`
- `frontend-vite/src/features/evaluation-programs/components/ReviewerLineAutomationTools.tsx`
- `frontend-vite/src/features/evaluation-programs/pages/ProgramOperationsPage.tsx`
- `frontend-vite/src/features/evaluation-programs/programI18n.ts`

## Windows temporary-copy validation

The four files above were synced to the existing Windows temporary frontend copy; no npm installation or lockfile update was performed.

| Gate | Command form | Result |
|---|---|---|
| TypeScript | bundled Node → `node_modules/typescript/bin/tsc -b --pretty false` | PASS (exit 0) |
| i18n | bundled Node → `--experimental-strip-types --test scripts/i18n-conformance.test.ts` | PASS (7/7) |
| Workspace tests | bundled Node → `--experimental-strip-types --test scripts/evaluation-workspace-*.test.ts` | PASS (5/5) |
| Design rules | `check-design-system.mjs src --max-hex=0 --max-inline-style=0` | PASS (0/0, 0/0) |
| Local UI rules | `check-local-ui.mjs src --max-tags=0 --max-blocked-imports=0` | PASS (0/0, 0/0) |
| Production Vite build | bundled Node → `node_modules/vite/bin/vite.js build` (no package-script prebuild) | PASS (exit 0, 7,503 modules, 6.84s) |

The project does not declare or install Vitest (`node_modules/vitest` absent), and the temporary-validation policy prohibits an npm install. Consequently a new Vitest suite was not introduced solely for S1. Existing Node workspace tests, TypeScript, i18n, and rule gates passed above. Browser/API verification remains coordinated with root because the backend preview/apply endpoint and HCM read-model fixture must be running first.

## Browser smoke preparation

- Added `scripts/verify-reviewer-line-browser.cjs` by reusing the existing local-only production-asset proxy/login pattern. It reads the local verifier JSON's `browserProgramId` and `browserParticipants`, records JSON/screenshots, and is syntax-checked with the bundled Node runtime.
- It intentionally has not run while the local S1 API/fixture is unavailable. Once ready, it covers ko/en/ja/zh-CN/vi at 1440 and 390 widths, preview-only viewport passes, Korean source/blocker labels, overflow/page errors, confirmation-without-reason, close-without-action, fresh preview/apply/re-preview, and employee API/UI denial.
- The S1 card is now additionally rendered only for `HR_ADMIN` or `SUPER_ADMIN` in `ProgramOperationsPage`; backend 403 remains the authorization authority. The corrected temporary copy typecheck passed (exit 0).

## Final runtime evidence

- Rebuilt the synchronized Windows production bundle without an npm reinstall: Vite 8.0.16, 7,503 transformed modules, exit 0 (2.07s). The final bundled-node TypeScript check also passed (exit 0).
- Ran `scripts/verify-reviewer-line-browser.cjs` against the held local API at `127.0.0.1:8089` and the completed `reviewer-line-local.json` fixture. The final pass has 13/13 checks: ko/en/ja/zh-CN/vi at both 1440 and 390, each previewing `ready=1` and `blocked=1`; Korean source/block labels; no horizontal overflow, browser error, failed API request, or console error; reasonless apply disabled; confirmation close made no mutation; fresh preview applied one and skipped one; re-preview found the existing reviewer; and employee preview received 403 with the HR-only card absent.
- Screenshots and machine-readable evidence: `_workspace/followups-20260908/browser-s1/result.json` and `reviewer-line-*.png`. The final run is authoritative; `failure.png` is the preserved diagnostic from the first verifier-only attempt, whose assertion assumed modal DOM removal rather than Mantine's hidden-state transition.

## OpenAPI regeneration and boundary check

- Regenerated `frontend-vite/src/api/generated/schema.d.ts` with `openapi-typescript 7.13.0` from `_workspace/followups-20260908/s1-openapi.json`, using the existing Windows temporary dependency installation. The generator cannot write directly to a UNC output path, so it generated in the Windows temporary frontend tree and copied the mechanical result to the source tree; source and temporary copies match byte-for-byte (325,680 bytes; SHA-256 `048fb4d01283557117102655106ff3f841a59284d993308bb5a326cc362b38c6`).
- The generated schema contains both reviewer-line preview/apply paths and the contract fields `previewHash`, nullable source metadata, `currentReviewerCount`, and plural `reviewerAssignmentIds`. The frontend client template remains `/v1/evaluation-programs/${programId}/reviewer-line:${action}`; its shared HTTP client supplies the `/api` prefix, demonstrated by the successful browser calls to the real `/api/v1/...` endpoints.
- `E9804941` and `E9804256` remain standard shared API-error codes rather than generated endpoint-specific schema literals; the UI retains its 409 stale-preview handling and backend runtime tests cover their HTTP semantics.

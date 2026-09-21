# Frontend extension map — evaluation follow-ups

Date: 2026-09-08  
Scope: read-only investigation before backend contract and standards gate. No product source changed by this map.

## Current extension points

| Follow-up | Current UI/API surface | Reuse decision |
|---|---|---|
| S1 automatic evaluation line | `src/features/evaluation-programs/pages/ProgramOperationsPage.tsx`, `components/OperationsUtilities.tsx`, `api/programs.ts` | Preserve the current per-participant `ParticipantAdministration` editor, reviewer Excel import/export, and manual reviewer replacement. Add a program-level automation card; do not overload the manual editor. |
| S2 KPI linkage | `src/api/kpi.ts`, `src/pages/review/ReviewKpiItemsTable.tsx`, `src/pages/MyKpiPage.tsx`, `src/pages/ManagerReviewPage.tsx` | Reuse query-key/API-client patterns and the read-only KPI item table only after the evaluation-program mapping response is defined. KPI effective values, achievement rate, and auto score are server-calculated/displayed values; the frontend must not recompute them. |
| S3 incomplete-owner reminders | `NotificationTools` in `components/OperationsUtilities.tsx`, notification mutations in `api/programs.ts` | Reuse template preview, queue, dispatch, mutation/error-toast handling and program-level placement. Backend must provide the actual responsible recipient and dedupe eligibility; do not derive these from the current active-participant list in the browser. |
| S4 custom PDF | `programsApi.exportResults`, `AnalyticsPage.tsx`, `components/downloadBlob.ts` | Reuse Blob download/error path for a backend-generated PDF. Existing results XLSX is retained unchanged; no browser-side PDF assembly. |

## S1: existing manual and Excel paths

`RosterAndGuideTools` is already rendered at program scope in `ProgramOperationsPage` and contains:

- participant XLSX export/import (`exportParticipants`, `useImportParticipantsMutation`);
- reviewer XLSX export/import (`exportReviewers`, `useImportReviewersMutation`);
- manual participant addition with employee and effective assignment lookup.

For an individually selected participant, `ParticipantAdministration` renders a mutable reviewer draft list, role/round/weight controls, then calls `useReplaceReviewersMutation`. This is the explicit manual override path and must remain available after automation. The selected participant detail is intentionally not the correct place to start a program-wide automatic assignment run.

## Recommended S1 placement and interaction

Add a new pure visual `AssignmentAutomationTools` component under `src/features/evaluation-programs/components/`, rendered in `ProgramOperationsPage` **after** `RosterAndGuideTools` and **before** `NotificationTools`/audit timeline. This preserves the existing operations order: roster is established first, a line can be previewed/applied next, reminders are handled after assigned work exists, and audit remains the resulting history.

Recommended flow:

1. Program-level criteria/effective-date inputs and a **Preview automatic assignment** button issue a non-mutating React Query request.
2. Render a preview `SectionCard`/responsive table grouped by participant. Each row must show proposed reviewer(s), relationship/effective-date basis, role/round, and an explicit status: eligible, no valid source, ambiguous, or blocked/conflict. Do not hide incomplete source rows.
3. Keep manual/Excel buttons enabled and clearly label preview as unapplied. Preview has no side effect and no reviewer replacement.
4. Only after a populated preview, open a `UiModal` confirmation using existing project modal patterns. It must state the count eligible to apply and the count excluded, retain the server-provided policy/version or preview token, and require an explicit user confirmation (plus a reason if backend contract requires it).
5. The **Apply** mutation sends only the confirmed server preview/version. On success invalidate program, participants, selected participant reviewers, dashboard, and audit query keys, then show the existing toast pattern. Do not optimistically manufacture reviewer rows.

This makes the required `preview → user confirmation → apply` boundary visible and prevents an implicit score or reviewer determination. Final type names, URL, preview-token/expiry, conflict behavior, and audit reason remain contract-dependent and must be supplied by Sol before implementation.

## KPI and score display reuse

`src/api/kpi.ts` already models `MyKpiAssignmentResponse` with server-owned effective weight/target, latest actual, and achievement rate. Its comments explicitly prohibit frontend recalculation. `ReviewKpiItemsTable` is the reusable score display: read-only mode shows node, weight, target, actual, achievement rate, auto score and item score; score-input mode is manager-review-only.

For S2, add an evaluation-program-specific hook/API file only when the backend specifies which program, participant, KPI cycle and frozen calculation/version are related. Reuse `ReviewKpiItemsTable` only if the new response is contract-compatible; otherwise create an evaluation-domain read-only wrapper rather than coercing KPI data with casts. The current `GoalResponse` supports catalog and department-goal links, not a program-to-KPI-assignment mapping.

## Notification and download reuse

`NotificationTools` currently previews templated messages, queues, and dispatches to every active participant employee ID. S3 must not reuse that recipient derivation: the backend needs to return owner, incomplete work, dedupe key/state, and eligibility. A sibling `IncompleteOwnerReminderTools` card in the same operations section can reuse form controls, `SectionCard`, `showToast`, and the preview/queue/dispatch interaction after that contract exists.

`AnalyticsPage` calls `programsApi.exportResults(programId)` and passes its Blob to `downloadBlob` as `evaluation-results.xlsx`; the custom pivot follows the same pattern. S4 should add a distinct backend PDF endpoint/hook and call `downloadBlob(blob, <server-safe-name>.pdf)`. It must not alter the existing XLSX endpoint or create a client-side PDF representation.

## Five-locale and component conventions

Evaluation-program strings are centralized in `src/features/evaluation-programs/programI18n.ts`, which contains Korean, English, Japanese, Simplified Chinese, and Vietnamese dictionaries. New S1/S3/S4 labels belong in a dedicated nested namespace there, with parity in all five locales; avoid literals in page/component code. Shared global KPI/review strings are in `src/i18n/{ko,en,ja,zh-CN,vi}.ts`; do not split a new evaluation-program namespace across both stores.

The page uses `@easy/ui-components` wrappers (`SectionCard`, `UiButton`, `FormSelect`, `UiModal`, `UiTable`) and React Query hooks. New components must keep data/mutations in a feature API hook and stay visual at component level; do not use Mantine core controls directly or duplicate server state in local state.

## Contract gates before frontend implementation

- S1: preview and apply request/response, policy/effective-date semantics, reviewer role/round/weight ownership, unassignable/conflict row rules, preview expiry/version, audit event, and authorization.
- S2: program/participant-to-KPI binding, calculation formula/version and frozen evidence, null/missing KPI behavior, and visibility/authorization.
- S3: responsible-owner aggregation, stage criteria, notification template variables, dedupe idempotency, queue/dispatch behavior, and read-model source.
- S4: PDF endpoint, result visibility/authorization, file name/content disposition, Korean font/render policy, output options, and error shape.

After those contracts and the standards mapping are approved, frontend ownership covers API clients/hooks, operations cards, i18n parity, type/build gates, and browser evidence. Existing dirty changes remain untouched.

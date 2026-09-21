# RootHR-inspired evaluation acceptance checklist

Executable checklist for the Easy Performance redesign. Verify each item with the named actor and record the resulting state/event. The minimum path is the smallest usable release; advanced items are reference parity targets.

Sources: [admin guide category](https://roothr.co.kr/guide/admin-guide/evaulation/) · [member guide category](https://roothr.co.kr/guide/member-guide/evaulation-member-guide/) · [admin overview](https://roothr.co.kr/guide/use-cases/evaulation/%EC%9D%B8%EC%82%AC%ED%8F%89%EA%B0%80-%EA%B4%80%EB%A6%AC-%EA%B0%9C%EC%9A%94/) · [performance runbook](https://roothr.co.kr/guide/use-cases/evaulation/%EC%84%B1%EA%B3%BC%C2%B7%EC%97%AD%EB%9F%89-%ED%8F%89%EA%B0%80-%EC%9A%B4%EC%98%81-%EA%B0%80%EC%9D%B4%EB%93%9C/)

## Minimum usable path

| # | Actor | Action | Pass condition |
|---|---|---|---|
| M1 | HR | Create a cycle with title, period, employee population, review rubric, and at least one enabled review stage. | Cycle saves as planned/draft; required fields and score/grade basis are validated. |
| M2 | HR | Review the stage preview, then open the cycle. | Employee cards remain hidden before OPEN and become visible after OPEN; transition is auditable. |
| M3 | HR | Generate participants and assign a manager/director reviewer for every participant. | Roster shows subject, reviewer, group, period, and status; missing mappings are actionable blockers. |
| M4 | Employee | Open the assigned evaluation and save a draft self-review (if enabled), then submit. | Only configured stages appear; draft is editable, submitted work is statused and editing is restricted. |
| M5 | Manager/director | Open assigned subject, inspect goals/context, enter score or grade and comment, save draft, then complete. | Reviewer sees only assigned subjects; required comments and rubric validation apply; completion advances the subject. |
| M6 | HR | Attempt calculation with an incomplete reviewer submission, then complete all required submissions and calculate. | First attempt explains blockers; second creates deterministic result data using configured weights. |
| M7 | HR | Finalize/close the cycle after reviewing scores and grades. | Finalized cycle is read-only for result content and has a completion timestamp/audit event. |
| M8 | Employee | View the published result, read manager feedback, and acknowledge it. | Unpublished result is unavailable; published result shows the employee’s result and feedback; acknowledgement is idempotent. |
| M9 | Manager/director | Reopen the assigned list after completion. | Current round/completion state is visible; unauthorized subjects or closed writes are unavailable. |

## Advanced reference parity

| # | Actor | Action | Pass condition |
|---|---|---|---|
| A1 | HR | Configure multiple rounds (self, 1st, 2nd, 3rd), goal agreement, check-in, calibration, and feedback with independent date windows. | Stage cards and APIs are conditional; a later round cannot start until its prerequisite is complete. |
| A2 | HR | Define reusable criteria library items and apply them to department/role groups. | Criteria are reusable but cycle configuration is snapshotted; group weights total 100%; changes show reapply impact. |
| A3 | HR | Generate participants twice and import a roster file. | Existing subjects are not duplicated; malformed rows are reported; valid rows are reviewable before commit. |
| A4 | Employee + manager/director | Employee submits goals; manager rejects with feedback; employee revises and resubmits; manager agrees. | Goal state follows draft → requested → rejected → resubmitted → agreed; agreed goals become review context. |
| A5 | Manager/director | Complete a mid-cycle check-in with progress notes and direction feedback. | Check-in is visible only when enabled; it informs review context without silently changing final score. |
| A6 | HR + manager/director | Calculate relative results, enter calibration adjustments, and compare target/current grade distribution. | Calibrator role is required; each adjustment is attributable and history-preserving; distribution updates immediately. |
| A7 | Employee + HR | Employee files an appeal against feedback; HR/manager resolves it with a recorded decision; close feedback. | Appeal has a distinct state, resolution, and timestamp; completed feedback cannot be edited. |
| A8 | HR | Publish results selectively by employee and choose visible fields (score/grade/comment). | Publication scope is explicit; reports and employee view reflect only published fields. |
| A9 | HR + employee | Run a multi-rater cycle with manager, peer, subordinate, and self raters. | Rater weights calculate independently; no manager-only stages are forced into the 360 path; employee can compare personal, org, and item averages where policy allows. |
| A10 | HR | Open the progress dashboard and select a blocker. | Dashboard shows per-stage requested/completed counts and links to the exact participant, reviewer, or missing configuration needing action. |

## Contract omissions to resolve before implementation

The current performance contract and workspace phase map cover cycle, self-evaluation, manager review, calibration, finalized results, and reports. The checklist identifies explicit follow-up contracts for:

- employee goal entities and manager agreement/rejection loop;
- check-in records and stage gating;
- reviewer assignment across multiple rounds and manager/director role permissions;
- calibration adjustment history and target/current distribution;
- feedback acknowledgement, appeal, resolution, and immutable completion;
- selective result publication and field visibility;
- criteria library/versioning and participant import/preview;
- separate 360/multi-rater lifecycle and anonymity/visibility policy.

These are requirements extracted from the source workflows, not a request to reproduce RootHR’s language or interface.


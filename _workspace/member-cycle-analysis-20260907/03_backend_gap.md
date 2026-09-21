# Backend evaluation-cycle capability and gap analysis

Date: 2026-09-07 KST  
Scope: read-only inspection of the current `easy-performance-management/backend` source. No code,
schema, runtime, or deployment change was made for this analysis.

## Evidence boundary

The 16 RootHR member-detail guides and the administrator links were not available in this bounded
task. The comparison axes came from the coordinator's requested list: evaluation kinds and
selectable stages, goal agreement, multi-round review, performance tasks/activities/department
goals, interview review and sharing, scales/questions, calibration, feedback, and reports.
Statements about the current product are verified from source. Statements about what RootHR may
require are hypotheses to confirm against the collected guides; they are not claims about RootHR's
actual wire contract or data model.

## Overall assessment

The backend has a usable, secure **fixed KPI evaluation workflow**:

`roster → goal draft/submit/approve → KPI actual + check-in → intermediate review → self review →
manager review → calibration → frozen report → acknowledgement/feedback/appeal → close`.

It is not yet a configurable enterprise evaluation framework. The implemented path assumes one
standard phase sequence, one active participant row, one primary manager in round 1, one review
aggregate per employee and cycle, and KPI as the only live scoring component. The database contains
some seams for future expansion (`ReviewerType`, `review_round`, MBO/competency/MRA columns), but
those seams must not be reported as functioning features.

## Capability matrix

| Area | Verified current capability | Missing or materially incomplete |
|---|---|---|
| Evaluation kind and stages | A cycle stores cadence type (`HALF_ANNUAL`, `ANNUAL`, `QUARTERLY`, `MONTHLY`, `CUSTOM`) and follows a guarded lifecycle. | Cadence is not evaluation kind. There is no evaluation template/type such as probation, promotion, leadership, competency-only, project, or ad-hoc review. Stages cannot be selected, reordered, repeated, or skipped per cycle. |
| Participant and reviewer setup | HR replaces the roster with active employees and one primary manager. Removed members are excluded and assignments revoked. Actor, tenant, employee binding, scoped reads, allowed actions, and blockers are server-owned. | No population rules, dynamic org cohorts, exceptions with effective dates, delegated operation scope, secondary approvers, committee membership, or bulk policy inheritance. |
| Goal agreement | Employee/HR/assigned manager can create a KPI-backed goal; the employee can edit draft/rejected goals, submit, and the assigned manager can approve or reject with a comment. Actual, evidence URL, note, and 0–100 progress check-ins persist. | No configurable agreement route, parallel/serial approval chain, co-editing, negotiation/version history, per-goal visibility, reusable goal library, bulk import, or separate employee-proposed versus company-assigned goal types. All goals are forced through the same approval rule. |
| Multi-round and multi-rater review | Reviewer rows have type (`MANAGER`, `PEER`, `HR`), round, and weight columns. | Runtime roster creation always writes `MANAGER`, round `1`, weight `1`. The review is unique per cycle and employee and only has self plus one manager input. There are no reviewer-specific response instances, round deadlines, anonymous peers, weighted round aggregation, skip/delegation, or second-level approval. |
| Performance tasks, activities, and org goals | KPI trees support corporate/division/team/individual levels, hierarchy, owner org, BSC perspective, assignment overrides, actual history, and a future cross-tree cascade reference. | A workspace goal currently creates a new individual KPI tree for that goal; it does not establish a governed link to an existing department goal. There is no task/activity/milestone/dependency model, work log, multiple evidence attachments, achievement approval, project membership, or organizational goal roll-up service. `PersonalOkr` is a separate CRUD domain and is not part of the evaluation workflow. |
| Intermediate review and interview | Employee records progress summary, achievements, blockers, and needed support, then submits; manager records a comment and completes the intermediate review. A separate mentor-feedback CRUD domain exists. | No interview/meeting aggregate, schedule, location/channel, attendees, agenda, minutes, action items, signatures, employee confirmation, reschedule/cancel state, or interview history. Mentor feedback is not linked to a cycle, goal, intermediate review, or final report. |
| Sharing and visibility | Workspace queries scope employees/managers/operators. Employee-facing review fields are masked before report publication. Reports are visible through the actor-bound facade. | Visibility is hard-coded rather than policy-driven. There is no audience model for HR-only, manager chain, employee, calibration committee, shared/private comments, field-level release dates, or attachment confidentiality. |
| Scales, score components, and questions | Policy selects `S/A/B/C/D`, `1–5`, or `1–100`; distribution mode is absolute, forced, or hybrid. KPI item auto scores and manager scores feed a frozen KPI score. | No custom scale definition, label/description/color, score bands, N/A behavior, rounding rule, score-component weights, competency/behavior library, question bank, questionnaire/version, section, question type, required rule, branching, or localized form content. `mboScore`, `competencyScore`, and `mraScore` are schema placeholders and are published as `null`. Forced distribution supports only `S/A/B/C/D`. |
| Calibration and adjustment | Sessions carry org scope, scheduled time and participant IDs; they support status changes, individual grade adjustment with reason, JSON adjustment history, distribution simulation/application, confirm, and review finalization. | No explicit committee member/role model, attendance, quorum, meeting notes, evidence comparison, multi-level calibration, org-specific quota policy, recommendation versus final decision split, or append-only adjustment entity. The session's participant list and logs are JSON snapshots rather than independently governed records. |
| Feedback and appeal | One feedback row per report supports manager comment, completion, employee acceptance, appeal, resolution comment and close guards. Policy can disable appeal. | No threaded conversation, multiple feedback providers, confidential notes, attachments, development-action ownership/due date, repeated appeal rounds, escalation chain, or notification/reminder contract. Although `ADJUSTMENT_REQUIRED` remains in the enum/schema, the current service rejects it as unsupported; the functioning resolution is effectively `UPHELD`. |
| Report and analytics | Finalized values and KPI item details are frozen into an append-only report snapshot. Publish, supersede/re-publish, view, acknowledgement, grade distribution, overall summary, and org-unit aggregation exist. | No configurable report template/sections, comparison periods, goal lineage, question answers, competency/MRA breakdown, interview history, development plan, PDF/Excel export, scheduled distribution, dashboard filters, saved views, or report-level sharing policy. `nextAction` and non-KPI score components are deliberately `null`. |
| Workflow governance | Server-derived blockers prevent advancing with missing goals, actuals, intermediate completion, self/manager submission, calibration, report publication, acknowledgement, or feedback. Roster and mutable workflow rows use tenant scoping and optimistic versioning where applicable. | The workflow graph, gates, due dates, grace periods, reminders, reopen rules, exceptions, delegated HR roles, and notification events are not configurable. The cycle is closed by transitioning from `CALIBRATION` to `FINALIZED`; there is no distinct immutable `CLOSED` cycle status. |

## Verified foundation in detail

### 1. Fixed lifecycle and server-owned progression

- `domain/evaluationcycle/entity/CycleStatus.java:32-40` defines the single lifecycle from
  `PLANNED` through `FINALIZED` or `CANCELLED`.
- `workflow/EvaluationWorkflowService.java:73-135` opens a planned/active cycle and advances it
  through a fixed switch of goal, intermediate, self, and manager gates.
- `workflow/EvaluationWorkflowService.java:138-179` requires finalized reviews, active reports,
  employee acknowledgement, and accepted or resolved feedback before the final cycle transition.
- `workflow/EvaluationWorkspaceDtos.java:27-39` exposes blocker codes and allowed actions so the
  UI does not invent progression rules.

This is a strong execution foundation for one standard workflow. A configurable stage graph would
need a new versioned definition layer; adding more conditionals to `CycleStatus` would make each
evaluation type interfere with the others.

### 2. Roster and reviewer seam

- `workflow/ParticipantRosterDtos.java:12-20` accepts only `employeeId` and
  `managerEmployeeId` per participant.
- `workflow/ParticipantRosterService.java:44-104` implements replacement semantics and revokes
  assignments for removed participants.
- `workflow/ParticipantRosterService.java:85-97` proves that every live assignment is currently
  fixed to `ReviewerType.MANAGER`, round `1`, weight `1`.
- `workflow/EvaluationReviewerAssignment.java:36-41` has future-facing reviewer type, round and
  weight fields, but no current facade mutation exposes them.
- `domain/review/entity/PerformanceReview.java:44-52` makes the review unique by tenant, cycle and
  employee, which prevents storing independent reviewer/round submissions in the current aggregate.

The reviewer-assignment table can be retained as the assignment header, but true multi-round
evaluation needs separate immutable response/submission records keyed by assignment and round.

### 3. Goal and achievement foundation

- `workflow/EvaluationWorkspaceDtos.java:45-54` defines goal create/update/decision and response
  fields: title, description, weight, target, unit, status and decision audit.
- `workflow/GoalAgreementService.java:42-90` creates a KPI tree, node and assignment behind each
  workspace goal.
- `workflow/GoalAgreementService.java:93-171` implements rejected-goal editing, employee submit,
  and assigned-manager approve/reject.
- `workflow/GoalAgreementService.java:116-151` stores an append-only KPI actual and separate
  check-in progress metadata, then rehydrates progress on GET.
- `domain/kpi/entity/KpiTree.java:47-66` and `KpiNode.java:53-96` provide org ownership, hierarchy,
  BSC classification, source and future cascade identifiers.

The important distinction is that hierarchical KPI storage exists, while governed organizational
goal cascading and agreement workflows do not yet exist.

### 4. Review and scoring foundation

- `domain/review/dto/ReviewDtos.java:36-84` exposes one self comment and one manager comment plus
  per-KPI manager scores.
- `domain/review/entity/PerformanceReview.java:74-116` stores KPI, MBO, competency, MRA and final
  score columns, but its source comments explicitly mark all non-KPI components as future work.
- `domain/report/service/ReportService.java:237-255` is controlling evidence: MBO, competency, MRA
  and next action are written as `null` into the frozen report.
- `workflow/EvaluationWorkspaceController.java:228-263` provides actor-scoped review detail,
  KPI items, self draft/submit and manager draft/submit routes.

This supports a complete KPI review, not yet a configurable form-based or composite evaluation.

### 5. Calibration, release and feedback foundation

- `domain/calibration/dto/CalibrationDtos.java:35-67` defines session, transition, adjustment and
  confirmation commands; lines 134-201 define distribution simulation/application.
- `workflow/EvaluationWorkspaceController.java:270-309` exposes the secured calibration task,
  session, adjustment, distribution and confirm facade.
- `domain/report/entity/PerformanceReport.java:75-108` freezes publication content and preserves
  supersede history while allowing only view/acknowledgement metadata to change.
- `workflow/PerformanceFeedback.java:20-52` stores one feedback/appeal lifecycle per report with
  optimistic versioning.
- `workflow/EvaluationWorkspaceController.java:313-354` exposes publish, personal report,
  acknowledge, feedback, appeal and result-summary routes.

These are appropriate reusable execution services beneath a future configurable evaluation
definition. They do not by themselves provide templates, questionnaires, audience rules, or
multi-round orchestration.

## Recommended backend expansion boundaries after guide confirmation

These are architecture candidates, not implementation decisions for this analysis turn.

1. **Versioned evaluation definition**: evaluation kind/template, selected stages, order, dates,
   stage gates, score components, visibility/release and appeal settings. A cycle should reference
   a frozen definition revision.
2. **Form and scale revision model**: custom scale, bands, sections, questions, options and required
   rules. Participant review responses should reference the frozen form revision.
3. **Reviewer assignment plus submission model**: keep the current assignment header, add one
   submission per reviewer/round/component with immutable submit snapshots and explicit aggregation.
4. **Goal lineage and work evidence**: distinguish company-assigned, cascaded and employee-proposed
   goals; add agreement revisions, activities/milestones and evidence records rather than adding
   more columns to KPI actuals.
5. **Interview aggregate**: schedule, participants, agenda, notes, action items, confirmations and
   visibility. Link it to participant, stage and optionally goals; do not overload the current
   intermediate-review comment fields.
6. **Publication projection**: compose reports from frozen goal, form, score, interview and action
   snapshots while retaining the current append-only publish/supersede mechanism.

## Questions that the 16 guides must settle

- Which evaluation kinds exist, and which stages are mandatory/optional/repeatable for each?
- Is goal agreement employee-proposed, manager-assigned, mutual, or selectable per cycle?
- How many reviewer groups and rounds exist, and how are their weights, anonymity and deadlines set?
- Are department goals copied, referenced, or cascaded with inherited weights and later snapshots?
- Is an interview required, who confirms it, and which notes are visible to employee/manager/HR?
- Are scales global, per template, per section, or per question? How are N/A and rounding handled?
- Does calibration produce a recommendation, a final grade, or both, and can it occur at multiple
  organization levels?
- Can an appeal cause a score/grade revision? If so, what immutable revision and re-publication
  chain is expected?
- Which report sections are visible at which release point, and which exports are required?

Until these answers are available, the fixed workflow should be treated as the validated baseline,
and the missing configurable-definition layer should remain explicit rather than inferred from UI
screenshots.

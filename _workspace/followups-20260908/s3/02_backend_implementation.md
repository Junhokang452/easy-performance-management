# S3 backend implementation

Date: 2026-09-08

## Outcome

Implemented operator-triggered, IN_APP-only reminders for the current incomplete evaluation owner.
The implementation derives the owner on the server, previews before mutation, revalidates under the
program write lock, preserves exact idempotent replay, and limits reminders to one logical work episode
per UTC day. The UTC daily policy was explicitly approved by the user on 2026-09-08; there is no scheduler
or automatic repeat delivery.

Endpoints:

- `POST /api/v1/evaluation-programs/{programId}/incomplete-reminders:preview`
- `POST /api/v1/evaluation-programs/{programId}/incomplete-reminders:queue`
- `GET /api/v1/evaluation-programs/{programId}/incomplete-reminders?page=0&size=20`

The reminder path never calls the EMAIL dispatcher. New notifications are persisted as `IN_APP/SENT`,
which means “registered in the inbox”, not read or task-completed.

## Product changes

Added:

- `ProgramReminderDtos.java`
- `ProgramReminderRun.java`
- `ProgramReminderRunRepository.java`
- `ProgramReminderController.java`
- `ProgramReminderService.java`
- `V20260908_003__responsible_reminders.sql`
- `ProgramReminderServiceTest.java`
- `ProgramReminderControllerMappingTest.java`

Updated:

- `ProgramNotification.java`, `ProgramNotificationRepository.java`
- `ProgramTypes.java`, `ProgramErrorCode.java`, `ProgramJson.java`
- `ProgramExecutionService.java`, `ProgramExecutionAccessTest.java`
- `ProgramParticipantRepository.java`, `ProgramReviewerAssignment.java`,
  `ProgramReviewerAssignmentRepository.java`, `ProgramGoalRepository.java`,
  `ProgramReviewSubmissionRepository.java`, `ProgramIntermediateReviewRepository.java`,
  `ProgramCalculationRepository.java`, `ProgramAdjustmentRepository.java`,
  `ProgramFeedbackRepository.java`, `RmEmployeeRepository.java`

No frontend, HCM, easy-platform-core, SMTP, external delivery, score, goal content, evaluation answer,
or calculation behavior was changed. `ProgramExecutionService` mutation paths now reuse the existing
program pessimistic lock so completion and reminder queueing are serialized; read paths remain unlocked.

## Key guards

- HR/SUPER operator and tenant-bound program/participant checks.
- Explicit participant IDs only, 1..100; supported stage filters only.
- Program and reviewee-group stage enablement is enforced.
- Owner must be exactly one active, tenant-local employee; there is no manager/HR fallback.
- APPEALED feedback resolves to the actual `FINAL_FEEDBACK` assignee.
- `INVALIDATED` self-review remains pending; inconsistent one-sided completion is blocked.
- Impossible agreement-goal states are excluded as `SOURCE_INCONSISTENT`.
- Preview hash excludes notification duplicate state but includes mutable source/owner/due-date state.
- Same-day duplicate uses the existing notification and returns `DUPLICATE_SUPPRESSED`.
- Unchanged BLOCKED candidate selection is typed 422; missing/changed candidate or UTC-date drift is 409.
- Exact same idempotency request returns its stored response even after program finalization.
- Five server locales (`ko`, `en`, `ja`, `zh-CN`, `vi`) and rendered subject/body bounds are validated
  before persistence.
- Migration uses tenant-leading indexes and `VARCHAR(64)` SHA-256 fields.

## Verification

Validation ran only in the isolated Windows copy:

`C:\Users\SAMSUNG\AppData\Local\Temp\easy-performance-validation-b1ce4a863a3c41ffb3bd65cfa5fe87a3\easy-performance-management`

- `compileJava`: PASS.
- Focused `ProgramReminderServiceTest`: **17/17 PASS** (13 methods; five-locale parameterization).
- Controller wire mapping regression: **1/1 PASS** (three approved paths plus two rejected slash-colon paths).
- Full backend `test`: **322/322 PASS**, failures 0, errors 0.
- `bootJar`: PASS.
- Source-to-temp parity: **84 files checked, 0 missing/mismatched** across the program main/test trees,
  `RmEmployeeRepository.java`, and the S3 migration.
- JAR: `backend/build/libs/easy-performance-management-backend-0.1.0.jar`
- Size: 72,631,025 bytes.
- SHA-256: `C47A9312BE259DC211A450A8A3F761053FE3CA6A08798485E209686DEB7D06DF`

The first full run had one test-fixture-only failure because `ProgramExecutionAccessTest` still stubbed the
old plain program lookup. Updating that assertion fixture to `findLocked` produced the passing full suite.
The first actual HTTP attempt then found that Spring combined a class-level `/incomplete-reminders` mapping
and method-level `:preview` into `/incomplete-reminders/:preview`. R01 moved the common mapping to the
program base and declared all three approved paths explicitly. The MockMvc regression and final 322/322
suite verify the repaired wire contract.

## Remaining verification

Actual PostgreSQL migration/API/browser checks are owned by root and intentionally run after the frozen JAR
handoff. The backend implementation should remain frozen unless that runtime verification finds a scoped defect.

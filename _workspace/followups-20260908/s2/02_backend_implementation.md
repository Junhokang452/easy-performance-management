# S2 KPI evidence backend implementation

Date: 2026-09-08  
Scope: `easy-performance-management` PA backend only

## Outcome

- Added explicit one-goal-to-one-KPI-assignment preview/apply/history APIs defined in `01_backend_contract.md`.
- Persisted each apply as an immutable goal-level evidence revision. Refresh supersedes only the same goal's prior evidence; replay returns the original revision and never reactivates it.
- Kept the feature evidence-only: no `ProgramCalculation`, goal content/status, KPI assignment/actual, submission score, or final grade is mutated.
- Captured the current KPI target/weight plus the selected actual leaf and formula result. `capturedAt` is truncated to PostgreSQL microsecond precision.
- Reused a shared server score policy. Formula: `clamp(round(round(actualValue/effectiveTarget,6)*100,2),0,100)`.
- Replaced the legacy KPI latest-actual root-only query with a shared leaf selector. Leaves are resolved over all correction history before cutoff filtering, so a future successor cannot resurrect an old root.
- Enforced tenant/actor/program/participant/goal/source ownership, active participant, program/cycle/cutoff windows, preview hash freshness, program lock serialization, and freeze after REVIEWER completion or calculation creation. SELF completion remains allowed.
- Added typed 404 for missing/foreign KPI links and typed 409 for stale/conflicting apply.

## Files

Added:

- `backend/src/main/java/com/easyperformance/domain/kpi/service/KpiActualSelector.java`
- `backend/src/main/java/com/easyperformance/domain/kpi/service/KpiScorePolicy.java`
- `backend/src/main/java/com/easyperformance/program/ProgramKpiDtos.java`
- `backend/src/main/java/com/easyperformance/program/ProgramKpiEvidence.java`
- `backend/src/main/java/com/easyperformance/program/ProgramKpiEvidenceRepository.java`
- `backend/src/main/java/com/easyperformance/program/ProgramKpiEvidenceService.java`
- `backend/src/main/java/com/easyperformance/program/ProgramKpiEvidenceController.java`
- `backend/src/main/resources/db/migration/V20260908_002__program_kpi_evidence.sql`
- `backend/src/test/java/com/easyperformance/domain/kpi/service/KpiActualSelectorTest.java`
- `backend/src/test/java/com/easyperformance/program/ProgramKpiEvidenceServiceTest.java`

Modified:

- KPI repositories: `KpiActualRepository`, `KpiAssignmentRepository`, `KpiNodeRepository`, `KpiTreeRepository`
- KPI/review services: `KpiService`, `ReviewService`
- Program repositories/services/types: `ProgramGoalRepository`, `ProgramCalculationRepository`, `ProgramReviewSubmissionRepository`, `ProgramJson`, `ProgramExecutionService`, `ProgramErrorCode`, `ProgramTypes`
- Regression test: `KpiServiceTest`

## Verification

- Windows isolated copy: `test bootJar` PASS in 3m26s against the frozen product source.
- Final full backend suite after three freeze-boundary tests were added: **304/304 PASS** in 40s.
- Focused `ProgramKpiEvidenceServiceTest`: **7/7 PASS**.
- Source-to-Windows-copy SHA-256 parity for the 24 owned implementation/test files: **24/24**.
- Boot JAR: `C:\Users\SAMSUNG\AppData\Local\Temp\easy-performance-validation-b1ce4a863a3c41ffb3bd65cfa5fe87a3\easy-performance-management\backend\build\libs\easy-performance-management-backend-0.1.0.jar`
- Boot JAR SHA-256: `8F1B2132EBAAB78166007F449CC791D2A3C1BC2F2E954F603F20C8D0DDA0FFDA`

## Remaining verification ownership

- Root owns the final PostgreSQL/HTTP smoke and cross-tenant/idempotency/concurrency evidence.
- No HCM, `easy-platform-core`, frontend, deployment, external connection, or existing S1 behavior was modified in this slice.

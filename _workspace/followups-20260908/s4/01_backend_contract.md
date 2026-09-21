# S4 backend contract — custom evaluation result PDF

Date: 2026-09-08  
Status: Phase 3a proposal; product code is not changed by this document.

## 1. Scope decision

S4 adds one synchronous, operator-only PDF export to the existing HR Analytics result surface. It does
not add an employee PDF, stored document, share link, email, scheduler, background job, public token,
or a new result calculation.

This is the narrow implementation requested by `s4/00_task.md`: the existing HR result summary is the
source, the existing XLSX/SVG endpoints remain unchanged, and the frontend only downloads server bytes.
The output is “custom” through an allowlisted title, locale, orientation, sections, and participant
columns—not through arbitrary HTML/CSS/templates.

## 2. Actual reusable contracts

### 2.1 HR result source — approved reuse

`ProgramAnalyticsService.resultSummary(actor, programId)` is the result SoR for this slice:

- `finalized()` calls `ProgramAccess.requireOperator(actor)`.
- Program lookup is `findByIdAndTenantId(programId, actor.tenantId())`; a foreign/missing program is 404.
- Program status must be `FINALIZED`; otherwise `RESULT_NOT_PUBLISHED` 409.
- `results()` includes only `ParticipantStatus.ACTIVE && resultPublished` participants.
- The score/grade comes from the latest `FINAL` calculation, with a completed adjustment used only when
  it references that exact calculation.
- Returned `ResultSummaryResponse` already contains the allowlisted fields needed here: `programId`,
  `finalizedCount`, grade counts, and rows of participant snapshot, final score, final grade, published,
  and visible feedback status.

The PDF exporter must call this service and must not copy its calculation logic.

`ProgramAnalyticsExportService.resultsXlsx()` proves the current operator export and field mapping, but
its `byte[]`/unbounded behavior is not copied blindly. The XLSX endpoint is preserved exactly.

### 2.2 Employee result sources — analyzed, excluded

- `ProgramExecutionService.workspace()` uses `ProgramAccess.self(...)`, `resultPublished`, and
  `MemberResultVisibility` to hide numeric fields for `GRADE_ONLY`; this is the safe future basis for an
  employee PDF.
- `ProgramAnalyticsService.personalReport()` checks operator-or-same-employee and applies `GRADE_ONLY`,
  but it is cross-program history and currently scans up to 10,000 programs. It is not a bounded
  single-program PDF source.
- `ProgramAnalyticsService.employeeFeedback()` is operator-only and exposes completed submissions and
  opinions. It must never be reused for an employee download.
- Raw `ReportController` `/reports/my` is currently under the catch-all HR/SUPER security rule and its
  DTO includes a separate legacy report snapshot. Its name does not make it an employee-safe API.

Therefore S4 does not mount a PDF action on `EvaluationWorkPage` or `PersonalReportPage`. A later employee
slice must define a distinct endpoint that calls `requireEmployeeActor` + `ProgramAccess.self`, requires
`resultPublished`, and applies `MemberResultVisibility` before building any output.

## 3. Exact HTTP contract

```http
POST /api/v1/evaluation-programs/{programId}/results.pdf
Content-Type: application/json
Accept: application/pdf
```

`EvaluationProgramController` remains the facade so the path stays adjacent to existing
`GET /{programId}/results.xlsx`.

```ts
type ProgramResultPdfLocale = 'ko' | 'en';
type ProgramResultPdfOrientation = 'PORTRAIT' | 'LANDSCAPE';
type ProgramResultPdfSection = 'SUMMARY' | 'PARTICIPANT_TABLE';
type ProgramResultPdfColumn =
  | 'EMPLOYEE_NO'
  | 'EMPLOYEE_NAME'
  | 'DEPARTMENT'
  | 'POSITION'
  | 'JOB'
  | 'SCORE'
  | 'GRADE'
  | 'FEEDBACK_STATUS';

type ProgramResultPdfRequest = {
  locale: ProgramResultPdfLocale;
  orientation: ProgramResultPdfOrientation;
  sections: ProgramResultPdfSection[];       // 1..2, distinct
  participantColumns: ProgramResultPdfColumn[]; // rules below
  title?: string | null;                     // trimmed, 1..100 when present
};
```

No client-supplied `employeeId`, tenant, filename, timezone, font, URL, HTML, CSS, image, attachment,
template, page size, or score exists in the request.

Validation is both Bean Validation and service validation:

- enum values only; unknown enum strings are 400 request binding errors. Missing/null required fields and null list items are 422 Bean Validation errors (actual HTTP verified).
- `sections` is non-empty, max 2, and distinct.
- `participantColumns` is distinct and max 8.
- When `PARTICIPANT_TABLE` is selected, columns must be non-empty, include `EMPLOYEE_NAME`, and include
  at least one of `SCORE` or `GRADE`.
- Without `PARTICIPANT_TABLE`, `participantColumns` must be empty.
- `PORTRAIT` allows at most 5 columns; `LANDSCAPE` allows at most 8.
- `title` is trimmed; blank, more than 100 Unicode code points, ISO controls/CR/LF, or unsupported bundled
  font glyphs are `PROGRAM_INVALID` 422 with a stable reason.
- Duplicate option values or inconsistent section/column combinations are `PROGRAM_INVALID` 422.

No server defaults are inferred from a missing field. The frontend supplies explicit defaults:

- locale `ko`
- orientation `LANDSCAPE`
- sections `[SUMMARY, PARTICIPANT_TABLE]`
- columns `[EMPLOYEE_NO, EMPLOYEE_NAME, DEPARTMENT, POSITION, JOB, SCORE, GRADE, FEEDBACK_STATUS]`
- title absent (server-localized default)

The eight-column default is valid only with the `LANDSCAPE` default. If the user changes to `PORTRAIT`,
the UI must require an explicit valid selection of at most five columns; neither FE nor BE silently drops
columns.

### Response

Successful response is raw bytes, not a JSON wrapper:

```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Disposition: attachment; filename="evaluation-results-{programId}.pdf"
Content-Length: <exact bytes>
X-Content-Type-Options: nosniff
Cache-Control: no-store, private
Pragma: no-cache
```

The filename is server-generated ASCII and never contains employee/program/user text. Errors use the
normal JSON error envelope because no binary body has begun before validation and rendering complete.

## 4. Document content

Every page has a localized document title, program name, evaluation year/kind, UTC generation timestamp,
page number, and a confidentiality footer. User `title` replaces only the localized document title; it is
not used in filename, log, or audit details.

`SUMMARY` contains:

- finalized/published result count from `ResultSummaryResponse.finalizedCount`;
- grade distribution as count (no recalculation beyond presenting the returned grade rows);
- applied locale, generation time, and program definition revision/source hash metadata.

`PARTICIPANT_TABLE` contains only the requested allowlisted columns. Mappings are fixed:

- employee number/name/department/position/job come from the frozen participant attributes snapshot;
- score and grade come from `ResultRow`;
- feedback status comes from `ResultRow.feedbackStatus`.

The PDF never includes reviewer identity, reviewer tendency, item answers, opinions, manager comment,
appeal text, reminder text, audit details, KPI evidence JSON, goal text, attachments, or hidden source IDs.
It performs no score or distribution calculation.

Null values render as the ASCII hyphen `-`. Cell text is wrapped to a fixed maximum number of lines and
then truncated with ASCII `...`; a single field cannot create an unbounded page. Every document footer
states in the selected PDF locale that long cell content may be omitted with `...`. Rows are never split
across pages, table headers repeat, and a new page is opened before the next whole row when space is
insufficient.

## 5. Authorization and tenant boundary

1. Controller obtains `actors.requireActor()`; no actor/tenant comes from the body.
2. Export source calls `ProgramAnalyticsService.resultSummary`, preserving its
   HR_ADMIN/SUPER_ADMIN operator check.
3. Every program/participant/result query remains tenant-filtered by `actor.tenantId()`.
4. Missing and foreign-tenant program IDs both return `PROGRAM_NOT_FOUND` 404.
5. Non-operator authenticated actors receive `PROGRAM_FORBIDDEN` 403 before result data is rendered.
6. Non-finalized programs receive `RESULT_NOT_PUBLISHED` 409.
7. A finalized program with zero published result rows also receives `RESULT_NOT_PUBLISHED` 409; it does
   not produce an empty document that implies publication.

The route stays under the already authenticated `/api/v1/evaluation-programs/**` facade, but service-layer
operator enforcement remains mandatory.

## 6. Resource and renderer safety

Before calling `resultSummary`, query the existing tenant-bound total participant count. If total program
participants exceed **200**, fail with `PROGRAM_INVALID` 422 reason `PDF_PARTICIPANT_LIMIT`. This conservative
bound is deliberate: `resultSummary` currently loads all program participants before filtering published
rows. The check must occur before that load.

Additional fixed bounds:

- request JSON remains below the global HTTP limit and contains at most 2 sections/8 columns/100 title chars;
- rendered PDF hard maximum: **8 MiB**, enforced by a bounded output stream while PDFBox writes—not only
  by a post-hoc `byte[].length` check; exceeding it is 422 `PDF_OUTPUT_LIMIT` before response commit;
- rendering deadline: **5 seconds**, checked between page/row operations using monotonic elapsed time;
- no more than 200 participant rows, fixed page dimensions, and a hard **40-page** maximum;
- no remote fetch, URL resolution, HTML/JS engine, image decoder, attachment parser, user font, or OS font;
- renderer uses only the pinned classpath font and built-in PDFBox primitives;
- all `PDDocument`, content stream, and input stream objects use try-with-resources;
- implementation uses an in-memory `ByteArrayOutputStream` only after the 200-row bound. No temp files are
  needed, so there is no cleanup race. The 8 MiB post-render cap protects the returned `byte[]` contract.

The planned pinned renderer is Apache PDFBox **3.0.8**. The bundled NanumGothic Regular font and OFL text
are owned/prepared separately by root. Unsupported glyphs fail closed as typed 422; the renderer must not
silently substitute an OS font or emit tofu boxes.

## 7. Source integrity and audit

Build one canonical source hash from:

- tenant/program ID, program row version and definition revision;
- locale/orientation/normalized sections/columns;
- normalized title (but do not persist/log the title text);
- the exact ordered `ResultSummaryResponse` values used for rendering.

The hash is SHA-256 lowercase hex. The PDF includes source hash, policy `PROGRAM_RESULT_PDF_V1`, and UTC
`generatedAt` so the downloaded document identifies its source without embedding a mutable URL.

On successful render, record a `RESULT_PDF_EXPORTED` program audit event after bytes are complete and before
the controller returns. Audit details contain only policy, sourceHash, locale, orientation, section/column
enum names, participant count, byte count, and generatedAt. They do not contain title, employee IDs/names,
scores, grades, feedback, comments, or PDF bytes. Failed authorization uses existing security/error
observability; it must not create a misleading success audit.

This synchronous export creates no persisted PDF/artifact. Repeating it is a safe read and intentionally
records a new successful download event; there is no duplicate artifact or delivery side effect requiring
an idempotency key. Stored PDF reuse, expiry, and share/download tokens are explicitly outside S4.

## 8. Transaction boundary

Use three components:

1. `ProgramResultPdfSourceService` (`@Transactional(readOnly=true)`): operator/tenant/status check,
   pre-count, `resultSummary` reuse, program metadata, canonical render model/hash.
2. `ProgramResultPdfRenderer` (no transaction): local classpath font load and bounded PDFBox rendering.
3. `ProgramResultPdfAuditService` (`@Transactional`): metadata-only audit event after successful render.

The facade/controller itself is not transactional. PDF rendering does not hold a database connection, and
audit persistence does not wrap rendering. No external network call is introduced.

## 9. Expected implementation files

Product scope (estimated 8–10 files):

- new `ProgramResultPdfDtos.java`
- new `ProgramResultPdfSourceService.java`
- new `ProgramResultPdfRenderer.java`
- new `ProgramResultPdfExportService.java` facade
- new `ProgramResultPdfAuditService.java` if the existing audit service cannot provide an explicit TX boundary
- update `EvaluationProgramController.java`
- update `ProgramTypes.java` (`RESULT_PDF_EXPORTED`)
- tests for source/access/bounds, renderer/page breaks/glyphs, controller mapping/headers
- `build.gradle.kts` PDFBox 3.0.8 dependency
- root-owned classpath font/OFL assets

No entity, repository schema, Flyway migration, HCM/core, existing XLSX/SVG, result calculation, result
publication, or employee visibility contract changes are expected.

## 10. Verification gate

- Unit/access: operator 200 source, employee 403, missing/cross-tenant 404, non-finalized/zero-published 409,
  pre-count 201 rejected before analytics load, invalid/duplicate options 422.
- Renderer: ko/en, portrait/landscape, all column combinations, null `-`, long Korean/Latin values with
  ASCII `...` and localized omission note, exact row page break, repeated headers, page numbering,
  unsupported glyph typed failure, `%PDF-` signature, writing-time 8 MiB limit, and 40-page limit.
- Controller: exact approved path, content type/disposition/content length/no-store/nosniff, JSON error before
  binary commit.
- Regression: existing results XLSX and pivot XLSX/SVG tests unchanged; full backend tests and bootJar.
- Actual runtime: tenant A operator succeeds; employee denied; tenant B operator proves own endpoint then
  tenant A program returns 404; 200-row succeeds/201 rejects; finalized/published guards; all options.
- Visual: render every generated PDF page to PNG and inspect Korean glyphs, clipping, row/page boundaries,
  headers/footer/page numbers, portrait and landscape.

## 11. Phase 3a gate recommendation

Proceed with the HR-only synchronous contract above. Do not add an employee PDF or stored/shared document in
this slice. Root must confirm the pinned font license/hash and Luna must confirm the PDFBox/PII/OOM boundary
before Phase 3b product edits.

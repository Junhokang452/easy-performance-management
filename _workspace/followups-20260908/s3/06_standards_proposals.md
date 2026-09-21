# S3 standards/conformance proposals (local only)

The orchestrator and standards-evolution workflow kept source-of-truth changes out of scope. No shared standard, HCM, core library, AGENTS file, commit, push or deployment was changed for S3.

## Candidates for a later standards update

1. Separate source freshness from delivery deduplication. A notification's registration/read status must not enter a preview source hash: an uncertain retry or a concurrent operator must reuse the existing logical-work/day notification instead of becoming artificially stale.
2. Treat work ownership as a domain decision. Goal authoring belongs to the employee; requested goal agreement belongs to the explicit agreement assignee; delivered feedback belongs to the employee; appealed feedback belongs to FINAL_FEEDBACK. Do not infer an operator recipient for a system-only step.
3. Preserve both guarantees: request-idempotency exact response replay and logical-work/day uniqueness. A new request key must not bypass the daily limit. User approved UTC-day once per task/owner, with explicit manual registration on later days; no automatic repeat.
4. Actual Spring request mapping is a runtime boundary. A class path ending in an action resource plus a method `:action` introduces an extra slash. Fix action paths as complete method suffixes and verify actual HandlerMapping/HTTP/OpenAPI, not only annotations read separately.
5. Keep additive history and generic delivery separate. An IN_APP-only feature must not call a generic dispatcher that also flushes preexisting EMAIL work.

## Proposed local conformance entry

- Product: easy-performance-management / evaluation programs.
- Slice: responsible-person incomplete-work reminders (S3).
- Evidence: `01_backend_contract.md`, `04_boundary_qa.md`, `05_frontend_verification.md`, `07_runtime_findings.md`, final runtime/browser outputs when completed.
- Status: implementation exists; final status must follow the actual HTTP/browser evidence, not the unit/build result alone.
- External integrations and operational activation: not requested, not executed.

This file is a proposal, not an applied easy-standards change or a submitted PR.

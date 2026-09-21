# Program audit backend/runtime completion

Date: 2026-09-08 (Asia/Seoul)

## Build evidence

- Focused `ProgramAuditQueryServiceTest`: 7/7 passed.
- Full backend test: 272/272 passed; failures/errors/skips 0.
- `bootJar`: passed.
- Verified JAR SHA-256: `e9e676457176a165841ff71371cd68e4195f535536e488bdb2700c8e6dd7bfb3`.
- The focused, full-test, and bootJar runs used an isolated Windows copy plus portable Temurin JDK 21 because WSL execution was unavailable. Product Java source was not changed by this audit.

## Live HTTP evidence

- Isolated PostgreSQL: `127.0.0.1:55489`, dedicated data directory under the validation temp root.
- Backend: `http://127.0.0.1:8089`, `local-demo`, health `UP`.
- Browser fixture program: `01a07f22-d19e-7ce6-af19-a8a54084e76d` (`Audit browser fixture 20260908124936`).
- Participant: `98a8a3b9-9b13-4c81-9aba-22b5384e11df`.
- Audit fixture: at least 31 rows; includes a null participant and a null actor.
- HTTP acceptance: 17/17 passed.
  - exact six-field row shape
  - stable page metadata and adjacent-page ordering
  - event-type-only, participant-only, and combined filters
  - employee 403
  - active Tenant B operator session 200 and Tenant B program collection 200
  - nonexistent signed Tenant B actor 401
  - Tenant B access to Tenant A history 404
  - page size 101 rejected with the intentional domain error `E9804256` / HTTP 422
  - `detailsJson` and `tenantId` absent recursively
- Null actor and null participant coverage: verified.

## Evidence files

- `windows-audit-held-api.json` — machine-readable 17-check result.
- `windows-audit-held-openapi.json` — live `/v3/api-docs`, 188,246 bytes, SHA-256 `7b8679ee0af1581482e7c62a03aa2c43b1aa7e8b3a69ae51c317fc9aa8aef1cf`.
- `windows-audit-held-runtime.json` — exact PID, port, program, participant, and data-directory metadata.
- `windows-audit-held-*.log` — lifecycle, PostgreSQL, backend, and verifier logs.

## Runtime ownership and cleanup

Browser QA completed 14/14 and the managed runtime was stopped at `2026-09-08T13:19:26.2078069+09:00`. Identity checks tied Java PID 14836 to the portable JDK under the validation root and PostgreSQL PID 16328 to the dedicated data directory before shutdown. Java stopped, `pg_ctl` fast-stop exited 0, and ports 8089/55489 were released. Existing listeners on 5432 (PID 8920) and 5433 (PID 8664) were identical before and after. The temp data directory and evidence were preserved.

## Remaining risk

- The live runtime uses a synthetic local-only fixture and an ephemeral JWT secret; it is not production tenancy/control-plane validation.
- Spring's raw `Page` JSON representation remains a framework warning risk even though the currently asserted response fields are verified.

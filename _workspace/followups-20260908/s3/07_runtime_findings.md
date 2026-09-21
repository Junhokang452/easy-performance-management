# S3 actual runtime findings

## R01 — class/method request mapping inserted an extra slash

- Runtime JAR (first attempt): `F7B6E8DCAB0A86CB2D5806C88C32E8D543798570625F9F483BF49DCC3C6C2079`.
- Boot succeeded (33.646s), database migration and schema validation succeeded.
- Expected POST `/api/v1/evaluation-programs/{programId}/incomplete-reminders:preview` returned HTTP404.
- Generated actual OpenAPI exposed `/incomplete-reminders/:preview` and `/:queue` because class mapping ended with `incomplete-reminders` while method mappings started at `:preview`/`:queue`.
- Fix requested: class base ends at `{programId}`, methods own full `/incomplete-reminders:preview`, `/incomplete-reminders:queue`, and GET `/incomplete-reminders` suffixes. FE/approved API contract unchanged.
- Require mapping-specific regression test, backend full test + rebuilt JAR, then actual API retry.
- First failure evidence preserved in `runtime-attempt-01-path-mapping.json`. Dedicated runtime stopped before rebuild; protected PostgreSQL listeners unchanged.

## R01 resolution — PASS

- Final frozen JAR: `C47A9312BE259DC211A450A8A3F761053FE3CA6A08798485E209686DEB7D06DF`.
- Backend full tests 322/322, including exact controller mapping regression; source/temp 84/84 parity.
- Reboot succeeded (58.623s). Actual OpenAPI exposes exactly the three approved S3 paths; no slash-colon path remains.
- Actual HTTP retry completed **39/39 PASS** (`responsible-reminders-local.json`). No additional product defects found.
- Browser completed **16/16 PASS** (`browser/result.json`), including real POST registration, lost-response exact-key retry, source-change 409 and recipient inbox read.
- S1 actual API regression **22/22** and S2 actual API regression **27/27** passed on this final JAR.
- Flyway `20260908.001`, `.002`, `.003` all `success=true`.
- Local database S3 rows after acceptance: IN_APP READ=2, SENT=5; duplicate `(tenant_id, reminder_dedupe_key)` groups=0. No S3 EMAIL row.
- Dedicated API8089/PG55489 stopped after tests. Existing PG5432/PID8920 and PG5433/PID8664 preserved. `runtime-final.json` records the final JAR hash and stopped state.

The old recipient-read endpoint intentionally returns 403 for another recipient; the HTTP harness follows that existing contract without changing the product authorization behavior.

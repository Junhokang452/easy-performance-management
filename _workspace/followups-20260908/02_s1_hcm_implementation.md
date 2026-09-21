# S1 HCM implementation and focused verification

Root owns 6 new files: `service/EasyPerformanceSnapshotService`, `sync/EasyPerformanceCoreMasterPushService`, `sync/EasyPerformanceCoreMasterPushController` and their three test classes in easy-hcm backend. One exact-class pointcut extension in `config/AuditLoggingAspect` also includes the new administrative push endpoint in existing audit logging; no other sync endpoint behavior changes. Other pre-existing dirty HCM changes remain untouched.

Implemented default-OFF manual-only sender, operator method security, fixed configured HTTPS origin, timeouts/no redirects, UTF-8 byte body and HMAC, signed tenant UUID, route guard before SQL, bounded native projection including deleted employees/assignments, epoch-microsecond version preservation, repeatable-read snapshot and external HTTP after the read transaction. No scheduler, live configuration, external send or deployment was performed.

## Actual gates

- Current HCM main `compileJava`: PASS in Windows isolated source copy.
- Focused `*EasyPerformance*` tests: **15/15, failures 0, errors 0** (snapshot 6, sender 7, authorization 2).
- Includes actual method-security proxy rejection for employee, allow for ADMIN/HR, UTF-8 Korean body/signature, signed tenant header/body, inactive/tombstone source, microsecond ordering, bound overflow, absent route/mismatched tenant, caller transaction rejection, disabled configuration and upstream 307 rejection.
- Full HCM test compilation: **BLOCKED by an existing unrelated test import** in `payrollpreparation/PayrollPreparationControllerTest.java:17` (`org.springframework.security.core.annotation.AuthenticationPrincipalArgumentResolver` does not exist). Original test was not changed. This is not a full-suite PASS.
- Focused compile workaround is explicit/reproducible: `hcm-focused-tests.gradle` limits only test compilation, not main compilation, to the three new test classes. No failing production tests were silently excluded from a claimed full pass.

Final Windows validation path: `C:/Users/SAMSUNG/AppData/Local/Temp/easy-hcm-s1-qa-zNJAxy`, a new S1-owned directory with current backend/src and shared core/src synced; existing dependency cache reused. The earlier canonical temp output was locked/disappeared during reuse, so its workspace is no longer used. No other process was killed and no existing data was deleted. Command: `gradlew.bat test --tests '*EasyPerformance*' --init-script ../hcm-focused-tests.gradle --no-daemon --console=plain`. Final run: BUILD SUCCESSFUL, 48 seconds, all 15 tests pass after UTF-8 bytes, ISO LocalDate serialization and audit pointcut changes. XML evidence copied under `hcm-test-results/`; all seven owned source files match the verified copy byte-for-byte.

PA integrated local API/browser verification is now complete; final evidence is in `99_final_report.md` (API 22/22, typed tenant collision 3/3, browser 13/13). No live HCM database integration was run; the HCM snapshot projection test uses H2 with synthetic data, while HTTP uses MockRestServiceServer. This is not a live end-to-end HCM outbound send.

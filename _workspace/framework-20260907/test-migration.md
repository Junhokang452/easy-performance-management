# Boot 4 test migration — easy-performance-management

Date: 2026-09-07  
Scope owner: `backend/src/test/**` and this note only

## Target contract

- Spring Boot `4.1.1`
- Java `21`
- Gradle `8.14.5`
- springdoc `3.1.1`
- composite `easy-platform-core:1.0.0-SNAPSHOT`
- Transitional JSON boundary: Jackson 2 public types and MVC converter (`com.fasterxml.jackson.*`)

The target comes from
`/home/samsung/code/_workspace/suite-security-baseline-20260907/02_target_version_decision.md`
and `/home/samsung/code/easy-platform/easy-platform-core/MIGRATION_BOOT_4.md`.

## Test source migration

Boot 4 moved MVC test annotations. The real security-chain regression slice now imports:

```java
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
```

The slice already used Spring Framework's supported replacement for `@MockBean`:

```java
import org.springframework.test.context.bean.override.mockito.MockitoBean;
```

No disabled tests, assumptions, filters, or exclusions were added. Existing authentication,
browser-session cookie/CSRF, JWT routing, roles, workflow, tenancy, and domain tests remain in the
normal test task.

## Focused Boot 4 test dependencies

The build owner applied the agreed Boot-managed set:

```kotlin
testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
testImplementation("org.springframework.boot:spring-boot-starter-security-test")
testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
testRuntimeOnly("org.junit.platform:junit-platform-launcher")
```

This replaces the broad legacy test starter while retaining MockMvc, Spring Security test support,
JPA slice support, JUnit, Mockito, and AssertJ for the existing suite.

## Added MVC wire characterization

`Jackson2MvcCompatibilityTest` is an actual `@WebMvcTest`/MockMvc slice. It adds four checks:

1. The runtime reports Boot `4.1.1`, and the first MVC JSON writer for the contract is
   `MappingJackson2HttpMessageConverter` backed by the Boot-managed
   `com.fasterxml.jackson.databind.ObjectMapper` bean.
2. A real MVC response preserves an explicit `null`, ISO-8601 `Instant`, ISO `LocalDate`, an exact
   high-precision `BigDecimal`, the enum name, and UUID text.
3. A real MVC request body round-trips the same contract through Jackson 2 without precision or
   enum/date drift.
4. A malformed enum still flows through `ProductRequestExceptionHandler` and returns the existing
   `ApiError` envelope: `code`, `message`, `messageKey`, `details`, nullable `traceId`, `path`, and ISO
   `timestamp`.

The slice sets `spring.http.converters.preferred-json-mapper=jackson2`, matching the product runtime
configuration. Its security filters are disabled because converter/error characterization is its
single purpose; the separate `SecurityConfigInternalAdminTest` continues to exercise the real
security filter chain for unauthenticated, ordinary-role, `SUPER_ADMIN`, and internal S2S routes.

## Integrated validation

The root coordinator ran the complete product test task once with constrained workers and heap to
avoid concurrent daemon/cache and shared-worktree conflicts.

| Result | Value |
|---|---:|
| Test classes | 30 |
| Tests | 218 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| New Jackson 2 MVC contract tests | 4/4 passed |

This is the preserved 214-test baseline plus four new MVC contract tests. The generated test output
also records Spring Boot `4.1.1`, Spring Framework `7.0.9`, and Java `21.0.12` for the real MVC slice.
No exclusion was used.

## 최종 보안 패치 검증

Tomcat 11.0.25 실제 ServerInfo 회귀 검증 1개를 추가한 최종 결과는 제품 219/219, 실패·오류·건너뜀 0입니다. `test-summary.json` 및 `product-test.log` 참조.

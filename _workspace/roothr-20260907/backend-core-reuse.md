# Backend core reuse audit

## Compared baselines

| Target | Revision | Role |
|---|---:|---|
| Product-pinned `lib/easy-platform/easy-platform-core` | `172e5eda09be07cde41dbae8ce6cdc007274723c` | What the product compiles and runs against through the Gradle composite build |
| Canonical `/home/samsung/code/easy-platform/easy-platform-core` | `9e5afefd0ab4636e1bb6c4d603e15039faa79e96` | Current shared-core source and future pin candidate |

The product uses `implementation("com.easyware.platform:easy-platform-core:0.1.0-SNAPSHOT")`, substituted by `includeBuild("../lib/easy-platform/easy-platform-core")`. The canonical checkout is 35+ commits ahead of the product pin. A blind pin bump is not a low-risk change because that range also contains unrelated attendance/payroll code and resource migrations.

## Reuse already in place

| Concern | Shared classes used by performance | Product code that remains intentionally thin |
|---|---|---|
| Entity base and audit fields | `BaseAuditEntity`, `TenantAwareAuditEntity`, `AuditEvent`, `AuditEventRepository`, audit auto-config | 23 product entities extend `TenantAwareAuditEntity`; product owns entity IDs and domain columns. `PerformanceManagementApplication` limits product repository scan while shared audit auto-config owns its repository. |
| Errors | `ApiException`, `ApiError`, `ErrorCode`, `ErrorCodeContract`, `ErrorMessageResolver`, `TraceIdFilter`, `GlobalExceptionHandler` | `PerformanceErrorCode` contains product/domain codes only. Services throw the shared `ApiException`. |
| JWT primitives | `JwtClaims`, `JwtTokenIssuer`, `JwtTokenParser`, `ParsedToken`, `JwtRefreshService` | `JwtService` adds product TTL and roles. `AuthService` adds the performance account lookup, bcrypt check, role reload, and response shape. |
| Tenant context and routing | `tenantctx.TenantContext`, `TenantContextFilter`, `TenantRoutingContext`, `TenantDataSourceRegistry`, `TenantContextBridge` | `PerformanceTenantContextBridge` is the required product SPI adapter. `SecurityConfig` only composes filters and route authorization. |
| Control plane and provisioning | `PlatformTenantStore`, `TenantProductDbStore`, `NeonProvisioningService`, `TenantBootstrap`, `TenantSchemaMigrator`, `TenantAdminSeedSupport`, `AbstractTenantSelfBootstrapScheduler`, `PlatformProductConfig`, `TenantSeeder`, `TenantOwnerProvisioner` | `PerformancePlatformProductConfig`, bootstrap config, seeder, owner provisioner, initial-admin seeder, and scheduler supply performance app code, migration locations, and domain seed behavior. These are proper SPI implementations rather than copied platform engines. |
| Web security paths | `PlatformSecurityMatchers` | Product `SecurityConfig` supplies the evaluation-specific object and lifecycle guards. |
| S2S cryptography | `HmacService` | Both HCM receive and Talent push use shared HMAC-SHA256 compute/verify and its minimum-secret fail-fast check. |
| UUIDv7 | `com.easyware.platform.UuidV7` | All product entities and workflow factories now call the shared generator directly. |

The pinned and canonical copies have identical hashes for `JwtTokenIssuer`, `JwtTokenParser`, `HmacService`, `TenantAwareAuditEntity`, and `ApiException`, so the product is already using stable APIs for those seams.

## Duplicate or compatibility code that remains

| Product class | Overlap | Assessment |
|---|---|---|
| `ProductRequestExceptionHandler` | Canonical `GlobalExceptionHandler` now maps malformed JSON/enums and path/query type mismatches to 400. The pinned `172e5ed` handler predates canonical commits `6469250` and `f8ac703`. | Required compatibility adapter at the current pin. Remove after a verified core bump; keeping it now prevents the observed 500 response. |
| `JwtAuthFilter` | Pinned/canonical `JwtAuthenticationFilterBase` handles parse, security context, tenant routing, default routing, and cleanup. | Do not cut over yet. The base emits `ROLE_` authorities while this product deliberately uses prefix-free authorities, and it has no hook for the product's cookie mutation Fetch-Metadata guard or its explicit 401 when the token tenant cannot be routed. |
| `TenantSupport` | Wraps shared `tenantctx.TenantContext` but returns the fixed tenant UUID when context is absent. | Technical debt and a fail-open compatibility path. Replace with `TenantContext.requireTenantId()` only after service tests explicitly establish tenant context and all non-request jobs use a scoped context. |
| `JpaAuditConfig` | Shared `AuditAutoConfiguration` supports `AuditorAwareSpi`; product declares its own `@EnableJpaAuditing`, time provider, and `AuditorAware<UUID>`. | The hard-coded system actor loses the authenticated actor. A later product adapter should implement `AuditorAwareSpi` from `TenantContext.get().getUserId()` with a system actor only for explicitly scoped jobs. Avoid enabling both auditing configurations simultaneously. |
| `SyncReceiveController.SyncChannel` | Reimplements configured-channel gating and constant-time Bearer comparison; HMAC itself is shared. | Shared core has no receiver-side Bearer+HMAC validator, so this cannot yet be safely removed. This repeated receiver policy is a core extraction candidate. |
| `EasyTalentResultPushService` HTTP setup | Creates `RestClient`, Bearer headers, and HMAC header manually. Shared `ExternalServiceClient` and `BearerHmacInterceptor` cover most of this. | Current shared client treats Bearer-only configuration as valid and returns `null` for disabled calls, while this contract requires HMAC and a typed `PushResult`. Enhance the shared client contract before cutover. |
| `PerformanceTenantContextBridge` and provisioning/seeding classes | Similar small classes exist in sister products. | These carry product app code, DB name, tenant schema location, and seed/provision behavior. They implement shared SPIs and should remain in the product. |
| `PerformanceErrorCode` | Every product has its own enum. | Correct boundary: the shared core owns generic transport/platform codes, while E98 domain codes stay here. |

## Scoring and evaluation logic

No pinned or canonical shared-core class provides performance rating bands, weighted KPI score calculation, calibration distribution, goal agreement, review transitions, or report publication.

The following code must remain in the performance product:

- `KpiService.computeAchievementRate` and effective KPI weight/target rules.
- `ReviewService.computeKpiScore`, grade-band mapping, score snapshots, and review transitions.
- `DistributionMath` largest-remainder allocation and the S/A/B/C/D ordering.
- `CalibrationService` session, adjustment, forced-distribution, and confirmation rules.
- The evaluation workspace facade and its participant, goal, intermediate-review, feedback, report, and close gates.

These rules are performance-domain policy. Extracting them into platform core would couple unrelated products to this product's rating model.

## Canonical-core delta relevant to performance

The current canonical core adds these useful capabilities after the pinned revision:

- Malformed request-body and type-mismatch handling in `GlobalExceptionHandler`.
- `TenantAdminGuard` and `MembershipRoleResolver` SPI.
- `SecurityPrincipalTenantContextResolver` and `TenantPrincipal`.

The canonical JWT issuer/parser, HMAC service, audit base, and API exception remain byte-identical to the pin. The new security principal resolver is not a direct replacement because performance already places the shared `TenantContext` in its JWT filter and uses the thread-local passthrough resolver.

## Actual replacement completed

- Replaced every product import of `com.easyperformance.common.UuidV7` with stable shared `com.easyware.platform.UuidV7`. This changes 17 baseline-tracked files (15 main classes and 2 tests); the 9 new workflow main classes and 7 new workflow tests introduced in this work already use the shared generator as well.
- Deleted the product UUID wrapper.
- Removed the product's direct `uuid-creator` dependency; the shared core owns that implementation dependency.
- Kept the UUID regression test against the shared API and expanded it to verify version 7, RFC variant 2, uniqueness, and the encoded current epoch-millisecond prefix.

This is behavior-preserving: both implementations call `UuidCreator.getTimeOrderedEpoch()`, and the shared implementation is identical at the pinned and canonical revisions.

The focused shared-UUID regression test passes. The temporary concurrent Web MVC test-fixture issue was resolved; the final whole-product suite passed **214/214**, without failures, errors, skips or exclusions. Root independently counted all 29 XML suites.

The final shared pin is `7c814bc`, a selective FE-only asset backport on `172e5ed`; canonical asset registration is `90439f4` on `9e5afef`. The backend APIs compared in this audit are unchanged by that asset registration. No unrelated canonical backend changes were pulled into the product.

## Recommended sequence

1. Verify a focused core pin bump in an isolated product branch with full tests, fresh PostgreSQL migration, boot, login/refresh/logout, and the complete evaluation API verifier. Then remove `ProductRequestExceptionHandler` because canonical `GlobalExceptionHandler` owns those mappings.
2. Replace `JpaAuditConfig` with a product `AuditorAwareSpi` that reads the authenticated shared tenant context, while preserving an explicit system actor for seed/scheduler jobs.
3. Remove `TenantSupport` fallback and migrate tests/background jobs to scoped tenant context. This is a security correction and should be its own slice.
4. Add shared `JwtAuthenticationFilterBase` hooks for authority prefix policy, browser-cookie mutation validation, and unroutable-tenant response behavior. Cut over the product filter only after those hooks exist.
5. Design a receiver-side shared Bearer+HMAC channel validator and strengthen `ExternalServiceClient` with required-HMAC configuration and typed disabled/error outcomes before changing either S2S boundary.

No shared-core or other repository was changed during this audit.

# easy-performance-management Neon Model B topology audit

Date: 2026-09-07  
Scope: code/configuration audit against `easy-standards/00-principles/13-tenancy-provisioning.md` and the pinned `easy-platform-core`, followed by local fail-closed readiness corrections. No Neon API, control-plane, database, or secret-value access was performed.

## Verdict

The intended production contract is correctly represented in code:

```text
customer tenant UUID
  -> exactly one Neon project for that customer
     -> one PERFORMANCE product database named `performance`
        -> runtime role `performance_app`
        -> the complete product Flyway history from `classpath:db/migration`
```

This matches ADR-013: customer = project, licensed product = database, shared tenant UUID, and integration across databases only through S2S/read models (`13-tenancy-provisioning.md:16-20,41-59,65-76,85-106`; `11-suite-architecture.md:39-53,69-72`). The current `local-demo` profile is deliberately a single local PostgreSQL database and is not evidence of a Neon Model B rollout.

The implementation is **structurally ready for a new PROVISIONING tenant when the shared-owner shell flow and performance self-migration gates are enabled together**. This review also added a fail-closed, customer-scoped rollout path for existing ACTIVE PERFORMANCE databases: the current schema version is `20260907.003`, and an ACTIVE database is eligible only when both the drift toggle and its tenant UUID canary entry are present. All gates remain off by default and no external resources were touched.

## Contract and routing evidence

| Concern | Evidence | Assessment |
|---|---|---|
| Product identity | `backend/src/main/java/com/easyperformance/platform/PerformancePlatformProductConfig.java:27-44` returns app code `PERFORMANCE`, database `performance`, role `performance_app`, and location `classpath:db/migration`. | Correct product-per-DB descriptor. |
| Shared library pin | Parent gitlink `lib/easy-platform` is clean at `172e5eda09be07cde41dbae8ce6cdc007274723c`; `backend/settings.gradle.kts:3-13` consumes `lib/easy-platform/easy-platform-core` as a composite build. | Audit is against the exact pinned implementation. |
| Project reuse and product DB creation | Pinned `NeonProvisioningService.java:444-475` reuses `platform_tenant.neon_project_id`; `:486-530` creates `product.databaseName()` in that project and records the product row. | Correct customer-project/product-database shape. |
| Entitlement | `SystemAdminTenantsController.java:77-89` creates the `PERFORMANCE` subscription before provisioning; pinned `NeonProvisioningService.java:537-565` intersects active subscriptions with registered products. | Correct license-driven intent. Shared-owner registration remains an external prerequisite. |
| Direct migration connection | Pinned `TenantSchemaMigrator.java:55-94` loads the product shell row, decrypts its migration URL, builds a dedicated owner datasource, runs Flyway at the supplied product location, and returns the actual version. | Correct: migrations run against the selected tenant product DB, not a pooled runtime route. |
| Runtime routing | `PerformanceTenantContextBridge.java:26-44` supplies the product tenant context. Pinned `NeonTenantDataSourceConfiguration.java:42-60` installs `TenantRoutingDataSource` and changes boot Flyway to `classpath:db/control`. | With Model B enabled, normal product access routes by tenant; startup cannot apply product DDL to the control DB. |
| Consumer control-plane safety | `application.yml:229-242` and `application-prod.yml:69-84` default `control-plane-owner=false`; pinned `NeonTenantDataSourceConfiguration.java:63-77` skips boot Flyway for a shared control-plane consumer. | Correct ownership boundary. |
| Least-privilege runtime role | Pinned `NeonProvisioningService.java:581-619` creates/normalizes a login role with `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOBYPASSRLS` and product-schema grants. | Matches ADR-013 §6. |
| Bootstrap SPI | `PerformanceTenantOwnerProvisioner.java:26-53` creates the tenant HR admin in the routed product DB. `PerformanceTenantSeeder.java:24-34` is an intentional no-op because there is no fixed product catalog or tenant self-row. | Required product hooks exist. |

## Do the new workflow migrations go to the tenant database?

Yes, conditionally:

- The new files are `backend/src/main/resources/db/migration/V20260907_001__audit_runtime.sql` and `V20260907_002__evaluation_workspace.sql`.
- Both the product descriptor (`PerformancePlatformProductConfig.java:42-44`) and self-bootstrap scheduler (`PerformanceTenantSelfBootstrapScheduler.java:79-88`) select the whole `classpath:db/migration` history.
- In the canonical shared-owner path, the platform owner first creates a `performance` shell row/DB. The performance service then uses pinned `TenantSchemaMigrator` with that row's encrypted direct migration URL. A new PROVISIONING database therefore receives all migrations through `V20260907_002`.
- In Model B mode, boot-time Flyway is restricted to `classpath:db/control`, and this consumer skips even that control migration. Product workflow DDL therefore does not fall into the control database.
- With `application-local-demo.yml:37-48`, Model B and bootstrap are off. The same migrations apply to the local `performance_demo` database through the ordinary `spring.flyway.locations` setting. That validates product behavior only.

The separate `backend/src/main/resources/db/tenant/V20260608_001__stage2_per_tenant.sql` is **not** included by either active product migration path. It only creates an idempotent stage marker, so its omission does not omit domain tables, but its comments claiming that `classpath:db/tenant` is the active fan-out location are stale and should be corrected or the marker moved into the selected history.

## Actionable gaps

### Resolved — Existing ACTIVE tenant database migration path

The original audit found that `PerformanceTenantSelfBootstrapScheduler` declared `20260612.001`, while the latest migration was `20260907.003`, and inherited the library's permanent ACTIVE-drift skip. It now declares `20260907.003` and supplies two fail-closed hooks (`PerformanceTenantSelfBootstrapScheduler.java:48-55,95-115`):

- `activeDriftEnabled()` is true only when the explicit toggle is true **and** the canary set is non-empty.
- `rolloutAllows(...)` always permits a new PROVISIONING row but permits an ACTIVE row only when its customer tenant UUID is in the canary set.
- Invalid canary UUID configuration fails startup instead of widening the rollout.

Configuration contract (`application.yml:153-168`):

- `EASYPLATFORM_PERFORMANCE_STAGE2_SELF_MIGRATE_ENABLED=true` enables product-owned migration.
- `EASYPLATFORM_PERFORMANCE_STAGE2_ACTIVE_DRIFT_ENABLED=true` allows ACTIVE drift evaluation.
- `EASYPLATFORM_PERFORMANCE_STAGE2_ACTIVE_DRIFT_CANARY_TENANT_IDS=<uuid[,uuid...]>` restricts ACTIVE migration to named customers.

The remaining step is operational: roll out one authorized synthetic/canary tenant, verify `flyway_schema_history` and `tenant_product_db.schema_version`, then expand the explicit UUID list in controlled batches. That step was intentionally not performed in this local-only session.

### P1 — Canonical shared-owner provisioning requires self-migrate, but it defaults off

`application.yml:153-164` defaults stage 2, self-migrate, and tenant bootstrap off. `application-prod.yml:42-82` also leaves Model B off unless the deployment supplies explicit values. That is safe for an unlaunched product, but it means the production topology is a coded contract rather than an active default.

More specifically, the shared owner should run shell-only provisioning and performance should own its schema migration. With self-migrate false, `AbstractTenantSelfBootstrapScheduler.java:218-250` only scans ACTIVE rows. A newly created shell is PROVISIONING, so the legacy path cannot activate it. `PerformanceTenantBootstrapConfig.java:132-183` also sees no `NeonProvisioningService` when performance correctly runs with `control-plane-owner=false` and degrades to no-op provisioning/migration.

Precise deployment contract:

- Shared control-plane owner: register the `PERFORMANCE` descriptor, seed the PERFORMANCE entitlement, and use shell-only provisioning.
- Performance service: set Model B on, stage 2 on, and `EASYPLATFORM_PERFORMANCE_STAGE2_SELF_MIGRATE_ENABLED=true`; supply control-plane/cipher credentials and keep `APP_NEON_CONTROL_PLANE_OWNER=false`.
- Do not enable only stage 2 with self-migrate false in the consumer deployment.

### Resolved — Tenant create preflights owner capability before writes

The original audit found that `SystemAdminTenantsController` created a platform tenant and subscription before resolving `NeonProvisioningService`. Under the intended consumer setting (`control-plane-owner=false`), that bean is absent because pinned `NeonProvisioningService.java:52-54` is owner-only.

`SystemAdminTenantsController.java:77-94` now resolves the store, owner provisioner, subscription store, and product descriptor before `store.create(...)`. Missing owner capability returns 503 without creating a tenant or subscription. The longer-term topology remains to put customer lifecycle mutation in the shared platform owner.

### Resolved — Legacy manual bootstrap has a dedicated SUPER_ADMIN boundary

The audit found that `PerformanceTenantBootstrapController.java:21-74` accepts a tenant UUID and invokes bootstrap without the Bearer/HMAC contract used by S2S receivers, while the generic internal matcher permits `/api/internal/**`.

`SecurityConfig.java:82-90` now requires `SUPER_ADMIN` for `/api/internal/admin/**` before evaluating the generic internal S2S permit-all matcher. The focused security test proves unauthenticated 401, ordinary authenticated role 403, SUPER_ADMIN 200 with handler invocation, and unchanged access to ordinary `/api/internal/sync/**` for controller-level Bearer/HMAC authentication.

### P2 — No automated proof of physical separation or control DB cleanliness

Current tests cover the auth routing failure boundary, but no performance test provisions or simulates two product rows and proves that tenant A writes cannot appear in tenant B, nor that product tables remain absent from the control DB. There is also no migration test asserting `V20260907_002` is the recorded current version.

Precise fix: add a local multi-database integration test with a synthetic control DB plus two tenant product DBs. Assert route A/B isolation, wrong/missing route fail-closed, migrations through `20260907.003` on both tenant DBs, no evaluation tables in control, and matching recorded schema versions.

## Readiness checklist

| ADR-013 requirement | Status |
|---|---|
| Customer = one Neon project | Implemented in pinned provisioning service. |
| PERFORMANCE = one `performance` DB inside that project | Implemented by product descriptor. |
| Shared cross-product tenant UUID | Implemented by control-plane row/product row contract. |
| Entitlement creates only purchased DBs | Implemented in code; owner registry/operational registration must be verified at rollout. |
| Product owns schema migrations | Implemented through direct self-migration location. |
| New tenant gets workflow schema | Ready only with shared-owner shell + performance self-migrate enabled. |
| Existing ACTIVE tenants receive workflow schema | Guarded path implemented: current version + explicit opt-in + customer UUID canary. Operational rollout remains pending. |
| Control DB remains free of product tables | Correct by configuration design; needs integration proof. |
| Cross-tenant routing fails closed | Auth boundary now has a routing-failure test; full two-database proof remains. |
| Local demo proves Neon topology | No; intentionally single local DB. |

No production/customer Neon resources were created or changed during this audit.

Local verification after the readiness corrections: the whole backend suite passed 214/214 with no failures, errors, skips, or test exclusions. `git diff --check` also passed.

# performance 단계 2 진입 실 cutover — B2B-Enterprise per-tenant (2026-06-08)

> **상위**: `_workspace/PERFORMANCE_MANAGEMENT_STAGE1_CUTOVER_2026-06-08.md` (#`b83acac`) — 단계 1 BE-CC-1 cutover
> **상위**: `_workspace/PERFORMANCE_TOPOLOGY_CORRECTION_2026-06-08.md` (#`559cad2`) — 듀얼 모드 5호 박제 폐기 → B2B-Enterprise + SMB 정정
> **본 슬라이스**: **G65 D=A 단계 2 B2B-Enterprise per-tenant 진입** — `PerformanceTenantBootstrapConfig` + lib BE 14 TenantBootstrap 3 SPI seam thin adapter
> **상태**: 실 cutover 완료 (코드 변경 = 6 파일)
> **LIVE 영향**: 0 (performance LIVE 미운영 + dev profile 한정 활성 + prod profile 안전 OFF 가시화 + 옵션 B 폴백)
> **작성**: backend-spring (Task #100)

---

## 1. 단계 2 진입 컨텍스트

### 1.1 사용자 결정 게이트 통과 트레일

| 게이트 | 결정 | 근거 |
|---|---|---|
| **G65** | ✅ D=A 단계 2 진입 즉시 cutover | 그린필드 LIVE 미운영 + 단계 1 풀 통과 (#`b83acac`) + 사용자 자동승인 모드 |
| ~~G67~~ | ⏸ 별도 슬라이스 (단계 5 SMB Shared 옵션) | 자매품 8/9 풀 안정 + ware/hcm SMB 진입 누적 안정성 후 |

### 1.2 토폴로지 정합 (2026-06-08 정정 후)

ADR-031 `_workspace/ADR_031_SIBLING_9_TOPOLOGY_MATRIX_2026-06-07.md` 정합:
- ~~듀얼 모드 5호~~ → **B2B-Enterprise per-tenant 본질 + SMB Shared 옵션** (ware/hcm/recruit 패턴)
- 단계 격상 5단계 → **4단계 + SMB 옵션 단계 5**
- 본 단계 2 = **B2B-Enterprise per-tenant 본질 진입** (도메인 본질 = 기업 성과 평가, B2C 부재)

### 1.3 본 cutover 범위

| 파일 | 신규/수정 | 변경 요지 |
|---|---|---|
| `backend/src/main/java/com/easyperformance/platform/PerformanceTenantBootstrapConfig.java` | **신규** | lib BE 14 TenantBootstrap 3 SPI seam performance thin adapter — PlatformTenantLookup + TenantProvisioner + TenantMigrator 빈 등록 (jobeval + mra 패턴 결합) |
| `backend/src/main/resources/application.yml` | 수정 | `easyplatform.performance.stage2.enabled` + `easyplatform.tenantbootstrap.enabled` + `neon.{api,project,region}` 게이트 가시화 (기본값 OFF) |
| `backend/src/main/resources/application-dev.yml` | 수정 | dev profile `stage2.enabled=true` + `tenantbootstrap.enabled=true` + `easyware.neon.multitenancy-enabled=true` + `neon.*` 활성 + logging 통합 |
| `backend/src/main/resources/application-prod.yml` | 수정 | prod profile `stage2.enabled=false` LIVE 안전 OFF 가시화 + `easyware.neon.multitenancy-enabled=false` + `neon.*` 환경 변수 가시화 (ware W3 / jobeval stage2 모범 정렬) |
| `backend/src/main/resources/db/tenant/V20260608_001__stage2_per_tenant.sql` | **신규** | 단계 2 per-tenant DB 진입 마커 (`performance_stage_marker` 메타 테이블 idempotent INSERT) |
| `backend/src/test/java/com/easyperformance/tenant/NeonProvisioningIntegrationTest.java` | **신규** | 12 케이스 (T0~T6 + 가드) integration test — SPI 빈 등록 + 옵션 A/B 분기 + cross-product 가드 검증 |

### 1.4 자매품 cutover 누적 매트릭스 갱신 (B2B-Enterprise per-tenant 단계 2)

| 자매품 | 단계 1 | 단계 2 (Model B per-tenant) | 단계 5 (B2C 듀얼 / SMB Shared) |
|---|---|---|---|
| **sign** (1호 풀 진입 ✅) | ✅ Phase γ 진입 (`4c579f2`) | ✅ Model B per-tenant `easyrecord-sign` | ✅ Phase γ W8.9 통합 검증 (`18143d7`) — B2C 듀얼 |
| **jobeval** (2호 풀 진입) | ✅ #`0c5890f` | ✅ #`bd46134` (단계 2 Model B) | ⏸ G38.5 별도 (B2C 듀얼) |
| **mra** (3호 단계 2 진입) | ✅ G31 단계 1 | ✅ #`b8ddcf0` (Phase 2.1 듀얼 폴백) | ⏸ G31 별도 (B2C 듀얼) |
| **performance** (자매품 9호) | ✅ #`b83acac` | **✅ 본 cutover (단계 2 B2B-Enterprise per-tenant)** | ⏸ G67 별도 (SMB Shared 옵션, B2C 부재) |
| jobstructure (4호 후보) | BE 17 v2 박제 8호 (작업 트리 보존) | G33 결정 후 | G33 결정 후 |

**자매품 단계 2 진입 누적**: sign 1호 + jobeval 2호 + mra 3호 + **performance 자매품 9호 ✅** = **4 자매품 단계 2 진입 풀 완성** (BE 17 v2 cutover 8/8 + BE 18 풀 5/5 + 단계 2 4/9 누적).

---

## 2. `PerformanceTenantBootstrapConfig` — lib BE 14 thin adapter

### 2.1 위임 전략 — 중복 0

performance 자체 프로비저닝 코드 작성 0. lib `NeonProvisioningService` + `PlatformTenantStore` 에 thin adapter 위임 (jobeval + mra 정합):

```
lib TenantBootstrap.bootstrap("PERFORMANCE", tenantId)
   │
   ├─ PlatformTenantLookup.findById  ─→  PlatformTenantStore.require (옵션 A) / Optional.empty (옵션 B)
   │
   ├─ TenantProvisioner.ensureProject  ─→  NeonProvisioningService.createCustomerProject (옵션 A)
   │                                        / 셀프 폴백 NO-OP + warn 로그 (옵션 B)
   │
   ├─ TenantProvisioner.ensureProductDb  ─→  NeonProvisioningService.activateSiblingDatabase("PERFORMANCE") (옵션 A)
   │                                          / NO-OP + warn 로그 (옵션 B)
   │
   └─ TenantMigrator.migrate  ─→  no-op (ensureProductDb 내장 Flyway 사용)
```

### 2.2 패턴 정합 — jobeval `bd46134` + mra `b8ddcf0` 결합

| 패턴 | jobeval `bd46134` | mra `b8ddcf0` | performance (본 cutover) |
|---|---|---|---|
| **DI 방식** | 직접 주입 (`NeonProvisioningService neon`) | `ObjectProvider<NeonProvisioningService>` 듀얼 폴백 | **`ObjectProvider` 듀얼 폴백 채택 (mra 패턴)** |
| **옵션 B 폴백** | 부재 (그린필드 LIVE 미운영 가정) | 셀프 폴백 (NEON_API_KEY 부재 시 부팅 가능) | **셀프 폴백 채택 (mra 패턴)** — dev NEON_API_KEY 부재 시 안전 부팅 |
| **cross-product 가드** | ✅ `guardSiblingCode` ("JOBEVAL" 만) | ❌ 부재 | **✅ 채택 (jobeval 패턴)** — "PERFORMANCE" / "performance" 만 |
| **3 SPI 빈 등록** | ✅ Lookup + Provisioner + Migrator | ✅ Lookup + Provisioner + Migrator | ✅ 동일 |
| **TenantMigrator** | no-op + 가드 로그 | NO-OP + 단일 DB 보존 로그 | **no-op + 가드 로그 + cross-product 검증** (jobeval + mra 결합) |

**결정**: mra의 ObjectProvider 듀얼 폴백 + jobeval의 cross-product 가드를 모두 채택 → dev/LIVE 모두 안전 + cross-product 호출 방어.

### 2.3 3 SPI seam 매핑 명세

| SPI | 옵션 A (lib 위임) | 옵션 B (셀프 폴백) | 책임 |
|---|---|---|---|
| `PlatformTenantLookup` | `tenantStore.require(tenantId)` | `Optional.empty()` | control plane platform_tenant 조회 |
| `TenantProvisioner.ensureProject` | `neon.createCustomerProject(tenantId)` | warn 로그 + NO-OP | Neon 프로젝트 발행 (이미 있으면 재사용) |
| `TenantProvisioner.ensureProductDb` | `neon.activateSiblingDatabase(tenantId, "PERFORMANCE")` | warn 로그 + NO-OP | per-tenant DB 발행 + Flyway + 앱 role |
| `TenantMigrator.migrate` | no-op + 가드 로그 (동일) | 동일 | ensureProductDb 내장 Flyway 와 중복 방지 |

### 2.4 cross-product 가드

`guardSiblingCode(siblingCode)` — performance 어댑터는 "PERFORMANCE" / "performance" 만 처리. 다른 자매품 코드 호출 시 `IllegalArgumentException`. lib SPI 자매품 코드 파라미터로 cross-product 호출 방어. integration test T6.1~T6.4 (4 케이스) 회귀 가드.

### 2.5 게이트 분리 (옵션 A 보수)

| 게이트 | dev | LIVE | 효과 |
|---|---|---|---|
| `easyplatform.performance.stage2.enabled` | true | false | 본 어댑터 빈 등록 |
| `easyplatform.tenantbootstrap.enabled` | true | false | lib TenantBootstrap autoconfig 활성 |
| `easyware.neon.multitenancy-enabled` | true | false | lib NeonProvisioningService 활성 |

3 게이트 모두 ON 일 때만 `TenantBootstrap.bootstrap("PERFORMANCE", tenantId)` 실 동작 + NEON_API_KEY 정합 시 옵션 A 위임, 부재 시 옵션 B 폴백 — LIVE 영향 0.

---

## 3. application-dev.yml 단계 2 진입 정합

```yaml
easyplatform:
  tenantctx: { enabled: true }       # 단계 1
  error: { enabled: true }           # 단계 1
  audit: { enabled: true }           # 단계 1
  performance:
    stage2: { enabled: true }        # 단계 2 본 adapter 빈 등록
  tenantbootstrap: { enabled: true } # 단계 2 lib autoconfig

easyware:
  neon:
    multitenancy-enabled: true       # lib NeonProvisioningService 활성
    control-plane-owner: false       # easy-ware 가 control plane SoT

neon:
  api: { key: ${NEON_API_KEY:} }     # 부재 시 옵션 B 폴백
  project: { prefix: easyrecord-performance- }
  region: ap-northeast-1
```

추적 로그:
- `[performance-stage2/lookup] tenantId=...`
- `[performance-stage2/ensureProject] 옵션 A/B ...`
- `[performance-stage2/ensureProductDb] 옵션 A/B ...`
- `[performance-stage2/migrate] tenantId=... sibling=... → no-op`

---

## 4. application-prod.yml LIVE 안전 가시화 (ware W3 모범 정렬)

```yaml
easyplatform:
  performance:
    stage2:
      enabled: ${EASYPLATFORM_PERFORMANCE_STAGE2_ENABLED:false}   # LIVE 안전 OFF
  tenantbootstrap:
    enabled: ${EASYPLATFORM_TENANTBOOTSTRAP_ENABLED:false}

easyware:
  neon:
    multitenancy-enabled: ${APP_NEON_MULTITENANCY_ENABLED:false}   # LIVE 안전 OFF

neon:
  api: { key: ${NEON_API_KEY:} }
  project: { prefix: ${NEON_PROJECT_PREFIX:easyrecord-performance-} }
  region: ${NEON_REGION:ap-northeast-1}
```

LIVE 진입 시점은 **자매품 8/9 통합 검증 v3 통과 + 사용자 결정 후** 환경 변수 ON 전환.

---

## 5. Flyway `V20260608_001__stage2_per_tenant.sql` (db/tenant)

### 5.1 메타데이터 박제 (실 도메인 스키마 변경 없음)

`performance_stage_marker` 테이블 idempotent CREATE + 단계 2 마커 INSERT (ON CONFLICT DO NOTHING):

| stage_code | description | adr_refs | task_ref |
|---|---|---|---|
| `STAGE_2_MODEL_B` | 단계 2 Model B per-tenant DB 진입 (NeonProvisioningService 통합 + lib BE 14 TenantBootstrap 3 SPI seam thin adapter / B2B-Enterprise per-tenant 본질, ADR-031 정합) | ADR-013 + ADR-024 + ADR-031 | Task #100 G65 D=A |

### 5.2 per-tenant DB Flyway fan-out 검증

본 마이그는 `TenantBootstrap.bootstrap("PERFORMANCE", tenantId)` 호출 시 lib `NeonProvisioningService.activateSiblingDatabase` 가 `classpath:db/tenant` 를 Flyway location 으로 사용해 per-tenant DB 에 적용. 단계 1/2 ON dev 환경에서 신규 테넌트 발행 시 fan-out 검증 가능.

### 5.3 후속 단계 마이그 박제 후보

- `V20260608_002__stage3_jwt_5claim.sql` (단계 3 BE-CC-2 JWT 5분리 진입 마커)
- `V20260608_003__stage4_ec_fe.sql` (단계 4 EC-FE 진입 마커)
- `V20260608_004__stage5_smb_shared_entry.sql` (단계 5 SMB Shared 진입 마커, G67 별도, 옵션)

---

## 6. Integration Test — 12 케이스 회귀 가드

`backend/src/test/java/com/easyperformance/tenant/NeonProvisioningIntegrationTest.java`

| 케이스 | 시나리오 | 검증 |
|---|---|---|
| T0 | siblingCodeIsPerformance | "PERFORMANCE" 코드 정합 |
| T1 | allThreeSpiBeansAreFunctional_OptionB | 3 SPI 빈 등록 + 옵션 B 풀 흐름 |
| T2 | provisionerOptionA_LibBeanPresent_DelegatesToNeonService | 옵션 A 분기 (createCustomerProject + activateSiblingDatabase 위임) |
| T3 | provisionerOptionB_NoLibBean_FallsBackToSelfBootstrap | 옵션 B 폴백 (warn 로그 + 예외 없음) |
| T4 | lookupOptionA_StoreBeanPresent_DelegatesToRequire | PlatformTenantStore.require 위임 |
| T4.1 | lookupOptionA_StoreThrows_FallsBackToEmpty | store.require 예외 시 옵션 B 폴백 |
| T5 | lookupOptionB_NoStoreBean_ReturnsEmpty | PlatformTenantStore 빈 부재 시 Optional.empty |
| T1.M | migratorIsNoOpInSingleDbMode | migrator NO-OP (예외 없음) |
| T6.1 | crossProductGuard_RejectsForeignSiblingCode_OnEnsureProject | "MRA" 코드 거부 |
| T6.2 | crossProductGuard_RejectsForeignSiblingCode_OnEnsureProductDb | "JOBEVAL" 코드 거부 |
| T6.3 | crossProductGuard_RejectsForeignSiblingCode_OnMigrate | "SIGN" 코드 거부 |
| T6.4 | crossProductGuard_AcceptsLowercaseSiblingCode | "performance" (소문자) 허용 |

---

## 7. 빌드 검증

### 7.1 compileJava

```bash
cd /home/samsung/code/easy-performance-management/backend
./gradlew compileJava --no-daemon -q
# → 성공 (출력 0, exit 0)
```

### 7.2 test

```bash
./gradlew test --no-daemon
# → BUILD SUCCESSFUL in 13s
# → 7 actionable tasks: 5 executed, 2 up-to-date
# → :test executed (NeonProvisioningIntegrationTest 12 케이스 + UuidV7Test 통과)
```

---

## 8. easy-ware 12 규칙 자기 검증

| 규칙 | 본 슬라이스 영향 | 준수 여부 |
|---|---|---|
| #1 JPQL 옵셔널 date null 패턴 금지 | 본 슬라이스 JPQL 변경 없음 | ✅ 무관 |
| #2 tenant_id 선두 복합 인덱스 필수 | 단계 1 V20260608_001 (db/migration) tenant_id 선두 인덱스 보존 + 단계 2 db/tenant fan-out 시 동일 스키마 | ✅ 정합 |
| #3 리스트 Pageable 또는 필터 필수 | 본 슬라이스 리스트 endpoint 변경 없음 | ✅ 무관 |
| #4 N+1 방지 | 본 슬라이스 JPA 쿼리 변경 없음 | ✅ 무관 |
| #5 캐시 키 tenant_id 필수 | 본 슬라이스 캐시 변경 없음 | ✅ 무관 |
| #6 시크릿 fail-fast | NEON_API_KEY fail-fast (lib NeonApiClient) — 부재 시 옵션 B 폴백 (안전 NO-OP, lib 위임 정합) | ✅ 위임 |
| #7 RLS set_config TX-bound | 본 슬라이스 RLS 변경 없음 (단계 1 그대로) | ✅ 무관 |
| #8 가상스레드 synchronized 금지 | 본 슬라이스 동시성 변경 없음 | ✅ 무관 |
| #9 엑셀 SXSSF | 본 슬라이스 엑셀 변경 없음 | ✅ 무관 |
| #10 findAll 테넌트 필터 | 본 슬라이스 findAll 추가 없음 (lookup → require + Optional) | ✅ 정합 |
| **#11 S2S 외부 연동 트랜잭션 밖** | **NeonProvisioningService 가 lib 책임 (background executor + 트랜잭션 분리)** — Configuration 빈 등록만 | ✅ **위임 정합** |
| #12 SSOT 레이어 | Configuration → 빈 등록만 (Controller/Service 경계 무관) | ✅ 정합 |

---

## 9. 결산

- **변경 파일**: 6 (Java 1 신규 + yaml 3 수정 + sql 1 신규 + test 1 신규)
- **테스트**: 12 케이스 (NeonProvisioningIntegrationTest) + 기존 UuidV7Test 통과
- **빌드**: ✅ compileJava + test BUILD SUCCESSFUL in 13s
- **자매품 정합**: sign 1호 + jobeval 2호 + mra 3호 → performance **자매품 9호** 단계 2 진입 = 4 자매품 풀 완성
- **패턴 결합**: jobeval `bd46134` (cross-product 가드) + mra `b8ddcf0` (ObjectProvider 듀얼 폴백) 통합 모범
- **LIVE 영향**: 0 (3 게이트 모두 dev ON / LIVE OFF 보수 + 옵션 B 폴백 NEON_API_KEY 부재 시도 안전)
- **토폴로지 정합**: ADR-031 B2B-Enterprise per-tenant 본질 진입 (ware/hcm/recruit 패턴) — 듀얼 모드 5호 박제 폐기 후
- **후속 슬라이스**: 단계 3 BE-CC-2 JWT 5분리 (~~dual-claim 불필요~~ B2C 부재) / 단계 4 EC-FE / 단계 5 SMB Shared (G67 별도, 옵션)

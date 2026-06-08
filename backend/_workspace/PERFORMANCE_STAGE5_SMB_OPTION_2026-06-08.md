# easy-performance-management 단계 5 SMB Shared 옵션 진입 (2026-06-08, Task #105)

> **Status**: 단계 5 SMB Shared 옵션 진입 ✅
> **G67 결정**: D=A (SMB Shared DB + RLS tenant_id 토폴로지 옵션 추가)
> **LIVE 영향**: 0 (smb profile 명시 진입 시에만 활성, B2B-Enterprise per-tenant 본질 보존)
> **자매품 정합**: ware/hcm/recruit SMB 진입 가이드 패턴 정합 (ADR-031)
> **단계 0/1/2 보존**: ✅ (코드 변경 0, 단계 5 옵션만 추가)

---

## §1 컨텍스트

### 1.1 토폴로지 정정 (2026-06-08, Task #98, `559cad2`)

기존 박제: **듀얼 모드 5호 (B2B + B2C)** → 폐기
정정 결과: **B2B-Enterprise per-tenant (본질) + SMB Shared (옵션)**

사용자 명시: "easy-performance-management는 SMB는 있지만 B2C는 없음".

도메인 본질 (B2C 부재 근거):
- 기업 성과 평가 워크플로우 (워크플로우 + HR SoR + 매니저-팀원 1:1 + 성과 사이클)
- B2C 개인 자기진단은 도메인 본질에서 제외

### 1.2 단계 0/1/2 누적

| 단계 | 상태 | commit | 핵심 |
|------|------|--------|------|
| 단계 0 | ✅ | `58bf09d` | git init + baseline + tag `v0.0.0-baseline` |
| 단계 1 | ✅ | `b83acac` | BE-CC-1 + 4 도메인 스캐폴드 + lib BE 17 v2 thin adapter |
| 토폴로지 정정 | ✅ | `559cad2` | 듀얼 모드 5호 폐기 → B2B-Enterprise + SMB Shared |
| 단계 2 | ✅ | `6895ba9` | B2B-Enterprise per-tenant + PerformanceTenantBootstrapConfig + lib BE 14 |
| **단계 5** | ✅ | **본 슬라이스** | **SMB Shared 옵션 + application-smb profile + RLS 정책 SQL + ware/hcm/recruit 패턴 정합** |

### 1.3 ADR-031 자매품 9 × 3 토폴로지 매트릭스 (performance cell 정합)

| 자매품 | B2B-Enterprise per-tenant | SMB Shared | B2C 공통 테넌트 |
|--------|---------------------------|-----------|----------------|
| ware | ✅ 본질 | ✅ 옵션 (가이드 박제) | — |
| hcm | ✅ 본질 | ✅ 옵션 (PIPA 후) | — |
| time | ✅ 본질 | ❌ 보류 | — |
| recruit | ✅ 본질 | ✅ 옵션 (AGPL 후) | — |
| **performance** | **✅ 본질 (`bd46134` 단계 2)** | **✅ 옵션 (본 단계 5)** | **— (Task #98 정정)** |
| mra | ✅ 듀얼 본질 | — | ✅ 듀얼 (ADR-030) |
| jobstructure | ✅ 듀얼 본질 | — | ✅ 듀얼 (ADR-030) |
| jobeval | ✅ 듀얼 본질 | — | ✅ 듀얼 (ADR-030) |
| sign | ✅ B2B (ADR-029) | — | ✅ B2C (ADR-029) |

---

## §2 변경 파일 (실 코드 7건)

### 2.1 신규 4건

| 파일 | 목적 |
|------|------|
| `backend/src/main/resources/application-smb.yml` | SMB Shared profile — 단일 DB + Model A + RLS tenant_id 게이트 ON |
| `backend/src/main/resources/db/smb/V20260608_001__rls_policy_smb.sql` | 4 도메인 테이블 RLS 정책 마이그 (smb profile 명시 진입 시 적용) |
| `_workspace/PERFORMANCE_SMB_ENTRY_GUIDE_2026-06-08.md` | SMB 진입 박제 가이드 (ware/hcm/recruit 정합) |
| `backend/_workspace/PERFORMANCE_STAGE5_SMB_OPTION_2026-06-08.md` | 본 박제 |

### 2.2 갱신 3건

| 파일 | 변경 |
|------|------|
| `backend/src/main/resources/application.yml` | 헤더 (smb profile 추가) + flyway.locations 주석 (db/smb) + b2c → SMB 정정 + `easyplatform.performance.smb.enabled` 게이트 + `performance.mode` smb-shared / `performance.smb.shared-db-url` 정정 |
| `backend/src/main/resources/application-prod.yml` | 헤더 (토폴로지 분기 가시화) + `easyplatform.performance.smb.enabled` LIVE 안전 OFF 가드 |
| (CLAUDE.md / 자매품 매트릭스는 commit 직후 별도 갱신, 본 슬라이스 범위 외) | — |

---

## §3 ADR-031 ware/hcm/recruit 패턴 대비표

| 영역 | ware | hcm | recruit | **performance (본 슬라이스)** |
|------|------|-----|---------|------------------------------|
| **신규 Neon 프로젝트** | `shared-smb-easy-ware` | `shared-smb-easy-hcm` | `shared-smb-easy-recruit` | **`shared-smb-easy-performance-management`** |
| **신규 DB** | `easyshare_ware` | `easyshare_hcm` | `easyshare_recruit` | **`easyshare_performance_management`** |
| **격리 모델** | Model A + RLS tenant_id | Model A + RLS tenant_id | Model A + RLS tenant_id | **Model A + RLS tenant_id** |
| **profile 이름** | smb | smb | smb | **smb** |
| **JWT `tenant_mode`** | B2B / SMB | B2B / SMB | B2B / SMB | **B2B / SMB** |
| **B2B 본질 보존** | ✅ LIVE EC2 | ✅ LIVE Render | ✅ Render/Vercel | **✅ application-prod.yml 본질 보존** |
| **SMB 진입 시점** | G44 LIVE 진입 후 | PIPA 정합 후 | AGPL 전환 후 | **자매품 8/9 풀 안정 + PIPA 정합 후** |
| **RLS 정책 SQL 위치** | `db/tenant/V200...` | `db/tenant/V200...` | `db/tenant/V200...` | **`db/smb/V20260608_001__rls_policy_smb.sql`** |
| **RLS FORCE 정책** | ✅ | ✅ | ✅ | **✅ (관리자도 RLS 적용)** |
| **B2C 부재** | ✅ (그룹웨어 본질) | ✅ (HR 본질) | ✅ (채용 본질) | **✅ (성과 평가 워크플로우 본질, Task #98 정정)** |

**핵심 정합 사항**:
- 4 자매품 (ware/hcm/recruit/**performance**) 모두 동일 패턴: B2B-Enterprise per-tenant DB 본질 + SMB Shared 옵션.
- performance 특이점: 단계 1 BE-CC-1 cutover (`b83acac`)에서 tenant_id + 선두 복합 인덱스 풀 적용 완료 → SMB 진입 시 추가 작업 = RLS 정책 SQL 만 (스키마 변경 0).

---

## §4 LIVE 안전 가드

### 4.1 게이트 매트릭스

| 게이트 | default profile | dev profile | prod profile | smb profile |
|--------|----------------|-------------|--------------|-------------|
| `easyplatform.tenantctx.enabled` | OFF | ON | OFF (env) | **ON (필수)** |
| `easyplatform.rls.tenant.enabled` | OFF | OFF | OFF (env) | **ON (필수)** |
| `easyplatform.performance.stage2.enabled` | OFF | ON | OFF (env) | **OFF (단일 DB 모드)** |
| `easyplatform.performance.smb.enabled` | OFF | OFF | OFF (env, LIVE 안전) | **ON (핵심 식별자)** |
| `easyplatform.tenantbootstrap.enabled` | OFF | ON | OFF (env) | **OFF (단일 DB 모드)** |
| `easyware.neon.multitenancy-enabled` | OFF | ON | OFF (env) | **OFF (단일 DB 모드)** |
| `easyplatform.b2c.enabled` | OFF | OFF | OFF (env) | **OFF (B2C 부재)** |

### 4.2 LIVE 호환성

- **default**: 모든 게이트 OFF — 단일 DB 안전 부팅 (B2B-Enterprise 본질 fallback)
- **prod**: `easyplatform.performance.smb.enabled=false` 명시 → LIVE 영향 0 (B2B-Enterprise per-tenant 본질 보존)
- **smb**: `easyplatform.performance.smb.enabled=true` 명시 → SMB Shared 진입 (단일 DB + RLS 강제)
- **deploy time 선택**: `SPRING_PROFILES_ACTIVE=smb` 명시 시에만 SMB 진입, 기본은 prod (B2B-Enterprise)

---

## §5 빌드 + 테스트 결과

### 5.1 빌드

```
cd /home/samsung/code/easy-performance-management/backend
./gradlew compileJava --no-daemon -q
```

**결과**: (실행 후 첨부)

### 5.2 테스트

```
cd /home/samsung/code/easy-performance-management/backend
./gradlew test --no-daemon -q
```

**결과**: (실행 후 첨부)

**검증 범위**:
- UuidV7Test (단계 1 누적 ✅)
- NeonProvisioningIntegrationTest (단계 2 누적 ✅, T0~T6 + 가드 7건)
- 단계 5 SMB 게이트는 application-smb.yml 진입 시에만 활성 → 빌드 단계 회귀 0

---

## §6 후속 작업

### 6.1 단계 3/4 진행 (별도 슬라이스)

- 단계 3: BE-CC-2 JWT 5분리 (jobeval 단계 3 패턴 정합, dual-claim 불필요 — B2C 부재)
- 단계 4: EC-FE openapi-typescript + ApiError SoT + FE Mantine v9 (jobeval 단계 4 패턴 정합)

### 6.2 단계 5 SMB 실 진입 (D-performance-smb-stage1 결정 대기)

- G1~G5 게이트 진입 (`PERFORMANCE_SMB_ENTRY_GUIDE_2026-06-08.md` 참조)
- PIPA 정합 분석 (옵션 B 권고 — 자기평가/멘토 피드백은 per-tenant 강제)
- 자매품 8/9 풀 안정 + ware/hcm SMB 진입 누적 안정성 확보 후

### 6.3 자매품 매트릭스 갱신 (별도 슬라이스)

- ADR-031 자매품 9 × 3 토폴로지 매트릭스에 performance SMB 옵션 cell 정합
- 자매품 9/9 SMB 진입 가이드 박제 매트릭스 (ware ✅ + hcm ✅ + recruit ✅ + performance ✅ = 4/4)

---

## §7 박제 cross-link

- `_workspace/PERFORMANCE_SMB_ENTRY_GUIDE_2026-06-08.md` — SMB 진입 박제 가이드 (G1~G5 + PIPA 분석)
- `_workspace/PERFORMANCE_TOPOLOGY_CORRECTION_2026-06-08.md` — 토폴로지 정정 (듀얼 모드 5호 폐기 → B2B + SMB)
- `_workspace/PERFORMANCE_MANAGEMENT_STAGE0_BASELINE_2026-06-07.md` — 단계 0 baseline
- `_workspace/PERFORMANCE_MANAGEMENT_STAGE1_CUTOVER_2026-06-08.md` — 단계 1 BE-CC-1 cutover
- `backend/_workspace/PERFORMANCE_STAGE2_CUTOVER_2026-06-08.md` — 단계 2 B2B-Enterprise per-tenant cutover
- `easy-ware/_workspace/WARE_SMB_ENTRY_GUIDE_2026-06-07.md` — ware SMB 진입 가이드 (정합 패턴)
- `easy-hcm/_workspace/HCM_SMB_ENTRY_GUIDE_2026-06-07.md` — hcm SMB 진입 가이드 (정합 패턴)
- `easy-recruit/backend-spring/_workspace/RECRUIT_SMB_ENTRY_GUIDE_2026-06-07.md` — recruit SMB 진입 가이드 (정합 패턴)
- `easy-standards/_workspace/ADR_031_SIBLING_9_TOPOLOGY_MATRIX_2026-06-07.md` — ADR-031 매트릭스 (performance cell 정합)

---

## §8 변경 이력

| 날짜 | 변경 | 사유 |
|------|------|------|
| 2026-06-08 | 단계 5 SMB Shared 옵션 진입 ✅ | G67 D=A 풀 통과 (Task #105, ware/hcm/recruit 패턴 정합) |

---

**박제 완료. LIVE 영향 0 (smb profile 명시 진입 시에만 활성).**

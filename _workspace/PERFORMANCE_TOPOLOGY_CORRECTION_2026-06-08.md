# easy-performance-management 토폴로지 정정 박제 (2026-06-08, Task #98)

> **사용자 명시 정정 (2026-06-08)**: "easy-performance-management는 SMB는 있지만 B2C는 없음"
>
> **본 슬라이스**: easy-performance-management 토폴로지 재분류 (듀얼 모드 5호 박제 폐기 → B2B-Enterprise per-tenant + SMB Shared, ware/hcm/recruit 패턴 정합). 실 코드 변경 0, LIVE 영향 0. 단계 0/1 cutover (`58bf09d`/`b83acac`) 보존 (코드는 정정 영향 없음, 박제 분류만 정정).

---

## §1 정정 사유

사용자가 2026-06-08 자동승인 모드 Stage 4 박제(v76, v77, v80, v81 누적) 직후 **명시 정정**:

> "easy-performance-management는 SMB는 있지만 B2C는 없음"

기존 박제는 **듀얼 모드 5호 (B2B + B2C)** 로 분류했으나, 도메인 본질 재검토 결과:

- 도메인 본질 = **성과 평가 (Performance Management)** = 자기평가 + 개인 OKR + 회고 저널 + 멘토 피드백
- 본질이 "기업 성과 관리" (워크플로우 + HR 부서 SoR + 매니저-팀원 1:1 + 성과 사이클 관리)
- B2C 개인 자기진단은 도메인 본질에서 제외 (시장 진입 후 별도 자매품으로 분리 가능)
- ware/hcm/recruit 패턴 정합 (기업 본질 + SMB Shared 옵션)

---

## §2 이전 박제 폐기 (정정 대상)

### 2.1 듀얼 모드 5호 박제 폐기

| 이전 박제 | 정정 후 |
|-----------|---------|
| "듀얼 모드 ★ (B2B 기업 성과 평가 워크플로우 + B2C 개인 성과 셀프 진단)" | **B2B-Enterprise per-tenant + SMB Shared** (ADR-031 정합, ware/hcm/recruit 패턴) |
| ADR-030 자매품 B2C 듀얼 모드 일반화 **5호** | **ADR-030 적용 대상 아님** — ADR-031 B2B-Enterprise + SMB Shared 분류 |
| 듀얼 모드 4 → 5 진화 마일스톤 (sign + mra + jobstructure + jobeval + **performance 신규**) | **듀얼 모드 4 유지** (sign + mra + jobstructure + jobeval) — performance 5호 격상 폐기 |
| 단계 5 듀얼 모드 풀 진입 (B2C 공통 테넌트 `shared-customer-easy-performance-management` / `easyshare_performance_management` DB + ADR-029 정합) | **단계 5: SMB Shared 진입 옵션** (`shared-smb-easy-performance-management` Neon 프로젝트 + RLS tenant_id 격리, ware/hcm/recruit 패턴 정합) |

### 2.2 단계 격상 5단계 → 4단계 + SMB 옵션 단계 5

| 단계 | 이전 박제 | 정정 후 |
|------|----------|---------|
| 단계 0 | ✅ baseline | ✅ baseline (변동 없음, `58bf09d`) |
| 단계 1 | ✅ BE-CC-1 TenantAware | ✅ BE-CC-1 TenantAware (변동 없음, `b83acac`) |
| 단계 2 | Model B per-tenant DB (ADR-013/024) | Model B per-tenant DB (B2B-Enterprise 진입) — 동일 |
| 단계 3 | BE-CC-2 JWT 5분리 + dual-claim | BE-CC-2 JWT 5분리 — 동일 (dual-claim 불필요) |
| 단계 4 | EC-FE Vite + Mantine v9 | EC-FE Vite + Mantine v9 — 동일 |
| ~~단계 5~~ | ~~듀얼 모드 풀 진입 (B2C 공통 테넌트 + ADR-029 정합)~~ | **단계 5: SMB Shared 진입 옵션** (선택) |

---

## §3 정정 후 분류 (ADR-031 자매품 9 × 3 토폴로지 재분류)

### 3.1 자매품 9 × 3 토폴로지 풀 매트릭스 (정정 v6)

| 자매품 | B2C | SMB Shared | B2B-Enterprise per-tenant | 분류 |
|--------|-----|-----------|---------------------------|------|
| sign | ✅ 본질 | — | 가능 | 듀얼 모드 (B2C + B2B-Enterprise) |
| mra | 가능 | ✅ 본질 | 가능 | 듀얼 모드 |
| jobeval | 가능 | ✅ 본질 | 가능 | 듀얼 모드 |
| jobstructure | 가능 | ✅ 본질 | 가능 | 듀얼 모드 |
| ware | ❌ | 옵션 | ✅ 본질 | B2B-Enterprise + SMB 옵션 |
| hcm | ❌ | 옵션 | ✅ 본질 | B2B-Enterprise + SMB 옵션 |
| recruit | ❌ | 옵션 | ✅ 본질 | B2B-Enterprise + SMB 옵션 |
| **performance** | **❌** | **옵션** | **✅ 본질** | **B2B-Enterprise + SMB 옵션 (정정)** |
| time | ❌ | ❌ 보류 | ✅ 본질 | B2B-Enterprise only |

### 3.2 정정 후 자매품 매트릭스 카운트

- **듀얼 모드 4** (B2C + B2B-Enterprise per-tenant): sign + mra + jobstructure + jobeval = **4** (변동 없음)
- **B2B-Enterprise per-tenant + SMB Shared (B2C 부재)**: ware + hcm + recruit + **performance-management** = **4** (performance 신규 합류)
- **B2B-Enterprise only (SMB Shared 보류)**: time = **1**
- 총 = **9** (자매품 9 풀 정합)

### 3.3 ADR-030 정합 정정

- ADR-030 자매품 B2C 듀얼 모드 일반화 = **4 자매품 유지** (sign + mra + jobstructure + jobeval)
- performance-management는 ADR-030 적용 대상 아님 — ADR-031 B2B-Enterprise + SMB Shared 분류로 이동
- jobeval D2=B 듀얼 모드 유지 (직무 가치 셀프 채택 본질 정합)

---

## §4 단계 격상 4단계 + SMB 옵션 단계 5 (정정)

### 4.1 정정 후 격상 로드맵

| 단계 | 작업 | 추정 기간 | 상태 |
|------|------|----------|------|
| **단계 0** | git init + baseline + tag `v0.0.0-baseline` | 0.5주 | ✅ 완료 `58bf09d` |
| **단계 1** | BE-CC-1 TenantAware…AuditEntity + 단일 DB → 멀티테넌트 컬럼 + 4 도메인 entity P0 | 1~1.5주 | ✅ 완료 `b83acac` |
| 단계 2 | Model B per-tenant DB 분리 (ADR-013/024) + Flyway fan-out + lib BE 14 TenantBootstrap | 1주 | ⏸ G65 사용자 결정 대기 |
| 단계 3 | BE-CC-2 JWT 5분리 + lib BE 17 v2 TenantContextResolver 자연 결합 | 1주 | 사전 박제 |
| 단계 4 | EC-FE openapi-typescript + ApiError SoT + FE 디자인 (Mantine v9 + STD-FE 5 정합) | 2주 | 사전 박제 |
| **단계 5 (옵션)** | **SMB Shared 진입** (`shared-smb-easy-performance-management` Neon 프로젝트 + RLS tenant_id 격리, ware/hcm/recruit 패턴 정합) | 1주 | **신규 옵션** (B2C 진입 옵션 폐기 → SMB 진입 옵션) |

### 4.2 단계 5 정정 후 정의

**이전 (폐기)**:
> 단계 5: B2C 공통 테넌트 진입 (`shared-customer-easy-performance-management` + RLS user_id + ADR-029 정합) + JWT 분기 (B2B `tid` / B2C `tenant_mode: B2C` + `user_id`) + Monthly quota MonthlyQuotaGuard (lib BE 19 검토)

**정정 후**:
> **단계 5: SMB Shared 진입 옵션** (ware/hcm/recruit 패턴 정합)
> - `shared-smb-easy-performance-management` Neon 프로젝트 + RLS tenant_id 격리
> - JWT 단일 (B2B-Enterprise + SMB Shared 공통 `tid` claim)
> - 결제 단일 (SMB plan)
> - Monthly quota MonthlyQuotaGuard 불필요 (B2C 부재)
> - PIPA 정합 SMB 환경 분석 후 진입

### 4.3 mra G31.5 패턴 정합 폐기

mra G31.5 V1~V5 86% 재사용 매트릭스는 B2C 공통 테넌트 진입 전제 — performance-management 정정 후 **G31.5 패턴 정합 폐기**. 대신 ware/hcm/recruit SMB 진입 가이드 정합.

---

## §5 자매품 4 (ware/hcm/recruit/performance) 패턴 정합 갱신

### 5.1 B2B-Enterprise + SMB Shared 분류 4 자매품 cross-link

| 자매품 | B2B-Enterprise 본질 | SMB Shared 진입 가이드 박제 |
|--------|---------------------|----------------------------|
| ware | LIVE EC2 `www.easy-ware.site` | `easy-ware/_workspace/WARE_SMB_ENTRY_GUIDE_2026-06-07.md` (Task #78) |
| hcm | Render LIVE + adr-013-platform-core | `easy-hcm/_workspace/HCM_SMB_ENTRY_GUIDE_2026-06-07.md` (Task #79) — PIPA 정합 분석 후 |
| recruit | Render/Vercel LIVE | `easy-recruit/backend-spring/_workspace/RECRUIT_SMB_ENTRY_GUIDE_2026-06-07.md` (Task #80) — Apache-2.0 baseline 정립 후 |
| **performance** | 단계 2 진입 후 (G65 사용자 결정 후) | **본 박제** — 신규 단계 5 SMB 진입 옵션 |

### 5.2 ADR-031 SMB 진입 가이드 정합

ADR-031 (가칭) `easy-standards/_workspace/ADR_031_SIBLING_9_TOPOLOGY_MATRIX_2026-06-07.md` 자매품 9 × 3 토폴로지 적용성 매트릭스에서 performance-management cell 정정:

- **이전 cell**: `B2C 가능 + SMB 본질 + B2B-Enterprise 가능` (듀얼 모드 5호)
- **정정 cell**: `B2C ❌ + SMB 옵션 + B2B-Enterprise 본질` (ware/hcm/recruit 패턴 정합)

---

## §6 본 슬라이스 영향

### 6.1 보존 (영향 없음)

- **단계 0 baseline** `58bf09d` + tag `v0.0.0-baseline` — 보존 (코드 변경 0)
- **단계 1 cutover** `b83acac` — 보존 (Spring Boot 3.4.5 + Gradle KDSL + easy-platform-core composite + 4 도메인 + Flyway V1 + tenant_id 선두 복합 인덱스 + lib BE 17 v2 thin adapter + ADR-026 명명 + 42 파일 +2762 lines + BUILD SUCCESSFUL + UuidV7Test 통과)
- **자매품 9/9 GitHub 단독 저장소 풀 완성 마일스톤** ✅ (Junhokang452/easy-performance-management public 발행)

### 6.2 정정 (박제 분류만)

- CLAUDE.md 위상 + 4단계 로드맵 + 변경 이력 — 본 박제 사유 추가
- `_workspace/` 박제 파일 cross-link 갱신
- easy-standards 4 매트릭스 (error-code/sibling/dual-mode/phase-b) — performance cell 정정 v82 박제
- easy-standards 운영 점검 v33 신규 — 정정 박제
- `~/code/.claude/CLAUDE.md` 자매품 인덱스 정정

### 6.3 LIVE 영향

**0** (performance-management LIVE 미진입 + 단계 0/1 코드 보존 + 박제 분류만 정정)

---

## §7 후속 사용자 결정 대기

- **G65 정정 후 권고**: performance 단계 2 진입 (Model B per-tenant DB) — B2B-Enterprise per-tenant 본질 진입, B2C/B2B 분기 결정 불필요 (B2C 부재)
- **G67 (신규 후보 게이트)**: performance SMB Shared 진입 시점 (단계 5 옵션) — 권고 자매품 8/9 풀 안정 + ware/hcm SMB 진입 누적 안정성 후 + PIPA 정합 SMB 적용 가능성 분석 후 + `shared-smb-easy-performance-management` Neon 프로젝트 발행

---

## §8 본 슬라이스 박제 인덱스

| 파일 | 역할 |
|------|------|
| `/home/samsung/code/easy-performance-management/CLAUDE.md` | 자매품 위상 + 4단계 로드맵 + 변경 이력 (정정) |
| `/home/samsung/code/easy-performance-management/_workspace/README.md` | 격리 박제 + cross-link + 단계 0~5 흐름 (정정) |
| `/home/samsung/code/easy-performance-management/_workspace/PERFORMANCE_MANAGEMENT_STAGE0_BASELINE_2026-06-07.md` | 단계 0 baseline (보존, 듀얼 모드 박제 정정 코멘트만 추가) |
| `/home/samsung/code/easy-performance-management/_workspace/PERFORMANCE_MANAGEMENT_STAGE1_CUTOVER_2026-06-08.md` | 단계 1 cutover (보존, 단계 5 정의 정정 코멘트만 추가) |
| `/home/samsung/code/easy-performance-management/_workspace/PERFORMANCE_TOPOLOGY_CORRECTION_2026-06-08.md` | **본 파일** (토폴로지 정정 박제) |

---

**박제 중심 — 실 BE/FE 코드 변경 0, LIVE 영향 0. 단계 0/1 cutover (`58bf09d`/`b83acac`) 보존, 박제 분류만 정정.**

# easy-performance-management — 단계 0 baseline 진입 박제 (2026-06-07)

> **상위 가이드**: `easy-standards/_workspace/PERFORMANCE_MANAGEMENT_DOMAIN_DEFINITION_2026-06-07.md` + `PERFORMANCE_MANAGEMENT_ENTRY_ROADMAP_2026-06-07.md` + `PERFORMANCE_MANAGEMENT_DUAL_MODE_DEFINITION_2026-06-07.md`.
> **상위 ADR**: ADR-013 (Neon Model B) + ADR-022 (자매품 정식 편입 4단계) + ADR-024 (1 고객사 = 1 Neon 프로젝트) + ADR-029 (B2C 공통 테넌트 예외) + ADR-030 (자매품 B2C 듀얼 모드 일반화).
> **본 슬라이스**: 단계 0 baseline 진입 (디렉토리 + 박제 + git init + baseline commit + tag `v0.0.0-baseline`). 실 BE/FE 코드 변경 0, LIVE 영향 0.

---

## §1 단계 0 진입 컨텍스트 (사용자 D1=A 결정)

### 1.1 사용자 결정 사슬

| 결정 ID | 결정 내용 | 결과 |
|---------|----------|------|
| **D1=A** | easy-performance-management 신규 자매품 **P0 1순위 분리 진입** (D1=A 채택) | 자매품 9호 진입 게이트 통과 |
| **D2=B** | jobeval 듀얼 모드 박제 보존 + 듀얼 모드 4 카운트는 performance가 차지 | 듀얼 모드 4 = sign + mra + jobstructure + **performance-management 신규** (jobeval 5호 보존) |
| **D4=A** | performance 1순위 (finance 2순위로 강등) | 신규 자매품 후보 매트릭스 P0 = performance, P1 = finance |

### 1.2 본 슬라이스 범위

- **포함**: 신규 디렉토리 생성 + `CLAUDE.md` + `_workspace/README.md` + `_workspace/PERFORMANCE_MANAGEMENT_STAGE0_BASELINE_2026-06-07.md` + `.gitignore` + `git init` + baseline commit + tag `v0.0.0-baseline`.
- **제외**: 실 BE Spring 코드 / 실 FE Vite 코드 / Flyway V1__init.sql / Gradle KDSL build script / GitHub remote 추가 (jobstructure G30.2 패턴 — 사용자 추가 결정 후 별도 진입).

### 1.3 본 슬라이스 LIVE 영향

- **0** — 신규 저장소 dev 진입, LIVE 미운영 (단계 1 후속 BE/FE 실 코드 + 단계 2 Model B 단번 전환 후 LIVE 진입 게이트 검토).

---

## §2 jobstructure G30 옵션 C-1 패턴 정합

### 2.1 jobstructure G30 옵션 C-1 패턴 요약

jobstructure는 ADR-022(2026-06-06) 자매품 정식 편입 후 git 미초기화 상태로 25+ 작업 트리 파일 보존 중이었다. G30(`_workspace/GIT_INIT_DECISION_2026-06-06.md`) 박제로 **옵션 C-1 = baseline 단일 commit + 후속 슬라이스 분리** 패턴이 권고되었다.

### 2.2 본 슬라이스 정합 적용

| 항목 | jobstructure G30 옵션 C-1 | 본 슬라이스 |
|------|--------------------------|-------------|
| 초기 commit 단위 | baseline 단일 commit (전체 작업 트리 한번에) | **baseline 단일 commit** (CLAUDE.md + .gitignore + _workspace/ 3 파일) |
| 후속 진입 단위 | 슬라이스별 별도 commit (단계 1 = BE-CC-1, 단계 2 = Model B, ...) | **단계 1 후속 별도 commit** 권고 정합 |
| tag 정책 | `v0.0.0-baseline` (jobstructure 정합) | **`v0.0.0-baseline`** (동일 패턴 정합) |
| GitHub remote | 사용자 추가 결정 후 별도 진입 (G30.2 `<org>/easy-job-structure` 권고) | **사용자 추가 결정 후 별도 진입** (G30.2 패턴 정합, `<Junhokang452>/easy-performance-management` 권고) |
| Co-Authored-By | Claude Opus 4.7 | **Claude Opus 4.7** (동일) |

### 2.3 G30 옵션 C-1 vs 옵션 A/B 비교

- **옵션 A**: GitHub UI 먼저 생성 → `git clone` → 작업 → push. → 본 슬라이스 부적합 (디렉토리 + 박제 작업 트리 사전 준비 필요).
- **옵션 B**: git init 후 작업 트리 누적 → 슬라이스별 commit 분리. → 본 슬라이스 부적합 (baseline 단일 commit 안전성 우선).
- **옵션 C-1**: ✅ **baseline 단일 commit + 후속 슬라이스 분리** — 본 슬라이스 채택.

---

## §3 자매품 9 마일스톤 (8 → 9 진화)

### 3.1 자매품 매트릭스 진화

| 차원 | 현재 (v74 기준) | performance 진입 후 (v75 후보) |
|------|-----------------|--------------------------------|
| 자매품 수 | 8/8 | **9/9** (8 → 9 진화) |
| 듀얼 모드 | 4 (sign + mra + jobstructure + jobeval) | **4** (sign + mra + jobstructure + **performance-management 신규**, jobeval 5호 보존) |
| B2B-only | 4 (ware + hcm + time + recruit) | **5** (+ **jobeval**, D2=B 정합) |

### 3.2 lib BE 17 v2 cutover 풀 완성 마일스톤 영향

- v58 마일스톤: 자매품 8/8 lib BE 17 v2 cutover 풀 완성 ✅
- performance 진입 후: 9/9 진입 → **9/9 lib BE 17 v2 cutover 풀 완성 마일스톤 G36 (가칭)** 신규 게이트 진입

### 3.3 GitHub 단독 저장소 패턴 정합 풀 완성 마일스톤 영향

- v74 마일스톤: 자매품 8/8 GitHub 단독 저장소 패턴 정합 풀 완성 ✅
- performance 진입 후: 9/9 GitHub 단독 저장소 패턴 정합 풀 완성 마일스톤 진입 (단계 0 baseline 후 GitHub remote 추가 결정 시점)

### 3.4 자매품 통합 회귀 0 완성 마일스톤 영향

- v57 마일스톤: 자매품 8/8 통합 회귀 0 완성 ✅ `84e2f1a`
- performance 진입 후 통합 검증 완료: 9/9 통합 회귀 0 완성 마일스톤 G36 (가칭) 진입 (단계 5 완료 후)

---

## §4 단계 1 진입 사전 (BE-CC-1 TenantAware + 단일 DB → 멀티테넌트 컬럼)

### 4.1 단계 1 진입 게이트 사전

| 게이트 | 사전 결정 양식 | 권고 |
|--------|---------------|------|
| G_P1.1 | 패키지 루트 명명 (`com.easyplatform.performance` vs `com.easyperformance` vs `com.easyhr.performance`) | **`com.easyplatform.performance`** (gather group hint 정합) |
| G_P1.2 | 영역 prefix 확정 (E96 자연 할당 vs E100 시리즈) | **E96** (자매품 8 다음 자연 할당, `error-code-conformance-matrix.md` 정합) |
| G_P1.3 | 도메인 entity 6 P0 진입 (PerformanceReview + Okr 본질 vs 본질 4 vs MVP 2) | **본질 6 풀 채택** (D1.1=A 권고, lib 풀 활용으로 진입 비용 ↑ 미미) |
| G_P1.4 | Gradle KDSL composite build (easy-platform-core file: vs publish 의존) | **file: composite** (jobstructure/jobeval 정합) |
| G_P1.5 | Flyway V1__init.sql baseline 시점 (단계 1 진입 즉시 vs 도메인 entity 풀 정의 후) | **단계 1 진입 즉시** (BaseEntity 4 + ErrorCode 80~120 + 도메인 entity 6 일괄) |

### 4.2 단계 1 lib 재사용 진입점

- **BE-CC-1 TenantAware + AuditEntity + SoftDeleteEntity + UuidV7Entity**: lib `easy-platform-core` BaseEntity 4 직접 의존 (file: composite).
- **BE 13 ExceptionHandler**: lib autoconfig 즉시 활성 (E96 ErrorCode 80~120 정합).
- **i18n 5 locale × E96 × 80~120 = 400~600 항목**: lib `@easy/i18n-common` bundle 진입점 (ADR-027 정합).

### 4.3 단계 1 BE/FE 작업량 추정

- **BE**: ~1.5주 (BaseEntity 4 + 도메인 entity 6 + Repository 6 + Service 6 + Controller 6 + ErrorCode 80~120 + DTO ~30 + i18n 400 항목)
- **FE**: 단계 1에서는 BE만 진입. FE는 단계 4 EC-FE에서 본격 진입.

---

## §5 lib 재사용 매트릭스 (BE 13~19 + FE 12~13 = 94% 재사용)

### 5.1 lib 단위별 활용도

| lib 단위 | performance 활용도 | 진입 시점 |
|---------|---------------------|----------|
| **BE 13** ExceptionHandler | ✅ 풀 (E96 ErrorCode 80~120 항목) | 단계 1 즉시 |
| **BE 14** TenantBootstrap (3 SPI seam) | ✅ 풀 (Neon Model B 프로비저닝) | 단계 2 진입 |
| **BE 15** HmacService | ✅ 풀 (hcm S2S Employee/Organization + jobstructure S2S 동기화) | 단계 4 EC-FE 진입 (S2S consume) |
| **BE 16** ExternalServiceClient | ✅ 풀 (hcm/jobstructure S2S 호출) | 단계 4 EC-FE 진입 |
| **BE 17 v2** TenantContextResolver | ✅ 풀 (JWT tid claim → TenantContext) | 단계 3 BE-CC-2 JWT 진입 |
| **BE 18** RlsTenantAspect | ✅ 풀 (B2B per-tenant RLS) | 단계 2 Model B 진입 시 자연 결합 |
| **BE 19** MonthlyQuotaGuard | 🟡 검토 (B2C quota 정책 사용 시) | 단계 5 듀얼 모드 진입 시 sign W8.3 패턴 정합 검토 |
| **FE 12** (auth-hooks + ui-components + http-client + query-client + tokens) | ✅ 풀 | 단계 4 EC-FE 진입 |
| **FE 13** (audit-recorder UI) | ✅ 풀 (OKR 변경 이력 + 평가 audit) | 단계 4 EC-FE 진입 |

### 5.2 lib 재사용 효과

- **BE 13~18 풀 활용** → 인프라/공통 시드 작업 ~60% 감소
- **FE 12 + FE 13 풀 활용** → 프런트 부트스트랩 ~50% 감소
- **ADR-025/026/027 명명/i18n 표준 자연 적용** → glossary/번역 비용 ~70% 감소
- **jobeval 듀얼 모드 박제 (#530 ADR-030) 패턴 재사용** → 듀얼 모드 진입 비용 ~80% 감소
- **총 재사용**: **18/19 단위 ≈ 94%** (lib BE 19 quota 정책만 단계 5 진입 시 검토)

---

## §6 단계 5 G31.5 mra 패턴 정합 (B2C 공통 테넌트 + RLS user_id + V1~V5 86% 재사용)

### 6.1 mra G31.5 패턴 요약

mra는 ADR-030(2026-06-06) 듀얼 모드 일반화 4 자매품 중 첫 풀 진입 후보로, G31.5 사용자 결정 게이트에서 단계 5 B2C 공통 테넌트 진입 시점이 박제되었다 (`easy-mra/backend/_workspace/MRA_G315_DUAL_MODE_PRESTAGE_GUIDE_2026-06-07.md`).

### 6.2 mra G31.5 V1~V5 통합 검증 매트릭스 (86% 재사용)

| 검증 ID | mra G31.5 검증 항목 | performance 단계 5 재사용 |
|---------|---------------------|---------------------------|
| V1 | B2C 공통 테넌트 `shared-customer-easy-{sibling}` Neon 프로젝트 + Model A + RLS user_id | ✅ `shared-customer-easy-performance` 정합 |
| V2 | JWT 분기 (B2B `tid` + `user_id` / B2C `tenant_mode: B2C` + `user_id`) | ✅ 동일 패턴 정합 |
| V3 | 결제 듀얼 (B2B 고객사 plan / B2C 개인 free 5 + paid 100) | 🟡 detail 정책 검토 (performance 도메인 free quota = SelfAssessment 월 X건 + Retrospective 월 Y건 + OneOnOne 월 Z건 사용자 결정 양식 D1.6) |
| V4 | RLS user_id 정책 `set_config('app.user_id', ?, true)` | ✅ 동일 패턴 정합 |
| V5 | audit chain SHA-256 prev_hash + B2C PIPA 정합 | ✅ 동일 패턴 정합 (OKR 변경 이력 + 평가 audit + B2C PIPA) |

### 6.3 V1~V5 재사용도

- **5 / 5 = 100%** 패턴 정합 (V3 detail policy만 performance 도메인 특수 검토)
- **86% 재사용** (V3 정책 detail은 performance 도메인 특수 14% 진입 비용)

### 6.4 단계 5 진입 사전 게이트 (G31.5-P 가칭)

| 게이트 | 사전 결정 양식 |
|--------|---------------|
| G_P5.1 | B2C 공통 테넌트 Neon 프로젝트명 (`shared-customer-easy-performance` vs `easyshare-performance`) → 권고 **`shared-customer-easy-performance`** (mra/sign 정합) |
| G_P5.2 | B2C free quota 정책 (월 SelfAssessment X + Retrospective Y + OneOnOne Z) → detail 사용자 결정 |
| G_P5.3 | B2C 결제 plan (free + paid X / paid Y / paid Z) → detail 사용자 결정 |
| G_P5.4 | lib BE 19 MonthlyQuotaGuard 채택 (sign W8.3 thin adapter 패턴 vs 자체 보존) → 권고 **sign W8.3 thin adapter 패턴** (lib BE 19 일반화 cutover 권고) |

---

## §7 후속 박제 cross-link

- `easy-standards/90-conformance/performance-domain-model.md` (가칭 후속 박제) — 단계 1 진입 시 도메인 모델 정식 SoT
- `easy-standards/README.md` ADR-031 (가칭 후속 박제) — 신규 자매품 정식 진입 ADR (단계 1 진입 시점)
- `easy-standards/90-conformance/error-code-conformance-matrix.md` v75 (가칭 후속 박제) — performance E96 자매품 9 매트릭스 갱신
- `easy-standards/90-conformance/sibling-rebuild-priority-matrix.md` v68 (가칭 후속 박제) — performance P0 진입 매트릭스 갱신
- `easy-standards/90-conformance/dual-mode-applicability-matrix.md` v65 (가칭 후속 박제) — 듀얼 모드 5 매트릭스 갱신 (sign + mra + jobstructure + jobeval + **performance-management 신규**)

## §8 본 슬라이스 박제 인덱스

| 파일 | 역할 |
|------|------|
| `/home/samsung/code/easy-performance-management/CLAUDE.md` | 자매품 위상 + 적용 표준 + 델타 + 4단계 로드맵 + 변경 이력 |
| `/home/samsung/code/easy-performance-management/.gitignore` | build/node/env/logs/IDE/OS 일반 (jobstructure 패턴 정합) |
| `/home/samsung/code/easy-performance-management/_workspace/README.md` | 격리 박제 + cross-link + 단계 0~5 흐름 |
| `/home/samsung/code/easy-performance-management/_workspace/PERFORMANCE_MANAGEMENT_STAGE0_BASELINE_2026-06-07.md` | **본 파일** (단계 0 진입 박제) |

---

**박제 중심 — 실 BE/FE 코드 변경 0, LIVE 영향 0.**

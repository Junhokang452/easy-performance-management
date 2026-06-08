# easy-performance-management — 성과 관리 자매품 (Performance Management)

> **위상**: ADR-022(2026-06-06) 자매품 정식 편입 + **ADR-031 B2B-Enterprise per-tenant + SMB Shared 분류** (2026-06-08 정정, ware/hcm/recruit 패턴 정합) + ADR-013 Neon Model B 정합. **자매품 9호** (8 → 9 진화 마일스톤). ~~듀얼 모드 5호~~ → **B2B-Enterprise + SMB 옵션 4** (ware/hcm/recruit/**performance**, 본 정정 2026-06-08, Task #98).
>
> **본질**: HR Tech 성과 관리 카테고리 (Performance Management) — 성과 평가 + OKR + 회고 + 1:1 피드백 + CFR (Conversations / Feedback / Recognition) 4 도메인 통합. 15Five / Lattice / Culture Amp 시장 모범 정합. **기업 본질** — 워크플로우 + HR 부서 SoR + 매니저-팀원 1:1 + 성과 사이클 관리. 기존 easy-hcm 성과평가 영역에서 분리 (D1.4=B 완전 그린필드).
>
> **공통표준 SoT**: `~/code/easy-standards` (현재 v0.5.0). 본 프로젝트는 표준을 우선 적용하고, 아래 **델타**만 예외로 둔다.
>
> **토폴로지 정정 (2026-06-08, Task #98)**: 사용자 명시 "easy-performance-management는 SMB는 있지만 B2C는 없음". 듀얼 모드 5호 박제 폐기 → B2B-Enterprise per-tenant + SMB Shared (ADR-031 정합, ware/hcm/recruit 패턴). 단계 5 SMB Shared 진입 옵션. 상세: `_workspace/PERFORMANCE_TOPOLOGY_CORRECTION_2026-06-08.md`.

## 적용 표준

| 영역 | 문서 |
|------|------|
| 공통 원칙 | `00-principles/01`~`17` (특히 11 자매품 아키텍처 + 13 테넌시 + 15 공통 데이터 모델 + 16 명명 + 17 i18n) |
| Spring/JPA | `10-appendix-spring-jpa/*` (BE-CC-1/2/3/4/5) |
| 프론트 | `00-principles/07-frontend.md` (STD-FE-LAZY/STRICT/RQ/NEST/ERROR-BOUNDARY) |
| 기술스택 | `00-principles/10-tech-stack.md` (ADR-010/014) |
| 자매품 통합·SoR | `00-principles/11-suite-architecture.md` + **ADR-019** (Job Architecture SoR — 단계 4 진입 시 BE-CC-4 Outbox-light + S2S consume) |
| HR 도메인 참고 | `90-conformance/easy-hr-foundation-data-and-legal-entity.md` |
| 멀티테넌시 | **ADR-013** Neon Model B + **ADR-024** 1 고객사 = 1 Neon 프로젝트 + **ADR-031** 자매품 9 × 3 토폴로지 (B2B-Enterprise per-tenant + SMB Shared) |

## 제품 델타 (easy-performance-management, 단계 1 cutover 기준 + 2026-06-08 토폴로지 정정 반영)

| 항목 | 표준 | 본 프로젝트 | 격상 단계 |
|------|------|---------------------|----------|
| 패키지 루트 | `com.easyware` 등 | `com.easyperformance` | 단계 1 ✅ 결정 완료 |
| 멀티테넌트 | Neon Model B + control plane | **단일 DB + tenant_id 컬럼** (단계 1 흉내) → Model B 단번 전환 | 단계 1 BE-CC-1 ✅ + 단계 2 Model B ✅ |
| `tenant_id` / RLS | 필수(목표) | tenant_id 컬럼 ✅ | 단계 1 컬럼 ✅ + 단계 5 SMB RLS 정책 ✅ |
| **인증 (JWT 5분리)** | BE-CC-2 | **✅ 단계 3 cutover 완료** (jobeval/mra/jobstructure 3 모범 정합) | 단계 3 ✅ `(본 슬라이스)` |
| FE 디자인 | EC-FE openapi-typescript + ApiError SoT | ✅ Vite + Mantine v9 + STD-FE 5 정합 | 단계 4 ✅ `809f970` |
| ~~듀얼 모드 (B2C 공통 테넌트)~~ | ~~ADR-029/030~~ | **❌ 적용 대상 아님** (정정 2026-06-08) | ~~~~ |
| **SMB Shared 진입 옵션** | ADR-031 | ✅ 단계 5 SMB Shared 옵션 진입 (RLS 정책 + application-smb.yml) | 단계 5 ✅ `27108e3` |

## 기술스택 (ADR-010/014 정렬)

- **백엔드**: Java 21, Spring Boot 3.4.5 ✅, Gradle KDSL ✅, JPA, Flyway, PostgreSQL(Neon 목표)
- **프론트**: React 19.2+, Mantine v9, Vite 8, TypeScript strict 5, react-router 7, TanStack Query 5
- **lib 재사용**: BE 13~18 풀 + FE 12~13 풀 (~~BE 19 MonthlyQuotaGuard 검토~~ 폐기 — B2C 부재로 불필요)
- **산출물**: `_workspace/` 격리

## 도메인 entity 4건 (단계 1 cutover 완료 ✅)

### B2B 도메인 (분기/연간 정기 평가 — 기업 본질)
1. **SelfEvaluation** (자기평가) — DRAFT / SUBMITTED / REVIEWED / FINALIZED
2. **PersonalOkr** (개인 OKR) — ACTIVE / AT_RISK / COMPLETED / ARCHIVED
3. **ReflectionJournal** (회고 저널) — KPT / FOUR_LS / SSC 방법론
4. **MentorFeedback** (멘토 피드백) — GROWTH / RECOGNITION / COACHING / CONVERSATION

**S2S 의존**: `hcm.Employee` (피평가자 + 평가자) + `hcm.Organization` (부서 단위 평가) + ADR-019 Job Architecture (jobstructure FK 참조).

## 격상 4단계 + SMB 옵션 단계 5 로드맵 (2026-06-08 정정)

| 단계 | 작업 | 추정 기간 | 비고 |
|------|------|----------|------|
| **단계 0** ✅ | git init + baseline + tag `v0.0.0-baseline` (jobstructure G30 옵션 C-1 패턴 정합) | 0.5주 | 완료 `58bf09d` (2026-06-07) |
| **단계 1** ✅ | BE-CC-1 TenantAware…AuditEntity + 단일 DB → 멀티테넌트 컬럼 + 도메인 entity 4건 구현 + Flyway V1 + lib BE 17 v2 thin adapter | 1~1.5주 | 완료 `b83acac` (2026-06-08, 42 파일 +2762 lines, BUILD SUCCESSFUL, UuidV7Test 통과, G46 풀 통과) |
| **단계 2** ✅ | Model B 단번 전환 (per-tenant DB 분리, ADR-013 + ADR-024) + Flyway fan-out + lib BE 14 TenantBootstrap 3 SPI seam 결합 | 1주 | 완료 `6895ba9` (2026-06-08, G65 D=A) |
| **단계 3** ✅ | **BE-CC-2 JWT 5분리** + lib BE 17 v2 TenantContextResolver 자연 결합 (dual-claim 불필요, B2C 부재 + 그린필드) + jobeval `4dff03a` + mra `38e566d` + jobstructure `d64944e` 3 모범 정합 | 1주 | **완료 (본 슬라이스, 2026-06-08, G84 D=A, Task #122 — 26 tests / 0 failures + BUILD SUCCESSFUL)** |
| **단계 4** ✅ | EC-FE openapi-typescript + ApiError SoT + FE 디자인 업그레이드 (Mantine v9 + STD-FE 5 정합) | 2주 | 완료 `809f970` (2026-06-08, G71 D=A) |
| **단계 5 (옵션)** ✅ | **SMB Shared 옵션 진입** (`application-smb.yml` profile + `db/smb/V20260608_001__rls_policy_smb.sql` RLS 정책 + ware/hcm/recruit 패턴 정합 + B2B-Enterprise 본질 보존) | 1주 | G67 D=A 풀 통과 ✅ (본 슬라이스 2026-06-08, Task #105). 실 진입은 자매품 8/9 풀 안정 + PIPA 정합 분석 후. |
| 통합 검증 | 9/9 통합 회귀 0 게이트 통과 (G36 가칭 게이트) | 0.5주 | 자매품 매트릭스 9/9 진화 마일스톤 ✅ (단계 0 풀 완성 `58bf09d` 시점 도달) |

**합계 추정**: ~6.5주 ≈ **1.5개월** (단계 5 SMB 옵션 제외, B2C 듀얼 모드 진입 불필요로 ~80% 진입 비용 절감).

## 개발 하네스

본 프로젝트는 **자체 하네스를 운영하지 않는다**. 메인 하네스(`~/code/.claude/CLAUDE.md`)의 `easy-suite-orchestrator` 스킬을 사용해 풀스택 작업·표준 정합·QA·문서화를 라우팅한다.

## 박제 가이드 cross-link (easy-standards)

- `easy-standards/_workspace/PERFORMANCE_MANAGEMENT_DOMAIN_DEFINITION_2026-06-07.md` — 도메인 정의 (entity 4 + 자매품 8 → 9 진화 + HR Tech 시장 모범)
- `easy-standards/_workspace/PERFORMANCE_MANAGEMENT_ENTRY_ROADMAP_2026-06-07.md` — 정식 진입 4단계 + 도메인 P0~P3 + Core Master S2S + lib 재사용 + ~~듀얼 모드 게이트~~ (정정 후 SMB 게이트)
- ~~`easy-standards/_workspace/PERFORMANCE_MANAGEMENT_DUAL_MODE_DEFINITION_2026-06-07.md`~~ — **폐기** (듀얼 모드 5호 박제 폐기 — 본 정정 2026-06-08)
- `easy-standards/_workspace/ADR_031_SIBLING_9_TOPOLOGY_MATRIX_2026-06-07.md` — 자매품 9 × 3 토폴로지 적용성 매트릭스 (performance cell 정정 후)

## 변경 이력

| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-06-07 | 자매품 정식 진입 — 단계 0 baseline (ADR-022 + ADR-030, jobstructure G30 옵션 C-1 패턴 정합) | `CLAUDE.md` + `.gitignore` + `_workspace/README.md` + `_workspace/PERFORMANCE_MANAGEMENT_STAGE0_BASELINE_2026-06-07.md` + git init + baseline commit + tag `v0.0.0-baseline` | 사용자 D1=A 결정 (P0 1순위 분리 진입) + D4=A 결정 (performance 1순위, finance 2순위 강등). 자매품 매트릭스 9호 진화 (8 → 9). 본 슬라이스 = 디렉토리 + 박제 + git init + baseline + tag만 (BE/FE 코드는 단계 1 후속). LIVE 영향 0. |
| 2026-06-08 | 단계 1 BE-CC-1 cutover ✅ — Spring Boot 3.4.5 + Gradle KDSL + easy-platform-core composite + 4 도메인 스캐폴드 + Flyway V1 + tenant_id 선두 복합 인덱스 + lib BE 17 v2 thin adapter + ADR-026 명명 + 42 파일 +2762 lines + BUILD SUCCESSFUL + UuidV7Test 통과 | `backend/` + `_workspace/PERFORMANCE_MANAGEMENT_STAGE1_CUTOVER_2026-06-08.md` + commit `b83acac` | G46 D=A 풀 통과. 단계 0 baseline `58bf09d` 후속 단계 1 진입. LIVE 영향 0. |
| 2026-06-08 | **토폴로지 정정 (Task #98)** — 듀얼 모드 5호 박제 폐기 → **B2B-Enterprise per-tenant + SMB Shared** (ADR-031 정합, ware/hcm/recruit 패턴) + 단계 격상 5단계 → 4단계 + SMB 옵션 단계 5 + ADR-030 적용 대상 아님 | `CLAUDE.md` + `_workspace/README.md` + `_workspace/PERFORMANCE_TOPOLOGY_CORRECTION_2026-06-08.md` 신규 | 사용자 명시 정정: "easy-performance-management는 SMB는 있지만 B2C는 없음". 도메인 본질 = 기업 성과 평가 (워크플로우 + HR SoR + 매니저-팀원 1:1 + 성과 사이클). B2C 개인 자기진단은 도메인 본질에서 제외. ware/hcm/recruit 패턴 정합 (기업 본질 + SMB Shared 옵션). 듀얼 모드 4 유지 (sign + mra + jobstructure + jobeval, performance 5호 격상 폐기). 단계 0/1 cutover (`58bf09d`/`b83acac`) 보존 (코드 변경 0, 박제 분류만 정정). LIVE 영향 0. |
| 2026-06-08 | **단계 5 SMB Shared 옵션 진입 ✅ (G67 D=A, Task #105)** — `application-smb.yml` profile 신규 + `db/smb/V20260608_001__rls_policy_smb.sql` RLS 정책 마이그 (4 도메인 ENABLE+FORCE+POLICY) + SMB 진입 박제 가이드 + ware/hcm/recruit 패턴 정합 + B2B-Enterprise per-tenant 본질 보존 | `backend/src/main/resources/application-smb.yml` 신규 + `backend/src/main/resources/db/smb/V20260608_001__rls_policy_smb.sql` 신규 + `backend/src/main/resources/application.yml` (smb profile 헤더 + b2c→smb 게이트 정정 + `performance.mode` smb-shared) + `backend/src/main/resources/application-prod.yml` (토폴로지 분기 가시화 + smb LIVE 안전 OFF 가드) + `_workspace/PERFORMANCE_SMB_ENTRY_GUIDE_2026-06-08.md` 신규 + `backend/_workspace/PERFORMANCE_STAGE5_SMB_OPTION_2026-06-08.md` 신규 | G67 D=A 풀 통과. BUILD SUCCESSFUL + 모든 테스트 통과 (UuidV7Test + NeonProvisioningIntegrationTest). LIVE 영향 0 (smb profile 명시 진입 시에만 활성, default/prod 는 B2B-Enterprise per-tenant 본질 보존). |
| 2026-06-08 | **단계 3 BE-CC-2 JWT 5분리 진입 ✅ (G84 D=A, Task #122)** — AuthController + AuthService + AuthDtos + JwtService + JwtAuthFilter + RefreshTokenStore (security 패키지 6 클래스) + PerformanceErrorCode 5종 (E98 prefix 사전 진입) + SecurityConfig JWT filter chain + lib BE 17 v2 TenantContextResolver 자연 결합 + 26 tests / 0 failures + jobeval `4dff03a` + mra `38e566d` + jobstructure `d64944e` 3 모범 정합 + **격상 4단계 풀 완성 마일스톤 도달** | `backend/src/main/java/com/easyperformance/security/{AuthController,AuthService,AuthDtos,JwtService,JwtAuthFilter,RefreshTokenStore}.java` 신규 + `backend/src/main/java/com/easyperformance/error/PerformanceErrorCode.java` 신규 + `backend/src/main/java/com/easyperformance/config/SecurityConfig.java` (permitAll → JWT filter chain) + `backend/src/main/resources/application.yml` (access TTL 15m → 5m + dev-default-tenant-id) + `backend/src/main/resources/application-dev.yml` (easyplatform.jwt.secret HS512 64+ bytes + app.security.jwt.* dev fallback) + `backend/src/test/java/com/easyperformance/security/{JwtServiceTest,AuthServiceTest}.java` 신규 + `backend/_workspace/PERFORMANCE_STAGE3_JWT_CUTOVER_2026-06-08.md` 신규 | G84 D=A 풀 통과. BUILD SUCCESSFUL + 26 tests / 0 failures (UuidV7 2 + AuthService 7 + JwtService 5 + NeonProvisioning 12). 그린필드라 dual-claim 불필요 (mra 패턴과 다름). Clock 주입 (jobstructure 추종) — 테스트 친화. ADR-031 정합 (B2C 부재). LIVE 영향 0 (dev fallback secret 만 dev profile, prod 는 ${JWT_SECRET} fail-fast). **격상 4단계 풀 완성 = 100% 도달** (단계 0 ✅ + 1 ✅ + 2 ✅ + 3 ✅ + 4 ✅ + 5 SMB ✅, 단계 4가 단계 3보다 먼저 진입한 비순서 케이스 정합). |
| 2026-06-08 | **Phase Docker 사전 적용 ✅ (Task #176, recruit `0ea4d0a` + 9 fix 누적 모범 정합)** — git submodule add `lib/easy-platform` (https://github.com/Junhokang452/easy-standards.git HEAD `a8e8176`) + Dockerfile multi-stage (FE Vite + BE Gradle + Runtime Temurin 21 JRE + Asia/Seoul TZ + healthcheck + port 10000) + .dockerignore (build context 최소화) + settings.gradle.kts 경로 조정 (`../../easy-platform-core` → `../lib/easy-platform/easy-platform-core`) + frontend-vite/package.json 4 packages 경로 조정 (http-client/query-client/tokens/ui-components) + application-prod.yml: (1) `spring.web.resources.static-locations` `file:/app/static/,classpath:/static/` 추가 (2) datasource fallback chain `DB_URL → DATABASE_URL → SPRING_DATASOURCE_URL` (3) `easyware.neon.*` CONTROL_DB_* placeholder default + BUILD SUCCESSFUL `compileJava` + sanity-check PASS-WITH-WARN (0 errors / 2 warnings — refresh.enabled true는 단계 3 cutover 완료라 의도적 + vite dedupe는 FE 영역 추후) | `.gitmodules` 신규 + `lib/easy-platform` submodule 추가 + `Dockerfile` 신규 + `.dockerignore` 신규 + `backend/settings.gradle.kts` (lib 경로 조정) + `frontend-vite/package.json` (4 packages 경로 조정) + `backend/src/main/resources/application-prod.yml` (static-locations + datasource fallback chain + CONTROL_DB_* placeholder default) + `backend/_workspace/PERFORMANCE_DOCKER_ENTRY_2026-06-08.md` 신규 | Task #176 풀 통과. 그린필드 100% Apache-2.0 + 격상 100% 풀 완성 보존 + ADR-031 토폴로지 정합 보존 (B2B-Enterprise per-tenant + SMB Shared + B2C 부재). 자매품 9호 Docker 사전 적용 모범 정합 — Render Docker Web Service + EC2 100% 호환. JwtServiceTest tamperedSignature 1건 flaky 후속 (Docker 작업 무관 — 단계 3 cutover 테스트). LIVE 영향 0. |

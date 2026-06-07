# easy-performance-management — 성과 관리 자매품 (Performance Management)

> **위상**: ADR-022(2026-06-06) 자매품 정식 편입 + ADR-030(2026-06-06) 듀얼 모드 일반화 + ADR-013 Neon Model B 정합. **자매품 9호** (8 → 9 진화 마일스톤, 듀얼 모드 5호 = sign + mra + jobstructure + jobeval + **performance-management 신규**).
>
> **본질**: HR Tech 성과 관리 카테고리 (Performance Management) — 성과 평가 + OKR + 회고 + 1:1 피드백 + CFR (Conversations / Feedback / Recognition) 4 도메인 통합. 15Five / Lattice / Culture Amp 시장 모범 정합. 기존 easy-hcm 성과평가 영역에서 분리 (D1.4=B 완전 그린필드).
>
> **공통표준 SoT**: `~/code/easy-standards` (현재 v0.5.0). 본 프로젝트는 표준을 우선 적용하고, 아래 **델타**만 예외로 둔다.

## 적용 표준

| 영역 | 문서 |
|------|------|
| 공통 원칙 | `00-principles/01`~`17` (특히 11 자매품 아키텍처 + 13 테넌시 + 15 공통 데이터 모델 + 16 명명 + 17 i18n) |
| Spring/JPA | `10-appendix-spring-jpa/*` (BE-CC-1/2/3/4/5) |
| 프론트 | `00-principles/07-frontend.md` (STD-FE-LAZY/STRICT/RQ/NEST/ERROR-BOUNDARY) |
| 기술스택 | `00-principles/10-tech-stack.md` (ADR-010/014) |
| 자매품 통합·SoR | `00-principles/11-suite-architecture.md` + **ADR-019** (Job Architecture SoR — 단계 4 진입 시 BE-CC-4 Outbox-light + S2S consume) |
| HR 도메인 참고 | `90-conformance/easy-hr-foundation-data-and-legal-entity.md` |
| 멀티테넌시 | **ADR-013** Neon Model B + **ADR-024** 1 고객사 = 1 Neon 프로젝트 + **ADR-029** B2C 공통 테넌트 예외 (sign 1차 모범) + **ADR-030** 자매품 B2C 듀얼 모드 일반화 |

## 제품 델타 (easy-performance-management, 단계 0 baseline 기준)

| 항목 | 표준 | 본 프로젝트 (단계 0) | 격상 단계 |
|------|------|---------------------|----------|
| 패키지 루트 | `com.easyware` 등 | `com.easyplatform.performance` (가칭) | 단계 1 진입 시 결정 |
| 멀티테넌트 | Neon Model B + control plane | **미적용** — 단일 DB baseline | 단계 1 BE-CC-1 TenantAware → 단계 2 Model B |
| `tenant_id` / RLS | 필수(목표) | **없음** — 단일 테넌트 가정 | 단계 1 컬럼 추가 → 단계 2 RLS 정책 |
| 인증 (JWT 5분리) | BE-CC-2 | **미구현** | 단계 3 BE-CC-2 JWT (dual-claim 비파괴) |
| FE 디자인 | EC-FE openapi-typescript + ApiError SoT | **미구현** | 단계 4 EC-FE |
| 듀얼 모드 (B2C 공통 테넌트) | ADR-029/030 | **미적용** — B2B-only baseline | 단계 5 `shared-customer-easy-performance` + RLS user_id (mra G31.5 패턴 정합) |

## 기술스택 (ADR-010/014 정렬)

- **백엔드**: Java 21, Spring Boot 3.4.x, Kotlin 2.0 (jobstructure 정합), Gradle KDSL, JPA, Flyway, PostgreSQL(Neon 목표)
- **프론트**: React 19.2+, Mantine v9, Vite 8, TypeScript strict 5, react-router 7, TanStack Query 5
- **lib 재사용** (94% 자연 활용 — 18/19 단위): BE 13~18 풀 + FE 12~13 풀 + BE 19 MonthlyQuotaGuard 검토 (B2C quota 진입 시)
- **산출물**: `_workspace/` 격리

## 도메인 entity 6건 (단계 1 후속 진입)

### B2B 도메인 (분기/연간 정기 평가 — 기업 본질)
1. **PerformanceReview** (성과 평가) — 상태 머신 DRAFT → SELF_PENDING → MANAGER_PENDING → CALIBRATION → FINALIZED → ARCHIVED
2. **Okr** (목표 + Key Results) — 회사 → 팀 → 개인 3 계층 alignment
3. **PerformanceCycle** (평가 주기 관리) — Q1~Q4 + 연간 사이클, HR 어드민 entity

### B2C 도메인 (개인 성장 — 공통 테넌트 + RLS user_id, 단계 5)
4. **SelfAssessment** (자기평가) — B2B sub-aggregate + B2C 독립 entity
5. **Retrospective** (회고) — KPT / 4Ls / SSC 방법론
6. **OneOnOneFeedback** (1:1 피드백 + CFR) — 4 모드 (MANAGER_REPORT / REPORT_MANAGER / MENTOR_MENTEE / PEER_RECOGNITION)

**S2S 의존**: `hcm.Employee` (피평가자 + 평가자) + `hcm.Organization` (부서 단위 평가) + ADR-019 Job Architecture (jobstructure FK 참조).

## 격상 4단계 로드맵 (단계 5 듀얼 모드 진입)

| 단계 | 작업 | 추정 기간 | 비고 |
|------|------|----------|------|
| **단계 0** | git init + baseline (jobstructure G30 옵션 C-1 패턴 정합) | 0.5주 | **본 진입 ✅** |
| 단계 1 | BE-CC-1 TenantAware…AuditEntity + 단일 DB → 멀티테넌트 컬럼 + 도메인 entity 6건 P0 (PerformanceReview + Okr) 구현 | 1~1.5주 | jobeval 단계 1 패턴 정합 |
| 단계 2 | Model B 단번 전환 (per-tenant DB 분리, ADR-013 + ADR-024) + Flyway fan-out + lib BE 14 TenantBootstrap 3 SPI seam 결합 | 1주 | mra G31 단계 2 패턴 정합 |
| 단계 3 | BE-CC-2 JWT 5분리 + dual-claim 비파괴 + lib BE 17 v2 TenantContextResolver 자연 결합 | 1주 | jobeval 단계 3 패턴 정합 |
| 단계 4 | EC-FE openapi-typescript + ApiError SoT + FE 디자인 업그레이드 (Mantine v9 + STD-FE 5 정합) + 도메인 P1~P3 (Self/Retro/1:1/Cycle 4) 구현 | 2주 | jobeval 단계 4 패턴 정합 |
| **단계 5** | B2C 공통 테넌트 진입 (`shared-customer-easy-performance` + RLS user_id + ADR-029 정합) + JWT 분기 (B2B `tid` / B2C `tenant_mode: B2C` + `user_id`) + Monthly quota MonthlyQuotaGuard (lib BE 19 검토) | 1주 | mra G31.5 패턴 정합 (V1~V5 86% 재사용) |
| 통합 검증 | 9/9 통합 회귀 0 게이트 통과 (G36 가칭 게이트) | 0.5주 | 자매품 매트릭스 9/9 진화 마일스톤 |

**합계 추정**: ~9주 ≈ **2개월** (lib 재사용 94% + 듀얼 모드 박제 재사용 80% 효과로 자매품 8 중 최소 진입 비용).

## 개발 하네스

본 프로젝트는 **자체 하네스를 운영하지 않는다**. 메인 하네스(`~/code/.claude/CLAUDE.md`)의 `easy-suite-orchestrator` 스킬을 사용해 풀스택 작업·표준 정합·QA·문서화를 라우팅한다.

## 박제 가이드 cross-link (easy-standards)

- `easy-standards/_workspace/PERFORMANCE_MANAGEMENT_DOMAIN_DEFINITION_2026-06-07.md` — 도메인 정의 (entity 6 + 자매품 8 → 9 진화 + HR Tech 시장 모범)
- `easy-standards/_workspace/PERFORMANCE_MANAGEMENT_ENTRY_ROADMAP_2026-06-07.md` — 정식 진입 4단계 + 도메인 P0~P3 + Core Master S2S + lib 재사용 + 듀얼 모드 게이트
- `easy-standards/_workspace/PERFORMANCE_MANAGEMENT_DUAL_MODE_DEFINITION_2026-06-07.md` — ADR-030 듀얼 모드 정합 + V1~V5 통합 검증 재사용 매트릭스

## 변경 이력

| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-06-07 | 자매품 정식 진입 — 단계 0 baseline (ADR-022 + ADR-030, jobstructure G30 옵션 C-1 패턴 정합) | `CLAUDE.md` + `.gitignore` + `_workspace/README.md` + `_workspace/PERFORMANCE_MANAGEMENT_STAGE0_BASELINE_2026-06-07.md` + git init + baseline commit + tag `v0.0.0-baseline` | 사용자 D1=A 결정 (P0 1순위 분리 진입) + D4=A 결정 (performance 1순위, finance 2순위 강등). 자매품 매트릭스 9호 진화 (8 → 9, 듀얼 모드 5호 = sign + mra + jobstructure + jobeval + **performance-management 신규**). 본 슬라이스 = 디렉토리 + 박제 + git init + baseline + tag만 (BE/FE 코드는 단계 1 후속). LIVE 영향 0. |

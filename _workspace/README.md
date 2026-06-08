# `_workspace/` — easy-performance-management 격리 박제 디렉토리

본 디렉토리는 easy-performance-management 자매품의 **격리 박제 산출물** 보관소다. 실 코드(BE/FE)는 단계 1 후속 진입 시 본 디렉토리 외부 (`backend/`, `frontend/`)에 배치한다.

> **토폴로지 정정 (2026-06-08, Task #98)**: 사용자 명시 "easy-performance-management는 SMB는 있지만 B2C는 없음". 듀얼 모드 5호 박제 폐기 → **B2B-Enterprise per-tenant + SMB Shared** (ADR-031 정합, ware/hcm/recruit 패턴). 상세: `PERFORMANCE_TOPOLOGY_CORRECTION_2026-06-08.md`.

## 박제 가이드 cross-link (easy-standards SoT)

본 자매품 진입의 상위 박제 가이드는 `easy-standards/_workspace/`에 정주한다.

| 박제 파일 | 역할 |
|----------|------|
| [`PERFORMANCE_MANAGEMENT_DOMAIN_DEFINITION_2026-06-07.md`](../../easy-standards/_workspace/PERFORMANCE_MANAGEMENT_DOMAIN_DEFINITION_2026-06-07.md) | 도메인 entity 4건 정의 + 자매품 8 → 9 진화 매트릭스 + HR Tech 시장 모범 (15Five/Lattice/Culture Amp) + 진입 비용 분석 |
| [`PERFORMANCE_MANAGEMENT_ENTRY_ROADMAP_2026-06-07.md`](../../easy-standards/_workspace/PERFORMANCE_MANAGEMENT_ENTRY_ROADMAP_2026-06-07.md) | 정식 진입 4단계 로드맵 + 도메인 P0~P3 + Core Master S2S 의존 + lib 재사용 매트릭스 (~~듀얼 모드 게이트~~ 정정 후 SMB 게이트) |
| ~~[`PERFORMANCE_MANAGEMENT_DUAL_MODE_DEFINITION_2026-06-07.md`](../../easy-standards/_workspace/PERFORMANCE_MANAGEMENT_DUAL_MODE_DEFINITION_2026-06-07.md)~~ | **폐기** (듀얼 모드 5호 박제 폐기 — 본 정정 2026-06-08, Task #98) |
| [`ADR_031_SIBLING_9_TOPOLOGY_MATRIX_2026-06-07.md`](../../easy-standards/_workspace/ADR_031_SIBLING_9_TOPOLOGY_MATRIX_2026-06-07.md) | 자매품 9 × 3 토폴로지 적용성 매트릭스 (performance cell 정정 후 B2B-Enterprise + SMB 옵션) |

## 단계 0 ~ 5 진입 흐름 (2026-06-08 정정)

```
단계 0 (baseline)  ✅ `58bf09d` (2026-06-07)
  │
  ├─ git init + .gitignore + CLAUDE.md + _workspace/ + baseline commit + tag v0.0.0-baseline
  │
단계 1 (BE-CC-1 TenantAware)  ✅ `b83acac` (2026-06-08)
  │
  ├─ Spring Boot 3.4.5 + Gradle KDSL + easy-platform-core composite
  ├─ 4 도메인 (SelfEvaluation/PersonalOkr/ReflectionJournal/MentorFeedback) 스캐폴드
  ├─ Flyway V20260608_001 baseline + tenant_id 선두 복합 인덱스
  ├─ lib BE 17 v2 TenantContextResolver thin adapter
  ├─ ADR-026 명명 표준 풀 정합
  ├─ 42 파일 +2762 lines + BUILD SUCCESSFUL + UuidV7Test 통과 (G46 풀 통과)
  │
단계 2 (Model B 단번 전환)  ⏸ G65 사용자 결정 대기
  │
  ├─ per-tenant DB 분리 (ADR-013 + ADR-024)
  ├─ Flyway fan-out + lib BE 14 TenantBootstrap 3 SPI seam 결합
  │   (jobeval 단계 2 `bd46134` JobEvalTenantBootstrapConfig 패턴 정합)
  │
단계 3 (BE-CC-2 JWT 5분리)
  │
  ├─ JWT 5분리 (dual-claim 불필요, B2C 부재)
  ├─ lib BE 17 v2 TenantContextResolver 자연 결합
  │
단계 4 (EC-FE)
  │
  ├─ openapi-typescript + ApiError SoT
  ├─ FE 디자인 업그레이드 (Mantine v9 + STD-FE 5 정합)
  ├─ FE 도메인 4 화면 구현
  │
단계 5 (SMB Shared 진입 옵션 — 2026-06-08 정정, 이전 듀얼 모드 5호 박제 폐기)
  │
  ├─ shared-smb-easy-performance-management Neon 프로젝트
  ├─ RLS tenant_id 격리 (ware/hcm/recruit 패턴 정합)
  ├─ JWT 단일 (B2B-Enterprise + SMB Shared 공통 tid claim)
  ├─ 결제 단일 (SMB plan)
  └─ PIPA 정합 SMB 환경 분석 후 진입 (G67 신규 후보 게이트)
```

## 본 디렉토리 박제 인덱스

| 박제 파일 | 역할 |
|----------|------|
| `README.md` | 본 파일 (격리 박제 + cross-link + 단계 0~5 흐름 정정) |
| `PERFORMANCE_MANAGEMENT_STAGE0_BASELINE_2026-06-07.md` | 단계 0 진입 박제 (보존, 듀얼 모드 박제는 본 README + TOPOLOGY_CORRECTION 박제로 정정 코멘트) |
| `PERFORMANCE_MANAGEMENT_STAGE1_CUTOVER_2026-06-08.md` | 단계 1 BE-CC-1 cutover 박제 (보존, 단계 5 정의는 본 README + TOPOLOGY_CORRECTION 박제로 정정) |
| `PERFORMANCE_TOPOLOGY_CORRECTION_2026-06-08.md` | **2026-06-08 정정 박제** (듀얼 모드 5호 폐기 → B2B-Enterprise + SMB Shared) |

## 운영 원칙 (격리 박제)

- 본 `_workspace/`는 **격리 박제**다. 실 코드 (BE Spring + FE Vite) 진입은 단계 1 후속이며, 본 디렉토리 외부에 배치한다.
- 박제 산출물은 후속 부분 재실행·감사 추적·사용자 결정 사전 가이드를 위해 보존한다.
- 본 디렉토리 갱신은 메인 하네스(`~/code/.claude/CLAUDE.md`)의 `easy-suite-orchestrator` + `docs-curator` + `tenancy-architect` + `standards-guardian` 에이전트가 책임진다.

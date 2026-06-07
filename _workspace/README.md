# `_workspace/` — easy-performance-management 격리 박제 디렉토리

본 디렉토리는 easy-performance-management 자매품의 **격리 박제 산출물** 보관소다. 실 코드(BE/FE)는 단계 1 후속 진입 시 본 디렉토리 외부 (`backend/`, `frontend/`)에 배치한다.

## 박제 가이드 cross-link (easy-standards SoT)

본 자매품 진입의 상위 박제 가이드 3건은 `easy-standards/_workspace/`에 정주한다.

| 박제 파일 | 역할 |
|----------|------|
| [`PERFORMANCE_MANAGEMENT_DOMAIN_DEFINITION_2026-06-07.md`](../../easy-standards/_workspace/PERFORMANCE_MANAGEMENT_DOMAIN_DEFINITION_2026-06-07.md) | 도메인 entity 6건 정의 (B2B 3 + B2C 3) + 자매품 8 → 9 진화 매트릭스 + HR Tech 시장 모범 (15Five/Lattice/Culture Amp) + 진입 비용 분석 + 사용자 결정 양식 D1.1~D1.5 |
| [`PERFORMANCE_MANAGEMENT_ENTRY_ROADMAP_2026-06-07.md`](../../easy-standards/_workspace/PERFORMANCE_MANAGEMENT_ENTRY_ROADMAP_2026-06-07.md) | 정식 진입 4단계 로드맵 + 도메인 P0~P3 + Core Master S2S 의존 + lib 재사용 매트릭스 + 듀얼 모드 게이트 |
| [`PERFORMANCE_MANAGEMENT_DUAL_MODE_DEFINITION_2026-06-07.md`](../../easy-standards/_workspace/PERFORMANCE_MANAGEMENT_DUAL_MODE_DEFINITION_2026-06-07.md) | ADR-030 듀얼 모드 4 정정 (sign + mra + jobstructure + performance, jobeval D2=B 정합) + V1~V5 통합 검증 재사용 매트릭스 (mra G31.5 패턴 86% 재사용) |

## 단계 0 ~ 5 진입 흐름

```
단계 0 (baseline)  ← 본 슬라이스 ✅
  │
  ├─ git init + .gitignore + CLAUDE.md + _workspace/ + baseline commit + tag v0.0.0-baseline
  │
단계 1 (BE-CC-1 TenantAware)
  │
  ├─ 단일 DB → 멀티테넌트 컬럼 (tenant_id) + BaseEntity 4 (Tenant/Audit/SoftDelete/UuidV7)
  ├─ 도메인 entity 6 P0 구현 (PerformanceReview + Okr)
  ├─ Flyway V1__init.sql baseline
  │
단계 2 (Model B 단번 전환)
  │
  ├─ per-tenant DB 분리 (ADR-013 + ADR-024)
  ├─ Flyway fan-out + lib BE 14 TenantBootstrap 3 SPI seam 결합
  │
단계 3 (BE-CC-2 JWT 5분리)
  │
  ├─ JWT 5분리 + dual-claim 비파괴
  ├─ lib BE 17 v2 TenantContextResolver 자연 결합
  │
단계 4 (EC-FE)
  │
  ├─ openapi-typescript + ApiError SoT
  ├─ FE 디자인 업그레이드 (Mantine v9 + STD-FE 5 정합)
  ├─ 도메인 P1~P3 구현 (Self/Retro/1:1/Cycle 4)
  │
단계 5 (듀얼 모드 진입)
  │
  ├─ B2C 공통 테넌트 `shared-customer-easy-performance` + RLS user_id (ADR-029)
  ├─ JWT 분기 (B2B `tid` / B2C `tenant_mode: B2C` + `user_id`)
  ├─ Monthly quota MonthlyQuotaGuard (lib BE 19 검토)
  └─ mra G31.5 패턴 정합 (V1~V5 86% 재사용)
```

## 본 디렉토리 박제 인덱스

| 박제 파일 | 역할 |
|----------|------|
| `README.md` | 본 파일 (격리 박제 + cross-link + 단계 0~5 흐름) |
| `PERFORMANCE_MANAGEMENT_STAGE0_BASELINE_2026-06-07.md` | 단계 0 진입 박제 (사용자 D1=A 결정 + jobstructure G30 옵션 C-1 패턴 + 자매품 9 마일스톤 + 단계 1 사전 + lib 재사용 매트릭스 + 단계 5 mra G31.5 패턴 정합) |

## 운영 원칙 (격리 박제)

- 본 `_workspace/`는 **격리 박제**다. 실 코드 (BE Spring + FE Vite) 진입은 단계 1 후속이며, 본 디렉토리 외부에 배치한다.
- 박제 산출물은 후속 부분 재실행·감사 추적·사용자 결정 사전 가이드를 위해 보존한다.
- 본 디렉토리 갱신은 메인 하네스(`~/code/.claude/CLAUDE.md`)의 `easy-suite-orchestrator` + `docs-curator` + `tenancy-architect` + `standards-guardian` 에이전트가 책임진다.

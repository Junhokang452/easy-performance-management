# easy-performance-management SMB Shared 진입 박제 가이드 (2026-06-08)

> **Status**: 박제 (실 진입 대기 — 사용자 G67 결정 + 자매품 8/9 풀 안정 + PIPA 정합 분석 후)
> **LIVE 영향**: 0 (가이드 단계 + 명시 진입 시에만 활성)
> **상위 결정**: G67 D=A 확정 (2026-06-08, Task #105)
> **연계 게이트**: G44 LIVE 진입 게이트 누적 안정성 후 (ware/hcm SMB 진입 우선)
> **자매품 정합**: ware (`WARE_SMB_ENTRY_GUIDE_2026-06-07.md`) + hcm (`HCM_SMB_ENTRY_GUIDE_2026-06-07.md`) + recruit (`RECRUIT_SMB_ENTRY_GUIDE_2026-06-07.md`) 패턴 정합

---

## §1 SMB 진입 컨텍스트

### 1.1 사용자 G67 D=A 결정 확정

**결정 양식**:
- **G67-D** = **A (SMB Shared DB + RLS tenant_id 토폴로지 옵션 추가)**
- 결정 시점: 2026-06-08
- 결정 효력: performance **B2B-Enterprise per-tenant DB 본질 유지** + **SMB 옵션 토폴로지 추가**

**대안 평가**:
| 옵션 | 내용 | 채택 여부 |
|------|------|----------|
| **A** | SMB Shared DB + RLS tenant_id 토폴로지 추가 | ✅ 채택 |
| B | B2B-Enterprise per-tenant DB만 유지 (SMB도 per-tenant DB 강제) | ❌ 비용 효율 미흡 |
| C | SMB 별도 제품 분기 (`easy-performance-smb`) | ❌ 코드 분기 부담 |

### 1.2 ADR-031 자매품 9 × 3 토폴로지 매트릭스 (performance cell 정합)

| 자매품 | B2B-Enterprise per-tenant DB | SMB Shared DB | B2C 공통 테넌트 |
|--------|------------------------------|---------------|---------------|
| ware | ✅ 본질 (LIVE EC2) | ✅ 옵션 (G45 후보) | — |
| hcm | ✅ 본질 (LIVE Render) | ✅ 옵션 (PIPA 정합 후) | — |
| time | ✅ 본질 | ❌ 보류 (4 사유) | — |
| recruit | ✅ 본질 | ✅ 옵션 (AGPL 전환 후) | — |
| **performance** | **✅ 본질 (단계 2 `bd46134` 정합)** | **✅ 옵션 (본 가이드, 단계 5 G67 D=A)** | **— (ADR-031 정정 2026-06-08 Task #98)** |
| mra | ✅ 듀얼 본질 | — | ✅ 듀얼 (ADR-030) |
| jobstructure | ✅ 듀얼 본질 | — | ✅ 듀얼 (ADR-030) |
| jobeval | ✅ 듀얼 본질 | — | ✅ 듀얼 (ADR-030) |
| sign | ✅ B2B (ADR-029) | — | ✅ B2C (ADR-029) |

**performance 토폴로지 의미**:
- **B2B-Enterprise (본질)**: 대기업/중견기업 — 데이터 주권·격리 강조, per-tenant DB (`easyrecord-performance-<tenant>`)
- **SMB Shared (옵션)**: 소규모 회사 5~50명 — cost optimization, Shared DB + RLS tenant_id

### 1.3 사용 시나리오

| 시나리오 | 대상 | 토폴로지 | 비용 (참고) |
|---------|------|---------|------|
| **대기업 그룹사** | 1000+ 명, 데이터 주권 | B2B-Enterprise per-tenant DB | $25/월 (Neon 프로젝트) |
| **중견기업** | 100~1000명, 컴플라이언스 | B2B-Enterprise per-tenant DB | $25/월 |
| **SMB 회사** | 5~50명, cost 민감 | SMB Shared DB + RLS | **$5/월 (회사당)** |
| **스타트업 트라이얼** | 5명 미만, free | SMB Shared DB (free plan) | $0 |

**performance 본질** (ADR-031 정정 2026-06-08, Task #98):
- 자기평가 (Self Evaluation) — 분기/연간 정기 평가
- 개인 OKR (Personal OKR) — 분기 OKR + 진척률
- 회고 저널 (Reflection Journal) — KPT/4Ls/SSC
- 멘토 피드백 (Mentor Feedback) — 1:1 피드백 + GROWTH/RECOGNITION/COACHING/CONVERSATION

**B2C 부재 근거**: 도메인 본질 = 기업 워크플로우 + HR SoR + 매니저-팀원 1:1 + 성과 사이클. B2C 개인 자기진단은 도메인 본질에서 제외.

---

## §2 토폴로지 정의

### 2.1 신규 Neon 프로젝트 + DB

```
Neon 프로젝트: shared-smb-easy-performance-management
└── DB: easyshare_performance_management (단일 공유 DB)
    ├── tenant: company-a (RLS 격리)
    ├── tenant: company-b (RLS 격리)
    ├── tenant: company-c (RLS 격리)
    └── ... (1 Neon 프로젝트 + 1 DB 공유)
```

**대조: B2B-Enterprise** (단계 2 `bd46134` 정합):
```
Neon 프로젝트: easyrecord-performance-<company-x>  (회사당 1개)
└── DB: easyrecord_performance (per-tenant 격리)
```

### 2.2 RLS tenant_id 격리

**모든 4 도메인 테이블에 tenant_id 컬럼 + RLS 정책** (`db/smb/V20260608_001__rls_policy_smb.sql`):

```sql
-- 예시: self_evaluation 테이블 (4 테이블 동일 패턴)
ALTER TABLE self_evaluation ENABLE ROW LEVEL SECURITY;
ALTER TABLE self_evaluation FORCE ROW LEVEL SECURITY;  -- 관리자도 RLS 적용

CREATE POLICY self_evaluation_tenant_isolation
    ON self_evaluation
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

**RLS 컨텍스트 설정 (Spring AOP, lib BE 18 RlsTenantAspect 위임)**:
```java
// lib RlsTenantAspect (autoconfig OFF 기본, smb profile 명시 ON)
// @Transactional 진입 시 TX-bound 커넥션에 set_config 주입
// PgBouncer transaction mode 정합 (필터/인터셉터 호출 금지 — 규칙 #7)
```

### 2.3 JWT 클레임 확장 (단계 3 BE-CC-2 진입 시 본격 활용)

**기존 (B2B-Enterprise)**:
```json
{
  "sub": "user-123",
  "tid": "company-x-uuid",
  "tenant_mode": "B2B"
}
```

**신규 (SMB Shared DB)**:
```json
{
  "sub": "user-456",
  "tid": "company-y-uuid",
  "tenant_mode": "SMB",
  "smb_pool": "shared-smb-easy-performance-management"
}
```

**라우팅 분기 (DataSourceConfig)**:
| `tenant_mode` | DataSource 라우팅 | RLS 사용 |
|---------------|------------------|---------|
| `B2B` | per-tenant Neon 프로젝트 (lookup) | ❌ (물리 격리) |
| `SMB` | `shared-smb-easy-performance-management` 단일 DB | ✅ (논리 격리) |

---

## §3 도메인 entity SMB 정합 매트릭스

### 3.1 4 entity × tenant_id RLS

| Entity | tenant_id 컬럼 | RLS 정책 | 인덱스 | 비고 |
|--------|---------------|---------|--------|------|
| **SelfEvaluation** | UUID NOT NULL ✅ | ✅ tenant isolation | `(tenant_id, employee_id)` / `(tenant_id, cycle_id)` / `(tenant_id, status)` | 단계 1 ✅ |
| **PersonalOkr** | UUID NOT NULL ✅ | ✅ tenant isolation | `(tenant_id, employee_id)` / `(tenant_id, status)` / `(tenant_id, period_end)` | 단계 1 ✅ |
| **ReflectionJournal** | UUID NOT NULL ✅ | ✅ tenant isolation | `(tenant_id, employee_id)` / `(tenant_id, reflection_date)` | 단계 1 ✅ |
| **MentorFeedback** | UUID NOT NULL ✅ | ✅ tenant isolation | `(tenant_id, mentee_id)` / `(tenant_id, mentor_id)` / `(tenant_id, feedback_date)` | 단계 1 ✅ |

**핵심 정합 사항**:
- 단계 1 BE-CC-1 cutover (`b83acac`)에서 4 도메인 모두 `tenant_id UUID NOT NULL` + 선두 복합 인덱스 풀 적용 완료.
- SMB Shared 진입 시 추가 작업 = RLS 정책 SQL 만 (스키마 변경 0).

### 3.2 RLS 정책 SQL 표준

**4 SMB 테이블 일괄 적용** (`db/smb/V20260608_001__rls_policy_smb.sql`):
- `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` (관리자도 RLS 적용)
- `CREATE POLICY {table}_tenant_isolation` (USING + WITH CHECK)
- `current_setting('app.tenant_id', true)::uuid` 정합 (lib BE 18 RlsTenantAspect set_config)

---

## §4 진입 게이트 5건

### G1 — Flyway SMB RLS 정책 마이그 적용

**파일**: `db/smb/V20260608_001__rls_policy_smb.sql` (✅ 본 슬라이스 신설)

**작업**:
- 4 도메인 테이블 RLS 활성화 + FORCE
- `{table}_tenant_isolation` 정책 적용
- `performance_stage_marker` STAGE_5_SMB_SHARED 가시화

**검증**:
- `psql` 직접 RLS 우회 시도 → 차단 확인 (FORCE ROW LEVEL SECURITY)
- `SET app.tenant_id = '<tenant-a>'` 후 다른 tenant 행 조회 → 0건
- `SET app.tenant_id = '<tenant-b>'` 후 tenant-b 행만 조회 확인

**완료 조건**: 4 entity 모두 RLS 풀 적용 + tenant 격리 회귀 0

---

### G2 — JWT 클레임 + DataSource 라우팅 (단계 3 BE-CC-2 통합)

**작업**:
- JWT 발급 시 `tenant_mode` 클레임 추가 (B2B / SMB)
- DataSourceConfig 라우팅 분기 (B2B per-tenant lookup / SMB shared 단일 DB)
- lib BE 17 v2 TenantContextResolver 정합

**검증**:
- B2B JWT → per-tenant DataSource 라우팅 확인
- SMB JWT → shared DataSource 라우팅 확인
- 다른 tenant 데이터 접근 시도 → RLS 차단

**완료 조건**: 단계 3 BE-CC-2 진입 시 풀 통합 + 토큰 호환성 회귀 0

---

### G3 — control plane SMB tenant 등록 흐름

**작업**:
- `platform_tenant` 테이블에 `tenant_mode VARCHAR(20)` 컬럼 추가 (`'B2B' | 'SMB'`)
- SMB 가입 endpoint (회사 정보 + 도메인 + 사용자 5명 미만)
- SMB tenant 생성 시 자동 `shared-smb-easy-performance-management` 프로젝트 등록

**검증**:
- SMB 가입 flow E2E 테스트
- tenant_mode='SMB' tenant 가입 후 라우팅 확인
- 다른 tenant 데이터 격리 검증

**완료 조건**: SMB onboarding flow 완성

---

### G4 — dev 검증

**작업**:
- 테스트 테넌트 5건 SMB pool 등록
- 각 테넌트 사용자 3명씩 시드
- 4 도메인 (SelfEvaluation / PersonalOkr / ReflectionJournal / MentorFeedback) CRUD 테스트
- 도메인 매핑 5건 등록 및 라우팅 테스트

**검증 시나리오**:
- 시나리오 1: tenant-a 사용자 → tenant-a 자기평가 작성/조회
- 시나리오 2: tenant-a 사용자 → tenant-b 자기평가 조회 시도 → 차단
- 시나리오 3: tenant-a OKR 진척률 업데이트 → tenant-b 영향 0
- 시나리오 4: tenant-a 멘토 피드백 → tenant-b 멘티 접근 시도 → 차단
- 시나리오 5: tenant 5건 동시 운영 → 데이터 격리 회귀 0

**완료 조건**: 시나리오 5건 모두 통과 + RLS 우회 시도 0건 성공

---

### G5 — staging 검증 (PIPA 정합 분석 포함)

**작업**:
- 실제 회사 5건 staging 환경 진입 (베타 테스터)
- 24h 운영 + 회귀 0 가드
- 성과 데이터 (자기평가 + OKR + 회고 + 멘토 피드백) 모니터링
- DB 풀 + RLS 적용도 + Outbox backlog 모니터링
- **PIPA 정합 분석**: 성과 평가 데이터는 인사 평가 데이터 (민감 정보) → SMB shared 환경 적합성 평가

**모니터링 메트릭**:
- 4 도메인 CRUD 처리량 (req/sec)
- DB 풀 사용률 (목표 < 80%, 30 max-pool-size)
- RLS 적용도 (목표 100%)
- HTTP 5xx 에러율 (목표 < 0.1%)
- tenant 격리 위반 시도 (목표 0)

**완료 조건**:
- 24h 회귀 0 + 메트릭 정상
- 베타 테스터 5건 모두 만족 (NPS > 7)
- PIPA 정합 분석 통과 (인사 평가 민감 데이터 SMB shared 적합성 확인)

---

## §5 사용자 결정 양식 (후속)

### D-performance-smb-stage1: SMB 진입 시점

**선택지**:
- **옵션 A**: 자매품 8/9 풀 안정 + jobeval 단계 4 완성 + ware/hcm SMB 진입 누적 안정성 후 (보수, 권고)
- **옵션 B**: 단계 4 EC-FE 완성 직후 즉시 SMB 진입 (병렬, 빠른 출시)
- **옵션 C**: 별도 sprint SMB 진입 (분리, 안전)

| 옵션 | 장점 | 단점 | 권장도 |
|------|------|------|--------|
| **A** | LIVE 안정화 우선, 위험 최소 | 출시 늦음 (3~6개월) | ⭐⭐⭐ 권장 |
| B | 출시 빠름, 단계 4 정합 | LIVE + SMB 동시 위험 | ⭐⭐ |
| C | 완전 분리, 영향 0 | 출시 가장 늦음 (6개월+) | ⭐ |

**권고**: **옵션 A** — 자매품 8/9 풀 안정 + ware/hcm SMB 진입 누적 안정성 확보 후 + PIPA 정합 분석 후 SMB 진입.

---

### D-performance-smb-pipa: PIPA 정합

**선택지**:
- **옵션 A**: SMB Shared 환경에 성과 평가 데이터 풀 진입 (cost 우선)
- **옵션 B**: SMB Shared 환경은 OKR + 회고만 (자기평가/멘토 피드백은 per-tenant DB 강제)
- **옵션 C**: SMB Shared 전체 보류, B2B-Enterprise per-tenant 만 유지

| 옵션 | 장점 | 단점 | 권장도 |
|------|------|------|--------|
| **A** | cost 효율 최대 | PIPA 위험 ↑ (민감 인사 평가 정보) | ⭐ |
| **B** | PIPA 안전 + 일부 cost 절감 | 구현 복잡도 ↑ | ⭐⭐⭐ 권장 |
| C | PIPA 안전 최대 | cost 절감 0 | ⭐⭐ |

**권고**: **옵션 B** — 자기평가/멘토 피드백 (민감 인사 평가)은 per-tenant DB 강제, OKR + 회고는 SMB shared 가능. PIPA 정합 분석 후 최종 결정.

---

## §6 비용 영향

### 6.1 회사당 월 비용 비교

| 토폴로지 | DB | 합계 | 비고 |
|---------|-----|------|------|
| **B2B-Enterprise per-tenant DB** | $20 (Neon 프로젝트) | **$20/월** | 회사당 |
| **SMB Shared DB** | $1 (분담) | **$1/월** | 회사당 (참고: 실제 첨부/SMTP 부재로 ware 대비 ↓) |
| **절감률** | 95% | **95%** | (참고: ware/hcm 75~85%, performance 첨부 부재로 절감률 ↑) |

### 6.2 SMB Shared DB 운영 비용 (월)

**고정 비용 (회사 수 무관)**:
- Neon 프로젝트 `shared-smb-easy-performance-management`: $50/월 (Scale-up plan)
- **합계 고정**: $50/월

**변동 비용 (회사 수 × 사용량)**:
- DB row + storage: ~$1/회사/월 (50명 기준, 성과 평가 데이터 row 한정적)
- **합계 변동**: ~$1/회사/월

**손익분기**:
- 회사 1건: $50 + $1 = $51/월 (적자)
- 회사 50건: $50 + $50 = $100/월 (회사당 $2, 흑자 진입)
- 회사 100건: $50 + $100 = $150/월 (회사당 $1.5, 본격 흑자)

---

## §7 ADR-031 정합

### 7.1 ADR-031 핵심 원칙

**ADR-031 자매품 9 × 3 토폴로지 매트릭스**:
- B2C 본질 (sign 1)
- B2B-Enterprise 본질 + SMB 옵션 (ware/hcm/recruit/**performance** 4)
- B2B-Enterprise 듀얼 본질 (mra/jobstructure/jobeval 3)
- B2B-Enterprise 본질 + SMB 보류 (time 1)

**performance 듀얼 토폴로지**:
- ✅ B2B-Enterprise per-tenant DB (본질, 단계 2 `bd46134`)
- ✅ SMB Shared DB + RLS tenant_id (옵션, 본 가이드 단계 5 G67 D=A)
- ❌ B2C 공통 테넌트 (도메인 본질 = 기업 워크플로우, 정정 2026-06-08 Task #98)

### 7.2 ADR-014 (자매품 스택 단일화) 정합

- ✅ ADR-013/024 Model B 정합 (1 고객사 = 1 Neon 프로젝트, B2B-Enterprise 본질)
- ✅ ADR-029 (sign B2C 공통 테넌트) Model A + RLS 패턴 정합 (RLS user_id 의 performance 변형 = RLS tenant_id)
- ✅ ware/hcm/recruit SMB 진입 가이드 패턴 정합

---

## §8 후속 박제 후보 (참고)

### 8.1 후속 게이트

- **G68 (가칭)**: SMB Shared DB 진입 게이트 (본 가이드 G1~G5 풀 통과 + PIPA 정합 분석)
- **G69 (가칭)**: SMB tenant onboarding flow (가입 → 도메인 등록 → 사용자 시드)
- **G70 (가칭)**: SMB 결제 진입 (Stripe / 토스페이먼츠 — starter $X/월)
- **G71 (가칭)**: SMB → B2B-Enterprise 마이그 (회사 성장 시 per-tenant DB 전환)

### 8.2 후속 lib 추출 후보

- **lib BE 20 후보**: `RlsTenantAspect` SMB 변형 (현재 BE 18 B2B 보존 → SMB tenant_id 분기)
- **lib BE 21 후보**: `TenantModeRouter` (B2B / SMB 라우팅 통합)

---

## §9 변경 이력

| 날짜 | 변경 | 사유 |
|------|------|------|
| 2026-06-08 | 초기 박제 | G67 D=A 확정 후속 진입 가이드 박제 (Task #105, ware/hcm/recruit 패턴 정합) |

---

**박제 완료 — 사용자 D-performance-smb-stage1 결정 + PIPA 정합 분석 후 G1~G5 진입.**

**LIVE 영향**: 0 (박제 단계 + 명시 진입 시에만 활성)

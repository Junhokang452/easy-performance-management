> 작성 시각: 2026-06-11 / 작성자: Claude (easy-suite-orchestrator 산출물) / 목적: P0~P2 로드맵 + 결정 게이트 G_PERF_E1~E10 + 위험 5 + 다음 액션 박제

# 04. Roadmap + Priority + Decision Gates

---

## §1 P0 / P1 / P2 단계 로드맵

### §1.1 P0 — Must (~12주)

**목표**: 사이클 기반 정기 평가 MVP — 한국형 KPI 가중치 + 강제 분포 + Calibration + Appeal + 사원/매니저/HR 3 페르소나 풀 동작.

| 슬라이스 | 작업 | 신규 entity | 신규 화면 | S2S | 예상 |
|---------|------|-------------|-----------|-----|------|
| **P0-S1** | EvaluationCycle + EvaluationPolicy + 8단계 상태기계 + HR 사이클 생성 모달 | 2 | 2 (#21, #22) | — | 1주 |
| **P0-S2** | KpiTree + KpiNode + KpiAssignment + KpiActual + 가중치 검증 + BSC 라벨 옵션 | 4 | 3 (#2, #13, #18) | — | 2주 |
| **P0-S3** | PerformanceReview + 자기평가/매니저평가 폼 + 자동 점수 계산 + Self↔Manager 비교 | 1 | 3 (#4, #12) | — | 2주 |
| **P0-S4** | RatingDistribution + Calibration 시뮬레이터 + 강제 분포 적용 | 2 (RatingDistribution + CalibrationSession) | 2 (#19, #28) | — | 1.5주 |
| **P0-S5** | PerformanceReport append-only + HR 일괄 발행 + 사원 결과 조회 | 1 | 2 (#7, #26) | — | 1주 |
| **P0-S6** | hcm S2S 수신 (rm_employee/org/assignment) — talent `STAGE15` 패턴 동형 | 0 (read-model 3 테이블) | 0 | hcm 수신 3 | 1주 |
| **P0-S7** | talent S2S 송신 (FINALIZED 시 performance-results) | 0 | 0 | talent 송신 1 | 0.5주 |
| **P0-S8** | 기존 4 도메인 흡수 매핑 + view + 마이그(PersonalOkr → Okr+Objective+KeyResult idempotent 분해) | 0 (마이그) | 0 | — | 1주 |
| **P0-S9** | i18n + ErrorCode E98 확장 (~40 신규) + 사원 dashboard `/my/dashboard` | 0 | 1 (#1) | — | 1주 |
| **P0-S10** | E2E + 회귀 + Appeal 골격 (옵션 추후 확장) | 1 (Appeal 스켈레톤) | 1 (#8) | — | 1주 |
| **합계** | | **~10 entity** | **~14 화면** | | **~12주** |

**P0 완성 게이트 (G_PERF_P0_END)**: 사이클 1개 풀 운영 (PLANNED → ACTIVE → … → FINALIZED → REPORT_PUBLISHED) + 강제 분포 시뮬레이션 + 사원 결과 view + talent 송신 sourceVersion 단조 검증.

---

### §1.2 P1 — Should (~8주)

**목표**: OKR cascading + Check-in + 역량평가 + 360 위임 통합 + Talent Profile 페이지.

| 슬라이스 | 작업 | 신규 entity | 신규 화면 | S2S | 예상 |
|---------|------|-------------|-----------|-----|------|
| **P1-S1** | Okr + Objective + KeyResult + alignment 트리 + 사원/매니저 OKR 화면 | 3 | 3 (#3, #14, OKR Tree composite) | — | 2주 |
| **P1-S2** | CheckIn (주간 + 1:1 + Reflection 통합) + ReflectionJournal/MentorFeedback 흡수 | 1 (CheckIn) | 2 (#5, #15) | — | 1주 |
| **P1-S3** | CompetencyFramework + Competency + CompetencyAssessment + BARS + 평가 폼 | 3 | 1 (#27) | easy-job rm_skill_set 수신 | 2주 |
| **P1-S4** | mra ReportSnapshot 수신 + 매니저 review 화면에 360 집계 노출 (min-rater 가드) | 0 (rm_mra_result) | 0 | mra 수신 1 | 0.5주 |
| **P1-S5** | Talent Profile (`/manager/talent-profile/{empId}`) composite view | 0 | 1 (#16) | — | 1주 |
| **P1-S6** | AchievementLog (사후 성과신고) + 사원 등록 + 매니저 confirm | 1 | 1 (#10) | — | 1주 |
| **P1-S7** | Director dashboard + 부서 분포·OKR 평균·KPI 달성률 | 0 | 1 (#20) | — | 0.5주 |
| **합계** | | **~8 entity** | **~9 화면** | | **~8주** |

**P1 완성 게이트 (G_PERF_P1_END)**: OKR cascade 정합 + 주간 Check-in 운영 + Competency 평가 풀 사이클 + 360 집계 매니저 view + Talent Profile 운영.

---

### §1.3 P2 — Nice (~6주)

**목표**: BSC 4 관점 보강 + Recognition + Appeal 풀 + Career Plan + Engagement Pulse + 한국 강제 분포 운영 정교화.

| 슬라이스 | 작업 | 신규 entity | 신규 화면 | S2S | 예상 |
|---------|------|-------------|-----------|-----|------|
| **P2-S1** | BSC 4 관점 KPI Tree 강화 (전략 맵 view + 4 관점 컬럼) | 0 (KpiNode.bscPerspective 활용) | 1 (Wireframe 4 강화) | — | 1주 |
| **P2-S2** | Recognition + Feedback (CFR Praise/Constructive) | 2 | 2 (#6, #17) | — | 1주 |
| **P2-S3** | Appeal 풀 워크플로우 + HR 검토 큐 + 결정 audit | 0 (P0 스켈레톤 강화) | 1 (#25) | — | 1주 |
| **P2-S4** | CareerDevelopmentPlan + 사원 화면 + 매니저 1:1 도구 | 1 | 1 (#9) | — | 1주 |
| **P2-S5** | EngagementPulse — 짧은 설문 (mra 위임 결정 또는 자체 — G_PERF_E10 결정) | 1 | 1 | — | 1주 |
| **P2-S6** | audit log + S2S config 시스템 관리 화면 | 0 | 2 (#29, #30) | — | 1주 |
| **합계** | | **~4 entity** | **~8 화면** | | **~6주** |

**P2 완성 게이트 (G_PERF_P2_END)**: 6 모범 SaaS(15Five / Lattice / Workday / SuccessFactors / Culture Amp / Klever) 화면 패턴 95% 커버.

---

### §1.4 P0~P2 누적 매트릭스

| 항목 | P0 | P1 | P2 | 누적 |
|------|----|----|----|------|
| 신규 entity | ~10 | ~8 | ~4 | **~22** |
| 신규 화면 | ~14 | ~9 | ~8 | **~31** |
| 신규 S2S 채널 | 2 (hcm 수신, talent 송신) | 2 (mra 수신, easy-job 수신) | 0 | **4** |
| 슬라이스 수 | 10 | 7 | 6 | **23** |
| 예상 인력 (1 dev) | 12주 | 8주 | 6주 | **~26주 ≈ 6.5개월** |

→ 단계 4 EC-FE 후속 진입, talent FE 패턴 재사용으로 FE 비용 ~30% 절감 가능.

---

## §2 사용자 결정 게이트 G_PERF_E1~E10

### G_PERF_E1: 등급 분포 정책

| 옵션 | 의미 |
|------|------|
| **A.** 강제 분포 (FORCED) — S/A/B/C/D 비율 적용 후 자동 조정 | 한국 대기업 표준 |
| **B.** 절대 평가 (ABSOLUTE) — 점수 그대로 등급 | 글로벌 모범 |
| **C.** 혼합 (HYBRID) — 부서별 정책 분리 + 강제 옵션 사이클별 ON/OFF | 가장 유연 |

→ **권고: C (혼합)** — EvaluationPolicy 옵션화로 모든 시나리오 수용.

### G_PERF_E2: KPI 자동 수집 SoT 범위

| 옵션 | 의미 |
|------|------|
| A. 수동 입력만 | 단순, 빠른 진입 |
| B. hcm 수집 (sales/manufacturing 등 lib seam) | 자매품 정합 |
| C. 외부 BI/CRM 연동 (KpiNode.sourceConfig) | 가장 광범, 비용 ↑ |

→ **권고: A → B → C 단계적** — P0 = A, P1 = B (KpiNode.source 컬럼만 박제), P2 = C 검토.

### G_PERF_E3: OKR 점수 합산 정책

| 옵션 | 의미 |
|------|------|
| A. 단순 평균 KR score | Lattice 모범 |
| B. KR weight 가중 평균 | 사용자 설정 가능 |
| C. confidence 보정 | 야망 정합 |

→ **권고: A** — 단순 평균, weight 옵션은 P1 확장.

### G_PERF_E4: 360 다면평가 = mra 위임 확정

| 옵션 | 의미 |
|------|------|
| **A.** mra 위임 확정 + performance 1:1 자체 | ★★★★★ 권고 |
| B. performance 자체 익명 360 | mra 중복, ★☆☆ |

→ **권고: A** — mra `dbdcf96` 패턴 동형 수신 (min-rater 가드 + 집계만).

### G_PERF_E5: BSC 4 관점 강제 vs 자유

| 옵션 | 의미 |
|------|------|
| A. 강제 BSC 4 관점 (KpiNode 필수) | 대기업 전략 통합 |
| **B.** 자유 옵션 (cycle.bscEnabled) | 권고, SMB 진입 비용 ↓ |

→ **권고: B**.

### G_PERF_E6: Calibration = talent 위임 vs performance 자체 9-Box

| 옵션 | 의미 |
|------|------|
| **A.** Calibration 등급 분포 = performance / 9-Box = talent | 권고, 자매품 경계 보존 |
| B. performance 자체 9-Box | talent 중복, ★☆☆ |

→ **권고: A** — talent FE `f3edc12` (39283dc) 9-Box Matrix Grid SoT 재사용 또는 link out.

### G_PERF_E7: Appeal 단계 운영 정책

| 옵션 | 의미 |
|------|------|
| A. 의무 (한국 대기업) | 14일 window + 검토 위원회 |
| **B.** 옵션 (EvaluationPolicy.appealEnabled) | 권고, 시나리오별 유연 |
| C. 없음 | 글로벌 SaaS 표준 |

→ **권고: B**.

### G_PERF_E8: 사후 성과신고 cutoff 시점

| 옵션 | 의미 |
|------|------|
| A. SELF_REVIEW 시작 시 cutoff | 사이클 정합 |
| **B.** SELF_REVIEW 마감 D-3 cutoff | 매니저 confirm 시간 확보 |
| C. 사이클 결산 직전 | 데드라인 압박 |

→ **권고: B**.

### G_PERF_E9: 사원 페르소나에 매니저 다른 팀원 점수 노출 정도

| 옵션 | 의미 |
|------|------|
| **A.** 익명 분포만 (등급 비율) | 권고, 한국 정합 |
| B. 본인 등급만 | 가장 보수 |
| C. 부서 동료 점수까지 (이름 포함) | 글로벌 일부 — 한국 NG |

→ **권고: A**.

### G_PERF_E10: i18n 5 locale 확장 우선순위

| 옵션 | 의미 |
|------|------|
| **A.** P0 ko + en 풀, P1 ja, P2 zh-CN + vi | 권고, talent 패턴 정합 |
| B. P0 ko + en + ja, P1 zh-CN, P2 vi | 일본 시장 우선 |

→ **권고: A**.

---

## §3 잠재 위험 5종

### Risk-1: 기존 4 도메인 회귀

- 기존 4 도메인 = 단순 entity 평면 → 사이클 도입 시 cycleId NULL 허용 (history 보존)
- **완화**: V2 마이그 idempotent + cycleId NULL 허용 + service-level view + 1 sprint legacy fallback

### Risk-2: 자매품 경계 침범 (9-Box / 360)

- 개발 중 "9-Box 자체 구현 유혹" — 빠르게 보이지만 talent 중복
- **완화**: 본 박제 + talent FE 39283dc 재사용 가이드 + Code Review 게이트

### Risk-3: 강제 분포 운영 사고

- HR 어드민이 시뮬레이션 없이 강제 분포 적용 → 매니저 합의 없이 등급 변경 → 정치 사고
- **완화**: 시뮬레이션 모드 의무 + Calibration 회의 audit 로그 + 2단계 확인 모달

### Risk-4: KPI 가중치 검증 누락

- Σweight ≠ 1.0 인 KPI 트리 → 점수 산출 오류 → 보상 영향
- **완화**: @PrePersist + Repository.sumWeight 의무 + CI 가드 + 단위 테스트

### Risk-5: 사이클 단계 전이 권한 사고

- HR 어드민이 단계 전이 잘못 → 사원 편집권 lock 풀림 → 점수 변조
- **완화**: 전이 매트릭스 정의 + audit chain + 2단계 확인 + 단계 lock 강제

---

## §4 추가 잠재 위험·완화 (선택)

| # | 위험 | 완화 |
|---|------|------|
| R6 | 360 익명성 누수 (rater_id 노출) | mra 수신 payload 가드 — raterIds/individualScores 절대 비포함 |
| R7 | i18n 5 locale 누락 (zh-CN/vi) | talent ko/en/ja/zh-CN/vi 5 locale 패턴 동형, partial → 풀 |
| R8 | Calibration 회의 실시간 동기화 (다중 부서장 동시 편집) | 낙관적 락 (@Version) + 충돌 시 재시도 |
| R9 | KPI 자동 수집 외부 의존 | sourceConfig SPI seam + 게이트 OFF 기본, 미설정 시 수동 입력 fallback |
| R10 | 사이클 종료 후 데이터 보존 (PIPA 5년) | PerformanceReport append-only + S3 archive + 사이클 ARCHIVED 후 read-only |

---

## §5 다음 액션 3개 (사용자가 즉시 진행 가능)

### Action-1: 의사결정 회수 + 박제 (1시간)

- 사용자가 본 문서 G_PERF_E1~E10 (10건) 답변 → 본 디렉터리에 `decisions_2026-06-12.md` 신규 박제
- 권고안 그대로 수용 시 모든 옵션 채택 가능, 권고와 다른 결정 시 영향 매트릭스 별도 박제 필요

### Action-2: P0-S1 슬라이스 진입 (~1주, 코드 변경 진입)

- `EvaluationCycle` + `EvaluationPolicy` entity + Flyway V2 (cycle / policy 2 테이블 + 8 상태 enum + tenant_id 선두 인덱스)
- `/hr/cycles` 페이지 + 생성 모달 (Wireframe 6) — Mantine v9 + STD-FE 5 정합
- HR Operator 권한 SecurityConfig 분기
- ErrorCode E98 확장 (10 신규 — CYCLE_*, POLICY_*)
- 단위 테스트 + 통합 테스트 + tsc + vite build 게이트
- 박제 산출물: `_workspace/EVAL_CYCLE_P0S1_2026-06-XX.md`

### Action-3: 자매품 경계 계약 박제 + S2S 수신 1차 (~1주, 코드 변경 진입)

- `_workspace/EVAL_SIBLING_BOUNDARY_2026-06-12.md` 박제 (talent 송신 spec + hcm 수신 spec + mra 수신 spec + easy-job 수신 spec)
- hcm rm_employee/org/assignment 3 수신 endpoint (`/api/internal/sync/core-master`) — talent `d95bf62` 패턴 동형 (Bearer + HMAC + sourceVersion + 503 fail-safe)
- 통합 테스트: 미설정 시 503, 설정 시 200 + read-model 적재 확인
- 박제: `_workspace/EVAL_S2S_RECEIVE_P0S6_2026-06-XX.md`

---

## §6 결정 게이트 진입 순서 (의존성 그래프)

```
G_PERF_E1 (분포 정책) ─────┐
G_PERF_E5 (BSC 강제/자유) ─┼─→ P0-S1 EvaluationPolicy 설계 진입
G_PERF_E7 (Appeal 옵션)   ─┤
G_PERF_E8 (사후신고 cutoff)┘

G_PERF_E2 (KPI 자동 수집)  ─→ P1-S? (P0 = MANUAL만)
G_PERF_E3 (OKR 합산)       ─→ P1-S1 OKR 진입
G_PERF_E4 (360 mra 위임)   ─→ P1-S4 mra 수신 (CRITICAL 결정)
G_PERF_E6 (9-Box talent)   ─→ P0-S7 talent 송신 (CRITICAL 결정)
G_PERF_E9 (점수 노출)      ─→ P0-S5 사원 결과 화면
G_PERF_E10 (i18n 순서)     ─→ P0~P2 전체 i18n 박제
```

→ **CRITICAL 결정 2건** = G_PERF_E4 (mra 위임) + G_PERF_E6 (talent 위임) — 본 자매품 경계 핵심.

---

## §7 향후 확장 후보 (P3+, 본 문서 외 검토)

- **Pay-for-Performance Matrix** — 등급 → 보상 매트릭스 (호봉·연봉·PI) — hcm payroll 연동
- **Succession 통합 view** — talent 9-Box 결과 ↔ performance history 시각화
- **AI Recommendation** — KPI/OKR 자동 추천, Check-in summary, Career path 추천
- **Mobile App** — 주간 Check-in 모바일 입력
- **Slack/Teams 통합** — Recognition / Feedback / Check-in 알림

→ 본 자매품 P3+ 백로그 또는 별도 자매품(easy-comp-management 등) 검토.

---

## §8 결론

- P0 12주 + P1 8주 + P2 6주 = **~6.5개월 진입 비용**
- 결정 게이트 10건 중 CRITICAL 2건 (E4 mra / E6 talent) 우선
- 다음 액션 3개 (의사결정 회수 + P0-S1 + 자매품 경계 박제)
- 자매품 경계 보존 + 한국 시장 특수성 흡수 + 글로벌 모범 정합 3축 동시 만족

본 4 문서가 사용자의 다음 단계 의사결정 진입점입니다.

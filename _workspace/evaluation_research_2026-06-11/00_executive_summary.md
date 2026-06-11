> 작성 시각: 2026-06-11 / 작성자: Claude (easy-suite-orchestrator 산출물) / 목적: easy-performance-management 평가 도메인 확장 의사결정용 풀스택 리서치 요약 (사용자 1차 결정 입력)

# 00. Executive Summary — easy-performance-management 평가 도메인 확장

## 한 페이지 답변

현 baseline 4 도메인(SelfEvaluation / PersonalOkr / ReflectionJournal / MentorFeedback)은 **개인 성장(B2C 성격) 슬라이스**에 가깝다. 사용자가 요청한 KPI / 개인 성과평가(MBO·수시) / 역량평가 / OKR / 360도·9-Box·Calibration·Continuous Feedback 는 **기업(B2B-Enterprise) 본질 — 사이클 운영 + 매니저-팀원 워크플로우 + HR 어드민 SoR**이다. 따라서 본 자매품은 **"개인 성장" 슬라이스를 보존하면서 "사이클 기반 정기 평가" 라는 두 번째 본질 슬라이스를 추가**하는 방향으로 확장해야 한다.

권장 모델은 **"PerformanceCycle 중심 통합 모델"** + **"방법론 조립형(KPI/OKR/MBO/Competency 동시 운영 가능)"** + **"talent SoR · mra SoR 위임"**. 즉 performance 는 **사이클 안에서 무엇을(KPI/OKR/MBO/Competency) 누가(사원/매니저/HR) 언제 실행하는가**를 책임지고, 9-Box/Calibration 최종 산출과 다면평가 익명 집계는 자매품에 위임한다.

이 결정은 자매품 매트릭스의 본질 정합을 깨지 않으면서, 한국 HR Tech 시장(정기 평가 + 등급 분포 + 이의신청)과 글로벌 모범(15Five / Lattice / Workday / SuccessFactors)을 동시에 만족시킨다.

---

## 핵심 의사결정 5건 (사용자 판단 필요)

| # | 결정 항목 | 권고 | 근거 |
|---|----------|------|------|
| **D1** | 사이클 모델 | **EvaluationCycle 단일 SoT + 사이클당 다중 방법론(KPI·OKR·MBO·Competency 조립)** | 한국형 정기 평가(반기/연간) + 글로벌 OKR cadence(분기) 양립 |
| **D2** | 9-Box / Calibration | **performance 는 점수 송신만, 9-Box·Calibration SoR = easy-talent-management** | ADR-051 D2 직후 talent 가 EvidenceSnapshot 으로 동결 소비 — 침범 금지 |
| **D3** | 360도 다면평가 | **mra 위임 + performance 자체는 1:1 피드백 / Continuous Feedback 만** | mra 익명성 SoT 보존 (TALENT_PLAN 리스크 2 — 익명성 가드 송신측 책임) |
| **D4** | KPI vs OKR 동시 운영 | **분리된 entity 트리 + 사이클 정책에서 둘 다 활성/하나만 활성 옵션** | 한국 대기업 = KPI(보상 연동) / 글로벌 스타트업 = OKR(야망) — 같은 회사가 둘 다 운영하는 경우도 다수 |
| **D5** | 등급 분포 정책 | **EvaluationPolicy 에서 강제 분포 / 절대 평가 / 혼합 3 모드 옵션** | 한국 대기업 강제 분포(S/A/B/C/D) vs 글로벌 회사 절대 평가 모두 수용 |

---

## P0/P1/P2 도메인 매트릭스 (요약)

| Phase | 도메인 | 목표 | 신규 entity | 신규 화면 (수) | S2S |
|------|--------|------|-------------|---------------|-----|
| **P0** (must, ~12주) | KPI + EvaluationCycle + PerformanceReview + Calibration 송신 | 사이클 기반 정기 평가 MVP — 한국형 KPI 가중치 + 사원/매니저/HR 3 페르소나 풀 동작 | EvaluationCycle / EvaluationPolicy / KpiTree / KpiNode / KpiAssignment / KpiActual / PerformanceReview / RatingDistribution / PerformanceReport | 12 | hcm rm_employee/rm_org 수신, talent score 송신 |
| **P1** (should, ~8주) | OKR cascading + Check-in + 역량평가 + 360 위임 | OKR alignment 트리 + 주간 Check-in + 직무역량/리더십/핵심가치 + mra 결과 노출 | Okr / Objective / KeyResult / CheckIn / Competency / CompetencyFramework / CompetencyAssessment | 10 | mra rm_mra_result 수신, easy-job-management SkillSet 카탈로그 수신 |
| **P2** (nice, ~6주) | BSC 보강 + Continuous Feedback + Recognition + Appeal + CDP | BSC 4 관점 KPI Tree 라벨 + 동료 즉시 피드백 + 인정 + 이의신청 + Career Plan | BscPerspective(라벨) / Feedback / Recognition / Appeal / CareerDevelopmentPlan / EngagementPulse | 8 | (없음, 내부 도메인) |

> 기존 4 도메인 보존: SelfEvaluation 은 PerformanceReview 의 self-section 으로 흡수, PersonalOkr 은 Okr+Objective+KeyResult 의 owner=Personal 변형, ReflectionJournal 은 사이클 단계 회고로 통합, MentorFeedback 은 OneOnOneCheckIn(MENTOR 모드)로 통합. **DB drop 없음 — view + service-level 매핑**.

---

## 권장 다음 단계 3개 액션

1. **사용자 D1~D5 결정 회수** — 본 문서 5건 의사결정을 결정 게이트 G_PERF_E1~E10(파일 04 §결정 게이트)에서 답변. 특히 D2(9-Box talent 위임), D3(360 mra 위임)은 자매품 경계 침범 차단 필수.
2. **P0 슬라이스 1 (EvaluationCycle + EvaluationPolicy + PerformanceReview 골격) 박제 + 단계 6 합의** — `_workspace/EVAL_CYCLE_SCAFFOLD_2026-06-12.md` 신설, Flyway V2 (cycle/policy/review/rating_distribution 4 테이블 + tenant_id 선두 인덱스), ErrorCode E98 영역 확장 카탈로그, 기존 4 도메인 보존 매핑.
3. **자매품 경계 계약 박제** — `_workspace/EVAL_SIBLING_BOUNDARY_2026-06-12.md` 에 ① performance → talent 송신(performance_results, ReviewDecision FINALIZED 시) ② mra → performance 수신(rm_mra_result, 익명 집계만) ③ hcm → performance 수신(rm_employee/org/assignment) ④ easy-job-management → performance 수신(SkillSet/Skill 카탈로그 read-model) 4 계약을 talent `d95bf62` 수신 패턴(Bearer + HMAC + sourceVersion idempotency + 미설정 503) 동형으로 정의.

---

## 자매품 경계 (한 번 더 강조 — 침범 금지)

| 영역 | SoR | performance 의 역할 |
|------|-----|--------------------|
| 조직·사원·발령 | **easy-hcm** | S2S 수신만 (rm_employee/rm_org/rm_assignment) |
| 직무 카탈로그(직군→직렬→직무→세부직무→스킬셋→스킬) | **easy-job-management** | S2S 수신만 (competency framework 참조) |
| 다면평가(360 익명 집계) | **easy-mra** | mra 결과 수신, performance 자체로는 1:1 피드백만 |
| 9-Box / Calibration / Succession | **easy-talent-management** | performance 점수 송신, talent EvidenceSnapshot 으로 동결 소비 |
| **사이클 운영 + KPI/OKR/MBO/Competency 평가 + 등급 산출 + 보상 연동 신호** | **easy-performance-management (본 자매품)** | SoR |

---

## 위험·주의

- **위험 1 (자매품 경계 침범)**: 9-Box UI를 performance 에 만들고 싶은 유혹 — talent FE 가 이미 9-Box Matrix Grid 를 SoR 로 가지고 있음 (`easy-talent-management/frontend/` `39283dc`). performance 는 칸 분포 비주얼만 보여주고 최종 합의/배치는 talent 가 가져간다.
- **위험 2 (mra 익명성)**: 360 결과를 performance 매니저 화면에 그대로 노출하면 mra 익명 익명성 가드 위반. **반드시 min-rater-count 가드 후 집계만 노출** (mra `dbdcf96` 정합).
- **위험 3 (KPI 자동 수집 SoT)**: KPI 실적을 외부 시스템(매출/CRM/BI)에서 자동 끌어오려면 hcm 외 추가 S2S 가 필요 — 본 슬라이스에서는 **수동 입력 1차 + KpiActual.source 컬럼으로 자동/수동 분리** 만 박제.
- **위험 4 (한국 강제 분포)**: HR 어드민이 평가 마감 후 "S 10% / A 20% / B 40% / C 20% / D 10%" 강제 분포 적용 → 매니저 점수가 자동 재조정 → 매니저 반발. **RatingDistribution 정책 + 사이클별 ON/OFF + 시뮬레이션 모드 필수**.
- **위험 5 (Phase B 평면화)**: 기존 4 도메인이 단순 entity 4개로 baseline 되어 있어 평면 모델. 사이클 도입 시 history/append-only 로 재구성 필요 — DB drop 회피하면서 service-level mapping 으로 흡수.

---

## 산출물 인덱스

| 파일 | 목적 |
|------|------|
| `00_executive_summary.md` | 본 문서 — 결정 5건 + P0/P1/P2 매트릭스 + 다음 액션 |
| `01_methodology_matrix.md` | 평가 방법론 9~12종 비교 + 한국 시장 + KPI/OKR/MBO 동시 운영 전략 |
| `02_market_research.md` | 글로벌 SaaS 10종 화면 패턴 카탈로그 + 채택 권고 9 패턴 |
| `03_domain_model_and_screens.md` | 도메인 모델 ERD 16 entity + 페르소나 5 × 화면 30 매트릭스 + 사이클 시퀀스 |
| `04_roadmap_and_priority.md` | P0/P1/P2 슬라이스 분해 + 결정 게이트 G_PERF_E1~E10 + 위험 5 |

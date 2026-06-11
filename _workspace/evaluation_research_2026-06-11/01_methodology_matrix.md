> 작성 시각: 2026-06-11 / 작성자: Claude (easy-suite-orchestrator 산출물) / 목적: 평가 방법론 9~12종 비교·궁합·동시 운영 전략 박제

# 01. Methodology Matrix — 평가 방법론 비교 + 한국 시장 적합성 + 동시 운영 전략

## §1 평가 방법론 12종 비교표

| # | 방법론 | 본질 | 강점 | 약점 | 한국 시장 적합성 | SoT 위치 |
|---|--------|------|------|------|------------------|----------|
| 1 | **MBO** (Management By Objectives) | 사원-매니저 합의 목표 + 가중치 합 100% + 정기 1회 평가 | 평가-목표 1:1 매칭, 보상 연동 자연 | 경직성, 사이클 중 목표 수정 어려움 | ★★★★★ (한국 대기업 표준) | performance.Mbo + performance.MboItem |
| 2 | **KPI** (Key Performance Indicator) | 정량 지표 + 가중치 + 트리 cascade + 자동/수동 수집 | 객관성, 부서/개인 cascade 자연, 보상 연동 | 정성 평가 누락, 지표 게이밍 위험 | ★★★★★ (재무·영업·생산 핵심) | performance.KpiTree + KpiNode + KpiActual |
| 3 | **BSC** (Balanced Scorecard) | 4 관점(재무·고객·내부프로세스·학습성장) 균형 | 전략 정합, 단기·장기 균형 | 도입·운영 비용 큼, 4 관점 강제 부담 | ★★★★☆ (대기업 전략기획 부서) | KpiNode.bscPerspective enum |
| 4 | **OKR** (Objective + Key Result) | 야망적 질적 O + 정량 KR 3~5 + 0.0~1.0 score | 투명성, alignment 트리, 70% 달성 정상 — 도전 장려 | 보상 연동 비권장, 한국 문화 충돌(낮은 점수 = 죄책감) | ★★★☆☆ (스타트업·IT 중심, 대기업 보조) | performance.Okr + Objective + KeyResult |
| 5 | **Competency Assessment** (직무역량) | behavioral indicator 척도 1~5 평가 | 성장 가시화, 교육 연계 | 척도 주관성, 평가자 편향 | ★★★★☆ (HRD 부서 필수) | performance.Competency + CompetencyAssessment |
| 6 | **360° Multi-Rater (다면평가)** | 상사·동료·부하·고객 익명 평가 | 사각 보완, 리더십 가시화 | 익명성 보장 비용, 정치적 활용 위험 | ★★★★☆ (리더 진단 표준) | **easy-mra (위임)** — performance 는 결과만 소비 |
| 7 | **Check-in (1:1)** | 주간/격주 매니저-팀원 단방향 메모 | OKR 진척·블로커 즉시 파악, 연중 지속 | 형식화 위험, 매니저 부담 | ★★★★☆ (리모트·IT 중심) | performance.CheckIn |
| 8 | **CFR** (Conversations + Feedback + Recognition) | OKR 보완 — 1:1 대화 + 즉시 피드백 + 공개 인정 | 문화 변화 견인, 사기 진작 | 도구 의존 ↑, 정성 데이터 처리 부담 | ★★★☆☆ (스타트업·디자인·콘텐츠) | performance.CheckIn + Feedback + Recognition |
| 9 | **9-Box / Calibration** | 성과×잠재력 → 9칸 배치 + 조직장 합의 | 후계·승진 의사결정 SoT | 라벨링 위험, 시간 소요 큼 | ★★★★☆ (대기업 인사위원회) | **easy-talent-management (위임)** — performance 는 점수 송신 |
| 10 | **Continuous Feedback (수시 피드백)** | 동료간 즉시 칭찬·피드백, Slack-like UX | 빈도 ↑, 사기 ↑ | 신호 잡음 ↑, 평가 무관 | ★★★☆☆ (테크 회사) | performance.Feedback + Recognition |
| 11 | **사후 성과신고형 (Self-reported Achievement)** | 연중 자유 등록 + 매니저 confirm + 사이클 결산 자동 합산 | 사원 주도성, 누적 자료화 | confirmer 부담, 게이밍 위험 | ★★★★☆ (한국 + 일본 — "성과 보고서" 문화) | performance.AchievementLog (P1) |
| 12 | **Engagement Pulse / eNPS** | 짧은 설문(2~5문) 주기 발사 | 문화 가시화, ELT 빠른 감지 | 평가 본질 아님(별도 SoR 검토 가치) | ★★★☆☆ (대기업 조직문화 부서) | performance.EngagementPulse (P2) — 또는 mra 위임 가능성 |

---

## §2 "한 회사가 동시에 운영하는 평가 조합" 시나리오 4종

### 시나리오 A: 한국 IT 스타트업 50~200명
- **운영**: OKR(분기) + Check-in(주간) + Continuous Feedback + Recognition + 연 1회 PerformanceReview(360 포함)
- **보상 연동**: PerformanceReview 만 (OKR 비연동, 정직한 OKR 보존)
- **performance 화면 조합**: OKR Tree + Check-in 주간 카드 + Praise wall + 연말 종합 리뷰 폼
- **자매품 경계**: mra 익명 360 사용 / talent 위임 없음(규모 부족)

### 시나리오 B: 한국 중견 제조 500~3,000명
- **운영**: KPI(반기·연간 정량) + Competency(연 1회 행동평가) + 360°(리더만) + 강제 분포(S/A/B/C/D)
- **보상 연동**: KPI 70% + Competency 30% 가중 → 등급 산출 → 보상 매트릭스 적용
- **performance 화면 조합**: KPI Tree + KPI 실적 입력 + Competency 평가 폼 + Calibration 시뮬레이터 + RatingDistribution UI
- **자매품 경계**: mra 360(리더 한정) / talent 9-Box(임원 인사위) / hcm 발령 SoR

### 시나리오 C: 한국 대기업 5,000명+
- **운영**: BSC 4 관점 KPI Tree + MBO + 다면평가 + 역량평가 + 강제 분포 + Calibration + 이의신청 단계 + 사후 성과신고형
- **보상 연동**: 종합 등급 → 호봉/연봉/PI 매트릭스
- **performance 화면 조합**: BSC 전략 맵 + KPI Tree(부서 cascade) + Mbo 폼 + Competency 평가 + Appeal 워크플로우 + AchievementLog
- **자매품 경계**: 모든 자매품 풀 활용 — mra(다면) / talent(9-Box·승계) / hcm(SoR) / easy-job(직무·스킬 카탈로그)

### 시나리오 D: 글로벌 SaaS·디자인 회사 (북미 모델)
- **운영**: OKR + Check-in + Continuous Feedback + 연 2회 Self-Manager review (절대 평가) + Career Conversation
- **보상 연동**: 매니저 추천 + Calibration 회의 (강제 분포 없음)
- **performance 화면 조합**: OKR + Check-in + Praise + Reviews(self/manager) + Career Track
- **자매품 경계**: mra 옵션 / talent 위임 / hcm SoR

### 시나리오 매트릭스 요약

| 시나리오 | 사이즈 | 보상 연동 강도 | KPI | OKR | MBO | Competency | 360° | Calibration | 강제 분포 |
|----------|--------|---------------|-----|-----|-----|-----------|------|-------------|----------|
| A 스타트업 | 50~200 | 약 | ❌ | ✅ | ❌ | ❌ | ✅(mra) | ❌ | ❌ |
| B 중견 제조 | 500~3K | 강 | ✅ | ❌ | ❌ | ✅ | ✅(리더만) | ✅(talent) | ✅ |
| C 대기업 | 5K+ | 매우 강 | ✅ | ❌ or 보조 | ✅ | ✅ | ✅ | ✅ | ✅ |
| D 글로벌 SaaS | 100~5K | 중간 | ❌ | ✅ | ❌ | ✅ | ✅(옵션) | ✅(절대 평가 기반) | ❌ |

**결론**: performance 는 4 시나리오를 모두 단일 자매품으로 수용해야 한다 → **EvaluationPolicy 가 사이클별로 어떤 방법론을 활성/가중을 결정**하는 메타 모델 필요.

---

## §3 KPI vs OKR vs MBO 동시 운영 전략

### 3.1 본질 차이

| 항목 | KPI | OKR | MBO |
|------|-----|-----|-----|
| 시간축 | 분기/반기/연 (정량 트래킹) | 분기 (cadence) | 연간 1회 |
| 목표 성격 | 정량 지표 | 야망적 + 정량 KR | 합의 목표(정량+정성) |
| 달성률 기대 | 100% | 60~70% (도전) | 100% |
| 보상 연동 | ✅ 강 | ❌ 권장 안 함 | ✅ 강 |
| Cascade | 조직 트리 | alignment(직접 종속 아님) | 1:1 합의 |
| 가중치 | ✅ 합 100% | ❌ 보통 균등 | ✅ 합 100% |
| 사용자 | HR + 임원 + 부서장 | 모든 사원(투명) | 사원-매니저 |

### 3.2 화면 분리 전략 (동시 운영 시)

| 운영 패턴 | 화면 구조 권고 |
|----------|---------------|
| **KPI + OKR 둘 다 활성** | 별도 탭 — `/cycle/{id}/kpi-tree` + `/cycle/{id}/okr-tree`. 두 트리는 정합 의무 없음 (서로 다른 cadence). 사원 대시보드는 두 트리에서 본인 노드 양쪽 카드로 노출. |
| **KPI + MBO 둘 다 활성** | KPI = 정량 객관 / MBO = 정성 합의 — 둘 다 가중치 합 100%, 최종 등급 = `(KPI*0.6 + MBO*0.4)*Competency 가중` 식. 사원 화면은 통합 "성과 폼" — KPI 섹션 + MBO 섹션 분리. |
| **OKR + Check-in (서구 표준)** | OKR Tree + 주간 Check-in 카드 — 같은 화면(OKR 노드 클릭 → Check-in 사이드패널). |
| **KPI + 360° (한국 강제분포)** | KPI 등급 + 다면평가 보조점수 → Calibration 회의에서 매니저 등급 조정 — RatingDistribution 강제 분포 시뮬레이터로 매니저 자동 조정. |

### 3.3 동시 운영 시 EvaluationPolicy 정책 모델

```
EvaluationPolicy {
  cycleId: UUID
  methods: [KPI(weight=0.6, mandatory=true), MBO(weight=0.3), COMPETENCY(weight=0.1)]
  stages: [GOAL_SETTING, MID_REVIEW, SELF_REVIEW, MANAGER_REVIEW, CALIBRATION, FINALIZED, APPEAL]
  ratingDistribution: { mode: FORCED, distribution: {S:0.10, A:0.20, B:0.40, C:0.20, D:0.10} }
  appealEnabled: true
  selfReportedEnabled: true
}
```

→ 사용자(HR 어드민)가 사이클 생성 시 policy 옵션 폼을 통해 위 6 항목을 선택. 사이클 시작 후에는 policy 변경 불가(append-only).

---

## §4 BSC와 KPI Tree Cascading 의 관계

### 4.1 본질 분리

- **BSC** = 전략 프레임워크 (4 관점: 재무 / 고객 / 내부프로세스 / 학습·성장)
- **KPI Tree** = 전략 → 부서 → 개인 cascade 의 표현 도구
- → BSC는 KPI Tree의 "라벨 + 그룹핑" 으로 구현하면 충분 (별도 entity 불필요)

### 4.2 권장 모델

```
KpiNode {
  id, parentId, label, weight, target, actual, unit,
  bscPerspective: enum [FINANCIAL, CUSTOMER, INTERNAL_PROCESS, LEARNING_GROWTH, NONE]  // BSC 사용 여부 옵션
  cascadeFrom: parentId
  level: enum [CORPORATE, DIVISION, TEAM, INDIVIDUAL]
}
```

→ KPI Tree 화면에서 BSC 옵션 ON 시 4 관점 컬럼/필터 활성, OFF 시 라벨만 노출.

### 4.3 화면 권고

- **BSC 모드 ON** (대기업 전략기획용): 전략 맵(4 관점 × 4 cascade level = 16 셀 행렬) + 각 셀에 KPI 카드
- **BSC 모드 OFF** (스타트업·중견): 트리 노드 컴포넌트만

### 4.4 강제 vs 자유

- 사용자 결정 게이트 G_PERF_E5 — 권고: **자유 옵션** (강제하면 SMB·중견 진입 비용 ↑)

---

## §5 역량평가 layer 와 easy-job-management cascade

### 5.1 역량 3 layer

| Layer | 설명 | 평가 대상 | 자매품 cascade |
|-------|------|----------|---------------|
| **CoreValue** (핵심가치) | 전사 공통 가치 (예: 고객지향·도전·협업) | 전 사원 | performance 자체 카탈로그 |
| **JobCompetency** (직무역량) | 직무별 행동 지표 (예: "분석력", "구조화 사고") | 직무 보유자 | easy-job-management.SkillSet/Skill 카탈로그 수신 |
| **LeadershipCompetency** (리더십역량) | 보직장·리더 한정 (예: "비전 제시", "갈등 조정") | 매니저+ | performance + easy-job-management 합산 |

### 5.2 데이터 흐름

```
easy-job-management.SkillSet (SoR)
       ↓ S2S 수신 (rm_skill_set, rm_skill — read-model)
performance.CompetencyFramework (직무역량 카탈로그 view)
       ↓ binding (직무-역량 매핑)
performance.CompetencyAssessment (개인 평가 결과)
       ↓ ReviewDecision 합산
PerformanceReview.competencyScore
       ↓ talent 송신 (EvidenceSnapshot 동결)
easy-talent-management (승계·승진 심사)
```

### 5.3 평가 폼 패턴

- 척도: 1~5 (Likert) + behavioral indicator 의무 코멘트
- BARS (Behaviorally Anchored Rating Scale) 표시 — 각 점수에 행동 예시 5~7줄
- self / manager / peer 별 동일 폼, 결과는 layer 별 종합

---

## §6 360도 다면평가 — easy-mra 위임 vs performance 자체 구현

### 6.1 결정 매트릭스

| 옵션 | 비용 | 익명성 보장 | 도메인 정합 | 권고 |
|------|------|------------|------------|------|
| **A. mra 위임 (권고)** | ↓ | mra SoT — 검증됨 | ★★★★★ | ✅ |
| B. performance 자체 360 구현 | ↑↑ (mra와 중복) | 별도 가드 필요 | ★☆☆☆☆ | ❌ |
| C. 하이브리드 (1:1 = performance, 익명 360 = mra) | 중간 | mra 가드 + performance 단순 | ★★★★☆ | 1:1 별개 도메인이므로 자연 분리 |

### 6.2 권고 = C (하이브리드)

- **performance 가 책임**: OneOnOneCheckIn / Feedback / Recognition / MentorFeedback (모두 식별 가능, 익명 아님)
- **mra 가 책임**: 익명 360°, 익명 집계 score, min-rater-count 가드 (`dbdcf96` 패턴)
- **데이터 흐름**: mra → performance 수신은 **ReportSnapshot 집계만** (개별 평가자 ID 수신 금지)

### 6.3 mra → performance 수신 계약 (제안)

```
POST /api/internal/sync/mra-results
Headers: Authorization: Bearer ${S2S_TOKEN}, X-Signature: HMAC, X-Source-Version: <monotonic>
Body: {
  employeeId, cycleId,
  raterCount, overallScore, dimensionScores: { LEADERSHIP, COLLAB, ... },
  // ❌ 절대 포함 금지: raterIds, individualScores
}
```

→ talent `d95bf62` 수신 패턴 동형. 미설정 시 503 자체 차단.

---

## §7 한국 시장 특수성 처리

### 7.1 정기 평가 사이클

- **반기/연간 + 정기 단계**: GOAL_SETTING → MID_REVIEW → SELF_REVIEW → MANAGER_REVIEW → 360_COLLECTION → CALIBRATION → FINALIZED → APPEAL
- → EvaluationPolicy.stages 배열로 표현 (8 단계 표준 + 회사별 sub-set)

### 7.2 강제 분포 (Forced Distribution)

- 한국 대기업 표준: S 10% / A 20% / B 40% / C 20% / D 10%
- → RatingDistribution { mode: FORCED | ABSOLUTE | HYBRID, distribution: Map }
- 운영: Calibration 단계에서 매니저 점수 → 분포 시뮬레이션 → 조정 → 최종
- **반드시 시뮬레이션 모드**: 적용 전 영향 미리보기 + 매니저 합의

### 7.3 이의신청 (Appeal)

- 한국 대기업 의무: FINALIZED → 사원 결과 공개 → N일 내 이의신청 → 검토 위원회 → DECISION
- → Appeal { reviewId, submittedBy, reason, status: SUBMITTED|REVIEW|ACCEPTED|REJECTED, decisionAt }

### 7.4 사후 성과신고형 (Self-reported Achievement)

- 한국 + 일본 문화 — 사원이 연중 본인 성과를 자유 등록 → 매니저 confirm → 사이클 결산 시 자동 합산
- → AchievementLog { employeeId, occurredAt, title, evidence, category, confirmedBy, confirmedAt, score }

### 7.5 인사위원회 / Calibration

- 매니저 회의 + 상위 부서장 조정 — talent SoR 위임 (ReviewDecision)
- performance 는 점수 송신 + 분포 시각화만

---

## §8 글로벌 모범과의 차이

| 항목 | 글로벌 (15Five·Lattice) | 한국 시장 | performance 권장 |
|------|-------------------------|----------|------------------|
| 평가 빈도 | 연 2~4회 (분기 Check-in) | 반기·연간 정기 | 둘 다 (사이클 type 옵션) |
| 보상 연동 | 매니저 추천 + 회의 | 등급 자동 산출 + 매트릭스 | RatingDistribution 옵션 |
| 다면평가 | 옵션 | 의무(리더) | mra 위임 |
| 이의신청 | 거의 없음 | 의무 | Appeal 옵션 |
| OKR | 표준 | 보조 | 사이클 policy 옵션 |
| 자기평가 | 단순 폼 | 상세 작성 | 폼 옵션 |

→ **performance 의 차별점**: 한국 정기 평가 + 글로벌 OKR 모두 수용하는 EvaluationPolicy 유연성.

---

## §9 결론

- 평가 방법론은 **상호 배타 아님** — 같은 회사가 동시 다중 운영
- performance 는 **방법론 조립 플랫폼**: EvaluationPolicy 가 사이클별로 KPI/OKR/MBO/Competency 가중·강제 분포·이의신청·사후 신고형을 옵션화
- **자매품 경계 보존**: 9-Box·Calibration 최종 → talent / 360 익명 → mra / 직무 카탈로그 → easy-job / 사원·조직 → hcm
- **한국 시장 특수성 흡수**: RatingDistribution(강제 분포 시뮬레이터) + Appeal + AchievementLog 3 카드 P0~P2 우선

다음 문서(`02_market_research.md`)에서는 글로벌 SaaS 10종의 화면 패턴 카탈로그를 추출하여 performance 가 채택할 9 패턴을 도출한다.

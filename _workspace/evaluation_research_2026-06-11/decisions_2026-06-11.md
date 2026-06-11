> 작성: 2026-06-11 / 작성자: easy-suite-orchestrator / 목적: 사용자 의사결정 G_PERF_E1~E10 + 자매품 경계 정정 박제

# 의사결정 박제 — G_PERF_E1 ~ E10 (사용자 일괄 동의)

사용자 응답 (2026-06-11): **"경계 침범이 아니라 데이터가 서로 연계되어 들어가야 할 것이다. 다음 단계 진행해줘. 모두 동의한다."**

→ `04_roadmap_and_priority.md §2` 권고안 **10건 전부 채택**. 권고와 다른 결정 0건 → 영향 매트릭스 별도 박제 불필요.

## §1 채택 결정 매트릭스

| ID | 영역 | 채택 옵션 | 운영 적용 지점 (P0-S?) |
|----|------|----------|----------------------|
| **E1** | 등급 분포 정책 | **C 혼합 (HYBRID)** — EvaluationPolicy.distributionMode = `FORCED` / `ABSOLUTE` / `HYBRID` 옵션화 | P0-S1 (Policy 옵션 컬럼) + P0-S4 (Calibration 시뮬레이터 분기) |
| **E2** | KPI 자동 수집 SoT | **단계적 A→B→C** — P0 = MANUAL 만, P1 = `KpiNode.source` 컬럼 박제 + hcm seam, P2 = 외부 BI | P0-S2 (KpiNode 컬럼 박제 + service-level enum SOURCE_MANUAL/SOURCE_HCM/SOURCE_EXTERNAL) |
| **E3** | OKR 점수 합산 | **A 단순 평균** + P1 weight 옵션 확장 | P1-S1 (Objective.scoreMode = `SIMPLE_AVG` 기본) |
| **E4** | 360 다면평가 | **A mra 위임 확정** + performance 1:1 자체 (mra `dbdcf96` min-rater 가드 동형) | P1-S4 (rm_mra_result 수신, payload 가드 = raterIds·individualScores 비포함) |
| **E5** | BSC 4 관점 | **B 자유 옵션** — `cycle.bscEnabled` + KpiNode.bscPerspective nullable | P0-S2 (KpiNode 컬럼 박제) + P2-S1 (전략 맵 view) |
| **E6** | Calibration / 9-Box | **A 분담** — Calibration 등급 분포 = performance / 9-Box 최종 배치 = talent (Matrix Grid `39283dc` SoT) | P0-S4 (performance Calibration 시뮬레이터) + P0-S7 (talent 송신) |
| **E7** | Appeal 운영 | **B 옵션** — `EvaluationPolicy.appealEnabled` | P0-S10 (Appeal 스켈레톤) + P2-S3 (풀 워크플로우) |
| **E8** | 사후 성과신고 cutoff | **B SELF_REVIEW 마감 D-3** | P1-S6 (AchievementLog cutoff 정책) |
| **E9** | 사원 점수 노출 | **A 익명 분포만** (등급 비율, 본인 결과 + 부서 분포 % 만) | P0-S5 (PerformanceReport view + 사원 dashboard) |
| **E10** | i18n 5 locale | **A 단계적** — P0 ko + en 풀 / P1 ja / P2 zh-CN + vi | P0~P2 i18n 전체 |

## §2 사용자 정정 흡수 — 자매품 경계 해석

### 정정 전 (리서치 문서 표현)
> "자매품 경계 = 침범 금지" / "Risk-2: 자매품 경계 침범 (9-Box / 360)"

### 정정 후 (사용자 명시)
> **"경계 침범이 아니라 데이터가 서로 연계되어 들어가야 할 것이다."**

### 해석 (확정)
- 자매품 분담은 **SoR(Source of Record) 위치**의 분담이지, **데이터 흐름의 차단**이 아니다.
- performance 가 자체 9-Box 화면을 가지지 않는 것은 _금지_ 가 아니라 _talent 가 SoR 이므로 talent 의 결과를 화면에 자연스럽게 노출(link out / embed / read-model)_ 하는 정합 결정.
- 360 결과는 mra SoR 이지만 performance 의 매니저 리뷰 화면에 **집계 카드로 노출**되어야 한다 (rm_mra_result 동결 소비).
- talent 의 9-Box 위치는 performance 의 사이클 리포트 화면에서 **참조 표시** 되어야 한다 (rm_talent_position 동결 소비, 단방향 read).
- hcm / easy-job 의 직무·역량 카탈로그는 performance 의 역량평가 폼에서 **드롭다운/트리 소비** 되어야 한다.

→ 결론: **경계 = SoR 분담 + 데이터 양방향 자연 흐름**. "침범"이라는 표현은 본 박제 이후 사용 금지. 후속 문서에서 "협업 / 연계 / SoR 위치" 로 대체.

### 영향
- `04_roadmap_and_priority.md §3 Risk-2` 문구 정정 → 별도 patch (Risk-2 = "자매품 경계 침범" → "자매품 SoR 분담 혼선" + 완화 = "본 박제 + talent rm_talent_position 수신 채널 신설 + Code Review 가이드")
- `03_domain_model_and_screens.md` 화면 카탈로그에 다음 노출 카드 신규:
  - 사이클 리포트 화면 (PerformanceReport view) — **9-Box 위치 카드** (talent rm_talent_position 소비, link to talent UI)
  - 매니저 리뷰 폼 — **360 집계 카드** (mra rm_mra_result 소비)
  - 역량평가 폼 — **Skill / Competency 트리** (easy-job rm_skill_set 소비)
- S2S 채널 누적 5종 (기존 4 + talent rm_talent_position 수신 1 신규):
  1. hcm → performance: `core-master` 수신 (rm_employee/org/assignment) — P0-S6
  2. talent ← performance: `performance-results` 송신 (FINALIZED 등급 + KPI 실적 + 역량 점수) — P0-S7
  3. mra → performance: `mra-results` 수신 (rm_mra_result 집계만, min-rater 가드) — P1-S4
  4. easy-job → performance: `job-catalog` 수신 (rm_skill_set, Competency 카탈로그 소비) — P1-S3
  5. **talent → performance: `talent-position` 수신 (rm_talent_position, 9-Box 위치 read-only)** — P1-S5 신규 (P0 추가 검토 필요)

## §3 권고와 다른 결정 0건

사용자가 권고안 그대로 전부 동의 → 영향 매트릭스 별도 박제 생략. 본 박제 1건이 의사결정 SoT.

## §4 다음 단계 즉시 진입

- **Action-1** (의사결정 박제) ✅ 본 파일
- **Action-2** (P0-S1 EvaluationCycle 골격) → 본 세션 진입
- **Action-3** (자매품 경계 박제) ✅ §2 + 별도 파일 `EVAL_SIBLING_BOUNDARY_2026-06-11.md`

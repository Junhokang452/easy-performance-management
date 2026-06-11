> 작성: 2026-06-11 / 작성자: easy-suite-orchestrator / 목적: 자매품 경계 = SoR 분담 + 데이터 양방향 자연 흐름 SoT (사용자 정정 흡수)

# 자매품 경계 박제 — performance ↔ talent / mra / hcm / easy-job

## §1 핵심 원칙

- 자매품 경계는 **SoR(Source of Record) 위치 분담**이지 **데이터 흐름의 차단**이 아니다.
- 화면은 **SoR 자매품의 데이터를 자유롭게 소비**한다 (read-model + link out + embed 카드).
- SoR 자매품에 **쓰기(write)**는 단방향 송신 + idempotency + sourceVersion 단조 + 503 fail-safe (talent `STAGE15` / store-hr `d95bf62` 패턴 동형).
- 익명성·PIPA·보안 가드는 **payload 수준**에서 강제 (rater_id 노출 금지 / 개인 점수 노출 금지 등).

## §2 S2S 채널 카탈로그 (누적 5종)

| # | 방향 | 채널 명 | 운반 데이터 | 가드 | 슬라이스 |
|---|------|--------|------------|------|---------|
| 1 | hcm → perf | `/api/internal/sync/core-master` | rm_employee / rm_organization / rm_assignment | Bearer + HMAC + sourceVersion + 미설정 503 | P0-S6 |
| 2 | perf → talent | (talent endpoint) `/api/internal/sync/performance-results` | PerformanceReview FINALIZED { reviewId, employeeId, cycleId, kpiScore, competencyScore, finalGrade, decidedAt, sourceVersion } | Bearer + HMAC + sourceVersion + Outbox-light | P0-S7 |
| 3 | mra → perf | `/api/internal/sync/mra-results` | rm_mra_result { employeeId, cycleId, aggregatedScore, raterCount, dimensionScores[] } — **raterIds·individualScores 비포함** | Bearer + HMAC + sourceVersion + min-rater 가드 (>=N) + 미설정 503 | P1-S4 |
| 4 | easy-job → perf | `/api/internal/sync/job-catalog` | rm_skill_set { skillSetId, skills[], jobCode, version } | Bearer + HMAC + sourceVersion + 미설정 503 | P1-S3 |
| 5 | talent → perf | `/api/internal/sync/talent-position` (**신규**, 사용자 정정 흡수) | rm_talent_position { employeeId, cycleId, nineBoxCell, readinessLevel, talentPoolId?, decidedAt, sourceVersion } — read-only | Bearer + HMAC + sourceVersion + 미설정 OFF (gracefully empty) | P1-S5 (또는 P0-S7 동시 양방향 신설 검토) |

## §3 화면 노출 카드 정합 (자매품 데이터 자연 노출)

| 화면 | 노출 카드 | 데이터 출처 | 인터랙션 |
|------|----------|------------|---------|
| 사이클 리포트 (`/my/cycles/{cycleId}/report`) | **9-Box 위치 카드** | rm_talent_position (talent SoR) | "talent 시스템에서 보기" 링크 — talent FE 39283dc 화면 deep link |
| 매니저 리뷰 폼 (`/manager/review/{empId}/{cycleId}`) | **360 집계 카드** (raterCount + dimensionScores 도넛) | rm_mra_result (mra SoR) | min-rater 미달 시 "표시 보류" 메시지 (익명성 보존) |
| 역량평가 폼 (`/employee/competency/{cycleId}`) | **Skill / Competency 트리** | rm_skill_set (easy-job SoR) | 트리 드롭다운 + 평가 척도 1~5 |
| HR Calibration 시뮬레이터 (`/hr/calibration/{cycleId}`) | **인접 talent 9-Box 미리보기** | rm_talent_position | 강제 분포 시뮬레이션 결과의 9-Box 영향 사전 미리보기 |
| 사원 dashboard (`/my/dashboard`) | **다음 Check-in / 다음 사이클 마일스톤** | EvaluationCycle + CheckIn | 즉시 진입 버튼 |

## §4 정합 가드 (Code Review 게이트)

- **PG-1**: performance 가 자체 9-Box 결정 entity 를 가지지 않는다. (TalentMatrixCell 같은 entity 금지 — talent SoR)
- **PG-2**: performance 가 raterId 또는 개별 rater 의 점수를 저장하지 않는다. (mra 익명성 보존 — mra ReportSnapshot 집계만 수신)
- **PG-3**: performance 가 직무·Skill 카탈로그 마스터를 자체 보관하지 않는다. (easy-job SoR — Competency entity 는 평가 인스턴스만 보관, 카탈로그는 read-model)
- **PG-4**: performance 가 사원·조직 마스터를 자체 보관하지 않는다. (hcm SoR — rm_employee/org/assignment read-model 만 보관)
- **PG-5**: talent / mra / easy-job 으로의 송신은 idempotency 보장 (sourceVersion 단조 + 멱등 키).

## §5 정합 패턴 참고 (자매품 모범)

- **talent**: store-hr 의 d95bf62 수신 패턴 (Bearer + HMAC + sourceVersion + 미설정 503 + 단조 idempotency) + STAGE15 4 채널 송수신 분리 + EvidenceSnapshot 불변 jsonb 동결.
- **mra**: dbdcf96 의 min-rater 가드 + 익명 집계만 반환 패턴.
- **hcm**: 송신측 c342838 (`hcm.s2s.easytalent.*` 명명 — 자매품 prefix 통일 + watermark 스케줄러 + 게이트 OFF 기본).
- **store-hr**: 1594c03 의 단계 2 실 활성 패턴 (default-tenant 게이트 + SPI 3종 + 게이트 ON fail-fast 순차).

## §6 정정 영향 — 후속 문서 patch

- `04_roadmap_and_priority.md §3 Risk-2` 문구 정정:
  - 기존: "자매품 경계 침범 (9-Box / 360)"
  - 정정: "자매품 SoR 분담 혼선 / 자체 구현 충동"
  - 완화 보강: "본 박제 + talent rm_talent_position 수신 채널 신설(§2 #5) + PG-1~PG-5 Code Review 게이트"
- `03_domain_model_and_screens.md` Part B 화면 카탈로그에 §3 노출 카드 5건 추가 (별도 patch 또는 다음 슬라이스 작성 시 자연스럽게 흡수).
- `04_roadmap_and_priority.md §1.2` P1-S5 (Talent Profile composite view) 의 입력으로 **talent → perf 수신 채널 #5** 명시.

## §7 다음 단계

- **즉시**: P0-S1 EvaluationCycle + EvaluationPolicy 골격 진입 (본 세션).
- **P0-S6 / S7 진입 시**: 본 박제 §2 의 채널 #1, #2 spec 그대로 구현 (talent `STAGE15` Bearer + HMAC 패턴 복사).
- **P1-S5 진입 시**: 채널 #5 (talent → perf 수신) 신설 — 사용자 정정 흡수 결과.

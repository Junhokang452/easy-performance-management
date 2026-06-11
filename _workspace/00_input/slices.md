> 작성: 2026-06-11 / 작성자: easy-suite-orchestrator / 목적: P0 10 슬라이스 사전 분할 + 세션 인계 SoT (토큰 위생 §1 정합)

# 작업 슬라이스 분할 — easy-performance-management 평가 도메인 확장 P0

> 사용자 의사결정(2026-06-11): G_PERF_E1~E10 권고안 일괄 채택. 경계 = SoR 분담 + 데이터 양방향 자연 흐름.
> 권고: **슬라이스당 1 세션 기준**, 작은 슬라이스는 2~3개 묶음 가능.

## P0 — Must (~12주, entity ~10, 화면 ~14)

- [x] **D**: 의사결정 박제 (`decisions_2026-06-11.md`) + 경계 정정 박제 (`EVAL_SIBLING_BOUNDARY_2026-06-11.md`)
- [x] **P0-S1**: EvaluationCycle + EvaluationPolicy + 8단계 상태기계 + HR 사이클 생성 모달 (1주) ✅ `9f42e50`
  - entity: `EvaluationCycle`, `EvaluationPolicy`
  - Flyway: `V20260611_001__evaluation_cycle_policy.sql`
  - 화면: `/hr/cycles` (#21), 사이클 생성 모달 (#22)
  - ErrorCode: E98 ~10건 (CYCLE_NOT_FOUND, POLICY_NOT_FOUND, CYCLE_INVALID_STATUS_TRANSITION, POLICY_INVALID_DISTRIBUTION, POLICY_INVALID_RATING_SCALE 등)
- [x] **P0-S2**: KpiTree + KpiNode + KpiAssignment + KpiActual + 가중치 검증 + BSC 라벨 옵션 (2주) ✅ (계약 `00_input/p0_s2_contract.md` + BE 64/64 + FE build + 경계 16/16)
  - entity 4건 + KpiNode.bscPerspective nullable + KpiNode.source enum 박제 (MANUAL / HCM / EXTERNAL)
  - 화면: KPI Tree (#2), KPI 입력 (#13), 매니저 KPI 모니터 (#18)
- [x] **P0-S3**: PerformanceReview + 자기/매니저 평가 폼 + 자동 점수 계산 + Self↔Manager 비교 (2주) ✅ (계약 `00_input/p0_s3_contract.md` + BE 98/98 + FE build + 경계 11/11)
  - entity 1건
  - 화면: 자기평가 폼 (#4), 매니저평가 폼 (#12), 비교 view
- [x] **P0-S4**: RatingDistribution + CalibrationSession + 강제 분포 시뮬레이터 (1.5주) ✅ (계약 `00_input/p0_s4_contract.md` + BE 135/135 + FE build + 경계 11/11 — 화면 실제 = #19+#24+#28 3 라우트)
  - entity 2건
  - 화면: HR 분포 시뮬레이터 (#19), Calibration 시뮬레이터 (#28)
  - 정합: E1 = HYBRID 옵션, E6 = performance Calibration / talent 9-Box
- [x] **P0-S5**: PerformanceReport append-only + HR 일괄 발행 + 사원 결과 조회 (1주) ✅ (계약 `00_input/p0_s5_contract.md` + BE 150/150 + FE build + 경계 7/7 — 화면 실제 = #7 `/my/report` + #26 `/hr/reports`)
  - entity 1건 (append-only, supersedes 체인 옵션)
  - 화면: 사이클 리포트 (#7), 사원 dashboard 결과 카드 (#26)
  - 정합: E9 = 본인 + 부서 분포 % 만
- [ ] **P0-S6**: hcm S2S 수신 (rm_employee/org/assignment, talent STAGE15 패턴 동형) (1주)
  - read-model 3 테이블 + endpoint `/api/internal/sync/core-master`
  - 가드: Bearer + HMAC + sourceVersion + 미설정 503
- [ ] **P0-S7**: talent S2S 송신 (FINALIZED → performance-results) (0.5주)
  - 송신측 watermark + Outbox-light
  - 정합: 경계 박제 §2 채널 #2
- [ ] **P0-S8**: 기존 4 도메인 흡수 매핑 + view + idempotent 마이그 (1주)
  - PersonalOkr → Okr+Objective+KeyResult (P1-S1 의존이지만 마이그 박제만 P0 에서)
  - ReflectionJournal/MentorFeedback: cycleId nullable 추가, history 보존
- [ ] **P0-S9**: i18n + ErrorCode E98 확장 (~40 신규 누적) + 사원 dashboard (1주)
  - 화면: `/my/dashboard` (#1)
  - i18n: ko + en 풀 (E10 권고)
- [ ] **P0-S10**: E2E + 회귀 + Appeal 스켈레톤 (1주)
  - Appeal entity 골격 (P2-S3 풀 워크플로우 의존)
  - 화면: HR Appeal 큐 (#8)

## P1 — Should (~8주, 별도 세션 권장)

- [ ] **P1-S1**: Okr + Objective + KeyResult + alignment 트리 (2주)
- [ ] **P1-S2**: CheckIn (주간 + 1:1 + Reflection 흡수) (1주)
- [ ] **P1-S3**: CompetencyFramework + Competency + Assessment + BARS + easy-job rm_skill_set 수신 (2주)
- [ ] **P1-S4**: mra rm_mra_result 수신 + 매니저 review 360 카드 (0.5주)
- [ ] **P1-S5**: Talent Profile composite + **talent rm_talent_position 수신 (경계 박제 §2 채널 #5 신규)** (1주)
- [ ] **P1-S6**: AchievementLog (사후 성과신고, E8 cutoff = SELF_REVIEW D-3) (1주)
- [ ] **P1-S7**: Director dashboard (0.5주)

## P2 — Nice (~6주, 별도 세션 권장)

- [ ] **P2-S1**: BSC 4 관점 전략 맵 view 강화
- [ ] **P2-S2**: Recognition + Feedback (CFR)
- [ ] **P2-S3**: Appeal 풀 워크플로우 (E7 옵션)
- [ ] **P2-S4**: CareerDevelopmentPlan
- [ ] **P2-S5**: EngagementPulse (mra 위임 검토)
- [ ] **P2-S6**: audit log + S2S config 관리 화면

## 세션 인계 규칙 (토큰 위생 §재개 패턴)

- 각 슬라이스 종료 시 `_workspace/checkpoint.md` 갱신 (덮어쓰기, 최신 1개)
- 다음 세션 재개 트리거: "이어서 진행" / "P0-S{N} 진입" / "performance 작업 재개"
- 작업 디렉터리: `/home/samsung/code/easy-performance-management/`
- SoT 문서:
  - 의사결정: `_workspace/evaluation_research_2026-06-11/decisions_2026-06-11.md`
  - 경계: `_workspace/evaluation_research_2026-06-11/EVAL_SIBLING_BOUNDARY_2026-06-11.md`
  - ERD/화면: `_workspace/evaluation_research_2026-06-11/03_domain_model_and_screens.md`
  - 로드맵: `_workspace/evaluation_research_2026-06-11/04_roadmap_and_priority.md`
  - 본 슬라이스: `_workspace/00_input/slices.md`

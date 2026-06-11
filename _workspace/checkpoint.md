> 작성: 2026-06-11 / 작성자: easy-suite-orchestrator / 목적: P0-S3 완료 시점 체크포인트 — 다음 세션 재개 진입점

# 작업 체크포인트 — easy-performance-management 평가 도메인 P0

**현재 작업**: easy-performance-management 평가 도메인 확장 (KPI / OKR / MBO / Competency / Cycle 등 P0~P2 26주 로드맵). 사용자 의사결정 G_PERF_E1~E10 일괄 채택 + 경계 = SoR 분담 + 데이터 양방향 자연 흐름.

## 완료

- [x] **D**: 리서치·설계 5 파일 (`_workspace/evaluation_research_2026-06-11/00~04*.md`) + 의사결정 박제 + 경계 정정 박제
- [x] **P0-S1**: EvaluationCycle + EvaluationPolicy 골격 (commit `9f42e50`) — BE 44/44 + FE typecheck/build ✅. 박제 `P0_S1_BACKEND/FRONTEND_2026-06-11.md`
- [x] **P0-S2**: KPI 도메인 (commit `7926f99`, 40 파일 +6,058) — 계약 선작성 패턴 1호 (`00_input/p0_s2_contract.md`), BE 64/64 (신규 KpiServiceTest 20) + FE lazy 3 + 경계 16/16 일치. 박제 `P0_S2_BACKEND/FRONTEND_2026-06-11.md`
- [x] **P0-S3**: PerformanceReview — 자기/매니저 평가 + 자동 점수 계산 + Self↔Manager 비교
  - 계약: `_workspace/00_input/p0_s3_contract.md` (REST 11 endpoint + ReviewStatus 10 enum 중 P0 전이 4 + submit 전용 2 + ErrorCode 10 + §5 산식 SoT) — BE·FE 병렬 위임, **계약 이탈 0 실측**
  - BE 신규 8 + 수정 1: `domain/review/` (ReviewStatus/PerformanceReview/Repository/ReviewDtos/ReviewService/ReviewController 11 endpoint) + Flyway `V20260611_003__performance_review.sql` + ErrorCode 10건(E9804447/240~245/928~930) + ReviewServiceTest **34**
  - FE 신규 9 + 수정 3: `api/reviews.ts` (RQ 11훅 + 전이맵 + §5 프리뷰 산식) + `/my/self-review` (#4 빈상태·잠금) + `/manager/review` (#12 Tabs 2: KPI 채점 / Self↔Manager 비교) + `pages/review/` 6 (StatusBadge·KpiItemsTable·TransitionMenu·CreateModal 개별+일괄·Comparison·errorMapping)
  - 게이트 실측: BE `gradlew test` **98/98** (회귀 64 + 신규 34, XML failures 합 0) / FE typecheck 0 + build ✅ (MySelfReview 4.08 / ManagerReview 20.59 kB lazy)
  - 경계 QA: BE 11 매핑 ↔ FE 8 고유경로×verb 전수 일치 + ReviewResponse 17필드 표본 일치 + legacy selfevaluation 비접촉 (git status 0)
  - 핵심 결정: `kpi_score_detail` jsonb 이중 용도(MANAGER_PENDING draft 부분배열 비노출 / submit-manager 풀 11필드 동결 스냅샷 노출) / 점수 산식 BE 유일 계산자(FE 프리뷰는 표시용 명시) / submit 전이는 transition endpoint 거부 / finalGrade 밴드 S_A_B_C_D 한정(S≥90/A≥80/B≥70/C≥60, P0-S4 policy 설정화 후속)
  - 박제: `_workspace/P0_S3_BACKEND_2026-06-11.md` + `_workspace/P0_S3_FRONTEND_2026-06-11.md`

## 다음 슬라이스

- **P0-S4**: RatingDistribution + CalibrationSession + 강제 분포 시뮬레이터 (1.5주, **다음 세션 권장**)
  - entity 2건: CalibrationSession (PLANNED→IN_SESSION→ADJUSTED→CONFIRMED→CLOSED + adjustmentLog JSONB + participantIds) + RatingDistribution (policyDistribution/actualDistribution/forcedApplied/simulationLog JSONB, orgUnitId NULL=전사)
  - 화면 2건: HR 분포 시뮬레이터 + Calibration 그리드 — slices.md 는 #19/#28, ERD 카탈로그는 #19 `/director/calibration/{cycleId}` + #24 `/hr/distribution/{cycleId}` (번호 드리프트 — 계약서 작성 시 03_domain_model_and_screens.md 카탈로그 라인 248~263 재확인)
  - 의존: P0-S1 policy(distributionMode HYBRID/FORCED + forcedDistribution) + P0-S3 reviews (MANAGER_SUBMITTED→CALIBRATION→FINALIZED 전이 + finalGrade 재산정 — 분포 적용이 review.finalGrade 를 조정하는 경로 설계 필요)
  - 결정 정합: E1 HYBRID 옵션 분기 + E6 분담 (performance = Calibration 등급 분포 / talent = 9-Box — talent Matrix Grid `39283dc` 와 경계 박제 §2 채널 유지)
  - 핵심 설계 질문 (계약서에서 고정할 것): ① 분포 시뮬레이션 = 저장 없는 계산 endpoint vs simulationLog append ② forced 적용 시 review.finalGrade 일괄 UPDATE 의 잠금 조건 (CALIBRATION 상태 한정) ③ ERD Entity 16/17 의 cycle 당 다중 세션(org 단위) UNIQUE 정책
  - SoT 참조: `_workspace/evaluation_research_2026-06-11/03_domain_model_and_screens.md` Entity 16/17 (line 103~112) + Wireframe(캘리브레이션) + 시나리오 line 540~555
  - **권장 절차 (P0-S2/S3 확립 패턴)**: 계약서 `_workspace/00_input/p0_s4_contract.md` 선작성 → BE·FE general-purpose 병렬 위임 → 실검증(git status + 게이트 + XML 수치 + 경계 grep) → 단일 커밋
  - 예상 변경: BE 신규 ~12 / FE 신규 ~10 / 변경 ~5

## 컨텍스트 요약 (재개 시 읽기)

- **작업 도메인**: easy-performance-management 평가 도메인 P0 (12주 로드맵 / 10 entity / 14 화면) — 진행 4/10 슬라이스 (S1~S3 + D)
- **적용 표준**: easy-standards v0.5.0 + ADR-013(Model B) + ADR-024 + ADR-031(B2B-Enterprise per-tenant + SMB 옵션) + ADR-026(명명) + ADR-027(i18n)
- **영향 저장소**: `easy-performance-management/` 단일 (P0-S6 부터 hcm 수신 + P0-S7 부터 talent 송신)
- **핵심 결정 SoT**: `_workspace/evaluation_research_2026-06-11/decisions_2026-06-11.md`
- **자매품 경계 SoT**: `_workspace/evaluation_research_2026-06-11/EVAL_SIBLING_BOUNDARY_2026-06-11.md`
- **ERD/화면 SoT**: `_workspace/evaluation_research_2026-06-11/03_domain_model_and_screens.md`
- **슬라이스 체크리스트 SoT**: `_workspace/00_input/slices.md`
- **슬라이스 실행 컨벤션 (P0-S2/S3 확립)**: 계약서 `00_input/p0_s{N}_contract.md` 선작성(엔드포인트·shape·ErrorCode·화면·산식 고정) → BE·FE general-purpose 병렬 위임 → 오케스트레이터 실검증 → 단일 커밋
- **ErrorCode 사용 현황 (E9804)**: 404 = 441~447 사용(다음 448) / 422 = 231~245 사용(다음 246) / 409 = 921~930 사용(다음 931) / AUTH = 101~104·415
- **push 정책**: main 직push 는 분류기 차단 — 사용자 명시 승인 영역. 현재 로컬 ahead (P0-S1~S3 커밋)

## 재개 명령어

새 세션에서: **"이어서 진행"** 또는 **"P0-S4 진입"** 또는 **"performance 작업 재개"**

오케스트레이터가 본 파일 + slices.md 를 읽고 다음 슬라이스부터 진입.

## 변경 이력

| 날짜 | 슬라이스 | 결과 |
|------|---------|------|
| 2026-06-11 | 리서치·설계 5 + 의사결정 박제 + 경계 정정 박제 | 5 파일 (1,433 라인) + 의사결정 10건 일괄 채택 + S2S 채널 5 박제 |
| 2026-06-11 | P0-S1 EvaluationCycle + EvaluationPolicy | BE 44/44 + FE typecheck/build ✅ + E97→E98 fix (commit `9f42e50`) |
| 2026-06-11 | P0-S2 KPI 도메인 (Tree/Node/Assignment/Actual) | BE 64/64 (신규 20) + FE lazy 3 + 경계 16/16 + 계약 이탈 0 (commit `7926f99`) |
| 2026-06-11 | P0-S3 PerformanceReview (자기/매니저 평가 + 자동 점수) | BE 98/98 (신규 34) + FE lazy 2 + 경계 11/11 + ReviewResponse 17필드 일치 + 계약 이탈 0 |

# easy-performance-management DS UI Audit (2026-06-13)

## 결과

- 프런트 소스 로컬 스타일 잔존: `hex=0`, `inline-style=0`
- CSS module 잔존: 0 (`App.module.css`, `LoginPage.module.css` 제거)
- 강화 게이트: `frontend-vite/package.json` `design:check`를 `--max-hex=0 --max-inline-style=0`로 상향
- 빌드 검증: `npm run build` 통과

## DS Variant 등록

`@easy/ui-components/performance`

- `PerformanceDistributionBars`
  - 등급 분포, 본인 등급 강조, 목표 비율 병기
  - `mobileSize: compact | comfortable`
- `PerformanceHierarchyList`
  - KPI/OKR 계층 목록, depth indent 표준화
  - `mobileSize: compact | comfortable`
- `PerformanceSelectableSurface`
  - 선택 가능한 트리/카드 표면
- `PerformanceChangedTableRow`
  - 선택/변경 행 강조 및 interactive cursor
- `PerformancePreWrapText`
  - 코멘트/회고/피드백 장문 표시
- `PerformanceWeightStatusBadge`
  - KPI 가중치 합 상태
- `PerformanceMetricGrid`
  - 성과 지표 카드 묶음 표준화 (`StatCard` 직접 사용을 화면에서 제거)
  - `mobileSize: compact | comfortable`
  - `columns`로 2열/4열 등 고정 포맷 화면 대응
- `PerformanceProgressSummary`
  - 라벨 + `value/total (%)` + 진행 막대 표준화
  - `mobileSize: compact | comfortable`
- `formatPerformanceRatioNumber`, `formatPerformanceRatioPercent`, `formatPerformanceRatioText`
  - 성과 화면 전반의 비율 계산/표시 문자열 통일
- `PerformanceScoreGrid`
  - KPI/최종/분해 점수 타일 표준화
  - `mobileSize: compact | comfortable`
  - `columns`로 2열/4열 포맷 대응
- `PerformanceCommentPanel`
  - 자기평가/매니저 코멘트 비교 패널 표준화
  - `mobileSize: compact | comfortable`
- `PerformanceGroupedListGrid`
  - BSC 관점/범주별 그룹 리스트 카드 표준화
  - `mobileSize: compact | comfortable`
- `PerformanceRecordCard`
  - 회고/멘토 피드백/개인 OKR처럼 메타+배지+본문 또는 진행률을 가진 기록 카드 표준화
  - `mobileSize: compact | comfortable`
- `PerformanceLogEntryCard`
  - 분포 시뮬레이션/보정 조정 이력처럼 primary+secondary+timestamp+details를 가진 로그 카드 표준화
  - `mobileSize: compact | comfortable`
- `PerformanceStatusIconGroup`
  - 열람/확인처럼 테이블 안에 들어가는 상태 아이콘 묶음 표준화
  - `mobileSize: compact | comfortable`

`@easy/ui-components/forms`

- `FormSegmentedControl`
  - 페르소나/모드 전환처럼 폼 맥락의 segmented control 사용을 DS 래퍼로 표준화
  - Mantine 원시 컴포넌트 직접 import 제거용 공통 표면

## 성과관리 리서치 반영 방향

확인한 현대 성과관리 제품 흐름:

- Lattice: reviews, real-time feedback, praise, calibration, promotions, succession, 1:1s, updates를 한 성과 흐름으로 묶음.
- Culture Amp: goal setting, feedback, performance evaluations, calibration views로 공정성과 피드백 품질을 관리.
- Betterworks: Goals & OKRs, Conversations/1:1s, Feedback, Calibration, Analytics를 연속 성과관리 플랫폼으로 구성.
- Workday 계열: 목표, 체크인, 보정, 분석을 성과관리의 핵심 경험으로 배치.

다음 화면 확장 후보:

- Performance cockpit: cycle 진행률, 미제출 리뷰, calibration readiness, report publish 상태를 한 화면에 집계. ✅ `2026-06-14` `/` 첫 화면으로 구현.
- Goal alignment: 개인 OKR/KPI와 조직 KPI tree를 나란히 비교하는 alignment view. ✅ `2026-06-14` `/kpi/alignment` 화면으로 구현.
- Calibration analytics: 분포 막대 + 변경 행 + 근거 코멘트 타임라인을 한 workspace로 통합. ✅ `2026-06-14` `/hr/calibration-analytics` 화면으로 구현.
- Employee result packet: KPI, manager comment, distribution percentile, development actions를 하나의 읽기 전용 리포트로 구성. ✅ `2026-06-14` `/my/report` 결과 패킷으로 구현.
- HR report governance: 발행 readiness, 열람/확인 진행률, 재발행 이력을 운영 대시보드로 구성. ✅ `2026-06-14` `/hr/reports` 화면으로 구현.
- Manager review workspace: 평가 대기열, 채점 대기, 제출률, 선택 대상 score context, 채점 완료도를 매니저 업무 화면에 통합. ✅ `2026-06-14` `/manager/review` 화면으로 구현.
- Cycle operating timeline: 사이클 상태 분포, 정책 준비율, 운영/전이 가능 사이클을 HR 사이클 화면에 통합. ✅ `2026-06-14` `/hr/cycles` 화면으로 구현.
- KPI director operating view: KPI 트리 포트폴리오, BSC coverage, 노드/가중치/배정 요약을 본부 읽기 화면에 통합. ✅ `2026-06-14` `/director/kpi-tree` 화면으로 구현.

## DS Extraction Cleanup Slice (2026-06-14)

- 변경 DS: `lib/easy-platform/easy-platform-core/packages/ui-components/src/performance/Performance.tsx`
- 신규 Variant: `PerformanceMetricGrid`, `PerformanceProgressSummary`, `PerformanceScoreGrid`, `PerformanceCommentPanel`, `PerformanceGroupedListGrid`, `PerformanceRecordCard`, `PerformanceLogEntryCard`, `PerformanceStatusIconGroup`, `formatPerformanceRatio*`
- 적용 화면: `/`, `/kpi/alignment`, `/hr/calibration-analytics`, `/my/report`, `/hr/reports`, `/manager/review`, `/hr/cycles`, `/director/kpi-tree`
- 결과: `frontend-vite/src/pages` 직접 `StatCard` 사용 0건, 반복 `ratioNumber/ratioPercent/ratioText` helper 제거, 로컬 `ScoreTile`/`ScoreSummary`/`CommentCard` 제거, KPI BSC 그룹 카드 DS화
- 추가 결과: reflection/mentor/OKR 목록 카드, calibration/distribution log card DS화
- 추가 결과: Login form card는 `ProductLoginShell`, AdminTenants console cards는 `SurfaceCard`로 전환
- 추가 결과: HR report 상태 아이콘 묶음은 `PerformanceStatusIconGroup`, AdminTenants 오류 tooltip은 `EasyTooltip`으로 전환
- 추가 결과: Login form controls는 `FormTextInput`/`FormPasswordInput`/`FormSegmentedControl`/`PrimaryButton`, AdminTenants form/actions는 `FormTextInput`/`FormActions`/DS Button으로 전환
- 추가 결과: Cycle create/edit/policy modal controls는 `FormTextInput`/`FormSelect`/`FormActions`/DS Button으로 전환
- 잔여 후보: kpi/review/report/calibration modal의 직접 Mantine primitive 사용 중 DS 승격 가치가 있는 작은 패턴
- 검증: `npm run typecheck`, `npm run design:check`, `npm run build`, `git diff --check` 통과

## Cockpit Slice (2026-06-14)

- 신규 화면: `frontend-vite/src/pages/CockpitPage.tsx`
- 라우팅: `/`를 자기평가 대신 cockpit으로 전환, 자기평가는 `/self-evaluations` 유지
- 데이터: 기존 `cycles`, `reviews`, `distribution`, `reports` react-query 훅 조합
- DS 준수: `PerformanceMetricGrid`, `SectionCard`, `PerformanceDistributionBars` 사용, local style 0 유지
- 검증: `npm run typecheck`, `npm run design:check`, `npm run build` 통과

## Goal Alignment Slice (2026-06-14)

- 신규 화면: `frontend-vite/src/pages/GoalAlignmentPage.tsx`
- 라우팅: `/kpi/alignment`
- 데이터: 기존 `useKpiTreesQuery`, `useKpiTreeDetailQuery`, `useMyKpiAssignmentsQuery` 조합
- 주요 UX: 조직 KPI hierarchy와 개인 KPI 배정을 좌우 비교, 매칭률/평균 달성률/미매칭 KPI 집계
- DS 준수: `PerformanceMetricGrid`, `SectionCard`, `PerformanceHierarchyList` 사용, local style 0 유지
- 검증: `npm run typecheck`, `npm run design:check`, `npm run build` 통과

## Calibration Analytics Slice (2026-06-14)

- 신규 화면: `frontend-vite/src/pages/CalibrationAnalyticsPage.tsx`
- 라우팅: `/hr/calibration-analytics`
- 데이터: 기존 `useCalibrationSessionsQuery`, `useCalibrationSessionQuery`, `useDistributionQuery` 조합
- 주요 UX: 세션 지표, 현재/목표 등급 분포, 세션 목록, 선택 세션 adjustment timeline 통합
- DS 준수: `PerformanceMetricGrid`, `SectionCard`, `DistributionBars`, `PerformancePreWrapText` 사용, local style 0 유지
- 검증: `npm run typecheck`, `npm run design:check`, `npm run build` 통과

## Employee Result Packet Slice (2026-06-14)

- 변경 화면: `frontend-vite/src/pages/MyReportPage.tsx`
- 보조 정리: `frontend-vite/src/pages/HrReportsPage.tsx` 테이블 로컬 `styles` 제거
- 데이터: 기존 `ReportResponse.content` 동결 스냅샷만 사용, API 변경 없음
- 주요 UX: 최종 등급/점수/열람/확인 상태 패킷, 점수 분해, KPI 스냅샷, 매니저 코멘트, 전사 분포, 후속 성장 액션 슬롯 통합
- DS 준수: `PerformanceMetricGrid`, `SectionCard`, `ReportDistributionBars`, `PerformancePreWrapText` 사용, local style 0 유지
- 검증: `npm run typecheck`, `npm run design:check`, `npm run build` 통과

## HR Report Governance Slice (2026-06-14)

- 변경 화면: `frontend-vite/src/pages/HrReportsPage.tsx`
- 데이터: 기존 `useReviewsByCycleQuery`, `useReportsByCycleQuery` 조합, API 변경 없음
- 주요 UX: 발행 대기/active 리포트/열람률/확인률 지표, 발행·열람·확인 진행률, active 리포트 목록, superseded 이력 목록 분리
- DS 준수: `PerformanceMetricGrid`, `PerformanceProgressSummary`, `SectionCard`, Mantine table 토큰 기반 사용, local style 0 유지
- 검증: `npm run typecheck`, `npm run design:check`, `npm run build` 통과
## Manager Review Workspace Slice (2026-06-14)

- 변경 화면: `frontend-vite/src/pages/ManagerReviewPage.tsx`
- 데이터: 기존 `useReviewsByCycleQuery`, `useReviewKpiItemsQuery` 조합, API 변경 없음
- 주요 UX: 평가 대기열 지표, 매니저 채점 대기 수, 제출 이후 진행률, 평균 KPI 점수, 선택 대상 score context, KPI 채점 완료도 표시
- DS 준수: `PerformanceMetricGrid`, `PerformanceProgressSummary`, `SectionCard`, `PerformanceChangedTableRow` 사용, local style 0 유지
- 검증: `npm run typecheck`, `npm run design:check`, `npm run build` 통과
## Cycle Operating Timeline Slice (2026-06-14)

- 변경 화면: `frontend-vite/src/pages/CyclesPage.tsx`
- 데이터: 기존 `useCyclesQuery` + `getAllowedNextStatuses` 조합, API 변경 없음
- 주요 UX: 전체 사이클 수, 정책 준비율, 운영 중 사이클, 전이 가능 사이클, 상태별 타임라인/분포 표시
- DS 준수: `PerformanceMetricGrid`, `PerformanceProgressSummary`, `SectionCard`, Mantine timeline progress 토큰 기반 사용, local style 0 유지
- 검증: `npm run typecheck`, `npm run design:check`, `npm run build` 통과
## KPI Director Operating View Slice (2026-06-14)

- 변경 화면: `frontend-vite/src/pages/DirectorKpiTreePage.tsx`
- 데이터: 기존 `useKpiTreesQuery`, `useKpiTreeDetailQuery` 조합, API 변경 없음
- 주요 UX: 트리 포트폴리오 수, BSC 적용률, 조직 범위 트리 수, 레벨 수, 선택 트리의 노드 수, BSC 지정률, 가중치 완결률, 배정 합계 표시
- DS 준수: `PerformanceMetricGrid`, `PerformanceProgressSummary`, `SectionCard`, `PerformanceSelectableSurface` 사용, local style 0 유지
- 검증: `npm run typecheck`, `npm run design:check`, `npm run build` 통과

# 작업 체크포인트

**작성**: 2026-06-14
**현재 작업**: easy-performance-management DS 전환 + Performance cockpit + Goal alignment + Calibration analytics + Employee result packet + HR report governance + Manager review workspace + Cycle operating timeline + KPI director operating view + stats/progress/score/comment/grouped-list/record/log DS Variant 추출 + 앱 카드 표면 DS 전환

## 완료

- [x] DS audit 강화: `hex=0`, `inline-style=0`
- [x] 성과관리 전용 DS Variant: `@easy/ui-components/performance`
- [x] stats/progress DS Variant 추출: `PerformanceMetricGrid`, `PerformanceProgressSummary`, `formatPerformanceRatio*`
- [x] score/comment/grouped-list DS Variant 추출: `PerformanceScoreGrid`, `PerformanceCommentPanel`, `PerformanceGroupedListGrid`
- [x] record/log DS Variant 추출: `PerformanceRecordCard`, `PerformanceLogEntryCard`
- [x] 페이지 직접 `StatCard` 사용 제거 (`rg "StatCard" frontend-vite/src/pages` = 0)
- [x] 로컬 `ScoreTile`, `ScoreSummary`, `CommentCard` 제거
- [x] KPI BSC 그룹 카드 로컬 `Card/SimpleGrid` 제거
- [x] reflection/mentor/OKR 목록 카드와 calibration/distribution log card DS화
- [x] Login form card → `ProductLoginShell` 전환
- [x] Login form controls → `FormTextInput`/`FormPasswordInput`/`FormSegmentedControl`/`PrimaryButton` 전환
- [x] AdminTenants console cards → `SurfaceCard` 전환
- [x] AdminTenants form/action controls → `FormTextInput`/`FormActions`/DS Button 전환
- [x] Cycle create/edit/policy modal controls → `FormTextInput`/`FormSelect`/`FormActions`/DS Button 전환
- [x] 앱 페이지 직접 `Card` 사용 제거 (`rg "<Card|Card," frontend-vite/src/pages` 직접 사용 0)
- [x] table status icon cluster → `PerformanceStatusIconGroup` 전환
- [x] 앱 페이지 직접 `ThemeIcon`/`Tooltip` 사용 제거 (`EasyTooltip`/DS Variant 경유)
- [x] 앱 로컬 CSS module 제거
- [x] `/` Performance cockpit 첫 화면 구현
- [x] `/kpi/alignment` Goal alignment view 구현
- [x] `/hr/calibration-analytics` Calibration analytics workspace 구현
- [x] `/my/report` Employee result packet 구현
- [x] `/hr/reports` HR report governance 구현
- [x] `/manager/review` Manager review workspace 구현
- [x] `/hr/cycles` Cycle operating timeline 구현
- [x] `/director/kpi-tree` KPI director operating view 구현
- [x] `npm run typecheck`, `npm run design:check`, `npm run build` 통과

## 다음 슬라이스

- Residual local UI audit
  - `frontend-vite/src/pages`에서 남은 반복 UI 후보 전수 점검
  - 잔여 후보: kpi/review/report/calibration modal의 직접 Mantine primitive 사용 중 DS 승격 가치가 있는 작은 패턴
  - 목표: DS Variant 추가가 필요한 것은 `@easy/ui-components/performance`로 이동, 화면은 데이터 조립만 유지

## 재개 명령어

새 세션에서: `다음 진행`

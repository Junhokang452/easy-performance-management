# S4 맞춤 PDF 프런트 계획 (read-only)

근거: `00_input/task.md`는 S4를 “기존 결과/권한/다운로드 재사용 → 안전한 출력 옵션·한글 렌더·페이지 나눔”으로 한정한다.

- HR 결과 표면은 `features/evaluation-programs/pages/AnalyticsPage.tsx`, `pages/PersonalReportPage.tsx` 및 `api/programs.ts`의 result/report query 계열이다.
- 직원 결과 표면은 `pages/EvaluationWorkPage.tsx`의 `ResultPanel`과 `pages/PersonalReportPage.tsx`; 공개 여부는 서버의 결과/feedback 권한이 SoT다.
- 다운로드 재사용점은 `components/downloadBlob.ts`; API 오류는 `api/error.ts`의 `getErrorMessage`, 화면은 `QueryState`, UI는 `@easy/ui-components` wrapper를 사용한다.
- 기존 PDF 생성 endpoint/library는 없고, attachment PDF download만 `ResourceController` employee actor 경로로 존재한다. S4는 서버 PDF bytes+Content-Disposition 계약이 필요하다.

## 최소 UI 제안

HR/허용된 직원 결과 카드의 “PDF 다운로드” 버튼 → `UiModal`에서 언어, 상세 포함 여부, 페이지 구분 옵션을 선택 → 서버 preview/생성 요청 → blob 다운로드. 클라이언트는 결과·점수·한글 레이아웃을 재계산/렌더링하지 않고 서버 bytes만 저장한다. 권한 403, 생성 실패, 빈 결과는 `getErrorMessage`로 표시한다.

## 계약 전 예상 변경 5개

1. `api/customPdf.ts` (RQ mutation/blob response)
2. `components/CustomResultPdfModal.tsx`
3. `pages/HrReportsPage.tsx` 또는 확정된 HR 결과 mount
4. `pages/PersonalReportPage.tsx` 또는 확정된 직원 결과 mount
5. `programI18n.ts` 5 locale PDF namespace

backend 계약에서 결과 scope, HR/self visibility, immutable result revision, option allowlist, filename/content type, pagination/한글 폰트 실패 shape를 먼저 확정해야 한다.

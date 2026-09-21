# S4 frontend verification

2026-09-08. Astra implemented API/modal/integration and completed the 5-locale dictionary after Terra's preliminary plan/draft. Source is the UNC repository; execution uses the existing Windows frontend validation copy. No npm install/shared library change.

## Implemented

- AnalyticsPage ResultsOverview: separate custom PDF action; existing XLSX retained.
- Request-only local UI state, React Query download mutation, existing apiClient/CSRF/refresh and downloadBlob.
- Explicit locale ko/en; orientation; sections; fixed columns; title. Invalid portrait columns are explained, not silently removed. Table-off omits columns. In-flight close/duplicate-click guards; cancel does not submit; errors stay recoverable.
- RESULT_PDF_EXPORTED audit filter and localized label.
- UI ko/en/ja/zh-CN/vi dictionaries fully translated; PDF document label language remains ko/en.

## Executed gates

- `tsc -b --pretty false`: PASS.
- direct Vite build: PASS, 26.01s. Existing shared client >500kB warning remains (~753.75kB); not a build failure.
- Node tests: 17/17 PASS (previous 12 + 5 PDF locale cases).
- Design system: hex=0/0, inline-style=0/0; local UI tags=0/0, blocked-imports=0/0.
- Scoped `git diff --check`: PASS (backend/src, frontend-vite/src, scripts).

## Final runtime and parity

- Actual HTTP45/45; browser15/15 including 5 locales × 1440/390 downloads, cancellation, portrait validation, typed422/retry and employee403. No page errors.
- R05 shared JSON Accept negotiation failure was fixed with explicit Accept */*, matching existing XLSX/SVG. Final tsc/build (1.71s), Node17, DS/localUI re-run PASS. Browser asserts Accept header and actual PDF bytes.
- Final source/temp hash9/9 (8 changed FE/test files plus regenerated OpenAPI). Generated schema includes actual results.pdf request and RESULT_PDF_EXPORTED.
- Main agent inspected final Korean desktop and Vietnamese mobile screenshots with transitions disabled; readable labels/options, no horizontal document overflow. Full 10-image evidence in browser/.
- S1/S2/S3 browser regression13/16/16 PASS; total S1~S4 60.

Browser harness: `scripts/verify-custom-result-pdf-browser.cjs`. Actual source fixture: `scripts/verify-custom-result-pdf-local.py`. Test data is synthetic and restricted to loopback8089/PG55489. PDF snapshot seeding is not a claim of full workflow completion.

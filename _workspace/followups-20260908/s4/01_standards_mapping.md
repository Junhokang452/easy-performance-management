# S4 표준 매핑 및 Phase 3a 설계 게이트

작성일: 2026-09-08  
담당: Luna (quality-gate)  
범위: easy-performance-management의 기존 HR Analytics 결과에 대한 맞춤 PDF 다운로드 설계만 검토  
판정: **Phase 3a 설계 PASS (HR 전용·조건부)** / **제품 구현·실제 PDF 검증은 pending**

## 1. 판정 요약

`_workspace/followups-20260908/s4/01_backend_contract.md`와 `01_frontend_plan.md`를 현재
소스·easy-standards SoT와 대조했다. S4의 최소 범위는 다음으로 고정한다.

- `POST /api/v1/evaluation-programs/{programId}/results.pdf` 한 개의 동기 binary endpoint.
- 기존 `ProgramAnalyticsService.resultSummary(actor, programId)`의 HR 결과 DTO를 유일한 결과
  source of truth로 재사용하며 PDF에서 점수·등급·분포를 다시 계산하지 않는다.
- `HR_ADMIN`/`SUPER_ADMIN` operator만 허용한다. 직원별 PDF, 공유 링크, 저장 문서, 메일·스케줄러,
  공개 토큰, 새 발행/공개 정책은 S4에 포함하지 않는다.
- locale(`ko|en`), orientation, sections, participant columns, 제한된 title만 allowlist로 받고
  HTML/CSS/URL/image/font/template을 요청으로 받지 않는다.
- 응답은 `application/pdf` bytes와 서버 생성 ASCII filename이며, 오류는 binary를 시작하기 전에
  기존 JSON error envelope로 반환한다.

이 범위는 보안·PII·OOM·프런트 표준에 부합하므로 **설계 착수는 PASS**다. 다만 현재 저장소에는
PDFBox dependency/renderer/controller/test가 아직 없고, 실제 PDF glyph·page-break·HTTP 응답을
검증한 증거도 없다. 따라서 이 문서를 제품 구현 PASS 또는 전체 품질 PASS로 해석하지 않는다.

## 2. 확인한 실제 재사용 경계

| 영역 | 실제 근거 | S4 판정/제약 |
|---|---|---|
| 결과 endpoint | `backend/src/main/java/com/easyperformance/program/EvaluationProgramController.java:78-80`의 `/{id}/results`와 `/{id}/results.xlsx` | PDF는 같은 facade에 `POST /{id}/results.pdf`로 인접시킨다. 기존 XLSX/SVG 계약은 변경하지 않는다. |
| 권한·tenant | `backend/src/main/java/com/easyperformance/program/ProgramAccess.java:16-18,26-27`; controller는 `actors.requireActor()` 사용 | 서비스에서 `requireOperator`를 다시 수행하고, 모든 조회는 `actor.tenantId()`로 제한한다. body의 actor/tenant/employeeId는 받지 않는다. |
| 결과 SoR | `backend/src/main/java/com/easyperformance/program/ProgramAnalyticsService.java:83-90,236-245,257-263` | finalized 프로그램, ACTIVE participant, `resultPublished`, latest FINAL calculation 및 해당 calculation을 가리키는 completed adjustment를 기존 서비스가 선택한다. PDF 서비스는 이 계산을 복사하지 않는다. |
| 결과 표시 정책 | `ProgramAnalyticsService.java:194-204`의 `personalReport`와 `ProgramExecutionService`의 self/member visibility | 직원 PDF는 이번 범위에서 제외한다. 직원용을 추가할 때는 `requireEmployeeActor` + `ProgramAccess.self` + `resultPublished` + `MemberResultVisibility`를 별도 계약으로 고정한다. |
| 기존 리포트 | `backend/src/main/java/com/easyperformance/domain/report/controller/ReportController.java:37-78`, `ReportService` | tenant 조회는 있으나 controller/service에 `ActorAccess` 역할 검사가 없다. S4 source나 직원 PDF 권한 근거로 재사용하지 않는다. |
| 기존 파일 응답 | `EvaluationProgramController.java:98-100`, `ResourceController.java` attachment download | `Content-Disposition`, `Content-Length`, `X-Content-Type-Options: nosniff` 패턴만 참고한다. PDF endpoint는 `Cache-Control: private, no-store`까지 추가한다. |
| FE 표면 | `frontend-vite/src/features/evaluation-programs/pages/AnalyticsPage.tsx`의 `ResultsOverview`/`downloadBlob` 및 `01_frontend_plan.md` | Analytics 결과 요약에만 버튼·modal을 mount한다. client는 결과/점수/PDF layout을 계산·렌더링하지 않고 서버 bytes를 저장한다. |

## 3. easy-standards SoT 매핑

검토한 SoT는 UNC root junction이 아닌 저장소의 현재 fallback인
`easy-performance-management/lib/easy-platform/easy-standards/`다. 해당 경로의 문서가 이 실행에서
읽힌 실제 기준이며, root `easy-standards`를 수정하거나 외부에 쓰지 않는다.

| 표준 출처 | S4 적용 | 설계 게이트 |
|---|---|---|
| `00-principles/02-security-owasp.md:10-11,22,35-37,49-58,112,135-139` (OWASP A01/API1/API3/API4/API5) | 서버 객체·기능·필드 인가, tenant 선두 조회, request allowlist, filename/URL/파일 입력 차단, PII 로그 금지 | operator-only는 버튼 숨김이 아니라 서비스에서 차단한다. foreign/missing program은 404, non-operator는 403, non-finalized/zero-published는 409, 잘못된 옵션은 422로 fail-closed 한다. |
| `00-principles/03-performance-oom.md:38-55,67,105-116` (대량·파일·tenant cache) | 현재 analytics가 participant를 모두 읽는 구조이므로 PDF 호출 전에 tenant-bound count를 확인한다. 최대 200 rows, 8 MiB, 5초, 40 pages로 동기 출력을 제한한다. | cap은 렌더 완료 후에만 검사하지 말고 output write 중 누적 byte와 page/row 경계를 검사한다. 한도를 넘으면 응답 commit 전 422; 무제한 `findAll`/대량 personalized cache/외부 storage job으로 확장하지 않는다. |
| `00-principles/04-observability.md:29-34,83-85,153-189,206-212` | 성공한 다운로드만 `RESULT_PDF_EXPORTED` 메타 감사 이벤트를 기록하고 traceId/actor/time/result를 관찰한다. | title·employee ID/name·score/grade/comment·PDF bytes·토큰·경로를 log/audit details에 넣지 않는다. i18n 추가는 glossary 확인과 5 locale 동시 검토를 통과해야 한다. |
| `00-principles/07-frontend.md:3,13-16,43-55,74-80,138` | React 19.2+/Vite/Mantine v9, `@easy/ui-components`, React Query mutation, lazy route, strict translation schema, server auth | download mutation은 기존 `apiClient`/`downloadBlob`을 사용한다. `useState`로 response/score를 복제하지 않으며, 버튼 visibility는 인가 대체가 아니다. |
| `00-principles/08-shared-code.md:10,23-26,36-43` | 제품 결과 계산은 제품에 남기고 공통 renderer 후보는 PDFBox/font/lock seam만 검토한다. | 이번 S4에서 easy-standards/lib 공통 도메인 진실원을 수정하지 않는다. 중복 PDF renderer가 2개 이상 생길 때만 shared extraction을 별도 ADR로 검토한다. |
| `00-principles/17-i18n-label-conventions.md` 및 `04-observability.md:153-189` | `program.*` PDF namespace와 공통 export label을 5 locale(`ko,en,zh_CN,ja,vi`)로 유지한다. | 계약상 server PDF input은 현재 `ko|en`만 허용한다. UI의 5 locale parity와 PDF의 지원 locale은 분리 명시하며, 미지원 glyph/언어는 조용히 tofu/OS font로 대체하지 않고 typed 422 또는 명시된 정책으로 끝낸다. |
| `10-appendix-spring-jpa/virtual-threads.md:7-30` | PDF renderer는 Java 21 virtual-thread 환경의 `synchronized` pinning을 피한다. | renderer/font initialization 보호가 필요하면 `ReentrantLock`을 사용한다. 신규 `synchronized`는 금지한다. |
| `10-appendix-spring-jpa/page-response-envelope.md:14,35-43,97` | PDF 자체는 page envelope가 아니지만, source/list API와 UI 계약은 pagination 정책을 따른다. | PDF request는 bounded single export다. 향후 batch/history endpoint를 만들면 `PageResponse` envelope로 설계하고 raw unbounded list를 새 계약으로 만들지 않는다. |

## 4. 보안·개인평가 visibility 판정

### 4.1 통과 가능한 경계

1. controller에서 `actors.requireActor()`를 얻고, source service가 `ProgramAccess.requireOperator(actor)`를
   다시 호출한다. client가 보낸 actor ID, tenant ID, employee ID를 신뢰하지 않는다.
2. `findByIdAndTenantId(programId, actor.tenantId())`와 기존 `resultSummary` 경계를 보존한다.
   타 tenant/missing program은 같은 404로 수렴해 존재 여부를 누출하지 않는다.
3. `ProgramStatus.FINALIZED`이고 `resultPublished`인 ACTIVE participants만 결과 source에 포함한다.
   0건은 빈 PDF를 만들어 “발행됨”처럼 보이지 않고 `RESULT_NOT_PUBLISHED` 409로 끝낸다.
4. PDF participant columns는 employee snapshot, score, grade, visible feedback status 등 서버가
   고정한 enum만 허용한다. reviewer identity/tendency, item answers, opinions, manager comment,
   appeal, KPI evidence JSON, goals, attachments, raw source IDs는 포함하지 않는다.
5. source hash는 tenant/program/version/definition revision/options/정확한 ordered DTO 값으로 만들되
   title text·개인정보·점수 본문을 audit/log에 기록하지 않는다. 성공 후에만 metadata-only audit event를
   기록한다. rendering/authorization 실패는 성공 event를 남기지 않는다.

### 4.2 구현 전 필수 확인

- `ProgramAnalyticsService.resultSummary`는 현재 `results()`에서 participant 전수 목록을 읽으므로
  count가 200을 초과하면 analytics 호출 **전에** 422해야 한다. count와 subsequent read가 서로 다른
  결과가 되지 않도록 한 source transaction/일관된 read boundary를 계약에 명시한다.
- 기존 `domain/report`의 `/reports/my`는 이름만으로 employee-safe하다고 판단할 수 없다. 현재
  `ReportController`에 actor role check가 없으므로 S4에서 연결하지 않는다.
- PDF가 “최종 결과”임을 명확히 하기 위해 finalized/published/active revision과 generation timestamp,
  source hash를 문서에 넣는다. 재발행·계산·점수 확정·공개 상태 변경은 PDF 요청에서 수행하지 않는다.

## 5. 출력 bounds·PDF renderer·공급망

### 5.1 승인된 bounds

- request: sections 1..2 distinct, columns 0..8 distinct, table 선택 시 employee name + score/grade,
  portrait 최대 5 columns, landscape 최대 8 columns, title 1..100 Unicode code points, CR/LF/control
  문자·중복·불일치 조합 금지.
- data: tenant-bound participant pre-count ≤200; rows ≤200; fixed page size; hard page cap 40.
- rendering: monotonic 5s deadline, row 단위 page break, header repeat, bounded line wrapping/ellipsis,
  한 row가 페이지를 독점하지 않도록 truncation footer/고지 정책을 구현 계약에 포함한다.
- output: `ByteArrayOutputStream`에 쓰되 8 MiB를 **write-time 누적 검사**한다. 8 MiB 초과 후 post-hoc
  검사는 불충분하다. cap 초과는 bytes commit 전 `PDF_OUTPUT_LIMIT` 422로 반환한다.
- no remote fetch, URL resolution, HTML/JS, image decoder, attachment parser, user font, OS font, temp
  file를 renderer에서 허용하지 않는다. PDFBox primitive text/shape만 사용한다.

### 5.2 폰트 라이선스·glyph

현재 자산 근거:

- `backend/src/main/resources/fonts/NanumGothic-Regular.ttf` (2,054,744 bytes),
  `fonts/OFL.txt`, `fonts/README.md`.
- README는 Google Fonts `ofl/nanumgothic` commit `133ccbee9a8b408eb71f31a36ccb9116f5c695ad`,
  SIL Open Font License 1.1, TTF SHA-256
  `76f45ef4a6bcff344c837c95a7dcc26e017e38b5846d5ae0cdcb5b86be2e2d31`, OFL SHA-256
  `eeacf16032901d0ed0456876ec77b8f0fda6b3fecec7d972f8543eb602e6c30f`를 명시하고 OS/network font
  lookup을 금지한다.

따라서 bundled-font provenance/license 설계는 PASS다. 그러나 현재 `backend/build.gradle.kts`에는
PDFBox dependency가 없고 Java renderer도 없다. root가 고정할 PDFBox **3.0.8** dependency와 lockfile/
SBOM/SCA 근거, NanumGothic glyph embedding, 한·영 및 unsupported glyph typed-422 테스트가 구현 후
필수다. PDFBox 3.0.7 등 구식 snippet이나 system font fallback을 근거로 PASS 처리하지 않는다.

### 5.3 PDF 파일 응답 계약

성공:

```http
200 OK
Content-Type: application/pdf
Content-Disposition: attachment; filename="evaluation-results-{programId}.pdf"; filename*=UTF-8''evaluation-results-{programId}.pdf
Content-Length: <exact bytes>
X-Content-Type-Options: nosniff
Cache-Control: private, no-store, max-age=0
Pragma: no-cache
```

filename은 server-generated ASCII allowlist(`evaluation-results-` + UUID + `.pdf`)로 고정하고
program name/employee name/title/CRLF를 넣지 않는다. 기존 `ContentDisposition` 패턴은 참고하되
UTF-8/ASCII fallback의 실제 wire header를 테스트한다. validation/auth/render 오류는 PDF body가
commit되기 전에 JSON error envelope여야 한다.

## 6. 프런트 UI·i18n 설계 게이트

- mount는 `AnalyticsPage`의 finalized result summary surface 하나로 한정한다. `PersonalReportPage`와
  `EvaluationWorkPage/ResultPanel`에는 직원 PDF 버튼을 붙이지 않는다.
- `PDF 다운로드` → `UiModal`에서 locale/orientation/sections/columns/title을 선택 → React Query
  mutation으로 POST → `downloadBlob`으로 bytes 저장의 순서다. score/grade/분포/PDF layout을 client에서
  재계산하거나 client PDF library로 다시 만들지 않는다.
- Mantine v9와 `@easy/ui-components` wrapper, design token, accessible labels/focus/keyboard,
  loading/disabled/error/empty 상태를 사용한다. 403/404/409/422/5xx는 `getErrorMessage`와 기존
  `QueryState`/toast convention으로 표시하고 raw key·stack trace·PII를 노출하지 않는다.
- API path/content negotiation/filename은 backend contract와 exact match한다. fetch를 직접 만들거나
  `useState`로 server response를 복제하지 않고 기존 `apiClient`/CSRF·refresh 경로를 재사용한다.
- UI translations는 `program.*` namespace에 5 locale 키를 동시에 추가하고 `glossary/i18n-terms.md`
  확인 후 검토한다. PDF server output은 현재 `ko|en`만 계약하는 차이를 UI에 명확히 표시한다.
- 버튼을 숨기는 것만으로 권한을 통제하지 않는다. employee actor가 직접 endpoint를 호출해도 서버 403이어야
  한다. client title/option allowlist도 server validation을 대체하지 않는다.

## 7. 실제 PDF QA gate (구현 후 반드시 수행)

Phase3a에서는 실행 PASS를 주장하지 않는다. Phase3b 이후 다음 증거가 있어야 S4 최종 PASS를 줄 수 있다.

### Backend/HTTP

- unit/access: operator 200, employee 403, missing/cross-tenant 404, non-finalized/zero-published 409,
  invalid/duplicate/too-many columns/title/glyph 422.
- bounds: 200 participant 성공, 201 rejected, count check가 `resultSummary` 전 load보다 먼저 실행,
  8 MiB write-time reject, 40-page/5s deadline, long Korean/Latin row truncation footer.
- renderer: `%PDF-` signature, PDFBox `qpdf --check`/`pdfinfo`/text extraction, ko/en glyph embedding,
  portrait/landscape, all approved option combinations, null em dash, repeated headers/page numbers,
  no reviewer/comment/hidden IDs.
- HTTP: exact method/path, `application/pdf`, ASCII + RFC5987 disposition, exact content length,
  no-store/nosniff; errors remain JSON before binary commit. Existing XLSX/SVG regression remains green.
- isolation/audit: tenant A operator success, tenant B cannot read A, audit only after complete bytes,
  source hash stable for same source/options, changed source revision not silently represented as old PDF.

### Frontend/visual

- 5 locale UI typecheck/build and browser route test; desktop/mobile widths, modal keyboard/focus, download
  error states and stale/empty states.
- Every generated PDF page rendered to PNG and visually inspected for Korean glyphs, clipping, overflow,
  title/footer/page number, repeated headers, row/page boundary, portrait/landscape. Generated bytes must
  be parsed, not judged only by browser download success.
- No runtime console/pageerror/API error in the final browser evidence; actual HTTP response must be captured,
  not mocked or inferred from the presence of a button.

## 8. 잔여 상태와 병합 판정

### PASS (설계 한정)

- HR Analytics operator-only scope, existing result source reuse, tenant/object authorization boundary,
  no employee PDF, bounded options/data, direct-text/no-remote-input renderer, bundled font provenance,
  binary response contract, FE React Query/i18n approach are sufficiently specified in the contract.
- Existing XLSX/SVG and S1/S2/S3 scope is preserved; no shared standard/product code is changed by this gate.

### 구현/검증 pending (현재 병합 차단 아님, Phase3b prerequisite)

- PDFBox 3.0.8 dependency/SCA, renderer/source/audit/controller, tests, OpenAPI update, FE modal/API wiring.
- write-time 8 MiB counter, 40-page cap, truncation footer, deadline and glyph fail-closed implementation.
- actual HTTP/tenant/employee denial, content headers, PDF parser and all-page visual evidence.

### 현재 차단으로 승격할 조건

- employee/self PDF 또는 stored/share/email/public token을 S4에 몰래 포함하는 경우.
- client-supplied HTML/CSS/URL/font/employee/tenant/score 또는 server-side operator guard 누락.
- result source 재계산, unpublished/draft/foreign data 출력, unbounded participant load before bound.
- OS/network font fallback, missing OFL/SBOM evidence, post-hoc-only byte cap, raw PDF/PII log, JSON 오류가
  binary commit 후 발생하는 경우.

**최종 결론:** Phase 3a는 위 HR-only contract와 bounds amendment(8 MiB write-time, 40 pages,
truncation footer, ASCII/RFC5987 filename)를 유지하는 조건으로 **PASS**. 제품 코드·빌드·실제 PDF/HTTP/
브라우저 검증은 아직 수행하지 않았으므로 현재 단계의 구현 병합 판정은 **PENDING**이며 Phase3b 이후
증거를 받아 독립 QA를 다시 수행한다.

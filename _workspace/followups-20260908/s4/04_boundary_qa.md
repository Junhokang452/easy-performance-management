# S4 백엔드 증분 경계 QA

작성일: 2026-09-08  
담당: Luna (quality-gate)  
범위: 현재 작성된 `ProgramResultPdf*` 백엔드 소스와 기존 Analytics/Controller 경계의 read-only 정적 검토  
실행 정책: 빌드·테스트·runtime은 root/Sol 담당이므로 이 세션에서 중복 실행하지 않음. FE 소스와
root 보고의 tsc/build/Node17/DS/localUI PASS를 확인했고, 전용 loopback HTTP·browser 산출물도
독립적으로 읽어 증거 수준을 분류함.

## 1. 검토 대상

- `backend/src/main/java/com/easyperformance/program/ProgramResultPdfDtos.java`
- `backend/src/main/java/com/easyperformance/program/ProgramResultPdfSourceService.java`
- `backend/src/main/java/com/easyperformance/program/ProgramResultPdfRenderer.java`
- `backend/src/main/java/com/easyperformance/program/ProgramResultPdfExportService.java`
- `backend/src/main/java/com/easyperformance/program/ProgramResultPdfAuditService.java`
- `backend/src/main/java/com/easyperformance/program/EvaluationProgramController.java`
- `backend/src/main/java/com/easyperformance/program/ProgramAnalyticsService.java`
- `backend/src/main/java/com/easyperformance/program/ProgramAccess.java`
- 계약: `_workspace/followups-20260908/s4/01_backend_contract.md`

## 2. 이전 finding 재검토

### F01 — PDFBox glyph 검사 checked exception (해소 확인)

- 위치: 최신 `ProgramResultPdfRenderer.java:108-121`.
- 기존 `hasGlyph(int)` 호출은 제거되고 `font.getStringWidth(value)`의 PDFBox 예외를
  `PDF_UNSUPPORTED_GLYPH` typed 422로 변환하도록 변경됐다. `ensureGlyphs`가 `throws IOException`을
  선언하고 호출부의 checked 흐름도 컴파일 가능한 형태다.
- `ProgramResultPdfRendererTest.rejectsUnsupportedUnicodeInsteadOfUsingCidAsUnicodeGlyphCheck`가
  unsupported Unicode의 typed `PROGRAM_INVALID` 매핑을 정적으로 확인한다.
- **정적 재판정: PASS.** Sol이 보고한 `compileJava PASS`와 이 소스 구조가 일치하며, 최신
  전용 runtime/browser에서 glyph 422 및 corrected PDF 응답도 확인됐다. 전체 테스트는 Sol 보고
  증거로 분류하고 이 세션에서 중복 실행하지 않았다.

### F02 — 계약상 모든 페이지의 메타데이터·기밀성 footer (해소 확인)

- 위치: 최신 `ProgramResultPdfRenderer.java:80-92`.
- `newPage()`가 title을 최대 2줄로 제한하고, program name을 폭에 맞춰 생략하며, footer에 localized
  confidentiality/omission/page 문구와 evaluation year/kind/definition revision/UTC generatedAt/
  source hash를 매 페이지 출력한다.
- summary-only에도 metadata가 유지되며, participant-table-only에도 page footer가 남는다.
- `ProgramResultPdfRendererTest`가 Korean/English, portrait/landscape, multipage 및 generatedAt
  텍스트를 검증한다.
- **정적 재판정: PASS.** 전용 HTTP의 PDF transport/header와 pypdf/Poppler 시각 산출물에서
  footer·metadata 보존을 확인했다.

### F03 — title/program name 폭 제한 (해소 확인)

- 위치: 최신 `ProgramResultPdfRenderer.java:86,92,110-113`.
- title은 최대 2줄 `wrap`, program name은 `fit`으로 page usable width에 제한된다. table cell도
  최대 2줄로 감싼 뒤 생략한다.
- `ProgramResultPdfRendererTest`가 긴 Korean/Latin title와 긴 조직/직무 셀의 multipage output을
  생성한다.
- **정적 재판정: PASS.** 19+14 pages의 geometry/font 자동 검사와 contact/full-size visual
  review에서 clipping 및 separator 침범이 보이지 않았다.

## 3. 통과한 정적 경계

- **API route/응답:** `EvaluationProgramController.java:82`는 `POST /{id}/results.pdf`, JSON
  request, `application/pdf` response를 exact facade에 붙인다. `Content-Disposition`,
  `Content-Length`, `nosniff`, `private no-store`, `pragma no-cache`가 설정된다.
- **권한/tenant:** controller `actors.requireActor()`와 source `ProgramAccess.requireOperator`
  (`ProgramResultPdfSourceService.java:44`)가 중복 방어한다. program lookup는
  `findByIdAndTenantId`이며 `ProgramAnalyticsService.finalized`도 같은 tenant/operator 경계를
  재검증한다.
- **상태/visibility:** source는 FINALIZED를 확인하고, Analytics source가 ACTIVE + resultPublished
  participant만 결과로 만든다(`ProgramAnalyticsService.java:83-90,236-245`). zero rows는
  `RESULT_NOT_PUBLISHED`로 거부한다. 직원용 PDF를 연결하지 않았다.
- **pre-count/bounds:** `ProgramResultPdfSourceService.java:49-53`에서 tenant-bound count를
  Analytics 호출 전 확인하고 최대 200으로 제한한다. renderer에는 8 MiB write-time output
  guard, 40-page guard, 5초 monotonic deadline이 있다(`ProgramResultPdfRenderer.java:23-24,127-128`).
- **source coherence/hash:** source load가 `REPEATABLE_READ` read-only transaction이고, 동일
  transaction에서 count·program·Analytics 결과를 읽은 후 tenant/program/version/options/ordered
  result 값을 SHA-256으로 만든다(`ProgramResultPdfSourceService.java:43-55,83-104`).
- **renderer input surface:** PDFBox primitive text/line만 사용하며 URL/HTML/JS/image/user font
  경로가 없다. classpath NanumGothic만 로드한다. participant cell은 width fit과 ISO control
  정규화를 거친다.
- **audit transaction:** renderer가 성공해 bytes를 만든 뒤 export service가 별도
  `ProgramResultPdfAuditService.record` transaction을 호출한다. audit details는 policy/hash/
  options/count/byteCount/time만 포함하고 이름·점수·comment·PDF bytes를 넣지 않는다.
- **source hash collision 방지:** 최신 `ProgramResultPdfSourceService.java:83-104`는 모든 문자열을
  UTF-8 length-prefix binary로 기록한 뒤 SHA-256한다. 이름/부서 등 delimiter가 포함돼도 서로 다른
  source가 같은 hash로 합쳐지지 않도록 보강됐다.
- **PDFBox 공급망:** `build.gradle.kts`는 `org.apache.pdfbox:pdfbox:3.0.8`을 고정하고,
  classpath `NanumGothic-Regular.ttf`와 OFL/hash 문서를 사용한다.
- **직원 PDF 격리:** 기존 `domain/report/ReportController`는 S4 source로 사용하지 않았고, 직원
  결과 표면에도 endpoint를 연결하지 않았다.
- **FE↔BE DTO/options:** `frontend-vite/src/features/evaluation-programs/api/customPdf.ts`의
  `CustomPdfRequest`, `PdfLocale`, `PdfOrientation`, `PdfSection`, `PdfColumn`이 backend DTO enum 및
  `POST /v1/evaluation-programs/{id}/results.pdf`와 일치한다. client-side validation은 UX 보조이고,
  server validation을 대체하지 않는다.
- **FE mount/download:** `AnalyticsPage.tsx`의 finalized result summary에만
  `CustomResultPdfModal`을 mount하고, `apiClient`의 `responseType: 'blob'` 결과를 기존
  `downloadBlob`으로 저장한다. employee `PersonalReportPage`/`EvaluationWorkPage`에는 mount가 없다.
- **FE audit/i18n:** backend `RESULT_PDF_EXPORTED` enum과 `api/audit.ts` event union 및
  `ProgramAuditTimeline` label이 일치한다. PDF 성공 후 mutation이 audit query prefix를 invalidate한다.
  `customPdfI18n.ts`는 ko/en/ja/zh-CN/vi 모든 label을 제공하고 `programI18n.ts`가 각 namespace를
  등록한다.

## 4. 최신 실행·시각 증거와 남은 pending

실행 명령은 root/Sol이 담당했으며 이 독립 세션에서는 중복 실행하지 않았다. 아래는 산출물에
기록된 결과와 그 증거 수준을 분리한 것이다.

- **백엔드 보고 증거: PASS (독립 재실행 아님).** 최신 동결 JAR은 SHA-256
  `5D008F2A56DEC877D56D2BCB1CC43C43395173B3D4978988334156AB65D16DF4`로 기록되어 있고,
  focused `14/14`, full `336/336`, `bootJar` PASS 및 source/temp parity `14/14`가 보고됐다.
  이 수치는 `_workspace/followups-20260908/s4/02_backend_implementation.md`의 실행 기록이며
  이 QA 세션의 실행 결과로 재주장하지 않는다.
- **PDF API/renderer: PASS (전용 실 HTTP + 합성 데이터 범위).** `pdf-local.json`은
  `completed:true`, 45/45 checks PASS이고 1·11페이지 산출물, transport/security/PII/
  row-bound/omission/audit 검사를 담는다. `scripts/verify-custom-result-pdf-local.py`를
  읽어 확인한 바, `urllib.request`가 전용 `127.0.0.1:8089` API에 실제 요청하고 전용
  PostgreSQL `127.0.0.1:55489`에 `psql`로 audit/source 불변을 확인한다. 프로그램/결과 값은
  합성 SQL fixture이며 workflow 전체 완료를 뜻하지 않는다는 원래 한계는 유지한다.
- **API 회귀: PASS (실 HTTP, 합성 데이터 범위).** `regression/s1-api.json` 22/22,
  `s2-api.json` 27/27, `s3-api.json` 39/39가 모두 completed이며 실패 case 0이다. 이는
  synthetic 객체를 사용하더라도 실제 전용 API wire를 호출한 결과로 분류하며, 운영 전 과정
  (외부 HCM/배포/실사용 데이터) PASS로 확대하지 않는다.
- **PDF 시각/구조: PASS (자동 + root full-size/contact-sheet 육안 범위).** `visual-unit-final/result.json`
  은 19 pages, embedded-font/geometry 모두 PASS, `visual-api-final/result.json`은 14 pages,
  embedded-font/geometry 모두 PASS다. 두 contact-sheet를 직접 확인해 R04 separator가 행
  baseline을 침범하지 않고, 반복 header/footer·마지막 행·portrait/long-title layout에
  뚜렷한 clipping이 없음을 확인했고, root가 first/last와 19+14-page contact/full-size를
  추가 확인했다. 각 결과 JSON의 `visualReview:PENDING` 문자열은 자동 산출 시점의 stale
  marker로, 최신 root visual sign-off로 해소됐다.
- **FE 정적/빌드 보고: PASS (root 보고).** tsc/Vite/Node/DS/localUI PASS와 5-locale
  dictionary 및 FE↔BE DTO/options/audit mapping 정합은 확인했다. 실제 download와 typed 422
  복구는 browser에서 확인했고, audit refresh 수신·감사 persistence는 별도 runtime evidence가
  필요하다.
- **브라우저: PASS, R05 해소.** 최초 결과의 `HttpMediaTypeNotAcceptableException`은 shared
  JSON `Accept` 기본값과 PDF 협상 충돌로 root가 재현했고, `customPdf.ts`의 PDF 요청
  `Accept: */*` 수정 후 source/temp·tsc/build와 실제 브라우저 재실행을 통과했다. 최신
  `browser/result.json`은 `completed:true`, 15/15 PASS, `consoleErrors:[]`다: 5 locale ×
  1440/390 실제 PDF download 10건, cancel 무요청, portrait 열 보존, unsupported glyph
  422 표시·복구, corrected retry PDF, employee 표면 제외 및 직접 요청 403을 확인했다.
  기존 `browser/failure.png`는 R05 과거 실패 증거로 격리한다.
- **회귀 브라우저: PASS.** `regression/s1-browser/result.json` 13/13,
  `s2-browser/result.json` 16/16, `s3-browser/result.json` 16/16 모두 `passed:true`다.
  따라서 S1~S3 교차 회귀도 현재 산출물 기준 PASS이며, S3는 더 이상 진행 중이 아니다.
- **잔여 범위:** QA 에이전트가 MockMvc를 별도로 재실행하지는 않았다. foreign404,
  draft/zero-published409 및 audit persistence는 PDF-local 실 HTTP/psql 검사로 확인했다.
  8MiB·40page·5sec는 구현/단위 경계 검증이며 HTTP 상한 소진 부하 시험은 미실행이다.
  전체 운영 workflow 및 외부 연계는 범위 밖이다. qpdf/pdfinfo는 요구 게이트가 아니며
  pypdf/pdfplumber와 Poppler 렌더 근거를 사용했다.

## 5. 최종 판정

F01~F03, R04 및 R05 원인 수정은 최신 소스·실제 전용 HTTP/브라우저/fixture/시각 산출물에서
**PASS**다.
source count/transaction/hash/tenant/권한/audit-after-render 경계와 FE↔BE endpoint·DTO enum·
options·5 locale·audit event mapping도 정합하다. S4 증분 품질 게이트 판정은 **PASS (범위 한정)**다.
다만 합성 데이터와 전용 loopback runtime을 운영 데이터/외부 workflow로 확대하지 않는다. 이는
S4 증분 품질 게이트의 범위 한계이지 현재 병합 blocker는 아니다.

1. 운영 승격 시에만 외부 workflow/실사용 데이터로 동일 15+13+16+16 browser와 audit를
   재실행한다.
2. root 최종 보고에는 source/JAR hash와 전용 runtime 수명을 함께 보존한다.

위 항목은 S4 코드 품질 PASS를 취소하는 blocker가 아니라 운영 범위 확장 시 필요한 추가
integration evidence다.

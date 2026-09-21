# S4 맞춤 결과 PDF 백엔드 구현 보고

## 결과

- HR 결과 분석의 공개 결과만 재사용하는 `POST /api/v1/evaluation-programs/{programId}/results.pdf`를 구현했다.
- 서버가 locale, 방향, 섹션, 컬럼, 제목을 allowlist/bounds로 검증하고, 테넌트·HR 권한·FINALIZED·published 경계를 기존 `ProgramAnalyticsService.resultSummary` 계약 그대로 적용한다.
- pre-count 와 결과 조회는 `REPEATABLE_READ` read-only 스냅샷에서 수행하며, 200명 초과는 민감 결과 row 로드 전 차단한다.
- PDFBox 3.0.8 + 클래스패스 NanumGothic만 사용해 텍스트를 직접 그린다. HTML/JS/URL/원격 리소스/임시 파일은 사용하지 않는다.
- 렌더 중 8 MiB output bound, 40 page, 5 sec deadline을 강제한다. Unicode 글리프는 CID `hasGlyph` 오용 대신 font encode 경로인 `getStringWidth` 예외로 fail-closed 검증한다.
- 결과 PDF의 모든 페이지에 기밀 표시, 생략 정책, 정의 revision, UTC 시각, `PROGRAM_RESULT_PDF_V1`, 길이-prefix canonical source hash를 표시한다.
- 감사 이벤트는 `RESULT_PDF_EXPORTED`로 기록하되 제목·이름·사번·점수·등급은 저장하지 않고, reason은 event type i18n 경로를 사용한다.

## 변경 파일

- `backend/build.gradle.kts` — PDFBox 3.0.8 pin
- `backend/src/main/java/com/easyperformance/program/ProgramResultPdfDtos.java`
- `backend/src/main/java/com/easyperformance/program/ProgramResultPdfSourceService.java`
- `backend/src/main/java/com/easyperformance/program/ProgramResultPdfRenderer.java`
- `backend/src/main/java/com/easyperformance/program/ProgramResultPdfAuditService.java`
- `backend/src/main/java/com/easyperformance/program/ProgramResultPdfExportService.java`
- `backend/src/main/java/com/easyperformance/program/EvaluationProgramController.java`
- `backend/src/main/java/com/easyperformance/program/ProgramTypes.java`
- `backend/src/test/java/com/easyperformance/program/ProgramResultPdfSourceServiceTest.java`
- `backend/src/test/java/com/easyperformance/program/ProgramResultPdfRendererTest.java`
- `backend/src/test/java/com/easyperformance/program/ProgramResultPdfAuditServiceTest.java`
- `backend/src/test/java/com/easyperformance/program/ProgramResultPdfContractTest.java`
- 서체·OFL 리소스 2개는 root 소유 준비본을 사용했다.

## 검증

- Windows 격리 사본 focused S4: **14/14 PASS** (R04 표 구분선 상·하단 간격 좌표 회귀 포함)
- Windows 격리 사본 전체 backend: **336/336 PASS**, failures/errors/skipped 0
- `bootJar`: **PASS**
- jar: `C:\Users\SAMSUNG\AppData\Local\Temp\easy-performance-validation-b1ce4a863a3c41ffb3bd65cfa5fe87a3\easy-performance-management\backend\build\libs\easy-performance-management-backend-0.1.0.jar`
- jar SHA-256: `5D008F2A56DEC877D56D2BCB1CC43C43395173B3D4978988334156AB65D16DF4`
- 원본↔Windows 검증 사본 S4 소스/테스트/리소스 hash parity: **14/14**
- 대표 PDF 4종: `_workspace/followups-20260908/s4/generated-pdfs/`
  - `ko-portrait.pdf`
  - `ko-landscape.pdf`
  - `en-landscape.pdf`
  - `ko-multipage-200.pdf`

## 남은 검증 경계

- 실 PostgreSQL/API 권한·테넌트·header/오류 계약과 브라우저 다운로드는 root 런타임 하네스가 이 동결 jar로 검증한다.
- 대표 PDF 전 페이지 시각 QA는 root의 PDF 렌더 검증 단계에 남아 있다.
- PDFBox 의존성 OSV 조회 결과는 root 소유 `_workspace/followups-20260908/s4/03_dependency_check.md`에 별도 보존된다.

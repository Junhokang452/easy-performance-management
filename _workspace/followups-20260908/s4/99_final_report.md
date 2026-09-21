# S4 맞춤 결과 PDF 완료

2026-09-08. 승인된 네 번째 보완 항목 구현·로컬 검증 PASS. 운영 배포/발송/DB 변경 없음.

## 제공 기능

HR_ADMIN/SUPER_ADMIN은 결과·통계 화면에서 맞춤 PDF를 내려받는다. 확정 프로그램의 ACTIVE·공개 결과만 사용하며 기존 계산/조정 snapshot을 재사용한다. 직원용 PDF는 이번 범위에 없다.

- 제목, 출력 언어(한국어/영어), 세로/가로, 요약/대상자 표, 고정 열 선택.
- UI는 ko/en/ja/zh-CN/vi 5개 언어. 기존 Excel 다운로드 유지.
- 고정 내장 한글 폰트, 반복 표 머리글, 행 단위 페이지 나눔, 긴 셀 말줄임, 기밀·페이지·생성시각·정책·원본 해시 표기.
- 최대 200명(제외/미공개 포함 프로그램 사전 카운트), 8MiB write-time 제한, 40페이지, 협력적 5초 렌더 기한. 외부 이미지/URL/HTML/폰트 입력 없음.
- 테넌트·역할·결과공개 검증, no-store 응답, 렌더 완료 후 메타데이터 감사 이벤트. PDF 저장/공유/메일/자동 재계산 없음.

## 최종 실행 근거

| 검증 | 결과 | 근거 |
|---|---|---|
| Backend 전체 / S4 집중 | 336/336 / 14/14 | 02_backend_implementation.md |
| 실제 로컬 HTTP | 45/45 | pdf-local.json |
| 실제 브라우저 | 15/15, page error 0 | browser/result.json |
| PDF 구조·폰트·경계·구분선 및 시각 | 8 PDF / 33페이지 PASS | visual-unit-final, visual-api-final |
| FE 타입/빌드/Node/DS/localUI | PASS / 17 tests / 위반0 | 05_frontend_verification.md |
| 검증 사본 해시 | BE14/14, FE+schema9/9 | root 최종 hash 검사 |
| S1~S3 회귀 | API88/88, browser45/45 | regression/ |

최종 JAR SHA256: `5D008F2A56DEC877D56D2BCB1CC43C43395173B3D4978988334156AB65D16DF4`. 실제 `/v3/api-docs`에서 OpenAPI 타입 재생성 후 tsc 확인. 실제 stack은 Java21/Boot4.1.1/Gradle8.14.5이며 오래된 하네스 버전으로 낮추지 않았다.

## 발견·해소

R04: 다음 행 글자를 가로지르는 표 구분선을 실제 이미지에서 발견. 좌표 수정 후 단위 회귀와 문자-bbox 교차 검사를 추가했다. 옛 PDF에서 새 검사가 실패하고 수정본33페이지에서 통과함을 확인했다.

R05: JSON Accept 기본값으로 실제 브라우저 PDF가 실패. 기존 Excel/SVG 방식의 Accept */* 적용 후 5언어·2폭 실다운로드와422복구가 통과했다. 상세 07_runtime_findings.md.

## 범위·제한

합성 데이터지만 요청은 실제 loopback8089 서버와 PostgreSQL55489에서 수행했다. S4 확정 결과 snapshot은 명시적으로 합성 SQL 시드했으므로 평가 전체 업무흐름을 처음부터 끝까지 재수행했다는 뜻은 아니다. 8MiB/40page/5sec는 구현·단위 수준 경계이며 실제 HTTP 부하 시험으로 최대치마다 소진시킨 것은 아니다. PDF 전 페이지는 contact sheet로 검수했고 대표 긴 제목/긴 셀/처음·마지막 페이지를 확대 확인했다. 운영 성능/SBOM 전체 감사는 별도다.

기존 FE shared chunk 크기 경고는 유지되며 빌드 실패가 아니다. 기존 S1 HCM 전체 테스트의 payroll import 차단은 이번 PA336 통과와 별개로 남는다. 기존 dirty tree를 보존했고 commit/push/deploy하지 않았다.

## 협업과 스킬 영향

Astra가 계약·통합·API/browser/PDF 검증을 조율했다. Sol은 BE와 S1~S3 API 회귀, Terra는 FE 사전 조사/초안, Luna는 표준·독립 QA를 맡았다. Terra가 완료하지 못한 FE 구현/5언어 마감/브라우저 회귀는 Astra가 회수해 완료했다.

easy-suite-orchestrator의 계약 선확정·경계 QA와 PDF 스킬의 렌더 후 검수 절차를 적용했다. 특히 화면 검수가 R04를 검출하여 문자-구분선 자동 회귀까지 추가하도록 영향을 주었다. 표준 갱신 후보는 06_standards_proposals.md에만 보존했고 공유 표준 저장소는 변경하지 않았다.

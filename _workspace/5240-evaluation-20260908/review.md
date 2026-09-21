# 5240 평가 참고 보완 — 2026-09-08

상태: 감사 조회 코드 적용, 다국어·정적 경계 검사 통과, 전체 빌드·서버·브라우저 검증 미완료. 기존 RootHR 전체 사이클 완료 기록과 이번 증분 검증을 구분한다.

## 읽음과 판단

- 평가 28개 원문 의미검토는 코디네이터 Sol의 evaluation-semantic-review.md / evaluation-ledger.md를 승계하고, 본 작업에서 28개 판정표 전체와 실제 PA 서버·화면 경계를 재대조했다. 이 세션에서 원문 28개를 모두 새로 읽었다고 주장하지 않는다.
- user 및 user-manual 45개 본문을 검색했다. 검색 후보 6개(사용자 매뉴얼, 매뉴얼 구성, 교육 신청, 교육결과보고, 급여명세서, 무엇을 어디서 신청하나요)의 본문을 읽었다. 일부는 '구성과' 등에 의한 부분문자열 오탐이다. 인사평가 전용 추가 페이지는 없었다. 교육결과보고의 평가·통계 활용은 LMS 영역이므로 PA에 교육 신청/결과보고를 복제하지 않는다.
- 원문 파일 및 공용 의미검토 보고서는 수정하지 않았다. 페이지별 최종 적용 판정은 이 폴더의 page-decisions.md에 기록한다.

## 적용

상태 변경·재계산·조정·마감 취소·공개 등의 기존 append-only audit를 관리자 운영 화면에서 확인하도록 연결했다.

- GET /api/v1/evaluation-programs/{programId}/audit-events
- HR_ADMIN/SUPER_ADMIN만 허용. 프로그램의 tenant 접근 확인 후 tenantId+programId로 항상 제한한다.
- eventType / participantId 선택 필터, page 기본 0, size 기본 25·최대 100. createdAt DESC, id DESC로 동시각 순서를 고정한다.
- 반환 필드: id, participantId, eventType, reason, actorEmployeeId, createdAt. 민감한 detailsJson 원본·취소 당시 피드백 스냅샷은 투영하지 않는다.
- 운영 화면에 유형·대상자 필터, 새로고침, 이전/다음 페이지, 변경 시각·행위자·사유를 표시한다. 조회된 이름이 없으면 ID를 표시하며 과거 행위자 이름을 추정하지 않는다.
- 18개 이벤트명 및 화면 문구를 ko/en/ja/zh-CN/vi로 제공한다. 기존 공통 SectionCard/FormSelect/Ui* 및 React Query 캐시를 사용한다.
- DB migration은 없다. 기존 감사 기록의 의미나 점수 정책, HCM Core Master, Prego HR, easy-mra 코드는 변경하지 않았다.

## 변경 파일

- backend/src/main/java/com/easyperformance/program/ProgramAuditQueryService.java
- backend/src/main/java/com/easyperformance/program/ProgramAuditController.java
- backend/src/main/java/com/easyperformance/program/ProgramAuditEventRepository.java
- backend/src/test/java/com/easyperformance/program/ProgramAuditQueryServiceTest.java
- frontend-vite/src/features/evaluation-programs/api/audit.ts
- frontend-vite/src/features/evaluation-programs/auditI18n.ts
- frontend-vite/src/features/evaluation-programs/programI18n.ts
- frontend-vite/src/features/evaluation-programs/components/ProgramAuditTimeline.tsx
- frontend-vite/src/features/evaluation-programs/pages/ProgramOperationsPage.tsx

## 검증 상태

- 정적 확인: operator 검사 → program/tenant 확인 → tenant+program 필터; 응답에서 detailsJson 제외; UI와 API 응답 6필드 대조; 5개 언어 연결; 기존 기록/산식 변경 없음.
- 새 백엔드 테스트 7개 작성: 직원 접근 거부, 다른 테넌트 프로그램 거부, 복합필터·안정 정렬·원본 미노출, 음수 page/초과 size/0 size 거부, SUPER_ADMIN 빈 목록 조회.
- 이번 7개 테스트는 아직 실행 성공이 확인되지 않았다. 현재 Windows PATH에서 Java 실행 파일을 찾지 못했고 WSL의 최소 /bin/echo조차 장시간 무응답이다. WSL 재시작은 다른 작업에 영향을 줄 수 있어 실행하지 않았다.
- Windows 전체 tsc 검사에서 @easy/http-client, @easy/ui-components 등 공유 패키지 미해석 및 파생 타입 오류가 발생했다. 통과하지 않았으며 모든 오류가 환경 때문이라고 확정하지 않는다. 정상 WSL 의존성 환경의 재검증이 필요하다.
- 기존 i18n 테스트 첫 실행은 @easy/i18n-common 미해석으로 실패했다(i18n-check.log). 검증 실행에서만 package.json의 실제 exports 경로를 연결한 재시도는 7/7 통과했다(i18n-windows-resolved.log, windows-i18n-check.mjs). 제품의 dependency 설정은 바꾸지 않았다.
- 정적 검사 11개 통과: 변경 FE 파일 5개 문법, BE/FE 이벤트 18종 순서 일치 1개, locale 5개 키·이벤트 문구 정합성. static-boundary-check.log. 이는 전체 타입 검사나 서버 실행 검증이 아니다.
- 이전 265/461 통과 수치를 이번 코드에 재사용하지 않는다.
- 브라우저 검증, 최신 OpenAPI 재생성, 이번 backend compile/test는 미완료. 배포/운영 Neon/외부 메일/원격 push 없음.

## 보류·이관

1. 평가자 기본라인 자동생성: 실제 HCM 관계와 유효기간·상충규칙을 확인해야 한다. 기존 사람별 배정/Excel import 유지. 근거 없는 자동배정은 구현하지 않았다.
2. 상대평가 비율 fallback: exact headcount 유지. 새 반올림·동률 정책을 임의로 도입하지 않았다.
3. 미완료자 독려 전용 UX: 기존 dashboard+알림 preview/queue/dispatch 활용. 정확한 단계별 책임자 recipient 선정·중복 기준을 별도 계약으로 보완해야 한다. 이번 감사 API는 notification dispatch 이력까지 제공한다고 주장하지 않는다.
4. KPI: legacy KPI actual과 새 부서목표/프로그램목표의 SoR 및 계산 연결을 별도 정리해야 한다. 원본 중복 생성 없음.
5. MRA: campaign/target/assignment, 익명성 및 unweighted SUBMITTED_OTHERS 통계 정책 유지. PA의 MULTI_RATER enum 잔존이 신규 다면평가 생성을 허용한다는 뜻은 아니며 현행 생성 검증에서 거부한다.

## 재개 검증

WSL 실행 복구 후 backend에서 ./gradlew test --tests '*ProgramAuditQueryServiceTest' 실행, 이어 API 실제 200/403/타 테넌트 차단/복합 필터·pagination 확인. frontend-vite에서 npm run typecheck, npm run test:i18n, npm run design:check, npm run local-ui:check, npm run build --ignore-scripts 실행. 최신 API 스키마 생성과 관리자 감사화면의 필터/페이지 이동/모바일/다국어 브라우저 검증까지 완료 후 완료로 격상한다.

## Astra 재개 결과 (2026-09-08)

- Storybook 정적 산출물 151개와 기존 production build 근거를 재확인했다. 최종 답변 뒤 foreground `http.server`가 `-1`로 종료된 현상은 제품/Storybook 빌드 실패가 아니라 장기 프로세스 수명 종료로 판정했다.
- WSL/Ubuntu가 `/bin/echo`에도 응답하지 않았고 다른 저장소 작업도 같은 배포판을 사용 중이어서, 전체 배포판 재시작은 임의 수행하지 않았다.
- Windows 임시 복사본과 Temurin JDK 21.0.12.1+1에서 신규 `ProgramAuditQueryServiceTest` 7/7, 전체 backend 272/272, `bootJar`가 통과했다. 검증 사본과 원본 ProgramAudit 소스·테스트 해시가 일치한다.
- `scripts/verify-program-audit.py`를 추가·보강해 row 6필드 exact shape, page metadata, 단독/복합 필터, 정렬, 403/cross-tenant, size 상한, 민감 필드 비노출을 검사하도록 했다. 실제 HTTP는 서버 미기동으로 0건이며 PASS로 승격하지 않는다.
- Windows 전용 PostgreSQL/API 우회는 제품 시작 전 PowerShell 자식 프로세스 대기 문제로 재시도까지 중단됐다. 두 시도 모두 전용 55489/8089 정리 완료, 기존 5432/5433은 변경하지 않았다.
- 프런트 정적 경계와 i18n 7/7은 통과했다. Windows 임시 npm 설치는 오류 없이 시간 상한을 넘겼으나 완료되지 않아 typecheck/design/local-ui/build/OpenAPI/browser는 미검증이다.
- 최신 품질 판정은 `12_quality_report.md`: backend PASS(Windows fallback), 전체 증분 완료/병합은 BLOCKED.

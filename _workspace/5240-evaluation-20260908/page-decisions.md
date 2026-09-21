# 페이지별 적용 판정

원문 전체 읽음은 evaluation-ledger.md의 Sol 검토 증거를 승계. 테스트 열의 '정적'은 실행 통과를 뜻하지 않는다.

| 번호 | URL/원문 | 최종 적용 여부·이유 | 변경 파일 | 이번 검증 |
|---|---|---|---|---|
| 1 | [평가관리 매뉴얼](https://guide.5240.cloud/evaluation) · pages/evaluation--aHR0cHM6Ly9n.json · 355자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 2 | [종합평가관리](https://guide.5240.cloud/evaluation/comprehensive/comprehensive-evaluation) · pages/evaluation_comprehensive_comprehensive-evaluation--aHR0cHM6Ly9n.json · 3,638자 | 보완 적용: 기존 감사 기록을 관리자 조회에 연결; 이력 없는 reset 복제 금지 | ProgramAuditQueryService/Controller/Repository, ProgramAuditTimeline, audit.ts, auditI18n.ts | 정적 대조; 신규 감사 테스트 실행 대기 |
| 3 | [역량업적 비율](https://guide.5240.cloud/evaluation/criteria/achievement-competency-ratio) · pages/evaluation_criteria_achievement-competency-ratio--aHR0cHM6Ly9n.json · 3,025자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 4 | [업적평가 항목](https://guide.5240.cloud/evaluation/criteria/achievement-items) · pages/evaluation_criteria_achievement-items--aHR0cHM6Ly9n.json · 3,556자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 5 | [기본평가라인](https://guide.5240.cloud/evaluation/criteria/basic-evaluation-line) · pages/evaluation_criteria_basic-evaluation-line--aHR0cHM6Ly9n.json · 3,050자 | 보류: HCM 기본 평가라인 관계·유효기간 계약 필요 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 6 | [역량구분과 비율](https://guide.5240.cloud/evaluation/criteria/competency-category-ratios) · pages/evaluation_criteria_competency-category-ratios--aHR0cHM6Ly9n.json · 2,588자 | 보류: 별도 역량 분류별 가중치 계층 필요성 미확정 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 7 | [역량항목과 비율](https://guide.5240.cloud/evaluation/criteria/competency-items) · pages/evaluation_criteria_competency-items--aHR0cHM6Ly9n.json · 3,328자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 8 | [평가기준 개요](https://guide.5240.cloud/evaluation/criteria/criteria-overview) · pages/evaluation_criteria_criteria-overview--aHR0cHM6Ly9n.json · 2,933자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 9 | [평가그룹](https://guide.5240.cloud/evaluation/criteria/evaluation-groups) · pages/evaluation_criteria_evaluation-groups--aHR0cHM6Ly9n.json · 3,221자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 10 | [평가ID](https://guide.5240.cloud/evaluation/criteria/evaluation-id) · pages/evaluation_criteria_evaluation-id--aHR0cHM6Ly9n.json · 4,442자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 11 | [예외평가라인](https://guide.5240.cloud/evaluation/criteria/exception-evaluation-line) · pages/evaluation_criteria_exception-evaluation-line--aHR0cHM6Ly9n.json · 2,660자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 12 | [등급별 점수](https://guide.5240.cloud/evaluation/criteria/grade-scores) · pages/evaluation_criteria_grade-scores--aHR0cHM6Ly9n.json · 2,787자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 13 | [조직KPI 달성율](https://guide.5240.cloud/evaluation/criteria/org-kpi-achievement) · pages/evaluation_criteria_org-kpi-achievement--aHR0cHM6Ly9n.json · 2,586자 | 보류: KPI 점수화 및 SoR 연결 계약 필요 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 14 | [절차별 비율](https://guide.5240.cloud/evaluation/criteria/procedure-ratios) · pages/evaluation_criteria_procedure-ratios--aHR0cHM6Ly9n.json · 2,710자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 15 | [상대평가 배분인원](https://guide.5240.cloud/evaluation/criteria/relative-distribution-headcount) · pages/evaluation_criteria_relative-distribution-headcount--aHR0cHM6Ly9n.json · 2,371자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 16 | [상대평가 배분비율](https://guide.5240.cloud/evaluation/criteria/relative-distribution-ratios) · pages/evaluation_criteria_relative-distribution-ratios--aHR0cHM6Ly9n.json · 2,769자 | 보류: exact 인원 유지, 비율 fallback 정책 미도입 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 17 | [평가 일상 운영 개요](https://guide.5240.cloud/evaluation/daily-operations/evaluation-daily-overview) · pages/evaluation_daily-operations_evaluation-daily-overview--aHR0cHM6Ly9n.json · 1,760자 | 보완 적용: 기존 감사 기록을 관리자 조회에 연결; 이력 없는 reset 복제 금지 | ProgramAuditQueryService/Controller/Repository, ProgramAuditTimeline, audit.ts, auditI18n.ts | 정적 대조; 신규 감사 테스트 실행 대기 |
| 18 | [평가함(직원)](https://guide.5240.cloud/evaluation/employee-screens/evaluation-box) · pages/evaluation_employee-screens_evaluation-box--aHR0cHM6Ly9n.json · 6,381자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 19 | [평가Feedback](https://guide.5240.cloud/evaluation/employee-screens/evaluation-feedback) · pages/evaluation_employee-screens_evaluation-feedback--aHR0cHM6Ly9n.json · 2,565자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 20 | [평가내역출력](https://guide.5240.cloud/evaluation/evaluators/evaluation-report-print) · pages/evaluation_evaluators_evaluation-report-print--aHR0cHM6Ly9n.json · 2,292자 | 부분 충족: 기존 XLSX 유지, 맞춤 PDF 미구현 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 21 | [평가상태관리](https://guide.5240.cloud/evaluation/evaluators/evaluation-status-management) · pages/evaluation_evaluators_evaluation-status-management--aHR0cHM6Ly9n.json · 2,791자 | 보완 적용: 기존 감사 기록을 관리자 조회에 연결; 이력 없는 reset 복제 금지 | ProgramAuditQueryService/Controller/Repository, ProgramAuditTimeline, audit.ts, auditI18n.ts | 정적 대조; 신규 감사 테스트 실행 대기 |
| 22 | [평가대상자관리](https://guide.5240.cloud/evaluation/evaluators/evaluation-targets) · pages/evaluation_evaluators_evaluation-targets--aHR0cHM6Ly9n.json · 3,867자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 23 | [평가자관리](https://guide.5240.cloud/evaluation/evaluators/evaluator-management) · pages/evaluation_evaluators_evaluator-management--aHR0cHM6Ly9n.json · 3,882자 | 부분 충족: 수동/Excel 배정 유지, 자동라인 보류 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 24 | [미평가자모니터링](https://guide.5240.cloud/evaluation/evaluators/unevaluated-monitoring) · pages/evaluation_evaluators_unevaluated-monitoring--aHR0cHM6Ly9n.json · 2,772자 | 부분 충족: 기존 알림 사용, 미완료 책임자 전용 독려 보류 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 25 | [평가 업무의 기본 개념](https://guide.5240.cloud/evaluation/faq-policy/evaluation-basics) · pages/evaluation_faq-policy_evaluation-basics--aHR0cHM6Ly9n.json · 3,191자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 26 | [KPI관리](https://guide.5240.cloud/evaluation/org-kpi/kpi-management) · pages/evaluation_org-kpi_kpi-management--aHR0cHM6Ly9n.json · 3,465자 | 보류: 기존 두 KPI 축 SoR 정리 필요 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 27 | [조직KPI목표/실적관리](https://guide.5240.cloud/evaluation/org-kpi/org-kpi-targets) · pages/evaluation_org-kpi_org-kpi-targets--aHR0cHM6Ly9n.json · 3,796자 | 부분 충족: 기존 actual 이력 유지, 5240 수동 점수 정책 미도입 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |
| 28 | [이럴 때 이 문서](https://guide.5240.cloud/evaluation/troubleshooting/troubleshooting-index) · pages/evaluation_troubleshooting_troubleshooting-index--aHR0cHM6Ly9n.json · 2,093자 | 기존 구현 유지: 기존 기준·단계·권한 계약으로 대응, 5240 세부정책 복제 없음 | 없음 | 정적 대조; 신규 감사 테스트 실행 대기 |

## 후속 검증 결과 (동일 작업, 2026-09-08)

- 감사 화면 관련 정적 검사 11개 통과(문법 5, BE/FE 이벤트 18종 일치 1, locale 정합 5).
- 기존 다국어 테스트 7/7 통과. 첫 Windows 실행의 공유 번역 패키지 미해석을 검증용 module hook으로 실제 package exports에 연결하여 재실행했다. 제품 dependency 설정 변경 없음.
- 전체 tsc는 공유 UI/HTTP 패키지 미해석 및 타입 오류로 미통과. BE 신규 7개 실행, 실제 API, 브라우저, 배포 빌드, OpenAPI 재생성은 미완료. 완료로 보고하지 않는다.
- 실행 로그/재현 스크립트/정적 결과: 제품 _workspace/5240-evaluation-20260908/. 상세 최신 상태는 review.md.

## Astra 재개 검증 (2026-09-08)

- Backend focused 7/7, 전체 272/272, bootJar PASS(Windows 임시 복사본·원본 해시 일치).
- 감사 API↔React Query 6필드·Page subset·18 event enum·권한/tenant/program 경계 정적 PASS.
- HTTP verifier는 exact shape·page metadata·단독/복합 필터까지 보강했으나 런타임 부재로 checks=0.
- FE 실행/type/build/OpenAPI/browser는 WSL 무응답과 Windows npm 설치 미완료로 BLOCKED. 본 표의 기존 “신규 감사 테스트 실행 대기”는 backend 단위 테스트에 한해 해소됐고, 실제 HTTP/JPA·화면 검증은 여전히 대기다.

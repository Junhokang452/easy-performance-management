# S2 KPI 연계 — 실행 입력

2026-09-08 사용자 재개 요청. S1 완료 이후 S2부터 진행한다.

- 범위: easy-performance-management backend/program/domain-kpi·DB·frontend 및 로컬 검증. HCM/core/공유표준/운영환경 변경 없음.
- 목표: 기존 KPI 원본·실적 이력을 평가 프로그램/참여자와 명시적으로 연결하고 서버 계산값 및 버전/근거를 보존한다. 프런트 재계산·암묵 점수 확정 금지.
- 보호: S1·기존 dirty 변경, 수동/Excel 배정, KPI 원본/실적 이력, 알림/XLSX/감사 계약 유지. 외부 DB/SMTP/push/deploy/커밋 없음.
- 팀: Astra 조율·설계판정·실제 API 검증·최종기록, Sol backend 계약/구현/테스트, Terra FE 계약/구현/브라우저, Luna 독립표준·경계QA.
- 순서: 계약/표준 조사 → 설계 gate → BE/FE 구현 → 단위/타입/실 API·브라우저 → 문서/체크포인트.
- 환경: 현재 Boot4.1.1 유지, Java21/Gradle8.14.5 Windows 전용 사본, FE 기존 Windows node_modules 활용. WSL 관리자 조작·의존성 재설치·타 프로세스 종료 금지.
- 다음 슬라이스 S3/S4는 이번 S2와 구분한다. 정책 선택으로 의미 있는 확장이 필요하면 사용자 확인 후 진행한다.

## 설계 범위 고정

- ProgramGoal 하나에 KpiAssignment 하나를 명시 연결한다. 교체/최신화도 preview → 이유 입력 → apply로 새 immutable revision을 만들며 과거 근거는 보존한다.
- evidence-only: 기존 goal의 가중치·목표값·달성 단계·review submission·최종 점수 계산을 변경하지 않는다. 향후 점수 합성 정책은 이번 작업 밖이다.
- 조직발령 기준 `program.asOfDate`와 실적 `cutoffDate`를 구분한다. 실적 기준일은 명시 입력하고 서버가 프로그램 기간/선택 원본 범위를 검증한다.
- 실제 leaf 실적을 선택하며 target/weight는 capturedAt의 현재 effective 값이다. 역사적인 정의를 재현했다고 주장하지 않는다.
- 기존 KpiService의 정정 실적 누락은 동일 selector 경로와 회귀 테스트에 한정해 보완한다. KPI 원본/실적 수정 API는 추가하지 않는다.

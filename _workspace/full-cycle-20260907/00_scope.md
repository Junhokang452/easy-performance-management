# 全평가 사이클 구현 범위

사용자의 관리자32개 링크를 구성원16개 분석에 결합하고 전체화면/프로세스 구현을 계속한다. 이번엔 분석으로 종료하지 않는다. 기존 단일KPI/Boot4 보안변경 및 사용자 작업 모두 보존한다.

SoT: easy-standards의 공통코드/프런트/다국어/테넌시 원칙, member-cycle-analysis-20260907/00_scope.md. 실제버전 Boot4.1.1/Tomcat11.0.25/Java21/Gradle8.14.5/core1.0-SNAPSHOT, React19/Mantine9/Vite 유지. 고객Neon프로젝트/제품DB물리격리 유지, 로컬 합성PG 검증만 사용. 외부프로비저닝/배포/push 없음.

구현방향: 기존 고정KPI 흐름의 데이터 보존. 구성형 평가 정의·개인/차수별 실행·항목응답·결과버전은 새 도메인으로 구현하고 새메뉴/독립화면에서 연결. 공통 인증 ActorAccess/UUID/감사/오류/테넌트 재사용. 선택단계와 권한은 서버가 판정. 자료분석표의 요구를 acceptance로 추적.

팀소유: backend_flow 관리자기본10문서→평가엔진BE 예정; auth_boundary_review 운영11문서→과제/면담BE 예정; frontend_completion 통계6문서→FE 예정; root 정책5문서/통합계약/공통UI/통합QA.

슬라이스: S0 원문48개요구대조, S1 계약/정의/스키마, S2 평가엔진, S3 성과과제·면담·근거자료, S4 FE 독립화면·5locale, S5 계산/공개/통계, S6 실제API/화면/보안검증 및 기록. 동시 공유패키지빌드와브라우저검증 금지(HMR회귀방지).

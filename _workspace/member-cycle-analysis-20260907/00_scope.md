# 이번 단계 범위와 표준

사용자 요청: 기존 화면은 너무 간단하므로 구성원 목표 수립·중간점검·평가 전체 화면/프로세스를 구현하려 한다. 우선 제공한 구성원 가이드를 분석 완료한 뒤 알리고, 사용자가 제공할 관리자 가이드와 결합한다. 이번 단계는 자료 분석·기존 코드 대조·구현 요구 정리이며 제품 코드는 변경하지 않는다.

저장소: easy-performance-management. 공통 코드/디자인 SoT: easy-platform-core, easy-standards. 기존 _workspace 증거는 이동/삭제하지 않고 별도 디렉터리에 후속 분석을 추가한다.

유지할 기준: ADR-007 공유 코드 경계, ADR-013/024 고객별 Neon 프로젝트/제품 DB, ADR-014 자매품 스택, 07-frontend, 17-i18n-label-conventions, performance-evaluation-shared-assets-2026-09-07. 실제 스택은 직전 승인된 Boot4.1.1/Tomcat11.0.25/Java21/Gradle8.14.5이며 문서에 남은 구버전으로 회귀시키지 않는다.

5개 언어 ko/en/ja/zh-CN/vi와 공통 토큰/SuiteShell 유지. 재사용 가능한 화면 자산은 공유 라이브러리에 컴포넌트·스토리·사용 계약을 등록할 후보로 정리한다. 평가 정책/상태/권한은 제품 도메인에 유지한다. 분석 중 외부 DB/배포/push/제품 빌드 변경 없음.

팀: root 원문 핵심10+흐름연결1/통합; auth_boundary_review 보조6문서; backend_flow 현재BE갭; frontend_completion 현재FE갭. 코드 탐색은 읽기 전용, 문서별 소유권 분리.

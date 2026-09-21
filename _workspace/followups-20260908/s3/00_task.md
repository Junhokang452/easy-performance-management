# S3 책임자 독려 — 실행 입력 및 라우팅

2026-09-08. 사용자 S2 이후 계속 진행 요청. 기존 S1/S2 산출물 및 dirty 변경 보존. 이번 슬라이스는 S3이고 S4 PDF는 다음이다.

- 영향 저장소: easy-performance-management 단일. backend program/notification·필요한 증분 DB·frontend-vite 평가 운영 UI·로컬 검증/문서.
- 목표: 프로그램 단계별 실제 미완료 책임자를 서버에서 계산하고, 명시 미리보기/확인 후 중복 방지되는 내부 알림을 제공한다. 기존 일괄 알림을 대체하거나 직원 전체로 잘못 발신하지 않는다.
- 제외: 운영 발신/SMTP·외부 DB/S2S·배포·커밋·HCM/core/공유표준 변경. 자동 반복 스케줄러는 추가하지 않는다.
- 팀: Astra 조율/설계 게이트/기존 delivery 안전검토/실 API·최종 문서, Sol BE 계약 및 구현/테스트, Terra FE 계획·구현/5locale, Luna 표준매핑/독립 경계QA.
- Phase3a 계약/표준 게이트 전 제품 구현 금지. 정의되지 않은 책임자는 추정 수신자로 대체하지 않고 차단/미배정으로 설명한다.
- runtime: 실제 현재 Boot4.1.1, Java21, Gradle8.14.5 유지. S2 Windows 격리 검증 사본/의존성 재사용. WSL 관리자조작·타 프로세스 종료 금지.
- 파일상 AGENTS.md/.Codex agent 정의는 현재 경로에 없음. 제공된 상위 AGENTS 지침 및 이용 가능한 스킬 적용. 모델은 사용자 명시 Astra/Sol/Terra/Luna 사용(스킬의 구형 opus 지시 대체).

예상 범위: BE/테스트/필요 migration 약 8~12파일, FE 4~6파일. 계약 조사로 정확한 범위 확정 후 게이트 기록.

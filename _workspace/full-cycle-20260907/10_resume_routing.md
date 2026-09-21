# 재개 작업 라우팅

## 영향 저장소

- `easy-performance-management`: 백엔드, 프런트엔드, 로컬 실행 스크립트, 검증 산출물.
- `easy-platform-core` gitlink/제품 로컬 사본: 읽기 중심으로 Storybook 정적 산출물과 공유 자산 정합성 확인.
- `easy-standards`: 기존 conformance 기록 확인만. 실제 표준 변경은 별도 승인 전 수행하지 않음.

## 담당

- Luna: standards-guardian — Phase 3a 표준·체크포인트 재개 게이트.
- Sol: backend-spring — 백엔드와 로컬 런타임 진단/필요 수정.
- Terra: frontend-mantine — 프런트와 Storybook 진단/필요 수정.
- Astra: 통합 판단, 변경 충돌 조정, 품질·경계 QA 및 최종 큐레이션.

## 게이트

- 기존 완료 증거와 현재 소스의 일치 여부를 우선 확인한다.
- Storybook 서버 종료를 제품 결함과 프로세스 수명 관리 문제로 구분한다.
- 수정 후 관련 빌드·테스트·HTTP 상태를 비례 검증한다.


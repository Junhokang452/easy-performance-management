# easy-standards 갱신 후보 — 감사 조회 검증

상태: 로컬 제안만 작성. easy-standards 수정·PR 생성·머지·버전 변경 없음.

## 1. 감사 원본과 운영 조회 투영의 분리

- 대상 후보: `00-principles/04-observability.md` 감사 이력 절, 신규 `90-conformance/performance-evaluation-audit-query-2026-09.md`.
- 사유: append-only 원본의 `detailsJson`을 관리자 화면에 그대로 노출하지 않고, 필요한 여섯 필드만 제공하는 PA 계약을 재사용할 수 있다.
- 제안: 서버 operator 인가, tenant+program 범위, page size 상한, createdAt/id 안정 정렬, 민감 필드 비노출을 acceptance 항목으로 명시한다.
- 영향: 우선 PA 운영 감사 조회. 다른 제품은 감사 요구와 개인정보 범위를 별도 검토한 뒤 적용한다.
- ADR: 기존 보안·관측성 원칙 보강 후보이며 신규 ADR 번호를 임의 할당하지 않는다.

## 2. 교차 테넌트 음성 검사에 양성 대조군 포함

- 대상 후보: `00-principles/02-security-owasp.md`, `00-principles/05-cicd-harness-gates.md` 인가 검증 절.
- 사유: 존재하지 않는 foreign actor나 기존 cookie와 foreign Bearer를 혼용하면, 인증 실패를 테넌트 격리 성공으로 오판할 수 있다.
- 제안: cookie-free B client로 유효 B session 및 자기 소속 목록 200을 먼저 확인한 뒤 A 객체 접근 거부를 검증한다. 별도로 존재하지 않는 B actor의 401도 확인한다.
- 근거: 이번 실연동 17개 assertions에서 B session 200 → B programs 200 → invalid B actor 401 → A audit 404를 모두 확인했다.

SoT 경로/pin 확인 후 별도 승인된 표준 PR에서 채택 여부를 결정한다. 이번 작업은 제품 감사 조회 검증이며 공유 규칙·스택·테넌시 결정을 변경하지 않아 AGENTS.md 규칙 델타를 추가하지 않았다.

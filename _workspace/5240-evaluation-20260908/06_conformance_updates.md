# Conformance 갱신 안

상태: 로컬 제안. 공유 easy-standards 저장소에는 쓰지 않았다.

대상 후보: `90-conformance/performance-evaluation-audit-query-2026-09.md`.

- [x] 서버 operator·tenant·program 범위 및 여섯 필드 최소 투영.
- [x] 실제 PostgreSQL/JPA + HTTP 17/17: 필터·정렬·페이지·민감 필드 비노출·유효 foreign 세션 대조군.
- [x] 최신 OpenAPI 명세 수집(195 paths, 감사 GET 및 query 계약).
- [x] FE i18n 7/7, 평가 workspace 5/5, 디자인·local-ui 검사.
- [x] 전체 FE typecheck — bundled Node 24, exit 0.
- [x] production build exit 0 및 브라우저 14/14: 5 locale×2 viewport, 필터·페이지·null 표시·일반 사용자 차단.

신규 제품 슬라이스 번호는 SoT의 현행 번호를 확인하지 않은 상태에서 선점하지 않는다. raw Spring Page의 명시적 envelope 전환은 후속 하위호환 검토 후보이며 이번 변경 범위에서 API shape를 바꾸지 않았다.

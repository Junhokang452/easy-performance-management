# S1 conformance 등록 초안

- 제품/범위: easy-performance-management 자동 평가라인 + easy-hcm 수동 발신 어댑터.
- 상태: 로컬 구현·검증 증거는 `99_final_report.md`에 집계한다. 운영 통합 인증을 의미하지 않는다.
- 보존 계약: 수동/Excel 배정, 기존 다중 평가자, audit projection, 공통 HTTP 오류 구조.
- 신규 계약: manager/tombstone/sourceVersion, signed tenant envelope, preview/apply fingerprint와 provenance.
- 증거: backend verification JSON, HCM focused XML, 실제 HTTP 및 tenant isolation JSON, 5언어 browser JSON/PNG, OpenAPI schema.
- 제한: HCM 전체 테스트는 기존 payroll test의 잘못된 import로 컴파일 차단. HCM 실제 운영 DB/외부 HTTP는 미실행. Model B 활성 상태 검증은 로컬 단위 테스트이며 live control-plane 검증은 하지 않았다.
- 잔여 슬라이스: S2 KPI 연계 → S3 책임자 독려 → S4 맞춤 PDF. 이번 S1으로 전체 요청 완료 처리하지 않는다.

공유 easy-standards 및 루트 AGENTS 이력은 직접 수정하지 않았다. 실제 파일이 없는 루트 `.Codex/AGENTS.md`를 새로 만들거나 제공된 대형 하네스 설명을 재작성하지 않는다.

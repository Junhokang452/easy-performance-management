# S1 자동 평가라인 완료 보고

2026-09-08. Astra 조율, Sol PA backend, Terra frontend, Luna 독립 QA, root HCM sender 및 실제 API 검증. 요청한 전체 순서 중 **S1만 완료**이며 S2 KPI 연계 → S3 미완료 책임자 독려 → S4 맞춤 PDF가 남아 있다.

## 구현 결과

- HCM 기준일 상사 관계·유효기간·삭제 상태·microsecond sourceVersion 수신 기반 자동 평가라인 미리보기/적용.
- 최대 100명, 명시 선택 역할, 적용 이유 필수. 불명확·유효하지 않은 원본은 차단하고 기존 평가자가 있는 참여자는 건너뛴다. 수동/Excel 다중 평가자 계약을 보존한다.
- preview fingerprint 재검증, 변경된 원본은 409, 저장된 실행 결과 재사용, 공유 프로그램 잠금으로 동시 적용·재시도 중복 방지. 출처와 실행 기록을 보존한다.
- signed tenant/body/header/배포 allowlist 일치, ACTIVE route 재검증·복원, 신규 read-model INSERT-only, tenant PK 충돌 403 및 전체 rollback. S2S 갱신은 REPEATABLE_READ로 sourceVersion 비교/쓰기 충돌을 제어하며 암묵 재시도하지 않는다.
- HCM 발신은 기본 OFF·명시 수동 호출 전용, 권한 검사·고정 HTTPS origin·timeout/no-redirect·UTF-8 HMAC, DB 읽기 트랜잭션 이후 HTTP. 자동 스케줄/실제 외부 발신은 추가하지 않았다.
- HR 운영 화면에 미리보기·사유 확인·결과 표시와 5개 언어를 추가하고 OpenAPI schema를 재생성했다.

## 최종 검증

| 항목 | 실제 결과 | 근거 |
|---|---|---|
| PA 전체 backend | 294/294, 실패·오류·skip 0, bootJar PASS | `s1-backend-verification-summary.json` |
| PA S1 focused | 36/36 | 위 JSON 및 backend 보고서 |
| HCM 신규 범위 | main compile PASS, focused 15/15 | `hcm-test-results/`, `02_s1_hcm_implementation.md` |
| 최종 JAR 실제 API | 22/22 | `reviewer-line-local-final.json` |
| 실제 PostgreSQL 다른 tenant PK 충돌 | 3/3, 모두 403, B 불변·A 전체 rollback | `reviewer-line-isolation-final.json` |
| 실제 브라우저 | 13/13, 5언어 × 1440/390, 적용·권한·사유 검증 | `browser-s1/result.json`, PNG |
| FE | TypeScript, production build, i18n 7/7, Node workspace 5/5, 디자인/UI 규칙 PASS | `02_s1_frontend_implementation.md` |
| Flyway | 전용 PG에서 20260908.001 success=true | 실제 psql 조회 |
| OpenAPI | 최종 실행 스펙과 저장 스펙 byte-identical | `s1-openapi.json` |

최종 실행 JAR SHA-256: `1D3AABF5DCFEC0EFE13FF9DF39355FA185701F9CAA5990BF5E4C83F84483F454`, 72,533,389 bytes. PA 변경 23개, HCM 소유 7개 파일은 검증용 Windows 사본과 byte parity를 확인했다. 최종 OpenAPI SHA-256: `ae0ac5e953e861e75cc03a5704ada36add3a2a8b0524fba81c2135291bc01c5d`.

브라우저는 최종 receiver 내부 hardening 직전 빌드로 실행했으며, 이후 FE/DTO 변경은 없고 최종 OpenAPI가 byte-identical임을 확인했다. 최종 API/tenant isolation은 위 최종 JAR로 다시 실행했다. 구 `reviewer-line-isolation.json`의 500은 guard 보강 전 진단 기록이며 최종 결과가 아니다. `browser-s1/failure.png`도 최초 테스트 스크립트의 modal DOM 가정 오류 진단으로 보존했고, 최종 result.json을 우선한다.

## 제한 및 보존

- **HCM 전체 suite는 통과로 보고하지 않는다.** 기존 `backend/src/test/java/com/easyhcm/backend/payrollpreparation/PayrollPreparationControllerTest.java:17`의 잘못된 `AuthenticationPrincipalArgumentResolver` import로 test compile이 차단된다. 해당 타 작업 파일은 수정하지 않았다.
- HCM snapshot은 합성 H2, outbound HTTP는 MockRestServiceServer, PA는 전용 실제 PostgreSQL/HTTP로 검증했다. 실제 HCM 운영 DB → PA 외부 발신 통합과 Model B live control-plane 검증은 하지 않았다.
- 운영 설정·외부 DB·SMTP·push·deploy·git commit 없음. 기존 대량 dirty 변경과 다른 프로세스를 보존했다. PA/HCM의 현재 Boot 4.1.1 기준으로 검증했으며 오래된 하네스 버전으로 낮추지 않았다.
- 전용 API 8089/PID3536 및 전용 PG 55489를 정상 종료했다. 기존 PostgreSQL 5432/PID8920, 5433/PID8664 유지 확인. 검증 데이터·임시 사본·산출물은 삭제하지 않았다. 기록: `s1-runtime-final.json`.
- easy-suite-orchestrator에 따라 계약 선작성·독립 QA·대규모 슬라이스 종료 체크포인트를 적용했다. 표준 갱신은 `06_standards_proposals.md`/`06_conformance_updates.md` 로컬 제안으로만 남겼다.

## 다음 작업

S2 KPI 연계는 program/participant와 KPI cycle/assignment의 연결, 계산 버전·근거 동결, 누락 및 권한 정책을 먼저 정한다. FE는 기존 서버 계산값과 실적 이력 재사용이 가능하며 약 4~6파일 규모로 예상한다(BE/DB 범위는 계약 조사 후 확정). 프런트 점수 재계산·암묵 확정은 하지 않는다. 별도 역량 계층/상대평가 비율 정책은 범위 밖이다.

큰 S1을 마쳤으므로 다음 슬라이스는 `checkpoint.md`를 읽고 `이어서 진행`으로 재개할 수 있다.

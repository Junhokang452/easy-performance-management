# 품질 검증 리포트

작성: 2026-09-08 (Asia/Seoul)  
대상: `easy-performance-management` / Program Audit 조회 증분  
최종 판정: **본 Program Audit 감사 증분 PASS (Windows fallback) — 전체 dirty tree·5240 백로그 판정 아님**

## 빌드/타입

| 대상 | 결과 | 현재 근거 |
|---|---|---|
| Backend focused `ProgramAuditQueryServiceTest` | **PASS** | 7/7, failures/errors/skipped 0, Windows fallback source hash match |
| Backend 전체 test/compile 경로 | **PASS** | 272/272, failures/errors/skipped 0 |
| Backend `bootJar` | **PASS** | exit 0, Windows fallback JAR 산출 |
| 실제 PostgreSQL/JPA/API verifier | **PASS** | held runtime 17/17 assertions, null actor/participant verified |
| OpenAPI generation/contract extraction | **PASS** | generation exit 0(6.3초), 원본 schema 기계적 동기화, 188,246 bytes, 195 paths, audit GET list 7/query 5, SHA-256 prefix `7b8679ee...` |
| Shared core HTTP/query/i18n-3dist build | **PASS** | bundled Node 24, exit 0 |
| FE i18n/workspace/design/local-ui | **PASS** | i18n 7/7, workspace 5/5, design hex 0/inline 0, local-ui tags 0/imports 0, bundled Node 24 exit 0(약 10초) |
| FE `typecheck` | **PASS** | bundled Node 24, exit 0, stdout/stderr 없음 |
| FE production `build` | **PASS** | Vite exit 0, 75 assets, 1,733,861 bytes, `ProgramOperationsPage` chunk 포함 |
| Browser verifier | **PASS** | 14/14, exit 0; 5 locale×1440/390px, overflow/error/filter/page/null/employee checks |

## 테스트

- Backend focused: **passed=7 failed=0 skipped=0**.
- Backend 전체: **passed=272 failed=0 skipped=0**.
- 실제 API: **17/17 PASS**. 포함 범위는 exact six-field/projection, pagination/sort, 단독·복합 filter, employee 403, valid tenant-B cookie-free session 200/HR_ADMIN, B own programs 200, invalid B actor 401, tenant-A audit 404, size=101=422/E9804256, null actor/participant fixture verified다.
- FE auxiliary: i18n 7/7, workspace 5/5, design/local-ui 정적 게이트 모두 PASS.
- FE 전체 `tsc`: bundled Node 24 exit 0, stdout/stderr 없음으로 **PASS**.
- production build/browser: **PASS**. Browser 원본 evidence는 `_workspace/5240-evaluation-20260908/browser-20260908-final/result.json`이다.

## 표준 준수 (12 규칙 + ADR)

- [규칙 #1] **PASS** — 신규 JPQL optional enum/UUID 조건에 date null 패턴 없음; 실제 PG/JPA 실행도 통과.
- [규칙 #3] **PASS** — audit list는 bounded `PageRequest`와 size 1~100을 사용하고 실제 page/filter를 통과.
- [규칙 #6] **PASS** — 신규 audit 코드에서 secret fail-fast 약화 없음.
- [규칙 #8] **PASS** — 신규 audit 코드에 `synchronized` 없음.
- [규칙 #9] **N/A/PASS** — 신규 Excel/XSSFWorkbook 없음.
- [UUIDv7 PK] **PASS** — `ProgramAuditEvent.@PrePersist`가 `UuidV7.generate()` 사용; 테스트 fixture의 random UUID는 런타임 PK가 아님.
- [인덱스] **PASS** — 기존 `program_audit_event` 인덱스가 `(tenant_id, program_id, created_at)` 및 `(tenant_id, participant_id, created_at)` tenant 선두.
- [리스트 API] **PASS** — Pageable, page/size 경계, newest-first sort가 정적·실행에서 확인됨.
- [캐시] **N/A** — 신규 `@Cacheable` 없음.
- [FE 래퍼/RQ/구조] **정적 PASS** — `@easy/ui-components`, React Query, `src/features/evaluation-programs/` 사용; 신규 unsafe cast 없음. 실행 typecheck/build는 대기.
- [Flyway/ADR] **PASS/N/A** — 이번 projection은 기존 schema를 읽고 신규 migration 없음.

## 계약 정합성

- JPA ↔ DB: **PASS** — 정적 컬럼/인덱스 일치 및 실제 PG/JPA 실행 통과.
- Backend DTO ↔ Frontend type: **PASS** — 6필드·nullable shape 및 민감 필드 비노출 일치.
- Endpoint/query/route: **PASS** — `/api/v1/...`, shared `/api` prefix, FE `/v1/...`, filters/page 모두 일치.
- 권한/tenant scope: **PASS** — valid B cookie-free 200, B own programs 200, invalid actor 401, A audit 404로 실제 경계 확인.
- OpenAPI: **PASS** — 195 paths 및 audit GET/query 추출 정합 확인. generated artifact SHA-256 prefix `7b8679ee...`.
- Browser UI contract: **대기** — production bundle 기반 실제 화면 조작 결과만 남음.

## Verifier 판정

- 최신 `verify-program-audit.py`는 별도 cookie-free opener로 foreign Bearer를 사용하고 valid B actor 선행 검증을 포함한다. 따라서 이전 session-cookie 혼용 우려는 해소됐다.
- 실제 held API artifact `windows-audit-held-api.json`: `passed=true`, `checks=17`, `error=null`, `nullActor=verified`, `nullParticipant=verified`.
- size=101 기대값은 실제 API contract `422 / E9804256`에 맞춰 정정됐으며 17/17 PASS했다.

## 현재 미검증/차단

- **PASS — FE typecheck**: bundled Node24에서 전체 TypeScript가 exit 0, stdout/stderr 없음.
- **PASS — FE production build**: Vite exit 0, 75 assets, 1,733,861 bytes, `ProgramOperationsPage` chunk 포함.
- **PASS — browser**: 14/14 exit 0; 5 locale×1440/390px, overflow false, console/pageerror/API error 없음, filter/page/null 표시/employee denial 확인.
- OpenAPI는 최신 generation exit 0(6.3초), 원본 schema 기계적 동기화, 188,246 bytes/195 paths로 생성·검토됐다. 첫 30초 요청 timeout은 과거 managed-server 수명 이력으로 분리한다.

## 과거 실행 이력(현재 판정과 분리)

- 초기 WSL focused 명령 무응답 및 WSL restart `StopPending`, 관리자 force UAC 취소는 현재 Windows fallback 결과와 별개인 환경 이력이다.
- 초기 Windows temp `npm ci` timeout/partial node_modules 기록은 현재 bundled Node24 i18n/workspace/design/local-ui PASS와 혼합하지 않는다.
- 초기 Windows PG process-wait 중단은 후속 managed runtime의 실제 PG/JPA/API 17/17 PASS로 대체되었으며 제품 결함이 아니다.

## dirty/untracked 및 shared audit 분리

- 현재 working tree에는 여러 에이전트의 tracked modification과 untracked 파일이 함께 있다. 전체 dirty tree를 Program Audit 증분 결함으로 귀속하지 않으며, 다른 변경을 되돌리지 않았다.
- `shared-npm-audit.json`의 7건(High 6/Moderate 1)은 과거 결과이며 현재 재감사하지 않았다. 별도 보안 owner의 재감사·fix/예외 만료 확인이 필요하고, 이번 증분 테스트 실패로 계산하지 않는다.

## 병합 판정 및 남은 순서

1. Backend, 실제 PG/JPA/API 17/17, OpenAPI, FE auxiliary gates, FE 전체 typecheck, production build, browser 14/14를 모두 통과로 기록한다.
2. 추가 viewport 캡처 재실행은 증거 보강용으로 보존하며 제품 DOM 변경으로 간주하지 않는다.
3. 본 Program Audit 감사 증분 품질 게이트는 **PASS**다. 단, 대량 dirty tree 전체 merge나 5240 전체 백로그 완료로 확대 해석하지 않는다.

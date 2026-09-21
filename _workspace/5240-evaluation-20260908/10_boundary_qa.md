# 경계면 정합성 리포트

작성: 2026-09-08  
대상: `easy-performance-management` Program Audit 조회 증분  
판정: **본 Program Audit 감사 증분 PASS (Windows fallback) / 전체 dirty tree·5240 백로그 판정 아님**

## 현재 확정 증거

- Backend Windows fallback: focused `ProgramAuditQueryServiceTest` **7/7**, 전체 backend **272/272**, `bootJar` exit 0. 임시 복사본과 ProgramAudit 소스 hash가 일치한다.
- 실제 PostgreSQL/JPA/API: held runtime에서 verifier **17/17 PASS**. exact six-field, projection, newest-first, page metadata, adjacent pagination, eventType/participantId 단독·복합 필터, employee 403, tenant-B cookie-free session 200/`HR_ADMIN`, B own programs 200, invalid B actor 401, tenant-A audit cross-tenant 404, size=101 **422/E9804256**, null actor/participant fixture verified를 포함한다.
- OpenAPI: schema generation exit 0(6.3초), 원본 schema 기계적 동기화 확인. generated document **188,246 bytes / 195 paths**, audit GET list **7개**, query **5개** 확인. 산출물 SHA-256 prefix `7b8679ee...`.
- Shared core bundled Node 24: HTTP/query/i18n-3dist 실제 build exit 0.
- FE bundled Node 24: i18n **7/7**, workspace **5/5**, design `hex=0`, `inline=0`, local-ui `tags=0`, `imports=0`, 모두 exit 0(약 10초).
- FE 전체 `tsc`: bundled Node 24 exit 0(출력 없음).
- Vite production build: exit 0, 75 assets, **1,733,861 bytes**, `ProgramOperationsPage` chunk 포함, 약 5분 15초. root CI에서도 vite/npm/tsc 잔여 프로세스 없음과 exit 0를 확인했다.
- Browser verifier: **14/14 PASS, exit 0**. 5 locale×1440/390px, overflow false, console/pageerror/API error 없음, 필터·페이지·null actor/participant 표시, employee 403·audit heading 없음 확인. 원본 evidence: `_workspace/5240-evaluation-20260908/browser-20260908-final/result.json`.
- 현재 추가 viewport 캡처 재실행은 증거 보강용이며 제품 DOM 변경이 아니다. locale는 기존 브라우저 locale 설정을 따르므로 5언어 UI 라벨·레이아웃 범위다.

## 계약 정합성

- API 응답 shape — Backend `AuditRow`와 FE `AuditRow`가 `id`, `participantId`, `eventType`, `reason`, `actorEmployeeId`, `createdAt` 6필드 및 nullable을 일치시킨다. `detailsJson`·`tenantId`는 투영·소비하지 않는다. **PASS**.
- Page envelope — Backend `Page<AuditRow>`와 FE가 사용하는 `content`, `totalElements`, `totalPages`, `number`, `size`가 실제 API와 일치한다. **PASS**. raw Spring `Page`의 OpenAPI 안정 DTO화는 후속 개선사항이다.
- Filter/sort — `eventType`, `participantId`, `page`, `size` query가 양쪽에서 일치하고 실제 API에서 단독·복합 필터와 `createdAt DESC, id DESC`가 통과했다. **PASS**.
- 권한/tenant/program — operator 확인 → tenant-scoped program 조회 → tenant+program repository 조건을 정적·실 API에서 교차 확인했다. B 유효 actor와 invalid actor를 별도 cookie-free client로 검증해 세션 혼용 허위 PASS 우려를 해소했다. **PASS**.
- 민감 정보 — `detailsJson` 및 `tenantId`가 실제 응답에 노출되지 않았다. **PASS**.
- enum/i18n — Backend/FE event type 18종과 ko/en/ja/zh-CN/vi 라벨이 일치한다. FE i18n 7/7 및 5 locale 정적 검사가 통과했다. **PASS**.
- JPA/DB — `ProgramAuditEvent`와 기존 `V20260907_004`의 컬럼·UUID/JSONB·tenant 선두 인덱스가 일치하며 실제 PG/JPA verifier가 통과했다. **PASS**.
- OpenAPI — generated artifact와 audit endpoint/query 정합을 확인했다. **PASS**.

## Verifier 정합성

- `scripts/verify-program-audit.py`는 `cookie_free_get()` 별도 opener로 foreign Bearer를 호출한다. 유효 B session 200/role, B own program 200, invalid B actor 401을 먼저 확인한 뒤 A audit 404를 검증하므로 tenant 차단의 양성 증거가 성립한다.
- size 상한은 실제 domain contract `422 / E9804256`로 정정되었고 17/17 실행에서 통과했다.
- null actor/participant는 실제 fixture에서 `verified`다. UI null fallback 렌더링은 browser 대기 항목이다.

## FE 실행 게이트

- PASS: bundled Node 24 기반 i18n, workspace, design, local-ui, shared core build, FE 전체 `tsc`, Vite production build.
- PASS: browser verifier 14/14(관리자/일반 사용자, 5 locale, 390/1440px, pagination/filter/refresh, overflow/pageerror/requestfailed).
- 이전 Windows temp `npm ci` timeout 및 partial dependency 상태는 **과거 실행 이력**이며 현재 bundled Node24 PASS 결과와 혼합하지 않는다.

## 과거 환경 이력(현재 판정과 분리)

- WSL restart는 사용자 승인 후 시도했으나 `StopPending`으로 정상 종료되지 않았고, 관리자 force UAC 승인은 취소되어 추가 권한을 요청하지 않았다.
- 초기 Windows PG harness는 process-wait에서 중단됐으나, 후속 managed runtime에서 실제 PG/JPA/API 17/17 PASS가 확보되어 제품 실패로 분류하지 않는다.

## 잔여 사항

- 본 감사 증분 범위에서는 필수 품질 게이트가 모두 통과했다. 이는 전체 dirty tree 병합 또는 5240 전체 백로그 완료를 의미하지 않는다.
- `_workspace/full-cycle-20260907/shared-npm-audit.json`의 과거 shared dependency audit 7건(High 6/Moderate 1)은 현재 재감사하지 않았다. Program Audit 증분 결함으로 계산하지 않으며 별도 보안 재감사가 필요하다.
- dirty/untracked 전체 작업트리는 여러 에이전트 범위를 포함하므로 이번 증분 결함과 동일시하지 않는다.

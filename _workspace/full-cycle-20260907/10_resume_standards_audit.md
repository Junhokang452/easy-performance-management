# Phase 3a 재개 표준·체크포인트 감사

작성: 2026-09-08  
범위: `easy-performance-management`, `full-cycle-20260907` 재개 + `5240-evaluation-20260908` 평가 시스템 보완  
판정: **이전 RootHR 전체 사이클은 완료 기록이 있으나, 2026-09-08 증분 보완은 미완료. Phase 3b/3c 진입 전 검증 게이트가 남아 있다.**

## 판정 근거

- 체크포인트의 초반 상태 문구는 `checkpoint.md:3`의 “구현 진행 중”이지만, 같은 파일의 `## 최종 완료`(`checkpoint.md:55-60`)와 `09_verification_report.md:25-55`가 후속 최종 판정이다. 후속 증거는 backend 265/265, fresh HTTP 461/461, legacy 97/97, core 205(외부 PG 8 제외), FE build/i18n/browser 완료를 기록한다.
- 이 최종 판정은 2026-09-07 RootHR 전체 사이클 범위에 한정한다. `5240-evaluation-20260908/review.md:3,35-44`는 감사 조회 보완만 적용되었고, 신규 backend 7개 테스트 실행 성공·최신 compile/test·API·OpenAPI·browser 검증이 아직 없다고 명시한다. 따라서 이전 265/461 수치를 새 변경에 재사용할 수 없다.
- 현재 `main` HEAD는 `1d9d282` (`refactor(performance): use platform thread-local tenant context resolver`)이며 `git status`상 대량 수정·미추적 파일이 존재한다. 작업 트리의 증분 변경을 먼저 분류·검증해야 기준 커밋의 완료 증거와 혼동하지 않는다.

## 적용 표준

- `lib/easy-platform/easy-standards/README.md:101-117` §4 ADR-001~014 — 신규 식별자 UUIDv7, 서버 인가/시크릿 fail-fast, 페이지네이션·OIV=false, Flyway `ddl-auto=validate`, `easy-platform-core`/`@easy/ui`, React 19.2+·Mantine v9·React Query, Core Master/Read Model/S2S, Neon Model B를 적용한다.
- `00-principles/02-security-owasp.md:22,35,49,60-69,137` §2~§6·§10 — audit 조회는 서버 측 HR_ADMIN/SUPER_ADMIN 인가, 객체·테넌트 범위 검증, 민감 details 투영 금지, 오류·시크릿 비노출을 실제 API로 검증해야 한다.
- `00-principles/03-performance-oom.md:9-16,53-67` §1·§5~§6 / `10-appendix-spring-jpa/persistence.md:75-80,111-121` §3~§5 — audit 목록은 `Page`/최대 size 제한·DTO 투영·OIV=false·tenant 범위·정렬 인덱스를 지켜야 한다. 현재 설계의 page 기본 0/size 25·최대 100 및 `createdAt,id` 정렬은 방향상 부합하지만 실행 검증 전에는 통과 판정하지 않는다.
- `00-principles/04-observability.md:29-36,81-134,153-201` §2·§3.2~§3.3 — 상태 전이 감사 이력, traceId/ApiError, 5 locale 용어·번역 일관성을 확인한다. 기존 append-only audit의 운영 조회 연결은 이 표준과 연결되지만, 신규 API의 실제 오류 응답/traceId는 미검증이다.
- `00-principles/05-cicd-harness-gates.md:35-45,48-67` §3~§5 — 평가·관리자 기능은 권한·입력·로그·dependency·SCA/SBOM·smoke/rollback DoD를 모두 다시 통과해야 한다. 정적 11개와 i18n 7/7은 전체 DoD가 아니다.
- `00-principles/07-frontend.md:14-16,53-54,74-79,86-142` §1·§3·§5·§6 — `@easy/ui-components` 래퍼, React Query 서버 상태 SSOT, lazy/Suspense, strict i18n 5 locale, 서버 권한 검증을 적용한다. `ProgramAuditTimeline`은 React Query와 공통 UI를 사용해 방향상 정합하나 Windows 의존성 미해석으로 전체 tsc는 미통과다.
- `00-principles/09-database.md:71-74,84,101-118` §4~§9 — actor UUID·감사/보존·인덱스·Service read-only transaction·idempotency·PostgreSQL/Testcontainers를 적용한다. 이번 감사 조회는 migration 없이 기존 감사 원본을 투영하므로 schema drift는 없지만, JPA/실 DB 검증이 남아 있다.
- `00-principles/11-suite-architecture.md:75-103,131-195` §3~§7 / `10-appendix-spring-jpa/s2s-integration.md:7-45` §1~§6 — Core Master는 HCM SoR, PA는 Read Model/S2S 소비자여야 하며 제품 간 직접 DB 조인 금지·공유 UUID·상수시간 HMAC/키 정책을 따른다. 이번 증분은 PA 내부 audit 조회만 변경하고 HCM/MRA 원본을 복제하지 않은 점은 정합하다.
- `90-conformance/performance-evaluation-shared-assets-2026-09-07.md:12-40` — WorkflowPhaseRail/공유 UI·Storybook은 core 자산으로 유지하고, 평가 산식·정책·상태 전이는 제품에 둔다. Storybook production build와 compact browser 증거가 등록되어 있다.

## 위반 후보 및 위험

- **완료 상태 오판 위험 (P0, 증거 기반)** — `5240.../review.md:39-44`가 신규 테스트·compile/API/browser/OpenAPI 미완료라고 명시한다. 이를 “완료”로 승격하면 `05 §5` DoD 위반이다.
- **표준 SoT 경로 위험 (P0)** — 작업 루트의 `easy-standards` 심볼릭 링크는 Windows/현재 환경에서 읽을 수 없고, 실제 조회는 `easy-performance-management/lib/easy-platform/easy-standards` 복사본으로 수행했다. 최신 SoT와 submodule pin의 동기화 여부를 확인하기 전에는 표준 갱신/최종 통과를 확정하지 않는다.
- **작업 트리 경계 위험 (P0)** — `1d9d282` 기준으로 backend/frontend/lib 및 `_workspace`에 대규모 수정·미추적 파일이 함께 있다. 전체 사이클 증거가 현재 파일 집합과 동일한 commit/JAR인지 확인하기 전까지는 재현 가능한 baseline이 아니다.
- **공유 dependency 보안 게이트 (P1)** — `full-cycle-20260907/shared-npm-audit.json`은 shared UI에서 moderate 1/high 6, 총 7건을 기록한다. 제품 최종 audit 0 주장과 별개이므로 shared dependency의 fix/예외·만료일을 `05 §3-§5`에 따라 분류해야 한다.
- **증분 API 경계 미검증 (P1)** — `ProgramAuditController`/`ProgramAuditQueryService`는 operator·program 접근과 tenant+program 필터를 코드에 두었지만, 실제 200/403/cross-tenant 차단, Page envelope, 복합 필터·pagination, actor/participant null fallback은 아직 실행 증거가 없다.
- **제품 요구 갭 (별도 백로그)** — `5240.../page-decisions.md:11-33`의 기본 평가라인 자동생성, 역량 가중치 계층, KPI SoR/점수 연결, 상대평가 비율 fallback, 맞춤 PDF, 자동 평가라인, 책임자별 독려 UX는 확인 전 추정 구현하지 않은 보류 항목이다. 이는 audit 조회 증분의 즉시 결함이 아니라 계약/표준 갭으로 유지한다.

## Storybook 종료 오류 판정

- `full-cycle-20260907/shared-storybook-build.log:2-9,292-299`는 deprecation warning과 큰 chunk warning만 남기고 `Vite built` 및 `Storybook build completed successfully`로 끝난다. `performance-evaluation-shared-assets-2026-09-07.md:30-34`도 production build와 실제 compact browser 검증을 PASS로 기록한다.
- 검색된 `demo-restart.log`/`demo-restart-final.log`의 `server stopped`는 제품 local-demo 서버 종료 기록이며 Storybook 오류가 아니다. Storybook 프로세스가 build/검증 후 종료된 현상만으로는 기능 결함·렌더 오류·Storybook regression으로 판정할 근거가 없다.
- **결론: 단순 프로세스 수명 종료(정상 cleanup 또는 실행기 종료)로 분류.** 별도의 Storybook stderr/브라우저 pageerror가 새로 발견되지 않는 한 기능 결함으로 승격하지 않는다. 다만 재개 검증 시 `build` exit code, `index.json`, compact Chromium pageerror/overflow를 한 묶음으로 보존한다.

## 다음 구현·검증 게이트

1. WSL/Java 실행 환경을 복구하고 `./gradlew test --tests '*ProgramAuditQueryServiceTest'`를 실행한다. 실패 시 기능 결함과 환경 결함을 분리해 로그를 남긴다.
2. 실제 API에서 200(정상), 403(일반 사용자), 타 테넌트/타 프로그램 차단, eventType·participantId 필터, page/size 경계, `detailsJson` 미노출, Page 응답 shape를 확인한다.
3. 최신 증분 기준으로 FE `typecheck`, i18n/design/local-ui, production build, OpenAPI 재생성을 재실행한다. 기존 full-cycle 증거를 재사용하지 않는다.
4. 관리자 audit timeline의 필터·이전/다음·새로고침·5 locale·390px/1440px·브라우저 pageerror를 실제 조작해 증거화한다.
5. 그 다음에만 Phase 3c 품질/경계 QA 및 Phase 3d 큐레이션으로 진행한다. 외부 Neon/control plane, SMTP, push/deploy는 이번 게이트 범위 밖이며 별도 승인 없이는 실행하지 않는다.

## 표준 갭

- 평가 audit projection(원본 append-only audit에서 운영자용 최소 필드·페이지·정렬·details 비노출)을 공통 원칙에 명시한 문서가 없다. 제안: `90-conformance/performance-evaluation-audit-query-2026-09.md`에 PA 계약·보안/PII 투영 규칙을 박제하고, 필요 시 `04 §2`와 `11 §4`에 cross-link한다.
- Storybook “production build 성공”과 “dev server가 검증 후 종료됨”을 구별하는 공통 증거 형식이 없다. 제안: shared-assets conformance에 build exit code/index.json/browser pageerror/프로세스 종료 원인을 표준 증거 필드로 추가한다.
- 최상위 `~/code/easy-standards` 링크가 읽히지 않는 환경에서 submodule 복사본을 SoT로 사용할 수 있는 fallback/동기화 규칙이 없다. 제안: 하네스 Phase 0에 SoT path/commit/hash 확인 게이트를 추가한다.

## 확인 필요

- `easy-standards` 심볼릭 링크의 의도된 target과 현재 SoT commit/hash, `lib/easy-platform` submodule pin의 최신성.
- 현재 대량 working-tree 변경 중 어떤 파일이 2026-09-08 증분 범위인지, 보호해야 할 기존 사용자 변경인지.
- HCM 기본 평가라인 관계·유효기간·상충규칙 및 KPI 원본/계산 소유권.
- shared npm audit 7건의 현재 fix 가능 버전·예외 승인자·만료일.
- Storybook 종료를 유발한 원래 실행 명령/exit code가 별도 로그에 있는지.


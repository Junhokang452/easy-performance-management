# Current implementation checkpoint

## Authorization
User requests a usable evaluation system, explicitly permits a complete architecture/UI redesign, and requires both RootHR administrator and member guide functionality. Astra coordinates Sol backend, Terra frontend, Luna reference catalog. Local synthetic development only; no deployments, remote databases or unrelated product edits.

## Target and baseline
- Product: `/home/samsung/code/easy-performance-management`, clean starting HEAD `1d9d282`.
- Baseline Gradle test task reused cached outputs; XML reported 177 tests, no failures/errors/skips. This is not a fresh full test run of changes.
- Standards mapping and paired reference acceptance saved in this directory.

## Completed isolated checks
- Fresh local PostgreSQL 18.6 at port 55487, database `performance_demo` (temporary initial runtime at `/tmp/easy-performance-data-20260907`).
- Existing fresh-boot defect reproduced: missing `audit_event`; fixed via `V20260907_001` copied from pinned platform audit 001+002 and removed unused B2C platform_user entity/repository scanning. Local app then started in 13.4 sec with ddl-auto=validate.
- Browser cookie session facade: metadata-only response, HttpOnly cookies, same-origin mutation protection, frontend reload restore, query cache clear on actor change. Real API login→restore→refresh→logout passed.
- Real browser login→reload at localhost5174 passed, no JS token storage or browser errors. This was authentication evidence only; new workflow API was still being implemented.
- Auth targeted tests: 8 pass. Refresh token accepted as access was first reproduced by failing filter test, then fixed with explicit access-type validation. Test-only temporary Gradle init excluded concurrently unfinished workflow test sources; final whole-suite run is still required without exclusions.
- Reproducible local launch/profile + synthetic identity seed scripts added; launch script itself still needs fresh end-to-end verification.

## In progress
- Sol `backend_flow`: secure participant/reviewer workflow, goal agreement+actuals, intermediate review, self/manager scoring, calibration, feedback/appeal/report gates and coarse legacy endpoint guards.
- Sol `frontend_completion`: completes Terra scaffold, actual role forms/i18n and browser checks.
- Sol `auth_boundary_review`: auth fixes complete; independent Neon topology audit in progress.
- Parent: local runtime, seed/flow verification, final boundary review.

## Remaining acceptance
Fresh launch script start/stop/restart; full backend tests no exclusions; FE type/build/design checks; real HR→employee→manager→HR→employee flow; negative ownership/tenant/closed-state tests; desktop/mobile screenshots and final access instructions. No completion claim until these are checked.

## Subsequent review corrections
- Terra's initial FE typecheck result was not a valid source check: `tsc --noEmit` ran against root `files: []`/project references. Parent actual build exposed missing navigation symbol, enum-index and duplicate i18n-key errors. Sol `frontend_completion` now owns completing the actual UI and fixing the validation script; do not rely on the earlier FE readiness report.
- Independent Sol auth review found Model B refresh/routing fail-closed defects inherited from existing auth paths. Sol `auth_boundary_review` now fixes those in auth backend with dedicated tests; parent fixes demo cleanup/frontend readiness, cookie logout local state and Axios singleton mutation header.

## Verified 2026-09-07 14:49–14:52 KST
- Reproducible launcher fresh PostgreSQL → Flyway → Hibernate schema validate → seeded accounts → Vite succeeded. Application boot 47.907 sec.
- Fresh boot exposed audit repository duplicate registration (fixed by relying on lib AuditEventAutoConfiguration) and new reviewer round SMALLINT/Integer mismatch (new unpublished V002 corrected to INTEGER; only empty task-owned local DB recreated).
- Initial actual API full lifecycle **75/75 pass**, including goal reject/edit/resubmit, approval, numeric check-in, intermediate, self/manager reviews, score visibility, calibration, publication, appeal resolution, acknowledgement, close and wrong-employee/tenant-ID negatives. This is local single-DB access-guard validation, not proof of Neon physical routing.
- Actual browser expired access cookie → silent refresh200 with mutation header → restored session → logout/reload passed. Product cookies HttpOnly; no access/refresh tokens in localStorage. Evidence `browser-session-result.json`.
- Auth agent targeted security suite31/31 pass in isolated temporary build; final whole-suite still pending.
- Actual HR screenshot rendered finalized results. Root review found missing creation navigation, closed roster edit controls, duplicated no-task message, missing org labels and empty org aggregation; agents correcting.
- Critical browser integration gap: raw cycle API was operator-only while member workspace queried it. Dedicated actor-scoped cycle discovery/read facade and FE switch are being added. API lifecycle test now includes discovery/metadata negative checks; initial75result predates these additions.

## User topology clarification
User explicitly reaffirmed Neon customer/product hierarchy. Confirmed against `easy-standards/00-principles/13-tenancy-provisioning.md`: **one customer = one Neon project; purchased products = separate databases within that project; one shared tenant UUID across products**. Shared control DB stores registry/subscription/encrypted connection info, product tables belong to the customer's performance DB. Local launch is verification only. No actual external Neon/control-plane write has occurred.

## Final integration checkpoint — 2026-09-07 15:16 KST
- Latest jar rebuilt and local demo stop/start/restart verified; data preserved and all synthetic identity seeds idempotent.
- Actual API lifecycle **94/94 pass** (expanded beyond initial75). Final evidence has timestamp and explicitly distinguishes local tenant-ID guards from untested Neon physical routing.
- Whole backend **212/212 pass**, 29 XML suites, no failures/errors/skips/exclusions; root independently counted.
- Frontend actual tsc build, 5 workspace tests, design audit0/0, local UI audit0/0 pass. OpenAPI generated from the running final backend.
- Neon readiness: expected schema20260907.002, opt-in ACTIVE drift with UUID canary allow-list, owner preflight before registry writes, internal admin bootstrap SUPER_ADMIN guard. All external gates remain OFF by default.
- Commonization: product UUID duplicate removed in favor of shared core; actual reuse audit documents JWT/HMAC/error/audit/tenancy/SPI seams and compatibility adapters.
- User requested Easy Design Standard assets and ware/hcm design family. Shared SuiteShell chrome/lookup controls/performance cards now consumed; WorkflowPhaseRail registered in shared component source, CSS, Storybook and asset metadata. Canonical localcommit90439f4; selective product pin7c814bc. Shared type checks (canonical and pinned), Storybook build and mobile asset browser checks pass. Canonical preexisting .gitignore/deploy changes preserved; no remote push.
- Browser HR create/roster/open → employee goal submit → manager approve and desktop/mobile390px verified. Remaining: finish extended UI scoring/feedback flow and final report/screenshots.


## 다국어 표준 보완 진행

사용자 지적에 따라 기존 2locale 기본 설정을 폐기하고 ko/en/ja/zh-CN/vi 전체 지원을 구현한다. ko.ts의 수동 I18nShape 및 workspace.copy의 Record<string,string> 허용을 제거하여 typeof ko가 유일한 키 스키마가 되었다. 현재 총 928키. 공통 동작·상태 등 14문구는 @easy/i18n-common을 직접 참조한다. 일본어/베트남어 928키 및 placeholder 정합 확인, 중국어 최종 교정·브라우저 5언어 검증 중. 로그인과 모바일에서도 언어 선택 가능, 날짜 locale 및 서버 blocker code 번역 연결.

역할별 실제 UI 전체 흐름은 UI 전체흐름 130182에서 완료했다. 최종 모바일 screenshots/workspace-full-mobile.png를 루트가 직접 확인하여 표 가독성·가로 넘침 개선 확인. 새 언어 메뉴 반영 이후 최종 캡처는 후속 i18n 브라우저 검증에서 보존한다.


## 최종 검증 완료 (2026-09-07)

- 표준 5locale 각각928키, 공통14문구 @easy/i18n-common 연결, 키/변수/언어/오류검증7/7, 실제브라우저15/15.
- 진행률metadata 영속화 V20260907_003 기존DB증분실적용, 부팅22.82초. 전체BE214/214(29suites; fail/error/skip0), 실API97/97.
- FE타입/빌드PASS, 기존화면테스트5/5, 디자인/로컬UI위반0, 실제OpenAPI최종재생성후타입검사PASS.
- 로컬주소 http://localhost:5174, launcher strictPort 적용하여 중복서버자동포트변경방지.
- 기존RootHR관리자/구성원업무기능, 공통디자인/백엔드lib점검, 5locale보완완료. 실제Neon프로젝트생성·controlplane등록·배포·push 없음.
- 별도 suite framework 보안전환 조율 작업이 Boot4.1.1 target을 전달했다. 제품기준선/RED준비만 suite-security-baseline-20260907/performance-pre-framework-baseline.md에기록. 현재BOM변경없음. 검증된portablecorepatch 수신후 이어갈수있도록조율작업에전달.

## 2026-09-07 framework security continuation

Authorized suite migration applied: Boot4.1.1/Tomcat11.0.25/Gradle8.14.5/core1.0-SNAPSHOT; local lib b1654e9. Product219/core193(+externalPG8 excluded), API97, i18n browser15; sameDB SCA78→0, no suppressions. See ../framework-20260907/99_final_report.md. No push/deploy/external DB.

# easy-performance-management 단계 4 EC-FE 진입 박제 (Task #113, 2026-06-08)

> **사용자 결정**: G71 D=A 풀 통과. Vite + React 19.2 + Mantine v9 + TypeScript strict + lib FE 12 풀 활용.
> **모범 정합**: easy-job-evaluation 단계 4 cutover `cc1bc03` (Task #99 + #587).
> **본질 정합**: ADR-031 정정 후 B2B-Enterprise per-tenant + SMB Shared 옵션 (B2C 부재). `application.yml` `performance.mode = b2b-enterprise|smb-shared` 분기.
> **누적 정합**: 단계 0 `58bf09d` + 단계 1 `b83acac` + 단계 2 `6895ba9` + 단계 5 SMB `27108e3`.

## 1. 풀 스캐폴드 산출물 (frontend-vite/)

### 1.1 빌드 토폴로지

- `package.json` — React 19.2.6 + Mantine v9.2.1 + Vite 8.0.12 + TypeScript 5.7.2 + TanStack Query 5.x + react-router-dom 7.15.1
- `tsconfig.json` + `tsconfig.app.json` + `tsconfig.node.json` (strict + Bundler module resolution + verbatimModuleSyntax)
- `vite.config.ts` — React plugin + dev port 5174 + `/api` proxy → http://localhost:8087 (BE `PORT:8087`)
- `index.html` — root #root + `/src/main.tsx`
- `.gitignore` — node_modules / dist / *.tsbuildinfo / .vite / .env

### 1.2 lib FE 12 file: 의존성 4종

| 패키지 | 경로 | 역할 |
|--------|------|------|
| `@easy/http-client` | `file:../../easy-platform-core/packages/http-client` | createHttpClient + 인터셉터 (X-Tenant-Id + CSRF + silent refresh + 401/403 dispatch) |
| `@easy/query-client` | `file:../../easy-platform-core/packages/query-client` | createEasyQueryClient + buildQueryKey + useTenantInvalidator |
| `@easy/tokens` | `file:../../easy-platform-core/packages/tokens` | createEasyTheme (Mantine v9 토큰 SoT) |
| `@easy/ui-components` | `file:../../easy-platform-core/packages/ui-components` | PageHeader + SectionCard + EmptyState + LoadingState + ErrorBoundary |

> **B2C dual mode 컴포넌트 미사용** (PersonalProfileCard / QuotaGauge / PlanBadge) — ADR-031 정정 후 B2C 부재.

### 1.3 src/ 구조 (총 22 파일)

```
src/
├── main.tsx                                  # Provider 계층 진입점 (StrictMode + MantineProvider + Notifications + QueryClient + Router + I18n + Auth)
├── App.tsx                                   # AppShell + NavLink 4 + Routes 5 + lazy + PageBoundary + ProtectedRoute
├── theme/
│   └── mantine-theme.ts                     # createEasyTheme() 직접 채택 (override 0)
├── i18n/
│   ├── index.tsx                            # I18nProvider + useT() + localStorage('easyperformance.locale')
│   ├── ko.ts                                # 한국어 namespace 5 계층 (common + domain.app/nav/4도메인 + error)
│   └── en.ts                                # English (동일 shape)
├── shared/
│   ├── RouteErrorBoundary.tsx               # STD-FE-ERROR-BOUNDARY (라우트 throw 차단 + key reset)
│   ├── PageBoundary.tsx                     # RouteErrorBoundary + Suspense + key={pathname}
│   └── AppHeaderActions.tsx                 # 다크모드 토글 + ko/en 토글 (useMantineColorScheme + useI18n)
├── auth/
│   ├── types.ts                             # TokenResponse + LoginRequest + AuthSession
│   ├── AuthProvider.tsx                     # 단계 3 미진입 stub (BE 호출 실패 시 dev stub fallback) + apiClient 헤더 자동 적용 + 401 unauthorized 이벤트 수신
│   └── ProtectedRoute.tsx                   # 미인증 시 /login redirect
├── api/
│   ├── client.ts                            # apiClient = createHttpClient({ eventPrefix: 'easyperformance', getTenantId, refreshEndpoint })
│   ├── error.ts                             # ApiError SoT + isPerformanceError (E97 prefix) + getErrorMessage + getErrorStatus
│   ├── selfEvaluation.ts                    # DTO + apis + QK + 4 hooks
│   ├── personalOkr.ts                       # DTO + apis + QK + 4 hooks
│   ├── reflectionJournal.ts                 # DTO + apis + QK + 4 hooks
│   └── mentorFeedback.ts                    # DTO + apis + QK + 4 hooks
└── pages/
    ├── LoginPage.tsx                        # dev stub login (BE 단계 3 진입 시 silent refresh 정합)
    ├── SelfEvaluationPage.tsx               # 자기평가 (DRAFT → SUBMITTED → REVIEWED → FINALIZED 상태 머신)
    ├── PersonalOkrPage.tsx                  # 개인 OKR (ACTIVE / AT_RISK / COMPLETED / ARCHIVED + Progress bar)
    ├── ReflectionJournalPage.tsx            # 회고 저널 (KPT / 4Ls / SSC + isPrivate 배지)
    └── MentorFeedbackPage.tsx               # 멘토 피드백 (GROWTH / RECOGNITION / COACHING / CONVERSATION + acknowledged 배지)
```

### 1.4 BE prefix 정합

- BE controllers (단계 1 cutover `b83acac`): `/api/internal/self-evaluations|personal-okrs|reflection-journals|mentor-feedbacks`
- list 응답 shape: Spring Data `Page<T>` envelope (`{ content, totalElements, totalPages, number, size }`)
- 단계 3 BE-CC-2 JWT 진입 시 `/api/v1/` prefix 전환 + 인증 헤더 정합

## 2. STD-FE 5 정합 검증

| STD | 적용 | 진입점 |
|-----|------|--------|
| STD-FE-LAZY | ✅ | App.tsx 의 5 페이지 모두 `React.lazy(() => import(...))` |
| STD-FE-STRICT | ✅ | main.tsx `<StrictMode>` |
| STD-FE-RQ | ✅ | 4 도메인 페이지 모두 `useQuery` + `useMutation` 만, useState fetch 0 |
| STD-FE-NEST | ✅ | `NavLink in NavLink` 없음, `Accordion.Control + Menu.Target` 등 인터랙티브 중첩 없음 |
| STD-FE-ERROR-BOUNDARY | ✅ | RouteErrorBoundary class + key={location.pathname} 리셋 + PageBoundary wrap |

## 3. jobeval `cc1bc03` 패턴 정합

| 영역 | jobeval `cc1bc03` | performance 단계 4 |
|------|-------------------|-------------------|
| Provider 계층 | StrictMode → MantineProvider → Notifications → QueryClient → Router → I18n → Auth | ✅ 동일 |
| createEasyQueryClient eventPrefix | `easyjobeval` | `easyperformance` |
| createHttpClient eventPrefix | `easyjobeval` | `easyperformance` |
| AuthProvider localStorage key | `easyjobeval.refreshToken` | `easyperformance.refreshToken` |
| I18n localStorage key | `easyjobeval.locale` | `easyperformance.locale` |
| RouteErrorBoundary | ✅ class + key reset | ✅ 동일 |
| PageBoundary | ✅ Suspense + RouteErrorBoundary + key={pathname} | ✅ 동일 |
| AppHeaderActions | 다크모드 + ko/en SegmentedControl | ✅ 동일 |
| ErrorCode prefix | E93 (jobeval) | E97 (performance) |
| 페이지 갯수 | 7 lazy (B2B 1 + B2C 6) + LoginPage | 4 lazy (B2B 4) + LoginPage |
| B2C 분리 라우팅 | ✅ /b2c/* 라우트 + B2C dual mode | ❌ 미적용 (B2C 부재, ADR-031) |

## 4. ADR-031 정정 정합 (B2C 부재)

- B2C 분리 라우트 (`/b2c/*`) 미적용
- B2C dual mode 컴포넌트 (PersonalProfileCard / QuotaGauge / PlanBadge) 미사용
- ADR-029 (sign B2C 공통 테넌트 예외) 적용 대상 아님
- 단계 5 SMB Shared 옵션 (`application-smb.yml` profile + `db/smb/V20260608_001__rls_policy_smb.sql`) 은 BE 측면 진입 — FE 측면 영향 0 (단일 코드 베이스에서 BE profile 분기로 처리)

## 5. 진입 비용

- **추정**: 2주 (jobeval 4단계 cc1bc03 패턴 정합 + B2C 미적용으로 ~30% 진입 비용 절감)
- **실제**: 본 슬라이스 단일 commit 진입 — jobeval 패턴 풀 활용으로 ~1.5일 추정 → 1일 미만 진입 ✅

## 6. 단계 5 SMB Shared 옵션 FE 영향 분석

- BE `application-smb.yml` profile 진입 시 동일 FE 코드 베이스 사용
- BE 응답 shape 동일 (Page envelope + DTO)
- SMB Shared 환경에서 사용자 multi-tenant 진입 시 `X-Tenant-Id` 헤더 분기 — `setActiveTenantId` 호출 사이트만 추가 갱신 필요 (현재 AuthProvider 단일 tenant 채택)

## 7. 후속 작업

| 단계 | 작업 | 비고 |
|------|------|------|
| 단계 3 BE-CC-2 JWT 진입 후 | AuthProvider dev stub 제거 + 실 silent refresh 정합 + `/api/v1/` prefix 전환 | jobeval `4dff03a` 패턴 정합 |
| openapi-typescript | `npm run openapi:types` (BE `/v3/api-docs` 엔드포인트 진입 후 자동 schema 생성) | 단계 3 진입 후 |
| ApiError + ErrorCode 정합 강화 | BE `PerformanceErrorCode.java` (E97* prefix) 진입 후 isPerformanceError 정합 강화 | 단계 3 진입 후 |
| lib FE 13 i18n-common 진입 후 | ko/en bundle merge + namespace 5 계층 통합 + 5 locale 확장 | jobeval 후속 lib publish 정합 |
| 단계 5 SMB FE 진입 가드 | multi-tenant 사용자 switcher 진입 (X-Tenant-Id 분기) | SMB shared 실 진입 시 |

## 8. 빌드 검증

- `npm install` ✅
- `npx tsc --noEmit` (typecheck) ✅
- `npm run build` (tsc -b && vite build) ✅

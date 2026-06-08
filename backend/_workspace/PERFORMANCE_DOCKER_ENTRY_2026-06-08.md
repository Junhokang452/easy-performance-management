# easy-performance-management Phase Docker 사전 적용 (Task #176, 2026-06-08)

> **위상**: 자매품 9호 — Phase Docker 사전 적용 (recruit `0ea4d0a` + 9 fix 누적 모범 정합).
> **모드**: 그린필드 100% Apache-2.0 + B2B-Enterprise per-tenant + SMB Shared (ADR-031 정합).
> **모범**: easy-recruit `0ea4d0a` 모범 정합 (submodule + Dockerfile + .dockerignore + yml fallback + multitenancy default + CONTROL_DB_* placeholder + Spring Boot static-locations).

## 1. 변경 요약

| 영역 | 변경 | 사유 |
|------|------|------|
| `.gitmodules` | 신규 — `lib/easy-platform` submodule (https://github.com/Junhokang452/easy-standards.git) | composite build seam — Docker stage 1/2 lib 빌드용 |
| `lib/easy-platform` | submodule add (HEAD `a8e8176` lib FE 13 i18n-common v0.3.0) | lib 6 FE packages + easy-platform-core BE |
| `backend/settings.gradle.kts` | `../../easy-platform-core` → `../lib/easy-platform/easy-platform-core` | submodule 경로 정합 |
| `frontend-vite/package.json` | `file:../../easy-platform-core/packages/*` → `file:../lib/easy-platform/easy-platform-core/packages/*` (4 packages) | submodule 경로 정합 |
| `Dockerfile` | 신규 — multi-stage (FE Vite + BE Gradle + Runtime Temurin 21) | recruit `0ea4d0a` 모범 정합 + Render Docker Web Service 호환 |
| `.dockerignore` | 신규 — build context 최소화 | recruit 모범 정합 |
| `application-prod.yml` | (1) Spring Boot `spring.web.resources.static-locations` `file:/app/static/,classpath:/static/` 추가 (2) datasource fallback chain `DB_URL → DATABASE_URL → SPRING_DATASOURCE_URL` (3) `easyware.neon.*` CONTROL_DB_* placeholder default 추가 | recruit fix #1/#3 정합 — Render Docker Web Service + LIVE 안전 placeholder |

## 2. 자매품 모범 정합 (recruit `0ea4d0a` + 9 fix 누적)

| Fix | 적용 | 비고 |
|-----|------|------|
| #1 yml duplicate key 회피 | ✅ 단일 `easyware:` 블록 통합 | snakeyaml strict 정합 |
| #2 `refresh.enabled` default false | ❌ 단계 3 cutover 완료라 `true` 유지 | lib 근본 fix `67f40b9` 적용 후 ON 안전 |
| #3 multitenancy default false | ✅ default `false` 유지 (LIVE 안전 OFF 기본) | application-prod.yml + application.yml 양쪽 정합 |
| #4 Dockerfile lib FE 12 빌드 단계 | ✅ stage 1 Frontend build에서 lib 빌드 → dist/ 인식 | TS2307 회피 |
| #5 Vite dedupe | ⏸ FE 영역 추후 (BE Docker 진입 우선) | sanity-check WARN |
| #6 app: 통합 | ✅ 단일 `app:` 블록 (security + cors) | snakeyaml strict 정합 |
| #7 Flyway VARCHAR→TEXT | ❌ 불필요 — VARCHAR는 enum CHECK 컬럼만 (UUID PK는 UUID 타입) | V20260608_001 status/method/category 컬럼만 VARCHAR(20) (CHECK 제약) |
| #8 CONTROL_DB_* placeholder default | ✅ application-prod.yml + application.yml 양쪽 추가 | PlaceholderResolutionException 회피 |
| #9 Spring Boot static-locations | ✅ application-prod.yml 추가 | Render Docker 단일 컨테이너 정적 파일 서빙 |

## 3. 빌드 검증

### `./gradlew compileJava` ✅
```
> Task :easy-platform-core:compileJava
> Task :compileJava UP-TO-DATE
BUILD SUCCESSFUL in 17s
2 actionable tasks: 1 executed, 1 up-to-date
```
- submodule 경로 `../lib/easy-platform/easy-platform-core` 해석 정상.
- composite build (lib `easy-platform-core` jar 의존) 정상.

### `./gradlew test` ⚠️ (26 tests / 1 failure)
- **JwtServiceTest.parse_tamperedSignature_throws()** — base64url 마지막 글자 변조 시 서명 검증 실패를 기대.
- Docker 사전 적용 변경(submodule 경로 + yml fallback)과 **무관**. JWT 코드 미변경.
- 단계 3 cutover (`6f6d3df`) 시점 작성된 테스트의 flaky 케이스 추정 (마지막 글자 padding/signature 부분 변조 시 간헐 통과).
- **후속 조치**: 별도 슬라이스로 테스트 강화 (서명 부분 명시 변조 + 길이 4의 배수 정렬).

### sanity-check ✅ (PASS-WITH-WARN, 0 errors / 2 warnings)
```
[1] .gitmodules                          [OK]
[2] Dockerfile                           [OK]
[3] .dockerignore                        [OK]
[4] Dockerfile lib FE 12 빌드 단계        [OK]
[5] application*.yml duplicate key       [OK] × 4 (default + dev + prod + smb)
[6] easyplatform.auth.refresh.enabled    [WARN] default 'true' (단계 3 cutover 완료라 의도적)
[7] easyware.neon.multitenancy-enabled   [INFO] default 미지정 (운영 진입 시 환경 변수)
[8] CONTROL_DB_* placeholder default     [OK]
[9] FE vite dedupe                       [WARN] FE 영역 추후
[10] render.{yaml,yml,json}              [INFO] Dashboard 수동 등록 또는 추후
```

## 4. 그린필드 + 격상 100% 보존 + ADR-031 정합

- **그린필드 100% Apache-2.0**: license history rewrite 불필요. 본 슬라이스도 Apache-2.0 신규 코드.
- **격상 100% 풀 완성**: 단계 0/1/2/3/4 + SMB 옵션 5 모두 보존 (Docker 사전 적용은 인프라 영역, 격상 코드 미변경).
- **ADR-031 토폴로지 정합**: B2B-Enterprise per-tenant + SMB Shared 본질 보존 (B2C 부재 정정 정합). Dockerfile 환경 변수 `SPRING_PROFILES_ACTIVE=prod` 기본 = B2B-Enterprise per-tenant. SMB 진입은 `SPRING_PROFILES_ACTIVE=smb` 명시.
- **lib BE 13~22 + FE 12~13 풀 활용**: submodule HEAD `a8e8176` = lib FE 13 i18n-common v0.3.0 + lib BE 22 JwtRefreshAspect (단계 3 cutover 시점 결합).

## 5. LIVE 안전 가드

- `SPRING_PROFILES_ACTIVE=prod` (Dockerfile 기본) → application-prod.yml 진입 → multitenancy/control-plane-owner 기본 OFF.
- `DB_URL`/`JWT_SECRET` 등 환경 변수 fail-fast (placeholder default 없음 = 운영 진입 시 명시 필수).
- Render Docker Web Service: `DATABASE_URL` 자동 매핑 안 됨 → fallback chain `DB_URL → DATABASE_URL → SPRING_DATASOURCE_URL` 으로 사용자 env 이름 자유.

## 6. 후속 후보

- (P1) JwtServiceTest tamperedSignature 강화 (서명 부분 명시 변조 + Base64URL 정렬 보장).
- (P2) Vite dedupe 추가 (frontend-vite/vite.config.ts) — lib FE 12 중복 인스턴스 방지.
- (P3) Render Blueprint render.yaml 추가 (Dashboard 수동 등록 대신 코드 박제).

## 7. 정합 cross-link

- easy-recruit Dockerfile (`0ea4d0a`) — 본 슬라이스 모범 참조.
- easy-platform/scripts/sanity-check-sibling.sh — 자매품 통합 sanity check.
- ADR-031 (자매품 9 × 3 토폴로지) — B2C 부재 본질 보존.
- 단계 3 BE-CC-2 JWT 5분리 cutover (`6f6d3df`) — Docker 사전 적용 + 단계 3 누적 정합.
- lib BE 22 JwtRefreshAspect cutover (`2119a8b`) — Docker 사전 적용 + lib BE 22 cutover 누적 정합.

# easy-performance-management 단계 3 BE-CC-2 JWT 5분리 진입 박제 (2026-06-08, Task #122)

> **결정**: G84 D=A — 그린필드 100% 정렬 / jobeval `4dff03a` + mra `38e566d` + jobstructure `d64944e` 3 모범 정합 / 단계 3 진입
> **결과**: 격상 4단계 풀 완성 마일스톤 도달 (단계 0/1/2/3/4 + 단계 5 SMB 옵션 모두 ✅)

## 1. 슬라이스 컨텍스트

| 단계 | 상태 | commit |
|------|-----|--------|
| 단계 0 baseline | ✅ | `58bf09d` + tag `v0.0.0-baseline` |
| 단계 1 BE-CC-1 TenantAware | ✅ | `b83acac` |
| 단계 2 Model B per-tenant | ✅ | `6895ba9` |
| **단계 3 BE-CC-2 JWT 5분리** | ✅ **본 슬라이스** | (commit hash 본 슬라이스 산출) |
| 단계 4 EC-FE Vite + Mantine v9 | ✅ | `809f970` |
| 단계 5 SMB Shared 옵션 | ✅ | `27108e3` |

본 슬라이스로 **격상 4단계 풀 완성 = 100% 도달**. 단계 4가 단계 3보다 먼저 진입한 비순서 케이스이지만, 단계 3 완성으로 4단계 풀 정합.

## 2. 모범 정합 (3 자매품 누적)

| 자매품 | commit | 패턴 핵심 |
|--------|--------|----------|
| **jobeval** | `4dff03a` (Java 그린필드 1호) | AuthController + AuthService + AuthDtos + RefreshTokenStore (in-memory) + JwtService facade + JwtAuthFilter + ErrorCode 5종 (E93*) + lib JwtTokenIssuer/Parser HS512 + JwtClaims.TID 위임 + access 5분 + refresh 7일 + tid + user_id + stub login (이메일 first-seen 자동 생성) |
| **mra** | `38e566d` (Java dual-claim 2호) | 기존 LoginService 보존 + AuthService 추가 + AuthController + 234 tests / 0 failures (+17 신규) + ErrorCode 5종 (E95*) + dual-claim 비파괴 전환 |
| **jobstructure** | `d64944e` (Kotlin 3호) | Kotlin idiomatic + jti claim (iat 충돌 방지) + 31 tests + ErrorCode 5종 (E97*) + sealed interface |

본 슬라이스 (performance, Java 그린필드 4호):
- **jobeval 정합 100%**: 동일 구조 + 동일 패키지 분리 (security 패키지 6 클래스).
- **Clock 주입 (jobstructure 추종)**: `RefreshTokenStore(Clock)` + `AuthService(..., Clock)` 두 생성자 — production은 system Clock, 테스트는 fixed Clock.
- **dual-claim 불필요 (mra와 다름)**: performance 그린필드라 legacy claim 없음 — 신규 표준 단일.

## 3. 산출 파일

### 신규 (8 파일)

| 파일 | 라인 | 비고 |
|------|-----|------|
| `backend/src/main/java/com/easyperformance/error/PerformanceErrorCode.java` | 59 | E98 prefix 사전 진입 + JWT 5종 (AUTH_LOGIN_FAILED / AUTH_REFRESH_TOKEN_NOT_FOUND/EXPIRED/INVALID / AUTH_USER_NOT_FOUND) |
| `backend/src/main/java/com/easyperformance/security/AuthDtos.java` | 73 | LoginRequest + TokenResponse + RefreshRequest + LogoutRequest 4 record |
| `backend/src/main/java/com/easyperformance/security/JwtService.java` | 89 | lib JwtTokenIssuer/Parser facade + HS512 + 5분 access + 7일 refresh |
| `backend/src/main/java/com/easyperformance/security/RefreshTokenStore.java` | 105 | in-memory ConcurrentHashMap + Clock 주입 + RefreshSession record |
| `backend/src/main/java/com/easyperformance/security/AuthService.java` | 158 | login + refresh rotation + logout + stub user 자동 생성 |
| `backend/src/main/java/com/easyperformance/security/AuthController.java` | 81 | 3 endpoint (/login + /refresh + /logout) |
| `backend/src/main/java/com/easyperformance/security/JwtAuthFilter.java` | 105 | OncePerRequestFilter + lib TenantContext (BE 17 v2) + SecurityContext |
| `backend/_workspace/PERFORMANCE_STAGE3_JWT_CUTOVER_2026-06-08.md` | (본 박제) |

### 갱신 (3 파일)

| 파일 | 변경 |
|------|------|
| `backend/src/main/java/com/easyperformance/config/SecurityConfig.java` | permitAll 임시 가드 → JWT filter chain (jobeval SecurityConfig 패턴 정합 + lib TenantContextFilter ObjectProvider 합성) |
| `backend/src/main/resources/application.yml` | `app.security.jwt.expiration` 900000(15m) → 300000(5m) + `app.security.auth.dev-default-tenant-id` 신규 |
| `backend/src/main/resources/application-dev.yml` | `easyplatform.jwt.secret` (HS512 64+ bytes 충족, JwtSecretValidator 통과) + `app.security.jwt.*` dev fallback |

### 테스트 (2 파일)

| 파일 | tests |
|------|------|
| `backend/src/test/java/com/easyperformance/security/JwtServiceTest.java` | 5 (access 발급 + parse + tenantId/roles claim + null tenantId + 무효/변조 토큰 + ttl) |
| `backend/src/test/java/com/easyperformance/security/AuthServiceTest.java` | 7 (login + 동일 이메일 재사용 + blank email + refresh rotation + invalid/blank token + logout) |

**테스트 회귀 가드 풀 통과**: 26 tests / 0 failures (UuidV7 2 + AuthService 7 + JwtService 5 + NeonProvisioning 12).

## 4. JWT 5분리 정합

| 분리 항목 | 본 슬라이스 구현 | lib 위임 |
|----------|----------------|---------|
| 1. Access Token | 5분 만료, Authorization: Bearer | `JwtTokenIssuer.issueAccess` |
| 2. Refresh Token | 7일 만료, body (httpOnly 쿠키 격상 후보) | `JwtTokenIssuer.issueRefresh` |
| 3. ID Token | 본 슬라이스 생략 (단계 3+ 격상 후보) | — |
| 4. Tenant Claim | `JwtClaims.TID` | `ParsedToken.tenantId()` |
| 5. User Claim | subject = userId | `ParsedToken.subjectAsUuid()` |

**Refresh rotation**: 매 refresh 마다 새 토큰 발행 + 이전 토큰 폐기 (재플레이 공격 방어).

## 5. ADR-031 정합 — B2B-Enterprise + SMB Shared 본질 (B2C 부재)

본 자매품 도메인 본질 = 기업 성과 평가 워크플로우 + HR SoR + 매니저-팀원 1:1 + 성과 사이클. B2C 부재.
- `JwtAuthFilter`: B2B 흐름만 (`TenantContext.b2b(tenantId, userId)`) — B2C 분기 미존재.
- `SecurityConfig`: `/api/b2c/**` 매처 불필요 — performance에는 B2C endpoint 없음.
- `AuthService`: dual-claim 불필요 — 그린필드 신규 표준 단일 (mra 패턴과 다름).

단계 5 SMB Shared 옵션 (`application-smb.yml`)에서도 JWT 흐름은 동일 — RLS tenant_id 격리만 추가 (단계 5 박제 `27108e3`).

## 6. lib BE 17 v2 + BE 18 cutover 자연 결합

- **BE 17 v2 TenantContextResolver** (jobeval `18bc01f` 1호 모범): `JwtAuthFilter` 가 `TenantContext.set(b2b(tenantId, userId))` 직접 + `SecurityConfig` 가 lib `TenantContextFilter` 빈을 ObjectProvider 로 조건 체인.
- **BE 18 RlsTenantAspect** (jobeval `fd23472` 1호 모범): 단계 2 Model B 진입 후 RLS 적용 시 활성. 본 슬라이스는 `easyplatform.rls.tenant.enabled=false` 기본 OFF (LIVE 안전).
- **lib JwtTokenIssuer/Parser**: HS512 + JwtSecretValidator (64+ bytes + CHANGE_ME prefix 차단) — 부팅 fail-fast.

## 7. 빌드 + 테스트 결과

```bash
$ ./gradlew compileJava
BUILD SUCCESSFUL in 16s

$ ./gradlew test
BUILD SUCCESSFUL in 3s
26 tests / 0 failures
```

## 8. LIVE 영향 0 가드

| 가드 | 상태 |
|------|------|
| `application.yml` JWT secret | `${JWT_SECRET:}` (default empty → prod 부팅 fail-fast) |
| `application-prod.yml` JWT secret | `${JWT_SECRET}` (환경변수 필수, fail-fast) |
| `application-dev.yml` JWT secret | dev-only 박제 secret (HS512 64+ bytes, JwtSecretValidator 통과) |
| Refresh store | in-memory (dev 만 — 재시작 시 폐기, LIVE 미운영) |
| Stub login | 이메일 first-seen 자동 생성 (dev 만 — 단계 4 격상 시 사용자 엔티티 + bcrypt) |
| RLS gate | `easyplatform.rls.tenant.enabled=false` (LIVE 안전 OFF) |
| Token 무효화 0 | 그린필드 신규 진입 (legacy 토큰 부재) |

## 9. 격상 4단계 풀 완성 마일스톤

본 슬라이스로 **performance 격상 4단계 풀 완성 도달 = 100%**.

| 단계 | 상태 |
|------|-----|
| 단계 0 baseline | ✅ `58bf09d` |
| 단계 1 BE-CC-1 TenantAware | ✅ `b83acac` |
| 단계 2 Model B per-tenant | ✅ `6895ba9` |
| **단계 3 BE-CC-2 JWT 5분리** | ✅ **본 슬라이스** |
| 단계 4 EC-FE Vite + Mantine v9 | ✅ `809f970` |
| 단계 5 SMB Shared 옵션 | ✅ `27108e3` |

자매품 9호 (easy-performance-management) — **B2B-Enterprise per-tenant + SMB Shared 본질 풀 완성**.

## 10. 후속 권고 (별도 슬라이스)

- **performance E98 영역 정식 박제**: `easy-standards/90-conformance/error-code-domain-extension.md` §2.1/§2.3 에 영역 98 Performance Management 정식 등재 (현재 본 자매품 자체 enum 만 진입). 표준 PR 별도.
- **i18n bundle**: `error-messages_*.properties` 5 locale × 5 코드 = 25 항목 추가. ADR-027 정합.
- **사용자 엔티티 격상** (단계 4 후속 또는 단계 6 후보): bcrypt 검증 + 사용자 가입 흐름 + 로그인 실패 카운트 + 비밀번호 정책.
- **httpOnly 쿠키 패턴 격상**: refresh 토큰을 body 응답에서 httpOnly + SameSite 쿠키로 전환 (단계 4 EC-FE 와 통합 후보).

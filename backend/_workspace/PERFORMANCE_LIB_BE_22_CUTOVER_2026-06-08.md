# performance lib BE 22 JwtRefreshAspect cutover (그린필드 모범 2호)

> **Task #159 / G127.6 D=A / 2026-06-08**
>
> 자매품 9호 (performance) — lib BE 22 cutover **6호** (jobeval `2f24a9b` 1호 / 그린필드 모범 **2호** 누적).

## 1. 본질

performance 단계 3 BE-CC-2 JWT 5분리 진입 (`6f6d3df`, G84) 직후 lib BE 22
JwtRefreshAspect cutover. **옵션 A** (그린필드, breaking change 0):

- performance 자체 `RefreshTokenStore.java` **삭제** → lib `InMemoryRefreshTokenStore` 자동 등록
- performance `AuthService.java` 의 자체 refresh rotation 흐름을 lib
  `JwtRefreshService.{issueRefresh, rotate, logout}` 위임 (thin adapter)
- jobeval `2f24a9b` 옵션 A 그린필드 모범 추종

lib `346306c` (G121 D=A, Task #149) 추출 (RefreshTokenStore SPI + InMemoryRefreshTokenStore +
RefreshSession + JwtRefreshService + JwtRefreshAutoConfiguration) 풀 활용.

## 2. 변경 파일 (4 + 1 박제)

| 파일 | 작업 | LOC 변화 |
|------|------|---------|
| `backend/src/main/java/com/easyperformance/security/RefreshTokenStore.java` | **삭제** | **-111** |
| `backend/src/main/java/com/easyperformance/security/AuthService.java` | 리팩터 (lib JwtRefreshService 위임) | -178 / +163 = **-15** |
| `backend/src/test/java/com/easyperformance/security/AuthServiceTest.java` | lib InMemoryRefreshTokenStore + JwtRefreshService 사용 + Thread.sleep 제거 | -139 / +134 = **-5** |
| `backend/src/main/resources/application.yml` | `easyplatform.auth.refresh.{enabled,ttl}` 신규 | **+12** |
| `backend/_workspace/PERFORMANCE_LIB_BE_22_CUTOVER_2026-06-08.md` | 박제 신규 | (본 파일) |

**순 LOC 감축**: -119 LOC (RefreshTokenStore.java 풀 삭제 -111 + AuthService 정제 -15 + 테스트 정제 -5 + 설정 +12).
**jobeval `2f24a9b` 실증 ~14 LOC** 대비 ~100+ LOC 추가 감축 (performance 자체 store 의 Clock 주입 + revokeAllForUser + cleanupExpired
유틸리티까지 lib 가 흡수). 그린필드 모범 2호로서 진입 비용 절감 폭 확대.

## 3. 흐름 변경 매트릭스

| 흐름 | Before (자체) | After (lib 위임) |
|------|--------------|-----------------|
| Login refresh 발행 | `libIssuer.issueRefresh + refreshTokenStore.register` (수동 2단계) | `libRefreshService.issueRefresh(sub, tid, claims)` (1줄, jti claim + store 등록 통합) |
| Refresh rotation | `libParser.parse → refreshTokenStore.validate → revoke → libIssuer.issueRefresh + register` (5단계) | `libRefreshService.rotate(old, accessTtl, claims) → TokenPair` (1줄, 위변조 차단 + typ=refresh 검증 + 재사용 차단 통합) |
| Logout | `refreshTokenStore.revoke(token)` | `libRefreshService.logout(token)` |
| Access 재발행 | n/a | rotate 후 performance `JwtService.issueAccessToken` 으로 재발행 (roles claim 정합 — lib rotate 의 access 는 roles 미포함) |

**rotation 안전성 강화**: lib `rotate` 는 typ=refresh 클레임 검증 (access 토큰으로 refresh 시도 시 차단). 자체 구현은 그 검증 없음.

## 4. 빈 등록 변화

| 빈 | Before | After |
|----|--------|-------|
| `com.easyperformance.security.RefreshTokenStore` | 자체 `@Component` | **삭제** |
| `com.easyware.platform.auth.refresh.RefreshTokenStore` (SPI) | 미등록 | lib `InMemoryRefreshTokenStore` 자동 등록 (`@ConditionalOnMissingBean`) |
| `com.easyware.platform.auth.refresh.JwtRefreshService` | 미등록 | lib autoconfig 자동 등록 |
| `com.easyperformance.security.AuthService` | `RefreshTokenStore` + `JwtTokenIssuer` + `JwtTokenParser` 4 의존성 주입 | `JwtRefreshService` 1 의존성 + `JwtService` (변동 없음) |

활성 조건: `easyplatform.auth.refresh.enabled=true` (application.yml 기본 `true` + env override 지원).

## 5. 테스트 결과

| 테스트 | 변화 |
|--------|------|
| `AuthServiceTest` (7 케이스) | 모두 lib 빈 결합으로 통과 — Thread.sleep 제거 (lib jti claim 으로 동일 시각 신규 토큰 보장) |
| `refresh_invalidToken_*` | 자체 구현 대비 에러 코드 변경: AUTH_REFRESH_TOKEN_INVALID → AUTH_REFRESH_TOKEN_EXPIRED (lib rotate 는 store validate 가 먼저 실행, 미등록 토큰은 IllegalStateException → AUTH_REFRESH_TOKEN_EXPIRED 로 매핑) |
| `JwtServiceTest`, `UuidV7Test`, `NeonProvisioningIntegrationTest` | 회귀 0 |

## 6. ADR-031 정합

performance 본질 = B2B-Enterprise per-tenant + SMB Shared. B2C 부재.
lib BE 22 는 자매품 본질과 무관 — refresh 흐름은 B2B + B2C 공통 인프라.
performance 의 SMB Shared 진입 시에도 lib JwtRefreshService 그대로 활용
(token store 는 dev/test 까지 in-memory, prod RDB/Redis 격상은 후속 lib 슬라이스에서 SPI 교체).

## 7. 자매품 cutover 매트릭스 (2026-06-08, lib BE 22 진행)

| 호 | 자매품 | commit | 비고 |
|----|--------|--------|------|
| 1호 | jobeval | `2f24a9b` | 그린필드 모범 1호 (~14 LOC 감축 실증) |
| 2호~5호 | (예: mra/jobstructure/hcm/ware 등 — 매트릭스 진행률 참조) | — | dev/dual-claim/LIVE 별 분기 |
| **6호** | **performance** | **본 슬라이스** | **그린필드 모범 2호** (~100+ LOC 감축 실증, jobeval 옵션 A 추종) |

## 8. 후속

- 단계 3 BE-CC-2 JWT 5분리 (`6f6d3df`) 풀 보존
- 격상 100% 풀 완성 (단계 0 ✅ + 1 ✅ + 2 ✅ + 3 ✅ + 4 ✅ + 5 SMB ✅) 풀 보존
- lib BE 22 cutover 매트릭스 진행률 + 그린필드 모범 누적
- 단계 4 EC-FE (Mantine v9 + openapi-typescript) 진입 후 사용자 가입 흐름 추가 시 본 thin adapter 그대로 활용

LIVE 영향 0 (dev/test 한정, prod 는 application-prod.yml 의 fail-fast 정합 유지).

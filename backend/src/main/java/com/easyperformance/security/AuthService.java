/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.security;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.easyperformance.common.UuidV7;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.auth.JwtClaims;
import com.easyware.platform.auth.refresh.JwtRefreshService;
import com.easyware.platform.error.ApiException;

/**
 * B2B Auth 서비스 — 단계 3 BE-CC-2 JWT 5분리 (G84 D=A, Task #122, 2026-06-08).
 *
 * <p><strong>lib BE 22 JwtRefreshAspect cutover (G127.6 D=A, Task #159, 2026-06-08)</strong> —
 * 자체 RefreshTokenStore + 자체 refresh rotation 흐름을 lib {@link JwtRefreshService} 로 위임
 * (thin adapter). lib commit {@code 346306c} — 자매품 7 동일 패턴 (RefreshTokenStore SPI +
 * InMemoryRefreshTokenStore + JwtRefreshService) 일반화. <b>그린필드 모범 2호</b> (jobeval
 * {@code 2f24a9b} 옵션 A 모범 추종, ~100+ LOC 감축 실증, breaking change 0).
 *
 * <p>그린필드 dev 진입 — 사용자 엔티티 미존재. 본 서비스는 이메일 → (userId, tenantId) 매핑을 in-memory
 * 로 유지하는 dev-only stub. 단계 4 (EC-FE) 후속에서 사용자 엔티티 + bcrypt 검증으로 격상.
 *
 * <p><strong>JWT 5분리 정합</strong>
 * <ol>
 *   <li>Access Token — {@link JwtService#issueAccessToken} (HS512, 5분, lib JwtTokenIssuer 위임).</li>
 *   <li>Refresh Token — lib {@link JwtRefreshService#issueRefresh} + 자동 store 등록 + jti claim 정합.</li>
 *   <li>ID Token — 본 단계 생략 (단계 3+ 격상 후보).</li>
 *   <li>Tenant Claim — TID (lib {@link JwtClaims#TID}) — JwtAuthFilter + lib TenantContextFilter 가 추출.</li>
 *   <li>User Claim — subject = userId.toString() (mra c632c5f 정합).</li>
 * </ol>
 *
 * <p><strong>Refresh rotation</strong> — lib {@link JwtRefreshService#rotate} 가 재플레이 공격 방어 +
 * 신규 access + refresh 쌍 발행 + 기존 폐기 처리. typ=refresh + 서명 검증 + store validate 통합.
 *
 * <p><strong>ADR-031 정합</strong> — performance 는 B2B-Enterprise per-tenant + SMB Shared 본질.
 * B2C 부재 (도메인 본질 = 기업 성과 평가 워크플로우). dual-claim 비파괴 (mra 패턴) 불필요 — 그린필드.
 *
 * <p><strong>모범 정합 (4 자매품 누적)</strong>
 * <ul>
 *   <li>jobeval `4dff03a` — Java 그린필드 모범 1호 (in-memory stub user + UUIDv7).</li>
 *   <li>jobeval `2f24a9b` — lib BE 22 cutover 1호 (옵션 A 그린필드 모범 — ~14 LOC 감축).</li>
 *   <li>mra `38e566d` — Java dual-claim 비파괴 모범 2호 (기존 LoginService 보존 + AuthService 신규).</li>
 *   <li>jobstructure `d64944e` — Kotlin idiomatic 모범 3호 (jti claim, Clock 주입).</li>
 *   <li>performance 본 슬라이스 = lib BE 22 cutover <b>6호</b> + 그린필드 모범 <b>2호</b>
 *       (jobeval 옵션 A 추종 + 자체 RefreshTokenStore 삭제 + lib JwtRefreshService 위임).</li>
 * </ul>
 */
@Service
public class AuthService {

    private final JwtService jwtService;
    private final JwtRefreshService libRefreshService;

    // dev-only stub user 저장소 — 이메일 → (userId, tenantId, roles).
    // 단계 4 격상 시 사용자 엔티티 + Repository 로 격상.
    private final Map<String, StubUser> stubUsersByEmail = new ConcurrentHashMap<>();
    private final Map<UUID, StubUser> stubUsersById = new ConcurrentHashMap<>();

    private final UUID defaultTenantId;

    public AuthService(JwtService jwtService,
                       JwtRefreshService libRefreshService,
                       @Value("${app.security.auth.dev-default-tenant-id:00000000-0000-0000-0000-000000000001}")
                       String defaultTenantIdStr) {
        this.jwtService = jwtService;
        this.libRefreshService = libRefreshService;
        this.defaultTenantId = UUID.fromString(defaultTenantIdStr);
    }

    /**
     * dev-only stub login — 이메일로 사용자 조회/신규 생성 후 access + refresh 발행.
     *
     * <p>단계 4 격상 시: bcrypt 검증 + 사용자 엔티티 조회 + 권한 분기 + 로그인 실패 카운트.
     */
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest req) {
        if (req == null || req.email() == null || req.email().isBlank()) {
            throw new ApiException(PerformanceErrorCode.AUTH_LOGIN_FAILED,
                Map.of("reason", "email-required"));
        }
        // dev stub — 이메일 first-seen 시 자동 생성 + UUIDv7 userId 발행 + default tenant 할당.
        // 단계 4 격상 시: 신규 가입은 별도 /signup 흐름, login 은 검증만.
        StubUser user = stubUsersByEmail.computeIfAbsent(req.email(),
            email -> {
                StubUser u = new StubUser(UuidV7.generate(), defaultTenantId, List.of("USER"));
                stubUsersById.put(u.userId(), u);
                return u;
            });

        return issueTokenPair(user.userId(), user.tenantId(), user.roles());
    }

    /**
     * Refresh — refresh 토큰 검증 + rotation (신규 access + refresh 발행 + 이전 refresh 폐기).
     *
     * <p>lib {@link JwtRefreshService#rotate} 위임 — typ=refresh 서명 검증 + store validate + 신규 쌍 발행.
     */
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest req) {
        if (req == null || req.refreshToken() == null || req.refreshToken().isBlank()) {
            throw new ApiException(PerformanceErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND,
                Map.of("reason", "refreshToken-required"));
        }
        Duration accessTtl = Duration.ofMillis(jwtService.accessTtlMs());

        // lib rotate — 기존 refresh 검증 + 신규 access + refresh 쌍 발행 + 기존 폐기.
        JwtRefreshService.TokenPair pair;
        try {
            pair = libRefreshService.rotate(req.refreshToken(), accessTtl, Map.of());
        } catch (IllegalStateException e) {
            throw new ApiException(PerformanceErrorCode.AUTH_REFRESH_TOKEN_EXPIRED,
                Map.of("reason", e.getMessage()));
        } catch (Exception e) {
            throw new ApiException(PerformanceErrorCode.AUTH_REFRESH_TOKEN_INVALID,
                Map.of("reason", "parse-failed"));
        }

        // lib rotate 의 신규 access 는 performance JwtService 표준(HS512 + roles claim) 과 별개로 발행됨 —
        // performance 정합을 위해 access 만 performance JwtService 로 재발행 (roles claim 포함).
        UUID userId = jwtService.parse(pair.refreshToken()).subjectAsUuid().orElseThrow(() ->
            new ApiException(PerformanceErrorCode.AUTH_REFRESH_TOKEN_INVALID,
                Map.of("reason", "subject-missing")));
        UUID tenantId = jwtService.parse(pair.refreshToken()).tenantId().orElse(null);

        StubUser stub = stubUsersById.get(userId);
        List<String> roles = stub != null ? stub.roles() : List.of("USER");

        String access = jwtService.issueAccessToken(userId, tenantId, roles);
        long refreshTtlSec = jwtService.refreshTtlMs() / 1000;

        return AuthDtos.TokenResponse.of(
            access, jwtService.accessTtlMs() / 1000,
            pair.refreshToken(), refreshTtlSec,
            userId, tenantId, roles);
    }

    /** Logout — refresh 토큰 폐기. access 토큰은 stateless 라 만료 시까지 유효 (5분 한도). */
    public void logout(AuthDtos.LogoutRequest req) {
        if (req != null && req.refreshToken() != null) {
            libRefreshService.logout(req.refreshToken());
        }
    }

    /**
     * access + refresh 쌍 발행 — access 는 performance JwtService (roles claim 포함),
     * refresh 는 lib {@link JwtRefreshService#issueRefresh} (자동 store 등록 + jti claim 정합).
     */
    private AuthDtos.TokenResponse issueTokenPair(UUID userId, UUID tenantId, List<String> roles) {
        String access = jwtService.issueAccessToken(userId, tenantId, roles);
        String refresh = libRefreshService.issueRefresh(userId.toString(), tenantId, Map.of());
        Duration refreshTtl = Duration.ofMillis(jwtService.refreshTtlMs());

        return AuthDtos.TokenResponse.of(
            access, jwtService.accessTtlMs() / 1000,
            refresh, refreshTtl.toSeconds(),
            userId, tenantId, roles);
    }

    /** dev-only stub user 메타. 단계 4 격상 시 사용자 엔티티로 대체. */
    private record StubUser(UUID userId, UUID tenantId, List<String> roles) {}
}

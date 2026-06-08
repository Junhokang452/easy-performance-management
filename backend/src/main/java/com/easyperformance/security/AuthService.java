/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.easyperformance.common.UuidV7;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.auth.JwtClaims;
import com.easyware.platform.auth.JwtTokenIssuer;
import com.easyware.platform.auth.JwtTokenParser;
import com.easyware.platform.auth.ParsedToken;
import com.easyware.platform.error.ApiException;

/**
 * B2B Auth 서비스 — 단계 3 BE-CC-2 JWT 5분리 (G84 D=A, Task #122, 2026-06-08).
 *
 * <p>그린필드 dev 진입 — 사용자 엔티티 미존재. 본 서비스는 이메일 → (userId, tenantId) 매핑을 in-memory
 * 로 유지하는 dev-only stub. 단계 4 (EC-FE) 후속에서 사용자 엔티티 + bcrypt 검증으로 격상.
 *
 * <p><strong>JWT 5분리 정합</strong>
 * <ol>
 *   <li>Access Token — JwtService.issueAccessToken(userId, tenantId, roles), 5분 만료 (TTL 설정 가능).</li>
 *   <li>Refresh Token — lib JwtTokenIssuer.issueRefresh, 7일 만료 + RefreshTokenStore 등록.</li>
 *   <li>ID Token — 본 단계 생략 (단계 3+ 격상 후보).</li>
 *   <li>Tenant Claim — TID (lib {@link JwtClaims#TID}) — JwtAuthFilter + lib TenantContextFilter 가 추출.</li>
 *   <li>User Claim — subject = userId.toString() (mra c632c5f 정합).</li>
 * </ol>
 *
 * <p><strong>Refresh rotation</strong> — 매 refresh 마다 새 토큰 발행 + 이전 토큰 폐기. 토큰 재사용 차단
 * (재플레이 공격 방어 — refresh 두 번 사용 시 두 번째는 401).
 *
 * <p><strong>ADR-031 정합</strong> — performance 는 B2B-Enterprise per-tenant + SMB Shared 본질.
 * B2C 부재 (도메인 본질 = 기업 성과 평가 워크플로우). dual-claim 비파괴 (mra 패턴) 불필요 — 그린필드.
 *
 * <p><strong>모범 정합 (3 자매품 누적)</strong>
 * <ul>
 *   <li>jobeval `4dff03a` — Java 그린필드 모범 1호 (in-memory stub user + UUIDv7).</li>
 *   <li>mra `38e566d` — Java dual-claim 비파괴 모범 2호 (기존 LoginService 보존 + AuthService 신규).</li>
 *   <li>jobstructure `d64944e` — Kotlin idiomatic 모범 3호 (jti claim, Clock 주입).</li>
 *   <li>performance 본 슬라이스 = Java 그린필드 모범 4호 (jobeval 정합 + Clock 주입 jobstructure 추종).</li>
 * </ul>
 */
@Service
public class AuthService {

    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtTokenIssuer libIssuer;
    private final JwtTokenParser libParser;
    private final Clock clock;

    // dev-only stub user 저장소 — 이메일 → (userId, tenantId, roles).
    // 단계 4 격상 시 사용자 엔티티 + Repository 로 격상.
    private final Map<String, StubUser> stubUsers = new ConcurrentHashMap<>();

    private final UUID defaultTenantId;

    public AuthService(JwtService jwtService,
                       RefreshTokenStore refreshTokenStore,
                       JwtTokenIssuer libIssuer,
                       JwtTokenParser libParser,
                       @Value("${app.security.auth.dev-default-tenant-id:00000000-0000-0000-0000-000000000001}")
                       String defaultTenantIdStr) {
        this(jwtService, refreshTokenStore, libIssuer, libParser, defaultTenantIdStr, Clock.systemUTC());
    }

    /** 테스트 친화 — 가짜 Clock 주입. */
    public AuthService(JwtService jwtService,
                       RefreshTokenStore refreshTokenStore,
                       JwtTokenIssuer libIssuer,
                       JwtTokenParser libParser,
                       String defaultTenantIdStr,
                       Clock clock) {
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
        this.libIssuer = libIssuer;
        this.libParser = libParser;
        this.defaultTenantId = UUID.fromString(defaultTenantIdStr);
        this.clock = clock;
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
        StubUser user = stubUsers.computeIfAbsent(req.email(),
            email -> new StubUser(UuidV7.generate(), defaultTenantId, List.of("USER")));

        return issueTokenPair(user.userId(), user.tenantId(), user.roles());
    }

    /** Refresh — refresh 토큰 검증 + rotation (신규 access + refresh 발행 + 이전 refresh 폐기). */
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest req) {
        if (req == null || req.refreshToken() == null || req.refreshToken().isBlank()) {
            throw new ApiException(PerformanceErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND,
                Map.of("reason", "refreshToken-required"));
        }
        String oldToken = req.refreshToken();

        // 1. lib parser 로 서명/만료 검증.
        ParsedToken parsed;
        try {
            parsed = libParser.parse(oldToken);
        } catch (Exception e) {
            throw new ApiException(PerformanceErrorCode.AUTH_REFRESH_TOKEN_INVALID,
                Map.of("reason", "parse-failed"));
        }
        // 2. RefreshTokenStore 검증 (revoke / re-use 차단).
        RefreshTokenStore.RefreshSession session = refreshTokenStore.validate(oldToken);
        if (session == null) {
            throw new ApiException(PerformanceErrorCode.AUTH_REFRESH_TOKEN_EXPIRED,
                Map.of("reason", "expired-or-revoked"));
        }
        // 3. rotation — 이전 토큰 폐기 후 신규 쌍 발행.
        refreshTokenStore.revoke(oldToken);

        UUID userId = parsed.subjectAsUuid().orElseThrow(() ->
            new ApiException(PerformanceErrorCode.AUTH_REFRESH_TOKEN_INVALID,
                Map.of("reason", "subject-missing")));
        UUID tenantId = parsed.tenantId().orElse(session.tenantId());

        // stub user 의 roles 조회 (이메일은 모름 — userId 기반으로 stub 에서 검색).
        List<String> roles = stubUsers.values().stream()
            .filter(u -> userId.equals(u.userId()))
            .findFirst()
            .map(StubUser::roles)
            .orElse(List.of("USER"));

        return issueTokenPair(userId, tenantId, roles);
    }

    /** Logout — refresh 토큰 폐기. access 토큰은 stateless 라 만료 시까지 유효 (5분 한도). */
    public void logout(AuthDtos.LogoutRequest req) {
        if (req != null && req.refreshToken() != null) {
            refreshTokenStore.revoke(req.refreshToken());
        }
    }

    /** access + refresh 쌍 발행 + RefreshTokenStore 등록. */
    private AuthDtos.TokenResponse issueTokenPair(UUID userId, UUID tenantId, List<String> roles) {
        String access = jwtService.issueAccessToken(userId, tenantId, roles);
        Duration refreshTtl = Duration.ofMillis(jwtService.refreshTtlMs());
        String refresh = libIssuer.issueRefresh(
            userId.toString(),
            Map.of(JwtClaims.TID, tenantId != null ? tenantId.toString() : ""),
            refreshTtl);
        Instant expiresAt = Instant.now(clock).plus(refreshTtl);
        refreshTokenStore.register(refresh, userId, tenantId, expiresAt);

        return AuthDtos.TokenResponse.of(
            access, jwtService.accessTtlMs() / 1000,
            refresh, refreshTtl.toSeconds(),
            userId, tenantId, roles);
    }

    /** dev-only stub user 메타. 단계 4 격상 시 사용자 엔티티로 대체. */
    private record StubUser(UUID userId, UUID tenantId, List<String> roles) {}
}

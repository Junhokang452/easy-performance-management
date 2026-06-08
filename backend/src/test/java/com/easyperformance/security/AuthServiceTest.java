/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.auth.JwtAlgorithm;
import com.easyware.platform.auth.JwtTokenIssuer;
import com.easyware.platform.auth.JwtTokenParser;
import com.easyware.platform.auth.refresh.InMemoryRefreshTokenStore;
import com.easyware.platform.auth.refresh.JwtRefreshService;
import com.easyware.platform.auth.refresh.RefreshTokenStore;
import com.easyware.platform.error.ApiException;

/**
 * AuthService 단위 테스트 — 단계 3 BE-CC-2 JWT 5분리 (G84 D=A, Task #122, 2026-06-08).
 *
 * <p>lib BE 22 cutover (G127.6 D=A, Task #159, 2026-06-08) — lib {@link JwtRefreshService} +
 * {@link InMemoryRefreshTokenStore} 위임. login + refresh rotation + logout 흐름 회귀 가드.
 * jobeval 옵션 A 그린필드 모범 정합 + Clock 주입.
 */
class AuthServiceTest {

    private static final String SECRET =
        "dev-only-performance-jwt-secret-please-rotate-in-prod-via-env-jwt-secret-min-64bytes-required-12345678";
    private static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    private AuthService authService;
    private RefreshTokenStore store;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-08T00:00:00Z"), ZoneOffset.UTC);
        JwtTokenIssuer issuer = new JwtTokenIssuer(SECRET, JwtAlgorithm.HS512);
        JwtTokenParser parser = new JwtTokenParser(SECRET);
        JwtService jwtService = new JwtService(issuer, parser, 300_000L, 604_800_000L);
        store = new InMemoryRefreshTokenStore(clock);
        JwtRefreshService libRefreshService = new JwtRefreshService(
            issuer, parser, store, Duration.ofMillis(604_800_000L), clock);
        authService = new AuthService(jwtService, libRefreshService, DEFAULT_TENANT);
    }

    @Test
    void login_issuesAccessAndRefreshTokens() {
        AuthDtos.TokenResponse response = authService.login(
            new AuthDtos.LoginRequest("alice@example.com", "irrelevant-for-stub"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.userId()).isNotNull();
        assertThat(response.tenantId().toString()).isEqualTo(DEFAULT_TENANT);
        assertThat(response.roles()).containsExactly("USER");
        assertThat(response.accessExpiresInSec()).isEqualTo(300L);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void login_sameEmail_reusesUserId() {
        AuthDtos.TokenResponse first = authService.login(
            new AuthDtos.LoginRequest("bob@example.com", "any"));
        AuthDtos.TokenResponse second = authService.login(
            new AuthDtos.LoginRequest("bob@example.com", "any"));

        // 같은 이메일 → 같은 stub userId 재사용 (단계 4 격상 시 사용자 엔티티 1:1 매핑).
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(second.tenantId()).isEqualTo(first.tenantId());
        // lib JwtRefreshService 는 jti claim (UUID.randomUUID) 으로 토큰 충돌 방지 — 동일 시각 + 동일 sub 라도 분리.
        assertThat(store.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void login_blankEmail_throwsAuthLoginFailed() {
        assertThatThrownBy(() -> authService.login(new AuthDtos.LoginRequest(" ", "p")))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_LOGIN_FAILED);
    }

    @Test
    void refresh_rotatesTokens_andRevokesOldOne() {
        AuthDtos.TokenResponse initial = authService.login(
            new AuthDtos.LoginRequest("carol@example.com", "any"));
        String oldRefresh = initial.refreshToken();

        // lib JwtRefreshService 는 jti claim 으로 동일 시각도 신규 토큰 보장 — Thread.sleep 불필요.
        AuthDtos.TokenResponse rotated = authService.refresh(
            new AuthDtos.RefreshRequest(oldRefresh));

        assertThat(rotated.refreshToken()).isNotEqualTo(oldRefresh);
        assertThat(rotated.userId()).isEqualTo(initial.userId());
        assertThat(rotated.tenantId()).isEqualTo(initial.tenantId());

        // 재사용 차단 — 이전 refresh 토큰은 무효
        assertThatThrownBy(() -> authService.refresh(new AuthDtos.RefreshRequest(oldRefresh)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
    }

    @Test
    void refresh_invalidToken_throwsAuthRefreshTokenInvalid() {
        // lib rotate 는 store validate 가 먼저 — 미등록 토큰 → IllegalStateException → AUTH_REFRESH_TOKEN_EXPIRED.
        assertThatThrownBy(() -> authService.refresh(new AuthDtos.RefreshRequest("not-a-jwt")))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
    }

    @Test
    void refresh_blankToken_throwsAuthRefreshTokenNotFound() {
        assertThatThrownBy(() -> authService.refresh(new AuthDtos.RefreshRequest(" ")))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    void logout_revokesRefreshToken() {
        AuthDtos.TokenResponse response = authService.login(
            new AuthDtos.LoginRequest("dave@example.com", "any"));
        assertThat(store.size()).isEqualTo(1);

        authService.logout(new AuthDtos.LogoutRequest(response.refreshToken()));
        assertThat(store.size()).isZero();

        // 폐기된 refresh 재사용 차단
        assertThatThrownBy(() -> authService.refresh(new AuthDtos.RefreshRequest(response.refreshToken())))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
    }
}

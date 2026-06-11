/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.easyperformance.common.UuidV7;
import com.easyperformance.domain.account.PerformanceUser;
import com.easyperformance.domain.account.PerformanceUserRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.auth.JwtAlgorithm;
import com.easyware.platform.auth.JwtTokenIssuer;
import com.easyware.platform.auth.JwtTokenParser;
import com.easyware.platform.auth.refresh.InMemoryRefreshTokenStore;
import com.easyware.platform.auth.refresh.JwtRefreshService;
import com.easyware.platform.auth.refresh.RefreshTokenStore;
import com.easyware.platform.error.ApiException;

/**
 * AuthService 단위 테스트 — 인증 격상 (실 사용자 엔티티 + bcrypt + 역할, 2026-06-12).
 *
 * <p>구 stub 전제 테스트 재작성: mock {@link PerformanceUserRepository} + 실 BCrypt(strength 4 —
 * 테스트 속도) 조합. lib BE 22 {@link JwtRefreshService} + {@link InMemoryRefreshTokenStore} 위임
 * 흐름(login + refresh rotation + logout) 회귀 가드 보존.
 */
class AuthServiceTest {

    private static final String SECRET =
        "dev-only-performance-jwt-secret-please-rotate-in-prod-via-env-jwt-secret-min-64bytes-required-12345678";
    private static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";
    private static final UUID DEFAULT_TENANT_ID = UUID.fromString(DEFAULT_TENANT);

    private AuthService authService;
    private RefreshTokenStore store;
    private PerformanceUserRepository users;
    private PasswordEncoder passwordEncoder;
    private PerformanceUser hrAdmin;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC);
        JwtTokenIssuer issuer = new JwtTokenIssuer(SECRET, JwtAlgorithm.HS512);
        JwtTokenParser parser = new JwtTokenParser(SECRET);
        JwtService jwtService = new JwtService(issuer, parser, 300_000L, 604_800_000L);
        store = new InMemoryRefreshTokenStore(clock);
        JwtRefreshService libRefreshService = new JwtRefreshService(
            issuer, parser, store, Duration.ofMillis(604_800_000L), clock);

        users = mock(PerformanceUserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);

        hrAdmin = newUser("alice@performance.dev", "HR_ADMIN", passwordEncoder.encode("dev"), true);
        when(users.findByTenantIdAndEmail(eq(DEFAULT_TENANT_ID), eq("alice@performance.dev")))
            .thenReturn(Optional.of(hrAdmin));
        when(users.findByIdAndTenantId(eq(hrAdmin.getId()), eq(DEFAULT_TENANT_ID)))
            .thenReturn(Optional.of(hrAdmin));

        // default-tenant 미설정 (게이트 OFF) → dev-default-tenant-id 폴백 해석.
        authService = new AuthService(jwtService, libRefreshService, users, passwordEncoder,
            "", DEFAULT_TENANT);
    }

    private static PerformanceUser newUser(String email, String role, String passwordHash, boolean active) {
        PerformanceUser user = new PerformanceUser();
        user.setId(UuidV7.generate());
        user.setTenantId(DEFAULT_TENANT_ID);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setDisplayName(email);
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    @Test
    void login_withValidCredentials_issuesAccessAndRefreshTokens() {
        AuthDtos.TokenResponse response = authService.login(
            new AuthDtos.LoginRequest("alice@performance.dev", "dev"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.userId()).isEqualTo(hrAdmin.getId());
        assertThat(response.tenantId()).isEqualTo(DEFAULT_TENANT_ID);
        assertThat(response.roles()).containsExactly("HR_ADMIN");
        assertThat(response.accessExpiresInSec()).isEqualTo(300L);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void login_wrongPassword_throwsAuthLoginFailed() {
        assertThatThrownBy(() -> authService.login(
                new AuthDtos.LoginRequest("alice@performance.dev", "wrong-password")))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_LOGIN_FAILED);
        assertThat(store.size()).isZero();
    }

    @Test
    void login_unknownEmail_throwsAuthLoginFailed() {
        when(users.findByTenantIdAndEmail(eq(DEFAULT_TENANT_ID), eq("ghost@performance.dev")))
            .thenReturn(Optional.empty());

        // 계정 부재도 비밀번호 불일치와 동일 코드 — 계정 존재 여부 노출 차단 (talent 정합).
        assertThatThrownBy(() -> authService.login(
                new AuthDtos.LoginRequest("ghost@performance.dev", "dev")))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_LOGIN_FAILED);
    }

    @Test
    void login_inactiveUser_throwsAuthLoginFailed() {
        PerformanceUser inactive = newUser("inactive@performance.dev", "EMPLOYEE",
            passwordEncoder.encode("dev"), false);
        when(users.findByTenantIdAndEmail(eq(DEFAULT_TENANT_ID), eq("inactive@performance.dev")))
            .thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> authService.login(
                new AuthDtos.LoginRequest("inactive@performance.dev", "dev")))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_LOGIN_FAILED);
    }

    @Test
    void login_blankEmailOrPassword_throwsAuthLoginFailed() {
        assertThatThrownBy(() -> authService.login(new AuthDtos.LoginRequest(" ", "p")))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_LOGIN_FAILED);
        assertThatThrownBy(() -> authService.login(new AuthDtos.LoginRequest("alice@performance.dev", " ")))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_LOGIN_FAILED);
    }

    @Test
    void login_trimsEmail_beforeLookup() {
        AuthDtos.TokenResponse response = authService.login(
            new AuthDtos.LoginRequest("  alice@performance.dev  ", "dev"));
        assertThat(response.userId()).isEqualTo(hrAdmin.getId());
    }

    @Test
    void refresh_rotatesTokens_andRevokesOldOne() {
        AuthDtos.TokenResponse initial = authService.login(
            new AuthDtos.LoginRequest("alice@performance.dev", "dev"));
        String oldRefresh = initial.refreshToken();

        // lib JwtRefreshService 는 jti claim 으로 동일 시각도 신규 토큰 보장 — Thread.sleep 불필요.
        AuthDtos.TokenResponse rotated = authService.refresh(
            new AuthDtos.RefreshRequest(oldRefresh));

        assertThat(rotated.refreshToken()).isNotEqualTo(oldRefresh);
        assertThat(rotated.userId()).isEqualTo(initial.userId());
        assertThat(rotated.tenantId()).isEqualTo(initial.tenantId());
        // 인증 격상 — refresh 시 실 계정 재조회로 roles 최신화 (stub "USER" 폴백 폐기).
        assertThat(rotated.roles()).containsExactly("HR_ADMIN");

        // 재사용 차단 — 이전 refresh 토큰은 무효
        assertThatThrownBy(() -> authService.refresh(new AuthDtos.RefreshRequest(oldRefresh)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
    }

    @Test
    void refresh_userMissing_throwsAuthRefreshTokenInvalid() {
        AuthDtos.TokenResponse initial = authService.login(
            new AuthDtos.LoginRequest("alice@performance.dev", "dev"));
        // rotation 후 재조회 시점에 계정 삭제/이관된 상황.
        when(users.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new AuthDtos.RefreshRequest(initial.refreshToken())))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    @Test
    void refresh_inactiveUser_throwsAuthRefreshTokenInvalid() {
        AuthDtos.TokenResponse initial = authService.login(
            new AuthDtos.LoginRequest("alice@performance.dev", "dev"));
        hrAdmin.setActive(false);

        assertThatThrownBy(() -> authService.refresh(new AuthDtos.RefreshRequest(initial.refreshToken())))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    @Test
    void refresh_invalidToken_throwsAuthRefreshTokenExpired() {
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
            new AuthDtos.LoginRequest("alice@performance.dev", "dev"));
        assertThat(store.size()).isEqualTo(1);

        authService.logout(new AuthDtos.LogoutRequest(response.refreshToken()));
        assertThat(store.size()).isZero();

        // 폐기된 refresh 재사용 차단
        assertThatThrownBy(() -> authService.refresh(new AuthDtos.RefreshRequest(response.refreshToken())))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
    }

    @Test
    void resolveDefaultTenantId_prefersAppTenancyKey_overDevFallback() {
        UUID tenancyKey = UUID.fromString("019eb0d5-0000-7000-8000-000000000001");

        // app.tenancy.default-tenant-id 설정 시 우선 (게이트 ON, talent/recruit G149 패턴).
        assertThat(AuthService.resolveDefaultTenantId(tenancyKey.toString(), DEFAULT_TENANT))
            .isEqualTo(tenancyKey);
        // 미설정(blank) 시 dev-default-tenant-id 폴백 (구 stub 키 보존).
        assertThat(AuthService.resolveDefaultTenantId("", DEFAULT_TENANT))
            .isEqualTo(DEFAULT_TENANT_ID);
        assertThat(AuthService.resolveDefaultTenantId(null, DEFAULT_TENANT))
            .isEqualTo(DEFAULT_TENANT_ID);
    }
}

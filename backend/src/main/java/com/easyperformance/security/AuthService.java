/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.security;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyperformance.domain.account.PerformanceUser;
import com.easyperformance.domain.account.PerformanceUserRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.auth.JwtClaims;
import com.easyware.platform.auth.refresh.JwtRefreshService;
import com.easyware.platform.error.ApiException;

/**
 * B2B Auth 서비스 — 인증 격상 (in-memory stub → 실 사용자 엔티티 + bcrypt + 역할, 2026-06-12).
 *
 * <p><strong>격상 내용</strong> (talent {@code AuthService} `8e29426` 패턴 / store-hr `abb0418` 정합):
 * <ul>
 *   <li>login — {@link PerformanceUser} 조회 + bcrypt 검증. 부재/비활성/불일치 모두 동일
 *       E9804101 (계정 존재 여부 노출 차단).</li>
 *   <li>roles — 계정 role 단일 발급 ({@code List.of(role)}) — JwtAuthFilter 가 그대로
 *       {@code SimpleGrantedAuthority} 매핑 (ROLE_ prefix 없음 — SUPER_ADMIN 가드는
 *       {@code hasAuthority("SUPER_ADMIN")}).</li>
 *   <li>endpoint 경로·{@link AuthDtos.TokenResponse} shape 불변 (FE wire 호환).</li>
 * </ul>
 *
 * <p><strong>default-tenant 해석 (게이트 라우팅 정합)</strong> — 우선순위:
 * {@code app.tenancy.default-tenant-id} (단계 2 게이트 ON, talent/recruit G149 패턴) &gt;
 * {@code app.security.auth.dev-default-tenant-id} (기존 stub 폴백 보존). 로그인 시점은 미인증이라
 * 테넌트 컨텍스트 부재 — 해석된 default tenant 의 계정을 조회한다 (멀티테넌트 로그인 해석은
 * 본 운영 단계 후속 — hcm tenantCode 페이로드 패턴).
 *
 * <p><strong>lib BE 22 JwtRefreshService 위임 보존</strong> (G127.6 cutover 6호) —
 * {@link JwtRefreshService#issueRefresh}/{@code rotate}/{@code logout} 흐름 불변.
 *
 * <p><strong>JWT 5분리 정합</strong>
 * <ol>
 *   <li>Access Token — {@link JwtService#issueAccessToken} (HS512, 5분, lib JwtTokenIssuer 위임).</li>
 *   <li>Refresh Token — lib {@link JwtRefreshService#issueRefresh} + 자동 store 등록 + jti claim 정합.</li>
 *   <li>ID Token — 생략 (격상 후보).</li>
 *   <li>Tenant Claim — TID (lib {@link JwtClaims#TID}).</li>
 *   <li>User Claim — subject = userId.toString().</li>
 * </ol>
 *
 * <p><strong>ADR-031 정합</strong> — performance 는 B2B-Enterprise per-tenant + SMB Shared 본질.
 * B2C 부재 — dual-claim 불필요.
 */
@Service
public class AuthService {

    private final JwtService jwtService;
    private final JwtRefreshService libRefreshService;
    private final PerformanceUserRepository users;
    private final PasswordEncoder passwordEncoder;

    /** 해석된 default tenant — app.tenancy.default-tenant-id > dev-default-tenant-id. */
    private final UUID defaultTenantId;

    public AuthService(JwtService jwtService,
                       JwtRefreshService libRefreshService,
                       PerformanceUserRepository users,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.tenancy.default-tenant-id:}")
                       String defaultTenantIdStr,
                       @Value("${app.security.auth.dev-default-tenant-id:00000000-0000-0000-0000-000000000001}")
                       String devDefaultTenantIdStr) {
        this.jwtService = jwtService;
        this.libRefreshService = libRefreshService;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.defaultTenantId = resolveDefaultTenantId(defaultTenantIdStr, devDefaultTenantIdStr);
    }

    /**
     * default tenant 해석 — {@code app.tenancy.default-tenant-id} (게이트) 우선,
     * 미설정 시 {@code app.security.auth.dev-default-tenant-id} 폴백 (기존 키 보존).
     */
    static UUID resolveDefaultTenantId(String defaultTenantIdStr, String devDefaultTenantIdStr) {
        if (defaultTenantIdStr != null && !defaultTenantIdStr.isBlank()) {
            return UUID.fromString(defaultTenantIdStr.trim());
        }
        return UUID.fromString(devDefaultTenantIdStr.trim());
    }

    /**
     * Login — 사용자 조회 + bcrypt 검증 후 access + refresh 발행.
     *
     * <p>부재/비활성/비밀번호 불일치 전부 동일 E9804101 — 계정 존재 여부 노출 차단 (talent 정합).
     */
    @Transactional(readOnly = true)
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest req) {
        if (req == null || req.email() == null || req.email().isBlank()
                || req.password() == null || req.password().isBlank()) {
            throw new ApiException(PerformanceErrorCode.AUTH_LOGIN_FAILED,
                Map.of("reason", "credentials-required"));
        }
        PerformanceUser account = users.findByTenantIdAndEmail(defaultTenantId, req.email().trim())
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.AUTH_LOGIN_FAILED));
        if (!account.isActive() || !passwordEncoder.matches(req.password(), account.getPasswordHash())) {
            throw new ApiException(PerformanceErrorCode.AUTH_LOGIN_FAILED);
        }
        return issueTokenPair(account.getId(), account.getTenantId(), List.of(account.getRole()));
    }

    /**
     * Refresh — refresh 토큰 검증 + rotation (신규 access + refresh 발행 + 이전 refresh 폐기).
     *
     * <p>lib {@link JwtRefreshService#rotate} 위임 — typ=refresh 서명 검증 + store validate + 신규 쌍 발행.
     * rotation 후 사용자 재조회 (roles 최신화 + 비활성/삭제 계정 차단) — 부재/비활성 시 E9804104.
     */
    @Transactional(readOnly = true)
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
        UUID tenantId = jwtService.parse(pair.refreshToken()).tenantId().orElse(defaultTenantId);

        // 인증 격상 — stub 메모리 폴백("USER") 폐기. 실 계정 재조회 (비활성/삭제 즉시 차단).
        PerformanceUser account = users.findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.AUTH_REFRESH_TOKEN_INVALID,
                Map.of("reason", "user-not-found")));
        if (!account.isActive()) {
            throw new ApiException(PerformanceErrorCode.AUTH_REFRESH_TOKEN_INVALID,
                Map.of("reason", "user-inactive"));
        }
        List<String> roles = List.of(account.getRole());

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
}

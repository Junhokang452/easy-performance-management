/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.PlatformTenant;
import com.easyware.platform.PlatformTenantStore;
import com.easyware.platform.TenantRoutingContext;
import com.easyware.platform.error.ApiException;

import jakarta.validation.Valid;

/**
 * B2B Auth 컨트롤러 — 단계 3 BE-CC-2 JWT 5분리 (G84 D=A, Task #122, 2026-06-08).
 *
 * <p>/api/auth/** = SecurityConfig 의 permitAll 분기 (lib SecurityConfig 와 정합).
 *
 * <p><strong>엔드포인트</strong>
 * <ul>
 *   <li>{@code POST /api/auth/login} — 자격증명 → access + refresh.</li>
 *   <li>{@code POST /api/auth/refresh} — refresh → 신규 access + refresh (rotation).</li>
 *   <li>{@code POST /api/auth/logout} — refresh 폐기 (stateless 환경 — access 토큰은 만료까지 유효).</li>
 * </ul>
 *
 * <p><strong>JWT 5분리 정합</strong>
 * <ol>
 *   <li>Access Token — 5분, Authorization: Bearer.</li>
 *   <li>Refresh Token — 7일, body 응답 (httpOnly 쿠키 패턴 격상 후보).</li>
 *   <li>ID Token — 단계 3+ 격상 후보 (본 슬라이스 생략).</li>
 *   <li>Tenant Claim — lib JwtClaims.TID.</li>
 *   <li>User Claim — subject = userId.</li>
 * </ol>
 *
 * <p><strong>표준 정합</strong>
 * <ul>
 *   <li>easy-standards 00-principles/05-authentication §3 — Refresh rotation + revocation.</li>
 *   <li>easy-standards 10-appendix-spring-jpa §JWT — HS512 + JwtTokenIssuer/Parser 위임.</li>
 *   <li>jobeval `4dff03a` — Java 그린필드 모범 1호 추종.</li>
 *   <li>mra `38e566d` — Java dual-claim 비파괴 모범 2호 (본 자매품 그린필드 → dual-claim 불필요).</li>
 *   <li>jobstructure `d64944e` — Kotlin idiomatic 모범 3호 (jti claim).</li>
 *   <li>BE 17 v2 TenantContextResolver cutover 1호 (jobeval `18bc01f`) 자연 결합.</li>
 *   <li>BE 18 RlsTenantAspect cutover 1호 (jobeval `fd23472`) 자연 결합.</li>
 * </ul>
 *
 * <p><strong>ADR-031 정합</strong> — performance 는 B2B-Enterprise per-tenant + SMB Shared 본질.
 * B2C 부재 (도메인 본질 = 기업 성과 평가 워크플로우 + HR SoR + 매니저-팀원 1:1).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ObjectProvider<PlatformTenantStore> tenantStore;
    private final TenantRoutingContext routingContext;

    public AuthController(AuthService authService,
                          ObjectProvider<PlatformTenantStore> tenantStore,
                          TenantRoutingContext routingContext) {
        this.authService = authService;
        this.tenantStore = tenantStore;
        this.routingContext = routingContext;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.TokenResponse> login(@RequestBody @Valid AuthDtos.LoginRequest req) {
        return ResponseEntity.ok(authenticateWithTenant(req));
    }

    /**
     * 표준 로그인 — 회사 코드(tenantCode) 가 있으면 control plane 에서 ACTIVE 테넌트로 해석해 해당 테넌트
     * DB 로 라우팅({@link TenantRoutingContext#within}) 후 인증한다 (게이트 ON Model B). 빈 값/게이트
     * OFF(스토어 빈 부재) 면 기본 테넌트로 진행 — 기존 동작 보존(가산적·LIVE-safe). 미상/비활성 회사
     * 코드는 계정 존재 노출 차단을 위해 동일 로그인 실패(E9804101)로 환원.
     */
    private AuthDtos.TokenResponse authenticateWithTenant(AuthDtos.LoginRequest req) {
        String code = req.tenantCode();
        if (code == null || code.isBlank()) {
            return authService.login(req);
        }
        PlatformTenantStore store = tenantStore.getIfAvailable();
        if (store == null) {
            return authService.login(req); // control plane 미연결(게이트 OFF) — 회사 코드 무시, 기본 테넌트
        }
        PlatformTenant tenant;
        try {
            tenant = store.requireActiveByCode(code.trim());
        } catch (RuntimeException ex) {
            throw new ApiException(PerformanceErrorCode.AUTH_LOGIN_FAILED);
        }
        return routingContext.within(tenant.id(), tenant.code(), () -> authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.TokenResponse> refresh(@RequestBody @Valid AuthDtos.RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) AuthDtos.LogoutRequest req) {
        authService.logout(req);
        return ResponseEntity.noContent().build();
    }
}

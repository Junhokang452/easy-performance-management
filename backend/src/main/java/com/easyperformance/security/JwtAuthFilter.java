/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.easyware.platform.TenantRoutingContext;
import com.easyware.platform.auth.JwtClaims;
import com.easyware.platform.auth.ParsedToken;
import com.easyware.platform.tenantctx.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT 인증 필터 — {@code Authorization: Bearer} 또는 {@code access_token} 쿠키에서 JWT 파싱 후
 * lib {@link TenantContext} + SecurityContext 설정. 요청 종료 시 반드시 clear (ThreadLocal 누수 방지).
 *
 * <p>BE-CC-2 lib 통합 (jobeval `4dff03a` + BE 17 v2 cutover 정합):
 * <ul>
 *   <li>JwtService 가 lib JwtTokenParser 위임 — parse 결과는 {@link ParsedToken} record.</li>
 *   <li>lib {@link TenantContext} 만 채움 (자체 TenantContext 부재 — performance 는 그린필드).</li>
 *   <li>토큰 부재/무효면 인증 미설정 상태로 통과 (permitAll 경로용 — 보호 경로는 시큐리티가 401).</li>
 *   <li>roles → {@code SimpleGrantedAuthority} <b>prefix 없음</b> — SUPER_ADMIN 가드는
 *       {@code hasAuthority("SUPER_ADMIN")} (SecurityConfig 정합).</li>
 * </ul>
 *
 * <p><strong>default-tenant 라우팅 게이트 (talent `8e29426` / recruit G149 패턴)</strong> —
 * {@code app.tenancy.default-tenant-id/-code} 설정 시 모든 요청을 해당 테넌트 DB 로 라우팅
 * ({@link TenantRoutingContext}). 미설정 시 no-op (단일 DB 모드 무영향). id 설정 + code 누락이면
 * 부팅 fail-fast (lib requireActive 가 code=null throw — 런타임 전면 장애 차단).
 *
 * <p><strong>ADR-031 정합</strong> — performance B2C 부재. B2B 단일 흐름만 지원.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TenantRoutingContext routingContext;
    private final UUID defaultTenantId;
    private final String defaultTenantCode;

    public JwtAuthFilter(JwtService jwtService, TenantRoutingContext routingContext,
                         String defaultTenantIdStr, String defaultTenantCode) {
        this.jwtService = jwtService;
        this.routingContext = routingContext;
        if (defaultTenantIdStr == null || defaultTenantIdStr.isBlank()) {
            this.defaultTenantId = null;
            this.defaultTenantCode = null;
        } else {
            if (defaultTenantCode == null || defaultTenantCode.isBlank()) {
                throw new IllegalStateException(
                    "app.tenancy.default-tenant-id 설정 시 default-tenant-code 필수");
            }
            this.defaultTenantId = UUID.fromString(defaultTenantIdStr.trim());
            this.defaultTenantCode = defaultTenantCode;
        }
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        boolean routingSet = false;
        if (defaultTenantId != null && routingContext != null) {
            routingContext.set(defaultTenantId, defaultTenantCode);
            routingSet = true;
        }
        try {
            String token = resolveToken(request);
            if (token != null) {
                try {
                    ParsedToken parsed = jwtService.parse(token);
                    UUID userId = parsed.subjectAsUuid().orElse(null);
                    if (userId != null) {
                        UUID tenantId = parsed.tenantId().orElse(null);
                        List<String> roles = extractRoles(parsed);

                        // lib TenantContext (BE 17 v2 cutover 자연 결합) — B2B 흐름만 (performance B2C 부재).
                        TenantContext.set(TenantContext.b2b(tenantId, userId));

                        List<SimpleGrantedAuthority> authorities = roles.isEmpty() ? List.of()
                            : roles.stream().map(SimpleGrantedAuthority::new).toList();
                        SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(userId, null, authorities));
                    }
                } catch (Exception ignored) {
                    // 무효/만료 토큰 → 인증 미설정 상태로 통과 (보호 경로는 EntryPoint 가 401)
                }
            }
            chain.doFilter(request, response);
        } finally {
            // default-tenant 라우팅 해제 (ThreadLocal 누수 방지).
            if (routingSet) {
                routingContext.clear();
            }
            // lib TenantContext ThreadLocal 해제 (누수 방지).
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(ParsedToken parsed) {
        Object raw = parsed.claims().get("roles");
        if (raw instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof String) {
            return (List<String>) raw;
        }
        // ROLE 단수 표준 claim 보강 (BE-CC-2 권장 기본값).
        Object single = parsed.claims().get(JwtClaims.ROLE);
        if (single instanceof String s) {
            return List.of(s);
        }
        return List.of();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}

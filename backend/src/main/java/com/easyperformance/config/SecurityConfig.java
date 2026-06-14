/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.easyperformance.security.JwtAuthFilter;
import com.easyperformance.security.JwtService;
import com.easyware.platform.TenantRoutingContext;
import com.easyware.platform.tenantctx.TenantContextFilter;
import com.easyware.platform.web.PlatformSecurityMatchers;

/**
 * Spring Security — 단계 3 BE-CC-2 JWT 5분리 + mono 표면 매처 (2026-06-12 격상).
 *
 * <p>stateless JWT (자매품 정렬). 세션 없음, CSRF off (httpOnly+SameSite 쿠키로 방어).
 *
 * <p><strong>mono 매처 정합 (store-hr `0c4a262` 패턴)</strong> — Render Docker 단일 컨테이너에서
 * Spring Boot 가 FE 정적 자원 + SPA 진입점을 함께 서빙:
 * <ul>
 *   <li>{@code /actuator/health,info,prometheus} — permitAll (모니터링).</li>
 *   <li>{@code /api/auth/**} — permitAll (로그인/refresh/logout).</li>
 *   <li>{@code /api/internal/**} — permitAll <b>보존</b> (P0-S6 S2S 수신 — Bearer+HMAC 자체 3중 가드,
 *       SyncReceiveController. JWT 불요).</li>
 *   <li>{@code /v3/api-docs/**} + swagger — permitAll (EC-FE openapi-typescript fetch).</li>
 *   <li>{@code /api/admin/**} — <b>SUPER_ADMIN 가드</b>. performance {@link JwtAuthFilter} 는 roles
 *       claim 을 prefix 없는 {@code SimpleGrantedAuthority} 로 매핑 (실측) →
 *       {@code hasAuthority("SUPER_ADMIN")} (store-hr 의 hasRole 과 의도적 차이).</li>
 *   <li>{@code /api/**} + {@code /actuator/**} — authenticated (인증 영역은 이 둘 뿐).</li>
 *   <li>{@code anyRequest().permitAll()} — 나머지는 정적 자원(/, /assets/** 등) + SPA 라우트
 *       (/login, /hr/**, /admin/** 등) → {@link SpaForwardingConfig} 가 index.html 폴백.</li>
 * </ul>
 *
 * <p><strong>BE 17 v2 cutover 자연 결합</strong> (lib {@code TenantContextFilter}, jobeval `18bc01f`):
 * {@link ObjectProvider} 패턴 — autoconfig OFF 시 lib 빈 null → 미등록 (no-op).
 *
 * <p><strong>default-tenant 라우팅 게이트</strong> (talent/recruit G149 패턴) —
 * {@code app.tenancy.default-tenant-id/-code} 설정 시 {@link JwtAuthFilter} 가 전 요청을 해당
 * 테넌트 DB 로 라우팅 ({@link TenantRoutingContext}). 미설정 시 no-op (단일 DB 모드 무영향).
 */
@Configuration
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ObjectProvider<TenantContextFilter> tenantContextFilterProvider,
                                            TenantRoutingContext tenantRoutingContext,
                                            @Value("${app.tenancy.default-tenant-id:}") String defaultTenantId,
                                            @Value("${app.tenancy.default-tenant-code:}") String defaultTenantCode)
            throws Exception {
        JwtAuthFilter jwtAuthFilter =
            new JwtAuthFilter(jwtService, tenantRoutingContext, defaultTenantId, defaultTenantCode);
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PlatformSecurityMatchers.ACTUATOR_HEALTH_INFO_PROMETHEUS_PUBLIC).permitAll()
                .requestMatchers(PlatformSecurityMatchers.API_AUTH_ALL_PUBLIC).permitAll()
                // S2S 수신 (P0-S6) — Bearer+HMAC 자체 인증 (SyncReceiveController 3중 가드). JWT 불요. 보존.
                .requestMatchers(PlatformSecurityMatchers.INTERNAL_S2S).permitAll()
                // OpenAPI spec endpoint (EC-FE-7) — FE openapi-typescript fetch.
                .requestMatchers(PlatformSecurityMatchers.SWAGGER_UI_HTML_API_DOCS).permitAll()
                // SystemAdmin (control plane tenants 콘솔) — SUPER_ADMIN 가드 (prefix 없는 authority 실측).
                .requestMatchers(PlatformSecurityMatchers.ADMIN_SUPER_ADMIN).hasAuthority("SUPER_ADMIN")
                // mono 표면 — 인증 영역 = /api/** + /actuator**(위 health 등 외) 만 (store-hr 0c4a262 정합).
                .requestMatchers(PlatformSecurityMatchers.API_AUTHENTICATED).authenticated()
                .requestMatchers(PlatformSecurityMatchers.ACTUATOR_ALL).authenticated()
                // 나머지 = 정적 자원(/, /index.html, /assets/**) + SPA 라우트 → SpaForwardingConfig 폴백.
                .anyRequest().permitAll())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // BE 17 v2 cutover (jobeval `18bc01f` 정합) — lib TenantContextFilter 를 JwtAuthFilter 다음에 체인.
        // autoconfig OFF (LIVE prod 기본) 또는 TenantContextResolver 부재 시 lib 빈 null → 미등록 (no-op).
        TenantContextFilter tenantContextFilter = tenantContextFilterProvider.getIfAvailable();
        if (tenantContextFilter != null) {
            http.addFilterAfter(tenantContextFilter, JwtAuthFilter.class);
        }
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

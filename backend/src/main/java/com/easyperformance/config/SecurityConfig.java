/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.config;

import org.springframework.beans.factory.ObjectProvider;
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
import com.easyware.platform.tenantctx.TenantContextFilter;

/**
 * Spring Security — 단계 3 BE-CC-2 JWT 5분리 (G84 D=A, Task #122, 2026-06-08).
 *
 * <p>stateless JWT (자매품 정렬). 세션 없음, CSRF off (httpOnly+SameSite 쿠키로 방어),
 * actuator health/info/prometheus + /api/auth/** 는 permitAll, 나머지는 인증 필요. {@link JwtAuthFilter} 선행.
 *
 * <p><strong>단계 3 진입 이전</strong> (단계 1 BE-CC-1): 모든 경로 permitAll 임시 가드.
 * <p><strong>단계 3 진입 이후</strong> (본 슬라이스): JWT filter chain — jobeval SecurityConfig 패턴 정합.
 *
 * <p><strong>BE 17 v2 cutover 자연 결합</strong> (lib `TenantContextFilter`, jobeval `18bc01f` 모범):
 * <ul>
 *   <li>autoconfig {@code easyplatform.tenantctx.enabled=true} (dev 진입) 활성 시 lib 가
 *       {@code TenantContextFilter} 빈 등록. 본 체인에 {@code JwtAuthFilter} 다음 순서로 체인.</li>
 *   <li>{@link ObjectProvider} 패턴 — autoconfig OFF 시 lib 빈 null. {@code getIfAvailable()} null 가드.</li>
 *   <li>JwtAuthFilter 가 이미 lib TenantContext 를 set/clear 하므로 v2 필터는 사실상 중복 — 그러나
 *       lib autoconfig 진입점·미래의 v3 (JwtAuthFilter 제거 후 lib 단독 위임) 표준 패턴 박제용으로 명시 체인.
 *       충돌 없음 ({@code OncePerRequestFilter} dedupe).</li>
 * </ul>
 *
 * <p><strong>ADR-031 정합</strong> — performance 는 B2B-Enterprise per-tenant + SMB Shared 본질.
 * B2C 부재 (도메인 본질 = 기업 성과 평가 워크플로우). {@code /api/b2c/**} 매처 불필요.
 */
@Configuration
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ObjectProvider<TenantContextFilter> tenantContextFilterProvider)
            throws Exception {
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtService);
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                // OpenAPI spec endpoint (EC-FE-7) — 단계 4 EC-FE 진입 시 FE openapi-typescript fetch.
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
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

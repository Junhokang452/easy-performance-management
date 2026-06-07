/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 단계 1 임시 보안 설정 — 모든 endpoint permitAll + CSRF off + stateless.
 *
 * <p>단계 3 BE-CC-2 JWT 5분리 진입 시 본 클래스를 JWT filter chain 으로 교체. lib BE 17 v2
 * TenantContextResolver 자연 결합.
 *
 * <p>{@code /api/internal/} prefix 는 단계 3 진입까지만 사용 — 단계 3 후 {@code /api/v1/} 로 전환.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.easyware.platform.auth.JwtAlgorithm;
import com.easyware.platform.auth.JwtClaims;
import com.easyware.platform.auth.JwtTokenIssuer;
import com.easyware.platform.auth.JwtTokenParser;
import com.easyware.platform.auth.ParsedToken;

/**
 * JwtService 단위 테스트 — 단계 3 BE-CC-2 JWT 5분리 (G84 D=A, Task #122, 2026-06-08).
 *
 * <p>access 토큰 발급 + parse + tenantId/roles claim 정합 회귀 가드.
 */
class JwtServiceTest {

    private static final String SECRET =
        "dev-only-performance-jwt-secret-please-rotate-in-prod-via-env-jwt-secret-min-64bytes-required-12345678";

    private JwtService jwtService;
    private JwtTokenParser parser;

    @BeforeEach
    void setUp() {
        JwtTokenIssuer issuer = new JwtTokenIssuer(SECRET, JwtAlgorithm.HS512);
        parser = new JwtTokenParser(SECRET);
        jwtService = new JwtService(issuer, parser, 300_000L, 604_800_000L);
    }

    @Test
    void issueAccessToken_setsTenantIdAndRoles() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<String> roles = List.of("USER");

        String token = jwtService.issueAccessToken(userId, tenantId, roles);
        assertThat(token).isNotBlank();

        ParsedToken parsed = jwtService.parse(token);
        assertThat(parsed.subjectAsUuid()).contains(userId);
        assertThat(parsed.tenantId()).contains(tenantId);
        assertThat(parsed.claims().get("roles")).isEqualTo(roles);
        assertThat(parsed.claims().get(JwtClaims.TYP)).isEqualTo(JwtClaims.TYP_ACCESS);
    }

    @Test
    void issueAccessToken_withNullTenantId_omitsTidClaim() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.issueAccessToken(userId, null, List.of("USER"));
        ParsedToken parsed = jwtService.parse(token);

        assertThat(parsed.subjectAsUuid()).contains(userId);
        assertThat(parsed.tenantId()).isEmpty();
    }

    @Test
    void parse_invalidToken_throws() {
        assertThatThrownBy(() -> jwtService.parse("garbage-not-a-jwt"))
            .isInstanceOf(Exception.class);
    }

    @Test
    void parse_tamperedSignature_throws() {
        String token = jwtService.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), List.of("USER"));
        // base64url 마지막 문자는 패딩 비트 때문에 바뀌어도 같은 바이트가 될 수 있다. 서명 첫 글자를 바꿔
        // 실제 서명 바이트가 달라지게 만든다.
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        String signature = parts[2];
        String tamperedSignature = (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1);
        String tampered = parts[0] + "." + parts[1] + "." + tamperedSignature;
        assertThatThrownBy(() -> jwtService.parse(tampered))
            .isInstanceOf(Exception.class);
    }

    @Test
    void ttlAccessors_returnConfigured() {
        assertThat(jwtService.accessTtlMs()).isEqualTo(300_000L);
        assertThat(jwtService.refreshTtlMs()).isEqualTo(604_800_000L);
    }
}

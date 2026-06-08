/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.security;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.easyware.platform.auth.JwtClaims;
import com.easyware.platform.auth.JwtTokenIssuer;
import com.easyware.platform.auth.JwtTokenParser;
import com.easyware.platform.auth.ParsedToken;

/**
 * JWT facade — BE-CC-2 lib (JwtTokenIssuer/JwtTokenParser) 위임 (jobeval `4dff03a` 정합).
 *
 * <p>HS512 (lib `JwtAlgorithm.HS512`) + 시크릿 검증 (lib `JwtSecretValidator`: 64+ bytes + prefix 차단).
 * Claim 표준: {@link JwtClaims#TID} (tenantId) + {@link JwtClaims#ROLE} 보강 + 본 자매품은 roles list 유지.
 *
 * <p><strong>단계 3 BE-CC-2 JWT 5분리 (G84 D=A, Task #122, 2026-06-08)</strong>
 * <ul>
 *   <li>Access Token — 5분, Authorization: Bearer (자매품 표준 정렬, jobeval 정합).</li>
 *   <li>Refresh Token — 7일, body (httpOnly 쿠키 격상 후보).</li>
 *   <li>ID Token — 본 단계 생략 (단계 3+ 격상 후보).</li>
 *   <li>Tenant Claim — TID (lib {@link JwtClaims#TID}).</li>
 *   <li>User Claim — subject = userId.</li>
 * </ul>
 *
 * <p>설정 키 (application.yml):
 * <ul>
 *   <li>{@code app.security.jwt.expiration} — Access TTL ms (기본 300000 = 5분).</li>
 *   <li>{@code app.security.jwt.refresh-expiration} — Refresh TTL ms (기본 604800000 = 7일).</li>
 * </ul>
 */
@Service
public class JwtService {

    private final JwtTokenIssuer issuer;
    private final JwtTokenParser parser;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public JwtService(
            JwtTokenIssuer issuer,
            JwtTokenParser parser,
            @Value("${app.security.jwt.expiration:300000}") long accessTtlMs,
            @Value("${app.security.jwt.refresh-expiration:604800000}") long refreshTtlMs) {
        this.issuer = issuer;
        this.parser = parser;
        this.accessTtlMs = accessTtlMs;
        this.refreshTtlMs = refreshTtlMs;
    }

    /** Access 토큰 발급 — subject=userId, tid=tenantId, roles=도메인 권한. */
    public String issueAccessToken(UUID userId, UUID tenantId, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        if (tenantId != null) {
            claims.put(JwtClaims.TID, tenantId.toString());
        }
        if (roles != null && !roles.isEmpty()) {
            claims.put("roles", roles);
        }
        return issuer.issueAccess(userId.toString(), claims, Duration.ofMillis(accessTtlMs));
    }

    /** 서명 검증 + claim 추출. */
    public ParsedToken parse(String token) {
        return parser.parse(token);
    }

    public long accessTtlMs() {
        return accessTtlMs;
    }

    public long refreshTtlMs() {
        return refreshTtlMs;
    }
}

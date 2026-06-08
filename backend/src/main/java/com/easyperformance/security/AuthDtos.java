/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.security;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 단계 3 BE-CC-2 JWT 5분리 — Auth DTO 가족 (G84 D=A, Task #122, 2026-06-08).
 *
 * <p>JWT 5분리 정의 (easy-standards 누적):
 * <ol>
 *   <li>Access Token (5분, Authorization: Bearer)</li>
 *   <li>Refresh Token (7일, httpOnly 쿠키 또는 body)</li>
 *   <li>ID Token (옵션 — 본 슬라이스 생략, 단계 3+ 격상 후보)</li>
 *   <li>Tenant Claim (tid — TenantContext 추출 사이트)</li>
 *   <li>User Claim (user_id — sub = userId, mra c632c5f 정합)</li>
 * </ol>
 *
 * <p>그린필드 dev 진입 — 본 단계는 사용자 엔티티/비밀번호 검증 미연동. dev-only stub login (이메일만으로
 * 신규 또는 기존 사용자 생성/조회). 단계 3+에서 사용자 엔티티 + bcrypt 검증으로 격상.
 *
 * <p><strong>모범 정합</strong> (3 자매품 누적):
 * <ul>
 *   <li>jobeval `4dff03a` Java 그린필드 모범 1호.</li>
 *   <li>mra `38e566d` Java dual-claim 비파괴 모범 2호.</li>
 *   <li>jobstructure `d64944e` Kotlin idiomatic 모범 3호.</li>
 *   <li>performance 본 슬라이스 = Java 그린필드 모범 4호 + ADR-031 B2B-Enterprise 본질.</li>
 * </ul>
 */
public final class AuthDtos {

    private AuthDtos() {}

    /** B2B Login 요청 (이메일 + 비밀번호) — dev stub 단계는 비밀번호 검증 생략. */
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 1, max = 128) String password) {}

    /**
     * Login 응답 — accessToken + refreshToken + 사용자 메타 (sub=userId / tid=tenantId / roles).
     *
     * <p>JWT 5분리 정합: accessToken (5분), refreshToken (7일, body — httpOnly 쿠키 격상 후보).
     */
    public record TokenResponse(
            String tokenType,
            String accessToken,
            long accessExpiresInSec,
            String refreshToken,
            long refreshExpiresInSec,
            UUID userId,
            UUID tenantId,
            List<String> roles) {

        public static TokenResponse of(String accessToken, long accessTtlSec,
                                       String refreshToken, long refreshTtlSec,
                                       UUID userId, UUID tenantId, List<String> roles) {
            return new TokenResponse("Bearer", accessToken, accessTtlSec,
                refreshToken, refreshTtlSec, userId, tenantId, roles);
        }
    }

    /** Refresh 요청 (refresh_token body) — httpOnly 쿠키 패턴 격상 후보. */
    public record RefreshRequest(@NotBlank String refreshToken) {}

    /** Logout 요청 (refresh_token body — 폐기). */
    public record LogoutRequest(@NotBlank String refreshToken) {}
}

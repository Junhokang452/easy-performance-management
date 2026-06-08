/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.error;

import com.easyware.platform.error.ErrorCodeContract;

/**
 * easy-performance-management 자매품 자체 ErrorCode (EC-3, 자매품 9호).
 *
 * <p>영역 prefix: <b>98 Performance (사전 진입)</b> — 성과 관리 도메인 (자기평가/개인 OKR/회고/멘토 피드백 + 인증).
 *
 * <p>표준 SoT: {@code easy-standards/90-conformance/error-code-domain-extension.md}.
 * 영역 97 (job-structure) 까지 확정, 98~99 예약 — performance 자매품 9호 진입에 따라 <b>98 사전 점유</b>.
 * 정식 박제는 표준 PR (영역 매트릭스 §2.1) — 본 슬라이스 (단계 3 BE-CC-2 JWT) 진입 박제용.
 *
 * <p>형식: {@code E98{HTTP3}{seq2}} — lib {@link com.easyware.platform.error.ErrorCodeContract}
 * 계약 구현. 동일 {@link com.easyware.platform.error.ApiException} 에 던질 수 있다.
 *
 * <p><strong>단계 3 BE-CC-2 JWT 5분리 (G84 D=A, Task #122, 2026-06-08)</strong>
 * <ul>
 *   <li>jobeval `4dff03a` 패턴 정합 (Java 그린필드 모범 1호).</li>
 *   <li>mra `38e566d` 패턴 정합 (dual-claim 비파괴 모범 2호).</li>
 *   <li>jobstructure `d64944e` 패턴 정합 (Kotlin idiomatic 모범 3호).</li>
 *   <li>performance 본 슬라이스 = Java 그린필드 모범 4호 + 격상 4단계 풀 완성 마일스톤 도달.</li>
 * </ul>
 */
public enum PerformanceErrorCode implements ErrorCodeContract {

    // B2B Auth — 단계 3 BE-CC-2 JWT 5분리 (G84 D=A, Task #122, 2026-06-08).
    // /api/auth/login + /api/auth/refresh + /api/auth/logout 흐름의 도메인 에러.
    // lib JwtTokenIssuer/JwtTokenParser 위임은 JwtService — 본 코드들은 비즈 흐름.
    AUTH_LOGIN_FAILED(               "E9804101", 401),
    AUTH_REFRESH_TOKEN_NOT_FOUND(    "E9804102", 401),
    AUTH_REFRESH_TOKEN_EXPIRED(      "E9804103", 401),
    AUTH_REFRESH_TOKEN_INVALID(      "E9804104", 401),
    AUTH_USER_NOT_FOUND(             "E9804415", 404),
    ;

    private final String code;
    private final int httpStatus;

    PerformanceErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String messageKey() {
        return "error." + code;
    }
}

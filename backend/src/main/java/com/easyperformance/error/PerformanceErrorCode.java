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

    // Cycle / Policy — P0-S1 EvaluationCycle + EvaluationPolicy (decisions_2026-06-11.md SoT).
    // EvaluationCycle 8단계 상태기계 + Policy 1:1 결합 + 분포/등급 검증 + 단계별 lock.
    CYCLE_NOT_FOUND(                 "E9804441", 404),
    CYCLE_INVALID_STATUS_TRANSITION( "E9804231", 422),
    CYCLE_DUPLICATE_NAME(            "E9804921", 409),
    CYCLE_INVALID_PERIOD(            "E9804232", 422),
    CYCLE_CANNOT_DELETE(             "E9804922", 409),
    POLICY_NOT_FOUND(                "E9804442", 404),
    POLICY_INVALID_DISTRIBUTION_SUM( "E9804233", 422),
    POLICY_INVALID_RATING_SCALE(     "E9804234", 422),
    POLICY_FORCED_REQUIRES_DISTRIBUTION("E9804235", 422),
    POLICY_LOCKED(                   "E9804923", 409),

    // KPI 도메인 — P0-S2 KpiTree / KpiNode / KpiAssignment / KpiActual (p0_s2_contract.md §3 SoT).
    // 가중치 정합(형제 합 ≤ 1.0 가드) + parent 같은 tree 무결성 + append-only supersede 체인 +
    // cycle FINALIZED/CANCELLED lock. 404 not-found 4 + 422 validation 4 + 409 conflict 4.
    KPI_TREE_NOT_FOUND(              "E9804443", 404),
    KPI_NODE_NOT_FOUND(             "E9804444", 404),
    KPI_ASSIGNMENT_NOT_FOUND(       "E9804445", 404),
    KPI_ACTUAL_NOT_FOUND(           "E9804446", 404),
    KPI_NODE_PARENT_TREE_MISMATCH(  "E9804236", 422),
    KPI_WEIGHT_OUT_OF_RANGE(        "E9804237", 422),
    KPI_WEIGHT_SUM_EXCEEDED(        "E9804238", 422),
    KPI_SOURCE_NOT_SUPPORTED(       "E9804239", 422),
    KPI_ASSIGNMENT_DUPLICATE(       "E9804924", 409),
    KPI_ACTUAL_ALREADY_SUPERSEDED(  "E9804925", 409),
    KPI_NODE_HAS_CHILDREN(          "E9804926", 409),
    KPI_CYCLE_LOCKED(               "E9804927", 409),

    // 성과 평가 도메인 — P0-S3 PerformanceReview (p0_s3_contract.md §4 SoT).
    // 10단계 상태기계 (P0-S3 전이 4 + submit 전용 2) + cycle 단계 게이트 + KPI 자동 점수 산출/동결 +
    // 섹션 PATCH 가드 + 제출 후 불변 lock. 404 not-found 1 + 422 validation 6 + 409 conflict 3.
    REVIEW_NOT_FOUND(                "E9804447", 404),
    REVIEW_INVALID_STATUS_TRANSITION("E9804240", 422),
    REVIEW_CYCLE_STAGE_MISMATCH(     "E9804241", 422),
    REVIEW_SCORE_OUT_OF_RANGE(       "E9804242", 422),
    REVIEW_ITEM_ASSIGNMENT_MISMATCH( "E9804243", 422),
    REVIEW_SCORE_INCOMPLETE(         "E9804244", 422),
    REVIEW_SECTION_NOT_EDITABLE(     "E9804245", 422),
    REVIEW_DUPLICATE(                "E9804928", 409),
    REVIEW_LOCKED(                   "E9804929", 409),
    REVIEW_CANNOT_DELETE(            "E9804930", 409),

    // 캘리브레이션 + 분포 도메인 — P0-S4 CalibrationSession / RatingDistribution (p0_s4_contract.md §4 SoT).
    // 5단계 세션 상태기계 (transition 2 + 자동 승격 1 + confirm 전용 2) + cycle 단계 게이트 (CALIBRATION 한정) +
    // 강제 분포 simulate/apply (HYBRID/FORCED 만, ABSOLUTE 거부 + S_A_B_C_D 한정 + largest remainder) +
    // 개별 등급 조정. 404 not-found 1 + 422 validation 6 + 409 conflict 3.
    // 기존 도메인 교차 재사용: REVIEW_NOT_FOUND(E9804447 404) + POLICY_NOT_FOUND(E9804442 404).
    CALIBRATION_SESSION_NOT_FOUND(           "E9804448", 404),
    CALIBRATION_INVALID_STATUS_TRANSITION(   "E9804246", 422),
    DISTRIBUTION_INVALID_TARGET(             "E9804247", 422),
    DISTRIBUTION_MODE_NOT_FORCED(            "E9804248", 422),
    DISTRIBUTION_SCALE_NOT_SUPPORTED(        "E9804249", 422),
    CALIBRATION_CYCLE_STAGE_MISMATCH(        "E9804250", 422),
    CALIBRATION_ADJUSTMENT_INVALID(          "E9804251", 422),
    CALIBRATION_SESSION_LOCKED(              "E9804931", 409),
    CALIBRATION_SESSION_CANNOT_DELETE(       "E9804932", 409),
    CALIBRATION_REVIEW_NOT_READY(            "E9804933", 409),

    // 성과 리포트 도메인 — P0-S5 PerformanceReport (p0_s5_contract.md §4 SoT).
    // append-only 발행 (publish 일괄 + supersede 체인) + content 동결 + view/acknowledge 멱등.
    // publish/supersede 는 cycle.status==FINALIZED 한정 + view/acknowledge/supersede 는 active 행 한정.
    // 404 not-found 1 + 422 validation 1 + 409 conflict 1.
    // 기존 도메인 교차 재사용: REVIEW_NOT_FOUND(E9804447 404) + CYCLE_NOT_FOUND(E9804441 404).
    REPORT_NOT_FOUND(                        "E9804449", 404),
    REPORT_CYCLE_NOT_FINALIZED(              "E9804252", 422),
    REPORT_NOT_ACTIVE(                       "E9804934", 409),

    // S2S 수신 도메인 — P0-S6 hcm core-master read-model (p0_s6_contract.md §5 SoT).
    // SyncReceiveController 3중 가드: bearer/secret 미설정 503 + bearer·HMAC 불일치 401 + 파싱 불가 400.
    // talent SYNC_* 동형 (E99 → E98 영역). census 충돌 0:
    //   401 영역은 101~104(auth login/refresh) 사용 중 → 105 빈자리 / 400 영역 0xx 미사용 → 005 / 5xx 첫 진입 → 5301.
    SYNC_NOT_CONFIGURED(                     "E9805301", 503),
    SYNC_AUTH_FAILED(                        "E9804105", 401),
    SYNC_INVALID_PAYLOAD(                    "E9804005", 400),
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

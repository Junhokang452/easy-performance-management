/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.calibration.entity;

/**
 * 캘리브레이션 세션 상태기계 5단계 — P0-S4 (p0_s4_contract.md §1/§3).
 *
 * <p>도메인 본질: 본부/HR 가 cycle 의 review 등급을 위원회 회의로 보정하는 세션 단위 — 일정 등록(PLANNED)
 * → 회의 시작(IN_SESSION) → 등급 조정 시 자동 승격(ADJUSTED) → 확정(CONFIRMED) → 종결(CLOSED).
 *
 * <p><strong>P0-S4 허용 전이</strong> (transition endpoint §3 매트릭스 + 자동 승격 1 + confirm 전용 2):
 * <pre>
 *   transition 으로 허용:
 *     PLANNED   → IN_SESSION   (cycle=CALIBRATION, 회의 시작)
 *     CONFIRMED → CLOSED       (cycle 무관, 종결)
 *   자동 승격 (명시 전이 불가):
 *     IN_SESSION → ADJUSTED    (adjustments API 첫 호출 시 서비스가 자동 승격)
 *   confirm 전용 endpoint (transition 으로 불가 — CALIBRATION_INVALID_STATUS_TRANSITION):
 *     IN_SESSION → CONFIRMED   (confirm)
 *     ADJUSTED   → CONFIRMED   (confirm)
 * </pre>
 *
 * <p>CONFIRMED/CLOSED 은 잠금 상태 — adjust/PATCH/confirm 재시도 시 CALIBRATION_SESSION_LOCKED 409.
 *
 * <p>DB CHECK 제약: {@code ck_calibration_session_status}.
 */
public enum CalibrationStatus {
    PLANNED,
    IN_SESSION,
    ADJUSTED,
    CONFIRMED,
    CLOSED
}

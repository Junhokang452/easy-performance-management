/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationcycle.entity;

/**
 * 평가 사이클 8단계 상태기계 — P0-S1 (decisions_2026-06-11.md G_PERF_E2).
 *
 * <p>전이 규칙 (EvaluationCycleService#validateStatusTransition):
 * <pre>
 *   PLANNED        → ACTIVE, CANCELLED
 *   ACTIVE         → GOAL_SETTING, CANCELLED
 *   GOAL_SETTING   → MID_REVIEW, CANCELLED
 *   MID_REVIEW     → SELF_REVIEW, CANCELLED
 *   SELF_REVIEW    → MANAGER_REVIEW, CANCELLED
 *   MANAGER_REVIEW → CALIBRATION, CANCELLED
 *   CALIBRATION    → FINALIZED, CANCELLED
 *   FINALIZED      → (종결)
 *   CANCELLED      → (종결)
 * </pre>
 *
 * <p>{@link #GOAL_SETTING} 진입 시 EvaluationPolicy 필수 (없으면 POLICY_NOT_FOUND).
 *
 * <p>HR Tech 시장 모범 (SuccessFactors / Workday / Lattice):
 * <ul>
 *   <li>목표 설정 (goal) → 중간 검토 (mid) → 자기 평가 (self) → 관리자 평가 (manager)
 *       → 캘리브레이션 (committee) → 확정 (FINALIZED, append-only)</li>
 * </ul>
 *
 * <p>DB CHECK 제약: {@code ck_cycle_status}.
 */
public enum CycleStatus {
    PLANNED,
    ACTIVE,
    GOAL_SETTING,
    MID_REVIEW,
    SELF_REVIEW,
    MANAGER_REVIEW,
    CALIBRATION,
    FINALIZED,
    CANCELLED
}

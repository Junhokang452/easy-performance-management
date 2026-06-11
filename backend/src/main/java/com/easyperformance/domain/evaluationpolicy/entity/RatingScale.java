/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationpolicy.entity;

/**
 * 등급 스케일 — P0-S1 (decisions_2026-06-11.md G_PERF_E4).
 *
 * <ul>
 *   <li>{@link #S_A_B_C_D} (기본) — 국내 대기업 모범 5단계.
 *       forcedDistribution 키는 {S,A,B,C,D} 부분집합만 허용.</li>
 *   <li>{@link #ONE_TO_FIVE} — 1~5 점수 스케일.</li>
 *   <li>{@link #ONE_TO_HUNDRED} — 0~100 점수 스케일 (정량 KPI 정합).</li>
 * </ul>
 *
 * <p>DB CHECK 제약: {@code ck_policy_rating_scale}.
 */
public enum RatingScale {
    S_A_B_C_D,
    ONE_TO_FIVE,
    ONE_TO_HUNDRED
}

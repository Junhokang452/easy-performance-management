/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationpolicy.entity;

/**
 * 평가 분포 모드 — P0-S1 (decisions_2026-06-11.md G_PERF_E3).
 *
 * <ul>
 *   <li>{@link #HYBRID} (기본) — 절대평가 + 상대조정. forcedDistribution 권장 (합 1.0 검증).</li>
 *   <li>{@link #FORCED} — 강제 분포. forcedDistribution 필수 (null/empty 거부 + 합 1.0 검증).</li>
 *   <li>{@link #ABSOLUTE} — 절대평가만. forcedDistribution 무시 (저장 시 null).</li>
 * </ul>
 *
 * <p>DB CHECK 제약: {@code ck_policy_distribution_mode}.
 */
public enum DistributionMode {
    HYBRID,
    FORCED,
    ABSOLUTE
}

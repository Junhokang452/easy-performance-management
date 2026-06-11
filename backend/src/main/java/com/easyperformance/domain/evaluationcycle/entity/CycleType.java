/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationcycle.entity;

/**
 * 평가 사이클 유형 — P0-S1 (decisions_2026-06-11.md G_PERF_E1).
 *
 * <p>국내 대기업/SMB 운영 모범:
 * <ul>
 *   <li>{@link #HALF_ANNUAL} 반기 (상/하반기) — HR Tech 시장 1순위 (Lattice/15Five 기본).</li>
 *   <li>{@link #ANNUAL} 연간 — 종합 평가/승진 심사 정합.</li>
 *   <li>{@link #QUARTERLY} 분기 — OKR 정합 (Google/Intel 모범).</li>
 *   <li>{@link #MONTHLY} 월간 — 짧은 사이클 운영 (스타트업/CFR 정합).</li>
 *   <li>{@link #CUSTOM} 임의 — period_start/period_end 자유 (특수 캠페인).</li>
 * </ul>
 *
 * <p>DB CHECK 제약: {@code ck_cycle_type}.
 */
public enum CycleType {
    HALF_ANNUAL,
    ANNUAL,
    QUARTERLY,
    MONTHLY,
    CUSTOM
}

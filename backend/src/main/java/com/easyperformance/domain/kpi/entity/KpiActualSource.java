/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.entity;

/**
 * KPI 실적 출처 — P0-S2 (p0_s2_contract.md §1 KpiActual).
 *
 * <p>P0 는 서버에서 {@link #MANUAL} 고정 — 클라이언트가 source 를 지정하지 않으며 service 가 강제.
 * {@link #AUTO}/{@link #IMPORT} 는 enum 박제만 (P1 진입 — 자동 실적 수집 / 배치 임포트).
 *
 * <ul>
 *   <li>{@link #MANUAL} 수동 입력 (P0 유일 — 서버 고정).</li>
 *   <li>{@link #AUTO} 자동 수집 (P1 — 외부 지표 연동).</li>
 *   <li>{@link #IMPORT} 배치 임포트 (P1 — CSV/Excel).</li>
 * </ul>
 *
 * <p>DB CHECK 제약: {@code ck_kpi_actual_source}.
 */
public enum KpiActualSource {
    MANUAL,
    AUTO,
    IMPORT
}

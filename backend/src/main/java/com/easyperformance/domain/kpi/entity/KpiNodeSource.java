/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.entity;

/**
 * KPI 노드 출처 — P0-S2 (p0_s2_contract.md §1 KpiNode, G_PERF_E2 단계적 A).
 *
 * <p>P0 는 {@link #MANUAL} 만 허용 — {@link #HCM}/{@link #EXTERNAL} 은 enum 박제만 (P1 진입).
 * MANUAL 외 source 로 노드 생성 시 KPI_SOURCE_NOT_SUPPORTED (E9804239) 거부.
 *
 * <ul>
 *   <li>{@link #MANUAL} 수동 입력 (P0 유일 지원).</li>
 *   <li>{@link #HCM} hcm Core Master S2S 동기화 (P1 — sourceConfig 매핑).</li>
 *   <li>{@link #EXTERNAL} 외부 시스템 연동 (P1 — sourceConfig 매핑).</li>
 * </ul>
 *
 * <p>DB CHECK 제약: {@code ck_kpi_node_source}.
 */
public enum KpiNodeSource {
    MANUAL,
    HCM,
    EXTERNAL
}

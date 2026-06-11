/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.entity;

/**
 * BSC (Balanced Scorecard) 4 관점 — P0-S2 (p0_s2_contract.md §1 KpiNode, G_PERF_E5).
 *
 * <p>Kaplan & Norton BSC 4 관점 — nullable (미지정 허용). cycle.bscEnabled + tree.bscEnabled 가
 * true 일 때 FE 가 관점별 컬럼 그룹핑 (본부 KPI Tree 화면 #18).
 *
 * <ul>
 *   <li>{@link #FINANCIAL} 재무 — 매출/이익/원가.</li>
 *   <li>{@link #CUSTOMER} 고객 — 만족도/점유율/유지율.</li>
 *   <li>{@link #INTERNAL_PROCESS} 내부 프로세스 — 품질/효율/혁신.</li>
 *   <li>{@link #LEARNING_GROWTH} 학습과 성장 — 역량/조직문화.</li>
 * </ul>
 *
 * <p>DB CHECK 제약: {@code ck_kpi_node_bsc_perspective} (NULL 허용).
 */
public enum BscPerspective {
    FINANCIAL,
    CUSTOMER,
    INTERNAL_PROCESS,
    LEARNING_GROWTH
}

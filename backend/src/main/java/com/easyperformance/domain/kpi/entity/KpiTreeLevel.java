/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.entity;

/**
 * KPI 트리 레벨 — P0-S2 (p0_s2_contract.md §1 KpiTree).
 *
 * <p>HR Tech KPI cascading 모범 (SuccessFactors / Workday goal management):
 * <ul>
 *   <li>{@link #CORPORATE} 전사 — 최상위 BSC 목표 (CEO/임원).</li>
 *   <li>{@link #DIVISION} 본부 — 부서 단위 cascade (본부장).</li>
 *   <li>{@link #TEAM} 팀 — 팀 단위 cascade (팀장).</li>
 *   <li>{@link #INDIVIDUAL} 개인 — 개인 KPI assignment 대상 (팀원).</li>
 * </ul>
 *
 * <p>DB CHECK 제약: {@code ck_kpi_tree_level}.
 */
public enum KpiTreeLevel {
    CORPORATE,
    DIVISION,
    TEAM,
    INDIVIDUAL
}

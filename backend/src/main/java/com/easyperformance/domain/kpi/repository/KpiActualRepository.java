/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.repository;

import com.easyperformance.domain.kpi.entity.KpiActual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * KpiActual Repository — append-only. ADR-026 명명 정합. assignment 별 실적 이력 + supersede 검사.
 *
 * <p>UPDATE/DELETE 메서드 미제공 (append-only — 정정은 supersede 신규 row 전용). easy-ware 규칙 #10.
 */
@Repository
public interface KpiActualRepository extends JpaRepository<KpiActual, UUID> {

    Optional<KpiActual> findByIdAndTenantId(UUID id, UUID tenantId);

    /** assignment 의 실적 이력 — asOfDate DESC. */
    List<KpiActual> findAllByTenantIdAndKpiAssignmentIdOrderByAsOfDateDescCreatedAtDesc(
        UUID tenantId, UUID kpiAssignmentId);

    /** supersede 안 된 실적만 (latestActual 계산용) — asOfDate DESC, createdAt DESC. */
    List<KpiActual> findAllByTenantIdAndKpiAssignmentIdAndSupersedesIdIsNullOrderByAsOfDateDescCreatedAtDesc(
        UUID tenantId, UUID kpiAssignmentId);

    /** 이미 supersede 된 원본인지 검사 (재정정 차단). */
    boolean existsByTenantIdAndSupersedesId(UUID tenantId, UUID supersedesId);
}

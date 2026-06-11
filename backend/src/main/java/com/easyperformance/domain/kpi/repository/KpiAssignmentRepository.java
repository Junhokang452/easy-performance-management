/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.repository;

import com.easyperformance.domain.kpi.entity.KpiAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * KpiAssignment Repository — ADR-026 명명 정합. node 별 배정 + 중복 검사 + my KPI (cycle×employee) 조회.
 *
 * <p>easy-ware 규칙 #10 — 모든 변형에 tenant_id 명시. my KPI 는 node→tree join 으로 cycle 필터
 * (JPQL named param 안전 패턴).
 */
@Repository
public interface KpiAssignmentRepository extends JpaRepository<KpiAssignment, UUID> {

    Optional<KpiAssignment> findByIdAndTenantId(UUID id, UUID tenantId);

    List<KpiAssignment> findAllByTenantIdAndKpiNodeIdOrderByCreatedAtAsc(UUID tenantId, UUID kpiNodeId);

    /** node × employee 중복 검사. */
    boolean existsByTenantIdAndKpiNodeIdAndEmployeeId(UUID tenantId, UUID kpiNodeId, UUID employeeId);

    /**
     * my KPI — 특정 cycle 의 특정 employee 배정 전부. node→tree join 으로 cycle 필터.
     * tree.cycleId 가 cycle 식별자 (assignment 자체엔 cycle 컬럼 없음).
     */
    @Query("""
        SELECT a FROM KpiAssignment a, KpiNode n, KpiTree t
        WHERE a.tenantId = :tenantId
          AND a.employeeId = :employeeId
          AND n.id = a.kpiNodeId
          AND n.tenantId = :tenantId
          AND t.id = n.treeId
          AND t.tenantId = :tenantId
          AND t.cycleId = :cycleId
        ORDER BY a.createdAt ASC
        """)
    List<KpiAssignment> findMyAssignments(@Param("tenantId") UUID tenantId,
                                          @Param("cycleId") UUID cycleId,
                                          @Param("employeeId") UUID employeeId);
}

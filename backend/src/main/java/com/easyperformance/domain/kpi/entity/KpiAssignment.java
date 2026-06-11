/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.entity;

import com.easyperformance.common.UuidV7;
import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * KPI 배정 (KpiAssignment) — P0-S2 (p0_s2_contract.md §1).
 *
 * <p>도메인 본질: KpiNode 1개를 특정 직원(employee)에게 배정 — 개인 KPI 단위. node 당 직원당 1개
 * ({@code (tenant_id, kpi_node_id, employee_id)} UNIQUE — 위반 KPI_ASSIGNMENT_DUPLICATE 409).
 *
 * <p>{@code weight}/{@code target_override} 는 개인 override — null 이면 node 값 승계 (effectiveWeight/
 * effectiveTarget 파생 계산은 service). {@code employee_id} 는 rm_employee 수신(P0-S6) 전 plain UUID.
 *
 * <p>{@code kpi_node_id → kpi_node(id) ON DELETE CASCADE}.
 *
 * <p>인덱스 — easy-ware 규칙 #2: {@code (tenant_id, employee_id)} (my KPI 조회).
 */
@Entity
@Table(
    name = "kpi_assignment",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_kpi_assignment_tenant_node_employee",
            columnNames = {"tenant_id", "kpi_node_id", "employee_id"})
    },
    indexes = {
        @Index(name = "ix_kpi_assignment_tenant_employee",
            columnList = "tenant_id, employee_id")
    }
)
public class KpiAssignment extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /** FK → kpi_node(id) ON DELETE CASCADE. */
    @Column(name = "kpi_node_id", columnDefinition = "uuid", nullable = false)
    private UUID kpiNodeId;

    /** 배정 대상 직원 (rm_employee P0-S6 수신 전 — FK 없는 plain UUID). */
    @Column(name = "employee_id", columnDefinition = "uuid", nullable = false)
    private UUID employeeId;

    /** 개인 가중치 override — numeric(5,4), (0,1] (CHECK + service). null 이면 node.weight 승계. */
    @Column(name = "weight", precision = 5, scale = 4)
    private BigDecimal weight;

    /** 개인 목표 override — numeric(18,4). null 이면 node.target 승계. */
    @Column(name = "target_override", precision = 18, scale = 4)
    private BigDecimal targetOverride;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getKpiNodeId() { return kpiNodeId; }
    public void setKpiNodeId(UUID kpiNodeId) { this.kpiNodeId = kpiNodeId; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

    public BigDecimal getTargetOverride() { return targetOverride; }
    public void setTargetOverride(BigDecimal targetOverride) { this.targetOverride = targetOverride; }
}

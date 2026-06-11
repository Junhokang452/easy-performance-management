/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.calibration.entity;

import com.easyperformance.common.UuidV7;
import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 등급 분포 (RatingDistribution) — P0-S4 (p0_s4_contract.md §1).
 *
 * <p>도메인 본질: cycle (× orgUnit) 의 마지막 강제 배분 적용 결과 + 시뮬레이션 이력. {@code apply} 시점에만
 * upsert (simulate 는 무저장 — §0-1). P0-S4 는 전사 (org_unit_id NULL) 행만 생성 (org 단위는 P0-S6 후).
 *
 * <p>UNIQUE (§0-3 partial unique 2개 — talent 패턴, DDL 에서 정의):
 * <ul>
 *   <li>{@code (tenant_id, cycle_id) WHERE org_unit_id IS NULL} — 전사 1행.</li>
 *   <li>{@code (tenant_id, cycle_id, org_unit_id) WHERE org_unit_id IS NOT NULL} — org 단위 1행.</li>
 * </ul>
 *
 * <p>{@code cycle_id → evaluation_cycle(id) ON DELETE CASCADE}.
 *
 * <p>JSONB 매핑 (P0-S1 D2 패턴):
 * <ul>
 *   <li>{@code policy_distribution} — 마지막 apply target {@code {S:0.1,A:0.25,...}} (비율).</li>
 *   <li>{@code actual_distribution} — 마지막 apply 결과 분포 {@code {S:5,A:12,...}} (건수).</li>
 *   <li>{@code simulation_log} — append 배열, entry =
 *       {@code {at, actorEmployeeId, targetDistribution, appliedCount, skippedCount, resultingDistribution}}.</li>
 * </ul>
 */
@Entity
@Table(name = "rating_distribution")
public class RatingDistribution extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /** FK → evaluation_cycle(id) ON DELETE CASCADE. */
    @Column(name = "cycle_id", columnDefinition = "uuid", nullable = false)
    private UUID cycleId;

    /** 조직 단위 (rm_org_unit P0-S6 수신 전 — FK 없는 plain UUID). NULL = 전사 (P0-S4 는 NULL 행만). */
    @Column(name = "org_unit_id", columnDefinition = "uuid")
    private UUID orgUnitId;

    /** 마지막 apply 에 사용된 target 분포 (비율 {S:0.1,...}, jsonb). nullable. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_distribution", columnDefinition = "jsonb")
    private String policyDistribution;

    /** 마지막 apply 결과 분포 (건수 {S:5,...}, jsonb). nullable. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actual_distribution", columnDefinition = "jsonb")
    private String actualDistribution;

    /** 강제 분포 적용 여부 — apply 시 true. */
    @Column(name = "forced_applied", nullable = false)
    private boolean forcedApplied;

    /** 마지막 apply 시각. nullable. */
    @Column(name = "applied_at")
    private Instant appliedAt;

    /** 마지막 apply 수행 직원. nullable. */
    @Column(name = "applied_by", columnDefinition = "uuid")
    private UUID appliedBy;

    /** 시뮬레이션/적용 이력 append 배열 (jsonb). apply 마다 append. nullable. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "simulation_log", columnDefinition = "jsonb")
    private String simulationLog;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCycleId() { return cycleId; }
    public void setCycleId(UUID cycleId) { this.cycleId = cycleId; }

    public UUID getOrgUnitId() { return orgUnitId; }
    public void setOrgUnitId(UUID orgUnitId) { this.orgUnitId = orgUnitId; }

    public String getPolicyDistribution() { return policyDistribution; }
    public void setPolicyDistribution(String policyDistribution) { this.policyDistribution = policyDistribution; }

    public String getActualDistribution() { return actualDistribution; }
    public void setActualDistribution(String actualDistribution) { this.actualDistribution = actualDistribution; }

    public boolean isForcedApplied() { return forcedApplied; }
    public void setForcedApplied(boolean forcedApplied) { this.forcedApplied = forcedApplied; }

    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }

    public UUID getAppliedBy() { return appliedBy; }
    public void setAppliedBy(UUID appliedBy) { this.appliedBy = appliedBy; }

    public String getSimulationLog() { return simulationLog; }
    public void setSimulationLog(String simulationLog) { this.simulationLog = simulationLog; }
}

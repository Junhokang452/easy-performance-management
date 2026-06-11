/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.entity;

import com.easyperformance.common.UuidV7;
import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * KPI 트리 (KpiTree) — P0-S2 (p0_s2_contract.md §1).
 *
 * <p>도메인 본질: 한 EvaluationCycle 안에서 조직 레벨별 KPI 계층의 컨테이너 — 전사/본부/팀/개인
 * ({@link KpiTreeLevel}) cascading 단위. KpiNode 들이 본 트리에 종속 (FK ON DELETE CASCADE).
 *
 * <p>{@code cycle_id → evaluation_cycle(id) ON DELETE CASCADE}. cycle 이 FINALIZED/CANCELLED 면
 * 본 트리·노드·assignment·actual 일체 쓰기 거부 (KPI_CYCLE_LOCKED 409 — service 가 cycle.status 해석).
 *
 * <p>{@code owner_org_unit_id} 는 rm_org_unit 수신(P0-S6) 전이라 FK 없는 plain UUID.
 *
 * <p>인덱스 — easy-ware 규칙 #2 (tenant_id 선두 복합): {@code (tenant_id, cycle_id, owner_org_unit_id)}.
 */
@Entity
@Table(
    name = "kpi_tree",
    indexes = {
        @Index(name = "ix_kpi_tree_tenant_cycle_owner",
            columnList = "tenant_id, cycle_id, owner_org_unit_id")
    }
)
public class KpiTree extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /** FK → evaluation_cycle(id) ON DELETE CASCADE. */
    @Column(name = "cycle_id", columnDefinition = "uuid", nullable = false)
    private UUID cycleId;

    /** 소유 조직 단위 (rm_org_unit P0-S6 수신 전 — FK 없는 plain UUID). nullable. */
    @Column(name = "owner_org_unit_id", columnDefinition = "uuid")
    private UUID ownerOrgUnitId;

    /** 트리 이름 (예: "2026 상반기 전사 KPI"). */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /** 조직 레벨 (전사/본부/팀/개인). */
    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 20, nullable = false)
    private KpiTreeLevel level;

    /** BSC (Balanced Scorecard) 적용 여부 — FE 본부 KPI Tree 토글 기본값. */
    @Column(name = "bsc_enabled", nullable = false)
    private boolean bscEnabled;

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

    public UUID getOwnerOrgUnitId() { return ownerOrgUnitId; }
    public void setOwnerOrgUnitId(UUID ownerOrgUnitId) { this.ownerOrgUnitId = ownerOrgUnitId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public KpiTreeLevel getLevel() { return level; }
    public void setLevel(KpiTreeLevel level) { this.level = level; }

    public boolean isBscEnabled() { return bscEnabled; }
    public void setBscEnabled(boolean bscEnabled) { this.bscEnabled = bscEnabled; }
}

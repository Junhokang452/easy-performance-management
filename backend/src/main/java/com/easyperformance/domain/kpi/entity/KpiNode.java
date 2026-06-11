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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * KPI 노드 (KpiNode) — P0-S2 (p0_s2_contract.md §1).
 *
 * <p>도메인 본질: KpiTree 안의 단일 KPI 항목 — self-referencing parent_id 로 트리 구조 형성. 가중치
 * (weight) numeric(5,4) — 형제(같은 parent) 합 ≤ 1.0 가드 (KPI_WEIGHT_SUM_EXCEEDED). parent 는
 * 반드시 같은 tree 소속 (KPI_NODE_PARENT_TREE_MISMATCH — service 검증).
 *
 * <p>{@code tree_id → kpi_tree(id) ON DELETE CASCADE}. {@code parent_id → kpi_node(id)} (self, nullable).
 *
 * <p>P0 는 {@code source = MANUAL} 만 허용 ({@link KpiNodeSource}). {@code source_config} jsonb 는
 * DDL 박제 + entity 매핑 유지 (P0-S1 D2 패턴 — lib OutboxEvent JSON 매핑 정합, Hibernate validate
 * 모드 컬럼 정합), 단 P0 미사용 (항상 null). {@code cascade_from_id} 는 상위 트리 KPI 참조 (cross-tree,
 * FK 없음 — rm 미수신·트리 간 정합은 P1).
 *
 * <p>인덱스 — easy-ware 규칙 #2: {@code ix_kpi_node_tenant_tree_parent (tenant_id, tree_id, parent_id)}.
 */
@Entity
@Table(
    name = "kpi_node",
    indexes = {
        @Index(name = "ix_kpi_node_tenant_tree_parent",
            columnList = "tenant_id, tree_id, parent_id")
    }
)
public class KpiNode extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /** FK → kpi_tree(id) ON DELETE CASCADE. */
    @Column(name = "tree_id", columnDefinition = "uuid", nullable = false)
    private UUID treeId;

    /** self FK → kpi_node(id). null 이면 루트 노드. parent 는 같은 tree 소속 (service 검증). */
    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    /** KPI 항목 라벨. */
    @Column(name = "label", length = 200, nullable = false)
    private String label;

    /** 가중치 — numeric(5,4), (0,1] (CHECK + service). 형제 합 ≤ 1.0 가드. */
    @Column(name = "weight", precision = 5, scale = 4, nullable = false)
    private BigDecimal weight;

    /** 목표값 — numeric(18,4). nullable (정성 KPI 등). */
    @Column(name = "target", precision = 18, scale = 4)
    private BigDecimal target;

    /** 단위 (예: "%", "건", "원"). nullable. */
    @Column(name = "unit", length = 20)
    private String unit;

    /** BSC 관점 — nullable (미지정). */
    @Enumerated(EnumType.STRING)
    @Column(name = "bsc_perspective", length = 20)
    private BscPerspective bscPerspective;

    /** 출처 — P0 는 MANUAL 만. 기본 MANUAL. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20, nullable = false)
    private KpiNodeSource source;

    /**
     * 출처 설정 JSON (P1 박제 — P0 미사용, 항상 null). lib OutboxEvent JSON 매핑 정합.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_config", columnDefinition = "jsonb")
    private String sourceConfig;

    /** 상위 트리 KPI 참조 (cross-tree cascade, FK 없음 — P1 정합). nullable. */
    @Column(name = "cascade_from_id", columnDefinition = "uuid")
    private UUID cascadeFromId;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
        if (this.source == null) {
            this.source = KpiNodeSource.MANUAL;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTreeId() { return treeId; }
    public void setTreeId(UUID treeId) { this.treeId = treeId; }

    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

    public BigDecimal getTarget() { return target; }
    public void setTarget(BigDecimal target) { this.target = target; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BscPerspective getBscPerspective() { return bscPerspective; }
    public void setBscPerspective(BscPerspective bscPerspective) { this.bscPerspective = bscPerspective; }

    public KpiNodeSource getSource() { return source; }
    public void setSource(KpiNodeSource source) { this.source = source; }

    public String getSourceConfig() { return sourceConfig; }
    public void setSourceConfig(String sourceConfig) { this.sourceConfig = sourceConfig; }

    public UUID getCascadeFromId() { return cascadeFromId; }
    public void setCascadeFromId(UUID cascadeFromId) { this.cascadeFromId = cascadeFromId; }
}

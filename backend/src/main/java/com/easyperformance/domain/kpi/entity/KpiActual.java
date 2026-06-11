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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * KPI 실적 (KpiActual) — append-only — P0-S2 (p0_s2_contract.md §1).
 *
 * <p>도메인 본질: KpiAssignment 의 실적 기록 — talent ReviewDecision 패턴 정합 (append-only +
 * supersedes 체인). <b>UPDATE/DELETE 경로 없음</b> — 정정은 supersede 신규 row 전용 ({@code supersedes_id}
 * 가 원본 가리킴). {@code supersedes_id} 는 UNIQUE (한 row 는 최대 1번만 supersede 가능 — 이미 supersede
 * 된 row 재정정은 KPI_ACTUAL_ALREADY_SUPERSEDED 409).
 *
 * <p>{@code kpi_assignment_id → kpi_assignment(id) ON DELETE CASCADE}. {@code supersedes_id → kpi_actual(id)}.
 *
 * <p>P0 는 {@code source = MANUAL} 서버 고정 ({@link KpiActualSource}). latestActual 파생 = supersede
 * 안 된 row 중 max(asOfDate, createdAt) — service 계산.
 *
 * <p>인덱스 — easy-ware 규칙 #2: {@code (tenant_id, kpi_assignment_id, as_of_date DESC)}.
 */
@Entity
@Table(
    name = "kpi_actual",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_kpi_actual_supersedes", columnNames = {"supersedes_id"})
    },
    indexes = {
        @Index(name = "ix_kpi_actual_tenant_assignment_asof",
            columnList = "tenant_id, kpi_assignment_id, as_of_date")
    }
)
public class KpiActual extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /** FK → kpi_assignment(id) ON DELETE CASCADE. */
    @Column(name = "kpi_assignment_id", columnDefinition = "uuid", nullable = false)
    private UUID kpiAssignmentId;

    /** 실적 기준일. */
    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    /** 실적값 — numeric(18,4). */
    @Column(name = "actual_value", precision = 18, scale = 4, nullable = false)
    private BigDecimal actualValue;

    /** 출처 — P0 는 MANUAL 서버 고정. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20, nullable = false)
    private KpiActualSource source;

    /** 보고자 (rm_employee P0-S6 수신 전 — FK 없는 plain UUID). nullable. */
    @Column(name = "reported_by", columnDefinition = "uuid")
    private UUID reportedBy;

    /** 증빙 URL. nullable. */
    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    /** 비고. nullable. */
    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    /**
     * supersede 대상 원본 actual id — null 이면 최초 기록. UNIQUE (원본 1회만 supersede).
     * FK → kpi_actual(id).
     */
    @Column(name = "supersedes_id", columnDefinition = "uuid")
    private UUID supersedesId;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
        if (this.source == null) {
            this.source = KpiActualSource.MANUAL;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getKpiAssignmentId() { return kpiAssignmentId; }
    public void setKpiAssignmentId(UUID kpiAssignmentId) { this.kpiAssignmentId = kpiAssignmentId; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal actualValue) { this.actualValue = actualValue; }

    public KpiActualSource getSource() { return source; }
    public void setSource(KpiActualSource source) { this.source = source; }

    public UUID getReportedBy() { return reportedBy; }
    public void setReportedBy(UUID reportedBy) { this.reportedBy = reportedBy; }

    public String getEvidenceUrl() { return evidenceUrl; }
    public void setEvidenceUrl(String evidenceUrl) { this.evidenceUrl = evidenceUrl; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public UUID getSupersedesId() { return supersedesId; }
    public void setSupersedesId(UUID supersedesId) { this.supersedesId = supersedesId; }
}

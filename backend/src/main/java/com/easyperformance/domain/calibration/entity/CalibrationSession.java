/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.calibration.entity;

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

import java.time.Instant;
import java.util.UUID;

/**
 * 캘리브레이션 세션 (CalibrationSession) — P0-S4 (p0_s4_contract.md §1).
 *
 * <p>도메인 본질: cycle 의 등급 보정 위원회 회의 단위. cycle 당 다중 세션 허용 (UNIQUE 없음 — §0-3). 5단계
 * 상태기계 ({@link CalibrationStatus}). {@code owner_org_unit_id} 는 P0-S6 rm_org_unit 수신 전 plain
 * UUID (P0-S4 는 전사/임의 단위만).
 *
 * <p>{@code cycle_id → evaluation_cycle(id) ON DELETE CASCADE}.
 *
 * <p>JSONB 매핑 (P0-S1 D2 패턴 — {@code @JdbcTypeCode(JSON)} + {@code String} + service ObjectMapper
 * {@code USE_BIG_DECIMAL_FOR_FLOATS}):
 * <ul>
 *   <li>{@code participant_ids} — 위원 직원 UUID 배열 (생성/PATCH 입력).</li>
 *   <li>{@code adjustment_log} — append 배열, entry =
 *       {@code {at, actorEmployeeId, reviewId, employeeId, fromGrade, toGrade, reason}}. adjustments API
 *       호출마다 append (이력 보존).</li>
 * </ul>
 *
 * <p>인덱스 — easy-ware 규칙 #2 (tenant_id 선두 복합): {@code (tenant_id, cycle_id, owner_org_unit_id)}.
 */
@Entity
@Table(
    name = "calibration_session",
    indexes = {
        @Index(name = "ix_calibration_session_tenant_cycle_owner",
            columnList = "tenant_id, cycle_id, owner_org_unit_id")
    }
)
public class CalibrationSession extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /** FK → evaluation_cycle(id) ON DELETE CASCADE. */
    @Column(name = "cycle_id", columnDefinition = "uuid", nullable = false)
    private UUID cycleId;

    /** 세션 담당 조직 단위 (rm_org_unit P0-S6 수신 전 — FK 없는 plain UUID). nullable = 전사/임의. */
    @Column(name = "owner_org_unit_id", columnDefinition = "uuid")
    private UUID ownerOrgUnitId;

    /** 5단계 상태 머신 — 기본 PLANNED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private CalibrationStatus status;

    /** 회의 예정 시각. nullable. */
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    /** 위원 직원 UUID 배열 (jsonb). null/빈 배열 허용. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "participant_ids", columnDefinition = "jsonb")
    private String participantIds;

    /** 등급 조정 이력 append 배열 (jsonb). adjustments API 호출마다 append. submit-manager 전 null. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "adjustment_log", columnDefinition = "jsonb")
    private String adjustmentLog;

    /** 확정 시각 — confirm 시 now(). nullable. */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** 확정 수행 직원 — confirm 시 actorEmployeeId. nullable. */
    @Column(name = "confirmed_by", columnDefinition = "uuid")
    private UUID confirmedBy;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
        if (this.status == null) {
            this.status = CalibrationStatus.PLANNED;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCycleId() { return cycleId; }
    public void setCycleId(UUID cycleId) { this.cycleId = cycleId; }

    public UUID getOwnerOrgUnitId() { return ownerOrgUnitId; }
    public void setOwnerOrgUnitId(UUID ownerOrgUnitId) { this.ownerOrgUnitId = ownerOrgUnitId; }

    public CalibrationStatus getStatus() { return status; }
    public void setStatus(CalibrationStatus status) { this.status = status; }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getParticipantIds() { return participantIds; }
    public void setParticipantIds(String participantIds) { this.participantIds = participantIds; }

    public String getAdjustmentLog() { return adjustmentLog; }
    public void setAdjustmentLog(String adjustmentLog) { this.adjustmentLog = adjustmentLog; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public UUID getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(UUID confirmedBy) { this.confirmedBy = confirmedBy; }
}

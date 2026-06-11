/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationcycle.entity;

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

import java.time.LocalDate;
import java.util.UUID;

/**
 * 평가 사이클 (EvaluationCycle) — P0-S1 (decisions_2026-06-11.md SoT).
 *
 * <p>도메인 본질: HR Tech Performance Management 의 최상위 컨테이너 — SelfEvaluation / PersonalOkr /
 * ReflectionJournal / MentorFeedback 4 도메인은 모두 본 사이클 1개에 종속. 평가 운영의 lifecycle 단위
 * (반기/연간/분기) 정의 + 8단계 상태기계 ({@link CycleStatus}) + Policy 1:1 결합.
 *
 * <p>{@code (tenant_id, name)} 유니크 — 동일 테넌트 내 사이클 이름 중복 차단 (CYCLE_DUPLICATE_NAME 409).
 * {@code period_end >= period_start} DB CHECK + service 사전 검증 (CYCLE_INVALID_PERIOD 422).
 * status ACTIVE 이상 (PLANNED 외) 에서 delete 거부 (CYCLE_CANNOT_DELETE 409).
 *
 * <p>인덱스 — easy-ware 규칙 #2 정합 (tenant_id 선두 복합 인덱스 필수): {@code (tenant_id, status)},
 * {@code (tenant_id, period_start, period_end)}.
 *
 * <p>상태 머신: PLANNED → ACTIVE → GOAL_SETTING → MID_REVIEW → SELF_REVIEW → MANAGER_REVIEW →
 * CALIBRATION → FINALIZED (+ CANCELLED 분기). 전이 검증은 service 계층 {@code validateStatusTransition}.
 * GOAL_SETTING 진입 시 EvaluationPolicy 가 cycle 에 존재해야 함.
 */
@Entity
@Table(
    name = "evaluation_cycle",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_cycle_tenant_name", columnNames = {"tenant_id", "name"})
    },
    indexes = {
        @Index(name = "ix_cycle_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "ix_cycle_tenant_period", columnList = "tenant_id, period_start, period_end")
    }
)
public class EvaluationCycle extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /** 사이클 이름 (예: "2026 상반기 평가"). 동일 테넌트 내 유니크. */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /** 평가 기간 시작일. */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** 평가 기간 종료일. {@code >= periodStart} (DB CHECK + service 검증). */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 사이클 유형 (반기/연간/분기/월간/임의). */
    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", length = 20, nullable = false)
    private CycleType cycleType;

    /** 8단계 상태 머신 — 기본 PLANNED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private CycleStatus status;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
        if (this.status == null) {
            this.status = CycleStatus.PLANNED;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public CycleType getCycleType() { return cycleType; }
    public void setCycleType(CycleType cycleType) { this.cycleType = cycleType; }

    public CycleStatus getStatus() { return status; }
    public void setStatus(CycleStatus status) { this.status = status; }
}

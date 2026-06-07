/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.personalokr.entity;

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

import java.time.LocalDate;
import java.util.UUID;

/**
 * 개인 OKR (Personal OKR) — Objective + 진척률.
 *
 * <p>단계 1 = 단순화 (Objective + 단일 진척률 0~100). 단계 4 진입 시 KeyResult 1:N 분리 + 회사→팀→개인
 * 3 계층 alignment.
 *
 * <p>본 슬라이스 범위 = B2B per-tenant. 단계 5 = B2C 개인 OKR 분기 (RLS user_id 격리).
 *
 * <p>인덱스 — easy-ware 규칙 #2 정합: {@code (tenant_id, employee_id)}, {@code (tenant_id, status)},
 * {@code (tenant_id, period_end)} (기간 조회 패턴).
 */
@Entity
@Table(
    name = "personal_okr",
    indexes = {
        @Index(name = "ix_personal_okr_tenant_employee", columnList = "tenant_id, employee_id"),
        @Index(name = "ix_personal_okr_tenant_status",   columnList = "tenant_id, status"),
        @Index(name = "ix_personal_okr_tenant_period",   columnList = "tenant_id, period_end")
    }
)
public class PersonalOkr extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_id", columnDefinition = "uuid", nullable = false)
    private UUID employeeId;

    /** Objective 문장 (최대 500자). */
    @Column(name = "objective", length = 500, nullable = false)
    private String objective;

    /** 진척률 (0.0 ~ 100.0). 단계 4 진입 시 KeyResult 가중 평균으로 대체. */
    @Column(name = "progress", precision = 5)
    private Double progress;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PersonalOkrStatus status;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
        if (this.status == null) {
            this.status = PersonalOkrStatus.ACTIVE;
        }
        if (this.progress == null) {
            this.progress = 0.0;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }

    public Double getProgress() { return progress; }
    public void setProgress(Double progress) { this.progress = progress; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public PersonalOkrStatus getStatus() { return status; }
    public void setStatus(PersonalOkrStatus status) { this.status = status; }
}

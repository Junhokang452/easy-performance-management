/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.selfevaluation.entity;

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
 * 자기평가 (SelfEvaluation) — 평가 사이클 내 자기평가 작성/제출.
 *
 * <p>도메인 본질: 자매품 9호 성과 평가 (Performance Management) — 4 도메인 중 핵심 1순위.
 * 단계 1 (본 슬라이스) = B2B per-tenant 운영. 단계 5 진입 시 B2C 개인 셀프 평가 분기 (RLS user_id).
 *
 * <p>tenant_id 컬럼 보유 — 단계 1 단일 DB 의 multi-tenant 가드 (lib TenantAwareAuditEntity 상속).
 * 단계 2 Model B 진입 시 컬럼 보존 + RLS 정책 SQL 박제. {@code created_at / updated_at / created_by /
 * updated_by} 는 lib BaseAuditEntity 가 제공.
 *
 * <p>상태 머신: DRAFT → SUBMITTED → REVIEWED → FINALIZED. status 변경은 service 계층에서 검증.
 *
 * <p>인덱스 — easy-ware 규칙 #2 정합 (tenant_id 선두 복합 인덱스 필수): {@code (tenant_id, employee_id)},
 * {@code (tenant_id, cycle_id)}, {@code (tenant_id, status)}.
 */
@Entity
@Table(
    name = "self_evaluation",
    indexes = {
        @Index(name = "ix_self_evaluation_tenant_employee", columnList = "tenant_id, employee_id"),
        @Index(name = "ix_self_evaluation_tenant_cycle",    columnList = "tenant_id, cycle_id"),
        @Index(name = "ix_self_evaluation_tenant_status",   columnList = "tenant_id, status")
    }
)
public class SelfEvaluation extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /** 피평가자(자기 자신) employee UUID — hcm.Employee FK (단계 2 진입 시 S2S 정합). */
    @Column(name = "employee_id", columnDefinition = "uuid", nullable = false)
    private UUID employeeId;

    /** 평가 사이클 UUID — 단계 4 진입 시 PerformanceCycle FK. 단계 1 = optional placeholder. */
    @Column(name = "cycle_id", columnDefinition = "uuid")
    private UUID cycleId;

    /** 평가 기간 시작일. */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** 평가 기간 종료일. */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 자기평가 본문 (성과/강점/개선점). 최대 8000자 — 자매품 정합. */
    @Column(name = "content", columnDefinition = "text")
    private String content;

    /** 자기 점수 (1.0 ~ 5.0). */
    @Column(name = "score", precision = 3)
    private Double score;

    /** 상태 머신. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private SelfEvaluationStatus status;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
        if (this.status == null) {
            this.status = SelfEvaluationStatus.DRAFT;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public UUID getCycleId() { return cycleId; }
    public void setCycleId(UUID cycleId) { this.cycleId = cycleId; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public SelfEvaluationStatus getStatus() { return status; }
    public void setStatus(SelfEvaluationStatus status) { this.status = status; }
}

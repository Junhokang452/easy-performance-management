/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.reflectionjournal.entity;

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
 * 회고 저널 (Reflection Journal) — KPT / 4Ls / SSC 방법론 회고 작성.
 *
 * <p>본 슬라이스 = method enum + 본문 단일 필드. 단계 4 진입 시 method-specific 구조화 (KPT = Keep/Problem/Try
 * 3 분리).
 *
 * <p>인덱스 — {@code (tenant_id, employee_id)}, {@code (tenant_id, reflection_date)}.
 */
@Entity
@Table(
    name = "reflection_journal",
    indexes = {
        @Index(name = "ix_reflection_journal_tenant_employee", columnList = "tenant_id, employee_id"),
        @Index(name = "ix_reflection_journal_tenant_date",     columnList = "tenant_id, reflection_date")
    }
)
public class ReflectionJournal extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_id", columnDefinition = "uuid", nullable = false)
    private UUID employeeId;

    /** 회고 일자. */
    @Column(name = "reflection_date", nullable = false)
    private LocalDate reflectionDate;

    /** 회고 방법론 (KPT / FOUR_LS / SSC). */
    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20, nullable = false)
    private ReflectionMethod method;

    /** 회고 본문 (최대 8000자 — 자매품 정합). */
    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;

    /** 비공개 / 공개 (멘토 공유). */
    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
        if (this.method == null) {
            this.method = ReflectionMethod.KPT;
        }
        if (this.isPrivate == null) {
            this.isPrivate = true;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public LocalDate getReflectionDate() { return reflectionDate; }
    public void setReflectionDate(LocalDate reflectionDate) { this.reflectionDate = reflectionDate; }

    public ReflectionMethod getMethod() { return method; }
    public void setMethod(ReflectionMethod method) { this.method = method; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Boolean getIsPrivate() { return isPrivate; }
    public void setIsPrivate(Boolean isPrivate) { this.isPrivate = isPrivate; }
}

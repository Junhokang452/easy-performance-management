/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.report.entity;

import com.easyperformance.common.UuidV7;
import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 성과 리포트 (PerformanceReport) — append-only — P0-S5 (p0_s5_contract.md §1).
 *
 * <p>도메인 본질: HR 이 cycle 단위로 FINALIZED review 를 직원에게 발행하는 리포트. content jsonb 는 발행
 * 시점 동결 스냅샷 (§5) — 이후 review/분포 가 변해도 불변. <b>UPDATE 는 {@code viewed_at}/
 * {@code acknowledged}/{@code acknowledged_at} 3 컬럼만 허용</b> (열람·확인 추적). content/publishedAt 등
 * 본문은 불변 — setter 미노출 (service 레벨 강제). 정정은 supersede 신규 row 전용 ({@code supersedes_id}
 * 가 원본 가리킴, UNIQUE — 한 row 는 최대 1번만 supersede). DELETE 경로 없음 (cycle/review CASCADE 만).
 *
 * <p>{@code cycle_id → evaluation_cycle(id) ON DELETE CASCADE}. {@code review_id → performance_review(id)
 * ON DELETE CASCADE}. {@code employee_id} 는 rm_employee 수신(P0-S6) 전 FK 없는 plain UUID.
 *
 * <p>active 판정 = 다른 행의 {@code supersedes_id} 로 참조되지 않은 행 (KpiActual {@code superseded}
 * computed 패턴 정합). view/acknowledge/supersede 는 active 행 한정 (superseded → REPORT_NOT_ACTIVE 409).
 *
 * <p>JSONB 매핑: {@code content} 는 lib OutboxEvent 패턴 정합 (P0-S1 D2) — {@code @JdbcTypeCode(JSON)} +
 * {@code String} + {@code columnDefinition = "jsonb"}. 발행 시점 §5 shape 를 동결 저장. BigDecimal 정밀도는
 * service 의 ObjectMapper {@code USE_BIG_DECIMAL_FOR_FLOATS} 로 보존. Response 에서는 파싱된 객체로 노출.
 *
 * <p>인덱스 — easy-ware 규칙 #2 (tenant_id 선두 복합): {@code (tenant_id, cycle_id, employee_id)},
 * {@code (tenant_id, review_id)}.
 */
@Entity
@Table(
    name = "performance_report",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_performance_report_supersedes", columnNames = {"supersedes_id"})
    },
    indexes = {
        @Index(name = "ix_performance_report_tenant_cycle_employee",
            columnList = "tenant_id, cycle_id, employee_id"),
        @Index(name = "ix_performance_report_tenant_review",
            columnList = "tenant_id, review_id")
    }
)
public class PerformanceReport extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /** FK → evaluation_cycle(id) ON DELETE CASCADE. */
    @Column(name = "cycle_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID cycleId;

    /** FK → performance_review(id) ON DELETE CASCADE. */
    @Column(name = "review_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID reviewId;

    /** 피평가자 직원 (rm_employee P0-S6 수신 전 — FK 없는 plain UUID). */
    @Column(name = "employee_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID employeeId;

    /** 발행 시각 — 불변. */
    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    /** 발행 수행 직원 — 불변. nullable. */
    @Column(name = "published_by", columnDefinition = "uuid", updatable = false)
    private UUID publishedBy;

    /**
     * 동결 스냅샷 (jsonb) — 발행 시점 §5 shape 로 생성 후 불변. lib OutboxEvent JSON 매핑 정합.
     * supersede 시 신규 row 에서 재동결 (현재 review 값 + 최신 분포).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false, updatable = false)
    private String content;

    /** 최초 열람 시각 — view 시 최초 1회 set (멱등). mutable 예외 #1. nullable. */
    @Column(name = "viewed_at")
    private Instant viewedAt;

    /** 확인 여부 — acknowledge 시 true 단방향 (멱등). mutable 예외 #2. 기본 false. */
    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged;

    /** 확인 시각 — acknowledge 시 set (멱등). mutable 예외 #2. nullable. */
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /**
     * supersede 대상 원본 report id — null 이면 최초 발행. UNIQUE (원본 1회만 supersede).
     * FK → performance_report(id). 불변.
     */
    @Column(name = "supersedes_id", columnDefinition = "uuid", updatable = false)
    private UUID supersedesId;

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

    public UUID getReviewId() { return reviewId; }
    public void setReviewId(UUID reviewId) { this.reviewId = reviewId; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public UUID getPublishedBy() { return publishedBy; }
    public void setPublishedBy(UUID publishedBy) { this.publishedBy = publishedBy; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Instant getViewedAt() { return viewedAt; }
    public void setViewedAt(Instant viewedAt) { this.viewedAt = viewedAt; }

    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }

    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public UUID getSupersedesId() { return supersedesId; }
    public void setSupersedesId(UUID supersedesId) { this.supersedesId = supersedesId; }
}

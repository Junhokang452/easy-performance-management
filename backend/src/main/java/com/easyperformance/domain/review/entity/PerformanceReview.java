/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.review.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 성과 평가 (PerformanceReview) — P0-S3 (p0_s3_contract.md §1).
 *
 * <p>도메인 본질: 한 직원(employee)의 한 사이클(cycle) 평가 단위 — 자기평가(self) + 매니저 평가(manager)
 * + KPI 자동 점수 산출 + Self↔Manager 비교. {@code (tenant_id, cycle_id, employee_id)} UNIQUE
 * (위반 REVIEW_DUPLICATE 409). 10단계 상태기계 ({@link ReviewStatus}) — P0-S3 전이는 §3 매트릭스 4개
 * + submit 전용 2개만.
 *
 * <p>{@code cycle_id → evaluation_cycle(id) ON DELETE CASCADE}. {@code employee_id} 는 rm_employee
 * 수신(P0-S6) 전 FK 없는 plain UUID.
 *
 * <p>점수: {@code kpi_score} 는 submit-manager 시 §5 산식으로 산출·동결. {@code mbo_score}/
 * {@code competency_score}/{@code mra_score} 는 P0 미사용 박제 (P1 채움). {@code final_score}/
 * {@code final_grade} 는 FINALIZED 전이 시 산출 (P0 단순 finalScore=kpiScore).
 *
 * <p>JSONB 매핑: {@code kpi_score_detail} 은 lib OutboxEvent 패턴 정합 (P0-S1 D2) —
 * {@code @JdbcTypeCode(JSON)} + {@code String} + {@code columnDefinition = "jsonb"}. submit-manager 에서
 * §1 shape 배열을 동결 저장. BigDecimal 정밀도는 service 의 ObjectMapper
 * {@code USE_BIG_DECIMAL_FOR_FLOATS} 로 보존. Response 에서는 파싱된 배열로 노출.
 *
 * <p>인덱스 — easy-ware 규칙 #2 (tenant_id 선두 복합): {@code (tenant_id, cycle_id, employee_id)},
 * {@code (tenant_id, status)}.
 */
@Entity
@Table(
    name = "performance_review",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_performance_review_cycle_employee",
            columnNames = {"tenant_id", "cycle_id", "employee_id"})
    },
    indexes = {
        @Index(name = "ix_performance_review_tenant_cycle_employee",
            columnList = "tenant_id, cycle_id, employee_id"),
        @Index(name = "ix_performance_review_tenant_status",
            columnList = "tenant_id, status")
    }
)
public class PerformanceReview extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /** FK → evaluation_cycle(id) ON DELETE CASCADE. */
    @Column(name = "cycle_id", columnDefinition = "uuid", nullable = false)
    private UUID cycleId;

    /** 피평가자 직원 (rm_employee P0-S6 수신 전 — FK 없는 plain UUID). */
    @Column(name = "employee_id", columnDefinition = "uuid", nullable = false)
    private UUID employeeId;

    /** 10단계 상태 머신 — 기본 DRAFT. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ReviewStatus status;

    /** KPI 자동 점수 (0~100) — submit-manager 시 §5 산식으로 산출·동결. nullable. */
    @Column(name = "kpi_score", precision = 5, scale = 2)
    private BigDecimal kpiScore;

    /** MBO 점수 — P0 미사용 박제 (P1 채움). nullable. */
    @Column(name = "mbo_score", precision = 5, scale = 2)
    private BigDecimal mboScore;

    /** 역량 점수 — P0 미사용 박제 (P1 채움). nullable. */
    @Column(name = "competency_score", precision = 5, scale = 2)
    private BigDecimal competencyScore;

    /** 360(mra) 점수 — P0 미사용 박제 (P1 채움). nullable. */
    @Column(name = "mra_score", precision = 5, scale = 2)
    private BigDecimal mraScore;

    /** 최종 점수 — FINALIZED 전이 시 산출 (P0 단순 = kpiScore). nullable. */
    @Column(name = "final_score", precision = 5, scale = 2)
    private BigDecimal finalScore;

    /** 최종 등급 — FINALIZED 전이 시 §5 밴드 (policy.ratingScale==S_A_B_C_D 일 때만). nullable. */
    @Column(name = "final_grade", length = 10)
    private String finalGrade;

    /** 자기평가 코멘트 — SELF_PENDING PATCH / submit-self 에서 저장. nullable. */
    @Column(name = "self_comment", columnDefinition = "text")
    private String selfComment;

    /** 매니저 평가 코멘트 — MANAGER_PENDING PATCH / submit-manager 에서 저장. nullable. */
    @Column(name = "manager_comment", columnDefinition = "text")
    private String managerComment;

    /**
     * KPI 점수 상세 스냅샷 (jsonb) — submit-manager 에서 §1 shape 배열로 동결. lib OutboxEvent JSON
     * 매핑 정합. submit-manager 전엔 null. 이후 KpiActual 추가돼도 review 점수 불변 (동결).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kpi_score_detail", columnDefinition = "jsonb")
    private String kpiScoreDetail;

    /** 확정 시각 — FINALIZED 전이 시 now(). nullable. */
    @Column(name = "finalized_at")
    private Instant finalizedAt;

    /** 확정 수행 직원 — FINALIZED 전이 시 actorEmployeeId. nullable. */
    @Column(name = "finalized_by", columnDefinition = "uuid")
    private UUID finalizedBy;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
        if (this.status == null) {
            this.status = ReviewStatus.DRAFT;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCycleId() { return cycleId; }
    public void setCycleId(UUID cycleId) { this.cycleId = cycleId; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }

    public BigDecimal getKpiScore() { return kpiScore; }
    public void setKpiScore(BigDecimal kpiScore) { this.kpiScore = kpiScore; }

    public BigDecimal getMboScore() { return mboScore; }
    public void setMboScore(BigDecimal mboScore) { this.mboScore = mboScore; }

    public BigDecimal getCompetencyScore() { return competencyScore; }
    public void setCompetencyScore(BigDecimal competencyScore) { this.competencyScore = competencyScore; }

    public BigDecimal getMraScore() { return mraScore; }
    public void setMraScore(BigDecimal mraScore) { this.mraScore = mraScore; }

    public BigDecimal getFinalScore() { return finalScore; }
    public void setFinalScore(BigDecimal finalScore) { this.finalScore = finalScore; }

    public String getFinalGrade() { return finalGrade; }
    public void setFinalGrade(String finalGrade) { this.finalGrade = finalGrade; }

    public String getSelfComment() { return selfComment; }
    public void setSelfComment(String selfComment) { this.selfComment = selfComment; }

    public String getManagerComment() { return managerComment; }
    public void setManagerComment(String managerComment) { this.managerComment = managerComment; }

    public String getKpiScoreDetail() { return kpiScoreDetail; }
    public void setKpiScoreDetail(String kpiScoreDetail) { this.kpiScoreDetail = kpiScoreDetail; }

    public Instant getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Instant finalizedAt) { this.finalizedAt = finalizedAt; }

    public UUID getFinalizedBy() { return finalizedBy; }
    public void setFinalizedBy(UUID finalizedBy) { this.finalizedBy = finalizedBy; }
}

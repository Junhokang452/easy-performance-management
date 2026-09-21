package com.easyperformance.workflow;

import com.easyware.platform.UuidV7;
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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "evaluation_reviewer_assignment",
    uniqueConstraints = @UniqueConstraint(name = "uq_eval_reviewer_assignment",
        columnNames = {"tenant_id", "participant_id", "reviewer_employee_id", "reviewer_type", "review_round"}),
    indexes = {
        @Index(name = "ix_eval_reviewer_tenant_cycle", columnList = "tenant_id, cycle_id"),
        @Index(name = "ix_eval_reviewer_tenant_reviewer_status", columnList = "tenant_id, reviewer_employee_id, status")
    })
public class EvaluationReviewerAssignment extends TenantAwareAuditEntity {
    @Id @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "cycle_id", columnDefinition = "uuid", nullable = false)
    private UUID cycleId;
    @Column(name = "participant_id", columnDefinition = "uuid", nullable = false)
    private UUID participantId;
    @Column(name = "reviewer_employee_id", columnDefinition = "uuid", nullable = false)
    private UUID reviewerEmployeeId;
    @Enumerated(EnumType.STRING) @Column(name = "reviewer_type", length = 20, nullable = false)
    private ReviewerType reviewerType;
    @Column(name = "review_round", nullable = false)
    private int round;
    @Column(name = "weight", precision = 5, scale = 4, nullable = false)
    private BigDecimal weight;
    @Enumerated(EnumType.STRING) @Column(name = "status", length = 20, nullable = false)
    private ReviewerAssignmentStatus status;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidV7.generate();
        if (round == 0) round = 1;
        if (weight == null) weight = BigDecimal.ONE;
        if (status == null) status = ReviewerAssignmentStatus.ASSIGNED;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCycleId() { return cycleId; }
    public void setCycleId(UUID cycleId) { this.cycleId = cycleId; }
    public UUID getParticipantId() { return participantId; }
    public void setParticipantId(UUID participantId) { this.participantId = participantId; }
    public UUID getReviewerEmployeeId() { return reviewerEmployeeId; }
    public void setReviewerEmployeeId(UUID reviewerEmployeeId) { this.reviewerEmployeeId = reviewerEmployeeId; }
    public ReviewerType getReviewerType() { return reviewerType; }
    public void setReviewerType(ReviewerType reviewerType) { this.reviewerType = reviewerType; }
    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public ReviewerAssignmentStatus getStatus() { return status; }
    public void setStatus(ReviewerAssignmentStatus status) { this.status = status; }
}

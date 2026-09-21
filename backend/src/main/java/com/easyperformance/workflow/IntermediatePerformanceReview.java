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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "intermediate_performance_review",
    uniqueConstraints = @UniqueConstraint(name = "uq_intermediate_review_participant",
        columnNames = {"tenant_id", "participant_id"}),
    indexes = @Index(name = "ix_intermediate_review_tenant_cycle_status",
        columnList = "tenant_id, cycle_id, status"))
public class IntermediatePerformanceReview extends TenantAwareAuditEntity {
    @Id @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "cycle_id", columnDefinition = "uuid", nullable = false)
    private UUID cycleId;
    @Column(name = "participant_id", columnDefinition = "uuid", nullable = false)
    private UUID participantId;
    @Column(name = "employee_id", columnDefinition = "uuid", nullable = false)
    private UUID employeeId;
    @Enumerated(EnumType.STRING) @Column(name = "status", length = 30, nullable = false)
    private IntermediateReviewStatus status;
    @Column(name = "progress_summary", columnDefinition = "text", nullable = false)
    private String progressSummary;
    @Column(name = "achievements", columnDefinition = "text") private String achievements;
    @Column(name = "blockers", columnDefinition = "text") private String blockers;
    @Column(name = "support_needed", columnDefinition = "text") private String supportNeeded;
    @Column(name = "manager_comment", columnDefinition = "text") private String managerComment;
    @Column(name = "employee_submitted_at") private Instant employeeSubmittedAt;
    @Column(name = "manager_completed_at") private Instant managerCompletedAt;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;

    @PrePersist void prePersist() {
        if (id == null) id = UuidV7.generate();
        if (status == null) status = IntermediateReviewStatus.DRAFT;
    }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCycleId() { return cycleId; }
    public void setCycleId(UUID cycleId) { this.cycleId = cycleId; }
    public UUID getParticipantId() { return participantId; }
    public void setParticipantId(UUID participantId) { this.participantId = participantId; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public IntermediateReviewStatus getStatus() { return status; }
    public void setStatus(IntermediateReviewStatus status) { this.status = status; }
    public String getProgressSummary() { return progressSummary; }
    public void setProgressSummary(String progressSummary) { this.progressSummary = progressSummary; }
    public String getAchievements() { return achievements; }
    public void setAchievements(String achievements) { this.achievements = achievements; }
    public String getBlockers() { return blockers; }
    public void setBlockers(String blockers) { this.blockers = blockers; }
    public String getSupportNeeded() { return supportNeeded; }
    public void setSupportNeeded(String supportNeeded) { this.supportNeeded = supportNeeded; }
    public String getManagerComment() { return managerComment; }
    public void setManagerComment(String managerComment) { this.managerComment = managerComment; }
    public Instant getEmployeeSubmittedAt() { return employeeSubmittedAt; }
    public void setEmployeeSubmittedAt(Instant employeeSubmittedAt) { this.employeeSubmittedAt = employeeSubmittedAt; }
    public Instant getManagerCompletedAt() { return managerCompletedAt; }
    public void setManagerCompletedAt(Instant managerCompletedAt) { this.managerCompletedAt = managerCompletedAt; }
}

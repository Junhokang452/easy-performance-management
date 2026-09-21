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
@Table(name = "performance_feedback",
    uniqueConstraints = @UniqueConstraint(name = "uq_performance_feedback_report", columnNames = {"tenant_id", "report_id"}),
    indexes = @Index(name = "ix_performance_feedback_tenant_participant_status",
        columnList = "tenant_id, participant_id, status"))
public class PerformanceFeedback extends TenantAwareAuditEntity {
    @Id @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false) private UUID id;
    @Column(name = "report_id", columnDefinition = "uuid", nullable = false) private UUID reportId;
    @Column(name = "participant_id", columnDefinition = "uuid", nullable = false) private UUID participantId;
    @Enumerated(EnumType.STRING) @Column(name = "status", length = 20, nullable = false) private FeedbackStatus status;
    @Column(name = "comment", columnDefinition = "text") private String comment;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "completed_by", columnDefinition = "uuid") private UUID completedBy;
    @Column(name = "appeal_reason", columnDefinition = "text") private String appealReason;
    @Column(name = "appealed_at") private Instant appealedAt;
    @Enumerated(EnumType.STRING) @Column(name = "resolution", length = 30) private FeedbackResolution resolution;
    @Column(name = "resolution_comment", columnDefinition = "text") private String resolutionComment;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "resolved_by", columnDefinition = "uuid") private UUID resolvedBy;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;
    @PrePersist void prePersist() { if (id == null) id = UuidV7.generate(); if (status == null) status = FeedbackStatus.DRAFT; }
    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public UUID getReportId() { return reportId; } public void setReportId(UUID reportId) { this.reportId = reportId; }
    public UUID getParticipantId() { return participantId; } public void setParticipantId(UUID participantId) { this.participantId = participantId; }
    public FeedbackStatus getStatus() { return status; } public void setStatus(FeedbackStatus status) { this.status = status; }
    public String getComment() { return comment; } public void setComment(String comment) { this.comment = comment; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public UUID getCompletedBy() { return completedBy; } public void setCompletedBy(UUID completedBy) { this.completedBy = completedBy; }
    public String getAppealReason() { return appealReason; } public void setAppealReason(String appealReason) { this.appealReason = appealReason; }
    public Instant getAppealedAt() { return appealedAt; } public void setAppealedAt(Instant appealedAt) { this.appealedAt = appealedAt; }
    public FeedbackResolution getResolution() { return resolution; } public void setResolution(FeedbackResolution resolution) { this.resolution = resolution; }
    public String getResolutionComment() { return resolutionComment; } public void setResolutionComment(String resolutionComment) { this.resolutionComment = resolutionComment; }
    public Instant getResolvedAt() { return resolvedAt; } public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public UUID getResolvedBy() { return resolvedBy; } public void setResolvedBy(UUID resolvedBy) { this.resolvedBy = resolvedBy; }
}

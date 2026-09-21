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
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation_goal", indexes = {
    @Index(name = "ix_eval_goal_tenant_cycle_employee", columnList = "tenant_id, cycle_id, employee_id"),
    @Index(name = "ix_eval_goal_tenant_participant_status", columnList = "tenant_id, participant_id, status")
})
public class EvaluationGoal extends TenantAwareAuditEntity {
    @Id @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "cycle_id", columnDefinition = "uuid", nullable = false)
    private UUID cycleId;
    @Column(name = "participant_id", columnDefinition = "uuid", nullable = false)
    private UUID participantId;
    @Column(name = "employee_id", columnDefinition = "uuid", nullable = false)
    private UUID employeeId;
    @Column(name = "kpi_assignment_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID kpiAssignmentId;
    @Column(name = "kpi_node_id", columnDefinition = "uuid", nullable = false)
    private UUID kpiNodeId;
    @Column(name = "title", length = 200, nullable = false)
    private String title;
    @Column(name = "description", columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING) @Column(name = "status", length = 30, nullable = false)
    private GoalStatus status;
    @Column(name = "decision_comment", columnDefinition = "text")
    private String decisionComment;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "approved_by", columnDefinition = "uuid")
    private UUID approvedBy;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;

    @PrePersist void prePersist() {
        if (id == null) id = UuidV7.generate();
        if (status == null) status = GoalStatus.DRAFT;
    }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCycleId() { return cycleId; }
    public void setCycleId(UUID cycleId) { this.cycleId = cycleId; }
    public UUID getParticipantId() { return participantId; }
    public void setParticipantId(UUID participantId) { this.participantId = participantId; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public UUID getKpiAssignmentId() { return kpiAssignmentId; }
    public void setKpiAssignmentId(UUID kpiAssignmentId) { this.kpiAssignmentId = kpiAssignmentId; }
    public UUID getKpiNodeId() { return kpiNodeId; }
    public void setKpiNodeId(UUID kpiNodeId) { this.kpiNodeId = kpiNodeId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public GoalStatus getStatus() { return status; }
    public void setStatus(GoalStatus status) { this.status = status; }
    public String getDecisionComment() { return decisionComment; }
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }
}

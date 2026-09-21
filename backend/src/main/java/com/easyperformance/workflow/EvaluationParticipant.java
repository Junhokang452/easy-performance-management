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

import java.util.UUID;

@Entity
@Table(name = "evaluation_participant",
    uniqueConstraints = @UniqueConstraint(name = "uq_eval_participant_cycle_employee",
        columnNames = {"tenant_id", "cycle_id", "employee_id"}),
    indexes = {
        @Index(name = "ix_eval_participant_tenant_cycle_status", columnList = "tenant_id, cycle_id, status"),
        @Index(name = "ix_eval_participant_tenant_employee", columnList = "tenant_id, employee_id")
    })
public class EvaluationParticipant extends TenantAwareAuditEntity {
    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "cycle_id", columnDefinition = "uuid", nullable = false)
    private UUID cycleId;
    @Column(name = "employee_id", columnDefinition = "uuid", nullable = false)
    private UUID employeeId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ParticipantStatus status;
    @Column(name = "exclusion_reason", columnDefinition = "text")
    private String exclusionReason;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidV7.generate();
        if (status == null) status = ParticipantStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCycleId() { return cycleId; }
    public void setCycleId(UUID cycleId) { this.cycleId = cycleId; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public ParticipantStatus getStatus() { return status; }
    public void setStatus(ParticipantStatus status) { this.status = status; }
    public String getExclusionReason() { return exclusionReason; }
    public void setExclusionReason(String exclusionReason) { this.exclusionReason = exclusionReason; }
}

package com.easyperformance.program;

import com.easyware.platform.UuidV7;
import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "program_kpi_evidence", uniqueConstraints = {
    @UniqueConstraint(name = "uq_program_kpi_evidence_goal_revision", columnNames = {"tenant_id", "goal_id", "revision"}),
    @UniqueConstraint(name = "uq_program_kpi_evidence_goal_hash", columnNames = {"tenant_id", "goal_id", "preview_hash"}),
    @UniqueConstraint(name = "uq_program_kpi_evidence_supersedes", columnNames = {"supersedes_evidence_id"})
}, indexes = {
    @Index(name = "ix_program_kpi_evidence_tenant_goal", columnList = "tenant_id, goal_id, revision"),
    @Index(name = "ix_program_kpi_evidence_tenant_participant", columnList = "tenant_id, participant_id, goal_id")
})
public class ProgramKpiEvidence extends TenantAwareAuditEntity {
    @Id @Column(columnDefinition = "uuid", nullable = false, updatable = false) private UUID id;
    @Column(name = "program_id", columnDefinition = "uuid", nullable = false, updatable = false) private UUID programId;
    @Column(name = "participant_id", columnDefinition = "uuid", nullable = false, updatable = false) private UUID participantId;
    @Column(name = "goal_id", columnDefinition = "uuid", nullable = false, updatable = false) private UUID goalId;
    @Column(name = "cycle_id", columnDefinition = "uuid", nullable = false, updatable = false) private UUID cycleId;
    @Column(name = "kpi_assignment_id", columnDefinition = "uuid", nullable = false, updatable = false) private UUID kpiAssignmentId;
    @Column(nullable = false, updatable = false) private int revision;
    @Column(name = "supersedes_evidence_id", columnDefinition = "uuid", updatable = false) private UUID supersedesEvidenceId;
    @Column(name = "actual_cutoff_date", nullable = false, updatable = false) private LocalDate actualCutoffDate;
    @Column(name = "preview_hash", length = 64, nullable = false, updatable = false) private String previewHash;
    @Column(nullable = false, length = 500, updatable = false) private String reason;
    @Column(name = "applied_by_employee_id", columnDefinition = "uuid", updatable = false) private UUID appliedByEmployeeId;
    @Column(name = "captured_at", nullable = false, updatable = false) private Instant capturedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "evidence_json", columnDefinition = "jsonb", nullable = false, updatable = false) private String evidenceJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_snapshot_json", columnDefinition = "jsonb", nullable = false, updatable = false) private String sourceSnapshotJson;

    @PrePersist void prePersist() { if (id == null) id = UuidV7.generate(); if (capturedAt == null) capturedAt = Instant.now(); }
    public UUID getId(){return id;} public void setId(UUID v){id=v;}
    public UUID getProgramId(){return programId;} public void setProgramId(UUID v){programId=v;}
    public UUID getParticipantId(){return participantId;} public void setParticipantId(UUID v){participantId=v;}
    public UUID getGoalId(){return goalId;} public void setGoalId(UUID v){goalId=v;}
    public UUID getCycleId(){return cycleId;} public void setCycleId(UUID v){cycleId=v;}
    public UUID getKpiAssignmentId(){return kpiAssignmentId;} public void setKpiAssignmentId(UUID v){kpiAssignmentId=v;}
    public int getRevision(){return revision;} public void setRevision(int v){revision=v;}
    public UUID getSupersedesEvidenceId(){return supersedesEvidenceId;} public void setSupersedesEvidenceId(UUID v){supersedesEvidenceId=v;}
    public LocalDate getActualCutoffDate(){return actualCutoffDate;} public void setActualCutoffDate(LocalDate v){actualCutoffDate=v;}
    public String getPreviewHash(){return previewHash;} public void setPreviewHash(String v){previewHash=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
    public UUID getAppliedByEmployeeId(){return appliedByEmployeeId;} public void setAppliedByEmployeeId(UUID v){appliedByEmployeeId=v;}
    public Instant getCapturedAt(){return capturedAt;} public void setCapturedAt(Instant v){capturedAt=v;}
    public String getEvidenceJson(){return evidenceJson;} public void setEvidenceJson(String v){evidenceJson=v;}
    public String getSourceSnapshotJson(){return sourceSnapshotJson;} public void setSourceSnapshotJson(String v){sourceSnapshotJson=v;}
}

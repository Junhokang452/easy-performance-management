package com.easyperformance.program;

import com.easyperformance.program.ProgramTypes.EvaluationKind;
import com.easyperformance.program.ProgramTypes.ProgramStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "evaluation_program", uniqueConstraints = @UniqueConstraint(name = "uq_program_tenant_year_name",
    columnNames = {"tenant_id", "evaluation_year", "name"}), indexes = {
    @Index(name = "ix_program_tenant_status_year", columnList = "tenant_id, status, evaluation_year"),
    @Index(name = "ix_program_tenant_period", columnList = "tenant_id, starts_on, ends_on")
})
public class EvaluationProgram extends TenantAwareAuditEntity {
    @Id @Column(columnDefinition = "uuid", nullable = false, updatable = false) private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "evaluation_year", nullable = false) private Integer evaluationYear;
    @Column(name = "as_of_date", nullable = false) private LocalDate asOfDate;
    @Column(name = "starts_on", nullable = false) private LocalDate startsOn;
    @Column(name = "ends_on", nullable = false) private LocalDate endsOn;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EvaluationKind kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ProgramStatus status;
    @Column(name = "definition_revision", nullable = false) private int definitionRevision;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "definition_json", columnDefinition = "jsonb", nullable = false)
    private String definitionJson;
    @Column(name = "applied_item_revision", nullable = false) private int appliedItemRevision;
    @Column(name = "opened_at") private Instant openedAt;
    @Column(name = "finalized_at") private Instant finalizedAt;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;

    @PrePersist void prePersist() {
        if (id == null) id = UuidV7.generate();
        if (status == null) status = ProgramStatus.DRAFT;
        if (definitionRevision == 0) definitionRevision = 1;
    }
    public UUID getId(){return id;} public void setId(UUID v){id=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public Integer getEvaluationYear(){return evaluationYear;} public void setEvaluationYear(Integer v){evaluationYear=v;}
    public LocalDate getAsOfDate(){return asOfDate;} public void setAsOfDate(LocalDate v){asOfDate=v;}
    public LocalDate getStartsOn(){return startsOn;} public void setStartsOn(LocalDate v){startsOn=v;}
    public LocalDate getEndsOn(){return endsOn;} public void setEndsOn(LocalDate v){endsOn=v;}
    public EvaluationKind getKind(){return kind;} public void setKind(EvaluationKind v){kind=v;}
    public ProgramStatus getStatus(){return status;} public void setStatus(ProgramStatus v){status=v;}
    public int getDefinitionRevision(){return definitionRevision;} public void setDefinitionRevision(int v){definitionRevision=v;}
    public String getDefinitionJson(){return definitionJson;} public void setDefinitionJson(String v){definitionJson=v;}
    public int getAppliedItemRevision(){return appliedItemRevision;} public void setAppliedItemRevision(int v){appliedItemRevision=v;}
    public Instant getOpenedAt(){return openedAt;} public void setOpenedAt(Instant v){openedAt=v;}
    public Instant getFinalizedAt(){return finalizedAt;} public void setFinalizedAt(Instant v){finalizedAt=v;}
    public long getRowVersion(){return rowVersion;}
}

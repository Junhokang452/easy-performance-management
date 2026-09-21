package com.easyperformance.program;

import com.easyware.platform.UuidV7;
import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity @Immutable
@Table(name="evaluation_program_revision", uniqueConstraints=@UniqueConstraint(name="uq_program_revision", columnNames={"tenant_id","program_id","revision"}),
    indexes=@Index(name="ix_program_revision_tenant_program", columnList="tenant_id, program_id, revision"))
public class EvaluationProgramRevision extends TenantAwareAuditEntity {
    @Id @Column(columnDefinition="uuid",nullable=false,updatable=false) private UUID id;
    @Column(name="program_id",columnDefinition="uuid",nullable=false,updatable=false) private UUID programId;
    @Column(nullable=false,updatable=false) private int revision;
    @Column(nullable=false,length=500,updatable=false) private String reason;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="definition_json",columnDefinition="jsonb",nullable=false,updatable=false) private String definitionJson;
    @Column(name="actor_employee_id",columnDefinition="uuid",updatable=false) private UUID actorEmployeeId;
    @PrePersist void prePersist(){if(id==null)id=UuidV7.generate();}
    public UUID getId(){return id;} public void setId(UUID v){id=v;}
    public UUID getProgramId(){return programId;} public void setProgramId(UUID v){programId=v;}
    public int getRevision(){return revision;} public void setRevision(int v){revision=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
    public String getDefinitionJson(){return definitionJson;} public void setDefinitionJson(String v){definitionJson=v;}
    public UUID getActorEmployeeId(){return actorEmployeeId;} public void setActorEmployeeId(UUID v){actorEmployeeId=v;}
}

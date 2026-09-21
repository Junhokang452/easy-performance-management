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
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name="program_reminder_run",
    uniqueConstraints=@UniqueConstraint(name="uq_program_reminder_run_idempotency",
        columnNames={"tenant_id","program_id","idempotency_key"}),
    indexes=@Index(name="ix_program_reminder_run_tenant_program_created",
        columnList="tenant_id, program_id, created_at"))
public class ProgramReminderRun extends TenantAwareAuditEntity {
    @Id @Column(columnDefinition="uuid",nullable=false,updatable=false) private UUID id;
    @Column(name="program_id",columnDefinition="uuid",nullable=false,updatable=false) private UUID programId;
    @Column(name="idempotency_key",columnDefinition="uuid",nullable=false,updatable=false) private UUID idempotencyKey;
    @Column(name="request_hash",length=64,nullable=false,updatable=false) private String requestHash;
    @Column(name="preview_hash",length=64,nullable=false,updatable=false) private String previewHash;
    @Column(name="reminder_on",nullable=false,updatable=false) private LocalDate reminderOn;
    @Column(name="policy_version",length=60,nullable=false,updatable=false) private String policyVersion;
    @Column(nullable=false,length=10,updatable=false) private String locale;
    @Column(nullable=false,length=500,updatable=false) private String reason;
    @Column(name="actor_employee_id",columnDefinition="uuid",updatable=false) private UUID actorEmployeeId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="response_json",columnDefinition="jsonb",nullable=false) private String responseJson;
    @Version @Column(name="row_version",nullable=false) private long rowVersion;

    @PrePersist void prePersist(){if(id==null)id=UuidV7.generate();if(responseJson==null)responseJson="{}";}
    public UUID getId(){return id;} public void setId(UUID v){id=v;}
    public UUID getProgramId(){return programId;} public void setProgramId(UUID v){programId=v;}
    public UUID getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(UUID v){idempotencyKey=v;}
    public String getRequestHash(){return requestHash;} public void setRequestHash(String v){requestHash=v;}
    public String getPreviewHash(){return previewHash;} public void setPreviewHash(String v){previewHash=v;}
    public LocalDate getReminderOn(){return reminderOn;} public void setReminderOn(LocalDate v){reminderOn=v;}
    public String getPolicyVersion(){return policyVersion;} public void setPolicyVersion(String v){policyVersion=v;}
    public String getLocale(){return locale;} public void setLocale(String v){locale=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
    public UUID getActorEmployeeId(){return actorEmployeeId;} public void setActorEmployeeId(UUID v){actorEmployeeId=v;}
    public String getResponseJson(){return responseJson;} public void setResponseJson(String v){responseJson=v;}
}

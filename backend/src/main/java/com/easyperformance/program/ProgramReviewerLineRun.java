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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** Persisted idempotency result keyed by tenant/program/preview hash. */
@Entity
@Table(name = "program_reviewer_line_run",
    uniqueConstraints = @UniqueConstraint(name = "uq_reviewer_line_run_hash",
        columnNames = {"tenant_id", "program_id", "preview_hash"}),
    indexes = @Index(name = "ix_reviewer_line_run_tenant_program",
        columnList = "tenant_id, program_id, created_at"))
public class ProgramReviewerLineRun extends TenantAwareAuditEntity {
    @Id @Column(columnDefinition = "uuid", nullable = false, updatable = false) private UUID id;
    @Column(name = "program_id", columnDefinition = "uuid", nullable = false) private UUID programId;
    @Column(name = "preview_hash", nullable = false, length = 64) private String previewHash;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "response_json", nullable = false, columnDefinition = "jsonb")
    private String responseJson;

    @PrePersist void prePersist() { if (id == null) id = UuidV7.generate(); }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }
    public String getPreviewHash() { return previewHash; }
    public void setPreviewHash(String previewHash) { this.previewHash = previewHash; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
}

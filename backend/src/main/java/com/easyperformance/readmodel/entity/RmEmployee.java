/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.readmodel.entity;

import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 사원 Read Model (easy-hcm SoR — BE-CC-4 소비, P0-S6 core-master 채널, talent RmEmployee 사본).
 *
 * <p><b>본 자매품에서 수정 금지</b> — {@code ReadModelSyncService} 만이 쓰기 진입점 (S2S 수신).
 * 그 외 레이어는 read-only 소비만 한다 (성과평가 employeeId 표시·선택 원천).
 *
 * <p>id = easy-hcm 사원 ID (외부 주입 — UuidV7 생성 없음). sourceVersion = SoR updatedAt
 * epochMilli 단조 증가 — 낮은/동일 버전 수신은 skip (idempotency).
 */
@Entity
@Table(
    name = "rm_employee",
    indexes = {
        @Index(name = "ix_rm_employee_tenant_no",  columnList = "tenant_id, employee_no"),
        @Index(name = "ix_rm_employee_tenant_org", columnList = "tenant_id, org_unit_id")
    }
)
public class RmEmployee extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_no", length = 50, nullable = false)
    private String employeeNo;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /** 소스 어휘 보존 (enum 미강제 — read-model 원칙, talent 정합). */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 소속 조직 (rm_org_unit 참조 — hcm 페이로드 homeStoreId 계열 매핑). */
    @Column(name = "org_unit_id", columnDefinition = "uuid")
    private UUID orgUnitId;

    @Column(name = "employment_type", length = 20)
    private String employmentType;

    @Column(name = "source_version", nullable = false)
    private Long sourceVersion;

    @Column(name = "synced_at", nullable = false)
    private OffsetDateTime syncedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getOrgUnitId() { return orgUnitId; }
    public void setOrgUnitId(UUID orgUnitId) { this.orgUnitId = orgUnitId; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public Long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(Long sourceVersion) { this.sourceVersion = sourceVersion; }

    public OffsetDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(OffsetDateTime syncedAt) { this.syncedAt = syncedAt; }
}

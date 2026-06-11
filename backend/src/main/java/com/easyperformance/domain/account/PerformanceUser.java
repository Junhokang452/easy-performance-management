/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.account;

import com.easyperformance.common.UuidV7;
import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 사용자 계정 (PerformanceUser) — 인증 격상 (in-memory stub → 실 사용자 엔티티 + bcrypt,
 * talent {@code TalentUser} 사본 / store-hr `user_account` 동형, ADR-025/026).
 *
 * <p>role = SUPER_ADMIN(시스템 관리자 — control plane 콘솔) / HR_ADMIN(HR 운영 전권) /
 * DIRECTOR(본부) / MANAGER(매니저) / EMPLOYEE(구성원). employee_id 는 rm_employee 매핑
 * (hcm S2S 수신 후 채움 — null 허용, null 이면 계정 id 를 employee 식별자로 쓰는 dev 호환 모드).
 * email 은 테넌트 내 유일 (DDL {@code uq_user_account_tenant_email}).
 */
@Entity
@Table(
    name = "user_account",
    indexes = {
        @Index(name = "ix_user_account_tenant_email", columnList = "tenant_id, email"),
        @Index(name = "ix_user_account_tenant_role",  columnList = "tenant_id, role")
    }
)
public class PerformanceUser extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Column(name = "role", length = 20, nullable = false)
    private String role;

    /** rm_employee 매핑 (선택) — 평가 대상자/평가자 기본값 등에 활용 (P0-S6 read-model 정합). */
    @Column(name = "employee_id", columnDefinition = "uuid")
    private UUID employeeId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

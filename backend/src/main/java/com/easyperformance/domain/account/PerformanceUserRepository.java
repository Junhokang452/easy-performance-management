/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * PerformanceUser Repository — ADR-026 명명 + 테넌트 필수 필터 (talent {@code TalentUserRepository} 사본).
 */
@Repository
public interface PerformanceUserRepository extends JpaRepository<PerformanceUser, UUID> {

    Optional<PerformanceUser> findByTenantIdAndEmail(UUID tenantId, String email);

    Optional<PerformanceUser> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
}

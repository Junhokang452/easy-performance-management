/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.readmodel.repository;

import com.easyperformance.readmodel.entity.RmOrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * RmOrgUnit Repository — 쓰기는 {@code ReadModelSyncService} 전용, 그 외 read-only 소비.
 */
@Repository
public interface RmOrgUnitRepository extends JpaRepository<RmOrgUnit, UUID> {

    Optional<RmOrgUnit> findByIdAndTenantId(UUID id, UUID tenantId);
}

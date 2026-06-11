/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.calibration.repository;

import com.easyperformance.domain.calibration.entity.RatingDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * RatingDistribution Repository — ADR-026 명명 표준 정합. cycle (× orgUnit) lookup 전용.
 *
 * <p>easy-ware 규칙 #10 — tenant_id 명시 변형만. P0-S4 는 전사 (org_unit_id NULL) 행만 upsert 하므로
 * {@code findByTenantIdAndCycleIdAndOrgUnitIdIsNull} 사용 (partial unique 정합).
 */
@Repository
public interface RatingDistributionRepository extends JpaRepository<RatingDistribution, UUID> {

    /** 전사 (org_unit_id NULL) 행 lookup — P0-S4 apply upsert 대상. */
    Optional<RatingDistribution> findByTenantIdAndCycleIdAndOrgUnitIdIsNull(UUID tenantId, UUID cycleId);
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.repository;

import com.easyperformance.domain.kpi.entity.KpiTree;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * KpiTree Repository — ADR-026 명명 표준 정합 ({@code findByIdAndTenantId / findAllBy...}).
 *
 * <p>easy-ware 규칙 #10 (테넌트 미필터 금지) — 모든 변형에 tenant_id 명시.
 */
@Repository
public interface KpiTreeRepository extends JpaRepository<KpiTree, UUID> {

    Optional<KpiTree> findByIdAndTenantId(UUID id, UUID tenantId);

    List<KpiTree> findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(UUID tenantId, UUID cycleId);
}

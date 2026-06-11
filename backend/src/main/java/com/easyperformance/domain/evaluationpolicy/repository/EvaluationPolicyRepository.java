/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationpolicy.repository;

import com.easyperformance.domain.evaluationpolicy.entity.EvaluationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * EvaluationPolicy Repository — ADR-026 명명 표준 정합. cycle 1:1 정책 lookup 전용.
 */
@Repository
public interface EvaluationPolicyRepository extends JpaRepository<EvaluationPolicy, UUID> {

    Optional<EvaluationPolicy> findByTenantIdAndCycleId(UUID tenantId, UUID cycleId);

    boolean existsByTenantIdAndCycleId(UUID tenantId, UUID cycleId);
}

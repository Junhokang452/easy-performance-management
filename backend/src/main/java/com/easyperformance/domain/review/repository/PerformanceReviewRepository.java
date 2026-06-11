/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.review.repository;

import com.easyperformance.domain.review.entity.PerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PerformanceReview Repository — ADR-026 명명 표준 정합 ({@code findByIdAndTenantId / findAllBy...}).
 *
 * <p>easy-ware 규칙 #10 (테넌트 미필터 금지) — 모든 변형에 tenant_id 명시. cycle 별 목록 + employeeId
 * 옵션 필터 + (cycle×employee) 단건/중복 검사.
 */
@Repository
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {

    Optional<PerformanceReview> findByIdAndTenantId(UUID id, UUID tenantId);

    /** cycle 의 평가 전부 — createdAt ASC. */
    List<PerformanceReview> findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(UUID tenantId, UUID cycleId);

    /** cycle × employee 필터 목록 — createdAt ASC (employeeId 옵션 필터 분기용). */
    List<PerformanceReview> findAllByTenantIdAndCycleIdAndEmployeeIdOrderByCreatedAtAsc(
        UUID tenantId, UUID cycleId, UUID employeeId);

    /** cycle × employee 단건 (GET /reviews/my). */
    Optional<PerformanceReview> findByTenantIdAndCycleIdAndEmployeeId(
        UUID tenantId, UUID cycleId, UUID employeeId);

    /** (cycle×employee) 중복 검사 (생성/bulk). */
    boolean existsByTenantIdAndCycleIdAndEmployeeId(UUID tenantId, UUID cycleId, UUID employeeId);
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationcycle.repository;

import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * EvaluationCycle Repository — ADR-026 명명 표준 정합:
 * {@code findById / findAllByTenantId / existsBy / countBy}.
 *
 * <p>easy-ware 12 규칙:
 * <ul>
 *   <li>#2 tenant_id 선두 복합 인덱스 필수 — entity {@code @Index} 박제</li>
 *   <li>#3 리스트 엔드포인트 Pageable 필수 — Page 반환</li>
 *   <li>#10 findAll() 테넌트 미필터 금지 — tenant_id 명시 변형만 제공</li>
 * </ul>
 */
@Repository
public interface EvaluationCycleRepository extends JpaRepository<EvaluationCycle, UUID> {

    Optional<EvaluationCycle> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<EvaluationCycle> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndName(UUID tenantId, String name);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    @Query("""
        select c from EvaluationCycle c
        where c.tenantId = :tenantId and (
          :operator = true
          or exists (select p.id from EvaluationParticipant p
                     where p.tenantId = :tenantId and p.cycleId = c.id
                       and p.employeeId = :employeeId and p.status = com.easyperformance.workflow.ParticipantStatus.ACTIVE)
          or exists (select a.id from EvaluationReviewerAssignment a
                     where a.tenantId = :tenantId and a.cycleId = c.id
                       and a.reviewerEmployeeId = :employeeId
                       and a.status <> com.easyperformance.workflow.ReviewerAssignmentStatus.REVOKED)
        )
        """)
    Page<EvaluationCycle> findVisible(@Param("tenantId") UUID tenantId,
                                      @Param("employeeId") UUID employeeId,
                                      @Param("operator") boolean operator,
                                      Pageable pageable);
}

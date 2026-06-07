/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.selfevaluation.repository;

import com.easyperformance.domain.selfevaluation.entity.SelfEvaluation;
import com.easyperformance.domain.selfevaluation.entity.SelfEvaluationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * SelfEvaluation Repository — ADR-026 명명 표준 정합:
 * {@code findById / findAllByTenantId / existsBy / countBy}.
 *
 * <p>easy-ware 12 규칙:
 * <ul>
 *   <li>#2 tenant_id 선두 복합 인덱스 필수 — entity {@code @Index} 박제</li>
 *   <li>#3 리스트 엔드포인트 Pageable 필수 — 본 인터페이스 Page 반환</li>
 *   <li>#10 findAll() 테넌트 미필터 금지 — 본 인터페이스는 tenant_id 명시 변형만 제공</li>
 * </ul>
 */
@Repository
public interface SelfEvaluationRepository extends JpaRepository<SelfEvaluation, UUID> {

    Optional<SelfEvaluation> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<SelfEvaluation> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<SelfEvaluation> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId, Pageable pageable);

    Page<SelfEvaluation> findAllByTenantIdAndStatus(UUID tenantId, SelfEvaluationStatus status, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);
}

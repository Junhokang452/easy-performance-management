/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.readmodel.repository;

import com.easyperformance.readmodel.entity.RmAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * RmAssignment Repository — 쓰기는 {@code ReadModelSyncService} 전용, 그 외 read-only 소비.
 *
 * <p>사원당 발령 이력은 상한이 작다 — List 허용 (이력 조회용, talent 정합).
 */
@Repository
public interface RmAssignmentRepository extends JpaRepository<RmAssignment, UUID> {

    List<RmAssignment> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);
}

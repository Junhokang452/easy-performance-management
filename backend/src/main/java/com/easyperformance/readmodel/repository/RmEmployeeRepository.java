/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.readmodel.repository;

import com.easyperformance.readmodel.entity.RmEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * RmEmployee Repository — 쓰기는 {@code ReadModelSyncService} 전용, 그 외 read-only 소비.
 *
 * <p>P0-S6 수신 슬라이스 범위: 멱등 upsert 용 {@code findById} 만 사용. 검색/선택기 finder
 * ({@code GET /rm/employees?ids=} 등)는 별도 슬라이스 (계약 §7 범위 제외, talent S18 패턴).
 */
@Repository
public interface RmEmployeeRepository extends JpaRepository<RmEmployee, UUID> {

    Optional<RmEmployee> findByIdAndTenantId(UUID id, UUID tenantId);
}

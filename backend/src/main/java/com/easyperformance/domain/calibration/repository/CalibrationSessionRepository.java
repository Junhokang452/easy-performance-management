/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.calibration.repository;

import com.easyperformance.domain.calibration.entity.CalibrationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CalibrationSession Repository — ADR-026 명명 표준 정합 ({@code findByIdAndTenantId / findAllBy...}).
 *
 * <p>easy-ware 규칙 #10 (테넌트 미필터 금지) — 모든 변형에 tenant_id 명시. cycle 당 다중 세션 (UNIQUE 없음)
 * → cycle 별 목록 + 단건 조회.
 */
@Repository
public interface CalibrationSessionRepository extends JpaRepository<CalibrationSession, UUID> {

    Optional<CalibrationSession> findByIdAndTenantId(UUID id, UUID tenantId);

    /** cycle 의 세션 전부 — createdAt ASC. */
    List<CalibrationSession> findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(UUID tenantId, UUID cycleId);
}

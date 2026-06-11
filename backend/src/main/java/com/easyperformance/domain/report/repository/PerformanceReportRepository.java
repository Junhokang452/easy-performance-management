/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.report.repository;

import com.easyperformance.domain.report.entity.PerformanceReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PerformanceReport Repository — append-only. ADR-026 명명 정합. cycle 별 이력 목록 + active 판정 보조.
 *
 * <p>UPDATE/DELETE 메서드 미제공 — service 가 mutable 3 컬럼만 dirty-write (정정은 supersede 신규 row,
 * 삭제는 cycle/review CASCADE 만). easy-ware 규칙 #10 (테넌트 미필터 금지) — 모든 변형에 tenant_id 명시.
 *
 * <p>active 판정 = supersede 안 된 행 (다른 행의 {@code supersedes_id} 로 참조되지 않음). KpiActual
 * {@code existsByTenantIdAndSupersedesId} 재정정 차단 패턴 정합 — 한 review 의 active 행은 supersede 체인
 * tail 1개뿐이므로 service 가 {@code findAllBy...ReviewId} 결과에서 superseded set 을 빼 판정.
 */
@Repository
public interface PerformanceReportRepository extends JpaRepository<PerformanceReport, UUID> {

    Optional<PerformanceReport> findByIdAndTenantId(UUID id, UUID tenantId);

    /** cycle 의 리포트 전부 (superseded 포함) — publishedAt DESC (HR 이력 가시성). */
    List<PerformanceReport> findAllByTenantIdAndCycleIdOrderByPublishedAtDesc(UUID tenantId, UUID cycleId);

    /** cycle × employee 필터 목록 — publishedAt DESC (employeeId 옵션 필터 분기용). */
    List<PerformanceReport> findAllByTenantIdAndCycleIdAndEmployeeIdOrderByPublishedAtDesc(
        UUID tenantId, UUID cycleId, UUID employeeId);

    /** review 의 리포트 전부 (publish skip 판정 — supersede 체인 포함). */
    List<PerformanceReport> findAllByTenantIdAndReviewId(UUID tenantId, UUID reviewId);

    /** cycle × employee 의 리포트 전부 (GET /reports/my active 판정용) — publishedAt DESC. */
    List<PerformanceReport> findAllByTenantIdAndCycleIdAndEmployeeId(
        UUID tenantId, UUID cycleId, UUID employeeId);

    /** 이미 supersede 된 원본인지 검사 (active 행 단건 판정). */
    boolean existsByTenantIdAndSupersedesId(UUID tenantId, UUID supersedesId);
}

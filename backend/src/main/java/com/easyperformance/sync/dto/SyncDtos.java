/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.sync.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * S2S 수신 페이로드 DTO (BE-CC-4 소비자 — P0-S6 core-master 채널, talent SyncDtos #1 사본).
 *
 * <p>공통: id = 소스 제품 SoR 식별자 (performance 미생성) · sourceVersion = 소스 updatedAt
 * epochMilli 단조 증가 (낮은/동일 버전 skip) · null id/version row 는 skip (배치 부분 실패 차단).
 *
 * <p>#1 hcm core-master — hcm→store-hr/talent {@code SyncBatchPayload} 동일 shape 재사용:
 * 점포 어휘 필드는 {@link JsonAlias} 로 수용 (homeStoreId→orgUnitId / storeType→orgType 계열 매핑).
 *
 * <p>송신측 hcm 의 {@code hcm.s2s.easyperformance.*} 타깃 추가는 별도 슬라이스 (hcm repo) —
 * 본 슬라이스는 수신측만. 게이트 미설정 503 자체 차단 = LIVE 안전.
 */
public final class SyncDtos {

    private SyncDtos() {
    }

    public record EmployeeUpsert(
        UUID id,
        String employeeNo,
        String name,
        String status,
        @JsonAlias("homeStoreId") UUID orgUnitId,
        String employmentType,
        Long sourceVersion
    ) {}

    public record OrgUnitUpsert(
        UUID id,
        String code,
        String name,
        UUID parentId,
        @JsonAlias("storeType") String orgType,
        Long sourceVersion
    ) {}

    public record AssignmentUpsert(
        UUID id,
        UUID employeeId,
        UUID orgUnitId,
        String positionCode,
        String gradeCode,
        String jobCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Long sourceVersion
    ) {}

    public record CoreMasterBatchRequest(
        List<EmployeeUpsert> employees,
        List<OrgUnitUpsert> orgUnits,
        List<AssignmentUpsert> assignments
    ) {}

    public record CoreMasterBatchResponse(
        int employeesApplied,
        int employeesSkipped,
        int orgUnitsApplied,
        int orgUnitsSkipped,
        int assignmentsApplied,
        int assignmentsSkipped
    ) {}
}

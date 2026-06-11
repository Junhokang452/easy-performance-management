/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.easyperformance.common.TenantSupport;
import com.easyperformance.readmodel.entity.RmAssignment;
import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.entity.RmOrgUnit;
import com.easyperformance.readmodel.repository.RmAssignmentRepository;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.readmodel.repository.RmOrgUnitRepository;
import com.easyperformance.sync.dto.SyncDtos.AssignmentUpsert;
import com.easyperformance.sync.dto.SyncDtos.CoreMasterBatchRequest;
import com.easyperformance.sync.dto.SyncDtos.CoreMasterBatchResponse;
import com.easyperformance.sync.dto.SyncDtos.EmployeeUpsert;
import com.easyperformance.sync.dto.SyncDtos.OrgUnitUpsert;

/**
 * rm_* 동기화 idempotency 단위 테스트 (P0-S6, talent ReadModelSyncServiceTest 정합) —
 * 부재 insert / 수신&gt;기존 update / 수신&lt;=기존 skip / null id·version skip / null 섹션 무해 /
 * org·assignment 적용 경로.
 */
class ReadModelSyncServiceTest {

    private final RmEmployeeRepository employees = mock(RmEmployeeRepository.class);
    private final RmOrgUnitRepository orgUnits = mock(RmOrgUnitRepository.class);
    private final RmAssignmentRepository assignments = mock(RmAssignmentRepository.class);

    private final ReadModelSyncService service = new ReadModelSyncService(
        employees, orgUnits, assignments);

    private static CoreMasterBatchRequest employeeOnly(EmployeeUpsert row) {
        return new CoreMasterBatchRequest(List.of(row), List.of(), List.of());
    }

    @Test
    void coreMaster_insertsWithExternalIdAndTenant() {
        UUID id = UUID.randomUUID();
        when(employees.findById(id)).thenReturn(Optional.empty());

        CoreMasterBatchResponse result = service.applyCoreMaster(employeeOnly(
            new EmployeeUpsert(id, "E001", "홍길동", "ACTIVE", UUID.randomUUID(), "FULL_TIME", 1L)));

        ArgumentCaptor<RmEmployee> captor = ArgumentCaptor.forClass(RmEmployee.class);
        verify(employees).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id);
        assertThat(captor.getValue().getTenantId()).isEqualTo(TenantSupport.FALLBACK_TENANT_ID);
        assertThat(captor.getValue().getSourceVersion()).isEqualTo(1L);
        assertThat(captor.getValue().getSyncedAt()).isNotNull();
        assertThat(result.employeesApplied()).isEqualTo(1);
        assertThat(result.employeesSkipped()).isZero();
    }

    @Test
    void coreMaster_updatesWhenIncomingVersionHigher() {
        UUID id = UUID.randomUUID();
        RmEmployee existing = new RmEmployee();
        existing.setId(id);
        existing.setTenantId(TenantSupport.FALLBACK_TENANT_ID);
        existing.setEmployeeNo("E001");
        existing.setName("구이름");
        existing.setStatus("ACTIVE");
        existing.setSourceVersion(1L);
        existing.setSyncedAt(OffsetDateTime.now().minusDays(1));
        when(employees.findById(id)).thenReturn(Optional.of(existing));

        CoreMasterBatchResponse result = service.applyCoreMaster(employeeOnly(
            new EmployeeUpsert(id, "E001", "새이름", "ACTIVE", null, "PART_TIME", 2L)));

        verify(employees).save(existing);
        assertThat(existing.getName()).isEqualTo("새이름");
        assertThat(existing.getSourceVersion()).isEqualTo(2L);
        assertThat(result.employeesApplied()).isEqualTo(1);
    }

    @Test
    void coreMaster_skipsWhenIncomingVersionLowerOrEqual() {
        UUID id = UUID.randomUUID();
        RmEmployee existing = new RmEmployee();
        existing.setId(id);
        existing.setTenantId(TenantSupport.FALLBACK_TENANT_ID);
        existing.setEmployeeNo("E001");
        existing.setName("현재");
        existing.setStatus("ACTIVE");
        existing.setSourceVersion(5L);
        existing.setSyncedAt(OffsetDateTime.now());
        when(employees.findById(id)).thenReturn(Optional.of(existing));

        CoreMasterBatchResponse result = service.applyCoreMaster(employeeOnly(
            new EmployeeUpsert(id, "E001", "과거", "ACTIVE", null, null, 5L)));

        verify(employees, never()).save(any());
        assertThat(existing.getName()).isEqualTo("현재");
        assertThat(result.employeesSkipped()).isEqualTo(1);
    }

    @Test
    void coreMaster_skipsMalformedRowsWithoutFailingBatch() {
        UUID goodOrgId = UUID.randomUUID();
        when(orgUnits.findById(goodOrgId)).thenReturn(Optional.empty());

        CoreMasterBatchResponse result = service.applyCoreMaster(new CoreMasterBatchRequest(
            List.of(new EmployeeUpsert(null, "E001", "무ID", "ACTIVE", null, null, 1L)),
            List.of(new OrgUnitUpsert(goodOrgId, "ORG-1", "기술본부", null, "DIVISION", 1L)),
            List.of(new AssignmentUpsert(UUID.randomUUID(), UUID.randomUUID(), null,
                null, null, null, null, null, null))));   // sourceVersion null

        assertThat(result.employeesSkipped()).isEqualTo(1);   // id null
        assertThat(result.orgUnitsApplied()).isEqualTo(1);
        assertThat(result.assignmentsSkipped()).isEqualTo(1); // sourceVersion null
        verify(orgUnits).save(any(RmOrgUnit.class));
        verify(assignments, never()).save(any());
    }

    @Test
    void coreMaster_toleratesNullSections() {
        CoreMasterBatchResponse result = service.applyCoreMaster(
            new CoreMasterBatchRequest(null, null, null));
        assertThat(result.employeesApplied()).isZero();
        assertThat(result.orgUnitsApplied()).isZero();
        assertThat(result.assignmentsApplied()).isZero();
        assertThat(result.employeesSkipped()).isZero();
        assertThat(result.orgUnitsSkipped()).isZero();
        assertThat(result.assignmentsSkipped()).isZero();
    }

    @Test
    void coreMaster_insertsOrgUnitAndAssignmentWithExternalId() {
        UUID orgId = UUID.randomUUID();
        UUID asgId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        when(orgUnits.findById(orgId)).thenReturn(Optional.empty());
        when(assignments.findById(asgId)).thenReturn(Optional.empty());

        CoreMasterBatchResponse result = service.applyCoreMaster(new CoreMasterBatchRequest(
            List.of(),
            List.of(new OrgUnitUpsert(orgId, "ORG-9", "영업본부", null, "DIVISION", 3L)),
            List.of(new AssignmentUpsert(asgId, empId, orgId, "MGR", "G3", "SALES.MGR",
                LocalDate.of(2026, 1, 1), null, 4L))));

        ArgumentCaptor<RmOrgUnit> orgCaptor = ArgumentCaptor.forClass(RmOrgUnit.class);
        verify(orgUnits).save(orgCaptor.capture());
        assertThat(orgCaptor.getValue().getId()).isEqualTo(orgId);
        assertThat(orgCaptor.getValue().getTenantId()).isEqualTo(TenantSupport.FALLBACK_TENANT_ID);
        assertThat(orgCaptor.getValue().getOrgType()).isEqualTo("DIVISION");

        ArgumentCaptor<RmAssignment> asgCaptor = ArgumentCaptor.forClass(RmAssignment.class);
        verify(assignments).save(asgCaptor.capture());
        assertThat(asgCaptor.getValue().getId()).isEqualTo(asgId);
        assertThat(asgCaptor.getValue().getEmployeeId()).isEqualTo(empId);
        assertThat(asgCaptor.getValue().getJobCode()).isEqualTo("SALES.MGR");
        assertThat(asgCaptor.getValue().getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 1));

        assertThat(result.orgUnitsApplied()).isEqualTo(1);
        assertThat(result.assignmentsApplied()).isEqualTo(1);
    }
}

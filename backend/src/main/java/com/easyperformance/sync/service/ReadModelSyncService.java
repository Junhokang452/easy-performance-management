/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.sync.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * rm_* Read Model S2S 수신 upsert — <b>rm 테이블 쓰기 권한을 가진 유일한 application 진입점</b>
 * (talent ReadModelSyncService `6022d5e` / store-hr `d95bf62` 패턴, P0-S6 core-master 채널).
 *
 * <p>idempotency 규칙:
 * <ul>
 *   <li>row 부재 — insert (id = SoR 식별자 외부 주입 + tenant_id 는 수신 시점 컨텍스트)</li>
 *   <li>수신 sourceVersion &gt; 기존 — update + sync 메타 갱신</li>
 *   <li>수신 sourceVersion &lt;= 기존 — skip (재전송 안전)</li>
 *   <li>id 또는 sourceVersion null — skip (불량 row 가 배치 전체를 깨지 않는다)</li>
 * </ul>
 */
@Service
public class ReadModelSyncService {

    private static final Logger log = LoggerFactory.getLogger(ReadModelSyncService.class);

    private final RmEmployeeRepository employees;
    private final RmOrgUnitRepository orgUnits;
    private final RmAssignmentRepository assignments;

    public ReadModelSyncService(
            RmEmployeeRepository employees,
            RmOrgUnitRepository orgUnits,
            RmAssignmentRepository assignments) {
        this.employees = employees;
        this.orgUnits = orgUnits;
        this.assignments = assignments;
    }

    @Transactional
    public CoreMasterBatchResponse applyCoreMaster(CoreMasterBatchRequest batch) {
        UUID tenantId = TenantSupport.currentTenantId();
        OffsetDateTime now = OffsetDateTime.now();
        int[] emp = applyEmployees(batch.employees(), tenantId, now);
        int[] org = applyOrgUnits(batch.orgUnits(), tenantId, now);
        int[] asg = applyAssignments(batch.assignments(), tenantId, now);
        CoreMasterBatchResponse result = new CoreMasterBatchResponse(
            emp[0], emp[1], org[0], org[1], asg[0], asg[1]);
        log.info("[performance-sync:core-master] employees {}/{} orgUnits {}/{} assignments {}/{} (applied/skipped)",
            emp[0], emp[1], org[0], org[1], asg[0], asg[1]);
        return result;
    }

    private int[] applyEmployees(List<EmployeeUpsert> rows, UUID tenantId, OffsetDateTime now) {
        int applied = 0;
        int skipped = 0;
        for (EmployeeUpsert row : rows == null ? List.<EmployeeUpsert>of() : rows) {
            if (row.id() == null || row.sourceVersion() == null) {
                skipped++;
                continue;
            }
            RmEmployee existing = employees.findById(row.id()).orElse(null);
            if (existing == null) {
                RmEmployee created = new RmEmployee();
                created.setId(row.id());
                created.setTenantId(tenantId);
                created.setEmployeeNo(row.employeeNo());
                created.setName(row.name());
                created.setStatus(row.status());
                created.setOrgUnitId(row.orgUnitId());
                created.setEmploymentType(row.employmentType());
                created.setSourceVersion(row.sourceVersion());
                created.setSyncedAt(now);
                employees.save(created);
                applied++;
            } else if (row.sourceVersion() > existing.getSourceVersion()) {
                existing.setEmployeeNo(row.employeeNo());
                existing.setName(row.name());
                existing.setStatus(row.status());
                existing.setOrgUnitId(row.orgUnitId());
                existing.setEmploymentType(row.employmentType());
                existing.setSourceVersion(row.sourceVersion());
                existing.setSyncedAt(now);
                employees.save(existing);
                applied++;
            } else {
                skipped++;
            }
        }
        return new int[] {applied, skipped};
    }

    private int[] applyOrgUnits(List<OrgUnitUpsert> rows, UUID tenantId, OffsetDateTime now) {
        int applied = 0;
        int skipped = 0;
        for (OrgUnitUpsert row : rows == null ? List.<OrgUnitUpsert>of() : rows) {
            if (row.id() == null || row.sourceVersion() == null) {
                skipped++;
                continue;
            }
            RmOrgUnit existing = orgUnits.findById(row.id()).orElse(null);
            if (existing == null) {
                RmOrgUnit created = new RmOrgUnit();
                created.setId(row.id());
                created.setTenantId(tenantId);
                created.setCode(row.code());
                created.setName(row.name());
                created.setParentId(row.parentId());
                created.setOrgType(row.orgType());
                created.setSourceVersion(row.sourceVersion());
                created.setSyncedAt(now);
                orgUnits.save(created);
                applied++;
            } else if (row.sourceVersion() > existing.getSourceVersion()) {
                existing.setCode(row.code());
                existing.setName(row.name());
                existing.setParentId(row.parentId());
                existing.setOrgType(row.orgType());
                existing.setSourceVersion(row.sourceVersion());
                existing.setSyncedAt(now);
                orgUnits.save(existing);
                applied++;
            } else {
                skipped++;
            }
        }
        return new int[] {applied, skipped};
    }

    private int[] applyAssignments(List<AssignmentUpsert> rows, UUID tenantId, OffsetDateTime now) {
        int applied = 0;
        int skipped = 0;
        for (AssignmentUpsert row : rows == null ? List.<AssignmentUpsert>of() : rows) {
            if (row.id() == null || row.sourceVersion() == null) {
                skipped++;
                continue;
            }
            RmAssignment existing = assignments.findById(row.id()).orElse(null);
            if (existing == null) {
                RmAssignment created = new RmAssignment();
                created.setId(row.id());
                created.setTenantId(tenantId);
                created.setEmployeeId(row.employeeId());
                created.setOrgUnitId(row.orgUnitId());
                created.setPositionCode(row.positionCode());
                created.setGradeCode(row.gradeCode());
                created.setJobCode(row.jobCode());
                created.setEffectiveFrom(row.effectiveFrom());
                created.setEffectiveTo(row.effectiveTo());
                created.setSourceVersion(row.sourceVersion());
                created.setSyncedAt(now);
                assignments.save(created);
                applied++;
            } else if (row.sourceVersion() > existing.getSourceVersion()) {
                existing.setEmployeeId(row.employeeId());
                existing.setOrgUnitId(row.orgUnitId());
                existing.setPositionCode(row.positionCode());
                existing.setGradeCode(row.gradeCode());
                existing.setJobCode(row.jobCode());
                existing.setEffectiveFrom(row.effectiveFrom());
                existing.setEffectiveTo(row.effectiveTo());
                existing.setSourceVersion(row.sourceVersion());
                existing.setSyncedAt(now);
                assignments.save(existing);
                applied++;
            } else {
                skipped++;
            }
        }
        return new int[] {applied, skipped};
    }
}

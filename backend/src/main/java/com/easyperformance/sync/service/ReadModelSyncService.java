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
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import jakarta.persistence.EntityManager;

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
    private final EntityManager entityManager;

    public ReadModelSyncService(
            RmEmployeeRepository employees,
            RmOrgUnitRepository orgUnits,
            RmAssignmentRepository assignments,
            EntityManager entityManager) {
        this.employees = employees;
        this.orgUnits = orgUnits;
        this.assignments = assignments;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CoreMasterBatchResponse applyCoreMaster(CoreMasterBatchRequest batch) {
        return applyCoreMaster(batch, false);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CoreMasterBatchResponse applyCoreMaster(CoreMasterBatchRequest batch, boolean extendedAssignmentCapability) {
        UUID tenantId = TenantSupport.currentTenantId();
        OffsetDateTime now = OffsetDateTime.now();
        int[] emp = applyEmployees(batch.employees(), tenantId, now);
        int[] org = applyOrgUnits(batch.orgUnits(), tenantId, now);
        int[] asg = applyAssignments(batch.assignments(), tenantId, now, extendedAssignmentCapability);
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
            if (row.id() == null || row.sourceVersion() == null || row.sourceVersion() <= 0) {
                skipped++;
                continue;
            }
            RmEmployee existing = employees.findByIdAndTenantId(row.id(), tenantId).orElse(null);
            if (existing == null) {
                rejectCrossTenantId(employees.existsById(row.id()), "employee", row.id());
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
                entityManager.persist(created);
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
            if (row.id() == null || row.sourceVersion() == null || row.sourceVersion() <= 0) {
                skipped++;
                continue;
            }
            RmOrgUnit existing = orgUnits.findByIdAndTenantId(row.id(), tenantId).orElse(null);
            if (existing == null) {
                rejectCrossTenantId(orgUnits.existsById(row.id()), "orgUnit", row.id());
                RmOrgUnit created = new RmOrgUnit();
                created.setId(row.id());
                created.setTenantId(tenantId);
                created.setCode(row.code());
                created.setName(row.name());
                created.setParentId(row.parentId());
                created.setOrgType(row.orgType());
                created.setSourceVersion(row.sourceVersion());
                created.setSyncedAt(now);
                entityManager.persist(created);
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

    private int[] applyAssignments(List<AssignmentUpsert> rows, UUID tenantId, OffsetDateTime now,
                                   boolean extendedAssignmentCapability) {
        int applied = 0;
        int skipped = 0;
        for (AssignmentUpsert row : rows == null ? List.<AssignmentUpsert>of() : rows) {
            if (row.id() == null || row.sourceVersion() == null || row.sourceVersion() <= 0
                    || (extendedAssignmentCapability && (row.effectiveFrom() == null
                        || (row.effectiveTo() != null && row.effectiveTo().isBefore(row.effectiveFrom()))))) {
                skipped++;
                continue;
            }
            RmAssignment existing = assignments.findByIdAndTenantId(row.id(), tenantId).orElse(null);
            if (existing != null && !extendedAssignmentCapability
                    && "HCM".equals(existing.getSourceSystem())) {
                skipped++;
                continue;
            }
            if (existing == null) {
                rejectCrossTenantId(assignments.existsById(row.id()), "assignment", row.id());
                RmAssignment created = new RmAssignment();
                created.setId(row.id());
                created.setTenantId(tenantId);
                created.setEmployeeId(row.employeeId());
                created.setOrgUnitId(row.orgUnitId());
                created.setPositionCode(row.positionCode());
                created.setGradeCode(row.gradeCode());
                created.setJobCode(row.jobCode());
                created.setManagerEmployeeId(extendedAssignmentCapability ? row.managerEmployeeId() : null);
                created.setEffectiveFrom(row.effectiveFrom());
                created.setEffectiveTo(row.effectiveTo());
                created.setDeleted(extendedAssignmentCapability ? row.deleted() : null);
                created.setSourceSystem(extendedAssignmentCapability ? "HCM" : "LEGACY");
                created.setSourceVersion(row.sourceVersion());
                created.setSyncedAt(now);
                entityManager.persist(created);
                applied++;
            } else if (row.sourceVersion() > existing.getSourceVersion()) {
                existing.setEmployeeId(row.employeeId());
                existing.setOrgUnitId(row.orgUnitId());
                existing.setPositionCode(row.positionCode());
                existing.setGradeCode(row.gradeCode());
                existing.setJobCode(row.jobCode());
                existing.setManagerEmployeeId(extendedAssignmentCapability ? row.managerEmployeeId() : null);
                existing.setEffectiveFrom(row.effectiveFrom());
                existing.setEffectiveTo(row.effectiveTo());
                existing.setDeleted(extendedAssignmentCapability ? row.deleted() : null);
                existing.setSourceSystem(extendedAssignmentCapability ? "HCM" : "LEGACY");
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

    private static void rejectCrossTenantId(boolean existsOutsideTenant, String type, UUID id) {
        if (existsOutsideTenant) {
            throw new ApiException(PerformanceErrorCode.SYNC_TENANT_MISMATCH,
                java.util.Map.of("reason", "ROW_ID_OWNED_BY_OTHER_TENANT", "type", type, "id", id));
        }
    }
}

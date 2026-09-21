package com.easyperformance.resources;

import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.resources.ResourceDtos.DepartmentGoalActualRequest;
import com.easyperformance.resources.ResourceDtos.DepartmentGoalCopyRequest;
import com.easyperformance.resources.ResourceDtos.DepartmentGoalResponse;
import com.easyperformance.resources.ResourceDtos.DepartmentGoalTransferRequest;
import com.easyperformance.resources.ResourceDtos.DepartmentGoalUpsertRequest;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DepartmentGoalService {
    private final DepartmentGoalRepository goals;
    private final EvaluationCatalogRepository catalogs;
    private final CatalogLevelRepository levels;
    private final RmEmployeeRepository employees;
    private final ResourceLookupService lookups;

    public DepartmentGoalService(DepartmentGoalRepository goals, EvaluationCatalogRepository catalogs,
                                 CatalogLevelRepository levels, RmEmployeeRepository employees, ResourceLookupService lookups) {
        this.goals = goals; this.catalogs = catalogs; this.levels = levels;
        this.employees = employees; this.lookups = lookups;
    }

    @Transactional(readOnly = true)
    public List<DepartmentGoalResponse> list(UUID tenantId, UUID employeeId, boolean admin, UUID departmentId) {
        UUID visibleDepartment = admin ? departmentId : employee(tenantId, employeeId).getOrgUnitId();
        List<DepartmentGoal> found = visibleDepartment == null
            ? goals.findAllByTenantIdOrderByYearDescCreatedAtDesc(tenantId)
            : goals.findAllByTenantIdAndDepartmentIdOrderByYearDescCreatedAtDesc(tenantId, visibleDepartment);
        return found.stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentGoalResponse get(UUID tenantId, UUID employeeId, boolean admin, UUID id) {
        DepartmentGoal goal = require(tenantId, id);
        if (!admin && !goal.getDepartmentId().equals(employee(tenantId, employeeId).getOrgUnitId())) forbidden();
        return response(goal);
    }

    @Transactional
    public DepartmentGoalResponse create(UUID tenantId, DepartmentGoalUpsertRequest request) {
        validate(tenantId, request);
        DepartmentGoal goal = new DepartmentGoal(); goal.setTenantId(tenantId); apply(goal, request);
        return response(goals.save(goal));
    }

    @Transactional
    public DepartmentGoalResponse update(UUID tenantId, UUID id, DepartmentGoalUpsertRequest request) {
        validate(tenantId, request); DepartmentGoal goal = require(tenantId, id); apply(goal, request);
        return response(goal);
    }

    @Transactional
    public DepartmentGoalResponse copy(UUID tenantId, UUID id, DepartmentGoalCopyRequest request) {
        DepartmentGoal source = require(tenantId, id); DepartmentGoal target = cloneOf(source);
        lookups.requireDepartment(tenantId, request.departmentId());
        target.setTenantId(tenantId); target.setYear(request.year()); target.setPeriodStart(request.periodStart());
        target.setPeriodEnd(request.periodEnd()); target.setDepartmentId(request.departmentId());
        target.setCopiedFromId(source.getId()); target.setActualValue(null); target.setAchievementRate(null); target.setActualNote(null);
        validatePeriod(target.getPeriodStart(), target.getPeriodEnd());
        return response(goals.save(target));
    }

    @Transactional
    public DepartmentGoalResponse transfer(UUID tenantId, UUID id, DepartmentGoalTransferRequest request) {
        DepartmentGoal source = require(tenantId, id); DepartmentGoal target = cloneOf(source);
        lookups.requireDepartment(tenantId, request.targetDepartmentId());
        target.setTenantId(tenantId); target.setDepartmentId(request.targetDepartmentId());
        target.setTransferredFromId(source.getId()); target.setActualNote("TRANSFER: " + request.reason().trim());
        return response(goals.save(target));
    }

    @Transactional
    public DepartmentGoalResponse actual(UUID tenantId, UUID id, DepartmentGoalActualRequest request) {
        DepartmentGoal goal = require(tenantId, id); goal.setActualValue(request.actualValue());
        goal.setAchievementRate(request.achievementRate()); goal.setActualNote(trim(request.note())); return response(goal);
    }

    DepartmentGoal require(UUID tenantId, UUID id) {
        return goals.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_NOT_FOUND));
    }

    RmEmployee employee(UUID tenantId, UUID employeeId) {
        return employees.findByIdAndTenantId(employeeId, tenantId)
            .filter(e -> "ACTIVE".equalsIgnoreCase(e.getStatus()))
            .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_FORBIDDEN));
    }

    private void validate(UUID tenantId, DepartmentGoalUpsertRequest request) {
        validatePeriod(request.periodStart(), request.periodEnd());
        lookups.requireDepartment(tenantId, request.departmentId());
        if (request.catalogId() != null) {
            EvaluationCatalogItem catalog = catalogs.findByIdAndTenantId(request.catalogId(), tenantId)
                .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_NOT_FOUND));
            if (!catalog.isActive() || catalog.getKind() != ResourceDtos.CatalogKind.PERFORMANCE
                || levels.findAllByTenantIdAndCatalogIdOrderByDisplayOrderAsc(tenantId, catalog.getId()).stream()
                    .noneMatch(level -> level.getCode().equals(request.targetLevel()))) {
                throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
            }
        }
    }

    private static void validatePeriod(java.time.LocalDate start, java.time.LocalDate end) {
        if (end.isBefore(start)) throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
    }

    private static void apply(DepartmentGoal goal, DepartmentGoalUpsertRequest r) {
        goal.setYear(r.year()); goal.setPeriodStart(r.periodStart()); goal.setPeriodEnd(r.periodEnd());
        goal.setDepartmentId(r.departmentId()); goal.setCatalogId(r.catalogId()); goal.setTitle(r.title().trim());
        goal.setDefinition(r.definition().trim()); goal.setWeight(r.weight()); goal.setTargetLevel(r.targetLevel().trim());
        goal.setUnit(r.unit().trim());
    }

    private static DepartmentGoal cloneOf(DepartmentGoal source) {
        DepartmentGoal target = new DepartmentGoal(); target.setCatalogId(source.getCatalogId()); target.setTitle(source.getTitle());
        target.setDefinition(source.getDefinition()); target.setWeight(source.getWeight()); target.setTargetLevel(source.getTargetLevel());
        target.setUnit(source.getUnit()); target.setYear(source.getYear()); target.setPeriodStart(source.getPeriodStart());
        target.setPeriodEnd(source.getPeriodEnd()); target.setDepartmentId(source.getDepartmentId()); return target;
    }

    private DepartmentGoalResponse response(DepartmentGoal g) {
        return new DepartmentGoalResponse(g.getId(), g.getYear(), g.getPeriodStart(), g.getPeriodEnd(), g.getDepartmentId(),
            g.getCatalogId(), g.getTitle(), g.getDefinition(), g.getWeight(), g.getTargetLevel(), g.getUnit(),
            g.getActualValue(), g.getAchievementRate(), g.getActualNote(), g.getCopiedFromId(), g.getTransferredFromId(),
            g.getRowVersion(), g.getCreatedAt(), g.getUpdatedAt());
    }

    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static void forbidden() { throw new ApiException(ResourceErrorCode.RESOURCE_FORBIDDEN); }
}

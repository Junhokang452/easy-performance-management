package com.easyperformance.resources;

import com.easyperformance.readmodel.entity.RmOrgUnit;
import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.entity.RmAssignment;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.resources.ResourceDtos.EmployeeOptionResponse;
import com.easyperformance.resources.ResourceDtos.LookupOptionResponse;
import com.easyperformance.resources.ResourceDtos.CatalogAssignmentType;
import com.easyware.platform.error.ApiException;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class ResourceLookupService {
    private final EntityManager entityManager;
    private final RmEmployeeRepository employees;

    public ResourceLookupService(EntityManager entityManager, RmEmployeeRepository employees) {
        this.entityManager = entityManager; this.employees = employees;
    }

    @Transactional(readOnly = true)
    public List<EmployeeOptionResponse> employees(UUID tenantId, String query) {
        return employees.searchActive(tenantId, normalize(query), PageRequest.of(0, 50)).stream()
            .map(e -> new EmployeeOptionResponse(e.getId(), e.getEmployeeNo(), e.getName(), e.getOrgUnitId())).toList();
    }

    @Transactional(readOnly = true)
    public List<LookupOptionResponse> departments(UUID tenantId, String query) {
        String q = normalize(query).toLowerCase();
        return entityManager.createQuery("select o from RmOrgUnit o where o.tenantId = :tenantId and "
                + "(:q = '' or lower(o.name) like concat('%', :q, '%') or lower(o.code) like concat('%', :q, '%')) order by o.name", RmOrgUnit.class)
            .setParameter("tenantId", tenantId).setParameter("q", q).setMaxResults(50).getResultList().stream()
            .map(o -> new LookupOptionResponse(o.getId().toString(), o.getName(), o.getCode())).toList();
    }

    @Transactional(readOnly = true)
    public List<LookupOptionResponse> jobs(UUID tenantId, String query) {
        String q = normalize(query).toLowerCase();
        return entityManager.createQuery("select distinct a.jobCode from RmAssignment a where a.tenantId = :tenantId "
                + "and a.jobCode is not null and (:q = '' or lower(a.jobCode) like concat('%', :q, '%')) order by a.jobCode", String.class)
            .setParameter("tenantId", tenantId).setParameter("q", q).setMaxResults(50).getResultList().stream()
            .map(code -> new LookupOptionResponse(code, code, null)).toList();
    }

    @Transactional(readOnly = true)
    public List<LookupOptionResponse> conditionValues(UUID tenantId, String field, String query, List<String> values) {
        String normalizedField = normalize(field).toUpperCase(Locale.ROOT);
        if (!Set.of("ORG_UNIT", "POSITION", "GRADE", "JOB", "EMPLOYMENT_TYPE", "EMPLOYEE").contains(normalizedField)) {
            throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
        }
        List<String> selected = selectedValues(values);
        if (!selected.isEmpty()) return selectedConditionValues(tenantId, normalizedField, selected);
        return switch (normalizedField) {
            case "ORG_UNIT" -> departments(tenantId, query);
            case "EMPLOYEE" -> employees(tenantId, query).stream()
                .map(e -> new LookupOptionResponse(e.id().toString(), e.name(), e.employeeNo())).toList();
            case "POSITION" -> assignmentCodes(tenantId, "positionCode", query, List.of());
            case "GRADE" -> assignmentCodes(tenantId, "gradeCode", query, List.of());
            case "JOB" -> assignmentCodes(tenantId, "jobCode", query, List.of());
            case "EMPLOYMENT_TYPE" -> employmentTypes(tenantId, query, List.of());
            default -> throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
        };
    }

    @Transactional(readOnly = true)
    public List<LookupOptionResponse> assignments(UUID tenantId, UUID employeeId) {
        employees.findByIdAndTenantId(employeeId, tenantId)
            .filter(employee -> "ACTIVE".equalsIgnoreCase(employee.getStatus()))
            .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_INVALID));
        List<RmAssignment> rows = entityManager.createQuery("select a from RmAssignment a where a.tenantId = :tenantId "
                + "and a.employeeId = :employeeId order by a.effectiveFrom desc, a.id", RmAssignment.class)
            .setParameter("tenantId", tenantId).setParameter("employeeId", employeeId).setMaxResults(50).getResultList();
        Set<UUID> orgIds = rows.stream().map(RmAssignment::getOrgUnitId).filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> orgNames = orgIds.isEmpty() ? Map.of() : entityManager
            .createQuery("select o from RmOrgUnit o where o.tenantId = :tenantId and o.id in :ids", RmOrgUnit.class)
            .setParameter("tenantId", tenantId).setParameter("ids", orgIds).getResultList().stream()
            .collect(Collectors.toMap(RmOrgUnit::getId, RmOrgUnit::getName));
        return rows.stream().map(a -> new LookupOptionResponse(a.getId().toString(),
            label(orgNames.get(a.getOrgUnitId()), a.getPositionCode(), a.getJobCode()), a.getGradeCode())).toList();
    }

    private List<LookupOptionResponse> selectedConditionValues(UUID tenantId, String field, List<String> selected) {
        if (field.equals("ORG_UNIT")) {
            List<UUID> ids = uuids(selected);
            return entityManager.createQuery("select o from RmOrgUnit o where o.tenantId = :tenantId and o.id in :ids order by o.name", RmOrgUnit.class)
                .setParameter("tenantId", tenantId).setParameter("ids", ids).setMaxResults(50).getResultList().stream()
                .map(o -> new LookupOptionResponse(o.getId().toString(), o.getName(), o.getCode())).toList();
        }
        if (field.equals("EMPLOYEE")) {
            List<UUID> ids = uuids(selected);
            return entityManager.createQuery("select e from RmEmployee e where e.tenantId = :tenantId and upper(e.status) = 'ACTIVE' "
                    + "and e.id in :ids order by e.name", RmEmployee.class)
                .setParameter("tenantId", tenantId).setParameter("ids", ids).setMaxResults(50).getResultList().stream()
                .map(e -> new LookupOptionResponse(e.getId().toString(), e.getName(), e.getEmployeeNo())).toList();
        }
        return switch (field) {
            case "POSITION" -> assignmentCodes(tenantId, "positionCode", "", selected);
            case "GRADE" -> assignmentCodes(tenantId, "gradeCode", "", selected);
            case "JOB" -> assignmentCodes(tenantId, "jobCode", "", selected);
            case "EMPLOYMENT_TYPE" -> employmentTypes(tenantId, "", selected);
            default -> throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
        };
    }

    private List<LookupOptionResponse> assignmentCodes(UUID tenantId, String property, String query, List<String> selected) {
        String q = normalize(query).toLowerCase(Locale.ROOT);
        String exact = selected.isEmpty() ? "" : " and a." + property + " in :values";
        var typedQuery = entityManager.createQuery("select distinct a." + property + " from RmAssignment a where a.tenantId = :tenantId "
            + "and a." + property + " is not null and a." + property + " <> ''"
            + exact + " and (:q = '' or lower(a." + property + ") like concat('%', :q, '%')) order by a." + property, String.class)
            .setParameter("tenantId", tenantId).setParameter("q", q);
        if (!selected.isEmpty()) typedQuery.setParameter("values", selected);
        return typedQuery.setMaxResults(50).getResultList().stream()
            .map(code -> new LookupOptionResponse(code, code, null)).toList();
    }

    private List<LookupOptionResponse> employmentTypes(UUID tenantId, String query, List<String> selected) {
        String q = normalize(query).toLowerCase(Locale.ROOT);
        String exact = selected.isEmpty() ? "" : " and e.employmentType in :values";
        var typedQuery = entityManager.createQuery("select distinct e.employmentType from RmEmployee e where e.tenantId = :tenantId "
            + "and upper(e.status) = 'ACTIVE' and e.employmentType is not null and e.employmentType <> ''"
            + exact + " and (:q = '' or lower(e.employmentType) like concat('%', :q, '%')) order by e.employmentType", String.class)
            .setParameter("tenantId", tenantId).setParameter("q", q);
        if (!selected.isEmpty()) typedQuery.setParameter("values", selected);
        return typedQuery.setMaxResults(50).getResultList().stream()
            .map(value -> new LookupOptionResponse(value, value, null)).toList();
    }

    private static List<String> selectedValues(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        values.stream().filter(java.util.Objects::nonNull).flatMap(value -> java.util.Arrays.stream(value.split(",")))
            .map(String::trim).filter(value -> !value.isEmpty()).forEach(selected::add);
        if (selected.size() > 50) throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
        return List.copyOf(selected);
    }

    private static List<UUID> uuids(List<String> values) {
        try { return values.stream().map(UUID::fromString).toList(); }
        catch (IllegalArgumentException exception) { throw new ApiException(ResourceErrorCode.RESOURCE_INVALID); }
    }

    private static String label(String... parts) {
        String joined = java.util.Arrays.stream(parts).filter(Objects::nonNull).map(String::trim)
            .filter(value -> !value.isEmpty()).collect(Collectors.joining(" / "));
        return joined.isEmpty() ? "-" : joined;
    }

    @Transactional(readOnly = true)
    public void requireDepartment(UUID tenantId, UUID departmentId) {
        Long count = entityManager.createQuery("select count(o) from RmOrgUnit o where o.tenantId = :tenantId and o.id = :id", Long.class)
            .setParameter("tenantId", tenantId).setParameter("id", departmentId).getSingleResult();
        if (count == 0) throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
    }

    @Transactional(readOnly = true)
    public void requireAssignmentReference(UUID tenantId, CatalogAssignmentType type, String reference) {
        if (type == CatalogAssignmentType.DEPARTMENT) {
            try { requireDepartment(tenantId, UUID.fromString(reference)); }
            catch (IllegalArgumentException exception) { throw new ApiException(ResourceErrorCode.RESOURCE_INVALID); }
            return;
        }
        Long count = entityManager.createQuery("select count(a) from RmAssignment a where a.tenantId = :tenantId and a.jobCode = :code "
                + "and (a.effectiveTo is null or a.effectiveTo >= current_date)", Long.class)
            .setParameter("tenantId", tenantId).setParameter("code", reference).getSingleResult();
        if (count == 0) throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
    }

    private static String normalize(String value) { return value == null ? "" : value.trim(); }
}

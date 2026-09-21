package com.easyperformance.resources;

import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.entity.RmAssignment;
import com.easyperformance.readmodel.entity.RmOrgUnit;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyware.platform.error.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceLookupServiceTest {
    @Mock EntityManager entityManager;
    @Mock RmEmployeeRepository employees;
    @Mock TypedQuery<String> stringQuery;
    @Mock TypedQuery<RmAssignment> assignmentQuery;
    @Mock TypedQuery<RmOrgUnit> orgUnitQuery;
    ResourceLookupService service;

    @BeforeEach void setUp() { service = new ResourceLookupService(entityManager, employees); }

    @Test void employeeSearchIsTenantScopedAndReturnsDisplayLabels() {
        UUID tenantId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID();
        RmEmployee employee = new RmEmployee(); employee.setId(employeeId); employee.setEmployeeNo("E-100"); employee.setName("Nguyen An");
        when(employees.searchActive(tenantId, "an", PageRequest.of(0, 50))).thenReturn(new PageImpl<>(List.of(employee)));

        var result = service.conditionValues(tenantId, "EMPLOYEE", "an", null);

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.value()).isEqualTo(employeeId.toString());
            assertThat(option.label()).isEqualTo("Nguyen An");
            assertThat(option.description()).isEqualTo("E-100");
        });
        verify(employees).searchActive(tenantId, "an", PageRequest.of(0, 50));
    }

    @Test void selectedScalarValuesUseExactTenantScopedQueryAndIgnoreSearchPage() {
        UUID tenantId = UUID.randomUUID();
        when(entityManager.createQuery(contains("a.gradeCode in :values"), eq(String.class))).thenReturn(stringQuery);
        when(stringQuery.setParameter(anyString(), any())).thenReturn(stringQuery);
        when(stringQuery.setMaxResults(50)).thenReturn(stringQuery);
        when(stringQuery.getResultList()).thenReturn(List.of("G5", "G6"));

        var result = service.conditionValues(tenantId, "GRADE", "unrelated", List.of("G5,G6", "G5"));

        assertThat(result).extracting(ResourceDtos.LookupOptionResponse::value).containsExactly("G5", "G6");
        verify(stringQuery).setParameter("tenantId", tenantId);
        verify(stringQuery).setParameter("values", List.of("G5", "G6"));
        verify(stringQuery).setParameter("q", "");
        verify(stringQuery).setMaxResults(50);
    }

    @Test void rejectsUnsupportedFieldAndMoreThanFiftySelectedValues() {
        UUID tenantId = UUID.randomUUID();
        assertThatThrownBy(() -> service.conditionValues(tenantId, "SALARY", "", null)).isInstanceOf(ApiException.class);
        List<String> tooMany = java.util.stream.IntStream.range(0, 51).mapToObj(i -> "V" + i).toList();
        assertThatThrownBy(() -> service.conditionValues(tenantId, "JOB", "", tooMany)).isInstanceOf(ApiException.class);
        verifyNoInteractions(entityManager, employees);
    }

    @Test void assignmentOptionsValidateEmployeeAndResolveTenantDepartmentLabel() {
        UUID tenantId = UUID.randomUUID(); UUID employeeId = UUID.randomUUID(); UUID assignmentId = UUID.randomUUID(); UUID orgId = UUID.randomUUID();
        RmEmployee employee = new RmEmployee(); employee.setId(employeeId); employee.setStatus("ACTIVE");
        RmAssignment assignment = new RmAssignment(); assignment.setId(assignmentId); assignment.setEmployeeId(employeeId);
        assignment.setOrgUnitId(orgId); assignment.setPositionCode("LEAD"); assignment.setJobCode("ENG"); assignment.setGradeCode("G6");
        RmOrgUnit org = new RmOrgUnit(); org.setId(orgId); org.setName("Product");
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(java.util.Optional.of(employee));
        when(entityManager.createQuery(contains("from RmAssignment"), eq(RmAssignment.class))).thenReturn(assignmentQuery);
        when(assignmentQuery.setParameter(anyString(), any())).thenReturn(assignmentQuery);
        when(assignmentQuery.setMaxResults(50)).thenReturn(assignmentQuery);
        when(assignmentQuery.getResultList()).thenReturn(List.of(assignment));
        when(entityManager.createQuery(contains("from RmOrgUnit"), eq(RmOrgUnit.class))).thenReturn(orgUnitQuery);
        when(orgUnitQuery.setParameter(anyString(), any())).thenReturn(orgUnitQuery);
        when(orgUnitQuery.getResultList()).thenReturn(List.of(org));

        var result = service.assignments(tenantId, employeeId);

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.value()).isEqualTo(assignmentId.toString());
            assertThat(option.label()).isEqualTo("Product / LEAD / ENG");
            assertThat(option.description()).isEqualTo("G6");
        });
        verify(assignmentQuery).setParameter("tenantId", tenantId);
        verify(assignmentQuery).setParameter("employeeId", employeeId);
        verify(orgUnitQuery).setParameter("tenantId", tenantId);
    }
}

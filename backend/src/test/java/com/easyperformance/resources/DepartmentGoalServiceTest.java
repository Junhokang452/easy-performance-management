package com.easyperformance.resources;

import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyware.platform.error.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DepartmentGoalServiceTest {
    @Test
    void memberCannotReadAnotherDepartmentsGoal() {
        DepartmentGoalRepository goals = mock(DepartmentGoalRepository.class);
        RmEmployeeRepository employees = mock(RmEmployeeRepository.class);
        DepartmentGoalService service = new DepartmentGoalService(goals, mock(EvaluationCatalogRepository.class),
            mock(CatalogLevelRepository.class), employees,
            mock(ResourceLookupService.class));
        UUID tenantId = UUID.randomUUID(), employeeId = UUID.randomUUID(), goalId = UUID.randomUUID();
        RmEmployee employee = new RmEmployee(); employee.setId(employeeId); employee.setTenantId(tenantId);
        employee.setOrgUnitId(UUID.randomUUID()); employee.setStatus("ACTIVE");
        DepartmentGoal goal = new DepartmentGoal(); goal.setId(goalId); goal.setTenantId(tenantId); goal.setDepartmentId(UUID.randomUUID());
        when(goals.findByIdAndTenantId(goalId, tenantId)).thenReturn(Optional.of(goal));
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service.get(tenantId, employeeId, false, goalId)).isInstanceOfSatisfying(ApiException.class,
            ex -> org.assertj.core.api.Assertions.assertThat(ex.errorCode()).isEqualTo(ResourceErrorCode.RESOURCE_FORBIDDEN));
    }
}

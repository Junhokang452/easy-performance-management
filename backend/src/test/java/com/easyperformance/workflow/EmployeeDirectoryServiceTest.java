package com.easyperformance.workflow;

import com.easyware.platform.UuidV7;
import com.easyperformance.domain.account.PerformanceUserRepository;
import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.readmodel.repository.RmOrgUnitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeDirectoryServiceTest {
    @Test
    void search_returnsTenantScopedActiveEmployeePage() {
        UUID tenantId = UuidV7.generate();
        RmEmployeeRepository employees = mock(RmEmployeeRepository.class);
        RmOrgUnitRepository orgUnits = mock(RmOrgUnitRepository.class);
        PerformanceUserRepository users = mock(PerformanceUserRepository.class);
        RmEmployee employee = new RmEmployee();
        employee.setId(UuidV7.generate());
        employee.setTenantId(tenantId);
        employee.setEmployeeNo("E-001");
        employee.setName("Kim");
        employee.setStatus("ACTIVE");
        var pageable = PageRequest.of(0, 20);
        when(employees.searchActive(tenantId, "kim", pageable))
            .thenReturn(new PageImpl<>(List.of(employee), pageable, 1));
        when(users.existsByTenantIdAndEmployeeId(tenantId, employee.getId())).thenReturn(true);
        EmployeeDirectoryService service = new EmployeeDirectoryService(employees, orgUnits, users);

        var result = service.search(tenantId, " kim ", pageable);

        assertThat(result.getContent()).singleElement().satisfies(row -> {
            assertThat(row.name()).isEqualTo("Kim");
            assertThat(row.hasUserBinding()).isTrue();
        });
    }
}

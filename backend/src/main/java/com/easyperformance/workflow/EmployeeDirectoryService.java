package com.easyperformance.workflow;

import com.easyperformance.domain.account.PerformanceUserRepository;
import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.readmodel.repository.RmOrgUnitRepository;
import com.easyperformance.workflow.ParticipantRosterDtos.EmployeeSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EmployeeDirectoryService {
    private final RmEmployeeRepository employees;
    private final RmOrgUnitRepository orgUnits;
    private final PerformanceUserRepository users;

    public EmployeeDirectoryService(RmEmployeeRepository employees,
                                    RmOrgUnitRepository orgUnits,
                                    PerformanceUserRepository users) {
        this.employees = employees;
        this.orgUnits = orgUnits;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Page<EmployeeSummary> search(UUID tenantId, String query, Pageable pageable) {
        String normalized = query == null ? "" : query.trim();
        return employees.searchActive(tenantId, normalized, pageable).map(employee -> summary(tenantId, employee));
    }

    private EmployeeSummary summary(UUID tenantId, RmEmployee employee) {
        String orgName = employee.getOrgUnitId() == null ? null
            : orgUnits.findByIdAndTenantId(employee.getOrgUnitId(), tenantId).map(o -> o.getName()).orElse(null);
        return new EmployeeSummary(employee.getId(), employee.getEmployeeNo(), employee.getName(),
            employee.getOrgUnitId(), orgName, employee.getStatus(),
            users.existsByTenantIdAndEmployeeId(tenantId, employee.getId()));
    }
}

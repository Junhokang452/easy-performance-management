package com.easyperformance.workflow;

import com.easyware.platform.UuidV7;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.workflow.ParticipantRosterDtos.ParticipantInput;
import com.easyperformance.workflow.ParticipantRosterDtos.ParticipantReplaceRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParticipantRosterServiceTest {

    @Test
    void replaceRoster_createsParticipantAndManagerAssignment() {
        UUID tenantId = UuidV7.generate();
        UUID cycleId = UuidV7.generate();
        UUID employeeId = UuidV7.generate();
        UUID managerId = UuidV7.generate();
        EvaluationCycleRepository cycles = mock(EvaluationCycleRepository.class);
        EvaluationParticipantRepository participants = mock(EvaluationParticipantRepository.class);
        EvaluationReviewerAssignmentRepository reviewers = mock(EvaluationReviewerAssignmentRepository.class);
        RmEmployeeRepository employees = mock(RmEmployeeRepository.class);
        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setId(cycleId);
        cycle.setTenantId(tenantId);
        cycle.setStatus(CycleStatus.PLANNED);
        when(cycles.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee(employeeId, tenantId, "Employee")));
        when(employees.findByIdAndTenantId(managerId, tenantId)).thenReturn(Optional.of(employee(managerId, tenantId, "Manager")));
        when(participants.findByTenantIdAndCycleIdAndEmployeeId(tenantId, cycleId, employeeId)).thenReturn(Optional.empty());
        when(participants.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(participants.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId)).thenReturn(List.of());
        ParticipantRosterService service = new ParticipantRosterService(cycles, participants, reviewers, employees);

        ParticipantRosterDtos.ParticipantRosterResponse result = service.replaceRoster(
            tenantId, cycleId,
            new ParticipantReplaceRequest(List.of(new ParticipantInput(employeeId, managerId))));

        assertThat(result.activeCount()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.employee().id()).isEqualTo(employeeId);
            assertThat(item.manager()).isNotNull();
            assertThat(item.manager().id()).isEqualTo(managerId);
        });
    }

    @Test
    void replaceRoster_withEmptyList_excludesExistingParticipant() {
        UUID tenantId = UuidV7.generate();
        UUID cycleId = UuidV7.generate();
        EvaluationCycleRepository cycles = mock(EvaluationCycleRepository.class);
        EvaluationParticipantRepository participants = mock(EvaluationParticipantRepository.class);
        EvaluationReviewerAssignmentRepository reviewers = mock(EvaluationReviewerAssignmentRepository.class);
        RmEmployeeRepository employees = mock(RmEmployeeRepository.class);
        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setId(cycleId); cycle.setTenantId(tenantId); cycle.setStatus(CycleStatus.PLANNED);
        EvaluationParticipant existing = new EvaluationParticipant();
        existing.setId(UuidV7.generate()); existing.setTenantId(tenantId); existing.setCycleId(cycleId);
        existing.setEmployeeId(UuidV7.generate()); existing.setStatus(ParticipantStatus.ACTIVE);
        when(cycles.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(participants.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId)).thenReturn(List.of(existing));
        when(reviewers.findAllByTenantIdAndParticipantIdAndStatusNot(
            tenantId, existing.getId(), ReviewerAssignmentStatus.REVOKED)).thenReturn(List.of());
        ParticipantRosterService service = new ParticipantRosterService(cycles, participants, reviewers, employees);

        var result = service.replaceRoster(tenantId, cycleId, new ParticipantReplaceRequest(List.of()));

        assertThat(result.activeCount()).isZero();
        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(ParticipantStatus.EXCLUDED);
        verify(participants).save(existing);
    }

    private static RmEmployee employee(UUID id, UUID tenantId, String name) {
        RmEmployee employee = new RmEmployee();
        employee.setId(id);
        employee.setTenantId(tenantId);
        employee.setEmployeeNo(id.toString().substring(0, 8));
        employee.setName(name);
        employee.setStatus("ACTIVE");
        employee.setSourceVersion(1L);
        return employee;
    }
}

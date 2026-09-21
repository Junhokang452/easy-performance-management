package com.easyperformance.workflow;

import com.easyware.platform.UuidV7;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.IntermediateReviewUpsertRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntermediateReviewServiceTest {

    @Test
    void submitByParticipant_thenCompleteByAssignedManager() {
        UUID tenantId = UuidV7.generate();
        UUID cycleId = UuidV7.generate();
        UUID employeeId = UuidV7.generate();
        UUID managerId = UuidV7.generate();
        EvaluationParticipant participant = new EvaluationParticipant();
        participant.setId(UuidV7.generate());
        participant.setTenantId(tenantId);
        participant.setCycleId(cycleId);
        participant.setEmployeeId(employeeId);
        participant.setStatus(ParticipantStatus.ACTIVE);
        EvaluationReviewerAssignment assignment = new EvaluationReviewerAssignment();
        assignment.setReviewerEmployeeId(managerId);
        assignment.setStatus(ReviewerAssignmentStatus.ASSIGNED);
        EvaluationParticipantRepository participants = mock(EvaluationParticipantRepository.class);
        EvaluationReviewerAssignmentRepository reviewers = mock(EvaluationReviewerAssignmentRepository.class);
        IntermediatePerformanceReviewRepository repository = mock(IntermediatePerformanceReviewRepository.class);
        EvaluationCycleRepository cycles = mock(EvaluationCycleRepository.class);
        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setId(cycleId);
        cycle.setTenantId(tenantId);
        cycle.setStatus(CycleStatus.MID_REVIEW);
        when(cycles.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(participants.findByTenantIdAndCycleIdAndEmployeeId(tenantId, cycleId, employeeId))
            .thenReturn(Optional.of(participant));
        when(participants.findByIdAndTenantId(participant.getId(), tenantId)).thenReturn(Optional.of(participant));
        when(reviewers.findByTenantIdAndParticipantIdAndReviewerTypeAndRound(
            tenantId, participant.getId(), ReviewerType.MANAGER, 1)).thenReturn(Optional.of(assignment));
        when(repository.findByTenantIdAndParticipantId(tenantId, participant.getId())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        IntermediateReviewService service = new IntermediateReviewService(participants, reviewers, repository, cycles);
        ActorAccess.Actor employee = new ActorAccess.Actor(UuidV7.generate(), tenantId, employeeId, "Employee", "EMPLOYEE");

        var submitted = service.submit(employee, cycleId,
            new IntermediateReviewUpsertRequest("On track", "Shipped", null, null));

        assertThat(submitted.status()).isEqualTo(IntermediateReviewStatus.EMPLOYEE_SUBMITTED);
    }
}

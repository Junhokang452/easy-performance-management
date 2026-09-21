package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.ReviewerInput;
import com.easyperformance.program.ProgramDtos.ReviewerReplaceRequest;
import com.easyperformance.program.ProgramTypes.ParticipantStatus;
import com.easyperformance.program.ProgramTypes.ProgramStatus;
import com.easyperformance.program.ProgramTypes.ReviewerRole;
import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.repository.RmAssignmentRepository;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.readmodel.repository.RmOrgUnitRepository;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgramRosterConcurrencyTest {
    @Test
    void manualReplacementUsesSameProgramWriteLockAsAutomationApply() {
        UUID tenantId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        EvaluationProgramRepository programs = mock(EvaluationProgramRepository.class);
        ProgramParticipantRepository participants = mock(ProgramParticipantRepository.class);
        ProgramReviewerAssignmentRepository reviewers = mock(ProgramReviewerAssignmentRepository.class);
        RmEmployeeRepository employees = mock(RmEmployeeRepository.class);
        RmAssignmentRepository assignments = mock(RmAssignmentRepository.class);
        RmOrgUnitRepository orgs = mock(RmOrgUnitRepository.class);
        ProgramAccess access = mock(ProgramAccess.class);
        ProgramAuditService audit = mock(ProgramAuditService.class);
        ProgramJson json = new ProgramJson(new ObjectMapper().findAndRegisterModules());
        var configuration = ProgramConfigurationFactory.defaults(ProgramTypes.EvaluationKind.PERFORMANCE);
        EvaluationProgram program = new EvaluationProgram();
        program.setId(programId);
        program.setTenantId(tenantId);
        program.setStatus(ProgramStatus.DRAFT);
        program.setDefinitionJson(json.write(configuration));
        ProgramParticipant participant = new ProgramParticipant();
        participant.setId(participantId);
        participant.setTenantId(tenantId);
        participant.setProgramId(programId);
        participant.setGroupId(configuration.groups().getFirst().id());
        participant.setStatus(ParticipantStatus.ACTIVE);
        RmEmployee employee = new RmEmployee();
        employee.setId(employeeId);
        employee.setStatus("ACTIVE");
        employee.setName("Manager");
        Actor actor = new Actor(UUID.randomUUID(), tenantId, UUID.randomUUID(), "HR", "HR_ADMIN");
        when(access.participant(actor, participantId)).thenReturn(participant);
        when(access.program(actor, programId)).thenReturn(program);
        when(programs.findLocked(programId, tenantId)).thenReturn(Optional.of(program));
        when(reviewers.findAllByTenantIdAndParticipantIdAndStatusNotOrderByRoleAscRoundAsc(
            any(), any(), any())).thenReturn(List.of());
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));
        when(reviewers.save(any())).thenAnswer(call -> {
            ProgramReviewerAssignment saved = call.getArgument(0);
            saved.prePersist();
            return saved;
        });
        ProgramRosterService service = new ProgramRosterService(programs, participants, reviewers,
            employees, assignments, orgs, access, json, audit);

        service.replaceReviewers(actor, participantId, new ReviewerReplaceRequest(List.of(
            new ReviewerInput(employeeId, ReviewerRole.REVIEWER, 1, new BigDecimal("100")))));

        verify(programs).findLocked(programId, tenantId);
    }
}

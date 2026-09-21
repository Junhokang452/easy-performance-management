package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.FinalizeRequest;
import com.easyperformance.program.ProgramTypes.*;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgramLifecycleServiceTest {
    @Mock EvaluationProgramRepository programs; @Mock ProgramParticipantRepository participants;
    @Mock ProgramCalculationRepository calculations; @Mock ProgramAdjustmentRepository adjustments;
    @Mock ProgramFeedbackRepository feedback; @Mock ProgramAccess access; @Mock ProgramJson json;
    @Mock ProgramAuditService audit; @Mock EvaluationProgramService programService;
    ProgramLifecycleService service; UUID tenantId; UUID programId; Actor operator;

    @BeforeEach
    void setUp() {
        service = new ProgramLifecycleService(programs, participants, calculations, adjustments, feedback,
            access, json, audit, programService);
        tenantId = UUID.randomUUID(); programId = UUID.randomUUID();
        operator = new Actor(UUID.randomUUID(), tenantId, UUID.randomUUID(), "operator", "HR_ADMIN");
    }

    @Test
    void oldCompletedAdjustmentCannotFinalizeANewerCalculation() {
        EvaluationProgram program = new EvaluationProgram(); program.setId(programId); program.setTenantId(tenantId);
        program.setStatus(ProgramStatus.OPEN); program.setDefinitionJson("definition");
        ProgramParticipant participant = new ProgramParticipant(); participant.setId(UUID.randomUUID()); participant.setTenantId(tenantId);
        participant.setProgramId(programId); participant.setStatus(ParticipantStatus.ACTIVE);
        participant.setCurrentStage(ProgramStage.FEEDBACK); participant.setStageStatus(ProgramStageStatus.COMPLETED);
        ProgramCalculation fresh = calculation(participant.getId());
        ProgramAdjustment stale = new ProgramAdjustment(); stale.setId(UUID.randomUUID()); stale.setTenantId(tenantId);
        stale.setParticipantId(participant.getId()); stale.setRevision(1); stale.setCalculationId(UUID.randomUUID());
        stale.setStatus(AdjustmentStatus.COMPLETED);
        ProgramFeedback agreed = new ProgramFeedback(); agreed.setStatus(FeedbackStatus.AGREED);
        when(access.lockedProgram(operator, programId)).thenReturn(program);
        when(json.configuration("definition")).thenReturn(ProgramConfigurationFactory.defaults(EvaluationKind.PERFORMANCE));
        when(participants.findAllByTenantIdAndProgramIdOrderByCreatedAtAsc(tenantId, programId)).thenReturn(List.of(participant));
        when(calculations.findAllByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, participant.getId())).thenReturn(List.of(fresh));
        when(adjustments.findFirstByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, participant.getId())).thenReturn(Optional.of(stale));
        when(feedback.findByTenantIdAndParticipantId(tenantId, participant.getId())).thenReturn(Optional.of(agreed));

        assertThatThrownBy(() -> service.finalizeProgram(operator, programId, new FinalizeRequest("re-finalize")))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.errorCode()).isEqualTo(ProgramErrorCode.PROGRAM_INCOMPLETE));
        verify(programs, never()).save(program);
    }

    @Test
    void draftCalculationDoesNotQualifyAsFinalResult() {
        EvaluationProgram program = new EvaluationProgram(); program.setId(programId); program.setTenantId(tenantId);
        program.setStatus(ProgramStatus.OPEN); program.setDefinitionJson("definition");
        ProgramParticipant participant = new ProgramParticipant(); participant.setId(UUID.randomUUID()); participant.setTenantId(tenantId);
        participant.setProgramId(programId); participant.setStatus(ParticipantStatus.ACTIVE);
        participant.setCurrentStage(ProgramStage.FEEDBACK); participant.setStageStatus(ProgramStageStatus.COMPLETED);
        ProgramCalculation draft = calculation(participant.getId()); draft.setStatus(CalculationStatus.DRAFT);
        when(access.lockedProgram(operator, programId)).thenReturn(program);
        when(json.configuration("definition")).thenReturn(ProgramConfigurationFactory.defaults(EvaluationKind.PERFORMANCE));
        when(participants.findAllByTenantIdAndProgramIdOrderByCreatedAtAsc(tenantId, programId)).thenReturn(List.of(participant));
        when(calculations.findAllByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, participant.getId())).thenReturn(List.of(draft));
        when(feedback.findByTenantIdAndParticipantId(tenantId, participant.getId())).thenReturn(Optional.of(feedback()));

        assertThatThrownBy(() -> service.finalizeProgram(operator, programId, new FinalizeRequest("finalize")))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.errorCode()).isEqualTo(ProgramErrorCode.PROGRAM_INCOMPLETE));
    }

    private ProgramCalculation calculation(UUID participantId) {
        ProgramCalculation c = new ProgramCalculation(); c.setId(UUID.randomUUID()); c.setTenantId(tenantId);
        c.setProgramId(programId); c.setParticipantId(participantId); c.setRevision(2); c.setStatus(CalculationStatus.FINAL);
        c.setAdjustedScore(new BigDecimal("88")); c.setCalculatedGrade("A"); c.setCalculatedAt(Instant.now()); return c;
    }
    private ProgramFeedback feedback() { ProgramFeedback f = new ProgramFeedback(); f.setStatus(FeedbackStatus.AGREED); return f; }
}

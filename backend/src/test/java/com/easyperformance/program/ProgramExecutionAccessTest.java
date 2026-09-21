package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.*;
import com.easyperformance.program.ProgramTypes.*;
import com.easyperformance.resources.ResourceIntegrationService;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgramExecutionAccessTest {
    @Mock EvaluationProgramRepository programs; @Mock ProgramParticipantRepository participants;
    @Mock ProgramReviewerAssignmentRepository reviewers; @Mock ProgramGoalRepository goals;
    @Mock ProgramGoalEventRepository goalEvents; @Mock ProgramIntermediateReviewRepository intermediate;
    @Mock ProgramReviewSubmissionRepository submissions; @Mock ProgramCalculationRepository calculations;
    @Mock ProgramAdjustmentRepository adjustments; @Mock ProgramFeedbackRepository feedback;
    @Mock ProgramAccess access; @Mock ProgramRosterService roster; @Mock EvaluationProgramService programService;
    @Mock ResourceIntegrationService resources; @Mock ProgramJson json; @Mock ProgramAuditService audit;
    ProgramExecutionService service; UUID tenantId; UUID programId; UUID employeeId; Actor member;

    @BeforeEach
    void setUp() {
        service = new ProgramExecutionService(programs, participants, reviewers, goals, goalEvents, intermediate,
            submissions, calculations, adjustments, feedback, access, roster, programService, resources, json, audit);
        tenantId = UUID.randomUUID(); programId = UUID.randomUUID(); employeeId = UUID.randomUUID();
        member = new Actor(UUID.randomUUID(), tenantId, employeeId, "member", "EMPLOYEE");
    }

    @Test
    void gradeOnlyMemberCalculationRemovesEveryNumericAndFormulaField() {
        EvaluationProgram program = program(ProgramStatus.FINALIZED); ProgramConfiguration config = gradeOnly();
        ProgramParticipant participant = participant(); participant.setResultPublished(true);
        ProgramCalculation calculation = calculation(participant.getId(), "A", new BigDecimal("91"));
        when(access.participant(member, participant.getId())).thenReturn(participant);
        when(access.program(member, programId)).thenReturn(program);
        when(access.operator(member)).thenReturn(false); when(access.self(member, participant)).thenReturn(true);
        when(calculations.findAllByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, participant.getId())).thenReturn(List.of(calculation));
        when(json.configuration("definition")).thenReturn(config);
        when(json.contributions("contributions")).thenReturn(List.of(new ScoreContribution("REVIEWER_1", new BigDecimal("91"), new BigDecimal("100"))));
        when(json.strings("warnings")).thenReturn(List.of("private-warning"));

        CalculationResponse response = service.calculations(member, participant.getId()).getFirst();

        assertThat(response.calculatedGrade()).isEqualTo("A");
        assertThat(response.rawScore()).isNull(); assertThat(response.normalizedScore()).isNull();
        assertThat(response.adjustedScore()).isNull(); assertThat(response.formula()).isNull();
        assertThat(response.contributions()).isEmpty(); assertThat(response.warnings()).isEmpty();
    }

    @Test
    void reviewerCannotChooseAnUnassignedFutureRoundToRevealPriorSubmissions() {
        ProgramParticipant participant = participant(); participant.setCurrentRound(1);
        EvaluationProgram program = program(ProgramStatus.OPEN);
        when(access.participant(member, participant.getId())).thenReturn(participant);
        when(access.program(member, programId)).thenReturn(program);
        when(access.operator(member)).thenReturn(false); when(access.self(member, participant)).thenReturn(false);
        when(reviewers.findByTenantIdAndParticipantIdAndRoleAndRoundAndStatusNot(tenantId, participant.getId(), ReviewerRole.REVIEWER, 999, AssignmentStatus.REVOKED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reviewContext(member, participant.getId(), 999)).isInstanceOf(ApiException.class);
        verifyNoInteractions(submissions);
    }

    @Test
    void finalizedProgramRejectsGoalOpinionMutation() {
        ProgramParticipant participant = participant(); ProgramGoal goal = new ProgramGoal();
        goal.setId(UUID.randomUUID()); goal.setTenantId(tenantId); goal.setProgramId(programId); goal.setParticipantId(participant.getId());
        goal.setStatus(GoalStatus.DRAFT);
        when(goals.findByIdAndTenantId(goal.getId(), tenantId)).thenReturn(Optional.of(goal));
        when(access.participant(member, participant.getId())).thenReturn(participant);
        when(programs.findLocked(programId, tenantId)).thenReturn(Optional.of(program(ProgramStatus.FINALIZED)));

        assertThatThrownBy(() -> service.saveGoalOpinion(member, goal.getId(), new GoalOpinionRequest("late edit")))
            .isInstanceOf(ApiException.class);
        verify(goals, never()).save(any());
    }

    private EvaluationProgram program(ProgramStatus status) {
        EvaluationProgram p = new EvaluationProgram(); p.setId(programId); p.setTenantId(tenantId); p.setStatus(status);
        p.setDefinitionJson("definition"); return p;
    }
    private ProgramParticipant participant() {
        ProgramParticipant p = new ProgramParticipant(); p.setId(UUID.randomUUID()); p.setTenantId(tenantId);
        p.setProgramId(programId); p.setEmployeeId(employeeId); p.setStatus(ParticipantStatus.ACTIVE); return p;
    }
    private ProgramCalculation calculation(UUID participantId, String grade, BigDecimal score) {
        ProgramCalculation c = new ProgramCalculation(); c.setId(UUID.randomUUID()); c.setTenantId(tenantId);
        c.setProgramId(programId); c.setParticipantId(participantId); c.setRevision(2); c.setStatus(CalculationStatus.FINAL);
        c.setContributionsJson("contributions"); c.setWarningsJson("warnings"); c.setRawScore(score); c.setNormalizedScore(score);
        c.setAdjustedScore(score); c.setCalculatedGrade(grade); c.setFormula("secret formula"); return c;
    }
    private ProgramConfiguration gradeOnly() {
        ProgramConfiguration c = ProgramConfigurationFactory.defaults(EvaluationKind.PERFORMANCE);
        PublicationPolicyInput p = c.publication();
        return new ProgramConfiguration(c.goalMode(), c.stages(), c.scales(), c.calculation(),
            new PublicationPolicyInput(p.previousRoundVisibility(), p.showReviewerAllocation(), MemberResultVisibility.GRADE_ONLY,
                p.scoreAdjustmentAllowed(), p.reviewerMeanMinimum(), p.reviewerMeanMaximum(), p.maximumGradeStepAdjustment(),
                p.multiRaterOpinionsVisible(), p.feedbackEnabled(), p.appealEnabled()),
            c.groups(), c.commonItems(), c.departmentPerformanceGroups(), c.allocationRows());
    }
}

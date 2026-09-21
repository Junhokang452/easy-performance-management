package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.ParticipantAttributes;
import com.easyperformance.program.ProgramDtos.PivotRequest;
import com.easyperformance.program.ProgramDtos.ReviewItemAnswerResponse;
import com.easyperformance.program.ProgramTypes.AdjustmentStatus;
import com.easyperformance.program.ProgramTypes.CalculationStatus;
import com.easyperformance.program.ProgramTypes.FeedbackStatus;
import com.easyperformance.program.ProgramTypes.ParticipantStatus;
import com.easyperformance.program.ProgramTypes.ProgramStatus;
import com.easyperformance.program.ProgramTypes.ReviewerRole;
import com.easyperformance.program.ProgramTypes.SubmissionStatus;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramAnalyticsServiceTest {
    @Mock ProgramAccess access; @Mock EvaluationProgramRepository programs;
    @Mock ProgramParticipantRepository participants; @Mock ProgramReviewSubmissionRepository submissions;
    @Mock ProgramCalculationRepository calculations; @Mock ProgramAdjustmentRepository adjustments;
    @Mock ProgramFeedbackRepository feedback; @Mock RmEmployeeRepository employees; @Mock ProgramJson json;
    ProgramAnalyticsService service; Actor operator; UUID tenantId; UUID programId;

    @BeforeEach
    void setUp() {
        service = new ProgramAnalyticsService(access, programs, participants, submissions, calculations,
            adjustments, feedback, employees, json);
        tenantId = UUID.randomUUID(); programId = UUID.randomUUID();
        operator = new Actor(UUID.randomUUID(), tenantId, UUID.randomUUID(), "운영자", "HR_ADMIN");
    }

    @Test
    void resultsAreUnavailableUntilProgramIsFinalized() {
        EvaluationProgram open = program(ProgramStatus.OPEN);
        when(programs.findByIdAndTenantId(programId, tenantId)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.resultSummary(operator, programId)).isInstanceOfSatisfying(ApiException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ProgramErrorCode.RESULT_NOT_PUBLISHED));
    }

    @Test
    void summaryUsesOnlyPublishedParticipantsAndCompletedAdjustment() {
        EvaluationProgram finalized = program(ProgramStatus.FINALIZED);
        ProgramParticipant published = participant(true); ProgramParticipant hidden = participant(false);
        ParticipantAttributes attributes = attributes(published.getEmployeeId());
        ProgramCalculation calculation = calculation(published.getId(), new BigDecimal("80"), "A");
        ProgramAdjustment adjustment = new ProgramAdjustment(); adjustment.setTenantId(tenantId);
        adjustment.setParticipantId(published.getId()); adjustment.setRevision(1); adjustment.setCalculationId(calculation.getId());
        adjustment.setStatus(AdjustmentStatus.COMPLETED);
        adjustment.setAdjustedScore(new BigDecimal("85")); adjustment.setAdjustedGrade("S");
        ProgramFeedback delivered = new ProgramFeedback(); delivered.setStatus(FeedbackStatus.DELIVERED);
        when(programs.findByIdAndTenantId(programId, tenantId)).thenReturn(Optional.of(finalized));
        when(participants.findAllByTenantIdAndProgramIdOrderByCreatedAtAsc(tenantId, programId)).thenReturn(List.of(published, hidden));
        when(json.participantAttributes(published.getAttributesJson())).thenReturn(attributes);
        when(calculations.findAllByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, published.getId())).thenReturn(List.of(calculation));
        when(adjustments.findFirstByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, published.getId())).thenReturn(Optional.of(adjustment));
        when(feedback.findByTenantIdAndParticipantId(tenantId, published.getId())).thenReturn(Optional.of(delivered));

        var result = service.resultSummary(operator, programId);

        assertThat(result.finalizedCount()).isEqualTo(1);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.score()).isEqualByComparingTo("85"); assertThat(row.grade()).isEqualTo("S");
            assertThat(row.feedbackStatus()).isEqualTo("DELIVERED");
        });
        assertThat(result.grades()).singleElement().extracting(g -> g.grade()).isEqualTo("S");
    }

    @Test
    void summaryIgnoresCompletedAdjustmentFromAnOlderCalculationRevision() {
        EvaluationProgram finalized = program(ProgramStatus.FINALIZED); ProgramParticipant participant = participant(true);
        ProgramCalculation latest = calculation(participant.getId(), new BigDecimal("78"), "B");
        ProgramAdjustment stale = new ProgramAdjustment(); stale.setParticipantId(participant.getId()); stale.setRevision(1);
        stale.setCalculationId(UUID.randomUUID()); stale.setStatus(AdjustmentStatus.COMPLETED);
        stale.setAdjustedScore(new BigDecimal("99")); stale.setAdjustedGrade("S");
        when(programs.findByIdAndTenantId(programId, tenantId)).thenReturn(Optional.of(finalized));
        when(participants.findAllByTenantIdAndProgramIdOrderByCreatedAtAsc(tenantId, programId)).thenReturn(List.of(participant));
        when(json.participantAttributes(participant.getAttributesJson())).thenReturn(attributes(participant.getEmployeeId()));
        when(calculations.findAllByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, participant.getId())).thenReturn(List.of(latest));
        when(adjustments.findFirstByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, participant.getId())).thenReturn(Optional.of(stale));
        when(feedback.findByTenantIdAndParticipantId(tenantId, participant.getId())).thenReturn(Optional.empty());

        var row = service.resultSummary(operator, programId).rows().getFirst();

        assertThat(row.score()).isEqualByComparingTo("78");
        assertThat(row.grade()).isEqualTo("B");
    }

    @Test
    void pivotRejectsUnsupportedOrGradelessDimensions() {
        PivotRequest request = new PivotRequest(programId, List.of("department"), List.of("position"));

        assertThatThrownBy(() -> service.pivot(operator, request)).isInstanceOfSatisfying(ApiException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ProgramErrorCode.PROGRAM_INVALID));
    }

    @Test
    void reviewerTendencyReturnsNullOpinionMetricsWithExplicitReasons() {
        EvaluationProgram finalized = program(ProgramStatus.FINALIZED); ProgramParticipant participant = participant(true);
        ParticipantAttributes attributes = attributes(participant.getEmployeeId()); UUID reviewerId = UUID.randomUUID();
        ProgramCalculation calculation = calculation(participant.getId(), new BigDecimal("75"), "B");
        ProgramReviewSubmission submission = new ProgramReviewSubmission(); submission.setTenantId(tenantId);
        submission.setProgramId(programId); submission.setParticipantId(participant.getId()); submission.setActorEmployeeId(reviewerId);
        submission.setRole(ReviewerRole.REVIEWER); submission.setRound(1); submission.setStatus(SubmissionStatus.COMPLETED);
        submission.setAnswersJson("answers"); submission.setOverallOpinion(null);
        when(programs.findByIdAndTenantId(programId, tenantId)).thenReturn(Optional.of(finalized));
        when(participants.findAllByTenantIdAndProgramIdOrderByCreatedAtAsc(tenantId, programId)).thenReturn(List.of(participant));
        when(json.participantAttributes(participant.getAttributesJson())).thenReturn(attributes);
        when(calculations.findAllByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, participant.getId())).thenReturn(List.of(calculation));
        when(adjustments.findFirstByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, participant.getId())).thenReturn(Optional.empty());
        when(submissions.findAllByTenantIdAndProgramIdAndStatus(tenantId, programId, SubmissionStatus.COMPLETED)).thenReturn(List.of(submission));
        when(json.answers("answers")).thenReturn(List.of(new ReviewItemAnswerResponse(UUID.randomUUID(), "목표", new BigDecimal("100"), "B", new BigDecimal("75"), null)));
        when(employees.findByIdAndTenantId(reviewerId, tenantId)).thenReturn(Optional.empty());

        var tendency = service.reviewerTendencies(operator, programId).getFirst();

        assertThat(tendency.targetCount()).isEqualTo(1);
        assertThat(tendency.meanScore()).isEqualByComparingTo("75.0000");
        assertThat(tendency.opinionSpecificity()).isNull();
        assertThat(tendency.positiveRatio()).isNull();
        assertThat(tendency.unavailableReasons()).contains("OPINION_DATA_UNAVAILABLE",
            "RANK_REQUIRES_MULTIPLE_REVIEWERS", "STANDARD_DEVIATION_REQUIRES_MULTIPLE_TARGETS");
    }

    @Test
    void itemResultsExposeCompletedAnswersWithLatestPublishedGrade() {
        EvaluationProgram finalized = program(ProgramStatus.FINALIZED); ProgramParticipant participant = participant(true);
        ParticipantAttributes attributes = attributes(participant.getEmployeeId()); ProgramCalculation calculation = calculation(participant.getId(), new BigDecimal("91"), "S");
        UUID itemId = UUID.randomUUID(); ProgramReviewSubmission submission = new ProgramReviewSubmission();
        submission.setParticipantId(participant.getId()); submission.setRound(2); submission.setStatus(SubmissionStatus.COMPLETED); submission.setAnswersJson("one");
        when(programs.findByIdAndTenantId(programId, tenantId)).thenReturn(Optional.of(finalized));
        when(participants.findAllByTenantIdAndProgramIdOrderByCreatedAtAsc(tenantId, programId)).thenReturn(List.of(participant));
        when(json.participantAttributes(participant.getAttributesJson())).thenReturn(attributes);
        when(calculations.findAllByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, participant.getId())).thenReturn(List.of(calculation));
        when(adjustments.findFirstByTenantIdAndParticipantIdOrderByRevisionDesc(tenantId, participant.getId())).thenReturn(Optional.empty());
        when(submissions.findAllByTenantIdAndProgramIdAndStatus(tenantId, programId, SubmissionStatus.COMPLETED)).thenReturn(List.of(submission));
        when(json.answers("one")).thenReturn(List.of(new ReviewItemAnswerResponse(itemId, "고객 대응", new BigDecimal("100"), "S", new BigDecimal("91"), "구체적")));

        assertThat(service.itemResults(operator, programId)).singleElement().satisfies(row -> {
            assertThat(row.itemId()).isEqualTo(itemId); assertThat(row.round()).isEqualTo(2); assertThat(row.grade()).isEqualTo("S");
        });
    }

    private EvaluationProgram program(ProgramStatus status) {
        EvaluationProgram program = new EvaluationProgram(); program.setId(programId); program.setTenantId(tenantId);
        program.setStatus(status); program.setEvaluationYear(2026); program.setName("2026 평가"); return program;
    }
    private ProgramParticipant participant(boolean published) {
        ProgramParticipant participant = new ProgramParticipant(); participant.setId(UUID.randomUUID()); participant.setTenantId(tenantId);
        participant.setProgramId(programId); participant.setEmployeeId(UUID.randomUUID()); participant.setStatus(ParticipantStatus.ACTIVE);
        participant.setResultPublished(published); participant.setAttributesJson("attributes-" + participant.getId()); return participant;
    }
    private ProgramCalculation calculation(UUID participantId, BigDecimal score, String grade) {
        ProgramCalculation calculation = new ProgramCalculation(); calculation.setId(UUID.randomUUID()); calculation.setTenantId(tenantId);
        calculation.setProgramId(programId); calculation.setParticipantId(participantId); calculation.setRevision(1);
        calculation.setAdjustedScore(score); calculation.setCalculatedGrade(grade); calculation.setStatus(CalculationStatus.FINAL); return calculation;
    }
    private static ParticipantAttributes attributes(UUID employeeId) {
        return new ParticipantAttributes(employeeId, "E001", "직원", UUID.randomUUID(), UUID.randomUUID(), "영업",
            "LEAD", "G5", "SALES", "FULL_TIME");
    }
}

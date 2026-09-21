package com.easyperformance.workflow;

import com.easyware.platform.UuidV7;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleResponse;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.CycleType;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.evaluationcycle.service.EvaluationCycleService;
import com.easyperformance.domain.evaluationpolicy.entity.EvaluationPolicy;
import com.easyperformance.domain.evaluationpolicy.repository.EvaluationPolicyRepository;
import com.easyperformance.domain.review.repository.PerformanceReviewRepository;
import com.easyperformance.domain.review.service.ReviewService;
import com.easyperformance.domain.report.repository.PerformanceReportRepository;
import com.easyperformance.domain.kpi.repository.KpiActualRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationWorkflowServiceTest {
    @Test
    void open_validRoster_createsReviewAndEntersGoalSetting() {
        UUID tenantId = UuidV7.generate();
        UUID cycleId = UuidV7.generate();
        UUID employeeId = UuidV7.generate();
        EvaluationCycleRepository cycles = mock(EvaluationCycleRepository.class);
        EvaluationPolicyRepository policies = mock(EvaluationPolicyRepository.class);
        EvaluationParticipantRepository participants = mock(EvaluationParticipantRepository.class);
        EvaluationReviewerAssignmentRepository reviewers = mock(EvaluationReviewerAssignmentRepository.class);
        PerformanceReviewRepository reviewRepository = mock(PerformanceReviewRepository.class);
        ReviewService reviewService = mock(ReviewService.class);
        EvaluationCycleService cycleService = mock(EvaluationCycleService.class);
        EvaluationGoalRepository goals = mock(EvaluationGoalRepository.class);
        IntermediatePerformanceReviewRepository intermediate = mock(IntermediatePerformanceReviewRepository.class);
        PerformanceReportRepository reports = mock(PerformanceReportRepository.class);
        PerformanceFeedbackRepository feedback = mock(PerformanceFeedbackRepository.class);
        KpiActualRepository actuals = mock(KpiActualRepository.class);
        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setId(cycleId);
        cycle.setTenantId(tenantId);
        cycle.setStatus(CycleStatus.PLANNED);
        EvaluationParticipant participant = new EvaluationParticipant();
        participant.setId(UuidV7.generate());
        participant.setTenantId(tenantId);
        participant.setCycleId(cycleId);
        participant.setEmployeeId(employeeId);
        participant.setStatus(ParticipantStatus.ACTIVE);
        EvaluationReviewerAssignment reviewer = new EvaluationReviewerAssignment();
        reviewer.setReviewerEmployeeId(UuidV7.generate());
        reviewer.setStatus(ReviewerAssignmentStatus.ASSIGNED);
        when(cycles.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(policies.findByTenantIdAndCycleId(tenantId, cycleId)).thenReturn(Optional.of(new EvaluationPolicy()));
        when(participants.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId)).thenReturn(List.of(participant));
        when(reviewers.findByTenantIdAndParticipantIdAndReviewerTypeAndRound(
            tenantId, participant.getId(), ReviewerType.MANAGER, 1)).thenReturn(Optional.of(reviewer));
        when(reviewRepository.existsByTenantIdAndCycleIdAndEmployeeId(tenantId, cycleId, employeeId)).thenReturn(false);
        CycleResponse opened = new CycleResponse(cycleId, tenantId, "Cycle", LocalDate.now(), LocalDate.now(),
            CycleType.QUARTERLY, CycleStatus.GOAL_SETTING, null, null, null);
        when(cycleService.transition(eq(cycleId), any())).thenReturn(opened);
        EvaluationWorkflowService service = new EvaluationWorkflowService(cycles, policies, participants, reviewers,
            reviewRepository, reviewService, cycleService, goals, intermediate, reports, feedback, actuals);
        ActorAccess.Actor actor = new ActorAccess.Actor(UuidV7.generate(), tenantId, UuidV7.generate(), "HR", "HR_ADMIN");

        var result = service.open(actor, cycleId);

        assertThat(result.createdReviews()).isEqualTo(1);
        assertThat(result.blockers()).isEmpty();
        verify(reviewService).createReview(eq(cycleId), any());
        verify(cycleService, org.mockito.Mockito.times(2)).transition(eq(cycleId), any());
    }

    @Test
    void selfReview_requiresActualForEveryApprovedGoal() {
        UUID tenantId = UuidV7.generate();
        UUID cycleId = UuidV7.generate();
        UUID employeeId = UuidV7.generate();
        EvaluationCycleRepository cycles = mock(EvaluationCycleRepository.class);
        EvaluationPolicyRepository policies = mock(EvaluationPolicyRepository.class);
        EvaluationParticipantRepository participants = mock(EvaluationParticipantRepository.class);
        EvaluationReviewerAssignmentRepository reviewers = mock(EvaluationReviewerAssignmentRepository.class);
        PerformanceReviewRepository reviewRepository = mock(PerformanceReviewRepository.class);
        ReviewService reviewService = mock(ReviewService.class);
        EvaluationCycleService cycleService = mock(EvaluationCycleService.class);
        EvaluationGoalRepository goals = mock(EvaluationGoalRepository.class);
        IntermediatePerformanceReviewRepository intermediate = mock(IntermediatePerformanceReviewRepository.class);
        PerformanceReportRepository reports = mock(PerformanceReportRepository.class);
        PerformanceFeedbackRepository feedback = mock(PerformanceFeedbackRepository.class);
        KpiActualRepository actuals = mock(KpiActualRepository.class);
        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setId(cycleId); cycle.setTenantId(tenantId); cycle.setStatus(CycleStatus.MID_REVIEW);
        EvaluationParticipant participant = new EvaluationParticipant();
        participant.setId(UuidV7.generate()); participant.setTenantId(tenantId); participant.setCycleId(cycleId);
        participant.setEmployeeId(employeeId); participant.setStatus(ParticipantStatus.ACTIVE);
        EvaluationGoal goal = new EvaluationGoal();
        goal.setId(UuidV7.generate()); goal.setTenantId(tenantId); goal.setCycleId(cycleId);
        goal.setParticipantId(participant.getId()); goal.setEmployeeId(employeeId);
        goal.setKpiAssignmentId(UuidV7.generate()); goal.setStatus(GoalStatus.APPROVED);
        IntermediatePerformanceReview mid = new IntermediatePerformanceReview();
        mid.setStatus(IntermediateReviewStatus.MANAGER_COMPLETED);
        when(cycles.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(participants.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId)).thenReturn(List.of(participant));
        when(goals.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId)).thenReturn(List.of(goal));
        when(intermediate.findByTenantIdAndParticipantId(tenantId, participant.getId())).thenReturn(Optional.of(mid));
        when(actuals.findAllByTenantIdAndKpiAssignmentIdAndSupersedesIdIsNullOrderByAsOfDateDescCreatedAtDesc(
            tenantId, goal.getKpiAssignmentId())).thenReturn(List.of());
        EvaluationWorkflowService service = new EvaluationWorkflowService(cycles, policies, participants, reviewers,
            reviewRepository, reviewService, cycleService, goals, intermediate, reports, feedback, actuals);
        ActorAccess.Actor actor = new ActorAccess.Actor(UuidV7.generate(), tenantId, UuidV7.generate(), "HR", "HR_ADMIN");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.advance(actor, cycleId, CycleStatus.SELF_REVIEW))
            .isInstanceOf(com.easyware.platform.error.ApiException.class);
    }
}

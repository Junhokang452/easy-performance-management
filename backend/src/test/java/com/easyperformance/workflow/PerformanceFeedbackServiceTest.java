package com.easyperformance.workflow;

import com.easyware.platform.UuidV7;
import com.easyperformance.domain.report.entity.PerformanceReport;
import com.easyperformance.domain.report.repository.PerformanceReportRepository;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.evaluationpolicy.repository.EvaluationPolicyRepository;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.FeedbackUpdateRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerformanceFeedbackServiceTest {
    @Test
    void assignedManager_completesFormalFeedback() {
        UUID tenantId = UuidV7.generate();
        UUID employeeId = UuidV7.generate();
        UUID managerId = UuidV7.generate();
        UUID reportId = UuidV7.generate();
        PerformanceReport report = new PerformanceReport();
        report.setId(reportId);
        report.setTenantId(tenantId);
        report.setCycleId(UuidV7.generate());
        report.setEmployeeId(employeeId);
        EvaluationParticipant participant = new EvaluationParticipant();
        participant.setId(UuidV7.generate());
        participant.setTenantId(tenantId);
        participant.setCycleId(report.getCycleId());
        participant.setEmployeeId(employeeId);
        EvaluationReviewerAssignment reviewer = new EvaluationReviewerAssignment();
        reviewer.setReviewerEmployeeId(managerId);
        reviewer.setStatus(ReviewerAssignmentStatus.ASSIGNED);
        PerformanceReportRepository reports = mock(PerformanceReportRepository.class);
        EvaluationParticipantRepository participants = mock(EvaluationParticipantRepository.class);
        EvaluationReviewerAssignmentRepository reviewers = mock(EvaluationReviewerAssignmentRepository.class);
        PerformanceFeedbackRepository feedback = mock(PerformanceFeedbackRepository.class);
        EvaluationCycleRepository cycles = mock(EvaluationCycleRepository.class);
        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setId(report.getCycleId()); cycle.setTenantId(tenantId); cycle.setStatus(CycleStatus.CALIBRATION);
        when(reports.findByIdAndTenantId(reportId, tenantId)).thenReturn(Optional.of(report));
        when(participants.findByTenantIdAndCycleIdAndEmployeeId(tenantId, report.getCycleId(), employeeId))
            .thenReturn(Optional.of(participant));
        when(reviewers.findByTenantIdAndParticipantIdAndReviewerTypeAndRound(
            tenantId, participant.getId(), ReviewerType.MANAGER, 1)).thenReturn(Optional.of(reviewer));
        when(feedback.findByTenantIdAndReportId(tenantId, reportId)).thenReturn(Optional.empty());
        when(feedback.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cycles.findByIdAndTenantId(report.getCycleId(), tenantId)).thenReturn(Optional.of(cycle));
        PerformanceFeedbackService service = new PerformanceFeedbackService(reports, participants, reviewers, feedback, cycles,
            mock(EvaluationPolicyRepository.class));
        ActorAccess.Actor manager = new ActorAccess.Actor(UuidV7.generate(), tenantId, managerId, "Manager", "MANAGER");

        var response = service.update(manager, reportId, new FeedbackUpdateRequest("Strong delivery"), true);

        assertThat(response.status()).isEqualTo(FeedbackStatus.COMPLETED);
        assertThat(response.comment()).isEqualTo("Strong delivery");
    }
}

package com.easyperformance.workflow;

import com.easyware.platform.UuidV7;
import com.easyperformance.domain.report.entity.PerformanceReport;
import com.easyperformance.domain.report.repository.PerformanceReportRepository;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.evaluationpolicy.repository.EvaluationPolicyRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.AppealRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.AppealResolveRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.FeedbackResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.FeedbackUpdateRequest;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PerformanceFeedbackService {
    private final PerformanceReportRepository reports;
    private final EvaluationParticipantRepository participants;
    private final EvaluationReviewerAssignmentRepository reviewers;
    private final PerformanceFeedbackRepository feedback;
    private final EvaluationCycleRepository cycles;
    private final EvaluationPolicyRepository policies;

    public PerformanceFeedbackService(PerformanceReportRepository reports,
                                      EvaluationParticipantRepository participants,
                                      EvaluationReviewerAssignmentRepository reviewers,
                                      PerformanceFeedbackRepository feedback,
                                      EvaluationCycleRepository cycles,
                                      EvaluationPolicyRepository policies) {
        this.reports = reports; this.participants = participants; this.reviewers = reviewers; this.feedback = feedback;
        this.cycles = cycles;
        this.policies = policies;
    }

    @Transactional
    public FeedbackResponse update(ActorAccess.Actor actor, UUID reportId, FeedbackUpdateRequest request, boolean complete) {
        PerformanceReport report = requireReport(actor.tenantId(), reportId);
        requireFeedbackStage(actor.tenantId(), report);
        EvaluationParticipant participant = requireParticipant(actor.tenantId(), report);
        requireManager(actor, participant);
        PerformanceFeedback entity = feedback.findByTenantIdAndReportId(actor.tenantId(), reportId)
            .orElseGet(() -> newFeedback(actor.tenantId(), reportId, participant.getId()));
        if (entity.getStatus() != FeedbackStatus.DRAFT) throw new ApiException(PerformanceErrorCode.FEEDBACK_LOCKED);
        entity.setComment(request.comment());
        if (complete) {
            entity.setStatus(FeedbackStatus.COMPLETED);
            entity.setCompletedAt(Instant.now());
            entity.setCompletedBy(actor.employeeId());
        }
        return response(feedback.save(entity));
    }

    @Transactional
    public FeedbackResponse accept(ActorAccess.Actor actor, UUID reportId) {
        PerformanceReport report = requireOwnedReport(actor, reportId);
        requireFeedbackStage(actor.tenantId(), report);
        PerformanceFeedback entity = requireFeedback(actor.tenantId(), report.getId());
        if (entity.getStatus() != FeedbackStatus.COMPLETED) throw new ApiException(PerformanceErrorCode.FEEDBACK_LOCKED);
        entity.setStatus(FeedbackStatus.ACCEPTED);
        return response(feedback.save(entity));
    }

    @Transactional
    public FeedbackResponse appeal(ActorAccess.Actor actor, UUID reportId, AppealRequest request) {
        PerformanceReport report = requireOwnedReport(actor, reportId);
        requireFeedbackStage(actor.tenantId(), report);
        boolean appealEnabled = policies.findByTenantIdAndCycleId(actor.tenantId(), report.getCycleId())
            .map(p -> p.isAppealEnabled()).orElse(false);
        if (!appealEnabled) throw new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED);
        PerformanceFeedback entity = requireFeedback(actor.tenantId(), report.getId());
        if (entity.getStatus() != FeedbackStatus.COMPLETED) throw new ApiException(PerformanceErrorCode.FEEDBACK_LOCKED);
        entity.setStatus(FeedbackStatus.APPEALED);
        entity.setAppealReason(request.reason());
        entity.setAppealedAt(Instant.now());
        return response(feedback.save(entity));
    }

    @Transactional
    public FeedbackResponse resolve(ActorAccess.Actor actor, UUID reportId, AppealResolveRequest request) {
        if (!"HR_ADMIN".equals(actor.role()) && !"SUPER_ADMIN".equals(actor.role()))
            throw new ApiException(PerformanceErrorCode.WORKFLOW_FORBIDDEN);
        if (request.resolution() != FeedbackResolution.UPHELD) {
            throw new ApiException(PerformanceErrorCode.FEEDBACK_RESOLUTION_UNSUPPORTED);
        }
        PerformanceReport report = requireReport(actor.tenantId(), reportId);
        requireFeedbackStage(actor.tenantId(), report);
        PerformanceFeedback entity = requireFeedback(actor.tenantId(), reportId);
        if (entity.getStatus() != FeedbackStatus.APPEALED) throw new ApiException(PerformanceErrorCode.FEEDBACK_LOCKED);
        entity.setStatus(FeedbackStatus.RESOLVED);
        entity.setResolution(request.resolution());
        entity.setResolutionComment(request.comment());
        entity.setResolvedAt(Instant.now());
        entity.setResolvedBy(actor.employeeId());
        return response(feedback.save(entity));
    }

    @Transactional(readOnly = true)
    public FeedbackResponse get(ActorAccess.Actor actor, UUID reportId) {
        PerformanceReport report = requireReport(actor.tenantId(), reportId);
        EvaluationParticipant participant = requireParticipant(actor.tenantId(), report);
        boolean owner = actor.employeeId() != null && actor.employeeId().equals(report.getEmployeeId());
        boolean operator = "HR_ADMIN".equals(actor.role()) || "SUPER_ADMIN".equals(actor.role())
            || "DIRECTOR".equals(actor.role());
        boolean manager = reviewers.findByTenantIdAndParticipantIdAndReviewerTypeAndRound(
            actor.tenantId(), participant.getId(), ReviewerType.MANAGER, 1)
            .map(a -> a.getStatus() != ReviewerAssignmentStatus.REVOKED
                && a.getReviewerEmployeeId().equals(actor.employeeId())).orElse(false);
        if (!owner && !operator && !manager) throw new ApiException(PerformanceErrorCode.WORKFLOW_FORBIDDEN);
        return feedback.findByTenantIdAndReportId(actor.tenantId(), reportId).map(this::response).orElse(null);
    }

    private PerformanceFeedback newFeedback(UUID tenantId, UUID reportId, UUID participantId) {
        PerformanceFeedback entity = new PerformanceFeedback();
        entity.setId(UuidV7.generate()); entity.setTenantId(tenantId); entity.setReportId(reportId);
        entity.setParticipantId(participantId); entity.setStatus(FeedbackStatus.DRAFT); return entity;
    }
    private PerformanceReport requireReport(UUID tenantId, UUID reportId) {
        return reports.findByIdAndTenantId(reportId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REPORT_NOT_FOUND));
    }
    private PerformanceReport requireOwnedReport(ActorAccess.Actor actor, UUID reportId) {
        PerformanceReport report = requireReport(actor.tenantId(), reportId);
        if (actor.employeeId() == null || !actor.employeeId().equals(report.getEmployeeId()))
            throw new ApiException(PerformanceErrorCode.WORKFLOW_FORBIDDEN);
        return report;
    }
    private EvaluationParticipant requireParticipant(UUID tenantId, PerformanceReport report) {
        return participants.findByTenantIdAndCycleIdAndEmployeeId(tenantId, report.getCycleId(), report.getEmployeeId())
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.PARTICIPANT_NOT_FOUND));
    }
    private void requireManager(ActorAccess.Actor actor, EvaluationParticipant participant) {
        var assignment = reviewers.findByTenantIdAndParticipantIdAndReviewerTypeAndRound(
            actor.tenantId(), participant.getId(), ReviewerType.MANAGER, 1)
            .filter(a -> a.getStatus() != ReviewerAssignmentStatus.REVOKED)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REVIEWER_ASSIGNMENT_NOT_FOUND));
        if (!assignment.getReviewerEmployeeId().equals(actor.employeeId()))
            throw new ApiException(PerformanceErrorCode.WORKFLOW_FORBIDDEN);
    }
    private PerformanceFeedback requireFeedback(UUID tenantId, UUID reportId) {
        return feedback.findByTenantIdAndReportId(tenantId, reportId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.FEEDBACK_NOT_FOUND));
    }
    private void requireFeedbackStage(UUID tenantId, PerformanceReport report) {
        var cycle = cycles.findByIdAndTenantId(report.getCycleId(), tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND));
        if (cycle.getStatus() != CycleStatus.CALIBRATION) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED);
        }
    }
    FeedbackResponse response(PerformanceFeedback e) {
        return new FeedbackResponse(e.getId(), e.getReportId(), e.getParticipantId(), e.getStatus(), e.getComment(),
            e.getCompletedAt(), e.getCompletedBy(), e.getAppealReason(), e.getAppealedAt(), e.getResolution(),
            e.getResolutionComment(), e.getResolvedAt(), e.getResolvedBy(), e.getUpdatedAt());
    }
}

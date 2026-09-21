package com.easyperformance.workflow;

import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleResponse;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleStatusTransitionRequest;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.evaluationcycle.service.EvaluationCycleService;
import com.easyperformance.domain.evaluationpolicy.repository.EvaluationPolicyRepository;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewCreateRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewTransitionRequest;
import com.easyperformance.domain.review.entity.PerformanceReview;
import com.easyperformance.domain.review.entity.ReviewStatus;
import com.easyperformance.domain.review.repository.PerformanceReviewRepository;
import com.easyperformance.domain.review.service.ReviewService;
import com.easyperformance.domain.report.repository.PerformanceReportRepository;
import com.easyperformance.domain.kpi.repository.KpiActualRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.BlockerCode;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CycleAdvanceResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CycleLaunchResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.GateBlocker;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EvaluationWorkflowService {
    private final EvaluationCycleRepository cycles;
    private final EvaluationPolicyRepository policies;
    private final EvaluationParticipantRepository participants;
    private final EvaluationReviewerAssignmentRepository reviewers;
    private final PerformanceReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final EvaluationCycleService cycleService;
    private final EvaluationGoalRepository goals;
    private final IntermediatePerformanceReviewRepository intermediateReviews;
    private final PerformanceReportRepository reports;
    private final PerformanceFeedbackRepository feedback;
    private final KpiActualRepository actuals;

    public EvaluationWorkflowService(EvaluationCycleRepository cycles,
                                     EvaluationPolicyRepository policies,
                                     EvaluationParticipantRepository participants,
                                     EvaluationReviewerAssignmentRepository reviewers,
                                     PerformanceReviewRepository reviewRepository,
                                     ReviewService reviewService,
                                     EvaluationCycleService cycleService,
                                     EvaluationGoalRepository goals,
                                     IntermediatePerformanceReviewRepository intermediateReviews,
                                     PerformanceReportRepository reports,
                                     PerformanceFeedbackRepository feedback,
                                     KpiActualRepository actuals) {
        this.cycles = cycles;
        this.policies = policies;
        this.participants = participants;
        this.reviewers = reviewers;
        this.reviewRepository = reviewRepository;
        this.reviewService = reviewService;
        this.cycleService = cycleService;
        this.goals = goals;
        this.intermediateReviews = intermediateReviews;
        this.reports = reports;
        this.feedback = feedback;
        this.actuals = actuals;
    }

    @Transactional
    public CycleLaunchResponse open(ActorAccess.Actor actor, UUID cycleId) {
        requireHr(actor);
        EvaluationCycle cycle = requireCycle(actor.tenantId(), cycleId);
        List<GateBlocker> blockers = new ArrayList<>();
        if (policies.findByTenantIdAndCycleId(actor.tenantId(), cycleId).isEmpty()) {
            blockers.add(new GateBlocker(BlockerCode.GOAL_NOT_APPROVED, "Evaluation policy is missing", null, null));
        }
        List<EvaluationParticipant> active = activeParticipants(actor.tenantId(), cycleId);
        if (active.isEmpty()) {
            blockers.add(new GateBlocker(BlockerCode.NO_ACTIVE_PARTICIPANTS, "No active participants", null, null));
        }
        for (EvaluationParticipant participant : active) {
            var manager = reviewers.findByTenantIdAndParticipantIdAndReviewerTypeAndRound(
                actor.tenantId(), participant.getId(), ReviewerType.MANAGER, 1);
            if (manager.isEmpty() || manager.get().getStatus() == ReviewerAssignmentStatus.REVOKED) {
                blockers.add(new GateBlocker(BlockerCode.MISSING_MANAGER, "Primary manager is missing",
                    participant.getId(), participant.getEmployeeId()));
            }
        }
        rejectIfBlocked(blockers);
        int created = 0;
        for (EvaluationParticipant participant : active) {
            if (!reviewRepository.existsByTenantIdAndCycleIdAndEmployeeId(
                actor.tenantId(), cycleId, participant.getEmployeeId())) {
                reviewService.createReview(cycleId, new ReviewCreateRequest(participant.getEmployeeId()));
                created++;
            }
        }
        CycleResponse response;
        if (cycle.getStatus() == CycleStatus.PLANNED) {
            cycleService.transition(cycleId, new CycleStatusTransitionRequest(CycleStatus.ACTIVE));
            response = cycleService.transition(cycleId, new CycleStatusTransitionRequest(CycleStatus.GOAL_SETTING));
        } else if (cycle.getStatus() == CycleStatus.ACTIVE) {
            response = cycleService.transition(cycleId, new CycleStatusTransitionRequest(CycleStatus.GOAL_SETTING));
        } else {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED,
                Map.of("cycleId", cycleId, "status", cycle.getStatus().name(), "operation", "open"));
        }
        return new CycleLaunchResponse(response, created, List.of());
    }

    @Transactional
    public CycleAdvanceResponse advance(ActorAccess.Actor actor, UUID cycleId, CycleStatus target) {
        requireHr(actor);
        if (target == CycleStatus.FINALIZED) {
            return new CycleAdvanceResponse(close(actor, cycleId), 0, List.of());
        }
        EvaluationCycle cycle = requireCycle(actor.tenantId(), cycleId);
        List<EvaluationParticipant> active = activeParticipants(actor.tenantId(), cycleId);
        List<GateBlocker> blockers = switch (target) {
            case MID_REVIEW -> goalBlockers(actor.tenantId(), cycleId, active);
            case SELF_REVIEW -> intermediateBlockers(actor.tenantId(), cycleId, active);
            case MANAGER_REVIEW -> reviewBlockers(actor.tenantId(), cycleId, active, ReviewStatus.SELF_SUBMITTED,
                BlockerCode.SELF_REVIEW_INCOMPLETE);
            case CALIBRATION -> reviewBlockers(actor.tenantId(), cycleId, active, ReviewStatus.MANAGER_SUBMITTED,
                BlockerCode.MANAGER_REVIEW_INCOMPLETE);
            default -> List.of();
        };
        rejectIfBlocked(blockers);
        CycleResponse response = cycleService.transition(cycleId, new CycleStatusTransitionRequest(target));
        int affected = transitionReviews(actor, cycleId, target);
        return new CycleAdvanceResponse(response, affected, List.of());
    }

    @Transactional
    public CycleResponse close(ActorAccess.Actor actor, UUID cycleId) {
        requireHr(actor);
        EvaluationCycle cycle = requireCycle(actor.tenantId(), cycleId);
        if (cycle.getStatus() != CycleStatus.CALIBRATION) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED,
                Map.of("cycleId", cycleId, "required", CycleStatus.CALIBRATION.name(),
                    "actual", cycle.getStatus().name()));
        }
        List<GateBlocker> blockers = new ArrayList<>();
        for (EvaluationParticipant participant : activeParticipants(actor.tenantId(), cycleId)) {
            PerformanceReview review = reviewRepository.findByTenantIdAndCycleIdAndEmployeeId(
                actor.tenantId(), cycleId, participant.getEmployeeId()).orElse(null);
            if (review == null || review.getStatus() != ReviewStatus.FINALIZED) {
                blockers.add(new GateBlocker(BlockerCode.CALIBRATION_INCOMPLETE,
                    "Finalized review is required", participant.getId(), participant.getEmployeeId()));
                continue;
            }
            var reportRows = reports.findAllByTenantIdAndCycleIdAndEmployeeId(
                actor.tenantId(), cycleId, participant.getEmployeeId());
            var activeReport = reportRows.stream()
                .filter(r -> !reports.existsByTenantIdAndSupersedesId(actor.tenantId(), r.getId()))
                .findFirst().orElse(null);
            if (activeReport == null) {
                blockers.add(new GateBlocker(BlockerCode.REPORT_NOT_PUBLISHED,
                    "Published report is required", participant.getId(), participant.getEmployeeId()));
                continue;
            }
            if (!activeReport.isAcknowledged()) {
                blockers.add(new GateBlocker(BlockerCode.REPORT_NOT_ACKNOWLEDGED,
                    "Employee acknowledgement is required", participant.getId(), participant.getEmployeeId()));
            }
            FeedbackStatus status = feedback.findByTenantIdAndReportId(actor.tenantId(), activeReport.getId())
                .map(PerformanceFeedback::getStatus).orElse(null);
            if (status != FeedbackStatus.ACCEPTED && status != FeedbackStatus.RESOLVED) {
                blockers.add(new GateBlocker(BlockerCode.FEEDBACK_INCOMPLETE,
                    "Accepted feedback or a resolved appeal is required", participant.getId(), participant.getEmployeeId()));
            }
        }
        rejectIfBlocked(blockers);
        return cycleService.transition(cycleId, new CycleStatusTransitionRequest(CycleStatus.FINALIZED));
    }

    private int transitionReviews(ActorAccess.Actor actor, UUID cycleId, CycleStatus target) {
        ReviewStatus from;
        ReviewStatus to;
        if (target == CycleStatus.SELF_REVIEW) { from = ReviewStatus.DRAFT; to = ReviewStatus.SELF_PENDING; }
        else if (target == CycleStatus.MANAGER_REVIEW) { from = ReviewStatus.SELF_SUBMITTED; to = ReviewStatus.MANAGER_PENDING; }
        else if (target == CycleStatus.CALIBRATION) { from = ReviewStatus.MANAGER_SUBMITTED; to = ReviewStatus.CALIBRATION; }
        else return 0;
        int count = 0;
        for (PerformanceReview review : reviewRepository
            .findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(actor.tenantId(), cycleId)) {
            if (review.getStatus() == from) {
                reviewService.transition(review.getId(), new ReviewTransitionRequest(to, actor.employeeId()));
                count++;
            }
        }
        return count;
    }

    private List<GateBlocker> goalBlockers(UUID tenantId, UUID cycleId, List<EvaluationParticipant> active) {
        List<EvaluationGoal> all = goals.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId);
        List<GateBlocker> blockers = new ArrayList<>();
        for (EvaluationParticipant participant : active) {
            List<EvaluationGoal> owned = all.stream()
                .filter(g -> participant.getId().equals(g.getParticipantId())).toList();
            if (owned.isEmpty()) blockers.add(new GateBlocker(BlockerCode.GOAL_MISSING,
                "At least one goal is required", participant.getId(), participant.getEmployeeId()));
            else if (owned.stream().anyMatch(g -> g.getStatus() != GoalStatus.APPROVED))
                blockers.add(new GateBlocker(BlockerCode.GOAL_NOT_APPROVED,
                    "Every goal must be approved", participant.getId(), participant.getEmployeeId()));
        }
        return blockers;
    }

    private List<GateBlocker> intermediateBlockers(UUID tenantId, UUID cycleId, List<EvaluationParticipant> active) {
        List<GateBlocker> blockers = new ArrayList<>();
        List<EvaluationGoal> cycleGoals = goals.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId);
        for (EvaluationParticipant participant : active) {
            boolean actualMissing = cycleGoals.stream()
                .filter(g -> participant.getId().equals(g.getParticipantId()) && g.getStatus() == GoalStatus.APPROVED)
                .anyMatch(g -> actuals
                    .findAllByTenantIdAndKpiAssignmentIdAndSupersedesIdIsNullOrderByAsOfDateDescCreatedAtDesc(
                        tenantId, g.getKpiAssignmentId()).isEmpty());
            if (actualMissing) blockers.add(new GateBlocker(BlockerCode.GOAL_ACTUAL_MISSING,
                "At least one actual result is required for every approved goal",
                participant.getId(), participant.getEmployeeId()));
            boolean complete = intermediateReviews.findByTenantIdAndParticipantId(tenantId, participant.getId())
                .map(r -> r.getStatus() == IntermediateReviewStatus.MANAGER_COMPLETED).orElse(false);
            if (!complete) blockers.add(new GateBlocker(BlockerCode.MID_REVIEW_INCOMPLETE,
                "Intermediate performance review is incomplete", participant.getId(), participant.getEmployeeId()));
        }
        return blockers;
    }

    private List<GateBlocker> reviewBlockers(UUID tenantId, UUID cycleId, List<EvaluationParticipant> active,
                                             ReviewStatus required, BlockerCode code) {
        List<PerformanceReview> all = reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId);
        List<GateBlocker> blockers = new ArrayList<>();
        for (EvaluationParticipant participant : active) {
            boolean complete = all.stream().anyMatch(r -> participant.getEmployeeId().equals(r.getEmployeeId())
                && r.getStatus() == required);
            if (!complete) blockers.add(new GateBlocker(code, "Required review stage is incomplete",
                participant.getId(), participant.getEmployeeId()));
        }
        return blockers;
    }

    private List<EvaluationParticipant> activeParticipants(UUID tenantId, UUID cycleId) {
        return participants.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId).stream()
            .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE).toList();
    }

    EvaluationCycle requireCycle(UUID tenantId, UUID cycleId) {
        return cycles.findByIdAndTenantId(cycleId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND));
    }

    private void requireHr(ActorAccess.Actor actor) {
        if (!"HR_ADMIN".equals(actor.role()) && !"SUPER_ADMIN".equals(actor.role()))
            throw new ApiException(PerformanceErrorCode.WORKFLOW_FORBIDDEN);
    }

    private void rejectIfBlocked(List<GateBlocker> blockers) {
        if (!blockers.isEmpty()) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED,
                Map.of("blockers", blockers));
        }
    }
}

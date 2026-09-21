package com.easyperformance.workflow;

import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationSessionResponse;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionResponse;
import com.easyperformance.domain.calibration.service.CalibrationService;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleResponse;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.evaluationpolicy.repository.EvaluationPolicyRepository;
import com.easyperformance.domain.report.dto.ReportDtos.ReportResponse;
import com.easyperformance.domain.report.entity.PerformanceReport;
import com.easyperformance.domain.report.repository.PerformanceReportRepository;
import com.easyperformance.domain.report.service.ReportService;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewResponse;
import com.easyperformance.domain.review.entity.PerformanceReview;
import com.easyperformance.domain.review.entity.ReviewStatus;
import com.easyperformance.domain.review.repository.PerformanceReviewRepository;
import com.easyperformance.domain.review.service.ReviewService;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.readmodel.repository.RmOrgUnitRepository;
import com.easyperformance.domain.account.PerformanceUserRepository;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.AllowedAction;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CalibrationReviewRow;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CalibrationSessionSummary;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CalibrationTasksResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.FeedbackTaskResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.ManagerTaskResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.ResultsSummaryResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.WorkspaceResponse;
import com.easyperformance.workflow.ParticipantRosterDtos.EmployeeSummary;
import com.easyperformance.workflow.ParticipantRosterDtos.ParticipantResponse;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EvaluationWorkspaceQueryService {
    private final EvaluationCycleRepository cycles;
    private final EvaluationPolicyRepository policies;
    private final EvaluationParticipantRepository participants;
    private final EvaluationReviewerAssignmentRepository reviewers;
    private final RmEmployeeRepository employees;
    private final RmOrgUnitRepository orgUnits;
    private final PerformanceUserRepository users;
    private final PerformanceReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final EvaluationGoalRepository goals;
    private final GoalAgreementService goalService;
    private final IntermediatePerformanceReviewRepository intermediateRepository;
    private final IntermediateReviewService intermediateService;
    private final PerformanceReportRepository reportRepository;
    private final ReportService reportService;
    private final CalibrationService calibrationService;
    private final PerformanceFeedbackRepository feedbackRepository;
    private final PerformanceFeedbackService feedbackService;

    public EvaluationWorkspaceQueryService(EvaluationCycleRepository cycles, EvaluationPolicyRepository policies,
        EvaluationParticipantRepository participants, EvaluationReviewerAssignmentRepository reviewers,
        RmEmployeeRepository employees, RmOrgUnitRepository orgUnits, PerformanceUserRepository users,
        PerformanceReviewRepository reviewRepository, ReviewService reviewService,
        EvaluationGoalRepository goals, GoalAgreementService goalService,
        IntermediatePerformanceReviewRepository intermediateRepository, IntermediateReviewService intermediateService,
        PerformanceReportRepository reportRepository, ReportService reportService,
        CalibrationService calibrationService, PerformanceFeedbackRepository feedbackRepository,
        PerformanceFeedbackService feedbackService) {
        this.cycles = cycles; this.policies = policies; this.participants = participants; this.reviewers = reviewers;
        this.employees = employees; this.orgUnits = orgUnits; this.users = users;
        this.reviewRepository = reviewRepository; this.reviewService = reviewService;
        this.goals = goals; this.goalService = goalService; this.intermediateRepository = intermediateRepository;
        this.intermediateService = intermediateService; this.reportRepository = reportRepository;
        this.reportService = reportService; this.calibrationService = calibrationService;
        this.feedbackRepository = feedbackRepository;
        this.feedbackService = feedbackService;
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse myWorkspace(ActorAccess.Actor actor, UUID cycleId) {
        var cycle = cycles.findByIdAndTenantId(cycleId, actor.tenantId())
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND));
        EvaluationParticipant participant = participants
            .findByTenantIdAndCycleIdAndEmployeeId(actor.tenantId(), cycleId, actor.employeeId()).orElse(null);
        var policy = policies.findByTenantIdAndCycleId(actor.tenantId(), cycleId);
        CycleResponse cycleResponse = CycleResponse.from(cycle, policy.map(p -> p.getId()).orElse(null));
        if (participant == null) return new WorkspaceResponse(cycleResponse, null, List.of(), null, null, null, null,
            List.of(), List.of());
        PerformanceReview reviewEntity = reviewRepository
            .findByTenantIdAndCycleIdAndEmployeeId(actor.tenantId(), cycleId, actor.employeeId()).orElse(null);
        ReviewResponse review = reviewEntity == null ? null : maskForEmployee(reviewService.getReview(reviewEntity.getId()),
            hasPublishedReport(actor.tenantId(), cycleId, actor.employeeId()));
        ReportResponse report = hasPublishedReport(actor.tenantId(), cycleId, actor.employeeId())
            ? reportService.getMyReport(cycleId, actor.employeeId()) : null;
        EvaluationWorkspaceDtos.FeedbackResponse feedback = report == null ? null
            : feedbackRepository.findByTenantIdAndReportId(actor.tenantId(), report.id())
                .map(feedbackService::response).orElse(null);
        var mid = intermediateRepository.findByTenantIdAndParticipantId(actor.tenantId(), participant.getId())
            .map(intermediateService::response).orElse(null);
        var memberGoals = goalService.list(actor.tenantId(), cycleId, actor.employeeId());
        return new WorkspaceResponse(cycleResponse, participantResponse(actor.tenantId(), participant),
            memberGoals, mid, review, report, feedback,
            allowedForMember(cycle.getStatus(), memberGoals, reviewEntity, report, feedback,
                policy.map(p -> p.isAppealEnabled()).orElse(false)), List.of());
    }

    @Transactional(readOnly = true)
    public Page<CycleResponse> visibleCycles(ActorAccess.Actor actor, Pageable pageable) {
        boolean operator = List.of("SUPER_ADMIN", "HR_ADMIN", "DIRECTOR").contains(actor.role());
        return cycles.findVisible(actor.tenantId(), actor.employeeId(), operator, pageable)
            .map(c -> CycleResponse.from(c,
                policies.findByTenantIdAndCycleId(actor.tenantId(), c.getId()).map(p -> p.getId()).orElse(null)));
    }

    @Transactional(readOnly = true)
    public CycleResponse visibleCycle(ActorAccess.Actor actor, UUID cycleId) {
        return visibleCycles(actor, Pageable.unpaged()).stream()
            .filter(c -> c.id().equals(cycleId)).findFirst()
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<ManagerTaskResponse> managerTasks(ActorAccess.Actor actor, UUID cycleId) {
        return reviewers.findAllByTenantIdAndCycleIdAndReviewerEmployeeIdAndStatusNot(
            actor.tenantId(), cycleId, actor.employeeId(), ReviewerAssignmentStatus.REVOKED).stream().map(a -> {
                EvaluationParticipant p = participants.findByIdAndTenantId(a.getParticipantId(), actor.tenantId())
                    .orElseThrow(() -> new ApiException(PerformanceErrorCode.PARTICIPANT_NOT_FOUND));
                List<EvaluationGoal> participantGoals = goals
                    .findAllByTenantIdAndCycleIdAndEmployeeIdOrderByCreatedAtAsc(actor.tenantId(), cycleId, p.getEmployeeId());
                PerformanceReview r = reviewRepository.findByTenantIdAndCycleIdAndEmployeeId(
                    actor.tenantId(), cycleId, p.getEmployeeId()).orElse(null);
                IntermediateReviewStatus mid = intermediateRepository
                    .findByTenantIdAndParticipantId(actor.tenantId(), p.getId()).map(IntermediatePerformanceReview::getStatus).orElse(null);
                List<AllowedAction> actions = new ArrayList<>();
                if (participantGoals.stream().anyMatch(g -> g.getStatus() == GoalStatus.PENDING_APPROVAL)) actions.add(AllowedAction.REVIEW_GOAL);
                if (mid == IntermediateReviewStatus.EMPLOYEE_SUBMITTED) actions.add(AllowedAction.COMPLETE_INTERMEDIATE_REVIEW);
                if (r != null && r.getStatus() == ReviewStatus.MANAGER_PENDING) {
                    actions.add(AllowedAction.EDIT_MANAGER_REVIEW);
                    actions.add(AllowedAction.SUBMIT_MANAGER_REVIEW);
                }
                return new ManagerTaskResponse(p.getId(), cycleId, employee(actor.tenantId(), p.getEmployeeId()),
                    participantGoals.size(), (int) participantGoals.stream().filter(g -> g.getStatus() == GoalStatus.PENDING_APPROVAL).count(),
                    mid, r == null ? null : r.getId(), r == null ? null : r.getStatus(), actions, List.of());
            }).toList();
    }

    @Transactional(readOnly = true)
    public CalibrationTasksResponse calibrationTasks(ActorAccess.Actor actor, UUID cycleId) {
        List<CalibrationSessionSummary> sessions = calibrationService.listSessions(cycleId).stream()
            .map(s -> new CalibrationSessionSummary(s.id(), s.status(), s.scheduledAt(), s.ownerOrgUnitId(),
                s.participantIds() == null ? 0 : s.participantIds().size())).toList();
        List<CalibrationReviewRow> rows = new ArrayList<>();
        for (PerformanceReview review : reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(actor.tenantId(), cycleId)) {
            EvaluationParticipant p = participants.findByTenantIdAndCycleIdAndEmployeeId(
                actor.tenantId(), cycleId, review.getEmployeeId()).orElse(null);
            if (p != null) rows.add(new CalibrationReviewRow(review.getId(), p.getId(),
                employee(actor.tenantId(), review.getEmployeeId()), review.getKpiScore(), review.getFinalGrade(), null,
                review.getFinalGrade() != null));
        }
        DistributionResponse distribution = calibrationService.getDistribution(cycleId);
        return new CalibrationTasksResponse(cycleId, sessions, rows, distribution.targetDistribution(),
            distribution.currentDistribution());
    }

    @Transactional(readOnly = true)
    public ResultsSummaryResponse results(ActorAccess.Actor actor, UUID cycleId) {
        List<EvaluationParticipant> active = participants.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(actor.tenantId(), cycleId)
            .stream().filter(p -> p.getStatus() == ParticipantStatus.ACTIVE).toList();
        List<PerformanceReview> finalized = reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(actor.tenantId(), cycleId)
            .stream().filter(r -> r.getStatus() == ReviewStatus.FINALIZED).toList();
        List<PerformanceReport> reports = reportRepository.findAllByTenantIdAndCycleIdOrderByPublishedAtDesc(actor.tenantId(), cycleId);
        Map<String, Integer> grades = new LinkedHashMap<>();
        finalized.forEach(r -> grades.merge(r.getFinalGrade() == null ? "UNRATED" : r.getFinalGrade(), 1, Integer::sum));
        BigDecimal average = average(finalized);
        int acknowledged = (int) reports.stream().filter(PerformanceReport::isAcknowledged).count();
        Map<UUID, List<EvaluationParticipant>> byOrg = new LinkedHashMap<>();
        for (EvaluationParticipant p : active) {
            UUID orgId = employees.findByIdAndTenantId(p.getEmployeeId(), actor.tenantId())
                .map(RmEmployee::getOrgUnitId).orElse(null);
            if (orgId != null) byOrg.computeIfAbsent(orgId, ignored -> new ArrayList<>()).add(p);
        }
        List<EvaluationWorkspaceDtos.OrgUnitResultRow> orgRows = byOrg.entrySet().stream().map(entry -> {
            var orgReviews = finalized.stream().filter(r -> entry.getValue().stream()
                .anyMatch(p -> p.getEmployeeId().equals(r.getEmployeeId()))).toList();
            Map<String, Integer> orgGrades = new LinkedHashMap<>();
            orgReviews.forEach(r -> orgGrades.merge(r.getFinalGrade() == null ? "UNRATED" : r.getFinalGrade(), 1, Integer::sum));
            String orgName = orgUnits.findByIdAndTenantId(entry.getKey(), actor.tenantId())
                .map(o -> o.getName()).orElse(null);
            return new EvaluationWorkspaceDtos.OrgUnitResultRow(entry.getKey(), orgName, entry.getValue().size(),
                average(orgReviews), orgGrades);
        }).toList();
        return new ResultsSummaryResponse(cycleId, active.size(), finalized.size(), reports.size(), acknowledged,
            average, grades, orgRows);
    }

    @Transactional(readOnly = true)
    public List<FeedbackTaskResponse> feedbackTasks(ActorAccess.Actor actor, UUID cycleId) {
        List<FeedbackTaskResponse> result = new ArrayList<>();
        for (PerformanceReport report : reportRepository.findAllByTenantIdAndCycleIdOrderByPublishedAtDesc(actor.tenantId(), cycleId)) {
            EvaluationParticipant participant = participants.findByTenantIdAndCycleIdAndEmployeeId(
                actor.tenantId(), cycleId, report.getEmployeeId()).orElse(null);
            if (participant == null) continue;
            boolean operator = "HR_ADMIN".equals(actor.role()) || "SUPER_ADMIN".equals(actor.role());
            boolean assignedManager = reviewers.findByTenantIdAndParticipantIdAndReviewerTypeAndRound(
                actor.tenantId(), participant.getId(), ReviewerType.MANAGER, 1)
                .map(a -> a.getStatus() != ReviewerAssignmentStatus.REVOKED
                    && a.getReviewerEmployeeId().equals(actor.employeeId())).orElse(false);
            if (!operator && !assignedManager) continue;
            PerformanceFeedback item = feedbackRepository.findByTenantIdAndReportId(actor.tenantId(), report.getId()).orElse(null);
            List<AllowedAction> actions = new ArrayList<>();
            if (assignedManager && (item == null || item.getStatus() == FeedbackStatus.DRAFT)) {
                actions.add(AllowedAction.EDIT_FEEDBACK);
                actions.add(AllowedAction.COMPLETE_FEEDBACK);
            }
            if (operator && item != null && item.getStatus() == FeedbackStatus.APPEALED) {
                actions.add(AllowedAction.RESOLVE_APPEAL);
            }
            result.add(new FeedbackTaskResponse(report.getId(), participant.getId(),
                employee(actor.tenantId(), report.getEmployeeId()), item == null ? null : item.getStatus(),
                item == null ? null : item.getComment(), item == null ? null : item.getAppealReason(),
                item == null ? null : item.getResolution(), actions));
        }
        return result;
    }

    private BigDecimal average(List<PerformanceReview> reviews) {
        List<BigDecimal> values = reviews.stream().map(PerformanceReview::getFinalScore).filter(java.util.Objects::nonNull).toList();
        if (values.isEmpty()) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
    ParticipantResponse participantResponse(UUID tenantId, EvaluationParticipant p) {
        var managerAssignment = reviewers.findByTenantIdAndParticipantIdAndReviewerTypeAndRound(
            tenantId, p.getId(), ReviewerType.MANAGER, 1).orElse(null);
        EmployeeSummary manager = managerAssignment == null ? null : employee(tenantId, managerAssignment.getReviewerEmployeeId());
        PerformanceReview review = reviewRepository.findByTenantIdAndCycleIdAndEmployeeId(tenantId, p.getCycleId(), p.getEmployeeId()).orElse(null);
        return new ParticipantResponse(p.getId(), p.getCycleId(), employee(tenantId, p.getEmployeeId()), p.getStatus(), manager,
            review == null ? null : review.getId(), review == null ? null : review.getStatus().name());
    }
    EmployeeSummary employee(UUID tenantId, UUID id) {
        RmEmployee e = employees.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.PARTICIPANT_EMPLOYEE_INVALID));
        String orgName = e.getOrgUnitId() == null ? null
            : orgUnits.findByIdAndTenantId(e.getOrgUnitId(), tenantId).map(o -> o.getName()).orElse(null);
        return new EmployeeSummary(e.getId(), e.getEmployeeNo(), e.getName(), e.getOrgUnitId(), orgName,
            e.getStatus(), users.existsByTenantIdAndEmployeeId(tenantId, e.getId()));
    }
    private boolean hasPublishedReport(UUID tenantId, UUID cycleId, UUID employeeId) {
        return !reportRepository.findAllByTenantIdAndCycleIdAndEmployeeId(tenantId, cycleId, employeeId).isEmpty();
    }
    private ReviewResponse maskForEmployee(ReviewResponse r, boolean published) {
        if (published) return r;
        return new ReviewResponse(r.id(), r.cycleId(), r.employeeId(), r.status(), null, null, null, null,
            null, null, r.selfComment(), null, null, null, null, r.createdAt(), r.updatedAt());
    }
    private List<AllowedAction> allowedForMember(com.easyperformance.domain.evaluationcycle.entity.CycleStatus status,
                                                  List<EvaluationWorkspaceDtos.GoalResponse> memberGoals,
                                                  PerformanceReview review, ReportResponse report,
                                                  EvaluationWorkspaceDtos.FeedbackResponse feedback,
                                                  boolean appealEnabled) {
        List<AllowedAction> actions = new ArrayList<>();
        if (status == com.easyperformance.domain.evaluationcycle.entity.CycleStatus.GOAL_SETTING) {
            actions.add(AllowedAction.CREATE_GOAL);
            if (memberGoals.stream().anyMatch(g -> g.status() == GoalStatus.DRAFT || g.status() == GoalStatus.REJECTED)) {
                actions.add(AllowedAction.EDIT_GOAL);
                actions.add(AllowedAction.SUBMIT_GOAL);
            }
        }
        if (status == com.easyperformance.domain.evaluationcycle.entity.CycleStatus.MID_REVIEW) {
            actions.add(AllowedAction.ADD_CHECK_IN); actions.add(AllowedAction.EDIT_INTERMEDIATE_REVIEW);
            actions.add(AllowedAction.SUBMIT_INTERMEDIATE_REVIEW);
        }
        if (review != null && review.getStatus() == ReviewStatus.SELF_PENDING) {
            actions.add(AllowedAction.EDIT_SELF_REVIEW); actions.add(AllowedAction.SUBMIT_SELF_REVIEW);
        }
        if (report != null) { actions.add(AllowedAction.VIEW_REPORT); actions.add(AllowedAction.ACKNOWLEDGE_REPORT); }
        if (feedback != null && feedback.status() == FeedbackStatus.COMPLETED) {
            actions.add(AllowedAction.ACCEPT_FEEDBACK);
            if (appealEnabled) actions.add(AllowedAction.APPEAL_FEEDBACK);
        }
        return actions;
    }
}

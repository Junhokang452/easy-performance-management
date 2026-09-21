package com.easyperformance.workflow;

import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationConfirmResponse;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationSessionCreateRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationSessionResponse;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionApplyRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionApplyResponse;
import com.easyperformance.domain.calibration.service.CalibrationService;
import com.easyperformance.domain.report.dto.ReportDtos.ReportPublishResponse;
import com.easyperformance.domain.report.dto.ReportDtos.ReportResponse;
import com.easyperformance.domain.report.repository.PerformanceReportRepository;
import com.easyperformance.domain.report.service.ReportService;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewItemScoreInput;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewResponse;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewSubmitManagerRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewSubmitSelfRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewUpdateRequest;
import com.easyperformance.domain.review.entity.PerformanceReview;
import com.easyperformance.domain.review.repository.PerformanceReviewRepository;
import com.easyperformance.domain.review.service.ReviewService;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.ActorResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.AppealRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.AppealResolveRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CalibrationAdjustmentRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CalibrationApplyRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CalibrationConfirmRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CalibrationTasksResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CheckInCreateRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CheckInResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CycleAdvanceRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CycleAdvanceResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CycleLaunchResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.FeedbackResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.FeedbackUpdateRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.GoalCreateRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.GoalDecisionRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.GoalResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.GoalUpdateRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.IntermediateManagerRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.IntermediateReviewResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.IntermediateReviewUpsertRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.ManagerReviewRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.ManagerTaskResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.ReportPublishRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.ResultsSummaryResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.SelfReviewRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.WorkspaceResponse;
import com.easyperformance.workflow.ParticipantRosterDtos.EmployeeSummary;
import com.easyperformance.workflow.ParticipantRosterDtos.ParticipantReplaceRequest;
import com.easyperformance.workflow.ParticipantRosterDtos.ParticipantResponse;
import com.easyperformance.workflow.ParticipantRosterDtos.ParticipantRosterResponse;
import com.easyware.platform.error.ApiException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evaluation-workspace")
public class EvaluationWorkspaceController {
    private final ActorAccess actors;
    private final EmployeeDirectoryService directory;
    private final ParticipantRosterService roster;
    private final EvaluationWorkflowService workflow;
    private final EvaluationWorkspaceQueryService queries;
    private final GoalAgreementService goalService;
    private final IntermediateReviewService intermediateService;
    private final PerformanceReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final EvaluationParticipantRepository participants;
    private final EvaluationReviewerAssignmentRepository reviewers;
    private final CalibrationService calibrationService;
    private final ReportService reportService;
    private final PerformanceReportRepository reportRepository;
    private final PerformanceFeedbackService feedbackService;

    public EvaluationWorkspaceController(ActorAccess actors, EmployeeDirectoryService directory,
        ParticipantRosterService roster, EvaluationWorkflowService workflow, EvaluationWorkspaceQueryService queries,
        GoalAgreementService goalService, IntermediateReviewService intermediateService,
        PerformanceReviewRepository reviewRepository, ReviewService reviewService,
        EvaluationParticipantRepository participants, EvaluationReviewerAssignmentRepository reviewers,
        CalibrationService calibrationService, ReportService reportService,
        PerformanceReportRepository reportRepository, PerformanceFeedbackService feedbackService) {
        this.actors = actors; this.directory = directory; this.roster = roster; this.workflow = workflow;
        this.queries = queries; this.goalService = goalService; this.intermediateService = intermediateService;
        this.reviewRepository = reviewRepository; this.reviewService = reviewService; this.participants = participants;
        this.reviewers = reviewers; this.calibrationService = calibrationService; this.reportService = reportService;
        this.reportRepository = reportRepository; this.feedbackService = feedbackService;
    }

    @GetMapping("/me")
    public ActorResponse me() {
        var a = actors.requireActor();
        return new ActorResponse(a.userId(), a.tenantId(), a.employeeId(), a.displayName(), a.role());
    }

    @GetMapping("/directory")
    public Page<EmployeeSummary> directory(@RequestParam(defaultValue = "") String q, Pageable pageable) {
        var a = actors.requireAnyRole("SUPER_ADMIN", "HR_ADMIN", "DIRECTOR", "MANAGER");
        return directory.search(a.tenantId(), q, pageable);
    }

    @GetMapping("/cycles")
    public Page<com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleResponse> cycles(Pageable pageable) {
        return queries.visibleCycles(actors.requireEmployeeActor(), pageable);
    }

    @GetMapping("/cycles/{cycleId}")
    public com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleResponse cycle(@PathVariable UUID cycleId) {
        return queries.visibleCycle(actors.requireEmployeeActor(), cycleId);
    }

    @PutMapping("/cycles/{cycleId}/participants")
    public ParticipantRosterResponse replaceParticipants(@PathVariable UUID cycleId,
                                                          @Valid @RequestBody ParticipantReplaceRequest request) {
        var a = actors.requireAnyRole("SUPER_ADMIN", "HR_ADMIN");
        return roster.replaceRoster(a.tenantId(), cycleId, request);
    }

    @GetMapping("/cycles/{cycleId}/participants")
    public Page<ParticipantResponse> participants(@PathVariable UUID cycleId, Pageable pageable) {
        var a = actors.requireAnyRole("SUPER_ADMIN", "HR_ADMIN", "DIRECTOR", "MANAGER");
        workflow.requireCycle(a.tenantId(), cycleId);
        List<ParticipantResponse> all = participants.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(a.tenantId(), cycleId)
            .stream().filter(p -> !"MANAGER".equals(a.role()) || isManager(a, p))
            .map(p -> queries.participantResponse(a.tenantId(), p)).toList();
        int from = Math.min((int) pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }

    @PostMapping("/cycles/{cycleId}/open")
    public CycleLaunchResponse open(@PathVariable UUID cycleId) {
        return workflow.open(actors.requireEmployeeActor(), cycleId);
    }

    @PostMapping("/cycles/{cycleId}/advance")
    public CycleAdvanceResponse advance(@PathVariable UUID cycleId, @Valid @RequestBody CycleAdvanceRequest request) {
        return workflow.advance(actors.requireEmployeeActor(), cycleId, request.targetStatus());
    }
    @PostMapping("/cycles/{cycleId}/close")
    public com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleResponse close(@PathVariable UUID cycleId) {
        return workflow.close(actors.requireEmployeeActor(), cycleId);
    }

    @GetMapping("/cycles/{cycleId}/me")
    public WorkspaceResponse myWorkspace(@PathVariable UUID cycleId) {
        return queries.myWorkspace(actors.requireEmployeeActor(), cycleId);
    }

    @GetMapping("/manager/tasks")
    public List<ManagerTaskResponse> managerTasks(@RequestParam UUID cycleId) {
        var a = actors.requireEmployeeActor();
        if (!"MANAGER".equals(a.role()) && !"DIRECTOR".equals(a.role())) throw forbidden();
        return queries.managerTasks(a, cycleId);
    }

    @PostMapping("/cycles/{cycleId}/goals")
    public ResponseEntity<GoalResponse> createGoal(@PathVariable UUID cycleId,
                                                   @Valid @RequestBody GoalCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.create(actors.requireEmployeeActor(), cycleId, request));
    }
    @PatchMapping("/goals/{goalId}")
    public GoalResponse updateGoal(@PathVariable UUID goalId, @Valid @RequestBody GoalUpdateRequest request) {
        return goalService.update(actors.requireEmployeeActor(), goalId, request);
    }
    @PostMapping("/goals/{goalId}/submit")
    public GoalResponse submitGoal(@PathVariable UUID goalId) { return goalService.submit(actors.requireEmployeeActor(), goalId); }
    @PostMapping("/goals/{goalId}/decision")
    public GoalResponse decideGoal(@PathVariable UUID goalId, @Valid @RequestBody GoalDecisionRequest request) {
        return goalService.decide(actors.requireEmployeeActor(), goalId, request);
    }
    @PostMapping("/goals/{goalId}/check-ins")
    public ResponseEntity<CheckInResponse> checkIn(@PathVariable UUID goalId,
                                                   @Valid @RequestBody CheckInCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.checkIn(actors.requireEmployeeActor(), goalId, request));
    }
    @GetMapping("/goals/{goalId}/check-ins")
    public List<CheckInResponse> checkIns(@PathVariable UUID goalId) {
        return goalService.checkIns(actors.requireEmployeeActor(), goalId);
    }
    @GetMapping("/participants/{participantId}/goals")
    public List<GoalResponse> participantGoals(@PathVariable UUID participantId) {
        var a = actors.requireEmployeeActor();
        EvaluationParticipant p = requireManagedOrHr(a, participantId);
        return goalService.list(a.tenantId(), p.getCycleId(), p.getEmployeeId());
    }

    @PutMapping("/cycles/{cycleId}/me/intermediate-review")
    public IntermediateReviewResponse saveIntermediate(@PathVariable UUID cycleId,
        @Valid @RequestBody IntermediateReviewUpsertRequest request) {
        return intermediateService.saveDraft(actors.requireEmployeeActor(), cycleId, request);
    }
    @PostMapping("/cycles/{cycleId}/me/intermediate-review/submit")
    public IntermediateReviewResponse submitIntermediate(@PathVariable UUID cycleId,
        @Valid @RequestBody IntermediateReviewUpsertRequest request) {
        return intermediateService.submit(actors.requireEmployeeActor(), cycleId, request);
    }
    @GetMapping("/participants/{participantId}/intermediate-review")
    public IntermediateReviewResponse intermediate(@PathVariable UUID participantId) {
        return intermediateService.getForManager(actors.requireEmployeeActor(), participantId);
    }
    @PutMapping("/participants/{participantId}/intermediate-review")
    public IntermediateReviewResponse managerIntermediate(@PathVariable UUID participantId,
        @Valid @RequestBody IntermediateManagerRequest request) {
        return intermediateService.managerComment(actors.requireEmployeeActor(), participantId, request, false);
    }
    @PostMapping("/participants/{participantId}/intermediate-review/complete")
    public IntermediateReviewResponse completeIntermediate(@PathVariable UUID participantId) {
        return intermediateService.complete(actors.requireEmployeeActor(), participantId);
    }

    @GetMapping("/reviews/{reviewId}")
    public ReviewResponse review(@PathVariable UUID reviewId) {
        var a = actors.requireEmployeeActor();
        PerformanceReview review = requireReview(a, reviewId, false);
        ReviewResponse response = reviewService.getReview(review.getId());
        if (review.getEmployeeId().equals(a.employeeId()) && !hasPublishedReport(a, review)) {
            return new ReviewResponse(response.id(), response.cycleId(), response.employeeId(), response.status(),
                null, null, null, null, null, null, response.selfComment(), null, null,
                null, null, response.createdAt(), response.updatedAt());
        }
        return response;
    }
    @GetMapping("/reviews/{reviewId}/kpi-items")
    public List<com.easyperformance.domain.review.dto.ReviewDtos.ReviewKpiItemResponse> reviewItems(@PathVariable UUID reviewId) {
        var a = actors.requireEmployeeActor(); requireReview(a, reviewId, true);
        return reviewService.getKpiItems(reviewId);
    }
    @PostMapping("/reviews/{reviewId}/self/draft")
    public ReviewResponse selfDraft(@PathVariable UUID reviewId, @RequestBody SelfReviewRequest request) {
        var a = actors.requireEmployeeActor(); requireSelf(a, reviewId);
        return reviewService.updateReview(reviewId, new ReviewUpdateRequest(request.comment(), null, null));
    }
    @PostMapping("/reviews/{reviewId}/self/submit")
    public ReviewResponse selfSubmit(@PathVariable UUID reviewId, @RequestBody SelfReviewRequest request) {
        var a = actors.requireEmployeeActor(); requireSelf(a, reviewId);
        return reviewService.submitSelf(reviewId, new ReviewSubmitSelfRequest(request.comment()));
    }
    @PostMapping("/reviews/{reviewId}/manager/draft")
    public ReviewResponse managerDraft(@PathVariable UUID reviewId, @Valid @RequestBody ManagerReviewRequest request) {
        var a = actors.requireEmployeeActor(); requireReview(a, reviewId, true);
        return reviewService.updateReview(reviewId, new ReviewUpdateRequest(null, request.comment(), scores(request)));
    }
    @PostMapping("/reviews/{reviewId}/manager/submit")
    public ReviewResponse managerSubmit(@PathVariable UUID reviewId, @Valid @RequestBody ManagerReviewRequest request) {
        var a = actors.requireEmployeeActor();
        EvaluationReviewerAssignment assignment = requireManagerAssignment(a, requireParticipantForReview(a, reviewId));
        ReviewResponse response = reviewService.submitManager(reviewId,
            new ReviewSubmitManagerRequest(request.comment(), scores(request)));
        assignment.setStatus(ReviewerAssignmentStatus.SUBMITTED); reviewers.save(assignment);
        return response;
    }

    @GetMapping("/cycles/{cycleId}/calibration/tasks")
    public CalibrationTasksResponse calibrationTasks(@PathVariable UUID cycleId) {
        var a = actors.requireAnyRole("SUPER_ADMIN", "HR_ADMIN", "DIRECTOR"); return queries.calibrationTasks(a, cycleId);
    }
    @PostMapping("/cycles/{cycleId}/calibration-sessions")
    public CalibrationSessionResponse createCalibration(@PathVariable UUID cycleId,
        @RequestBody CalibrationSessionCreateRequest request) {
        var a = actors.requireAnyRole("SUPER_ADMIN", "HR_ADMIN", "DIRECTOR");
        List<UUID> ids = request.participantIds() == null ? participants
            .findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(a.tenantId(), cycleId).stream()
            .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE).map(EvaluationParticipant::getId).toList()
            : request.participantIds();
        CalibrationSessionResponse created = calibrationService.createSession(cycleId,
            new CalibrationSessionCreateRequest(request.ownerOrgUnitId(), request.scheduledAt(), ids));
        return calibrationService.transition(created.id(),
            new com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationTransitionRequest(
                com.easyperformance.domain.calibration.entity.CalibrationStatus.IN_SESSION, a.employeeId()));
    }
    @PostMapping("/calibration-sessions/{sessionId}/adjustments")
    public CalibrationSessionResponse adjust(@PathVariable UUID sessionId,
        @Valid @RequestBody CalibrationAdjustmentRequest request) {
        var a = actors.requireEmployeeActor();
        if (!List.of("SUPER_ADMIN", "HR_ADMIN", "DIRECTOR").contains(a.role())) throw forbidden();
        return calibrationService.adjust(sessionId,
            new com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationAdjustmentRequest(
                request.reviewId(), request.toGrade(), request.reason(), a.employeeId()));
    }
    @PostMapping("/cycles/{cycleId}/calibration/apply")
    public DistributionApplyResponse applyCalibration(@PathVariable UUID cycleId,
        @RequestBody CalibrationApplyRequest request) {
        var a = actors.requireEmployeeActor();
        if (!List.of("SUPER_ADMIN", "HR_ADMIN", "DIRECTOR").contains(a.role())) throw forbidden();
        return calibrationService.apply(cycleId, new DistributionApplyRequest(request.targetDistribution(), a.employeeId()));
    }
    @PostMapping("/cycles/{cycleId}/calibration/confirm")
    public CalibrationConfirmResponse confirmCalibration(@PathVariable UUID cycleId,
        @Valid @RequestBody CalibrationConfirmRequest request) {
        var a = actors.requireEmployeeActor();
        if (!List.of("SUPER_ADMIN", "HR_ADMIN", "DIRECTOR").contains(a.role())) throw forbidden();
        return calibrationService.confirm(request.sessionId(),
            new com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationConfirmRequest(a.employeeId(), true));
    }

    @PostMapping("/cycles/{cycleId}/reports/publish")
    public ReportPublishResponse publish(@PathVariable UUID cycleId, @RequestBody(required = false) ReportPublishRequest request) {
        var a = actors.requireEmployeeActor(); if (!List.of("SUPER_ADMIN", "HR_ADMIN").contains(a.role())) throw forbidden();
        return reportService.publish(cycleId, a.employeeId());
    }
    @GetMapping("/cycles/{cycleId}/report")
    public ReportResponse myReport(@PathVariable UUID cycleId) {
        var a = actors.requireEmployeeActor(); return reportService.getMyReport(cycleId, a.employeeId());
    }
    @PostMapping("/reports/{reportId}/acknowledge")
    public ReportResponse acknowledge(@PathVariable UUID reportId) {
        var a = actors.requireEmployeeActor(); requireOwnedReport(a, reportId);
        return reportService.acknowledge(reportId, a.employeeId());
    }
    @PutMapping("/reports/{reportId}/feedback")
    public FeedbackResponse feedbackDraft(@PathVariable UUID reportId, @Valid @RequestBody FeedbackUpdateRequest request) {
        return feedbackService.update(actors.requireEmployeeActor(), reportId, request, false);
    }
    @PostMapping("/reports/{reportId}/feedback/complete")
    public FeedbackResponse feedbackComplete(@PathVariable UUID reportId, @Valid @RequestBody FeedbackUpdateRequest request) {
        return feedbackService.update(actors.requireEmployeeActor(), reportId, request, true);
    }
    @PostMapping("/reports/{reportId}/accept")
    public FeedbackResponse accept(@PathVariable UUID reportId) { return feedbackService.accept(actors.requireEmployeeActor(), reportId); }
    @PostMapping("/reports/{reportId}/appeal")
    public FeedbackResponse appeal(@PathVariable UUID reportId, @Valid @RequestBody AppealRequest request) {
        return feedbackService.appeal(actors.requireEmployeeActor(), reportId, request);
    }
    @PostMapping("/reports/{reportId}/appeal/resolve")
    public FeedbackResponse resolve(@PathVariable UUID reportId, @Valid @RequestBody AppealResolveRequest request) {
        return feedbackService.resolve(actors.requireEmployeeActor(), reportId, request);
    }
    @GetMapping("/reports/{reportId}/feedback")
    public FeedbackResponse feedback(@PathVariable UUID reportId) {
        return feedbackService.get(actors.requireEmployeeActor(), reportId);
    }
    @GetMapping("/cycles/{cycleId}/feedback")
    public List<EvaluationWorkspaceDtos.FeedbackTaskResponse> feedbackTasks(@PathVariable UUID cycleId) {
        var a = actors.requireAnyRole("SUPER_ADMIN", "HR_ADMIN", "MANAGER");
        return queries.feedbackTasks(a, cycleId);
    }
    @GetMapping("/cycles/{cycleId}/results/summary")
    public ResultsSummaryResponse results(@PathVariable UUID cycleId) {
        return queries.results(actors.requireAnyRole("SUPER_ADMIN", "HR_ADMIN", "DIRECTOR"), cycleId);
    }

    private EvaluationParticipant requireParticipantForReview(ActorAccess.Actor a, UUID reviewId) {
        PerformanceReview review = reviewRepository.findByIdAndTenantId(reviewId, a.tenantId())
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REVIEW_NOT_FOUND));
        return participants.findByTenantIdAndCycleIdAndEmployeeId(a.tenantId(), review.getCycleId(), review.getEmployeeId())
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.PARTICIPANT_NOT_FOUND));
    }
    private PerformanceReview requireReview(ActorAccess.Actor a, UUID reviewId, boolean managerOnly) {
        PerformanceReview review = reviewRepository.findByIdAndTenantId(reviewId, a.tenantId())
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REVIEW_NOT_FOUND));
        if (!managerOnly && review.getEmployeeId().equals(a.employeeId())) return review;
        requireManagerAssignment(a, requireParticipantForReview(a, reviewId)); return review;
    }
    private void requireSelf(ActorAccess.Actor a, UUID reviewId) {
        PerformanceReview review = reviewRepository.findByIdAndTenantId(reviewId, a.tenantId())
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REVIEW_NOT_FOUND));
        if (!review.getEmployeeId().equals(a.employeeId())) throw forbidden();
    }
    private EvaluationParticipant requireManagedOrHr(ActorAccess.Actor a, UUID participantId) {
        EvaluationParticipant p = participants.findByIdAndTenantId(participantId, a.tenantId())
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.PARTICIPANT_NOT_FOUND));
        if (List.of("SUPER_ADMIN", "HR_ADMIN").contains(a.role())) return p;
        requireManagerAssignment(a, p); return p;
    }
    private EvaluationReviewerAssignment requireManagerAssignment(ActorAccess.Actor a, EvaluationParticipant p) {
        EvaluationReviewerAssignment assignment = reviewers.findByTenantIdAndParticipantIdAndReviewerTypeAndRound(
            a.tenantId(), p.getId(), ReviewerType.MANAGER, 1)
            .filter(r -> r.getStatus() != ReviewerAssignmentStatus.REVOKED)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REVIEWER_ASSIGNMENT_NOT_FOUND));
        if (!assignment.getReviewerEmployeeId().equals(a.employeeId()) && !List.of("SUPER_ADMIN", "HR_ADMIN").contains(a.role()))
            throw forbidden();
        return assignment;
    }
    private boolean isManager(ActorAccess.Actor a, EvaluationParticipant p) {
        return reviewers.findByTenantIdAndParticipantIdAndReviewerTypeAndRound(a.tenantId(), p.getId(), ReviewerType.MANAGER, 1)
            .map(r -> r.getReviewerEmployeeId().equals(a.employeeId()) && r.getStatus() != ReviewerAssignmentStatus.REVOKED).orElse(false);
    }
    private List<ReviewItemScoreInput> scores(ManagerReviewRequest request) {
        return request.itemScores().stream().map(i -> new ReviewItemScoreInput(i.assignmentId(), i.managerScore())).toList();
    }
    private void requireOwnedReport(ActorAccess.Actor a, UUID reportId) {
        var report = reportRepository.findByIdAndTenantId(reportId, a.tenantId())
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REPORT_NOT_FOUND));
        if (!report.getEmployeeId().equals(a.employeeId())) throw forbidden();
    }
    private boolean hasPublishedReport(ActorAccess.Actor a, PerformanceReview review) {
        return !reportRepository.findAllByTenantIdAndCycleIdAndEmployeeId(
            a.tenantId(), review.getCycleId(), review.getEmployeeId()).isEmpty();
    }
    private ApiException forbidden() { return new ApiException(PerformanceErrorCode.WORKFLOW_FORBIDDEN); }
}

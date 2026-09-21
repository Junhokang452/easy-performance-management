package com.easyperformance.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewResponse;
import com.easyperformance.domain.report.dto.ReportDtos.ReportResponse;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleResponse;
import com.easyperformance.domain.review.entity.ReviewStatus;
import com.easyperformance.domain.calibration.entity.CalibrationStatus;

public final class EvaluationWorkspaceDtos {
    private EvaluationWorkspaceDtos() {}

    public record ActorResponse(UUID userId, UUID tenantId, UUID employeeId, String displayName, String role) {}
    public enum BlockerCode {
        MISSING_EMPLOYEE_BINDING, NO_ACTIVE_PARTICIPANTS, PARTICIPANT_NOT_ACTIVE, MISSING_MANAGER,
        GOAL_MISSING, GOAL_NOT_APPROVED, GOAL_ACTUAL_MISSING, MID_REVIEW_INCOMPLETE, SELF_REVIEW_INCOMPLETE,
        MANAGER_REVIEW_INCOMPLETE, CALIBRATION_INCOMPLETE, REPORT_NOT_PUBLISHED,
        REPORT_NOT_ACKNOWLEDGED, FEEDBACK_INCOMPLETE
    }
    public enum AllowedAction {
        CREATE_GOAL, EDIT_GOAL, SUBMIT_GOAL, REVIEW_GOAL, EDIT_INTERMEDIATE_REVIEW,
        SUBMIT_INTERMEDIATE_REVIEW, COMPLETE_INTERMEDIATE_REVIEW, EDIT_SELF_REVIEW,
        SUBMIT_SELF_REVIEW, EDIT_MANAGER_REVIEW, SUBMIT_MANAGER_REVIEW, VIEW_REPORT,
        ACKNOWLEDGE_REPORT, ADD_CHECK_IN, EDIT_FEEDBACK, COMPLETE_FEEDBACK,
        ACCEPT_FEEDBACK, APPEAL_FEEDBACK, RESOLVE_APPEAL
    }
    public record GateBlocker(BlockerCode code, String message, UUID participantId, UUID employeeId) {}
    public record CycleLaunchResponse(com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleResponse cycle,
                                      int createdReviews, List<GateBlocker> blockers) {}
    public record CycleAdvanceResponse(com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleResponse cycle,
                                       int affectedCount, List<GateBlocker> blockers) {}
    public record GoalCreateRequest(UUID employeeId, @NotBlank @Size(max = 200) String title,
                                    String description, @NotNull BigDecimal weight,
                                    BigDecimal target, @Size(max = 20) String unit) {}
    public record GoalUpdateRequest(@Size(max = 200) String title, String description,
                                    BigDecimal weight, BigDecimal target, @Size(max = 20) String unit) {}
    public record GoalDecisionRequest(@NotNull GoalDecision decision, String comment) {}
    public record GoalResponse(UUID id, UUID cycleId, UUID employeeId, UUID kpiAssignmentId,
                               String title, String description, BigDecimal weight, BigDecimal target,
                               String unit, GoalStatus status, String decisionComment,
                               Instant approvedAt, UUID approvedBy, Instant createdAt, Instant updatedAt) {}
    public record SelfReviewRequest(String comment) {}
    public record ManagerReviewRequest(String comment, @NotEmpty @Valid List<ManagerScoreInput> itemScores) {}
    public record ManagerScoreInput(@NotNull UUID assignmentId, BigDecimal managerScore) {}
    public record CycleAdvanceRequest(@NotNull com.easyperformance.domain.evaluationcycle.entity.CycleStatus targetStatus) {}
    public record CalibrationApplyRequest(java.util.Map<String, BigDecimal> targetDistribution) {}
    public record CalibrationConfirmRequest(@NotNull UUID sessionId) {}
    public record CalibrationAdjustmentRequest(@NotNull UUID reviewId, @NotBlank String toGrade,
                                               @NotBlank String reason) {}
    public record ReportPublishRequest(List<UUID> employeeIds) {}
    public record FeedbackUpdateRequest(@NotBlank String comment) {}
    public record AppealRequest(@NotBlank String reason) {}
    public record AppealResolveRequest(@NotNull FeedbackResolution resolution, @NotBlank String comment) {}
    public record FeedbackResponse(UUID id, UUID reportId, UUID participantId, FeedbackStatus status,
                                   String comment, Instant completedAt, UUID completedBy,
                                   String appealReason, Instant appealedAt, FeedbackResolution resolution,
                                   String resolutionComment, Instant resolvedAt, UUID resolvedBy,
                                   Instant updatedAt) {}
    public record IntermediateReviewUpsertRequest(@NotBlank String progressSummary, String achievements,
                                                  String blockers, String supportNeeded) {}
    public record IntermediateManagerRequest(@NotBlank String managerComment) {}
    public record IntermediateReviewResponse(UUID id, UUID cycleId, UUID participantId, UUID employeeId,
                                             IntermediateReviewStatus status, String progressSummary,
                                             String achievements, String blockers, String supportNeeded,
                                             String managerComment, Instant employeeSubmittedAt,
                                             Instant managerCompletedAt, Instant createdAt, Instant updatedAt) {}
    public record CheckInCreateRequest(@NotNull LocalDate asOfDate, @NotNull BigDecimal actualValue,
                                       @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal progressPercent,
                                       String note, @Size(max = 500) String evidenceUrl) {}
    public record CheckInResponse(UUID id, UUID goalId, LocalDate asOfDate, BigDecimal actualValue,
                                  BigDecimal progressPercent, String note, String evidenceUrl,
                                  Instant createdAt) {}
    public record WorkspaceResponse(CycleResponse cycle, ParticipantRosterDtos.ParticipantResponse participant,
                                    List<GoalResponse> goals, IntermediateReviewResponse intermediateReview,
                                    ReviewResponse review, ReportResponse report, FeedbackResponse feedback,
                                    List<AllowedAction> allowedActions, List<GateBlocker> blockers) {}
    public record ManagerTaskResponse(UUID participantId, UUID cycleId,
                                      ParticipantRosterDtos.EmployeeSummary employee,
                                      int goalTotal, int goalPendingApproval,
                                      IntermediateReviewStatus intermediateReviewStatus,
                                      UUID reviewId, ReviewStatus reviewStatus,
                                      List<AllowedAction> allowedActions, List<GateBlocker> blockers) {}
    public record CalibrationSessionSummary(UUID id, CalibrationStatus status, Instant scheduledAt,
                                            UUID ownerOrgUnitId, int participantCount) {}
    public record CalibrationReviewRow(UUID reviewId, UUID participantId,
                                       ParticipantRosterDtos.EmployeeSummary employee,
                                       BigDecimal kpiScore, String currentGrade, String proposedGrade,
                                       boolean adjusted) {}
    public record CalibrationTasksResponse(UUID cycleId, List<CalibrationSessionSummary> sessions,
                                           List<CalibrationReviewRow> rows,
                                           Map<String, BigDecimal> targetDistribution,
                                           Map<String, Integer> currentDistribution) {}
    public record OrgUnitResultRow(UUID orgUnitId, String orgUnitName, int participantCount,
                                   BigDecimal averageScore, Map<String, Integer> gradeCounts) {}
    public record ResultsSummaryResponse(UUID cycleId, int participantCount, int finalizedCount,
                                         int publishedCount, int acknowledgedCount,
                                         BigDecimal averageScore, Map<String, Integer> gradeCounts,
                                         List<OrgUnitResultRow> orgUnitRows) {}
    public record FeedbackTaskResponse(UUID reportId, UUID participantId,
                                       ParticipantRosterDtos.EmployeeSummary employee,
                                       FeedbackStatus status, String comment, String appealReason,
                                       FeedbackResolution resolution, List<AllowedAction> allowedActions) {}
}

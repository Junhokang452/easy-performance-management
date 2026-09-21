package com.easyperformance.program;

import com.easyperformance.program.ProgramTypes.AdjustmentMethod;
import com.easyperformance.program.ProgramTypes.AdjustmentStatus;
import com.easyperformance.program.ProgramTypes.AdjustmentTarget;
import com.easyperformance.program.ProgramTypes.AppealResolution;
import com.easyperformance.program.ProgramTypes.AssignmentStatus;
import com.easyperformance.program.ProgramTypes.CalculationStatus;
import com.easyperformance.program.ProgramTypes.ConditionField;
import com.easyperformance.program.ProgramTypes.ConditionOperator;
import com.easyperformance.program.ProgramTypes.EvaluationKind;
import com.easyperformance.program.ProgramTypes.EvaluationMethod;
import com.easyperformance.program.ProgramTypes.FeedbackStatus;
import com.easyperformance.program.ProgramTypes.FormMode;
import com.easyperformance.program.ProgramTypes.GoalMode;
import com.easyperformance.program.ProgramTypes.GoalStatus;
import com.easyperformance.program.ProgramTypes.ItemAssignmentMode;
import com.easyperformance.program.ProgramTypes.MemberResultVisibility;
import com.easyperformance.program.ProgramTypes.NotificationChannel;
import com.easyperformance.program.ProgramTypes.NotificationStatus;
import com.easyperformance.program.ProgramTypes.ParticipantStatus;
import com.easyperformance.program.ProgramTypes.PopulationBasis;
import com.easyperformance.program.ProgramTypes.PreviousRoundVisibility;
import com.easyperformance.program.ProgramTypes.ProgramStage;
import com.easyperformance.program.ProgramTypes.ProgramStageStatus;
import com.easyperformance.program.ProgramTypes.ProgramStatus;
import com.easyperformance.program.ProgramTypes.ReviewerRole;
import com.easyperformance.program.ProgramTypes.ScaleKind;
import com.easyperformance.program.ProgramTypes.ScaleUse;
import com.easyperformance.program.ProgramTypes.SubmissionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Typed public contract. Configuration JSON is never accepted as an untyped string or map. */
public final class ProgramDtos {
    private ProgramDtos() {}

    public record ProgramCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Min(2000) @Max(2200) Integer evaluationYear,
        @NotNull LocalDate asOfDate,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        @NotNull EvaluationKind kind) {}

    public record ProgramCopyRequest(@NotBlank @Size(max = 120) String name,
                                     @NotNull Integer evaluationYear,
                                     @NotNull LocalDate asOfDate,
                                     @NotNull LocalDate startsOn,
                                     @NotNull LocalDate endsOn) {}

    public record ProgramBasicUpdateRequest(@NotBlank @Size(max = 120) String name,
                                            @NotNull @Min(2000) @Max(2200) Integer evaluationYear,
                                            @NotNull LocalDate asOfDate,
                                            @NotNull LocalDate startsOn,
                                            @NotNull LocalDate endsOn) {}

    public record ProgramSummaryResponse(UUID id, String name, Integer evaluationYear,
                                         EvaluationKind kind, ProgramStatus status,
                                         LocalDate asOfDate, LocalDate startsOn, LocalDate endsOn,
                                         int definitionRevision, long participantCount,
                                         Instant createdAt, Instant updatedAt) {}

    public record ProgramResponse(UUID id, UUID tenantId, String name, Integer evaluationYear,
                                  EvaluationKind kind, ProgramStatus status, LocalDate asOfDate,
                                  LocalDate startsOn, LocalDate endsOn, int definitionRevision,
                                  ProgramConfiguration configuration, Instant openedAt,
                                  Instant finalizedAt, Instant createdAt, Instant updatedAt) {}

    public record MemberStageOverview(ProgramStage stage, LocalDate startsOn, LocalDate endsOn) {}
    public record MemberScaleLevel(String code, String label, String color) {}
    public record MemberScaleOverview(String name, ScaleKind kind, List<MemberScaleLevel> levels) {}
    public record MemberProgramOverview(UUID id, String name, Integer evaluationYear,
                                        EvaluationKind kind, ProgramStatus status,
                                        LocalDate asOfDate, LocalDate startsOn, LocalDate endsOn,
                                        GoalMode goalMode, List<MemberStageOverview> stages,
                                        MemberScaleOverview inputScale,
                                        PreviousRoundVisibility previousRoundVisibility,
                                        boolean showReviewerAllocation,
                                        MemberResultVisibility memberResultVisibility,
                                        boolean feedbackEnabled, boolean appealEnabled) {}

    public record ProgramConfiguration(
        @NotNull GoalMode goalMode,
        @NotEmpty @Valid List<StageDefinitionInput> stages,
        @NotEmpty @Valid List<ScaleDefinitionInput> scales,
        @NotNull @Valid CalculationPolicyInput calculation,
        @NotNull @Valid PublicationPolicyInput publication,
        @Valid List<RevieweeGroupInput> groups,
        @Valid List<CommonItemInput> commonItems,
        @Valid List<DepartmentPerformanceGroupInput> departmentPerformanceGroups,
        @Valid List<AllocationRowInput> allocationRows) {}

    public record DefinitionUpdateRequest(@NotNull @Valid ProgramConfiguration configuration,
                                          @NotBlank @Size(max = 500) String revisionReason) {}

    public record StageDefinitionInput(@NotNull ProgramStage stage, boolean enabled,
                                       LocalDate startsOn, LocalDate endsOn,
                                       FormMode formMode, boolean itemOpinionEnabled,
                                       boolean itemOpinionRequired, UUID guideAttachmentId) {}

    public record ScaleDefinitionInput(@NotNull UUID id, @NotBlank @Size(max = 100) String name,
                                       @NotNull ScaleUse use, @NotNull ScaleKind kind,
                                       @NotEmpty @Valid List<ScaleLevelInput> levels) {}

    public record ScaleLevelInput(@NotBlank @Size(max = 20) String code,
                                  @NotBlank @Size(max = 100) String label,
                                  BigDecimal convertedScore, BigDecimal lowerExclusive,
                                  BigDecimal upperInclusive, @Size(max = 20) String color) {}

    public record CalculationPolicyInput(
        @NotNull UUID inputScaleId, @NotNull UUID resultScaleId, UUID departmentResultScaleId,
        boolean departmentPerformanceEnabled,
        @NotNull AdjustmentTarget adjustmentTarget,
        @NotNull AdjustmentMethod adjustmentMethod,
        @NotNull PopulationBasis populationBasis,
        @DecimalMin("0") @DecimalMax("100") BigDecimal targetMean,
        @DecimalMin("0") BigDecimal targetStandardDeviation,
        @Valid List<ComponentWeightInput> componentWeights,
        @NotNull Integer decimalPlaces) {}

    public record ComponentWeightInput(@NotBlank String component,
                                       @DecimalMin("0") @DecimalMax("100") BigDecimal weightPercent) {}

    public record PublicationPolicyInput(@NotNull PreviousRoundVisibility previousRoundVisibility,
                                         boolean showReviewerAllocation,
                                         @NotNull MemberResultVisibility memberResultVisibility,
                                         boolean scoreAdjustmentAllowed,
                                         BigDecimal reviewerMeanMinimum,
                                         BigDecimal reviewerMeanMaximum,
                                         @Min(0) Integer maximumGradeStepAdjustment,
                                         boolean multiRaterOpinionsVisible,
                                         boolean feedbackEnabled,
                                         boolean appealEnabled) {}

    public record RevieweeGroupInput(@NotNull UUID id, @NotBlank @Size(max = 100) String name,
                                     @Size(max = 500) String definition,
                                     @NotNull ItemAssignmentMode itemAssignmentMode,
                                     @NotNull EvaluationMethod evaluationMethod,
                                     boolean intermediateEnabled, boolean selfReviewEnabled,
                                     @Min(0) int priority,
                                     @Valid List<GroupConditionInput> conditions,
                                     @NotEmpty @Valid List<ReviewerWeightPlanInput> reviewerWeightPlans) {}

    public record GroupConditionInput(@NotNull ConditionField field,
                                      @NotNull ConditionOperator operator,
                                      @NotEmpty List<@NotBlank String> values) {}

    /** reviewerWeights keys are review rounds (1..3), values and departmentWeight are percentages. */
    public record ReviewerWeightPlanInput(@Min(1) @Max(3) int actualReviewerCount,
                                          @NotEmpty Map<@Min(1) @Max(3) Integer,
                                              @DecimalMin("0") @DecimalMax("100") BigDecimal> reviewerWeights,
                                          @DecimalMin("0") @DecimalMax("100") BigDecimal departmentWeight) {}

    public record CommonItemInput(@NotNull UUID id, @NotNull UUID groupId, @Min(0) @Max(3) int round,
                                  UUID catalogItemId, @NotBlank @Size(max = 200) String title,
                                  @Size(max = 2000) String definition,
                                  @DecimalMin("0") @DecimalMax("100") BigDecimal weightPercent,
                                  @NotNull UUID scaleId, @Min(0) int displayOrder,
                                  boolean opinionRequired) {}

    public record DepartmentPerformanceGroupInput(@NotNull UUID id, @NotBlank @Size(max = 100) String name,
                                                  @NotBlank @Size(max = 20) String grade,
                                                  @Min(0) int displayOrder,
                                                  @Valid List<GroupConditionInput> conditions) {}

    /** gradeHeadcounts are exact integer seats, not percentages. */
    public record AllocationRowInput(@Min(1) int populationSize,
                                     @NotEmpty Map<@NotBlank String, @Min(0) Integer> gradeHeadcounts) {}

    public record DefinitionRevisionResponse(UUID id, UUID programId, int revision,
                                             String reason, ProgramConfiguration configuration,
                                             UUID createdBy, Instant createdAt) {}

    public record ParticipantGenerateRequest(List<UUID> employeeIds) {}
    public record ParticipantAddRequest(@NotNull UUID employeeId, UUID assignmentId,
                                        UUID orgUnitId, @DecimalMin("0.0001") @DecimalMax("100") BigDecimal weightPercent) {}
    public record ParticipantChangeRequest(UUID groupId, ParticipantStatus status,
                                           @Size(max = 500) String reason,
                                           @DecimalMin("0.0001") @DecimalMax("100") BigDecimal weightPercent) {}
    public record ParticipantAttributes(UUID employeeId, String employeeNo, String name,
                                        UUID assignmentId, UUID orgUnitId, String orgUnitName,
                                        String positionCode, String gradeCode, String jobCode,
                                        String employmentType) {}
    public record ParticipantResponse(UUID id, UUID programId, ParticipantAttributes employee,
                                      UUID groupId, String groupName, ParticipantStatus status,
                                      BigDecimal weightPercent, ProgramStage currentStage,
                                      ProgramStageStatus stageStatus, int currentRound,
                                      boolean resultPublished, String exclusionReason,
                                      long rowVersion, Instant updatedAt) {}

    public record ReviewerInput(@NotNull UUID employeeId, @NotNull ReviewerRole role,
                                @Min(0) @Max(3) int round,
                                @DecimalMin("0") @DecimalMax("100") BigDecimal weightPercent) {}
    public record ReviewerReplaceRequest(@NotEmpty @Valid List<ReviewerInput> reviewers) {}
    public record ReviewerResponse(UUID id, UUID participantId, UUID reviewerEmployeeId,
                                   String reviewerName, ReviewerRole role, int round,
                                   BigDecimal weightPercent, AssignmentStatus status) {}

    public record StageBatchRequest(List<UUID> participantIds,
                                    @NotBlank @Size(max = 500) String reason) {}
    public record StageExceptionRequest(@NotNull ProgramStage toStage,
                                        @NotNull ProgramStageStatus toStatus,
                                        @Min(0) @Max(3) int round,
                                        @NotBlank @Size(max = 500) String reason) {}
    public record BatchExclusion(UUID participantId, String code, String reason) {}
    public record BatchOperationResponse(int changedCount, List<UUID> changedParticipantIds,
                                         List<BatchExclusion> excluded) {}

    public record GoalUpsertRequest(UUID catalogItemId, UUID departmentGoalId,
                                    @NotBlank @Size(max = 200) String title,
                                    @NotBlank @Size(max = 2000) String definition,
                                    @DecimalMin("0.0001") @DecimalMax("100") BigDecimal weightPercent,
                                    BigDecimal targetValue, @Size(max = 20) String unit,
                                    @Valid List<AchievementLevelInput> achievementLevels) {}
    public record AchievementLevelInput(@NotBlank String code, @NotBlank String label,
                                        BigDecimal thresholdValue) {}
    public record GoalOpinionRequest(@NotBlank @Size(max = 4000) String opinion) {}
    public record GoalDecisionRequest(boolean approve, @NotBlank @Size(max = 4000) String opinion) {}
    public record GoalSelfReportRequest(@NotNull String achievedLevelCode,
                                        @NotBlank @Size(max = 4000) String achievementSummary,
                                        List<UUID> taskIds) {}
    public record GoalResponse(UUID id, UUID participantId, UUID catalogItemId,
                               UUID departmentGoalId, String title, String definition,
                               BigDecimal weightPercent, BigDecimal targetValue, String unit,
                               List<AchievementLevelInput> achievementLevels, GoalStatus status,
                               String draftOpinion, String decisionOpinion,
                               String achievedLevelCode, String achievementSummary,
                               List<TaskEvidenceSnapshot> taskEvidence,
                               int revision, long rowVersion, Instant updatedAt) {}
    public record GoalHistoryResponse(UUID id, UUID goalId, int revision, GoalStatus status,
                                      String operation, String opinion, UUID actorEmployeeId,
                                      Instant createdAt) {}

    public record IntermediateUpdateRequest(@NotBlank @Size(max = 8000) String opinion,
                                            List<UUID> taskIds) {}
    public record IntermediateResponse(UUID id, UUID participantId, UUID checkerEmployeeId,
                                       String opinion, List<TaskEvidenceSnapshot> taskEvidence,
                                       SubmissionStatus status, Instant completedAt, long rowVersion) {}
    public record TaskEvidenceSnapshot(UUID taskId, String title, String status,
                                       BigDecimal progressPercent, Instant occurredAt, String summary) {}

    public record ReviewItemAnswerInput(@NotNull UUID itemId, String scaleCode,
                                        BigDecimal numericScore, @Size(max = 4000) String opinion) {}
    public record ReviewSaveRequest(@NotEmpty @Valid List<ReviewItemAnswerInput> answers,
                                    @Size(max = 8000) String overallOpinion) {}
    public record ReviewItemAnswerResponse(UUID itemId, String title, BigDecimal weightPercent,
                                           String scaleCode, BigDecimal numericScore, String opinion) {}
    public record ReviewSubmissionResponse(UUID id, UUID participantId, UUID reviewerAssignmentId,
                                           ReviewerRole role, int round,
                                           List<ReviewItemAnswerResponse> answers,
                                           String overallOpinion, SubmissionStatus status,
                                           Instant completedAt, long rowVersion) {}
    public record ReviewScaleOverview(UUID id, String name, ScaleKind kind,
                                      List<MemberScaleLevel> levels) {}
    public record ReviewContextResponse(ParticipantResponse participant, List<GoalResponse> goals,
                                        IntermediateResponse intermediate,
                                        List<ReviewSubmissionResponse> visiblePreviousRounds,
                                        ReviewSubmissionResponse currentSubmission,
                                        List<CommonItemInput> items,
                                        List<ReviewScaleOverview> inputScales) {}

    public record ScoreContribution(String component, BigDecimal score, BigDecimal weightPercent) {}
    public record ScoreCalculationResult(BigDecimal score, List<ScoreContribution> contributions) {}
    public record CalculationRunRequest(List<UUID> participantIds, boolean excludeIncomplete,
                                        @NotBlank @Size(max = 500) String reason) {}
    public record CalculationResponse(UUID id, UUID participantId, int revision,
                                      List<ScoreContribution> contributions,
                                      BigDecimal rawScore, BigDecimal normalizedScore,
                                      BigDecimal adjustedScore, String calculatedGrade,
                                      CalculationStatus status, String formula,
                                      List<String> warnings, Instant calculatedAt) {}

    public record AdjustmentSaveRequest(BigDecimal adjustedScore, @NotBlank String adjustedGrade,
                                        @NotBlank @Size(max = 4000) String reason) {}
    public record AdjustmentResponse(UUID id, UUID participantId, int revision, UUID calculationId,
                                     BigDecimal beforeScore, String beforeGrade,
                                     BigDecimal adjustedScore, String adjustedGrade,
                                     String reason, AdjustmentStatus status,
                                     UUID actorEmployeeId, Instant completedAt, long rowVersion) {}

    public record FeedbackSaveRequest(@NotBlank @Size(max = 8000) String comment) {}
    public record FeedbackAppealRequest(@NotBlank @Size(max = 8000) String reason) {}
    public record FeedbackResolveRequest(@NotNull AppealResolution resolution,
                                         BigDecimal adjustedScore, String adjustedGrade,
                                         @NotBlank @Size(max = 8000) String comment) {}
    public record FeedbackResponse(UUID id, UUID participantId, UUID writerEmployeeId,
                                   String comment, FeedbackStatus status, String appealReason,
                                   AppealResolution resolution, String resolutionComment,
                                   Instant deliveredAt, Instant resolvedAt, long rowVersion) {}

    public record FinalizeRequest(@NotBlank @Size(max = 500) String reason) {}
    public record FinalizationCancelRequest(@NotBlank @Size(max = 1000) String reason) {}
    public record PublishRequest(@NotEmpty List<UUID> participantIds) {}

    public record NotificationPreviewRequest(@NotEmpty List<UUID> recipientEmployeeIds,
                                             @NotBlank @Size(max = 200) String companyName,
                                             @NotBlank @Size(max = 200) String subjectTemplate,
                                             @NotBlank @Size(max = 8000) String bodyTemplate,
                                             @NotNull NotificationChannel channel) {}
    public record NotificationPreview(UUID recipientEmployeeId, String subject, String body) {}
    public record NotificationQueueResponse(int queued, NotificationStatus status,
                                            List<UUID> notificationIds) {}
    public record NotificationDispatchResponse(int sent, int failed, int configurationRequired) {}
    public record NotificationResponse(UUID id, UUID recipientEmployeeId,
                                       NotificationChannel channel, NotificationStatus status,
                                       String subject, String body, Instant sentAt, Instant readAt) {}
    public record GuideAttachmentResponse(UUID id, UUID programId, String filename,
                                          String contentType, long size, UUID uploadedBy,
                                          Instant createdAt) {}

    public record ProgramDashboardResponse(UUID programId, long participantCount,
                                           long excludedCount, Map<ProgramStage, Long> ready,
                                           Map<ProgramStage, Long> inProgress,
                                           Map<ProgramStage, Long> completed,
                                           Map<ProgramStage, Long> blocked) {}
    public record MyProgramWorkspaceResponse(MemberProgramOverview program,
                                             ParticipantResponse participant,
                                             List<GoalResponse> goals,
                                             IntermediateResponse intermediate,
                                             List<ReviewSubmissionResponse> submissions,
                                             CalculationResponse calculation,
                                             AdjustmentResponse adjustment,
                                             FeedbackResponse feedback,
                                             List<String> allowedActions,
                                             List<String> blockers) {}
    public record GradeCount(String grade, long count) {}
    public record ResultRow(UUID participantId, ParticipantAttributes employee,
                            BigDecimal score, String grade, boolean published,
                            String feedbackStatus) {}
    public record ResultSummaryResponse(UUID programId, long finalizedCount,
                                        List<GradeCount> grades, List<ResultRow> rows) {}
    public record ItemResultRow(UUID participantId, ParticipantAttributes employee,
                                UUID itemId, String itemTitle, int round,
                                BigDecimal weightPercent, BigDecimal score,
                                String scaleCode, String grade) {}
    public record ReviewerResultRow(UUID reviewerEmployeeId, String reviewerName,
                                    UUID participantId, ParticipantAttributes employee,
                                    int round, BigDecimal score, String grade,
                                    String opinion) {}
    public record EmployeeFeedbackDetail(UUID participantId, ParticipantAttributes employee,
                                         CalculationResponse calculation,
                                         AdjustmentResponse adjustment,
                                         FeedbackResponse feedback,
                                         List<ReviewSubmissionResponse> submissions) {}
    public record GradeMatrixCell(String xGrade, String yGrade, long count,
                                  List<ParticipantAttributes> employees) {}
    public record GradeMatrixResponse(UUID xProgramId, UUID yProgramId,
                                      List<GradeMatrixCell> cells) {}
    public record PivotRequest(@NotNull UUID programId, @NotEmpty List<String> rowAxes,
                               @NotEmpty List<String> columnAxes) {}
    public record PivotCell(Map<String, String> dimensions, String grade, long count,
                            List<ParticipantAttributes> employees) {}
    public record PivotResponse(List<PivotCell> cells) {}
    public record ReviewerTendencyResponse(UUID reviewerEmployeeId, String reviewerName,
                                           long targetCount, BigDecimal meanScore,
                                           Integer organizationMeanRank, BigDecimal standardDeviation,
                                           BigDecimal opinionSpecificity,
                                           BigDecimal positiveRatio, BigDecimal neutralRatio,
                                           BigDecimal negativeRatio, BigDecimal averageOpinionLength,
                                           List<String> frequentKeywords,
                                           List<String> unavailableReasons) {}
    public record PersonalHistoryPoint(Integer year, UUID programId, String programName,
                                       EvaluationKind kind, BigDecimal score, String grade) {}
    public record PersonalReportResponse(UUID employeeId, List<PersonalHistoryPoint> history) {}
}

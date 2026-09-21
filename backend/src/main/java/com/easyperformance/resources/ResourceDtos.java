package com.easyperformance.resources;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Typed HTTP contracts for evaluation catalogs, department goals, work evidence, and interviews. */
public final class ResourceDtos {
    private ResourceDtos() {}

    public enum CatalogKind { PERFORMANCE, COMPETENCY }
    public enum CatalogAssignmentType { JOB, DEPARTMENT }
    public enum TaskStatus { PLANNED, IN_PROGRESS, COMPLETED, DISCARDED }
    public enum ProgressMode { CHECKLIST, ACTUAL }
    public enum StakeholderRole { OWNER, MANAGER, COLLABORATOR }
    public enum InterviewView { AUTHORED, MINE, BY_EMPLOYEE, REFERENCED }
    public enum InterviewAuditAction { CREATED, UPDATED, VISIBILITY_CHANGED }

    public record AchievementLevelInput(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 100) String label,
        @DecimalMin("0") BigDecimal minValue,
        @DecimalMin("0") BigDecimal maxValue,
        @Size(max = 1000) String description,
        @Min(0) int displayOrder
    ) {}

    public record CatalogAssignmentInput(
        @NotNull CatalogAssignmentType type,
        @NotBlank @Size(max = 120) String reference
    ) {}

    public record CatalogUpsertRequest(
        @NotNull CatalogKind kind,
        @NotBlank @Size(max = 100) String category,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 4000) String definition,
        boolean active,
        @Min(0) int displayOrder,
        @Valid List<AchievementLevelInput> achievementLevels,
        @Valid List<CatalogAssignmentInput> assignments
    ) {}

    public record CatalogCopyRequest(@NotBlank @Size(max = 200) String name) {}
    public record ActiveUpdateRequest(boolean active) {}

    public record AchievementLevelResponse(UUID id, String code, String label, BigDecimal minValue,
                                            BigDecimal maxValue, String description, int displayOrder) {}
    public record CatalogAssignmentResponse(UUID id, CatalogAssignmentType type, String reference) {}
    public record LookupOptionResponse(String value, String label, String description) {}
    public record EmployeeOptionResponse(UUID id, String employeeNo, String name, UUID departmentId) {}
    public record CatalogResponse(UUID id, CatalogKind kind, String category, String name, String definition,
                                  boolean active, int displayOrder, UUID copiedFromId,
                                  List<AchievementLevelResponse> achievementLevels,
                                  List<CatalogAssignmentResponse> assignments, long rowVersion,
                                  Instant createdAt, Instant updatedAt) {}

    public record DepartmentGoalUpsertRequest(
        @NotNull @Min(2000) @Max(2200) Integer year,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotNull UUID departmentId,
        UUID catalogId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String definition,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal weight,
        @NotBlank @Size(max = 40) String targetLevel,
        @NotBlank @Size(max = 40) String unit
    ) {}

    public record DepartmentGoalCopyRequest(@NotNull @Min(2000) @Max(2200) Integer year,
                                            @NotNull LocalDate periodStart,
                                            @NotNull LocalDate periodEnd,
                                            @NotNull UUID departmentId) {}
    public record DepartmentGoalTransferRequest(@NotNull UUID targetDepartmentId,
                                                @NotBlank @Size(max = 1000) String reason) {}
    public record DepartmentGoalActualRequest(@NotNull BigDecimal actualValue,
                                              @NotNull @DecimalMin("0") BigDecimal achievementRate,
                                              @Size(max = 2000) String note) {}

    public record DepartmentGoalResponse(UUID id, int year, LocalDate periodStart, LocalDate periodEnd,
                                         UUID departmentId, UUID catalogId, String title, String definition,
                                         BigDecimal weight, String targetLevel, String unit,
                                         BigDecimal actualValue, BigDecimal achievementRate, String actualNote,
                                         UUID copiedFromId, UUID transferredFromId, long rowVersion,
                                         Instant createdAt, Instant updatedAt) {}

    public record StakeholderInput(@NotNull UUID employeeId, @NotNull StakeholderRole role) {}
    public record TaskCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @Size(max = 4000) String description,
        @NotNull ProgressMode progressMode,
        UUID departmentGoalId,
        @Valid List<StakeholderInput> stakeholders
    ) {}
    public record TaskUpdateRequest(@NotBlank @Size(max = 200) String title,
                                    @NotNull LocalDate periodStart,
                                    @NotNull LocalDate periodEnd,
                                    @Size(max = 4000) String description,
                                    UUID departmentGoalId,
                                    @Valid List<StakeholderInput> stakeholders) {}
    public record TaskStatusRequest(@NotNull TaskStatus status, @NotBlank @Size(max = 1000) String reason) {}
    public record TaskReopenRequest(@NotBlank @Size(max = 1000) String reason) {}
    public record ChecklistCreateRequest(@NotBlank @Size(max = 500) String text, @Min(0) int displayOrder) {}
    public record ChecklistCompletionRequest(boolean completed) {}
    public record TaskProgressRequest(@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent,
                                      @NotBlank @Size(max = 2000) String note) {}
    public record TaskActivityRequest(@NotBlank @Size(max = 4000) String message) {}
    public record LabelCreateRequest(@NotBlank @Size(max = 80) String name) {}
    public record LabelApplyRequest(@NotNull UUID labelId) {}
    public record TaskFeedbackRequest(@NotNull UUID toEmployeeId, @Min(1) @Max(5) int rating,
                                      @NotBlank @Size(max = 4000) String message) {}

    public record StakeholderResponse(UUID employeeId, StakeholderRole role) {}
    public record ChecklistResponse(UUID id, String text, boolean completed, int displayOrder,
                                    Instant completedAt, UUID completedBy) {}
    public record ActivityResponse(UUID id, UUID authorEmployeeId, String type, String message,
                                   BigDecimal progressPercent, TaskStatus fromStatus, TaskStatus toStatus,
                                   Instant createdAt) {}
    public record LabelResponse(UUID id, String name) {}
    public record TaskFeedbackResponse(UUID id, UUID fromEmployeeId, UUID toEmployeeId, int rating,
                                       String message, Instant createdAt) {}
    public record AttachmentResponse(UUID id, String filename, String contentType, long size, UUID uploadedBy,
                                     Instant createdAt) {}
    public record TaskSummaryResponse(UUID id, String title, LocalDate periodStart, LocalDate periodEnd,
                                      TaskStatus status, ProgressMode progressMode, BigDecimal progressPercent,
                                      UUID departmentGoalId, List<StakeholderResponse> stakeholders,
                                      List<LabelResponse> labels, long rowVersion, Instant updatedAt) {}
    public record TaskDetailResponse(TaskSummaryResponse task, String description,
                                     List<ChecklistResponse> checklist,
                                     List<ActivityResponse> activities,
                                     List<TaskFeedbackResponse> feedback,
                                     List<AttachmentResponse> attachments) {}

    public record InterviewCreateRequest(@NotNull UUID subjectEmployeeId,
                                         @NotNull Instant occurredAt,
                                         @NotBlank @Size(max = 4000) String summary,
                                         @Size(max = 4000) String keyIssues,
                                         @Size(max = 4000) String requests,
                                         @Size(max = 4000) String followUp,
                                         boolean subjectVisible,
                                         boolean referencesVisible,
                                         List<@NotNull UUID> referenceEmployeeIds) {}
    public record InterviewUpdateRequest(@NotNull Instant occurredAt,
                                         @NotBlank @Size(max = 4000) String summary,
                                         @Size(max = 4000) String keyIssues,
                                         @Size(max = 4000) String requests,
                                         @Size(max = 4000) String followUp,
                                         List<@NotNull UUID> referenceEmployeeIds) {}
    public record InterviewVisibilityRequest(boolean subjectVisible, boolean referencesVisible) {}
    public record InterviewAuditResponse(UUID id, InterviewAuditAction action, UUID actorEmployeeId,
                                         boolean subjectVisible, boolean referencesVisible, Instant createdAt) {}
    public record InterviewResponse(UUID id, UUID authorEmployeeId, UUID subjectEmployeeId,
                                    Instant occurredAt, String summary, String keyIssues, String requests, String followUp,
                                    boolean subjectVisible,
                                    boolean referencesVisible, List<UUID> referenceEmployeeIds,
                                    long rowVersion, Instant createdAt, Instant updatedAt,
                                    List<InterviewAuditResponse> auditTrail) {}
}

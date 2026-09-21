package com.easyperformance.program;

import com.easyperformance.program.ProgramTypes.NotificationStatus;
import com.easyperformance.program.ProgramTypes.ProgramStage;
import com.easyperformance.program.ProgramTypes.ReviewerRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProgramReminderDtos {
    private ProgramReminderDtos() {}

    public static final String POLICY_VERSION = "RESPONSIBLE_REMINDER_V1";
    public static final String HASH_PATTERN = "[0-9a-f]{64}";
    public static final String LOCALE_PATTERN = "ko|en|ja|zh-CN|vi";

    public enum ReminderAction {
        GOAL_AUTHOR, GOAL_APPROVAL, GOAL_SELF_REPORT, INTERMEDIATE_CHECK,
        SELF_REVIEW, REVIEW, CALIBRATION, FEEDBACK_DELIVERY,
        FEEDBACK_ACKNOWLEDGEMENT, FEEDBACK_RESOLUTION
    }
    public enum ReminderOwnerKind { SELF, ASSIGNMENT, UNRESOLVED }
    public enum ReminderCandidateStatus { READY, ALREADY_QUEUED, BLOCKED }
    public enum ReminderExclusionStatus { COMPLETED, NOT_APPLICABLE }
    public enum ReminderDueState { NO_DUE_DATE, BEFORE_DUE, DUE_TODAY, OVERDUE }
    public enum ReminderQueueDisposition { QUEUED, DUPLICATE_SUPPRESSED }

    public record ReminderPreviewRequest(
        @NotEmpty @Size(max=100) List<@NotNull UUID> participantIds,
        @Size(max=6) List<@NotNull ProgramStage> stages,
        @NotBlank @Pattern(regexp=LOCALE_PATTERN) String locale) {}

    public record ReminderQueueRequest(
        @NotEmpty @Size(max=100) List<@NotNull UUID> participantIds,
        @Size(max=6) List<@NotNull ProgramStage> stages,
        @NotBlank @Pattern(regexp=LOCALE_PATTERN) String locale,
        @NotNull LocalDate reminderOn,
        @NotBlank @Pattern(regexp=HASH_PATTERN) String previewHash,
        @NotEmpty @Size(max=100) List<@NotBlank @Pattern(regexp=HASH_PATTERN) String> candidateKeys,
        @NotNull UUID idempotencyKey,
        @NotBlank @Size(max=500) String reason) {}

    public record ReminderPreviewResponse(
        UUID programId, LocalDate reminderOn, String zoneId, String policyVersion,
        String previewHash, List<ReminderCandidate> candidates,
        List<ReminderExclusion> exclusions, ReminderSummary summary) {}

    public record ReminderCandidate(
        String candidateKey, UUID participantId, UUID participantEmployeeId, String participantName,
        ProgramStage stage, int currentRound, ReminderAction action, ReminderOwnerKind ownerKind,
        ReviewerRole ownerRole, UUID reviewerAssignmentId, UUID recipientEmployeeId, String recipientName,
        LocalDate startsOn, LocalDate dueDate, ReminderDueState dueState,
        ReminderCandidateStatus status, String reasonCode, UUID existingNotificationId,
        String subject, String body, String deepLink, String sourceFingerprint) {}

    public record ReminderExclusion(
        UUID participantId, ProgramStage currentStage, int currentRound,
        ReminderExclusionStatus status, String reasonCode) {}

    public record ReminderSummary(
        int requestedParticipants, int ready, int alreadyQueued, int blocked,
        int completed, int notApplicable) {}

    public record ReminderQueueResponse(
        UUID programId, LocalDate reminderOn, String policyVersion, String previewHash,
        UUID idempotencyKey, int queued, int duplicateSuppressed, List<ReminderQueueRow> rows) {}

    public record ReminderQueueRow(
        String candidateKey, UUID notificationId, UUID participantId, UUID recipientEmployeeId,
        ProgramStage stage, ReminderAction action, ReminderQueueDisposition disposition,
        NotificationStatus notificationStatus, Instant registeredAt) {}

    public record ReminderHistoryRow(
        UUID notificationId, UUID participantId, UUID recipientEmployeeId, ProgramStage stage,
        int currentRound, ReminderAction action, LocalDate reminderOn, String policyVersion,
        NotificationStatus notificationStatus, String subject, String body, String deepLink,
        Instant registeredAt, Instant readAt, UUID idempotencyKey) {}

    public record ReminderHistoryResponse(Page<ReminderHistoryRow> page) {}
}

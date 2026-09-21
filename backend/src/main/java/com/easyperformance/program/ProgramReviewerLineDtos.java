package com.easyperformance.program;

import com.easyperformance.program.ProgramTypes.ReviewerLineApplyStatus;
import com.easyperformance.program.ProgramTypes.ReviewerLinePreviewStatus;
import com.easyperformance.program.ProgramTypes.ReviewerRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Stable request/response contract for explicit reviewer-line preview and apply. */
public final class ProgramReviewerLineDtos {
    private ProgramReviewerLineDtos() {}

    public record ReviewerLinePreviewRequest(
        @NotEmpty @Size(max = 100) List<UUID> participantIds,
        @NotEmpty Set<ReviewerRole> roles
    ) {}

    public record ReviewerLinePreviewResponse(
        UUID programId,
        LocalDate asOfDate,
        int definitionRevision,
        String previewHash,
        Instant generatedAt,
        ReviewerLinePreviewSummary summary,
        List<ReviewerLinePreviewRow> rows
    ) {}

    public record ReviewerLinePreviewSummary(
        int total,
        int ready,
        int sourceMissing,
        int blocked,
        int skippedExisting
    ) {}

    public record ReviewerLinePreviewRow(
        UUID participantId,
        UUID participantEmployeeId,
        UUID participantAssignmentId,
        long participantRowVersion,
        UUID sourceAssignmentId,
        Long sourceVersion,
        String sourceSystem,
        LocalDate sourceEffectiveFrom,
        LocalDate sourceEffectiveTo,
        Boolean sourceDeleted,
        UUID proposedReviewerEmployeeId,
        String proposedReviewerName,
        int currentReviewerCount,
        List<ReviewerLineProposal> proposals,
        ReviewerLinePreviewStatus status,
        List<ReviewerLineIssue> issues
    ) {}

    public record ReviewerLineProposal(ReviewerRole role, int round, BigDecimal weightPercent) {}
    public record ReviewerLineIssue(String code, String message) {}

    public record ReviewerLineApplyRequest(
        @NotBlank String previewHash,
        @NotEmpty @Size(max = 100) List<UUID> participantIds,
        @NotEmpty Set<ReviewerRole> roles,
        @NotBlank @Size(max = 500) String reason
    ) {}

    public record ReviewerLineApplyResponse(
        UUID programId,
        LocalDate asOfDate,
        UUID automationRunId,
        int applied,
        int skipped,
        List<ReviewerLineApplyRow> rows
    ) {}

    public record ReviewerLineApplyRow(
        UUID participantId,
        ReviewerLineApplyStatus status,
        List<UUID> reviewerAssignmentIds,
        List<ReviewerLineIssue> issues
    ) {}
}

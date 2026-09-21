package com.easyperformance.program;

import com.easyperformance.domain.kpi.entity.KpiActualSource;
import com.easyperformance.domain.kpi.entity.KpiNodeSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ProgramKpiDtos {
    private ProgramKpiDtos() {}

    public enum KpiLinkStatus { READY, SOURCE_MISSING, BLOCKED }

    public record KpiCandidateResponse(
        UUID kpiAssignmentId, UUID kpiNodeId, String nodeLabel, UUID treeId, String treeName,
        UUID cycleId, String cycleName, UUID employeeId, BigDecimal effectiveWeight,
        BigDecimal effectiveTarget, String unit, KpiNodeSource nodeSource, UUID latestActualId,
        LocalDate latestActualAsOfDate, BigDecimal latestActualValue, KpiActualSource latestActualSource,
        BigDecimal achievementRate, BigDecimal autoScore, KpiLinkStatus availability, String reasonCode
    ) {}

    public record KpiLinkPreviewRequest(
        @NotNull UUID cycleId,
        @NotNull LocalDate actualCutoffDate,
        @NotNull UUID kpiAssignmentId
    ) {}

    public record KpiLinkApplyRequest(
        @NotNull UUID cycleId,
        @NotNull LocalDate actualCutoffDate,
        @NotNull UUID kpiAssignmentId,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String previewHash,
        @NotBlank @Size(max = 500) String reason
    ) {}

    public record KpiLinkPreviewResponse(
        UUID programId, UUID participantId, UUID cycleId, LocalDate programAsOfDate,
        LocalDate actualCutoffDate, int programDefinitionRevision, long programRowVersion,
        long participantRowVersion, String previewHash, Instant capturedAt, KpiLinkPreviewRow row
    ) {}

    public record KpiLinkPreviewRow(
        UUID goalId, int goalRevision, long goalRowVersion, String goalTitle,
        BigDecimal goalWeightPercent, UUID kpiAssignmentId, Instant kpiAssignmentUpdatedAt,
        UUID kpiNodeId, Instant kpiNodeUpdatedAt, String nodeLabel, UUID treeId, String treeName,
        UUID cycleId, UUID employeeId, BigDecimal effectiveWeight, BigDecimal effectiveTarget,
        String unit, KpiNodeSource nodeSource, UUID actualId, LocalDate actualAsOfDate,
        BigDecimal actualValue, KpiActualSource actualSource, Instant actualCreatedAt,
        UUID actualSupersedesId, BigDecimal achievementRate, BigDecimal autoScore,
        String formulaVersion, String formula, KpiLinkStatus status, String reasonCode
    ) {}

    public record KpiSourceSnapshot(
        int programDefinitionRevision, long programRowVersion, long participantRowVersion,
        int goalRevision, long goalRowVersion, Instant kpiAssignmentUpdatedAt,
        Instant kpiNodeUpdatedAt, UUID actualId, Instant actualCreatedAt
    ) {}

    public record KpiLinkApplyResponse(
        UUID evidenceId, UUID programId, UUID participantId, UUID goalId, UUID cycleId,
        int revision, UUID supersedesEvidenceId, LocalDate actualCutoffDate, String previewHash,
        Instant appliedAt, UUID appliedByEmployeeId, boolean active, KpiLinkPreviewRow evidence,
        KpiSourceSnapshot sourceSnapshot, String reason
    ) {}
}

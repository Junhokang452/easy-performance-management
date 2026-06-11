/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.calibration.dto;

import com.easyperformance.domain.calibration.entity.CalibrationSession;
import com.easyperformance.domain.calibration.entity.CalibrationStatus;
import com.easyperformance.domain.evaluationpolicy.entity.DistributionMode;
import com.easyperformance.domain.evaluationpolicy.entity.RatingScale;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 캘리브레이션 + 분포 도메인 DTO 모음 — P0-S4 (p0_s4_contract.md §6). ADR-026 명명 정합.
 *
 * <p>Response shape 는 계약 §6 그대로 (FE 에이전트 병렬 구현 — 이탈 금지). 파생 값 (effectiveGrade /
 * proposed quota / 분포 카운트) 은 service (BE 유일 계산자) 가 계산해 주입. 분포 객체 키 단위:
 * {@code targetDistribution} = 비율 (0~1 number) / {@code current·resulting·actual·policy} = 건수 (int).
 */
public final class CalibrationDtos {

    private CalibrationDtos() {
    }

    // ─────────────────────────────────────────────────────────────────────
    // CalibrationSession 요청
    // ─────────────────────────────────────────────────────────────────────

    /** POST /cycles/{cycleId}/calibration-sessions — 세션 생성 (status=PLANNED). */
    public record CalibrationSessionCreateRequest(
        UUID ownerOrgUnitId,
        Instant scheduledAt,
        List<UUID> participantIds
    ) {}

    /** PATCH /calibration-sessions/{sessionId} — PLANNED 한정 부분 수정. */
    public record CalibrationSessionUpdateRequest(
        UUID ownerOrgUnitId,
        Instant scheduledAt,
        List<UUID> participantIds
    ) {}

    /** POST /calibration-sessions/{sessionId}/transition — §3 매트릭스 상태 전이. */
    public record CalibrationTransitionRequest(
        @NotNull CalibrationStatus targetStatus,
        UUID actorEmployeeId
    ) {}

    /** POST /calibration-sessions/{sessionId}/adjustments — 개별 등급 조정. */
    public record CalibrationAdjustmentRequest(
        @NotNull UUID reviewId,
        @NotNull String toGrade,
        String reason,
        UUID actorEmployeeId
    ) {}

    /** POST /calibration-sessions/{sessionId}/confirm — 확정 (+ 일괄 finalize 옵션). */
    public record CalibrationConfirmRequest(
        UUID actorEmployeeId,
        Boolean finalizeReviews
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // CalibrationSession 응답
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 캘리브레이션 세션 응답 (§6 shape). {@code participantIds}/{@code adjustmentLog} 는 service 가 jsonb
     * String → 파싱 배열로 변환해 주입.
     */
    public record CalibrationSessionResponse(
        UUID id,
        UUID cycleId,
        UUID ownerOrgUnitId,
        CalibrationStatus status,
        Instant scheduledAt,
        List<UUID> participantIds,
        List<AdjustmentEntry> adjustmentLog,
        Instant confirmedAt,
        UUID confirmedBy,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static CalibrationSessionResponse from(CalibrationSession e,
                                                      List<UUID> participantIds,
                                                      List<AdjustmentEntry> adjustmentLog) {
            return new CalibrationSessionResponse(
                e.getId(),
                e.getCycleId(),
                e.getOwnerOrgUnitId(),
                e.getStatus(),
                e.getScheduledAt(),
                participantIds,
                adjustmentLog,
                e.getConfirmedAt(),
                e.getConfirmedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt()
            );
        }
    }

    /**
     * 조정 이력 entry (adjustment_log jsonb 직렬화/역직렬화 대상). camelCase 필드 — Jackson 기본 record
     * 매핑 정합.
     */
    public record AdjustmentEntry(
        Instant at,
        UUID actorEmployeeId,
        UUID reviewId,
        UUID employeeId,
        String fromGrade,
        String toGrade,
        String reason
    ) {}

    /** confirm 응답 — 세션 + 일괄 finalize 카운트. */
    public record CalibrationConfirmResponse(
        CalibrationSessionResponse session,
        int finalizedCount,
        int skippedCount
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // 분포 요청
    // ─────────────────────────────────────────────────────────────────────

    /** POST /cycles/{cycleId}/distribution/simulate — 무저장 시뮬레이션. */
    public record DistributionSimulateRequest(
        Map<String, BigDecimal> targetDistribution
    ) {}

    /** POST /cycles/{cycleId}/distribution/apply — 강제 배분 적용 (review 등급 일괄 UPDATE + upsert). */
    public record DistributionApplyRequest(
        Map<String, BigDecimal> targetDistribution,
        UUID actorEmployeeId
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // 분포 응답
    // ─────────────────────────────────────────────────────────────────────

    /**
     * GET /cycles/{cycleId}/distribution 응답 (§6 shape). {@code targetDistribution} = 비율 (0~1) /
     * {@code currentDistribution} = 건수 (S,A,B,C,D,UNRATED). {@code simulationLog} 는 service 가 파싱 주입.
     */
    public record DistributionResponse(
        UUID cycleId,
        DistributionMode distributionMode,
        RatingScale ratingScale,
        Map<String, BigDecimal> targetDistribution,
        Map<String, Integer> currentDistribution,
        int totalReviews,
        int calibrationReadyCount,
        boolean forcedApplied,
        Instant appliedAt,
        UUID appliedBy,
        List<SimulationEntry> simulationLog
    ) {}

    /**
     * 시뮬레이션/적용 이력 entry (simulation_log jsonb 직렬화/역직렬화 대상). camelCase 필드.
     * {@code targetDistribution} = 비율 / {@code resultingDistribution} = 건수.
     */
    public record SimulationEntry(
        Instant at,
        UUID actorEmployeeId,
        Map<String, BigDecimal> targetDistribution,
        int appliedCount,
        int skippedCount,
        Map<String, Integer> resultingDistribution
    ) {}

    /** simulate 응답 — proposed 목록 + 결과 분포 (건수) + 사용된 target (비율). */
    public record DistributionSimulationResponse(
        List<ProposedGradeRow> proposed,
        Map<String, Integer> resultingDistribution,
        Map<String, BigDecimal> targetDistribution
    ) {}

    /** 강제 배분 제안 행 — {@code currentGrade} = effectiveGrade / {@code proposedGrade} = 배분 결과. */
    public record ProposedGradeRow(
        UUID reviewId,
        UUID employeeId,
        BigDecimal kpiScore,
        String currentGrade,
        String proposedGrade
    ) {}

    /** apply 응답 — 적용/스킵 카운트 + 결과 분포 (건수). */
    public record DistributionApplyResponse(
        int appliedCount,
        int skippedCount,
        Map<String, Integer> resultingDistribution
    ) {}
}

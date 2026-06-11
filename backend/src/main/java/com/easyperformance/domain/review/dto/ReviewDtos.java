/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.review.dto;

import com.easyperformance.domain.review.entity.PerformanceReview;
import com.easyperformance.domain.review.entity.ReviewStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 성과 평가 도메인 DTO 모음 — P0-S3 (p0_s3_contract.md §6). ADR-026 명명 정합
 * ({@code {Entity}CreateRequest / UpdateRequest / Response}).
 *
 * <p>Response shape 는 계약 §6 그대로 (FE 에이전트 병렬 구현 — 이탈 금지). 파생 값 (kpiScoreDetail 파싱
 * 배열 / kpi-items live·동결 분기 / autoScore·itemScore 산출) 은 service 가 계산해 정적 팩토리에 주입.
 */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    // ─────────────────────────────────────────────────────────────────────
    // 요청
    // ─────────────────────────────────────────────────────────────────────

    /** POST /cycles/{cycleId}/reviews — 단건 생성 (status=DRAFT). */
    public record ReviewCreateRequest(
        @NotNull UUID employeeId
    ) {}

    /** POST /cycles/{cycleId}/reviews/bulk — 일괄 생성 (기존 (cycle×employee) skip). */
    public record ReviewBulkCreateRequest(
        @NotNull List<UUID> employeeIds
    ) {}

    /**
     * PATCH /reviews/{reviewId} — 섹션 임시저장. 섹션 가드 (§6):
     * SELF_PENDING = selfComment 만 / MANAGER_PENDING = managerComment + itemScores 만.
     */
    public record ReviewUpdateRequest(
        String selfComment,
        String managerComment,
        @Valid List<ReviewItemScoreInput> itemScores
    ) {}

    /** POST /reviews/{reviewId}/submit-self — 자기평가 제출 (SELF_PENDING → SELF_SUBMITTED). */
    public record ReviewSubmitSelfRequest(
        String selfComment
    ) {}

    /**
     * POST /reviews/{reviewId}/submit-manager — 매니저 평가 제출
     * (MANAGER_PENDING → MANAGER_SUBMITTED + §5 스냅샷·kpiScore 산출).
     */
    public record ReviewSubmitManagerRequest(
        String managerComment,
        @NotNull @Valid List<ReviewItemScoreInput> itemScores
    ) {}

    /** POST /reviews/{reviewId}/transition — §3 매트릭스 상태 전이. */
    public record ReviewTransitionRequest(
        @NotNull ReviewStatus targetStatus,
        UUID actorEmployeeId
    ) {}

    /** per-item managerScore 입력 (PATCH / submit-manager). managerScore null 이면 autoScore 폴백. */
    public record ReviewItemScoreInput(
        @NotNull UUID assignmentId,
        BigDecimal managerScore
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // 응답
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 성과 평가 응답 (§6 shape). {@code kpiScoreDetail} 은 저장 스냅샷 (submit-manager 전 null) —
     * service 가 jsonb String → 파싱 배열로 변환해 주입.
     */
    public record ReviewResponse(
        UUID id,
        UUID cycleId,
        UUID employeeId,
        ReviewStatus status,
        BigDecimal kpiScore,
        BigDecimal mboScore,
        BigDecimal competencyScore,
        BigDecimal mraScore,
        BigDecimal finalScore,
        String finalGrade,
        String selfComment,
        String managerComment,
        List<ReviewKpiItemResponse> kpiScoreDetail,
        Instant finalizedAt,
        UUID finalizedBy,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static ReviewResponse from(PerformanceReview e, List<ReviewKpiItemResponse> kpiScoreDetail) {
            return new ReviewResponse(
                e.getId(),
                e.getCycleId(),
                e.getEmployeeId(),
                e.getStatus(),
                e.getKpiScore(),
                e.getMboScore(),
                e.getCompetencyScore(),
                e.getMraScore(),
                e.getFinalScore(),
                e.getFinalGrade(),
                e.getSelfComment(),
                e.getManagerComment(),
                kpiScoreDetail,
                e.getFinalizedAt(),
                e.getFinalizedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt()
            );
        }
    }

    /**
     * KPI 항목 응답 (§6 shape) — kpi-items 폼 렌더 + kpiScoreDetail 스냅샷 공용. weight/target 는
     * effective 값. 숫자는 number|null. service 가 live 계산 (kpi-items) 또는 동결 스냅샷 파싱
     * (kpiScoreDetail) 로 주입.
     *
     * <p>jsonb 직렬화/역직렬화 대상이므로 모든 필드는 Jackson 기본 record 매핑 (camelCase) 정합.
     */
    public record ReviewKpiItemResponse(
        UUID assignmentId,
        String nodeLabel,
        String treeName,
        BigDecimal weight,
        BigDecimal target,
        String unit,
        BigDecimal latestActualValue,
        BigDecimal achievementRate,
        BigDecimal autoScore,
        BigDecimal managerScore,
        BigDecimal itemScore
    ) {}

    /** POST /cycles/{cycleId}/reviews/bulk 응답. */
    public record ReviewBulkCreateResponse(
        int createdCount,
        int skippedCount,
        List<ReviewResponse> created
    ) {}
}

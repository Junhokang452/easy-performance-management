/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.report.dto;

import com.easyperformance.domain.report.entity.PerformanceReport;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewKpiItemResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 성과 리포트 도메인 DTO 모음 — P0-S5 (p0_s5_contract.md §5/§6). ADR-026 명명 정합.
 *
 * <p>Response shape 는 계약 §6 그대로 (FE 에이전트 병렬 구현 — 이탈 금지). {@code content} (§5 동결 스냅샷)
 * 는 service 가 발행 시점 생성 → jsonb 저장 → Response 에서 파싱 객체로 노출. {@code superseded} 는
 * computed (다른 행이 나를 supersedes_id 로 참조).
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    // ─────────────────────────────────────────────────────────────────────
    // 요청
    // ─────────────────────────────────────────────────────────────────────

    /**
     * POST /cycles/{cycleId}/reports/publish — cycle 일괄 발행 (FINALIZED review 중 active report
     * 미존재분만 생성). {@code actorEmployeeId} 는 publishedBy 기록 (P0 바디 파라미터, P1 JWT principal).
     */
    public record ReportPublishRequest(
        UUID actorEmployeeId
    ) {}

    /** POST /reports/{reportId}/acknowledge — 확인 (acknowledged=true 단방향, 멱등). */
    public record ReportAcknowledgeRequest(
        UUID actorEmployeeId
    ) {}

    /**
     * POST /reports/{reportId}/supersede — 개별 재발행 (신규 row, supersedesId=원본, content 재동결).
     * {@code actorEmployeeId} 는 신규 row 의 publishedBy 기록.
     */
    public record ReportSupersedeRequest(
        UUID actorEmployeeId
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // content 동결 스냅샷 (§5 shape) — 발행 시점 service 가 생성 후 불변
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 리포트 본문 동결 스냅샷 (§5). 발행 시점 review 확정값 + 발행 시점 cycle 의 FINALIZED 분포 비율.
     * jsonb 직렬화/역직렬화 대상이므로 모든 필드 Jackson 기본 record 매핑 (camelCase) 정합.
     *
     * <p>{@code kpiItems} = review.kpiScoreDetail 전체 사본 (이미 동결 스냅샷). {@code distribution} =
     * 발행 시점 FINALIZED review finalGrade 분포 "비율" (0~1 round 4, 건수 비노출 E9 — finalGrade null 행은
     * 분모 포함·버킷 제외). {@code mboScore}/{@code competencyScore}/{@code mraScore}/{@code nextAction} 은
     * P0 null 박제.
     */
    public record ReportContent(
        String finalGrade,
        BigDecimal finalScore,
        BigDecimal kpiScore,
        BigDecimal mboScore,
        BigDecimal competencyScore,
        BigDecimal mraScore,
        String managerComment,
        List<ReviewKpiItemResponse> kpiItems,
        Map<String, BigDecimal> distribution,
        Object nextAction
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // 응답
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 성과 리포트 응답 (§6 shape). {@code content} 는 저장 jsonb → 파싱 객체 (P0-S3 kpiScoreDetail 노출
     * 패턴). {@code superseded} 는 service 가 active 판정해 주입.
     */
    public record ReportResponse(
        UUID id,
        UUID cycleId,
        UUID reviewId,
        UUID employeeId,
        Instant publishedAt,
        UUID publishedBy,
        ReportContent content,
        Instant viewedAt,
        boolean acknowledged,
        Instant acknowledgedAt,
        UUID supersedesId,
        boolean superseded,
        Instant createdAt
    ) {
        public static ReportResponse from(PerformanceReport e, ReportContent content, boolean superseded) {
            return new ReportResponse(
                e.getId(),
                e.getCycleId(),
                e.getReviewId(),
                e.getEmployeeId(),
                e.getPublishedAt(),
                e.getPublishedBy(),
                content,
                e.getViewedAt(),
                e.isAcknowledged(),
                e.getAcknowledgedAt(),
                e.getSupersedesId(),
                superseded,
                e.getCreatedAt()
            );
        }
    }

    /** POST /cycles/{cycleId}/reports/publish 응답 — 발행/스킵 카운트 + 발행된 리포트 목록. */
    public record ReportPublishResponse(
        int publishedCount,
        int skippedCount,
        List<ReportResponse> published
    ) {}
}

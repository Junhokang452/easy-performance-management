/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.report.service;

import com.easyperformance.common.TenantSupport;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.report.dto.ReportDtos.ReportContent;
import com.easyperformance.domain.report.dto.ReportDtos.ReportPublishResponse;
import com.easyperformance.domain.report.dto.ReportDtos.ReportResponse;
import com.easyperformance.domain.report.entity.PerformanceReport;
import com.easyperformance.domain.report.repository.PerformanceReportRepository;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewKpiItemResponse;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewResponse;
import com.easyperformance.domain.review.entity.PerformanceReview;
import com.easyperformance.domain.review.entity.ReviewStatus;
import com.easyperformance.domain.review.repository.PerformanceReviewRepository;
import com.easyperformance.domain.review.service.ReviewService;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 성과 리포트 도메인 Service — 비즈니스 SSOT (PerformanceReport). P0-S5 (p0_s5_contract.md §3/§5).
 *
 * <p>테넌트 격리: {@link TenantSupport#currentTenantId()} 위임 — 모든 쿼리에 tenant_id 필수
 * (easy-ware 규칙 #10).
 *
 * <p>핵심 책임:
 * <ul>
 *   <li><b>일괄 발행 (publish)</b> — cycle.status==FINALIZED 한정 (REPORT_CYCLE_NOT_FINALIZED). cycle 의
 *       FINALIZED review 중 active report 미존재분만 신규 발행 (published/skipped 카운트). content 는 §5
 *       동결 스냅샷.</li>
 *   <li><b>개별 재발행 (supersede)</b> — cycle.status==FINALIZED 한정 + 원본이 active 행 (REPORT_NOT_ACTIVE).
 *       신규 row (supersedesId=원본, content 재동결 — review 현재값 + 최신 분포).</li>
 *   <li><b>view</b> — active 행 한정. viewed_at 최초 1회 set (이미 있으면 no-op 멱등).</li>
 *   <li><b>acknowledge</b> — active 행 한정. acknowledged=true 단방향 + acknowledged_at (멱등).</li>
 *   <li><b>content 동결</b> — review 확정값 (finalGrade/finalScore/kpiScore/managerComment + kpiScoreDetail
 *       전체 사본) + 발행 시점 FINALIZED finalGrade 분포 비율 (0~1 round 4, 건수 비노출 E9).</li>
 * </ul>
 *
 * <p>review 확정값 노출은 {@link ReviewService#getReview} public 재사용 (kpiScoreDetail 파싱 + managerComment
 * + finalGrade/score — cross-package private 0). 분포 버킷 카운트는 본 서비스 내 단순 구현 (CalibrationService
 * effectiveGrade 분포와 별개 — 여기는 FINALIZED·finalGrade 만, 중복 아님). JSONB content 는 {@link ReportJson}
 * 위임 (ObjectMapper {@code USE_BIG_DECIMAL_FOR_FLOATS} — 비율 정밀도 보존).
 */
@Service
public class ReportService {

    /** 분포 비율 round scale (소수 4자리, §5). */
    private static final int RATIO_SCALE = 4;

    /** 정준 등급 순서 (분포 버킷). */
    private static final List<String> GRADE_LIST = List.of("S", "A", "B", "C", "D");

    private final PerformanceReportRepository reportRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final EvaluationCycleRepository cycleRepository;
    private final ReviewService reviewService;
    private final ReportJson json;
    private final Clock clock;

    public ReportService(PerformanceReportRepository reportRepository,
                         PerformanceReviewRepository reviewRepository,
                         EvaluationCycleRepository cycleRepository,
                         ReviewService reviewService) {
        this(reportRepository, reviewRepository, cycleRepository, reviewService, Clock.systemUTC());
    }

    /** 테스트 친화 — Clock 주입 (jobstructure/security/ReviewService/CalibrationService 패턴 정합). */
    public ReportService(PerformanceReportRepository reportRepository,
                         PerformanceReviewRepository reviewRepository,
                         EvaluationCycleRepository cycleRepository,
                         ReviewService reviewService,
                         Clock clock) {
        this.reportRepository = reportRepository;
        this.reviewRepository = reviewRepository;
        this.cycleRepository = cycleRepository;
        this.reviewService = reviewService;
        this.json = new ReportJson();
        this.clock = clock;
    }

    // ═════════════════════════════════════════════════════════════════════
    // 조회
    // ═════════════════════════════════════════════════════════════════════

    /** cycle 의 리포트 전부 (superseded 포함 — HR 이력 가시성, publishedAt DESC). employeeId 옵션 필터. */
    @Transactional(readOnly = true)
    public List<ReportResponse> listReports(UUID cycleId, UUID employeeId) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireCycle(cycleId, tenantId);
        List<PerformanceReport> reports = (employeeId == null)
            ? reportRepository.findAllByTenantIdAndCycleIdOrderByPublishedAtDesc(tenantId, cycleId)
            : reportRepository.findAllByTenantIdAndCycleIdAndEmployeeIdOrderByPublishedAtDesc(
                tenantId, cycleId, employeeId);
        return reports.stream().map(r -> toResponse(tenantId, r)).toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID reportId) {
        UUID tenantId = TenantSupport.currentTenantId();
        return toResponse(tenantId, requireReport(reportId, tenantId));
    }

    /**
     * 사원 본인 리포트 (GET /reports/my) — active 행만 (없으면 REPORT_NOT_FOUND 404). cycle × employee 의
     * 리포트 중 supersede 안 된 1개. (체인 선형성 보장 — active 는 최대 1개.)
     */
    @Transactional(readOnly = true)
    public ReportResponse getMyReport(UUID cycleId, UUID employeeId) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireCycle(cycleId, tenantId);
        List<PerformanceReport> reports = reportRepository
            .findAllByTenantIdAndCycleIdAndEmployeeId(tenantId, cycleId, employeeId);
        Set<UUID> supersededIds = supersededIds(reports);
        PerformanceReport active = reports.stream()
            .filter(r -> !supersededIds.contains(r.getId()))
            .findFirst()
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REPORT_NOT_FOUND,
                Map.of("entity", "PerformanceReport", "cycleId", cycleId, "employeeId", employeeId)));
        return toResponse(tenantId, active);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 발행 (publish) — 일괄
    // ═════════════════════════════════════════════════════════════════════

    /**
     * cycle 일괄 발행 — cycle.status==FINALIZED 한정. FINALIZED review 전수 조회 → active report 미존재분만
     * 신규 발행 (그 외 skip 카운트, 에러 아님). content 는 §5 동결 스냅샷 + 발행 시점 분포.
     */
    @Transactional
    public ReportPublishResponse publish(UUID cycleId, UUID actorEmployeeId) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireFinalizedCycle(cycleId, tenantId);

        // 발행 시점 분포 (FINALIZED finalGrade 비율) — 본 일괄 발행 동안 1회 산출 후 공통 동결.
        Map<String, BigDecimal> distribution = computeFinalizedDistribution(tenantId, cycleId);

        List<ReportResponse> created = new ArrayList<>();
        int skipped = 0;
        for (PerformanceReview review : reviewRepository
                .findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId)) {
            if (review.getStatus() != ReviewStatus.FINALIZED) {
                continue; // FINALIZED 만 대상.
            }
            if (hasActiveReport(tenantId, review.getId())) {
                skipped++;
                continue; // 이미 active report 존재 — skip.
            }
            PerformanceReport report = newReport(tenantId, review, actorEmployeeId, distribution, null);
            created.add(toResponse(tenantId, reportRepository.save(report)));
        }
        return new ReportPublishResponse(created.size(), skipped, created);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 재발행 (supersede) — 개별
    // ═════════════════════════════════════════════════════════════════════

    /**
     * 개별 재발행 — cycle.status==FINALIZED 한정 + 원본이 active 행 (superseded → REPORT_NOT_ACTIVE 409).
     * 신규 row (supersedesId=원본, content 재동결 — review 현재 확정값 + 최신 분포).
     */
    @Transactional
    public ReportResponse supersede(UUID reportId, UUID actorEmployeeId) {
        UUID tenantId = TenantSupport.currentTenantId();
        PerformanceReport original = requireReport(reportId, tenantId);
        requireFinalizedCycle(original.getCycleId(), tenantId);
        requireActive(tenantId, original);

        // content 재동결 — review 현재 확정값 + 최신 분포.
        Map<String, BigDecimal> distribution = computeFinalizedDistribution(tenantId, original.getCycleId());
        PerformanceReview review = reviewRepository.findByIdAndTenantId(original.getReviewId(), tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REVIEW_NOT_FOUND,
                Map.of("entity", "PerformanceReview", "id", original.getReviewId())));
        PerformanceReport next = newReport(tenantId, review, actorEmployeeId, distribution, reportId);
        return toResponse(tenantId, reportRepository.save(next));
    }

    // ═════════════════════════════════════════════════════════════════════
    // view / acknowledge (mutable 예외 2 필드)
    // ═════════════════════════════════════════════════════════════════════

    /** 열람 — active 행 한정. viewed_at 최초 1회 set (이미 있으면 no-op 멱등). */
    @Transactional
    public ReportResponse view(UUID reportId) {
        UUID tenantId = TenantSupport.currentTenantId();
        PerformanceReport report = requireReport(reportId, tenantId);
        requireActive(tenantId, report);
        if (report.getViewedAt() == null) {
            report.setViewedAt(Instant.now(clock));
            report = reportRepository.save(report);
        }
        return toResponse(tenantId, report);
    }

    /** 확인 — active 행 한정. acknowledged=true 단방향 + acknowledged_at (멱등 — 이미 true 면 no-op). */
    @Transactional
    public ReportResponse acknowledge(UUID reportId, UUID actorEmployeeId) {
        UUID tenantId = TenantSupport.currentTenantId();
        PerformanceReport report = requireReport(reportId, tenantId);
        requireActive(tenantId, report);
        if (!report.isAcknowledged()) {
            report.setAcknowledged(true);
            report.setAcknowledgedAt(Instant.now(clock));
            report = reportRepository.save(report);
        }
        return toResponse(tenantId, report);
    }

    // ═════════════════════════════════════════════════════════════════════
    // content 동결 스냅샷 (§5)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * §5 content 스냅샷 생성 — review 확정값 ({@link ReviewService#getReview} 재사용: finalGrade/finalScore/
     * kpiScore/managerComment + kpiScoreDetail 전체 사본) + 발행 시점 분포. mbo/competency/mra/nextAction = null.
     */
    private ReportContent buildContent(PerformanceReview review, Map<String, BigDecimal> distribution) {
        ReviewResponse r = reviewService.getReview(review.getId());
        List<ReviewKpiItemResponse> kpiItems = r.kpiScoreDetail();
        return new ReportContent(
            r.finalGrade(),
            r.finalScore(),
            r.kpiScore(),
            null,  // mboScore — P0 박제
            null,  // competencyScore — P0 박제
            null,  // mraScore — P0 박제
            r.managerComment(),
            kpiItems,
            distribution,
            null   // nextAction — P1 박제
        );
    }

    /**
     * 발행 시점 cycle 의 FINALIZED review finalGrade 분포 "비율" (§5). 분모 = FINALIZED 수 (finalGrade null
     * 행 포함) / 버킷 = {S,A,B,C,D} 만 (finalGrade null 또는 비-SABCD 는 버킷 제외). 비율 = count/total round 4.
     * FINALIZED 0개면 {S..D: 0.0000}. CalibrationService effectiveGrade 분포와 별개 (여긴 FINALIZED·finalGrade 만).
     */
    private Map<String, BigDecimal> computeFinalizedDistribution(UUID tenantId, UUID cycleId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String g : GRADE_LIST) {
            counts.put(g, 0);
        }
        int total = 0;
        for (PerformanceReview r : reviewRepository
                .findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId)) {
            if (r.getStatus() != ReviewStatus.FINALIZED) {
                continue;
            }
            total++; // 분모 = FINALIZED 전수 (finalGrade null 도 포함).
            String grade = r.getFinalGrade();
            if (grade != null && counts.containsKey(grade)) {
                counts.merge(grade, 1, Integer::sum);
            }
            // finalGrade null 또는 비-SABCD 는 버킷 제외 (분모만 포함).
        }
        Map<String, BigDecimal> ratio = new LinkedHashMap<>();
        for (String g : GRADE_LIST) {
            BigDecimal value = (total == 0)
                ? BigDecimal.ZERO.setScale(RATIO_SCALE)
                : new BigDecimal(counts.get(g)).divide(new BigDecimal(total), RATIO_SCALE, RoundingMode.HALF_UP);
            ratio.put(g, value);
        }
        return ratio;
    }

    // ═════════════════════════════════════════════════════════════════════
    // active 판정 / 게이트 / 검증 헬퍼
    // ═════════════════════════════════════════════════════════════════════

    /** review 가 active report 를 보유하는지 (publish skip 판정). supersede 안 된 행 1개라도 있으면 true. */
    private boolean hasActiveReport(UUID tenantId, UUID reviewId) {
        List<PerformanceReport> reports = reportRepository.findAllByTenantIdAndReviewId(tenantId, reviewId);
        Set<UUID> supersededIds = supersededIds(reports);
        return reports.stream().anyMatch(r -> !supersededIds.contains(r.getId()));
    }

    /** report 가 active (다른 행의 supersedes_id 로 참조되지 않음) 인지 — 아니면 REPORT_NOT_ACTIVE 409. */
    private void requireActive(UUID tenantId, PerformanceReport report) {
        if (reportRepository.existsByTenantIdAndSupersedesId(tenantId, report.getId())) {
            throw new ApiException(PerformanceErrorCode.REPORT_NOT_ACTIVE,
                Map.of("reportId", report.getId(), "reason", "report has been superseded"));
        }
    }

    /** report 리스트에서 supersede 된 원본 id 집합 (다른 행이 supersedes_id 로 가리키는 id). */
    private Set<UUID> supersededIds(List<PerformanceReport> reports) {
        Set<UUID> set = new HashSet<>();
        for (PerformanceReport r : reports) {
            if (r.getSupersedesId() != null) {
                set.add(r.getSupersedesId());
            }
        }
        return set;
    }

    /** cycle.status==FINALIZED 게이트 (publish/supersede) — 아니면 REPORT_CYCLE_NOT_FINALIZED 422. */
    private void requireFinalizedCycle(UUID cycleId, UUID tenantId) {
        EvaluationCycle cycle = requireCycle(cycleId, tenantId);
        if (cycle.getStatus() != CycleStatus.FINALIZED) {
            throw new ApiException(PerformanceErrorCode.REPORT_CYCLE_NOT_FINALIZED,
                Map.of("cycleId", cycleId, "currentStatus", cycle.getStatus().name(),
                    "requiredStatus", CycleStatus.FINALIZED.name()));
        }
    }

    private EvaluationCycle requireCycle(UUID cycleId, UUID tenantId) {
        return cycleRepository.findByIdAndTenantId(cycleId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND,
                Map.of("entity", "EvaluationCycle", "id", cycleId)));
    }

    private PerformanceReport requireReport(UUID reportId, UUID tenantId) {
        return reportRepository.findByIdAndTenantId(reportId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REPORT_NOT_FOUND,
                Map.of("entity", "PerformanceReport", "id", reportId)));
    }

    // ═════════════════════════════════════════════════════════════════════
    // 생성 / 응답 매핑
    // ═════════════════════════════════════════════════════════════════════

    /** 신규 발행 row 생성 — content 동결 직렬화 + publishedAt = now + supersedesId 옵션. */
    private PerformanceReport newReport(UUID tenantId, PerformanceReview review, UUID actorEmployeeId,
                                        Map<String, BigDecimal> distribution, UUID supersedesId) {
        ReportContent content = buildContent(review, distribution);
        PerformanceReport report = new PerformanceReport();
        report.setTenantId(tenantId);
        report.setCycleId(review.getCycleId());
        report.setReviewId(review.getId());
        report.setEmployeeId(review.getEmployeeId());
        report.setPublishedAt(Instant.now(clock));
        report.setPublishedBy(actorEmployeeId);
        report.setContent(json.serializeContent(content));
        report.setAcknowledged(false);
        report.setSupersedesId(supersedesId);
        return report;
    }

    /** 엔티티 → ReportResponse. content 파싱 객체 노출 + superseded computed (active 판정). */
    private ReportResponse toResponse(UUID tenantId, PerformanceReport report) {
        ReportContent content = json.parseContent(report.getContent());
        boolean superseded = reportRepository.existsByTenantIdAndSupersedesId(tenantId, report.getId());
        return ReportResponse.from(report, content, superseded);
    }
}

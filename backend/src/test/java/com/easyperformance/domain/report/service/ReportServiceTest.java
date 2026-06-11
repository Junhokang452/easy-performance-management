/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.CycleType;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
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

/**
 * ReportService 단위 테스트 — P0-S5 (p0_s5_contract.md §3 게이트 + §4 ErrorCode 3 + §5 content 동결).
 *
 * <p>커버: 조회 (list/get/my active 판정) + publish 일괄 (FINALIZED 한정 게이트 + skip 카운트 + 분포 비율 +
 * kpiItems 동결) + supersede 개별 (FINALIZED 게이트 + active 게이트 + 신규 row + content 재동결) +
 * view/acknowledge (active 게이트 + 멱등) + ErrorCode (REPORT_NOT_FOUND / REPORT_CYCLE_NOT_FINALIZED /
 * REPORT_NOT_ACTIVE) + superseded computed.
 *
 * <p>{@link ReviewService} 는 mock — {@code getReview} 가 content 동결 소스 (finalGrade/score/kpiScore/
 * managerComment + kpiScoreDetail). {@link TenantSupport} fallback 테넌트 ({@code ...-001}) 사용 (게이트 OFF —
 * CalibrationServiceTest 정합).
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private PerformanceReportRepository reportRepository;
    @Mock private PerformanceReviewRepository reviewRepository;
    @Mock private EvaluationCycleRepository cycleRepository;
    @Mock private ReviewService reviewService;

    private ReportService service;

    private UUID tenantId;
    private UUID cycleId;
    private UUID reportId;

    private static final Clock FIXED =
        Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        cycleId = UUID.randomUUID();
        reportId = UUID.randomUUID();
        service = new ReportService(reportRepository, reviewRepository, cycleRepository, reviewService, FIXED);
    }

    // ═══════════════════════════ 조회 ═══════════════════════════

    @Test
    void listReports_returnsAllIncludingSuperseded() {
        PerformanceReview review = review(UUID.randomUUID(), ReviewStatus.FINALIZED, new BigDecimal("85"), "A");
        PerformanceReport r1 = report(uuid(1), review, null);          // 원본 (superseded)
        PerformanceReport r2 = report(uuid(2), review, uuid(1));       // 정정본 (active)
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(java.util.Optional.of(cycle(CycleStatus.FINALIZED)));
        when(reportRepository.findAllByTenantIdAndCycleIdOrderByPublishedAtDesc(any(), eq(cycleId)))
            .thenReturn(List.of(r2, r1));
        stubReviewContent(review);
        // r1 은 r2 가 supersedes_id 로 참조 → superseded=true.
        when(reportRepository.existsByTenantIdAndSupersedesId(any(), eq(uuid(1)))).thenReturn(true);
        when(reportRepository.existsByTenantIdAndSupersedesId(any(), eq(uuid(2)))).thenReturn(false);

        List<ReportResponse> result = service.listReports(cycleId, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(uuid(2));
        assertThat(result.get(0).superseded()).isFalse();
        assertThat(result.get(1).id()).isEqualTo(uuid(1));
        assertThat(result.get(1).superseded()).isTrue();         // computed
        assertThat(result.get(1).supersedesId()).isNull();
        assertThat(result.get(0).supersedesId()).isEqualTo(uuid(1));
    }

    @Test
    void getReport_notFound_throws() {
        when(reportRepository.findByIdAndTenantId(eq(reportId), any())).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.getReport(reportId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    void getMyReport_returnsActiveRowOnly() {
        UUID employeeId = UUID.randomUUID();
        PerformanceReview review = review(UUID.randomUUID(), ReviewStatus.FINALIZED, new BigDecimal("90"), "S");
        PerformanceReport original = report(uuid(1), review, null);    // superseded
        PerformanceReport active = report(uuid(2), review, uuid(1));   // active
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(java.util.Optional.of(cycle(CycleStatus.FINALIZED)));
        when(reportRepository.findAllByTenantIdAndCycleIdAndEmployeeId(any(), eq(cycleId), eq(employeeId)))
            .thenReturn(List.of(active, original));
        stubReviewContent(review);
        when(reportRepository.existsByTenantIdAndSupersedesId(any(), eq(uuid(2)))).thenReturn(false);

        ReportResponse response = service.getMyReport(cycleId, employeeId);

        // active = uuid(1) 을 supersedes_id 로 가리키는 정정본 (uuid(2)) — 원본 uuid(1) 은 제외.
        assertThat(response.id()).isEqualTo(uuid(2));
        assertThat(response.superseded()).isFalse();
    }

    @Test
    void getMyReport_noActiveRow_throwsNotFound() {
        // 모든 행이 superseded (체인 꼬리 부재 — 방어적). 빈 결과도 동일 404.
        UUID employeeId = UUID.randomUUID();
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(java.util.Optional.of(cycle(CycleStatus.FINALIZED)));
        when(reportRepository.findAllByTenantIdAndCycleIdAndEmployeeId(any(), eq(cycleId), eq(employeeId)))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.getMyReport(cycleId, employeeId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REPORT_NOT_FOUND);
    }

    // ═══════════════════════════ publish (일괄) ═══════════════════════════

    @Test
    void publish_cycleNotFinalized_isRejected() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(java.util.Optional.of(cycle(CycleStatus.CALIBRATION)));

        assertThatThrownBy(() -> service.publish(cycleId, UUID.randomUUID()))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REPORT_CYCLE_NOT_FINALIZED);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void publish_publishesFinalizedWithoutActiveReport_skipsRest() {
        // f1 FINALIZED active 없음 → 발행 / f2 FINALIZED 이미 active 존재 → skip /
        // m1 MANAGER_SUBMITTED → skip silently (FINALIZED 아님).
        UUID f1Id = uuid(11);
        UUID f2Id = uuid(12);
        UUID m1Id = uuid(13);
        PerformanceReview f1 = review(f1Id, ReviewStatus.FINALIZED, new BigDecimal("85"), "A");
        PerformanceReview f2 = review(f2Id, ReviewStatus.FINALIZED, new BigDecimal("95"), "S");
        PerformanceReview m1 = review(m1Id, ReviewStatus.MANAGER_SUBMITTED, new BigDecimal("70"), null);

        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(java.util.Optional.of(cycle(CycleStatus.FINALIZED)));
        when(reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(any(), eq(cycleId)))
            .thenReturn(List.of(f1, f2, m1));
        // f1 → active report 없음 (발행 대상).
        when(reportRepository.findAllByTenantIdAndReviewId(any(), eq(f1Id))).thenReturn(List.of());
        // f2 → 이미 active report 존재 (supersede 안 된 행) → skip.
        PerformanceReport f2Existing = report(uuid(99), f2, null);
        when(reportRepository.findAllByTenantIdAndReviewId(any(), eq(f2Id))).thenReturn(List.of(f2Existing));
        stubReviewContent(f1);
        when(reportRepository.save(any(PerformanceReport.class))).thenAnswer(inv -> {
            PerformanceReport p = inv.getArgument(0);
            if (p.getId() == null) p.setId(reportId);
            return p;
        });
        when(reportRepository.existsByTenantIdAndSupersedesId(any(), any())).thenReturn(false);

        ReportPublishResponse response = service.publish(cycleId, UUID.randomUUID());

        assertThat(response.publishedCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(1);   // f2 만 skip (m1 은 FINALIZED 아니라 count 외)
        assertThat(response.published()).hasSize(1);
        assertThat(response.published().get(0).reviewId()).isEqualTo(f1Id);
        // 발행 시점 published_at = FIXED clock.
        assertThat(response.published().get(0).publishedAt()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));
        // f1 만 저장.
        verify(reportRepository, times(1)).save(any(PerformanceReport.class));
    }

    @Test
    void publish_computesFinalizedDistributionRatio_nullGradeInDenominator() {
        // FINALIZED 4: A, A, S, finalGrade null (분모 포함·버킷 제외). 비율 = A 0.5, S 0.25, B/C/D 0.
        UUID f1Id = uuid(21);
        PerformanceReview f1 = review(f1Id, ReviewStatus.FINALIZED, new BigDecimal("85"), "A");
        PerformanceReview f2 = review(uuid(22), ReviewStatus.FINALIZED, new BigDecimal("82"), "A");
        PerformanceReview f3 = review(uuid(23), ReviewStatus.FINALIZED, new BigDecimal("95"), "S");
        PerformanceReview fNull = review(uuid(24), ReviewStatus.FINALIZED, new BigDecimal("50"), null); // 분모만
        PerformanceReview self = review(uuid(25), ReviewStatus.SELF_SUBMITTED, new BigDecimal("70"), null); // 제외

        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(java.util.Optional.of(cycle(CycleStatus.FINALIZED)));
        when(reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(any(), eq(cycleId)))
            .thenReturn(List.of(f1, f2, f3, fNull, self));
        when(reportRepository.findAllByTenantIdAndReviewId(any(), eq(f1Id))).thenReturn(List.of());
        // f2/f3/fNull 은 이미 active report 존재 → publish 대상 외 (단건 발행만 검증).
        when(reportRepository.findAllByTenantIdAndReviewId(any(), eq(uuid(22))))
            .thenReturn(List.of(report(uuid(91), f2, null)));
        when(reportRepository.findAllByTenantIdAndReviewId(any(), eq(uuid(23))))
            .thenReturn(List.of(report(uuid(92), f3, null)));
        when(reportRepository.findAllByTenantIdAndReviewId(any(), eq(uuid(24))))
            .thenReturn(List.of(report(uuid(93), fNull, null)));
        stubReviewContent(f1);
        when(reportRepository.save(any(PerformanceReport.class))).thenAnswer(inv -> {
            PerformanceReport p = inv.getArgument(0);
            if (p.getId() == null) p.setId(reportId);
            return p;
        });
        when(reportRepository.existsByTenantIdAndSupersedesId(any(), any())).thenReturn(false);

        ReportPublishResponse response = service.publish(cycleId, UUID.randomUUID());

        // f1 만 신규 발행 (나머지 3 active 존재). 분포 = 전체 FINALIZED 4 기준.
        assertThat(response.publishedCount()).isEqualTo(1);
        var dist = response.published().get(0).content().distribution();
        // 분모 = 4 (fNull 포함). A=2 → 0.5, S=1 → 0.25, B/C/D=0, null 행 버킷 제외.
        assertThat(dist.get("A")).isEqualByComparingTo("0.5000");
        assertThat(dist.get("S")).isEqualByComparingTo("0.2500");
        assertThat(dist.get("B")).isEqualByComparingTo("0.0000");
        assertThat(dist.get("C")).isEqualByComparingTo("0.0000");
        assertThat(dist.get("D")).isEqualByComparingTo("0.0000");
    }

    @Test
    void publish_freezesContentFromReviewSnapshot() {
        UUID f1Id = uuid(31);
        PerformanceReview f1 = review(f1Id, ReviewStatus.FINALIZED, new BigDecimal("88.50"), "A");
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(java.util.Optional.of(cycle(CycleStatus.FINALIZED)));
        when(reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(any(), eq(cycleId)))
            .thenReturn(List.of(f1));
        when(reportRepository.findAllByTenantIdAndReviewId(any(), eq(f1Id))).thenReturn(List.of());
        // review 스냅샷 — kpiScoreDetail 1 항목 + managerComment.
        ReviewKpiItemResponse item = new ReviewKpiItemResponse(
            uuid(50), "매출 달성", "팀 KPI", new BigDecimal("0.5"), new BigDecimal("100"), "건",
            new BigDecimal("90"), new BigDecimal("0.9"), new BigDecimal("90.00"),
            new BigDecimal("88.00"), new BigDecimal("88.00"));
        when(reviewService.getReview(eq(f1Id))).thenReturn(reviewResponse(f1, List.of(item)));
        when(reportRepository.save(any(PerformanceReport.class))).thenAnswer(inv -> {
            PerformanceReport p = inv.getArgument(0);
            if (p.getId() == null) p.setId(reportId);
            return p;
        });
        when(reportRepository.existsByTenantIdAndSupersedesId(any(), any())).thenReturn(false);

        ReportPublishResponse response = service.publish(cycleId, UUID.randomUUID());

        var content = response.published().get(0).content();
        assertThat(content.finalGrade()).isEqualTo("A");
        assertThat(content.finalScore()).isEqualByComparingTo("88.50");
        assertThat(content.kpiScore()).isEqualByComparingTo("88.50");
        assertThat(content.managerComment()).isEqualTo("우수한 성과");
        assertThat(content.mboScore()).isNull();          // P0 박제
        assertThat(content.competencyScore()).isNull();
        assertThat(content.mraScore()).isNull();
        assertThat(content.nextAction()).isNull();        // P1 박제
        assertThat(content.kpiItems()).hasSize(1);        // review.kpiScoreDetail 전체 사본
        assertThat(content.kpiItems().get(0).nodeLabel()).isEqualTo("매출 달성");
    }

    // ═══════════════════════════ supersede (개별) ═══════════════════════════

    @Test
    void supersede_cycleNotFinalized_isRejected() {
        PerformanceReport original = report(reportId,
            review(UUID.randomUUID(), ReviewStatus.FINALIZED, new BigDecimal("85"), "A"), null);
        when(reportRepository.findByIdAndTenantId(eq(reportId), any())).thenReturn(java.util.Optional.of(original));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(java.util.Optional.of(cycle(CycleStatus.CALIBRATION)));

        assertThatThrownBy(() -> service.supersede(reportId, UUID.randomUUID()))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REPORT_CYCLE_NOT_FINALIZED);
    }

    @Test
    void supersede_notActiveOriginal_isRejected() {
        PerformanceReport original = report(reportId,
            review(UUID.randomUUID(), ReviewStatus.FINALIZED, new BigDecimal("85"), "A"), null);
        when(reportRepository.findByIdAndTenantId(eq(reportId), any())).thenReturn(java.util.Optional.of(original));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(java.util.Optional.of(cycle(CycleStatus.FINALIZED)));
        // 이미 supersede 된 원본 → REPORT_NOT_ACTIVE.
        when(reportRepository.existsByTenantIdAndSupersedesId(any(), eq(reportId))).thenReturn(true);

        assertThatThrownBy(() -> service.supersede(reportId, UUID.randomUUID()))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REPORT_NOT_ACTIVE);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void supersede_createsNewRowWithSupersedesIdAndRefreezesContent() {
        UUID reviewId = UUID.randomUUID();
        PerformanceReview review = review(reviewId, ReviewStatus.FINALIZED, new BigDecimal("92"), "S");
        PerformanceReport original = report(reportId, review, null);
        when(reportRepository.findByIdAndTenantId(eq(reportId), any())).thenReturn(java.util.Optional.of(original));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(java.util.Optional.of(cycle(CycleStatus.FINALIZED)));
        // 원본은 active (아무도 참조 안 함). supersede 후 신규 row 의 superseded 판정에도 false.
        when(reportRepository.existsByTenantIdAndSupersedesId(any(), any())).thenReturn(false);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(java.util.Optional.of(review));
        // 재동결 분포 산출 + content.
        when(reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(any(), eq(cycleId)))
            .thenReturn(List.of(review));
        stubReviewContent(review);
        when(reportRepository.save(any(PerformanceReport.class))).thenAnswer(inv -> {
            PerformanceReport p = inv.getArgument(0);
            if (p.getId() == null) p.setId(uuid(77));
            return p;
        });
        UUID actor = UUID.randomUUID();

        ReportResponse response = service.supersede(reportId, actor);

        // 신규 row — supersedesId = 원본, content 재동결, publishedBy = actor.
        ArgumentCaptor<PerformanceReport> captor = ArgumentCaptor.forClass(PerformanceReport.class);
        verify(reportRepository).save(captor.capture());
        PerformanceReport saved = captor.getValue();
        assertThat(saved.getSupersedesId()).isEqualTo(reportId);
        assertThat(saved.getReviewId()).isEqualTo(reviewId);
        assertThat(saved.getPublishedBy()).isEqualTo(actor);
        assertThat(saved.getPublishedAt()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));
        assertThat(saved.getContent()).isNotNull();    // 재동결
        assertThat(response.supersedesId()).isEqualTo(reportId);
        assertThat(response.content().finalGrade()).isEqualTo("S");
    }

    // ═══════════════════════════ view ═══════════════════════════

    @Test
    void view_setsViewedAtFirstTimeOnly_idempotent() {
        PerformanceReview review = review(UUID.randomUUID(), ReviewStatus.FINALIZED, new BigDecimal("85"), "A");
        PerformanceReport report = report(reportId, review, null);
        when(reportRepository.findByIdAndTenantId(eq(reportId), any())).thenReturn(java.util.Optional.of(report));
        when(reportRepository.existsByTenantIdAndSupersedesId(any(), eq(reportId))).thenReturn(false);
        stubReviewContent(review);
        when(reportRepository.save(any(PerformanceReport.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1차 — viewedAt null → set + save.
        ReportResponse first = service.view(reportId);
        assertThat(first.viewedAt()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));
        assertThat(report.getViewedAt()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));

        // 2차 — 이미 set → no-op (save 추가 호출 없음).
        ReportResponse second = service.view(reportId);
        assertThat(second.viewedAt()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));
        // save 는 1차에서만 (멱등 no-op).
        verify(reportRepository, times(1)).save(any(PerformanceReport.class));
    }

    @Test
    void view_notActive_isRejected() {
        PerformanceReport report = report(reportId,
            review(UUID.randomUUID(), ReviewStatus.FINALIZED, new BigDecimal("85"), "A"), null);
        when(reportRepository.findByIdAndTenantId(eq(reportId), any())).thenReturn(java.util.Optional.of(report));
        when(reportRepository.existsByTenantIdAndSupersedesId(any(), eq(reportId))).thenReturn(true);

        assertThatThrownBy(() -> service.view(reportId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REPORT_NOT_ACTIVE);
        verify(reportRepository, never()).save(any());
    }

    // ═══════════════════════════ acknowledge ═══════════════════════════

    @Test
    void acknowledge_setsTrueAndTimestamp_idempotent() {
        PerformanceReview review = review(UUID.randomUUID(), ReviewStatus.FINALIZED, new BigDecimal("85"), "A");
        PerformanceReport report = report(reportId, review, null);
        when(reportRepository.findByIdAndTenantId(eq(reportId), any())).thenReturn(java.util.Optional.of(report));
        when(reportRepository.existsByTenantIdAndSupersedesId(any(), eq(reportId))).thenReturn(false);
        stubReviewContent(review);
        when(reportRepository.save(any(PerformanceReport.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID actor = UUID.randomUUID();

        ReportResponse first = service.acknowledge(reportId, actor);
        assertThat(first.acknowledged()).isTrue();
        assertThat(first.acknowledgedAt()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));
        assertThat(report.isAcknowledged()).isTrue();

        // 2차 — 이미 true → no-op.
        service.acknowledge(reportId, actor);
        verify(reportRepository, times(1)).save(any(PerformanceReport.class));
    }

    @Test
    void acknowledge_notActive_isRejected() {
        PerformanceReport report = report(reportId,
            review(UUID.randomUUID(), ReviewStatus.FINALIZED, new BigDecimal("85"), "A"), null);
        when(reportRepository.findByIdAndTenantId(eq(reportId), any())).thenReturn(java.util.Optional.of(report));
        when(reportRepository.existsByTenantIdAndSupersedesId(any(), eq(reportId))).thenReturn(true);

        assertThatThrownBy(() -> service.acknowledge(reportId, UUID.randomUUID()))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REPORT_NOT_ACTIVE);
        verify(reportRepository, never()).save(any());
    }

    // ═══════════════════════════ fixtures ═══════════════════════════

    private UUID uuid(int seq) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", seq));
    }

    private EvaluationCycle cycle(CycleStatus status) {
        EvaluationCycle c = new EvaluationCycle();
        c.setId(cycleId);
        c.setTenantId(tenantId);
        c.setName("2026 상반기");
        c.setPeriodStart(LocalDate.of(2026, 1, 1));
        c.setPeriodEnd(LocalDate.of(2026, 6, 30));
        c.setCycleType(CycleType.HALF_ANNUAL);
        c.setStatus(status);
        return c;
    }

    private PerformanceReview review(UUID id, ReviewStatus status, BigDecimal score, String finalGrade) {
        PerformanceReview r = new PerformanceReview();
        r.setId(id);
        r.setTenantId(tenantId);
        r.setCycleId(cycleId);
        r.setEmployeeId(UUID.randomUUID());
        r.setStatus(status);
        r.setKpiScore(score);
        r.setFinalScore(score);
        r.setFinalGrade(finalGrade);
        r.setManagerComment("우수한 성과");
        return r;
    }

    private PerformanceReport report(UUID id, PerformanceReview review, UUID supersedesId) {
        PerformanceReport report = new PerformanceReport();
        report.setId(id);
        report.setTenantId(tenantId);
        report.setCycleId(review.getCycleId());
        report.setReviewId(review.getId());
        report.setEmployeeId(review.getEmployeeId());
        report.setPublishedAt(Instant.parse("2026-06-29T00:00:00Z"));
        report.setAcknowledged(false);
        report.setSupersedesId(supersedesId);
        // content 는 실제 직렬화 — toResponse 파싱 정합 (graceful null 회피).
        report.setContent("{\"finalGrade\":\"" + (review.getFinalGrade() == null ? "" : review.getFinalGrade())
            + "\",\"finalScore\":null,\"kpiScore\":null,\"mboScore\":null,\"competencyScore\":null,"
            + "\"mraScore\":null,\"managerComment\":null,\"kpiItems\":null,\"distribution\":null,"
            + "\"nextAction\":null}");
        return report;
    }

    /** review 확정값 노출 (content 동결 소스) — getReview stub (kpiScoreDetail 1 항목). */
    private void stubReviewContent(PerformanceReview review) {
        lenient().when(reviewService.getReview(eq(review.getId())))
            .thenReturn(reviewResponse(review, List.of()));
    }

    private ReviewResponse reviewResponse(PerformanceReview e, List<ReviewKpiItemResponse> kpiItems) {
        return ReviewResponse.from(e, kpiItems);
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.calibration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationAdjustmentRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationConfirmRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationConfirmResponse;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationSessionCreateRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationSessionResponse;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationSessionUpdateRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationTransitionRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionApplyRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionApplyResponse;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionResponse;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionSimulateRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionSimulationResponse;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.ProposedGradeRow;
import com.easyperformance.domain.calibration.entity.CalibrationSession;
import com.easyperformance.domain.calibration.entity.CalibrationStatus;
import com.easyperformance.domain.calibration.entity.RatingDistribution;
import com.easyperformance.domain.calibration.repository.CalibrationSessionRepository;
import com.easyperformance.domain.calibration.repository.RatingDistributionRepository;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.CycleType;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.evaluationpolicy.entity.DistributionMode;
import com.easyperformance.domain.evaluationpolicy.entity.EvaluationPolicy;
import com.easyperformance.domain.evaluationpolicy.entity.RatingScale;
import com.easyperformance.domain.evaluationpolicy.repository.EvaluationPolicyRepository;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewTransitionRequest;
import com.easyperformance.domain.review.entity.PerformanceReview;
import com.easyperformance.domain.review.entity.ReviewStatus;
import com.easyperformance.domain.review.repository.PerformanceReviewRepository;
import com.easyperformance.domain.review.service.ReviewService;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;

/**
 * CalibrationService 단위 테스트 — P0-S4 (p0_s4_contract.md §3 상태기계 + §4 ErrorCode 10 + §5 산식).
 *
 * <p>커버: 세션 CRUD/transition/adjust(자동 승격)/confirm(finalize)/delete + 분포 GET/simulate/apply +
 * ErrorCode (SESSION_NOT_FOUND / INVALID_TRANSITION / MODE_NOT_FORCED / SCALE_NOT_SUPPORTED /
 * CYCLE_STAGE_MISMATCH / ADJUSTMENT_INVALID / SESSION_LOCKED / CANNOT_DELETE / REVIEW_NOT_READY +
 * 교차 재사용 REVIEW_NOT_FOUND) + effectiveGrade + apply upsert·재적용 append.
 *
 * <p>ReviewService 는 mock — {@code bandGrade} 는 실제 산식 위임 (lenient answer), {@code transition} 은
 * confirm finalize 검증용.
 */
@ExtendWith(MockitoExtension.class)
class CalibrationServiceTest {

    @Mock private CalibrationSessionRepository sessionRepository;
    @Mock private RatingDistributionRepository distributionRepository;
    @Mock private PerformanceReviewRepository reviewRepository;
    @Mock private EvaluationCycleRepository cycleRepository;
    @Mock private EvaluationPolicyRepository policyRepository;
    @Mock private ReviewService reviewService;

    private CalibrationService service;

    private UUID tenantId;
    private UUID cycleId;
    private UUID sessionId;

    private static final Clock FIXED =
        Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        cycleId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        service = new CalibrationService(sessionRepository, distributionRepository, reviewRepository,
            cycleRepository, policyRepository, reviewService, FIXED);
        // bandGrade 실제 산식 위임 (S≥90/A≥80/B≥70/C≥60/D<60, null→null).
        lenient().when(reviewService.bandGrade(any())).thenAnswer(inv -> {
            BigDecimal s = inv.getArgument(0);
            if (s == null) return null;
            if (s.compareTo(new BigDecimal("90")) >= 0) return "S";
            if (s.compareTo(new BigDecimal("80")) >= 0) return "A";
            if (s.compareTo(new BigDecimal("70")) >= 0) return "B";
            if (s.compareTo(new BigDecimal("60")) >= 0) return "C";
            return "D";
        });
    }

    // ═══════════════════════════ 세션 조회 / 생성 / PATCH ═══════════════════════════

    @Test
    void getSession_notFound_throws() {
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSession(sessionId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CALIBRATION_SESSION_NOT_FOUND);
    }

    @Test
    void createSession_happy_statusPlanned() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.GOAL_SETTING)));
        when(sessionRepository.save(any(CalibrationSession.class))).thenAnswer(inv -> {
            CalibrationSession s = inv.getArgument(0);
            if (s.getId() == null) s.setId(sessionId);
            return s;
        });

        UUID p1 = UUID.randomUUID();
        CalibrationSessionResponse response = service.createSession(cycleId,
            new CalibrationSessionCreateRequest(null, Instant.parse("2026-07-01T10:00:00Z"), List.of(p1)));

        assertThat(response.status()).isEqualTo(CalibrationStatus.PLANNED);
        assertThat(response.cycleId()).isEqualTo(cycleId);
        assertThat(response.participantIds()).containsExactly(p1);
    }

    @Test
    void createSession_terminalCycle_isRejected() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.FINALIZED)));

        assertThatThrownBy(() -> service.createSession(cycleId,
                new CalibrationSessionCreateRequest(null, null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CALIBRATION_CYCLE_STAGE_MISMATCH);
    }

    @Test
    void updateSession_nonPlanned_isLocked() {
        CalibrationSession session = session(CalibrationStatus.IN_SESSION);
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));

        assertThatThrownBy(() -> service.updateSession(sessionId,
                new CalibrationSessionUpdateRequest(null, null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CALIBRATION_SESSION_LOCKED);
    }

    // ═══════════════════════════ transition (§3) ═══════════════════════════

    @Test
    void transition_plannedToInSession_happy() {
        CalibrationSession session = session(CalibrationStatus.PLANNED);
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(sessionRepository.save(any(CalibrationSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CalibrationSessionResponse response = service.transition(sessionId,
            new CalibrationTransitionRequest(CalibrationStatus.IN_SESSION, null));

        assertThat(response.status()).isEqualTo(CalibrationStatus.IN_SESSION);
    }

    @Test
    void transition_inSessionToInSession_wrongCycleStage_isRejected() {
        CalibrationSession session = session(CalibrationStatus.PLANNED);
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.MANAGER_REVIEW))); // CALIBRATION 요구

        assertThatThrownBy(() -> service.transition(sessionId,
                new CalibrationTransitionRequest(CalibrationStatus.IN_SESSION, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CALIBRATION_CYCLE_STAGE_MISMATCH);
    }

    @Test
    void transition_confirmViaTransition_isRejected() {
        // IN_SESSION → CONFIRMED 는 confirm 전용 — transition 으로 불가.
        CalibrationSession session = session(CalibrationStatus.IN_SESSION);
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.transition(sessionId,
                new CalibrationTransitionRequest(CalibrationStatus.CONFIRMED, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CALIBRATION_INVALID_STATUS_TRANSITION);
    }

    @Test
    void transition_confirmedToClosed_happy() {
        CalibrationSession session = session(CalibrationStatus.CONFIRMED);
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(CalibrationSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CalibrationSessionResponse response = service.transition(sessionId,
            new CalibrationTransitionRequest(CalibrationStatus.CLOSED, null));

        assertThat(response.status()).isEqualTo(CalibrationStatus.CLOSED);
    }

    // ═══════════════════════════ adjust ═══════════════════════════

    @Test
    void adjust_inSession_recordsFromGradeAndAutoPromotes() {
        CalibrationSession session = session(CalibrationStatus.IN_SESSION);
        UUID reviewId = UUID.randomUUID();
        PerformanceReview review = review(reviewId, ReviewStatus.CALIBRATION, new BigDecimal("85.00"), null);
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any(CalibrationSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CalibrationSessionResponse response = service.adjust(sessionId,
            new CalibrationAdjustmentRequest(reviewId, "S", "탁월 성과", UUID.randomUUID()));

        // IN_SESSION → ADJUSTED 자동 승격.
        assertThat(response.status()).isEqualTo(CalibrationStatus.ADJUSTED);
        // review.finalGrade = toGrade.
        assertThat(review.getFinalGrade()).isEqualTo("S");
        // adjustment_log entry: fromGrade = 조정 전 effectiveGrade(85→A), toGrade = S.
        assertThat(response.adjustmentLog()).hasSize(1);
        assertThat(response.adjustmentLog().get(0).fromGrade()).isEqualTo("A");
        assertThat(response.adjustmentLog().get(0).toGrade()).isEqualTo("S");
        assertThat(response.adjustmentLog().get(0).reason()).isEqualTo("탁월 성과");
    }

    @Test
    void adjust_invalidGrade_isRejected() {
        CalibrationSession session = session(CalibrationStatus.IN_SESSION);
        UUID reviewId = UUID.randomUUID();
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));

        assertThatThrownBy(() -> service.adjust(sessionId,
                new CalibrationAdjustmentRequest(reviewId, "Z", null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CALIBRATION_ADJUSTMENT_INVALID);
    }

    @Test
    void adjust_reviewNotFound_reusesReviewNotFoundCode() {
        CalibrationSession session = session(CalibrationStatus.IN_SESSION);
        UUID reviewId = UUID.randomUUID();
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adjust(sessionId,
                new CalibrationAdjustmentRequest(reviewId, "A", null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    void adjust_reviewNotInCycle_isRejected() {
        CalibrationSession session = session(CalibrationStatus.IN_SESSION);
        UUID reviewId = UUID.randomUUID();
        PerformanceReview review = review(reviewId, ReviewStatus.CALIBRATION, new BigDecimal("70"), null);
        review.setCycleId(UUID.randomUUID()); // 다른 cycle
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.adjust(sessionId,
                new CalibrationAdjustmentRequest(reviewId, "A", null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CALIBRATION_ADJUSTMENT_INVALID);
    }

    @Test
    void adjust_reviewNotCalibrationStatus_isNotReady() {
        CalibrationSession session = session(CalibrationStatus.IN_SESSION);
        UUID reviewId = UUID.randomUUID();
        PerformanceReview review = review(reviewId, ReviewStatus.MANAGER_SUBMITTED, new BigDecimal("70"), null);
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.adjust(sessionId,
                new CalibrationAdjustmentRequest(reviewId, "A", null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CALIBRATION_REVIEW_NOT_READY);
    }

    @Test
    void adjust_confirmedSession_isLocked() {
        CalibrationSession session = session(CalibrationStatus.CONFIRMED);
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.adjust(sessionId,
                new CalibrationAdjustmentRequest(UUID.randomUUID(), "A", null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CALIBRATION_SESSION_LOCKED);
    }

    // ═══════════════════════════ confirm ═══════════════════════════

    @Test
    void confirm_finalizeReviews_finalizesCalibrationRowsAndSkipsNullScore() {
        CalibrationSession session = session(CalibrationStatus.ADJUSTED);
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        UUID r3 = UUID.randomUUID();
        PerformanceReview rev1 = review(r1, ReviewStatus.CALIBRATION, new BigDecimal("85"), "A"); // finalize
        PerformanceReview rev2 = review(r2, ReviewStatus.CALIBRATION, null, null);                // skip (null score)
        PerformanceReview rev3 = review(r3, ReviewStatus.MANAGER_SUBMITTED, new BigDecimal("70"), null); // not CALIBRATION → skip silently
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(sessionRepository.save(any(CalibrationSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(any(), eq(cycleId)))
            .thenReturn(List.of(rev1, rev2, rev3));
        UUID actor = UUID.randomUUID();

        CalibrationConfirmResponse response = service.confirm(sessionId,
            new CalibrationConfirmRequest(actor, true));

        assertThat(response.session().status()).isEqualTo(CalibrationStatus.CONFIRMED);
        assertThat(response.session().confirmedBy()).isEqualTo(actor);
        assertThat(response.session().confirmedAt()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));
        assertThat(response.finalizedCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(1);
        // P0-S3 transition 재사용 (public 경로) — rev1 만 FINALIZED 전이 호출.
        verify(reviewService).transition(eq(r1),
            eq(new ReviewTransitionRequest(ReviewStatus.FINALIZED, actor)));
        verify(reviewService, never()).transition(eq(r2), any());
    }

    @Test
    void confirm_withoutFinalize_onlyConfirmsSession() {
        CalibrationSession session = session(CalibrationStatus.IN_SESSION);
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(sessionRepository.save(any(CalibrationSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CalibrationConfirmResponse response = service.confirm(sessionId,
            new CalibrationConfirmRequest(UUID.randomUUID(), false));

        assertThat(response.session().status()).isEqualTo(CalibrationStatus.CONFIRMED);
        assertThat(response.finalizedCount()).isZero();
        assertThat(response.skippedCount()).isZero();
        verify(reviewService, never()).transition(any(), any());
    }

    @Test
    void confirm_plannedSession_isLocked() {
        // PLANNED 는 confirm 불가 (IN_SESSION/ADJUSTED 만).
        CalibrationSession session = session(CalibrationStatus.PLANNED);
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.confirm(sessionId,
                new CalibrationConfirmRequest(null, true)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CALIBRATION_SESSION_LOCKED);
    }

    // ═══════════════════════════ delete ═══════════════════════════

    @Test
    void deleteSession_planned_succeeds() {
        CalibrationSession session = session(CalibrationStatus.PLANNED);
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));

        service.deleteSession(sessionId);

        verify(sessionRepository).delete(session);
    }

    @Test
    void deleteSession_nonPlanned_isRejected() {
        CalibrationSession session = session(CalibrationStatus.IN_SESSION);
        when(sessionRepository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.deleteSession(sessionId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CALIBRATION_SESSION_CANNOT_DELETE);
        verify(sessionRepository, never()).delete(any());
    }

    // ═══════════════════════════ 분포 GET ═══════════════════════════

    @Test
    void getDistribution_bucketsByEffectiveGrade() {
        // CALIBRATION(85→A, finalGrade null) + FINALIZED(finalGrade S) + CALIBRATION(null score→UNRATED) +
        // SELF_SUBMITTED(제외). calibrationReady = CALIBRATION AND kpiScore!=null = 1.
        PerformanceReview a = review(UUID.randomUUID(), ReviewStatus.CALIBRATION, new BigDecimal("85"), null);
        PerformanceReview s = review(UUID.randomUUID(), ReviewStatus.FINALIZED, new BigDecimal("95"), "S");
        PerformanceReview u = review(UUID.randomUUID(), ReviewStatus.CALIBRATION, null, null);
        PerformanceReview x = review(UUID.randomUUID(), ReviewStatus.SELF_SUBMITTED, new BigDecimal("70"), null);
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId)))
            .thenReturn(Optional.of(policy(DistributionMode.HYBRID, RatingScale.S_A_B_C_D, null)));
        when(reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(any(), eq(cycleId)))
            .thenReturn(List.of(a, s, u, x));
        when(distributionRepository.findByTenantIdAndCycleIdAndOrgUnitIdIsNull(any(), eq(cycleId)))
            .thenReturn(Optional.empty());

        DistributionResponse response = service.getDistribution(cycleId);

        assertThat(response.currentDistribution().get("S")).isEqualTo(1);
        assertThat(response.currentDistribution().get("A")).isEqualTo(1);
        assertThat(response.currentDistribution().get("UNRATED")).isEqualTo(1);
        assertThat(response.totalReviews()).isEqualTo(4);
        assertThat(response.calibrationReadyCount()).isEqualTo(1);
        assertThat(response.forcedApplied()).isFalse();
        assertThat(response.distributionMode()).isEqualTo(DistributionMode.HYBRID);
    }

    // ═══════════════════════════ simulate ═══════════════════════════

    @Test
    void simulate_allocatesByLargestRemainder_noStore() {
        // N=5 CALIBRATION reviews, target {S0.2,A0.2,B0.2,C0.2,D0.2} → 1 each.
        // 정렬 kpiScore DESC: 95,85,75,65,55 → S,A,B,C,D 각 1.
        List<PerformanceReview> reviews = List.of(
            review(uuid(1), ReviewStatus.CALIBRATION, new BigDecimal("55"), null),
            review(uuid(2), ReviewStatus.CALIBRATION, new BigDecimal("95"), null),
            review(uuid(3), ReviewStatus.CALIBRATION, new BigDecimal("75"), null),
            review(uuid(4), ReviewStatus.CALIBRATION, new BigDecimal("65"), null),
            review(uuid(5), ReviewStatus.CALIBRATION, new BigDecimal("85"), null));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId)))
            .thenReturn(Optional.of(policy(DistributionMode.FORCED, RatingScale.S_A_B_C_D, null)));
        when(reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(any(), eq(cycleId)))
            .thenReturn(reviews);

        DistributionSimulationResponse response = service.simulate(cycleId,
            new DistributionSimulateRequest(Map.of(
                "S", new BigDecimal("0.2"), "A", new BigDecimal("0.2"), "B", new BigDecimal("0.2"),
                "C", new BigDecimal("0.2"), "D", new BigDecimal("0.2"))));

        assertThat(response.proposed()).hasSize(5);
        // 최고 점수(95) → S.
        ProposedGradeRow top = response.proposed().get(0);
        assertThat(top.kpiScore()).isEqualByComparingTo("95");
        assertThat(top.proposedGrade()).isEqualTo("S");
        assertThat(response.resultingDistribution().get("S")).isEqualTo(1);
        assertThat(response.resultingDistribution().get("D")).isEqualTo(1);
        // 무저장 — distribution 저장 호출 0.
        verify(distributionRepository, never()).save(any());
    }

    @Test
    void simulate_absoluteMode_isRejected() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId)))
            .thenReturn(Optional.of(policy(DistributionMode.ABSOLUTE, RatingScale.S_A_B_C_D, null)));

        assertThatThrownBy(() -> service.simulate(cycleId, new DistributionSimulateRequest(null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.DISTRIBUTION_MODE_NOT_FORCED);
    }

    @Test
    void simulate_nonSabcdScale_isRejected() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId)))
            .thenReturn(Optional.of(policy(DistributionMode.FORCED, RatingScale.ONE_TO_HUNDRED, null)));

        assertThatThrownBy(() -> service.simulate(cycleId, new DistributionSimulateRequest(null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.DISTRIBUTION_SCALE_NOT_SUPPORTED);
    }

    @Test
    void simulate_noTargetAndNoPolicyDistribution_invalidTarget() {
        // request target null + policy.forcedDistribution null → INVALID_TARGET.
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId)))
            .thenReturn(Optional.of(policy(DistributionMode.FORCED, RatingScale.S_A_B_C_D, null)));

        assertThatThrownBy(() -> service.simulate(cycleId, new DistributionSimulateRequest(null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.DISTRIBUTION_INVALID_TARGET);
    }

    // ═══════════════════════════ apply ═══════════════════════════

    @Test
    void apply_updatesFinalGradesAndUpsertsDistribution() {
        // N=2 CALIBRATION + 1 CALIBRATION null score (skip). target {S0.5,A0.5}.
        PerformanceReview r1 = review(uuid(1), ReviewStatus.CALIBRATION, new BigDecimal("90"), null);
        PerformanceReview r2 = review(uuid(2), ReviewStatus.CALIBRATION, new BigDecimal("80"), null);
        PerformanceReview rNull = review(uuid(3), ReviewStatus.CALIBRATION, null, null);
        List<PerformanceReview> all = List.of(r1, r2, rNull);
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId)))
            .thenReturn(Optional.of(policy(DistributionMode.FORCED, RatingScale.S_A_B_C_D, null)));
        when(reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(any(), eq(cycleId)))
            .thenReturn(all);
        when(reviewRepository.findByIdAndTenantId(eq(uuid(1)), any())).thenReturn(Optional.of(r1));
        when(reviewRepository.findByIdAndTenantId(eq(uuid(2)), any())).thenReturn(Optional.of(r2));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(distributionRepository.findByTenantIdAndCycleIdAndOrgUnitIdIsNull(any(), eq(cycleId)))
            .thenReturn(Optional.empty());
        when(distributionRepository.save(any(RatingDistribution.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID actor = UUID.randomUUID();

        DistributionApplyResponse response = service.apply(cycleId,
            new DistributionApplyRequest(Map.of("S", new BigDecimal("0.5"), "A", new BigDecimal("0.5")), actor));

        assertThat(response.appliedCount()).isEqualTo(2);
        assertThat(response.skippedCount()).isEqualTo(1); // null score CALIBRATION
        assertThat(response.resultingDistribution().get("S")).isEqualTo(1);
        assertThat(response.resultingDistribution().get("A")).isEqualTo(1);
        // 최고 점수 90 → S, 80 → A.
        assertThat(r1.getFinalGrade()).isEqualTo("S");
        assertThat(r2.getFinalGrade()).isEqualTo("A");

        // distribution upsert: forced_applied true + appliedBy + log append.
        ArgumentCaptor<RatingDistribution> captor = ArgumentCaptor.forClass(RatingDistribution.class);
        verify(distributionRepository).save(captor.capture());
        RatingDistribution saved = captor.getValue();
        assertThat(saved.isForcedApplied()).isTrue();
        assertThat(saved.getAppliedBy()).isEqualTo(actor);
        assertThat(saved.getActualDistribution()).contains("\"S\":1").contains("\"A\":1");
        assertThat(saved.getSimulationLog()).isNotNull(); // 1 entry append
    }

    @Test
    void apply_reapply_appendsSecondLogEntry() {
        // 기존 distribution 행에 simulation_log 1 entry 존재 → 재적용 시 2개.
        PerformanceReview r1 = review(uuid(1), ReviewStatus.CALIBRATION, new BigDecimal("90"), null);
        RatingDistribution existing = new RatingDistribution();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenantId);
        existing.setCycleId(cycleId);
        existing.setForcedApplied(true);
        existing.setSimulationLog("[{\"at\":\"2026-06-29T00:00:00Z\",\"actorEmployeeId\":null,"
            + "\"targetDistribution\":{\"S\":1.0},\"appliedCount\":1,\"skippedCount\":0,"
            + "\"resultingDistribution\":{\"S\":1}}]");
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId)))
            .thenReturn(Optional.of(policy(DistributionMode.FORCED, RatingScale.S_A_B_C_D, null)));
        when(reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(any(), eq(cycleId)))
            .thenReturn(List.of(r1));
        when(reviewRepository.findByIdAndTenantId(eq(uuid(1)), any())).thenReturn(Optional.of(r1));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(distributionRepository.findByTenantIdAndCycleIdAndOrgUnitIdIsNull(any(), eq(cycleId)))
            .thenReturn(Optional.of(existing));
        when(distributionRepository.save(any(RatingDistribution.class))).thenAnswer(inv -> inv.getArgument(0));

        service.apply(cycleId,
            new DistributionApplyRequest(Map.of("S", new BigDecimal("1.0")), UUID.randomUUID()));

        ArgumentCaptor<RatingDistribution> captor = ArgumentCaptor.forClass(RatingDistribution.class);
        verify(distributionRepository).save(captor.capture());
        // 재적용 → simulation_log 2 entry (append, 이력 보존 — 멱등 아님).
        assertThat(captor.getValue().getSimulationLog())
            .contains("2026-06-29T00:00:00Z")  // 기존 entry 보존
            .contains("2026-06-30");            // 신규 entry (FIXED clock)
    }

    @Test
    void apply_cycleNotCalibration_isRejected() {
        // §3: simulate/apply 는 cycle=CALIBRATION 한정 (stage 게이트가 policy 검증보다 먼저 발동).
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.MANAGER_REVIEW)));

        assertThatThrownBy(() -> service.apply(cycleId,
                new DistributionApplyRequest(Map.of("S", new BigDecimal("1.0")), null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CALIBRATION_CYCLE_STAGE_MISMATCH);
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

    private EvaluationPolicy policy(DistributionMode mode, RatingScale scale, String forcedDistribution) {
        EvaluationPolicy p = new EvaluationPolicy();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setCycleId(cycleId);
        p.setDistributionMode(mode);
        p.setRatingScale(scale);
        p.setAppealEnabled(false);
        p.setBscEnabled(false);
        p.setAchievementLogCutoffDays(3);
        p.setForcedDistribution(forcedDistribution);
        return p;
    }

    private CalibrationSession session(CalibrationStatus status) {
        CalibrationSession s = new CalibrationSession();
        s.setId(sessionId);
        s.setTenantId(tenantId);
        s.setCycleId(cycleId);
        s.setStatus(status);
        return s;
    }

    private PerformanceReview review(UUID id, ReviewStatus status, BigDecimal kpiScore, String finalGrade) {
        PerformanceReview r = new PerformanceReview();
        r.setId(id);
        r.setTenantId(tenantId);
        r.setCycleId(cycleId);
        r.setEmployeeId(UUID.randomUUID());
        r.setStatus(status);
        r.setKpiScore(kpiScore);
        r.setFinalGrade(finalGrade);
        return r;
    }
}

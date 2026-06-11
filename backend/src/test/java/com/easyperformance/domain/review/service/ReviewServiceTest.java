/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.review.service;

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
import java.util.Optional;
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
import com.easyperformance.domain.evaluationpolicy.entity.DistributionMode;
import com.easyperformance.domain.evaluationpolicy.entity.EvaluationPolicy;
import com.easyperformance.domain.evaluationpolicy.entity.RatingScale;
import com.easyperformance.domain.evaluationpolicy.repository.EvaluationPolicyRepository;
import com.easyperformance.domain.kpi.dto.KpiDtos.MyKpiAssignmentResponse;
import com.easyperformance.domain.kpi.entity.KpiNodeSource;
import com.easyperformance.domain.kpi.service.KpiService;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewBulkCreateRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewBulkCreateResponse;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewCreateRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewItemScoreInput;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewKpiItemResponse;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewResponse;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewSubmitManagerRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewSubmitSelfRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewTransitionRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewUpdateRequest;
import com.easyperformance.domain.review.entity.PerformanceReview;
import com.easyperformance.domain.review.entity.ReviewStatus;
import com.easyperformance.domain.review.repository.PerformanceReviewRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;

/**
 * ReviewService 단위 테스트 — P0-S3 (p0_s3_contract.md §3 상태기계 + §5 점수 산식 + §4 ErrorCode 10).
 *
 * <p>커버: 10 ErrorCode 전부 + happy path (create/bulk/transition 4/submit-self/submit-manager) +
 * §5 점수 산식 (autoScore clamp + managerScore merge + 가중 합산 + 비-NULL 항목만) + finalGrade 밴드 +
 * kpi-items live merge vs 동결 스냅샷 + 섹션 PATCH 가드 + delete DRAFT 한정.
 *
 * <p>TenantSupport fallback UUID (단계 1 단일 DB) — Mockito mock 으로 repository + KpiService 격리.
 * Clock 고정 (jobstructure/security 패턴).
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private PerformanceReviewRepository reviewRepository;
    @Mock private EvaluationCycleRepository cycleRepository;
    @Mock private EvaluationPolicyRepository policyRepository;
    @Mock private KpiService kpiService;

    private ReviewService service;

    private UUID tenantId;
    private UUID cycleId;
    private UUID reviewId;
    private UUID employeeId;
    private UUID assignmentA;
    private UUID assignmentB;

    private static final Clock FIXED =
        Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        cycleId = UUID.randomUUID();
        reviewId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        assignmentA = UUID.randomUUID();
        assignmentB = UUID.randomUUID();
        service = new ReviewService(reviewRepository, cycleRepository, policyRepository, kpiService, FIXED);
    }

    // ═══════════════════════════ 조회 ═══════════════════════════

    @Test
    void getReview_notFound_throws() {
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReview(reviewId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    void getMyReview_notFound_throws() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.SELF_REVIEW)));
        when(reviewRepository.findByTenantIdAndCycleIdAndEmployeeId(any(), eq(cycleId), eq(employeeId)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyReview(cycleId, employeeId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    void listReviews_employeeFilter_usesFilteredQuery() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.SELF_REVIEW)));
        when(reviewRepository.findAllByTenantIdAndCycleIdAndEmployeeIdOrderByCreatedAtAsc(
                any(), eq(cycleId), eq(employeeId)))
            .thenReturn(List.of(review(ReviewStatus.DRAFT)));

        List<ReviewResponse> result = service.listReviews(cycleId, employeeId);

        assertThat(result).hasSize(1);
        verify(reviewRepository).findAllByTenantIdAndCycleIdAndEmployeeIdOrderByCreatedAtAsc(any(), eq(cycleId), eq(employeeId));
        verify(reviewRepository, never()).findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(any(), any());
    }

    // ═══════════════════════════ 생성 ═══════════════════════════

    @Test
    void createReview_happy_statusDraft() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.GOAL_SETTING)));
        when(reviewRepository.existsByTenantIdAndCycleIdAndEmployeeId(any(), eq(cycleId), eq(employeeId)))
            .thenReturn(false);
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> {
            PerformanceReview r = inv.getArgument(0);
            if (r.getId() == null) r.setId(reviewId);
            return r;
        });

        ReviewResponse response = service.createReview(cycleId, new ReviewCreateRequest(employeeId));

        assertThat(response.status()).isEqualTo(ReviewStatus.DRAFT);
        assertThat(response.employeeId()).isEqualTo(employeeId);
        assertThat(response.cycleId()).isEqualTo(cycleId);
        assertThat(response.kpiScoreDetail()).isNull(); // submit-manager 전 null
    }

    @Test
    void createReview_duplicate_isRejected() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.GOAL_SETTING)));
        when(reviewRepository.existsByTenantIdAndCycleIdAndEmployeeId(any(), eq(cycleId), eq(employeeId)))
            .thenReturn(true);

        assertThatThrownBy(() -> service.createReview(cycleId, new ReviewCreateRequest(employeeId)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_DUPLICATE);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_cycleNotFound_throws() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createReview(cycleId, new ReviewCreateRequest(employeeId)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CYCLE_NOT_FOUND);
    }

    @Test
    void bulkCreate_skipsExisting_countsCorrectly() {
        UUID emp1 = UUID.randomUUID();
        UUID emp2 = UUID.randomUUID(); // 이미 존재 → skip
        UUID emp3 = UUID.randomUUID();
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.GOAL_SETTING)));
        when(reviewRepository.existsByTenantIdAndCycleIdAndEmployeeId(any(), eq(cycleId), eq(emp1))).thenReturn(false);
        when(reviewRepository.existsByTenantIdAndCycleIdAndEmployeeId(any(), eq(cycleId), eq(emp2))).thenReturn(true);
        when(reviewRepository.existsByTenantIdAndCycleIdAndEmployeeId(any(), eq(cycleId), eq(emp3))).thenReturn(false);
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> {
            PerformanceReview r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });

        ReviewBulkCreateResponse response = service.bulkCreate(cycleId,
            new ReviewBulkCreateRequest(List.of(emp1, emp2, emp3)));

        assertThat(response.createdCount()).isEqualTo(2);
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.created()).hasSize(2);
    }

    // ═══════════════════════════ transition (§3) ═══════════════════════════

    @Test
    void transition_draftToSelfPending_happy() {
        PerformanceReview review = review(ReviewStatus.DRAFT);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.SELF_REVIEW)));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.transition(reviewId,
            new ReviewTransitionRequest(ReviewStatus.SELF_PENDING, null));

        assertThat(response.status()).isEqualTo(ReviewStatus.SELF_PENDING);
    }

    @Test
    void transition_wrongCycleStage_isRejected() {
        PerformanceReview review = review(ReviewStatus.DRAFT);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        // cycle 이 GOAL_SETTING 인데 DRAFT→SELF_PENDING 은 SELF_REVIEW 요구.
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.GOAL_SETTING)));

        assertThatThrownBy(() -> service.transition(reviewId,
                new ReviewTransitionRequest(ReviewStatus.SELF_PENDING, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_CYCLE_STAGE_MISMATCH);
    }

    @Test
    void transition_illegalEdge_isRejected() {
        // DRAFT → CALIBRATION 은 §3 매트릭스에 없음.
        PerformanceReview review = review(ReviewStatus.DRAFT);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.transition(reviewId,
                new ReviewTransitionRequest(ReviewStatus.CALIBRATION, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_INVALID_STATUS_TRANSITION);
    }

    @Test
    void transition_submitViaTransition_isRejected() {
        // SELF_PENDING → SELF_SUBMITTED 는 submit-self 전용 — transition 으로 불가.
        PerformanceReview review = review(ReviewStatus.SELF_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.transition(reviewId,
                new ReviewTransitionRequest(ReviewStatus.SELF_SUBMITTED, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_INVALID_STATUS_TRANSITION);
    }

    @Test
    void transition_calibrationToFinalized_computesGradeAndStamps() {
        PerformanceReview review = review(ReviewStatus.CALIBRATION);
        review.setKpiScore(new BigDecimal("85.00")); // → grade A
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId)))
            .thenReturn(Optional.of(policy(RatingScale.S_A_B_C_D)));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID actor = UUID.randomUUID();

        ReviewResponse response = service.transition(reviewId,
            new ReviewTransitionRequest(ReviewStatus.FINALIZED, actor));

        assertThat(response.status()).isEqualTo(ReviewStatus.FINALIZED);
        assertThat(response.finalScore()).isEqualByComparingTo("85.00");
        assertThat(response.finalGrade()).isEqualTo("A");
        assertThat(response.finalizedBy()).isEqualTo(actor);
        assertThat(response.finalizedAt()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));
    }

    @Test
    void transition_finalizeWithNullKpiScore_isRejected() {
        PerformanceReview review = review(ReviewStatus.CALIBRATION);
        review.setKpiScore(null);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));

        assertThatThrownBy(() -> service.transition(reviewId,
                new ReviewTransitionRequest(ReviewStatus.FINALIZED, UUID.randomUUID())))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_SCORE_INCOMPLETE);
    }

    @Test
    void transition_finalize_nonSabcdScale_gradeNull() {
        PerformanceReview review = review(ReviewStatus.CALIBRATION);
        review.setKpiScore(new BigDecimal("85.00"));
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.CALIBRATION)));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId)))
            .thenReturn(Optional.of(policy(RatingScale.ONE_TO_HUNDRED)));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.transition(reviewId,
            new ReviewTransitionRequest(ReviewStatus.FINALIZED, UUID.randomUUID()));

        assertThat(response.finalScore()).isEqualByComparingTo("85.00");
        assertThat(response.finalGrade()).isNull(); // 비-S_A_B_C_D → 밴드 미적용
    }

    // ═══════════════════════════ submit-self ═══════════════════════════

    @Test
    void submitSelf_happy_savesCommentAndStatus() {
        PerformanceReview review = review(ReviewStatus.SELF_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.SELF_REVIEW)));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.submitSelf(reviewId, new ReviewSubmitSelfRequest("자기평가 완료"));

        assertThat(response.status()).isEqualTo(ReviewStatus.SELF_SUBMITTED);
        assertThat(response.selfComment()).isEqualTo("자기평가 완료");
    }

    @Test
    void submitSelf_wrongStatus_isRejected() {
        // DRAFT 에서 submit-self 불가.
        PerformanceReview review = review(ReviewStatus.DRAFT);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.submitSelf(reviewId, new ReviewSubmitSelfRequest("x")))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_INVALID_STATUS_TRANSITION);
    }

    @Test
    void submitSelf_wrongCycleStage_isRejected() {
        PerformanceReview review = review(ReviewStatus.SELF_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.MANAGER_REVIEW))); // SELF_REVIEW 요구

        assertThatThrownBy(() -> service.submitSelf(reviewId, new ReviewSubmitSelfRequest("x")))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_CYCLE_STAGE_MISMATCH);
    }

    // ═══════════════════════════ submit-manager (§5 점수 산식) ═══════════════════════════

    @Test
    void submitManager_computesWeightedKpiScoreAndFreezesSnapshot() {
        PerformanceReview review = review(ReviewStatus.MANAGER_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.MANAGER_REVIEW)));
        // assignmentA: weight 0.6, achievementRate 0.9 → autoScore 90, managerScore 80 → itemScore 80
        // assignmentB: weight 0.4, achievementRate 0.5 → autoScore 50, managerScore null → itemScore 50 (auto)
        // kpiScore = (80*0.6 + 50*0.4) / (0.6+0.4) = (48+20)/1.0 = 68.00
        stubAssignments(
            myAssignment(assignmentA, new BigDecimal("0.6"), new BigDecimal("0.9")),
            myAssignment(assignmentB, new BigDecimal("0.4"), new BigDecimal("0.5")));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.submitManager(reviewId, new ReviewSubmitManagerRequest(
            "매니저 코멘트",
            List.of(new ReviewItemScoreInput(assignmentA, new BigDecimal("80")),
                    new ReviewItemScoreInput(assignmentB, null))));

        assertThat(response.status()).isEqualTo(ReviewStatus.MANAGER_SUBMITTED);
        assertThat(response.kpiScore()).isEqualByComparingTo("68.00");
        assertThat(response.managerComment()).isEqualTo("매니저 코멘트");
        // 동결 스냅샷 노출.
        assertThat(response.kpiScoreDetail()).isNotNull().hasSize(2);
        ReviewKpiItemResponse itemA = response.kpiScoreDetail().stream()
            .filter(i -> i.assignmentId().equals(assignmentA)).findFirst().orElseThrow();
        assertThat(itemA.autoScore()).isEqualByComparingTo("90.00");
        assertThat(itemA.managerScore()).isEqualByComparingTo("80");
        assertThat(itemA.itemScore()).isEqualByComparingTo("80");
        ReviewKpiItemResponse itemB = response.kpiScoreDetail().stream()
            .filter(i -> i.assignmentId().equals(assignmentB)).findFirst().orElseThrow();
        assertThat(itemB.managerScore()).isNull();
        assertThat(itemB.itemScore()).isEqualByComparingTo("50.00"); // auto 폴백
    }

    @Test
    void submitManager_allItemScoresNull_kpiScoreFromAutoOnly() {
        // managerScore 전부 null + achievementRate 둘 다 존재 → autoScore 로 산출.
        PerformanceReview review = review(ReviewStatus.MANAGER_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.MANAGER_REVIEW)));
        // A: w0.5 ar1.0 → auto100 ; B: w0.5 ar0.6 → auto60 ; kpiScore=(100*.5+60*.5)/1.0=80.00
        stubAssignments(
            myAssignment(assignmentA, new BigDecimal("0.5"), new BigDecimal("1.0")),
            myAssignment(assignmentB, new BigDecimal("0.5"), new BigDecimal("0.6")));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.submitManager(reviewId, new ReviewSubmitManagerRequest(
            null,
            List.of(new ReviewItemScoreInput(assignmentA, null),
                    new ReviewItemScoreInput(assignmentB, null))));

        assertThat(response.kpiScore()).isEqualByComparingTo("80.00");
    }

    @Test
    void submitManager_nullAchievementSkippedFromKpiScore() {
        // A: ar null + managerScore null → itemScore null → 제외 ; B: ar0.8 manager null → auto80 포함
        // kpiScore = (80*0.4)/(0.4) = 80.00 (A 제외, 분모에서도 빠짐)
        PerformanceReview review = review(ReviewStatus.MANAGER_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.MANAGER_REVIEW)));
        stubAssignments(
            myAssignment(assignmentA, new BigDecimal("0.6"), null),
            myAssignment(assignmentB, new BigDecimal("0.4"), new BigDecimal("0.8")));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.submitManager(reviewId, new ReviewSubmitManagerRequest(
            null,
            List.of(new ReviewItemScoreInput(assignmentA, null),
                    new ReviewItemScoreInput(assignmentB, null))));

        assertThat(response.kpiScore()).isEqualByComparingTo("80.00");
    }

    @Test
    void submitManager_autoScoreClampsAbove100() {
        // achievementRate 1.5 → raw 150 → clamp 100. managerScore null → itemScore 100.
        PerformanceReview review = review(ReviewStatus.MANAGER_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.MANAGER_REVIEW)));
        stubAssignments(myAssignment(assignmentA, new BigDecimal("1.0"), new BigDecimal("1.5")));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.submitManager(reviewId, new ReviewSubmitManagerRequest(
            null, List.of(new ReviewItemScoreInput(assignmentA, null))));

        assertThat(response.kpiScore()).isEqualByComparingTo("100.00");
        ReviewKpiItemResponse item = response.kpiScoreDetail().get(0);
        assertThat(item.autoScore()).isEqualByComparingTo("100.00");
    }

    @Test
    void submitManager_managerScoreOutOfRange_isRejected() {
        PerformanceReview review = review(ReviewStatus.MANAGER_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.MANAGER_REVIEW)));
        stubAssignments(myAssignment(assignmentA, new BigDecimal("1.0"), new BigDecimal("0.9")));

        assertThatThrownBy(() -> service.submitManager(reviewId, new ReviewSubmitManagerRequest(
                null, List.of(new ReviewItemScoreInput(assignmentA, new BigDecimal("120"))))))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_SCORE_OUT_OF_RANGE);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitManager_assignmentNotInCycleEmployee_isRejected() {
        // 입력 assignmentId 가 listMyAssignments 에 없음 → mismatch.
        PerformanceReview review = review(ReviewStatus.MANAGER_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.MANAGER_REVIEW)));
        stubAssignments(myAssignment(assignmentA, new BigDecimal("1.0"), new BigDecimal("0.9")));
        UUID foreign = UUID.randomUUID();

        assertThatThrownBy(() -> service.submitManager(reviewId, new ReviewSubmitManagerRequest(
                null, List.of(new ReviewItemScoreInput(foreign, new BigDecimal("80"))))))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_ITEM_ASSIGNMENT_MISMATCH);
    }

    @Test
    void submitManager_wrongStatus_isRejected() {
        PerformanceReview review = review(ReviewStatus.SELF_SUBMITTED); // MANAGER_PENDING 아님
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.submitManager(reviewId, new ReviewSubmitManagerRequest(
                null, List.of())))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_INVALID_STATUS_TRANSITION);
    }

    // ═══════════════════════════ PATCH 섹션 가드 ═══════════════════════════

    @Test
    void updateReview_selfPendingWithSelfComment_succeeds() {
        PerformanceReview review = review(ReviewStatus.SELF_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.updateReview(reviewId,
            new ReviewUpdateRequest("초안 코멘트", null, null));

        assertThat(response.selfComment()).isEqualTo("초안 코멘트");
        assertThat(response.status()).isEqualTo(ReviewStatus.SELF_PENDING);
    }

    @Test
    void updateReview_selfPendingWithManagerComment_isRejected() {
        PerformanceReview review = review(ReviewStatus.SELF_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.updateReview(reviewId,
                new ReviewUpdateRequest(null, "매니저가 침범", null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_SECTION_NOT_EDITABLE);
    }

    @Test
    void updateReview_managerPendingWithSelfComment_isRejected() {
        PerformanceReview review = review(ReviewStatus.MANAGER_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.updateReview(reviewId,
                new ReviewUpdateRequest("자기평가 침범", null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_SECTION_NOT_EDITABLE);
    }

    @Test
    void updateReview_managerPendingDraftItemScores_keepsKpiScoreNull() {
        // PATCH 는 cycle 단계 게이트 없음 (submit/transition 만 게이트) — assignment 소속 검증만 cycle 무관.
        PerformanceReview review = review(ReviewStatus.MANAGER_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        stubAssignments(myAssignment(assignmentA, new BigDecimal("1.0"), new BigDecimal("0.9")));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.updateReview(reviewId, new ReviewUpdateRequest(
            null, "진행중 메모", List.of(new ReviewItemScoreInput(assignmentA, new BigDecimal("75")))));

        assertThat(response.managerComment()).isEqualTo("진행중 메모");
        assertThat(response.kpiScore()).isNull();          // draft 만 — kpiScore 미산출
        assertThat(response.kpiScoreDetail()).isNull();    // MANAGER_PENDING — 동결 전이라 미노출
        // 저장된 draft 가 kpi_score_detail 컬럼에 쓰였는지 (merge 용).
        ArgumentCaptor<PerformanceReview> captor = ArgumentCaptor.forClass(PerformanceReview.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getKpiScoreDetail()).contains(assignmentA.toString()).contains("75");
    }

    @Test
    void updateReview_draftStatus_isLocked() {
        PerformanceReview review = review(ReviewStatus.DRAFT);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.updateReview(reviewId,
                new ReviewUpdateRequest("x", null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_LOCKED);
    }

    @Test
    void updateReview_finalizedStatus_isLocked() {
        PerformanceReview review = review(ReviewStatus.FINALIZED);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.updateReview(reviewId,
                new ReviewUpdateRequest("x", null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_LOCKED);
    }

    // ═══════════════════════════ kpi-items (live merge vs 동결) ═══════════════════════════

    @Test
    void getKpiItems_managerPending_liveWithDraftMerge() {
        // MANAGER_PENDING 에 draft managerScore 저장돼 있음 → live 계산 + merge.
        PerformanceReview review = review(ReviewStatus.MANAGER_PENDING);
        review.setKpiScoreDetail("[{\"assignmentId\":\"" + assignmentA + "\",\"managerScore\":77}]");
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));
        stubAssignments(myAssignment(assignmentA, new BigDecimal("1.0"), new BigDecimal("0.9")));

        List<ReviewKpiItemResponse> items = service.getKpiItems(reviewId);

        assertThat(items).hasSize(1);
        ReviewKpiItemResponse item = items.get(0);
        assertThat(item.autoScore()).isEqualByComparingTo("90.00");   // live ar0.9 → 90
        assertThat(item.managerScore()).isEqualByComparingTo("77");    // draft merge
        assertThat(item.itemScore()).isEqualByComparingTo("77");       // manager 우선
    }

    @Test
    void getKpiItems_frozen_returnsStoredSnapshot() {
        // MANAGER_SUBMITTED 동결 — live 미계산, 저장 스냅샷 그대로.
        PerformanceReview review = review(ReviewStatus.FINALIZED);
        review.setKpiScoreDetail("[{\"assignmentId\":\"" + assignmentA + "\",\"nodeLabel\":\"매출\","
            + "\"treeName\":\"팀\",\"weight\":1.0,\"target\":100,\"unit\":\"건\","
            + "\"latestActualValue\":90,\"achievementRate\":0.9,\"autoScore\":90.00,"
            + "\"managerScore\":88,\"itemScore\":88}]");
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        List<ReviewKpiItemResponse> items = service.getKpiItems(reviewId);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).itemScore()).isEqualByComparingTo("88");
        assertThat(items.get(0).nodeLabel()).isEqualTo("매출");
        // KpiService.listMyAssignments 호출 안 됨 (동결 우선).
        verify(kpiService, never()).listMyAssignments(any(), any());
    }

    // ═══════════════════════════ delete ═══════════════════════════

    @Test
    void deleteReview_draft_succeeds() {
        PerformanceReview review = review(ReviewStatus.DRAFT);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        service.deleteReview(reviewId);

        verify(reviewRepository).delete(review);
    }

    @Test
    void deleteReview_nonDraft_isRejected() {
        PerformanceReview review = review(ReviewStatus.SELF_PENDING);
        when(reviewRepository.findByIdAndTenantId(eq(reviewId), any())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.deleteReview(reviewId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.REVIEW_CANNOT_DELETE);
        verify(reviewRepository, never()).delete(any());
    }

    // ═══════════════════════════ fixtures ═══════════════════════════

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

    private EvaluationPolicy policy(RatingScale scale) {
        EvaluationPolicy p = new EvaluationPolicy();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setCycleId(cycleId);
        p.setDistributionMode(DistributionMode.HYBRID);
        p.setRatingScale(scale);
        p.setAppealEnabled(false);
        p.setBscEnabled(false);
        p.setAchievementLogCutoffDays(3);
        return p;
    }

    private PerformanceReview review(ReviewStatus status) {
        PerformanceReview r = new PerformanceReview();
        r.setId(reviewId);
        r.setTenantId(tenantId);
        r.setCycleId(cycleId);
        r.setEmployeeId(employeeId);
        r.setStatus(status);
        return r;
    }

    private MyKpiAssignmentResponse myAssignment(UUID assignmentId, BigDecimal weight, BigDecimal achievementRate) {
        return new MyKpiAssignmentResponse(
            assignmentId,
            UUID.randomUUID(),       // kpiNodeId
            "Node",                  // nodeLabel
            UUID.randomUUID(),       // treeId
            "Tree",                  // treeName
            cycleId,
            weight,                  // effective weight
            new BigDecimal("100"),   // effective target
            "건",                    // unit
            null,                    // bscPerspective
            KpiNodeSource.MANUAL,
            new BigDecimal("90"),    // latestActualValue
            LocalDate.of(2026, 3, 31),
            achievementRate
        );
    }

    /** KpiService.listMyAssignments 스텁 (lenient — kpi-items/validate/score 경로 공용). */
    private void stubAssignments(MyKpiAssignmentResponse... assignments) {
        lenient().when(kpiService.listMyAssignments(eq(cycleId), eq(employeeId)))
            .thenReturn(List.of(assignments));
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.review.service;

import com.easyperformance.common.TenantSupport;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.evaluationpolicy.entity.EvaluationPolicy;
import com.easyperformance.domain.evaluationpolicy.entity.RatingScale;
import com.easyperformance.domain.evaluationpolicy.repository.EvaluationPolicyRepository;
import com.easyperformance.domain.kpi.dto.KpiDtos.MyKpiAssignmentResponse;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 성과 평가 도메인 Service — 비즈니스 SSOT (PerformanceReview). P0-S3 (p0_s3_contract.md §3/§5).
 *
 * <p>테넌트 격리: {@link TenantSupport#currentTenantId()} 위임 — 모든 쿼리에 tenant_id 필수
 * (easy-ware 규칙 #10).
 *
 * <p>핵심 책임 (BE 가 유일 점수 계산자 §5):
 * <ul>
 *   <li>10단계 상태기계 — P0-S3 transition 4 + submit 전용 2 (§3 매트릭스, 그 외 전이 REVIEW_INVALID_STATUS_TRANSITION).</li>
 *   <li>cycle 단계 게이트 — 전이/submit 마다 cycle.status 요구값 검증 (REVIEW_CYCLE_STAGE_MISMATCH).</li>
 *   <li>KPI 자동 점수 — autoScore = clamp(round(achievementRate×100,2),0,100) / itemScore = managerScore ?? autoScore /
 *       kpiScore = Σ(itemScore×effWeight)/Σ(effWeight) (itemScore 비-NULL 항목만), submit-manager 에서 동결 스냅샷.</li>
 *   <li>섹션 PATCH 가드 — SELF_PENDING=selfComment / MANAGER_PENDING=managerComment+itemScores 만 (REVIEW_SECTION_NOT_EDITABLE),
 *       DRAFT·종결 상태 = REVIEW_LOCKED.</li>
 *   <li>finalGrade 밴드 — policy.ratingScale==S_A_B_C_D 일 때만 (S≥90/A≥80/B≥70/C≥60/D&lt;60).</li>
 * </ul>
 *
 * <p>{@code kpi_score_detail} jsonb 매핑: lib OutboxEvent 패턴 (P0-S1 D2) — ObjectMapper
 * {@code USE_BIG_DECIMAL_FOR_FLOATS} 로 BigDecimal 정밀도 보존. MANAGER_PENDING 동안엔 draft managerScore
 * 만 동일 컬럼에 부분 배열로 보관 (kpi-items merge 용) + Response 노출은 안 함 (submit-manager 전 null);
 * submit-manager 에서 풀 스냅샷 동결.
 */
@Service
public class ReviewService {

    /** 점수 산식 round scale (소수 2자리). */
    private static final int SCORE_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** PATCH/submit 후 불변 — 어떤 섹션도 수정 불가 (종결·제출 상태). */
    private static final Set<ReviewStatus> LOCKED_FOR_PATCH = EnumSet.of(
        ReviewStatus.DRAFT,
        ReviewStatus.SELF_SUBMITTED,
        ReviewStatus.MANAGER_SUBMITTED,
        ReviewStatus.CALIBRATION,
        ReviewStatus.FINALIZED,
        ReviewStatus.APPEAL_REQUESTED,
        ReviewStatus.APPEAL_RESOLVED,
        ReviewStatus.ARCHIVED);

    /** kpiScoreDetail 스냅샷을 Response 에 노출하는 상태 (동결 이후). */
    private static final Set<ReviewStatus> SNAPSHOT_FROZEN = EnumSet.of(
        ReviewStatus.MANAGER_SUBMITTED,
        ReviewStatus.CALIBRATION,
        ReviewStatus.FINALIZED,
        ReviewStatus.APPEAL_REQUESTED,
        ReviewStatus.APPEAL_RESOLVED,
        ReviewStatus.ARCHIVED);

    private final PerformanceReviewRepository reviewRepository;
    private final EvaluationCycleRepository cycleRepository;
    private final EvaluationPolicyRepository policyRepository;
    private final KpiService kpiService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** Spring 주입용 기본 생성자 — 이중 생성자(@Autowired 미지정)는 부팅 실패 (2026-06-12 PERFDEV 첫 실부팅 실측). Clock 생성자는 테스트 전용. */
    @Autowired
    public ReviewService(PerformanceReviewRepository reviewRepository,
                         EvaluationCycleRepository cycleRepository,
                         EvaluationPolicyRepository policyRepository,
                         KpiService kpiService) {
        this(reviewRepository, cycleRepository, policyRepository, kpiService, Clock.systemUTC());
    }

    /** 테스트 친화 — Clock 주입 (jobstructure/security 패턴 정합). */
    public ReviewService(PerformanceReviewRepository reviewRepository,
                         EvaluationCycleRepository cycleRepository,
                         EvaluationPolicyRepository policyRepository,
                         KpiService kpiService,
                         Clock clock) {
        this.reviewRepository = reviewRepository;
        this.cycleRepository = cycleRepository;
        this.policyRepository = policyRepository;
        this.kpiService = kpiService;
        this.objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.clock = clock;
    }

    // ═════════════════════════════════════════════════════════════════════
    // 조회
    // ═════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<ReviewResponse> listReviews(UUID cycleId, UUID employeeId) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireCycle(cycleId, tenantId);
        List<PerformanceReview> reviews = (employeeId == null)
            ? reviewRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId)
            : reviewRepository.findAllByTenantIdAndCycleIdAndEmployeeIdOrderByCreatedAtAsc(tenantId, cycleId, employeeId);
        return reviews.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ReviewResponse getReview(UUID reviewId) {
        UUID tenantId = TenantSupport.currentTenantId();
        return toResponse(requireReview(reviewId, tenantId));
    }

    @Transactional(readOnly = true)
    public ReviewResponse getMyReview(UUID cycleId, UUID employeeId) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireCycle(cycleId, tenantId);
        PerformanceReview review = reviewRepository
            .findByTenantIdAndCycleIdAndEmployeeId(tenantId, cycleId, employeeId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REVIEW_NOT_FOUND,
                Map.of("entity", "PerformanceReview", "cycleId", cycleId, "employeeId", employeeId)));
        return toResponse(review);
    }

    /**
     * KPI 항목 (폼 렌더용) — 항상 live 계산 (assignment 현재값) + 저장된 managerScore 를 assignmentId 로 merge.
     * 단 동결 이후(MANAGER_SUBMITTED~)엔 저장 스냅샷을 그대로 반환 (동결 우선).
     */
    @Transactional(readOnly = true)
    public List<ReviewKpiItemResponse> getKpiItems(UUID reviewId) {
        UUID tenantId = TenantSupport.currentTenantId();
        PerformanceReview review = requireReview(reviewId, tenantId);

        // 동결 이후엔 저장 스냅샷 그대로.
        if (SNAPSHOT_FROZEN.contains(review.getStatus())) {
            return parseSnapshot(review.getKpiScoreDetail());
        }

        // live 계산 + draft managerScore merge.
        Map<UUID, BigDecimal> draftScores = parseDraftManagerScores(review.getKpiScoreDetail());
        return buildLiveItems(review.getCycleId(), review.getEmployeeId(), draftScores);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 생성
    // ═════════════════════════════════════════════════════════════════════

    @Transactional
    public ReviewResponse createReview(UUID cycleId, ReviewCreateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireCycle(cycleId, tenantId);
        if (reviewRepository.existsByTenantIdAndCycleIdAndEmployeeId(tenantId, cycleId, request.employeeId())) {
            throw new ApiException(PerformanceErrorCode.REVIEW_DUPLICATE,
                Map.of("cycleId", cycleId, "employeeId", request.employeeId()));
        }
        PerformanceReview review = newReview(tenantId, cycleId, request.employeeId());
        return toResponse(reviewRepository.save(review));
    }

    /** 일괄 생성 — 기존 (cycle×employee) 는 skip (에러 아님). createdCount/skippedCount 응답. */
    @Transactional
    public ReviewBulkCreateResponse bulkCreate(UUID cycleId, ReviewBulkCreateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireCycle(cycleId, tenantId);

        List<ReviewResponse> created = new ArrayList<>();
        int skipped = 0;
        for (UUID employeeId : request.employeeIds()) {
            if (employeeId == null
                || reviewRepository.existsByTenantIdAndCycleIdAndEmployeeId(tenantId, cycleId, employeeId)) {
                skipped++;
                continue;
            }
            PerformanceReview review = reviewRepository.save(newReview(tenantId, cycleId, employeeId));
            created.add(toResponse(review));
        }
        return new ReviewBulkCreateResponse(created.size(), skipped, created);
    }

    // ═════════════════════════════════════════════════════════════════════
    // PATCH (섹션 임시저장)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * 섹션 임시저장. 가드 (§6):
     * <ul>
     *   <li>SELF_PENDING — selfComment 만 허용 (managerComment/itemScores 제공 시 REVIEW_SECTION_NOT_EDITABLE).</li>
     *   <li>MANAGER_PENDING — managerComment + itemScores 만 허용 (selfComment 제공 시 REVIEW_SECTION_NOT_EDITABLE).
     *       itemScores 는 draft 저장만 (kpiScore NULL 유지).</li>
     *   <li>그 외 (DRAFT·종결·제출 상태) — REVIEW_LOCKED.</li>
     * </ul>
     */
    @Transactional
    public ReviewResponse updateReview(UUID reviewId, ReviewUpdateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        PerformanceReview review = requireReview(reviewId, tenantId);
        ReviewStatus status = review.getStatus();

        if (status == ReviewStatus.SELF_PENDING) {
            // selfComment 만 허용.
            if (request.managerComment() != null || (request.itemScores() != null && !request.itemScores().isEmpty())) {
                throw sectionNotEditable(status, "self");
            }
            if (request.selfComment() != null) {
                review.setSelfComment(request.selfComment());
            }
        } else if (status == ReviewStatus.MANAGER_PENDING) {
            // managerComment + itemScores 만 허용.
            if (request.selfComment() != null) {
                throw sectionNotEditable(status, "manager");
            }
            if (request.managerComment() != null) {
                review.setManagerComment(request.managerComment());
            }
            if (request.itemScores() != null) {
                validateItemScores(review.getCycleId(), review.getEmployeeId(), request.itemScores());
                // draft 저장만 — kpiScore 는 NULL 유지 (submit-manager 에서 산출·동결).
                review.setKpiScoreDetail(serializeDraftManagerScores(request.itemScores()));
            }
        } else {
            // DRAFT 와 종결/제출 상태 일체 PATCH 불가.
            throw new ApiException(PerformanceErrorCode.REVIEW_LOCKED,
                Map.of("reviewId", reviewId, "status", status.name(), "operation", "patch"));
        }

        return toResponse(reviewRepository.save(review));
    }

    // ═════════════════════════════════════════════════════════════════════
    // submit-self / submit-manager (전용 endpoint)
    // ═════════════════════════════════════════════════════════════════════

    /** 자기평가 제출 — SELF_PENDING → SELF_SUBMITTED (cycle=SELF_REVIEW). selfComment 저장. */
    @Transactional
    public ReviewResponse submitSelf(UUID reviewId, ReviewSubmitSelfRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        PerformanceReview review = requireReview(reviewId, tenantId);

        if (review.getStatus() != ReviewStatus.SELF_PENDING) {
            throw invalidTransition(review.getStatus(), ReviewStatus.SELF_SUBMITTED, "submit-self");
        }
        requireCycleStage(review.getCycleId(), tenantId, CycleStatus.SELF_REVIEW);

        if (request.selfComment() != null) {
            review.setSelfComment(request.selfComment());
        }
        review.setStatus(ReviewStatus.SELF_SUBMITTED);
        return toResponse(reviewRepository.save(review));
    }

    /**
     * 매니저 평가 제출 — MANAGER_PENDING → MANAGER_SUBMITTED (cycle=MANAGER_REVIEW). §5 스냅샷 +
     * kpiScore 산출·동결. itemScores 의 managerScore 로 itemScore 계산 후 가중 합산.
     */
    @Transactional
    public ReviewResponse submitManager(UUID reviewId, ReviewSubmitManagerRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        PerformanceReview review = requireReview(reviewId, tenantId);

        if (review.getStatus() != ReviewStatus.MANAGER_PENDING) {
            throw invalidTransition(review.getStatus(), ReviewStatus.MANAGER_SUBMITTED, "submit-manager");
        }
        requireCycleStage(review.getCycleId(), tenantId, CycleStatus.MANAGER_REVIEW);
        validateItemScores(review.getCycleId(), review.getEmployeeId(), request.itemScores());

        // §5 스냅샷 산출 — live 계산 + managerScore merge → itemScore → 가중 합산.
        Map<UUID, BigDecimal> managerScores = toScoreMap(request.itemScores());
        List<ReviewKpiItemResponse> snapshot =
            buildLiveItems(review.getCycleId(), review.getEmployeeId(), managerScores);
        BigDecimal kpiScore = computeKpiScore(review.getCycleId(), review.getEmployeeId(), managerScores);

        if (request.managerComment() != null) {
            review.setManagerComment(request.managerComment());
        }
        review.setKpiScore(kpiScore);
        review.setKpiScoreDetail(serializeSnapshot(snapshot));
        review.setStatus(ReviewStatus.MANAGER_SUBMITTED);
        return toResponse(reviewRepository.save(review));
    }

    // ═════════════════════════════════════════════════════════════════════
    // transition (§3 매트릭스)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * 상태 전이 — §3 매트릭스 4개만 (submit 전이는 전용 endpoint 라 여기선 REVIEW_INVALID_STATUS_TRANSITION).
     * <pre>
     *   DRAFT             → SELF_PENDING       (cycle=SELF_REVIEW)
     *   SELF_SUBMITTED    → MANAGER_PENDING    (cycle=MANAGER_REVIEW)
     *   MANAGER_SUBMITTED → CALIBRATION        (cycle=CALIBRATION)
     *   CALIBRATION       → FINALIZED          (cycle=CALIBRATION, finalScore/finalGrade 산출 + kpiScore NULL 거부)
     * </pre>
     */
    @Transactional
    public ReviewResponse transition(UUID reviewId, ReviewTransitionRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        PerformanceReview review = requireReview(reviewId, tenantId);
        ReviewStatus from = review.getStatus();
        ReviewStatus to = request.targetStatus();

        if (from == ReviewStatus.DRAFT && to == ReviewStatus.SELF_PENDING) {
            requireCycleStage(review.getCycleId(), tenantId, CycleStatus.SELF_REVIEW);
            review.setStatus(to);
        } else if (from == ReviewStatus.SELF_SUBMITTED && to == ReviewStatus.MANAGER_PENDING) {
            requireCycleStage(review.getCycleId(), tenantId, CycleStatus.MANAGER_REVIEW);
            review.setStatus(to);
        } else if (from == ReviewStatus.MANAGER_SUBMITTED && to == ReviewStatus.CALIBRATION) {
            requireCycleStage(review.getCycleId(), tenantId, CycleStatus.CALIBRATION);
            review.setStatus(to);
        } else if (from == ReviewStatus.CALIBRATION && to == ReviewStatus.FINALIZED) {
            requireCycleStage(review.getCycleId(), tenantId, CycleStatus.CALIBRATION);
            finalizeReview(tenantId, review, request.actorEmployeeId());
        } else {
            throw invalidTransition(from, to, "transition");
        }

        return toResponse(reviewRepository.save(review));
    }

    /**
     * 확정 — finalScore = kpiScore (P0 단순, MBO 등 P1 가중 확장 박제) + finalGrade = §5 밴드 +
     * finalizedAt = now + finalizedBy = actorEmployeeId. kpiScore NULL 이면 REVIEW_SCORE_INCOMPLETE 거부.
     *
     * <p><strong>P0-S4 보강 (p0_s4_contract.md §5)</strong>: 캘리브레이션 조정/강제 배분 결과 보존 —
     * 기존 {@code finalGrade} 가 이미 설정돼 있으면 그대로 유지, null 일 때만 policy 기반 밴드 산출
     * ({@link #computeFinalGrade}). 기존 P0-S3 테스트 (null → 밴드) 는 그대로 통과.
     */
    private void finalizeReview(UUID tenantId, PerformanceReview review, UUID actorEmployeeId) {
        if (review.getKpiScore() == null) {
            throw new ApiException(PerformanceErrorCode.REVIEW_SCORE_INCOMPLETE,
                Map.of("reviewId", review.getId(), "reason", "kpiScore is null"));
        }
        BigDecimal finalScore = review.getKpiScore();
        review.setFinalScore(finalScore);
        String existingGrade = review.getFinalGrade();
        review.setFinalGrade(existingGrade != null
            ? existingGrade
            : computeFinalGrade(tenantId, review.getCycleId(), finalScore));
        review.setFinalizedAt(Instant.now(clock));
        review.setFinalizedBy(actorEmployeeId);
        review.setStatus(ReviewStatus.FINALIZED);
    }

    // ═════════════════════════════════════════════════════════════════════
    // delete
    // ═════════════════════════════════════════════════════════════════════

    /** 삭제 — DRAFT 한정 (그 외 REVIEW_CANNOT_DELETE 409). */
    @Transactional
    public void deleteReview(UUID reviewId) {
        UUID tenantId = TenantSupport.currentTenantId();
        PerformanceReview review = requireReview(reviewId, tenantId);
        if (review.getStatus() != ReviewStatus.DRAFT) {
            throw new ApiException(PerformanceErrorCode.REVIEW_CANNOT_DELETE,
                Map.of("reviewId", reviewId, "status", review.getStatus().name()));
        }
        reviewRepository.delete(review);
    }

    // ═════════════════════════════════════════════════════════════════════
    // §5 점수 산식 (BE 유일 계산자)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * live KPI 항목 빌드 — KpiService.listMyAssignments (cross-package public, P0-S1 D1 함정 회피) +
     * autoScore/managerScore/itemScore 계산. {@code managerScores} 는 assignmentId → managerScore.
     */
    private List<ReviewKpiItemResponse> buildLiveItems(UUID cycleId, UUID employeeId,
                                                       Map<UUID, BigDecimal> managerScores) {
        List<MyKpiAssignmentResponse> assignments = kpiService.listMyAssignments(cycleId, employeeId);
        List<ReviewKpiItemResponse> result = new ArrayList<>(assignments.size());
        for (MyKpiAssignmentResponse a : assignments) {
            BigDecimal autoScore = computeAutoScore(a.achievementRate());
            BigDecimal managerScore = managerScores.get(a.id());
            BigDecimal itemScore = managerScore != null ? managerScore : autoScore;
            result.add(new ReviewKpiItemResponse(
                a.id(),
                a.nodeLabel(),
                a.treeName(),
                a.weight(),
                a.target(),
                a.unit(),
                a.latestActualValue(),
                a.achievementRate(),
                autoScore,
                managerScore,
                itemScore
            ));
        }
        return result;
    }

    /**
     * kpiScore = Σ(itemScore × effectiveWeight) / Σ(effectiveWeight) — itemScore 비-NULL 항목만 분자·분모
     * 포함 (점진 입력 허용). 비-NULL 항목 0개면 NULL. round 2.
     */
    private BigDecimal computeKpiScore(UUID cycleId, UUID employeeId, Map<UUID, BigDecimal> managerScores) {
        List<MyKpiAssignmentResponse> assignments = kpiService.listMyAssignments(cycleId, employeeId);
        BigDecimal numerator = ZERO;
        BigDecimal denominator = ZERO;
        for (MyKpiAssignmentResponse a : assignments) {
            BigDecimal autoScore = computeAutoScore(a.achievementRate());
            BigDecimal managerScore = managerScores.get(a.id());
            BigDecimal itemScore = managerScore != null ? managerScore : autoScore;
            if (itemScore == null) {
                continue; // 비-NULL 항목만 포함.
            }
            // effectiveWeight 는 P0-S2 계약상 NOT NULL (node.weight NOT NULL). 방어적 null skip.
            BigDecimal weight = a.weight();
            if (weight == null) {
                continue;
            }
            numerator = numerator.add(itemScore.multiply(weight));
            denominator = denominator.add(weight);
        }
        if (denominator.compareTo(ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, SCORE_SCALE, RoundingMode.HALF_UP);
    }

    /** autoScore = clamp(round(achievementRate × 100, 2), 0, 100). achievementRate null → null. */
    private BigDecimal computeAutoScore(BigDecimal achievementRate) {
        if (achievementRate == null) {
            return null;
        }
        BigDecimal scaled = achievementRate.multiply(HUNDRED).setScale(SCORE_SCALE, RoundingMode.HALF_UP);
        if (scaled.compareTo(ZERO) < 0) {
            return ZERO.setScale(SCORE_SCALE);
        }
        if (scaled.compareTo(HUNDRED) > 0) {
            return HUNDRED.setScale(SCORE_SCALE);
        }
        return scaled;
    }

    /**
     * finalGrade 밴드 — policy.ratingScale == S_A_B_C_D 일 때만 (S≥90/A≥80/B≥70/C≥60/D&lt;60).
     * 다른 scale 또는 policy 부재 또는 score null 은 null.
     */
    private String computeFinalGrade(UUID tenantId, UUID cycleId, BigDecimal finalScore) {
        EvaluationPolicy policy = policyRepository.findByTenantIdAndCycleId(tenantId, cycleId).orElse(null);
        if (policy == null || policy.getRatingScale() != RatingScale.S_A_B_C_D) {
            return null;
        }
        return bandGrade(finalScore);
    }

    /**
     * 점수 → 등급 밴드 (S≥90/A≥80/B≥70/C≥60/D&lt;60) — policy 무관 순수 산식. {@code score} null → null.
     *
     * <p><strong>BE 유일 밴드 계산자 (p0_s4_contract.md §5)</strong>: 캘리브레이션 effectiveGrade
     * ({@code finalGrade ?? bandGrade(kpiScore)}) 가 이 메서드를 cross-package public 재사용
     * (중복 구현 금지 — P0-S1 D1 함정 회피). {@link #computeFinalGrade} 도 policy 게이트 후 이 메서드 위임.
     */
    public String bandGrade(BigDecimal score) {
        if (score == null) {
            return null;
        }
        if (score.compareTo(new BigDecimal("90")) >= 0) return "S";
        if (score.compareTo(new BigDecimal("80")) >= 0) return "A";
        if (score.compareTo(new BigDecimal("70")) >= 0) return "B";
        if (score.compareTo(new BigDecimal("60")) >= 0) return "C";
        return "D";
    }

    // ═════════════════════════════════════════════════════════════════════
    // 검증 헬퍼
    // ═════════════════════════════════════════════════════════════════════

    /**
     * itemScores 검증 — (1) assignmentId 가 해당 (cycle×employee) 소속 assignment 인지
     * (REVIEW_ITEM_ASSIGNMENT_MISMATCH) (2) managerScore ∈ [0,100] (REVIEW_SCORE_OUT_OF_RANGE, null 허용).
     */
    private void validateItemScores(UUID cycleId, UUID employeeId, List<ReviewItemScoreInput> itemScores) {
        if (itemScores == null || itemScores.isEmpty()) {
            return;
        }
        Set<UUID> validAssignmentIds = new java.util.HashSet<>();
        for (MyKpiAssignmentResponse a : kpiService.listMyAssignments(cycleId, employeeId)) {
            validAssignmentIds.add(a.id());
        }
        for (ReviewItemScoreInput input : itemScores) {
            if (!validAssignmentIds.contains(input.assignmentId())) {
                throw new ApiException(PerformanceErrorCode.REVIEW_ITEM_ASSIGNMENT_MISMATCH,
                    Map.of("assignmentId", input.assignmentId(), "cycleId", cycleId, "employeeId", employeeId));
            }
            BigDecimal score = input.managerScore();
            if (score != null && (score.compareTo(ZERO) < 0 || score.compareTo(HUNDRED) > 0)) {
                throw new ApiException(PerformanceErrorCode.REVIEW_SCORE_OUT_OF_RANGE,
                    Map.of("assignmentId", input.assignmentId(),
                        "managerScore", score.toPlainString(), "range", "[0,100]"));
            }
        }
    }

    /** cycle 단계 게이트 — 요구 status 와 다르면 REVIEW_CYCLE_STAGE_MISMATCH (FINALIZED/CANCELLED 포함). */
    private void requireCycleStage(UUID cycleId, UUID tenantId, CycleStatus required) {
        EvaluationCycle cycle = requireCycle(cycleId, tenantId);
        if (cycle.getStatus() != required) {
            throw new ApiException(PerformanceErrorCode.REVIEW_CYCLE_STAGE_MISMATCH,
                Map.of("cycleId", cycleId, "currentStatus", cycle.getStatus().name(),
                    "requiredStatus", required.name()));
        }
    }

    private ApiException sectionNotEditable(ReviewStatus status, String editableSection) {
        return new ApiException(PerformanceErrorCode.REVIEW_SECTION_NOT_EDITABLE,
            Map.of("status", status.name(), "editableSection", editableSection));
    }

    private ApiException invalidTransition(ReviewStatus from, ReviewStatus to, String operation) {
        return new ApiException(PerformanceErrorCode.REVIEW_INVALID_STATUS_TRANSITION,
            Map.of("from", from.name(), "to", to == null ? "null" : to.name(), "operation", operation));
    }

    private PerformanceReview newReview(UUID tenantId, UUID cycleId, UUID employeeId) {
        PerformanceReview review = new PerformanceReview();
        review.setTenantId(tenantId);
        review.setCycleId(cycleId);
        review.setEmployeeId(employeeId);
        review.setStatus(ReviewStatus.DRAFT);
        return review;
    }

    private EvaluationCycle requireCycle(UUID cycleId, UUID tenantId) {
        return cycleRepository.findByIdAndTenantId(cycleId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND,
                Map.of("entity", "EvaluationCycle", "id", cycleId)));
    }

    private PerformanceReview requireReview(UUID reviewId, UUID tenantId) {
        return reviewRepository.findByIdAndTenantId(reviewId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REVIEW_NOT_FOUND,
                Map.of("entity", "PerformanceReview", "id", reviewId)));
    }

    // ═════════════════════════════════════════════════════════════════════
    // 응답 매핑 / jsonb 직렬화
    // ═════════════════════════════════════════════════════════════════════

    /** 엔티티 → ReviewResponse. kpiScoreDetail 은 동결 이후에만 스냅샷 배열 노출 (그 전 null). */
    private ReviewResponse toResponse(PerformanceReview review) {
        List<ReviewKpiItemResponse> detail = SNAPSHOT_FROZEN.contains(review.getStatus())
            ? parseSnapshot(review.getKpiScoreDetail())
            : null;
        return ReviewResponse.from(review, detail);
    }

    private Map<UUID, BigDecimal> toScoreMap(List<ReviewItemScoreInput> itemScores) {
        Map<UUID, BigDecimal> map = new LinkedHashMap<>();
        if (itemScores != null) {
            for (ReviewItemScoreInput input : itemScores) {
                map.put(input.assignmentId(), input.managerScore());
            }
        }
        return map;
    }

    /** 풀 스냅샷 배열 직렬화 (submit-manager 동결). */
    private String serializeSnapshot(List<ReviewKpiItemResponse> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new ApiException(PerformanceErrorCode.REVIEW_SCORE_INCOMPLETE,
                Map.of("error", "snapshot-serialization-failed"), e);
        }
    }

    /** jsonb 스냅샷 String → 파싱 배열. null/blank → null. */
    private List<ReviewKpiItemResponse> parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ReviewKpiItemResponse>>() {});
        } catch (JsonProcessingException e) {
            // 손상된 행은 운영 사고 — graceful null 폴백.
            return null;
        }
    }

    /**
     * MANAGER_PENDING draft managerScore 직렬화 — 부분 배열 {assignmentId, managerScore} 만 보관.
     * kpi-items merge 용 (풀 스냅샷 동결 전).
     */
    private String serializeDraftManagerScores(List<ReviewItemScoreInput> itemScores) {
        List<Map<String, Object>> draft = new ArrayList<>();
        for (ReviewItemScoreInput input : itemScores) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("assignmentId", input.assignmentId().toString());
            row.put("managerScore", input.managerScore());
            draft.add(row);
        }
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (JsonProcessingException e) {
            throw new ApiException(PerformanceErrorCode.REVIEW_SCORE_INCOMPLETE,
                Map.of("error", "draft-serialization-failed"), e);
        }
    }

    /**
     * 저장된 jsonb 에서 draft managerScore (assignmentId → managerScore) 추출. draft 부분 배열 또는
     * 풀 스냅샷 둘 다 managerScore 필드를 가지므로 공용 파싱. null/blank → 빈 맵.
     */
    private Map<UUID, BigDecimal> parseDraftManagerScores(String json) {
        Map<UUID, BigDecimal> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return map;
        }
        try {
            List<Map<String, Object>> rows =
                objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> row : rows) {
                Object aid = row.get("assignmentId");
                Object score = row.get("managerScore");
                if (aid == null) {
                    continue;
                }
                UUID assignmentId = UUID.fromString(aid.toString());
                BigDecimal managerScore = (score == null) ? null : new BigDecimal(score.toString());
                map.put(assignmentId, managerScore);
            }
        } catch (JsonProcessingException | IllegalArgumentException e) {
            // 손상된 행은 graceful 빈 맵 — live 계산만 (managerScore merge 생략).
            return new LinkedHashMap<>();
        }
        return map;
    }
}

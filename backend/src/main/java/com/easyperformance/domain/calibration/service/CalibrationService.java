/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.calibration.service;

import com.easyperformance.common.TenantSupport;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.AdjustmentEntry;
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
import com.easyperformance.domain.calibration.dto.CalibrationDtos.SimulationEntry;
import com.easyperformance.domain.calibration.entity.CalibrationSession;
import com.easyperformance.domain.calibration.entity.CalibrationStatus;
import com.easyperformance.domain.calibration.entity.RatingDistribution;
import com.easyperformance.domain.calibration.repository.CalibrationSessionRepository;
import com.easyperformance.domain.calibration.repository.RatingDistributionRepository;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.evaluationpolicy.entity.DistributionMode;
import com.easyperformance.domain.evaluationpolicy.entity.EvaluationPolicy;
import com.easyperformance.domain.evaluationpolicy.entity.RatingScale;
import com.easyperformance.domain.evaluationpolicy.repository.EvaluationPolicyRepository;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewResponse;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewTransitionRequest;
import com.easyperformance.domain.review.entity.PerformanceReview;
import com.easyperformance.domain.review.entity.ReviewStatus;
import com.easyperformance.domain.review.repository.PerformanceReviewRepository;
import com.easyperformance.domain.review.service.ReviewService;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 캘리브레이션 + 분포 도메인 Service — 비즈니스 SSOT (CalibrationSession + RatingDistribution).
 * P0-S4 (p0_s4_contract.md §3/§5).
 *
 * <p>테넌트 격리: {@link TenantSupport#currentTenantId()} 위임 — 모든 쿼리에 tenant_id 필수
 * (easy-ware 규칙 #10).
 *
 * <p>핵심 책임 (BE 가 유일 계산자 §5):
 * <ul>
 *   <li>세션 5단계 상태기계 — transition 2 + 자동 승격 1 (adjustments) + confirm 전용 2 (§3 매트릭스).</li>
 *   <li>cycle 단계 게이트 — IN_SESSION 진입/adjustments/confirm/simulate/apply 는 CALIBRATION 한정
 *       (CALIBRATION_CYCLE_STAGE_MISMATCH).</li>
 *   <li>effectiveGrade = {@code finalGrade ?? bandGrade(kpiScore)} — {@link ReviewService#bandGrade}
 *       public 재사용 (중복 구현 금지 — P0-S1 D1 함정 회피). 둘 다 null → UNRATED.</li>
 *   <li>강제 배분 = largest remainder method (Σquota==N 보장, 동률 S→A→B→C→D) — {@link DistributionMath} 위임.</li>
 *   <li>apply = 대상 review.finalGrade 일괄 UPDATE (status 불변) + RatingDistribution upsert + simulation_log append.</li>
 * </ul>
 *
 * <p>JSONB 매핑 (P0-S1 D2 패턴): {@link CalibrationJson} 위임 — ObjectMapper {@code USE_BIG_DECIMAL_FOR_FLOATS}.
 */
@Service
public class CalibrationService {

    /** PLANNED 만 PATCH/삭제 허용 (그 외 잠금/거부). */
    private static final Set<CalibrationStatus> CONFIRMABLE_FROM = EnumSet.of(
        CalibrationStatus.IN_SESSION, CalibrationStatus.ADJUSTED);

    /** 강제 배분 대상/현재 분포 카운트 대상 review 상태 (effectiveGrade 버킷). */
    private static final Set<ReviewStatus> CURRENT_DISTRIBUTION_STATUSES = EnumSet.of(
        ReviewStatus.CALIBRATION, ReviewStatus.FINALIZED);

    private final CalibrationSessionRepository sessionRepository;
    private final RatingDistributionRepository distributionRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final EvaluationCycleRepository cycleRepository;
    private final EvaluationPolicyRepository policyRepository;
    private final ReviewService reviewService;
    private final CalibrationJson json;

    public CalibrationService(CalibrationSessionRepository sessionRepository,
                              RatingDistributionRepository distributionRepository,
                              PerformanceReviewRepository reviewRepository,
                              EvaluationCycleRepository cycleRepository,
                              EvaluationPolicyRepository policyRepository,
                              ReviewService reviewService) {
        this(sessionRepository, distributionRepository, reviewRepository, cycleRepository,
            policyRepository, reviewService, java.time.Clock.systemUTC());
    }

    /** 테스트 친화 — Clock 주입 (jobstructure/security/ReviewService 패턴 정합). */
    public CalibrationService(CalibrationSessionRepository sessionRepository,
                              RatingDistributionRepository distributionRepository,
                              PerformanceReviewRepository reviewRepository,
                              EvaluationCycleRepository cycleRepository,
                              EvaluationPolicyRepository policyRepository,
                              ReviewService reviewService,
                              java.time.Clock clock) {
        this.sessionRepository = sessionRepository;
        this.distributionRepository = distributionRepository;
        this.reviewRepository = reviewRepository;
        this.cycleRepository = cycleRepository;
        this.policyRepository = policyRepository;
        this.reviewService = reviewService;
        this.json = new CalibrationJson(clock);
    }

    // ═════════════════════════════════════════════════════════════════════
    // CalibrationSession — 조회 / 생성 / PATCH
    // ═════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<CalibrationSessionResponse> listSessions(UUID cycleId) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireCycle(cycleId, tenantId);
        return sessionRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId)
            .stream().map(this::toSessionResponse).toList();
    }

    @Transactional(readOnly = true)
    public CalibrationSessionResponse getSession(UUID sessionId) {
        UUID tenantId = TenantSupport.currentTenantId();
        return toSessionResponse(requireSession(sessionId, tenantId));
    }

    /** 세션 생성 — cycle 이 FINALIZED/CANCELLED 외 전부 허용 (사전 일정 등록). status=PLANNED. */
    @Transactional
    public CalibrationSessionResponse createSession(UUID cycleId, CalibrationSessionCreateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        EvaluationCycle cycle = requireCycle(cycleId, tenantId);
        requireMutableCycleStage(cycle);

        CalibrationSession session = new CalibrationSession();
        session.setTenantId(tenantId);
        session.setCycleId(cycleId);
        session.setOwnerOrgUnitId(request.ownerOrgUnitId());
        session.setStatus(CalibrationStatus.PLANNED);
        session.setScheduledAt(request.scheduledAt());
        session.setParticipantIds(json.serializeUuids(request.participantIds()));
        return toSessionResponse(sessionRepository.save(session));
    }

    /** 세션 부분 수정 — PLANNED 한정 (그 외 LOCKED 409). cycle 은 FINALIZED/CANCELLED 외 전부 허용. */
    @Transactional
    public CalibrationSessionResponse updateSession(UUID sessionId, CalibrationSessionUpdateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        CalibrationSession session = requireSession(sessionId, tenantId);
        requireMutableCycleStage(requireCycle(session.getCycleId(), tenantId));
        if (session.getStatus() != CalibrationStatus.PLANNED) {
            throw sessionLocked(session, "patch");
        }
        if (request.ownerOrgUnitId() != null) {
            session.setOwnerOrgUnitId(request.ownerOrgUnitId());
        }
        if (request.scheduledAt() != null) {
            session.setScheduledAt(request.scheduledAt());
        }
        if (request.participantIds() != null) {
            session.setParticipantIds(json.serializeUuids(request.participantIds()));
        }
        return toSessionResponse(sessionRepository.save(session));
    }

    // ═════════════════════════════════════════════════════════════════════
    // CalibrationSession — transition / adjustments / confirm / delete
    // ═════════════════════════════════════════════════════════════════════

    /**
     * 상태 전이 — §3 매트릭스 (PLANNED→IN_SESSION cycle=CALIBRATION / CONFIRMED→CLOSED 무관). CONFIRMED 로의
     * 전이는 confirm endpoint 전용 (여기선 CALIBRATION_INVALID_STATUS_TRANSITION). 그 외 전이도 동일 거부.
     */
    @Transactional
    public CalibrationSessionResponse transition(UUID sessionId, CalibrationTransitionRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        CalibrationSession session = requireSession(sessionId, tenantId);
        CalibrationStatus from = session.getStatus();
        CalibrationStatus to = request.targetStatus();

        if (from == CalibrationStatus.PLANNED && to == CalibrationStatus.IN_SESSION) {
            requireCycleStage(session.getCycleId(), tenantId);
            session.setStatus(to);
        } else if (from == CalibrationStatus.CONFIRMED && to == CalibrationStatus.CLOSED) {
            session.setStatus(to);
        } else {
            throw invalidTransition(from, to);
        }
        return toSessionResponse(sessionRepository.save(session));
    }

    /**
     * 개별 등급 조정 — §5: review.finalGrade = toGrade + adjustment_log append (fromGrade = 조정 전
     * effectiveGrade) + IN_SESSION 이면 ADJUSTED 자동 승격. cycle=CALIBRATION 한정.
     */
    @Transactional
    public CalibrationSessionResponse adjust(UUID sessionId, CalibrationAdjustmentRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        CalibrationSession session = requireSession(sessionId, tenantId);
        if (session.getStatus() == CalibrationStatus.CONFIRMED
            || session.getStatus() == CalibrationStatus.CLOSED) {
            throw sessionLocked(session, "adjust");
        }
        requireCycleStage(session.getCycleId(), tenantId);

        // toGrade ∈ {S,A,B,C,D}.
        if (!DistributionMath.GRADES.contains(request.toGrade())) {
            throw new ApiException(PerformanceErrorCode.CALIBRATION_ADJUSTMENT_INVALID,
                Map.of("toGrade", String.valueOf(request.toGrade()), "allowed", DistributionMath.GRADES));
        }
        // 대상 review — 미존재 = REVIEW_NOT_FOUND (도메인 교차 재사용). cycle 소속 검증.
        PerformanceReview review = reviewRepository
            .findByIdAndTenantId(request.reviewId(), tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REVIEW_NOT_FOUND,
                Map.of("entity", "PerformanceReview", "id", request.reviewId())));
        if (!review.getCycleId().equals(session.getCycleId())) {
            throw new ApiException(PerformanceErrorCode.CALIBRATION_ADJUSTMENT_INVALID,
                Map.of("reviewId", review.getId(), "reviewCycleId", review.getCycleId(),
                    "sessionCycleId", session.getCycleId(), "reason", "review not in session cycle"));
        }
        // review 가 CALIBRATION 상태여야 조정 가능 (강제 배분 대상 단계).
        if (review.getStatus() != ReviewStatus.CALIBRATION) {
            throw new ApiException(PerformanceErrorCode.CALIBRATION_REVIEW_NOT_READY,
                Map.of("reviewId", review.getId(), "status", review.getStatus().name(),
                    "requiredStatus", ReviewStatus.CALIBRATION.name()));
        }

        String fromGrade = effectiveGrade(review);
        review.setFinalGrade(request.toGrade());
        reviewRepository.save(review);

        AdjustmentEntry entry = new AdjustmentEntry(
            json.now(), request.actorEmployeeId(), review.getId(), review.getEmployeeId(),
            fromGrade, request.toGrade(), request.reason());
        session.setAdjustmentLog(json.appendAdjustment(session.getAdjustmentLog(), entry));

        if (session.getStatus() == CalibrationStatus.IN_SESSION) {
            session.setStatus(CalibrationStatus.ADJUSTED); // 자동 승격.
        }
        return toSessionResponse(sessionRepository.save(session));
    }

    /**
     * 확정 — confirmed_at/by 세팅 + CONFIRMED 전이. {@code finalizeReviews==true} 면 cycle 의
     * status==CALIBRATION review 전부에 P0-S3 FINALIZED 전이를 행 단위 적용 (kpiScore NULL 행 skip).
     * cycle=CALIBRATION 한정 + 세션은 IN_SESSION/ADJUSTED 에서만 (그 외 LOCKED).
     */
    @Transactional
    public CalibrationConfirmResponse confirm(UUID sessionId, CalibrationConfirmRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        CalibrationSession session = requireSession(sessionId, tenantId);
        if (!CONFIRMABLE_FROM.contains(session.getStatus())) {
            throw sessionLocked(session, "confirm");
        }
        requireCycleStage(session.getCycleId(), tenantId);

        session.setStatus(CalibrationStatus.CONFIRMED);
        session.setConfirmedAt(json.now());
        session.setConfirmedBy(request.actorEmployeeId());
        CalibrationSession saved = sessionRepository.save(session);

        int finalized = 0;
        int skipped = 0;
        if (Boolean.TRUE.equals(request.finalizeReviews())) {
            for (PerformanceReview review : reviewRepository
                    .findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, session.getCycleId())) {
                if (review.getStatus() != ReviewStatus.CALIBRATION) {
                    continue; // CALIBRATION 단계 행만 대상.
                }
                if (review.getKpiScore() == null) {
                    skipped++;
                    continue; // 점수 미산출 행 skip (카운트).
                }
                // P0-S3 FINALIZED 전이 재사용 (public 경로 — cross-package private 금지).
                reviewService.transition(review.getId(),
                    new ReviewTransitionRequest(ReviewStatus.FINALIZED, request.actorEmployeeId()));
                finalized++;
            }
        }
        return new CalibrationConfirmResponse(toSessionResponse(saved), finalized, skipped);
    }

    /** 세션 삭제 — PLANNED 한정 (그 외 CALIBRATION_SESSION_CANNOT_DELETE 409). */
    @Transactional
    public void deleteSession(UUID sessionId) {
        UUID tenantId = TenantSupport.currentTenantId();
        CalibrationSession session = requireSession(sessionId, tenantId);
        if (session.getStatus() != CalibrationStatus.PLANNED) {
            throw new ApiException(PerformanceErrorCode.CALIBRATION_SESSION_CANNOT_DELETE,
                Map.of("sessionId", sessionId, "status", session.getStatus().name()));
        }
        sessionRepository.delete(session);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 분포 — GET / simulate / apply
    // ═════════════════════════════════════════════════════════════════════

    /**
     * 현재 분포 (순수 계산 — RatingDistribution 행 불요): cycle 의 review 중 status ∈ {CALIBRATION, FINALIZED}
     * 전체를 effectiveGrade 로 버킷 카운트 {S,A,B,C,D,UNRATED}. policy/적용 메타는 행 있으면 노출.
     */
    @Transactional(readOnly = true)
    public DistributionResponse getDistribution(UUID cycleId) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireCycle(cycleId, tenantId);
        EvaluationPolicy policy = policyRepository.findByTenantIdAndCycleId(tenantId, cycleId).orElse(null);

        List<PerformanceReview> all = reviewRepository
            .findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId);
        Map<String, Integer> current = DistributionMath.emptyCounts(true);
        int calibrationReady = 0;
        for (PerformanceReview r : all) {
            if (CURRENT_DISTRIBUTION_STATUSES.contains(r.getStatus())) {
                String g = effectiveGrade(r);
                current.merge(g, 1, Integer::sum);
            }
            if (r.getStatus() == ReviewStatus.CALIBRATION && r.getKpiScore() != null) {
                calibrationReady++;
            }
        }

        RatingDistribution dist = distributionRepository
            .findByTenantIdAndCycleIdAndOrgUnitIdIsNull(tenantId, cycleId).orElse(null);

        Map<String, BigDecimal> target = json.parseRatioMap(
            dist != null ? dist.getPolicyDistribution()
                         : (policy != null ? policy.getForcedDistribution() : null));

        return new DistributionResponse(
            cycleId,
            policy != null ? policy.getDistributionMode() : null,
            policy != null ? policy.getRatingScale() : null,
            target,
            current,
            all.size(),
            calibrationReady,
            dist != null && dist.isForcedApplied(),
            dist != null ? dist.getAppliedAt() : null,
            dist != null ? dist.getAppliedBy() : null,
            dist != null ? json.parseSimulationLog(dist.getSimulationLog()) : null);
    }

    /** 강제 배분 시뮬레이션 — 무저장 순수 계산 (proposed 목록 + 결과 분포). cycle=CALIBRATION 한정 (§3). */
    @Transactional(readOnly = true)
    public DistributionSimulationResponse simulate(UUID cycleId, DistributionSimulateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireCycleStage(cycleId, tenantId);
        EvaluationPolicy policy = requireForcedPolicy(cycleId, tenantId);

        Allocation alloc = allocate(tenantId, cycleId, policy, request.targetDistribution());
        return new DistributionSimulationResponse(alloc.proposed, alloc.resulting, alloc.target);
    }

    /**
     * 강제 배분 적용 — 대상 review.finalGrade = proposedGrade 일괄 UPDATE (status 불변, FINALIZED 전이 아님)
     * + RatingDistribution upsert + simulation_log append. appliedCount = 대상 N / skippedCount = cycle 의
     * CALIBRATION 상태 중 kpiScore NULL 수.
     */
    @Transactional
    public DistributionApplyResponse apply(UUID cycleId, DistributionApplyRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireCycleStage(cycleId, tenantId);
        EvaluationPolicy policy = requireForcedPolicy(cycleId, tenantId);

        Allocation alloc = allocate(tenantId, cycleId, policy, request.targetDistribution());

        // 대상 review 등급 일괄 UPDATE (status 는 CALIBRATION 유지).
        for (ProposedGradeRow row : alloc.proposed) {
            PerformanceReview review = reviewRepository
                .findByIdAndTenantId(row.reviewId(), tenantId).orElseThrow();
            review.setFinalGrade(row.proposedGrade());
            reviewRepository.save(review);
        }

        // skippedCount = CALIBRATION 상태 중 kpiScore NULL 수 (배분 대상 외).
        int skipped = 0;
        for (PerformanceReview r : reviewRepository
                .findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId)) {
            if (r.getStatus() == ReviewStatus.CALIBRATION && r.getKpiScore() == null) {
                skipped++;
            }
        }

        // RatingDistribution upsert (전사 NULL-org 행).
        RatingDistribution dist = distributionRepository
            .findByTenantIdAndCycleIdAndOrgUnitIdIsNull(tenantId, cycleId)
            .orElseGet(() -> {
                RatingDistribution d = new RatingDistribution();
                d.setTenantId(tenantId);
                d.setCycleId(cycleId);
                d.setOrgUnitId(null);
                return d;
            });
        dist.setPolicyDistribution(json.serializeRatioMap(alloc.target));
        dist.setActualDistribution(json.serializeCountMap(alloc.resulting));
        dist.setForcedApplied(true);
        dist.setAppliedAt(json.now());
        dist.setAppliedBy(request.actorEmployeeId());
        SimulationEntry entry = new SimulationEntry(
            json.now(), request.actorEmployeeId(), alloc.target,
            alloc.proposed.size(), skipped, alloc.resulting);
        dist.setSimulationLog(json.appendSimulation(dist.getSimulationLog(), entry));
        distributionRepository.save(dist);

        return new DistributionApplyResponse(alloc.proposed.size(), skipped, alloc.resulting);
    }

    // ═════════════════════════════════════════════════════════════════════
    // §5 강제 배분 산식
    // ═════════════════════════════════════════════════════════════════════

    /** simulate/apply 공통 배분 계산 결과. */
    private record Allocation(List<ProposedGradeRow> proposed,
                              Map<String, Integer> resulting,
                              Map<String, BigDecimal> target) {}

    /**
     * 강제 배분 계산 (§5):
     * <ol>
     *   <li>대상 = status==CALIBRATION AND kpiScore != null, {@code kpiScore DESC, employeeId ASC}.</li>
     *   <li>target = request.targetDistribution ?? policy.forcedDistribution (둘 다 없으면 INVALID_TARGET).</li>
     *   <li>quota = largest remainder (Σquota==N 보장, 동률 S→A→B→C→D).</li>
     *   <li>정렬 순서대로 S quota → A → … 할당 = proposedGrade.</li>
     * </ol>
     */
    private Allocation allocate(UUID tenantId, UUID cycleId, EvaluationPolicy policy,
                                Map<String, BigDecimal> requestTarget) {
        // target 해석 + 검증.
        Map<String, BigDecimal> rawTarget = (requestTarget != null && !requestTarget.isEmpty())
            ? requestTarget
            : json.parseRatioMap(policy.getForcedDistribution());
        Map<String, BigDecimal> target = DistributionMath.validateAndNormalizeTarget(rawTarget);

        // 대상 review 정렬.
        List<PerformanceReview> targets = new ArrayList<>();
        for (PerformanceReview r : reviewRepository
                .findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId)) {
            if (r.getStatus() == ReviewStatus.CALIBRATION && r.getKpiScore() != null) {
                targets.add(r);
            }
        }
        targets.sort(Comparator
            .comparing(PerformanceReview::getKpiScore, Comparator.reverseOrder())
            .thenComparing(PerformanceReview::getEmployeeId));

        int n = targets.size();
        Map<String, Integer> quota = DistributionMath.largestRemainder(n, target);

        // 정렬 순서대로 등급 할당.
        List<ProposedGradeRow> proposed = new ArrayList<>(n);
        Map<String, Integer> resulting = DistributionMath.emptyCounts(false);
        int idx = 0;
        for (String grade : DistributionMath.GRADE_LIST) {
            int q = quota.getOrDefault(grade, 0);
            for (int i = 0; i < q && idx < n; i++, idx++) {
                PerformanceReview r = targets.get(idx);
                proposed.add(new ProposedGradeRow(
                    r.getId(), r.getEmployeeId(), r.getKpiScore(), effectiveGrade(r), grade));
                resulting.merge(grade, 1, Integer::sum);
            }
        }
        return new Allocation(proposed, resulting, target);
    }

    /** effectiveGrade = finalGrade ?? bandGrade(kpiScore). 둘 다 null → UNRATED. */
    private String effectiveGrade(PerformanceReview review) {
        if (review.getFinalGrade() != null) {
            return review.getFinalGrade();
        }
        String band = reviewService.bandGrade(review.getKpiScore());
        return band != null ? band : "UNRATED";
    }

    // ═════════════════════════════════════════════════════════════════════
    // 게이트 / 검증 헬퍼
    // ═════════════════════════════════════════════════════════════════════

    /** cycle 단계 게이트 — CALIBRATION 한정 (IN_SESSION 진입/adjust/confirm/simulate/apply). */
    private void requireCycleStage(UUID cycleId, UUID tenantId) {
        EvaluationCycle cycle = requireCycle(cycleId, tenantId);
        if (cycle.getStatus() != CycleStatus.CALIBRATION) {
            throw new ApiException(PerformanceErrorCode.CALIBRATION_CYCLE_STAGE_MISMATCH,
                Map.of("cycleId", cycleId, "currentStatus", cycle.getStatus().name(),
                    "requiredStatus", CycleStatus.CALIBRATION.name()));
        }
    }

    /** 생성/PATCH 시 cycle 이 FINALIZED/CANCELLED 면 거부 (사전 일정 등록은 그 외 단계 전부 허용). */
    private void requireMutableCycleStage(EvaluationCycle cycle) {
        if (cycle.getStatus() == CycleStatus.FINALIZED || cycle.getStatus() == CycleStatus.CANCELLED) {
            throw new ApiException(PerformanceErrorCode.CALIBRATION_CYCLE_STAGE_MISMATCH,
                Map.of("cycleId", cycle.getId(), "currentStatus", cycle.getStatus().name(),
                    "reason", "session create/patch not allowed in terminal cycle stage"));
        }
    }

    /** policy 가 FORCED/HYBRID 인지 (ABSOLUTE 거부) + ratingScale==S_A_B_C_D 인지. policy 부재 = POLICY_NOT_FOUND. */
    private EvaluationPolicy requireForcedPolicy(UUID cycleId, UUID tenantId) {
        EvaluationPolicy policy = policyRepository.findByTenantIdAndCycleId(tenantId, cycleId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.POLICY_NOT_FOUND,
                Map.of("entity", "EvaluationPolicy", "cycleId", cycleId)));
        if (policy.getDistributionMode() == DistributionMode.ABSOLUTE) {
            throw new ApiException(PerformanceErrorCode.DISTRIBUTION_MODE_NOT_FORCED,
                Map.of("cycleId", cycleId, "distributionMode", policy.getDistributionMode().name(),
                    "allowed", List.of(DistributionMode.FORCED.name(), DistributionMode.HYBRID.name())));
        }
        if (policy.getRatingScale() != RatingScale.S_A_B_C_D) {
            throw new ApiException(PerformanceErrorCode.DISTRIBUTION_SCALE_NOT_SUPPORTED,
                Map.of("cycleId", cycleId, "ratingScale", policy.getRatingScale().name(),
                    "requiredScale", RatingScale.S_A_B_C_D.name()));
        }
        return policy;
    }

    private EvaluationCycle requireCycle(UUID cycleId, UUID tenantId) {
        return cycleRepository.findByIdAndTenantId(cycleId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND,
                Map.of("entity", "EvaluationCycle", "id", cycleId)));
    }

    private CalibrationSession requireSession(UUID sessionId, UUID tenantId) {
        return sessionRepository.findByIdAndTenantId(sessionId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CALIBRATION_SESSION_NOT_FOUND,
                Map.of("entity", "CalibrationSession", "id", sessionId)));
    }

    private ApiException invalidTransition(CalibrationStatus from, CalibrationStatus to) {
        return new ApiException(PerformanceErrorCode.CALIBRATION_INVALID_STATUS_TRANSITION,
            Map.of("from", from.name(), "to", to == null ? "null" : to.name()));
    }

    private ApiException sessionLocked(CalibrationSession session, String operation) {
        return new ApiException(PerformanceErrorCode.CALIBRATION_SESSION_LOCKED,
            Map.of("sessionId", session.getId(), "status", session.getStatus().name(),
                "operation", operation));
    }

    // ═════════════════════════════════════════════════════════════════════
    // 응답 매핑
    // ═════════════════════════════════════════════════════════════════════

    private CalibrationSessionResponse toSessionResponse(CalibrationSession session) {
        return CalibrationSessionResponse.from(session,
            json.parseUuids(session.getParticipantIds()),
            json.parseAdjustmentLog(session.getAdjustmentLog()));
    }
}

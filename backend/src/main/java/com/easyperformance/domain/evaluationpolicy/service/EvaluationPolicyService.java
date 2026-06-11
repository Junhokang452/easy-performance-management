/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationpolicy.service;

import com.easyperformance.common.TenantSupport;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.evaluationpolicy.dto.EvaluationPolicyDtos.PolicyResponse;
import com.easyperformance.domain.evaluationpolicy.dto.EvaluationPolicyDtos.PolicyUpsertRequest;
import com.easyperformance.domain.evaluationpolicy.entity.DistributionMode;
import com.easyperformance.domain.evaluationpolicy.entity.EvaluationPolicy;
import com.easyperformance.domain.evaluationpolicy.entity.RatingScale;
import com.easyperformance.domain.evaluationpolicy.repository.EvaluationPolicyRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * EvaluationPolicy Service — 비즈니스 SSOT (분포 검증 + 등급 키 검증 + lock 검증).
 *
 * <p>테넌트 격리: {@link TenantSupport#currentTenantId()} 위임 (easy-ware 규칙 #10).
 *
 * <p>ADR-026 명명: {@code get / upsert}.
 *
 * <p>BigDecimal 정밀도 보존 — ObjectMapper {@code USE_BIG_DECIMAL_FOR_FLOATS}.
 */
@Service
public class EvaluationPolicyService {

    private static final Set<String> S_A_B_C_D_KEYS = Set.of("S", "A", "B", "C", "D");
    private static final BigDecimal ONE = BigDecimal.ONE;
    /** 합계 허용 오차 (±0.001). */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.001");

    private final EvaluationPolicyRepository policyRepository;
    private final EvaluationCycleRepository cycleRepository;
    private final ObjectMapper objectMapper;

    public EvaluationPolicyService(EvaluationPolicyRepository policyRepository,
                                   EvaluationCycleRepository cycleRepository) {
        this.policyRepository = policyRepository;
        this.cycleRepository = cycleRepository;
        this.objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    @Transactional(readOnly = true)
    public PolicyResponse get(UUID cycleId) {
        UUID tenantId = TenantSupport.currentTenantId();
        EvaluationPolicy policy = policyRepository.findByTenantIdAndCycleId(tenantId, cycleId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.POLICY_NOT_FOUND,
                Map.of("entity", "EvaluationPolicy", "cycleId", cycleId)));
        return PolicyResponse.from(policy, deserialize(policy.getForcedDistribution()));
    }

    /**
     * Policy upsert — cycle 1:1 정책. 없으면 생성, 있으면 갱신.
     *
     * <p>cycle.status != PLANNED 일 때 distributionMode/ratingScale 변경 거부 (POLICY_LOCKED).
     * forcedDistribution 분포 조정은 PLANNED 이외에서도 허용.
     */
    @Transactional
    public PolicyResponse upsert(UUID cycleId, PolicyUpsertRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        EvaluationCycle cycle = cycleRepository.findByIdAndTenantId(cycleId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND,
                Map.of("entity", "EvaluationCycle", "id", cycleId)));

        EvaluationPolicy existing = policyRepository.findByTenantIdAndCycleId(tenantId, cycleId).orElse(null);

        // 분포/등급 키 검증 (생성/갱신 공통).
        validateDistribution(request);
        validateRatingScaleKeys(request);

        if (existing == null) {
            // 신규 생성.
            EvaluationPolicy created = new EvaluationPolicy();
            created.setTenantId(tenantId);
            created.setCycleId(cycleId);
            created.setDistributionMode(request.distributionMode());
            created.setRatingScale(request.ratingScale());
            created.setAppealEnabled(request.appealEnabled());
            created.setBscEnabled(request.bscEnabled());
            created.setAchievementLogCutoffDays(request.achievementLogCutoffDays());
            created.setForcedDistribution(serializeForMode(request.distributionMode(), request.forcedDistribution()));
            EvaluationPolicy saved = policyRepository.save(created);
            return PolicyResponse.from(saved, deserialize(saved.getForcedDistribution()));
        }

        // 갱신 — lock 검증 (distributionMode/ratingScale 변경은 PLANNED 단계에서만).
        validateLocked(cycle, existing, request);

        existing.setDistributionMode(request.distributionMode());
        existing.setRatingScale(request.ratingScale());
        existing.setAppealEnabled(request.appealEnabled());
        existing.setBscEnabled(request.bscEnabled());
        existing.setAchievementLogCutoffDays(request.achievementLogCutoffDays());
        existing.setForcedDistribution(serializeForMode(request.distributionMode(), request.forcedDistribution()));
        EvaluationPolicy saved = policyRepository.save(existing);
        return PolicyResponse.from(saved, deserialize(saved.getForcedDistribution()));
    }

    /**
     * Default policy 생성 — Cycle create 시 policy null 이면 자동 호출. HYBRID/S_A_B_C_D/false/false/3/null.
     * 별도 트랜잭션 진입 회피 — cycle service 의 동일 TX 안에서 호출.
     */
    @Transactional
    public EvaluationPolicy createDefault(UUID tenantId, UUID cycleId) {
        EvaluationPolicy created = new EvaluationPolicy();
        created.setTenantId(tenantId);
        created.setCycleId(cycleId);
        created.setDistributionMode(DistributionMode.HYBRID);
        created.setRatingScale(RatingScale.S_A_B_C_D);
        created.setAppealEnabled(false);
        created.setBscEnabled(false);
        created.setAchievementLogCutoffDays(3);
        created.setForcedDistribution(null);
        return policyRepository.save(created);
    }

    /** PolicyUpsertRequest 의 forcedDistribution + distributionMode 검증. */
    void validateDistribution(PolicyUpsertRequest request) {
        DistributionMode mode = request.distributionMode();
        Map<String, BigDecimal> fd = request.forcedDistribution();

        if (mode == DistributionMode.FORCED) {
            if (fd == null || fd.isEmpty()) {
                throw new ApiException(PerformanceErrorCode.POLICY_FORCED_REQUIRES_DISTRIBUTION,
                    Map.of("distributionMode", mode.name()));
            }
            validateSum(fd);
            return;
        }
        if (mode == DistributionMode.HYBRID) {
            // forcedDistribution 제공 시에만 합 검증 (없어도 허용).
            if (fd != null && !fd.isEmpty()) {
                validateSum(fd);
            }
            return;
        }
        // ABSOLUTE — forcedDistribution 무시. 검증 패스.
    }

    /** forcedDistribution 합계 1.0 ±0.001 검증. */
    private void validateSum(Map<String, BigDecimal> fd) {
        BigDecimal sum = fd.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = sum.subtract(ONE).abs();
        if (diff.compareTo(TOLERANCE) > 0) {
            throw new ApiException(PerformanceErrorCode.POLICY_INVALID_DISTRIBUTION_SUM,
                Map.of("expectedSum", "1.0", "actualSum", sum.setScale(3, RoundingMode.HALF_UP).toPlainString()));
        }
    }

    /** S_A_B_C_D + forcedDistribution 키 부분집합 검증. */
    void validateRatingScaleKeys(PolicyUpsertRequest request) {
        if (request.ratingScale() != RatingScale.S_A_B_C_D) {
            return; // 다른 스케일은 키 형식 자유.
        }
        Map<String, BigDecimal> fd = request.forcedDistribution();
        if (fd == null || fd.isEmpty()) {
            return;
        }
        for (String key : fd.keySet()) {
            if (!S_A_B_C_D_KEYS.contains(key)) {
                throw new ApiException(PerformanceErrorCode.POLICY_INVALID_RATING_SCALE,
                    Map.of("ratingScale", "S_A_B_C_D", "invalidKey", key,
                        "allowed", S_A_B_C_D_KEYS));
            }
        }
    }

    /** cycle.status != PLANNED → distributionMode/ratingScale 변경 거부. */
    void validateLocked(EvaluationCycle cycle, EvaluationPolicy existing, PolicyUpsertRequest request) {
        if (cycle.getStatus() == CycleStatus.PLANNED) {
            return;
        }
        boolean modeChanged = existing.getDistributionMode() != request.distributionMode();
        boolean scaleChanged = existing.getRatingScale() != request.ratingScale();
        if (modeChanged || scaleChanged) {
            throw new ApiException(PerformanceErrorCode.POLICY_LOCKED,
                Map.of("cycleStatus", cycle.getStatus().name(),
                    "modeChanged", modeChanged,
                    "scaleChanged", scaleChanged));
        }
    }

    /** ABSOLUTE 면 null, 아니면 ObjectMapper 직렬화. */
    private String serializeForMode(DistributionMode mode, Map<String, BigDecimal> fd) {
        if (mode == DistributionMode.ABSOLUTE) {
            return null;
        }
        if (fd == null || fd.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(fd);
        } catch (JsonProcessingException e) {
            throw new ApiException(PerformanceErrorCode.POLICY_INVALID_DISTRIBUTION_SUM,
                Map.of("error", "serialization-failed"), e);
        }
    }

    /** jsonb 문자열 → Map. null/empty 이면 null. BigDecimal 정밀도 보존. */
    private Map<String, BigDecimal> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, BigDecimal>>() {});
        } catch (JsonProcessingException e) {
            // 손상된 행은 운영 사고 — graceful null 폴백 + 로그 (LIVE 진입 시 로그 추가).
            return null;
        }
    }
}

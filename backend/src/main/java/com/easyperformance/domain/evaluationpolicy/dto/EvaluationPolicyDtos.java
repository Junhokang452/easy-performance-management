/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationpolicy.dto;

import com.easyperformance.domain.evaluationpolicy.entity.DistributionMode;
import com.easyperformance.domain.evaluationpolicy.entity.EvaluationPolicy;
import com.easyperformance.domain.evaluationpolicy.entity.RatingScale;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * EvaluationPolicy DTO 모음 — ADR-026 명명 정합 ({@code UpsertRequest / Response}).
 *
 * <p>{@code forcedDistribution} 은 BigDecimal Map (정밀도 보존). service 계층에서 entity
 * {@code String forcedDistribution} 컬럼과 ObjectMapper 직렬화/역직렬화.
 *
 * <p>검증 (service):
 * <ul>
 *   <li>FORCED + forcedDistribution null/empty → POLICY_FORCED_REQUIRES_DISTRIBUTION 422</li>
 *   <li>HYBRID + forcedDistribution 제공 시 합 1.0 (±0.001) → POLICY_INVALID_DISTRIBUTION_SUM 422</li>
 *   <li>FORCED 합 1.0 (±0.001) → POLICY_INVALID_DISTRIBUTION_SUM 422</li>
 *   <li>S_A_B_C_D + forcedDistribution 키가 {S,A,B,C,D} 부분집합 외 → POLICY_INVALID_RATING_SCALE 422</li>
 *   <li>ABSOLUTE → forcedDistribution 무시 (entity 저장 시 null)</li>
 *   <li>cycle.status != PLANNED → distributionMode/ratingScale 변경 거부 (POLICY_LOCKED 409,
 *       forcedDistribution 분포 조정은 허용)</li>
 * </ul>
 */
public final class EvaluationPolicyDtos {

    private EvaluationPolicyDtos() {
    }

    public record PolicyUpsertRequest(
        @NotNull DistributionMode distributionMode,
        @NotNull RatingScale ratingScale,
        boolean appealEnabled,
        boolean bscEnabled,
        @NotNull @Min(0) @Max(30) Integer achievementLogCutoffDays,
        Map<String, BigDecimal> forcedDistribution
    ) {}

    public record PolicyResponse(
        UUID id,
        UUID tenantId,
        UUID cycleId,
        DistributionMode distributionMode,
        RatingScale ratingScale,
        boolean appealEnabled,
        boolean bscEnabled,
        Integer achievementLogCutoffDays,
        Map<String, BigDecimal> forcedDistribution,
        Instant createdAt,
        Instant updatedAt
    ) {
        /**
         * Entity → Response. forcedDistribution Map 역직렬화는 service 가 수행 (ObjectMapper 의존
         * 회피 — DTO 는 순수 데이터). 본 정적 팩토리는 이미 Map 으로 역직렬화된 forcedDistribution 을 받는다.
         */
        public static PolicyResponse from(EvaluationPolicy e, Map<String, BigDecimal> forcedDistribution) {
            return new PolicyResponse(
                e.getId(),
                e.getTenantId(),
                e.getCycleId(),
                e.getDistributionMode(),
                e.getRatingScale(),
                e.isAppealEnabled(),
                e.isBscEnabled(),
                e.getAchievementLogCutoffDays(),
                forcedDistribution,
                e.getCreatedAt(),
                e.getUpdatedAt()
            );
        }
    }
}

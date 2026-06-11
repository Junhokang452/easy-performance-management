/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.CycleType;
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

/**
 * EvaluationPolicyService 단위 테스트 — P0-S1 (decisions_2026-06-11.md SoT).
 *
 * <p>커버 케이스:
 * <ul>
 *   <li>HYBRID + forcedDistribution 합 1.0 정상 (신규 upsert)</li>
 *   <li>FORCED + 빈 forcedDistribution 422 (POLICY_FORCED_REQUIRES_DISTRIBUTION)</li>
 *   <li>sum != 1.0 422 (POLICY_INVALID_DISTRIBUTION_SUM)</li>
 *   <li>S_A_B_C_D + 키 외 (예: "X") 422 (POLICY_INVALID_RATING_SCALE)</li>
 *   <li>cycle ACTIVE 후 distributionMode 변경 거부 409 (POLICY_LOCKED)</li>
 *   <li>ABSOLUTE → forcedDistribution 무시 저장</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EvaluationPolicyServiceTest {

    @Mock
    private EvaluationPolicyRepository policyRepository;

    @Mock
    private EvaluationCycleRepository cycleRepository;

    @InjectMocks
    private EvaluationPolicyService service;

    private UUID tenantId;
    private UUID cycleId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        cycleId = UUID.randomUUID();
    }

    @Test
    void upsert_hybridWithValidDistribution_succeeds() {
        EvaluationCycle cycle = newCycle(CycleStatus.PLANNED);
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(cycle));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId))).thenReturn(Optional.empty());
        when(policyRepository.save(any(EvaluationPolicy.class))).thenAnswer(inv -> {
            EvaluationPolicy e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });

        Map<String, BigDecimal> dist = new LinkedHashMap<>();
        dist.put("S", new BigDecimal("0.10"));
        dist.put("A", new BigDecimal("0.20"));
        dist.put("B", new BigDecimal("0.40"));
        dist.put("C", new BigDecimal("0.20"));
        dist.put("D", new BigDecimal("0.10"));

        PolicyUpsertRequest request = new PolicyUpsertRequest(
            DistributionMode.HYBRID, RatingScale.S_A_B_C_D, false, false, 3, dist);

        PolicyResponse response = service.upsert(cycleId, request);

        assertThat(response.distributionMode()).isEqualTo(DistributionMode.HYBRID);
        assertThat(response.forcedDistribution()).hasSize(5);
        assertThat(response.forcedDistribution().get("B")).isEqualByComparingTo(new BigDecimal("0.40"));
    }

    @Test
    void upsert_forcedWithEmptyDistribution_throws() {
        EvaluationCycle cycle = newCycle(CycleStatus.PLANNED);
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(cycle));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId))).thenReturn(Optional.empty());

        PolicyUpsertRequest request = new PolicyUpsertRequest(
            DistributionMode.FORCED, RatingScale.S_A_B_C_D, false, false, 3, null);

        assertThatThrownBy(() -> service.upsert(cycleId, request))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.POLICY_FORCED_REQUIRES_DISTRIBUTION);
    }

    @Test
    void upsert_distributionSumNotOne_throws() {
        EvaluationCycle cycle = newCycle(CycleStatus.PLANNED);
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(cycle));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId))).thenReturn(Optional.empty());

        Map<String, BigDecimal> dist = new LinkedHashMap<>();
        dist.put("S", new BigDecimal("0.50"));
        dist.put("A", new BigDecimal("0.30")); // 합 0.80 — out of tolerance.

        PolicyUpsertRequest request = new PolicyUpsertRequest(
            DistributionMode.FORCED, RatingScale.S_A_B_C_D, false, false, 3, dist);

        assertThatThrownBy(() -> service.upsert(cycleId, request))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.POLICY_INVALID_DISTRIBUTION_SUM);
    }

    @Test
    void upsert_invalidRatingScaleKey_throws() {
        EvaluationCycle cycle = newCycle(CycleStatus.PLANNED);
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(cycle));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId))).thenReturn(Optional.empty());

        Map<String, BigDecimal> dist = new LinkedHashMap<>();
        dist.put("X", new BigDecimal("1.0")); // 비허용 키 (S_A_B_C_D 외).

        PolicyUpsertRequest request = new PolicyUpsertRequest(
            DistributionMode.FORCED, RatingScale.S_A_B_C_D, false, false, 3, dist);

        assertThatThrownBy(() -> service.upsert(cycleId, request))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.POLICY_INVALID_RATING_SCALE);
    }

    @Test
    void upsert_lockedWhenCycleActive_modeChange_throws() {
        EvaluationCycle cycle = newCycle(CycleStatus.ACTIVE);
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(cycle));

        EvaluationPolicy existing = new EvaluationPolicy();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenantId);
        existing.setCycleId(cycleId);
        existing.setDistributionMode(DistributionMode.HYBRID);
        existing.setRatingScale(RatingScale.S_A_B_C_D);
        existing.setAchievementLogCutoffDays(3);
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId))).thenReturn(Optional.of(existing));

        // 분포 모드 변경 시도 — cycle.status = ACTIVE 이므로 거부.
        Map<String, BigDecimal> dist = new LinkedHashMap<>();
        dist.put("S", new BigDecimal("0.10"));
        dist.put("A", new BigDecimal("0.20"));
        dist.put("B", new BigDecimal("0.40"));
        dist.put("C", new BigDecimal("0.20"));
        dist.put("D", new BigDecimal("0.10"));

        PolicyUpsertRequest request = new PolicyUpsertRequest(
            DistributionMode.FORCED, // ← 모드 변경
            RatingScale.S_A_B_C_D, false, false, 3, dist);

        assertThatThrownBy(() -> service.upsert(cycleId, request))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.POLICY_LOCKED);
    }

    @Test
    void upsert_absoluteIgnoresForcedDistribution() {
        EvaluationCycle cycle = newCycle(CycleStatus.PLANNED);
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(cycle));
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId))).thenReturn(Optional.empty());
        when(policyRepository.save(any(EvaluationPolicy.class))).thenAnswer(inv -> {
            EvaluationPolicy e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });

        Map<String, BigDecimal> dist = new LinkedHashMap<>();
        dist.put("S", new BigDecimal("0.99")); // 임의 값 — ABSOLUTE 라서 검증/저장 모두 무시.

        PolicyUpsertRequest request = new PolicyUpsertRequest(
            DistributionMode.ABSOLUTE, RatingScale.ONE_TO_FIVE, false, false, 3, dist);

        PolicyResponse response = service.upsert(cycleId, request);

        assertThat(response.distributionMode()).isEqualTo(DistributionMode.ABSOLUTE);
        assertThat(response.forcedDistribution()).isNull();
    }

    @Test
    void get_notFound_throws() {
        when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(cycleId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.POLICY_NOT_FOUND);
    }

    @Test
    void createDefault_setsHybridSabCDCutoff3() {
        when(policyRepository.save(any(EvaluationPolicy.class))).thenAnswer(inv -> {
            EvaluationPolicy e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });

        EvaluationPolicy created = service.createDefault(tenantId, cycleId);

        assertThat(created.getDistributionMode()).isEqualTo(DistributionMode.HYBRID);
        assertThat(created.getRatingScale()).isEqualTo(RatingScale.S_A_B_C_D);
        assertThat(created.isAppealEnabled()).isFalse();
        assertThat(created.isBscEnabled()).isFalse();
        assertThat(created.getAchievementLogCutoffDays()).isEqualTo(3);
        assertThat(created.getForcedDistribution()).isNull();
    }

    private EvaluationCycle newCycle(CycleStatus status) {
        EvaluationCycle entity = new EvaluationCycle();
        entity.setId(cycleId);
        entity.setTenantId(tenantId);
        entity.setName("Test cycle");
        entity.setPeriodStart(LocalDate.of(2026, 1, 1));
        entity.setPeriodEnd(LocalDate.of(2026, 6, 30));
        entity.setCycleType(CycleType.HALF_ANNUAL);
        entity.setStatus(status);
        return entity;
    }
}

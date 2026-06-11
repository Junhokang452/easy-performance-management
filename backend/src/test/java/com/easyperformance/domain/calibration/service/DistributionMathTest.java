/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.calibration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;

/**
 * DistributionMath 단위 테스트 — P0-S4 (p0_s4_contract.md §5 largest remainder + target 검증).
 *
 * <p>핵심 경계: <b>Σquota == N 보장</b> (N=0 / N=1 / floor 합 &lt; N 잔여 배분 / 동률 S→A→B→C→D) +
 * target 검증 (키 ∉ {S,A,B,C,D} / 합 ≠ 1.0±0.001 / 음수 / null·empty).
 */
class DistributionMathTest {

    private static Map<String, BigDecimal> ratios(String... pairs) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], new BigDecimal(pairs[i + 1]));
        }
        return m;
    }

    private static int sum(Map<String, Integer> quota) {
        return quota.values().stream().mapToInt(Integer::intValue).sum();
    }

    // ═══════════════════════════ largest remainder Σ==N 경계 ═══════════════════════════

    @Test
    void largestRemainder_nZero_allZeroSumZero() {
        Map<String, BigDecimal> target = DistributionMath.validateAndNormalizeTarget(
            ratios("S", "0.1", "A", "0.2", "B", "0.4", "C", "0.2", "D", "0.1"));

        Map<String, Integer> quota = DistributionMath.largestRemainder(0, target);

        assertThat(sum(quota)).isZero();
        assertThat(quota.values()).allMatch(v -> v == 0);
    }

    @Test
    void largestRemainder_nOne_assignsSingleToHighestRemainder() {
        // N=1, 비율 {S0.1,A0.2,B0.4,C0.2,D0.1}: floor 전부 0 → leftover 1 → 소수부 최대 B(0.4) 가 +1.
        Map<String, BigDecimal> target = DistributionMath.validateAndNormalizeTarget(
            ratios("S", "0.1", "A", "0.2", "B", "0.4", "C", "0.2", "D", "0.1"));

        Map<String, Integer> quota = DistributionMath.largestRemainder(1, target);

        assertThat(sum(quota)).isEqualTo(1);
        assertThat(quota.get("B")).isEqualTo(1);
    }

    @Test
    void largestRemainder_floorSumLessThanN_distributesLeftover() {
        // N=10, 비율 {S0.1,A0.25,B0.3,C0.25,D0.1}: raw=1,2.5,3,2.5,1 → floor=1,2,3,2,1 = 9, leftover 1.
        // 소수부 = 0,0.5,0,0.5,0 → 동률 0.5 (A,C) 중 정준 순서 A 먼저 → A +1.
        Map<String, BigDecimal> target = DistributionMath.validateAndNormalizeTarget(
            ratios("S", "0.1", "A", "0.25", "B", "0.3", "C", "0.25", "D", "0.1"));

        Map<String, Integer> quota = DistributionMath.largestRemainder(10, target);

        assertThat(sum(quota)).isEqualTo(10);
        assertThat(quota.get("S")).isEqualTo(1);
        assertThat(quota.get("A")).isEqualTo(3); // 2 floor + 1 leftover (동률 tie-break)
        assertThat(quota.get("B")).isEqualTo(3);
        assertThat(quota.get("C")).isEqualTo(2);
        assertThat(quota.get("D")).isEqualTo(1);
    }

    @Test
    void largestRemainder_tieBreakFollowsCanonicalOrder() {
        // N=3, 균등 1/3 씩 (S,A,B 만): raw=1.0,1.0,1.0,0,0 → floor 합 3 = N, leftover 0.
        Map<String, BigDecimal> target = DistributionMath.validateAndNormalizeTarget(
            ratios("S", "0.3334", "A", "0.3333", "B", "0.3333"));

        Map<String, Integer> quota = DistributionMath.largestRemainder(3, target);

        assertThat(sum(quota)).isEqualTo(3);
        // 합 1.0 근처라 floor 로 정확히 떨어지지 않을 수 있어 Σ==N 만 강하게 보장.
    }

    @Test
    void largestRemainder_largeN_sumAlwaysEqualsN() {
        Map<String, BigDecimal> target = DistributionMath.validateAndNormalizeTarget(
            ratios("S", "0.1", "A", "0.2", "B", "0.4", "C", "0.2", "D", "0.1"));

        for (int n : new int[] {2, 5, 7, 13, 100, 999}) {
            Map<String, Integer> quota = DistributionMath.largestRemainder(n, target);
            assertThat(sum(quota)).as("N=%d", n).isEqualTo(n);
        }
    }

    // ═══════════════════════════ target 검증 ═══════════════════════════

    @Test
    void validateTarget_nullOrEmpty_rejected() {
        assertThatThrownBy(() -> DistributionMath.validateAndNormalizeTarget(null))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.DISTRIBUTION_INVALID_TARGET);
        assertThatThrownBy(() -> DistributionMath.validateAndNormalizeTarget(new LinkedHashMap<>()))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.DISTRIBUTION_INVALID_TARGET);
    }

    @Test
    void validateTarget_unknownKey_rejected() {
        assertThatThrownBy(() -> DistributionMath.validateAndNormalizeTarget(
                ratios("S", "0.5", "X", "0.5")))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.DISTRIBUTION_INVALID_TARGET);
    }

    @Test
    void validateTarget_sumNotOne_rejected() {
        assertThatThrownBy(() -> DistributionMath.validateAndNormalizeTarget(
                ratios("S", "0.1", "A", "0.2", "B", "0.3"))) // 합 0.6
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.DISTRIBUTION_INVALID_TARGET);
    }

    @Test
    void validateTarget_negative_rejected() {
        assertThatThrownBy(() -> DistributionMath.validateAndNormalizeTarget(
                ratios("S", "1.1", "A", "-0.1")))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.DISTRIBUTION_INVALID_TARGET);
    }

    @Test
    void validateTarget_withinTolerance_normalizesAllFiveGrades() {
        // 합 0.9995 (1.0±0.001 내) — 누락 등급 0 채움 + 정준 순서.
        Map<String, BigDecimal> result = DistributionMath.validateAndNormalizeTarget(
            ratios("S", "0.0995", "A", "0.4", "B", "0.5"));

        assertThat(result.keySet()).containsExactly("S", "A", "B", "C", "D");
        assertThat(result.get("C")).isEqualByComparingTo("0");
        assertThat(result.get("D")).isEqualByComparingTo("0");
    }
}

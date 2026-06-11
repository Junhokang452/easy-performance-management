/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.calibration.service;

import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 강제 분포 산식 SoT — P0-S4 (p0_s4_contract.md §5). largest remainder method + target 검증.
 *
 * <p>등급 정준 순서 {@code S → A → B → C → D} (동률 시 +1 배분 우선순위 = 이 순서). 분포 객체는
 * {@code LinkedHashMap} 으로 항상 5등급 (또는 +UNRATED) 키를 정준 순서로 보유 — FE 막대 렌더 정합.
 *
 * <p><strong>largest remainder (최대잔여법)</strong>: {@code floor(N × ratio_g)} 우선 배분 후
 * 잔여 {@code N − Σfloor} 를 소수부 큰 순 (동률 시 정준 순서) 으로 +1. <b>Σquota == N 보장</b>.
 */
final class DistributionMath {

    private DistributionMath() {
    }

    /** 정준 등급 순서 (배분 + 동률 tie-break). */
    static final List<String> GRADE_LIST = List.of("S", "A", "B", "C", "D");

    /** 등급 집합 (멤버십 검증). */
    static final Set<String> GRADES = Set.of("S", "A", "B", "C", "D");

    static final String UNRATED = "UNRATED";

    /** 비율 합 허용 오차 (1.0 ± 0.001). */
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.001");

    /**
     * 빈 카운트 맵 (정준 순서). {@code withUnrated=true} 면 UNRATED 버킷 추가 (현재 분포용).
     */
    static Map<String, Integer> emptyCounts(boolean withUnrated) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String g : GRADE_LIST) {
            map.put(g, 0);
        }
        if (withUnrated) {
            map.put(UNRATED, 0);
        }
        return map;
    }

    /**
     * target 분포 검증 + 정규화 (§4 DISTRIBUTION_INVALID_TARGET): null/empty → 거부. 키 ∉ {S,A,B,C,D} →
     * 거부. 음수 → 거부. 합 ≠ 1.0±0.001 → 거부. 통과 시 5등급 전부 채운 정준 순서 맵 (누락 등급 = 0.0).
     */
    static Map<String, BigDecimal> validateAndNormalizeTarget(Map<String, BigDecimal> raw) {
        if (raw == null || raw.isEmpty()) {
            throw invalidTarget("target distribution is null or empty", Map.of());
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : raw.entrySet()) {
            String key = e.getKey();
            BigDecimal ratio = e.getValue();
            if (!GRADES.contains(key)) {
                throw invalidTarget("unknown grade key", Map.of("key", String.valueOf(key)));
            }
            if (ratio == null) {
                throw invalidTarget("null ratio", Map.of("key", key));
            }
            if (ratio.compareTo(BigDecimal.ZERO) < 0) {
                throw invalidTarget("negative ratio", Map.of("key", key, "ratio", ratio.toPlainString()));
            }
            sum = sum.add(ratio);
        }
        if (sum.subtract(ONE).abs().compareTo(TOLERANCE) > 0) {
            throw invalidTarget("ratio sum != 1.0", Map.of("sum", sum.toPlainString()));
        }
        // 정준 순서 5등급 전부 채움 (누락 = 0).
        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        for (String g : GRADE_LIST) {
            normalized.put(g, raw.getOrDefault(g, BigDecimal.ZERO));
        }
        return normalized;
    }

    /**
     * largest remainder 배분 — {@code N × ratio_g} floor 우선 + 잔여 소수부 큰 순 (동률 정준 순서) +1.
     * Σquota == N 보장. {@code target} 은 이미 정규화 (5등급 정준 순서) 가정.
     */
    static Map<String, Integer> largestRemainder(int n, Map<String, BigDecimal> target) {
        Map<String, Integer> quota = new LinkedHashMap<>();
        for (String g : GRADE_LIST) {
            quota.put(g, 0);
        }
        if (n <= 0) {
            return quota; // Σ = 0 = N.
        }
        BigDecimal bigN = BigDecimal.valueOf(n);
        List<Remainder> remainders = new ArrayList<>(GRADE_LIST.size());
        int floorSum = 0;
        int order = 0;
        for (String g : GRADE_LIST) {
            BigDecimal ratio = target.getOrDefault(g, BigDecimal.ZERO);
            BigDecimal raw = bigN.multiply(ratio);
            int floor = raw.setScale(0, java.math.RoundingMode.FLOOR).intValueExact();
            BigDecimal frac = raw.subtract(BigDecimal.valueOf(floor));
            quota.put(g, floor);
            floorSum += floor;
            remainders.add(new Remainder(g, frac, order++));
        }
        int leftover = n - floorSum;
        // 소수부 DESC, 동률 시 정준 순서 ASC.
        remainders.sort(Comparator
            .comparing((Remainder r) -> r.frac).reversed()
            .thenComparingInt(r -> r.order));
        for (int i = 0; i < leftover && i < remainders.size(); i++) {
            String g = remainders.get(i).grade;
            quota.merge(g, 1, Integer::sum);
        }
        return quota;
    }

    private static ApiException invalidTarget(String reason, Map<String, Object> extra) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("reason", reason);
        args.putAll(extra);
        return new ApiException(PerformanceErrorCode.DISTRIBUTION_INVALID_TARGET, args);
    }

    /** 등급별 소수부 (잔여 배분 정렬용). */
    private record Remainder(String grade, BigDecimal frac, int order) {}
}

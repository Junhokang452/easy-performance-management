/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.calibration.service;

import com.easyperformance.domain.calibration.dto.CalibrationDtos.AdjustmentEntry;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.SimulationEntry;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 캘리브레이션 도메인 jsonb 직렬화/역직렬화 + Clock — P0-S4 (p0_s4_contract.md §1 D2 패턴).
 *
 * <p>lib OutboxEvent 패턴 정합 — ObjectMapper {@code USE_BIG_DECIMAL_FOR_FLOATS} (비율 정밀도 보존) +
 * JavaTimeModule (Instant ISO-8601). 손상된 jsonb 행은 graceful null/빈 리스트 폴백 (운영 사고 방어).
 *
 * <p>{@link Clock} 주입 (jobstructure/security/ReviewService 패턴) — adjustment/simulation entry 의
 * {@code at} + confirmed/applied 시각 테스트 친화 고정.
 */
final class CalibrationJson {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    CalibrationJson() {
        this(Clock.systemUTC());
    }

    CalibrationJson(Clock clock) {
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.clock = clock;
    }

    Instant now() {
        return Instant.now(clock);
    }

    // ─────────────────────────────────────────────────────────────────────
    // UUID 배열 (participant_ids)
    // ─────────────────────────────────────────────────────────────────────

    /** UUID 배열 → jsonb String. null → null, 빈 리스트 → "[]". */
    String serializeUuids(List<UUID> ids) {
        if (ids == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw serializeFailed("participant_ids", e);
        }
    }

    /** jsonb String → UUID 리스트. null/blank → null. 손상 → null. */
    List<UUID> parseUuids(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<String> raw = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            List<UUID> result = new ArrayList<>(raw.size());
            for (String s : raw) {
                result.add(UUID.fromString(s));
            }
            return result;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 비율 맵 (policy_distribution / target) + 카운트 맵 (actual_distribution)
    // ─────────────────────────────────────────────────────────────────────

    /** 비율 맵 → jsonb String (정준 순서 보존). null → null. */
    String serializeRatioMap(Map<String, BigDecimal> map) {
        if (map == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw serializeFailed("ratio-map", e);
        }
    }

    /**
     * jsonb String → 비율 맵 (정준 순서 5등급 채움). null/blank → null (분포 미적용). 손상 → null.
     * USE_BIG_DECIMAL_FOR_FLOATS 로 BigDecimal 보존.
     */
    Map<String, BigDecimal> parseRatioMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> raw =
                objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Map<String, BigDecimal> result = new LinkedHashMap<>();
            for (String g : DistributionMath.GRADE_LIST) {
                Object v = raw.get(g);
                result.put(g, v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()));
            }
            return result;
        } catch (JsonProcessingException | NumberFormatException e) {
            return null;
        }
    }

    /** 카운트 맵 → jsonb String (정준 순서 보존). null → null. */
    String serializeCountMap(Map<String, Integer> map) {
        if (map == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw serializeFailed("count-map", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // adjustment_log (append 배열)
    // ─────────────────────────────────────────────────────────────────────

    /** 기존 adjustment_log 에 entry append → jsonb String. */
    String appendAdjustment(String existing, AdjustmentEntry entry) {
        List<AdjustmentEntry> log = parseAdjustmentLog(existing);
        List<AdjustmentEntry> next = (log == null) ? new ArrayList<>() : new ArrayList<>(log);
        next.add(entry);
        try {
            return objectMapper.writeValueAsString(next);
        } catch (JsonProcessingException e) {
            throw serializeFailed("adjustment_log", e);
        }
    }

    /** jsonb String → AdjustmentEntry 리스트. null/blank → null. 손상 → null. */
    List<AdjustmentEntry> parseAdjustmentLog(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AdjustmentEntry>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // simulation_log (append 배열)
    // ─────────────────────────────────────────────────────────────────────

    /** 기존 simulation_log 에 entry append → jsonb String. */
    String appendSimulation(String existing, SimulationEntry entry) {
        List<SimulationEntry> log = parseSimulationLog(existing);
        List<SimulationEntry> next = (log == null) ? new ArrayList<>() : new ArrayList<>(log);
        next.add(entry);
        try {
            return objectMapper.writeValueAsString(next);
        } catch (JsonProcessingException e) {
            throw serializeFailed("simulation_log", e);
        }
    }

    /** jsonb String → SimulationEntry 리스트. null/blank → null. 손상 → null. */
    List<SimulationEntry> parseSimulationLog(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SimulationEntry>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private ApiException serializeFailed(String field, Throwable cause) {
        return new ApiException(PerformanceErrorCode.DISTRIBUTION_INVALID_TARGET,
            Map.of("error", "jsonb-serialization-failed", "field", field), cause);
    }
}

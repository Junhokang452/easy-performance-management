/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.report.service;

import com.easyperformance.domain.report.dto.ReportDtos.ReportContent;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

/**
 * 성과 리포트 도메인 content jsonb 직렬화/역직렬화 — P0-S5 (p0_s5_contract.md §5).
 *
 * <p>lib OutboxEvent 패턴 정합 (P0-S1 D2 / CalibrationJson 동형) — ObjectMapper
 * {@code USE_BIG_DECIMAL_FOR_FLOATS} (분포 비율 + 점수 정밀도 보존) + JavaTimeModule (방어적 — nextAction
 * P1 확장 대비). 손상된 jsonb 행은 graceful null 폴백 (운영 사고 방어 — ReviewService.parseSnapshot 정합).
 *
 * <p>content 는 발행 시점 생성 후 불변 (entity {@code updatable=false}) — parse 는 Response 노출 전용.
 */
final class ReportJson {

    private final ObjectMapper objectMapper;

    ReportJson() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    /** ReportContent → jsonb String (발행 시점 동결). 직렬화 실패는 발행 차단 (운영 사고). */
    String serializeContent(ReportContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new ApiException(PerformanceErrorCode.REPORT_NOT_FOUND,
                Map.of("error", "content-serialization-failed"), e);
        }
    }

    /** jsonb String → ReportContent (Response 노출). null/blank → null. 손상 → null (graceful 폴백). */
    ReportContent parseContent(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ReportContent.class);
        } catch (JsonProcessingException e) {
            // 손상된 행은 운영 사고 — graceful null 폴백 (ReviewService.parseSnapshot 정합).
            return null;
        }
    }
}

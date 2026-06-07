/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.selfevaluation.dto;

import com.easyperformance.domain.selfevaluation.entity.SelfEvaluation;
import com.easyperformance.domain.selfevaluation.entity.SelfEvaluationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * SelfEvaluation DTO 모음 — ADR-026 명명 정합 ({@code {Entity}CreateRequest / UpdateRequest / Response}).
 *
 * <p>엔티티 프런트 노출 금지 (easy-ware 12 규칙 SSOT 레이어) — entity → DTO 변환은 service 트랜잭션 안에서.
 */
public final class SelfEvaluationDtos {

    private SelfEvaluationDtos() {
    }

    public record SelfEvaluationCreateRequest(
        @NotNull UUID employeeId,
        UUID cycleId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @Size(max = 8000) String content,
        Double score
    ) {}

    public record SelfEvaluationUpdateRequest(
        @Size(max = 8000) String content,
        Double score,
        SelfEvaluationStatus status
    ) {}

    public record SelfEvaluationResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        UUID cycleId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String content,
        Double score,
        SelfEvaluationStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static SelfEvaluationResponse from(SelfEvaluation e) {
            return new SelfEvaluationResponse(
                e.getId(),
                e.getTenantId(),
                e.getEmployeeId(),
                e.getCycleId(),
                e.getPeriodStart(),
                e.getPeriodEnd(),
                e.getContent(),
                e.getScore(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
            );
        }
    }
}

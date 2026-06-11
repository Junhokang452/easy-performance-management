/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationcycle.dto;

import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.CycleType;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationpolicy.dto.EvaluationPolicyDtos.PolicyUpsertRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * EvaluationCycle DTO 모음 — ADR-026 명명 정합 ({@code {Entity}CreateRequest / UpdateRequest /
 * StatusTransitionRequest / Response}).
 *
 * <p>{@link CycleCreateRequest} 의 {@code policy} 가 null 이면 service 가 default policy 자동 생성
 * (HYBRID / S_A_B_C_D / appealEnabled=false / bscEnabled=false / cutoff=3 / forcedDistribution=null).
 */
public final class EvaluationCycleDtos {

    private EvaluationCycleDtos() {
    }

    public record CycleCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotNull CycleType cycleType,
        @Valid PolicyUpsertRequest policy
    ) {}

    public record CycleUpdateRequest(
        @Size(max = 100) String name,
        LocalDate periodStart,
        LocalDate periodEnd,
        CycleType cycleType
    ) {}

    public record CycleStatusTransitionRequest(
        @NotNull CycleStatus toStatus
    ) {}

    public record CycleResponse(
        UUID id,
        UUID tenantId,
        String name,
        LocalDate periodStart,
        LocalDate periodEnd,
        CycleType cycleType,
        CycleStatus status,
        UUID policyId,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static CycleResponse from(EvaluationCycle e, UUID policyId) {
            return new CycleResponse(
                e.getId(),
                e.getTenantId(),
                e.getName(),
                e.getPeriodStart(),
                e.getPeriodEnd(),
                e.getCycleType(),
                e.getStatus(),
                policyId,
                e.getCreatedAt(),
                e.getUpdatedAt()
            );
        }
    }
}

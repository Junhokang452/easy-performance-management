/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.personalokr.dto;

import com.easyperformance.domain.personalokr.entity.PersonalOkr;
import com.easyperformance.domain.personalokr.entity.PersonalOkrStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PersonalOkrDtos {

    private PersonalOkrDtos() {
    }

    public record PersonalOkrCreateRequest(
        @NotNull UUID employeeId,
        @NotBlank @Size(max = 500) String objective,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        Double progress
    ) {}

    public record PersonalOkrUpdateRequest(
        @Size(max = 500) String objective,
        Double progress,
        PersonalOkrStatus status
    ) {}

    public record PersonalOkrResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        String objective,
        Double progress,
        LocalDate periodStart,
        LocalDate periodEnd,
        PersonalOkrStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static PersonalOkrResponse from(PersonalOkr e) {
            return new PersonalOkrResponse(
                e.getId(),
                e.getTenantId(),
                e.getEmployeeId(),
                e.getObjective(),
                e.getProgress(),
                e.getPeriodStart(),
                e.getPeriodEnd(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
            );
        }
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.reflectionjournal.dto;

import com.easyperformance.domain.reflectionjournal.entity.ReflectionJournal;
import com.easyperformance.domain.reflectionjournal.entity.ReflectionMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ReflectionJournalDtos {

    private ReflectionJournalDtos() {
    }

    public record ReflectionJournalCreateRequest(
        @NotNull UUID employeeId,
        @NotNull LocalDate reflectionDate,
        ReflectionMethod method,
        @NotBlank @Size(max = 8000) String content,
        Boolean isPrivate
    ) {}

    public record ReflectionJournalUpdateRequest(
        @Size(max = 8000) String content,
        ReflectionMethod method,
        Boolean isPrivate
    ) {}

    public record ReflectionJournalResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        LocalDate reflectionDate,
        ReflectionMethod method,
        String content,
        Boolean isPrivate,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static ReflectionJournalResponse from(ReflectionJournal e) {
            return new ReflectionJournalResponse(
                e.getId(),
                e.getTenantId(),
                e.getEmployeeId(),
                e.getReflectionDate(),
                e.getMethod(),
                e.getContent(),
                e.getIsPrivate(),
                e.getCreatedAt(),
                e.getUpdatedAt()
            );
        }
    }
}

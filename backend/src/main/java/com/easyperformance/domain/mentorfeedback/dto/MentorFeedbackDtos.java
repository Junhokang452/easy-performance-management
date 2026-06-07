/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.mentorfeedback.dto;

import com.easyperformance.domain.mentorfeedback.entity.FeedbackCategory;
import com.easyperformance.domain.mentorfeedback.entity.MentorFeedback;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class MentorFeedbackDtos {

    private MentorFeedbackDtos() {
    }

    public record MentorFeedbackCreateRequest(
        @NotNull UUID mentorId,
        @NotNull UUID menteeId,
        @NotNull LocalDate feedbackDate,
        FeedbackCategory category,
        @NotBlank @Size(max = 8000) String content
    ) {}

    public record MentorFeedbackUpdateRequest(
        @Size(max = 8000) String content,
        FeedbackCategory category,
        Boolean acknowledged
    ) {}

    public record MentorFeedbackResponse(
        UUID id,
        UUID tenantId,
        UUID mentorId,
        UUID menteeId,
        LocalDate feedbackDate,
        FeedbackCategory category,
        String content,
        Boolean acknowledged,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static MentorFeedbackResponse from(MentorFeedback e) {
            return new MentorFeedbackResponse(
                e.getId(),
                e.getTenantId(),
                e.getMentorId(),
                e.getMenteeId(),
                e.getFeedbackDate(),
                e.getCategory(),
                e.getContent(),
                e.getAcknowledged(),
                e.getCreatedAt(),
                e.getUpdatedAt()
            );
        }
    }
}

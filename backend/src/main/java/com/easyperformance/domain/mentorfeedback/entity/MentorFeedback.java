/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.mentorfeedback.entity;

import com.easyperformance.common.UuidV7;
import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 멘토 피드백 (Mentor Feedback) — 멘토 → 멘티 1:1 피드백.
 *
 * <p>본 슬라이스 = MENTOR → MENTEE 단일 방향. 단계 4 진입 시 CFR (Conversations / Feedback / Recognition)
 * 4 모드 분기 (MANAGER_REPORT / REPORT_MANAGER / MENTOR_MENTEE / PEER_RECOGNITION).
 *
 * <p>인덱스 — {@code (tenant_id, mentee_id)}, {@code (tenant_id, mentor_id)}, {@code (tenant_id, feedback_date)}.
 */
@Entity
@Table(
    name = "mentor_feedback",
    indexes = {
        @Index(name = "ix_mentor_feedback_tenant_mentee", columnList = "tenant_id, mentee_id"),
        @Index(name = "ix_mentor_feedback_tenant_mentor", columnList = "tenant_id, mentor_id"),
        @Index(name = "ix_mentor_feedback_tenant_date",   columnList = "tenant_id, feedback_date")
    }
)
public class MentorFeedback extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /** 멘토 employee UUID. */
    @Column(name = "mentor_id", columnDefinition = "uuid", nullable = false)
    private UUID mentorId;

    /** 멘티 employee UUID. */
    @Column(name = "mentee_id", columnDefinition = "uuid", nullable = false)
    private UUID menteeId;

    @Column(name = "feedback_date", nullable = false)
    private LocalDate feedbackDate;

    /** 피드백 카테고리. */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private FeedbackCategory category;

    /** 피드백 본문 (최대 8000자 — 자매품 정합). */
    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;

    /** 멘티 확인 여부. */
    @Column(name = "acknowledged", nullable = false)
    private Boolean acknowledged;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
        if (this.category == null) {
            this.category = FeedbackCategory.GROWTH;
        }
        if (this.acknowledged == null) {
            this.acknowledged = false;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getMentorId() { return mentorId; }
    public void setMentorId(UUID mentorId) { this.mentorId = mentorId; }

    public UUID getMenteeId() { return menteeId; }
    public void setMenteeId(UUID menteeId) { this.menteeId = menteeId; }

    public LocalDate getFeedbackDate() { return feedbackDate; }
    public void setFeedbackDate(LocalDate feedbackDate) { this.feedbackDate = feedbackDate; }

    public FeedbackCategory getCategory() { return category; }
    public void setCategory(FeedbackCategory category) { this.category = category; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Boolean getAcknowledged() { return acknowledged; }
    public void setAcknowledged(Boolean acknowledged) { this.acknowledged = acknowledged; }
}

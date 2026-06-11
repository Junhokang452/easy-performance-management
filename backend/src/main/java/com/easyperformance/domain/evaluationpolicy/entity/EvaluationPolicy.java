/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationpolicy.entity;

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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * 평가 정책 (EvaluationPolicy) — P0-S1 (decisions_2026-06-11.md SoT).
 *
 * <p>도메인 본질: EvaluationCycle 1:1 — 본 사이클에 적용되는 운영 규칙 (분포 / 등급 / 이의제기 / BSC /
 * 성과로그 기준일). cycle.status 가 PLANNED 동안만 distributionMode/ratingScale 변경 가능
 * (이후 POLICY_LOCKED 409). forcedDistribution 만 사이클 진행 중에도 조정 허용 (HR Tech 모범).
 *
 * <p>{@code (tenant_id, cycle_id)} 유니크 — cycle 1개당 정책 1개만 존재 (1:1). FK
 * {@code cycle_id → evaluation_cycle(id) ON DELETE CASCADE} (cycle 삭제 시 정책 동반 삭제 —
 * cycle 자체가 PLANNED 일 때만 삭제 가능하므로 안전).
 *
 * <p>JSONB 매핑: {@code forcedDistribution} 은 lib OutboxEvent 패턴 정합 — {@code @JdbcTypeCode(JSON)}
 * + {@code String payload} + {@code columnDefinition = "jsonb"}. BigDecimal 정밀도 보존 위해
 * service 계층에서 ObjectMapper {@code USE_BIG_DECIMAL_FOR_FLOATS} 옵션 적용.
 *
 * <p>인덱스: {@code (tenant_id, cycle_id)} (uq 와 동일 컬럼이지만 명시 인덱스로 lookup 가속).
 */
@Entity
@Table(
    name = "evaluation_policy",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_policy_tenant_cycle", columnNames = {"tenant_id", "cycle_id"})
    },
    indexes = {
        @Index(name = "ix_policy_tenant_cycle", columnList = "tenant_id, cycle_id")
    }
)
public class EvaluationPolicy extends TenantAwareAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    /** FK → evaluation_cycle(id) ON DELETE CASCADE. */
    @Column(name = "cycle_id", columnDefinition = "uuid", nullable = false)
    private UUID cycleId;

    /** 분포 모드 (HYBRID / FORCED / ABSOLUTE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "distribution_mode", length = 20, nullable = false)
    private DistributionMode distributionMode;

    /** 등급 스케일 (S_A_B_C_D / ONE_TO_FIVE / ONE_TO_HUNDRED). */
    @Enumerated(EnumType.STRING)
    @Column(name = "rating_scale", length = 20, nullable = false)
    private RatingScale ratingScale;

    /** 이의제기 허용 여부 (기본 false). */
    @Column(name = "appeal_enabled", nullable = false)
    private boolean appealEnabled;

    /** BSC (Balanced Scorecard) 적용 여부 (기본 false). */
    @Column(name = "bsc_enabled", nullable = false)
    private boolean bscEnabled;

    /** 성과 로그 기준일 (D-N) — 0~30 (기본 3일). DB CHECK {@code ck_policy_cutoff_days}. */
    @Column(name = "achievement_log_cutoff_days", nullable = false)
    private Integer achievementLogCutoffDays;

    /**
     * 강제 분포 JSON (예: {@code {"S":0.10,"A":0.20,"B":0.40,"C":0.20,"D":0.10}}).
     *
     * <p>service 계층에서 BigDecimal Map ↔ String 변환 (ObjectMapper + USE_BIG_DECIMAL_FOR_FLOATS).
     * ABSOLUTE 모드에선 null 저장. FORCED/HYBRID 에서 null/empty 면 검증 실패 (POLICY_*).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forced_distribution", columnDefinition = "jsonb")
    private String forcedDistribution;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidV7.generate();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCycleId() { return cycleId; }
    public void setCycleId(UUID cycleId) { this.cycleId = cycleId; }

    public DistributionMode getDistributionMode() { return distributionMode; }
    public void setDistributionMode(DistributionMode distributionMode) { this.distributionMode = distributionMode; }

    public RatingScale getRatingScale() { return ratingScale; }
    public void setRatingScale(RatingScale ratingScale) { this.ratingScale = ratingScale; }

    public boolean isAppealEnabled() { return appealEnabled; }
    public void setAppealEnabled(boolean appealEnabled) { this.appealEnabled = appealEnabled; }

    public boolean isBscEnabled() { return bscEnabled; }
    public void setBscEnabled(boolean bscEnabled) { this.bscEnabled = bscEnabled; }

    public Integer getAchievementLogCutoffDays() { return achievementLogCutoffDays; }
    public void setAchievementLogCutoffDays(Integer achievementLogCutoffDays) { this.achievementLogCutoffDays = achievementLogCutoffDays; }

    public String getForcedDistribution() { return forcedDistribution; }
    public void setForcedDistribution(String forcedDistribution) { this.forcedDistribution = forcedDistribution; }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.dto;

import com.easyperformance.domain.kpi.entity.BscPerspective;
import com.easyperformance.domain.kpi.entity.KpiActual;
import com.easyperformance.domain.kpi.entity.KpiActualSource;
import com.easyperformance.domain.kpi.entity.KpiAssignment;
import com.easyperformance.domain.kpi.entity.KpiNode;
import com.easyperformance.domain.kpi.entity.KpiNodeSource;
import com.easyperformance.domain.kpi.entity.KpiTree;
import com.easyperformance.domain.kpi.entity.KpiTreeLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * KPI 도메인 DTO 모음 — P0-S2 (p0_s2_contract.md §4). ADR-026 명명 정합
 * ({@code {Entity}CreateRequest / UpdateRequest / Response}).
 *
 * <p>Response shape 는 계약 §4 그대로 (FE 에이전트 병렬 구현 — 이탈 금지). 파생 값
 * (childWeightSum / childWeightComplete / assignmentCount / effective weight·target / latestActual /
 * achievementRate / superseded) 은 service 가 계산해 정적 팩토리에 주입.
 */
public final class KpiDtos {

    private KpiDtos() {
    }

    // ─────────────────────────────────────────────────────────────────────
    // KpiTree
    // ─────────────────────────────────────────────────────────────────────

    public record KpiTreeCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull KpiTreeLevel level,
        UUID ownerOrgUnitId,
        Boolean bscEnabled
    ) {}

    public record KpiTreeUpdateRequest(
        @Size(max = 100) String name,
        KpiTreeLevel level,
        UUID ownerOrgUnitId,
        Boolean bscEnabled
    ) {}

    public record KpiTreeResponse(
        UUID id,
        UUID cycleId,
        String name,
        KpiTreeLevel level,
        UUID ownerOrgUnitId,
        boolean bscEnabled,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static KpiTreeResponse from(KpiTree e) {
            return new KpiTreeResponse(
                e.getId(),
                e.getCycleId(),
                e.getName(),
                e.getLevel(),
                e.getOwnerOrgUnitId(),
                e.isBscEnabled(),
                e.getCreatedAt(),
                e.getUpdatedAt()
            );
        }
    }

    public record KpiTreeDetailResponse(
        UUID id,
        UUID cycleId,
        String name,
        KpiTreeLevel level,
        UUID ownerOrgUnitId,
        boolean bscEnabled,
        Instant createdAt,
        Instant updatedAt,
        List<KpiNodeResponse> nodes
    ) {
        public static KpiTreeDetailResponse from(KpiTree e, List<KpiNodeResponse> nodes) {
            return new KpiTreeDetailResponse(
                e.getId(),
                e.getCycleId(),
                e.getName(),
                e.getLevel(),
                e.getOwnerOrgUnitId(),
                e.isBscEnabled(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                nodes
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // KpiNode
    // ─────────────────────────────────────────────────────────────────────

    public record KpiNodeCreateRequest(
        UUID parentId,
        @NotBlank @Size(max = 200) String label,
        @NotNull BigDecimal weight,
        BigDecimal target,
        @Size(max = 20) String unit,
        BscPerspective bscPerspective,
        KpiNodeSource source,
        UUID cascadeFromId
    ) {}

    public record KpiNodeUpdateRequest(
        @Size(max = 200) String label,
        BigDecimal weight,
        BigDecimal target,
        @Size(max = 20) String unit,
        BscPerspective bscPerspective
    ) {}

    public record KpiNodeResponse(
        UUID id,
        UUID treeId,
        UUID parentId,
        String label,
        BigDecimal weight,
        BigDecimal target,
        String unit,
        BscPerspective bscPerspective,
        KpiNodeSource source,
        UUID cascadeFromId,
        BigDecimal childWeightSum,
        boolean childWeightComplete,
        long assignmentCount,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static KpiNodeResponse from(KpiNode e,
                                           BigDecimal childWeightSum,
                                           boolean childWeightComplete,
                                           long assignmentCount) {
            return new KpiNodeResponse(
                e.getId(),
                e.getTreeId(),
                e.getParentId(),
                e.getLabel(),
                e.getWeight(),
                e.getTarget(),
                e.getUnit(),
                e.getBscPerspective(),
                e.getSource(),
                e.getCascadeFromId(),
                childWeightSum,
                childWeightComplete,
                assignmentCount,
                e.getCreatedAt(),
                e.getUpdatedAt()
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // KpiAssignment
    // ─────────────────────────────────────────────────────────────────────

    public record KpiAssignmentCreateRequest(
        @NotNull UUID employeeId,
        BigDecimal weight,
        BigDecimal targetOverride
    ) {}

    public record KpiAssignmentUpdateRequest(
        BigDecimal weight,
        BigDecimal targetOverride
    ) {}

    public record KpiAssignmentResponse(
        UUID id,
        UUID kpiNodeId,
        UUID employeeId,
        BigDecimal weight,
        BigDecimal targetOverride,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static KpiAssignmentResponse from(KpiAssignment e) {
            return new KpiAssignmentResponse(
                e.getId(),
                e.getKpiNodeId(),
                e.getEmployeeId(),
                e.getWeight(),
                e.getTargetOverride(),
                e.getCreatedAt(),
                e.getUpdatedAt()
            );
        }
    }

    /**
     * My KPI 응답 — effective weight/target + latest actual + achievement rate. service 가 node/tree
     * 컨텍스트 + latest actual 계산을 주입 (assignment 단독으로 산출 불가).
     */
    public record MyKpiAssignmentResponse(
        UUID id,
        UUID kpiNodeId,
        String nodeLabel,
        UUID treeId,
        String treeName,
        UUID cycleId,
        BigDecimal weight,
        BigDecimal target,
        String unit,
        BscPerspective bscPerspective,
        KpiNodeSource source,
        BigDecimal latestActualValue,
        LocalDate latestActualAsOfDate,
        BigDecimal achievementRate
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // KpiActual (append-only)
    // ─────────────────────────────────────────────────────────────────────

    public record KpiActualCreateRequest(
        @NotNull LocalDate asOfDate,
        @NotNull BigDecimal actualValue,
        @Size(max = 500) String evidenceUrl,
        String comment
    ) {}

    public record KpiActualSupersedeRequest(
        LocalDate asOfDate,
        @NotNull BigDecimal actualValue,
        @Size(max = 500) String evidenceUrl,
        String comment
    ) {}

    public record KpiActualResponse(
        UUID id,
        UUID kpiAssignmentId,
        LocalDate asOfDate,
        BigDecimal actualValue,
        KpiActualSource source,
        UUID reportedBy,
        String evidenceUrl,
        String comment,
        UUID supersedesId,
        boolean superseded,
        Instant createdAt
    ) {
        public static KpiActualResponse from(KpiActual e, boolean superseded) {
            return new KpiActualResponse(
                e.getId(),
                e.getKpiAssignmentId(),
                e.getAsOfDate(),
                e.getActualValue(),
                e.getSource(),
                e.getReportedBy(),
                e.getEvidenceUrl(),
                e.getComment(),
                e.getSupersedesId(),
                superseded,
                e.getCreatedAt()
            );
        }
    }
}

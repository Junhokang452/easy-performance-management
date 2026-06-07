/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.selfevaluation.service;

import com.easyperformance.common.TenantSupport;
import com.easyperformance.domain.selfevaluation.dto.SelfEvaluationDtos.SelfEvaluationCreateRequest;
import com.easyperformance.domain.selfevaluation.dto.SelfEvaluationDtos.SelfEvaluationResponse;
import com.easyperformance.domain.selfevaluation.dto.SelfEvaluationDtos.SelfEvaluationUpdateRequest;
import com.easyperformance.domain.selfevaluation.entity.SelfEvaluation;
import com.easyperformance.domain.selfevaluation.entity.SelfEvaluationStatus;
import com.easyperformance.domain.selfevaluation.repository.SelfEvaluationRepository;
import com.easyware.platform.error.ApiException;
import com.easyware.platform.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * SelfEvaluation Service — 비즈니스 SSOT (검증 + 상태 머신).
 *
 * <p>테넌트 격리: {@link TenantSupport#currentTenantId()} 위임 — 모든 쿼리에 tenant_id 필수 (easy-ware
 * 규칙 #10). DTO 매핑은 트랜잭션 안에서 수행 — {@code open-in-view=false} 환경에서 lazy 회피.
 *
 * <p>ADR-026 명명: {@code create / get / update / delete} + {@code list*} 조회.
 */
@Service
public class SelfEvaluationService {

    private final SelfEvaluationRepository repository;

    public SelfEvaluationService(SelfEvaluationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SelfEvaluationResponse create(SelfEvaluationCreateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        SelfEvaluation entity = new SelfEvaluation();
        entity.setTenantId(tenantId);
        entity.setEmployeeId(request.employeeId());
        entity.setCycleId(request.cycleId());
        entity.setPeriodStart(request.periodStart());
        entity.setPeriodEnd(request.periodEnd());
        entity.setContent(request.content());
        entity.setScore(request.score());
        entity.setStatus(SelfEvaluationStatus.DRAFT);
        return SelfEvaluationResponse.from(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public SelfEvaluationResponse get(UUID id) {
        UUID tenantId = TenantSupport.currentTenantId();
        SelfEvaluation entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("entity", "SelfEvaluation", "id", id)));
        return SelfEvaluationResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public Page<SelfEvaluationResponse> list(Pageable pageable) {
        UUID tenantId = TenantSupport.currentTenantId();
        return repository.findAllByTenantId(tenantId, pageable).map(SelfEvaluationResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<SelfEvaluationResponse> listByEmployee(UUID employeeId, Pageable pageable) {
        UUID tenantId = TenantSupport.currentTenantId();
        return repository.findAllByTenantIdAndEmployeeId(tenantId, employeeId, pageable)
            .map(SelfEvaluationResponse::from);
    }

    @Transactional
    public SelfEvaluationResponse update(UUID id, SelfEvaluationUpdateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        SelfEvaluation entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("entity", "SelfEvaluation", "id", id)));
        if (request.content() != null) entity.setContent(request.content());
        if (request.score() != null) entity.setScore(request.score());
        if (request.status() != null) {
            validateStatusTransition(entity.getStatus(), request.status());
            entity.setStatus(request.status());
        }
        return SelfEvaluationResponse.from(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantSupport.currentTenantId();
        SelfEvaluation entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("entity", "SelfEvaluation", "id", id)));
        repository.delete(entity);
    }

    /** 상태 전이 검증 — DRAFT → SUBMITTED → REVIEWED → FINALIZED. 역행 금지. */
    private void validateStatusTransition(SelfEvaluationStatus from, SelfEvaluationStatus to) {
        if (from == to) return;
        boolean allowed = switch (from) {
            case DRAFT -> to == SelfEvaluationStatus.SUBMITTED;
            case SUBMITTED -> to == SelfEvaluationStatus.REVIEWED;
            case REVIEWED -> to == SelfEvaluationStatus.FINALIZED;
            case FINALIZED -> false;
        };
        if (!allowed) {
            throw new ApiException(ErrorCode.UNPROCESSABLE,
                Map.of("from", from.name(), "to", to.name(), "entity", "SelfEvaluation"));
        }
    }
}

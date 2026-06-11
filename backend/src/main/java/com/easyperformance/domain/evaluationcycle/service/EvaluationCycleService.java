/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationcycle.service;

import com.easyperformance.common.TenantSupport;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleCreateRequest;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleResponse;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleStatusTransitionRequest;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleUpdateRequest;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.evaluationpolicy.dto.EvaluationPolicyDtos.PolicyUpsertRequest;
import com.easyperformance.domain.evaluationpolicy.entity.EvaluationPolicy;
import com.easyperformance.domain.evaluationpolicy.repository.EvaluationPolicyRepository;
import com.easyperformance.domain.evaluationpolicy.service.EvaluationPolicyService;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * EvaluationCycle Service — 비즈니스 SSOT (검증 + 8단계 상태 머신).
 *
 * <p>테넌트 격리: {@link TenantSupport#currentTenantId()} 위임 — 모든 쿼리에 tenant_id 필수
 * (easy-ware 규칙 #10). DTO 매핑은 트랜잭션 안에서 수행.
 *
 * <p>ADR-026 명명: {@code create / get / list / update / delete} + {@code transition} (상태 전이).
 *
 * <p>create 시 PolicyUpsertRequest 가 null 이면 default policy 자동 생성. GOAL_SETTING 전이 시
 * policy 가 존재해야 함 (없으면 POLICY_NOT_FOUND 422).
 */
@Service
public class EvaluationCycleService {

    /** PLANNED 외 status 에서 delete 거부. */
    private static final Set<CycleStatus> DELETABLE_STATUSES = EnumSet.of(CycleStatus.PLANNED);

    private final EvaluationCycleRepository repository;
    private final EvaluationPolicyRepository policyRepository;
    private final EvaluationPolicyService policyService;

    public EvaluationCycleService(EvaluationCycleRepository repository,
                                  EvaluationPolicyRepository policyRepository,
                                  EvaluationPolicyService policyService) {
        this.repository = repository;
        this.policyRepository = policyRepository;
        this.policyService = policyService;
    }

    @Transactional
    public CycleResponse create(CycleCreateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        validatePeriod(request.periodStart(), request.periodEnd());
        validateDuplicateName(tenantId, request.name(), null);

        EvaluationCycle entity = new EvaluationCycle();
        entity.setTenantId(tenantId);
        entity.setName(request.name());
        entity.setPeriodStart(request.periodStart());
        entity.setPeriodEnd(request.periodEnd());
        entity.setCycleType(request.cycleType());
        entity.setStatus(CycleStatus.PLANNED);
        EvaluationCycle saved = repository.save(entity);

        // Policy 자동 생성 — request.policy 가 null 이면 default, 아니면 upsert (검증 내장).
        EvaluationPolicy policy;
        PolicyUpsertRequest pr = request.policy();
        if (pr == null) {
            policy = policyService.createDefault(tenantId, saved.getId());
        } else {
            // upsert 가 내부적으로 distribution + ratingScaleKeys 검증 + lock 검증 수행.
            // 신규 cycle 은 PLANNED 라 lock 검증은 자동 통과.
            policyService.upsert(saved.getId(), pr);
            policy = policyRepository.findByTenantIdAndCycleId(tenantId, saved.getId()).orElseThrow();
        }
        return CycleResponse.from(saved, policy.getId());
    }

    @Transactional(readOnly = true)
    public CycleResponse get(UUID id) {
        UUID tenantId = TenantSupport.currentTenantId();
        EvaluationCycle entity = requireById(id, tenantId);
        UUID policyId = policyRepository.findByTenantIdAndCycleId(tenantId, id)
            .map(EvaluationPolicy::getId).orElse(null);
        return CycleResponse.from(entity, policyId);
    }

    @Transactional(readOnly = true)
    public Page<CycleResponse> list(Pageable pageable) {
        UUID tenantId = TenantSupport.currentTenantId();
        return repository.findAllByTenantId(tenantId, pageable)
            .map(e -> {
                UUID policyId = policyRepository.findByTenantIdAndCycleId(tenantId, e.getId())
                    .map(EvaluationPolicy::getId).orElse(null);
                return CycleResponse.from(e, policyId);
            });
    }

    @Transactional
    public CycleResponse update(UUID id, CycleUpdateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        EvaluationCycle entity = requireById(id, tenantId);

        // name 변경 시 중복 검증.
        if (request.name() != null && !request.name().equals(entity.getName())) {
            validateDuplicateName(tenantId, request.name(), id);
            entity.setName(request.name());
        }

        // periodStart/periodEnd 부분 갱신 — 새 값 + 기존 값으로 invariant 검증.
        LocalDate newStart = request.periodStart() != null ? request.periodStart() : entity.getPeriodStart();
        LocalDate newEnd = request.periodEnd() != null ? request.periodEnd() : entity.getPeriodEnd();
        if (request.periodStart() != null || request.periodEnd() != null) {
            validatePeriod(newStart, newEnd);
            entity.setPeriodStart(newStart);
            entity.setPeriodEnd(newEnd);
        }

        if (request.cycleType() != null) {
            entity.setCycleType(request.cycleType());
        }

        EvaluationCycle saved = repository.save(entity);
        UUID policyId = policyRepository.findByTenantIdAndCycleId(tenantId, id)
            .map(EvaluationPolicy::getId).orElse(null);
        return CycleResponse.from(saved, policyId);
    }

    @Transactional
    public CycleResponse transition(UUID id, CycleStatusTransitionRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        EvaluationCycle entity = requireById(id, tenantId);
        CycleStatus to = request.toStatus();
        validateStatusTransition(entity.getStatus(), to);

        // GOAL_SETTING 으로 전이 시 policy 존재 필수.
        if (to == CycleStatus.GOAL_SETTING) {
            boolean hasPolicy = policyRepository.existsByTenantIdAndCycleId(tenantId, id);
            if (!hasPolicy) {
                throw new ApiException(PerformanceErrorCode.POLICY_NOT_FOUND,
                    Map.of("entity", "EvaluationPolicy", "cycleId", id,
                        "reason", "GOAL_SETTING requires policy"));
            }
        }

        entity.setStatus(to);
        EvaluationCycle saved = repository.save(entity);
        UUID policyId = policyRepository.findByTenantIdAndCycleId(tenantId, id)
            .map(EvaluationPolicy::getId).orElse(null);
        return CycleResponse.from(saved, policyId);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantSupport.currentTenantId();
        EvaluationCycle entity = requireById(id, tenantId);
        if (!DELETABLE_STATUSES.contains(entity.getStatus())) {
            throw new ApiException(PerformanceErrorCode.CYCLE_CANNOT_DELETE,
                Map.of("status", entity.getStatus().name(),
                    "allowedStatuses", DELETABLE_STATUSES));
        }
        // policy 는 FK ON DELETE CASCADE 로 동반 삭제.
        repository.delete(entity);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 내부 검증
    // ─────────────────────────────────────────────────────────────────────

    private EvaluationCycle requireById(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND,
                Map.of("entity", "EvaluationCycle", "id", id)));
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new ApiException(PerformanceErrorCode.CYCLE_INVALID_PERIOD,
                Map.of("periodStart", start, "periodEnd", end));
        }
    }

    private void validateDuplicateName(UUID tenantId, String name, UUID excludeId) {
        if (repository.existsByTenantIdAndName(tenantId, name)) {
            // update 흐름: 본인 행이면 통과 (이미 동일 name).
            if (excludeId != null) {
                EvaluationCycle existing = repository.findByIdAndTenantId(excludeId, tenantId).orElse(null);
                if (existing != null && name.equals(existing.getName())) {
                    return;
                }
            }
            throw new ApiException(PerformanceErrorCode.CYCLE_DUPLICATE_NAME,
                Map.of("tenantId", tenantId, "name", name));
        }
    }

    /** 8단계 상태 전이 검증. FINALIZED/CANCELLED 는 종결. */
    void validateStatusTransition(CycleStatus from, CycleStatus to) {
        if (from == to) {
            throw new ApiException(PerformanceErrorCode.CYCLE_INVALID_STATUS_TRANSITION,
                Map.of("from", from.name(), "to", to.name(), "reason", "same-status"));
        }
        boolean allowed = switch (from) {
            case PLANNED        -> to == CycleStatus.ACTIVE         || to == CycleStatus.CANCELLED;
            case ACTIVE         -> to == CycleStatus.GOAL_SETTING   || to == CycleStatus.CANCELLED;
            case GOAL_SETTING   -> to == CycleStatus.MID_REVIEW     || to == CycleStatus.CANCELLED;
            case MID_REVIEW     -> to == CycleStatus.SELF_REVIEW    || to == CycleStatus.CANCELLED;
            case SELF_REVIEW    -> to == CycleStatus.MANAGER_REVIEW || to == CycleStatus.CANCELLED;
            case MANAGER_REVIEW -> to == CycleStatus.CALIBRATION    || to == CycleStatus.CANCELLED;
            case CALIBRATION    -> to == CycleStatus.FINALIZED      || to == CycleStatus.CANCELLED;
            case FINALIZED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new ApiException(PerformanceErrorCode.CYCLE_INVALID_STATUS_TRANSITION,
                Map.of("from", from.name(), "to", to.name(),
                    "entity", "EvaluationCycle"));
        }
    }
}

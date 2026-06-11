/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationcycle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleCreateRequest;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleResponse;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleStatusTransitionRequest;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.CycleType;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.evaluationpolicy.entity.EvaluationPolicy;
import com.easyperformance.domain.evaluationpolicy.repository.EvaluationPolicyRepository;
import com.easyperformance.domain.evaluationpolicy.service.EvaluationPolicyService;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;

/**
 * EvaluationCycleService 단위 테스트 — P0-S1 (decisions_2026-06-11.md SoT).
 *
 * <p>커버 케이스:
 * <ul>
 *   <li>create 정상 + default policy 자동 생성</li>
 *   <li>중복 name 409 (CYCLE_DUPLICATE_NAME)</li>
 *   <li>status 전이 PLANNED→ACTIVE 정상</li>
 *   <li>FINALIZED 후 전이 거부 422 (CYCLE_INVALID_STATUS_TRANSITION)</li>
 *   <li>ACTIVE delete 거부 409 (CYCLE_CANNOT_DELETE)</li>
 *   <li>periodEnd&lt;periodStart 거부 422 (CYCLE_INVALID_PERIOD)</li>
 * </ul>
 *
 * <p>TenantSupport 는 fallback UUID 반환 — 단계 1 단일 DB 가정. Mockito mock 으로 repository 격리.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationCycleServiceTest {

    @Mock
    private EvaluationCycleRepository repository;

    @Mock
    private EvaluationPolicyRepository policyRepository;

    @Mock
    private EvaluationPolicyService policyService;

    @InjectMocks
    private EvaluationCycleService service;

    private UUID tenantId;
    private UUID cycleId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        cycleId = UUID.randomUUID();
    }

    @Test
    void create_withNullPolicy_callsCreateDefault() {
        CycleCreateRequest request = new CycleCreateRequest(
            "2026 상반기",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 6, 30),
            CycleType.HALF_ANNUAL,
            null
        );

        when(repository.existsByTenantIdAndName(any(), eq("2026 상반기"))).thenReturn(false);
        when(repository.save(any(EvaluationCycle.class))).thenAnswer(inv -> {
            EvaluationCycle e = inv.getArgument(0);
            if (e.getId() == null) e.setId(cycleId);
            return e;
        });

        EvaluationPolicy defaultPolicy = new EvaluationPolicy();
        defaultPolicy.setId(UUID.randomUUID());
        when(policyService.createDefault(any(), any())).thenReturn(defaultPolicy);

        CycleResponse response = service.create(request);

        assertThat(response.name()).isEqualTo("2026 상반기");
        assertThat(response.status()).isEqualTo(CycleStatus.PLANNED);
        assertThat(response.policyId()).isEqualTo(defaultPolicy.getId());
    }

    @Test
    void create_duplicateName_throwsConflict() {
        CycleCreateRequest request = new CycleCreateRequest(
            "Dup",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 6, 30),
            CycleType.HALF_ANNUAL,
            null
        );

        when(repository.existsByTenantIdAndName(any(), eq("Dup"))).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CYCLE_DUPLICATE_NAME);
    }

    @Test
    void create_invalidPeriod_throwsUnprocessable() {
        CycleCreateRequest request = new CycleCreateRequest(
            "Bad period",
            LocalDate.of(2026, 6, 30),
            LocalDate.of(2026, 1, 1),
            CycleType.HALF_ANNUAL,
            null
        );

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CYCLE_INVALID_PERIOD);
    }

    @Test
    void transition_plannedToActive_succeeds() {
        EvaluationCycle entity = newCycle(CycleStatus.PLANNED);
        when(repository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(entity));
        when(repository.save(any(EvaluationCycle.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(policyRepository.findByTenantIdAndCycleId(any(), eq(cycleId))).thenReturn(Optional.empty());

        CycleResponse response = service.transition(cycleId,
            new CycleStatusTransitionRequest(CycleStatus.ACTIVE));

        assertThat(response.status()).isEqualTo(CycleStatus.ACTIVE);
    }

    @Test
    void transition_fromFinalized_isRejected() {
        EvaluationCycle entity = newCycle(CycleStatus.FINALIZED);
        when(repository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.transition(cycleId,
                new CycleStatusTransitionRequest(CycleStatus.CANCELLED)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CYCLE_INVALID_STATUS_TRANSITION);
    }

    @Test
    void transition_toGoalSetting_withoutPolicy_throwsPolicyNotFound() {
        EvaluationCycle entity = newCycle(CycleStatus.ACTIVE);
        when(repository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(entity));
        when(policyRepository.existsByTenantIdAndCycleId(any(), eq(cycleId))).thenReturn(false);

        assertThatThrownBy(() -> service.transition(cycleId,
                new CycleStatusTransitionRequest(CycleStatus.GOAL_SETTING)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.POLICY_NOT_FOUND);
    }

    @Test
    void delete_activeStatus_isRejected() {
        EvaluationCycle entity = newCycle(CycleStatus.ACTIVE);
        when(repository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.delete(cycleId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CYCLE_CANNOT_DELETE);
    }

    @Test
    void delete_plannedStatus_succeeds() {
        EvaluationCycle entity = newCycle(CycleStatus.PLANNED);
        when(repository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(entity));

        service.delete(cycleId);
        // 예외 없으면 OK — repository.delete 호출 검증은 verify 로도 가능하나 본 슬라이스 핵심은 status 가드.
    }

    @Test
    void get_notFound_throws() {
        when(repository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(cycleId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CYCLE_NOT_FOUND);
    }

    @Test
    void list_returnsPage() {
        when(repository.findAllByTenantId(any(), any()))
            .thenReturn(org.springframework.data.domain.Page.empty());
        var page = service.list(PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isZero();
    }

    private EvaluationCycle newCycle(CycleStatus status) {
        EvaluationCycle entity = new EvaluationCycle();
        entity.setId(cycleId);
        entity.setTenantId(tenantId);
        entity.setName("Test cycle");
        entity.setPeriodStart(LocalDate.of(2026, 1, 1));
        entity.setPeriodEnd(LocalDate.of(2026, 6, 30));
        entity.setCycleType(CycleType.HALF_ANNUAL);
        entity.setStatus(status);
        return entity;
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.evaluationcycle.controller;

import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleCreateRequest;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleResponse;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleStatusTransitionRequest;
import com.easyperformance.domain.evaluationcycle.dto.EvaluationCycleDtos.CycleUpdateRequest;
import com.easyperformance.domain.evaluationcycle.service.EvaluationCycleService;
import com.easyperformance.domain.evaluationpolicy.dto.EvaluationPolicyDtos.PolicyResponse;
import com.easyperformance.domain.evaluationpolicy.dto.EvaluationPolicyDtos.PolicyUpsertRequest;
import com.easyperformance.domain.evaluationpolicy.service.EvaluationPolicyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * EvaluationCycle REST Controller — P0-S1 (decisions_2026-06-11.md SoT).
 *
 * <p>base path = {@code /api/v1/cycles}. 8 엔드포인트 (cycle 6 + policy 2 — policy 도 본 controller 위임).
 *
 * <p>FE 에이전트와 OpenAPI 계약 동일. 이탈 금지.
 */
@RestController
@RequestMapping("/api/v1/cycles")
public class EvaluationCycleController {

    private final EvaluationCycleService cycleService;
    private final EvaluationPolicyService policyService;

    public EvaluationCycleController(EvaluationCycleService cycleService,
                                     EvaluationPolicyService policyService) {
        this.cycleService = cycleService;
        this.policyService = policyService;
    }

    @GetMapping
    public ResponseEntity<Page<CycleResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(cycleService.list(pageable));
    }

    @PostMapping
    public ResponseEntity<CycleResponse> create(@Valid @RequestBody CycleCreateRequest request) {
        CycleResponse response = cycleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CycleResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(cycleService.get(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CycleResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody CycleUpdateRequest request
    ) {
        return ResponseEntity.ok(cycleService.update(id, request));
    }

    @PostMapping("/{id}/transition")
    public ResponseEntity<CycleResponse> transition(
        @PathVariable UUID id,
        @Valid @RequestBody CycleStatusTransitionRequest request
    ) {
        return ResponseEntity.ok(cycleService.transition(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        cycleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/policy")
    public ResponseEntity<PolicyResponse> getPolicy(@PathVariable UUID id) {
        return ResponseEntity.ok(policyService.get(id));
    }

    @PutMapping("/{id}/policy")
    public ResponseEntity<PolicyResponse> upsertPolicy(
        @PathVariable UUID id,
        @Valid @RequestBody PolicyUpsertRequest request
    ) {
        return ResponseEntity.ok(policyService.upsert(id, request));
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.selfevaluation.controller;

import com.easyperformance.domain.selfevaluation.dto.SelfEvaluationDtos.SelfEvaluationCreateRequest;
import com.easyperformance.domain.selfevaluation.dto.SelfEvaluationDtos.SelfEvaluationResponse;
import com.easyperformance.domain.selfevaluation.dto.SelfEvaluationDtos.SelfEvaluationUpdateRequest;
import com.easyperformance.domain.selfevaluation.service.SelfEvaluationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * SelfEvaluation REST Controller.
 *
 * <p>단계 1 = {@code /api/internal/} prefix (BE-CC-2 JWT 단계 3 진입까지 임시). 단계 3 이후 인증/인가
 * 적용 + {@code /api/v1/} prefix 전환.
 */
@RestController
@RequestMapping("/api/internal/self-evaluations")
public class SelfEvaluationController {

    private final SelfEvaluationService service;

    public SelfEvaluationController(SelfEvaluationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SelfEvaluationResponse> create(@Valid @RequestBody SelfEvaluationCreateRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SelfEvaluationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @GetMapping
    public ResponseEntity<Page<SelfEvaluationResponse>> list(
        @RequestParam(required = false) UUID employeeId,
        Pageable pageable
    ) {
        Page<SelfEvaluationResponse> page = employeeId != null
            ? service.listByEmployee(employeeId, pageable)
            : service.list(pageable);
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SelfEvaluationResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody SelfEvaluationUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

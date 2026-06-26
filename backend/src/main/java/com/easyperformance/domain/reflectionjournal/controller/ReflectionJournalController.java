/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.reflectionjournal.controller;

import com.easyperformance.domain.reflectionjournal.dto.ReflectionJournalDtos.ReflectionJournalCreateRequest;
import com.easyperformance.domain.reflectionjournal.dto.ReflectionJournalDtos.ReflectionJournalResponse;
import com.easyperformance.domain.reflectionjournal.dto.ReflectionJournalDtos.ReflectionJournalUpdateRequest;
import com.easyperformance.domain.reflectionjournal.service.ReflectionJournalService;
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

@RestController
@RequestMapping("/api/v1/reflection-journals")
public class ReflectionJournalController {

    private final ReflectionJournalService service;

    public ReflectionJournalController(ReflectionJournalService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReflectionJournalResponse> create(
        @Valid @RequestBody ReflectionJournalCreateRequest request
    ) {
        return ResponseEntity.ok(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReflectionJournalResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @GetMapping
    public ResponseEntity<Page<ReflectionJournalResponse>> list(
        @RequestParam(required = false) UUID employeeId,
        Pageable pageable
    ) {
        Page<ReflectionJournalResponse> page = employeeId != null
            ? service.listByEmployee(employeeId, pageable)
            : service.list(pageable);
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReflectionJournalResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody ReflectionJournalUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

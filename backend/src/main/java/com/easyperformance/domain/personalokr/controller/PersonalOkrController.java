/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.personalokr.controller;

import com.easyperformance.domain.personalokr.dto.PersonalOkrDtos.PersonalOkrCreateRequest;
import com.easyperformance.domain.personalokr.dto.PersonalOkrDtos.PersonalOkrResponse;
import com.easyperformance.domain.personalokr.dto.PersonalOkrDtos.PersonalOkrUpdateRequest;
import com.easyperformance.domain.personalokr.service.PersonalOkrService;
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
@RequestMapping("/api/v1/personal-okrs")
public class PersonalOkrController {

    private final PersonalOkrService service;

    public PersonalOkrController(PersonalOkrService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PersonalOkrResponse> create(@Valid @RequestBody PersonalOkrCreateRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonalOkrResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @GetMapping
    public ResponseEntity<Page<PersonalOkrResponse>> list(
        @RequestParam(required = false) UUID employeeId,
        Pageable pageable
    ) {
        Page<PersonalOkrResponse> page = employeeId != null
            ? service.listByEmployee(employeeId, pageable)
            : service.list(pageable);
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PersonalOkrResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody PersonalOkrUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

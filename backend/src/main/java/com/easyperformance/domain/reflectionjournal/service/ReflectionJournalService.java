/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.reflectionjournal.service;

import com.easyperformance.common.TenantSupport;
import com.easyperformance.domain.reflectionjournal.dto.ReflectionJournalDtos.ReflectionJournalCreateRequest;
import com.easyperformance.domain.reflectionjournal.dto.ReflectionJournalDtos.ReflectionJournalResponse;
import com.easyperformance.domain.reflectionjournal.dto.ReflectionJournalDtos.ReflectionJournalUpdateRequest;
import com.easyperformance.domain.reflectionjournal.entity.ReflectionJournal;
import com.easyperformance.domain.reflectionjournal.entity.ReflectionMethod;
import com.easyperformance.domain.reflectionjournal.repository.ReflectionJournalRepository;
import com.easyware.platform.error.ApiException;
import com.easyware.platform.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class ReflectionJournalService {

    private final ReflectionJournalRepository repository;

    public ReflectionJournalService(ReflectionJournalRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ReflectionJournalResponse create(ReflectionJournalCreateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        ReflectionJournal entity = new ReflectionJournal();
        entity.setTenantId(tenantId);
        entity.setEmployeeId(request.employeeId());
        entity.setReflectionDate(request.reflectionDate());
        entity.setMethod(request.method() != null ? request.method() : ReflectionMethod.KPT);
        entity.setContent(request.content());
        entity.setIsPrivate(request.isPrivate() != null ? request.isPrivate() : Boolean.TRUE);
        return ReflectionJournalResponse.from(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public ReflectionJournalResponse get(UUID id) {
        UUID tenantId = TenantSupport.currentTenantId();
        ReflectionJournal entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("entity", "ReflectionJournal", "id", id)));
        return ReflectionJournalResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public Page<ReflectionJournalResponse> list(Pageable pageable) {
        UUID tenantId = TenantSupport.currentTenantId();
        return repository.findAllByTenantId(tenantId, pageable).map(ReflectionJournalResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ReflectionJournalResponse> listByEmployee(UUID employeeId, Pageable pageable) {
        UUID tenantId = TenantSupport.currentTenantId();
        return repository.findAllByTenantIdAndEmployeeId(tenantId, employeeId, pageable)
            .map(ReflectionJournalResponse::from);
    }

    @Transactional
    public ReflectionJournalResponse update(UUID id, ReflectionJournalUpdateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        ReflectionJournal entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("entity", "ReflectionJournal", "id", id)));
        if (request.content() != null) entity.setContent(request.content());
        if (request.method() != null) entity.setMethod(request.method());
        if (request.isPrivate() != null) entity.setIsPrivate(request.isPrivate());
        return ReflectionJournalResponse.from(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantSupport.currentTenantId();
        ReflectionJournal entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("entity", "ReflectionJournal", "id", id)));
        repository.delete(entity);
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.personalokr.service;

import com.easyperformance.common.TenantSupport;
import com.easyperformance.domain.personalokr.dto.PersonalOkrDtos.PersonalOkrCreateRequest;
import com.easyperformance.domain.personalokr.dto.PersonalOkrDtos.PersonalOkrResponse;
import com.easyperformance.domain.personalokr.dto.PersonalOkrDtos.PersonalOkrUpdateRequest;
import com.easyperformance.domain.personalokr.entity.PersonalOkr;
import com.easyperformance.domain.personalokr.entity.PersonalOkrStatus;
import com.easyperformance.domain.personalokr.repository.PersonalOkrRepository;
import com.easyware.platform.error.ApiException;
import com.easyware.platform.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class PersonalOkrService {

    private final PersonalOkrRepository repository;

    public PersonalOkrService(PersonalOkrRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PersonalOkrResponse create(PersonalOkrCreateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        PersonalOkr entity = new PersonalOkr();
        entity.setTenantId(tenantId);
        entity.setEmployeeId(request.employeeId());
        entity.setObjective(request.objective());
        entity.setPeriodStart(request.periodStart());
        entity.setPeriodEnd(request.periodEnd());
        entity.setProgress(request.progress() != null ? request.progress() : 0.0);
        entity.setStatus(PersonalOkrStatus.ACTIVE);
        return PersonalOkrResponse.from(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public PersonalOkrResponse get(UUID id) {
        UUID tenantId = TenantSupport.currentTenantId();
        PersonalOkr entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("entity", "PersonalOkr", "id", id)));
        return PersonalOkrResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public Page<PersonalOkrResponse> list(Pageable pageable) {
        UUID tenantId = TenantSupport.currentTenantId();
        return repository.findAllByTenantId(tenantId, pageable).map(PersonalOkrResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<PersonalOkrResponse> listByEmployee(UUID employeeId, Pageable pageable) {
        UUID tenantId = TenantSupport.currentTenantId();
        return repository.findAllByTenantIdAndEmployeeId(tenantId, employeeId, pageable)
            .map(PersonalOkrResponse::from);
    }

    @Transactional
    public PersonalOkrResponse update(UUID id, PersonalOkrUpdateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        PersonalOkr entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("entity", "PersonalOkr", "id", id)));
        if (request.objective() != null) entity.setObjective(request.objective());
        if (request.progress() != null) {
            if (request.progress() < 0 || request.progress() > 100) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                    Map.of("field", "progress", "value", request.progress(), "range", "0-100"));
            }
            entity.setProgress(request.progress());
        }
        if (request.status() != null) entity.setStatus(request.status());
        return PersonalOkrResponse.from(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantSupport.currentTenantId();
        PersonalOkr entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("entity", "PersonalOkr", "id", id)));
        repository.delete(entity);
    }
}

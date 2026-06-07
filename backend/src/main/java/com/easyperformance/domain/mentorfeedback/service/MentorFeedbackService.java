/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.mentorfeedback.service;

import com.easyperformance.common.TenantSupport;
import com.easyperformance.domain.mentorfeedback.dto.MentorFeedbackDtos.MentorFeedbackCreateRequest;
import com.easyperformance.domain.mentorfeedback.dto.MentorFeedbackDtos.MentorFeedbackResponse;
import com.easyperformance.domain.mentorfeedback.dto.MentorFeedbackDtos.MentorFeedbackUpdateRequest;
import com.easyperformance.domain.mentorfeedback.entity.FeedbackCategory;
import com.easyperformance.domain.mentorfeedback.entity.MentorFeedback;
import com.easyperformance.domain.mentorfeedback.repository.MentorFeedbackRepository;
import com.easyware.platform.error.ApiException;
import com.easyware.platform.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class MentorFeedbackService {

    private final MentorFeedbackRepository repository;

    public MentorFeedbackService(MentorFeedbackRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public MentorFeedbackResponse create(MentorFeedbackCreateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        MentorFeedback entity = new MentorFeedback();
        entity.setTenantId(tenantId);
        entity.setMentorId(request.mentorId());
        entity.setMenteeId(request.menteeId());
        entity.setFeedbackDate(request.feedbackDate());
        entity.setCategory(request.category() != null ? request.category() : FeedbackCategory.GROWTH);
        entity.setContent(request.content());
        entity.setAcknowledged(Boolean.FALSE);
        return MentorFeedbackResponse.from(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public MentorFeedbackResponse get(UUID id) {
        UUID tenantId = TenantSupport.currentTenantId();
        MentorFeedback entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("entity", "MentorFeedback", "id", id)));
        return MentorFeedbackResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public Page<MentorFeedbackResponse> list(Pageable pageable) {
        UUID tenantId = TenantSupport.currentTenantId();
        return repository.findAllByTenantId(tenantId, pageable).map(MentorFeedbackResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<MentorFeedbackResponse> listByMentee(UUID menteeId, Pageable pageable) {
        UUID tenantId = TenantSupport.currentTenantId();
        return repository.findAllByTenantIdAndMenteeId(tenantId, menteeId, pageable)
            .map(MentorFeedbackResponse::from);
    }

    @Transactional
    public MentorFeedbackResponse update(UUID id, MentorFeedbackUpdateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        MentorFeedback entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("entity", "MentorFeedback", "id", id)));
        if (request.content() != null) entity.setContent(request.content());
        if (request.category() != null) entity.setCategory(request.category());
        if (request.acknowledged() != null) entity.setAcknowledged(request.acknowledged());
        return MentorFeedbackResponse.from(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantSupport.currentTenantId();
        MentorFeedback entity = repository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("entity", "MentorFeedback", "id", id)));
        repository.delete(entity);
    }
}

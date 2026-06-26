/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.mentorfeedback.controller;

import com.easyperformance.domain.mentorfeedback.dto.MentorFeedbackDtos.MentorFeedbackCreateRequest;
import com.easyperformance.domain.mentorfeedback.dto.MentorFeedbackDtos.MentorFeedbackResponse;
import com.easyperformance.domain.mentorfeedback.dto.MentorFeedbackDtos.MentorFeedbackUpdateRequest;
import com.easyperformance.domain.mentorfeedback.service.MentorFeedbackService;
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
@RequestMapping("/api/v1/mentor-feedbacks")
public class MentorFeedbackController {

    private final MentorFeedbackService service;

    public MentorFeedbackController(MentorFeedbackService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MentorFeedbackResponse> create(@Valid @RequestBody MentorFeedbackCreateRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MentorFeedbackResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @GetMapping
    public ResponseEntity<Page<MentorFeedbackResponse>> list(
        @RequestParam(required = false) UUID menteeId,
        Pageable pageable
    ) {
        Page<MentorFeedbackResponse> page = menteeId != null
            ? service.listByMentee(menteeId, pageable)
            : service.list(pageable);
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MentorFeedbackResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody MentorFeedbackUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

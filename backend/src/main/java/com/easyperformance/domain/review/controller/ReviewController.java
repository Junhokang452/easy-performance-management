/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.review.controller;

import com.easyperformance.domain.review.dto.ReviewDtos.ReviewBulkCreateRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewBulkCreateResponse;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewCreateRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewKpiItemResponse;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewResponse;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewSubmitManagerRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewSubmitSelfRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewTransitionRequest;
import com.easyperformance.domain.review.dto.ReviewDtos.ReviewUpdateRequest;
import com.easyperformance.domain.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 성과 평가 REST Controller — P0-S3 (p0_s3_contract.md §6). base path = {@code /api/v1}.
 *
 * <p>11 엔드포인트 — cycle 별 목록/생성/일괄 + review 단건/my/kpi-items + PATCH + submit-self/manager +
 * transition + delete. resource 루트가 cycle/review 2종 횡단이라 메서드별 full path (KpiController 패턴).
 *
 * <p>FE 에이전트와 OpenAPI 계약 동일 — 이탈 금지. Response shape 는 ReviewDtos 가 SoT.
 */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // cycle 별 목록 / 생성 / 일괄
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/cycles/{cycleId}/reviews")
    public ResponseEntity<List<ReviewResponse>> listReviews(
        @PathVariable UUID cycleId,
        @RequestParam(required = false) UUID employeeId
    ) {
        return ResponseEntity.ok(reviewService.listReviews(cycleId, employeeId));
    }

    @PostMapping("/cycles/{cycleId}/reviews")
    public ResponseEntity<ReviewResponse> createReview(
        @PathVariable UUID cycleId,
        @Valid @RequestBody ReviewCreateRequest request
    ) {
        ReviewResponse response = reviewService.createReview(cycleId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/cycles/{cycleId}/reviews/bulk")
    public ResponseEntity<ReviewBulkCreateResponse> bulkCreate(
        @PathVariable UUID cycleId,
        @Valid @RequestBody ReviewBulkCreateRequest request
    ) {
        ReviewBulkCreateResponse response = reviewService.bulkCreate(cycleId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────────────
    // review 단건 / my / kpi-items
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/reviews/{reviewId}")
    public ResponseEntity<ReviewResponse> getReview(@PathVariable UUID reviewId) {
        return ResponseEntity.ok(reviewService.getReview(reviewId));
    }

    @GetMapping("/reviews/my")
    public ResponseEntity<ReviewResponse> getMyReview(
        @RequestParam UUID cycleId,
        @RequestParam UUID employeeId
    ) {
        return ResponseEntity.ok(reviewService.getMyReview(cycleId, employeeId));
    }

    @GetMapping("/reviews/{reviewId}/kpi-items")
    public ResponseEntity<List<ReviewKpiItemResponse>> getKpiItems(@PathVariable UUID reviewId) {
        return ResponseEntity.ok(reviewService.getKpiItems(reviewId));
    }

    // ─────────────────────────────────────────────────────────────────────
    // PATCH / submit / transition / delete
    // ─────────────────────────────────────────────────────────────────────

    @PatchMapping("/reviews/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(
        @PathVariable UUID reviewId,
        @Valid @RequestBody ReviewUpdateRequest request
    ) {
        return ResponseEntity.ok(reviewService.updateReview(reviewId, request));
    }

    @PostMapping("/reviews/{reviewId}/submit-self")
    public ResponseEntity<ReviewResponse> submitSelf(
        @PathVariable UUID reviewId,
        @Valid @RequestBody ReviewSubmitSelfRequest request
    ) {
        return ResponseEntity.ok(reviewService.submitSelf(reviewId, request));
    }

    @PostMapping("/reviews/{reviewId}/submit-manager")
    public ResponseEntity<ReviewResponse> submitManager(
        @PathVariable UUID reviewId,
        @Valid @RequestBody ReviewSubmitManagerRequest request
    ) {
        return ResponseEntity.ok(reviewService.submitManager(reviewId, request));
    }

    @PostMapping("/reviews/{reviewId}/transition")
    public ResponseEntity<ReviewResponse> transition(
        @PathVariable UUID reviewId,
        @Valid @RequestBody ReviewTransitionRequest request
    ) {
        return ResponseEntity.ok(reviewService.transition(reviewId, request));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable UUID reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.noContent().build();
    }
}

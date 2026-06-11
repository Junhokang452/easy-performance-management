/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.calibration.controller;

import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationAdjustmentRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationConfirmRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationConfirmResponse;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationSessionCreateRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationSessionResponse;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationSessionUpdateRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.CalibrationTransitionRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionApplyRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionApplyResponse;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionResponse;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionSimulateRequest;
import com.easyperformance.domain.calibration.dto.CalibrationDtos.DistributionSimulationResponse;
import com.easyperformance.domain.calibration.service.CalibrationService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 캘리브레이션 + 분포 REST Controller — P0-S4 (p0_s4_contract.md §6). base path = {@code /api/v1}.
 *
 * <p>11 엔드포인트 — 세션 목록/생성/단건/PATCH/transition/adjustments/confirm/delete (8) +
 * 분포 GET/simulate/apply (3). resource 루트가 cycle/calibration-session 횡단이라 메서드별 full path
 * (ReviewController 패턴).
 *
 * <p>FE 에이전트와 OpenAPI 계약 동일 — 이탈 금지. Response shape 는 CalibrationDtos 가 SoT.
 */
@RestController
@RequestMapping("/api/v1")
public class CalibrationController {

    private final CalibrationService calibrationService;

    public CalibrationController(CalibrationService calibrationService) {
        this.calibrationService = calibrationService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // CalibrationSession
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/cycles/{cycleId}/calibration-sessions")
    public ResponseEntity<List<CalibrationSessionResponse>> listSessions(@PathVariable UUID cycleId) {
        return ResponseEntity.ok(calibrationService.listSessions(cycleId));
    }

    @PostMapping("/cycles/{cycleId}/calibration-sessions")
    public ResponseEntity<CalibrationSessionResponse> createSession(
        @PathVariable UUID cycleId,
        @Valid @RequestBody CalibrationSessionCreateRequest request
    ) {
        CalibrationSessionResponse response = calibrationService.createSession(cycleId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/calibration-sessions/{sessionId}")
    public ResponseEntity<CalibrationSessionResponse> getSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(calibrationService.getSession(sessionId));
    }

    @PatchMapping("/calibration-sessions/{sessionId}")
    public ResponseEntity<CalibrationSessionResponse> updateSession(
        @PathVariable UUID sessionId,
        @Valid @RequestBody CalibrationSessionUpdateRequest request
    ) {
        return ResponseEntity.ok(calibrationService.updateSession(sessionId, request));
    }

    @PostMapping("/calibration-sessions/{sessionId}/transition")
    public ResponseEntity<CalibrationSessionResponse> transition(
        @PathVariable UUID sessionId,
        @Valid @RequestBody CalibrationTransitionRequest request
    ) {
        return ResponseEntity.ok(calibrationService.transition(sessionId, request));
    }

    @PostMapping("/calibration-sessions/{sessionId}/adjustments")
    public ResponseEntity<CalibrationSessionResponse> adjust(
        @PathVariable UUID sessionId,
        @Valid @RequestBody CalibrationAdjustmentRequest request
    ) {
        return ResponseEntity.ok(calibrationService.adjust(sessionId, request));
    }

    @PostMapping("/calibration-sessions/{sessionId}/confirm")
    public ResponseEntity<CalibrationConfirmResponse> confirm(
        @PathVariable UUID sessionId,
        @Valid @RequestBody CalibrationConfirmRequest request
    ) {
        return ResponseEntity.ok(calibrationService.confirm(sessionId, request));
    }

    @DeleteMapping("/calibration-sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
        calibrationService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 분포
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/cycles/{cycleId}/distribution")
    public ResponseEntity<DistributionResponse> getDistribution(@PathVariable UUID cycleId) {
        return ResponseEntity.ok(calibrationService.getDistribution(cycleId));
    }

    @PostMapping("/cycles/{cycleId}/distribution/simulate")
    public ResponseEntity<DistributionSimulationResponse> simulate(
        @PathVariable UUID cycleId,
        @Valid @RequestBody DistributionSimulateRequest request
    ) {
        return ResponseEntity.ok(calibrationService.simulate(cycleId, request));
    }

    @PostMapping("/cycles/{cycleId}/distribution/apply")
    public ResponseEntity<DistributionApplyResponse> apply(
        @PathVariable UUID cycleId,
        @Valid @RequestBody DistributionApplyRequest request
    ) {
        return ResponseEntity.ok(calibrationService.apply(cycleId, request));
    }
}

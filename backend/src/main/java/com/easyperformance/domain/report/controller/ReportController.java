/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.report.controller;

import com.easyperformance.domain.report.dto.ReportDtos.ReportAcknowledgeRequest;
import com.easyperformance.domain.report.dto.ReportDtos.ReportPublishRequest;
import com.easyperformance.domain.report.dto.ReportDtos.ReportPublishResponse;
import com.easyperformance.domain.report.dto.ReportDtos.ReportResponse;
import com.easyperformance.domain.report.dto.ReportDtos.ReportSupersedeRequest;
import com.easyperformance.domain.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 성과 리포트 REST Controller — P0-S5 (p0_s5_contract.md §6). base path = {@code /api/v1}.
 *
 * <p>7 엔드포인트 — cycle 별 목록/일괄 발행 (2) + report 단건/my/view/acknowledge/supersede (5). resource
 * 루트가 cycle/report 2종 횡단이라 메서드별 full path (ReviewController/CalibrationController 패턴).
 *
 * <p>FE 에이전트와 OpenAPI 계약 동일 — 이탈 금지. Response shape 는 ReportDtos 가 SoT. supersede 는 신규
 * row 생성이라 201 (그 외 200).
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // cycle 별 목록 / 일괄 발행
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/cycles/{cycleId}/reports")
    public ResponseEntity<List<ReportResponse>> listReports(
        @PathVariable UUID cycleId,
        @RequestParam(required = false) UUID employeeId
    ) {
        return ResponseEntity.ok(reportService.listReports(cycleId, employeeId));
    }

    @PostMapping("/cycles/{cycleId}/reports/publish")
    public ResponseEntity<ReportPublishResponse> publish(
        @PathVariable UUID cycleId,
        @Valid @RequestBody ReportPublishRequest request
    ) {
        return ResponseEntity.ok(reportService.publish(cycleId, request.actorEmployeeId()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // report 단건 / my / view / acknowledge / supersede
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<ReportResponse> getReport(@PathVariable UUID reportId) {
        return ResponseEntity.ok(reportService.getReport(reportId));
    }

    @GetMapping("/reports/my")
    public ResponseEntity<ReportResponse> getMyReport(
        @RequestParam UUID cycleId,
        @RequestParam UUID employeeId
    ) {
        return ResponseEntity.ok(reportService.getMyReport(cycleId, employeeId));
    }

    @PostMapping("/reports/{reportId}/view")
    public ResponseEntity<ReportResponse> view(@PathVariable UUID reportId) {
        return ResponseEntity.ok(reportService.view(reportId));
    }

    @PostMapping("/reports/{reportId}/acknowledge")
    public ResponseEntity<ReportResponse> acknowledge(
        @PathVariable UUID reportId,
        @Valid @RequestBody ReportAcknowledgeRequest request
    ) {
        return ResponseEntity.ok(reportService.acknowledge(reportId, request.actorEmployeeId()));
    }

    @PostMapping("/reports/{reportId}/supersede")
    public ResponseEntity<ReportResponse> supersede(
        @PathVariable UUID reportId,
        @Valid @RequestBody ReportSupersedeRequest request
    ) {
        ReportResponse response = reportService.supersede(reportId, request.actorEmployeeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.controller;

import com.easyperformance.domain.kpi.dto.KpiDtos.KpiActualCreateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiActualResponse;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiActualSupersedeRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiAssignmentCreateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiAssignmentResponse;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiAssignmentUpdateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiNodeCreateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiNodeResponse;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiNodeUpdateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiTreeCreateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiTreeDetailResponse;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiTreeResponse;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiTreeUpdateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.MyKpiAssignmentResponse;
import com.easyperformance.domain.kpi.service.KpiService;
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
 * KPI 도메인 REST Controller — P0-S2 (p0_s2_contract.md §4). base path = {@code /api/v1}.
 *
 * <p>17 엔드포인트 — KpiTree 5 + KpiNode 3 + KpiAssignment 6 + KpiActual 3. 4 entity 가 한
 * aggregate 가족이라 단일 controller 통합 (resource 루트가 cycle/tree/node/assignment/actual 5종이라
 * 메서드별 full path).
 *
 * <p>FE 에이전트와 OpenAPI 계약 동일 — 이탈 금지. Response shape 는 KpiDtos 가 SoT.
 */
@RestController
@RequestMapping("/api/v1")
public class KpiController {

    private final KpiService kpiService;

    public KpiController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // KpiTree
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/cycles/{cycleId}/kpi-trees")
    public ResponseEntity<List<KpiTreeResponse>> listTrees(@PathVariable UUID cycleId) {
        return ResponseEntity.ok(kpiService.listTrees(cycleId));
    }

    @PostMapping("/cycles/{cycleId}/kpi-trees")
    public ResponseEntity<KpiTreeResponse> createTree(
        @PathVariable UUID cycleId,
        @Valid @RequestBody KpiTreeCreateRequest request
    ) {
        KpiTreeResponse response = kpiService.createTree(cycleId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/kpi-trees/{treeId}")
    public ResponseEntity<KpiTreeDetailResponse> getTree(@PathVariable UUID treeId) {
        return ResponseEntity.ok(kpiService.getTree(treeId));
    }

    @PatchMapping("/kpi-trees/{treeId}")
    public ResponseEntity<KpiTreeResponse> updateTree(
        @PathVariable UUID treeId,
        @Valid @RequestBody KpiTreeUpdateRequest request
    ) {
        return ResponseEntity.ok(kpiService.updateTree(treeId, request));
    }

    @DeleteMapping("/kpi-trees/{treeId}")
    public ResponseEntity<Void> deleteTree(@PathVariable UUID treeId) {
        kpiService.deleteTree(treeId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // KpiNode
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping("/kpi-trees/{treeId}/nodes")
    public ResponseEntity<KpiNodeResponse> createNode(
        @PathVariable UUID treeId,
        @Valid @RequestBody KpiNodeCreateRequest request
    ) {
        KpiNodeResponse response = kpiService.createNode(treeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/kpi-nodes/{nodeId}")
    public ResponseEntity<KpiNodeResponse> updateNode(
        @PathVariable UUID nodeId,
        @Valid @RequestBody KpiNodeUpdateRequest request
    ) {
        return ResponseEntity.ok(kpiService.updateNode(nodeId, request));
    }

    @DeleteMapping("/kpi-nodes/{nodeId}")
    public ResponseEntity<Void> deleteNode(@PathVariable UUID nodeId) {
        kpiService.deleteNode(nodeId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // KpiAssignment
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/kpi-nodes/{nodeId}/assignments")
    public ResponseEntity<List<KpiAssignmentResponse>> listAssignments(@PathVariable UUID nodeId) {
        return ResponseEntity.ok(kpiService.listAssignments(nodeId));
    }

    @PostMapping("/kpi-nodes/{nodeId}/assignments")
    public ResponseEntity<KpiAssignmentResponse> createAssignment(
        @PathVariable UUID nodeId,
        @Valid @RequestBody KpiAssignmentCreateRequest request
    ) {
        KpiAssignmentResponse response = kpiService.createAssignment(nodeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/kpi-assignments/{assignmentId}")
    public ResponseEntity<KpiAssignmentResponse> updateAssignment(
        @PathVariable UUID assignmentId,
        @Valid @RequestBody KpiAssignmentUpdateRequest request
    ) {
        return ResponseEntity.ok(kpiService.updateAssignment(assignmentId, request));
    }

    @DeleteMapping("/kpi-assignments/{assignmentId}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable UUID assignmentId) {
        kpiService.deleteAssignment(assignmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/kpi-assignments/my")
    public ResponseEntity<List<MyKpiAssignmentResponse>> listMyAssignments(
        @RequestParam UUID cycleId,
        @RequestParam UUID employeeId
    ) {
        return ResponseEntity.ok(kpiService.listMyAssignments(cycleId, employeeId));
    }

    @GetMapping("/kpi-assignments/{assignmentId}/actuals")
    public ResponseEntity<List<KpiActualResponse>> listActuals(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(kpiService.listActuals(assignmentId));
    }

    @PostMapping("/kpi-assignments/{assignmentId}/actuals")
    public ResponseEntity<KpiActualResponse> createActual(
        @PathVariable UUID assignmentId,
        @Valid @RequestBody KpiActualCreateRequest request
    ) {
        KpiActualResponse response = kpiService.createActual(assignmentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────────────
    // KpiActual
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping("/kpi-actuals/{actualId}/supersede")
    public ResponseEntity<KpiActualResponse> supersedeActual(
        @PathVariable UUID actualId,
        @Valid @RequestBody KpiActualSupersedeRequest request
    ) {
        KpiActualResponse response = kpiService.supersedeActual(actualId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

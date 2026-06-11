/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.service;

import com.easyperformance.common.TenantSupport;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
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
import com.easyperformance.domain.kpi.entity.KpiActual;
import com.easyperformance.domain.kpi.entity.KpiActualSource;
import com.easyperformance.domain.kpi.entity.KpiAssignment;
import com.easyperformance.domain.kpi.entity.KpiNode;
import com.easyperformance.domain.kpi.entity.KpiNodeSource;
import com.easyperformance.domain.kpi.entity.KpiTree;
import com.easyperformance.domain.kpi.repository.KpiActualRepository;
import com.easyperformance.domain.kpi.repository.KpiAssignmentRepository;
import com.easyperformance.domain.kpi.repository.KpiNodeRepository;
import com.easyperformance.domain.kpi.repository.KpiTreeRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * KPI 도메인 Service — 비즈니스 SSOT (KpiTree / KpiNode / KpiAssignment / KpiActual).
 *
 * <p>테넌트 격리: {@link TenantSupport#currentTenantId()} 위임 — 모든 쿼리에 tenant_id 필수
 * (easy-ware 규칙 #10). 4 entity 는 한 aggregate 가족이라 단일 서비스로 통합.
 *
 * <p>검증 규칙 (p0_s2_contract.md §3):
 * <ul>
 *   <li>cycle FINALIZED/CANCELLED → 일체 쓰기 거부 (KPI_CYCLE_LOCKED) — tree→cycle 해석.</li>
 *   <li>parent 는 같은 tree 소속 (KPI_NODE_PARENT_TREE_MISMATCH).</li>
 *   <li>weight ∈ (0,1] (KPI_WEIGHT_OUT_OF_RANGE) + 형제 합 ≤ 1.0+0.001 (KPI_WEIGHT_SUM_EXCEEDED).</li>
 *   <li>P0 node source ≠ MANUAL 거부 (KPI_SOURCE_NOT_SUPPORTED) / actual source 서버 MANUAL 고정.</li>
 *   <li>assignment (node×employee) 중복 (KPI_ASSIGNMENT_DUPLICATE).</li>
 *   <li>actual append-only — supersede 신규 row 전용 + 재정정 차단 (KPI_ACTUAL_ALREADY_SUPERSEDED).</li>
 *   <li>자식 있는 node delete 거부 (KPI_NODE_HAS_CHILDREN).</li>
 * </ul>
 *
 * <p>파생 계산: effectiveWeight = assignment.weight ?? node.weight / effectiveTarget = targetOverride
 * ?? node.target / latestActual = supersede 안 된 row 중 max(asOfDate, createdAt) /
 * achievementRate = latestActualValue ÷ effectiveTarget (target null·0 또는 actual 없음 → null).
 */
@Service
public class KpiService {

    /** cycle 쓰기 금지 status (lock). */
    private static final Set<CycleStatus> LOCKED_STATUSES =
        EnumSet.of(CycleStatus.FINALIZED, CycleStatus.CANCELLED);

    /** weight 합 허용 오차 (±0.001). */
    private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.001");
    private static final BigDecimal ONE = BigDecimal.ONE;
    /** weight 합 완결성 판정 (==1.0 ±0.001). */
    private static final BigDecimal WEIGHT_MAX = ONE.add(WEIGHT_TOLERANCE);

    private final KpiTreeRepository treeRepository;
    private final KpiNodeRepository nodeRepository;
    private final KpiAssignmentRepository assignmentRepository;
    private final KpiActualRepository actualRepository;
    private final EvaluationCycleRepository cycleRepository;

    public KpiService(KpiTreeRepository treeRepository,
                      KpiNodeRepository nodeRepository,
                      KpiAssignmentRepository assignmentRepository,
                      KpiActualRepository actualRepository,
                      EvaluationCycleRepository cycleRepository) {
        this.treeRepository = treeRepository;
        this.nodeRepository = nodeRepository;
        this.assignmentRepository = assignmentRepository;
        this.actualRepository = actualRepository;
        this.cycleRepository = cycleRepository;
    }

    // ═════════════════════════════════════════════════════════════════════
    // KpiTree
    // ═════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<KpiTreeResponse> listTrees(UUID cycleId) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireCycle(cycleId, tenantId);
        return treeRepository.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId)
            .stream().map(KpiTreeResponse::from).toList();
    }

    @Transactional
    public KpiTreeResponse createTree(UUID cycleId, KpiTreeCreateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        EvaluationCycle cycle = requireCycle(cycleId, tenantId);
        requireCycleWritable(cycle);

        KpiTree tree = new KpiTree();
        tree.setTenantId(tenantId);
        tree.setCycleId(cycleId);
        tree.setName(request.name());
        tree.setLevel(request.level());
        tree.setOwnerOrgUnitId(request.ownerOrgUnitId());
        tree.setBscEnabled(request.bscEnabled() != null && request.bscEnabled());
        return KpiTreeResponse.from(treeRepository.save(tree));
    }

    @Transactional(readOnly = true)
    public KpiTreeDetailResponse getTree(UUID treeId) {
        UUID tenantId = TenantSupport.currentTenantId();
        KpiTree tree = requireTree(treeId, tenantId);
        List<KpiNode> nodes = nodeRepository.findAllByTenantIdAndTreeIdOrderByCreatedAtAsc(tenantId, treeId);
        List<KpiNodeResponse> nodeResponses = toNodeResponses(tenantId, nodes);
        return KpiTreeDetailResponse.from(tree, nodeResponses);
    }

    @Transactional
    public KpiTreeResponse updateTree(UUID treeId, KpiTreeUpdateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        KpiTree tree = requireTree(treeId, tenantId);
        requireCycleWritable(requireCycle(tree.getCycleId(), tenantId));

        if (request.name() != null) {
            tree.setName(request.name());
        }
        if (request.level() != null) {
            tree.setLevel(request.level());
        }
        if (request.ownerOrgUnitId() != null) {
            tree.setOwnerOrgUnitId(request.ownerOrgUnitId());
        }
        if (request.bscEnabled() != null) {
            tree.setBscEnabled(request.bscEnabled());
        }
        return KpiTreeResponse.from(treeRepository.save(tree));
    }

    @Transactional
    public void deleteTree(UUID treeId) {
        UUID tenantId = TenantSupport.currentTenantId();
        KpiTree tree = requireTree(treeId, tenantId);
        requireCycleWritable(requireCycle(tree.getCycleId(), tenantId));
        // node/assignment/actual 은 FK ON DELETE CASCADE 로 동반 삭제.
        treeRepository.delete(tree);
    }

    // ═════════════════════════════════════════════════════════════════════
    // KpiNode
    // ═════════════════════════════════════════════════════════════════════

    @Transactional
    public KpiNodeResponse createNode(UUID treeId, KpiNodeCreateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        KpiTree tree = requireTree(treeId, tenantId);
        requireCycleWritable(requireCycle(tree.getCycleId(), tenantId));

        // P0: source 는 MANUAL 만 허용.
        KpiNodeSource source = request.source() == null ? KpiNodeSource.MANUAL : request.source();
        if (source != KpiNodeSource.MANUAL) {
            throw new ApiException(PerformanceErrorCode.KPI_SOURCE_NOT_SUPPORTED,
                Map.of("source", source.name(), "allowed", "MANUAL"));
        }

        validateWeightRange(request.weight());

        // parent 검증: 같은 tree 소속.
        UUID parentId = request.parentId();
        if (parentId != null) {
            KpiNode parent = requireNode(parentId, tenantId);
            if (!parent.getTreeId().equals(treeId)) {
                throw new ApiException(PerformanceErrorCode.KPI_NODE_PARENT_TREE_MISMATCH,
                    Map.of("parentId", parentId, "parentTreeId", parent.getTreeId(), "treeId", treeId));
            }
        }

        // 형제 가중치 합 가드 (신규 노드 추가 — 기존 형제 합 + new weight ≤ 1.0+tol).
        validateSiblingWeightSum(tenantId, treeId, parentId, null, request.weight());

        KpiNode node = new KpiNode();
        node.setTenantId(tenantId);
        node.setTreeId(treeId);
        node.setParentId(parentId);
        node.setLabel(request.label());
        node.setWeight(request.weight());
        node.setTarget(request.target());
        node.setUnit(request.unit());
        node.setBscPerspective(request.bscPerspective());
        node.setSource(source);
        node.setCascadeFromId(request.cascadeFromId());
        KpiNode saved = nodeRepository.save(node);
        return toNodeResponse(tenantId, saved);
    }

    @Transactional
    public KpiNodeResponse updateNode(UUID nodeId, KpiNodeUpdateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        KpiNode node = requireNode(nodeId, tenantId);
        KpiTree tree = requireTree(node.getTreeId(), tenantId);
        requireCycleWritable(requireCycle(tree.getCycleId(), tenantId));

        if (request.weight() != null) {
            validateWeightRange(request.weight());
            // 본인 행 제외한 형제 합 + new weight 가드.
            validateSiblingWeightSum(tenantId, node.getTreeId(), node.getParentId(), nodeId, request.weight());
            node.setWeight(request.weight());
        }
        if (request.label() != null) {
            node.setLabel(request.label());
        }
        if (request.target() != null) {
            node.setTarget(request.target());
        }
        if (request.unit() != null) {
            node.setUnit(request.unit());
        }
        if (request.bscPerspective() != null) {
            node.setBscPerspective(request.bscPerspective());
        }
        KpiNode saved = nodeRepository.save(node);
        return toNodeResponse(tenantId, saved);
    }

    @Transactional
    public void deleteNode(UUID nodeId) {
        UUID tenantId = TenantSupport.currentTenantId();
        KpiNode node = requireNode(nodeId, tenantId);
        KpiTree tree = requireTree(node.getTreeId(), tenantId);
        requireCycleWritable(requireCycle(tree.getCycleId(), tenantId));

        if (nodeRepository.existsByTenantIdAndParentId(tenantId, nodeId)) {
            throw new ApiException(PerformanceErrorCode.KPI_NODE_HAS_CHILDREN,
                Map.of("nodeId", nodeId));
        }
        // assignment/actual 은 FK ON DELETE CASCADE 로 동반 삭제.
        nodeRepository.delete(node);
    }

    // ═════════════════════════════════════════════════════════════════════
    // KpiAssignment
    // ═════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<KpiAssignmentResponse> listAssignments(UUID nodeId) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireNode(nodeId, tenantId);
        return assignmentRepository.findAllByTenantIdAndKpiNodeIdOrderByCreatedAtAsc(tenantId, nodeId)
            .stream().map(KpiAssignmentResponse::from).toList();
    }

    @Transactional
    public KpiAssignmentResponse createAssignment(UUID nodeId, KpiAssignmentCreateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        KpiNode node = requireNode(nodeId, tenantId);
        KpiTree tree = requireTree(node.getTreeId(), tenantId);
        requireCycleWritable(requireCycle(tree.getCycleId(), tenantId));

        if (request.weight() != null) {
            validateWeightRange(request.weight());
        }
        if (assignmentRepository.existsByTenantIdAndKpiNodeIdAndEmployeeId(tenantId, nodeId, request.employeeId())) {
            throw new ApiException(PerformanceErrorCode.KPI_ASSIGNMENT_DUPLICATE,
                Map.of("kpiNodeId", nodeId, "employeeId", request.employeeId()));
        }

        KpiAssignment assignment = new KpiAssignment();
        assignment.setTenantId(tenantId);
        assignment.setKpiNodeId(nodeId);
        assignment.setEmployeeId(request.employeeId());
        assignment.setWeight(request.weight());
        assignment.setTargetOverride(request.targetOverride());
        return KpiAssignmentResponse.from(assignmentRepository.save(assignment));
    }

    @Transactional
    public KpiAssignmentResponse updateAssignment(UUID assignmentId, KpiAssignmentUpdateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        KpiAssignment assignment = requireAssignment(assignmentId, tenantId);
        KpiNode node = requireNode(assignment.getKpiNodeId(), tenantId);
        KpiTree tree = requireTree(node.getTreeId(), tenantId);
        requireCycleWritable(requireCycle(tree.getCycleId(), tenantId));

        if (request.weight() != null) {
            validateWeightRange(request.weight());
            assignment.setWeight(request.weight());
        }
        if (request.targetOverride() != null) {
            assignment.setTargetOverride(request.targetOverride());
        }
        return KpiAssignmentResponse.from(assignmentRepository.save(assignment));
    }

    @Transactional
    public void deleteAssignment(UUID assignmentId) {
        UUID tenantId = TenantSupport.currentTenantId();
        KpiAssignment assignment = requireAssignment(assignmentId, tenantId);
        KpiNode node = requireNode(assignment.getKpiNodeId(), tenantId);
        KpiTree tree = requireTree(node.getTreeId(), tenantId);
        requireCycleWritable(requireCycle(tree.getCycleId(), tenantId));
        // actual 은 FK ON DELETE CASCADE 로 동반 삭제.
        assignmentRepository.delete(assignment);
    }

    /**
     * My KPI — cycle 의 employee 배정 전부 + effective weight/target + latest actual + 달성률.
     * 읽기 전용 — cycle lock 무관 (조회).
     */
    @Transactional(readOnly = true)
    public List<MyKpiAssignmentResponse> listMyAssignments(UUID cycleId, UUID employeeId) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireCycle(cycleId, tenantId);
        List<KpiAssignment> assignments = assignmentRepository.findMyAssignments(tenantId, cycleId, employeeId);

        // node/tree 컨텍스트 캐시 (N+1 완화 — 페이지당 소량 가정, P1 batch 최적화).
        Map<UUID, KpiNode> nodeCache = new HashMap<>();
        Map<UUID, KpiTree> treeCache = new HashMap<>();
        List<MyKpiAssignmentResponse> result = new ArrayList<>(assignments.size());
        for (KpiAssignment a : assignments) {
            KpiNode node = nodeCache.computeIfAbsent(a.getKpiNodeId(),
                id -> nodeRepository.findByIdAndTenantId(id, tenantId).orElse(null));
            if (node == null) {
                continue;
            }
            KpiTree tree = treeCache.computeIfAbsent(node.getTreeId(),
                id -> treeRepository.findByIdAndTenantId(id, tenantId).orElse(null));

            BigDecimal effectiveWeight = a.getWeight() != null ? a.getWeight() : node.getWeight();
            BigDecimal effectiveTarget = a.getTargetOverride() != null ? a.getTargetOverride() : node.getTarget();

            KpiActual latest = latestActual(tenantId, a.getId());
            BigDecimal latestValue = latest != null ? latest.getActualValue() : null;
            BigDecimal achievementRate = computeAchievementRate(latestValue, effectiveTarget);

            result.add(new MyKpiAssignmentResponse(
                a.getId(),
                a.getKpiNodeId(),
                node.getLabel(),
                node.getTreeId(),
                tree != null ? tree.getName() : null,
                cycleId,
                effectiveWeight,
                effectiveTarget,
                node.getUnit(),
                node.getBscPerspective(),
                node.getSource(),
                latestValue,
                latest != null ? latest.getAsOfDate() : null,
                achievementRate
            ));
        }
        return result;
    }

    // ═════════════════════════════════════════════════════════════════════
    // KpiActual (append-only)
    // ═════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<KpiActualResponse> listActuals(UUID assignmentId) {
        UUID tenantId = TenantSupport.currentTenantId();
        requireAssignment(assignmentId, tenantId);
        List<KpiActual> actuals =
            actualRepository.findAllByTenantIdAndKpiAssignmentIdOrderByAsOfDateDescCreatedAtDesc(tenantId, assignmentId);
        // superseded 여부 = 다른 row 가 이 row 를 supersede 했는가. 이력 내에서 supersedesId 집합으로 판정.
        Set<UUID> supersededIds = actuals.stream()
            .map(KpiActual::getSupersedesId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        return actuals.stream()
            .map(a -> KpiActualResponse.from(a, supersededIds.contains(a.getId())))
            .toList();
    }

    @Transactional
    public KpiActualResponse createActual(UUID assignmentId, KpiActualCreateRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        KpiAssignment assignment = requireAssignment(assignmentId, tenantId);
        KpiNode node = requireNode(assignment.getKpiNodeId(), tenantId);
        KpiTree tree = requireTree(node.getTreeId(), tenantId);
        requireCycleWritable(requireCycle(tree.getCycleId(), tenantId));

        KpiActual actual = new KpiActual();
        actual.setTenantId(tenantId);
        actual.setKpiAssignmentId(assignmentId);
        actual.setAsOfDate(request.asOfDate());
        actual.setActualValue(request.actualValue());
        actual.setSource(KpiActualSource.MANUAL); // P0 서버 고정.
        actual.setEvidenceUrl(request.evidenceUrl());
        actual.setComment(request.comment());
        actual.setSupersedesId(null);
        KpiActual saved = actualRepository.save(actual);
        return KpiActualResponse.from(saved, false);
    }

    /**
     * 실적 정정 — append-only supersede. 원본을 UPDATE 하지 않고 신규 row 생성 (supersedesId=원본).
     * 이미 supersede 된 원본은 재정정 거부 (KPI_ACTUAL_ALREADY_SUPERSEDED).
     */
    @Transactional
    public KpiActualResponse supersedeActual(UUID actualId, KpiActualSupersedeRequest request) {
        UUID tenantId = TenantSupport.currentTenantId();
        KpiActual original = requireActual(actualId, tenantId);
        KpiAssignment assignment = requireAssignment(original.getKpiAssignmentId(), tenantId);
        KpiNode node = requireNode(assignment.getKpiNodeId(), tenantId);
        KpiTree tree = requireTree(node.getTreeId(), tenantId);
        requireCycleWritable(requireCycle(tree.getCycleId(), tenantId));

        // 이미 supersede 된 원본 재정정 차단 (UNIQUE supersedes_id 위반 사전 방지).
        if (actualRepository.existsByTenantIdAndSupersedesId(tenantId, actualId)) {
            throw new ApiException(PerformanceErrorCode.KPI_ACTUAL_ALREADY_SUPERSEDED,
                Map.of("actualId", actualId));
        }

        KpiActual correction = new KpiActual();
        correction.setTenantId(tenantId);
        correction.setKpiAssignmentId(original.getKpiAssignmentId());
        // asOfDate 미제공 시 원본 승계.
        correction.setAsOfDate(request.asOfDate() != null ? request.asOfDate() : original.getAsOfDate());
        correction.setActualValue(request.actualValue());
        correction.setSource(KpiActualSource.MANUAL); // P0 서버 고정.
        correction.setEvidenceUrl(request.evidenceUrl());
        correction.setComment(request.comment());
        correction.setSupersedesId(actualId);
        KpiActual saved = actualRepository.save(correction);
        return KpiActualResponse.from(saved, false);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 파생 계산 / 응답 매핑
    // ═════════════════════════════════════════════════════════════════════

    /** node 1건 → 응답 (childWeightSum/complete + assignmentCount 계산). */
    private KpiNodeResponse toNodeResponse(UUID tenantId, KpiNode node) {
        return toNodeResponses(tenantId, List.of(node)).get(0);
    }

    /**
     * node 리스트 → 응답 리스트. childWeightSum = 본 노드의 직속 자식 weight 합 (본 노드가 parent 인
     * 노드들). childWeightComplete = 합 == 1.0 ±0.001 (자식 없으면 false). assignmentCount = 본 노드
     * 배정 수.
     */
    private List<KpiNodeResponse> toNodeResponses(UUID tenantId, List<KpiNode> nodes) {
        List<KpiNodeResponse> result = new ArrayList<>(nodes.size());
        for (KpiNode node : nodes) {
            List<KpiNode> children = nodeRepository.findSiblings(tenantId, node.getTreeId(), node.getId());
            BigDecimal childSum = children.stream()
                .map(KpiNode::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean complete = !children.isEmpty() && childSum.subtract(ONE).abs().compareTo(WEIGHT_TOLERANCE) <= 0;
            long assignmentCount = assignmentRepository
                .findAllByTenantIdAndKpiNodeIdOrderByCreatedAtAsc(tenantId, node.getId()).size();
            result.add(KpiNodeResponse.from(node, childSum, complete, assignmentCount));
        }
        return result;
    }

    /** supersede 안 된 actual 중 max(asOfDate, createdAt) — DESC 정렬 첫 행. */
    private KpiActual latestActual(UUID tenantId, UUID assignmentId) {
        List<KpiActual> live = actualRepository
            .findAllByTenantIdAndKpiAssignmentIdAndSupersedesIdIsNullOrderByAsOfDateDescCreatedAtDesc(tenantId, assignmentId);
        if (live.isEmpty()) {
            return null;
        }
        // supersede 안 된 row 중 후속 정정본이 있으면 그 정정본을 latest 로 — 정정 체인 추적.
        // P0 단순화: supersedesId IS NULL 인 최초 row 는 정정되면 supersededIds 에 들어가 제외되어야 하나,
        // append-only 모델에서 latest 는 "아무도 supersede 하지 않은 살아있는 row" 가 정답.
        // 즉 이 row.id 를 supersedesId 로 가리키는 정정본이 없어야 함.
        for (KpiActual a : live) {
            if (!actualRepository.existsByTenantIdAndSupersedesId(tenantId, a.getId())) {
                return a;
            }
        }
        return null;
    }

    /** achievementRate = latestValue ÷ effectiveTarget. target null·0 또는 value null → null. */
    private BigDecimal computeAchievementRate(BigDecimal latestValue, BigDecimal effectiveTarget) {
        if (latestValue == null || effectiveTarget == null
            || effectiveTarget.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return latestValue.divide(effectiveTarget, 6, java.math.RoundingMode.HALF_UP);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 검증 헬퍼
    // ═════════════════════════════════════════════════════════════════════

    private void validateWeightRange(BigDecimal weight) {
        if (weight == null
            || weight.compareTo(BigDecimal.ZERO) <= 0
            || weight.compareTo(ONE) > 0) {
            throw new ApiException(PerformanceErrorCode.KPI_WEIGHT_OUT_OF_RANGE,
                Map.of("weight", weight == null ? "null" : weight.toPlainString(), "range", "(0,1]"));
        }
    }

    /**
     * 형제(같은 tree + 같은 parent) weight 합 가드 — 쓰기 시 합 ≤ 1.0+0.001 (초과만 거부).
     * excludeNodeId 는 update 흐름의 본인 행 (기존 weight 제외). 트리 점진 구축 중 합 &lt; 1.0 은 허용.
     */
    private void validateSiblingWeightSum(UUID tenantId, UUID treeId, UUID parentId,
                                          UUID excludeNodeId, BigDecimal newWeight) {
        List<KpiNode> siblings = nodeRepository.findSiblings(tenantId, treeId, parentId);
        BigDecimal others = BigDecimal.ZERO;
        for (KpiNode s : siblings) {
            if (excludeNodeId != null && s.getId().equals(excludeNodeId)) {
                continue;
            }
            others = others.add(s.getWeight());
        }
        BigDecimal total = others.add(newWeight);
        if (total.compareTo(WEIGHT_MAX) > 0) {
            throw new ApiException(PerformanceErrorCode.KPI_WEIGHT_SUM_EXCEEDED,
                Map.of("siblingSum", others.toPlainString(),
                    "newWeight", newWeight.toPlainString(),
                    "total", total.toPlainString(),
                    "max", "1.0"));
        }
    }

    private void requireCycleWritable(EvaluationCycle cycle) {
        if (LOCKED_STATUSES.contains(cycle.getStatus())) {
            throw new ApiException(PerformanceErrorCode.KPI_CYCLE_LOCKED,
                Map.of("cycleId", cycle.getId(), "status", cycle.getStatus().name()));
        }
    }

    private EvaluationCycle requireCycle(UUID cycleId, UUID tenantId) {
        return cycleRepository.findByIdAndTenantId(cycleId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND,
                Map.of("entity", "EvaluationCycle", "id", cycleId)));
    }

    private KpiTree requireTree(UUID treeId, UUID tenantId) {
        return treeRepository.findByIdAndTenantId(treeId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.KPI_TREE_NOT_FOUND,
                Map.of("entity", "KpiTree", "id", treeId)));
    }

    private KpiNode requireNode(UUID nodeId, UUID tenantId) {
        return nodeRepository.findByIdAndTenantId(nodeId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.KPI_NODE_NOT_FOUND,
                Map.of("entity", "KpiNode", "id", nodeId)));
    }

    private KpiAssignment requireAssignment(UUID assignmentId, UUID tenantId) {
        return assignmentRepository.findByIdAndTenantId(assignmentId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.KPI_ASSIGNMENT_NOT_FOUND,
                Map.of("entity", "KpiAssignment", "id", assignmentId)));
    }

    private KpiActual requireActual(UUID actualId, UUID tenantId) {
        return actualRepository.findByIdAndTenantId(actualId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.KPI_ACTUAL_NOT_FOUND,
                Map.of("entity", "KpiActual", "id", actualId)));
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.kpi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.CycleType;
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
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiTreeCreateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.MyKpiAssignmentResponse;
import com.easyperformance.domain.kpi.entity.BscPerspective;
import com.easyperformance.domain.kpi.entity.KpiActual;
import com.easyperformance.domain.kpi.entity.KpiActualSource;
import com.easyperformance.domain.kpi.entity.KpiAssignment;
import com.easyperformance.domain.kpi.entity.KpiNode;
import com.easyperformance.domain.kpi.entity.KpiNodeSource;
import com.easyperformance.domain.kpi.entity.KpiTree;
import com.easyperformance.domain.kpi.entity.KpiTreeLevel;
import com.easyperformance.domain.kpi.repository.KpiActualRepository;
import com.easyperformance.domain.kpi.repository.KpiAssignmentRepository;
import com.easyperformance.domain.kpi.repository.KpiNodeRepository;
import com.easyperformance.domain.kpi.repository.KpiTreeRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;

/**
 * KpiService 단위 테스트 — P0-S2 (p0_s2_contract.md §3 검증 매트릭스 + 파생 계산).
 *
 * <p>커버: 12 ErrorCode 전부 + happy path (tree/node/assignment/actual create) + supersede append-only +
 * 형제 weight 합 가드 (update 본인 행 제외) + my KPI effective weight/achievementRate 파생.
 *
 * <p>TenantSupport fallback UUID (단계 1 단일 DB) — Mockito mock 으로 repository 격리.
 */
@ExtendWith(MockitoExtension.class)
class KpiServiceTest {

    @Mock private KpiTreeRepository treeRepository;
    @Mock private KpiNodeRepository nodeRepository;
    @Mock private KpiAssignmentRepository assignmentRepository;
    @Mock private KpiActualRepository actualRepository;
    @Mock private EvaluationCycleRepository cycleRepository;

    @InjectMocks private KpiService service;

    private UUID tenantId;
    private UUID cycleId;
    private UUID treeId;
    private UUID nodeId;
    private UUID assignmentId;
    private UUID actualId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        cycleId = UUID.randomUUID();
        treeId = UUID.randomUUID();
        nodeId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();
        actualId = UUID.randomUUID();
    }

    // ═══════════════════════════ KpiTree ═══════════════════════════

    @Test
    void createTree_happy() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.GOAL_SETTING)));
        when(treeRepository.save(any(KpiTree.class))).thenAnswer(inv -> {
            KpiTree t = inv.getArgument(0);
            if (t.getId() == null) t.setId(treeId);
            return t;
        });

        var response = service.createTree(cycleId,
            new KpiTreeCreateRequest("전사 KPI", KpiTreeLevel.CORPORATE, null, true));

        assertThat(response.name()).isEqualTo("전사 KPI");
        assertThat(response.level()).isEqualTo(KpiTreeLevel.CORPORATE);
        assertThat(response.bscEnabled()).isTrue();
        assertThat(response.cycleId()).isEqualTo(cycleId);
    }

    @Test
    void createTree_lockedCycle_isRejected() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.FINALIZED)));

        assertThatThrownBy(() -> service.createTree(cycleId,
                new KpiTreeCreateRequest("X", KpiTreeLevel.TEAM, null, false)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.KPI_CYCLE_LOCKED);
    }

    @Test
    void createTree_cycleNotFound_throws() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTree(cycleId,
                new KpiTreeCreateRequest("X", KpiTreeLevel.TEAM, null, false)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.CYCLE_NOT_FOUND);
    }

    @Test
    void getTree_notFound_throws() {
        when(treeRepository.findByIdAndTenantId(eq(treeId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTree(treeId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.KPI_TREE_NOT_FOUND);
    }

    // ═══════════════════════════ KpiNode ═══════════════════════════

    @Test
    void createNode_rootHappy_computesChildSum() {
        stubTreeWithCycle(CycleStatus.GOAL_SETTING);
        when(nodeRepository.findSiblings(any(), eq(treeId), eq((UUID) null))).thenReturn(List.of());
        when(nodeRepository.save(any(KpiNode.class))).thenAnswer(inv -> {
            KpiNode n = inv.getArgument(0);
            if (n.getId() == null) n.setId(nodeId);
            return n;
        });
        // toNodeResponse: children of new node (none) + assignment count.
        when(nodeRepository.findSiblings(any(), eq(treeId), eq(nodeId))).thenReturn(List.of());
        when(assignmentRepository.findAllByTenantIdAndKpiNodeIdOrderByCreatedAtAsc(any(), eq(nodeId)))
            .thenReturn(List.of());

        KpiNodeResponse response = service.createNode(treeId,
            new KpiNodeCreateRequest(null, "매출", new BigDecimal("0.4000"),
                new BigDecimal("1000"), "억원", BscPerspective.FINANCIAL, null, null));

        assertThat(response.label()).isEqualTo("매출");
        assertThat(response.weight()).isEqualByComparingTo("0.4000");
        assertThat(response.source()).isEqualTo(KpiNodeSource.MANUAL);
        assertThat(response.childWeightComplete()).isFalse(); // no children
    }

    @Test
    void createNode_nonManualSource_isRejected() {
        stubTreeWithCycle(CycleStatus.GOAL_SETTING);

        assertThatThrownBy(() -> service.createNode(treeId,
                new KpiNodeCreateRequest(null, "X", new BigDecimal("0.5"), null, null, null,
                    KpiNodeSource.HCM, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.KPI_SOURCE_NOT_SUPPORTED);
    }

    @Test
    void createNode_weightOutOfRange_isRejected() {
        stubTreeWithCycle(CycleStatus.GOAL_SETTING);

        assertThatThrownBy(() -> service.createNode(treeId,
                new KpiNodeCreateRequest(null, "X", new BigDecimal("1.5"), null, null, null, null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.KPI_WEIGHT_OUT_OF_RANGE);
    }

    @Test
    void createNode_parentInDifferentTree_isRejected() {
        stubTreeWithCycle(CycleStatus.GOAL_SETTING);
        UUID parentId = UUID.randomUUID();
        UUID otherTreeId = UUID.randomUUID();
        KpiNode parent = node(parentId, otherTreeId, null, new BigDecimal("0.5"));
        when(nodeRepository.findByIdAndTenantId(eq(parentId), any())).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.createNode(treeId,
                new KpiNodeCreateRequest(parentId, "X", new BigDecimal("0.5"), null, null, null, null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.KPI_NODE_PARENT_TREE_MISMATCH);
    }

    @Test
    void createNode_siblingWeightSumExceeded_isRejected() {
        stubTreeWithCycle(CycleStatus.GOAL_SETTING);
        // 기존 형제 합 0.8 + new 0.3 = 1.1 > 1.0+tol.
        KpiNode existing = node(UUID.randomUUID(), treeId, null, new BigDecimal("0.8000"));
        when(nodeRepository.findSiblings(any(), eq(treeId), eq((UUID) null))).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createNode(treeId,
                new KpiNodeCreateRequest(null, "X", new BigDecimal("0.3000"), null, null, null, null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.KPI_WEIGHT_SUM_EXCEEDED);
    }

    @Test
    void updateNode_weightExcludesSelf_succeeds() {
        stubNodeWithCycle(CycleStatus.GOAL_SETTING);
        KpiNode self = node(nodeId, treeId, null, new BigDecimal("0.4000"));
        KpiNode sibling = node(UUID.randomUUID(), treeId, null, new BigDecimal("0.6000"));
        // self(0.4) + sibling(0.6) = 1.0. Update self → 0.4 again must pass (exclude self).
        when(nodeRepository.findSiblings(any(), eq(treeId), eq((UUID) null)))
            .thenReturn(List.of(self, sibling));
        when(nodeRepository.findByIdAndTenantId(eq(nodeId), any())).thenReturn(Optional.of(self));
        when(nodeRepository.save(any(KpiNode.class))).thenAnswer(inv -> inv.getArgument(0));
        when(nodeRepository.findSiblings(any(), eq(treeId), eq(nodeId))).thenReturn(List.of());
        when(assignmentRepository.findAllByTenantIdAndKpiNodeIdOrderByCreatedAtAsc(any(), eq(nodeId)))
            .thenReturn(List.of());

        var response = service.updateNode(nodeId,
            new com.easyperformance.domain.kpi.dto.KpiDtos.KpiNodeUpdateRequest(
                null, new BigDecimal("0.4000"), null, null, null));

        assertThat(response.weight()).isEqualByComparingTo("0.4000");
    }

    @Test
    void deleteNode_withChildren_isRejected() {
        stubNodeWithCycle(CycleStatus.GOAL_SETTING);
        when(nodeRepository.findByIdAndTenantId(eq(nodeId), any()))
            .thenReturn(Optional.of(node(nodeId, treeId, null, new BigDecimal("0.5"))));
        when(nodeRepository.existsByTenantIdAndParentId(any(), eq(nodeId))).thenReturn(true);

        assertThatThrownBy(() -> service.deleteNode(nodeId))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.KPI_NODE_HAS_CHILDREN);
    }

    // ═══════════════════════════ KpiAssignment ═══════════════════════════

    @Test
    void createAssignment_happy() {
        stubNodeWithCycle(CycleStatus.GOAL_SETTING);
        when(nodeRepository.findByIdAndTenantId(eq(nodeId), any()))
            .thenReturn(Optional.of(node(nodeId, treeId, null, new BigDecimal("0.5"))));
        UUID employeeId = UUID.randomUUID();
        when(assignmentRepository.existsByTenantIdAndKpiNodeIdAndEmployeeId(any(), eq(nodeId), eq(employeeId)))
            .thenReturn(false);
        when(assignmentRepository.save(any(KpiAssignment.class))).thenAnswer(inv -> {
            KpiAssignment a = inv.getArgument(0);
            if (a.getId() == null) a.setId(assignmentId);
            return a;
        });

        KpiAssignmentResponse response = service.createAssignment(nodeId,
            new KpiAssignmentCreateRequest(employeeId, new BigDecimal("0.3000"), new BigDecimal("500")));

        assertThat(response.employeeId()).isEqualTo(employeeId);
        assertThat(response.weight()).isEqualByComparingTo("0.3000");
    }

    @Test
    void createAssignment_duplicate_isRejected() {
        stubNodeWithCycle(CycleStatus.GOAL_SETTING);
        when(nodeRepository.findByIdAndTenantId(eq(nodeId), any()))
            .thenReturn(Optional.of(node(nodeId, treeId, null, new BigDecimal("0.5"))));
        UUID employeeId = UUID.randomUUID();
        when(assignmentRepository.existsByTenantIdAndKpiNodeIdAndEmployeeId(any(), eq(nodeId), eq(employeeId)))
            .thenReturn(true);

        assertThatThrownBy(() -> service.createAssignment(nodeId,
                new KpiAssignmentCreateRequest(employeeId, null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.KPI_ASSIGNMENT_DUPLICATE);
    }

    @Test
    void createAssignment_nodeNotFound_throws() {
        when(nodeRepository.findByIdAndTenantId(eq(nodeId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAssignment(nodeId,
                new KpiAssignmentCreateRequest(UUID.randomUUID(), null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.KPI_NODE_NOT_FOUND);
    }

    @Test
    void updateAssignment_notFound_throws() {
        when(assignmentRepository.findByIdAndTenantId(eq(assignmentId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAssignment(assignmentId,
                new KpiAssignmentUpdateRequest(new BigDecimal("0.2"), null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.KPI_ASSIGNMENT_NOT_FOUND);
    }

    @Test
    void listMyAssignments_computesEffectiveAndAchievementRate() {
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any()))
            .thenReturn(Optional.of(cycle(CycleStatus.SELF_REVIEW)));
        UUID employeeId = UUID.randomUUID();

        KpiNode node = node(nodeId, treeId, null, new BigDecimal("0.5000"));
        node.setTarget(new BigDecimal("100"));
        node.setUnit("건");
        KpiAssignment assignment = new KpiAssignment();
        assignment.setId(assignmentId);
        assignment.setTenantId(tenantId);
        assignment.setKpiNodeId(nodeId);
        assignment.setEmployeeId(employeeId);
        assignment.setWeight(new BigDecimal("0.7000"));        // override → effectiveWeight 0.7
        assignment.setTargetOverride(new BigDecimal("200"));   // override → effectiveTarget 200

        when(assignmentRepository.findMyAssignments(any(), eq(cycleId), eq(employeeId)))
            .thenReturn(List.of(assignment));
        when(nodeRepository.findByIdAndTenantId(eq(nodeId), any())).thenReturn(Optional.of(node));
        KpiTree tree = tree(treeId, cycleId);
        tree.setName("팀 KPI");
        when(treeRepository.findByIdAndTenantId(eq(treeId), any())).thenReturn(Optional.of(tree));

        KpiActual live = actual(actualId, assignmentId, new BigDecimal("150"), null);
        when(actualRepository.findAllByTenantIdAndKpiAssignmentIdAndSupersedesIdIsNullOrderByAsOfDateDescCreatedAtDesc(
                any(), eq(assignmentId))).thenReturn(List.of(live));
        when(actualRepository.existsByTenantIdAndSupersedesId(any(), eq(actualId))).thenReturn(false);

        List<MyKpiAssignmentResponse> result = service.listMyAssignments(cycleId, employeeId);

        assertThat(result).hasSize(1);
        MyKpiAssignmentResponse r = result.get(0);
        assertThat(r.weight()).isEqualByComparingTo("0.7000");   // effective = override
        assertThat(r.target()).isEqualByComparingTo("200");      // effective = override
        assertThat(r.treeName()).isEqualTo("팀 KPI");
        assertThat(r.latestActualValue()).isEqualByComparingTo("150");
        // achievementRate = 150 / 200 = 0.75
        assertThat(r.achievementRate()).isEqualByComparingTo("0.75");
    }

    // ═══════════════════════════ KpiActual (append-only) ═══════════════════════════

    @Test
    void createActual_forcesManualSource() {
        stubAssignmentChainWithCycle(CycleStatus.SELF_REVIEW);
        when(actualRepository.save(any(KpiActual.class))).thenAnswer(inv -> {
            KpiActual a = inv.getArgument(0);
            if (a.getId() == null) a.setId(actualId);
            return a;
        });

        KpiActualResponse response = service.createActual(assignmentId,
            new KpiActualCreateRequest(LocalDate.of(2026, 3, 31), new BigDecimal("80"), "http://e", "ok"));

        assertThat(response.source()).isEqualTo(KpiActualSource.MANUAL);
        assertThat(response.actualValue()).isEqualByComparingTo("80");
        assertThat(response.supersedesId()).isNull();
        assertThat(response.superseded()).isFalse();
    }

    @Test
    void supersedeActual_createsNewRowReferencingOriginal() {
        stubAssignmentChainWithCycle(CycleStatus.CALIBRATION);
        KpiActual original = actual(actualId, assignmentId, new BigDecimal("80"), null);
        when(actualRepository.findByIdAndTenantId(eq(actualId), any())).thenReturn(Optional.of(original));
        when(actualRepository.existsByTenantIdAndSupersedesId(any(), eq(actualId))).thenReturn(false);
        UUID newId = UUID.randomUUID();
        when(actualRepository.save(any(KpiActual.class))).thenAnswer(inv -> {
            KpiActual a = inv.getArgument(0);
            if (a.getId() == null) a.setId(newId);
            return a;
        });

        KpiActualResponse response = service.supersedeActual(actualId,
            new KpiActualSupersedeRequest(null, new BigDecimal("95"), null, "정정"));

        ArgumentCaptor<KpiActual> captor = ArgumentCaptor.forClass(KpiActual.class);
        verify(actualRepository).save(captor.capture());
        KpiActual saved = captor.getValue();
        assertThat(saved.getSupersedesId()).isEqualTo(actualId);     // 신규 row → 원본 가리킴
        assertThat(saved.getAsOfDate()).isEqualTo(original.getAsOfDate()); // 미제공 → 원본 승계
        assertThat(saved.getActualValue()).isEqualByComparingTo("95");
        assertThat(response.actualValue()).isEqualByComparingTo("95");
    }

    @Test
    void supersedeActual_alreadySuperseded_isRejected() {
        stubAssignmentChainWithCycle(CycleStatus.CALIBRATION);
        KpiActual original = actual(actualId, assignmentId, new BigDecimal("80"), null);
        when(actualRepository.findByIdAndTenantId(eq(actualId), any())).thenReturn(Optional.of(original));
        when(actualRepository.existsByTenantIdAndSupersedesId(any(), eq(actualId))).thenReturn(true);

        assertThatThrownBy(() -> service.supersedeActual(actualId,
                new KpiActualSupersedeRequest(null, new BigDecimal("95"), null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.KPI_ACTUAL_ALREADY_SUPERSEDED);
        verify(actualRepository, never()).save(any());
    }

    @Test
    void supersedeActual_actualNotFound_throws() {
        when(actualRepository.findByIdAndTenantId(eq(actualId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.supersedeActual(actualId,
                new KpiActualSupersedeRequest(null, new BigDecimal("1"), null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.KPI_ACTUAL_NOT_FOUND);
    }

    // ═══════════════════════════ fixtures ═══════════════════════════

    private EvaluationCycle cycle(CycleStatus status) {
        EvaluationCycle c = new EvaluationCycle();
        c.setId(cycleId);
        c.setTenantId(tenantId);
        c.setName("2026 상반기");
        c.setPeriodStart(LocalDate.of(2026, 1, 1));
        c.setPeriodEnd(LocalDate.of(2026, 6, 30));
        c.setCycleType(CycleType.HALF_ANNUAL);
        c.setStatus(status);
        return c;
    }

    private KpiTree tree(UUID id, UUID cycle) {
        KpiTree t = new KpiTree();
        t.setId(id);
        t.setTenantId(tenantId);
        t.setCycleId(cycle);
        t.setName("Tree");
        t.setLevel(KpiTreeLevel.TEAM);
        t.setBscEnabled(false);
        return t;
    }

    private KpiNode node(UUID id, UUID tree, UUID parent, BigDecimal weight) {
        KpiNode n = new KpiNode();
        n.setId(id);
        n.setTenantId(tenantId);
        n.setTreeId(tree);
        n.setParentId(parent);
        n.setLabel("Node");
        n.setWeight(weight);
        n.setSource(KpiNodeSource.MANUAL);
        return n;
    }

    private KpiActual actual(UUID id, UUID assignment, BigDecimal value, UUID supersedes) {
        KpiActual a = new KpiActual();
        a.setId(id);
        a.setTenantId(tenantId);
        a.setKpiAssignmentId(assignment);
        a.setAsOfDate(LocalDate.of(2026, 3, 31));
        a.setActualValue(value);
        a.setSource(KpiActualSource.MANUAL);
        a.setSupersedesId(supersedes);
        return a;
    }

    /** tree + cycle 체인 (createNode 진입 경로). */
    private void stubTreeWithCycle(CycleStatus status) {
        when(treeRepository.findByIdAndTenantId(eq(treeId), any())).thenReturn(Optional.of(tree(treeId, cycleId)));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(cycle(status)));
    }

    /** node → tree → cycle 체인 (node update/delete 진입). */
    private void stubNodeWithCycle(CycleStatus status) {
        lenient().when(nodeRepository.findByIdAndTenantId(eq(nodeId), any()))
            .thenReturn(Optional.of(node(nodeId, treeId, null, new BigDecimal("0.5"))));
        when(treeRepository.findByIdAndTenantId(eq(treeId), any())).thenReturn(Optional.of(tree(treeId, cycleId)));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(cycle(status)));
    }

    /** assignment → node → tree → cycle 체인 (actual create/supersede 진입). */
    private void stubAssignmentChainWithCycle(CycleStatus status) {
        KpiAssignment a = new KpiAssignment();
        a.setId(assignmentId);
        a.setTenantId(tenantId);
        a.setKpiNodeId(nodeId);
        a.setEmployeeId(UUID.randomUUID());
        when(assignmentRepository.findByIdAndTenantId(eq(assignmentId), any())).thenReturn(Optional.of(a));
        when(nodeRepository.findByIdAndTenantId(eq(nodeId), any()))
            .thenReturn(Optional.of(node(nodeId, treeId, null, new BigDecimal("0.5"))));
        when(treeRepository.findByIdAndTenantId(eq(treeId), any())).thenReturn(Optional.of(tree(treeId, cycleId)));
        when(cycleRepository.findByIdAndTenantId(eq(cycleId), any())).thenReturn(Optional.of(cycle(status)));
    }
}

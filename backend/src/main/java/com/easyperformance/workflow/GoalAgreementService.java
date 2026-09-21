package com.easyperformance.workflow;

import com.easyware.platform.UuidV7;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiAssignmentCreateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiAssignmentResponse;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiNodeCreateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiNodeUpdateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiAssignmentUpdateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiNodeResponse;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiTreeCreateRequest;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiTreeResponse;
import com.easyperformance.domain.kpi.dto.KpiDtos.MyKpiAssignmentResponse;
import com.easyperformance.domain.kpi.entity.KpiNodeSource;
import com.easyperformance.domain.kpi.entity.KpiTreeLevel;
import com.easyperformance.domain.kpi.service.KpiService;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.GoalCreateRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.GoalDecisionRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.GoalResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.GoalUpdateRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CheckInCreateRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CheckInResponse;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GoalAgreementService {
    private final EvaluationParticipantRepository participants;
    private final EvaluationReviewerAssignmentRepository reviewers;
    private final EvaluationGoalRepository goals;
    private final KpiService kpis;
    private final EvaluationCycleRepository cycles;
    private final EvaluationGoalCheckInRepository checkIns;

    public GoalAgreementService(EvaluationParticipantRepository participants,
                                EvaluationReviewerAssignmentRepository reviewers,
                                EvaluationGoalRepository goals,
                                KpiService kpis,
                                EvaluationCycleRepository cycles,
                                EvaluationGoalCheckInRepository checkIns) {
        this.participants = participants;
        this.reviewers = reviewers;
        this.goals = goals;
        this.kpis = kpis;
        this.cycles = cycles;
        this.checkIns = checkIns;
    }

    @Transactional
    public GoalResponse create(ActorAccess.Actor actor, UUID cycleId, GoalCreateRequest request) {
        requireStage(actor.tenantId(), cycleId, CycleStatus.GOAL_SETTING);
        UUID employeeId = request.employeeId() == null ? actor.employeeId() : request.employeeId();
        if (employeeId == null) {
            throw new ApiException(PerformanceErrorCode.ACTOR_EMPLOYEE_BINDING_REQUIRED);
        }
        EvaluationParticipant participant = participants
            .findByTenantIdAndCycleIdAndEmployeeId(actor.tenantId(), cycleId, employeeId)
            .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.PARTICIPANT_NOT_FOUND));
        requireSelfManagerOrHr(actor, participant);

        KpiTreeResponse tree = kpis.createTree(cycleId,
            new KpiTreeCreateRequest("Goals - " + employeeId, KpiTreeLevel.INDIVIDUAL, null, false));
        KpiNodeResponse node = kpis.createNode(tree.id(),
            new KpiNodeCreateRequest(null, request.title(), request.weight(), request.target(), request.unit(),
                null, KpiNodeSource.MANUAL, null));
        KpiAssignmentResponse assignment = kpis.createAssignment(node.id(),
            new KpiAssignmentCreateRequest(employeeId, request.weight(), request.target()));

        EvaluationGoal goal = new EvaluationGoal();
        goal.setId(UuidV7.generate());
        goal.setTenantId(actor.tenantId());
        goal.setCycleId(cycleId);
        goal.setParticipantId(participant.getId());
        goal.setEmployeeId(employeeId);
        goal.setKpiAssignmentId(assignment.id());
        goal.setKpiNodeId(node.id());
        goal.setTitle(request.title());
        goal.setDescription(request.description());
        goal.setStatus(GoalStatus.DRAFT);
        return response(goals.save(goal), request.weight(), request.target(), request.unit());
    }

    @Transactional
    public GoalResponse update(ActorAccess.Actor actor, UUID goalId, GoalUpdateRequest request) {
        EvaluationGoal goal = requireGoal(actor.tenantId(), goalId);
        requireStage(actor.tenantId(), goal.getCycleId(), CycleStatus.GOAL_SETTING);
        if (actor.employeeId() == null || !actor.employeeId().equals(goal.getEmployeeId())
            || (goal.getStatus() != GoalStatus.DRAFT && goal.getStatus() != GoalStatus.REJECTED)) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_FORBIDDEN);
        }
        if (request.title() != null) goal.setTitle(request.title());
        if (request.description() != null) goal.setDescription(request.description());
        if (request.title() != null || request.weight() != null || request.target() != null || request.unit() != null) {
            kpis.updateNode(goal.getKpiNodeId(), new KpiNodeUpdateRequest(
                request.title(), request.weight(), request.target(), request.unit(), null));
        }
        if (request.weight() != null || request.target() != null) {
            kpis.updateAssignment(goal.getKpiAssignmentId(),
                new KpiAssignmentUpdateRequest(request.weight(), request.target()));
        }
        goal.setStatus(GoalStatus.DRAFT);
        goal.setDecisionComment(null);
        return hydrated(goals.save(goal));
    }

    @Transactional
    public CheckInResponse checkIn(ActorAccess.Actor actor, UUID goalId, CheckInCreateRequest request) {
        EvaluationGoal goal = requireGoal(actor.tenantId(), goalId);
        requireStage(actor.tenantId(), goal.getCycleId(), CycleStatus.MID_REVIEW);
        if (actor.employeeId() == null || !actor.employeeId().equals(goal.getEmployeeId())
            || goal.getStatus() != GoalStatus.APPROVED) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_FORBIDDEN);
        }
        var actual = kpis.createActual(goal.getKpiAssignmentId(),
            new com.easyperformance.domain.kpi.dto.KpiDtos.KpiActualCreateRequest(
                request.asOfDate(), request.actualValue(), request.evidenceUrl(), request.note()));
        EvaluationGoalCheckIn metadata = new EvaluationGoalCheckIn();
        metadata.setKpiActualId(actual.id());
        metadata.setTenantId(actor.tenantId());
        metadata.setGoalId(goalId);
        metadata.setProgressPercent(request.progressPercent());
        checkIns.save(metadata);
        return new CheckInResponse(actual.id(), goalId, actual.asOfDate(), actual.actualValue(),
            request.progressPercent(), actual.comment(), actual.evidenceUrl(), actual.createdAt());
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> checkIns(ActorAccess.Actor actor, UUID goalId) {
        EvaluationGoal goal = requireGoal(actor.tenantId(), goalId);
        EvaluationParticipant participant = participants.findByIdAndTenantId(goal.getParticipantId(), actor.tenantId())
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.PARTICIPANT_NOT_FOUND));
        requireSelfManagerOrHr(actor, participant);
        Map<UUID, BigDecimal> progressByActualId = new java.util.HashMap<>();
        checkIns.findAllByTenantIdAndGoalId(actor.tenantId(), goalId)
            .forEach(metadata -> progressByActualId.put(
                metadata.getKpiActualId(), metadata.getProgressPercent()));
        return kpis.listActuals(goal.getKpiAssignmentId()).stream()
            .map(a -> new CheckInResponse(a.id(), goalId, a.asOfDate(), a.actualValue(),
                progressByActualId.get(a.id()),
                a.comment(), a.evidenceUrl(), a.createdAt())).toList();
    }

    @Transactional
    public GoalResponse submit(ActorAccess.Actor actor, UUID goalId) {
        EvaluationGoal goal = requireGoal(actor.tenantId(), goalId);
        requireStage(actor.tenantId(), goal.getCycleId(), CycleStatus.GOAL_SETTING);
        if (!goal.getEmployeeId().equals(actor.employeeId()) ||
            (goal.getStatus() != GoalStatus.DRAFT && goal.getStatus() != GoalStatus.REJECTED)) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_FORBIDDEN);
        }
        goal.setStatus(GoalStatus.PENDING_APPROVAL);
        return hydrated(goals.save(goal));
    }

    @Transactional
    public GoalResponse decide(ActorAccess.Actor actor, UUID goalId, GoalDecisionRequest request) {
        EvaluationGoal goal = requireGoal(actor.tenantId(), goalId);
        requireStage(actor.tenantId(), goal.getCycleId(), CycleStatus.GOAL_SETTING);
        EvaluationParticipant participant = participants.findByIdAndTenantId(goal.getParticipantId(), actor.tenantId())
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.PARTICIPANT_NOT_FOUND));
        requireAssignedManager(actor, participant);
        if (goal.getStatus() != GoalStatus.PENDING_APPROVAL) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED,
                Map.of("goalId", goalId, "status", goal.getStatus().name()));
        }
        goal.setDecisionComment(request.comment());
        if (request.decision() == GoalDecision.APPROVE) {
            goal.setStatus(GoalStatus.APPROVED);
            goal.setApprovedAt(Instant.now());
            goal.setApprovedBy(actor.employeeId());
        } else {
            goal.setStatus(GoalStatus.REJECTED);
            goal.setApprovedAt(null);
            goal.setApprovedBy(null);
        }
        return hydrated(goals.save(goal));
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(UUID tenantId, UUID cycleId, UUID employeeId) {
        List<MyKpiAssignmentResponse> assignments = kpis.listMyAssignments(cycleId, employeeId);
        Map<UUID, MyKpiAssignmentResponse> byId = assignments.stream()
            .collect(java.util.stream.Collectors.toMap(MyKpiAssignmentResponse::id, a -> a));
        return goals.findAllByTenantIdAndCycleIdAndEmployeeIdOrderByCreatedAtAsc(tenantId, cycleId, employeeId)
            .stream().map(g -> response(g, byId.get(g.getKpiAssignmentId()))).toList();
    }

    private void requireSelfManagerOrHr(ActorAccess.Actor actor, EvaluationParticipant participant) {
        if (participant.getEmployeeId().equals(actor.employeeId()) || "HR_ADMIN".equals(actor.role())
            || "SUPER_ADMIN".equals(actor.role())) return;
        requireAssignedManager(actor, participant);
    }

    private void requireAssignedManager(ActorAccess.Actor actor, EvaluationParticipant participant) {
        EvaluationReviewerAssignment assignment = reviewers
            .findByTenantIdAndParticipantIdAndReviewerTypeAndRound(
                actor.tenantId(), participant.getId(), ReviewerType.MANAGER, 1)
            .filter(r -> r.getStatus() != ReviewerAssignmentStatus.REVOKED)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REVIEWER_ASSIGNMENT_NOT_FOUND));
        if (actor.employeeId() == null || !actor.employeeId().equals(assignment.getReviewerEmployeeId())) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_FORBIDDEN);
        }
    }

    private EvaluationGoal requireGoal(UUID tenantId, UUID goalId) {
        return goals.findByIdAndTenantId(goalId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.KPI_ASSIGNMENT_NOT_FOUND));
    }

    private void requireStage(UUID tenantId, UUID cycleId, CycleStatus expected) {
        var cycle = cycles.findByIdAndTenantId(cycleId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND));
        if (cycle.getStatus() != expected) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED,
                Map.of("cycleId", cycleId, "required", expected.name(), "actual", cycle.getStatus().name()));
        }
    }

    private GoalResponse response(EvaluationGoal goal, BigDecimal weight, BigDecimal target, String unit) {
        return new GoalResponse(goal.getId(), goal.getCycleId(), goal.getEmployeeId(), goal.getKpiAssignmentId(),
            goal.getTitle(), goal.getDescription(), weight, target, unit, goal.getStatus(), goal.getDecisionComment(),
            goal.getApprovedAt(), goal.getApprovedBy(), goal.getCreatedAt(), goal.getUpdatedAt());
    }

    private GoalResponse hydrated(EvaluationGoal goal) {
        return kpis.listMyAssignments(goal.getCycleId(), goal.getEmployeeId()).stream()
            .filter(a -> a.id().equals(goal.getKpiAssignmentId())).findFirst()
            .map(a -> response(goal, a)).orElseGet(() -> response(goal, null, null, null));
    }

    private GoalResponse response(EvaluationGoal goal, MyKpiAssignmentResponse assignment) {
        return response(goal, assignment == null ? null : assignment.weight(),
            assignment == null ? null : assignment.target(), assignment == null ? null : assignment.unit());
    }
}

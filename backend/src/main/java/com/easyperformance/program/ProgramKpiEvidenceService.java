package com.easyperformance.program;

import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.kpi.entity.KpiActual;
import com.easyperformance.domain.kpi.entity.KpiAssignment;
import com.easyperformance.domain.kpi.entity.KpiNode;
import com.easyperformance.domain.kpi.entity.KpiTree;
import com.easyperformance.domain.kpi.repository.KpiActualRepository;
import com.easyperformance.domain.kpi.repository.KpiAssignmentRepository;
import com.easyperformance.domain.kpi.repository.KpiNodeRepository;
import com.easyperformance.domain.kpi.repository.KpiTreeRepository;
import com.easyperformance.domain.kpi.service.KpiActualSelector;
import com.easyperformance.domain.kpi.service.KpiScorePolicy;
import com.easyperformance.program.ProgramKpiDtos.*;
import com.easyperformance.program.ProgramTypes.GoalStatus;
import com.easyperformance.program.ProgramTypes.ParticipantStatus;
import com.easyperformance.program.ProgramTypes.ProgramEventType;
import com.easyperformance.program.ProgramTypes.ProgramStatus;
import com.easyperformance.program.ProgramTypes.ReviewerRole;
import com.easyperformance.program.ProgramTypes.SubmissionStatus;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProgramKpiEvidenceService {
    private final EvaluationProgramRepository programs;
    private final ProgramParticipantRepository participants;
    private final ProgramGoalRepository goals;
    private final ProgramReviewSubmissionRepository submissions;
    private final ProgramCalculationRepository calculations;
    private final ProgramKpiEvidenceRepository evidence;
    private final KpiAssignmentRepository assignments;
    private final KpiNodeRepository nodes;
    private final KpiTreeRepository trees;
    private final KpiActualRepository actuals;
    private final EvaluationCycleRepository cycles;
    private final ProgramAccess access;
    private final ProgramJson json;
    private final ProgramAuditService audit;
    private final Clock clock;

    @Autowired
    public ProgramKpiEvidenceService(EvaluationProgramRepository programs, ProgramParticipantRepository participants,
        ProgramGoalRepository goals, ProgramReviewSubmissionRepository submissions,
        ProgramCalculationRepository calculations, ProgramKpiEvidenceRepository evidence,
        KpiAssignmentRepository assignments, KpiNodeRepository nodes, KpiTreeRepository trees,
        KpiActualRepository actuals, EvaluationCycleRepository cycles, ProgramAccess access,
        ProgramJson json, ProgramAuditService audit) {
        this(programs, participants, goals, submissions, calculations, evidence, assignments, nodes, trees,
            actuals, cycles, access, json, audit, Clock.systemDefaultZone());
    }

    ProgramKpiEvidenceService(EvaluationProgramRepository programs, ProgramParticipantRepository participants,
        ProgramGoalRepository goals, ProgramReviewSubmissionRepository submissions,
        ProgramCalculationRepository calculations, ProgramKpiEvidenceRepository evidence,
        KpiAssignmentRepository assignments, KpiNodeRepository nodes, KpiTreeRepository trees,
        KpiActualRepository actuals, EvaluationCycleRepository cycles, ProgramAccess access,
        ProgramJson json, ProgramAuditService audit, Clock clock) {
        this.programs=programs; this.participants=participants; this.goals=goals; this.submissions=submissions;
        this.calculations=calculations; this.evidence=evidence; this.assignments=assignments; this.nodes=nodes;
        this.trees=trees; this.actuals=actuals; this.cycles=cycles; this.access=access; this.json=json;
        this.audit=audit; this.clock=clock;
    }

    @Transactional(readOnly = true)
    public Page<KpiCandidateResponse> candidates(Actor actor, UUID programId, UUID participantId,
        UUID cycleId, LocalDate actualCutoffDate, Pageable pageable) {
        access.requireOperator(actor);
        requirePage(pageable);
        Context context = context(actor, programId, participantId, null, cycleId, actualCutoffDate, false);
        Page<KpiAssignment> page = assignments.findCandidates(actor.tenantId(), cycleId,
            context.participant().getEmployeeId(), pageable);
        Map<UUID,KpiNode> nodeMap = nodes.findAllByTenantIdAndIdIn(actor.tenantId(),
            page.stream().map(KpiAssignment::getKpiNodeId).distinct().toList()).stream()
            .collect(Collectors.toMap(KpiNode::getId, Function.identity()));
        Map<UUID,KpiTree> treeMap = trees.findAllByTenantIdAndIdIn(actor.tenantId(),
            nodeMap.values().stream().map(KpiNode::getTreeId).distinct().toList()).stream()
            .collect(Collectors.toMap(KpiTree::getId, Function.identity()));
        Map<UUID,List<KpiActual>> actualMap = actuals.findAllByTenantIdAndKpiAssignmentIdIn(actor.tenantId(),
            page.stream().map(KpiAssignment::getId).toList()).stream()
            .collect(Collectors.groupingBy(KpiActual::getKpiAssignmentId));
        return page.map(a -> candidate(a, nodeMap.get(a.getKpiNodeId()), treeMap,
            context.cycle(), actualMap.getOrDefault(a.getId(), List.of()), actualCutoffDate));
    }

    @Transactional(readOnly = true)
    public KpiLinkPreviewResponse preview(Actor actor, UUID programId, UUID participantId, UUID goalId,
        KpiLinkPreviewRequest request) {
        access.requireOperator(actor);
        return prepare(actor, programId, participantId, goalId, request, false).preview();
    }

    @Transactional
    public KpiLinkApplyResponse apply(Actor actor, UUID programId, UUID participantId, UUID goalId,
        KpiLinkApplyRequest request) {
        access.requireOperator(actor);
        EvaluationProgram locked = programs.findLocked(programId, actor.tenantId())
            .orElseThrow(() -> new ApiException(ProgramErrorCode.PROGRAM_NOT_FOUND));
        context(actor, programId, participantId, goalId, null, null, false);
        Optional<ProgramKpiEvidence> replay = evidence.findByTenantIdAndGoalIdAndPreviewHash(
            actor.tenantId(), goalId, request.previewHash());
        if (replay.isPresent()) {
            ProgramKpiEvidence stored = replay.get();
            if (stored.getProgramId().equals(programId) && stored.getParticipantId().equals(participantId)
                && stored.getCycleId().equals(request.cycleId())
                && stored.getKpiAssignmentId().equals(request.kpiAssignmentId())
                && stored.getActualCutoffDate().equals(request.actualCutoffDate())) return response(stored, activeId(actor.tenantId(), goalId));
            throw new ApiException(ProgramErrorCode.PROGRAM_KPI_LINK_CONFLICT);
        }

        if (locked.getStatus() != ProgramStatus.OPEN) throw new ApiException(ProgramErrorCode.PROGRAM_LOCKED);
        Prepared prepared = prepare(actor, programId, participantId, goalId,
            new KpiLinkPreviewRequest(request.cycleId(), request.actualCutoffDate(), request.kpiAssignmentId()), true);
        if (!prepared.preview().previewHash().equals(request.previewHash()))
            throw new ApiException(ProgramErrorCode.PROGRAM_KPI_LINK_STALE);
        if (prepared.preview().row().status() != KpiLinkStatus.READY)
            throw new ApiException(ProgramErrorCode.PROGRAM_INVALID, Map.of("reason", prepared.preview().row().reasonCode()));

        ProgramKpiEvidence previous = prepared.current();
        ProgramKpiEvidence row = new ProgramKpiEvidence();
        row.setTenantId(actor.tenantId()); row.setProgramId(programId); row.setParticipantId(participantId);
        row.setGoalId(goalId); row.setCycleId(request.cycleId()); row.setKpiAssignmentId(request.kpiAssignmentId());
        row.setRevision(previous == null ? 1 : previous.getRevision() + 1);
        row.setSupersedesEvidenceId(previous == null ? null : previous.getId());
        row.setActualCutoffDate(request.actualCutoffDate()); row.setPreviewHash(request.previewHash());
        row.setReason(request.reason().trim()); row.setAppliedByEmployeeId(actor.employeeId());
        row.setCapturedAt(prepared.preview().capturedAt()); row.setEvidenceJson(json.write(prepared.preview().row()));
        row.setSourceSnapshotJson(json.write(prepared.sourceSnapshot()));
        try { row = evidence.saveAndFlush(row); }
        catch (DataIntegrityViolationException exception) {
            throw new ApiException(ProgramErrorCode.PROGRAM_KPI_LINK_CONFLICT, Map.of("goalId", goalId), exception);
        }
        audit.record(actor, programId, participantId, ProgramEventType.KPI_LINK_APPLIED, request.reason(),
            Map.of("evidenceId", row.getId(), "goalId", goalId, "cycleId", request.cycleId(),
                "actualCutoffDate", request.actualCutoffDate(), "previewHash", request.previewHash(),
                "revision", row.getRevision()));
        return response(row, row.getId());
    }

    @Transactional(readOnly = true)
    public Page<KpiLinkApplyResponse> history(Actor actor, UUID programId, UUID participantId,
        UUID goalId, Pageable pageable) {
        requirePage(pageable);
        context(actor, programId, participantId, goalId, null, null, false);
        UUID active = activeId(actor.tenantId(), goalId);
        return evidence.findAllByTenantIdAndGoalIdOrderByRevisionDesc(actor.tenantId(), goalId, pageable)
            .map(row -> response(row, active));
    }

    private Prepared prepare(Actor actor, UUID programId, UUID participantId, UUID goalId,
        KpiLinkPreviewRequest request, boolean lockedSources) {
        Context context = context(actor, programId, participantId, goalId, request.cycleId(),
            request.actualCutoffDate(), true);
        if (lockedSources) {
            ProgramGoal lockedGoal = goals.findLocked(goalId, actor.tenantId())
                .orElseThrow(() -> new ApiException(ProgramErrorCode.GOAL_NOT_FOUND));
            if (!lockedGoal.getProgramId().equals(programId) || !lockedGoal.getParticipantId().equals(participantId))
                throw new ApiException(ProgramErrorCode.GOAL_NOT_FOUND);
            context = new Context(context.program(), context.participant(), lockedGoal, context.cycle());
        }
        requireMutable(actor.tenantId(), context.participant());
        KpiAssignment assignment = (lockedSources ? assignments.findLocked(request.kpiAssignmentId(), actor.tenantId())
            : assignments.findByIdAndTenantId(request.kpiAssignmentId(), actor.tenantId()))
            .orElseThrow(() -> new ApiException(ProgramErrorCode.PROGRAM_KPI_LINK_NOT_FOUND));
        if (!assignment.getEmployeeId().equals(context.participant().getEmployeeId()))
            throw new ApiException(ProgramErrorCode.PROGRAM_INVALID, Map.of("reason", "KPI_EMPLOYEE_MISMATCH"));
        KpiNode node = (lockedSources ? nodes.findLocked(assignment.getKpiNodeId(), actor.tenantId())
            : nodes.findByIdAndTenantId(assignment.getKpiNodeId(), actor.tenantId()))
            .orElseThrow(() -> new ApiException(ProgramErrorCode.PROGRAM_KPI_LINK_NOT_FOUND));
        KpiTree tree = trees.findByIdAndTenantId(node.getTreeId(), actor.tenantId())
            .orElseThrow(() -> new ApiException(ProgramErrorCode.PROGRAM_KPI_LINK_NOT_FOUND));
        if (!tree.getCycleId().equals(request.cycleId()))
            throw new ApiException(ProgramErrorCode.PROGRAM_INVALID, Map.of("reason", "KPI_CYCLE_MISMATCH"));
        List<KpiActual> history = actuals.findAllByTenantIdAndKpiAssignmentIdOrderByAsOfDateDescCreatedAtDesc(
            actor.tenantId(), assignment.getId());
        KpiLinkPreviewRow result = previewRow(context.goal(), assignment, node, tree, context.cycle(), history,
            request.actualCutoffDate());
        ProgramKpiEvidence current = evidence.findFirstByTenantIdAndGoalIdOrderByRevisionDesc(actor.tenantId(), goalId).orElse(null);
        KpiSourceSnapshot source = sourceSnapshot(context, assignment, node, result.actualId(), result.actualCreatedAt());
        String hash = hash(new HashMaterial(actor.tenantId(), programId, participantId, goalId,
            request.cycleId(), request.actualCutoffDate(), current == null ? null : current.getId(), result, source));
        Instant capturedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        return new Prepared(new KpiLinkPreviewResponse(programId, participantId, request.cycleId(),
            context.program().getAsOfDate(), request.actualCutoffDate(), context.program().getDefinitionRevision(),
            context.program().getRowVersion(), context.participant().getRowVersion(), hash, capturedAt, result), source, current);
    }

    private Context context(Actor actor, UUID programId, UUID participantId, UUID goalId, UUID cycleId,
        LocalDate cutoff, boolean requireOpen) {
        EvaluationProgram program = programs.findByIdAndTenantId(programId, actor.tenantId())
            .orElseThrow(() -> new ApiException(ProgramErrorCode.PROGRAM_NOT_FOUND));
        if (requireOpen && program.getStatus() != ProgramStatus.OPEN) throw new ApiException(ProgramErrorCode.PROGRAM_LOCKED);
        ProgramParticipant participant = access.participant(actor, participantId);
        if (!participant.getProgramId().equals(programId)) throw new ApiException(ProgramErrorCode.PARTICIPANT_NOT_FOUND);
        ProgramGoal goal = null;
        if (goalId != null) {
            goal = goals.findByIdAndTenantId(goalId, actor.tenantId()).orElseThrow(() -> new ApiException(ProgramErrorCode.GOAL_NOT_FOUND));
            if (!goal.getProgramId().equals(programId) || !goal.getParticipantId().equals(participantId))
                throw new ApiException(ProgramErrorCode.GOAL_NOT_FOUND);
        }
        EvaluationCycle cycle = null;
        if (cycleId != null) cycle = cycles.findByIdAndTenantId(cycleId, actor.tenantId())
            .orElseThrow(() -> new ApiException(ProgramErrorCode.PROGRAM_INVALID, Map.of("reason", "KPI_CYCLE_NOT_FOUND")));
        if (cycle != null && cutoff != null) validateCutoff(program, cycle, cutoff);
        return new Context(program, participant, goal, cycle);
    }

    private void requireMutable(UUID tenantId, ProgramParticipant participant) {
        if (participant.getStatus() != ParticipantStatus.ACTIVE)
            throw new ApiException(ProgramErrorCode.PROGRAM_INVALID, Map.of("reason", "PARTICIPANT_NOT_ACTIVE"));
        if (calculations.existsByTenantIdAndParticipantId(tenantId, participant.getId())
            || submissions.existsByTenantIdAndParticipantIdAndRoleAndStatus(tenantId, participant.getId(),
                ReviewerRole.REVIEWER, SubmissionStatus.COMPLETED))
            throw new ApiException(ProgramErrorCode.PROGRAM_LOCKED, Map.of("reason", "KPI_EVIDENCE_FROZEN"));
    }

    private KpiLinkPreviewRow previewRow(ProgramGoal goal, KpiAssignment assignment, KpiNode node,
        KpiTree tree, EvaluationCycle cycle, List<KpiActual> history, LocalDate cutoff) {
        BigDecimal target = assignment.getTargetOverride() == null ? node.getTarget() : assignment.getTargetOverride();
        BigDecimal weight = assignment.getWeight() == null ? node.getWeight() : assignment.getWeight();
        KpiActual actual = KpiActualSelector.latestCurrentLeaf(history, cutoff);
        KpiLinkStatus status; String reason;
        if (goal.getStatus() != GoalStatus.AGREED && goal.getStatus() != GoalStatus.SELF_REPORTED) {
            status=KpiLinkStatus.BLOCKED; reason="GOAL_NOT_FROZEN";
        } else if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            status=KpiLinkStatus.BLOCKED; reason="TARGET_NOT_POSITIVE";
        } else if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            status=KpiLinkStatus.BLOCKED; reason="WEIGHT_NOT_POSITIVE";
        } else if (actual == null) {
            status=KpiLinkStatus.SOURCE_MISSING; reason="ACTUAL_MISSING";
        } else { status=KpiLinkStatus.READY; reason=null; }
        BigDecimal rate = actual == null ? null : KpiScorePolicy.achievementRate(actual.getActualValue(), target);
        return new KpiLinkPreviewRow(goal.getId(),goal.getRevision(),goal.getRowVersion(),goal.getTitle(),
            goal.getWeightPercent(),assignment.getId(),assignment.getUpdatedAt(),node.getId(),node.getUpdatedAt(),
            node.getLabel(),tree.getId(),tree.getName(),cycle.getId(),assignment.getEmployeeId(),weight,target,
            node.getUnit(),node.getSource(),actual==null?null:actual.getId(),actual==null?null:actual.getAsOfDate(),
            actual==null?null:actual.getActualValue(),actual==null?null:actual.getSource(),actual==null?null:actual.getCreatedAt(),
            actual==null?null:actual.getSupersedesId(),rate,KpiScorePolicy.autoScore(rate),KpiScorePolicy.VERSION,
            KpiScorePolicy.FORMULA,status,reason);
    }

    private KpiCandidateResponse candidate(KpiAssignment assignment, KpiNode node, Map<UUID,KpiTree> treeMap,
        EvaluationCycle cycle, List<KpiActual> history, LocalDate cutoff) {
        if (node == null) throw new ApiException(ProgramErrorCode.PROGRAM_INVALID, Map.of("reason", "KPI_NODE_NOT_FOUND"));
        KpiTree tree = treeMap.get(node.getTreeId());
        if (tree == null) throw new ApiException(ProgramErrorCode.PROGRAM_INVALID, Map.of("reason", "KPI_TREE_NOT_FOUND"));
        BigDecimal target=assignment.getTargetOverride()==null?node.getTarget():assignment.getTargetOverride();
        BigDecimal weight=assignment.getWeight()==null?node.getWeight():assignment.getWeight();
        KpiActual actual=KpiActualSelector.latestCurrentLeaf(history,cutoff);
        KpiLinkStatus status; String reason;
        if(target==null||target.compareTo(BigDecimal.ZERO)<=0){status=KpiLinkStatus.BLOCKED;reason="TARGET_NOT_POSITIVE";}
        else if(weight==null||weight.compareTo(BigDecimal.ZERO)<=0){status=KpiLinkStatus.BLOCKED;reason="WEIGHT_NOT_POSITIVE";}
        else if(actual==null){status=KpiLinkStatus.SOURCE_MISSING;reason="ACTUAL_MISSING";}
        else{status=KpiLinkStatus.READY;reason=null;}
        BigDecimal rate=actual==null?null:KpiScorePolicy.achievementRate(actual.getActualValue(),target);
        return new KpiCandidateResponse(assignment.getId(),node.getId(),node.getLabel(),tree.getId(),tree.getName(),
            cycle.getId(),cycle.getName(),assignment.getEmployeeId(),weight,target,node.getUnit(),node.getSource(),
            actual==null?null:actual.getId(),actual==null?null:actual.getAsOfDate(),actual==null?null:actual.getActualValue(),
            actual==null?null:actual.getSource(),rate,KpiScorePolicy.autoScore(rate),status,reason);
    }

    private KpiSourceSnapshot sourceSnapshot(Context context,KpiAssignment assignment,KpiNode node,UUID actualId,Instant actualCreatedAt){
        return new KpiSourceSnapshot(context.program().getDefinitionRevision(),context.program().getRowVersion(),
            context.participant().getRowVersion(),context.goal().getRevision(),context.goal().getRowVersion(),
            assignment.getUpdatedAt(),node.getUpdatedAt(),actualId,actualCreatedAt);
    }

    private void validateCutoff(EvaluationProgram program, EvaluationCycle cycle, LocalDate cutoff) {
        LocalDate earliest=program.getStartsOn().isAfter(cycle.getPeriodStart())?program.getStartsOn():cycle.getPeriodStart();
        LocalDate latest=program.getEndsOn().isBefore(cycle.getPeriodEnd())?program.getEndsOn():cycle.getPeriodEnd();
        LocalDate today=LocalDate.now(clock); if(today.isBefore(latest))latest=today;
        if(cutoff.isBefore(earliest)||cutoff.isAfter(latest))throw new ApiException(ProgramErrorCode.PROGRAM_INVALID,
            Map.of("reason","KPI_CUTOFF_OUT_OF_RANGE","earliest",earliest,"latest",latest));
    }

    private UUID activeId(UUID tenantId,UUID goalId){return evidence.findFirstByTenantIdAndGoalIdOrderByRevisionDesc(tenantId,goalId).map(ProgramKpiEvidence::getId).orElse(null);}
    private KpiLinkApplyResponse response(ProgramKpiEvidence row,UUID active){return new KpiLinkApplyResponse(row.getId(),row.getProgramId(),row.getParticipantId(),row.getGoalId(),row.getCycleId(),row.getRevision(),row.getSupersedesEvidenceId(),row.getActualCutoffDate(),row.getPreviewHash(),row.getCapturedAt(),row.getAppliedByEmployeeId(),row.getId().equals(active),json.kpiLinkPreviewRow(row.getEvidenceJson()),json.kpiSourceSnapshot(row.getSourceSnapshotJson()),row.getReason());}
    private String hash(Object value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(json.write(value).getBytes(StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(bytes);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void requirePage(Pageable pageable){if(pageable.getPageSize()<1||pageable.getPageSize()>100)throw new ApiException(ProgramErrorCode.PROGRAM_INVALID,Map.of("reason","PAGE_SIZE_INVALID"));}

    private record Context(EvaluationProgram program,ProgramParticipant participant,ProgramGoal goal,EvaluationCycle cycle){}
    private record Prepared(KpiLinkPreviewResponse preview,KpiSourceSnapshot sourceSnapshot,ProgramKpiEvidence current){}
    private record HashMaterial(UUID tenantId,UUID programId,UUID participantId,UUID goalId,UUID cycleId,
        LocalDate actualCutoffDate,UUID currentEvidenceId,KpiLinkPreviewRow row,KpiSourceSnapshot sourceSnapshot){}
}

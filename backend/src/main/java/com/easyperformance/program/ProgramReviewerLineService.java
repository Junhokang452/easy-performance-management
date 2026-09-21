package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.ProgramConfiguration;
import com.easyperformance.program.ProgramDtos.ReviewerWeightPlanInput;
import com.easyperformance.program.ProgramDtos.RevieweeGroupInput;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLineApplyRequest;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLineApplyResponse;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLineApplyRow;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLineIssue;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLinePreviewRequest;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLinePreviewResponse;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLinePreviewRow;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLinePreviewSummary;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLineProposal;
import com.easyperformance.program.ProgramTypes.AssignmentStatus;
import com.easyperformance.program.ProgramTypes.ParticipantStatus;
import com.easyperformance.program.ProgramTypes.ProgramEventType;
import com.easyperformance.program.ProgramTypes.ProgramStageStatus;
import com.easyperformance.program.ProgramTypes.ProgramStatus;
import com.easyperformance.program.ProgramTypes.ReviewerAssignmentOrigin;
import com.easyperformance.program.ProgramTypes.ReviewerLineApplyStatus;
import com.easyperformance.program.ProgramTypes.ReviewerLinePreviewStatus;
import com.easyperformance.program.ProgramTypes.ReviewerRole;
import com.easyperformance.readmodel.entity.RmAssignment;
import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.repository.RmAssignmentRepository;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.UuidV7;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** SSOT for fail-closed direct-manager reviewer-line preview and explicit apply. */
@Service
public class ProgramReviewerLineService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final Set<ReviewerRole> ALLOWED_ROLES = EnumSet.of(
        ReviewerRole.AGREEMENT_REVIEWER, ReviewerRole.CHECKER, ReviewerRole.REVIEWER,
        ReviewerRole.FINAL_FEEDBACK);

    private final EvaluationProgramRepository programs;
    private final ProgramParticipantRepository participants;
    private final ProgramReviewerAssignmentRepository reviewers;
    private final ProgramReviewSubmissionRepository submissions;
    private final ProgramReviewerLineRunRepository runs;
    private final RmAssignmentRepository assignments;
    private final RmEmployeeRepository employees;
    private final ProgramAccess access;
    private final ProgramJson json;
    private final ProgramAuditService audit;

    public ProgramReviewerLineService(EvaluationProgramRepository programs,
                                      ProgramParticipantRepository participants,
                                      ProgramReviewerAssignmentRepository reviewers,
                                      ProgramReviewSubmissionRepository submissions,
                                      ProgramReviewerLineRunRepository runs,
                                      RmAssignmentRepository assignments,
                                      RmEmployeeRepository employees,
                                      ProgramAccess access,
                                      ProgramJson json,
                                      ProgramAuditService audit) {
        this.programs = programs;
        this.participants = participants;
        this.reviewers = reviewers;
        this.submissions = submissions;
        this.runs = runs;
        this.assignments = assignments;
        this.employees = employees;
        this.access = access;
        this.json = json;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ReviewerLinePreviewResponse preview(Actor actor, UUID programId,
                                               ReviewerLinePreviewRequest request) {
        access.requireOperator(actor);
        validateRequest(request.participantIds(), request.roles());
        EvaluationProgram program = programs.findByIdAndTenantId(programId, actor.tenantId())
            .orElseThrow(() -> new ApiException(ProgramErrorCode.PROGRAM_NOT_FOUND));
        requireMutable(program);
        return previewInternal(actor, program, request.participantIds(), request.roles());
    }

    @Transactional
    public ReviewerLineApplyResponse apply(Actor actor, UUID programId, ReviewerLineApplyRequest request) {
        access.requireOperator(actor);
        validateRequest(request.participantIds(), request.roles());
        EvaluationProgram program = access.lockedProgram(actor, programId);
        String fingerprint = requestFingerprint(request.participantIds(), request.roles());
        ProgramReviewerLineRun prior = runs.findByTenantIdAndProgramIdAndPreviewHash(
            actor.tenantId(), programId, request.previewHash()).orElse(null);
        if (prior != null) {
            if (!prior.getRequestFingerprint().equals(fingerprint)) {
                throw stale("REQUEST_FINGERPRINT_MISMATCH");
            }
            return json.reviewerLineApplyResponse(prior.getResponseJson());
        }
        requireMutable(program);
        ReviewerLinePreviewResponse fresh = previewInternal(
            actor, program, request.participantIds(), request.roles());
        if (!MessageDigest.isEqual(fresh.previewHash().getBytes(StandardCharsets.UTF_8),
                request.previewHash().getBytes(StandardCharsets.UTF_8))) {
            throw stale("PREVIEW_HASH_MISMATCH");
        }

        UUID runId = UuidV7.generate();
        ProgramReviewerLineRun run = new ProgramReviewerLineRun();
        run.setId(runId);
        run.setTenantId(actor.tenantId());
        run.setProgramId(programId);
        run.setPreviewHash(request.previewHash());
        run.setRequestFingerprint(fingerprint);
        run.setResponseJson("{}");
        runs.saveAndFlush(run);
        List<ReviewerLineApplyRow> resultRows = new ArrayList<>();
        int applied = 0;
        for (ReviewerLinePreviewRow row : fresh.rows()) {
            if (row.status() != ReviewerLinePreviewStatus.READY) {
                resultRows.add(new ReviewerLineApplyRow(row.participantId(),
                    ReviewerLineApplyStatus.valueOf(row.status().name()), List.of(), row.issues()));
                continue;
            }
            List<UUID> assignmentIds = new ArrayList<>();
            for (ReviewerLineProposal proposal : row.proposals()) {
                ProgramReviewerAssignment assignment = new ProgramReviewerAssignment();
                assignment.setTenantId(actor.tenantId());
                assignment.setProgramId(programId);
                assignment.setParticipantId(row.participantId());
                assignment.setReviewerEmployeeId(row.proposedReviewerEmployeeId());
                assignment.setRole(proposal.role());
                assignment.setRound(proposal.round());
                assignment.setWeightPercent(proposal.weightPercent());
                assignment.setAssignmentOrigin(ReviewerAssignmentOrigin.HCM_MANAGER);
                assignment.setSourceAssignmentId(row.sourceAssignmentId());
                assignment.setSourceVersion(row.sourceVersion());
                assignment.setSourceAsOfDate(program.getAsOfDate());
                assignment.setAutomationRunId(runId);
                assignmentIds.add(reviewers.save(assignment).getId());
            }
            applied++;
            resultRows.add(new ReviewerLineApplyRow(row.participantId(),
                ReviewerLineApplyStatus.APPLIED, List.copyOf(assignmentIds), List.of()));
        }
        ReviewerLineApplyResponse response = new ReviewerLineApplyResponse(programId,
            program.getAsOfDate(), runId, applied, resultRows.size() - applied, List.copyOf(resultRows));
        run.setResponseJson(json.write(response));
        runs.save(run);
        audit.record(actor, programId, null, ProgramEventType.REVIEWER_LINE_APPLIED,
            request.reason().trim(), Map.of("automationRunId", runId, "previewHash", request.previewHash(),
                "applied", applied, "skipped", resultRows.size() - applied));
        return response;
    }

    private ReviewerLinePreviewResponse previewInternal(Actor actor, EvaluationProgram program,
                                                        List<UUID> requestedIds, Set<ReviewerRole> roles) {
        List<UUID> ids = requestedIds.stream().sorted().toList();
        List<ReviewerRole> sortedRoles = roles.stream().sorted().toList();
        ProgramConfiguration configuration = json.configuration(program.getDefinitionJson());
        List<ReviewerLinePreviewRow> rows = ids.stream()
            .map(id -> participant(actor, program.getId(), id))
            .map(participant -> previewRow(actor.tenantId(), program, configuration, participant, sortedRoles))
            .toList();
        String hash = previewHash(actor.tenantId(), program, sortedRoles, rows);
        int ready = count(rows, ReviewerLinePreviewStatus.READY);
        int sourceMissing = count(rows, ReviewerLinePreviewStatus.SOURCE_MISSING);
        int blocked = count(rows, ReviewerLinePreviewStatus.BLOCKED);
        int skippedExisting = count(rows, ReviewerLinePreviewStatus.SKIPPED_EXISTING);
        return new ReviewerLinePreviewResponse(program.getId(), program.getAsOfDate(),
            program.getDefinitionRevision(), hash, Instant.now(),
            new ReviewerLinePreviewSummary(rows.size(), ready, sourceMissing, blocked, skippedExisting), rows);
    }

    private ReviewerLinePreviewRow previewRow(UUID tenantId, EvaluationProgram program,
                                              ProgramConfiguration configuration,
                                              ProgramParticipant participant,
                                              List<ReviewerRole> roles) {
        List<ProgramReviewerAssignment> active = reviewers
            .findAllByTenantIdAndParticipantIdAndStatusNotOrderByRoleAscRoundAsc(
                tenantId, participant.getId(), AssignmentStatus.REVOKED);
        if (!active.isEmpty()) {
            return row(participant, null, null, null, active.size(), List.of(),
                ReviewerLinePreviewStatus.SKIPPED_EXISTING,
                List.of(issue("EXISTING_REVIEWER_ASSIGNMENT", "participant already has an active reviewer assignment")));
        }

        List<ReviewerLineIssue> issues = new ArrayList<>();
        if (participant.getStatus() != ParticipantStatus.ACTIVE) {
            issues.add(issue("PARTICIPANT_INACTIVE", "participant is not active"));
        }
        if ((participant.getStageStatus() != ProgramStageStatus.NOT_STARTED
                && participant.getStageStatus() != ProgramStageStatus.READY)
                || submissions.existsByTenantIdAndParticipantId(tenantId, participant.getId())) {
            issues.add(issue("WORK_ALREADY_STARTED", "participant evaluation work already started"));
        }
        if (participant.getAssignmentId() == null) {
            return row(participant, null, null, null, 0, List.of(),
                ReviewerLinePreviewStatus.SOURCE_MISSING,
                append(issues, issue("NO_PARTICIPANT_ASSIGNMENT", "participant has no frozen assignment")));
        }

        List<RmAssignment> effective = assignments.findAllByTenantIdAndEmployeeId(
                tenantId, participant.getEmployeeId()).stream()
            .filter(a -> effectiveAt(a, program.getAsOfDate()))
            .toList();
        if (effective.isEmpty()) {
            return row(participant, null, null, null, 0, List.of(),
                ReviewerLinePreviewStatus.SOURCE_MISSING,
                append(issues, issue("NO_EFFECTIVE_ASSIGNMENT", "no assignment is effective on program as-of date")));
        }
        if (effective.size() != 1) {
            return row(participant, null, null, null, 0, List.of(),
                ReviewerLinePreviewStatus.BLOCKED,
                append(issues, issue("AMBIGUOUS_EFFECTIVE_ASSIGNMENT", "multiple assignments are effective on program as-of date")));
        }
        RmAssignment source = effective.getFirst();
        if (!source.getId().equals(participant.getAssignmentId())) {
            issues.add(issue("PARTICIPANT_ASSIGNMENT_STALE", "participant frozen assignment differs from effective source"));
        }
        if (source.getDeleted() == null || !"HCM".equals(source.getSourceSystem())) {
            issues.add(issue("SOURCE_LEGACY_UNSUPPORTED", "source row lacks authenticated manager/delete capability"));
        } else if (source.getDeleted()) {
            issues.add(issue("SOURCE_TOMBSTONE_OR_STALE", "source assignment is deleted"));
        }
        UUID managerId = source.getManagerEmployeeId();
        RmEmployee manager = null;
        if (managerId == null) {
            issues.add(issue("MANAGER_MISSING", "direct manager is missing"));
        } else if (managerId.equals(participant.getEmployeeId())) {
            issues.add(issue("SELF_MANAGER", "participant cannot review self as direct manager"));
        } else {
            manager = employees.findByIdAndTenantId(managerId, tenantId).orElse(null);
            if (manager == null || !"ACTIVE".equalsIgnoreCase(manager.getStatus())) {
                issues.add(issue("MANAGER_NOT_FOUND_OR_INACTIVE", "direct manager is absent or inactive"));
            } else {
                String chainIssue = managerChainIssue(tenantId, participant.getEmployeeId(), managerId,
                    program.getAsOfDate());
                if (chainIssue != null) {
                    issues.add(issue(chainIssue, "manager relationship is ambiguous or contains a cycle"));
                }
            }
        }
        List<ReviewerLineProposal> proposals = proposals(configuration, participant, roles, issues);
        ReviewerLinePreviewStatus status = issues.isEmpty()
            ? ReviewerLinePreviewStatus.READY : ReviewerLinePreviewStatus.BLOCKED;
        return row(participant, source, managerId, manager == null ? null : manager.getName(),
            0, proposals, status, List.copyOf(issues));
    }

    private List<ReviewerLineProposal> proposals(ProgramConfiguration configuration,
                                                 ProgramParticipant participant,
                                                 List<ReviewerRole> roles,
                                                 List<ReviewerLineIssue> issues) {
        if (roles.contains(ReviewerRole.REVIEWER) && !exactOneReviewerPlan(configuration, participant)) {
            issues.add(issue("REVIEWER_WEIGHT_PLAN_INCOMPLETE",
                "REVIEWER automation requires exactly one round with 100 percent weight"));
        }
        return roles.stream().map(role -> role == ReviewerRole.REVIEWER
            ? new ReviewerLineProposal(role, 1, ONE_HUNDRED)
            : new ReviewerLineProposal(role, 0, BigDecimal.ZERO)).toList();
    }

    private boolean exactOneReviewerPlan(ProgramConfiguration configuration, ProgramParticipant participant) {
        RevieweeGroupInput group = configuration.groups().stream()
            .filter(candidate -> Objects.equals(candidate.id(), participant.getGroupId())).findFirst().orElse(null);
        if (group == null) return false;
        ReviewerWeightPlanInput plan = group.reviewerWeightPlans().stream()
            .filter(candidate -> candidate.actualReviewerCount() == 1).findFirst().orElse(null);
        return plan != null && plan.reviewerWeights().size() == 1
            && plan.reviewerWeights().containsKey(1)
            && ONE_HUNDRED.compareTo(plan.reviewerWeights().get(1)) == 0;
    }

    private String managerChainIssue(UUID tenantId, UUID participantEmployeeId, UUID managerEmployeeId,
                                     LocalDate asOfDate) {
        Set<UUID> seen = new HashSet<>();
        seen.add(participantEmployeeId);
        UUID current = managerEmployeeId;
        for (int depth = 0; depth < 100 && current != null; depth++) {
            if (!seen.add(current)) return "MANAGER_CYCLE";
            List<RmAssignment> rows = assignments.findAllByTenantIdAndEmployeeId(tenantId, current).stream()
                .filter(a -> effectiveAt(a, asOfDate))
                .toList();
            if (rows.size() > 1) return "MANAGER_RELATION_AMBIGUOUS";
            if (rows.isEmpty()) return null;
            RmAssignment row = rows.getFirst();
            if (!Boolean.FALSE.equals(row.getDeleted()) || !"HCM".equals(row.getSourceSystem())) {
                return "MANAGER_CHAIN_UNVERIFIED";
            }
            current = row.getManagerEmployeeId();
        }
        return current == null ? null : "MANAGER_CYCLE";
    }

    private ReviewerLinePreviewRow row(ProgramParticipant participant, RmAssignment source,
                                       UUID managerId, String managerName, int currentReviewerCount,
                                       List<ReviewerLineProposal> proposals,
                                       ReviewerLinePreviewStatus status, List<ReviewerLineIssue> issues) {
        return new ReviewerLinePreviewRow(participant.getId(), participant.getEmployeeId(),
            participant.getAssignmentId(), participant.getRowVersion(), source == null ? null : source.getId(),
            source == null ? null : source.getSourceVersion(), source == null ? null : source.getSourceSystem(),
            source == null ? null : source.getEffectiveFrom(), source == null ? null : source.getEffectiveTo(),
            source == null ? null : source.getDeleted(), managerId, managerName, currentReviewerCount,
            proposals, status, issues);
    }

    private ProgramParticipant participant(Actor actor, UUID programId, UUID participantId) {
        ProgramParticipant participant = participants.findByIdAndTenantId(participantId, actor.tenantId())
            .orElseThrow(() -> new ApiException(ProgramErrorCode.PARTICIPANT_NOT_FOUND));
        if (!participant.getProgramId().equals(programId)) {
            throw new ApiException(ProgramErrorCode.PARTICIPANT_NOT_FOUND);
        }
        return participant;
    }

    private void validateRequest(List<UUID> participantIds, Set<ReviewerRole> roles) {
        if (participantIds == null || participantIds.isEmpty() || participantIds.size() > 100
                || participantIds.stream().anyMatch(Objects::isNull)
                || new LinkedHashSet<>(participantIds).size() != participantIds.size()) {
            throw new ApiException(ProgramErrorCode.PROGRAM_INVALID,
                Map.of("reason", "PARTICIPANT_IDS_MUST_BE_UNIQUE_1_TO_100"));
        }
        if (roles == null || roles.isEmpty() || roles.stream().anyMatch(Objects::isNull)
                || !ALLOWED_ROLES.containsAll(roles)) {
            throw new ApiException(ProgramErrorCode.PROGRAM_INVALID,
                Map.of("reason", "REVIEWER_LINE_ROLE_UNSUPPORTED", "allowedRoles", ALLOWED_ROLES));
        }
    }

    private void requireMutable(EvaluationProgram program) {
        if (program.getStatus() == ProgramStatus.FINALIZED || program.getStatus() == ProgramStatus.CANCELLED) {
            throw new ApiException(ProgramErrorCode.PROGRAM_LOCKED);
        }
    }

    private String previewHash(UUID tenantId, EvaluationProgram program, List<ReviewerRole> roles,
                               List<ReviewerLinePreviewRow> rows) {
        StringBuilder value = new StringBuilder().append(tenantId).append('|').append(program.getId())
            .append('|').append(program.getAsOfDate()).append('|').append(program.getDefinitionRevision())
            .append('|').append(roles).append('|');
        for (ReviewerLinePreviewRow row : rows) {
            value.append(row.participantId()).append(':').append(row.participantRowVersion()).append(':')
                .append(row.sourceAssignmentId()).append(':').append(row.sourceVersion()).append(':')
                .append(row.sourceDeleted()).append(':').append(row.proposedReviewerEmployeeId()).append(':')
                .append(row.currentReviewerCount()).append(':').append(row.status()).append(':')
                .append(row.proposals()).append(':')
                .append(row.issues().stream().map(ReviewerLineIssue::code).toList()).append(';');
        }
        return sha256(value.toString());
    }

    private String requestFingerprint(List<UUID> participantIds, Set<ReviewerRole> roles) {
        return sha256(participantIds.stream().sorted().toList() + "|" + roles.stream().sorted().toList());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static boolean effectiveAt(RmAssignment assignment, LocalDate date) {
        return assignment.getEffectiveFrom() != null
            && (assignment.getEffectiveTo() == null || !assignment.getEffectiveTo().isBefore(assignment.getEffectiveFrom()))
            && !assignment.getEffectiveFrom().isAfter(date)
            && (assignment.getEffectiveTo() == null || !assignment.getEffectiveTo().isBefore(date));
    }

    private static int count(List<ReviewerLinePreviewRow> rows, ReviewerLinePreviewStatus status) {
        return (int) rows.stream().filter(row -> row.status() == status).count();
    }

    private static ReviewerLineIssue issue(String code, String message) {
        return new ReviewerLineIssue(code, message);
    }

    private static List<ReviewerLineIssue> append(List<ReviewerLineIssue> existing, ReviewerLineIssue issue) {
        List<ReviewerLineIssue> result = new ArrayList<>(existing);
        result.add(issue);
        return List.copyOf(result);
    }

    private static ApiException stale(String reason) {
        return new ApiException(ProgramErrorCode.REVIEWER_LINE_STALE, Map.of("reason", reason));
    }
}

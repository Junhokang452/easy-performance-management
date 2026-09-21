package com.easyperformance.workflow;

import com.easyware.platform.UuidV7;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.IntermediateManagerRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.IntermediateReviewResponse;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.IntermediateReviewUpsertRequest;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class IntermediateReviewService {
    private final EvaluationParticipantRepository participants;
    private final EvaluationReviewerAssignmentRepository reviewers;
    private final IntermediatePerformanceReviewRepository repository;
    private final EvaluationCycleRepository cycles;

    public IntermediateReviewService(EvaluationParticipantRepository participants,
                                     EvaluationReviewerAssignmentRepository reviewers,
                                     IntermediatePerformanceReviewRepository repository,
                                     EvaluationCycleRepository cycles) {
        this.participants = participants;
        this.reviewers = reviewers;
        this.repository = repository;
        this.cycles = cycles;
    }

    @Transactional
    public IntermediateReviewResponse saveDraft(ActorAccess.Actor actor, UUID cycleId,
                                                IntermediateReviewUpsertRequest request) {
        return saveEmployee(actor, cycleId, request, false);
    }

    @Transactional
    public IntermediateReviewResponse submit(ActorAccess.Actor actor, UUID cycleId,
                                             IntermediateReviewUpsertRequest request) {
        return saveEmployee(actor, cycleId, request, true);
    }

    private IntermediateReviewResponse saveEmployee(ActorAccess.Actor actor, UUID cycleId,
                                                    IntermediateReviewUpsertRequest request, boolean submit) {
        requireMidReview(actor.tenantId(), cycleId);
        EvaluationParticipant participant = participants
            .findByTenantIdAndCycleIdAndEmployeeId(actor.tenantId(), cycleId, actor.employeeId())
            .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.PARTICIPANT_NOT_FOUND));
        IntermediatePerformanceReview review = repository
            .findByTenantIdAndParticipantId(actor.tenantId(), participant.getId())
            .orElseGet(IntermediatePerformanceReview::new);
        if (review.getId() == null) {
            review.setId(UuidV7.generate());
            review.setTenantId(actor.tenantId());
            review.setCycleId(cycleId);
            review.setParticipantId(participant.getId());
            review.setEmployeeId(actor.employeeId());
            review.setStatus(IntermediateReviewStatus.DRAFT);
        }
        if (review.getStatus() == IntermediateReviewStatus.MANAGER_COMPLETED) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED);
        }
        review.setProgressSummary(request.progressSummary());
        review.setAchievements(request.achievements());
        review.setBlockers(request.blockers());
        review.setSupportNeeded(request.supportNeeded());
        if (submit) {
            review.setStatus(IntermediateReviewStatus.EMPLOYEE_SUBMITTED);
            review.setEmployeeSubmittedAt(Instant.now());
        }
        return response(repository.save(review));
    }

    @Transactional
    public IntermediateReviewResponse managerComment(ActorAccess.Actor actor, UUID participantId,
                                                     IntermediateManagerRequest request, boolean complete) {
        EvaluationParticipant participant = requireManagedParticipant(actor, participantId);
        requireMidReview(actor.tenantId(), participant.getCycleId());
        IntermediatePerformanceReview review = repository.findByTenantIdAndParticipantId(actor.tenantId(), participantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED,
                Map.of("participantId", participantId, "reason", "employee-not-submitted")));
        if (review.getStatus() != IntermediateReviewStatus.EMPLOYEE_SUBMITTED) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED);
        }
        review.setManagerComment(request.managerComment());
        if (complete) {
            review.setStatus(IntermediateReviewStatus.MANAGER_COMPLETED);
            review.setManagerCompletedAt(Instant.now());
        }
        return response(repository.save(review));
    }

    @Transactional
    public IntermediateReviewResponse complete(ActorAccess.Actor actor, UUID participantId) {
        EvaluationParticipant participant = requireManagedParticipant(actor, participantId);
        requireMidReview(actor.tenantId(), participant.getCycleId());
        IntermediatePerformanceReview review = repository.findByTenantIdAndParticipantId(actor.tenantId(), participantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED));
        if (review.getStatus() != IntermediateReviewStatus.EMPLOYEE_SUBMITTED
            || review.getManagerComment() == null || review.getManagerComment().isBlank()) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED,
                Map.of("participantId", participantId, "reason", "manager-comment-required"));
        }
        review.setStatus(IntermediateReviewStatus.MANAGER_COMPLETED);
        review.setManagerCompletedAt(Instant.now());
        return response(repository.save(review));
    }

    @Transactional(readOnly = true)
    public IntermediateReviewResponse getForManager(ActorAccess.Actor actor, UUID participantId) {
        requireManagedParticipant(actor, participantId);
        return repository.findByTenantIdAndParticipantId(actor.tenantId(), participantId)
            .map(this::response).orElse(null);
    }

    private EvaluationParticipant requireManagedParticipant(ActorAccess.Actor actor, UUID participantId) {
        EvaluationParticipant participant = participants.findByIdAndTenantId(participantId, actor.tenantId())
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.PARTICIPANT_NOT_FOUND));
        if ("HR_ADMIN".equals(actor.role()) || "SUPER_ADMIN".equals(actor.role())) return participant;
        EvaluationReviewerAssignment assignment = reviewers
            .findByTenantIdAndParticipantIdAndReviewerTypeAndRound(actor.tenantId(), participantId, ReviewerType.MANAGER, 1)
            .filter(r -> r.getStatus() != ReviewerAssignmentStatus.REVOKED)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.REVIEWER_ASSIGNMENT_NOT_FOUND));
        if (!assignment.getReviewerEmployeeId().equals(actor.employeeId())) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_FORBIDDEN);
        }
        return participant;
    }

    private void requireMidReview(UUID tenantId, UUID cycleId) {
        var cycle = cycles.findByIdAndTenantId(cycleId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND));
        if (cycle.getStatus() != CycleStatus.MID_REVIEW) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED,
                Map.of("cycleId", cycleId, "required", CycleStatus.MID_REVIEW.name(),
                    "actual", cycle.getStatus().name()));
        }
    }

    IntermediateReviewResponse response(IntermediatePerformanceReview review) {
        return new IntermediateReviewResponse(review.getId(), review.getCycleId(), review.getParticipantId(),
            review.getEmployeeId(), review.getStatus(), review.getProgressSummary(), review.getAchievements(),
            review.getBlockers(), review.getSupportNeeded(), review.getManagerComment(),
            review.getEmployeeSubmittedAt(), review.getManagerCompletedAt(), review.getCreatedAt(), review.getUpdatedAt());
    }
}

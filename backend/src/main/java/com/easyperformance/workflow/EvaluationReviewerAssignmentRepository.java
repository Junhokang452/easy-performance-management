package com.easyperformance.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationReviewerAssignmentRepository extends JpaRepository<EvaluationReviewerAssignment, UUID> {
    Optional<EvaluationReviewerAssignment> findByTenantIdAndParticipantIdAndReviewerTypeAndRound(
        UUID tenantId, UUID participantId, ReviewerType reviewerType, int round);
    List<EvaluationReviewerAssignment> findAllByTenantIdAndParticipantIdAndStatusNot(
        UUID tenantId, UUID participantId, ReviewerAssignmentStatus status);
    List<EvaluationReviewerAssignment> findAllByTenantIdAndCycleIdAndReviewerEmployeeIdAndStatusNot(
        UUID tenantId, UUID cycleId, UUID reviewerEmployeeId, ReviewerAssignmentStatus status);
}

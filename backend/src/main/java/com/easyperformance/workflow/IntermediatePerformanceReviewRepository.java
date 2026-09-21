package com.easyperformance.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IntermediatePerformanceReviewRepository extends JpaRepository<IntermediatePerformanceReview, UUID> {
    Optional<IntermediatePerformanceReview> findByTenantIdAndParticipantId(UUID tenantId, UUID participantId);
    long countByTenantIdAndCycleIdAndStatusNot(UUID tenantId, UUID cycleId, IntermediateReviewStatus status);
}

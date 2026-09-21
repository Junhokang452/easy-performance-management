package com.easyperformance.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationParticipantRepository extends JpaRepository<EvaluationParticipant, UUID> {
    Optional<EvaluationParticipant> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<EvaluationParticipant> findByTenantIdAndCycleIdAndEmployeeId(UUID tenantId, UUID cycleId, UUID employeeId);
    List<EvaluationParticipant> findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(UUID tenantId, UUID cycleId);
    long countByTenantIdAndCycleIdAndStatus(UUID tenantId, UUID cycleId, ParticipantStatus status);
}

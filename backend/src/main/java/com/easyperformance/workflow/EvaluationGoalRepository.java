package com.easyperformance.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationGoalRepository extends JpaRepository<EvaluationGoal, UUID> {
    Optional<EvaluationGoal> findByIdAndTenantId(UUID id, UUID tenantId);
    List<EvaluationGoal> findAllByTenantIdAndCycleIdAndEmployeeIdOrderByCreatedAtAsc(
        UUID tenantId, UUID cycleId, UUID employeeId);
    List<EvaluationGoal> findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(UUID tenantId, UUID cycleId);
    long countByTenantIdAndCycleIdAndStatusNot(UUID tenantId, UUID cycleId, GoalStatus status);
    long countByTenantIdAndCycleId(UUID tenantId, UUID cycleId);
}

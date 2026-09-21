package com.easyperformance.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvaluationGoalCheckInRepository extends JpaRepository<EvaluationGoalCheckIn, UUID> {
    List<EvaluationGoalCheckIn> findAllByTenantIdAndGoalId(UUID tenantId, UUID goalId);
}

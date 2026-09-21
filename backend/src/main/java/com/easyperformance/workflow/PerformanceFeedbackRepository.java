package com.easyperformance.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PerformanceFeedbackRepository extends JpaRepository<PerformanceFeedback, UUID> {
    Optional<PerformanceFeedback> findByTenantIdAndReportId(UUID tenantId, UUID reportId);
    List<PerformanceFeedback> findAllByTenantIdAndParticipantId(UUID tenantId, UUID participantId);
}

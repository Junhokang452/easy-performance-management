package com.easyperformance.program;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProgramKpiEvidenceRepository extends JpaRepository<ProgramKpiEvidence, UUID> {
    Optional<ProgramKpiEvidence> findByTenantIdAndGoalIdAndPreviewHash(UUID tenantId, UUID goalId, String previewHash);
    Optional<ProgramKpiEvidence> findFirstByTenantIdAndGoalIdOrderByRevisionDesc(UUID tenantId, UUID goalId);
    Page<ProgramKpiEvidence> findAllByTenantIdAndGoalIdOrderByRevisionDesc(UUID tenantId, UUID goalId, Pageable pageable);
}

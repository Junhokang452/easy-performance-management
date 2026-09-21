package com.easyperformance.program;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProgramReviewerLineRunRepository extends JpaRepository<ProgramReviewerLineRun, UUID> {
    Optional<ProgramReviewerLineRun> findByTenantIdAndProgramIdAndPreviewHash(
        UUID tenantId, UUID programId, String previewHash);
}

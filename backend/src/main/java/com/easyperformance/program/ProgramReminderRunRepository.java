package com.easyperformance.program;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProgramReminderRunRepository extends JpaRepository<ProgramReminderRun,UUID> {
    Optional<ProgramReminderRun> findByTenantIdAndProgramIdAndIdempotencyKey(
        UUID tenantId, UUID programId, UUID idempotencyKey);
}

package com.easyperformance.program;

import com.easyperformance.program.ProgramTypes.ProgramEventType;
import com.easyperformance.workflow.ActorAccess.Actor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

/** Read-only operator projection; raw snapshots remain internal audit evidence. */
@Service
@Transactional(readOnly = true)
public class ProgramAuditQueryService {
    private final ProgramAccess access;
    private final ProgramAuditEventRepository events;
    public ProgramAuditQueryService(ProgramAccess access, ProgramAuditEventRepository events) {
        this.access = access;
        this.events = events;
    }
    public record AuditRow(UUID id, UUID participantId, ProgramEventType eventType,
                           String reason, UUID actorEmployeeId, Instant createdAt) {}

    public Page<AuditRow> list(Actor actor, UUID programId, ProgramEventType eventType,
                              UUID participantId, int page, int size) {
        access.requireOperator(actor);
        access.program(actor, programId);
        if (page < 0 || size < 1 || size > 100) {
            throw new ProgramRuleViolation("AUDIT_PAGE_INVALID", "page must be nonnegative and size between 1 and 100");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return events.searchAudit(actor.tenantId(), programId, eventType, participantId, pageable)
            .map(e -> new AuditRow(e.getId(), e.getParticipantId(), e.getEventType(),
                e.getReason(), e.getActorEmployeeId(), e.getCreatedAt()));
    }
}

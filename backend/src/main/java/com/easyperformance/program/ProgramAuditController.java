package com.easyperformance.program;

import com.easyperformance.program.ProgramTypes.ProgramEventType;
import com.easyperformance.workflow.ActorAccess;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evaluation-programs/{programId}/audit-events")
public class ProgramAuditController {
    private final ActorAccess actors;
    private final ProgramAuditQueryService audit;
    public ProgramAuditController(ActorAccess actors, ProgramAuditQueryService audit) {
        this.actors = actors;
        this.audit = audit;
    }
    @GetMapping
    public Page<ProgramAuditQueryService.AuditRow> list(@PathVariable UUID programId,
        @RequestParam(required = false) ProgramEventType eventType,
        @RequestParam(required = false) UUID participantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size) {
        return audit.list(actors.requireActor(), programId, eventType, participantId, page, size);
    }
}

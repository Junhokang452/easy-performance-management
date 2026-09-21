package com.easyperformance.program;

import com.easyperformance.program.ProgramKpiDtos.*;
import com.easyperformance.workflow.ActorAccess;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evaluation-programs")
public class ProgramKpiEvidenceController {
    private final ActorAccess actors; private final ProgramKpiEvidenceService service;
    public ProgramKpiEvidenceController(ActorAccess actors,ProgramKpiEvidenceService service){this.actors=actors;this.service=service;}

    @GetMapping("/{programId}/participants/{participantId}/kpi-candidates")
    public Page<KpiCandidateResponse> candidates(@PathVariable UUID programId,@PathVariable UUID participantId,
        @RequestParam UUID cycleId,@RequestParam LocalDate actualCutoffDate,Pageable pageable){
        return service.candidates(actors.requireActor(),programId,participantId,cycleId,actualCutoffDate,pageable);
    }
    @PostMapping("/{programId}/participants/{participantId}/goals/{goalId}/kpi-link:preview")
    public KpiLinkPreviewResponse preview(@PathVariable UUID programId,@PathVariable UUID participantId,
        @PathVariable UUID goalId,@Valid @RequestBody KpiLinkPreviewRequest request){
        return service.preview(actors.requireActor(),programId,participantId,goalId,request);
    }
    @PostMapping("/{programId}/participants/{participantId}/goals/{goalId}/kpi-link:apply")
    public KpiLinkApplyResponse apply(@PathVariable UUID programId,@PathVariable UUID participantId,
        @PathVariable UUID goalId,@Valid @RequestBody KpiLinkApplyRequest request){
        return service.apply(actors.requireActor(),programId,participantId,goalId,request);
    }
    @GetMapping("/{programId}/participants/{participantId}/goals/{goalId}/kpi-links")
    public Page<KpiLinkApplyResponse> history(@PathVariable UUID programId,@PathVariable UUID participantId,
        @PathVariable UUID goalId,Pageable pageable){
        return service.history(actors.requireActor(),programId,participantId,goalId,pageable);
    }
}

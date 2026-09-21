package com.easyperformance.program;

import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLineApplyRequest;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLineApplyResponse;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLinePreviewRequest;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLinePreviewResponse;
import com.easyperformance.workflow.ActorAccess;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evaluation-programs")
public class ProgramReviewerLineController {
    private final ActorAccess actors;
    private final ProgramReviewerLineService service;

    public ProgramReviewerLineController(ActorAccess actors, ProgramReviewerLineService service) {
        this.actors = actors;
        this.service = service;
    }

    @PostMapping("/{programId}/reviewer-line:preview")
    public ReviewerLinePreviewResponse preview(@PathVariable UUID programId,
                                               @Valid @RequestBody ReviewerLinePreviewRequest request) {
        return service.preview(actors.requireActor(), programId, request);
    }

    @PostMapping("/{programId}/reviewer-line:apply")
    public ReviewerLineApplyResponse apply(@PathVariable UUID programId,
                                           @Valid @RequestBody ReviewerLineApplyRequest request) {
        return service.apply(actors.requireActor(), programId, request);
    }
}

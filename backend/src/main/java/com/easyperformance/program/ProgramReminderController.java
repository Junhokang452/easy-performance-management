package com.easyperformance.program;

import com.easyperformance.program.ProgramReminderDtos.ReminderHistoryRow;
import com.easyperformance.program.ProgramReminderDtos.ReminderPreviewRequest;
import com.easyperformance.program.ProgramReminderDtos.ReminderPreviewResponse;
import com.easyperformance.program.ProgramReminderDtos.ReminderQueueRequest;
import com.easyperformance.program.ProgramReminderDtos.ReminderQueueResponse;
import com.easyperformance.workflow.ActorAccess;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evaluation-programs/{programId}")
public class ProgramReminderController {
    private final ActorAccess actors;
    private final ProgramReminderService reminders;
    public ProgramReminderController(ActorAccess actors,ProgramReminderService reminders){this.actors=actors;this.reminders=reminders;}

    @PostMapping("/incomplete-reminders:preview")
    public ReminderPreviewResponse preview(@PathVariable UUID programId,@Valid @RequestBody ReminderPreviewRequest request){
        return reminders.preview(actors.requireActor(),programId,request);
    }
    @PostMapping("/incomplete-reminders:queue")
    public ReminderQueueResponse queue(@PathVariable UUID programId,@Valid @RequestBody ReminderQueueRequest request){
        return reminders.queue(actors.requireActor(),programId,request);
    }
    @GetMapping("/incomplete-reminders")
    public Page<ReminderHistoryRow> history(@PathVariable UUID programId,Pageable pageable){
        return reminders.history(actors.requireActor(),programId,pageable);
    }
}

package com.easyperformance.program;

import com.easyperformance.program.ProgramReminderDtos.*;
import com.easyperformance.workflow.ActorAccess;
import com.easyperformance.workflow.ActorAccess.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProgramReminderControllerMappingTest {
    private final UUID tenantId=UUID.randomUUID(),programId=UUID.randomUUID(),participantId=UUID.randomUUID();
    private ActorAccess actors;
    private ProgramReminderService reminders;
    private MockMvc mvc;

    @BeforeEach
    void setUp(){
        actors=mock(ActorAccess.class);reminders=mock(ProgramReminderService.class);
        Actor actor=new Actor(UUID.randomUUID(),tenantId,UUID.randomUUID(),"operator","HR_ADMIN");
        when(actors.requireActor()).thenReturn(actor);
        when(reminders.preview(eq(actor),eq(programId),any())).thenReturn(new ReminderPreviewResponse(
            programId,LocalDate.of(2026,9,8),"UTC",ProgramReminderDtos.POLICY_VERSION,"a".repeat(64),List.of(),List.of(),new ReminderSummary(1,0,0,0,0,1)));
        when(reminders.queue(eq(actor),eq(programId),any())).thenReturn(new ReminderQueueResponse(
            programId,LocalDate.of(2026,9,8),ProgramReminderDtos.POLICY_VERSION,"a".repeat(64),UUID.randomUUID(),0,0,List.of()));
        when(reminders.history(eq(actor),eq(programId),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(),PageRequest.of(0,20),0));
        mvc=MockMvcBuilders.standaloneSetup(new ProgramReminderController(actors,reminders))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
    }

    @Test
    void approvedWirePathsMapWithoutAnInsertedSlashBeforeColon() throws Exception {
        String preview="""
            {"participantIds":["%s"],"stages":["SELF_REVIEW"],"locale":"ko"}
            """.formatted(participantId);
        String queue="""
            {"participantIds":["%s"],"stages":["SELF_REVIEW"],"locale":"ko","reminderOn":"2026-09-08",
             "previewHash":"%s","candidateKeys":["%s"],"idempotencyKey":"%s","reason":"확인 요청"}
            """.formatted(participantId,"a".repeat(64),"b".repeat(64),UUID.randomUUID());
        String base="/api/v1/evaluation-programs/"+programId;

        mvc.perform(post(base+"/incomplete-reminders:preview").contentType(MediaType.APPLICATION_JSON).content(preview)).andExpect(status().isOk());
        mvc.perform(post(base+"/incomplete-reminders:queue").contentType(MediaType.APPLICATION_JSON).content(queue)).andExpect(status().isOk());
        mvc.perform(get(base+"/incomplete-reminders")).andExpect(status().isOk());
        mvc.perform(post(base+"/incomplete-reminders/:preview").contentType(MediaType.APPLICATION_JSON).content(preview)).andExpect(status().isNotFound());
        mvc.perform(post(base+"/incomplete-reminders/:queue").contentType(MediaType.APPLICATION_JSON).content(queue)).andExpect(status().isNotFound());
    }
}

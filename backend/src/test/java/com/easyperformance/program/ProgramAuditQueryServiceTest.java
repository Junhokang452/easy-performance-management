package com.easyperformance.program;

import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProgramAuditQueryServiceTest {
    final EvaluationProgramRepository programs = mock(EvaluationProgramRepository.class);
    final ProgramParticipantRepository participants = mock(ProgramParticipantRepository.class);
    final ProgramReviewerAssignmentRepository reviewers = mock(ProgramReviewerAssignmentRepository.class);
    final ProgramAuditEventRepository events = mock(ProgramAuditEventRepository.class);
    final UUID tenant = UUID.randomUUID(), programId = UUID.randomUUID();
    final Actor admin = new Actor(UUID.randomUUID(), tenant, UUID.randomUUID(), "Admin", "HR_ADMIN");
    final ProgramAuditQueryService service = new ProgramAuditQueryService(new ProgramAccess(programs, participants, reviewers), events);
    EvaluationProgram program;

    @BeforeEach void prepare() {
        program = new EvaluationProgram(); program.setId(programId); program.setTenantId(tenant);
        program.setStatus(ProgramTypes.ProgramStatus.DRAFT);
    }
    void available() { when(programs.findByIdAndTenantId(programId, tenant)).thenReturn(Optional.of(program)); }

    @Test void employeeCannotReadEvenTheirOwnAudit() {
        var employee = new Actor(UUID.randomUUID(), tenant, UUID.randomUUID(), "Employee", "EMPLOYEE");
        assertThatThrownBy(() -> service.list(employee, programId, null, null, 0, 25)).isInstanceOf(ApiException.class);
        verifyNoInteractions(events, programs);
    }
    @Test void programFromOtherTenantIsNotReadable() {
        when(programs.findByIdAndTenantId(programId, tenant)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list(admin, programId, null, null, 0, 25)).isInstanceOf(ApiException.class);
        verifyNoInteractions(events);
    }
    @Test void restrictsBothFiltersAndUsesStableNewestFirstPagination() {
        available(); UUID participant = UUID.randomUUID();
        var type = ProgramTypes.ProgramEventType.FINALIZATION_CANCELLED;
        ProgramAuditEvent event = new ProgramAuditEvent(); event.setId(UUID.randomUUID());
        event.setParticipantId(participant); event.setEventType(type); event.setReason("Correct invalid result");
        event.setActorEmployeeId(admin.employeeId()); event.setCreatedAt(Instant.parse("2026-09-08T00:00:00Z"));
        event.setDetailsJson("{\"privateSnapshot\":\"not projected\"}");
        when(events.searchAudit(eq(tenant), eq(programId), eq(type), eq(participant), any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.of(event), invocation.getArgument(4), 51));
        var result = service.list(admin, programId, type, participant, 2, 25);
        assertThat(result.getTotalElements()).isEqualTo(51);
        assertThat(result.getContent()).singleElement().satisfies(row -> {
            assertThat(row.reason()).isEqualTo("Correct invalid result");
            assertThat(row.actorEmployeeId()).isEqualTo(admin.employeeId());
        });
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(events).searchAudit(eq(tenant), eq(programId), eq(type), eq(participant), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getSort().toString()).isEqualTo("createdAt: DESC,id: DESC");
        assertThat(ProgramAuditQueryService.AuditRow.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName).doesNotContain("detailsJson", "tenantId");
    }
    @Test void rejectsNegativePage() {
        available(); assertThatThrownBy(() -> service.list(admin, programId, null, null, -1, 25)).isInstanceOf(ProgramRuleViolation.class);
        verifyNoInteractions(events);
    }
    @Test void rejectsOversizedPage() {
        available(); assertThatThrownBy(() -> service.list(admin, programId, null, null, 0, 101)).isInstanceOf(ProgramRuleViolation.class);
        verifyNoInteractions(events);
    }
    @Test void rejectsEmptyPageSize() {
        available(); assertThatThrownBy(() -> service.list(admin, programId, null, null, 0, 0)).isInstanceOf(ProgramRuleViolation.class);
        verifyNoInteractions(events);
    }
    @Test void superAdminCanReadEmptyHistoryWithoutFilters() {
        available(); var root = new Actor(UUID.randomUUID(), tenant, null, "Root", "SUPER_ADMIN");
        when(events.searchAudit(eq(tenant), eq(programId), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.list(root, programId, null, null, 0, 100).getContent()).isEmpty();
    }
}

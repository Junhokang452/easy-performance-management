package com.easyperformance.resources;

import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.resources.ResourceDtos.InterviewView;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {
    @Mock InterviewRecordRepository interviews;
    @Mock InterviewReferenceRepository references;
    @Mock InterviewAuditRepository audits;
    @Mock RmEmployeeRepository employees;
    InterviewService service;
    UUID tenantId; UUID actorEmployeeId; Actor actor;

    @BeforeEach
    void setUp() {
        service = new InterviewService(interviews, references, audits, employees);
        tenantId = UUID.randomUUID(); actorEmployeeId = UUID.randomUUID();
        actor = new Actor(UUID.randomUUID(), tenantId, actorEmployeeId, "평가 관리자", "HR_ADMIN");
    }

    @Test
    void adminOrEvaluatorRoleDoesNotBypassPrivateInterviewAcl() {
        InterviewRecord record = record(UUID.randomUUID(), UUID.randomUUID(), actorEmployeeId, false, false);
        when(interviews.findByIdAndTenantId(record.getId(), tenantId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.get(actor, record.getId())).isInstanceOfSatisfying(ApiException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ResourceErrorCode.RESOURCE_FORBIDDEN));
    }

    @Test
    void subjectAndReferenceVisibilityAreIndependent() {
        UUID authorId = UUID.randomUUID();
        InterviewRecord subjectHidden = record(UUID.randomUUID(), authorId, actorEmployeeId, false, true);
        when(interviews.findAllByTenantIdAndSubjectEmployeeIdOrderByOccurredAtDesc(tenantId, actorEmployeeId))
            .thenReturn(List.of(subjectHidden));
        assertThat(service.list(actor, InterviewView.MINE, null)).isEmpty();

        InterviewRecord referenceVisible = record(UUID.randomUUID(), authorId, UUID.randomUUID(), false, true);
        InterviewReference reference = new InterviewReference(); reference.setTenantId(tenantId);
        reference.setInterviewId(referenceVisible.getId()); reference.setEmployeeId(actorEmployeeId);
        when(references.findAllByTenantIdAndEmployeeId(tenantId, actorEmployeeId)).thenReturn(List.of(reference));
        when(interviews.findByIdAndTenantId(referenceVisible.getId(), tenantId)).thenReturn(Optional.of(referenceVisible));
        when(references.findAllByTenantIdAndInterviewId(tenantId, referenceVisible.getId())).thenReturn(List.of(reference));
        when(audits.findAllByTenantIdAndInterviewIdOrderByCreatedAtAsc(tenantId, referenceVisible.getId())).thenReturn(List.of());

        assertThat(service.list(actor, InterviewView.REFERENCED, null)).extracting(ResourceDtos.InterviewResponse::id)
            .containsExactly(referenceVisible.getId());
    }

    private InterviewRecord record(UUID id, UUID authorId, UUID subjectId, boolean subjectVisible, boolean referencesVisible) {
        InterviewRecord record = new InterviewRecord(); record.setId(id); record.setTenantId(tenantId);
        record.setAuthorEmployeeId(authorId); record.setSubjectEmployeeId(subjectId); record.setOccurredAt(Instant.now());
        record.setSummary("비공개 기록"); record.setSubjectVisible(subjectVisible); record.setReferencesVisible(referencesVisible);
        return record;
    }
}

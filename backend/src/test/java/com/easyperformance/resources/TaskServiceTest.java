package com.easyperformance.resources;

import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.resources.ResourceDtos.TaskReopenRequest;
import com.easyperformance.resources.ResourceDtos.TaskStatus;
import com.easyperformance.resources.ResourceDtos.TaskUpdateRequest;
import com.easyperformance.resources.ResourceDtos.StakeholderRole;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock PerformanceTaskRepository tasks;
    @Mock TaskStakeholderRepository stakeholders;
    @Mock TaskChecklistRepository checklist;
    @Mock TaskActivityRepository activities;
    @Mock TaskLabelRepository labels;
    @Mock TaskLabelLinkRepository labelLinks;
    @Mock TaskFeedbackRepository feedback;
    @Mock TaskAttachmentRepository attachments;
    @Mock RmEmployeeRepository employees;
    @Mock ResourceIntegrationService resources;
    TaskService service;
    UUID tenantId; UUID employeeId; UUID taskId; Actor actor; PerformanceTask task;

    @BeforeEach
    void setUp() {
        service = new TaskService(tasks, stakeholders, checklist, activities, labels, labelLinks, feedback,
            attachments, employees, resources);
        tenantId = UUID.randomUUID(); employeeId = UUID.randomUUID(); taskId = UUID.randomUUID();
        actor = new Actor(UUID.randomUUID(), tenantId, employeeId, "구성원", "HR_ADMIN");
        task = new PerformanceTask(); task.setId(taskId); task.setTenantId(tenantId); task.setOwnerEmployeeId(employeeId);
        task.setTitle("신규시장 실행"); task.setPeriodStart(LocalDate.now()); task.setPeriodEnd(LocalDate.now().plusMonths(1));
        task.setStatus(TaskStatus.IN_PROGRESS); task.setProgressMode(ResourceDtos.ProgressMode.ACTUAL);
        task.setActualProgress(java.math.BigDecimal.TEN);
    }

    @Test
    void tenantAdminWhoIsNotStakeholderCannotReadTask() {
        when(tasks.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.of(task));
        when(stakeholders.existsByTenantIdAndTaskIdAndEmployeeId(tenantId, taskId, employeeId)).thenReturn(false);

        assertThatThrownBy(() -> service.get(actor, taskId)).isInstanceOfSatisfying(ApiException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ResourceErrorCode.RESOURCE_FORBIDDEN));
    }

    @Test
    void terminalTaskCannotBeEditedThroughGenericUpdate() {
        task.setStatus(TaskStatus.COMPLETED); permitManage();
        TaskUpdateRequest request = new TaskUpdateRequest("수정", LocalDate.now(), LocalDate.now().plusDays(1), null, null, List.of());

        assertThatThrownBy(() -> service.update(actor, taskId, request)).isInstanceOfSatisfying(ApiException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ResourceErrorCode.RESOURCE_LOCKED));
    }

    @Test
    void explicitReopenRestoresInProgressAndAppendsReasonedHistory() {
        task.setStatus(TaskStatus.COMPLETED); permitManage(); emptyDetails();
        when(activities.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.reopen(actor, taskId, new TaskReopenRequest("고객 검증 결과 반영"));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        ArgumentCaptor<TaskActivity> event = ArgumentCaptor.forClass(TaskActivity.class);
        verify(activities).save(event.capture());
        assertThat(event.getValue().getType()).isEqualTo("REOPENED");
        assertThat(event.getValue().getMessage()).isEqualTo("고객 검증 결과 반영");
        assertThat(event.getValue().getFromStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(event.getValue().getToStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void attachmentUsesOpaqueIdStorageAndStripsClientPath() {
        permitManage();
        when(attachments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(activities.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        byte[] bytes = "%PDF-private".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "../../quarterly.pdf", "application/pdf", bytes);

        service.addAttachment(actor, taskId, file);

        ArgumentCaptor<TaskAttachment> stored = ArgumentCaptor.forClass(TaskAttachment.class);
        verify(attachments).save(stored.capture());
        assertThat(stored.getValue().getFilename()).isEqualTo("quarterly.pdf");
        assertThat(stored.getValue().getContent()).containsExactly(bytes);
    }

    @Test
    void stakeholderReplacementFlushesDeletesBeforeReinsertingUniqueKeys() {
        permitManage(); emptyDetails();
        when(activities.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TaskUpdateRequest request = new TaskUpdateRequest("수정", LocalDate.now(), LocalDate.now().plusDays(1), null, null, List.of());

        service.update(actor, taskId, request);

        var order = inOrder(stakeholders);
        order.verify(stakeholders).deleteAllByTenantIdAndTaskId(tenantId, taskId);
        order.verify(stakeholders).flush();
        order.verify(stakeholders).saveAll(any());
    }

    private void permitManage() {
        when(tasks.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.of(task));
        when(stakeholders.existsByTenantIdAndTaskIdAndEmployeeId(tenantId, taskId, employeeId)).thenReturn(true);
        TaskStakeholder manager = new TaskStakeholder(); manager.setTenantId(tenantId); manager.setTaskId(taskId);
        manager.setEmployeeId(employeeId); manager.setRole(StakeholderRole.MANAGER);
        when(stakeholders.findByTenantIdAndTaskIdAndEmployeeId(tenantId, taskId, employeeId)).thenReturn(Optional.of(manager));
    }

    private void emptyDetails() {
        when(stakeholders.findAllByTenantIdAndTaskId(tenantId, taskId)).thenReturn(List.of());
        when(labelLinks.findAllByTenantIdAndTaskId(tenantId, taskId)).thenReturn(List.of());
        when(checklist.findAllByTenantIdAndTaskIdOrderByDisplayOrderAscCreatedAtAsc(tenantId, taskId)).thenReturn(List.of());
        when(activities.findAllByTenantIdAndTaskIdOrderByCreatedAtAsc(tenantId, taskId)).thenReturn(List.of());
        when(feedback.findAllByTenantIdAndTaskIdOrderByCreatedAtAsc(tenantId, taskId)).thenReturn(List.of());
        when(attachments.findAllByTenantIdAndTaskIdOrderByCreatedAtAsc(tenantId, taskId)).thenReturn(List.of());
    }
}

package com.easyperformance.resources;

import com.easyperformance.resources.ResourceDtos.ProgressMode;
import com.easyperformance.resources.ResourceDtos.TaskStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceIntegrationServiceTest {
    @Test
    void checklistEvidenceComputesProgressWithoutConvertingItToEvaluationScore() {
        var catalogs = mock(EvaluationCatalogRepository.class); var levels = mock(CatalogLevelRepository.class);
        var goals = mock(DepartmentGoalRepository.class); var departmentGoals = mock(DepartmentGoalService.class);
        var tasks = mock(PerformanceTaskRepository.class); var stakeholders = mock(TaskStakeholderRepository.class);
        var checklist = mock(TaskChecklistRepository.class);
        var service = new ResourceIntegrationService(catalogs, levels, goals, departmentGoals, tasks, stakeholders, checklist);
        UUID tenantId = UUID.randomUUID(), employeeId = UUID.randomUUID(), taskId = UUID.randomUUID();
        PerformanceTask task = new PerformanceTask(); task.setId(taskId); task.setTenantId(tenantId); task.setTitle("성과 근거");
        task.setDescription("실행 활동 원문"); task.setStatus(TaskStatus.IN_PROGRESS); task.setProgressMode(ProgressMode.CHECKLIST);
        TaskChecklistItem done = new TaskChecklistItem(); done.setCompleted(true);
        TaskChecklistItem open = new TaskChecklistItem(); open.setCompleted(false);
        when(tasks.findAllByTenantIdAndIdIn(tenantId, List.of(taskId))).thenReturn(List.of(task));
        when(stakeholders.existsByTenantIdAndTaskIdAndEmployeeId(tenantId, taskId, employeeId)).thenReturn(true);
        when(checklist.findAllByTenantIdAndTaskIdOrderByDisplayOrderAscCreatedAtAsc(tenantId, taskId)).thenReturn(List.of(done, open));

        var evidence = service.taskEvidence(tenantId, employeeId, List.of(taskId)).getFirst();

        assertThat(evidence.progressPercent()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(evidence).hasNoNullFieldsOrPropertiesExcept("occurredAt");
    }
}

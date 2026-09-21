package com.easyperformance.resources;

import com.easyperformance.resources.ResourceDtos.CatalogKind;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Stable typed boundary consumed by the evaluation-program aggregate. */
@Service
public class ResourceIntegrationService {
    private final EvaluationCatalogRepository catalogs;
    private final CatalogLevelRepository levels;
    private final DepartmentGoalRepository goals;
    private final DepartmentGoalService departmentGoals;
    private final PerformanceTaskRepository tasks;
    private final TaskStakeholderRepository stakeholders;
    private final TaskChecklistRepository checklist;

    public ResourceIntegrationService(EvaluationCatalogRepository catalogs, CatalogLevelRepository levels,
        DepartmentGoalRepository goals, DepartmentGoalService departmentGoals, PerformanceTaskRepository tasks,
        TaskStakeholderRepository stakeholders, TaskChecklistRepository checklist) {
        this.catalogs = catalogs; this.levels = levels; this.goals = goals; this.departmentGoals = departmentGoals;
        this.tasks = tasks; this.stakeholders = stakeholders; this.checklist = checklist;
    }

    @Transactional(readOnly = true)
    public CatalogSelection requireActiveCatalog(UUID tenantId, UUID catalogId, CatalogKind expectedKind) {
        EvaluationCatalogItem catalog = catalogs.findByIdAndTenantId(catalogId, tenantId)
            .filter(EvaluationCatalogItem::isActive)
            .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_NOT_FOUND));
        if (catalog.getKind() != expectedKind) throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
        return new CatalogSelection(catalog.getId(), catalog.getKind(), catalog.getName(), catalog.getDefinition(),
            levelSelections(tenantId, catalog.getId()));
    }

    @Transactional(readOnly = true)
    public DepartmentGoalSelection requireVisibleDepartmentGoal(UUID tenantId, UUID employeeId, UUID goalId) {
        DepartmentGoal goal = goals.findByIdAndTenantId(goalId, tenantId)
            .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_NOT_FOUND));
        if (!goal.getDepartmentId().equals(departmentGoals.employee(tenantId, employeeId).getOrgUnitId())) {
            throw new ApiException(ResourceErrorCode.RESOURCE_FORBIDDEN);
        }
        List<AchievementLevelSelection> achievementLevels = goal.getCatalogId() == null
            ? List.of() : levelSelections(tenantId, goal.getCatalogId());
        return new DepartmentGoalSelection(goal.getId(), goal.getDepartmentId(), goal.getTitle(), goal.getDefinition(),
            goal.getWeight(), goal.getUnit(), achievementLevels);
    }

    @Transactional(readOnly = true)
    public List<TaskEvidence> taskEvidence(UUID tenantId, UUID employeeId, Collection<UUID> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return List.of();
        LinkedHashMap<UUID, PerformanceTask> found = new LinkedHashMap<>();
        tasks.findAllByTenantIdAndIdIn(tenantId, taskIds).forEach(task -> found.put(task.getId(), task));
        return taskIds.stream().distinct().map(id -> {
            PerformanceTask task = found.get(id);
            if (task == null) throw new ApiException(ResourceErrorCode.RESOURCE_NOT_FOUND);
            if (!stakeholders.existsByTenantIdAndTaskIdAndEmployeeId(tenantId, id, employeeId)) {
                throw new ApiException(ResourceErrorCode.RESOURCE_FORBIDDEN);
            }
            return new TaskEvidence(task.getId(), task.getTitle(), task.getStatus().name(), progress(task),
                task.getUpdatedAt(), summarize(task.getDescription()));
        }).toList();
    }

    private List<AchievementLevelSelection> levelSelections(UUID tenantId, UUID catalogId) {
        return levels.findAllByTenantIdAndCatalogIdOrderByDisplayOrderAsc(tenantId, catalogId).stream()
            .map(l -> new AchievementLevelSelection(l.getCode(), l.getLabel(), l.getMinValue(), l.getMaxValue())).toList();
    }

    private BigDecimal progress(PerformanceTask task) {
        if (task.getProgressMode() == ResourceDtos.ProgressMode.ACTUAL) return task.getActualProgress();
        List<TaskChecklistItem> items = checklist.findAllByTenantIdAndTaskIdOrderByDisplayOrderAscCreatedAtAsc(
            task.getTenantId(), task.getId());
        if (items.isEmpty()) return BigDecimal.ZERO;
        long completed = items.stream().filter(TaskChecklistItem::isCompleted).count();
        return BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(items.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    private static String summarize(String value) {
        if (value == null) return "";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public record CatalogSelection(UUID id, CatalogKind kind, String name, String definition,
                                   List<AchievementLevelSelection> achievementLevels) {}
    public record AchievementLevelSelection(String code, String label, BigDecimal minValue, BigDecimal maxValue) {}
    public record DepartmentGoalSelection(UUID id, UUID orgUnitId, String title, String definition, BigDecimal weight,
                                          String unit, List<AchievementLevelSelection> achievementLevels) {}
    public record TaskEvidence(UUID taskId, String title, String status, BigDecimal progressPercent,
                               Instant occurredAt, String summary) {}
}

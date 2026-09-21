package com.easyperformance.resources;

import com.easyperformance.resources.ResourceDtos.CatalogKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EvaluationCatalogRepository extends JpaRepository<EvaluationCatalogItem, UUID> {
    Optional<EvaluationCatalogItem> findByIdAndTenantId(UUID id, UUID tenantId);
    List<EvaluationCatalogItem> findAllByTenantIdAndKindOrderByDisplayOrderAscNameAsc(UUID tenantId, CatalogKind kind);
    List<EvaluationCatalogItem> findAllByTenantIdOrderByKindAscDisplayOrderAscNameAsc(UUID tenantId);
}
interface CatalogLevelRepository extends JpaRepository<CatalogAchievementLevel, UUID> {
    List<CatalogAchievementLevel> findAllByTenantIdAndCatalogIdOrderByDisplayOrderAsc(UUID tenantId, UUID catalogId);
    void deleteAllByTenantIdAndCatalogId(UUID tenantId, UUID catalogId);
}
interface CatalogAssignmentRepository extends JpaRepository<CatalogAssignment, UUID> {
    List<CatalogAssignment> findAllByTenantIdAndCatalogIdOrderByTypeAscReferenceAsc(UUID tenantId, UUID catalogId);
    void deleteAllByTenantIdAndCatalogId(UUID tenantId, UUID catalogId);
}
interface DepartmentGoalRepository extends JpaRepository<DepartmentGoal, UUID> {
    Optional<DepartmentGoal> findByIdAndTenantId(UUID id, UUID tenantId);
    List<DepartmentGoal> findAllByTenantIdOrderByYearDescCreatedAtDesc(UUID tenantId);
    List<DepartmentGoal> findAllByTenantIdAndDepartmentIdOrderByYearDescCreatedAtDesc(UUID tenantId, UUID departmentId);
}
interface PerformanceTaskRepository extends JpaRepository<PerformanceTask, UUID> {
    Optional<PerformanceTask> findByIdAndTenantId(UUID id, UUID tenantId);
    List<PerformanceTask> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
interface TaskStakeholderRepository extends JpaRepository<TaskStakeholder, UUID> {
    List<TaskStakeholder> findAllByTenantIdAndEmployeeIdOrderByCreatedAtDesc(UUID tenantId, UUID employeeId);
    List<TaskStakeholder> findAllByTenantIdAndTaskId(UUID tenantId, UUID taskId);
    Optional<TaskStakeholder> findByTenantIdAndTaskIdAndEmployeeId(UUID tenantId, UUID taskId, UUID employeeId);
    boolean existsByTenantIdAndTaskIdAndEmployeeId(UUID tenantId, UUID taskId, UUID employeeId);
    void deleteAllByTenantIdAndTaskId(UUID tenantId, UUID taskId);
}
interface TaskChecklistRepository extends JpaRepository<TaskChecklistItem, UUID> {
    List<TaskChecklistItem> findAllByTenantIdAndTaskIdOrderByDisplayOrderAscCreatedAtAsc(UUID tenantId, UUID taskId);
    Optional<TaskChecklistItem> findByIdAndTenantIdAndTaskId(UUID id, UUID tenantId, UUID taskId);
}
interface TaskActivityRepository extends JpaRepository<TaskActivity, UUID> {
    List<TaskActivity> findAllByTenantIdAndTaskIdOrderByCreatedAtAsc(UUID tenantId, UUID taskId);
}
interface TaskLabelRepository extends JpaRepository<TaskLabel, UUID> {
    List<TaskLabel> findAllByTenantIdAndOwnerEmployeeIdOrderByNameAsc(UUID tenantId, UUID ownerEmployeeId);
    Optional<TaskLabel> findByIdAndTenantIdAndOwnerEmployeeId(UUID id, UUID tenantId, UUID ownerEmployeeId);
    Optional<TaskLabel> findByIdAndTenantId(UUID id, UUID tenantId);
}
interface TaskLabelLinkRepository extends JpaRepository<TaskLabelLink, UUID> {
    List<TaskLabelLink> findAllByTenantIdAndTaskId(UUID tenantId, UUID taskId);
    boolean existsByTenantIdAndTaskIdAndLabelId(UUID tenantId, UUID taskId, UUID labelId);
    void deleteByTenantIdAndTaskIdAndLabelId(UUID tenantId, UUID taskId, UUID labelId);
}
interface TaskFeedbackRepository extends JpaRepository<TaskFeedback, UUID> {
    List<TaskFeedback> findAllByTenantIdAndTaskIdOrderByCreatedAtAsc(UUID tenantId, UUID taskId);
}
interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, UUID> {
    List<TaskAttachment> findAllByTenantIdAndTaskIdOrderByCreatedAtAsc(UUID tenantId, UUID taskId);
    Optional<TaskAttachment> findByIdAndTenantIdAndTaskId(UUID id, UUID tenantId, UUID taskId);
}
interface InterviewRecordRepository extends JpaRepository<InterviewRecord, UUID> {
    Optional<InterviewRecord> findByIdAndTenantId(UUID id, UUID tenantId);
    List<InterviewRecord> findAllByTenantIdAndAuthorEmployeeIdOrderByOccurredAtDesc(UUID tenantId, UUID employeeId);
    List<InterviewRecord> findAllByTenantIdAndSubjectEmployeeIdOrderByOccurredAtDesc(UUID tenantId, UUID employeeId);
}
interface InterviewReferenceRepository extends JpaRepository<InterviewReference, UUID> {
    List<InterviewReference> findAllByTenantIdAndInterviewId(UUID tenantId, UUID interviewId);
    List<InterviewReference> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);
    void deleteAllByTenantIdAndInterviewId(UUID tenantId, UUID interviewId);
}
interface InterviewAuditRepository extends JpaRepository<InterviewAuditEvent, UUID> {
    List<InterviewAuditEvent> findAllByTenantIdAndInterviewIdOrderByCreatedAtAsc(UUID tenantId, UUID interviewId);
}

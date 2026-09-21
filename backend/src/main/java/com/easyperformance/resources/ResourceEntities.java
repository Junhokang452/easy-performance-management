package com.easyperformance.resources;

import com.easyperformance.resources.ResourceDtos.CatalogAssignmentType;
import com.easyperformance.resources.ResourceDtos.CatalogKind;
import com.easyperformance.resources.ResourceDtos.InterviewAuditAction;
import com.easyperformance.resources.ResourceDtos.ProgressMode;
import com.easyperformance.resources.ResourceDtos.StakeholderRole;
import com.easyperformance.resources.ResourceDtos.TaskStatus;
import com.easyware.platform.UuidV7;
import com.easyware.platform.audit.TenantAwareAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@MappedSuperclass
abstract class ResourceEntity extends TenantAwareAuditEntity {
    @Id @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;
    @Version @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @PrePersist
    protected void assignId() { if (id == null) id = UuidV7.generate(); }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public long getRowVersion() { return rowVersion; }
}

@Entity @Table(name = "evaluation_resource_catalog")
class EvaluationCatalogItem extends ResourceEntity {
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CatalogKind kind;
    @Column(nullable = false, length = 100) private String category;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, columnDefinition = "text") private String definition;
    @Column(nullable = false) private boolean active;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "copied_from_id", columnDefinition = "uuid") private UUID copiedFromId;
    public CatalogKind getKind() { return kind; } public void setKind(CatalogKind v) { kind = v; }
    public String getCategory() { return category; } public void setCategory(String v) { category = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getDefinition() { return definition; } public void setDefinition(String v) { definition = v; }
    public boolean isActive() { return active; } public void setActive(boolean v) { active = v; }
    public int getDisplayOrder() { return displayOrder; } public void setDisplayOrder(int v) { displayOrder = v; }
    public UUID getCopiedFromId() { return copiedFromId; } public void setCopiedFromId(UUID v) { copiedFromId = v; }
}

@Entity @Table(name = "evaluation_resource_catalog_level")
class CatalogAchievementLevel extends ResourceEntity {
    @Column(name = "catalog_id", columnDefinition = "uuid", nullable = false) private UUID catalogId;
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 100) private String label;
    @Column(name = "min_value", precision = 12, scale = 4) private BigDecimal minValue;
    @Column(name = "max_value", precision = 12, scale = 4) private BigDecimal maxValue;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    public UUID getCatalogId() { return catalogId; } public void setCatalogId(UUID v) { catalogId = v; }
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getLabel() { return label; } public void setLabel(String v) { label = v; }
    public BigDecimal getMinValue() { return minValue; } public void setMinValue(BigDecimal v) { minValue = v; }
    public BigDecimal getMaxValue() { return maxValue; } public void setMaxValue(BigDecimal v) { maxValue = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public int getDisplayOrder() { return displayOrder; } public void setDisplayOrder(int v) { displayOrder = v; }
}

@Entity @Table(name = "evaluation_resource_catalog_assignment")
class CatalogAssignment extends ResourceEntity {
    @Column(name = "catalog_id", columnDefinition = "uuid", nullable = false) private UUID catalogId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CatalogAssignmentType type;
    @Column(name = "reference_key", nullable = false, length = 120) private String reference;
    public UUID getCatalogId() { return catalogId; } public void setCatalogId(UUID v) { catalogId = v; }
    public CatalogAssignmentType getType() { return type; } public void setType(CatalogAssignmentType v) { type = v; }
    public String getReference() { return reference; } public void setReference(String v) { reference = v; }
}

@Entity @Table(name = "department_goal")
class DepartmentGoal extends ResourceEntity {
    @Column(name = "goal_year", nullable = false) private int year;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end", nullable = false) private LocalDate periodEnd;
    @Column(name = "department_id", columnDefinition = "uuid", nullable = false) private UUID departmentId;
    @Column(name = "catalog_id", columnDefinition = "uuid") private UUID catalogId;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "text") private String definition;
    @Column(nullable = false, precision = 7, scale = 4) private BigDecimal weight;
    @Column(name = "target_level", nullable = false, length = 40) private String targetLevel;
    @Column(nullable = false, length = 40) private String unit;
    @Column(name = "actual_value", precision = 18, scale = 4) private BigDecimal actualValue;
    @Column(name = "achievement_rate", precision = 8, scale = 4) private BigDecimal achievementRate;
    @Column(name = "actual_note", columnDefinition = "text") private String actualNote;
    @Column(name = "copied_from_id", columnDefinition = "uuid") private UUID copiedFromId;
    @Column(name = "transferred_from_id", columnDefinition = "uuid") private UUID transferredFromId;
    public int getYear() { return year; } public void setYear(int v) { year = v; }
    public LocalDate getPeriodStart() { return periodStart; } public void setPeriodStart(LocalDate v) { periodStart = v; }
    public LocalDate getPeriodEnd() { return periodEnd; } public void setPeriodEnd(LocalDate v) { periodEnd = v; }
    public UUID getDepartmentId() { return departmentId; } public void setDepartmentId(UUID v) { departmentId = v; }
    public UUID getCatalogId() { return catalogId; } public void setCatalogId(UUID v) { catalogId = v; }
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getDefinition() { return definition; } public void setDefinition(String v) { definition = v; }
    public BigDecimal getWeight() { return weight; } public void setWeight(BigDecimal v) { weight = v; }
    public String getTargetLevel() { return targetLevel; } public void setTargetLevel(String v) { targetLevel = v; }
    public String getUnit() { return unit; } public void setUnit(String v) { unit = v; }
    public BigDecimal getActualValue() { return actualValue; } public void setActualValue(BigDecimal v) { actualValue = v; }
    public BigDecimal getAchievementRate() { return achievementRate; } public void setAchievementRate(BigDecimal v) { achievementRate = v; }
    public String getActualNote() { return actualNote; } public void setActualNote(String v) { actualNote = v; }
    public UUID getCopiedFromId() { return copiedFromId; } public void setCopiedFromId(UUID v) { copiedFromId = v; }
    public UUID getTransferredFromId() { return transferredFromId; } public void setTransferredFromId(UUID v) { transferredFromId = v; }
}

@Entity @Table(name = "performance_task")
class PerformanceTask extends ResourceEntity {
    @Column(name = "owner_employee_id", columnDefinition = "uuid", nullable = false) private UUID ownerEmployeeId;
    @Column(nullable = false, length = 200) private String title;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end", nullable = false) private LocalDate periodEnd;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TaskStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "progress_mode", nullable = false, length = 20) private ProgressMode progressMode;
    @Column(name = "actual_progress", nullable = false, precision = 7, scale = 4) private BigDecimal actualProgress;
    @Column(name = "department_goal_id", columnDefinition = "uuid") private UUID departmentGoalId;
    public UUID getOwnerEmployeeId() { return ownerEmployeeId; } public void setOwnerEmployeeId(UUID v) { ownerEmployeeId = v; }
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public LocalDate getPeriodStart() { return periodStart; } public void setPeriodStart(LocalDate v) { periodStart = v; }
    public LocalDate getPeriodEnd() { return periodEnd; } public void setPeriodEnd(LocalDate v) { periodEnd = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public TaskStatus getStatus() { return status; } public void setStatus(TaskStatus v) { status = v; }
    public ProgressMode getProgressMode() { return progressMode; } public void setProgressMode(ProgressMode v) { progressMode = v; }
    public BigDecimal getActualProgress() { return actualProgress; } public void setActualProgress(BigDecimal v) { actualProgress = v; }
    public UUID getDepartmentGoalId() { return departmentGoalId; } public void setDepartmentGoalId(UUID v) { departmentGoalId = v; }
}

@Entity @Table(name = "performance_task_stakeholder")
class TaskStakeholder extends ResourceEntity {
    @Column(name = "task_id", columnDefinition = "uuid", nullable = false) private UUID taskId;
    @Column(name = "employee_id", columnDefinition = "uuid", nullable = false) private UUID employeeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private StakeholderRole role;
    public UUID getTaskId() { return taskId; } public void setTaskId(UUID v) { taskId = v; }
    public UUID getEmployeeId() { return employeeId; } public void setEmployeeId(UUID v) { employeeId = v; }
    public StakeholderRole getRole() { return role; } public void setRole(StakeholderRole v) { role = v; }
}

@Entity @Table(name = "performance_task_checklist")
class TaskChecklistItem extends ResourceEntity {
    @Column(name = "task_id", columnDefinition = "uuid", nullable = false) private UUID taskId;
    @Column(nullable = false, length = 500) private String text;
    @Column(nullable = false) private boolean completed;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "completed_by", columnDefinition = "uuid") private UUID completedBy;
    public UUID getTaskId() { return taskId; } public void setTaskId(UUID v) { taskId = v; }
    public String getText() { return text; } public void setText(String v) { text = v; }
    public boolean isCompleted() { return completed; } public void setCompleted(boolean v) { completed = v; }
    public int getDisplayOrder() { return displayOrder; } public void setDisplayOrder(int v) { displayOrder = v; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant v) { completedAt = v; }
    public UUID getCompletedBy() { return completedBy; } public void setCompletedBy(UUID v) { completedBy = v; }
}

@Entity @Table(name = "performance_task_activity")
class TaskActivity extends ResourceEntity {
    @Column(name = "task_id", columnDefinition = "uuid", nullable = false) private UUID taskId;
    @Column(name = "author_employee_id", columnDefinition = "uuid", nullable = false) private UUID authorEmployeeId;
    @Column(nullable = false, length = 40) private String type;
    @Column(columnDefinition = "text", nullable = false) private String message;
    @Column(name = "progress_percent", precision = 7, scale = 4) private BigDecimal progressPercent;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 20) private TaskStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", length = 20) private TaskStatus toStatus;
    public UUID getTaskId() { return taskId; } public void setTaskId(UUID v) { taskId = v; }
    public UUID getAuthorEmployeeId() { return authorEmployeeId; } public void setAuthorEmployeeId(UUID v) { authorEmployeeId = v; }
    public String getType() { return type; } public void setType(String v) { type = v; }
    public String getMessage() { return message; } public void setMessage(String v) { message = v; }
    public BigDecimal getProgressPercent() { return progressPercent; } public void setProgressPercent(BigDecimal v) { progressPercent = v; }
    public TaskStatus getFromStatus() { return fromStatus; } public void setFromStatus(TaskStatus v) { fromStatus = v; }
    public TaskStatus getToStatus() { return toStatus; } public void setToStatus(TaskStatus v) { toStatus = v; }
}

@Entity @Table(name = "performance_task_label")
class TaskLabel extends ResourceEntity {
    @Column(name = "owner_employee_id", columnDefinition = "uuid", nullable = false) private UUID ownerEmployeeId;
    @Column(nullable = false, length = 80) private String name;
    public UUID getOwnerEmployeeId() { return ownerEmployeeId; } public void setOwnerEmployeeId(UUID v) { ownerEmployeeId = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
}

@Entity @Table(name = "performance_task_label_link")
class TaskLabelLink extends ResourceEntity {
    @Column(name = "task_id", columnDefinition = "uuid", nullable = false) private UUID taskId;
    @Column(name = "label_id", columnDefinition = "uuid", nullable = false) private UUID labelId;
    public UUID getTaskId() { return taskId; } public void setTaskId(UUID v) { taskId = v; }
    public UUID getLabelId() { return labelId; } public void setLabelId(UUID v) { labelId = v; }
}

@Entity @Table(name = "performance_task_feedback")
class TaskFeedback extends ResourceEntity {
    @Column(name = "task_id", columnDefinition = "uuid", nullable = false) private UUID taskId;
    @Column(name = "from_employee_id", columnDefinition = "uuid", nullable = false) private UUID fromEmployeeId;
    @Column(name = "to_employee_id", columnDefinition = "uuid", nullable = false) private UUID toEmployeeId;
    @Column(nullable = false) private int rating;
    @Column(nullable = false, columnDefinition = "text") private String message;
    public UUID getTaskId() { return taskId; } public void setTaskId(UUID v) { taskId = v; }
    public UUID getFromEmployeeId() { return fromEmployeeId; } public void setFromEmployeeId(UUID v) { fromEmployeeId = v; }
    public UUID getToEmployeeId() { return toEmployeeId; } public void setToEmployeeId(UUID v) { toEmployeeId = v; }
    public int getRating() { return rating; } public void setRating(int v) { rating = v; }
    public String getMessage() { return message; } public void setMessage(String v) { message = v; }
}

@Entity @Table(name = "performance_task_attachment")
class TaskAttachment extends ResourceEntity {
    @Column(name = "task_id", columnDefinition = "uuid", nullable = false) private UUID taskId;
    @Column(name = "uploaded_by", columnDefinition = "uuid", nullable = false) private UUID uploadedBy;
    @Column(name = "file_name", nullable = false, length = 255) private String filename;
    @Column(name = "content_type", nullable = false, length = 120) private String contentType;
    @Column(name = "file_size", nullable = false) private long size;
    @Column(name = "file_content", nullable = false, columnDefinition = "bytea") private byte[] content;
    public UUID getTaskId() { return taskId; } public void setTaskId(UUID v) { taskId = v; }
    public UUID getUploadedBy() { return uploadedBy; } public void setUploadedBy(UUID v) { uploadedBy = v; }
    public String getFilename() { return filename; } public void setFilename(String v) { filename = v; }
    public String getContentType() { return contentType; } public void setContentType(String v) { contentType = v; }
    public long getSize() { return size; } public void setSize(long v) { size = v; }
    public byte[] getContent() { return content; } public void setContent(byte[] v) { content = v; }
}

@Entity @Table(name = "interview_record")
class InterviewRecord extends ResourceEntity {
    @Column(name = "author_employee_id", columnDefinition = "uuid", nullable = false) private UUID authorEmployeeId;
    @Column(name = "subject_employee_id", columnDefinition = "uuid", nullable = false) private UUID subjectEmployeeId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(nullable = false, columnDefinition = "text") private String summary;
    @Column(name = "key_issues", columnDefinition = "text") private String keyIssues;
    @Column(columnDefinition = "text") private String requests;
    @Column(name = "follow_up", columnDefinition = "text") private String followUp;
    @Column(name = "subject_visible", nullable = false) private boolean subjectVisible;
    @Column(name = "references_visible", nullable = false) private boolean referencesVisible;
    public UUID getAuthorEmployeeId() { return authorEmployeeId; } public void setAuthorEmployeeId(UUID v) { authorEmployeeId = v; }
    public UUID getSubjectEmployeeId() { return subjectEmployeeId; } public void setSubjectEmployeeId(UUID v) { subjectEmployeeId = v; }
    public Instant getOccurredAt() { return occurredAt; } public void setOccurredAt(Instant v) { occurredAt = v; }
    public String getSummary() { return summary; } public void setSummary(String v) { summary = v; }
    public String getKeyIssues() { return keyIssues; } public void setKeyIssues(String v) { keyIssues = v; }
    public String getRequests() { return requests; } public void setRequests(String v) { requests = v; }
    public String getFollowUp() { return followUp; } public void setFollowUp(String v) { followUp = v; }
    public boolean isSubjectVisible() { return subjectVisible; } public void setSubjectVisible(boolean v) { subjectVisible = v; }
    public boolean isReferencesVisible() { return referencesVisible; } public void setReferencesVisible(boolean v) { referencesVisible = v; }
}

@Entity @Table(name = "interview_reference")
class InterviewReference extends ResourceEntity {
    @Column(name = "interview_id", columnDefinition = "uuid", nullable = false) private UUID interviewId;
    @Column(name = "employee_id", columnDefinition = "uuid", nullable = false) private UUID employeeId;
    public UUID getInterviewId() { return interviewId; } public void setInterviewId(UUID v) { interviewId = v; }
    public UUID getEmployeeId() { return employeeId; } public void setEmployeeId(UUID v) { employeeId = v; }
}

@Entity @Table(name = "interview_audit_event")
class InterviewAuditEvent extends ResourceEntity {
    @Column(name = "interview_id", columnDefinition = "uuid", nullable = false) private UUID interviewId;
    @Column(name = "actor_employee_id", columnDefinition = "uuid", nullable = false) private UUID actorEmployeeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private InterviewAuditAction action;
    @Column(name = "subject_visible", nullable = false) private boolean subjectVisible;
    @Column(name = "references_visible", nullable = false) private boolean referencesVisible;
    public UUID getInterviewId() { return interviewId; } public void setInterviewId(UUID v) { interviewId = v; }
    public UUID getActorEmployeeId() { return actorEmployeeId; } public void setActorEmployeeId(UUID v) { actorEmployeeId = v; }
    public InterviewAuditAction getAction() { return action; } public void setAction(InterviewAuditAction v) { action = v; }
    public boolean isSubjectVisible() { return subjectVisible; } public void setSubjectVisible(boolean v) { subjectVisible = v; }
    public boolean isReferencesVisible() { return referencesVisible; } public void setReferencesVisible(boolean v) { referencesVisible = v; }
}

package com.easyperformance.resources;

import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.resources.ResourceDtos.ActivityResponse;
import com.easyperformance.resources.ResourceDtos.AttachmentResponse;
import com.easyperformance.resources.ResourceDtos.ChecklistCompletionRequest;
import com.easyperformance.resources.ResourceDtos.ChecklistCreateRequest;
import com.easyperformance.resources.ResourceDtos.ChecklistResponse;
import com.easyperformance.resources.ResourceDtos.LabelResponse;
import com.easyperformance.resources.ResourceDtos.ProgressMode;
import com.easyperformance.resources.ResourceDtos.StakeholderInput;
import com.easyperformance.resources.ResourceDtos.StakeholderResponse;
import com.easyperformance.resources.ResourceDtos.StakeholderRole;
import com.easyperformance.resources.ResourceDtos.TaskActivityRequest;
import com.easyperformance.resources.ResourceDtos.TaskCreateRequest;
import com.easyperformance.resources.ResourceDtos.TaskDetailResponse;
import com.easyperformance.resources.ResourceDtos.TaskFeedbackRequest;
import com.easyperformance.resources.ResourceDtos.TaskFeedbackResponse;
import com.easyperformance.resources.ResourceDtos.TaskProgressRequest;
import com.easyperformance.resources.ResourceDtos.TaskReopenRequest;
import com.easyperformance.resources.ResourceDtos.TaskStatus;
import com.easyperformance.resources.ResourceDtos.TaskStatusRequest;
import com.easyperformance.resources.ResourceDtos.TaskSummaryResponse;
import com.easyperformance.resources.ResourceDtos.TaskUpdateRequest;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskService {
    static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/png", "image/jpeg", "text/plain",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PerformanceTaskRepository tasks; private final TaskStakeholderRepository stakeholders;
    private final TaskChecklistRepository checklist; private final TaskActivityRepository activities;
    private final TaskLabelRepository labels; private final TaskLabelLinkRepository labelLinks;
    private final TaskFeedbackRepository feedback; private final TaskAttachmentRepository attachments;
    private final RmEmployeeRepository employees; private final ResourceIntegrationService resources;

    public TaskService(PerformanceTaskRepository tasks, TaskStakeholderRepository stakeholders,
        TaskChecklistRepository checklist, TaskActivityRepository activities, TaskLabelRepository labels,
        TaskLabelLinkRepository labelLinks, TaskFeedbackRepository feedback, TaskAttachmentRepository attachments,
        RmEmployeeRepository employees, ResourceIntegrationService resources) {
        this.tasks = tasks; this.stakeholders = stakeholders; this.checklist = checklist; this.activities = activities;
        this.labels = labels; this.labelLinks = labelLinks; this.feedback = feedback; this.attachments = attachments;
        this.employees = employees; this.resources = resources;
    }

    @Transactional(readOnly = true)
    public List<TaskSummaryResponse> list(Actor actor) {
        return stakeholders.findAllByTenantIdAndEmployeeIdOrderByCreatedAtDesc(actor.tenantId(), actor.employeeId()).stream()
            .map(TaskStakeholder::getTaskId).distinct().map(id -> tasks.findByIdAndTenantId(id, actor.tenantId()).orElse(null))
            .filter(java.util.Objects::nonNull).map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse get(Actor actor, UUID id) { return detail(requireVisible(actor, id)); }

    @Transactional
    public TaskDetailResponse create(Actor actor, TaskCreateRequest request) {
        validatePeriod(request.periodStart(), request.periodEnd());
        if (request.departmentGoalId() != null) resources.requireVisibleDepartmentGoal(actor.tenantId(), actor.employeeId(), request.departmentGoalId());
        PerformanceTask task = new PerformanceTask(); task.setTenantId(actor.tenantId()); task.setOwnerEmployeeId(actor.employeeId());
        task.setTitle(request.title().trim()); task.setPeriodStart(request.periodStart()); task.setPeriodEnd(request.periodEnd());
        task.setDescription(trim(request.description())); task.setStatus(TaskStatus.PLANNED); task.setProgressMode(request.progressMode());
        task.setActualProgress(BigDecimal.ZERO); task.setDepartmentGoalId(request.departmentGoalId()); task = tasks.save(task);
        replaceStakeholders(actor, task, request.stakeholders());
        activity(actor, task, "CREATED", "", BigDecimal.ZERO);
        return detail(task);
    }

    @Transactional
    public TaskDetailResponse update(Actor actor, UUID id, TaskUpdateRequest request) {
        PerformanceTask task = requireManage(actor, id); requireEditable(task); validatePeriod(request.periodStart(), request.periodEnd());
        if (request.departmentGoalId() != null) resources.requireVisibleDepartmentGoal(actor.tenantId(), task.getOwnerEmployeeId(), request.departmentGoalId());
        task.setTitle(request.title().trim()); task.setPeriodStart(request.periodStart()); task.setPeriodEnd(request.periodEnd());
        task.setDescription(trim(request.description())); task.setDepartmentGoalId(request.departmentGoalId());
        replaceStakeholders(actor, task, request.stakeholders()); activity(actor, task, "UPDATED", "", null);
        return detail(task);
    }

    @Transactional
    public TaskDetailResponse changeStatus(Actor actor, UUID id, TaskStatusRequest request) {
        PerformanceTask task = requireManage(actor, id); TaskStatus from = task.getStatus();
        boolean allowed = (from == TaskStatus.PLANNED && Set.of(TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED, TaskStatus.DISCARDED).contains(request.status()))
            || (from == TaskStatus.IN_PROGRESS && Set.of(TaskStatus.COMPLETED, TaskStatus.DISCARDED).contains(request.status()));
        if (!allowed) throw new ApiException(ResourceErrorCode.RESOURCE_LOCKED);
        task.setStatus(request.status()); statusActivity(actor, task, "STATUS_CHANGED", request.reason().trim(), from, request.status());
        return detail(task);
    }

    @Transactional
    public TaskDetailResponse reopen(Actor actor, UUID id, TaskReopenRequest request) {
        PerformanceTask task = requireManage(actor, id);
        if (task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.DISCARDED) throw new ApiException(ResourceErrorCode.RESOURCE_CONFLICT);
        TaskStatus from = task.getStatus(); task.setStatus(TaskStatus.IN_PROGRESS);
        statusActivity(actor, task, "REOPENED", request.reason().trim(), from, TaskStatus.IN_PROGRESS); return detail(task);
    }

    @Transactional
    public ChecklistResponse addChecklist(Actor actor, UUID id, ChecklistCreateRequest request) {
        PerformanceTask task = requireManage(actor, id); requireEditable(task);
        if (task.getProgressMode() != ProgressMode.CHECKLIST) throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
        TaskChecklistItem item = new TaskChecklistItem(); item.setTenantId(actor.tenantId()); item.setTaskId(id);
        item.setText(request.text().trim()); item.setDisplayOrder(request.displayOrder()); item = checklist.save(item);
        activity(actor, task, "CHECKLIST_ADDED", item.getText(), progress(task)); return checklistResponse(item);
    }

    @Transactional
    public ChecklistResponse completeChecklist(Actor actor, UUID id, UUID itemId, ChecklistCompletionRequest request) {
        PerformanceTask task = requireManage(actor, id); requireEditable(task);
        TaskChecklistItem item = checklist.findByIdAndTenantIdAndTaskId(itemId, actor.tenantId(), id)
            .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_NOT_FOUND));
        item.setCompleted(request.completed()); item.setCompletedAt(request.completed() ? Instant.now() : null);
        item.setCompletedBy(request.completed() ? actor.employeeId() : null);
        activity(actor, task, "CHECKLIST_UPDATED", item.getText(), progress(task)); return checklistResponse(item);
    }

    @Transactional
    public TaskDetailResponse updateProgress(Actor actor, UUID id, TaskProgressRequest request) {
        PerformanceTask task = requireManage(actor, id); requireEditable(task);
        if (task.getProgressMode() != ProgressMode.ACTUAL) throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
        task.setActualProgress(request.progressPercent());
        activity(actor, task, "PROGRESS_UPDATED", request.note().trim(), request.progressPercent()); return detail(task);
    }

    @Transactional
    public ActivityResponse addActivity(Actor actor, UUID id, TaskActivityRequest request) {
        PerformanceTask task = requireVisible(actor, id); requireEditable(task);
        return activity(actor, task, "COMMENT", request.message().trim(), null);
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> labels(Actor actor) {
        return labels.findAllByTenantIdAndOwnerEmployeeIdOrderByNameAsc(actor.tenantId(), actor.employeeId()).stream()
            .map(l -> new LabelResponse(l.getId(), l.getName())).toList();
    }

    @Transactional
    public LabelResponse createLabel(Actor actor, String name) {
        TaskLabel label = new TaskLabel(); label.setTenantId(actor.tenantId()); label.setOwnerEmployeeId(actor.employeeId()); label.setName(name.trim());
        label = labels.save(label); return new LabelResponse(label.getId(), label.getName());
    }

    @Transactional
    public TaskDetailResponse applyLabel(Actor actor, UUID id, UUID labelId) {
        PerformanceTask task = requireManage(actor, id); requireEditable(task);
        labels.findByIdAndTenantIdAndOwnerEmployeeId(labelId, actor.tenantId(), actor.employeeId())
            .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_NOT_FOUND));
        if (!labelLinks.existsByTenantIdAndTaskIdAndLabelId(actor.tenantId(), id, labelId)) {
            TaskLabelLink link = new TaskLabelLink(); link.setTenantId(actor.tenantId()); link.setTaskId(id); link.setLabelId(labelId); labelLinks.save(link);
        }
        return detail(task);
    }

    @Transactional
    public TaskDetailResponse removeLabel(Actor actor, UUID id, UUID labelId) {
        PerformanceTask task = requireManage(actor, id); requireEditable(task);
        labelLinks.deleteByTenantIdAndTaskIdAndLabelId(actor.tenantId(), id, labelId); return detail(task);
    }

    @Transactional
    public TaskFeedbackResponse addFeedback(Actor actor, UUID id, TaskFeedbackRequest request) {
        PerformanceTask task = requireVisible(actor, id); requireEditable(task);
        if (!stakeholders.existsByTenantIdAndTaskIdAndEmployeeId(actor.tenantId(), id, request.toEmployeeId())) {
            throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
        }
        TaskFeedback item = new TaskFeedback(); item.setTenantId(actor.tenantId()); item.setTaskId(id);
        item.setFromEmployeeId(actor.employeeId()); item.setToEmployeeId(request.toEmployeeId()); item.setRating(request.rating());
        item.setMessage(request.message().trim()); item = feedback.save(item);
        return feedbackResponse(item);
    }

    @Transactional
    public AttachmentResponse addAttachment(Actor actor, UUID id, MultipartFile file) {
        PerformanceTask task = requireManage(actor, id); requireEditable(task);
        if (file == null) throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
        byte[] content;
        try { content = file.getBytes(); } catch (IOException e) { throw new ApiException(ResourceErrorCode.RESOURCE_INVALID); }
        validateFile(file, content);
        TaskAttachment attachment = new TaskAttachment(); attachment.setTenantId(actor.tenantId()); attachment.setTaskId(id);
        attachment.setUploadedBy(actor.employeeId()); attachment.setFilename(safeFilename(file.getOriginalFilename()));
        attachment.setContentType(file.getContentType()); attachment.setSize(file.getSize());
        attachment.setContent(content);
        attachment = attachments.save(attachment); activity(actor, task, "ATTACHMENT_ADDED", attachment.getFilename(), null);
        return attachmentResponse(attachment);
    }

    @Transactional(readOnly = true)
    public TaskAttachment download(Actor actor, UUID taskId, UUID attachmentId) {
        requireVisible(actor, taskId);
        return attachments.findByIdAndTenantIdAndTaskId(attachmentId, actor.tenantId(), taskId)
            .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_NOT_FOUND));
    }

    PerformanceTask requireVisible(Actor actor, UUID id) {
        PerformanceTask task = tasks.findByIdAndTenantId(id, actor.tenantId())
            .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_NOT_FOUND));
        if (!stakeholders.existsByTenantIdAndTaskIdAndEmployeeId(actor.tenantId(), id, actor.employeeId())) {
            throw new ApiException(ResourceErrorCode.RESOURCE_FORBIDDEN);
        }
        return task;
    }

    private PerformanceTask requireManage(Actor actor, UUID id) {
        PerformanceTask task = requireVisible(actor, id);
        TaskStakeholder stakeholder = stakeholders.findByTenantIdAndTaskIdAndEmployeeId(actor.tenantId(), id, actor.employeeId()).orElseThrow();
        if (stakeholder.getRole() != StakeholderRole.OWNER && stakeholder.getRole() != StakeholderRole.MANAGER) {
            throw new ApiException(ResourceErrorCode.RESOURCE_FORBIDDEN);
        }
        return task;
    }

    private void replaceStakeholders(Actor actor, PerformanceTask task, List<StakeholderInput> requested) {
        LinkedHashMap<UUID, StakeholderRole> roles = new LinkedHashMap<>(); roles.put(task.getOwnerEmployeeId(), StakeholderRole.OWNER);
        if (requested != null) for (StakeholderInput input : requested) {
            if (input.role() == StakeholderRole.OWNER && !input.employeeId().equals(task.getOwnerEmployeeId())) throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
            requireActiveEmployee(actor.tenantId(), input.employeeId()); roles.put(input.employeeId(), input.employeeId().equals(task.getOwnerEmployeeId()) ? StakeholderRole.OWNER : input.role());
        }
        stakeholders.deleteAllByTenantIdAndTaskId(actor.tenantId(), task.getId());
        stakeholders.flush();
        stakeholders.saveAll(roles.entrySet().stream().map(entry -> {
            TaskStakeholder s = new TaskStakeholder(); s.setTenantId(actor.tenantId()); s.setTaskId(task.getId());
            s.setEmployeeId(entry.getKey()); s.setRole(entry.getValue()); return s;
        }).toList());
    }

    private void requireActiveEmployee(UUID tenantId, UUID employeeId) {
        employees.findByIdAndTenantId(employeeId, tenantId).filter(e -> "ACTIVE".equalsIgnoreCase(e.getStatus()))
            .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_INVALID));
    }

    private ActivityResponse activity(Actor actor, PerformanceTask task, String type, String message, BigDecimal progress) {
        TaskActivity item = new TaskActivity(); item.setTenantId(actor.tenantId()); item.setTaskId(task.getId());
        item.setAuthorEmployeeId(actor.employeeId()); item.setType(type); item.setMessage(message); item.setProgressPercent(progress);
        item = activities.save(item); return activityResponse(item);
    }

    private ActivityResponse statusActivity(Actor actor, PerformanceTask task, String type, String reason,
                                            TaskStatus from, TaskStatus to) {
        TaskActivity item = new TaskActivity(); item.setTenantId(actor.tenantId()); item.setTaskId(task.getId());
        item.setAuthorEmployeeId(actor.employeeId()); item.setType(type); item.setMessage(reason);
        item.setProgressPercent(progress(task)); item.setFromStatus(from); item.setToStatus(to);
        item = activities.save(item); return activityResponse(item);
    }

    private TaskSummaryResponse summary(PerformanceTask task) {
        List<StakeholderResponse> stakeholderResponses = stakeholders.findAllByTenantIdAndTaskId(task.getTenantId(), task.getId()).stream()
            .map(s -> new StakeholderResponse(s.getEmployeeId(), s.getRole())).toList();
        List<LabelResponse> labelResponses = labelLinks.findAllByTenantIdAndTaskId(task.getTenantId(), task.getId()).stream()
            .map(TaskLabelLink::getLabelId).map(id -> labels.findByIdAndTenantId(id, task.getTenantId()))
            .flatMap(java.util.Optional::stream).map(l -> new LabelResponse(l.getId(), l.getName())).toList();
        return new TaskSummaryResponse(task.getId(), task.getTitle(), task.getPeriodStart(), task.getPeriodEnd(), task.getStatus(),
            task.getProgressMode(), progress(task), task.getDepartmentGoalId(), stakeholderResponses, labelResponses,
            task.getRowVersion(), task.getUpdatedAt());
    }

    private TaskDetailResponse detail(PerformanceTask task) {
        return new TaskDetailResponse(summary(task), task.getDescription(),
            checklist.findAllByTenantIdAndTaskIdOrderByDisplayOrderAscCreatedAtAsc(task.getTenantId(), task.getId()).stream().map(TaskService::checklistResponse).toList(),
            activities.findAllByTenantIdAndTaskIdOrderByCreatedAtAsc(task.getTenantId(), task.getId()).stream().map(TaskService::activityResponse).toList(),
            feedback.findAllByTenantIdAndTaskIdOrderByCreatedAtAsc(task.getTenantId(), task.getId()).stream().map(TaskService::feedbackResponse).toList(),
            attachments.findAllByTenantIdAndTaskIdOrderByCreatedAtAsc(task.getTenantId(), task.getId()).stream().map(TaskService::attachmentResponse).toList());
    }

    private BigDecimal progress(PerformanceTask task) {
        if (task.getProgressMode() == ProgressMode.ACTUAL) return task.getActualProgress();
        List<TaskChecklistItem> items = checklist.findAllByTenantIdAndTaskIdOrderByDisplayOrderAscCreatedAtAsc(task.getTenantId(), task.getId());
        if (items.isEmpty()) return BigDecimal.ZERO;
        return BigDecimal.valueOf(items.stream().filter(TaskChecklistItem::isCompleted).count()).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
    }

    private static void validatePeriod(java.time.LocalDate start, java.time.LocalDate end) {
        if (end.isBefore(start)) throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
    }
    private static void requireEditable(PerformanceTask task) {
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.DISCARDED) throw new ApiException(ResourceErrorCode.RESOURCE_LOCKED);
    }
    private static void validateFile(MultipartFile file, byte[] content) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_ATTACHMENT_BYTES) throw new ApiException(ResourceErrorCode.ATTACHMENT_TOO_LARGE);
        if (file.getContentType() == null || !ALLOWED_TYPES.contains(file.getContentType())) throw new ApiException(ResourceErrorCode.ATTACHMENT_TYPE_UNSUPPORTED);
        boolean signatureValid = switch (file.getContentType()) {
            case "application/pdf" -> startsWith(content, "%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            case "image/png" -> startsWith(content, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "image/jpeg" -> content.length >= 3 && content[0] == (byte) 0xff && content[1] == (byte) 0xd8 && content[2] == (byte) 0xff;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> startsWith(content, new byte[] {0x50, 0x4b});
            case "text/plain" -> java.util.stream.IntStream.range(0, content.length).noneMatch(i -> content[i] == 0);
            default -> false;
        };
        if (!signatureValid) throw new ApiException(ResourceErrorCode.ATTACHMENT_TYPE_UNSUPPORTED);
    }
    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
        return true;
    }
    private static String safeFilename(String value) {
        String filename = value == null ? "attachment" : value.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\u0000]", "_").trim();
        if (filename.isEmpty()) filename = "attachment";
        return filename.length() > 255 ? filename.substring(filename.length() - 255) : filename;
    }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static ChecklistResponse checklistResponse(TaskChecklistItem i) { return new ChecklistResponse(i.getId(), i.getText(), i.isCompleted(), i.getDisplayOrder(), i.getCompletedAt(), i.getCompletedBy()); }
    private static ActivityResponse activityResponse(TaskActivity i) { return new ActivityResponse(i.getId(), i.getAuthorEmployeeId(), i.getType(), i.getMessage(), i.getProgressPercent(), i.getFromStatus(), i.getToStatus(), i.getCreatedAt()); }
    private static TaskFeedbackResponse feedbackResponse(TaskFeedback i) { return new TaskFeedbackResponse(i.getId(), i.getFromEmployeeId(), i.getToEmployeeId(), i.getRating(), i.getMessage(), i.getCreatedAt()); }
    private static AttachmentResponse attachmentResponse(TaskAttachment i) { return new AttachmentResponse(i.getId(), i.getFilename(), i.getContentType(), i.getSize(), i.getUploadedBy(), i.getCreatedAt()); }
}

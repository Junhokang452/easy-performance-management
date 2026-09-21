package com.easyperformance.resources;

import com.easyperformance.resources.ResourceDtos.ActiveUpdateRequest;
import com.easyperformance.resources.ResourceDtos.AttachmentResponse;
import com.easyperformance.resources.ResourceDtos.CatalogCopyRequest;
import com.easyperformance.resources.ResourceDtos.CatalogKind;
import com.easyperformance.resources.ResourceDtos.CatalogResponse;
import com.easyperformance.resources.ResourceDtos.CatalogUpsertRequest;
import com.easyperformance.resources.ResourceDtos.ChecklistCompletionRequest;
import com.easyperformance.resources.ResourceDtos.ChecklistCreateRequest;
import com.easyperformance.resources.ResourceDtos.ChecklistResponse;
import com.easyperformance.resources.ResourceDtos.DepartmentGoalActualRequest;
import com.easyperformance.resources.ResourceDtos.DepartmentGoalCopyRequest;
import com.easyperformance.resources.ResourceDtos.DepartmentGoalResponse;
import com.easyperformance.resources.ResourceDtos.DepartmentGoalTransferRequest;
import com.easyperformance.resources.ResourceDtos.DepartmentGoalUpsertRequest;
import com.easyperformance.resources.ResourceDtos.EmployeeOptionResponse;
import com.easyperformance.resources.ResourceDtos.InterviewCreateRequest;
import com.easyperformance.resources.ResourceDtos.InterviewResponse;
import com.easyperformance.resources.ResourceDtos.InterviewUpdateRequest;
import com.easyperformance.resources.ResourceDtos.InterviewView;
import com.easyperformance.resources.ResourceDtos.InterviewVisibilityRequest;
import com.easyperformance.resources.ResourceDtos.LabelApplyRequest;
import com.easyperformance.resources.ResourceDtos.LabelCreateRequest;
import com.easyperformance.resources.ResourceDtos.LabelResponse;
import com.easyperformance.resources.ResourceDtos.LookupOptionResponse;
import com.easyperformance.resources.ResourceDtos.TaskActivityRequest;
import com.easyperformance.resources.ResourceDtos.TaskCreateRequest;
import com.easyperformance.resources.ResourceDtos.TaskDetailResponse;
import com.easyperformance.resources.ResourceDtos.TaskFeedbackRequest;
import com.easyperformance.resources.ResourceDtos.TaskFeedbackResponse;
import com.easyperformance.resources.ResourceDtos.TaskProgressRequest;
import com.easyperformance.resources.ResourceDtos.TaskReopenRequest;
import com.easyperformance.resources.ResourceDtos.TaskStatusRequest;
import com.easyperformance.resources.ResourceDtos.TaskSummaryResponse;
import com.easyperformance.resources.ResourceDtos.TaskUpdateRequest;
import com.easyperformance.workflow.ActorAccess;
import com.easyperformance.workflow.ActorAccess.Actor;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evaluation-resources")
public class ResourceController {
    private static final Set<String> ADMIN_ROLES = Set.of("SUPER_ADMIN", "HR_ADMIN");
    private final ActorAccess actors; private final CatalogService catalogs; private final DepartmentGoalService goals;
    private final TaskService tasks; private final InterviewService interviews; private final ResourceLookupService lookups;

    public ResourceController(ActorAccess actors, CatalogService catalogs, DepartmentGoalService goals,
        TaskService tasks, InterviewService interviews, ResourceLookupService lookups) {
        this.actors = actors; this.catalogs = catalogs; this.goals = goals; this.tasks = tasks;
        this.interviews = interviews; this.lookups = lookups;
    }

    @GetMapping("/catalogs") public List<CatalogResponse> catalogs(@RequestParam(required = false) CatalogKind kind,
        @RequestParam(defaultValue = "false") boolean includeInactive) {
        Actor actor = actors.requireActor(); boolean admin = isAdmin(actor); return catalogs.list(actor.tenantId(), kind, admin && includeInactive);
    }
    @GetMapping("/catalogs/{id}") public CatalogResponse catalog(@PathVariable UUID id) {
        Actor actor = actors.requireActor(); return catalogs.get(actor.tenantId(), id, isAdmin(actor));
    }
    @PostMapping("/catalogs") public CatalogResponse createCatalog(@Valid @RequestBody CatalogUpsertRequest request) {
        Actor actor = admin(); return catalogs.create(actor.tenantId(), request);
    }
    @PutMapping("/catalogs/{id}") public CatalogResponse updateCatalog(@PathVariable UUID id, @Valid @RequestBody CatalogUpsertRequest request) {
        Actor actor = admin(); return catalogs.update(actor.tenantId(), id, request);
    }
    @PostMapping("/catalogs/{id}/copy") public CatalogResponse copyCatalog(@PathVariable UUID id, @Valid @RequestBody CatalogCopyRequest request) {
        Actor actor = admin(); return catalogs.copy(actor.tenantId(), id, request);
    }
    @PatchMapping("/catalogs/{id}/active") public CatalogResponse activeCatalog(@PathVariable UUID id, @RequestBody ActiveUpdateRequest request) {
        Actor actor = admin(); return catalogs.setActive(actor.tenantId(), id, request.active());
    }
    @DeleteMapping("/catalogs/{id}") public CatalogResponse archiveCatalog(@PathVariable UUID id) {
        Actor actor = admin(); return catalogs.setActive(actor.tenantId(), id, false);
    }

    @GetMapping("/department-goals") public List<DepartmentGoalResponse> goals(@RequestParam(required = false) UUID departmentId) {
        Actor actor = actors.requireEmployeeActor(); return goals.list(actor.tenantId(), actor.employeeId(), isAdmin(actor), departmentId);
    }
    @GetMapping("/department-goals/{id}") public DepartmentGoalResponse goal(@PathVariable UUID id) {
        Actor actor = actors.requireEmployeeActor(); return goals.get(actor.tenantId(), actor.employeeId(), isAdmin(actor), id);
    }
    @PostMapping("/department-goals") public DepartmentGoalResponse createGoal(@Valid @RequestBody DepartmentGoalUpsertRequest request) {
        Actor actor = admin(); return goals.create(actor.tenantId(), request);
    }
    @PutMapping("/department-goals/{id}") public DepartmentGoalResponse updateGoal(@PathVariable UUID id, @Valid @RequestBody DepartmentGoalUpsertRequest request) {
        Actor actor = admin(); return goals.update(actor.tenantId(), id, request);
    }
    @PostMapping("/department-goals/{id}/copy") public DepartmentGoalResponse copyGoal(@PathVariable UUID id, @Valid @RequestBody DepartmentGoalCopyRequest request) {
        Actor actor = admin(); return goals.copy(actor.tenantId(), id, request);
    }
    @PostMapping("/department-goals/{id}/transfer") public DepartmentGoalResponse transferGoal(@PathVariable UUID id, @Valid @RequestBody DepartmentGoalTransferRequest request) {
        Actor actor = admin(); return goals.transfer(actor.tenantId(), id, request);
    }
    @PostMapping("/department-goals/{id}/actual") public DepartmentGoalResponse goalActual(@PathVariable UUID id, @Valid @RequestBody DepartmentGoalActualRequest request) {
        Actor actor = admin(); return goals.actual(actor.tenantId(), id, request);
    }

    @GetMapping("/tasks") public List<TaskSummaryResponse> tasks() { return tasks.list(actors.requireEmployeeActor()); }
    @GetMapping("/tasks/{id}") public TaskDetailResponse task(@PathVariable UUID id) { return tasks.get(actors.requireEmployeeActor(), id); }
    @PostMapping("/tasks") public TaskDetailResponse createTask(@Valid @RequestBody TaskCreateRequest request) { return tasks.create(actors.requireEmployeeActor(), request); }
    @PutMapping("/tasks/{id}") public TaskDetailResponse updateTask(@PathVariable UUID id, @Valid @RequestBody TaskUpdateRequest request) { return tasks.update(actors.requireEmployeeActor(), id, request); }
    @PostMapping("/tasks/{id}/status") public TaskDetailResponse taskStatus(@PathVariable UUID id, @Valid @RequestBody TaskStatusRequest request) { return tasks.changeStatus(actors.requireEmployeeActor(), id, request); }
    @PostMapping("/tasks/{id}/reopen") public TaskDetailResponse reopenTask(@PathVariable UUID id, @Valid @RequestBody TaskReopenRequest request) { return tasks.reopen(actors.requireEmployeeActor(), id, request); }
    @PostMapping("/tasks/{id}/checklist") public ChecklistResponse addChecklist(@PathVariable UUID id, @Valid @RequestBody ChecklistCreateRequest request) { return tasks.addChecklist(actors.requireEmployeeActor(), id, request); }
    @PatchMapping("/tasks/{id}/checklist/{itemId}") public ChecklistResponse completeChecklist(@PathVariable UUID id, @PathVariable UUID itemId, @RequestBody ChecklistCompletionRequest request) { return tasks.completeChecklist(actors.requireEmployeeActor(), id, itemId, request); }
    @PostMapping("/tasks/{id}/progress") public TaskDetailResponse progress(@PathVariable UUID id, @Valid @RequestBody TaskProgressRequest request) { return tasks.updateProgress(actors.requireEmployeeActor(), id, request); }
    @PostMapping("/tasks/{id}/activities") public ResourceDtos.ActivityResponse activity(@PathVariable UUID id, @Valid @RequestBody TaskActivityRequest request) { return tasks.addActivity(actors.requireEmployeeActor(), id, request); }
    @GetMapping("/task-labels") public List<LabelResponse> labels() { return tasks.labels(actors.requireEmployeeActor()); }
    @PostMapping("/task-labels") public LabelResponse createLabel(@Valid @RequestBody LabelCreateRequest request) { return tasks.createLabel(actors.requireEmployeeActor(), request.name()); }
    @PostMapping("/tasks/{id}/labels") public TaskDetailResponse applyLabel(@PathVariable UUID id, @Valid @RequestBody LabelApplyRequest request) { return tasks.applyLabel(actors.requireEmployeeActor(), id, request.labelId()); }
    @DeleteMapping("/tasks/{id}/labels/{labelId}") public TaskDetailResponse removeLabel(@PathVariable UUID id, @PathVariable UUID labelId) { return tasks.removeLabel(actors.requireEmployeeActor(), id, labelId); }
    @PostMapping("/tasks/{id}/feedback") public TaskFeedbackResponse feedback(@PathVariable UUID id, @Valid @RequestBody TaskFeedbackRequest request) { return tasks.addFeedback(actors.requireEmployeeActor(), id, request); }
    @PostMapping(value = "/tasks/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse attachment(@PathVariable UUID id, @RequestPart("file") MultipartFile file) { return tasks.addAttachment(actors.requireEmployeeActor(), id, file); }
    @GetMapping("/tasks/{id}/attachments/{attachmentId}") public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        TaskAttachment attachment = tasks.download(actors.requireEmployeeActor(), id, attachmentId);
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.parseMediaType(attachment.getContentType()));
        headers.setContentLength(attachment.getSize()); headers.setContentDisposition(ContentDisposition.attachment()
            .filename(attachment.getFilename(), StandardCharsets.UTF_8).build()); headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(attachment.getContent());
    }

    @GetMapping("/interviews") public List<InterviewResponse> interviews(@RequestParam(defaultValue = "MINE") InterviewView view,
        @RequestParam(required = false) UUID employeeId) { return interviews.list(actors.requireEmployeeActor(), view, employeeId); }
    @GetMapping("/interviews/{id}") public InterviewResponse interview(@PathVariable UUID id) { return interviews.get(actors.requireEmployeeActor(), id); }
    @PostMapping("/interviews") public InterviewResponse createInterview(@Valid @RequestBody InterviewCreateRequest request) { return interviews.create(actors.requireEmployeeActor(), request); }
    @PutMapping("/interviews/{id}") public InterviewResponse updateInterview(@PathVariable UUID id, @Valid @RequestBody InterviewUpdateRequest request) { return interviews.update(actors.requireEmployeeActor(), id, request); }
    @PatchMapping("/interviews/{id}/visibility") public InterviewResponse visibility(@PathVariable UUID id, @RequestBody InterviewVisibilityRequest request) { return interviews.visibility(actors.requireEmployeeActor(), id, request); }

    @GetMapping("/lookup/employees") public List<EmployeeOptionResponse> employees(@RequestParam(defaultValue = "") String q) { Actor a = actors.requireEmployeeActor(); return lookups.employees(a.tenantId(), q); }
    @GetMapping("/lookup/employees/{employeeId}/assignments") public List<LookupOptionResponse> assignments(@PathVariable UUID employeeId) {
        Actor a = actors.requireEmployeeActor(); return lookups.assignments(a.tenantId(), employeeId);
    }
    @GetMapping("/lookup/departments") public List<LookupOptionResponse> departments(@RequestParam(defaultValue = "") String q) { Actor a = actors.requireEmployeeActor(); return lookups.departments(a.tenantId(), q); }
    @GetMapping("/lookup/jobs") public List<LookupOptionResponse> jobs(@RequestParam(defaultValue = "") String q) { Actor a = actors.requireEmployeeActor(); return lookups.jobs(a.tenantId(), q); }
    @GetMapping("/lookup/condition-values") public List<LookupOptionResponse> conditionValues(
        @RequestParam String field, @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) List<String> values) {
        Actor a = actors.requireEmployeeActor(); return lookups.conditionValues(a.tenantId(), field, q, values);
    }

    private Actor admin() { return actors.requireAnyRole("SUPER_ADMIN", "HR_ADMIN"); }
    private static boolean isAdmin(Actor actor) { return ADMIN_ROLES.contains(actor.role()); }
}

package com.easyperformance.resources;

import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.resources.ResourceDtos.InterviewAuditAction;
import com.easyperformance.resources.ResourceDtos.InterviewAuditResponse;
import com.easyperformance.resources.ResourceDtos.InterviewCreateRequest;
import com.easyperformance.resources.ResourceDtos.InterviewResponse;
import com.easyperformance.resources.ResourceDtos.InterviewUpdateRequest;
import com.easyperformance.resources.ResourceDtos.InterviewView;
import com.easyperformance.resources.ResourceDtos.InterviewVisibilityRequest;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class InterviewService {
    private final InterviewRecordRepository interviews;
    private final InterviewReferenceRepository references;
    private final InterviewAuditRepository audits;
    private final RmEmployeeRepository employees;

    public InterviewService(InterviewRecordRepository interviews, InterviewReferenceRepository references,
                            InterviewAuditRepository audits, RmEmployeeRepository employees) {
        this.interviews = interviews; this.references = references; this.audits = audits; this.employees = employees;
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> list(Actor actor, InterviewView view, UUID employeeId) {
        List<InterviewRecord> candidates = switch (view) {
            case AUTHORED -> interviews.findAllByTenantIdAndAuthorEmployeeIdOrderByOccurredAtDesc(actor.tenantId(), actor.employeeId());
            case MINE -> interviews.findAllByTenantIdAndSubjectEmployeeIdOrderByOccurredAtDesc(actor.tenantId(), actor.employeeId());
            case REFERENCED -> byReference(actor.tenantId(), actor.employeeId());
            case BY_EMPLOYEE -> byEmployee(actor.tenantId(), requireEmployeeId(employeeId));
        };
        return candidates.stream().filter(record -> canView(actor, record)).map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public InterviewResponse get(Actor actor, UUID id) {
        InterviewRecord record = require(actor.tenantId(), id);
        if (!canView(actor, record)) throw new ApiException(ResourceErrorCode.RESOURCE_FORBIDDEN);
        return response(record);
    }

    @Transactional
    public InterviewResponse create(Actor actor, InterviewCreateRequest request) {
        requireActive(actor.tenantId(), request.subjectEmployeeId());
        LinkedHashSet<UUID> referenceIds = new LinkedHashSet<>(safe(request.referenceEmployeeIds()));
        referenceIds.forEach(id -> requireActive(actor.tenantId(), id));
        InterviewRecord record = new InterviewRecord(); record.setTenantId(actor.tenantId());
        record.setAuthorEmployeeId(actor.employeeId()); record.setSubjectEmployeeId(request.subjectEmployeeId());
        record.setOccurredAt(request.occurredAt()); record.setSummary(request.summary().trim());
        record.setKeyIssues(trim(request.keyIssues())); record.setRequests(trim(request.requests())); record.setFollowUp(trim(request.followUp()));
        record.setSubjectVisible(request.subjectVisible()); record.setReferencesVisible(request.referencesVisible());
        record = interviews.save(record); replaceReferences(record, referenceIds);
        audit(actor, record, InterviewAuditAction.CREATED); return response(record);
    }

    @Transactional
    public InterviewResponse update(Actor actor, UUID id, InterviewUpdateRequest request) {
        InterviewRecord record = requireAuthor(actor, id); record.setOccurredAt(request.occurredAt());
        record.setSummary(request.summary().trim()); record.setKeyIssues(trim(request.keyIssues()));
        record.setRequests(trim(request.requests())); record.setFollowUp(trim(request.followUp()));
        if (request.referenceEmployeeIds() != null) {
            LinkedHashSet<UUID> referenceIds = new LinkedHashSet<>(request.referenceEmployeeIds());
            referenceIds.forEach(employeeId -> requireActive(actor.tenantId(), employeeId));
            replaceReferences(record, referenceIds);
        }
        audit(actor, record, InterviewAuditAction.UPDATED); return response(record);
    }

    @Transactional
    public InterviewResponse visibility(Actor actor, UUID id, InterviewVisibilityRequest request) {
        InterviewRecord record = requireAuthor(actor, id); record.setSubjectVisible(request.subjectVisible());
        record.setReferencesVisible(request.referencesVisible()); audit(actor, record, InterviewAuditAction.VISIBILITY_CHANGED);
        return response(record);
    }

    private InterviewRecord requireAuthor(Actor actor, UUID id) {
        InterviewRecord record = require(actor.tenantId(), id);
        if (!record.getAuthorEmployeeId().equals(actor.employeeId())) throw new ApiException(ResourceErrorCode.RESOURCE_FORBIDDEN);
        return record;
    }

    private boolean canView(Actor actor, InterviewRecord record) {
        if (record.getAuthorEmployeeId().equals(actor.employeeId())) return true;
        if (record.isSubjectVisible() && record.getSubjectEmployeeId().equals(actor.employeeId())) return true;
        return record.isReferencesVisible() && references.findAllByTenantIdAndInterviewId(actor.tenantId(), record.getId()).stream()
            .anyMatch(reference -> reference.getEmployeeId().equals(actor.employeeId()));
    }

    private List<InterviewRecord> byReference(UUID tenantId, UUID employeeId) {
        return references.findAllByTenantIdAndEmployeeId(tenantId, employeeId).stream()
            .map(InterviewReference::getInterviewId).distinct().map(id -> interviews.findByIdAndTenantId(id, tenantId).orElse(null))
            .filter(java.util.Objects::nonNull).sorted(java.util.Comparator.comparing(InterviewRecord::getOccurredAt).reversed()).toList();
    }

    private List<InterviewRecord> byEmployee(UUID tenantId, UUID employeeId) {
        LinkedHashMap<UUID, InterviewRecord> result = new LinkedHashMap<>();
        interviews.findAllByTenantIdAndSubjectEmployeeIdOrderByOccurredAtDesc(tenantId, employeeId).forEach(r -> result.put(r.getId(), r));
        interviews.findAllByTenantIdAndAuthorEmployeeIdOrderByOccurredAtDesc(tenantId, employeeId).forEach(r -> result.put(r.getId(), r));
        byReference(tenantId, employeeId).forEach(r -> result.put(r.getId(), r));
        return result.values().stream().sorted(java.util.Comparator.comparing(InterviewRecord::getOccurredAt).reversed()).toList();
    }

    private void replaceReferences(InterviewRecord record, java.util.Collection<UUID> employeeIds) {
        references.deleteAllByTenantIdAndInterviewId(record.getTenantId(), record.getId());
        references.flush();
        List<InterviewReference> entities = new ArrayList<>();
        for (UUID employeeId : employeeIds) {
            InterviewReference ref = new InterviewReference(); ref.setTenantId(record.getTenantId());
            ref.setInterviewId(record.getId()); ref.setEmployeeId(employeeId); entities.add(ref);
        }
        references.saveAll(entities);
    }

    private void audit(Actor actor, InterviewRecord record, InterviewAuditAction action) {
        InterviewAuditEvent event = new InterviewAuditEvent(); event.setTenantId(actor.tenantId()); event.setInterviewId(record.getId());
        event.setActorEmployeeId(actor.employeeId()); event.setAction(action); event.setSubjectVisible(record.isSubjectVisible());
        event.setReferencesVisible(record.isReferencesVisible()); audits.save(event);
    }

    private InterviewResponse response(InterviewRecord record) {
        List<UUID> referenceIds = references.findAllByTenantIdAndInterviewId(record.getTenantId(), record.getId()).stream()
            .map(InterviewReference::getEmployeeId).toList();
        List<InterviewAuditResponse> trail = audits.findAllByTenantIdAndInterviewIdOrderByCreatedAtAsc(record.getTenantId(), record.getId()).stream()
            .map(a -> new InterviewAuditResponse(a.getId(), a.getAction(), a.getActorEmployeeId(), a.isSubjectVisible(),
                a.isReferencesVisible(), a.getCreatedAt())).toList();
        return new InterviewResponse(record.getId(), record.getAuthorEmployeeId(), record.getSubjectEmployeeId(), record.getOccurredAt(),
            record.getSummary(), record.getKeyIssues(), record.getRequests(), record.getFollowUp(),
            record.isSubjectVisible(), record.isReferencesVisible(), referenceIds, record.getRowVersion(),
            record.getCreatedAt(), record.getUpdatedAt(), trail);
    }

    private InterviewRecord require(UUID tenantId, UUID id) {
        return interviews.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_NOT_FOUND));
    }
    private void requireActive(UUID tenantId, UUID employeeId) {
        employees.findByIdAndTenantId(employeeId, tenantId).filter(e -> "ACTIVE".equalsIgnoreCase(e.getStatus()))
            .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_INVALID));
    }
    private static UUID requireEmployeeId(UUID id) { if (id == null) throw new ApiException(ResourceErrorCode.RESOURCE_INVALID); return id; }
    private static List<UUID> safe(List<UUID> ids) { return ids == null ? List.of() : ids; }
    private static String trim(String value) { return value == null ? null : value.trim(); }
}

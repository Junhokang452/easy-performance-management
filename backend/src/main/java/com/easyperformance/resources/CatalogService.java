package com.easyperformance.resources;

import com.easyperformance.resources.ResourceDtos.AchievementLevelInput;
import com.easyperformance.resources.ResourceDtos.AchievementLevelResponse;
import com.easyperformance.resources.ResourceDtos.CatalogAssignmentInput;
import com.easyperformance.resources.ResourceDtos.CatalogAssignmentResponse;
import com.easyperformance.resources.ResourceDtos.CatalogCopyRequest;
import com.easyperformance.resources.ResourceDtos.CatalogKind;
import com.easyperformance.resources.ResourceDtos.CatalogResponse;
import com.easyperformance.resources.ResourceDtos.CatalogUpsertRequest;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CatalogService {
    private final EvaluationCatalogRepository catalogs;
    private final CatalogLevelRepository levels;
    private final CatalogAssignmentRepository assignments;
    private final ResourceLookupService lookups;

    public CatalogService(EvaluationCatalogRepository catalogs, CatalogLevelRepository levels,
                          CatalogAssignmentRepository assignments, ResourceLookupService lookups) {
        this.catalogs = catalogs; this.levels = levels; this.assignments = assignments; this.lookups = lookups;
    }

    @Transactional(readOnly = true)
    public List<CatalogResponse> list(UUID tenantId, CatalogKind kind, boolean includeInactive) {
        List<EvaluationCatalogItem> found = kind == null
            ? catalogs.findAllByTenantIdOrderByKindAscDisplayOrderAscNameAsc(tenantId)
            : catalogs.findAllByTenantIdAndKindOrderByDisplayOrderAscNameAsc(tenantId, kind);
        return found.stream().filter(c -> includeInactive || c.isActive()).map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public CatalogResponse get(UUID tenantId, UUID id, boolean includeInactive) {
        EvaluationCatalogItem item = require(tenantId, id);
        if (!includeInactive && !item.isActive()) throw new ApiException(ResourceErrorCode.RESOURCE_NOT_FOUND);
        return response(item);
    }

    @Transactional
    public CatalogResponse create(UUID tenantId, CatalogUpsertRequest request) {
        validate(tenantId, request);
        EvaluationCatalogItem item = new EvaluationCatalogItem();
        item.setTenantId(tenantId);
        apply(item, request);
        item = catalogs.save(item);
        replaceChildren(item, request.achievementLevels(), request.assignments());
        return response(item);
    }

    @Transactional
    public CatalogResponse update(UUID tenantId, UUID id, CatalogUpsertRequest request) {
        validate(tenantId, request);
        EvaluationCatalogItem item = require(tenantId, id);
        apply(item, request);
        replaceChildren(item, request.achievementLevels(), request.assignments());
        return response(item);
    }

    @Transactional
    public CatalogResponse copy(UUID tenantId, UUID id, CatalogCopyRequest request) {
        EvaluationCatalogItem source = require(tenantId, id);
        EvaluationCatalogItem target = new EvaluationCatalogItem();
        target.setTenantId(tenantId); target.setKind(source.getKind()); target.setCategory(source.getCategory());
        target.setName(request.name().trim()); target.setDefinition(source.getDefinition()); target.setActive(false);
        target.setDisplayOrder(source.getDisplayOrder()); target.setCopiedFromId(source.getId());
        target = catalogs.save(target);
        List<AchievementLevelInput> copiedLevels = levels.findAllByTenantIdAndCatalogIdOrderByDisplayOrderAsc(tenantId, id)
            .stream().map(l -> new AchievementLevelInput(l.getCode(), l.getLabel(), l.getMinValue(), l.getMaxValue(),
                l.getDescription(), l.getDisplayOrder())).toList();
        List<CatalogAssignmentInput> copiedAssignments = assignments.findAllByTenantIdAndCatalogIdOrderByTypeAscReferenceAsc(tenantId, id)
            .stream().map(a -> new CatalogAssignmentInput(a.getType(), a.getReference())).toList();
        replaceChildren(target, copiedLevels, copiedAssignments);
        return response(target);
    }

    @Transactional
    public CatalogResponse setActive(UUID tenantId, UUID id, boolean active) {
        EvaluationCatalogItem item = require(tenantId, id);
        item.setActive(active);
        return response(item);
    }

    EvaluationCatalogItem require(UUID tenantId, UUID id) {
        return catalogs.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ApiException(ResourceErrorCode.RESOURCE_NOT_FOUND));
    }

    private void apply(EvaluationCatalogItem item, CatalogUpsertRequest r) {
        item.setKind(r.kind()); item.setCategory(r.category().trim()); item.setName(r.name().trim());
        item.setDefinition(r.definition().trim()); item.setActive(r.active()); item.setDisplayOrder(r.displayOrder());
    }

    private void replaceChildren(EvaluationCatalogItem item, List<AchievementLevelInput> levelInputs,
                                 List<CatalogAssignmentInput> assignmentInputs) {
        levels.deleteAllByTenantIdAndCatalogId(item.getTenantId(), item.getId());
        assignments.deleteAllByTenantIdAndCatalogId(item.getTenantId(), item.getId());
        levels.flush();
        assignments.flush();
        List<CatalogAchievementLevel> levelEntities = new ArrayList<>();
        for (AchievementLevelInput input : safe(levelInputs)) {
            CatalogAchievementLevel level = new CatalogAchievementLevel(); level.setTenantId(item.getTenantId());
            level.setCatalogId(item.getId()); level.setCode(input.code().trim()); level.setLabel(input.label().trim());
            level.setMinValue(input.minValue()); level.setMaxValue(input.maxValue()); level.setDescription(trim(input.description()));
            level.setDisplayOrder(input.displayOrder()); levelEntities.add(level);
        }
        levels.saveAll(levelEntities);
        List<CatalogAssignment> assignmentEntities = new ArrayList<>();
        for (CatalogAssignmentInput input : safe(assignmentInputs)) {
            CatalogAssignment assignment = new CatalogAssignment(); assignment.setTenantId(item.getTenantId());
            assignment.setCatalogId(item.getId()); assignment.setType(input.type());
            assignment.setReference(input.reference().trim()); assignmentEntities.add(assignment);
        }
        assignments.saveAll(assignmentEntities);
    }

    private void validate(UUID tenantId, CatalogUpsertRequest request) {
        if (safe(request.achievementLevels()).isEmpty()) throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
        Set<String> codes = new HashSet<>();
        for (AchievementLevelInput level : safe(request.achievementLevels())) {
            if (!codes.add(level.code().trim().toUpperCase())
                || (level.minValue() != null && level.maxValue() != null && level.minValue().compareTo(level.maxValue()) > 0)) {
                throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
            }
        }
        Set<String> refs = new HashSet<>();
        for (CatalogAssignmentInput assignment : safe(request.assignments())) {
            if (!refs.add(assignment.type() + ":" + assignment.reference().trim())) {
                throw new ApiException(ResourceErrorCode.RESOURCE_INVALID);
            }
            lookups.requireAssignmentReference(tenantId, assignment.type(), assignment.reference().trim());
        }
    }

    private CatalogResponse response(EvaluationCatalogItem item) {
        var levelResponses = levels.findAllByTenantIdAndCatalogIdOrderByDisplayOrderAsc(item.getTenantId(), item.getId()).stream()
            .map(l -> new AchievementLevelResponse(l.getId(), l.getCode(), l.getLabel(), l.getMinValue(), l.getMaxValue(),
                l.getDescription(), l.getDisplayOrder())).toList();
        var assignmentResponses = assignments.findAllByTenantIdAndCatalogIdOrderByTypeAscReferenceAsc(item.getTenantId(), item.getId()).stream()
            .map(a -> new CatalogAssignmentResponse(a.getId(), a.getType(), a.getReference())).toList();
        return new CatalogResponse(item.getId(), item.getKind(), item.getCategory(), item.getName(), item.getDefinition(),
            item.isActive(), item.getDisplayOrder(), item.getCopiedFromId(), levelResponses, assignmentResponses,
            item.getRowVersion(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private static <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
    private static String trim(String value) { return value == null ? null : value.trim(); }
}

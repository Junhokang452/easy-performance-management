package com.easyperformance.workflow;

import com.easyware.platform.UuidV7;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.workflow.ParticipantRosterDtos.EmployeeSummary;
import com.easyperformance.workflow.ParticipantRosterDtos.ParticipantInput;
import com.easyperformance.workflow.ParticipantRosterDtos.ParticipantReplaceRequest;
import com.easyperformance.workflow.ParticipantRosterDtos.ParticipantResponse;
import com.easyperformance.workflow.ParticipantRosterDtos.ParticipantRosterResponse;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ParticipantRosterService {
    private final EvaluationCycleRepository cycles;
    private final EvaluationParticipantRepository participants;
    private final EvaluationReviewerAssignmentRepository reviewers;
    private final RmEmployeeRepository employees;

    public ParticipantRosterService(EvaluationCycleRepository cycles,
                                    EvaluationParticipantRepository participants,
                                    EvaluationReviewerAssignmentRepository reviewers,
                                    RmEmployeeRepository employees) {
        this.cycles = cycles;
        this.participants = participants;
        this.reviewers = reviewers;
        this.employees = employees;
    }

    @Transactional
    public ParticipantRosterResponse replaceRoster(UUID tenantId, UUID cycleId, ParticipantReplaceRequest request) {
        EvaluationCycle cycle = cycles.findByIdAndTenantId(cycleId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.CYCLE_NOT_FOUND));
        if (cycle.getStatus() != CycleStatus.PLANNED && cycle.getStatus() != CycleStatus.ACTIVE) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_PHASE_BLOCKED,
                Map.of("cycleId", cycleId, "status", cycle.getStatus().name(), "operation", "replace-roster"));
        }
        List<EvaluationParticipant> existingParticipants =
            participants.findAllByTenantIdAndCycleIdOrderByCreatedAtAsc(tenantId, cycleId);
        Set<UUID> requestedEmployeeIds = request.participants().stream()
            .map(ParticipantInput::employeeId).collect(Collectors.toSet());
        for (EvaluationParticipant existing : existingParticipants) {
            if (!requestedEmployeeIds.contains(existing.getEmployeeId())
                && existing.getStatus() != ParticipantStatus.EXCLUDED) {
                existing.setStatus(ParticipantStatus.EXCLUDED);
                existing.setExclusionReason("Removed from roster replacement");
                participants.save(existing);
                for (EvaluationReviewerAssignment reviewer : reviewers
                    .findAllByTenantIdAndParticipantIdAndStatusNot(
                        tenantId, existing.getId(), ReviewerAssignmentStatus.REVOKED)) {
                    reviewer.setStatus(ReviewerAssignmentStatus.REVOKED);
                    reviewers.save(reviewer);
                }
            }
        }
        List<ParticipantResponse> result = new ArrayList<>();
        for (ParticipantInput input : request.participants()) {
            RmEmployee employee = requireActiveEmployee(tenantId, input.employeeId());
            RmEmployee manager = requireActiveEmployee(tenantId, input.managerEmployeeId());
            EvaluationParticipant participant = participants
                .findByTenantIdAndCycleIdAndEmployeeId(tenantId, cycleId, input.employeeId())
                .orElseGet(EvaluationParticipant::new);
            if (participant.getId() == null) participant.setId(UuidV7.generate());
            participant.setTenantId(tenantId);
            participant.setCycleId(cycleId);
            participant.setEmployeeId(input.employeeId());
            participant.setStatus(ParticipantStatus.ACTIVE);
            participant.setExclusionReason(null);
            participant = participants.save(participant);

            EvaluationReviewerAssignment reviewer = reviewers
                .findByTenantIdAndParticipantIdAndReviewerTypeAndRound(
                    tenantId, participant.getId(), ReviewerType.MANAGER, 1)
                .orElseGet(EvaluationReviewerAssignment::new);
            if (reviewer.getId() == null) reviewer.setId(UuidV7.generate());
            reviewer.setTenantId(tenantId);
            reviewer.setCycleId(cycleId);
            reviewer.setParticipantId(participant.getId());
            reviewer.setReviewerEmployeeId(manager.getId());
            reviewer.setReviewerType(ReviewerType.MANAGER);
            reviewer.setRound(1);
            reviewer.setWeight(BigDecimal.ONE);
            reviewer.setStatus(ReviewerAssignmentStatus.ASSIGNED);
            reviewers.save(reviewer);
            result.add(new ParticipantResponse(participant.getId(), cycleId, summary(employee),
                participant.getStatus(), summary(manager), null, null));
        }
        long excluded = existingParticipants.stream()
            .filter(p -> !requestedEmployeeIds.contains(p.getEmployeeId())).count();
        return new ParticipantRosterResponse(cycleId, result.size(), excluded, 0, result);
    }

    private RmEmployee requireActiveEmployee(UUID tenantId, UUID employeeId) {
        RmEmployee employee = employees.findByIdAndTenantId(employeeId, tenantId)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.PARTICIPANT_EMPLOYEE_INVALID,
                Map.of("employeeId", employeeId, "reason", "not-found")));
        if (!"ACTIVE".equalsIgnoreCase(employee.getStatus())) {
            throw new ApiException(PerformanceErrorCode.PARTICIPANT_EMPLOYEE_INVALID,
                Map.of("employeeId", employeeId, "reason", "inactive"));
        }
        return employee;
    }

    static EmployeeSummary summary(RmEmployee employee) {
        return new EmployeeSummary(employee.getId(), employee.getEmployeeNo(), employee.getName(),
            employee.getOrgUnitId(), null, employee.getStatus(), false);
    }
}

package com.easyperformance.workflow;

import com.easyware.platform.UuidV7;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiAssignmentResponse;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiNodeResponse;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiTreeResponse;
import com.easyperformance.domain.kpi.dto.KpiDtos.MyKpiAssignmentResponse;
import com.easyperformance.domain.kpi.dto.KpiDtos.KpiActualResponse;
import com.easyperformance.domain.kpi.entity.KpiActualSource;
import com.easyperformance.domain.kpi.entity.KpiNodeSource;
import com.easyperformance.domain.kpi.entity.KpiTreeLevel;
import com.easyperformance.domain.kpi.service.KpiService;
import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.GoalCreateRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.GoalUpdateRequest;
import com.easyperformance.workflow.EvaluationWorkspaceDtos.CheckInCreateRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoalAgreementServiceTest {

    @Test
    void createGoal_forSelfParticipant_createsKpiBackedDraft() {
        UUID tenantId = UuidV7.generate();
        UUID cycleId = UuidV7.generate();
        UUID employeeId = UuidV7.generate();
        UUID participantId = UuidV7.generate();
        EvaluationParticipant participant = new EvaluationParticipant();
        participant.setId(participantId);
        participant.setTenantId(tenantId);
        participant.setCycleId(cycleId);
        participant.setEmployeeId(employeeId);
        participant.setStatus(ParticipantStatus.ACTIVE);
        EvaluationParticipantRepository participants = mock(EvaluationParticipantRepository.class);
        EvaluationReviewerAssignmentRepository reviewers = mock(EvaluationReviewerAssignmentRepository.class);
        EvaluationGoalRepository goals = mock(EvaluationGoalRepository.class);
        KpiService kpis = mock(KpiService.class);
        EvaluationCycleRepository cycles = mock(EvaluationCycleRepository.class);
        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setId(cycleId); cycle.setTenantId(tenantId); cycle.setStatus(CycleStatus.GOAL_SETTING);
        when(cycles.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(participants.findByTenantIdAndCycleIdAndEmployeeId(tenantId, cycleId, employeeId))
            .thenReturn(Optional.of(participant));
        UUID treeId = UuidV7.generate();
        UUID nodeId = UuidV7.generate();
        UUID assignmentId = UuidV7.generate();
        when(kpis.createTree(eq(cycleId), any())).thenReturn(new KpiTreeResponse(
            treeId, cycleId, "Goals", KpiTreeLevel.INDIVIDUAL, null, false, null, null));
        when(kpis.createNode(eq(treeId), any())).thenReturn(new KpiNodeResponse(
            nodeId, treeId, null, "Improve", BigDecimal.ONE, BigDecimal.TEN, "%", null,
            KpiNodeSource.MANUAL, null, BigDecimal.ZERO, false, 0, null, null));
        when(kpis.createAssignment(eq(nodeId), any())).thenReturn(new KpiAssignmentResponse(
            assignmentId, nodeId, employeeId, BigDecimal.ONE, BigDecimal.TEN, null, null));
        when(goals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GoalAgreementService service = new GoalAgreementService(participants, reviewers, goals, kpis, cycles,
            mock(EvaluationGoalCheckInRepository.class));
        ActorAccess.Actor actor = new ActorAccess.Actor(UuidV7.generate(), tenantId, employeeId, "Employee", "EMPLOYEE");

        EvaluationWorkspaceDtos.GoalResponse response = service.create(actor, cycleId,
            new GoalCreateRequest(null, "Improve", "description", BigDecimal.ONE, BigDecimal.TEN, "%"));

        assertThat(response.employeeId()).isEqualTo(employeeId);
        assertThat(response.status()).isEqualTo(GoalStatus.DRAFT);
        assertThat(response.kpiAssignmentId()).isEqualTo(assignmentId);
    }

    @Test
    void updateRejectedGoal_returnsItToDraftForResubmission() {
        UUID tenantId = UuidV7.generate();
        UUID employeeId = UuidV7.generate();
        EvaluationGoalRepository goals = mock(EvaluationGoalRepository.class);
        EvaluationGoal goal = new EvaluationGoal();
        goal.setId(UuidV7.generate());
        goal.setTenantId(tenantId);
        goal.setCycleId(UuidV7.generate());
        goal.setEmployeeId(employeeId);
        goal.setStatus(GoalStatus.REJECTED);
        goal.setTitle("Old");
        when(goals.findByIdAndTenantId(goal.getId(), tenantId)).thenReturn(Optional.of(goal));
        when(goals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        EvaluationCycleRepository cycles = mock(EvaluationCycleRepository.class);
        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setId(goal.getCycleId()); cycle.setTenantId(tenantId); cycle.setStatus(CycleStatus.GOAL_SETTING);
        when(cycles.findByIdAndTenantId(goal.getCycleId(), tenantId)).thenReturn(Optional.of(cycle));
        GoalAgreementService service = new GoalAgreementService(mock(EvaluationParticipantRepository.class),
            mock(EvaluationReviewerAssignmentRepository.class), goals, mock(KpiService.class), cycles,
            mock(EvaluationGoalCheckInRepository.class));
        ActorAccess.Actor actor = new ActorAccess.Actor(UuidV7.generate(), tenantId, employeeId, "Employee", "EMPLOYEE");

        var result = service.update(actor, goal.getId(), new GoalUpdateRequest("Revised", null, null, null, null));

        assertThat(result.status()).isEqualTo(GoalStatus.DRAFT);
        assertThat(result.title()).isEqualTo("Revised");
    }

    @Test
    void listGoals_rehydratesEffectiveKpiValues() {
        UUID tenantId = UuidV7.generate();
        UUID cycleId = UuidV7.generate();
        UUID employeeId = UuidV7.generate();
        UUID assignmentId = UuidV7.generate();
        EvaluationGoalRepository goals = mock(EvaluationGoalRepository.class);
        KpiService kpis = mock(KpiService.class);
        EvaluationGoal goal = new EvaluationGoal();
        goal.setId(UuidV7.generate()); goal.setTenantId(tenantId); goal.setCycleId(cycleId);
        goal.setEmployeeId(employeeId); goal.setKpiAssignmentId(assignmentId);
        goal.setTitle("Improve"); goal.setStatus(GoalStatus.APPROVED);
        when(goals.findAllByTenantIdAndCycleIdAndEmployeeIdOrderByCreatedAtAsc(tenantId, cycleId, employeeId))
            .thenReturn(List.of(goal));
        when(kpis.listMyAssignments(cycleId, employeeId)).thenReturn(List.of(new MyKpiAssignmentResponse(
            assignmentId, UuidV7.generate(), "Improve", UuidV7.generate(), "Goals", cycleId,
            new BigDecimal("0.40"), new BigDecimal("125"), "cases", null, KpiNodeSource.MANUAL,
            null, null, null)));
        GoalAgreementService service = new GoalAgreementService(mock(EvaluationParticipantRepository.class),
            mock(EvaluationReviewerAssignmentRepository.class), goals, kpis, mock(EvaluationCycleRepository.class),
            mock(EvaluationGoalCheckInRepository.class));

        var response = service.list(tenantId, cycleId, employeeId).getFirst();

        assertThat(response.weight()).isEqualByComparingTo("0.40");
        assertThat(response.target()).isEqualByComparingTo("125");
        assertThat(response.unit()).isEqualTo("cases");
    }

    @Test
    void checkIn_persistsProgressAndReturnsItAfterReload() {
        UUID tenantId = UuidV7.generate();
        UUID cycleId = UuidV7.generate();
        UUID employeeId = UuidV7.generate();
        UUID participantId = UuidV7.generate();
        UUID goalId = UuidV7.generate();
        UUID assignmentId = UuidV7.generate();
        UUID actualId = UuidV7.generate();
        LocalDate asOfDate = LocalDate.of(2026, 9, 7);
        Instant createdAt = Instant.parse("2026-09-07T06:00:00Z");

        EvaluationGoal goal = new EvaluationGoal();
        goal.setId(goalId); goal.setTenantId(tenantId); goal.setCycleId(cycleId);
        goal.setParticipantId(participantId); goal.setEmployeeId(employeeId);
        goal.setKpiAssignmentId(assignmentId); goal.setStatus(GoalStatus.APPROVED);
        EvaluationParticipant participant = new EvaluationParticipant();
        participant.setId(participantId); participant.setTenantId(tenantId); participant.setCycleId(cycleId);
        participant.setEmployeeId(employeeId); participant.setStatus(ParticipantStatus.ACTIVE);
        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setId(cycleId); cycle.setTenantId(tenantId); cycle.setStatus(CycleStatus.MID_REVIEW);

        EvaluationGoalRepository goals = mock(EvaluationGoalRepository.class);
        EvaluationParticipantRepository participants = mock(EvaluationParticipantRepository.class);
        EvaluationCycleRepository cycles = mock(EvaluationCycleRepository.class);
        EvaluationGoalCheckInRepository checkIns = mock(EvaluationGoalCheckInRepository.class);
        KpiService kpis = mock(KpiService.class);
        KpiActualResponse actual = new KpiActualResponse(actualId, assignmentId, asOfDate,
            new BigDecimal("96"), KpiActualSource.MANUAL, employeeId, "https://evidence.example",
            "On track", null, false, createdAt);

        when(goals.findByIdAndTenantId(goalId, tenantId)).thenReturn(Optional.of(goal));
        when(cycles.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(participants.findByIdAndTenantId(participantId, tenantId)).thenReturn(Optional.of(participant));
        when(kpis.createActual(eq(assignmentId), any())).thenReturn(actual);
        when(kpis.listActuals(assignmentId)).thenReturn(List.of(actual));
        when(checkIns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(checkIns.findAllByTenantIdAndGoalId(tenantId, goalId)).thenAnswer(invocation -> {
            EvaluationGoalCheckIn metadata = new EvaluationGoalCheckIn();
            metadata.setKpiActualId(actualId); metadata.setTenantId(tenantId); metadata.setGoalId(goalId);
            metadata.setProgressPercent(new BigDecimal("96"));
            return List.of(metadata);
        });
        GoalAgreementService service = new GoalAgreementService(participants,
            mock(EvaluationReviewerAssignmentRepository.class), goals, kpis, cycles, checkIns);
        ActorAccess.Actor actor = new ActorAccess.Actor(UuidV7.generate(), tenantId, employeeId,
            "Employee", "EMPLOYEE");

        var created = service.checkIn(actor, goalId, new CheckInCreateRequest(asOfDate,
            new BigDecimal("96"), new BigDecimal("96"), "On track", "https://evidence.example"));
        var reloaded = service.checkIns(actor, goalId).getFirst();

        assertThat(created.progressPercent()).isEqualByComparingTo("96");
        assertThat(reloaded.progressPercent()).isEqualByComparingTo("96");
        var metadataCaptor = org.mockito.ArgumentCaptor.forClass(EvaluationGoalCheckIn.class);
        verify(checkIns).save(metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().getKpiActualId()).isEqualTo(actualId);
        assertThat(metadataCaptor.getValue().getProgressPercent()).isEqualByComparingTo("96");
    }

    @Test
    void checkInProgress_validationAcceptsBoundariesAndRejectsOutsideRange() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            LocalDate date = LocalDate.of(2026, 9, 7);

            assertThat(validator.validate(new CheckInCreateRequest(date, BigDecimal.ONE,
                BigDecimal.ZERO, null, null))).isEmpty();
            assertThat(validator.validate(new CheckInCreateRequest(date, BigDecimal.ONE,
                new BigDecimal("100"), null, null))).isEmpty();
            assertThat(validator.validate(new CheckInCreateRequest(date, BigDecimal.ONE,
                new BigDecimal("-0.01"), null, null))).extracting(v -> v.getPropertyPath().toString())
                .contains("progressPercent");
            assertThat(validator.validate(new CheckInCreateRequest(date, BigDecimal.ONE,
                new BigDecimal("100.01"), null, null))).extracting(v -> v.getPropertyPath().toString())
                .contains("progressPercent");
        }
    }
}

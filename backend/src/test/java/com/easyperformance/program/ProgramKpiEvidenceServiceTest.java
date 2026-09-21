package com.easyperformance.program;

import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.domain.evaluationcycle.entity.CycleType;
import com.easyperformance.domain.evaluationcycle.entity.EvaluationCycle;
import com.easyperformance.domain.evaluationcycle.repository.EvaluationCycleRepository;
import com.easyperformance.domain.kpi.entity.*;
import com.easyperformance.domain.kpi.repository.*;
import com.easyperformance.program.ProgramKpiDtos.*;
import com.easyperformance.program.ProgramTypes.*;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgramKpiEvidenceServiceTest {
    @Mock EvaluationProgramRepository programs; @Mock ProgramParticipantRepository participants;
    @Mock ProgramGoalRepository goals; @Mock ProgramReviewSubmissionRepository submissions;
    @Mock ProgramCalculationRepository calculations; @Mock ProgramKpiEvidenceRepository evidence;
    @Mock KpiAssignmentRepository assignments; @Mock KpiNodeRepository nodes; @Mock KpiTreeRepository trees;
    @Mock KpiActualRepository actuals; @Mock EvaluationCycleRepository cycles; @Mock ProgramAccess access;
    @Mock ProgramAuditService audit;
    ProgramKpiEvidenceService service;
    UUID tenant=UUID.randomUUID(), programId=UUID.randomUUID(), participantId=UUID.randomUUID(), goalId=UUID.randomUUID();
    UUID employeeId=UUID.randomUUID(), cycleId=UUID.randomUUID(), assignmentId=UUID.randomUUID(), nodeId=UUID.randomUUID(), treeId=UUID.randomUUID();
    Actor actor;

    @BeforeEach void setUp(){
        actor=new Actor(UUID.randomUUID(),tenant,UUID.randomUUID(),"HR","HR_ADMIN");
        service=new ProgramKpiEvidenceService(programs,participants,goals,submissions,calculations,evidence,
            assignments,nodes,trees,actuals,cycles,access,new ProgramJson(new ObjectMapper().findAndRegisterModules()),audit,
            Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"),ZoneOffset.UTC));
    }

    @Test void previewUsesCurrentCorrectedLeafAndSharedFormula(){
        stubContext(); stubSources(); UUID rootId=UUID.randomUUID();
        KpiActual root=actual(rootId,null,LocalDate.of(2026,6,30),"70");
        KpiActual correction=actual(UUID.randomUUID(),rootId,LocalDate.of(2026,6,30),"90");
        when(actuals.findAllByTenantIdAndKpiAssignmentIdOrderByAsOfDateDescCreatedAtDesc(tenant,assignmentId))
            .thenReturn(List.of(correction,root));
        when(evidence.findFirstByTenantIdAndGoalIdOrderByRevisionDesc(tenant,goalId)).thenReturn(Optional.empty());

        KpiLinkPreviewResponse result=service.preview(actor,programId,participantId,goalId,
            new KpiLinkPreviewRequest(cycleId,LocalDate.of(2026,6,30),assignmentId));

        assertThat(result.previewHash()).matches("[0-9a-f]{64}");
        assertThat(result.row().actualId()).isEqualTo(correction.getId());
        assertThat(result.row().achievementRate()).isEqualByComparingTo("0.900000");
        assertThat(result.row().autoScore()).isEqualByComparingTo("90.00");
        assertThat(result.row().status()).isEqualTo(KpiLinkStatus.READY);
    }

    @Test void futureSuccessorDoesNotResurrectOldRoot(){
        stubContext(); stubSources(); UUID rootId=UUID.randomUUID();
        when(actuals.findAllByTenantIdAndKpiAssignmentIdOrderByAsOfDateDescCreatedAtDesc(tenant,assignmentId))
            .thenReturn(List.of(actual(rootId,null,LocalDate.of(2026,6,30),"70"),
                actual(UUID.randomUUID(),rootId,LocalDate.of(2026,9,30),"90")));
        when(evidence.findFirstByTenantIdAndGoalIdOrderByRevisionDesc(tenant,goalId)).thenReturn(Optional.empty());

        KpiLinkPreviewResponse result=service.preview(actor,programId,participantId,goalId,
            new KpiLinkPreviewRequest(cycleId,LocalDate.of(2026,6,30),assignmentId));
        assertThat(result.row().status()).isEqualTo(KpiLinkStatus.SOURCE_MISSING);
        assertThat(result.row().actualId()).isNull();
    }

    @Test void applyCreatesImmutableFirstRevisionWithoutCalculationMutation(){
        stubContext(); stubSources();
        when(actuals.findAllByTenantIdAndKpiAssignmentIdOrderByAsOfDateDescCreatedAtDesc(tenant,assignmentId))
            .thenReturn(List.of(actual(UUID.randomUUID(),null,LocalDate.of(2026,6,30),"90")));
        when(evidence.findFirstByTenantIdAndGoalIdOrderByRevisionDesc(tenant,goalId)).thenReturn(Optional.empty());
        when(evidence.findByTenantIdAndGoalIdAndPreviewHash(eq(tenant),eq(goalId),anyString())).thenReturn(Optional.empty());
        when(programs.findLocked(programId,tenant)).thenReturn(Optional.of(program()));
        when(goals.findLocked(goalId,tenant)).thenReturn(Optional.of(goal()));
        when(assignments.findLocked(assignmentId,tenant)).thenReturn(Optional.of(assignment()));
        when(nodes.findLocked(nodeId,tenant)).thenReturn(Optional.of(node()));
        when(evidence.saveAndFlush(any())).thenAnswer(inv->{ProgramKpiEvidence row=inv.getArgument(0);row.prePersist();return row;});

        KpiLinkPreviewResponse preview=service.preview(actor,programId,participantId,goalId,
            new KpiLinkPreviewRequest(cycleId,LocalDate.of(2026,6,30),assignmentId));
        KpiLinkApplyResponse result=service.apply(actor,programId,participantId,goalId,
            new KpiLinkApplyRequest(cycleId,LocalDate.of(2026,6,30),assignmentId,preview.previewHash(),"확인"));

        assertThat(result.revision()).isOne(); assertThat(result.active()).isTrue();
        assertThat(result.evidence().autoScore()).isEqualByComparingTo("90.00");
        verify(evidence).saveAndFlush(any()); verify(calculations,never()).save(any());
    }

    @Test void inactiveParticipantIsRejected(){
        stubContext();
        ProgramParticipant excluded=participant(); excluded.setStatus(ParticipantStatus.EXCLUDED);
        when(access.participant(actor,participantId)).thenReturn(excluded);
        assertThatThrownBy(()->service.preview(actor,programId,participantId,goalId,
            new KpiLinkPreviewRequest(cycleId,LocalDate.of(2026,6,30),assignmentId)))
            .isInstanceOf(ApiException.class).extracting(e->((ApiException)e).errorCode())
            .isEqualTo(ProgramErrorCode.PROGRAM_INVALID);
    }

    @Test void completedSelfSubmissionDoesNotFreezeEvidence(){
        stubContext(); stubSources();
        when(submissions.existsByTenantIdAndParticipantIdAndRoleAndStatus(
            eq(tenant),eq(participantId),any(ReviewerRole.class),eq(SubmissionStatus.COMPLETED)))
            .thenAnswer(invocation -> invocation.getArgument(2)==ReviewerRole.SELF);
        when(actuals.findAllByTenantIdAndKpiAssignmentIdOrderByAsOfDateDescCreatedAtDesc(tenant,assignmentId))
            .thenReturn(List.of(actual(UUID.randomUUID(),null,LocalDate.of(2026,6,30),"90")));
        when(evidence.findFirstByTenantIdAndGoalIdOrderByRevisionDesc(tenant,goalId)).thenReturn(Optional.empty());

        KpiLinkPreviewResponse result=service.preview(actor,programId,participantId,goalId,
            new KpiLinkPreviewRequest(cycleId,LocalDate.of(2026,6,30),assignmentId));

        assertThat(result.row().status()).isEqualTo(KpiLinkStatus.READY);
        verify(submissions,never()).existsByTenantIdAndParticipantIdAndRoleAndStatus(
            tenant,participantId,ReviewerRole.SELF,SubmissionStatus.COMPLETED);
    }

    @Test void completedReviewerSubmissionFreezesEvidence(){
        stubContext();
        when(submissions.existsByTenantIdAndParticipantIdAndRoleAndStatus(
            tenant,participantId,ReviewerRole.REVIEWER,SubmissionStatus.COMPLETED)).thenReturn(true);

        assertThatThrownBy(()->service.preview(actor,programId,participantId,goalId,
            new KpiLinkPreviewRequest(cycleId,LocalDate.of(2026,6,30),assignmentId)))
            .isInstanceOf(ApiException.class).extracting(e->((ApiException)e).errorCode())
            .isEqualTo(ProgramErrorCode.PROGRAM_LOCKED);
    }

    @Test void existingCalculationFreezesEvidence(){
        stubContext();
        when(calculations.existsByTenantIdAndParticipantId(tenant,participantId)).thenReturn(true);

        assertThatThrownBy(()->service.preview(actor,programId,participantId,goalId,
            new KpiLinkPreviewRequest(cycleId,LocalDate.of(2026,6,30),assignmentId)))
            .isInstanceOf(ApiException.class).extracting(e->((ApiException)e).errorCode())
            .isEqualTo(ProgramErrorCode.PROGRAM_LOCKED);
    }

    private void stubContext(){
        doNothing().when(access).requireOperator(actor); when(programs.findByIdAndTenantId(programId,tenant)).thenReturn(Optional.of(program()));
        when(access.participant(actor,participantId)).thenReturn(participant()); when(goals.findByIdAndTenantId(goalId,tenant)).thenReturn(Optional.of(goal()));
        when(cycles.findByIdAndTenantId(cycleId,tenant)).thenReturn(Optional.of(cycle()));
        lenient().when(calculations.existsByTenantIdAndParticipantId(tenant,participantId)).thenReturn(false);
        lenient().when(submissions.existsByTenantIdAndParticipantIdAndRoleAndStatus(tenant,participantId,ReviewerRole.REVIEWER,SubmissionStatus.COMPLETED)).thenReturn(false);
    }
    private void stubSources(){when(assignments.findByIdAndTenantId(assignmentId,tenant)).thenReturn(Optional.of(assignment()));when(nodes.findByIdAndTenantId(nodeId,tenant)).thenReturn(Optional.of(node()));when(trees.findByIdAndTenantId(treeId,tenant)).thenReturn(Optional.of(tree()));}
    private EvaluationProgram program(){EvaluationProgram p=new EvaluationProgram();p.setId(programId);p.setTenantId(tenant);p.setStatus(ProgramStatus.OPEN);p.setAsOfDate(LocalDate.of(2026,1,1));p.setStartsOn(LocalDate.of(2026,1,1));p.setEndsOn(LocalDate.of(2026,12,31));p.setDefinitionRevision(1);return p;}
    private ProgramParticipant participant(){ProgramParticipant p=new ProgramParticipant();p.setId(participantId);p.setTenantId(tenant);p.setProgramId(programId);p.setEmployeeId(employeeId);p.setStatus(ParticipantStatus.ACTIVE);return p;}
    private ProgramGoal goal(){ProgramGoal g=new ProgramGoal();g.setId(goalId);g.setTenantId(tenant);g.setProgramId(programId);g.setParticipantId(participantId);g.setTitle("매출");g.setWeightPercent(new BigDecimal("100"));g.setStatus(GoalStatus.AGREED);g.setRevision(2);return g;}
    private EvaluationCycle cycle(){EvaluationCycle c=new EvaluationCycle();c.setId(cycleId);c.setTenantId(tenant);c.setName("2026");c.setPeriodStart(LocalDate.of(2026,1,1));c.setPeriodEnd(LocalDate.of(2026,12,31));c.setCycleType(CycleType.ANNUAL);c.setStatus(CycleStatus.ACTIVE);return c;}
    private KpiAssignment assignment(){KpiAssignment a=new KpiAssignment();a.setId(assignmentId);a.setTenantId(tenant);a.setEmployeeId(employeeId);a.setKpiNodeId(nodeId);return a;}
    private KpiNode node(){KpiNode n=new KpiNode();n.setId(nodeId);n.setTenantId(tenant);n.setTreeId(treeId);n.setLabel("매출 KPI");n.setWeight(BigDecimal.ONE);n.setTarget(new BigDecimal("100"));n.setSource(KpiNodeSource.MANUAL);return n;}
    private KpiTree tree(){KpiTree t=new KpiTree();t.setId(treeId);t.setTenantId(tenant);t.setCycleId(cycleId);t.setName("개인 KPI");return t;}
    private KpiActual actual(UUID id,UUID supersedes,LocalDate date,String value){KpiActual a=new KpiActual();a.setId(id);a.setTenantId(tenant);a.setKpiAssignmentId(assignmentId);a.setAsOfDate(date);a.setActualValue(new BigDecimal(value));a.setSource(KpiActualSource.MANUAL);a.setSupersedesId(supersedes);return a;}
}

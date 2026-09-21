package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.ProgramConfiguration;
import com.easyperformance.program.ProgramDtos.RevieweeGroupInput;
import com.easyperformance.program.ProgramReminderDtos.*;
import com.easyperformance.program.ProgramTypes.*;
import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgramReminderServiceTest {
    @Mock EvaluationProgramRepository programs; @Mock ProgramParticipantRepository participants;
    @Mock ProgramReviewerAssignmentRepository reviewers; @Mock ProgramGoalRepository goals;
    @Mock ProgramReviewSubmissionRepository submissions; @Mock ProgramIntermediateReviewRepository intermediate;
    @Mock ProgramCalculationRepository calculations; @Mock ProgramAdjustmentRepository adjustments;
    @Mock ProgramFeedbackRepository feedback; @Mock RmEmployeeRepository employees;
    @Mock ProgramNotificationRepository notifications; @Mock ProgramReminderRunRepository runs;
    @Mock ProgramAccess access; @Mock ProgramJson json; @Mock ProgramAuditService audit;

    private final UUID tenantId=UUID.randomUUID(),programId=UUID.randomUUID(),participantId=UUID.randomUUID();
    private final UUID participantEmployeeId=UUID.randomUUID();
    private final LocalDate today=LocalDate.of(2026,9,8);
    private Actor operator;
    private EvaluationProgram program;
    private ProgramParticipant participant;
    private ProgramConfiguration configuration;
    private ProgramReminderService service;

    @BeforeEach
    void setUp(){
        operator=new Actor(UUID.randomUUID(),tenantId,UUID.randomUUID(),"operator","HR_ADMIN");
        program=new EvaluationProgram();program.setId(programId);program.setTenantId(tenantId);program.setName("2026 평가");
        program.setStatus(ProgramStatus.OPEN);program.setKind(EvaluationKind.PERFORMANCE);program.setDefinitionJson("definition");program.setDefinitionRevision(3);
        configuration=ProgramConfigurationFactory.defaults(EvaluationKind.PERFORMANCE);
        participant=new ProgramParticipant();participant.setId(participantId);participant.setTenantId(tenantId);participant.setProgramId(programId);
        participant.setEmployeeId(participantEmployeeId);participant.setGroupId(configuration.groups().getFirst().id());participant.setStatus(ParticipantStatus.ACTIVE);
        participant.setCurrentStage(ProgramStage.SELF_REVIEW);participant.setStageStatus(ProgramStageStatus.IN_PROGRESS);participant.setCurrentRound(0);participant.setAttributesJson("{}");
        service=serviceAt(Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"),ZoneOffset.UTC));
        lenient().when(programs.findByIdAndTenantId(programId,tenantId)).thenReturn(Optional.of(program));
        lenient().when(programs.findLocked(programId,tenantId)).thenReturn(Optional.of(program));
        lenient().when(participants.findAllByTenantIdAndProgramIdAndIdIn(eq(tenantId),eq(programId),anyCollection())).thenReturn(List.of(participant));
        lenient().when(json.configuration("definition")).thenReturn(configuration);
        lenient().when(json.participantAttributes("{}")).thenReturn(new ProgramDtos.ParticipantAttributes(participantEmployeeId,"E001","홍길동",null,null,null,null,null,null,null));
        lenient().when(employees.findAllByTenantIdAndIdIn(eq(tenantId),anyCollection())).thenAnswer(invocation->{
            Collection<UUID> ids=invocation.getArgument(1);return ids.contains(participantEmployeeId)?List.of(employee(participantEmployeeId,"홍길동")):List.of();});
        lenient().when(notifications.findAllByTenantIdAndReminderDedupeKeyIn(eq(tenantId),anyCollection())).thenReturn(List.of());
        lenient().when(runs.findByTenantIdAndProgramIdAndIdempotencyKey(eq(tenantId),eq(programId),any())).thenReturn(Optional.empty());
    }

    @ParameterizedTest
    @ValueSource(strings={"ko","en","ja","zh-CN","vi"})
    void previewRendersEverySupportedLocaleOnServer(String locale){
        ReminderPreviewResponse response=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),List.of(ProgramStage.SELF_REVIEW),locale));

        assertThat(response.zoneId()).isEqualTo("UTC");
        assertThat(response.reminderOn()).isEqualTo(today);
        assertThat(response.previewHash()).matches("[0-9a-f]{64}");
        assertThat(response.candidates()).singleElement().satisfies(row->{
            assertThat(row.status()).isEqualTo(ReminderCandidateStatus.READY);
            assertThat(row.action()).isEqualTo(ReminderAction.SELF_REVIEW);
            assertThat(row.subject()).isNotBlank().doesNotContain("\r","\n");
            assertThat(row.subject().length()).isLessThanOrEqualTo(200);
            assertThat(row.body()).contains(row.deepLink());
            assertThat(row.body().length()).isLessThanOrEqualTo(8000);
        });
    }

    @Test
    void invalidatedSelfSubmissionRemainsPending(){
        ProgramReviewSubmission invalidated=new ProgramReviewSubmission();invalidated.setId(UUID.randomUUID());invalidated.setTenantId(tenantId);
        invalidated.setProgramId(programId);invalidated.setParticipantId(participantId);invalidated.setRole(ReviewerRole.SELF);invalidated.setRound(0);invalidated.setStatus(SubmissionStatus.INVALIDATED);
        when(submissions.findAllByTenantIdAndProgramIdAndParticipantIdIn(eq(tenantId),eq(programId),anyCollection())).thenReturn(List.of(invalidated));

        ReminderPreviewResponse response=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko"));

        assertThat(response.candidates()).singleElement().extracting(ReminderCandidate::status).isEqualTo(ReminderCandidateStatus.READY);
    }

    @Test
    void groupDisabledIntermediateStageIsNotAddressed(){
        participant.setCurrentStage(ProgramStage.INTERMEDIATE);
        RevieweeGroupInput source=configuration.groups().getFirst();
        RevieweeGroupInput disabled=new RevieweeGroupInput(source.id(),source.name(),source.definition(),source.itemAssignmentMode(),source.evaluationMethod(),false,source.selfReviewEnabled(),source.priority(),source.conditions(),source.reviewerWeightPlans());
        configuration=new ProgramDtos.ProgramConfiguration(configuration.goalMode(),configuration.stages(),configuration.scales(),configuration.calculation(),configuration.publication(),List.of(disabled),configuration.commonItems(),configuration.departmentPerformanceGroups(),configuration.allocationRows());
        when(json.configuration("definition")).thenReturn(configuration);

        ReminderPreviewResponse response=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko"));

        assertThat(response.candidates()).isEmpty();
        assertThat(response.exclusions()).singleElement().satisfies(row->{
            assertThat(row.status()).isEqualTo(ReminderExclusionStatus.NOT_APPLICABLE);
            assertThat(row.reasonCode()).isEqualTo("STAGE_FILTERED_OUT");
        });
    }

    @Test
    void appealedFeedbackUsesFinalFeedbackAssignee(){
        participant.setCurrentStage(ProgramStage.FEEDBACK);
        UUID reviewerId=UUID.randomUUID();ProgramReviewerAssignment assignment=assignment(reviewerId,ReviewerRole.FINAL_FEEDBACK,0);
        ProgramFeedback appealed=new ProgramFeedback();appealed.setId(UUID.randomUUID());appealed.setTenantId(tenantId);appealed.setProgramId(programId);appealed.setParticipantId(participantId);appealed.setStatus(FeedbackStatus.APPEALED);
        when(reviewers.findAllByTenantIdAndProgramIdAndParticipantIdInAndStatusNot(eq(tenantId),eq(programId),anyCollection(),eq(AssignmentStatus.REVOKED))).thenReturn(List.of(assignment));
        when(feedback.findAllByTenantIdAndProgramIdAndParticipantIdIn(eq(tenantId),eq(programId),anyCollection())).thenReturn(List.of(appealed));
        when(employees.findAllByTenantIdAndIdIn(eq(tenantId),anyCollection())).thenReturn(List.of(employee(participantEmployeeId,"홍길동"),employee(reviewerId,"김책임")));

        ReminderPreviewResponse response=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko"));

        assertThat(response.candidates()).singleElement().satisfies(row->{
            assertThat(row.action()).isEqualTo(ReminderAction.FEEDBACK_RESOLUTION);
            assertThat(row.ownerRole()).isEqualTo(ReviewerRole.FINAL_FEEDBACK);
            assertThat(row.recipientEmployeeId()).isEqualTo(reviewerId);
        });
    }

    @Test
    void selectingAnUnchangedBlockedCandidateIsInvalidRatherThanStale(){
        when(employees.findAllByTenantIdAndIdIn(eq(tenantId),anyCollection())).thenReturn(List.of());
        ReminderPreviewResponse preview=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko"));
        ReminderQueueRequest request=queueRequest(preview,preview.candidates().getFirst().candidateKey(),UUID.randomUUID());

        assertThatThrownBy(()->service.queue(operator,programId,request))
            .isInstanceOfSatisfying(ApiException.class,error->assertThat(error.errorCode()).isEqualTo(ProgramErrorCode.PROGRAM_INVALID));
        verify(runs,never()).saveAndFlush(any());
    }

    @Test
    void exactReplaySurvivesProgramFinalization(){
        ReminderPreviewResponse preview=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko"));
        ReminderQueueRequest request=queueRequest(preview,preview.candidates().getFirst().candidateKey(),UUID.randomUUID());
        AtomicReference<ProgramReminderRun> saved=new AtomicReference<>();
        when(runs.findByTenantIdAndProgramIdAndIdempotencyKey(tenantId,programId,request.idempotencyKey())).thenAnswer(invocation->Optional.ofNullable(saved.get()));
        when(runs.saveAndFlush(any())).thenAnswer(invocation->{ProgramReminderRun row=invocation.getArgument(0);row.prePersist();saved.set(row);return row;});
        when(notifications.saveAndFlush(any())).thenAnswer(invocation->{ProgramNotification row=invocation.getArgument(0);row.prePersist();return row;});
        when(json.write(any())).thenReturn("{}");

        ReminderQueueResponse first=service.queue(operator,programId,request);
        when(json.reminderQueueResponse("{}")).thenReturn(first);
        program.setStatus(ProgramStatus.FINALIZED);
        ReminderQueueResponse replay=service.queue(operator,programId,request);

        assertThat(replay).isEqualTo(first);
        verify(notifications,times(1)).saveAndFlush(any());
    }

    @Test
    void utcDateDoesNotDependOnJvmClockZone(){
        service=serviceAt(Clock.fixed(Instant.parse("2026-09-08T16:00:00Z"),ZoneId.of("Asia/Seoul")));

        ReminderPreviewResponse response=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko"));

        assertThat(response.reminderOn()).isEqualTo(LocalDate.of(2026,9,8));
    }

    @Test
    void nextUtcDayCreatesANewDailyReminderForTheSameEpisode(){
        MutableClock mutable=new MutableClock(Instant.parse("2026-09-08T23:59:59Z"));service=serviceAt(mutable);
        when(runs.saveAndFlush(any())).thenAnswer(invocation->{ProgramReminderRun row=invocation.getArgument(0);row.prePersist();return row;});
        when(notifications.saveAndFlush(any())).thenAnswer(invocation->{ProgramNotification row=invocation.getArgument(0);row.prePersist();return row;});
        when(json.write(any())).thenReturn("{}");
        ReminderPreviewResponse firstPreview=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko"));
        ReminderQueueResponse first=service.queue(operator,programId,queueRequest(firstPreview,firstPreview.candidates().getFirst().candidateKey(),UUID.randomUUID()));

        mutable.set(Instant.parse("2026-09-09T00:00:01Z"));
        ReminderPreviewResponse secondPreview=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko"));
        ReminderQueueResponse second=service.queue(operator,programId,queueRequest(secondPreview,secondPreview.candidates().getFirst().candidateKey(),UUID.randomUUID()));

        assertThat(secondPreview.candidates().getFirst().candidateKey()).isEqualTo(firstPreview.candidates().getFirst().candidateKey());
        assertThat(first.reminderOn()).isEqualTo(LocalDate.of(2026,9,8));assertThat(second.reminderOn()).isEqualTo(LocalDate.of(2026,9,9));
        verify(notifications,times(2)).saveAndFlush(any());
    }

    @Test
    void ambiguousOwnerIsBlockedWithoutFallback(){
        participant.setCurrentStage(ProgramStage.INTERMEDIATE);UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        when(reviewers.findAllByTenantIdAndProgramIdAndParticipantIdInAndStatusNot(eq(tenantId),eq(programId),anyCollection(),eq(AssignmentStatus.REVOKED)))
            .thenReturn(List.of(assignment(first,ReviewerRole.CHECKER,0),assignment(second,ReviewerRole.CHECKER,0)));
        when(employees.findAllByTenantIdAndIdIn(eq(tenantId),anyCollection())).thenReturn(List.of(employee(participantEmployeeId,"홍길동"),employee(first,"검토자1"),employee(second,"검토자2")));

        ReminderPreviewResponse response=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko"));

        assertThat(response.candidates()).singleElement().satisfies(row->{assertThat(row.status()).isEqualTo(ReminderCandidateStatus.BLOCKED);assertThat(row.reasonCode()).isEqualTo("OWNER_AMBIGUOUS");});
    }

    @Test
    void inactiveSelfOwnerIsBlockedWithoutFallback(){
        RmEmployee inactive=employee(participantEmployeeId,"홍길동");inactive.setStatus("INACTIVE");
        when(employees.findAllByTenantIdAndIdIn(eq(tenantId),anyCollection())).thenReturn(List.of(inactive));

        ReminderPreviewResponse response=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko"));

        assertThat(response.candidates()).singleElement().satisfies(row->{assertThat(row.status()).isEqualTo(ReminderCandidateStatus.BLOCKED);assertThat(row.reasonCode()).isEqualTo("OWNER_INACTIVE");});
    }

    @Test
    void reviewerAssignmentCompletedWithoutSubmissionIsBlocked(){
        participant.setCurrentStage(ProgramStage.REVIEW);participant.setCurrentRound(1);UUID reviewerId=UUID.randomUUID();
        ProgramReviewerAssignment assignment=assignment(reviewerId,ReviewerRole.REVIEWER,1);assignment.setStatus(AssignmentStatus.COMPLETED);
        when(reviewers.findAllByTenantIdAndProgramIdAndParticipantIdInAndStatusNot(eq(tenantId),eq(programId),anyCollection(),eq(AssignmentStatus.REVOKED))).thenReturn(List.of(assignment));
        when(employees.findAllByTenantIdAndIdIn(eq(tenantId),anyCollection())).thenReturn(List.of(employee(participantEmployeeId,"홍길동"),employee(reviewerId,"평가자")));

        ReminderPreviewResponse response=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko"));

        assertThat(response.candidates()).singleElement().satisfies(row->{assertThat(row.status()).isEqualTo(ReminderCandidateStatus.BLOCKED);assertThat(row.reasonCode()).isEqualTo("SOURCE_INCONSISTENT");});
    }

    @Test
    void impossibleAgreementGoalStateIsExcludedAsSourceInconsistent(){
        participant.setCurrentStage(ProgramStage.GOAL);ProgramGoal goal=new ProgramGoal();goal.setId(UUID.randomUUID());goal.setTenantId(tenantId);goal.setProgramId(programId);goal.setParticipantId(participantId);goal.setStatus(GoalStatus.SELF_REPORTED);goal.setRevision(1);
        when(goals.findAllByTenantIdAndParticipantIdIn(eq(tenantId),anyCollection())).thenReturn(List.of(goal));

        ReminderPreviewResponse response=service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko"));

        assertThat(response.candidates()).isEmpty();assertThat(response.exclusions()).singleElement().extracting(ReminderExclusion::reasonCode).isEqualTo("SOURCE_INCONSISTENT");
    }

    @Test
    void renderedTemplateOverflowIsRejectedBeforePersistence(){
        program.setName("가".repeat(200));

        assertThatThrownBy(()->service.preview(operator,programId,new ReminderPreviewRequest(List.of(participantId),null,"ko")))
            .isInstanceOfSatisfying(ApiException.class,error->assertThat(error.errorCode()).isEqualTo(ProgramErrorCode.PROGRAM_INVALID));
        verify(notifications,never()).save(any());
    }

    private ReminderQueueRequest queueRequest(ReminderPreviewResponse preview,String candidateKey,UUID idempotencyKey){
        return new ReminderQueueRequest(List.of(participantId),null,"ko",preview.reminderOn(),preview.previewHash(),List.of(candidateKey),idempotencyKey,"미완료 업무 확인 요청");
    }

    private ProgramReviewerAssignment assignment(UUID employeeId,ReviewerRole role,int round){
        ProgramReviewerAssignment row=new ProgramReviewerAssignment();row.setId(UUID.randomUUID());row.setTenantId(tenantId);row.setProgramId(programId);row.setParticipantId(participantId);
        row.setReviewerEmployeeId(employeeId);row.setRole(role);row.setRound(round);row.setWeightPercent(new java.math.BigDecimal("100"));row.setStatus(AssignmentStatus.ASSIGNED);return row;
    }

    private RmEmployee employee(UUID id,String name){
        RmEmployee row=new RmEmployee();row.setId(id);row.setTenantId(tenantId);row.setEmployeeNo(id.toString());row.setName(name);row.setStatus("ACTIVE");row.setSourceVersion(1L);return row;
    }

    private ProgramReminderService serviceAt(Clock clock){
        return new ProgramReminderService(programs,participants,reviewers,goals,submissions,intermediate,calculations,
            adjustments,feedback,employees,notifications,runs,access,json,audit,clock);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;MutableClock(Instant instant){this.instant=instant;}void set(Instant value){instant=value;}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}@Override public Clock withZone(ZoneId zone){return this;}@Override public Instant instant(){return instant;}
    }
}

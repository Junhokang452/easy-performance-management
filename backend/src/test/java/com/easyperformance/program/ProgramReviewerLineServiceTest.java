package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.ProgramConfiguration;
import com.easyperformance.program.ProgramDtos.ReviewerWeightPlanInput;
import com.easyperformance.program.ProgramDtos.RevieweeGroupInput;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLineApplyRequest;
import com.easyperformance.program.ProgramReviewerLineDtos.ReviewerLinePreviewRequest;
import com.easyperformance.program.ProgramTypes.AssignmentStatus;
import com.easyperformance.program.ProgramTypes.ParticipantStatus;
import com.easyperformance.program.ProgramTypes.ProgramStageStatus;
import com.easyperformance.program.ProgramTypes.ProgramStatus;
import com.easyperformance.program.ProgramTypes.ReviewerLineApplyStatus;
import com.easyperformance.program.ProgramTypes.ReviewerLinePreviewStatus;
import com.easyperformance.program.ProgramTypes.ReviewerRole;
import com.easyperformance.readmodel.entity.RmAssignment;
import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.repository.RmAssignmentRepository;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgramReviewerLineServiceTest {
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID PROGRAM = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID PARTICIPANT = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID EMPLOYEE = UUID.fromString("00000000-0000-0000-0000-000000000104");
    private static final UUID ASSIGNMENT = UUID.fromString("00000000-0000-0000-0000-000000000105");
    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000106");
    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 1);

    private final EvaluationProgramRepository programs = mock(EvaluationProgramRepository.class);
    private final ProgramParticipantRepository participants = mock(ProgramParticipantRepository.class);
    private final ProgramReviewerAssignmentRepository reviewers = mock(ProgramReviewerAssignmentRepository.class);
    private final ProgramReviewSubmissionRepository submissions = mock(ProgramReviewSubmissionRepository.class);
    private final ProgramReviewerLineRunRepository runs = mock(ProgramReviewerLineRunRepository.class);
    private final RmAssignmentRepository assignments = mock(RmAssignmentRepository.class);
    private final RmEmployeeRepository employees = mock(RmEmployeeRepository.class);
    private final ProgramAccess access = mock(ProgramAccess.class);
    private final ProgramAuditService audit = mock(ProgramAuditService.class);
    private final ProgramJson json = new ProgramJson(new ObjectMapper().findAndRegisterModules());
    private final Actor actor = new Actor(UUID.randomUUID(), TENANT, UUID.randomUUID(), "HR", "HR_ADMIN");

    private ProgramReviewerLineService service;
    private EvaluationProgram program;
    private ProgramParticipant participant;
    private RmAssignment source;
    private RmEmployee manager;
    private ProgramConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = ProgramConfigurationFactory.defaults(ProgramTypes.EvaluationKind.PERFORMANCE);
        program = new EvaluationProgram();
        program.setId(PROGRAM);
        program.setTenantId(TENANT);
        program.setStatus(ProgramStatus.DRAFT);
        program.setAsOfDate(AS_OF);
        program.setDefinitionRevision(3);
        program.setDefinitionJson(json.write(configuration));

        participant = new ProgramParticipant();
        participant.setId(PARTICIPANT);
        participant.setTenantId(TENANT);
        participant.setProgramId(PROGRAM);
        participant.setEmployeeId(EMPLOYEE);
        participant.setAssignmentId(ASSIGNMENT);
        participant.setGroupId(configuration.groups().getFirst().id());
        participant.setStatus(ParticipantStatus.ACTIVE);
        participant.setStageStatus(ProgramStageStatus.NOT_STARTED);

        source = assignment(ASSIGNMENT, EMPLOYEE, MANAGER);
        manager = employee(MANAGER, "Manager", "ACTIVE");

        when(programs.findByIdAndTenantId(PROGRAM, TENANT)).thenReturn(Optional.of(program));
        when(access.lockedProgram(actor, PROGRAM)).thenReturn(program);
        when(participants.findByIdAndTenantId(PARTICIPANT, TENANT)).thenReturn(Optional.of(participant));
        when(reviewers.findAllByTenantIdAndParticipantIdAndStatusNotOrderByRoleAscRoundAsc(
            TENANT, PARTICIPANT, AssignmentStatus.REVOKED)).thenReturn(List.of());
        when(submissions.existsByTenantIdAndParticipantId(TENANT, PARTICIPANT)).thenReturn(false);
        when(assignments.findAllByTenantIdAndEmployeeId(TENANT, EMPLOYEE)).thenReturn(List.of(source));
        when(assignments.findAllByTenantIdAndEmployeeId(TENANT, MANAGER)).thenReturn(List.of());
        when(employees.findByIdAndTenantId(MANAGER, TENANT)).thenReturn(Optional.of(manager));
        when(runs.findByTenantIdAndProgramIdAndPreviewHash(any(), any(), any())).thenReturn(Optional.empty());
        when(reviewers.save(any())).thenAnswer(invocation -> {
            ProgramReviewerAssignment saved = invocation.getArgument(0);
            saved.prePersist();
            return saved;
        });
        service = new ProgramReviewerLineService(programs, participants, reviewers, submissions,
            runs, assignments, employees, access, json, audit);
    }

    @Test
    void previewReadyUsesProgramAsOfAndExactOneHundredPercentPlan() {
        var response = service.preview(actor, PROGRAM, preview(Set.of(ReviewerRole.REVIEWER)));

        assertThat(response.asOfDate()).isEqualTo(AS_OF);
        assertThat(response.previewHash()).hasSize(64);
        assertThat(response.summary().ready()).isEqualTo(1);
        assertThat(response.rows().getFirst().status()).isEqualTo(ReviewerLinePreviewStatus.READY);
        assertThat(response.rows().getFirst().sourceDeleted()).isFalse();
        assertThat(response.rows().getFirst().proposals().getFirst().weightPercent())
            .isEqualByComparingTo("100");
    }

    @Test
    void rejectsSelfAndAdjusterRoles() {
        assertThatThrownBy(() -> service.preview(actor, PROGRAM, preview(Set.of(ReviewerRole.SELF))))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).errorCode().code()).isEqualTo("E9804256"));
    }

    @Test
    void rejectsDuplicateParticipantIds() {
        var request = new ReviewerLinePreviewRequest(List.of(PARTICIPANT, PARTICIPANT),
            Set.of(ReviewerRole.REVIEWER));
        assertThatThrownBy(() -> service.preview(actor, PROGRAM, request))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void skipsWholeParticipantWhenAnyActiveReviewerExists() {
        when(reviewers.findAllByTenantIdAndParticipantIdAndStatusNotOrderByRoleAscRoundAsc(
            TENANT, PARTICIPANT, AssignmentStatus.REVOKED)).thenReturn(List.of(new ProgramReviewerAssignment()));

        var row = service.preview(actor, PROGRAM, preview(Set.of(ReviewerRole.REVIEWER))).rows().getFirst();

        assertThat(row.status()).isEqualTo(ReviewerLinePreviewStatus.SKIPPED_EXISTING);
        assertThat(row.currentReviewerCount()).isEqualTo(1);
        verify(assignments, never()).findAllByTenantIdAndEmployeeId(TENANT, EMPLOYEE);
    }

    @Test
    void blocksLegacySourceWithNullableDeletedCapability() {
        source.setDeleted(null);
        source.setSourceSystem("LEGACY");

        var row = service.preview(actor, PROGRAM, preview(Set.of(ReviewerRole.REVIEWER))).rows().getFirst();

        assertThat(row.status()).isEqualTo(ReviewerLinePreviewStatus.BLOCKED);
        assertThat(row.issues()).extracting(ProgramReviewerLineDtos.ReviewerLineIssue::code)
            .contains("SOURCE_LEGACY_UNSUPPORTED");
    }

    @Test
    void blocksAmbiguousEffectiveAssignmentsInsteadOfChoosingLatest() {
        when(assignments.findAllByTenantIdAndEmployeeId(TENANT, EMPLOYEE))
            .thenReturn(List.of(source, assignment(UUID.randomUUID(), EMPLOYEE, MANAGER)));

        var row = service.preview(actor, PROGRAM, preview(Set.of(ReviewerRole.REVIEWER))).rows().getFirst();

        assertThat(row.status()).isEqualTo(ReviewerLinePreviewStatus.BLOCKED);
        assertThat(row.issues()).extracting(ProgramReviewerLineDtos.ReviewerLineIssue::code)
            .contains("AMBIGUOUS_EFFECTIVE_ASSIGNMENT");
    }

    @Test
    void blocksInactiveManagerAndManagerCycle() {
        manager.setStatus("INACTIVE");
        var inactive = service.preview(actor, PROGRAM, preview(Set.of(ReviewerRole.REVIEWER)))
            .rows().getFirst();
        assertThat(inactive.issues()).extracting(ProgramReviewerLineDtos.ReviewerLineIssue::code)
            .contains("MANAGER_NOT_FOUND_OR_INACTIVE");

        manager.setStatus("ACTIVE");
        when(assignments.findAllByTenantIdAndEmployeeId(TENANT, MANAGER))
            .thenReturn(List.of(assignment(UUID.randomUUID(), MANAGER, EMPLOYEE)));
        var cycle = service.preview(actor, PROGRAM, preview(Set.of(ReviewerRole.REVIEWER)))
            .rows().getFirst();
        assertThat(cycle.issues()).extracting(ProgramReviewerLineDtos.ReviewerLineIssue::code)
            .contains("MANAGER_CYCLE");
    }

    @Test
    void blocksReviewerPlanWithMoreThanOneRound() {
        RevieweeGroupInput original = configuration.groups().getFirst();
        RevieweeGroupInput twoRounds = new RevieweeGroupInput(original.id(), original.name(),
            original.definition(), original.itemAssignmentMode(), original.evaluationMethod(),
            original.intermediateEnabled(), original.selfReviewEnabled(), original.priority(),
            original.conditions(), List.of(new ReviewerWeightPlanInput(2,
                Map.of(1, new BigDecimal("50"), 2, new BigDecimal("50")), BigDecimal.ZERO)));
        configuration = new ProgramConfiguration(configuration.goalMode(), configuration.stages(),
            configuration.scales(), configuration.calculation(), configuration.publication(),
            List.of(twoRounds), configuration.commonItems(), configuration.departmentPerformanceGroups(),
            configuration.allocationRows());
        program.setDefinitionJson(json.write(configuration));

        var row = service.preview(actor, PROGRAM, preview(Set.of(ReviewerRole.REVIEWER))).rows().getFirst();

        assertThat(row.status()).isEqualTo(ReviewerLinePreviewStatus.BLOCKED);
        assertThat(row.issues()).extracting(ProgramReviewerLineDtos.ReviewerLineIssue::code)
            .contains("REVIEWER_WEIGHT_PLAN_INCOMPLETE");
    }

    @Test
    void applyPersistsProvenanceAndReturnsSameStoredResponseOnReplay() {
        var preview = service.preview(actor, PROGRAM,
            preview(Set.of(ReviewerRole.REVIEWER, ReviewerRole.CHECKER)));
        var request = new ReviewerLineApplyRequest(preview.previewHash(), List.of(PARTICIPANT),
            Set.of(ReviewerRole.REVIEWER, ReviewerRole.CHECKER), "approved");

        var first = service.apply(actor, PROGRAM, request);

        assertThat(first.applied()).isEqualTo(1);
        assertThat(first.rows().getFirst().status()).isEqualTo(ReviewerLineApplyStatus.APPLIED);
        assertThat(first.rows().getFirst().reviewerAssignmentIds()).hasSize(2);
        ArgumentCaptor<ProgramReviewerAssignment> assignmentCaptor =
            ArgumentCaptor.forClass(ProgramReviewerAssignment.class);
        verify(reviewers, times(2)).save(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getAllValues())
            .allSatisfy(saved -> {
                assertThat(saved.getAssignmentOrigin()).isEqualTo(ProgramTypes.ReviewerAssignmentOrigin.HCM_MANAGER);
                assertThat(saved.getSourceAssignmentId()).isEqualTo(ASSIGNMENT);
                assertThat(saved.getSourceVersion()).isEqualTo(10_000_001L);
            });
        ArgumentCaptor<ProgramReviewerLineRun> runCaptor = ArgumentCaptor.forClass(ProgramReviewerLineRun.class);
        verify(runs).save(runCaptor.capture());
        when(runs.findByTenantIdAndProgramIdAndPreviewHash(TENANT, PROGRAM, preview.previewHash()))
            .thenReturn(Optional.of(runCaptor.getValue()));

        var replay = service.apply(actor, PROGRAM, request);

        assertThat(replay).isEqualTo(first);
        verify(reviewers, times(2)).save(any());
    }

    @Test
    void stalePreviewFailsBeforeReviewerMutation() {
        var request = new ReviewerLineApplyRequest("0".repeat(64), List.of(PARTICIPANT),
            Set.of(ReviewerRole.REVIEWER), "approved");

        assertThatThrownBy(() -> service.apply(actor, PROGRAM, request))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).errorCode().code()).isEqualTo("E9804941"));
        verify(reviewers, never()).save(any());
        verify(runs, never()).saveAndFlush(any());
    }

    private ReviewerLinePreviewRequest preview(Set<ReviewerRole> roles) {
        return new ReviewerLinePreviewRequest(List.of(PARTICIPANT), roles);
    }

    private static RmAssignment assignment(UUID id, UUID employeeId, UUID managerId) {
        RmAssignment value = new RmAssignment();
        value.setId(id);
        value.setTenantId(TENANT);
        value.setEmployeeId(employeeId);
        value.setManagerEmployeeId(managerId);
        value.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        value.setEffectiveTo(LocalDate.of(2026, 12, 31));
        value.setDeleted(false);
        value.setSourceSystem("HCM");
        value.setSourceVersion(10_000_001L);
        value.setSyncedAt(OffsetDateTime.now());
        return value;
    }

    private static RmEmployee employee(UUID id, String name, String status) {
        RmEmployee value = new RmEmployee();
        value.setId(id);
        value.setTenantId(TENANT);
        value.setEmployeeNo("E-" + id.toString().substring(0, 4));
        value.setName(name);
        value.setStatus(status);
        value.setSourceVersion(1L);
        value.setSyncedAt(OffsetDateTime.now());
        return value;
    }
}

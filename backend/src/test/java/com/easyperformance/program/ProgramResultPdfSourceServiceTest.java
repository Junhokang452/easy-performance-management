package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.*;
import com.easyperformance.program.ProgramResultPdfDtos.*;
import com.easyperformance.program.ProgramTypes.*;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgramResultPdfSourceServiceTest {
    @Mock ProgramAccess access;
    @Mock EvaluationProgramRepository programs;
    @Mock ProgramParticipantRepository participants;
    @Mock ProgramAnalyticsService analytics;
    ProgramResultPdfSourceService service;
    Actor actor;
    UUID tenantId;
    UUID programId;
    EvaluationProgram program;

    @BeforeEach
    void setUp() {
        tenantId=UUID.randomUUID();programId=UUID.randomUUID();
        actor=new Actor(UUID.randomUUID(),tenantId,UUID.randomUUID(),"operator","HR_ADMIN");
        program=new EvaluationProgram();program.setId(programId);program.setTenantId(tenantId);program.setName("2026 평가");
        program.setEvaluationYear(2026);program.setKind(EvaluationKind.PERFORMANCE);program.setStatus(ProgramStatus.FINALIZED);program.setDefinitionRevision(3);
        service=new ProgramResultPdfSourceService(access,programs,participants,analytics,
            Clock.fixed(Instant.parse("2026-09-08T01:02:03.123456789Z"),ZoneOffset.UTC));
    }

    @Test
    void loadsBoundedPublishedSnapshotAndCanonicalizesTimestamp() {
        var request=request(PdfOrientation.LANDSCAPE,List.of(PdfSection.SUMMARY,PdfSection.PARTICIPANT_TABLE),
            List.of(PdfColumn.EMPLOYEE_NAME,PdfColumn.DEPARTMENT,PdfColumn.SCORE,PdfColumn.GRADE),"  리포트  ");
        ResultSummaryResponse summary=summary(1);
        when(programs.findByIdAndTenantId(programId,tenantId)).thenReturn(Optional.of(program));
        when(participants.countByTenantIdAndProgramId(tenantId,programId)).thenReturn(1L);
        when(analytics.resultSummary(actor,programId)).thenReturn(summary);

        ProgramResultPdfSource result=service.load(actor,programId,request);

        assertThat(result.request().title()).isEqualTo("리포트");
        assertThat(result.generatedAt()).isEqualTo(Instant.parse("2026-09-08T01:02:03.123456Z"));
        assertThat(result.sourceHash()).matches("[0-9a-f]{64}");
        verify(access).requireOperator(actor);
    }

    @Test
    void rejectsOversizedProgramBeforeLoadingSensitiveResultRows() {
        when(programs.findByIdAndTenantId(programId,tenantId)).thenReturn(Optional.of(program));
        when(participants.countByTenantIdAndProgramId(tenantId,programId)).thenReturn(201L);

        assertThatThrownBy(()->service.load(actor,programId,request(PdfOrientation.PORTRAIT,List.of(PdfSection.SUMMARY),List.of(),null)))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.errorCode()).isEqualTo(ProgramErrorCode.PROGRAM_INVALID));
        verifyNoInteractions(analytics);
    }

    @Test
    void rejectsPortraitColumnOverflowAndDuplicateOptions() {
        assertThatThrownBy(()->service.load(actor,programId,request(PdfOrientation.PORTRAIT,List.of(PdfSection.PARTICIPANT_TABLE),
            List.of(PdfColumn.EMPLOYEE_NO,PdfColumn.EMPLOYEE_NAME,PdfColumn.DEPARTMENT,PdfColumn.POSITION,PdfColumn.SCORE,PdfColumn.GRADE),null)))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.load(actor,programId,request(PdfOrientation.LANDSCAPE,List.of(PdfSection.SUMMARY,PdfSection.SUMMARY),List.of(),null)))
            .isInstanceOf(ApiException.class);
        verifyNoInteractions(programs,participants,analytics);
    }

    @Test
    void rejectsParticipantTableWithoutIdentityAndResultColumn() {
        assertThatThrownBy(()->service.load(actor,programId,request(PdfOrientation.LANDSCAPE,List.of(PdfSection.PARTICIPANT_TABLE),
            List.of(PdfColumn.DEPARTMENT,PdfColumn.POSITION),null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.load(actor,programId,request(PdfOrientation.LANDSCAPE,List.of(PdfSection.PARTICIPANT_TABLE),
            List.of(PdfColumn.EMPLOYEE_NAME,PdfColumn.DEPARTMENT),null))).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsEmptyPublishedResultAfterFinalizedProgramCheck() {
        when(programs.findByIdAndTenantId(programId,tenantId)).thenReturn(Optional.of(program));
        when(participants.countByTenantIdAndProgramId(tenantId,programId)).thenReturn(1L);
        when(analytics.resultSummary(actor,programId)).thenReturn(new ResultSummaryResponse(programId,0,List.of(),List.of()));

        assertThatThrownBy(()->service.load(actor,programId,request(PdfOrientation.PORTRAIT,List.of(PdfSection.SUMMARY),List.of(),null)))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.errorCode()).isEqualTo(ProgramErrorCode.RESULT_NOT_PUBLISHED));
    }

    @Test
    void sourceHashUsesUnambiguousLengthPrefixedFields() {
        UUID participantId=UUID.randomUUID(),employeeId=UUID.randomUUID();
        ResultSummaryResponse first=summary(participantId,new ParticipantAttributes(employeeId,"a:b","c",null,null,"dept",null,null,null,null));
        ResultSummaryResponse second=summary(participantId,new ParticipantAttributes(employeeId,"a","b:c",null,null,"dept",null,null,null,null));
        when(programs.findByIdAndTenantId(programId,tenantId)).thenReturn(Optional.of(program));
        when(participants.countByTenantIdAndProgramId(tenantId,programId)).thenReturn(1L);
        when(analytics.resultSummary(actor,programId)).thenReturn(first,second);
        var request=request(PdfOrientation.PORTRAIT,List.of(PdfSection.SUMMARY),List.of(),null);

        String firstHash=service.load(actor,programId,request).sourceHash();
        String secondHash=service.load(actor,programId,request).sourceHash();

        assertThat(firstHash).isNotEqualTo(secondHash);
    }

    private ProgramResultPdfRequest request(PdfOrientation orientation,List<PdfSection> sections,List<PdfColumn> columns,String title){
        return new ProgramResultPdfRequest(PdfLocale.ko,orientation,sections,columns,title);
    }
    private ResultSummaryResponse summary(int count){
        ParticipantAttributes employee=new ParticipantAttributes(UUID.randomUUID(),"E001","김성과",null,null,"성과팀","LEAD",null,"ENGINEER","REGULAR");
        return summary(UUID.randomUUID(),employee);
    }
    private ResultSummaryResponse summary(UUID participantId,ParticipantAttributes employee){
        ResultRow row=new ResultRow(participantId,employee,new BigDecimal("91.25"),"S",true,"DELIVERED");
        return new ResultSummaryResponse(programId,1,List.of(new GradeCount("S",1)),List.of(row));
    }
}

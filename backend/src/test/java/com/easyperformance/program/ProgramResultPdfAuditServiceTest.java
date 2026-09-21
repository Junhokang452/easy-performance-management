package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.*;
import com.easyperformance.program.ProgramResultPdfDtos.*;
import com.easyperformance.program.ProgramTypes.*;
import com.easyperformance.workflow.ActorAccess.Actor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProgramResultPdfAuditServiceTest {
    @Mock ProgramAuditService audit;

    @Test
    void auditContainsMetadataButNoTitleOrParticipantResultValues() {
        UUID tenant=UUID.randomUUID(),program=UUID.randomUUID();Actor actor=new Actor(UUID.randomUUID(),tenant,UUID.randomUUID(),"HR","HR_ADMIN");
        ParticipantAttributes employee=new ParticipantAttributes(UUID.randomUUID(),"SECRET-NO","SECRET-NAME",null,null,"SECRET-DEPT",null,null,null,null);
        ResultSummaryResponse summary=new ResultSummaryResponse(program,1,List.of(new GradeCount("SECRET-GRADE",1)),
            List.of(new ResultRow(UUID.randomUUID(),employee,new BigDecimal("99.99"),"SECRET-GRADE",true,"DELIVERED")));
        ProgramResultPdfRequest request=new ProgramResultPdfRequest(PdfLocale.ko,PdfOrientation.PORTRAIT,List.of(PdfSection.SUMMARY),List.of(),"SECRET-TITLE");
        ProgramResultPdfSource source=new ProgramResultPdfSource(program,"SECRET-PROGRAM",2026,EvaluationKind.PERFORMANCE,1,1,Instant.parse("2026-09-08T00:00:00Z"),"a".repeat(64),request,summary);
        ProgramResultPdfFile file=new ProgramResultPdfFile(new byte[]{1,2,3},"evaluation-results-"+program+".pdf",source.sourceHash(),source.generatedAt());

        new ProgramResultPdfAuditService(audit).record(actor,source,file);

        ArgumentCaptor<Object> details=ArgumentCaptor.forClass(Object.class);
        verify(audit).record(org.mockito.ArgumentMatchers.eq(actor),org.mockito.ArgumentMatchers.eq(program),org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq(ProgramEventType.RESULT_PDF_EXPORTED),org.mockito.ArgumentMatchers.isNull(),details.capture());
        String serialized=String.valueOf(details.getValue());
        assertThat(details.getValue()).isInstanceOf(Map.class);
        assertThat(serialized).doesNotContain("SECRET-TITLE","SECRET-NAME","SECRET-NO","SECRET-GRADE","99.99","SECRET-PROGRAM");
        assertThat(serialized).contains("PROGRAM_RESULT_PDF_V1","participantCount=1","byteCount=3");
    }
}

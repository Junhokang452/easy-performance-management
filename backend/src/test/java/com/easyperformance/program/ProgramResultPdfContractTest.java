package com.easyperformance.program;

import com.easyperformance.program.ProgramResultPdfDtos.*;
import com.easyperformance.workflow.ActorAccess.Actor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgramResultPdfContractTest {
    @Mock ProgramResultPdfSourceService sources;
    @Mock ProgramResultPdfRenderer renderer;
    @Mock ProgramResultPdfAuditService audit;

    @Test
    void controllerMappingKeepsApprovedResultsDotPdfWire() throws Exception {
        Method method=EvaluationProgramController.class.getDeclaredMethod("resultsPdf",UUID.class,ProgramResultPdfRequest.class);
        PostMapping mapping=method.getAnnotation(PostMapping.class);

        assertThat(mapping.value()).containsExactly("/{id}/results.pdf");
        assertThat(mapping.consumes()).containsExactly(MediaType.APPLICATION_JSON_VALUE);
        assertThat(mapping.produces()).containsExactly(MediaType.APPLICATION_PDF_VALUE);
    }

    @Test
    void exportAuditsOnlySuccessfullyRenderedFiles() {
        ProgramResultPdfExportService service=new ProgramResultPdfExportService(sources,renderer,audit);
        Actor actor=new Actor(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"HR","HR_ADMIN");UUID programId=UUID.randomUUID();
        ProgramResultPdfRequest request=new ProgramResultPdfRequest(PdfLocale.en,PdfOrientation.PORTRAIT,List.of(PdfSection.SUMMARY),List.of(),null);
        ProgramResultPdfSource source=mock(ProgramResultPdfSource.class);
        when(source.sourceHash()).thenReturn("a".repeat(64));when(source.generatedAt()).thenReturn(Instant.EPOCH);
        when(sources.load(actor,programId,request)).thenReturn(source);when(renderer.render(source)).thenReturn(new byte[]{1,2,3});

        ProgramResultPdfFile file=service.export(actor,programId,request);

        assertThat(file.filename()).isEqualTo("evaluation-results-"+programId+".pdf");assertThat(file.bytes()).containsExactly(1,2,3);
        verify(audit).record(actor,source,file);
    }

    @Test
    void failedRenderingDoesNotWriteSuccessAudit() {
        ProgramResultPdfExportService service=new ProgramResultPdfExportService(sources,renderer,audit);
        Actor actor=new Actor(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"HR","HR_ADMIN");UUID programId=UUID.randomUUID();
        ProgramResultPdfRequest request=new ProgramResultPdfRequest(PdfLocale.en,PdfOrientation.PORTRAIT,List.of(PdfSection.SUMMARY),List.of(),null);
        ProgramResultPdfSource source=mock(ProgramResultPdfSource.class);
        when(sources.load(actor,programId,request)).thenReturn(source);when(renderer.render(source)).thenThrow(new IllegalStateException("render failure"));

        assertThatThrownBy(()->service.export(actor,programId,request)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(audit);
    }
}

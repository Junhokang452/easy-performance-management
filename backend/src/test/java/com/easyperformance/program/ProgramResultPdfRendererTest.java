package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.*;
import com.easyperformance.program.ProgramResultPdfDtos.*;
import com.easyperformance.program.ProgramTypes.EvaluationKind;
import com.easyware.platform.error.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ProgramResultPdfRendererTest {
    private final ProgramResultPdfRenderer renderer=new ProgramResultPdfRenderer();

    @Test
    void rendersKoreanEnglishPortraitLandscapeAndBoundedMultipageEvidence() throws IOException {
        byte[] koPortrait=renderer.render(source(PdfLocale.ko,PdfOrientation.PORTRAIT,2,"2026 평가 결과"));
        byte[] koLandscape=renderer.render(source(PdfLocale.ko,PdfOrientation.LANDSCAPE,2,"조직 성과 평가"));
        byte[] enLandscape=renderer.render(source(PdfLocale.en,PdfOrientation.LANDSCAPE,2,"Evaluation results"));
        byte[] multipage=renderer.render(source(PdfLocale.ko,PdfOrientation.LANDSCAPE,200,"긴 제목 평가 결과 보고서 반복 텍스트 반복 텍스트 반복 텍스트"));

        assertPdf(koPortrait,1,"2026 평가 결과");assertPdf(koLandscape,1,"조직 성과 평가");
        assertPdf(enLandscape,1,"Evaluation results");assertPdf(multipage,2,"기밀");
        assertThat(multipage.length).isLessThanOrEqualTo(ProgramResultPdfRenderer.MAX_BYTES);
        writeEvidence("ko-portrait.pdf",koPortrait);writeEvidence("ko-landscape.pdf",koLandscape);
        writeEvidence("en-landscape.pdf",enLandscape);writeEvidence("ko-multipage-200.pdf",multipage);
    }

    @Test
    void rejectsUnsupportedUnicodeInsteadOfUsingCidAsUnicodeGlyphCheck() {
        assertThatThrownBy(()->renderer.render(source(PdfLocale.ko,PdfOrientation.PORTRAIT,1,"unsupported \ud83e\uddea")))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.errorCode()).isEqualTo(ProgramErrorCode.PROGRAM_INVALID));
    }

    @Test
    void keepsSummaryMetadataAboveFooterAfterManyGradeRows() throws IOException {
        ProgramResultPdfSource base=source(PdfLocale.en,PdfOrientation.PORTRAIT,1,"Many grades");
        List<GradeCount> grades=new ArrayList<>();for(int index=0;index<200;index++)grades.add(new GradeCount("G"+index,1));
        ResultSummaryResponse summary=new ResultSummaryResponse(base.programId(),200,grades,base.summary().rows());
        ProgramResultPdfRequest request=new ProgramResultPdfRequest(PdfLocale.en,PdfOrientation.PORTRAIT,List.of(PdfSection.SUMMARY),List.of(),"Many grades");
        byte[] bytes=renderer.render(new ProgramResultPdfSource(base.programId(),base.programName(),base.evaluationYear(),base.kind(),base.programRowVersion(),base.definitionRevision(),base.generatedAt(),base.sourceHash(),request,summary));

        try(PDDocument document=Loader.loadPDF(bytes)){
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PDFTextStripper().getText(document)).contains("Generated (UTC): 2026-09-08T03:00:00Z");
        }
    }

    @Test
    void tableSeparatorLeavesClearanceFromBothAdjacentRows() {
        float baseline=100f,rowHeight=ProgramResultPdfRenderer.rowHeight(2);
        float separator=ProgramResultPdfRenderer.separatorY(baseline,rowHeight);

        assertThat(baseline-10-separator).isEqualTo(6f);
        assertThat(separator-(baseline-rowHeight)).isEqualTo(12f);
    }

    private void assertPdf(byte[] bytes,int minimumPages,String text) throws IOException {
        assertThat(bytes).startsWith("%PDF-".getBytes());
        try(PDDocument document=Loader.loadPDF(bytes)){
            assertThat(document.getNumberOfPages()).isBetween(minimumPages,ProgramResultPdfRenderer.MAX_PAGES);
            assertThat(new PDFTextStripper().getText(document)).contains(text,ProgramResultPdfDtos.POLICY_VERSION);
        }
    }

    private void writeEvidence(String filename,byte[] bytes) throws IOException {
        String directory=System.getProperty("pdfEvidenceDir");if(directory==null||directory.isBlank())directory=System.getenv("PDF_EVIDENCE_DIR");
        if(directory==null||directory.isBlank())return;
        Path path=Path.of(directory);Files.createDirectories(path);Files.write(path.resolve(filename),bytes);
    }

    private ProgramResultPdfSource source(PdfLocale locale,PdfOrientation orientation,int rows,String title){
        UUID programId=UUID.randomUUID();List<ResultRow> resultRows=new ArrayList<>();
        for(int index=1;index<=rows;index++){
            String suffix=String.format("%03d",index);String longText="긴 조직명과 직무 정보가 표의 셀 크기를 넘어가는 경우 생략 표시 검증 반복 텍스트 "+suffix;
            ParticipantAttributes employee=new ParticipantAttributes(UUID.randomUUID(),"E"+suffix,"평가대상자 이름 장문 검증 "+suffix,null,null,longText,"LEAD "+suffix,null,"ENGINEER "+suffix,"REGULAR");
            resultRows.add(new ResultRow(UUID.randomUUID(),employee,new BigDecimal("91.25"),index%2==0?"S":"A",true,"DELIVERED"));
        }
        ResultSummaryResponse summary=new ResultSummaryResponse(programId,rows,List.of(new GradeCount("S",rows/2),new GradeCount("A",rows-rows/2)),resultRows);
        List<PdfColumn> columns=orientation==PdfOrientation.PORTRAIT
            ?List.of(PdfColumn.EMPLOYEE_NO,PdfColumn.EMPLOYEE_NAME,PdfColumn.DEPARTMENT,PdfColumn.SCORE,PdfColumn.GRADE)
            :List.of(PdfColumn.EMPLOYEE_NO,PdfColumn.EMPLOYEE_NAME,PdfColumn.DEPARTMENT,PdfColumn.POSITION,PdfColumn.JOB,PdfColumn.SCORE,PdfColumn.GRADE,PdfColumn.FEEDBACK_STATUS);
        ProgramResultPdfRequest request=new ProgramResultPdfRequest(locale,orientation,List.of(PdfSection.SUMMARY,PdfSection.PARTICIPANT_TABLE),columns,title);
        return new ProgramResultPdfSource(programId,"전사 성과관리 프로그램 이름 장문 검증",2026,EvaluationKind.PERFORMANCE,7,4,
            Instant.parse("2026-09-08T03:00:00Z"),"a".repeat(64),request,summary);
    }
}

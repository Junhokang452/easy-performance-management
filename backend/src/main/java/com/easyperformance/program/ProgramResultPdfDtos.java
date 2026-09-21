package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.ResultSummaryResponse;
import com.easyperformance.program.ProgramTypes.EvaluationKind;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProgramResultPdfDtos {
    private ProgramResultPdfDtos() {}

    public static final String POLICY_VERSION="PROGRAM_RESULT_PDF_V1";
    public enum PdfLocale { ko, en }
    public enum PdfOrientation { PORTRAIT, LANDSCAPE }
    public enum PdfSection { SUMMARY, PARTICIPANT_TABLE }
    public enum PdfColumn { EMPLOYEE_NO, EMPLOYEE_NAME, DEPARTMENT, POSITION, JOB, SCORE, GRADE, FEEDBACK_STATUS }

    public record ProgramResultPdfRequest(
        @NotNull PdfLocale locale,
        @NotNull PdfOrientation orientation,
        @NotEmpty @Size(max=2) List<@NotNull PdfSection> sections,
        @NotNull @Size(max=8) List<@NotNull PdfColumn> participantColumns,
        @Size(max=100) String title) {}

    record ProgramResultPdfSource(UUID programId,String programName,Integer evaluationYear,EvaluationKind kind,
                                  long programRowVersion,int definitionRevision,Instant generatedAt,String sourceHash,
                                  ProgramResultPdfRequest request,ResultSummaryResponse summary) {}

    record ProgramResultPdfFile(byte[] bytes,String filename,String sourceHash,Instant generatedAt) {}
}

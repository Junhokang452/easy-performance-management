package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.ParticipantAttributes;
import com.easyperformance.program.ProgramDtos.ResultRow;
import com.easyperformance.program.ProgramDtos.ResultSummaryResponse;
import com.easyperformance.program.ProgramResultPdfDtos.*;
import com.easyperformance.program.ProgramTypes.ProgramStatus;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ProgramResultPdfSourceService {
    static final int MAX_PARTICIPANTS=200;
    private final ProgramAccess access;
    private final EvaluationProgramRepository programs;
    private final ProgramParticipantRepository participants;
    private final ProgramAnalyticsService analytics;
    private final Clock clock;

    @Autowired
    public ProgramResultPdfSourceService(ProgramAccess access,EvaluationProgramRepository programs,
        ProgramParticipantRepository participants,ProgramAnalyticsService analytics){
        this(access,programs,participants,analytics,Clock.systemUTC());
    }

    ProgramResultPdfSourceService(ProgramAccess access,EvaluationProgramRepository programs,
        ProgramParticipantRepository participants,ProgramAnalyticsService analytics,Clock clock){
        this.access=access;this.programs=programs;this.participants=participants;this.analytics=analytics;this.clock=clock;
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ProgramResultPdfSource load(Actor actor,UUID programId,ProgramResultPdfRequest request){
        access.requireOperator(actor);ProgramResultPdfRequest normalized=validate(request);
        EvaluationProgram program=programs.findByIdAndTenantId(programId,actor.tenantId())
            .orElseThrow(()->new ApiException(ProgramErrorCode.PROGRAM_NOT_FOUND));
        if(program.getStatus()!=ProgramStatus.FINALIZED)throw new ApiException(ProgramErrorCode.RESULT_NOT_PUBLISHED);
        long total=participants.countByTenantIdAndProgramId(actor.tenantId(),programId);
        if(total>MAX_PARTICIPANTS)throw invalid("PDF_PARTICIPANT_LIMIT",Map.of("maximum",MAX_PARTICIPANTS,"actual",total));
        ResultSummaryResponse summary=analytics.resultSummary(actor,programId);
        if(summary.rows().isEmpty())throw new ApiException(ProgramErrorCode.RESULT_NOT_PUBLISHED);
        if(summary.rows().size()>MAX_PARTICIPANTS)throw invalid("PDF_PARTICIPANT_LIMIT",Map.of("maximum",MAX_PARTICIPANTS,"actual",summary.rows().size()));
        Instant generatedAt=Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        String hash=sourceHash(actor.tenantId(),program,normalized,summary);
        return new ProgramResultPdfSource(programId,program.getName(),program.getEvaluationYear(),program.getKind(),
            program.getRowVersion(),program.getDefinitionRevision(),generatedAt,hash,normalized,summary);
    }

    private static ProgramResultPdfRequest validate(ProgramResultPdfRequest request){
        if(request==null||request.locale()==null||request.orientation()==null||request.sections()==null||request.participantColumns()==null)
            throw invalid("PDF_OPTIONS_INVALID",Map.of());
        validateUnique(request.sections(),"sections");validateUnique(request.participantColumns(),"participantColumns");
        if(request.sections().isEmpty()||request.sections().size()>2||request.participantColumns().size()>8)
            throw invalid("PDF_OPTIONS_INVALID",Map.of());
        boolean table=request.sections().contains(PdfSection.PARTICIPANT_TABLE);
        if(table){
            if(request.participantColumns().isEmpty()||!request.participantColumns().contains(PdfColumn.EMPLOYEE_NAME)
                ||request.participantColumns().stream().noneMatch(c->c==PdfColumn.SCORE||c==PdfColumn.GRADE))
                throw invalid("PDF_COLUMNS_INVALID",Map.of());
            int maximum=request.orientation()==PdfOrientation.PORTRAIT?5:8;
            if(request.participantColumns().size()>maximum)throw invalid("PDF_COLUMNS_INVALID",Map.of("maximum",maximum));
        }else if(!request.participantColumns().isEmpty())throw invalid("PDF_COLUMNS_INVALID",Map.of());
        String title=request.title();
        if(title!=null){title=title.trim();if(title.isEmpty()||title.codePointCount(0,title.length())>100||title.codePoints().anyMatch(Character::isISOControl))throw invalid("PDF_TITLE_INVALID",Map.of());}
        return new ProgramResultPdfRequest(request.locale(),request.orientation(),List.copyOf(request.sections()),List.copyOf(request.participantColumns()),title);
    }

    private static <T> void validateUnique(List<T> values,String field){
        if(values.stream().anyMatch(Objects::isNull)||new HashSet<>(values).size()!=values.size())throw invalid("PDF_OPTIONS_INVALID",Map.of("field",field));
    }

    private static String sourceHash(UUID tenantId,EvaluationProgram program,ProgramResultPdfRequest request,ResultSummaryResponse summary){
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
            field(out,tenantId);field(out,program.getId());field(out,program.getName());field(out,program.getEvaluationYear());
            field(out,program.getKind());out.writeLong(program.getRowVersion());out.writeInt(program.getDefinitionRevision());
            field(out,request.locale());field(out,request.orientation());list(out,request.sections());list(out,request.participantColumns());field(out,request.title());
            out.writeLong(summary.finalizedCount());out.writeInt(summary.grades().size());
            for(var row:summary.grades()){field(out,row.grade());out.writeLong(row.count());}
            out.writeInt(summary.rows().size());
            for(ResultRow row:summary.rows()){
                ParticipantAttributes employee=row.employee();field(out,row.participantId());field(out,employee.employeeNo());field(out,employee.name());
                field(out,employee.orgUnitName());field(out,employee.positionCode());field(out,employee.jobCode());field(out,decimal(row.score()));
                field(out,row.grade());field(out,row.feedbackStatus());
            }
            out.flush();return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch(Exception exception) { throw new IllegalStateException(exception); }
    }

    private static String decimal(BigDecimal value){return value==null?"":value.stripTrailingZeros().toPlainString();}
    private static void list(DataOutputStream out,List<?> values)throws IOException{out.writeInt(values.size());for(Object value:values)field(out,value);}
    private static void field(DataOutputStream out,Object value)throws IOException{
        if(value==null){out.writeInt(-1);return;}byte[] encoded=String.valueOf(value).getBytes(StandardCharsets.UTF_8);out.writeInt(encoded.length);out.write(encoded);
    }
    private static ApiException invalid(String reason,Map<String,Object> extra){Map<String,Object> details=new LinkedHashMap<>(extra);details.put("reason",reason);return new ApiException(ProgramErrorCode.PROGRAM_INVALID,details);}
}

package com.easyperformance.program;

import com.easyperformance.program.ProgramResultPdfDtos.*;
import com.easyperformance.workflow.ActorAccess.Actor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProgramResultPdfExportService {
    private final ProgramResultPdfSourceService sources;
    private final ProgramResultPdfRenderer renderer;
    private final ProgramResultPdfAuditService audit;
    public ProgramResultPdfExportService(ProgramResultPdfSourceService sources,ProgramResultPdfRenderer renderer,ProgramResultPdfAuditService audit){this.sources=sources;this.renderer=renderer;this.audit=audit;}

    public ProgramResultPdfFile export(Actor actor,UUID programId,ProgramResultPdfRequest request){
        ProgramResultPdfSource source=sources.load(actor,programId,request);byte[] bytes=renderer.render(source);
        ProgramResultPdfFile file=new ProgramResultPdfFile(bytes,"evaluation-results-"+programId+".pdf",source.sourceHash(),source.generatedAt());
        audit.record(actor,source,file);return file;
    }
}

package com.easyperformance.program;

import com.easyperformance.program.ProgramResultPdfDtos.ProgramResultPdfFile;
import com.easyperformance.program.ProgramResultPdfDtos.ProgramResultPdfSource;
import com.easyperformance.program.ProgramTypes.ProgramEventType;
import com.easyperformance.workflow.ActorAccess.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ProgramResultPdfAuditService {
    private final ProgramAuditService audit;
    public ProgramResultPdfAuditService(ProgramAuditService audit){this.audit=audit;}

    @Transactional
    public void record(Actor actor,ProgramResultPdfSource source,ProgramResultPdfFile file){
        audit.record(actor,source.programId(),null,ProgramEventType.RESULT_PDF_EXPORTED,null,Map.of(
            "policyVersion",ProgramResultPdfDtos.POLICY_VERSION,"sourceHash",source.sourceHash(),
            "locale",source.request().locale(),"orientation",source.request().orientation(),
            "sections",source.request().sections(),"participantColumns",source.request().participantColumns(),
            "participantCount",source.summary().rows().size(),"byteCount",file.bytes().length,
            "generatedAt",source.generatedAt()));
    }
}

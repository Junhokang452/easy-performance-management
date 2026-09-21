package com.easyperformance.program;

import com.easyperformance.program.ProgramTypes.AssignmentStatus;
import com.easyperformance.program.ProgramTypes.ParticipantStatus;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ProgramAccess {
    private final EvaluationProgramRepository programs;
    private final ProgramParticipantRepository participants;
    private final ProgramReviewerAssignmentRepository reviewers;
    public ProgramAccess(EvaluationProgramRepository programs,ProgramParticipantRepository participants,ProgramReviewerAssignmentRepository reviewers){this.programs=programs;this.participants=participants;this.reviewers=reviewers;}
    public boolean operator(Actor a){return "HR_ADMIN".equals(a.role())||"SUPER_ADMIN".equals(a.role());}
    public void requireOperator(Actor a){if(!operator(a))throw forbidden(a,"operator");}
    public EvaluationProgram program(Actor a,UUID id){EvaluationProgram p=programs.findByIdAndTenantId(id,a.tenantId()).orElseThrow(()->new ApiException(ProgramErrorCode.PROGRAM_NOT_FOUND));if(operator(a)||(p.getStatus()!=ProgramTypes.ProgramStatus.DRAFT&&p.getStatus()!=ProgramTypes.ProgramStatus.CANCELLED&&canRead(a,p)))return p;throw forbidden(a,"open-program-member-or-assignee");}
    public EvaluationProgram lockedProgram(Actor a,UUID id){requireOperator(a);return programs.findLocked(id,a.tenantId()).orElseThrow(()->new ApiException(ProgramErrorCode.PROGRAM_NOT_FOUND));}
    public ProgramParticipant participant(Actor a,UUID id){ProgramParticipant p=participants.findByIdAndTenantId(id,a.tenantId()).orElseThrow(()->new ApiException(ProgramErrorCode.PARTICIPANT_NOT_FOUND));if(operator(a))return p;EvaluationProgram program=programs.findByIdAndTenantId(p.getProgramId(),a.tenantId()).orElseThrow(()->new ApiException(ProgramErrorCode.PROGRAM_NOT_FOUND));if(program.getStatus()!=ProgramTypes.ProgramStatus.DRAFT&&program.getStatus()!=ProgramTypes.ProgramStatus.CANCELLED&&(self(a,p)||assigned(a,p.getProgramId(),p.getId())))return p;throw forbidden(a,"open-participant-owner-or-assignee");}
    public ProgramParticipant self(Actor a,UUID programId){List<ProgramParticipant> rows=selfParticipants(a,programId);if(rows.isEmpty())throw new ApiException(ProgramErrorCode.PARTICIPANT_NOT_FOUND);if(rows.size()>1)throw new ProgramRuleViolation("PARTICIPANT_SELECTION_REQUIRED","participantId is required when an employee has multiple assignments");return rows.getFirst();}
    public ProgramParticipant self(Actor a,UUID programId,UUID participantId){return selfParticipants(a,programId).stream().filter(p->p.getId().equals(participantId)).findFirst().orElseThrow(()->new ApiException(ProgramErrorCode.PARTICIPANT_NOT_FOUND));}
    public List<ProgramParticipant> selfParticipants(Actor a,UUID programId){if(a.employeeId()==null)throw forbidden(a,"employee-binding");EvaluationProgram program=programs.findByIdAndTenantId(programId,a.tenantId()).orElseThrow(()->new ApiException(ProgramErrorCode.PROGRAM_NOT_FOUND));if(program.getStatus()==ProgramTypes.ProgramStatus.DRAFT||program.getStatus()==ProgramTypes.ProgramStatus.CANCELLED)throw forbidden(a,"open-program");return participants.findAllByTenantIdAndEmployeeIdAndStatusOrderByCreatedAtDesc(a.tenantId(),a.employeeId(),ParticipantStatus.ACTIVE).stream().filter(p->p.getProgramId().equals(programId)).toList();}
    public boolean self(Actor a,ProgramParticipant p){return a.employeeId()!=null&&a.employeeId().equals(p.getEmployeeId());}
    public boolean assigned(Actor a,UUID programId,UUID participantId){return a.employeeId()!=null&&reviewers.findAllByTenantIdAndProgramIdAndReviewerEmployeeIdAndStatusNot(a.tenantId(),programId,a.employeeId(),AssignmentStatus.REVOKED).stream().anyMatch(r->r.getParticipantId().equals(participantId));}
    public boolean canReadParticipant(Actor a,ProgramParticipant p){return operator(a)||self(a,p)||assigned(a,p.getProgramId(),p.getId());}
    private boolean canRead(Actor a,EvaluationProgram p){if(a.employeeId()==null)return false;return participants.findAllByTenantIdAndEmployeeIdAndStatusOrderByCreatedAtDesc(a.tenantId(),a.employeeId(),ParticipantStatus.ACTIVE).stream().anyMatch(x->x.getProgramId().equals(p.getId()))||!reviewers.findAllByTenantIdAndProgramIdAndReviewerEmployeeIdAndStatusNot(a.tenantId(),p.getId(),a.employeeId(),AssignmentStatus.REVOKED).isEmpty();}
    private ApiException forbidden(Actor a,String required){return new ApiException(ProgramErrorCode.PROGRAM_FORBIDDEN,Map.of("required",required,"role",a.role()));}
}

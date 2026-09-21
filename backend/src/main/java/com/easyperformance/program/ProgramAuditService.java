package com.easyperformance.program;
import com.easyperformance.program.ProgramTypes.ProgramEventType;import com.easyperformance.workflow.ActorAccess.Actor;import org.springframework.stereotype.Service;import java.util.*;
@Service
public class ProgramAuditService{
 private final ProgramAuditEventRepository events;private final ProgramJson json;
 public ProgramAuditService(ProgramAuditEventRepository events,ProgramJson json){this.events=events;this.json=json;}
 public void record(Actor actor,UUID programId,UUID participantId,ProgramEventType type,String reason,Object details){ProgramAuditEvent e=new ProgramAuditEvent();e.setTenantId(actor.tenantId());e.setProgramId(programId);e.setParticipantId(participantId);e.setEventType(type);e.setReason(reason==null?type.name():reason);e.setActorEmployeeId(actor.employeeId());e.setDetailsJson(json.write(details==null?Map.of():details));events.save(e);}
}

package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.ParticipantAttributes;
import com.easyperformance.program.ProgramDtos.ProgramConfiguration;
import com.easyperformance.program.ProgramDtos.RevieweeGroupInput;
import com.easyperformance.program.ProgramDtos.StageDefinitionInput;
import com.easyperformance.program.ProgramReminderDtos.*;
import com.easyperformance.program.ProgramTypes.*;
import com.easyperformance.readmodel.entity.RmEmployee;
import com.easyperformance.readmodel.repository.RmEmployeeRepository;
import com.easyperformance.workflow.ActorAccess.Actor;
import com.easyware.platform.error.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.easyperformance.program.ProgramReminderDtos.POLICY_VERSION;

@Service
public class ProgramReminderService {
    private static final Set<ProgramStage> SUPPORTED=EnumSet.of(ProgramStage.GOAL,ProgramStage.INTERMEDIATE,
        ProgramStage.SELF_REVIEW,ProgramStage.REVIEW,ProgramStage.CALIBRATION,ProgramStage.FEEDBACK);

    private final EvaluationProgramRepository programs; private final ProgramParticipantRepository participants;
    private final ProgramReviewerAssignmentRepository reviewers; private final ProgramGoalRepository goals;
    private final ProgramReviewSubmissionRepository submissions; private final ProgramIntermediateReviewRepository intermediate;
    private final ProgramCalculationRepository calculations; private final ProgramAdjustmentRepository adjustments;
    private final ProgramFeedbackRepository feedback; private final RmEmployeeRepository employees;
    private final ProgramNotificationRepository notifications; private final ProgramReminderRunRepository runs;
    private final ProgramAccess access; private final ProgramJson json; private final ProgramAuditService audit; private final Clock clock;

    @Autowired
    public ProgramReminderService(EvaluationProgramRepository programs,ProgramParticipantRepository participants,
        ProgramReviewerAssignmentRepository reviewers,ProgramGoalRepository goals,
        ProgramReviewSubmissionRepository submissions,ProgramIntermediateReviewRepository intermediate,
        ProgramCalculationRepository calculations,ProgramAdjustmentRepository adjustments,
        ProgramFeedbackRepository feedback,RmEmployeeRepository employees,
        ProgramNotificationRepository notifications,ProgramReminderRunRepository runs,
        ProgramAccess access,ProgramJson json,ProgramAuditService audit){
        this(programs,participants,reviewers,goals,submissions,intermediate,calculations,adjustments,feedback,
            employees,notifications,runs,access,json,audit,Clock.systemUTC());
    }

    ProgramReminderService(EvaluationProgramRepository programs,ProgramParticipantRepository participants,
        ProgramReviewerAssignmentRepository reviewers,ProgramGoalRepository goals,
        ProgramReviewSubmissionRepository submissions,ProgramIntermediateReviewRepository intermediate,
        ProgramCalculationRepository calculations,ProgramAdjustmentRepository adjustments,
        ProgramFeedbackRepository feedback,RmEmployeeRepository employees,
        ProgramNotificationRepository notifications,ProgramReminderRunRepository runs,
        ProgramAccess access,ProgramJson json,ProgramAuditService audit,Clock clock){
        this.programs=programs;this.participants=participants;this.reviewers=reviewers;this.goals=goals;
        this.submissions=submissions;this.intermediate=intermediate;this.calculations=calculations;
        this.adjustments=adjustments;this.feedback=feedback;this.employees=employees;
        this.notifications=notifications;this.runs=runs;this.access=access;this.json=json;this.audit=audit;this.clock=clock;
    }

    @Transactional(readOnly=true)
    public ReminderPreviewResponse preview(Actor actor,UUID programId,ReminderPreviewRequest request){
        access.requireOperator(actor);validateScope(request.participantIds(),request.stages());
        EvaluationProgram program=requireOpen(programs.findByIdAndTenantId(programId,actor.tenantId())
            .orElseThrow(()->new ApiException(ProgramErrorCode.PROGRAM_NOT_FOUND)));
        return build(actor,program,request.participantIds(),request.stages(),request.locale(),today()).response();
    }

    @Transactional
    public ReminderQueueResponse queue(Actor actor,UUID programId,ReminderQueueRequest request){
        access.requireOperator(actor);validateScope(request.participantIds(),request.stages());validateUnique(request.candidateKeys(),"candidateKeys");
        EvaluationProgram program=programs.findLocked(programId,actor.tenantId())
            .orElseThrow(()->new ApiException(ProgramErrorCode.PROGRAM_NOT_FOUND));
        String requestHash=requestHash(request);
        ProgramReminderRun prior=runs.findByTenantIdAndProgramIdAndIdempotencyKey(actor.tenantId(),programId,request.idempotencyKey()).orElse(null);
        if(prior!=null){if(!prior.getRequestHash().equals(requestHash))throw new ApiException(ProgramErrorCode.PROGRAM_REMINDER_CONFLICT,Map.of("reason","IDEMPOTENCY_KEY_REUSED"));return json.reminderQueueResponse(prior.getResponseJson());}
        requireOpen(program);
        if(!request.reminderOn().equals(today()))throw stale("REMINDER_DATE_CHANGED");
        BuildResult current=build(actor,program,request.participantIds(),request.stages(),request.locale(),request.reminderOn());
        if(!current.response().previewHash().equals(request.previewHash()))throw stale("SOURCE_CHANGED");
        Map<String,ReminderCandidate> byKey=current.response().candidates().stream().collect(Collectors.toMap(ReminderCandidate::candidateKey,Function.identity()));
        List<ReminderCandidate> selected=request.candidateKeys().stream().map(key->{ReminderCandidate row=byKey.get(key);if(row==null)throw stale("CANDIDATE_CHANGED");if(row.status()==ReminderCandidateStatus.BLOCKED)throw new ApiException(ProgramErrorCode.PROGRAM_INVALID,Map.of("reason","REMINDER_CANDIDATE_BLOCKED","candidateKey",key));return row;}).sorted(CANDIDATE_ORDER).toList();
        try{
            ProgramReminderRun run=new ProgramReminderRun();run.setTenantId(actor.tenantId());run.setProgramId(programId);
            run.setIdempotencyKey(request.idempotencyKey());run.setRequestHash(requestHash);run.setPreviewHash(request.previewHash());
            run.setReminderOn(request.reminderOn());run.setPolicyVersion(POLICY_VERSION);run.setLocale(request.locale());
            run.setReason(request.reason().trim());run.setActorEmployeeId(actor.employeeId());run.setResponseJson("{}");run=runs.saveAndFlush(run);
            int queued=0,duplicate=0;List<ReminderQueueRow> rows=new ArrayList<>();Instant now=Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            for(ReminderCandidate candidate:selected){String dedupe=dedupeKey(candidate.candidateKey(),request.reminderOn());ProgramNotification existing=current.existingByDedupe().get(dedupe);
                ReminderQueueDisposition disposition;
                if(existing!=null){disposition=ReminderQueueDisposition.DUPLICATE_SUPPRESSED;duplicate++;}
                else{existing=notification(actor,program,run,candidate,request,now,dedupe);existing=notifications.saveAndFlush(existing);disposition=ReminderQueueDisposition.QUEUED;queued++;}
                rows.add(queueRow(candidate,existing,disposition));}
            ReminderQueueResponse response=new ReminderQueueResponse(programId,request.reminderOn(),POLICY_VERSION,
                request.previewHash(),request.idempotencyKey(),queued,duplicate,List.copyOf(rows));
            run.setResponseJson(json.write(response));runs.save(run);
            audit.record(actor,programId,null,ProgramEventType.REMINDERS_QUEUED,request.reason().trim(),
                Map.of("reminderOn",request.reminderOn(),"policyVersion",POLICY_VERSION,"previewHash",request.previewHash(),
                    "queued",queued,"duplicateSuppressed",duplicate,"candidateKeys",request.candidateKeys()));
            return response;
        }catch(DataIntegrityViolationException exception){throw new ApiException(ProgramErrorCode.PROGRAM_REMINDER_CONFLICT,Map.of("reason","UNIQUE_CONFLICT"));}
    }

    @Transactional(readOnly=true)
    public Page<ReminderHistoryRow> history(Actor actor,UUID programId,Pageable pageable){
        access.requireOperator(actor);access.program(actor,programId);
        if(pageable.getPageSize()<1||pageable.getPageSize()>100)throw new ApiException(ProgramErrorCode.PROGRAM_INVALID,Map.of("reason","REMINDER_PAGE_INVALID"));
        return notifications.findAllByTenantIdAndProgramIdAndReminderDedupeKeyIsNotNullOrderByCreatedAtDesc(actor.tenantId(),programId,pageable).map(this::historyRow);
    }

    private BuildResult build(Actor actor,EvaluationProgram program,List<UUID> requested,List<ProgramStage> requestedStages,String locale,LocalDate reminderOn){
        validateScope(requested,requestedStages);Set<ProgramStage> stages=stages(requestedStages);List<UUID> ids=requested.stream().sorted().toList();
        List<ProgramParticipant> participantRows=participants.findAllByTenantIdAndProgramIdAndIdIn(actor.tenantId(),program.getId(),ids);
        if(participantRows.size()!=ids.size())throw new ApiException(ProgramErrorCode.PARTICIPANT_NOT_FOUND);
        Map<UUID,ProgramParticipant> participantMap=participantRows.stream().collect(Collectors.toMap(ProgramParticipant::getId,Function.identity()));
        List<ProgramReviewerAssignment> reviewerRows=reviewers.findAllByTenantIdAndProgramIdAndParticipantIdInAndStatusNot(actor.tenantId(),program.getId(),ids,AssignmentStatus.REVOKED);
        List<ProgramGoal> goalRows=goals.findAllByTenantIdAndParticipantIdIn(actor.tenantId(),ids);
        List<ProgramReviewSubmission> submissionRows=submissions.findAllByTenantIdAndProgramIdAndParticipantIdIn(actor.tenantId(),program.getId(),ids);
        List<ProgramIntermediateReview> intermediateRows=intermediate.findAllByTenantIdAndParticipantIdIn(actor.tenantId(),ids);
        List<ProgramCalculation> calculationRows=calculations.findAllByTenantIdAndProgramIdAndParticipantIdIn(actor.tenantId(),program.getId(),ids);
        List<ProgramAdjustment> adjustmentRows=adjustments.findAllByTenantIdAndProgramIdAndParticipantIdIn(actor.tenantId(),program.getId(),ids);
        List<ProgramFeedback> feedbackRows=feedback.findAllByTenantIdAndProgramIdAndParticipantIdIn(actor.tenantId(),program.getId(),ids);
        Sources sources=new Sources(group(reviewerRows,ProgramReviewerAssignment::getParticipantId),group(goalRows,ProgramGoal::getParticipantId),
            group(submissionRows,ProgramReviewSubmission::getParticipantId),unique(intermediateRows,ProgramIntermediateReview::getParticipantId),
            group(calculationRows,ProgramCalculation::getParticipantId),group(adjustmentRows,ProgramAdjustment::getParticipantId),
            unique(feedbackRows,ProgramFeedback::getParticipantId));
        Set<UUID> employeeIds=new LinkedHashSet<>();participantRows.forEach(p->employeeIds.add(p.getEmployeeId()));reviewerRows.forEach(r->employeeIds.add(r.getReviewerEmployeeId()));
        Map<UUID,RmEmployee> employeeMap=employees.findAllByTenantIdAndIdIn(actor.tenantId(),employeeIds).stream().collect(Collectors.toMap(RmEmployee::getId,Function.identity()));
        ProgramConfiguration configuration=json.configuration(program.getDefinitionJson());List<ReminderCandidate> raw=new ArrayList<>();List<ReminderExclusion> exclusions=new ArrayList<>();
        for(UUID id:ids){ProgramParticipant p=participantMap.get(id);evaluate(program,configuration,p,sources,employeeMap,stages,reminderOn,locale,raw,exclusions);}
        raw.sort(CANDIDATE_ORDER);exclusions.sort(Comparator.comparing(ReminderExclusion::participantId));
        String previewHash=previewHash(program,ids,stages,locale,reminderOn,raw,exclusions);
        Map<String,ReminderCandidate> readyByDedupe=raw.stream().filter(r->r.status()==ReminderCandidateStatus.READY)
            .collect(Collectors.toMap(r->dedupeKey(r.candidateKey(),reminderOn),Function.identity(),(a,b)->a,LinkedHashMap::new));
        Map<String,ProgramNotification> existing=readyByDedupe.isEmpty()?Map.of():notifications.findAllByTenantIdAndReminderDedupeKeyIn(actor.tenantId(),readyByDedupe.keySet()).stream().collect(Collectors.toMap(ProgramNotification::getReminderDedupeKey,Function.identity()));
        List<ReminderCandidate> visible=raw.stream().map(r->{ProgramNotification n=existing.get(dedupeKey(r.candidateKey(),reminderOn));return n==null?r:withExisting(r,n);}).toList();
        ReminderSummary summary=new ReminderSummary(ids.size(),count(visible,ReminderCandidateStatus.READY),count(visible,ReminderCandidateStatus.ALREADY_QUEUED),count(visible,ReminderCandidateStatus.BLOCKED),
            (int)exclusions.stream().filter(e->e.status()==ReminderExclusionStatus.COMPLETED).count(),(int)exclusions.stream().filter(e->e.status()==ReminderExclusionStatus.NOT_APPLICABLE).count());
        return new BuildResult(new ReminderPreviewResponse(program.getId(),reminderOn,"UTC",POLICY_VERSION,previewHash,visible,List.copyOf(exclusions),summary),existing);
    }

    private void evaluate(EvaluationProgram program,ProgramConfiguration config,ProgramParticipant p,Sources s,Map<UUID,RmEmployee> employees,
        Set<ProgramStage> stages,LocalDate on,String locale,List<ReminderCandidate> out,List<ReminderExclusion> excluded){
        if(p.getStatus()!=ParticipantStatus.ACTIVE){excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"PARTICIPANT_NOT_ACTIVE"));return;}
        ProgramStage stage=p.getCurrentStage();if(stage==null||!SUPPORTED.contains(stage)){excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"OWNER_UNADDRESSABLE"));return;}
        if(!stages.contains(stage)){excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"STAGE_FILTERED_OUT"));return;}
        if(p.getStageStatus()==ProgramStageStatus.COMPLETED){excluded.add(exclusion(p,ReminderExclusionStatus.COMPLETED,"STAGE_COMPLETED"));return;}
        if(p.getStageStatus()==ProgramStageStatus.SKIPPED){excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"STAGE_SKIPPED"));return;}
        String state=state(program,p,s,employees);StageDefinitionInput definition=effectiveDefinition(config,p,stage);
        if(definition==null){excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"STAGE_FILTERED_OUT"));return;}
        if(p.getStageStatus()!=ProgramStageStatus.IN_PROGRESS){if(p.getStageStatus()==ProgramStageStatus.BLOCKED)out.add(blocked(program,p,defaultAction(config,stage),"STAGE_BLOCKED",definition,on,state));else excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"STAGE_NOT_STARTED"));return;}
        List<ProgramReviewerAssignment> rs=s.reviewers().getOrDefault(p.getId(),List.of());List<ProgramGoal> gs=s.goals().getOrDefault(p.getId(),List.of());List<ProgramReviewSubmission> ss=s.submissions().getOrDefault(p.getId(),List.of());
        switch(stage){
            case GOAL -> evaluateGoal(program,config,p,gs,rs,employees,definition,on,locale,state,out,excluded);
            case INTERMEDIATE -> {ProgramIntermediateReview row=s.intermediate().get(p.getId());if(row!=null&&row.getStatus()==SubmissionStatus.COMPLETED)excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"SOURCE_INCONSISTENT"));else out.add(assigned(program,p,ReminderAction.INTERMEDIATE_CHECK,ReviewerRole.CHECKER,0,rs,employees,definition,on,locale,state));}
            case SELF_REVIEW -> {boolean completed=ss.stream().anyMatch(x->x.getRole()==ReviewerRole.SELF&&x.getRound()==0&&x.getStatus()==SubmissionStatus.COMPLETED);if(completed)excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"SOURCE_INCONSISTENT"));else out.add(self(program,p,ReminderAction.SELF_REVIEW,employees,definition,on,locale,state));}
            case REVIEW -> evaluateReview(program,p,rs,ss,employees,definition,on,locale,state,out,excluded);
            case CALIBRATION -> evaluateCalibration(program,p,rs,s.calculations().getOrDefault(p.getId(),List.of()),s.adjustments().getOrDefault(p.getId(),List.of()),employees,definition,on,locale,state,out,excluded);
            case FEEDBACK -> evaluateFeedback(program,p,rs,s.feedback().get(p.getId()),employees,definition,on,locale,state,out,excluded);
            default -> excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"OWNER_UNADDRESSABLE"));
        }
    }

    private StageDefinitionInput effectiveDefinition(ProgramConfiguration config,ProgramParticipant participant,ProgramStage stage){
        StageDefinitionInput definition=config.stages().stream().filter(x->x.stage()==stage&&x.enabled()).findFirst().orElse(null);
        if(definition==null)return null;
        RevieweeGroupInput group=config.groups().stream().filter(g->g.id().equals(participant.getGroupId())).findFirst().orElse(null);
        if(group==null)return null;
        if(stage==ProgramStage.INTERMEDIATE&&!group.intermediateEnabled())return null;
        if(stage==ProgramStage.SELF_REVIEW&&!group.selfReviewEnabled())return null;
        return definition;
    }

    private void evaluateGoal(EvaluationProgram program,ProgramConfiguration config,ProgramParticipant p,List<ProgramGoal> gs,List<ProgramReviewerAssignment> rs,
        Map<UUID,RmEmployee> employees,StageDefinitionInput definition,LocalDate on,String locale,String state,List<ReminderCandidate> out,List<ReminderExclusion> excluded){
        if(config.goalMode()==GoalMode.SELF_REPORT){if(!gs.isEmpty()&&gs.stream().allMatch(g->g.getStatus()==GoalStatus.SELF_REPORTED))excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"SOURCE_INCONSISTENT"));else out.add(self(program,p,ReminderAction.GOAL_SELF_REPORT,employees,definition,on,locale,state));return;}
        if(!gs.isEmpty()&&gs.stream().allMatch(g->g.getStatus()==GoalStatus.AGREED)){excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"SOURCE_INCONSISTENT"));return;}
        int before=out.size();
        if(gs.isEmpty()||gs.stream().anyMatch(g->g.getStatus()==GoalStatus.DRAFT||g.getStatus()==GoalStatus.RETURNED))out.add(self(program,p,ReminderAction.GOAL_AUTHOR,employees,definition,on,locale,state));
        if(gs.stream().anyMatch(g->g.getStatus()==GoalStatus.AGREEMENT_REQUESTED))out.add(assigned(program,p,ReminderAction.GOAL_APPROVAL,ReviewerRole.AGREEMENT_REVIEWER,0,rs,employees,definition,on,locale,state));
        if(out.size()==before)excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"SOURCE_INCONSISTENT"));
    }

    private void evaluateReview(EvaluationProgram program,ProgramParticipant p,List<ProgramReviewerAssignment> rs,List<ProgramReviewSubmission> ss,
        Map<UUID,RmEmployee> employees,StageDefinitionInput definition,LocalDate on,String locale,String state,List<ReminderCandidate> out,List<ReminderExclusion> excluded){
        List<ProgramReviewerAssignment> slot=slot(rs,ReviewerRole.REVIEWER,p.getCurrentRound());
        if(slot.size()!=1){out.add(assigned(program,p,ReminderAction.REVIEW,ReviewerRole.REVIEWER,p.getCurrentRound(),rs,employees,definition,on,locale,state));return;}
        ProgramReviewerAssignment a=slot.getFirst();ProgramReviewSubmission submission=ss.stream().filter(x->Objects.equals(x.getReviewerAssignmentId(),a.getId())&&x.getRound()==p.getCurrentRound()&&x.getRole()==ReviewerRole.REVIEWER).findFirst().orElse(null);
        if(submission!=null&&submission.getStatus()==SubmissionStatus.COMPLETED&&a.getStatus()==AssignmentStatus.COMPLETED){excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"SOURCE_INCONSISTENT"));return;}
        if(submission!=null&&submission.getStatus()==SubmissionStatus.COMPLETED&&a.getStatus()!=AssignmentStatus.COMPLETED){out.add(blocked(program,p,ReminderAction.REVIEW,"SOURCE_INCONSISTENT",definition,on,state));return;}
        if((submission==null||submission.getStatus()==SubmissionStatus.DRAFT)&&a.getStatus()==AssignmentStatus.COMPLETED){out.add(blocked(program,p,ReminderAction.REVIEW,"SOURCE_INCONSISTENT",definition,on,state));return;}
        out.add(assigned(program,p,ReminderAction.REVIEW,ReviewerRole.REVIEWER,p.getCurrentRound(),rs,employees,definition,on,locale,state));
    }

    private void evaluateCalibration(EvaluationProgram program,ProgramParticipant p,List<ProgramReviewerAssignment> rs,List<ProgramCalculation> cs,List<ProgramAdjustment> as,
        Map<UUID,RmEmployee> employees,StageDefinitionInput definition,LocalDate on,String locale,String state,List<ReminderCandidate> out,List<ReminderExclusion> excluded){
        ProgramCalculation latest=cs.stream().filter(c->c.getStatus()==CalculationStatus.FINAL).max(Comparator.comparingInt(ProgramCalculation::getRevision)).orElse(null);
        if(latest==null){out.add(blocked(program,p,ReminderAction.CALIBRATION,"SOURCE_MISSING",definition,on,state));return;}
        ProgramAdjustment adjustment=as.stream().max(Comparator.comparingInt(ProgramAdjustment::getRevision)).orElse(null);
        if(adjustment!=null&&adjustment.getCalculationId().equals(latest.getId())&&adjustment.getStatus()==AdjustmentStatus.COMPLETED){excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"SOURCE_INCONSISTENT"));return;}
        out.add(assigned(program,p,ReminderAction.CALIBRATION,ReviewerRole.ADJUSTER,0,rs,employees,definition,on,locale,state));
    }

    private void evaluateFeedback(EvaluationProgram program,ProgramParticipant p,List<ProgramReviewerAssignment> rs,ProgramFeedback f,Map<UUID,RmEmployee> employees,
        StageDefinitionInput definition,LocalDate on,String locale,String state,List<ReminderCandidate> out,List<ReminderExclusion> excluded){
        if(f==null||f.getStatus()==FeedbackStatus.DRAFT){out.add(assigned(program,p,ReminderAction.FEEDBACK_DELIVERY,ReviewerRole.FINAL_FEEDBACK,0,rs,employees,definition,on,locale,state));return;}
        if(f.getStatus()==FeedbackStatus.DELIVERED){out.add(self(program,p,ReminderAction.FEEDBACK_ACKNOWLEDGEMENT,employees,definition,on,locale,state));return;}
        if(f.getStatus()==FeedbackStatus.APPEALED){out.add(assigned(program,p,ReminderAction.FEEDBACK_RESOLUTION,ReviewerRole.FINAL_FEEDBACK,0,rs,employees,definition,on,locale,state));return;}
        excluded.add(exclusion(p,ReminderExclusionStatus.NOT_APPLICABLE,"SOURCE_INCONSISTENT"));
    }

    private ReminderCandidate self(EvaluationProgram program,ProgramParticipant p,ReminderAction action,Map<UUID,RmEmployee> employees,StageDefinitionInput definition,LocalDate on,String locale,String state){
        RmEmployee owner=employees.get(p.getEmployeeId());if(owner==null||!active(owner))return blocked(program,p,action,owner==null?"OWNER_MISSING":"OWNER_INACTIVE",definition,on,state);
        return ready(program,p,action,ReminderOwnerKind.SELF,null,null,owner,definition,on,locale,state);
    }
    private ReminderCandidate assigned(EvaluationProgram program,ProgramParticipant p,ReminderAction action,ReviewerRole role,int round,List<ProgramReviewerAssignment> rs,
        Map<UUID,RmEmployee> employees,StageDefinitionInput definition,LocalDate on,String locale,String state){
        List<ProgramReviewerAssignment> slot=slot(rs,role,round);if(slot.isEmpty())return blocked(program,p,action,"OWNER_UNASSIGNED",definition,on,state);if(slot.size()>1)return blocked(program,p,action,"OWNER_AMBIGUOUS",definition,on,state);
        ProgramReviewerAssignment assignment=slot.getFirst();RmEmployee owner=employees.get(assignment.getReviewerEmployeeId());if(owner==null||!active(owner))return blocked(program,p,action,owner==null?"OWNER_MISSING":"OWNER_INACTIVE",definition,on,state);
        return ready(program,p,action,ReminderOwnerKind.ASSIGNMENT,role,assignment,owner,definition,on,locale,state);
    }
    private ReminderCandidate ready(EvaluationProgram program,ProgramParticipant p,ReminderAction action,ReminderOwnerKind kind,ReviewerRole role,ProgramReviewerAssignment assignment,
        RmEmployee owner,StageDefinitionInput definition,LocalDate on,String locale,String state){
        String key=episode(program,p,action,owner.getId(),assignment==null?null:assignment.getId());String link=kind==ReminderOwnerKind.SELF?"/evaluations/"+program.getId():"/admin/evaluation-programs/"+program.getId()+"/participants/"+p.getId()+"/review/"+Math.max(1,p.getCurrentRound());
        String participantName=participantName(p);Rendered rendered=render(locale,program.getName(),participantName,action,definition.endsOn(),link);
        return new ReminderCandidate(key,p.getId(),p.getEmployeeId(),participantName,p.getCurrentStage(),p.getCurrentRound(),action,kind,role,assignment==null?null:assignment.getId(),owner.getId(),owner.getName(),definition.startsOn(),definition.endsOn(),due(definition.endsOn(),on),ReminderCandidateStatus.READY,null,null,rendered.subject(),rendered.body(),link,source(state,owner,assignment,definition));
    }
    private ReminderCandidate blocked(EvaluationProgram program,ProgramParticipant p,ReminderAction action,String reason,StageDefinitionInput definition,LocalDate on,String state){
        String key=episode(program,p,action,null,null);return new ReminderCandidate(key,p.getId(),p.getEmployeeId(),participantName(p),p.getCurrentStage(),p.getCurrentRound(),action,ReminderOwnerKind.UNRESOLVED,null,null,null,null,definition.startsOn(),definition.endsOn(),due(definition.endsOn(),on),ReminderCandidateStatus.BLOCKED,reason,null,null,null,null,hash(state+"|"+reason+"|"+definition.startsOn()+"|"+definition.endsOn()));
    }
    private ReminderCandidate withExisting(ReminderCandidate r,ProgramNotification n){return new ReminderCandidate(r.candidateKey(),r.participantId(),r.participantEmployeeId(),r.participantName(),r.stage(),r.currentRound(),r.action(),r.ownerKind(),r.ownerRole(),r.reviewerAssignmentId(),r.recipientEmployeeId(),r.recipientName(),r.startsOn(),r.dueDate(),r.dueState(),ReminderCandidateStatus.ALREADY_QUEUED,null,n.getId(),n.getSubject(),n.getBody(),n.getDeepLink(),r.sourceFingerprint());}

    private ProgramNotification notification(Actor actor,EvaluationProgram program,ProgramReminderRun run,ReminderCandidate c,ReminderQueueRequest request,Instant now,String dedupe){
        validateRendered(c.subject(),c.body());ProgramNotification n=new ProgramNotification();n.setTenantId(actor.tenantId());n.setProgramId(program.getId());n.setRecipientEmployeeId(c.recipientEmployeeId());n.setChannel(NotificationChannel.IN_APP);n.setStatus(NotificationStatus.SENT);n.setSubject(c.subject());n.setBody(c.body());n.setSentAt(now);n.setReminderRunId(run.getId());n.setReminderIdempotencyKey(request.idempotencyKey());n.setParticipantId(c.participantId());n.setReminderEpisodeKey(c.candidateKey());n.setReminderDedupeKey(dedupe);n.setReminderOn(request.reminderOn());n.setReminderPolicyVersion(POLICY_VERSION);n.setReminderStage(c.stage());n.setReminderAction(c.action());n.setReminderRound(c.currentRound());n.setDeepLink(c.deepLink());n.setSourceSnapshotJson(json.write(Map.of("candidateKey",c.candidateKey(),"participantId",c.participantId(),"stage",c.stage(),"action",c.action(),"currentRound",c.currentRound(),"ownerKind",c.ownerKind(),"recipientEmployeeId",c.recipientEmployeeId(),"sourceFingerprint",c.sourceFingerprint(),"reminderOn",request.reminderOn(),"policyVersion",POLICY_VERSION)));return n;
    }

    private String state(EvaluationProgram program,ProgramParticipant p,Sources s,Map<UUID,RmEmployee> employees){StringBuilder b=new StringBuilder().append(program.getId()).append('|').append(program.getRowVersion()).append('|').append(program.getDefinitionRevision()).append('|').append(p.getId()).append('|').append(p.getRowVersion()).append('|').append(p.getStatus()).append('|').append(p.getCurrentStage()).append('|').append(p.getStageStatus()).append('|').append(p.getCurrentRound());
        sorted(s.goals().getOrDefault(p.getId(),List.of()),ProgramGoal::getId).forEach(x->b.append("|g:").append(x.getId()).append(':').append(x.getRevision()).append(':').append(x.getRowVersion()).append(':').append(x.getStatus()));
        sorted(s.reviewers().getOrDefault(p.getId(),List.of()),ProgramReviewerAssignment::getId).forEach(x->b.append("|r:").append(x.getId()).append(':').append(x.getRole()).append(':').append(x.getRound()).append(':').append(x.getStatus()).append(':').append(x.getRowVersion()));
        sorted(s.submissions().getOrDefault(p.getId(),List.of()),ProgramReviewSubmission::getId).forEach(x->b.append("|s:").append(x.getId()).append(':').append(x.getReviewerAssignmentId()).append(':').append(x.getRole()).append(':').append(x.getRound()).append(':').append(x.getStatus()).append(':').append(x.getRowVersion()));
        ProgramIntermediateReview i=s.intermediate().get(p.getId());if(i!=null)b.append("|i:").append(i.getId()).append(':').append(i.getStatus()).append(':').append(i.getRowVersion());
        sorted(s.calculations().getOrDefault(p.getId(),List.of()),ProgramCalculation::getId).forEach(x->b.append("|c:").append(x.getId()).append(':').append(x.getRevision()).append(':').append(x.getStatus()));
        sorted(s.adjustments().getOrDefault(p.getId(),List.of()),ProgramAdjustment::getId).forEach(x->b.append("|a:").append(x.getId()).append(':').append(x.getRevision()).append(':').append(x.getCalculationId()).append(':').append(x.getStatus()).append(':').append(x.getRowVersion()));
        ProgramFeedback f=s.feedback().get(p.getId());if(f!=null)b.append("|f:").append(f.getId()).append(':').append(f.getStatus()).append(':').append(f.getRowVersion());RmEmployee self=employees.get(p.getEmployeeId());if(self!=null)b.append("|e:").append(self.getId()).append(':').append(self.getStatus()).append(':').append(self.getSourceVersion());return b.toString();}

    private String previewHash(EvaluationProgram program,List<UUID> ids,Set<ProgramStage> stages,String locale,LocalDate on,List<ReminderCandidate> candidates,List<ReminderExclusion> exclusions){StringBuilder b=new StringBuilder().append(program.getId()).append('|').append(on).append('|').append(locale).append('|').append(POLICY_VERSION).append('|').append(ids).append('|').append(stages.stream().sorted().toList());candidates.forEach(c->b.append("|candidate:").append(c.candidateKey()).append(':').append(c.sourceFingerprint()).append(':').append(c.startsOn()).append(':').append(c.dueDate()));exclusions.forEach(e->b.append("|excluded:").append(e.participantId()).append(':').append(e.currentStage()).append(':').append(e.currentRound()).append(':').append(e.status()).append(':').append(e.reasonCode()));return hash(b.toString());}
    private String requestHash(ReminderQueueRequest r){return hash(r.participantIds().stream().sorted().toList()+"|"+stages(r.stages()).stream().sorted().toList()+"|"+r.locale()+"|"+r.reminderOn()+"|"+r.previewHash()+"|"+r.candidateKeys().stream().sorted().toList()+"|"+r.reason().trim());}
    private String episode(EvaluationProgram p,ProgramParticipant participant,ReminderAction action,UUID owner,UUID assignment){return hash(participant.getTenantId()+"|"+p.getId()+"|"+participant.getId()+"|"+participant.getCurrentStage()+"|"+action+"|"+participant.getCurrentRound()+"|"+owner+"|"+(assignment==null?"SELF":assignment)+"|"+POLICY_VERSION);}
    private String source(String state,RmEmployee owner,ProgramReviewerAssignment assignment,StageDefinitionInput definition){return hash(state+"|owner:"+owner.getId()+":"+owner.getStatus()+":"+owner.getSourceVersion()+"|assignment:"+(assignment==null?"SELF":assignment.getId()+":"+assignment.getRowVersion()+":"+assignment.getStatus())+"|dates:"+definition.startsOn()+":"+definition.endsOn());}
    private static String dedupeKey(String episode,LocalDate on){return hash(episode+"|"+on);}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}

    private static final Comparator<ReminderCandidate> CANDIDATE_ORDER=Comparator.comparing(ReminderCandidate::participantId).thenComparing(ReminderCandidate::stage).thenComparingInt(ReminderCandidate::currentRound).thenComparing(ReminderCandidate::action).thenComparing(r->String.valueOf(r.recipientEmployeeId()));
    private static <T> Map<UUID,List<T>> group(List<T> rows,Function<T,UUID> key){return rows.stream().collect(Collectors.groupingBy(key));}
    private static <T> Map<UUID,T> unique(List<T> rows,Function<T,UUID> key){return rows.stream().collect(Collectors.toMap(key,Function.identity()));}
    private static <T> List<T> sorted(List<T> rows,Function<T,UUID> id){return rows.stream().sorted(Comparator.comparing(id)).toList();}
    private static List<ProgramReviewerAssignment> slot(List<ProgramReviewerAssignment> rows,ReviewerRole role,int round){return rows.stream().filter(r->r.getRole()==role&&r.getRound()==round).toList();}
    private static boolean active(RmEmployee employee){return "ACTIVE".equalsIgnoreCase(employee.getStatus());}
    private static int count(List<ReminderCandidate> rows,ReminderCandidateStatus status){return (int)rows.stream().filter(r->r.status()==status).count();}
    private static ReminderExclusion exclusion(ProgramParticipant p,ReminderExclusionStatus status,String reason){return new ReminderExclusion(p.getId(),p.getCurrentStage(),p.getCurrentRound(),status,reason);}
    private static ReminderDueState due(LocalDate due,LocalDate on){if(due==null)return ReminderDueState.NO_DUE_DATE;int c=on.compareTo(due);return c<0?ReminderDueState.BEFORE_DUE:c==0?ReminderDueState.DUE_TODAY:ReminderDueState.OVERDUE;}
    private static ProgramStage defaultStage(ProgramStage stage){return stage;}
    private static ReminderAction defaultAction(ProgramConfiguration c,ProgramStage stage){return switch(stage){case GOAL->c.goalMode()==GoalMode.SELF_REPORT?ReminderAction.GOAL_SELF_REPORT:ReminderAction.GOAL_AUTHOR;case INTERMEDIATE->ReminderAction.INTERMEDIATE_CHECK;case SELF_REVIEW->ReminderAction.SELF_REVIEW;case REVIEW->ReminderAction.REVIEW;case CALIBRATION->ReminderAction.CALIBRATION;case FEEDBACK->ReminderAction.FEEDBACK_DELIVERY;default->throw new IllegalArgumentException();};}
    private String participantName(ProgramParticipant p){try{ParticipantAttributes a=json.participantAttributes(p.getAttributesJson());return a.name()==null||a.name().isBlank()?p.getEmployeeId().toString():a.name();}catch(RuntimeException e){return p.getEmployeeId().toString();}}
    private LocalDate today(){return LocalDate.now(clock.withZone(ZoneOffset.UTC));}
    private EvaluationProgram requireOpen(EvaluationProgram p){if(p.getStatus()!=ProgramStatus.OPEN)throw new ApiException(ProgramErrorCode.PROGRAM_LOCKED,Map.of("required","OPEN"));return p;}
    private static Set<ProgramStage> stages(List<ProgramStage> values){return values==null||values.isEmpty()?EnumSet.copyOf(SUPPORTED):EnumSet.copyOf(values);}
    private static void validateScope(List<UUID> ids,List<ProgramStage> stageValues){if(ids==null||ids.isEmpty()||ids.size()>100)throw new ApiException(ProgramErrorCode.PROGRAM_INVALID,Map.of("reason","REMINDER_SCOPE_INVALID"));validateUnique(ids,"participantIds");if(stageValues!=null){validateUnique(stageValues,"stages");if(stageValues.stream().anyMatch(s->!SUPPORTED.contains(s)))throw new ApiException(ProgramErrorCode.PROGRAM_INVALID,Map.of("reason","REMINDER_STAGE_UNSUPPORTED"));}}
    private static void validateUnique(Collection<?> values,String field){if(values==null||new HashSet<>(values).size()!=values.size())throw new ApiException(ProgramErrorCode.PROGRAM_INVALID,Map.of("reason",field+"_DUPLICATE"));}
    private static void validateRendered(String subject,String body){if(subject==null||subject.isBlank()||subject.length()>200||subject.indexOf('\r')>=0||subject.indexOf('\n')>=0||body==null||body.isBlank()||body.length()>8000)throw new ApiException(ProgramErrorCode.PROGRAM_INVALID,Map.of("reason","REMINDER_TEMPLATE_INVALID"));}
    private static ApiException stale(String reason){return new ApiException(ProgramErrorCode.PROGRAM_REMINDER_STALE,Map.of("reason",reason));}
    private ReminderQueueRow queueRow(ReminderCandidate c,ProgramNotification n,ReminderQueueDisposition disposition){return new ReminderQueueRow(c.candidateKey(),n.getId(),c.participantId(),n.getRecipientEmployeeId(),c.stage(),c.action(),disposition,n.getStatus(),n.getSentAt());}
    private ReminderHistoryRow historyRow(ProgramNotification n){return new ReminderHistoryRow(n.getId(),n.getParticipantId(),n.getRecipientEmployeeId(),n.getReminderStage(),n.getReminderRound(),n.getReminderAction(),n.getReminderOn(),n.getReminderPolicyVersion(),n.getStatus(),n.getSubject(),n.getBody(),n.getDeepLink(),n.getSentAt(),n.getReadAt(),n.getReminderIdempotencyKey());}

    private static Rendered render(String locale,String program,String participant,ReminderAction action,LocalDate due,String link){String actionText=switch(locale){case "ko"->ko(action);case "ja"->ja(action);case "zh-CN"->zh(action);case "vi"->vi(action);default->en(action);};String dueText=due==null?"-":due.toString();String subject=switch(locale){case "ko"->"["+program+"] 미완료 평가 업무 알림";case "ja"->"["+program+"] 未完了の評価タスク";case "zh-CN"->"["+program+"] 未完成的评估任务";case "vi"->"["+program+"] Nhắc việc đánh giá chưa hoàn thành";default->"["+program+"] Incomplete evaluation task";};String body=switch(locale){case "ko"->participant+" 대상 "+actionText+" 업무가 미완료입니다. 마감일: "+dueText+". "+link;case "ja"->participant+" の「"+actionText+"」が未完了です。期限: "+dueText+"。 "+link;case "zh-CN"->participant+" 的“"+actionText+"”尚未完成。截止日期："+dueText+"。 "+link;case "vi"->"Nhiệm vụ "+actionText+" cho "+participant+" chưa hoàn thành. Hạn: "+dueText+". "+link;default->actionText+" for "+participant+" is incomplete. Due: "+dueText+". "+link;};validateRendered(subject,body);return new Rendered(subject,body);}
    private static String ko(ReminderAction a){return switch(a){case GOAL_AUTHOR->"목표 작성";case GOAL_APPROVAL->"목표 합의";case GOAL_SELF_REPORT->"목표 실적 입력";case INTERMEDIATE_CHECK->"중간 검토";case SELF_REVIEW->"자기평가";case REVIEW->"평가";case CALIBRATION->"조정";case FEEDBACK_DELIVERY->"피드백 전달";case FEEDBACK_ACKNOWLEDGEMENT->"피드백 확인";case FEEDBACK_RESOLUTION->"이의 해결";};}
    private static String en(ReminderAction a){return a.name().toLowerCase(Locale.ROOT).replace('_',' ');}
    private static String ja(ReminderAction a){return switch(a){case GOAL_AUTHOR->"目標作成";case GOAL_APPROVAL->"目標合意";case GOAL_SELF_REPORT->"目標実績入力";case INTERMEDIATE_CHECK->"中間レビュー";case SELF_REVIEW->"自己評価";case REVIEW->"評価";case CALIBRATION->"調整";case FEEDBACK_DELIVERY->"フィードバック送付";case FEEDBACK_ACKNOWLEDGEMENT->"フィードバック確認";case FEEDBACK_RESOLUTION->"異議解決";};}
    private static String zh(ReminderAction a){return switch(a){case GOAL_AUTHOR->"目标编写";case GOAL_APPROVAL->"目标确认";case GOAL_SELF_REPORT->"目标实绩录入";case INTERMEDIATE_CHECK->"中期检查";case SELF_REVIEW->"自我评价";case REVIEW->"评价";case CALIBRATION->"校准";case FEEDBACK_DELIVERY->"反馈发送";case FEEDBACK_ACKNOWLEDGEMENT->"反馈确认";case FEEDBACK_RESOLUTION->"异议处理";};}
    private static String vi(ReminderAction a){return switch(a){case GOAL_AUTHOR->"soạn mục tiêu";case GOAL_APPROVAL->"thỏa thuận mục tiêu";case GOAL_SELF_REPORT->"cập nhật kết quả mục tiêu";case INTERMEDIATE_CHECK->"đánh giá giữa kỳ";case SELF_REVIEW->"tự đánh giá";case REVIEW->"đánh giá";case CALIBRATION->"hiệu chỉnh";case FEEDBACK_DELIVERY->"gửi phản hồi";case FEEDBACK_ACKNOWLEDGEMENT->"xác nhận phản hồi";case FEEDBACK_RESOLUTION->"giải quyết khiếu nại";};}

    private record Rendered(String subject,String body){}
    private record BuildResult(ReminderPreviewResponse response,Map<String,ProgramNotification> existingByDedupe){}
    private record Sources(Map<UUID,List<ProgramReviewerAssignment>> reviewers,Map<UUID,List<ProgramGoal>> goals,
        Map<UUID,List<ProgramReviewSubmission>> submissions,Map<UUID,ProgramIntermediateReview> intermediate,
        Map<UUID,List<ProgramCalculation>> calculations,Map<UUID,List<ProgramAdjustment>> adjustments,
        Map<UUID,ProgramFeedback> feedback){}
}

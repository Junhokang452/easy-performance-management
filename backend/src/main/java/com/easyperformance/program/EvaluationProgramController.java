package com.easyperformance.program;

import com.easyperformance.program.ProgramDtos.*;
import com.easyperformance.program.ProgramResultPdfDtos.ProgramResultPdfFile;
import com.easyperformance.program.ProgramResultPdfDtos.ProgramResultPdfRequest;
import com.easyperformance.program.ProgramTypes.ProgramStage;
import com.easyperformance.workflow.ActorAccess;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/evaluation-programs")
public class EvaluationProgramController {
 private final ActorAccess actors;private final EvaluationProgramService programs;private final ProgramRosterService roster;private final ProgramExecutionService execution;private final ProgramLifecycleService lifecycle;private final ProgramNotificationService notifications;private final ProgramGuideService guides;private final ProgramAnalyticsService analytics;private final ProgramAnalyticsExportService analyticsExports;private final ProgramResultPdfExportService pdfExports;
 public EvaluationProgramController(ActorAccess actors,EvaluationProgramService programs,ProgramRosterService roster,ProgramExecutionService execution,ProgramLifecycleService lifecycle,ProgramNotificationService notifications,ProgramGuideService guides,ProgramAnalyticsService analytics,ProgramAnalyticsExportService analyticsExports,ProgramResultPdfExportService pdfExports){this.actors=actors;this.programs=programs;this.roster=roster;this.execution=execution;this.lifecycle=lifecycle;this.notifications=notifications;this.guides=guides;this.analytics=analytics;this.analyticsExports=analyticsExports;this.pdfExports=pdfExports;}

 @PostMapping public ResponseEntity<ProgramResponse> create(@Valid @RequestBody ProgramCreateRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(programs.create(actors.requireActor(),r));}
 @GetMapping public Page<ProgramSummaryResponse> list(Pageable pageable){return programs.list(actors.requireActor(),pageable);}
 @GetMapping("/{id}")public ProgramResponse get(@PathVariable UUID id){return programs.get(actors.requireActor(),id);}
 @PutMapping("/{id}/basic")public ProgramResponse basic(@PathVariable UUID id,@Valid @RequestBody ProgramBasicUpdateRequest r){return programs.updateBasic(actors.requireActor(),id,r);}
 @PostMapping("/{id}/copy")public ResponseEntity<ProgramResponse> copy(@PathVariable UUID id,@Valid @RequestBody ProgramCopyRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(programs.copy(actors.requireActor(),id,r));}
 @PutMapping("/{id}/definition")public ProgramResponse definition(@PathVariable UUID id,@Valid @RequestBody DefinitionUpdateRequest r){return programs.updateDefinition(actors.requireActor(),id,r);}
 @GetMapping("/{id}/revisions")public List<DefinitionRevisionResponse> revisions(@PathVariable UUID id){return programs.revisions(actors.requireActor(),id);}
 @PostMapping("/{id}/common-items:apply")public ProgramResponse applyItems(@PathVariable UUID id){return programs.applyCommonItems(actors.requireActor(),id);}
 @PostMapping("/{id}/open")public ProgramResponse open(@PathVariable UUID id){return programs.open(actors.requireActor(),id);}

 @PostMapping("/{id}/participants:generate")public List<ParticipantResponse> generate(@PathVariable UUID id,@RequestBody ParticipantGenerateRequest r){return roster.generate(actors.requireActor(),id,r);}
 @PostMapping("/{id}/participants")public ResponseEntity<ParticipantResponse> addParticipant(@PathVariable UUID id,@Valid @RequestBody ParticipantAddRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(roster.add(actors.requireActor(),id,r));}
 @GetMapping("/{id}/participants")public Page<ParticipantResponse> participants(@PathVariable UUID id,Pageable pageable){return roster.list(actors.requireActor(),id,pageable);}
 @PatchMapping("/participants/{id}")public ParticipantResponse changeParticipant(@PathVariable UUID id,@Valid @RequestBody ParticipantChangeRequest r){return roster.change(actors.requireActor(),id,r);}
 @GetMapping("/participants/{id}/reviewers")public List<ReviewerResponse> reviewers(@PathVariable UUID id){return roster.reviewers(actors.requireActor(),id);}
 @PutMapping("/participants/{id}/reviewers")public List<ReviewerResponse> reviewers(@PathVariable UUID id,@Valid @RequestBody ReviewerReplaceRequest r){return roster.replaceReviewers(actors.requireActor(),id,r);}
 @PostMapping("/{id}/stages/{stage}:start")public BatchOperationResponse start(@PathVariable UUID id,@PathVariable ProgramStage stage,@Valid @RequestBody StageBatchRequest r){return roster.startStage(actors.requireActor(),id,stage,r);}
 @PostMapping("/participants/{id}/stage:override")public ParticipantResponse override(@PathVariable UUID id,@Valid @RequestBody StageExceptionRequest r){return roster.overrideStage(actors.requireActor(),id,r);}
 @GetMapping(value="/{id}/participants.xlsx",produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")public ResponseEntity<byte[]> participantsXlsx(@PathVariable UUID id){return xlsx("participants.xlsx",roster.exportParticipants(actors.requireActor(),id));}
 @PostMapping(value="/{id}/participants:import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)public List<ParticipantResponse> importParticipants(@PathVariable UUID id,@RequestPart("file")MultipartFile file)throws IOException{return roster.importParticipants(actors.requireActor(),id,file.getBytes());}
 @GetMapping(value="/{id}/reviewers.xlsx",produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")public ResponseEntity<byte[]> reviewersXlsx(@PathVariable UUID id){return xlsx("reviewers.xlsx",roster.exportReviewers(actors.requireActor(),id));}
 @PostMapping(value="/{id}/reviewers:import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)public List<ReviewerResponse> importReviewers(@PathVariable UUID id,@RequestPart("file")MultipartFile file)throws IOException{return roster.importReviewers(actors.requireActor(),id,file.getBytes());}

 @GetMapping("/{id}/me/participants")public List<ParticipantResponse> myParticipants(@PathVariable UUID id){return execution.selfParticipants(actors.requireEmployeeActor(),id);}
 @GetMapping("/{id}/me")public MyProgramWorkspaceResponse me(@PathVariable UUID id,@RequestParam(required=false)UUID participantId){return execution.workspace(actors.requireEmployeeActor(),id,participantId);}
 @GetMapping("/participants/{id}/employee-preview")public MyProgramWorkspaceResponse employeePreview(@PathVariable UUID id){return execution.employeePreview(actors.requireActor(),id);}
 @GetMapping("/participants/{id}/goals")public List<GoalResponse> goals(@PathVariable UUID id){return execution.goals(actors.requireEmployeeActor(),id);}
 @PostMapping("/participants/{id}/goals")public ResponseEntity<GoalResponse> createGoal(@PathVariable UUID id,@Valid @RequestBody GoalUpsertRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(execution.createGoal(actors.requireEmployeeActor(),id,r));}
 @PutMapping("/goals/{id}")public GoalResponse updateGoal(@PathVariable UUID id,@Valid @RequestBody GoalUpsertRequest r){return execution.updateGoal(actors.requireEmployeeActor(),id,r);}
 @PutMapping("/goals/{id}/opinion")public GoalResponse goalOpinion(@PathVariable UUID id,@Valid @RequestBody GoalOpinionRequest r){return execution.saveGoalOpinion(actors.requireEmployeeActor(),id,r);}
 @PostMapping("/goals/{id}:request-agreement")public GoalResponse requestAgreement(@PathVariable UUID id){return execution.requestAgreement(actors.requireEmployeeActor(),id);}
 @PostMapping("/goals/{id}:decide")public GoalResponse decideGoal(@PathVariable UUID id,@Valid @RequestBody GoalDecisionRequest r){return execution.decideGoal(actors.requireEmployeeActor(),id,r);}
 @PostMapping("/goals/{id}:self-report")public GoalResponse selfReport(@PathVariable UUID id,@Valid @RequestBody GoalSelfReportRequest r){return execution.selfReport(actors.requireEmployeeActor(),id,r);}
 @GetMapping("/goals/{id}/history")public List<GoalHistoryResponse> goalHistory(@PathVariable UUID id){return execution.goalHistory(actors.requireEmployeeActor(),id);}

 @GetMapping("/participants/{id}/intermediate")public IntermediateResponse intermediate(@PathVariable UUID id){return execution.intermediate(actors.requireEmployeeActor(),id);}
 @PutMapping("/participants/{id}/intermediate")public IntermediateResponse intermediate(@PathVariable UUID id,@Valid @RequestBody IntermediateUpdateRequest r){return execution.saveIntermediate(actors.requireEmployeeActor(),id,r,false);}
 @PostMapping("/participants/{id}/intermediate:complete")public IntermediateResponse completeIntermediate(@PathVariable UUID id,@Valid @RequestBody IntermediateUpdateRequest r){return execution.saveIntermediate(actors.requireEmployeeActor(),id,r,true);}
 @GetMapping("/participants/{id}/review-context")public ReviewContextResponse reviewContext(@PathVariable UUID id,@RequestParam(required=false)Integer round){return execution.reviewContext(actors.requireEmployeeActor(),id,round);}
 @PutMapping("/participants/{id}/review-submission")public ReviewSubmissionResponse saveReview(@PathVariable UUID id,@RequestParam(required=false)Integer round,@Valid @RequestBody ReviewSaveRequest r){return execution.saveReview(actors.requireEmployeeActor(),id,round,r,false);}
 @PostMapping("/participants/{id}/review-submission:complete")public ReviewSubmissionResponse completeReview(@PathVariable UUID id,@RequestParam(required=false)Integer round,@Valid @RequestBody ReviewSaveRequest r){return execution.saveReview(actors.requireEmployeeActor(),id,round,r,true);}

 @PostMapping("/{id}/calculations")public List<CalculationResponse> calculate(@PathVariable UUID id,@Valid @RequestBody CalculationRunRequest r){return execution.calculate(actors.requireActor(),id,r);}
 @GetMapping("/participants/{id}/calculations")public List<CalculationResponse> calculations(@PathVariable UUID id){return execution.calculations(actors.requireEmployeeActor(),id);}
 @PutMapping("/participants/{id}/adjustment")public AdjustmentResponse adjustment(@PathVariable UUID id,@Valid @RequestBody AdjustmentSaveRequest r){return execution.saveAdjustment(actors.requireEmployeeActor(),id,r,false);}
 @PostMapping("/participants/{id}/adjustment:complete")public AdjustmentResponse completeAdjustment(@PathVariable UUID id,@Valid @RequestBody AdjustmentSaveRequest r){return execution.saveAdjustment(actors.requireEmployeeActor(),id,r,true);}
 @GetMapping("/participants/{id}/feedback")public FeedbackResponse feedback(@PathVariable UUID id){return execution.feedback(actors.requireEmployeeActor(),id);}
 @PutMapping("/participants/{id}/feedback")public FeedbackResponse feedback(@PathVariable UUID id,@Valid @RequestBody FeedbackSaveRequest r){return execution.saveFeedback(actors.requireEmployeeActor(),id,r,false);}
 @PostMapping("/participants/{id}/feedback:deliver")public FeedbackResponse deliverFeedback(@PathVariable UUID id,@Valid @RequestBody FeedbackSaveRequest r){return execution.saveFeedback(actors.requireEmployeeActor(),id,r,true);}
 @PostMapping("/feedback/{id}:agree")public FeedbackResponse agreeFeedback(@PathVariable UUID id){return execution.agreeFeedback(actors.requireEmployeeActor(),id);}
 @PostMapping("/feedback/{id}:appeal")public FeedbackResponse appealFeedback(@PathVariable UUID id,@Valid @RequestBody FeedbackAppealRequest r){return execution.appealFeedback(actors.requireEmployeeActor(),id,r);}
 @PostMapping("/feedback/{id}:resolve")public FeedbackResponse resolveFeedback(@PathVariable UUID id,@Valid @RequestBody FeedbackResolveRequest r){return execution.resolveFeedback(actors.requireEmployeeActor(),id,r);}

 @PostMapping("/{id}:finalize")public ProgramResponse finalizeProgram(@PathVariable UUID id,@Valid @RequestBody FinalizeRequest r){return lifecycle.finalizeProgram(actors.requireActor(),id,r);}
 @PostMapping("/{id}:cancel-finalization")public ProgramResponse cancelFinalization(@PathVariable UUID id,@Valid @RequestBody FinalizationCancelRequest r){return lifecycle.cancelFinalization(actors.requireActor(),id,r);}
 @PostMapping("/{id}/results:publish")public BatchOperationResponse publish(@PathVariable UUID id,@Valid @RequestBody PublishRequest r){return lifecycle.publish(actors.requireActor(),id,r);}
 @GetMapping("/{id}/dashboard")public ProgramDashboardResponse dashboard(@PathVariable UUID id){return lifecycle.dashboard(actors.requireActor(),id);}
 @GetMapping("/{id}/results")public ResultSummaryResponse results(@PathVariable UUID id){return analytics.resultSummary(actors.requireActor(),id);}
 @GetMapping(value="/{id}/results.xlsx",produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")public ResponseEntity<byte[]> resultsXlsx(@PathVariable UUID id){return download("evaluation-results.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",analyticsExports.resultsXlsx(actors.requireActor(),id));}
 @PostMapping(value="/{id}/results.pdf",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_PDF_VALUE)public ResponseEntity<byte[]> resultsPdf(@PathVariable UUID id,@Valid @RequestBody ProgramResultPdfRequest request){ProgramResultPdfFile file=pdfExports.export(actors.requireActor(),id,request);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.filename()).build().toString()).header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore().cachePrivate()).header(HttpHeaders.PRAGMA,"no-cache").contentType(MediaType.APPLICATION_PDF).contentLength(file.bytes().length).body(file.bytes());}
 @GetMapping("/{id}/results/items")public List<ItemResultRow> itemResults(@PathVariable UUID id){return analytics.itemResults(actors.requireActor(),id);}
 @GetMapping("/{id}/results/reviewers")public List<ReviewerResultRow> reviewerResults(@PathVariable UUID id){return analytics.reviewerResults(actors.requireActor(),id);}
 @GetMapping("/participants/{id}/result-feedback")public EmployeeFeedbackDetail employeeFeedback(@PathVariable UUID id){return analytics.employeeFeedback(actors.requireActor(),id);}
 @GetMapping("/analytics/grade-matrix")public GradeMatrixResponse gradeMatrix(@RequestParam UUID xProgramId,@RequestParam UUID yProgramId){return analytics.gradeMatrix(actors.requireActor(),xProgramId,yProgramId);}
 @PostMapping("/analytics/pivot")public PivotResponse pivot(@Valid @RequestBody PivotRequest r){return analytics.pivot(actors.requireActor(),r);}
 @PostMapping(value="/analytics/pivot.xlsx",produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")public ResponseEntity<byte[]> pivotXlsx(@Valid @RequestBody PivotRequest r){return download("evaluation-pivot.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",analyticsExports.pivotXlsx(actors.requireActor(),r));}
 @PostMapping(value="/analytics/pivot.svg",produces="image/svg+xml")public ResponseEntity<byte[]> pivotSvg(@Valid @RequestBody PivotRequest r){return download("evaluation-pivot.svg","image/svg+xml",analyticsExports.pivotSvg(actors.requireActor(),r));}
 @GetMapping("/{id}/analytics/reviewer-tendencies")public List<ReviewerTendencyResponse> reviewerTendencies(@PathVariable UUID id){return analytics.reviewerTendencies(actors.requireActor(),id);}
 @GetMapping("/me/history")public PersonalReportResponse personalHistory(){var actor=actors.requireEmployeeActor();return analytics.personalReport(actor,actor.employeeId());}

 @PostMapping("/{id}/notifications:preview")public List<NotificationPreview> preview(@PathVariable UUID id,@Valid @RequestBody NotificationPreviewRequest r){return notifications.preview(actors.requireActor(),id,r);}
 @PostMapping("/{id}/notifications:queue")public NotificationQueueResponse queue(@PathVariable UUID id,@Valid @RequestBody NotificationPreviewRequest r){return notifications.queue(actors.requireActor(),id,r);}
 @PostMapping("/{id}/notifications:dispatch")public NotificationDispatchResponse dispatch(@PathVariable UUID id){return notifications.dispatch(actors.requireActor(),id);}
 @GetMapping("/me/notifications")public Page<NotificationResponse> notifications(Pageable pageable){return notifications.mine(actors.requireEmployeeActor(),pageable);}
 @PostMapping("/me/notifications/{id}:read")public NotificationResponse readNotification(@PathVariable UUID id){return notifications.read(actors.requireEmployeeActor(),id);}

 @PostMapping(value="/{id}/guides",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)public ResponseEntity<GuideAttachmentResponse> uploadGuide(@PathVariable UUID id,@RequestPart("file")MultipartFile file)throws IOException{return ResponseEntity.status(HttpStatus.CREATED).body(guides.upload(actors.requireActor(),id,file.getOriginalFilename(),file.getContentType(),file.getBytes()));}
 @GetMapping("/{id}/guides")public List<GuideAttachmentResponse> guides(@PathVariable UUID id){return guides.list(actors.requireActor(),id);}
 @GetMapping("/guides/{id}")public ResponseEntity<byte[]> downloadGuide(@PathVariable UUID id){ProgramGuideAttachment f=guides.download(actors.requireActor(),id);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.getFilename(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(f.getContentType())).contentLength(f.getSize()).body(f.getContent());}
 private static ResponseEntity<byte[]> xlsx(String filename,byte[] body){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString()).header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(body);}
 private static ResponseEntity<byte[]> download(String filename,String contentType,byte[] body){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString()).header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(contentType)).contentLength(body.length).body(body);}
}

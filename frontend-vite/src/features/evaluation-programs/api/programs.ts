import { buildQueryKey } from '@easy/query-client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiClient } from '../../../api/client';

const BASE = '/v1/evaluation-programs';

export type ProgramStatus = 'DRAFT' | 'OPEN' | 'FINALIZED' | 'CANCELLED';
export type EvaluationKind = 'PERFORMANCE' | 'COMPETENCY' | 'COMBINED' | 'MULTI_RATER';
export type ProgramStage = 'GOAL' | 'INTERMEDIATE' | 'SELF_REVIEW' | 'REVIEW' | 'CALCULATION' | 'CALIBRATION' | 'FEEDBACK';
export type ProgramStageStatus = 'NOT_STARTED' | 'READY' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED' | 'BLOCKED';
export type GoalMode = 'AGREEMENT' | 'SELF_REPORT';
export type FormMode = 'DEFINITION_ONLY' | 'DEFINITION_AND_ACHIEVEMENT_LEVELS';
export type ScaleUse = 'INPUT' | 'RESULT' | 'DEPARTMENT_RESULT';
export type ScaleKind = 'SCORE' | 'GRADE';
export type ReviewerRole = 'AGREEMENT_REVIEWER' | 'CHECKER' | 'REVIEWER' | 'ADJUSTER' | 'FINAL_FEEDBACK';
export type SubmissionStatus = 'DRAFT' | 'COMPLETED' | 'INVALIDATED';
export type ParticipantStatus = 'ACTIVE' | 'EXCLUDED' | 'DELETED';
export type GoalStatus = 'DRAFT' | 'AGREEMENT_REQUESTED' | 'AGREED' | 'RETURNED' | 'SELF_REPORTED';
export type FeedbackStatus = 'DRAFT' | 'DELIVERED' | 'AGREED' | 'APPEALED' | 'RESOLVED';
export type NotificationChannel = 'IN_APP' | 'EMAIL';
export type NotificationStatus = 'READY' | 'SENDING' | 'SENT' | 'FAILED' | 'CONFIG_REQUIRED' | 'READ';

export interface ProgramCreateRequest { name: string; evaluationYear: number; asOfDate: string; startsOn: string; endsOn: string; kind: EvaluationKind }
export interface ProgramSummaryResponse extends ProgramCreateRequest { id: string; status: ProgramStatus; definitionRevision: number; participantCount: number; createdAt: string; updatedAt: string }
export interface StageDefinitionInput { stage: ProgramStage; enabled: boolean; startsOn: string | null; endsOn: string | null; formMode: FormMode | null; itemOpinionEnabled: boolean; itemOpinionRequired: boolean; guideAttachmentId: string | null }
export interface ScaleLevelInput { code: string; label: string; convertedScore: number | null; lowerExclusive: number | null; upperInclusive: number | null; color: string | null }
export interface ScaleDefinitionInput { id: string; name: string; use: ScaleUse; kind: ScaleKind; levels: ScaleLevelInput[] }
export interface ComponentWeightInput { component: string; weightPercent: number }
export interface CalculationPolicyInput { inputScaleId: string; resultScaleId: string; departmentResultScaleId: string | null; departmentPerformanceEnabled: boolean; adjustmentTarget: 'ALL' | 'ABSOLUTE_ONLY' | 'RELATIVE_ONLY'; adjustmentMethod: 'NONE' | 'MEAN' | 'STANDARD_DEVIATION'; populationBasis: 'DEPARTMENT' | 'DEPARTMENT_PERFORMANCE_GROUP'; targetMean: number | null; targetStandardDeviation: number | null; componentWeights: ComponentWeightInput[]; decimalPlaces: number }
export interface PublicationPolicyInput { previousRoundVisibility: 'HIDDEN' | 'SCORE_ONLY' | 'SCORE_AND_OPINION'; showReviewerAllocation: boolean; memberResultVisibility: 'SCORE_AND_GRADE' | 'GRADE_ONLY'; scoreAdjustmentAllowed: boolean; reviewerMeanMinimum: number | null; reviewerMeanMaximum: number | null; maximumGradeStepAdjustment: number | null; multiRaterOpinionsVisible: boolean; feedbackEnabled: boolean; appealEnabled: boolean }
export interface GroupConditionInput { field: 'ORG_UNIT' | 'POSITION' | 'GRADE' | 'JOB' | 'EMPLOYMENT_TYPE' | 'EMPLOYEE'; operator: 'IN' | 'NOT_IN' | 'EQUALS' | 'NOT_EQUALS'; values: string[] }
export interface ReviewerWeightPlanInput { actualReviewerCount: number; reviewerWeights: Record<string, number>; departmentWeight: number }
export interface RevieweeGroupInput { id: string; name: string; definition: string; itemAssignmentMode: 'AGREEMENT' | 'DESIGNATED'; evaluationMethod: 'ABSOLUTE' | 'RELATIVE'; intermediateEnabled: boolean; selfReviewEnabled: boolean; priority: number; conditions: GroupConditionInput[]; reviewerWeightPlans: ReviewerWeightPlanInput[] }
export interface CommonItemInput { id: string; groupId: string; round: number; catalogItemId: string | null; title: string; definition: string; weightPercent: number; scaleId: string; displayOrder: number; opinionRequired: boolean }
export interface DepartmentPerformanceGroupInput { id: string; name: string; grade: string; displayOrder: number; conditions: GroupConditionInput[] }
export interface AllocationRowInput { populationSize: number; gradeHeadcounts: Record<string, number> }
export interface ProgramConfiguration { goalMode: GoalMode; stages: StageDefinitionInput[]; scales: ScaleDefinitionInput[]; calculation: CalculationPolicyInput; publication: PublicationPolicyInput; groups: RevieweeGroupInput[]; commonItems: CommonItemInput[]; departmentPerformanceGroups: DepartmentPerformanceGroupInput[]; allocationRows: AllocationRowInput[] }
export interface ProgramResponse extends ProgramCreateRequest { id: string; tenantId: string; status: ProgramStatus; definitionRevision: number; configuration: ProgramConfiguration; openedAt: string | null; finalizedAt: string | null; createdAt: string; updatedAt: string }
export interface MemberProgramOverview { id: string; name: string; evaluationYear: number; kind: EvaluationKind; status: ProgramStatus; asOfDate: string; startsOn: string; endsOn: string; goalMode: GoalMode; stages: Array<{ stage: ProgramStage; startsOn: string | null; endsOn: string | null }>; inputScale: { name: string; kind: ScaleKind; levels: Array<{ code: string; label: string; color: string | null }> }; previousRoundVisibility: PublicationPolicyInput['previousRoundVisibility']; showReviewerAllocation: boolean; memberResultVisibility: PublicationPolicyInput['memberResultVisibility']; feedbackEnabled: boolean; appealEnabled: boolean }

export interface ParticipantAttributes { employeeId: string; employeeNo: string; name: string; assignmentId: string | null; orgUnitId: string | null; orgUnitName: string | null; positionCode: string | null; gradeCode: string | null; jobCode: string | null; employmentType: string | null }
export interface ParticipantResponse { id: string; programId: string; employee: ParticipantAttributes; groupId: string | null; groupName: string | null; status: ParticipantStatus; weightPercent: number; currentStage: ProgramStage; stageStatus: ProgramStageStatus; currentRound: number; resultPublished: boolean; exclusionReason: string | null; rowVersion: number; updatedAt: string }
export interface ReviewerInput { employeeId: string; role: ReviewerRole; round: number; weightPercent: number }
export interface ReviewerResponse { id: string; participantId: string; reviewerEmployeeId: string; reviewerName: string; role: ReviewerRole; round: number; weightPercent: number; status: 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'REVOKED' }
export interface BatchOperationResponse { changedCount: number; changedParticipantIds: string[]; excluded: Array<{ participantId: string; code: string; reason: string }> }

export interface AchievementLevelInput { code: string; label: string; thresholdValue: number | null }
export interface GoalUpsertRequest { catalogItemId: string | null; departmentGoalId: string | null; title: string; definition: string; weightPercent: number; targetValue: number | null; unit: string; achievementLevels: AchievementLevelInput[] }
export interface GoalResponse extends GoalUpsertRequest { id: string; participantId: string; status: GoalStatus; draftOpinion: string | null; decisionOpinion: string | null; achievedLevelCode: string | null; achievementSummary: string | null; taskEvidence: TaskEvidenceSnapshot[]; revision: number; rowVersion: number; updatedAt: string }
export interface GoalHistoryResponse { id: string; goalId: string; revision: number; status: GoalStatus; operation: string; opinion: string | null; actorEmployeeId: string; createdAt: string }
export interface TaskEvidenceSnapshot { taskId: string; title: string; status: string; progressPercent: number; occurredAt: string; summary: string }
export interface IntermediateResponse { id: string; participantId: string; checkerEmployeeId: string | null; opinion: string; taskEvidence: TaskEvidenceSnapshot[]; status: SubmissionStatus; completedAt: string | null; rowVersion: number }
export interface ReviewItemAnswerInput { itemId: string; scaleCode: string | null; numericScore: number | null; opinion: string }
export interface ReviewItemAnswerResponse extends ReviewItemAnswerInput { title: string; weightPercent: number }
export interface ReviewSubmissionResponse { id: string; participantId: string; reviewerAssignmentId: string | null; role: ReviewerRole; round: number; answers: ReviewItemAnswerResponse[]; overallOpinion: string | null; status: SubmissionStatus; completedAt: string | null; rowVersion: number }
export interface ReviewScaleOverview { id: string; name: string; kind: ScaleKind; levels: Array<{ code: string; label: string; color: string | null }> }
export interface ReviewContextResponse { participant: ParticipantResponse; goals: GoalResponse[]; intermediate: IntermediateResponse | null; visiblePreviousRounds: ReviewSubmissionResponse[]; currentSubmission: ReviewSubmissionResponse | null; items: CommonItemInput[]; inputScales: ReviewScaleOverview[] }
export interface ScoreContribution { component: string; score: number; weightPercent: number }
export interface CalculationResponse { id: string; participantId: string; revision: number; contributions: ScoreContribution[]; rawScore: number; normalizedScore: number; adjustedScore: number | null; calculatedGrade: string; status: 'DRAFT' | 'FINAL'; formula: string; warnings: string[]; calculatedAt: string }
export interface AdjustmentResponse { id: string; participantId: string; calculationId: string; beforeScore: number; beforeGrade: string; adjustedScore: number | null; adjustedGrade: string; reason: string; status: 'DRAFT' | 'COMPLETED'; actorEmployeeId: string; completedAt: string | null; rowVersion: number }
export interface FeedbackResponse { id: string; participantId: string; writerEmployeeId: string; comment: string; status: FeedbackStatus; appealReason: string | null; resolution: 'UPHELD' | 'SCORE_ADJUSTED' | null; resolutionComment: string | null; deliveredAt: string | null; resolvedAt: string | null; rowVersion: number }
export interface ProgramDashboardResponse { programId: string; participantCount: number; excludedCount: number; ready: Partial<Record<ProgramStage, number>>; inProgress: Partial<Record<ProgramStage, number>>; completed: Partial<Record<ProgramStage, number>>; blocked: Partial<Record<ProgramStage, number>> }
export interface ResultRow { participantId: string; employee: ParticipantAttributes; score: number | null; grade: string | null; published: boolean; feedbackStatus: string | null }
export interface ResultSummaryResponse { programId: string; finalizedCount: number; grades: Array<{ grade: string; count: number }>; rows: ResultRow[] }
export interface ItemResultRow { participantId: string; employee: ParticipantAttributes; itemId: string; itemTitle: string; round: number; weightPercent: number; score: number | null; scaleCode: string | null; grade: string | null }
export interface ReviewerResultRow { reviewerEmployeeId: string; reviewerName: string; participantId: string; employee: ParticipantAttributes; round: number; score: number | null; grade: string | null; opinion: string | null }
export interface EmployeeFeedbackDetail { participantId: string; employee: ParticipantAttributes; calculation: CalculationResponse | null; adjustment: AdjustmentResponse | null; feedback: FeedbackResponse | null; submissions: ReviewSubmissionResponse[] }
export interface GradeMatrixResponse { xProgramId: string; yProgramId: string; cells: Array<{ xGrade: string; yGrade: string; count: number; employees: ParticipantAttributes[] }> }
export interface PivotResponse { cells: Array<{ dimensions: Record<string, string>; grade: string; count: number; employees: ParticipantAttributes[] }> }
export interface ReviewerTendencyResponse { reviewerEmployeeId: string; reviewerName: string; targetCount: number; meanScore: number | null; organizationMeanRank: number | null; standardDeviation: number | null; opinionSpecificity: number | null; positiveRatio: number | null; neutralRatio: number | null; negativeRatio: number | null; averageOpinionLength: number | null; frequentKeywords: string[]; unavailableReasons: string[] }
export interface PersonalReportResponse { employeeId: string; history: Array<{ year: number; programId: string; programName: string; kind: EvaluationKind; score: number | null; grade: string | null }> }
export interface NotificationInput { recipientEmployeeIds: string[]; companyName: string; subjectTemplate: string; bodyTemplate: string; channel: NotificationChannel }
export interface NotificationPreview { recipientEmployeeId: string; subject: string; body: string }
export interface NotificationQueueResponse { queued: number; status: NotificationStatus; notificationIds: string[] }
export interface NotificationDispatchResponse { sent: number; failed: number; configurationRequired: number }
export interface NotificationResponse { id: string; recipientEmployeeId: string; channel: NotificationChannel; status: NotificationStatus; subject: string; body: string; sentAt: string | null; readAt: string | null }
export interface GuideAttachmentResponse { id: string; programId: string; filename: string; contentType: string; size: number; uploadedBy: string; createdAt: string }
export interface MyProgramWorkspaceResponse { program: MemberProgramOverview; participant: ParticipantResponse; goals: GoalResponse[]; intermediate: IntermediateResponse | null; submissions: ReviewSubmissionResponse[]; calculation: CalculationResponse | null; adjustment: AdjustmentResponse | null; feedback: FeedbackResponse | null; allowedActions: string[]; blockers: string[] }
export interface PageEnvelope<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }

export const programKeys = {
  all: () => buildQueryKey('performance', 'evaluation-programs'),
  list: () => buildQueryKey('performance', 'evaluation-programs', 'list'),
  detail: (id: string) => buildQueryKey('performance', 'evaluation-programs', id),
  revisions: (id: string) => buildQueryKey('performance', 'evaluation-programs', id, 'revisions'),
  participants: (id: string) => buildQueryKey('performance', 'evaluation-programs', id, 'participants'),
  reviewers: (participantId: string) => buildQueryKey('performance', 'evaluation-programs', 'participants', participantId, 'reviewers'),
  me: (id: string, participantId?: string | null) => buildQueryKey('performance', 'evaluation-programs', id, 'me', participantId ?? 'single'),
  myParticipants: (id: string) => buildQueryKey('performance', 'evaluation-programs', id, 'me', 'participants'),
  goalHistory: (goalId: string) => buildQueryKey('performance', 'evaluation-programs', 'goals', goalId, 'history'),
  reviewContext: (participantId: string, round: number) => buildQueryKey('performance', 'evaluation-programs', 'participants', participantId, 'review-context', round),
  calculations: (participantId: string) => buildQueryKey('performance', 'evaluation-programs', 'participants', participantId, 'calculations'),
  goals: (participantId: string) => buildQueryKey('performance', 'evaluation-programs', 'participants', participantId, 'goals'),
  intermediate: (participantId: string) => buildQueryKey('performance', 'evaluation-programs', 'participants', participantId, 'intermediate'),
  feedback: (participantId: string) => buildQueryKey('performance', 'evaluation-programs', 'participants', participantId, 'feedback'),
  employeePreview: (participantId: string) => buildQueryKey('performance', 'evaluation-programs', 'participants', participantId, 'employee-preview'),
  dashboard: (id: string) => buildQueryKey('performance', 'evaluation-programs', id, 'dashboard'),
  results: (id: string) => buildQueryKey('performance', 'evaluation-programs', id, 'results'),
  itemResults: (id: string) => buildQueryKey('performance', 'evaluation-programs', id, 'results', 'items'),
  reviewerResults: (id: string) => buildQueryKey('performance', 'evaluation-programs', id, 'results', 'reviewers'),
  employeeFeedback: (id: string) => buildQueryKey('performance', 'evaluation-programs', 'participants', id, 'result-feedback'),
  matrix: (x: string, y: string) => buildQueryKey('performance', 'evaluation-programs', 'analytics', 'matrix', x, y),
  tendency: (id: string) => buildQueryKey('performance', 'evaluation-programs', id, 'analytics', 'tendency'),
  personalHistory: () => buildQueryKey('performance', 'evaluation-programs', 'me', 'history'),
  notifications: () => buildQueryKey('performance', 'evaluation-programs', 'me', 'notifications'),
  guides: (id: string) => buildQueryKey('performance', 'evaluation-programs', id, 'guides'),
} as const;

const programsApi = {
  list: () => apiClient.get<PageEnvelope<ProgramSummaryResponse>>(BASE, { params: { page: 0, size: 100 } }).then(({ data }) => data),
  create: (input: ProgramCreateRequest) => apiClient.post<ProgramResponse>(BASE, input).then(({ data }) => data),
  get: (id: string) => apiClient.get<ProgramResponse>(`${BASE}/${id}`).then(({ data }) => data),
  updateBasic: (id: string, input: Pick<ProgramCreateRequest, 'name' | 'evaluationYear' | 'asOfDate' | 'startsOn' | 'endsOn'>) => apiClient.put<ProgramResponse>(`${BASE}/${id}/basic`, input).then(({ data }) => data),
  copy: (id: string, input: Omit<ProgramCreateRequest, 'kind'>) => apiClient.post<ProgramResponse>(`${BASE}/${id}/copy`, input).then(({ data }) => data),
  updateDefinition: (id: string, configuration: ProgramConfiguration, revisionReason: string) => apiClient.put<ProgramResponse>(`${BASE}/${id}/definition`, { configuration, revisionReason }).then(({ data }) => data),
  open: (id: string) => apiClient.post<ProgramResponse>(`${BASE}/${id}/open`, {}).then(({ data }) => data),
  listParticipants: (id: string) => apiClient.get<PageEnvelope<ParticipantResponse>>(`${BASE}/${id}/participants`, { params: { page: 0, size: 100 } }).then(({ data }) => data),
  exportParticipants: (id: string) => apiClient.get<Blob>(`${BASE}/${id}/participants.xlsx`, { responseType: 'blob', headers: { Accept: '*/*' } }).then(({ data }) => data),
  importParticipants: (id: string, file: File) => { const body = new FormData(); body.append('file', file); return apiClient.post<ParticipantResponse[]>(`${BASE}/${id}/participants:import`, body).then(({ data }) => data); },
  exportReviewers: (id: string) => apiClient.get<Blob>(`${BASE}/${id}/reviewers.xlsx`, { responseType: 'blob', headers: { Accept: '*/*' } }).then(({ data }) => data),
  importReviewers: (id: string, file: File) => { const body = new FormData(); body.append('file', file); return apiClient.post<ReviewerResponse[]>(`${BASE}/${id}/reviewers:import`, body).then(({ data }) => data); },
  addParticipant: (id: string, input: { employeeId: string; assignmentId?: string; orgUnitId?: string; weightPercent: number }) => apiClient.post<ParticipantResponse>(`${BASE}/${id}/participants`, input).then(({ data }) => data),
  generateParticipants: (id: string, employeeIds: string[]) => apiClient.post<ParticipantResponse[]>(`${BASE}/${id}/participants:generate`, { employeeIds }).then(({ data }) => data),
  updateParticipant: (participantId: string, input: { groupId?: string; status?: ParticipantStatus; reason?: string; weightPercent?: number }) => apiClient.patch<ParticipantResponse>(`${BASE}/participants/${participantId}`, input).then(({ data }) => data),
  getReviewers: (participantId: string) => apiClient.get<ReviewerResponse[]>(`${BASE}/participants/${participantId}/reviewers`).then(({ data }) => data),
  replaceReviewers: (participantId: string, reviewers: ReviewerInput[]) => apiClient.put<ReviewerResponse[]>(`${BASE}/participants/${participantId}/reviewers`, { reviewers }).then(({ data }) => data),
  applyCommonItems: (id: string) => apiClient.post<ProgramResponse>(`${BASE}/${id}/common-items:apply`, {}).then(({ data }) => data),
  startStage: (id: string, stage: ProgramStage, participantIds: string[], reason: string) => apiClient.post<BatchOperationResponse>(`${BASE}/${id}/stages/${stage}:start`, { participantIds, reason }).then(({ data }) => data),
  overrideStage: (participantId: string, input: { toStage: ProgramStage; toStatus: ProgramStageStatus; round: number; reason: string }) => apiClient.post<ParticipantResponse>(`${BASE}/participants/${participantId}/stage:override`, input).then(({ data }) => data),
  getMe: (id: string, participantId?: string | null) => apiClient.get<MyProgramWorkspaceResponse>(`${BASE}/${id}/me`, { params: participantId ? { participantId } : undefined }).then(({ data }) => data),
  getEmployeePreview: (participantId: string) => apiClient.get<MyProgramWorkspaceResponse>(`${BASE}/participants/${participantId}/employee-preview`).then(({ data }) => data),
  getMyParticipants: (id: string) => apiClient.get<ParticipantResponse[]>(`${BASE}/${id}/me/participants`).then(({ data }) => data),
  getGoals: (participantId: string) => apiClient.get<GoalResponse[]>(`${BASE}/participants/${participantId}/goals`).then(({ data }) => data),
  getIntermediate: (participantId: string) => apiClient.get<IntermediateResponse | null>(`${BASE}/participants/${participantId}/intermediate`).then(({ data }) => data || null),
  createGoal: (participantId: string, input: GoalUpsertRequest) => apiClient.post<GoalResponse>(`${BASE}/participants/${participantId}/goals`, input).then(({ data }) => data),
  updateGoal: (goalId: string, input: GoalUpsertRequest) => apiClient.put<GoalResponse>(`${BASE}/goals/${goalId}`, input).then(({ data }) => data),
  saveGoalOpinion: (goalId: string, opinion: string) => apiClient.put<GoalResponse>(`${BASE}/goals/${goalId}/opinion`, { opinion }).then(({ data }) => data),
  requestGoalAgreement: (goalId: string) => apiClient.post<GoalResponse>(`${BASE}/goals/${goalId}:request-agreement`, {}).then(({ data }) => data),
  decideGoal: (goalId: string, approve: boolean, opinion: string) => apiClient.post<GoalResponse>(`${BASE}/goals/${goalId}:decide`, { approve, opinion }).then(({ data }) => data),
  selfReportGoal: (goalId: string, achievedLevelCode: string, achievementSummary: string, taskIds: string[]) => apiClient.post<GoalResponse>(`${BASE}/goals/${goalId}:self-report`, { achievedLevelCode, achievementSummary, taskIds }).then(({ data }) => data),
  getGoalHistory: (goalId: string) => apiClient.get<GoalHistoryResponse[]>(`${BASE}/goals/${goalId}/history`).then(({ data }) => data),
  saveIntermediate: (participantId: string, opinion: string, taskIds: string[]) => apiClient.put<IntermediateResponse>(`${BASE}/participants/${participantId}/intermediate`, { opinion, taskIds }).then(({ data }) => data),
  completeIntermediate: (participantId: string, opinion: string, taskIds: string[]) => apiClient.post<IntermediateResponse>(`${BASE}/participants/${participantId}/intermediate:complete`, { opinion, taskIds }).then(({ data }) => data),
  getReviewContext: (participantId: string, round: number) => apiClient.get<ReviewContextResponse>(`${BASE}/participants/${participantId}/review-context`, { params: { round } }).then(({ data }) => data),
  saveReview: (participantId: string, round: number, answers: ReviewItemAnswerInput[], overallOpinion: string) => apiClient.put<ReviewSubmissionResponse>(`${BASE}/participants/${participantId}/review-submission`, { answers, overallOpinion }, { params: { round } }).then(({ data }) => data),
  completeReview: (participantId: string, round: number, answers: ReviewItemAnswerInput[], overallOpinion: string) => apiClient.post<ReviewSubmissionResponse>(`${BASE}/participants/${participantId}/review-submission:complete`, { answers, overallOpinion }, { params: { round } }).then(({ data }) => data),
  runCalculation: (id: string, participantIds: string[], excludeIncomplete: boolean, reason: string) => apiClient.post<CalculationResponse[]>(`${BASE}/${id}/calculations`, { participantIds, excludeIncomplete, reason }).then(({ data }) => data),
  getCalculations: (participantId: string) => apiClient.get<CalculationResponse[]>(`${BASE}/participants/${participantId}/calculations`).then(({ data }) => data),
  saveAdjustment: (participantId: string, adjustedScore: number | null, adjustedGrade: string, reason: string) => apiClient.put<AdjustmentResponse>(`${BASE}/participants/${participantId}/adjustment`, { adjustedScore, adjustedGrade, reason }).then(({ data }) => data),
  completeAdjustment: (participantId: string, adjustedScore: number | null, adjustedGrade: string, reason: string) => apiClient.post<AdjustmentResponse>(`${BASE}/participants/${participantId}/adjustment:complete`, { adjustedScore, adjustedGrade, reason }).then(({ data }) => data),
  saveFeedback: (participantId: string, comment: string) => apiClient.put<FeedbackResponse>(`${BASE}/participants/${participantId}/feedback`, { comment }).then(({ data }) => data),
  getFeedback: (participantId: string) => apiClient.get<FeedbackResponse | null>(`${BASE}/participants/${participantId}/feedback`).then(({ data }) => data),
  deliverFeedback: (participantId: string, comment: string) => apiClient.post<FeedbackResponse>(`${BASE}/participants/${participantId}/feedback:deliver`, { comment }).then(({ data }) => data),
  agreeFeedback: (feedbackId: string) => apiClient.post<FeedbackResponse>(`${BASE}/feedback/${feedbackId}:agree`, {}).then(({ data }) => data),
  appealFeedback: (feedbackId: string, reason: string) => apiClient.post<FeedbackResponse>(`${BASE}/feedback/${feedbackId}:appeal`, { reason }).then(({ data }) => data),
  resolveFeedback: (feedbackId: string, input: { resolution: 'UPHELD' | 'SCORE_ADJUSTED'; adjustedScore?: number; adjustedGrade?: string; comment: string }) => apiClient.post<FeedbackResponse>(`${BASE}/feedback/${feedbackId}:resolve`, input).then(({ data }) => data),
  finalize: (id: string, reason: string) => apiClient.post<ProgramResponse>(`${BASE}/${id}:finalize`, { reason }).then(({ data }) => data),
  cancelFinalization: (id: string, reason: string) => apiClient.post<ProgramResponse>(`${BASE}/${id}:cancel-finalization`, { reason }).then(({ data }) => data),
  publishResults: (id: string, participantIds: string[]) => apiClient.post<BatchOperationResponse>(`${BASE}/${id}/results:publish`, { participantIds }).then(({ data }) => data),
  getDashboard: (id: string) => apiClient.get<ProgramDashboardResponse>(`${BASE}/${id}/dashboard`).then(({ data }) => data),
  getResults: (id: string) => apiClient.get<ResultSummaryResponse>(`${BASE}/${id}/results`).then(({ data }) => data),
  exportResults: (id: string) => apiClient.get<Blob>(`${BASE}/${id}/results.xlsx`, { responseType: 'blob', headers: { Accept: '*/*' } }).then(({ data }) => data),
  getItemResults: (id: string) => apiClient.get<ItemResultRow[]>(`${BASE}/${id}/results/items`).then(({ data }) => data),
  getReviewerResults: (id: string) => apiClient.get<ReviewerResultRow[]>(`${BASE}/${id}/results/reviewers`).then(({ data }) => data),
  getEmployeeFeedback: (participantId: string) => apiClient.get<EmployeeFeedbackDetail>(`${BASE}/participants/${participantId}/result-feedback`).then(({ data }) => data),
  getGradeMatrix: (xProgramId: string, yProgramId: string) => apiClient.get<GradeMatrixResponse>(`${BASE}/analytics/grade-matrix`, { params: { xProgramId, yProgramId } }).then(({ data }) => data),
  getPivot: (programId: string, rowAxes: string[], columnAxes: string[]) => apiClient.post<PivotResponse>(`${BASE}/analytics/pivot`, { programId, rowAxes, columnAxes }).then(({ data }) => data),
  exportPivot: (programId: string, rowAxes: string[], columnAxes: string[]) => apiClient.post<Blob>(`${BASE}/analytics/pivot.xlsx`, { programId, rowAxes, columnAxes }, { responseType: 'blob', headers: { Accept: '*/*' } }).then(({ data }) => data),
  exportPivotSvg: (programId: string, rowAxes: string[], columnAxes: string[]) => apiClient.post<Blob>(`${BASE}/analytics/pivot.svg`, { programId, rowAxes, columnAxes }, { responseType: 'blob', headers: { Accept: '*/*' } }).then(({ data }) => data),
  getTendencies: (id: string) => apiClient.get<ReviewerTendencyResponse[]>(`${BASE}/${id}/analytics/reviewer-tendencies`).then(({ data }) => data),
  getPersonalHistory: () => apiClient.get<PersonalReportResponse>(`${BASE}/me/history`).then(({ data }) => data),
  previewNotifications: (id: string, input: NotificationInput) => apiClient.post<NotificationPreview[]>(`${BASE}/${id}/notifications:preview`, input).then(({ data }) => data),
  queueNotifications: (id: string, input: NotificationInput) => apiClient.post<NotificationQueueResponse>(`${BASE}/${id}/notifications:queue`, input).then(({ data }) => data),
  dispatchNotifications: (id: string) => apiClient.post<NotificationDispatchResponse>(`${BASE}/${id}/notifications:dispatch`, {}).then(({ data }) => data),
  listNotifications: () => apiClient.get<PageEnvelope<NotificationResponse>>(`${BASE}/me/notifications`, { params: { page: 0, size: 100 } }).then(({ data }) => data),
  readNotification: (id: string) => apiClient.post<NotificationResponse>(`${BASE}/me/notifications/${id}:read`, {}).then(({ data }) => data),
  listGuides: (id: string) => apiClient.get<GuideAttachmentResponse[]>(`${BASE}/${id}/guides`).then(({ data }) => data),
  uploadGuide: (id: string, file: File) => { const body = new FormData(); body.append('file', file); return apiClient.post<GuideAttachmentResponse>(`${BASE}/${id}/guides`, body).then(({ data }) => data); },
  downloadGuide: (id: string) => apiClient.get<Blob>(`${BASE}/guides/${id}`, { responseType: 'blob', headers: { Accept: '*/*' } }).then(({ data }) => data),
};

export function useProgramsQuery() { return useQuery({ queryKey: programKeys.list(), queryFn: programsApi.list, select: (page) => page.content }); }
export function useProgramQuery(id: string | null) { return useQuery({ queryKey: programKeys.detail(id ?? ''), queryFn: () => programsApi.get(id as string), enabled: Boolean(id) }); }
export function useProgramParticipantsQuery(id: string | null) { return useQuery({ queryKey: programKeys.participants(id ?? ''), queryFn: () => programsApi.listParticipants(id as string), select: (page) => page.content, enabled: Boolean(id) }); }
export function useParticipantReviewersQuery(id: string | null) { return useQuery({ queryKey: programKeys.reviewers(id ?? ''), queryFn: () => programsApi.getReviewers(id as string), enabled: Boolean(id) }); }
export function useMyProgramQuery(id: string | null, participantId?: string | null) { return useQuery({ queryKey: programKeys.me(id ?? '', participantId), queryFn: () => programsApi.getMe(id as string, participantId), enabled: Boolean(id) && participantId !== null }); }
export function useEmployeePreviewQuery(participantId: string | null) { return useQuery({ queryKey: programKeys.employeePreview(participantId ?? ''), queryFn: () => programsApi.getEmployeePreview(participantId as string), enabled: Boolean(participantId) }); }
export function useMyProgramParticipantsQuery(id: string | null) { return useQuery({ queryKey: programKeys.myParticipants(id ?? ''), queryFn: () => programsApi.getMyParticipants(id as string), enabled: Boolean(id) }); }
export function useParticipantGoalsQuery(id: string | null) { return useQuery({ queryKey: programKeys.goals(id ?? ''), queryFn: () => programsApi.getGoals(id as string), enabled: Boolean(id) }); }
export function useParticipantIntermediateQuery(id: string | null) { return useQuery({ queryKey: programKeys.intermediate(id ?? ''), queryFn: () => programsApi.getIntermediate(id as string), enabled: Boolean(id) }); }
export function useParticipantFeedbackQuery(id: string | null) { return useQuery({ queryKey: programKeys.feedback(id ?? ''), queryFn: () => programsApi.getFeedback(id as string), enabled: Boolean(id) }); }
export function useGoalHistoryQuery(id: string | null) { return useQuery({ queryKey: programKeys.goalHistory(id ?? ''), queryFn: () => programsApi.getGoalHistory(id as string), enabled: Boolean(id) }); }
export function useReviewContextQuery(participantId: string | null, round: number, enabled = true) { return useQuery({ queryKey: programKeys.reviewContext(participantId ?? '', round), queryFn: () => programsApi.getReviewContext(participantId as string, round), enabled: Boolean(participantId) && enabled }); }
export function useCalculationsQuery(participantId: string | null) { return useQuery({ queryKey: programKeys.calculations(participantId ?? ''), queryFn: () => programsApi.getCalculations(participantId as string), enabled: Boolean(participantId) }); }
export function useProgramDashboardQuery(id: string | null) { return useQuery({ queryKey: programKeys.dashboard(id ?? ''), queryFn: () => programsApi.getDashboard(id as string), enabled: Boolean(id) }); }
export function useProgramResultsQuery(id: string | null) { return useQuery({ queryKey: programKeys.results(id ?? ''), queryFn: () => programsApi.getResults(id as string), enabled: Boolean(id) }); }
export function useItemResultsQuery(id: string | null) { return useQuery({ queryKey: programKeys.itemResults(id ?? ''), queryFn: () => programsApi.getItemResults(id as string), enabled: Boolean(id) }); }
export function useReviewerResultsQuery(id: string | null) { return useQuery({ queryKey: programKeys.reviewerResults(id ?? ''), queryFn: () => programsApi.getReviewerResults(id as string), enabled: Boolean(id) }); }
export function useEmployeeFeedbackQuery(id: string | null) { return useQuery({ queryKey: programKeys.employeeFeedback(id ?? ''), queryFn: () => programsApi.getEmployeeFeedback(id as string), enabled: Boolean(id) }); }
export function useGradeMatrixQuery(x: string | null, y: string | null) { return useQuery({ queryKey: programKeys.matrix(x ?? '', y ?? ''), queryFn: () => programsApi.getGradeMatrix(x as string, y as string), enabled: Boolean(x && y) }); }
export function useReviewerTendenciesQuery(id: string | null) { return useQuery({ queryKey: programKeys.tendency(id ?? ''), queryFn: () => programsApi.getTendencies(id as string), enabled: Boolean(id) }); }
export function usePersonalHistoryQuery() { return useQuery({ queryKey: programKeys.personalHistory(), queryFn: programsApi.getPersonalHistory }); }
export function useNotificationsQuery() { return useQuery({ queryKey: programKeys.notifications(), queryFn: programsApi.listNotifications, select: (page) => page.content }); }
export function useProgramGuidesQuery(id: string | null) { return useQuery({ queryKey: programKeys.guides(id ?? ''), queryFn: () => programsApi.listGuides(id as string), enabled: Boolean(id) }); }
export function usePivotMutation() { return useMutation({ mutationFn: ({ programId, rowAxes, columnAxes }: { programId: string; rowAxes: string[]; columnAxes: string[] }) => programsApi.getPivot(programId, rowAxes, columnAxes) }); }

function useProgramMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({ mutationFn, onSuccess: () => queryClient.invalidateQueries({ queryKey: programKeys.all() }) });
}
export const useCreateProgramMutation = () => useProgramMutation(programsApi.create);
export const useCopyProgramMutation = () => useProgramMutation(({ id, input }: { id: string; input: Parameters<typeof programsApi.copy>[1] }) => programsApi.copy(id, input));
export const useUpdateDefinitionMutation = () => useProgramMutation(({ id, configuration, reason }: { id: string; configuration: ProgramConfiguration; reason: string }) => programsApi.updateDefinition(id, configuration, reason));
export const useUpdateProgramBasicMutation = () => useProgramMutation(({ id, input }: { id: string; input: Parameters<typeof programsApi.updateBasic>[1] }) => programsApi.updateBasic(id, input));
export const useOpenProgramMutation = () => useProgramMutation((id: string) => programsApi.open(id));
export const useAddParticipantMutation = () => useProgramMutation(({ id, input }: { id: string; input: Parameters<typeof programsApi.addParticipant>[1] }) => programsApi.addParticipant(id, input));
export const useGenerateParticipantsMutation = () => useProgramMutation(({ id, employeeIds }: { id: string; employeeIds: string[] }) => programsApi.generateParticipants(id, employeeIds));
export const useUpdateParticipantMutation = () => useProgramMutation(({ id, input }: { id: string; input: Parameters<typeof programsApi.updateParticipant>[1] }) => programsApi.updateParticipant(id, input));
export const useImportParticipantsMutation = () => useProgramMutation(({ id, file }: { id: string; file: File }) => programsApi.importParticipants(id, file));
export const useImportReviewersMutation = () => useProgramMutation(({ id, file }: { id: string; file: File }) => programsApi.importReviewers(id, file));
export const useReplaceReviewersMutation = () => useProgramMutation(({ participantId, reviewers }: { participantId: string; reviewers: ReviewerInput[] }) => programsApi.replaceReviewers(participantId, reviewers));
export const useApplyCommonItemsMutation = () => useProgramMutation((id: string) => programsApi.applyCommonItems(id));
export const useStartStageMutation = () => useProgramMutation(({ id, stage, participantIds, reason }: { id: string; stage: ProgramStage; participantIds: string[]; reason: string }) => programsApi.startStage(id, stage, participantIds, reason));
export const useOverrideStageMutation = () => useProgramMutation(({ participantId, input }: { participantId: string; input: Parameters<typeof programsApi.overrideStage>[1] }) => programsApi.overrideStage(participantId, input));
export const useCreateProgramGoalMutation = () => useProgramMutation(({ participantId, input }: { participantId: string; input: GoalUpsertRequest }) => programsApi.createGoal(participantId, input));
export const useUpdateProgramGoalMutation = () => useProgramMutation(({ goalId, input }: { goalId: string; input: GoalUpsertRequest }) => programsApi.updateGoal(goalId, input));
export const useSaveGoalOpinionMutation = () => useProgramMutation(({ goalId, opinion }: { goalId: string; opinion: string }) => programsApi.saveGoalOpinion(goalId, opinion));
export const useRequestGoalAgreementMutation = () => useProgramMutation((goalId: string) => programsApi.requestGoalAgreement(goalId));
export const useDecideProgramGoalMutation = () => useProgramMutation(({ goalId, approve, opinion }: { goalId: string; approve: boolean; opinion: string }) => programsApi.decideGoal(goalId, approve, opinion));
export const useSelfReportGoalMutation = () => useProgramMutation(({ goalId, achievedLevelCode, achievementSummary, taskIds }: { goalId: string; achievedLevelCode: string; achievementSummary: string; taskIds: string[] }) => programsApi.selfReportGoal(goalId, achievedLevelCode, achievementSummary, taskIds));
export const useSaveIntermediateMutation = () => useProgramMutation(({ participantId, opinion, taskIds }: { participantId: string; opinion: string; taskIds: string[] }) => programsApi.saveIntermediate(participantId, opinion, taskIds));
export const useCompleteIntermediateMutation = () => useProgramMutation(({ participantId, opinion, taskIds }: { participantId: string; opinion: string; taskIds: string[] }) => programsApi.completeIntermediate(participantId, opinion, taskIds));
export const useSaveReviewMutation = () => useProgramMutation(({ participantId, round, answers, overallOpinion }: { participantId: string; round: number; answers: ReviewItemAnswerInput[]; overallOpinion: string }) => programsApi.saveReview(participantId, round, answers, overallOpinion));
export const useCompleteReviewMutation = () => useProgramMutation(({ participantId, round, answers, overallOpinion }: { participantId: string; round: number; answers: ReviewItemAnswerInput[]; overallOpinion: string }) => programsApi.completeReview(participantId, round, answers, overallOpinion));
export const useRunCalculationMutation = () => useProgramMutation(({ id, participantIds, excludeIncomplete, reason }: { id: string; participantIds: string[]; excludeIncomplete: boolean; reason: string }) => programsApi.runCalculation(id, participantIds, excludeIncomplete, reason));
export const useSaveAdjustmentMutation = () => useProgramMutation(({ participantId, adjustedScore, adjustedGrade, reason }: { participantId: string; adjustedScore: number | null; adjustedGrade: string; reason: string }) => programsApi.saveAdjustment(participantId, adjustedScore, adjustedGrade, reason));
export const useCompleteAdjustmentMutation = () => useProgramMutation(({ participantId, adjustedScore, adjustedGrade, reason }: { participantId: string; adjustedScore: number | null; adjustedGrade: string; reason: string }) => programsApi.completeAdjustment(participantId, adjustedScore, adjustedGrade, reason));
export const useSaveFeedbackMutation = () => useProgramMutation(({ participantId, comment }: { participantId: string; comment: string }) => programsApi.saveFeedback(participantId, comment));
export const useDeliverFeedbackMutation = () => useProgramMutation(({ participantId, comment }: { participantId: string; comment: string }) => programsApi.deliverFeedback(participantId, comment));
export const useAgreeFeedbackMutation = () => useProgramMutation((feedbackId: string) => programsApi.agreeFeedback(feedbackId));
export const useAppealProgramFeedbackMutation = () => useProgramMutation(({ feedbackId, reason }: { feedbackId: string; reason: string }) => programsApi.appealFeedback(feedbackId, reason));
export const useResolveProgramFeedbackMutation = () => useProgramMutation(({ feedbackId, input }: { feedbackId: string; input: Parameters<typeof programsApi.resolveFeedback>[1] }) => programsApi.resolveFeedback(feedbackId, input));
export const useFinalizeProgramMutation = () => useProgramMutation(({ id, reason }: { id: string; reason: string }) => programsApi.finalize(id, reason));
export const useCancelFinalizationMutation = () => useProgramMutation(({ id, reason }: { id: string; reason: string }) => programsApi.cancelFinalization(id, reason));
export const usePublishResultsMutation = () => useProgramMutation(({ id, participantIds }: { id: string; participantIds: string[] }) => programsApi.publishResults(id, participantIds));
export const usePreviewNotificationsMutation = () => useMutation({ mutationFn: ({ id, input }: { id: string; input: NotificationInput }) => programsApi.previewNotifications(id, input) });
export const useQueueNotificationsMutation = () => useProgramMutation(({ id, input }: { id: string; input: NotificationInput }) => programsApi.queueNotifications(id, input));
export const useDispatchNotificationsMutation = () => useProgramMutation((id: string) => programsApi.dispatchNotifications(id));
export const useReadNotificationMutation = () => useProgramMutation((id: string) => programsApi.readNotification(id));
export const useUploadGuideMutation = () => useProgramMutation(({ id, file }: { id: string; file: File }) => programsApi.uploadGuide(id, file));

export { programsApi };

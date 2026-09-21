import { buildQueryKey } from '@easy/query-client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { CycleResponse, CycleStatus } from './cycles';
import { cyclesQueryKeys } from './cycles';
import type { ReportResponse } from './reports';
import type { ReviewItemScoreInput, ReviewKpiItemResponse, ReviewResponse } from './reviews';
import type { PageEnvelope } from './selfEvaluation';
import { apiClient } from './client';

const BASE = '/v1/evaluation-workspace';

export type WorkspaceRole = 'HR_ADMIN' | 'SUPER_ADMIN' | 'DIRECTOR' | 'MANAGER' | 'EMPLOYEE';
export type ParticipantStatus = 'ACTIVE' | 'EXCLUDED';
export type ReviewerType = 'MANAGER' | 'PEER' | 'HR';
export type GoalStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';
export type IntermediateReviewStatus = 'DRAFT' | 'EMPLOYEE_SUBMITTED' | 'MANAGER_COMPLETED';
export type AllowedAction =
  | 'CREATE_GOAL' | 'EDIT_GOAL' | 'SUBMIT_GOAL' | 'REVIEW_GOAL'
  | 'ADD_CHECK_IN'
  | 'EDIT_INTERMEDIATE_REVIEW' | 'SUBMIT_INTERMEDIATE_REVIEW' | 'COMPLETE_INTERMEDIATE_REVIEW'
  | 'EDIT_SELF_REVIEW' | 'SUBMIT_SELF_REVIEW' | 'EDIT_MANAGER_REVIEW'
  | 'SUBMIT_MANAGER_REVIEW' | 'VIEW_REPORT' | 'ACKNOWLEDGE_REPORT'
  | 'EDIT_FEEDBACK' | 'COMPLETE_FEEDBACK' | 'ACCEPT_FEEDBACK' | 'APPEAL_FEEDBACK'
  | 'RESOLVE_APPEAL';

export interface EmployeeSummary {
  id: string;
  employeeNo: string;
  name: string;
  orgUnitId: string | null;
  orgUnitName: string | null;
  status: string;
  hasUserBinding: boolean;
}

export interface WorkspaceMe {
  userId: string;
  tenantId: string;
  employeeId: string | null;
  displayName: string;
  role: WorkspaceRole;
}

export interface Participant {
  id: string;
  cycleId: string;
  employee: EmployeeSummary;
  status: ParticipantStatus;
  manager: EmployeeSummary | null;
  reviewId: string | null;
  reviewStatus: string | null;
}

export interface GateBlocker {
  code: string;
  message: string;
  participantId: string | null;
  employeeId: string | null;
}

export interface Goal {
  id: string;
  cycleId: string;
  employeeId: string;
  title: string;
  description: string | null;
  weight: number;
  target: number | null;
  unit: string | null;
  status: GoalStatus;
  decisionComment: string | null;
  approvedAt: string | null;
  approvedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CheckIn {
  id: string;
  goalId: string;
  asOfDate: string;
  actualValue: number;
  progressPercent: number | null;
  note: string | null;
  evidenceUrl: string | null;
  createdAt: string;
}

export interface IntermediateReview {
  id: string;
  cycleId: string;
  participantId: string;
  employeeId: string;
  status: IntermediateReviewStatus;
  progressSummary: string;
  achievements: string | null;
  blockers: string | null;
  supportNeeded: string | null;
  managerComment: string | null;
  employeeSubmittedAt: string | null;
  managerCompletedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WorkspaceResponse {
  cycle: CycleResponse;
  participant: Participant | null;
  goals: Goal[];
  intermediateReview: IntermediateReview | null;
  review: ReviewResponse | null;
  report: ReportResponse | null;
  feedback: FeedbackResponse | null;
  allowedActions: AllowedAction[];
  blockers: GateBlocker[];
}

export interface ManagerTask {
  participantId: string;
  cycleId: string;
  employee: EmployeeSummary;
  goalTotal: number;
  goalPendingApproval: number;
  intermediateReviewStatus: IntermediateReviewStatus | null;
  reviewId: string | null;
  reviewStatus: string | null;
  allowedActions: AllowedAction[];
  blockers: GateBlocker[];
}

export interface FeedbackResponse {
  id: string;
  reportId: string;
  participantId: string;
  status: 'DRAFT' | 'COMPLETED' | 'ACCEPTED' | 'APPEALED' | 'RESOLVED';
  comment: string | null;
  appealReason: string | null;
  resolution: string | null;
  resolutionComment: string | null;
  completedAt?: string | null;
  appealedAt?: string | null;
  resolvedAt?: string | null;
  updatedAt?: string | null;
}

export interface FeedbackTask {
  reportId: string;
  participantId: string;
  employee: EmployeeSummary;
  status: FeedbackResponse['status'] | null;
  comment: string | null;
  appealReason: string | null;
  resolution: string | null;
  allowedActions: AllowedAction[];
}

export interface ResultsSummary {
  cycleId: string;
  participantCount: number;
  finalizedCount: number;
  publishedCount: number;
  acknowledgedCount: number;
  averageScore: number | null;
  gradeCounts: Record<string, number>;
  orgUnitRows: Array<{ orgUnitId: string | null; orgUnitName: string | null; participantCount: number; averageScore: number | null; gradeCounts: Record<string, number> }>;
}

export interface CalibrationTasksResponse {
  cycleId: string;
  sessions: Array<{ id: string; status: string; scheduledAt: string | null; ownerOrgUnitId: string | null; participantCount: number }>;
  rows: Array<{ reviewId: string; participantId: string; employee: EmployeeSummary; kpiScore: number | null; currentGrade: string | null; proposedGrade: string | null; adjusted: boolean }>;
  targetDistribution: Record<string, number>;
  currentDistribution: Record<string, number>;
}

export interface CalibrationSessionResponse {
  id: string;
  status: string;
  scheduledAt: string | null;
  ownerOrgUnitId: string | null;
  participantIds: string[];
}

export interface CalibrationApplyResponse {
  appliedCount: number;
  skippedCount: number;
  resultingDistribution: Record<string, number>;
}

export interface CalibrationConfirmResponse {
  session: CalibrationSessionResponse;
  finalizedCount: number;
  skippedCount: number;
}

export interface ParticipantUpsert {
  employeeId: string;
  managerEmployeeId: string;
}

export interface ParticipantUpsertResponse {
  cycleId: string;
  activeCount: number;
  excludedCount: number;
  missingManagerCount: number;
  items: Participant[];
}

export const workspaceQueryKeys = {
  all: () => buildQueryKey('performance', 'evaluation-workspace'),
  me: () => buildQueryKey('performance', 'evaluation-workspace', 'me'),
  cycles: () => buildQueryKey('performance', 'evaluation-workspace', 'cycles'),
  cycle: (cycleId: string) => buildQueryKey('performance', 'evaluation-workspace', 'cycle-detail', cycleId),
  directory: (query: string, page: number, size: number) => buildQueryKey('performance', 'evaluation-workspace', 'directory', { query, page, size }),
  participants: (cycleId: string) => buildQueryKey('performance', 'evaluation-workspace', 'participants', cycleId),
  managerTasks: (cycleId: string) => buildQueryKey('performance', 'evaluation-workspace', 'manager-tasks', cycleId),
  results: (cycleId: string) => buildQueryKey('performance', 'evaluation-workspace', 'results', cycleId),
  participantGoals: (participantId: string) => buildQueryKey('performance', 'evaluation-workspace', 'participant-goals', participantId),
  participantIntermediate: (participantId: string) => buildQueryKey('performance', 'evaluation-workspace', 'participant-intermediate', participantId),
  calibration: (cycleId: string) => buildQueryKey('performance', 'evaluation-workspace', 'calibration', cycleId),
  reviewItems: (reviewId: string) => buildQueryKey('performance', 'evaluation-workspace', 'review-items', reviewId),
  feedbackTasks: (cycleId: string) => buildQueryKey('performance', 'evaluation-workspace', 'feedback-tasks', cycleId),
  checkIns: (goalId: string) => buildQueryKey('performance', 'evaluation-workspace', 'check-ins', goalId),
  workspace: (cycleId: string) => buildQueryKey('performance', 'evaluation-workspace', 'cycle', cycleId, 'me'),
} as const;

const workspaceApi = {
  me: () => apiClient.get<WorkspaceMe>(`${BASE}/me`).then((response) => response.data),
  cycles: () => apiClient.get<PageEnvelope<CycleResponse>>(`${BASE}/cycles`, { params: { page: 0, size: 100 } }).then((response) => response.data),
  cycle: (cycleId: string) => apiClient.get<CycleResponse>(`${BASE}/cycles/${cycleId}`).then((response) => response.data),
  directory: (query: string, page: number, size: number) => apiClient.get<PageEnvelope<EmployeeSummary>>(`${BASE}/directory`, { params: { q: query || undefined, page, size } }).then((response) => response.data),
  participants: (cycleId: string) => apiClient.get<PageEnvelope<Participant>>(`${BASE}/cycles/${cycleId}/participants`, { params: { page: 0, size: 100 } }).then((response) => response.data),
  workspace: (cycleId: string) => apiClient.get<WorkspaceResponse>(`${BASE}/cycles/${cycleId}/me`).then((response) => response.data),
  managerTasks: (cycleId: string) => apiClient.get<ManagerTask[]>(`${BASE}/manager/tasks`, { params: { cycleId } }).then((response) => response.data),
  results: (cycleId: string) => apiClient.get<ResultsSummary>(`${BASE}/cycles/${cycleId}/results/summary`).then((response) => response.data),
  participantGoals: (participantId: string) => apiClient.get<Goal[]>(`${BASE}/participants/${participantId}/goals`).then((response) => response.data),
  participantIntermediate: (participantId: string) => apiClient.get<IntermediateReview | null>(`${BASE}/participants/${participantId}/intermediate-review`).then((response) => response.data),
  calibration: (cycleId: string) => apiClient.get<CalibrationTasksResponse>(`${BASE}/cycles/${cycleId}/calibration/tasks`).then((response) => response.data),
  adjustCalibration: (sessionId: string, reviewId: string, toGrade: string, reason: string) => apiClient.post(`${BASE}/calibration-sessions/${sessionId}/adjustments`, { reviewId, toGrade, reason }).then((response) => response.data),
  reviewItems: (reviewId: string) => apiClient.get<ReviewKpiItemResponse[]>(`${BASE}/reviews/${reviewId}/kpi-items`).then((response) => response.data),
  feedbackTasks: (cycleId: string) => apiClient.get<FeedbackTask[]>(`${BASE}/cycles/${cycleId}/feedback`).then((response) => response.data),
  checkIns: (goalId: string) => apiClient.get<CheckIn[]>(`${BASE}/goals/${goalId}/check-ins`).then((response) => response.data),
  upsertParticipants: (cycleId: string, participants: ParticipantUpsert[]) => apiClient.put<ParticipantUpsertResponse>(`${BASE}/cycles/${cycleId}/participants`, { participants }).then((response) => response.data),
  open: (cycleId: string) => apiClient.post(`${BASE}/cycles/${cycleId}/open`).then((response) => response.data),
  advance: (cycleId: string, targetStatus: CycleStatus) => apiClient.post(`${BASE}/cycles/${cycleId}/advance`, { targetStatus }).then((response) => response.data),
  createGoal: (cycleId: string, input: Omit<Goal, 'id' | 'cycleId' | 'employeeId' | 'status' | 'decisionComment' | 'approvedAt' | 'approvedBy' | 'createdAt' | 'updatedAt'>) => apiClient.post<Goal>(`${BASE}/cycles/${cycleId}/goals`, input).then((response) => response.data),
  updateGoal: (goalId: string, input: Partial<Pick<Goal, 'title' | 'description' | 'weight' | 'target' | 'unit'>>) => apiClient.patch<Goal>(`${BASE}/goals/${goalId}`, input).then((response) => response.data),
  submitGoal: (goalId: string) => apiClient.post<Goal>(`${BASE}/goals/${goalId}/submit`, {}).then((response) => response.data),
  createCheckIn: (goalId: string, input: CheckInInput) => apiClient.post<CheckIn>(`${BASE}/goals/${goalId}/check-ins`, input).then((response) => response.data),
  saveIntermediate: (cycleId: string, input: IntermediateInput) => apiClient.put<IntermediateReview>(`${BASE}/cycles/${cycleId}/me/intermediate-review`, input).then((response) => response.data),
  submitIntermediate: (cycleId: string, input: IntermediateInput) => apiClient.post<IntermediateReview>(`${BASE}/cycles/${cycleId}/me/intermediate-review/submit`, input).then((response) => response.data),
  saveSelfReview: (reviewId: string, comment: string) => apiClient.post<ReviewResponse>(`${BASE}/reviews/${reviewId}/self/draft`, { comment }).then((response) => response.data),
  submitSelfReview: (reviewId: string, comment: string) => apiClient.post<ReviewResponse>(`${BASE}/reviews/${reviewId}/self/submit`, { comment }).then((response) => response.data),
  createCalibration: (cycleId: string) => apiClient.post<CalibrationSessionResponse>(`${BASE}/cycles/${cycleId}/calibration-sessions`, {}).then((response) => response.data),
  applyCalibration: (cycleId: string, targetDistribution?: Record<string, number>) => apiClient.post<CalibrationApplyResponse>(`${BASE}/cycles/${cycleId}/calibration/apply`, { targetDistribution: targetDistribution ?? null }).then((response) => response.data),
  publish: (cycleId: string) => apiClient.post(`${BASE}/cycles/${cycleId}/reports/publish`, {}).then((response) => response.data),
  acknowledgeReport: (reportId: string) => apiClient.post(`${BASE}/reports/${reportId}/acknowledge`, {}).then((response) => response.data),
  close: (cycleId: string) => apiClient.post<CycleResponse>(`${BASE}/cycles/${cycleId}/close`).then((response) => response.data),
  confirmCalibration: (cycleId: string, sessionId: string) => apiClient.post<CalibrationConfirmResponse>(`${BASE}/cycles/${cycleId}/calibration/confirm`, { sessionId }).then((response) => response.data),
  decideGoal: (goalId: string, decision: 'APPROVE' | 'REJECT', comment: string) => apiClient.post<Goal>(`${BASE}/goals/${goalId}/decision`, { decision, comment: comment || undefined }).then((response) => response.data),
  saveManagerIntermediate: (participantId: string, managerComment: string) => apiClient.put<IntermediateReview>(`${BASE}/participants/${participantId}/intermediate-review`, { managerComment }).then((response) => response.data),
  completeManagerIntermediate: (participantId: string) => apiClient.post<IntermediateReview>(`${BASE}/participants/${participantId}/intermediate-review/complete`, {}).then((response) => response.data),
  saveFeedback: (reportId: string, comment: string) => apiClient.put<FeedbackResponse>(`${BASE}/reports/${reportId}/feedback`, { comment }).then((response) => response.data),
  completeFeedback: (reportId: string, comment: string) => apiClient.post<FeedbackResponse>(`${BASE}/reports/${reportId}/feedback/complete`, { comment }).then((response) => response.data),
  appeal: (reportId: string, reason: string) => apiClient.post<FeedbackResponse>(`${BASE}/reports/${reportId}/appeal`, { reason }).then((response) => response.data),
  acceptFeedback: (reportId: string) => apiClient.post<FeedbackResponse>(`${BASE}/reports/${reportId}/accept`, {}).then((response) => response.data),
  resolveAppeal: (reportId: string, resolution: 'UPHELD' | 'ADJUSTMENT_REQUIRED', comment: string) => apiClient.post<FeedbackResponse>(`${BASE}/reports/${reportId}/appeal/resolve`, { resolution, comment }).then((response) => response.data),
  saveManagerReview: (reviewId: string, comment: string, itemScores: ReviewItemScoreInput[]) => apiClient.post<ReviewResponse>(`${BASE}/reviews/${reviewId}/manager/draft`, { comment: comment || undefined, itemScores }).then((response) => response.data),
  submitManagerReview: (reviewId: string, comment: string, itemScores: ReviewItemScoreInput[]) => apiClient.post<ReviewResponse>(`${BASE}/reviews/${reviewId}/manager/submit`, { comment: comment || undefined, itemScores }).then((response) => response.data),
};

export interface IntermediateInput {
  progressSummary: string;
  achievements?: string;
  blockers?: string;
  supportNeeded?: string;
}

export interface CheckInInput {
  asOfDate: string;
  actualValue: number;
  progressPercent?: number;
  note?: string;
  evidenceUrl?: string;
}

export function useWorkspaceMeQuery() {
  return useQuery({ queryKey: workspaceQueryKeys.me(), queryFn: workspaceApi.me });
}

export function useWorkspaceCyclesQuery() {
  return useQuery({ queryKey: workspaceQueryKeys.cycles(), queryFn: workspaceApi.cycles });
}

export function useWorkspaceCycleQuery(cycleId: string | null) {
  return useQuery({
    queryKey: workspaceQueryKeys.cycle(cycleId ?? ''),
    queryFn: () => workspaceApi.cycle(cycleId as string),
    enabled: Boolean(cycleId),
  });
}

export function useEmployeeDirectoryQuery(query: string) {
  return useQuery({ queryKey: workspaceQueryKeys.directory(query, 0, 100), queryFn: () => workspaceApi.directory(query, 0, 100) });
}

export function useParticipantsQuery(cycleId: string | null) {
  return useQuery({ queryKey: workspaceQueryKeys.participants(cycleId ?? ''), queryFn: () => workspaceApi.participants(cycleId as string), enabled: Boolean(cycleId) });
}

export function useManagerTasksQuery(cycleId: string | null) { return useQuery({ queryKey: workspaceQueryKeys.managerTasks(cycleId ?? ''), queryFn: () => workspaceApi.managerTasks(cycleId as string), enabled: Boolean(cycleId) }); }
export function useResultsSummaryQuery(cycleId: string | null, enabled: boolean) { return useQuery({ queryKey: workspaceQueryKeys.results(cycleId ?? ''), queryFn: () => workspaceApi.results(cycleId as string), enabled: Boolean(cycleId) && enabled }); }
export function useParticipantGoalsQuery(participantId: string | null) { return useQuery({ queryKey: workspaceQueryKeys.participantGoals(participantId ?? ''), queryFn: () => workspaceApi.participantGoals(participantId as string), enabled: Boolean(participantId) }); }
export function useParticipantIntermediateQuery(participantId: string | null) { return useQuery({ queryKey: workspaceQueryKeys.participantIntermediate(participantId ?? ''), queryFn: () => workspaceApi.participantIntermediate(participantId as string), enabled: Boolean(participantId) }); }
export function useCalibrationTasksQuery(cycleId: string | null, enabled: boolean) { return useQuery({ queryKey: workspaceQueryKeys.calibration(cycleId ?? ''), queryFn: () => workspaceApi.calibration(cycleId as string), enabled: Boolean(cycleId) && enabled }); }
export function useWorkspaceReviewItemsQuery(reviewId: string | null) { return useQuery({ queryKey: workspaceQueryKeys.reviewItems(reviewId ?? ''), queryFn: () => workspaceApi.reviewItems(reviewId as string), enabled: Boolean(reviewId) }); }
export function useFeedbackTasksQuery(cycleId: string | null, enabled: boolean) { return useQuery({ queryKey: workspaceQueryKeys.feedbackTasks(cycleId ?? ''), queryFn: () => workspaceApi.feedbackTasks(cycleId as string), enabled: Boolean(cycleId) && enabled }); }
export function useCheckInsQuery(goalId: string | null, enabled: boolean) { return useQuery({ queryKey: workspaceQueryKeys.checkIns(goalId ?? ''), queryFn: () => workspaceApi.checkIns(goalId as string), enabled: Boolean(goalId) && enabled }); }

export function useEvaluationWorkspaceQuery(cycleId: string | null) {
  return useQuery({ queryKey: workspaceQueryKeys.workspace(cycleId ?? ''), queryFn: () => workspaceApi.workspace(cycleId as string), enabled: Boolean(cycleId) });
}

function useWorkspaceMutation<TInput, TResult>(mutation: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: mutation,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.all() }),
        queryClient.invalidateQueries({ queryKey: cyclesQueryKeys.all() }),
      ]);
    },
  });
}

export function useUpsertParticipantsMutation(cycleId: string) { return useWorkspaceMutation((participants: ParticipantUpsert[]) => workspaceApi.upsertParticipants(cycleId, participants)); }
export function useOpenCycleMutation(cycleId: string) { return useWorkspaceMutation(() => workspaceApi.open(cycleId)); }
export function useAdvanceCycleMutation(cycleId: string) { return useWorkspaceMutation((status: CycleStatus) => workspaceApi.advance(cycleId, status)); }
export function useCreateGoalMutation(cycleId: string) { return useWorkspaceMutation((input: Parameters<typeof workspaceApi.createGoal>[1]) => workspaceApi.createGoal(cycleId, input)); }
export function useUpdateGoalMutation() { return useWorkspaceMutation(({ goalId, input }: { goalId: string; input: Parameters<typeof workspaceApi.updateGoal>[1] }) => workspaceApi.updateGoal(goalId, input)); }
export function useSubmitGoalMutation() { return useWorkspaceMutation((goalId: string) => workspaceApi.submitGoal(goalId)); }
export function useCreateCheckInMutation() { return useWorkspaceMutation(({ goalId, input }: { goalId: string; input: CheckInInput }) => workspaceApi.createCheckIn(goalId, input)); }
export function useSaveIntermediateMutation(cycleId: string) { return useWorkspaceMutation((input: IntermediateInput) => workspaceApi.saveIntermediate(cycleId, input)); }
export function useSubmitIntermediateMutation(cycleId: string) { return useWorkspaceMutation((input: IntermediateInput) => workspaceApi.submitIntermediate(cycleId, input)); }
export function useSaveSelfReviewMutation(reviewId: string) { return useWorkspaceMutation((comment: string) => workspaceApi.saveSelfReview(reviewId, comment)); }
export function useSubmitSelfReviewMutation(reviewId: string) { return useWorkspaceMutation((comment: string) => workspaceApi.submitSelfReview(reviewId, comment)); }
export function usePublishReportsMutation(cycleId: string) { return useWorkspaceMutation(() => workspaceApi.publish(cycleId)); }
export function useAcknowledgeReportMutation(reportId: string) { return useWorkspaceMutation(() => workspaceApi.acknowledgeReport(reportId)); }
export function useCloseCycleMutation(cycleId: string) { return useWorkspaceMutation(() => workspaceApi.close(cycleId)); }
export function useConfirmCalibrationMutation(cycleId: string) { return useWorkspaceMutation((sessionId: string) => workspaceApi.confirmCalibration(cycleId, sessionId)); }
export function useCreateCalibrationMutation(cycleId: string) { return useWorkspaceMutation(() => workspaceApi.createCalibration(cycleId)); }
export function useApplyCalibrationMutation(cycleId: string) { return useWorkspaceMutation((targetDistribution?: Record<string, number>) => workspaceApi.applyCalibration(cycleId, targetDistribution)); }
export function useGoalDecisionMutation() { return useWorkspaceMutation(({ goalId, decision, comment }: { goalId: string; decision: 'APPROVE' | 'REJECT'; comment: string }) => workspaceApi.decideGoal(goalId, decision, comment)); }
export function useSaveManagerIntermediateMutation() { return useWorkspaceMutation(({ participantId, comment }: { participantId: string; comment: string }) => workspaceApi.saveManagerIntermediate(participantId, comment)); }
export function useCompleteManagerIntermediateMutation() { return useWorkspaceMutation((participantId: string) => workspaceApi.completeManagerIntermediate(participantId)); }
export function useSaveFeedbackMutation() { return useWorkspaceMutation(({ reportId, comment }: { reportId: string; comment: string }) => workspaceApi.saveFeedback(reportId, comment)); }
export function useCompleteFeedbackMutation() { return useWorkspaceMutation(({ reportId, comment }: { reportId: string; comment: string }) => workspaceApi.completeFeedback(reportId, comment)); }
export function useAppealMutation() { return useWorkspaceMutation(({ reportId, reason }: { reportId: string; reason: string }) => workspaceApi.appeal(reportId, reason)); }
export function useAcceptFeedbackMutation() { return useWorkspaceMutation((reportId: string) => workspaceApi.acceptFeedback(reportId)); }
export function useResolveAppealMutation() { return useWorkspaceMutation(({ reportId, resolution, comment }: { reportId: string; resolution: 'UPHELD' | 'ADJUSTMENT_REQUIRED'; comment: string }) => workspaceApi.resolveAppeal(reportId, resolution, comment)); }
export function useSaveManagerReviewMutation(reviewId: string) { return useWorkspaceMutation(({ comment, itemScores }: { comment: string; itemScores: ReviewItemScoreInput[] }) => workspaceApi.saveManagerReview(reviewId, comment, itemScores)); }
export function useSubmitManagerReviewMutation(reviewId: string) { return useWorkspaceMutation(({ comment, itemScores }: { comment: string; itemScores: ReviewItemScoreInput[] }) => workspaceApi.submitManagerReview(reviewId, comment, itemScores)); }
export function useCalibrationAdjustmentMutation() { return useWorkspaceMutation(({ sessionId, reviewId, toGrade, reason }: { sessionId: string; reviewId: string; toGrade: string; reason: string }) => workspaceApi.adjustCalibration(sessionId, reviewId, toGrade, reason)); }

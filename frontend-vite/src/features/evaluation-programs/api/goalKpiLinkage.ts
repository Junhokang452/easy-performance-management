import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buildQueryKey } from '@easy/query-client';

import { apiClient } from '../../../api/client';
import type { CycleResponse } from '../../../api/cycles';
import type { PageEnvelope } from '../../../api/selfEvaluation';
import { programKeys } from './programs';

export type KpiLinkAvailability = 'READY' | 'SOURCE_MISSING' | 'BLOCKED';
export type KpiNodeSource = 'MANUAL' | 'HCM' | 'EXTERNAL';
export type KpiActualSource = 'MANUAL' | 'AUTO' | 'IMPORT';

export interface KpiCandidateResponse {
  kpiAssignmentId: string;
  kpiNodeId: string;
  nodeLabel: string;
  treeId: string;
  treeName: string;
  cycleId: string;
  cycleName: string;
  employeeId: string;
  effectiveWeight: number;
  effectiveTarget: number | null;
  unit: string | null;
  nodeSource: KpiNodeSource;
  latestActualId: string | null;
  latestActualAsOfDate: string | null;
  latestActualValue: number | null;
  latestActualSource: KpiActualSource | null;
  achievementRate: number | null;
  autoScore: number | null;
  availability: KpiLinkAvailability;
  reasonCode: string | null;
}

export interface KpiLinkPreviewRow {
  goalId: string;
  goalRevision: number;
  goalRowVersion: number;
  goalTitle: string;
  goalWeightPercent: number;
  kpiAssignmentId: string;
  kpiAssignmentUpdatedAt: string;
  kpiNodeId: string;
  kpiNodeUpdatedAt: string;
  nodeLabel: string;
  treeId: string;
  treeName: string;
  cycleId: string;
  employeeId: string;
  effectiveWeight: number;
  effectiveTarget: number | null;
  unit: string | null;
  nodeSource: KpiNodeSource;
  actualId: string | null;
  actualAsOfDate: string | null;
  actualValue: number | null;
  actualSource: KpiActualSource | null;
  actualCreatedAt: string | null;
  actualSupersedesId: string | null;
  achievementRate: number | null;
  autoScore: number | null;
  formulaVersion: 'KPI_ACHIEVEMENT_V1';
  formula: string;
  status: KpiLinkAvailability;
  reasonCode: string | null;
}

export interface KpiLinkPreviewInput {
  cycleId: string;
  actualCutoffDate: string;
  kpiAssignmentId: string;
}

export interface KpiLinkPreviewResponse {
  programId: string;
  participantId: string;
  cycleId: string;
  programAsOfDate: string;
  actualCutoffDate: string;
  programDefinitionRevision: number;
  programRowVersion: number;
  participantRowVersion: number;
  previewHash: string;
  capturedAt: string;
  row: KpiLinkPreviewRow;
}

export interface KpiSourceSnapshot {
  programDefinitionRevision: number;
  programRowVersion: number;
  participantRowVersion: number;
  goalRevision: number;
  goalRowVersion: number;
  kpiAssignmentUpdatedAt: string;
  kpiNodeUpdatedAt: string;
  actualId: string;
  actualCreatedAt: string;
}

export interface KpiLinkApplyInput extends KpiLinkPreviewInput {
  previewHash: string;
  reason: string;
}

export interface KpiLinkApplyResponse {
  evidenceId: string;
  programId: string;
  participantId: string;
  goalId: string;
  cycleId: string;
  revision: number;
  supersedesEvidenceId: string | null;
  actualCutoffDate: string;
  previewHash: string;
  appliedAt: string;
  appliedByEmployeeId: string | null;
  active: boolean;
  evidence: KpiLinkPreviewRow;
  sourceSnapshot: KpiSourceSnapshot;
  reason: string;
}

const basePath = (programId: string, participantId: string, goalId: string) =>
  `/v1/evaluation-programs/${programId}/participants/${participantId}/goals/${goalId}/kpi-link`;

const candidatePath = (programId: string, participantId: string) =>
  `/v1/evaluation-programs/${programId}/participants/${participantId}/kpi-candidates`;

export const goalKpiLinkageKeys = {
  all: () => buildQueryKey('performance', 'evaluation-programs', 'goal-kpi-links'),
  candidates: (programId: string, participantId: string, cycleId: string, actualCutoffDate: string) =>
    buildQueryKey('performance', 'evaluation-programs', programId, 'participants', participantId, 'kpi-candidates', cycleId, actualCutoffDate),
  history: (programId: string, participantId: string, goalId: string) =>
    buildQueryKey('performance', 'evaluation-programs', programId, 'participants', participantId, 'goals', goalId, 'kpi-links'),
} as const;

export function useGoalKpiCandidatesQuery(input: {
  programId: string | null;
  participantId: string | null;
  cycleId: string | null;
  actualCutoffDate: string | null;
  page?: number;
}) {
  const { programId, participantId, cycleId, actualCutoffDate, page = 0 } = input;
  return useQuery({
    queryKey: [...goalKpiLinkageKeys.candidates(programId ?? '', participantId ?? '', cycleId ?? '', actualCutoffDate ?? ''), page],
    queryFn: () => apiClient.get<PageEnvelope<KpiCandidateResponse>>(candidatePath(programId as string, participantId as string), {
      params: { cycleId, actualCutoffDate, page, size: 100 },
    }).then(({ data }) => data),
    enabled: Boolean(programId && participantId && cycleId && actualCutoffDate),
  });
}

export function useGoalKpiPreviewMutation(programId: string, participantId: string, goalId: string) {
  return useMutation({
    mutationFn: (input: KpiLinkPreviewInput) => apiClient.post<KpiLinkPreviewResponse>(`${basePath(programId, participantId, goalId)}:preview`, input).then(({ data }) => data),
  });
}

export function useGoalKpiApplyMutation(programId: string, participantId: string, goalId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: KpiLinkApplyInput) => apiClient.post<KpiLinkApplyResponse>(`${basePath(programId, participantId, goalId)}:apply`, input).then(({ data }) => data),
    onSuccess: () => Promise.all([
      queryClient.invalidateQueries({ queryKey: goalKpiLinkageKeys.all() }),
      queryClient.invalidateQueries({ queryKey: programKeys.all() }),
    ]),
  });
}

export function useGoalKpiCyclesQuery(page: number) {
  return useQuery({
    queryKey: [...goalKpiLinkageKeys.all(), 'cycles', page],
    queryFn: () => apiClient.get<PageEnvelope<CycleResponse>>('/v1/cycles', { params: { page, size: 100 } }).then(({ data }) => data),
  });
}

export function useGoalKpiLinkHistoryQuery(programId: string | null, participantId: string | null, goalId: string | null, page = 0) {
  return useQuery({
    queryKey: [...goalKpiLinkageKeys.history(programId ?? '', participantId ?? '', goalId ?? ''), page],
    queryFn: () => apiClient.get<PageEnvelope<KpiLinkApplyResponse>>(`${basePath(programId as string, participantId as string, goalId as string)}s`, {
      params: { page, size: 20 },
    }).then(({ data }) => data),
    enabled: Boolean(programId && participantId && goalId),
  });
}

export function isGoalKpiPreviewStale(error: unknown): boolean {
  if (typeof error !== 'object' || error === null || !('response' in error)) return false;
  const response = (error as { response?: { status?: number } }).response;
  return response?.status === 409;
}

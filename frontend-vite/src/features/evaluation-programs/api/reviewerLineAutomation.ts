import { useMutation, useQueryClient } from '@tanstack/react-query';

import { apiClient } from '../../../api/client';
import { programKeys } from './programs';

export const autoReviewerRoles = [
  'AGREEMENT_REVIEWER',
  'CHECKER',
  'REVIEWER',
  'FINAL_FEEDBACK',
] as const;

export type AutoReviewerRole = typeof autoReviewerRoles[number];
export type ReviewerLinePreviewStatus = 'READY' | 'SOURCE_MISSING' | 'BLOCKED' | 'SKIPPED_EXISTING';
export type ReviewerLineApplyStatus = 'APPLIED' | 'SKIPPED_EXISTING' | 'SOURCE_MISSING' | 'BLOCKED';

export interface ReviewerLineIssue {
  code: string;
  message: string;
}

export interface ReviewerLineProposal {
  role: AutoReviewerRole;
  round: number;
  weightPercent: number;
}

export interface ReviewerLinePreviewRow {
  participantId: string;
  participantEmployeeId: string;
  participantAssignmentId: string | null;
  participantRowVersion: number;
  sourceAssignmentId: string | null;
  sourceVersion: number | null;
  sourceSystem: string | null;
  sourceEffectiveFrom: string | null;
  sourceEffectiveTo: string | null;
  sourceDeleted: boolean | null;
  currentReviewerCount: number;
  proposedReviewerEmployeeId: string | null;
  proposedReviewerName: string | null;
  proposals: ReviewerLineProposal[];
  status: ReviewerLinePreviewStatus;
  issues: ReviewerLineIssue[];
}

export interface ReviewerLinePreviewResponse {
  programId: string;
  asOfDate: string;
  definitionRevision: number;
  previewHash: string;
  generatedAt: string;
  summary: {
    total: number;
    ready: number;
    sourceMissing: number;
    blocked: number;
    skippedExisting: number;
  };
  rows: ReviewerLinePreviewRow[];
}

export interface ReviewerLineApplyRow {
  participantId: string;
  status: ReviewerLineApplyStatus;
  reviewerAssignmentIds: string[];
  issues: ReviewerLineIssue[];
}

export interface ReviewerLineApplyResponse {
  programId: string;
  asOfDate: string;
  automationRunId: string;
  applied: number;
  skipped: number;
  rows: ReviewerLineApplyRow[];
}

export interface ReviewerLinePreviewInput {
  participantIds: string[];
  roles: AutoReviewerRole[];
}

export interface ReviewerLineApplyInput extends ReviewerLinePreviewInput {
  previewHash: string;
  reason: string;
}

const path = (programId: string, action: 'preview' | 'apply'): string =>
  `/v1/evaluation-programs/${programId}/reviewer-line:${action}`;

export function useReviewerLinePreviewMutation(programId: string) {
  return useMutation({
    mutationFn: (input: ReviewerLinePreviewInput) =>
      apiClient.post<ReviewerLinePreviewResponse>(path(programId, 'preview'), input).then(({ data }) => data),
  });
}

export function useReviewerLineApplyMutation(programId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ReviewerLineApplyInput) =>
      apiClient.post<ReviewerLineApplyResponse>(path(programId, 'apply'), input).then(({ data }) => data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: programKeys.all() }),
  });
}

export function isReviewerLinePreviewStale(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false;
  const response = 'response' in error ? error.response : undefined;
  return Boolean(response && typeof response === 'object' && 'status' in response && response.status === 409);
}

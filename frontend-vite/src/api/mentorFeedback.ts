/**
 * MentorFeedback API — React Query 기반 (FE-CC-5 QK + 헬퍼 패턴).
 *
 * BE 정합:
 * - prefix: `/api/internal/mentor-feedbacks`
 * - list: `Page<MentorFeedbackResponse>` envelope
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buildQueryKey } from '@easy/query-client';

import { apiClient } from './client';
import type { PageEnvelope } from './selfEvaluation';

export type FeedbackCategory = 'GROWTH' | 'RECOGNITION' | 'COACHING' | 'CONVERSATION';

export interface MentorFeedbackResponse {
  id: string;
  tenantId: string;
  mentorId: string;
  menteeId: string;
  feedbackDate: string;
  category: FeedbackCategory;
  content: string;
  acknowledged?: boolean | null;
  createdAt: string;
  updatedAt: string;
}

export interface MentorFeedbackCreateRequest {
  mentorId: string;
  menteeId: string;
  feedbackDate: string;
  category?: FeedbackCategory;
  content: string;
}

export interface MentorFeedbackUpdateRequest {
  content?: string;
  category?: FeedbackCategory;
  acknowledged?: boolean;
}

const BASE = '/api/internal/mentor-feedbacks';

export const mentorFeedbackQueryKeys = {
  all: () => buildQueryKey('performance', 'mentorFeedback'),
  list: (params?: { mentorId?: string; menteeId?: string }) =>
    buildQueryKey('performance', 'mentorFeedback', 'list', {
      mentorId: params?.mentorId ?? null,
      menteeId: params?.menteeId ?? null,
    }),
  detail: (id: string) => buildQueryKey('performance', 'mentorFeedback', 'detail', id),
} as const;

export const mentorFeedbackApi = {
  list: (params?: { mentorId?: string; menteeId?: string }) => {
    const query: Record<string, string> = {};
    if (params?.mentorId) query['mentorId'] = params.mentorId;
    if (params?.menteeId) query['menteeId'] = params.menteeId;
    return apiClient
      .get<PageEnvelope<MentorFeedbackResponse>>(BASE, { params: query })
      .then((r) => r.data);
  },

  get: (id: string) =>
    apiClient.get<MentorFeedbackResponse>(`${BASE}/${id}`).then((r) => r.data),

  create: (req: MentorFeedbackCreateRequest) =>
    apiClient.post<MentorFeedbackResponse>(BASE, req).then((r) => r.data),

  update: (id: string, req: MentorFeedbackUpdateRequest) =>
    apiClient.put<MentorFeedbackResponse>(`${BASE}/${id}`, req).then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete<void>(`${BASE}/${id}`).then((r) => r.data),
};

export function useMentorFeedbackList(params?: { mentorId?: string; menteeId?: string }) {
  return useQuery({
    queryKey: mentorFeedbackQueryKeys.list(params),
    queryFn: () => mentorFeedbackApi.list(params),
  });
}

export function useMentorFeedbackCreate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: mentorFeedbackApi.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: mentorFeedbackQueryKeys.all() }),
  });
}

export function useMentorFeedbackUpdate(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: MentorFeedbackUpdateRequest) => mentorFeedbackApi.update(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: mentorFeedbackQueryKeys.all() }),
  });
}

export function useMentorFeedbackDelete() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: mentorFeedbackApi.delete,
    onSuccess: () => qc.invalidateQueries({ queryKey: mentorFeedbackQueryKeys.all() }),
  });
}

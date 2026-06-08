/**
 * SelfEvaluation API — React Query 기반 (FE-CC-5 QK + 헬퍼 패턴).
 *
 * BE 정합:
 * - prefix: `/api/internal/self-evaluations` (단계 1 ~ 단계 3 진입까지 임시)
 * - list: `Page<SelfEvaluationResponse>` (Spring Data envelope: `{ content, totalElements, ... }`)
 *
 * jobeval `cc1bc03` 패턴 정합.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buildQueryKey } from '@easy/query-client';

import { apiClient } from './client';

export type SelfEvaluationStatus = 'DRAFT' | 'SUBMITTED' | 'REVIEWED' | 'FINALIZED';

export interface SelfEvaluationResponse {
  id: string;
  tenantId: string;
  employeeId: string;
  cycleId?: string | null;
  periodStart: string; // ISO date (yyyy-MM-dd)
  periodEnd: string;
  content?: string | null;
  score?: number | null;
  status: SelfEvaluationStatus;
  createdAt: string; // ISO instant
  updatedAt: string;
}

export interface SelfEvaluationCreateRequest {
  employeeId: string;
  cycleId?: string | null;
  periodStart: string;
  periodEnd: string;
  content?: string;
  score?: number;
}

export interface SelfEvaluationUpdateRequest {
  content?: string;
  score?: number;
  status?: SelfEvaluationStatus;
}

export interface PageEnvelope<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const BASE = '/api/internal/self-evaluations';

export const selfEvaluationQueryKeys = {
  all: () => buildQueryKey('performance', 'selfEvaluation'),
  list: (employeeId?: string) =>
    buildQueryKey('performance', 'selfEvaluation', 'list', { employeeId: employeeId ?? null }),
  detail: (id: string) => buildQueryKey('performance', 'selfEvaluation', 'detail', id),
} as const;

export const selfEvaluationApi = {
  list: (employeeId?: string) =>
    apiClient
      .get<PageEnvelope<SelfEvaluationResponse>>(BASE, {
        params: employeeId ? { employeeId } : {},
      })
      .then((r) => r.data),

  get: (id: string) =>
    apiClient.get<SelfEvaluationResponse>(`${BASE}/${id}`).then((r) => r.data),

  create: (req: SelfEvaluationCreateRequest) =>
    apiClient.post<SelfEvaluationResponse>(BASE, req).then((r) => r.data),

  update: (id: string, req: SelfEvaluationUpdateRequest) =>
    apiClient.put<SelfEvaluationResponse>(`${BASE}/${id}`, req).then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete<void>(`${BASE}/${id}`).then((r) => r.data),
};

export function useSelfEvaluationList(employeeId?: string) {
  return useQuery({
    queryKey: selfEvaluationQueryKeys.list(employeeId),
    queryFn: () => selfEvaluationApi.list(employeeId),
  });
}

export function useSelfEvaluationCreate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: selfEvaluationApi.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: selfEvaluationQueryKeys.all() }),
  });
}

export function useSelfEvaluationUpdate(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: SelfEvaluationUpdateRequest) => selfEvaluationApi.update(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: selfEvaluationQueryKeys.all() }),
  });
}

export function useSelfEvaluationDelete() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: selfEvaluationApi.delete,
    onSuccess: () => qc.invalidateQueries({ queryKey: selfEvaluationQueryKeys.all() }),
  });
}

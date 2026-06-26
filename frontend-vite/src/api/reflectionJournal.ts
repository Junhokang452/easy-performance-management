/**
 * ReflectionJournal API — React Query 기반 (FE-CC-5 QK + 헬퍼 패턴).
 *
 * BE 정합:
 * - prefix: `/api/v1/reflection-journals`
 * - list: `Page<ReflectionJournalResponse>` envelope
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buildQueryKey } from '@easy/query-client';

import { apiClient } from './client';
import type { PageEnvelope } from './selfEvaluation';

export type ReflectionMethod = 'KPT' | 'FOUR_LS' | 'SSC';

export interface ReflectionJournalResponse {
  id: string;
  tenantId: string;
  employeeId: string;
  reflectionDate: string;
  method: ReflectionMethod;
  content: string;
  isPrivate?: boolean | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReflectionJournalCreateRequest {
  employeeId: string;
  reflectionDate: string;
  method?: ReflectionMethod;
  content: string;
  isPrivate?: boolean;
}

export interface ReflectionJournalUpdateRequest {
  content?: string;
  method?: ReflectionMethod;
  isPrivate?: boolean;
}

const BASE = '/v1/reflection-journals';

export const reflectionJournalQueryKeys = {
  all: () => buildQueryKey('performance', 'reflectionJournal'),
  list: (employeeId?: string) =>
    buildQueryKey('performance', 'reflectionJournal', 'list', { employeeId: employeeId ?? null }),
  detail: (id: string) => buildQueryKey('performance', 'reflectionJournal', 'detail', id),
} as const;

export const reflectionJournalApi = {
  list: (employeeId?: string) =>
    apiClient
      .get<PageEnvelope<ReflectionJournalResponse>>(BASE, {
        params: employeeId ? { employeeId } : {},
      })
      .then((r) => r.data),

  get: (id: string) =>
    apiClient.get<ReflectionJournalResponse>(`${BASE}/${id}`).then((r) => r.data),

  create: (req: ReflectionJournalCreateRequest) =>
    apiClient.post<ReflectionJournalResponse>(BASE, req).then((r) => r.data),

  update: (id: string, req: ReflectionJournalUpdateRequest) =>
    apiClient.put<ReflectionJournalResponse>(`${BASE}/${id}`, req).then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete<void>(`${BASE}/${id}`).then((r) => r.data),
};

export function useReflectionJournalList(employeeId?: string) {
  return useQuery({
    queryKey: reflectionJournalQueryKeys.list(employeeId),
    queryFn: () => reflectionJournalApi.list(employeeId),
  });
}

export function useReflectionJournalCreate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: reflectionJournalApi.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: reflectionJournalQueryKeys.all() }),
  });
}

export function useReflectionJournalUpdate(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ReflectionJournalUpdateRequest) => reflectionJournalApi.update(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: reflectionJournalQueryKeys.all() }),
  });
}

export function useReflectionJournalDelete() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: reflectionJournalApi.delete,
    onSuccess: () => qc.invalidateQueries({ queryKey: reflectionJournalQueryKeys.all() }),
  });
}

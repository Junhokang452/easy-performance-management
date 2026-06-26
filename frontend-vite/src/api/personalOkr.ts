/**
 * PersonalOkr API — React Query 기반 (FE-CC-5 QK + 헬퍼 패턴).
 *
 * BE 정합:
 * - prefix: `/api/v1/personal-okrs`
 * - list: `Page<PersonalOkrResponse>` envelope
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buildQueryKey } from '@easy/query-client';

import { apiClient } from './client';
import type { PageEnvelope } from './selfEvaluation';

export type PersonalOkrStatus = 'ACTIVE' | 'AT_RISK' | 'COMPLETED' | 'ARCHIVED';

export interface PersonalOkrResponse {
  id: string;
  tenantId: string;
  employeeId: string;
  objective: string;
  progress?: number | null;
  periodStart: string;
  periodEnd: string;
  status: PersonalOkrStatus;
  createdAt: string;
  updatedAt: string;
}

export interface PersonalOkrCreateRequest {
  employeeId: string;
  objective: string;
  periodStart: string;
  periodEnd: string;
  progress?: number;
}

export interface PersonalOkrUpdateRequest {
  objective?: string;
  progress?: number;
  status?: PersonalOkrStatus;
}

const BASE = '/v1/personal-okrs';

export const personalOkrQueryKeys = {
  all: () => buildQueryKey('performance', 'personalOkr'),
  list: (employeeId?: string) =>
    buildQueryKey('performance', 'personalOkr', 'list', { employeeId: employeeId ?? null }),
  detail: (id: string) => buildQueryKey('performance', 'personalOkr', 'detail', id),
} as const;

export const personalOkrApi = {
  list: (employeeId?: string) =>
    apiClient
      .get<PageEnvelope<PersonalOkrResponse>>(BASE, {
        params: employeeId ? { employeeId } : {},
      })
      .then((r) => r.data),

  get: (id: string) =>
    apiClient.get<PersonalOkrResponse>(`${BASE}/${id}`).then((r) => r.data),

  create: (req: PersonalOkrCreateRequest) =>
    apiClient.post<PersonalOkrResponse>(BASE, req).then((r) => r.data),

  update: (id: string, req: PersonalOkrUpdateRequest) =>
    apiClient.put<PersonalOkrResponse>(`${BASE}/${id}`, req).then((r) => r.data),

  delete: (id: string) =>
    apiClient.delete<void>(`${BASE}/${id}`).then((r) => r.data),
};

export function usePersonalOkrList(employeeId?: string) {
  return useQuery({
    queryKey: personalOkrQueryKeys.list(employeeId),
    queryFn: () => personalOkrApi.list(employeeId),
  });
}

export function usePersonalOkrCreate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: personalOkrApi.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: personalOkrQueryKeys.all() }),
  });
}

export function usePersonalOkrUpdate(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: PersonalOkrUpdateRequest) => personalOkrApi.update(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: personalOkrQueryKeys.all() }),
  });
}

export function usePersonalOkrDelete() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: personalOkrApi.delete,
    onSuccess: () => qc.invalidateQueries({ queryKey: personalOkrQueryKeys.all() }),
  });
}

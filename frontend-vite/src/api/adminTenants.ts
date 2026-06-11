/**
 * SystemAdmin Tenants API — control plane 테넌트 lifecycle (목록/생성/재시도/일시중지/재개/폐기).
 *
 * BE: `/api/admin/tenants` (`SystemAdminTenantsController`, hasAuthority('SUPER_ADMIN')).
 * 응답 정합: Jackson `@JsonNaming(LowerCamelCaseStrategy)` → camelCase.
 *
 * 게이트 OFF (단일 DB 개발, `easyware.neon.multitenancy-enabled=false`) → BE 503 반환 →
 * 훅 isError 로 AdminTenantsPage 가 게이트 OFF 안내 카드 표시.
 * create/retry 202 + PROVISIONING 반환 — 페이지 5초 폴링으로 ACTIVE/FAILED 추적.
 *
 * store-hr `f8df3db` `api/admin/tenants.ts` 사본 (performance flat api/ 컨벤션 + apiClient 정합).
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiClient } from './client';

export type TenantStatus = 'PROVISIONING' | 'ACTIVE' | 'FAILED' | 'SUSPENDED';

export interface PlatformTenant {
  id: string;
  code: string;
  name: string;
  status?: TenantStatus | string | null;
  region?: string | null;
  adminUsername?: string | null;
  adminEmail?: string | null;
  neonProjectId?: string | null;
  lastError?: string | null;
  countryCode?: string | null;
  defaultLocale?: string | null;
  timezone?: string | null;
  currencyCode?: string | null;
  industryCode?: string | null;
  companySize?: string | null;
  planCode?: string | null;
  enabledModules?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateTenantInput {
  code: string;
  name: string;
  region: string;
  adminUsername: string;
  adminEmail: string;
}

const BASE = '/admin/tenants';

export const tenantsKeys = {
  all: ['admin', 'tenants'] as const,
  list: () => [...tenantsKeys.all, 'list'] as const,
  detail: (id: string) => [...tenantsKeys.all, 'detail', id] as const,
};

export const tenantsApi = {
  list: () => apiClient.get<PlatformTenant[]>(BASE).then((r) => r.data),

  get: (id: string) => apiClient.get<PlatformTenant>(`${BASE}/${id}`).then((r) => r.data),

  create: (input: CreateTenantInput) =>
    apiClient.post<PlatformTenant>(BASE, input).then((r) => r.data),

  retry: (id: string) => apiClient.post<PlatformTenant>(`${BASE}/${id}/retry`).then((r) => r.data),

  suspend: (id: string) =>
    apiClient.post<PlatformTenant>(`${BASE}/${id}/suspend`).then((r) => r.data),

  resume: (id: string) =>
    apiClient.post<PlatformTenant>(`${BASE}/${id}/resume`).then((r) => r.data),

  remove: (id: string) => apiClient.delete<void>(`${BASE}/${id}`).then((r) => r.data),
};

export function useTenants() {
  return useQuery({
    queryKey: tenantsKeys.list(),
    queryFn: tenantsApi.list,
    retry: false, // 게이트 OFF 시 503 즉시 표시
    // PROVISIONING 행이 있으면 5초 폴링 (ware 콘솔 패턴)
    refetchInterval: (query) =>
      query.state.data?.some((t) => t.status === 'PROVISIONING') ? 5000 : false,
  });
}

export function useCreateTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: tenantsApi.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: tenantsKeys.list() }),
  });
}

/** retry / suspend / resume 공용 액션 훅. */
export function useTenantAction(action: 'retry' | 'suspend' | 'resume') {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => tenantsApi[action](id),
    onSuccess: () => qc.invalidateQueries({ queryKey: tenantsKeys.list() }),
  });
}

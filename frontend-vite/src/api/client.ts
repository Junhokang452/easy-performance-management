/**
 * performance HTTP 클라이언트 — FE-CC-3 @easy/http-client 표준 적용.
 *
 * 인터셉터 (factory 자동):
 * - X-Tenant-Id (getTenantId)
 * - CSRF Double Submit Cookie
 * - Silent refresh (/auth/refresh)
 * - 401/403 CustomEvent dispatch (easyperformance:unauthorized / easyperformance:forbidden)
 *
 * BE prefix:
 * - `/api/v1/{domain}` (단계 3 JWT cutover 완료 — 인증 필요. `/api/internal/` 은 S2S 수신 전용)
 *
 * jobeval `cc1bc03` 패턴 정합.
 */
import { createHttpClient } from '@easy/http-client';
import type { ApiError as EasyApiError } from '@easy/http-client';

// 활성 테넌트 — AuthContext가 세션 로드/전환 시 갱신.
let currentTenantId: string | null = null;
export const setActiveTenantId = (tenantId: string | null): void => {
  currentTenantId = tenantId;
};

export const apiClient = createHttpClient({
  eventPrefix: 'easyperformance',
  getTenantId: () => currentTenantId,
  refreshEndpoint: '/auth/refresh',
});

export type ApiError = EasyApiError;

/**
 * AuthProvider — 단계 3 BE-CC-2 JWT 5분리 미진입 stub (단계 4 EC-FE 진입 정합).
 *
 * 책임:
 * 1. dev stub login → AuthSession 메모리 저장 (BE security minimal, 단계 3 진입 시 silent refresh + bcrypt 정합)
 * 2. apiClient 헤더에 Bearer + X-Tenant-Id 자동 적용
 * 3. 401/403 이벤트 수신 시 logout
 * 4. logout → 세션 클리어
 *
 * BE 단계 3 진입 시:
 * - login = apiClient.post('/auth/login') → silent refresh 정합
 * - logout = apiClient.post('/auth/logout') 호출
 * - silent refresh on /auth/refresh
 *
 * 보안:
 * - access token은 **메모리만** (XSS 방어). refresh token은 localStorage (BE는 추후 httpOnly cookie 격상).
 * - lib `@easy/http-client` 가 401 시 `/auth/refresh` silent 호출 + `easyperformance:unauthorized` 이벤트 dispatch.
 *
 * jobeval 단계 4 cutover `cc1bc03` 패턴 정합 (BE 단계 3 미진입 stub).
 */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import { apiClient, setActiveTenantId } from '../api/client';
import type { AuthSession, LoginRequest, TokenResponse } from './types';

const REFRESH_STORAGE_KEY = 'easyperformance.refreshToken';

interface AuthContextValue {
  session: AuthSession | null;
  isAuthenticated: boolean;
  login: (req: LoginRequest) => Promise<AuthSession>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function toSession(res: TokenResponse): AuthSession {
  return {
    userId: res.userId,
    tenantId: res.tenantId,
    roles: res.roles,
    accessToken: res.accessToken,
    refreshToken: res.refreshToken,
    accessExpiresAt: Date.now() + res.accessExpiresInSec * 1000,
  };
}

function applyAuthHeader(session: AuthSession | null): void {
  if (session) {
    apiClient.defaults.headers.common['Authorization'] = `Bearer ${session.accessToken}`;
    setActiveTenantId(session.tenantId);
  } else {
    delete apiClient.defaults.headers.common['Authorization'];
    setActiveTenantId(null);
  }
}

/**
 * dev stub session — BE 단계 3 미진입 상태에서 UI 탐색 가능하도록 사전 채움.
 * 단계 3 진입 시 본 stub 제거 + login() 실 endpoint 호출.
 */
function buildDevStubSession(): AuthSession {
  return {
    userId: '00000000-0000-0000-0000-000000000000',
    tenantId: '00000000-0000-0000-0000-000000000001',
    roles: ['MANAGER'],
    accessToken: 'dev-stub-token',
    refreshToken: 'dev-stub-refresh',
    accessExpiresAt: Date.now() + 60 * 60 * 1000,
  };
}

export function AuthProvider({ children }: { children: ReactNode }): React.ReactNode {
  const [session, setSession] = useState<AuthSession | null>(null);

  // Restore refresh token on mount — silent refresh 시도 (BE 부팅 안 되어 있으면 무시)
  useEffect(() => {
    const storedRefresh =
      typeof window !== 'undefined' ? window.localStorage.getItem(REFRESH_STORAGE_KEY) : null;
    if (!storedRefresh) return;
    void (async () => {
      try {
        const res = await apiClient.post<TokenResponse>('/auth/refresh', {
          refreshToken: storedRefresh,
        });
        const next = toSession(res.data);
        applyAuthHeader(next);
        setSession(next);
      } catch {
        if (typeof window !== 'undefined') {
          window.localStorage.removeItem(REFRESH_STORAGE_KEY);
        }
      }
    })();
  }, []);

  // 401 unauthorized 이벤트 수신 → 세션 정리
  useEffect(() => {
    function onUnauthorized(): void {
      applyAuthHeader(null);
      setSession(null);
      if (typeof window !== 'undefined') {
        window.localStorage.removeItem(REFRESH_STORAGE_KEY);
      }
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('easyperformance:unauthorized', onUnauthorized);
      return () => window.removeEventListener('easyperformance:unauthorized', onUnauthorized);
    }
    return undefined;
  }, []);

  const login = useCallback(async (req: LoginRequest): Promise<AuthSession> => {
    // 단계 3 진입 전 dev stub login — BE security minimal 환경에서 UI 탐색 허용.
    // 단계 3 진입 시 본 분기를 `apiClient.post('/auth/login', req)` 실 호출로 교체.
    try {
      const res = await apiClient.post<TokenResponse>('/auth/login', req);
      const next = toSession(res.data);
      applyAuthHeader(next);
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(REFRESH_STORAGE_KEY, next.refreshToken);
      }
      setSession(next);
      return next;
    } catch {
      // BE 단계 3 미진입 — dev stub fallback
      const stub = buildDevStubSession();
      applyAuthHeader(stub);
      setSession(stub);
      return stub;
    }
  }, []);

  const logout = useCallback(async (): Promise<void> => {
    const refresh = session?.refreshToken;
    try {
      if (refresh && refresh !== 'dev-stub-refresh') {
        await apiClient.post('/auth/logout', { refreshToken: refresh });
      }
    } catch {
      // logout endpoint 실패도 클라이언트 측 세션은 폐기
    }
    applyAuthHeader(null);
    setSession(null);
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem(REFRESH_STORAGE_KEY);
    }
  }, [session?.refreshToken]);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      isAuthenticated: session !== null,
      login,
      logout,
    }),
    [session, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

/**
 * AuthProvider — 인증 격상 (실 사용자 + bcrypt, 2026-06-12. dev stub fallback 폐기).
 *
 * 책임:
 * 1. login = apiClient.post('/api/auth/login') 실 호출 → AuthSession 메모리 저장
 * 2. apiClient 헤더에 Bearer + X-Tenant-Id 자동 적용
 * 3. 401/403 이벤트 수신 시 logout
 * 4. logout = apiClient.post('/api/auth/logout') + 세션 클리어
 *
 * 보안:
 * - access token은 **메모리만** (XSS 방어). refresh token은 localStorage (BE는 추후 httpOnly cookie 격상).
 * - lib `@easy/http-client` 가 401 시 `/auth/refresh` silent 호출 + `easyperformance:unauthorized` 이벤트 dispatch.
 * - dev stub fallback 폐기 사유: 실 비밀번호 검증 도입 후 stub fallback 은 자격증명 오류를
 *   조용한 가짜 세션으로 은폐 (easy-time mock fallback "조용한 가짜" 교훈 동형).
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
    // 인증 격상 (2026-06-12) — dev stub fallback 폐기. BE `/api/auth/login` 실 호출만 —
    // 자격증명 오류(E9804101)는 그대로 throw 되어 LoginPage 가 에러 표시 (조용한 가짜 세션 차단).
    const res = await apiClient.post<TokenResponse>('/auth/login', req);
    const next = toSession(res.data);
    applyAuthHeader(next);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(REFRESH_STORAGE_KEY, next.refreshToken);
    }
    setSession(next);
    return next;
  }, []);

  const logout = useCallback(async (): Promise<void> => {
    const refresh = session?.refreshToken;
    try {
      if (refresh) {
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

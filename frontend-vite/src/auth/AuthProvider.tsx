import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { apiClient, setActiveTenantId } from '../api/client';
import { showToast } from '../shared/toast';
import { getErrorMessage } from '../api/error';
import type { AuthSession, LoginRequest } from './types';

interface AuthContextValue {
  session: AuthSession | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (req: LoginRequest) => Promise<AuthSession>;
  logout: () => Promise<void>;
}
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }): React.ReactNode {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [isLoading, setLoading] = useState(true);
  const queryClient = useQueryClient();
  const apply = useCallback((next: AuthSession | null) => {
    setActiveTenantId(next?.tenantId ?? null);
    setSession(next);
  }, []);

  useEffect(() => {
    let active = true;
    // Remove credentials written by the previous browser client, never read or reuse them.
    window.localStorage.removeItem('easyperformance.refreshToken');
    void apiClient.get<AuthSession>('/auth/session').then(({ data }) => {
      if (active) apply(data);
    }).catch(() => { if (active) apply(null); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [apply]);

  useEffect(() => {
    const clear = () => { queryClient.clear(); apply(null); };
    window.addEventListener('easyperformance:unauthorized', clear);
    return () => window.removeEventListener('easyperformance:unauthorized', clear);
  }, [apply, queryClient]);

  const login = useCallback(async (req: LoginRequest) => {
    const { data } = await apiClient.post<AuthSession>('/auth/session/login', req);
    queryClient.clear();
    apply(data);
    return data;
  }, [apply, queryClient]);

  const logout = useCallback(async () => {
    try {
      await apiClient.post('/auth/session/logout');
    } catch (error) {
      showToast({ tone: 'danger', message: getErrorMessage(error) });
    } finally {
      queryClient.clear();
      apply(null);
    }
  }, [apply, queryClient]);

  const value = useMemo(() => ({ session, isLoading, isAuthenticated: session !== null, login, logout }),
    [session, isLoading, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}

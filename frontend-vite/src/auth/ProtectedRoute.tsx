/**
 * ProtectedRoute — 인증 필요 라우트 가드 (단계 4 강화).
 *
 * 미인증 시 `/login` 으로 redirect. 인증 상태는 `useAuth()` 의 `isAuthenticated`.
 * 단계 3 BE-CC-2 JWT 진입 시 lib `@easy/auth-hooks` 통합 hook 으로 교체 가능.
 * jobeval 단계 4 cutover `cc1bc03` 패턴 정합.
 */
import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';

import { useAuth } from './AuthProvider';

export function ProtectedRoute({ children }: { children: ReactNode }): React.ReactNode {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <>{children}</>;
}

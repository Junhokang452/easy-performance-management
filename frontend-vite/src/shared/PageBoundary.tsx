/**
 * PageBoundary — 라우트 페이지 wrapping (단계 4 강화).
 *
 * 1) `<RouteErrorBoundary>` 로 throw 차단
 * 2) `<Suspense>` 로 lazy 페이지 로딩 fallback
 * 3) `key={pathname}` 로 라우트 전환 시 상태 리셋
 *
 * STD-FE-LAZY + STD-FE-ERROR-BOUNDARY 동시 만족.
 * jobeval 단계 4 cutover `cc1bc03` 패턴 정합.
 */
import { Suspense, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { Center } from '@easy/ui-components/mantine';

import { RouteErrorBoundary } from './RouteErrorBoundary';
import { UiLoader } from '@easy/ui-components';

interface Props {
  children: ReactNode;
}

export function PageBoundary({ children }: Props): React.ReactNode {
  const location = useLocation();
  return (
    <RouteErrorBoundary resetKey={location.pathname}>
      <Suspense
        fallback={
          <Center mih={200}>
            <UiLoader />
          </Center>
        }
      >
        {children}
      </Suspense>
    </RouteErrorBoundary>
  );
}

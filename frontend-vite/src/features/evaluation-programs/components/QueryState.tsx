import type { ReactNode } from 'react';
import { EmptyState, ErrorState, LoadingState } from '@easy/ui-components';

export function QueryState({ pending, error, empty, loadingLabel, errorLabel, emptyTitle, emptyDescription, children }: {
  pending: boolean;
  error: unknown;
  empty: boolean;
  loadingLabel: string;
  errorLabel: string;
  emptyTitle: string;
  emptyDescription?: string;
  children: ReactNode;
}): ReactNode {
  if (pending) return <LoadingState message={loadingLabel} />;
  if (error) return <ErrorState title={errorLabel} />;
  if (empty) return <EmptyState title={emptyTitle} description={emptyDescription} />;
  return children;
}

/**
 * ReflectionJournalPage — 회고 저널 페이지 (단계 4 EC-FE 진입).
 *
 * BE 정합: GET/POST/PUT/DELETE `/api/internal/reflection-journals` (Page envelope).
 * 방법론: KPT / 4Ls / SSC.
 */
import { Badge, Stack, Text } from '@mantine/core';
import {
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';
import {
  PerformancePreWrapText,
  PerformanceRecordCard,
} from '@easy/ui-components/performance';

import { useReflectionJournalList, type ReflectionMethod } from '../api/reflectionJournal';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';

function methodColor(method: ReflectionMethod): string {
  switch (method) {
    case 'KPT':
      return 'blue';
    case 'FOUR_LS':
      return 'teal';
    case 'SSC':
      return 'orange';
    default:
      return 'gray';
  }
}

export function ReflectionJournalPage(): React.ReactNode {
  const t = useT();
  const { data, isLoading, isError, error } = useReflectionJournalList();
  const rows = data?.content ?? [];

  const methodLabel = (m: ReflectionMethod): string => {
    switch (m) {
      case 'KPT':
        return t.domain.reflectionJournal.methodKpt;
      case 'FOUR_LS':
        return t.domain.reflectionJournal.methodFourLs;
      case 'SSC':
        return t.domain.reflectionJournal.methodSsc;
      default:
        return m;
    }
  };

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.domain.reflectionJournal.title}
        description={t.domain.reflectionJournal.description}
      />
      <SectionCard>
        {isError ? (
          <Text c="red">
            {t.common.message.loadError}: {getErrorMessage(error)}
          </Text>
        ) : isLoading ? (
          <LoadingState message={t.common.status.loading} />
        ) : rows.length === 0 ? (
          <EmptyState
            title={t.domain.reflectionJournal.empty}
            description={t.domain.reflectionJournal.emptyDescription}
          />
        ) : (
          <Stack>
            {rows.map((journal) => (
              <PerformanceRecordCard
                key={journal.id}
                mobileSize="comfortable"
                meta={
                  <>
                    <Text size="sm" fw={600}>
                      {journal.reflectionDate}
                    </Text>
                    <Badge color={methodColor(journal.method)} variant="light">
                      {methodLabel(journal.method)}
                    </Badge>
                  </>
                }
                badges={
                  journal.isPrivate ? (
                    <Badge color="gray" variant="outline">
                      {t.domain.reflectionJournal.isPrivate}
                    </Badge>
                  ) : null
                }
              >
                <PerformancePreWrapText>{journal.content}</PerformancePreWrapText>
              </PerformanceRecordCard>
            ))}
          </Stack>
        )}
      </SectionCard>
    </ErrorBoundary>
  );
}

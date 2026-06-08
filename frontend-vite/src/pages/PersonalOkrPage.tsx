/**
 * PersonalOkrPage — 개인 OKR 페이지 (단계 4 EC-FE 진입, B2B 분기 평가 본질).
 *
 * BE 정합: GET/POST/PUT/DELETE `/api/internal/personal-okrs` (Page envelope).
 * Status 상태 머신: ACTIVE / AT_RISK / COMPLETED / ARCHIVED.
 */
import { Badge, Card, Group, Progress, Stack, Text } from '@mantine/core';
import {
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';

import { usePersonalOkrList, type PersonalOkrStatus } from '../api/personalOkr';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';

function statusColor(status: PersonalOkrStatus): string {
  switch (status) {
    case 'COMPLETED':
      return 'green';
    case 'AT_RISK':
      return 'red';
    case 'ARCHIVED':
      return 'gray';
    case 'ACTIVE':
    default:
      return 'blue';
  }
}

export function PersonalOkrPage(): React.ReactNode {
  const t = useT();
  const { data, isLoading, isError, error } = usePersonalOkrList();
  const rows = data?.content ?? [];

  const statusLabel = (s: PersonalOkrStatus): string => {
    switch (s) {
      case 'ACTIVE':
        return t.domain.personalOkr.statusActive;
      case 'AT_RISK':
        return t.domain.personalOkr.statusAtRisk;
      case 'COMPLETED':
        return t.domain.personalOkr.statusCompleted;
      case 'ARCHIVED':
        return t.domain.personalOkr.statusArchived;
      default:
        return s;
    }
  };

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.domain.personalOkr.title}
        description={t.domain.personalOkr.description}
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
            title={t.domain.personalOkr.empty}
            description={t.domain.personalOkr.emptyDescription}
          />
        ) : (
          <Stack>
            {rows.map((okr) => (
              <Card key={okr.id} shadow="sm" padding="lg" radius="md" withBorder>
                <Group justify="space-between" mb="md">
                  <Text size="sm" c="dimmed">
                    {okr.periodStart} ~ {okr.periodEnd}
                  </Text>
                  <Badge color={statusColor(okr.status)} variant="light">
                    {statusLabel(okr.status)}
                  </Badge>
                </Group>
                <Text fw={600} size="lg">
                  {okr.objective}
                </Text>
                <Progress value={typeof okr.progress === 'number' ? okr.progress : 0} mt="sm" />
                <Text size="xs" c="dimmed" mt={4}>
                  {t.domain.personalOkr.progress}{' '}
                  {typeof okr.progress === 'number' ? okr.progress.toFixed(0) : 0}%
                </Text>
              </Card>
            ))}
          </Stack>
        )}
      </SectionCard>
    </ErrorBoundary>
  );
}

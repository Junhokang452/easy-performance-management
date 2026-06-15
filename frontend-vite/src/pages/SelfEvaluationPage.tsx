/**
 * SelfEvaluationPage — 자기평가 페이지 (단계 4 EC-FE 진입, B2B 분기/연간 평가 본질).
 *
 * BE 정합: GET/POST/PUT/DELETE `/api/internal/self-evaluations` (Page envelope).
 * Status 상태 머신: DRAFT → SUBMITTED → REVIEWED → FINALIZED.
 */
import { Badge, Group, Stack, Table, Text } from '@easy/ui-components/mantine';
import {
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';

import { useSelfEvaluationList, type SelfEvaluationStatus } from '../api/selfEvaluation';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';

function statusColor(status: SelfEvaluationStatus): string {
  switch (status) {
    case 'FINALIZED':
      return 'green';
    case 'REVIEWED':
      return 'teal';
    case 'SUBMITTED':
      return 'blue';
    case 'DRAFT':
    default:
      return 'gray';
  }
}

export function SelfEvaluationPage(): React.ReactNode {
  const t = useT();
  const { data, isLoading, isError, error } = useSelfEvaluationList();
  const rows = data?.content ?? [];

  const statusLabel = (s: SelfEvaluationStatus): string => {
    switch (s) {
      case 'DRAFT':
        return t.domain.selfEvaluation.statusDraft;
      case 'SUBMITTED':
        return t.domain.selfEvaluation.statusSubmitted;
      case 'REVIEWED':
        return t.domain.selfEvaluation.statusReviewed;
      case 'FINALIZED':
        return t.domain.selfEvaluation.statusFinalized;
      default:
        return s;
    }
  };

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.domain.selfEvaluation.title}
        description={t.domain.selfEvaluation.description}
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
            title={t.domain.selfEvaluation.empty}
            description={t.domain.selfEvaluation.emptyDescription}
          />
        ) : (
          <Stack>
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t.domain.selfEvaluation.period}</Table.Th>
                  <Table.Th>{t.domain.selfEvaluation.score}</Table.Th>
                  <Table.Th>{t.domain.selfEvaluation.status}</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {rows.map((row) => (
                  <Table.Tr key={row.id}>
                    <Table.Td>
                      <Group gap={6}>
                        <Text size="sm">{row.periodStart}</Text>
                        <Text size="sm" c="dimmed">~</Text>
                        <Text size="sm">{row.periodEnd}</Text>
                      </Group>
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">{row.score ?? '—'}</Text>
                    </Table.Td>
                    <Table.Td>
                      <Badge color={statusColor(row.status)} variant="light">
                        {statusLabel(row.status)}
                      </Badge>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Stack>
        )}
      </SectionCard>
    </ErrorBoundary>
  );
}

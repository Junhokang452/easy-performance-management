/**
 * CalibrationAnalyticsPage — 보정 분석 workspace.
 *
 * 기존 calibration session + distribution API를 조합하는 읽기 전용 화면이다.
 * 등급/분포 계산은 BE 응답만 렌더하며 FE는 표시용 집계만 수행한다.
 */
import { useEffect, useMemo, useState } from 'react';
import { Group, Select, SimpleGrid, Stack, Text } from '@easy/ui-components/mantine';
import {
  IconChartBar,
  IconChecklist,
  IconHistory,
  IconUsers,
} from '@tabler/icons-react';
import {
  UiBadge,
  UiTable,
  EmptyState,
  ErrorBoundary,
  LoadingState,
  PageHeader,
  SectionCard,
} from '@easy/ui-components';
import {
  PerformanceMetricGrid,
  PerformancePreWrapText,
} from '@easy/ui-components/performance';

import {
  useCalibrationSessionQuery,
  useCalibrationSessionsQuery,
  useDistributionQuery,
  type AdjustmentEntry,
  type CalibrationSessionResponse,
} from '../api/calibration';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleSelect } from './kpi/CycleSelect';
import { DistributionBars } from './calibration/DistributionBars';
import { GradeBadge } from './calibration/GradeBadge';
import { SessionStatusBadge } from './calibration/SessionStatusBadge';

export function CalibrationAnalyticsPage(): React.ReactNode {
  const t = useT();
  const [cycleId, setCycleId] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);

  const sessionsQuery = useCalibrationSessionsQuery(cycleId);
  const sessions = sessionsQuery.data ?? [];
  const distributionQuery = useDistributionQuery(cycleId);
  const distribution = distributionQuery.data ?? null;
  const sessionDetailQuery = useCalibrationSessionQuery(sessionId);
  const selectedSession = sessionDetailQuery.data ?? null;

  useEffect(() => {
    setSessionId(null);
  }, [cycleId]);

  useEffect(() => {
    if (!sessionId && sessions.length > 0) {
      setSessionId(sessions[0]?.id ?? null);
    }
  }, [sessionId, sessions]);

  const totalAdjustments = sessions.reduce(
    (sum, session) => sum + (session.adjustmentLog?.length ?? 0),
    0,
  );

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.calibration.analytics.title}
        description={t.calibration.analytics.description}
      />
      <SectionCard>
        <Group align="end" gap="md" wrap="wrap">
          <CycleSelect value={cycleId} onChange={setCycleId} />
          {cycleId ? (
            <Select
              label={t.calibration.director.selectSession}
              placeholder={t.calibration.director.sessionPlaceholder}
              data={sessions.map((session) => ({
                value: session.id,
                label: `${t.calibration.status[session.status]} · ${formatDateTime(
                  session.scheduledAt,
                )}`,
              }))}
              value={sessionId}
              onChange={setSessionId}
              disabled={sessionsQuery.isLoading || sessions.length === 0}
              clearable
              miw={280}
            />
          ) : null}
        </Group>
      </SectionCard>

      {!cycleId ? (
        <SectionCard>
          <Text c="dimmed" size="sm">
            {t.calibration.analytics.needCycle}
          </Text>
        </SectionCard>
      ) : sessionsQuery.isError || distributionQuery.isError ? (
        <SectionCard>
          <Text c="red">
            {t.common.message.loadError}:{' '}
            {getErrorMessage(sessionsQuery.error ?? distributionQuery.error)}
          </Text>
        </SectionCard>
      ) : sessionsQuery.isLoading || distributionQuery.isLoading ? (
        <SectionCard>
          <LoadingState message={t.common.status.loading} />
        </SectionCard>
      ) : (
        <Stack>
          <PerformanceMetricGrid
            mobileSize="comfortable"
            items={[
              {
                label: t.calibration.analytics.stat.sessions,
                value: sessions.length,
                description: t.calibration.analytics.stat.sessionsHint,
                icon: <IconUsers size={18} />,
                tone: 'brand',
              },
              {
                label: t.calibration.analytics.stat.ready,
                value: distribution?.calibrationReadyCount ?? 0,
                description: t.calibration.analytics.stat.readyHint.replace(
                  '{total}',
                  String(distribution?.totalReviews ?? 0),
                ),
                icon: <IconChecklist size={18} />,
                tone: 'green',
              },
              {
                label: t.calibration.analytics.stat.adjustments,
                value: totalAdjustments,
                description: t.calibration.analytics.stat.adjustmentsHint,
                icon: <IconHistory size={18} />,
                tone: 'violet',
              },
              {
                label: t.calibration.analytics.stat.forced,
                value: distribution?.forcedApplied
                  ? t.distribution.policy.applied
                  : t.distribution.policy.notApplied,
                description: t.calibration.analytics.stat.forcedHint,
                icon: <IconChartBar size={18} />,
                tone: distribution?.forcedApplied ? 'blue' : 'gray',
              },
            ]}
          />

          <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="md">
            <SectionCard
              title={t.calibration.analytics.distributionTitle}
              description={t.calibration.analytics.distributionDescription}
            >
              {distribution ? (
                <DistributionBars
                  current={distribution.currentDistribution}
                  target={distribution.targetDistribution}
                />
              ) : (
                <Text size="sm" c="dimmed">
                  {t.cockpit.distribution.empty}
                </Text>
              )}
            </SectionCard>

            <SectionCard
              title={t.calibration.analytics.sessionsTitle}
              description={t.calibration.analytics.sessionsDescription}
            >
              <SessionsTable sessions={sessions} />
            </SectionCard>
          </SimpleGrid>

          <SectionCard
            title={t.calibration.analytics.timelineTitle}
            description={t.calibration.analytics.timelineDescription}
          >
            {sessionDetailQuery.isLoading ? (
              <LoadingState message={t.common.status.loading} />
            ) : selectedSession ? (
              <AdjustmentTimeline session={selectedSession} />
            ) : (
              <EmptyState title={t.calibration.director.noSession} />
            )}
          </SectionCard>
        </Stack>
      )}
    </ErrorBoundary>
  );
}

function SessionsTable({
  sessions,
}: {
  sessions: CalibrationSessionResponse[];
}): React.ReactNode {
  const t = useT();

  if (sessions.length === 0) {
    return <EmptyState title={t.calibration.page.empty} />;
  }

  return (
    <UiTable striped highlightOnHover>
      <UiTable.Thead>
        <UiTable.Tr>
          <UiTable.Th>{t.calibration.page.col.status}</UiTable.Th>
          <UiTable.Th>{t.calibration.page.col.scheduledAt}</UiTable.Th>
          <UiTable.Th>{t.calibration.page.col.participants}</UiTable.Th>
          <UiTable.Th>{t.calibration.page.col.adjustments}</UiTable.Th>
        </UiTable.Tr>
      </UiTable.Thead>
      <UiTable.Tbody>
        {sessions.map((session) => (
          <UiTable.Tr key={session.id}>
            <UiTable.Td>
              <SessionStatusBadge status={session.status} />
            </UiTable.Td>
            <UiTable.Td>
              <Text size="sm">{formatDateTime(session.scheduledAt)}</Text>
            </UiTable.Td>
            <UiTable.Td>
              <Text size="sm">{session.participantIds?.length ?? 0}</Text>
            </UiTable.Td>
            <UiTable.Td>
              <UiBadge variant="light">
                {session.adjustmentLog?.length ?? 0}
              </UiBadge>
            </UiTable.Td>
          </UiTable.Tr>
        ))}
      </UiTable.Tbody>
    </UiTable>
  );
}

function AdjustmentTimeline({
  session,
}: {
  session: CalibrationSessionResponse;
}): React.ReactNode {
  const t = useT();
  const entries = useMemo(
    () => (session.adjustmentLog ? [...session.adjustmentLog].reverse() : []),
    [session.adjustmentLog],
  );

  if (entries.length === 0) {
    return (
      <Text size="sm" c="dimmed">
        {t.calibration.director.logEmpty}
      </Text>
    );
  }

  return (
    <UiTable striped highlightOnHover>
      <UiTable.Thead>
        <UiTable.Tr>
          <UiTable.Th>{t.calibration.analytics.timelineCol.when}</UiTable.Th>
          <UiTable.Th>{t.calibration.analytics.timelineCol.employee}</UiTable.Th>
          <UiTable.Th>{t.calibration.analytics.timelineCol.grade}</UiTable.Th>
          <UiTable.Th>{t.calibration.analytics.timelineCol.reason}</UiTable.Th>
        </UiTable.Tr>
      </UiTable.Thead>
      <UiTable.Tbody>
        {entries.map((entry, index) => (
          <AdjustmentRow key={`${entry.reviewId}-${entry.at}-${index}`} entry={entry} />
        ))}
      </UiTable.Tbody>
    </UiTable>
  );
}

function AdjustmentRow({ entry }: { entry: AdjustmentEntry }): React.ReactNode {
  return (
    <UiTable.Tr>
      <UiTable.Td>
        <Text size="sm">{formatDateTime(entry.at)}</Text>
      </UiTable.Td>
      <UiTable.Td>
        <Text size="sm" ff="monospace">
          {entry.employeeId}
        </Text>
      </UiTable.Td>
      <UiTable.Td>
        <Group gap="xs" wrap="nowrap">
          <GradeBadge grade={entry.fromGrade} />
          <Text size="sm" c="dimmed">
            →
          </Text>
          <GradeBadge grade={entry.toGrade} />
        </Group>
      </UiTable.Td>
      <UiTable.Td>
        {entry.reason ? (
          <PerformancePreWrapText>{entry.reason}</PerformancePreWrapText>
        ) : (
          <Text size="sm" c="dimmed">
            —
          </Text>
        )}
      </UiTable.Td>
    </UiTable.Tr>
  );
}

function formatDateTime(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}

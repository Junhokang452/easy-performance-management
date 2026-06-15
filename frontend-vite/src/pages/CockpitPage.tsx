/**
 * CockpitPage — 성과관리 운영 첫 화면.
 *
 * 기존 cycle/review/calibration/report API 조합만 사용한다. 집계값은 화면
 * 진행률 표시용이며, 점수·등급·분포 계산의 SoT는 각 BE 응답이다.
 */
import { Badge, Group, Progress, SimpleGrid, Stack, Table, Text } from '@easy/ui-components/mantine';
import {
  IconChartBar,
  IconChecklist,
  IconFileReport,
  IconTargetArrow,
} from '@tabler/icons-react';
import {
  EmptyState,
  ErrorBoundary,
  LoadingState,
  PageHeader,
  SectionCard,
} from '@easy/ui-components';
import {
  PerformanceDistributionBars,
  PerformanceMetricGrid,
} from '@easy/ui-components/performance';

import {
  useCyclesQuery,
  type CycleResponse,
  type CycleStatus,
} from '../api/cycles';
import {
  ALL_REVIEW_STATUSES,
  useReviewsByCycleQuery,
  type ReviewResponse,
  type ReviewStatus,
} from '../api/reviews';
import {
  GRADE_BUCKETS,
  distributionTotal,
  formatRatio,
  useDistributionQuery,
  type GradeBucket,
} from '../api/calibration';
import { useReportsByCycleQuery } from '../api/reports';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleStatusBadge } from './cycles/CycleStatusBadge';

const ACTIVE_STATUS_ORDER: CycleStatus[] = [
  'ACTIVE',
  'GOAL_SETTING',
  'MID_REVIEW',
  'SELF_REVIEW',
  'MANAGER_REVIEW',
  'CALIBRATION',
  'FINALIZED',
  'PLANNED',
  'CANCELLED',
];

const REVIEW_PIPELINE: ReviewStatus[] = [
  'SELF_PENDING',
  'SELF_SUBMITTED',
  'MANAGER_PENDING',
  'MANAGER_SUBMITTED',
  'CALIBRATION',
  'FINALIZED',
];

const BAR_COLOR: Record<GradeBucket, string> = {
  S: 'red',
  A: 'orange',
  B: 'yellow',
  C: 'blue',
  D: 'grape',
  UNRATED: 'gray',
};

export function CockpitPage(): React.ReactNode {
  const t = useT();
  const cyclesQuery = useCyclesQuery();
  const cycles = cyclesQuery.data?.content ?? [];
  const activeCycle = selectActiveCycle(cycles);

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.cockpit.title}
        description={t.cockpit.description}
      />
      {cyclesQuery.isError ? (
        <SectionCard>
          <Text c="red">
            {t.common.message.loadError}: {getErrorMessage(cyclesQuery.error)}
          </Text>
        </SectionCard>
      ) : cyclesQuery.isLoading ? (
        <SectionCard>
          <LoadingState message={t.common.status.loading} />
        </SectionCard>
      ) : !activeCycle ? (
        <SectionCard>
          <EmptyState
            title={t.cockpit.empty}
            description={t.cockpit.emptyDescription}
          />
        </SectionCard>
      ) : (
        <CockpitContent cycle={activeCycle} cycles={cycles} />
      )}
    </ErrorBoundary>
  );
}

function CockpitContent({
  cycle,
  cycles,
}: {
  cycle: CycleResponse;
  cycles: CycleResponse[];
}): React.ReactNode {
  const t = useT();
  const reviewsQuery = useReviewsByCycleQuery(cycle.id);
  const distributionQuery = useDistributionQuery(cycle.id);
  const reportsQuery = useReportsByCycleQuery(cycle.id);

  const reviews = reviewsQuery.data ?? [];
  const reports = reportsQuery.data ?? [];
  const distribution = distributionQuery.data ?? null;
  const reviewCounts = countByStatus(reviews);
  const finalizedCount = reviewCounts.FINALIZED ?? 0;
  const submittedCount =
    (reviewCounts.SELF_SUBMITTED ?? 0) +
    (reviewCounts.MANAGER_SUBMITTED ?? 0) +
    (reviewCounts.CALIBRATION ?? 0) +
    finalizedCount;
  const reviewTotal = reviews.length;
  const readiness = distribution?.calibrationReadyCount ?? 0;
  const reportPublished = reports.length;

  return (
    <Stack>
      <SectionCard>
        <Group justify="space-between" align="flex-start">
          <Stack gap={4}>
            <Text fw={700}>{cycle.name}</Text>
            <Text size="sm" c="dimmed">
              {cycle.periodStart} - {cycle.periodEnd}
            </Text>
          </Stack>
          <CycleStatusBadge status={cycle.status} />
        </Group>
      </SectionCard>

      <PerformanceMetricGrid
        mobileSize="comfortable"
        items={[
          {
            label: t.cockpit.stat.cycles,
            value: cycles.length,
            description: t.cockpit.stat.cyclesHint,
            icon: <IconTargetArrow size={18} />,
            tone: 'brand',
          },
          {
            label: t.cockpit.stat.reviewProgress,
            value: formatPercent(submittedCount, reviewTotal),
            description: t.cockpit.stat.reviewProgressHint
              .replace('{done}', String(submittedCount))
              .replace('{total}', String(reviewTotal)),
            icon: <IconChecklist size={18} />,
            tone: 'green',
          },
          {
            label: t.cockpit.stat.calibrationReady,
            value: readiness,
            description: t.cockpit.stat.calibrationReadyHint.replace(
              '{total}',
              String(distribution?.totalReviews ?? reviewTotal),
            ),
            icon: <IconChartBar size={18} />,
            tone: 'violet',
          },
          {
            label: t.cockpit.stat.reports,
            value: reportPublished,
            description: t.cockpit.stat.reportsHint.replace(
              '{finalized}',
              String(finalizedCount),
            ),
            icon: <IconFileReport size={18} />,
            tone: 'blue',
          },
        ]}
      />

      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="md">
        <SectionCard
          title={t.cockpit.pipeline.title}
          description={t.cockpit.pipeline.description}
        >
          <Stack>
            {REVIEW_PIPELINE.map((status) => {
              const count = reviewCounts[status] ?? 0;
              return (
                <Stack key={status} gap={4}>
                  <Group justify="space-between">
                    <Text size="sm" fw={500}>
                      {t.review.status[status]}
                    </Text>
                    <Text size="sm" c="dimmed">
                      {count}
                    </Text>
                  </Group>
                  <Progress
                    value={reviewTotal > 0 ? (count / reviewTotal) * 100 : 0}
                    radius="sm"
                  />
                </Stack>
              );
            })}
          </Stack>
        </SectionCard>

        <SectionCard
          title={t.cockpit.distribution.title}
          description={t.cockpit.distribution.description}
        >
          {distributionQuery.isLoading ? (
            <LoadingState message={t.common.status.loading} />
          ) : distribution ? (
            <PerformanceDistributionBars
              label={t.cockpit.distribution.current}
              totalLabel={t.cockpit.distribution.total.replace(
                '{count}',
                String(distributionTotal(distribution.currentDistribution)),
              )}
              mobileSize="compact"
              buckets={GRADE_BUCKETS.map((bucket) => {
                const count = distribution.currentDistribution[bucket] ?? 0;
                const total = distributionTotal(distribution.currentDistribution);
                const ratio = total > 0 ? count / total : 0;
                return {
                  key: bucket,
                  label: bucket === 'UNRATED' ? t.calibration.grade.UNRATED : bucket,
                  color: BAR_COLOR[bucket],
                  count,
                  ratio,
                  ratioLabel: formatRatio(ratio),
                };
              })}
            />
          ) : (
            <Text size="sm" c="dimmed">
              {t.cockpit.distribution.empty}
            </Text>
          )}
        </SectionCard>
      </SimpleGrid>

      <SectionCard
        title={t.cockpit.cycles.title}
        description={t.cockpit.cycles.description}
      >
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t.cycles.field.name}</Table.Th>
              <Table.Th>{t.cycles.field.status}</Table.Th>
              <Table.Th>{t.cycles.field.periodStart}</Table.Th>
              <Table.Th>{t.cockpit.cycles.policy}</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {cycles.slice(0, 5).map((row) => (
              <Table.Tr key={row.id}>
                <Table.Td>
                  <Text size="sm" fw={row.id === cycle.id ? 700 : 500}>
                    {row.name}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <CycleStatusBadge status={row.status} />
                </Table.Td>
                <Table.Td>
                  <Text size="sm">
                    {row.periodStart} - {row.periodEnd}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Badge variant={row.policyId ? 'light' : 'outline'}>
                    {row.policyId ? t.common.status.active : t.cycles.policy.notSet}
                  </Badge>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </SectionCard>
    </Stack>
  );
}

function selectActiveCycle(cycles: CycleResponse[]): CycleResponse | null {
  if (cycles.length === 0) return null;
  return [...cycles].sort((a, b) => {
    const statusDelta =
      ACTIVE_STATUS_ORDER.indexOf(a.status) - ACTIVE_STATUS_ORDER.indexOf(b.status);
    if (statusDelta !== 0) return statusDelta;
    return b.periodStart.localeCompare(a.periodStart);
  })[0] ?? null;
}

function countByStatus(reviews: ReviewResponse[]): Partial<Record<ReviewStatus, number>> {
  const counts: Partial<Record<ReviewStatus, number>> = {};
  for (const status of ALL_REVIEW_STATUSES) counts[status] = 0;
  for (const review of reviews) {
    counts[review.status] = (counts[review.status] ?? 0) + 1;
  }
  return counts;
}

function formatPercent(done: number, total: number): string {
  if (total <= 0) return '0%';
  return `${Math.round((done / total) * 100)}%`;
}

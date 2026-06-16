/**
 * HrReportsPage (#26) — HR 리포트 발행 (`/hr/reports`).
 *
 * cycle Select(CycleSelect 재사용) → 발행 요약(FINALIZED review 수 vs 발행 수) +
 * 일괄 발행 confirm 모달(cycle FINALIZED 아닐 때 안내 + published/skipped notification) +
 * 발행 현황 테이블(employeeId·finalGrade GradeBadge·publishedAt·viewed/acknowledged 아이콘·superseded UiBadge) +
 * 행별 재발행(supersede) confirm 모달("content 재동결" 경고).
 *
 * 계약 §6/§7. publish=일괄(FINALIZED review 중 active report 미존재분만) / supersede=개별 재발행(신규 row).
 * finalGrade·점수는 BE content 표시만. STD-FE 5 정합.
 */
import { useState } from 'react';
import { Group, Modal, ScrollArea, Stack, Text } from '@easy/ui-components/mantine';
import { showToast } from '../shared/toast';
import {
  IconAlertTriangle,
  IconCircleCheck,
  IconEye,
  IconEyeOff,
  IconFileAnalytics,
  IconRefresh,
  IconSend,
} from '@tabler/icons-react';
import {
  UiBadge,
  UiAlert,
  UiButton,
  UiTable,
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';
import {
  PerformanceMetricGrid,
  PerformanceProgressSummary,
  PerformanceStatusIconGroup,
  formatPerformanceRatioPercent,
  formatPerformanceRatioText,
} from '@easy/ui-components/performance';

import {
  formatReportDateTime,
  usePublishReportsMutation,
  useReportsByCycleQuery,
  useSupersedeReportMutation,
  type ReportResponse,
} from '../api/reports';
import {
  useCyclesQuery,
  type CycleStatus,
} from '../api/cycles';
import { useReviewsByCycleQuery } from '../api/reviews';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleSelect } from './kpi/CycleSelect';
import { GradeBadge } from './calibration/GradeBadge';
import { mapReportErrorToMessage } from './report/errorMapping';

export function HrReportsPage(): React.ReactNode {
  const t = useT();
  const [cycleId, setCycleId] = useState<string | null>(null);

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.report.hr.title}
        description={t.report.hr.description}
      />
      <SectionCard>
        <CycleSelect value={cycleId} onChange={setCycleId} />
      </SectionCard>

      {!cycleId ? (
        <SectionCard>
          <Text c="dimmed" size="sm">
            {t.report.hr.needCycle}
          </Text>
        </SectionCard>
      ) : (
        <ReportsPanel cycleId={cycleId} />
      )}
    </ErrorBoundary>
  );
}

interface PanelProps {
  cycleId: string;
}

function ReportsPanel({ cycleId }: PanelProps): React.ReactNode {
  const t = useT();

  const cyclesQuery = useCyclesQuery();
  const cycleStatus: CycleStatus | null =
    cyclesQuery.data?.content.find((c) => c.id === cycleId)?.status ?? null;
  const cycleFinalized = cycleStatus === 'FINALIZED';

  const reportsQuery = useReportsByCycleQuery(cycleId);
  const reports = reportsQuery.data ?? [];

  // 발행 요약: FINALIZED review 수 (useReviewsByCycleQuery 재사용 클라이언트 필터) vs 발행 완료 수.
  const reviewsQuery = useReviewsByCycleQuery(cycleId);
  const finalizedReviewCount = (reviewsQuery.data ?? []).filter(
    (r) => r.status === 'FINALIZED',
  ).length;
  // active(비-superseded) 리포트가 발행 완료된 review 수.
  const activeReports = reports.filter((r) => !r.superseded);
  const supersededReports = reports.filter((r) => r.superseded);
  const publishedActiveCount = activeReports.length;
  const readyToPublishCount = Math.max(
    finalizedReviewCount - publishedActiveCount,
    0,
  );
  const viewedActiveCount = activeReports.filter((r) => r.viewedAt).length;
  const acknowledgedActiveCount = activeReports.filter(
    (r) => r.acknowledged,
  ).length;

  const publishMut = usePublishReportsMutation(cycleId);
  const supersedeMut = useSupersedeReportMutation(cycleId);

  const [publishOpen, setPublishOpen] = useState(false);
  const [supersedeTarget, setSupersedeTarget] = useState<ReportResponse | null>(
    null,
  );

  const handlePublish = (): void => {
    publishMut.mutate(
      { actorEmployeeId: null },
      {
        onSuccess: (res) => {
          setPublishOpen(false);
          showToast({
            tone: 'success',
            message: t.report.hr.publishResult
              .replace('{published}', String(res.publishedCount))
              .replace('{skipped}', String(res.skippedCount)),
          });
        },
        onError: (err) => {
          setPublishOpen(false);
          showToast({
            tone: 'danger',
            message: mapReportErrorToMessage(err, t),
          });
        },
      },
    );
  };

  const handleSupersede = (): void => {
    if (!supersedeTarget) return;
    supersedeMut.mutate(
      { reportId: supersedeTarget.id, req: { actorEmployeeId: null } },
      {
        onSuccess: () => {
          setSupersedeTarget(null);
          showToast({
            tone: 'success',
            message: t.report.hr.supersedeDone,
          });
        },
        onError: (err) => {
          setSupersedeTarget(null);
          showToast({
            tone: 'danger',
            message: mapReportErrorToMessage(err, t),
          });
        },
      },
    );
  };

  if (reportsQuery.isError) {
    return (
      <SectionCard>
        <Text c="red">
          {t.common.message.loadError}: {getErrorMessage(reportsQuery.error)}
        </Text>
      </SectionCard>
    );
  }

  return (
    <>
      <SectionCard
        title={t.report.hr.governance.title}
        description={t.report.hr.governance.description}
      >
        <Stack>
          <PerformanceMetricGrid
            items={[
              {
                label: t.report.hr.governance.readiness,
                value: String(readyToPublishCount),
                description: t.report.hr.governance.readinessHint.replace(
                  '{count}',
                  String(finalizedReviewCount),
                ),
                icon: <IconSend size={20} />,
                tone: readyToPublishCount > 0 ? 'yellow' : 'green',
              },
              {
                label: t.report.hr.governance.published,
                value: String(publishedActiveCount),
                description: formatPerformanceRatioText(
                  publishedActiveCount,
                  finalizedReviewCount,
                ),
                icon: <IconFileAnalytics size={20} />,
                tone: 'brand',
              },
              {
                label: t.report.hr.governance.viewed,
                value: formatPerformanceRatioPercent(
                  viewedActiveCount,
                  publishedActiveCount,
                ),
                description: formatPerformanceRatioText(
                  viewedActiveCount,
                  publishedActiveCount,
                ),
                icon: <IconEye size={20} />,
                tone:
                  publishedActiveCount > 0 &&
                  viewedActiveCount === publishedActiveCount
                    ? 'green'
                    : 'blue',
              },
              {
                label: t.report.hr.governance.acknowledged,
                value: formatPerformanceRatioPercent(
                  acknowledgedActiveCount,
                  publishedActiveCount,
                ),
                description: formatPerformanceRatioText(
                  acknowledgedActiveCount,
                  publishedActiveCount,
                ),
                icon: <IconCircleCheck size={20} />,
                tone:
                  publishedActiveCount > 0 &&
                  acknowledgedActiveCount === publishedActiveCount
                    ? 'green'
                    : 'yellow',
              },
            ]}
          />

          <Stack gap="xs">
            <PerformanceProgressSummary
              label={t.report.hr.governance.progressPublished}
              value={publishedActiveCount}
              total={finalizedReviewCount}
            />
            <PerformanceProgressSummary
              label={t.report.hr.governance.progressViewed}
              value={viewedActiveCount}
              total={publishedActiveCount}
            />
            <PerformanceProgressSummary
              label={t.report.hr.governance.progressAcknowledged}
              value={acknowledgedActiveCount}
              total={publishedActiveCount}
            />
          </Stack>

          <Group gap="xs" wrap="wrap">
            {cycleStatus && (
              <UiBadge
                variant={cycleFinalized ? 'filled' : 'outline'}
                color={cycleFinalized ? 'teal' : 'gray'}
              >
                {t.cycles.status[cycleStatus]}
              </UiBadge>
            )}
            <UiBadge variant="light" color="gray">
              {t.report.hr.governance.superseded}: {supersededReports.length}
            </UiBadge>
          </Group>

          {!cycleFinalized && (
            <UiAlert color="yellow" variant="light">
              {t.report.hr.needFinalized}
            </UiAlert>
          )}

          <Group justify="flex-end" wrap="wrap">
            <UiButton
              leftSection={<IconSend size={16} />}
              onClick={() => setPublishOpen(true)}
              disabled={!cycleFinalized}
              loading={publishMut.isPending}
            >
              {t.report.hr.publish}
            </UiButton>
          </Group>
        </Stack>
      </SectionCard>

      <SectionCard
        title={t.report.hr.governance.listTitle}
        description={t.report.hr.governance.listDescription}
      >
        {reportsQuery.isLoading ? (
          <LoadingState message={t.common.status.loading} />
        ) : activeReports.length === 0 ? (
          <EmptyState
            title={t.report.hr.empty}
            description={t.report.hr.emptyHint}
          />
        ) : (
          <ReportsTable
            reports={activeReports}
            cycleFinalized={cycleFinalized}
            onSupersede={setSupersedeTarget}
            supersedePending={supersedeMut.isPending}
          />
        )}
      </SectionCard>

      <SectionCard
        title={t.report.hr.governance.historyTitle}
        description={t.report.hr.governance.historyDescription}
      >
        {reportsQuery.isLoading ? (
          <LoadingState message={t.common.status.loading} />
        ) : supersededReports.length === 0 ? (
          <EmptyState
            title={t.report.hr.governance.historyEmpty}
            description={t.report.hr.supersedeWarning}
          />
        ) : (
          <SupersedeHistoryTable reports={supersededReports} />
        )}
      </SectionCard>

      {/* 일괄 발행 확인 모달 */}
      <Modal
        opened={publishOpen}
        onClose={() => {
          if (!publishMut.isPending) setPublishOpen(false);
        }}
        title={t.report.hr.publishTitle}
        centered
        size="md"
      >
        <Stack>
          {!cycleFinalized ? (
            <UiAlert
              color="yellow"
              variant="light"
              icon={<IconAlertTriangle size={18} />}
            >
              {t.report.hr.needFinalized}
            </UiAlert>
          ) : (
            <Text size="sm">
              {t.report.hr.publishConfirm.replace(
                '{count}',
                String(finalizedReviewCount),
              )}
            </Text>
          )}
          <Group justify="flex-end" mt="sm">
            <UiButton
              variant="default"
              onClick={() => setPublishOpen(false)}
              disabled={publishMut.isPending}
            >
              {t.common.action.cancel}
            </UiButton>
            <UiButton
              onClick={handlePublish}
              loading={publishMut.isPending}
              disabled={!cycleFinalized}
            >
              {t.report.hr.publish}
            </UiButton>
          </Group>
        </Stack>
      </Modal>

      {/* 재발행(supersede) 확인 모달 */}
      <Modal
        opened={Boolean(supersedeTarget)}
        onClose={() => {
          if (!supersedeMut.isPending) setSupersedeTarget(null);
        }}
        title={t.report.hr.supersedeTitle}
        centered
        size="md"
      >
        <Stack>
          <UiAlert
            color="orange"
            variant="light"
            icon={<IconAlertTriangle size={18} />}
          >
            {t.report.hr.supersedeWarning}
          </UiAlert>
          {supersedeTarget && (
            <Text size="sm" ff="monospace">
              {supersedeTarget.employeeId}
            </Text>
          )}
          <Group justify="flex-end" mt="sm">
            <UiButton
              variant="default"
              onClick={() => setSupersedeTarget(null)}
              disabled={supersedeMut.isPending}
            >
              {t.common.action.cancel}
            </UiButton>
            <UiButton
              color="orange"
              onClick={handleSupersede}
              loading={supersedeMut.isPending}
            >
              {t.report.hr.supersede}
            </UiButton>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

interface TableProps {
  reports: ReportResponse[];
  cycleFinalized: boolean;
  onSupersede: (report: ReportResponse) => void;
  supersedePending: boolean;
}

function ReportsTable({
  reports,
  cycleFinalized,
  onSupersede,
  supersedePending,
}: TableProps): React.ReactNode {
  const t = useT();
  return (
    <ScrollArea type="auto" offsetScrollbars>
      <UiTable striped highlightOnHover miw={760}>
        <UiTable.Thead>
          <UiTable.Tr>
            <UiTable.Th>{t.report.hr.col.employeeId}</UiTable.Th>
            <UiTable.Th>{t.report.hr.col.finalGrade}</UiTable.Th>
            <UiTable.Th>{t.report.hr.col.publishedAt}</UiTable.Th>
            <UiTable.Th>{t.report.hr.col.status}</UiTable.Th>
            <UiTable.Th />
          </UiTable.Tr>
        </UiTable.Thead>
        <UiTable.Tbody>
          {reports.map((report) => (
            <UiTable.Tr key={report.id}>
              <UiTable.Td>
                <Text size="sm" ff="monospace" fw={700} c="var(--easy-color-text)">
                  {report.employeeId}
                </Text>
              </UiTable.Td>
              <UiTable.Td>
                <GradeBadge grade={report.content.finalGrade} />
              </UiTable.Td>
              <UiTable.Td>
                <Text size="sm">{formatReportDateTime(report.publishedAt)}</Text>
              </UiTable.Td>
              <UiTable.Td>
                <PerformanceStatusIconGroup
                  items={[
                    {
                      key: 'viewed',
                      label: report.viewedAt
                        ? t.report.hr.viewed
                        : t.report.hr.notViewed,
                      color: report.viewedAt ? 'blue' : 'gray',
                      icon: report.viewedAt ? (
                        <IconEye size={14} />
                      ) : (
                        <IconEyeOff size={14} />
                      ),
                    },
                    {
                      key: 'acknowledged',
                      label: report.acknowledged
                        ? t.report.hr.acknowledged
                        : t.report.hr.notAcknowledged,
                      color: report.acknowledged ? 'green' : 'gray',
                      icon: <IconCircleCheck size={14} />,
                    },
                  ]}
                  trailing={
                    report.superseded ? (
                    <UiBadge size="xs" variant="outline" color="gray">
                      {t.report.hr.superseded}
                    </UiBadge>
                    ) : null
                  }
                />
              </UiTable.Td>
              <UiTable.Td>
                <Group justify="flex-end" wrap="nowrap">
                  {!report.superseded && (
                    <UiButton
                      size="xs"
                      variant="light"
                      color="orange"
                      leftSection={<IconRefresh size={14} />}
                      onClick={() => onSupersede(report)}
                      disabled={!cycleFinalized || supersedePending}
                    >
                      {t.report.hr.supersede}
                    </UiButton>
                  )}
                </Group>
              </UiTable.Td>
            </UiTable.Tr>
          ))}
        </UiTable.Tbody>
      </UiTable>
    </ScrollArea>
  );
}

function SupersedeHistoryTable({
  reports,
}: {
  reports: ReportResponse[];
}): React.ReactNode {
  const t = useT();
  return (
    <ScrollArea type="auto" offsetScrollbars>
      <UiTable striped highlightOnHover miw={680}>
        <UiTable.Thead>
          <UiTable.Tr>
            <UiTable.Th>{t.report.hr.col.employeeId}</UiTable.Th>
            <UiTable.Th>{t.report.hr.col.finalGrade}</UiTable.Th>
            <UiTable.Th>{t.report.hr.col.publishedAt}</UiTable.Th>
            <UiTable.Th>{t.report.hr.col.status}</UiTable.Th>
          </UiTable.Tr>
        </UiTable.Thead>
        <UiTable.Tbody>
          {reports.map((report) => (
            <UiTable.Tr key={report.id}>
              <UiTable.Td>
                <Text size="sm" ff="monospace" fw={700} c="var(--easy-color-text)">
                  {report.employeeId}
                </Text>
              </UiTable.Td>
              <UiTable.Td>
                <GradeBadge grade={report.content.finalGrade} />
              </UiTable.Td>
              <UiTable.Td>
                <Text size="sm">{formatReportDateTime(report.publishedAt)}</Text>
              </UiTable.Td>
              <UiTable.Td>
                <UiBadge size="xs" variant="outline" color="gray">
                  {t.report.hr.superseded}
                </UiBadge>
              </UiTable.Td>
            </UiTable.Tr>
          ))}
        </UiTable.Tbody>
      </UiTable>
    </ScrollArea>
  );
}

/**
 * HrReportsPage (#26) — HR 리포트 발행 (`/hr/reports`).
 *
 * cycle Select(CycleSelect 재사용) → 발행 요약(FINALIZED review 수 vs 발행 수) +
 * 일괄 발행 confirm 모달(cycle FINALIZED 아닐 때 안내 + published/skipped notification) +
 * 발행 현황 테이블(employeeId·finalGrade GradeBadge·publishedAt·viewed/acknowledged 아이콘·superseded Badge) +
 * 행별 재발행(supersede) confirm 모달("content 재동결" 경고).
 *
 * 계약 §6/§7. publish=일괄(FINALIZED review 중 active report 미존재분만) / supersede=개별 재발행(신규 row).
 * finalGrade·점수는 BE content 표시만. STD-FE 5 정합.
 */
import { useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  Stack,
  Table,
  Text,
  ThemeIcon,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconCircleCheck,
  IconEye,
  IconEyeOff,
  IconRefresh,
  IconSend,
} from '@tabler/icons-react';
import {
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';

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
  const publishedActiveCount = reports.filter((r) => !r.superseded).length;

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
          notifications.show({
            color: 'green',
            message: t.report.hr.publishResult
              .replace('{published}', String(res.publishedCount))
              .replace('{skipped}', String(res.skippedCount)),
          });
        },
        onError: (err) => {
          setPublishOpen(false);
          notifications.show({
            color: 'red',
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
          notifications.show({
            color: 'green',
            message: t.report.hr.supersedeDone,
          });
        },
        onError: (err) => {
          setSupersedeTarget(null);
          notifications.show({
            color: 'red',
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
      <SectionCard>
        <Stack>
          <Group gap="xs">
            <Badge variant="light" color="blue">
              {t.report.hr.summaryFinalized}: {finalizedReviewCount}
            </Badge>
            <Badge variant="light" color="teal">
              {t.report.hr.summaryPublished}: {publishedActiveCount}
            </Badge>
            {cycleStatus && (
              <Badge
                variant={cycleFinalized ? 'filled' : 'outline'}
                color={cycleFinalized ? 'teal' : 'gray'}
              >
                {t.cycles.status[cycleStatus]}
              </Badge>
            )}
          </Group>

          {!cycleFinalized && (
            <Alert color="yellow" variant="light">
              {t.report.hr.needFinalized}
            </Alert>
          )}

          <Group justify="flex-end">
            <Button
              leftSection={<IconSend size={16} />}
              onClick={() => setPublishOpen(true)}
              disabled={!cycleFinalized}
              loading={publishMut.isPending}
            >
              {t.report.hr.publish}
            </Button>
          </Group>
        </Stack>
      </SectionCard>

      <SectionCard>
        {reportsQuery.isLoading ? (
          <LoadingState message={t.common.status.loading} />
        ) : reports.length === 0 ? (
          <EmptyState
            title={t.report.hr.empty}
            description={t.report.hr.emptyHint}
          />
        ) : (
          <ReportsTable
            reports={reports}
            cycleFinalized={cycleFinalized}
            onSupersede={setSupersedeTarget}
            supersedePending={supersedeMut.isPending}
          />
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
            <Alert
              color="yellow"
              variant="light"
              icon={<IconAlertTriangle size={18} />}
            >
              {t.report.hr.needFinalized}
            </Alert>
          ) : (
            <Text size="sm">
              {t.report.hr.publishConfirm.replace(
                '{count}',
                String(finalizedReviewCount),
              )}
            </Text>
          )}
          <Group justify="flex-end" mt="sm">
            <Button
              variant="default"
              onClick={() => setPublishOpen(false)}
              disabled={publishMut.isPending}
            >
              {t.common.action.cancel}
            </Button>
            <Button
              onClick={handlePublish}
              loading={publishMut.isPending}
              disabled={!cycleFinalized}
            >
              {t.report.hr.publish}
            </Button>
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
          <Alert
            color="orange"
            variant="light"
            icon={<IconAlertTriangle size={18} />}
          >
            {t.report.hr.supersedeWarning}
          </Alert>
          {supersedeTarget && (
            <Text size="sm" ff="monospace">
              {supersedeTarget.employeeId}
            </Text>
          )}
          <Group justify="flex-end" mt="sm">
            <Button
              variant="default"
              onClick={() => setSupersedeTarget(null)}
              disabled={supersedeMut.isPending}
            >
              {t.common.action.cancel}
            </Button>
            <Button
              color="orange"
              onClick={handleSupersede}
              loading={supersedeMut.isPending}
            >
              {t.report.hr.supersede}
            </Button>
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
    <Table striped highlightOnHover>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>{t.report.hr.col.employeeId}</Table.Th>
          <Table.Th>{t.report.hr.col.finalGrade}</Table.Th>
          <Table.Th>{t.report.hr.col.publishedAt}</Table.Th>
          <Table.Th>{t.report.hr.col.status}</Table.Th>
          <Table.Th />
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {reports.map((report) => (
          <Table.Tr key={report.id}>
            <Table.Td>
              <Text size="sm" ff="monospace">
                {report.employeeId}
              </Text>
            </Table.Td>
            <Table.Td>
              <GradeBadge grade={report.content.finalGrade} />
            </Table.Td>
            <Table.Td>
              <Text size="sm">{formatReportDateTime(report.publishedAt)}</Text>
            </Table.Td>
            <Table.Td>
              <Group gap={6} wrap="nowrap">
                <Tooltip
                  label={
                    report.viewedAt
                      ? t.report.hr.viewed
                      : t.report.hr.notViewed
                  }
                >
                  <ThemeIcon
                    size="sm"
                    variant="light"
                    color={report.viewedAt ? 'blue' : 'gray'}
                  >
                    {report.viewedAt ? (
                      <IconEye size={14} />
                    ) : (
                      <IconEyeOff size={14} />
                    )}
                  </ThemeIcon>
                </Tooltip>
                <Tooltip
                  label={
                    report.acknowledged
                      ? t.report.hr.acknowledged
                      : t.report.hr.notAcknowledged
                  }
                >
                  <ThemeIcon
                    size="sm"
                    variant="light"
                    color={report.acknowledged ? 'green' : 'gray'}
                  >
                    <IconCircleCheck size={14} />
                  </ThemeIcon>
                </Tooltip>
                {report.superseded && (
                  <Badge size="xs" variant="outline" color="gray">
                    {t.report.hr.superseded}
                  </Badge>
                )}
              </Group>
            </Table.Td>
            <Table.Td>
              <Group justify="flex-end">
                {!report.superseded && (
                  <Button
                    size="xs"
                    variant="light"
                    color="orange"
                    leftSection={<IconRefresh size={14} />}
                    onClick={() => onSupersede(report)}
                    disabled={!cycleFinalized || supersedePending}
                  >
                    {t.report.hr.supersede}
                  </Button>
                )}
              </Group>
            </Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  );
}

/**
 * CyclesPage — HR > 평가 사이클 관리 페이지 (P0-S1 슬라이스).
 *
 * SoT: `_workspace/evaluation_research_2026-06-11/decisions_2026-06-11.md`
 *
 * 기능:
 * - 사이클 운영 지표 + 상태 타임라인
 * - 사이클 목록 Table (이름·유형·기간·상태·정책 여부·Actions)
 * - 사이클 생성/편집/정책 모달
 * - 상태 전이 메뉴 (현재 상태 → 가능한 다음 상태만)
 * - 사이클 삭제 (PLANNED 단계만 BE 허용; FE 는 확인 후 삭제 요청)
 *
 * STD-FE 5 정합.
 */
import { useMemo, useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Menu,
  Modal,
  Progress,
  Stack,
  Table,
  Text,
} from '@easy/ui-components/mantine';
import { notifications } from '@mantine/notifications';
import {
  IconCalendarStats,
  IconChevronDown,
  IconCircleCheck,
  IconDotsVertical,
  IconGitBranch,
  IconPlus,
  IconSettings,
} from '@tabler/icons-react';
import {
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';
import {
  PerformanceMetricGrid,
  PerformanceProgressSummary,
  formatPerformanceRatioNumber,
  formatPerformanceRatioPercent,
  formatPerformanceRatioText,
} from '@easy/ui-components/performance';

import {
  getAllowedNextStatuses,
  useCyclesQuery,
  useDeleteCycleMutation,
  useTransitionCycleMutation,
  type CycleResponse,
  type CycleStatus,
} from '../api/cycles';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleCreateModal } from './cycles/CycleCreateModal';
import { CycleEditModal } from './cycles/CycleEditModal';
import { CycleStatusBadge } from './cycles/CycleStatusBadge';
import { PolicyEditModal } from './cycles/PolicyEditModal';
import { mapApiErrorToMessage } from './cycles/errorMapping';

const STATUS_FLOW: CycleStatus[] = [
  'PLANNED',
  'ACTIVE',
  'GOAL_SETTING',
  'MID_REVIEW',
  'SELF_REVIEW',
  'MANAGER_REVIEW',
  'CALIBRATION',
  'FINALIZED',
  'CANCELLED',
];

export function CyclesPage(): React.ReactNode {
  const t = useT();
  const { data, isLoading, isError, error } = useCyclesQuery();

  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<CycleResponse | null>(null);
  const [policyTarget, setPolicyTarget] = useState<CycleResponse | null>(null);

  const rows = data?.content ?? [];
  const stats = useMemo(() => buildCycleStats(rows), [rows]);

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.cycles.title}
        actions={
          <Button
            leftSection={<IconPlus size={16} />}
            onClick={() => setCreateOpen(true)}
          >
            {t.cycles.create}
          </Button>
        }
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
            title={t.cycles.empty}
            action={{
              label: t.cycles.create,
              onClick: () => setCreateOpen(true),
            }}
          />
        ) : (
          <Stack>
            <CycleOperatingOverview stats={stats} />
            <CycleStatusTimeline stats={stats} />
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t.cycles.field.name}</Table.Th>
                  <Table.Th>{t.cycles.field.cycleType}</Table.Th>
                  <Table.Th>{t.cycles.field.periodStart}</Table.Th>
                  <Table.Th>{t.cycles.field.status}</Table.Th>
                  <Table.Th>{t.policy.title}</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {rows.map((row) => (
                  <CycleRow
                    key={row.id}
                    row={row}
                    onEdit={() => setEditTarget(row)}
                    onPolicy={() => setPolicyTarget(row)}
                  />
                ))}
              </Table.Tbody>
            </Table>
          </Stack>
        )}
      </SectionCard>

      <CycleCreateModal
        opened={createOpen}
        onClose={() => setCreateOpen(false)}
      />
      {editTarget && (
        <CycleEditModal
          opened={Boolean(editTarget)}
          onClose={() => setEditTarget(null)}
          cycle={editTarget}
        />
      )}
      {policyTarget && (
        <PolicyEditModal
          opened={Boolean(policyTarget)}
          onClose={() => setPolicyTarget(null)}
          cycle={policyTarget}
        />
      )}
    </ErrorBoundary>
  );
}

interface CycleStats {
  total: number;
  policyReady: number;
  inOperation: number;
  transitionable: number;
  statusCounts: Record<CycleStatus, number>;
}

function CycleOperatingOverview({
  stats,
}: {
  stats: CycleStats;
}): React.ReactNode {
  const t = useT();
  return (
    <Stack gap="sm">
      <PerformanceMetricGrid
        items={[
          {
            label: t.cycles.operating.total,
            value: String(stats.total),
            description: t.cycles.operating.totalHint,
            icon: <IconCalendarStats size={20} />,
            tone: 'brand',
          },
          {
            label: t.cycles.operating.policyReady,
            value: formatPerformanceRatioPercent(stats.policyReady, stats.total),
            description: formatPerformanceRatioText(stats.policyReady, stats.total),
            icon: <IconSettings size={20} />,
            tone: stats.policyReady === stats.total ? 'green' : 'yellow',
          },
          {
            label: t.cycles.operating.inOperation,
            value: String(stats.inOperation),
            description: t.cycles.operating.inOperationHint,
            icon: <IconGitBranch size={20} />,
            tone: 'blue',
          },
          {
            label: t.cycles.operating.transitionable,
            value: String(stats.transitionable),
            description: t.cycles.operating.transitionableHint,
            icon: <IconCircleCheck size={20} />,
            tone: stats.transitionable > 0 ? 'yellow' : 'gray',
          },
        ]}
      />
      <PerformanceProgressSummary
        label={t.cycles.operating.policyProgress}
        value={stats.policyReady}
        total={stats.total}
      />
    </Stack>
  );
}

function CycleStatusTimeline({
  stats,
}: {
  stats: CycleStats;
}): React.ReactNode {
  const t = useT();
  return (
    <SectionCard
      title={t.cycles.operating.timelineTitle}
      description={t.cycles.operating.timelineDescription}
    >
      <Stack gap="sm">
        {STATUS_FLOW.map((status) => {
          const count = stats.statusCounts[status];
          return (
            <Stack key={status} gap={4}>
              <Group justify="space-between" wrap="nowrap">
                <Group gap="xs" wrap="nowrap">
                  <CycleStatusBadge status={status} />
                  <Text size="sm" fw={600}>
                    {t.cycles.status[status]}
                  </Text>
                </Group>
                <Badge variant="light" color="gray">
                  {count}
                </Badge>
              </Group>
              <Progress value={formatPerformanceRatioNumber(count, stats.total)} />
            </Stack>
          );
        })}
      </Stack>
    </SectionCard>
  );
}

interface CycleRowProps {
  row: CycleResponse;
  onEdit: () => void;
  onPolicy: () => void;
}

function CycleRow({ row, onEdit, onPolicy }: CycleRowProps): React.ReactNode {
  const t = useT();
  const transitionMut = useTransitionCycleMutation(row.id);
  const deleteMut = useDeleteCycleMutation();
  const nextStatuses = getAllowedNextStatuses(row.status);
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);

  const handleTransition = (next: CycleStatus): void => {
    transitionMut.mutate(
      { toStatus: next },
      {
        onSuccess: () => {
          notifications.show({
            color: 'green',
            message: t.common.message.updated,
          });
        },
        onError: (err) => {
          notifications.show({
            color: 'red',
            message: mapApiErrorToMessage(err, t),
          });
        },
      },
    );
  };

  const confirmDelete = (): void => {
    deleteMut.mutate(row.id, {
      onSuccess: () => {
        setConfirmDeleteOpen(false);
        notifications.show({
          color: 'green',
          message: t.common.message.deleted,
        });
      },
      onError: (err) => {
        setConfirmDeleteOpen(false);
        notifications.show({
          color: 'red',
          message: mapApiErrorToMessage(err, t),
        });
      },
    });
  };

  return (
    <Table.Tr>
      <Table.Td>
        <Text size="sm" fw={500}>
          {row.name}
        </Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm">{t.cycles.type[row.cycleType]}</Text>
      </Table.Td>
      <Table.Td>
        <Group gap={6}>
          <Text size="sm">{row.periodStart}</Text>
          <Text size="sm" c="dimmed">
            ~
          </Text>
          <Text size="sm">{row.periodEnd}</Text>
        </Group>
      </Table.Td>
      <Table.Td>
        <CycleStatusBadge status={row.status} />
      </Table.Td>
      <Table.Td>
        {row.policyId ? (
          <Badge color="teal" variant="dot">
            OK
          </Badge>
        ) : (
          <Button size="xs" variant="light" color="yellow" onClick={onPolicy}>
            {t.cycles.policy.notSet}
          </Button>
        )}
      </Table.Td>
      <Table.Td>
        <Group gap={4} justify="flex-end">
          {nextStatuses.length > 0 && (
            <Menu shadow="md" position="bottom-end" withinPortal>
              <Menu.Target>
                <Button
                  size="xs"
                  variant="light"
                  rightSection={<IconChevronDown size={14} />}
                  loading={transitionMut.isPending}
                >
                  {t.cycles.action.transition}
                </Button>
              </Menu.Target>
              <Menu.Dropdown>
                {nextStatuses.map((s) => (
                  <Menu.Item key={s} onClick={() => handleTransition(s)}>
                    {t.cycles.status[s]}
                  </Menu.Item>
                ))}
              </Menu.Dropdown>
            </Menu>
          )}
          <Menu shadow="md" position="bottom-end" withinPortal>
            <Menu.Target>
              <ActionIcon variant="subtle" aria-label="more">
                <IconDotsVertical size={16} />
              </ActionIcon>
            </Menu.Target>
            <Menu.Dropdown>
              <Menu.Item onClick={onEdit}>{t.cycles.action.edit}</Menu.Item>
              <Menu.Item onClick={onPolicy}>
                {t.cycles.action.policy}
              </Menu.Item>
              <Menu.Item
                color="red"
                onClick={() => setConfirmDeleteOpen(true)}
              >
                {t.cycles.action.delete}
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </Group>
        <Modal
          opened={confirmDeleteOpen}
          onClose={() => setConfirmDeleteOpen(false)}
          title={t.cycles.action.delete}
          centered
          size="sm"
        >
          <Stack>
            <Text size="sm">{row.name}</Text>
            <Group justify="flex-end">
              <Button
                variant="default"
                onClick={() => setConfirmDeleteOpen(false)}
                disabled={deleteMut.isPending}
              >
                {t.common.action.cancel}
              </Button>
              <Button
                color="red"
                onClick={confirmDelete}
                loading={deleteMut.isPending}
              >
                {t.common.action.delete}
              </Button>
            </Group>
          </Stack>
        </Modal>
      </Table.Td>
    </Table.Tr>
  );
}

function buildCycleStats(rows: CycleResponse[]): CycleStats {
  const statusCounts = Object.fromEntries(
    STATUS_FLOW.map((status) => [status, 0]),
  ) as Record<CycleStatus, number>;

  for (const row of rows) {
    statusCounts[row.status] += 1;
  }

  return {
    total: rows.length,
    policyReady: rows.filter((row) => row.policyId).length,
    inOperation: rows.filter(
      (row) => row.status !== 'FINALIZED' && row.status !== 'CANCELLED',
    ).length,
    transitionable: rows.filter(
      (row) => getAllowedNextStatuses(row.status).length > 0,
    ).length,
    statusCounts,
  };
}

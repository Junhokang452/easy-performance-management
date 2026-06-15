/**
 * HrCalibrationSessionsPage (#28) — Calibration 세션 관리 (`/hr/calibration-sessions`).
 *
 * cycle Select(CycleSelect 재사용) → 세션 목록 테이블(상태 Badge·일정·참가자 수·orgUnit·조정 건수) +
 * 생성/수정 모달(participantIds TagsInput) + transition 메뉴(PLANNED→IN_SESSION / CONFIRMED→CLOSED) +
 * confirm 모달(finalizeReviews Switch + finalized/skipped 결과 notification) + PLANNED 한정 삭제 confirm.
 *
 * 계약 §6 11 endpoint. 상태 전이·등급 계산은 BE 가 SoT. STD-FE 5 정합.
 */
import { useState } from 'react';
import {
  ActionIcon,
  Button,
  Group,
  Menu,
  Modal,
  Stack,
  Table,
  Text,
} from '@easy/ui-components/mantine';
import { notifications } from '@mantine/notifications';
import { IconDotsVertical, IconPlus } from '@tabler/icons-react';
import {
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';

import {
  canConfirmSession,
  useCalibrationSessionsQuery,
  useDeleteCalibrationSessionMutation,
  type CalibrationSessionResponse,
} from '../api/calibration';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleSelect } from './kpi/CycleSelect';
import { SessionStatusBadge } from './calibration/SessionStatusBadge';
import { SessionTransitionMenu } from './calibration/SessionTransitionMenu';
import { SessionFormModal } from './calibration/SessionFormModal';
import { ConfirmSessionModal } from './calibration/ConfirmSessionModal';
import { mapCalibrationErrorToMessage } from './calibration/errorMapping';

export function HrCalibrationSessionsPage(): React.ReactNode {
  const t = useT();
  const [cycleId, setCycleId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const sessionsQuery = useCalibrationSessionsQuery(cycleId);
  const sessions = sessionsQuery.data ?? [];

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.calibration.page.title}
        description={t.calibration.page.description}
      />
      <SectionCard>
        <Stack>
          <Group justify="space-between" align="end">
            <CycleSelect value={cycleId} onChange={setCycleId} />
            {cycleId && (
              <Button
                leftSection={<IconPlus size={16} />}
                onClick={() => setCreateOpen(true)}
              >
                {t.calibration.page.create}
              </Button>
            )}
          </Group>

          {!cycleId ? (
            <Text c="dimmed" size="sm">
              {t.calibration.page.needCycle}
            </Text>
          ) : sessionsQuery.isError ? (
            <Text c="red">
              {t.common.message.loadError}:{' '}
              {getErrorMessage(sessionsQuery.error)}
            </Text>
          ) : sessionsQuery.isLoading ? (
            <LoadingState message={t.common.status.loading} />
          ) : sessions.length === 0 ? (
            <EmptyState
              title={t.calibration.page.empty}
              action={{
                label: t.calibration.page.create,
                onClick: () => setCreateOpen(true),
              }}
            />
          ) : (
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t.calibration.page.col.status}</Table.Th>
                  <Table.Th>{t.calibration.page.col.scheduledAt}</Table.Th>
                  <Table.Th>{t.calibration.page.col.ownerOrgUnit}</Table.Th>
                  <Table.Th>{t.calibration.page.col.participants}</Table.Th>
                  <Table.Th>{t.calibration.page.col.adjustments}</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {sessions.map((session) => (
                  <SessionRow
                    key={session.id}
                    cycleId={cycleId}
                    session={session}
                  />
                ))}
              </Table.Tbody>
            </Table>
          )}
        </Stack>
      </SectionCard>

      {cycleId && (
        <SessionFormModal
          opened={createOpen}
          onClose={() => setCreateOpen(false)}
          cycleId={cycleId}
        />
      )}
    </ErrorBoundary>
  );
}

interface RowProps {
  cycleId: string;
  session: CalibrationSessionResponse;
}

function SessionRow({ cycleId, session }: RowProps): React.ReactNode {
  const t = useT();
  const deleteMut = useDeleteCalibrationSessionMutation(cycleId);

  const [editOpen, setEditOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const participantCount = session.participantIds?.length ?? 0;
  const adjustmentCount = session.adjustmentLog?.length ?? 0;
  const editable = session.status === 'PLANNED';
  const confirmable = canConfirmSession(session.status);

  const handleDelete = (): void => {
    deleteMut.mutate(session.id, {
      onSuccess: () => {
        setDeleteOpen(false);
        notifications.show({
          color: 'green',
          message: t.common.message.deleted,
        });
      },
      onError: (err) => {
        setDeleteOpen(false);
        notifications.show({
          color: 'red',
          message: mapCalibrationErrorToMessage(err, t),
        });
      },
    });
  };

  return (
    <Table.Tr>
      <Table.Td>
        <SessionStatusBadge status={session.status} />
      </Table.Td>
      <Table.Td>
        <Text size="sm">{formatDateTime(session.scheduledAt)}</Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm" ff="monospace" c={session.ownerOrgUnitId ? undefined : 'dimmed'}>
          {session.ownerOrgUnitId ?? t.calibration.page.companyWide}
        </Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm">{participantCount}</Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm">{adjustmentCount}</Text>
      </Table.Td>
      <Table.Td>
        <Group gap={4} justify="flex-end">
          <SessionTransitionMenu cycleId={cycleId} session={session} />
          {confirmable && (
            <Button
              size="xs"
              variant="light"
              color="teal"
              onClick={() => setConfirmOpen(true)}
            >
              {t.calibration.action.confirm}
            </Button>
          )}
          <Menu shadow="md" position="bottom-end" withinPortal>
            <Menu.Target>
              <ActionIcon variant="subtle" aria-label="more">
                <IconDotsVertical size={16} />
              </ActionIcon>
            </Menu.Target>
            <Menu.Dropdown>
              <Menu.Item
                disabled={!editable}
                onClick={() => setEditOpen(true)}
              >
                {t.calibration.action.edit}
              </Menu.Item>
              <Menu.Item
                color="red"
                disabled={!editable}
                onClick={() => setDeleteOpen(true)}
              >
                {t.calibration.action.delete}
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </Group>

        <SessionFormModal
          opened={editOpen}
          onClose={() => setEditOpen(false)}
          cycleId={cycleId}
          session={session}
        />
        <ConfirmSessionModal
          opened={confirmOpen}
          onClose={() => setConfirmOpen(false)}
          cycleId={cycleId}
          session={session}
        />
        <Modal
          opened={deleteOpen}
          onClose={() => setDeleteOpen(false)}
          title={t.calibration.action.delete}
          centered
          size="sm"
        >
          <Stack>
            <Text size="sm">{t.calibration.page.confirmDelete}</Text>
            <Group justify="flex-end">
              <Button
                variant="default"
                onClick={() => setDeleteOpen(false)}
                disabled={deleteMut.isPending}
              >
                {t.common.action.cancel}
              </Button>
              <Button
                color="red"
                onClick={handleDelete}
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

/** ISO timestamptz → 표시 문자열. null → '—'. */
function formatDateTime(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}

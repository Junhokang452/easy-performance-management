/**
 * CyclesPage — HR > 평가 사이클 관리 페이지 (P0-S1 슬라이스).
 *
 * SoT: `_workspace/evaluation_research_2026-06-11/decisions_2026-06-11.md`
 *
 * 기능:
 * - 사이클 목록 Table (이름·유형·기간·상태·정책 여부·Actions)
 * - 사이클 생성 모달 (선택적 Policy 동시 생성)
 * - 사이클 편집 모달 (PATCH 부분 수정)
 * - 상태 전이 메뉴 (현재 상태 → 가능한 다음 상태만)
 * - 정책 편집 모달 (없으면 신규, 있으면 prefill 후 PUT)
 * - 사이클 삭제 (PLANNED 단계만 BE 허용; FE 는 확인 후 삭제 요청)
 *
 * STD-FE 5 정합:
 * - LAZY: App.tsx 에서 lazy import
 * - RQ: useQuery / useMutation 만 (useState fetch 금지)
 * - STRICT: tsconfig strict 통과
 * - ERROR-BOUNDARY: PageBoundary 상위에서 wrap
 * - NEST: Menu / Modal / Button 중첩 패턴 — react 19 nested-button 위반 없음
 */
import { useState } from 'react';
import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Group,
  Menu,
  Modal,
  Stack,
  Table,
  Text,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconChevronDown,
  IconDotsVertical,
  IconPlus,
} from '@tabler/icons-react';
import {
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';

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

export function CyclesPage(): React.ReactNode {
  const t = useT();
  const { data, isLoading, isError, error } = useCyclesQuery();

  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<CycleResponse | null>(null);
  const [policyTarget, setPolicyTarget] = useState<CycleResponse | null>(null);

  const rows = data?.content ?? [];

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
          <Alert
            color="yellow"
            variant="light"
            p={6}
            style={{ display: 'inline-block' }}
          >
            <Text
              size="xs"
              style={{ cursor: 'pointer', textDecoration: 'underline' }}
              onClick={onPolicy}
            >
              {t.cycles.policy.notSet}
            </Text>
          </Alert>
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

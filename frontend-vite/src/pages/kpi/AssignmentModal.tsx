/**
 * AssignmentModal — KPI 노드별 배정 관리 모달 (매니저 트리 화면).
 *
 * - GET /kpi-nodes/{nodeId}/assignments
 * - POST 배정 추가 ({employeeId, weight?, targetOverride?})
 * - PATCH 배정 수정 (weight/targetOverride)
 * - DELETE 배정 삭제 (인라인 confirm)
 *
 * 계약 §3: (node×employee) 중복 = E9804924 (BE 차단). weight 비우면 node.weight 사용(개인 override).
 */
import { useEffect, useState } from 'react';
import {
  ActionIcon,
  Alert,
  Button,
  Center,
  Group,
  Loader,
  Modal,
  NumberInput,
  Stack,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconTrash } from '@tabler/icons-react';

import {
  useCreateAssignmentMutation,
  useDeleteAssignmentMutation,
  useNodeAssignmentsQuery,
  type KpiNodeResponse,
} from '../../api/kpi';
import { useT } from '../../i18n';
import { getErrorMessage } from '../../api/error';
import { mapKpiErrorToMessage } from './errorMapping';

interface Props {
  opened: boolean;
  onClose: () => void;
  node: KpiNodeResponse;
  treeId: string;
}

export function AssignmentModal({
  opened,
  onClose,
  node,
  treeId,
}: Props): React.ReactNode {
  const t = useT();
  const { data, isLoading, isError, error } = useNodeAssignmentsQuery(
    opened ? node.id : null,
  );
  const createMut = useCreateAssignmentMutation(node.id, treeId);
  const deleteMut = useDeleteAssignmentMutation(node.id, treeId);

  const [employeeId, setEmployeeId] = useState('');
  const [weight, setWeight] = useState<number | string>('');
  const [targetOverride, setTargetOverride] = useState<number | string>('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [deleteId, setDeleteId] = useState<string | null>(null);

  useEffect(() => {
    if (opened) {
      setEmployeeId('');
      setWeight('');
      setTargetOverride('');
      setErrorMessage(null);
      setDeleteId(null);
    }
  }, [opened]);

  const rows = data ?? [];

  const handleAdd = (): void => {
    setErrorMessage(null);
    if (!employeeId.trim()) {
      setErrorMessage(t.error.unknown);
      return;
    }
    const weightNum =
      weight === '' || weight == null
        ? null
        : typeof weight === 'number'
          ? weight
          : Number(weight);
    if (weightNum != null && (!Number.isFinite(weightNum) || weightNum <= 0 || weightNum > 1)) {
      setErrorMessage(t.error.E9804237);
      return;
    }
    const targetNum =
      targetOverride === '' || targetOverride == null
        ? null
        : typeof targetOverride === 'number'
          ? targetOverride
          : Number(targetOverride);

    createMut.mutate(
      {
        employeeId: employeeId.trim(),
        weight: weightNum,
        targetOverride: targetNum,
      },
      {
        onSuccess: () => {
          notifications.show({
            color: 'green',
            message: t.common.message.created,
          });
          setEmployeeId('');
          setWeight('');
          setTargetOverride('');
        },
        onError: (err) => setErrorMessage(mapKpiErrorToMessage(err, t)),
      },
    );
  };

  const confirmDelete = (): void => {
    if (!deleteId) return;
    deleteMut.mutate(deleteId, {
      onSuccess: () => {
        setDeleteId(null);
        notifications.show({
          color: 'green',
          message: t.common.message.deleted,
        });
      },
      onError: (err) => {
        setDeleteId(null);
        notifications.show({
          color: 'red',
          message: mapKpiErrorToMessage(err, t),
        });
      },
    });
  };

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={`${t.kpi.assignment.title} — ${node.label}`}
      size="xl"
      centered
    >
      <Stack>
        {isError ? (
          <Text c="red">
            {t.common.message.loadError}: {getErrorMessage(error)}
          </Text>
        ) : isLoading ? (
          <Center mih={100}>
            <Loader />
          </Center>
        ) : rows.length === 0 ? (
          <Text c="dimmed" size="sm">
            {t.kpi.assignment.empty}
          </Text>
        ) : (
          <Table striped highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{t.kpi.assignment.employeeId}</Table.Th>
                <Table.Th>{t.kpi.assignment.weight}</Table.Th>
                <Table.Th>{t.kpi.assignment.targetOverride}</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {rows.map((row) => (
                <Table.Tr key={row.id}>
                  <Table.Td>
                    <Text size="sm" ff="monospace">
                      {row.employeeId}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{row.weight ?? '—'}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{row.targetOverride ?? '—'}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Group justify="flex-end">
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        aria-label={t.common.action.delete}
                        onClick={() => setDeleteId(row.id)}
                      >
                        <IconTrash size={16} />
                      </ActionIcon>
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}

        <Stack gap="xs">
          <Text size="sm" fw={500}>
            {t.kpi.assignment.add}
          </Text>
          <TextInput
            label={t.kpi.assignment.employeeId}
            value={employeeId}
            onChange={(e) => setEmployeeId(e.currentTarget.value)}
          />
          <Group grow align="end">
            <NumberInput
              label={t.kpi.assignment.weight}
              description={t.kpi.assignment.weightOverrideHint}
              value={weight}
              onChange={setWeight}
              min={0}
              max={1}
              step={0.05}
              decimalScale={4}
            />
            <NumberInput
              label={t.kpi.assignment.targetOverride}
              value={targetOverride}
              onChange={setTargetOverride}
              decimalScale={4}
            />
          </Group>

          {errorMessage && (
            <Alert color="red" variant="light">
              {errorMessage}
            </Alert>
          )}

          <Group justify="flex-end">
            <Button onClick={handleAdd} loading={createMut.isPending}>
              {t.kpi.assignment.add}
            </Button>
          </Group>
        </Stack>
      </Stack>

      <Modal
        opened={Boolean(deleteId)}
        onClose={() => setDeleteId(null)}
        title={t.common.action.delete}
        centered
        size="sm"
      >
        <Stack>
          <Text size="sm">{t.kpi.confirmDeleteAssignment}</Text>
          <Group justify="flex-end">
            <Button
              variant="default"
              onClick={() => setDeleteId(null)}
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
    </Modal>
  );
}

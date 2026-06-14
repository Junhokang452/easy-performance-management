/**
 * ManagerKpiTreePage (#13) — KPI 트리 (매니저) (`/manager/kpi-tree`).
 *
 * cycle Select → tree 목록·생성 → 선택 트리 렌더(부모-자식 들여쓰기, KpiNodeTree editable) +
 * per-parent 가중치 합 뱃지 + node CRUD 모달 + node 별 배정 관리 모달.
 * 드래그·드롭 cascade 재배치는 P1 보류.
 *
 * STD-FE 5 정합.
 */
import { useEffect, useState } from 'react';
import {
  ActionIcon,
  Alert,
  Button,
  Center,
  Group,
  Loader,
  Menu,
  Modal,
  Stack,
  Text,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconDotsVertical,
  IconPlus,
} from '@tabler/icons-react';
import { PerformanceSelectableSurface } from '@easy/ui-components/performance';
import {
  PageHeader,
  SectionCard,
  EmptyState,
  ErrorBoundary,
} from '@easy/ui-components';

import {
  useDeleteKpiNodeMutation,
  useDeleteKpiTreeMutation,
  useKpiTreeDetailQuery,
  useKpiTreesQuery,
  type KpiNodeResponse,
  type KpiTreeResponse,
} from '../api/kpi';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleSelect } from './kpi/CycleSelect';
import { KpiNodeTree } from './kpi/KpiNodeTree';
import { NodeFormModal, type NodeFormMode } from './kpi/NodeFormModal';
import { AssignmentModal } from './kpi/AssignmentModal';
import { TreeFormModal, type TreeFormMode } from './kpi/TreeFormModal';
import { mapKpiErrorToMessage } from './kpi/errorMapping';

export function ManagerKpiTreePage(): React.ReactNode {
  const t = useT();
  const [cycleId, setCycleId] = useState<string | null>(null);
  const [selectedTreeId, setSelectedTreeId] = useState<string | null>(null);

  const treesQuery = useKpiTreesQuery(cycleId);
  const trees = treesQuery.data ?? [];

  // cycle 변경 시 선택 트리 초기화.
  useEffect(() => {
    setSelectedTreeId(null);
  }, [cycleId]);

  const [treeFormMode, setTreeFormMode] = useState<TreeFormMode | null>(null);

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.kpi.manager.title}
        description={t.kpi.manager.description}
      />
      <SectionCard>
        <Stack>
          <CycleSelect value={cycleId} onChange={setCycleId} />

          {cycleId && (
            <Group justify="space-between" align="center">
              <Text size="sm" fw={500}>
                {t.kpi.manager.treeList}
              </Text>
              <Button
                size="xs"
                leftSection={<IconPlus size={14} />}
                onClick={() => setTreeFormMode({ kind: 'create' })}
              >
                {t.kpi.manager.createTree}
              </Button>
            </Group>
          )}

          {cycleId && treesQuery.isError && (
            <Text c="red">
              {t.common.message.loadError}:{' '}
              {getErrorMessage(treesQuery.error)}
            </Text>
          )}
          {cycleId && treesQuery.isLoading && (
            <Center mih={80}>
              <Loader />
            </Center>
          )}
          {cycleId &&
            !treesQuery.isLoading &&
            !treesQuery.isError &&
            trees.length === 0 && (
              <EmptyState
                title={t.kpi.manager.empty}
                action={{
                  label: t.kpi.manager.createTree,
                  onClick: () => setTreeFormMode({ kind: 'create' }),
                }}
              />
            )}

          {trees.length > 0 && (
            <Group gap="xs" wrap="wrap">
              {trees.map((tree) => (
                <TreeChip
                  key={tree.id}
                  tree={tree}
                  active={tree.id === selectedTreeId}
                  cycleId={cycleId as string}
                  onSelect={() => setSelectedTreeId(tree.id)}
                  onEdit={() => setTreeFormMode({ kind: 'edit', tree })}
                  onDeleted={() => {
                    if (selectedTreeId === tree.id) setSelectedTreeId(null);
                  }}
                />
              ))}
            </Group>
          )}
        </Stack>
      </SectionCard>

      {selectedTreeId && (
        <SectionCard>
          <TreeDetail treeId={selectedTreeId} />
        </SectionCard>
      )}

      {cycleId && treeFormMode && (
        <TreeFormModal
          opened={Boolean(treeFormMode)}
          onClose={() => setTreeFormMode(null)}
          cycleId={cycleId}
          mode={treeFormMode}
        />
      )}
    </ErrorBoundary>
  );
}

interface TreeChipProps {
  tree: KpiTreeResponse;
  active: boolean;
  cycleId: string;
  onSelect: () => void;
  onEdit: () => void;
  onDeleted: () => void;
}

function TreeChip({
  tree,
  active,
  cycleId,
  onSelect,
  onEdit,
  onDeleted,
}: TreeChipProps): React.ReactNode {
  const t = useT();
  const deleteMut = useDeleteKpiTreeMutation(cycleId);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const handleDelete = (): void => {
    deleteMut.mutate(tree.id, {
      onSuccess: () => {
        setConfirmDelete(false);
        onDeleted();
        notifications.show({
          color: 'green',
          message: t.common.message.deleted,
        });
      },
      onError: (err) => {
        setConfirmDelete(false);
        notifications.show({
          color: 'red',
          message: mapKpiErrorToMessage(err, t),
        });
      },
    });
  };

  return (
    <PerformanceSelectableSurface active={active}>
      <Group gap="xs" wrap="nowrap">
        <Stack gap={0} onClick={onSelect} miw={0}>
          <Text size="sm" fw={active ? 700 : 500} truncate>
            {tree.name}
          </Text>
          <Text size="xs" c="dimmed">
            {t.kpi.level[tree.level]}
            {tree.bscEnabled ? ' · BSC' : ''}
          </Text>
        </Stack>
        <Menu shadow="md" position="bottom-end" withinPortal>
          <Menu.Target>
            <ActionIcon variant="subtle" aria-label="more">
              <IconDotsVertical size={16} />
            </ActionIcon>
          </Menu.Target>
          <Menu.Dropdown>
            <Menu.Item onClick={onEdit}>{t.kpi.manager.editTree}</Menu.Item>
            <Menu.Item color="red" onClick={() => setConfirmDelete(true)}>
              {t.kpi.manager.deleteTree}
            </Menu.Item>
          </Menu.Dropdown>
        </Menu>
      </Group>

      <Modal
        opened={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title={t.kpi.manager.deleteTree}
        centered
        size="sm"
      >
        <Stack>
          <Text size="sm">{t.kpi.confirmDeleteTree}</Text>
          <Group justify="flex-end">
            <Button
              variant="default"
              onClick={() => setConfirmDelete(false)}
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
    </PerformanceSelectableSurface>
  );
}

interface TreeDetailProps {
  treeId: string;
}

function TreeDetail({ treeId }: TreeDetailProps): React.ReactNode {
  const t = useT();
  const { data, isLoading, isError, error } = useKpiTreeDetailQuery(treeId);

  const [nodeFormMode, setNodeFormMode] = useState<NodeFormMode | null>(null);
  const [assignmentNode, setAssignmentNode] = useState<KpiNodeResponse | null>(
    null,
  );
  const [deleteNode, setDeleteNode] = useState<KpiNodeResponse | null>(null);

  const deleteNodeMut = useDeleteKpiNodeMutation(treeId);

  if (isError) {
    return (
      <Text c="red">
        {t.common.message.loadError}: {getErrorMessage(error)}
      </Text>
    );
  }
  if (isLoading || !data) {
    return (
      <Center mih={100}>
        <Loader />
      </Center>
    );
  }

  const handleConfirmDeleteNode = (): void => {
    if (!deleteNode) return;
    deleteNodeMut.mutate(deleteNode.id, {
      onSuccess: () => {
        setDeleteNode(null);
        notifications.show({
          color: 'green',
          message: t.common.message.deleted,
        });
      },
      onError: (err) => {
        setDeleteNode(null);
        notifications.show({
          color: 'red',
          message: mapKpiErrorToMessage(err, t),
        });
      },
    });
  };

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Stack gap={0}>
          <Text fw={600}>{data.name}</Text>
          <Text size="xs" c="dimmed">
            {t.kpi.level[data.level]}
            {data.bscEnabled ? ' · BSC' : ''}
          </Text>
        </Stack>
        <Button
          size="xs"
          variant="light"
          leftSection={<IconPlus size={14} />}
          onClick={() => setNodeFormMode({ kind: 'createRoot' })}
        >
          {t.kpi.manager.addRootNode}
        </Button>
      </Group>

      {data.nodes.length === 0 ? (
        <Alert color="gray" variant="light">
          {t.kpi.manager.emptyTree}
        </Alert>
      ) : (
        <KpiNodeTree
          nodes={data.nodes}
          actions={{
            onAddChild: (parent) =>
              setNodeFormMode({ kind: 'createChild', parent }),
            onEdit: (node) => setNodeFormMode({ kind: 'edit', node }),
            onDelete: (node) => setDeleteNode(node),
            onManageAssignments: (node) => setAssignmentNode(node),
          }}
        />
      )}

      {nodeFormMode && (
        <NodeFormModal
          opened={Boolean(nodeFormMode)}
          onClose={() => setNodeFormMode(null)}
          treeId={treeId}
          mode={nodeFormMode}
        />
      )}
      {assignmentNode && (
        <AssignmentModal
          opened={Boolean(assignmentNode)}
          onClose={() => setAssignmentNode(null)}
          node={assignmentNode}
          treeId={treeId}
        />
      )}
      <Modal
        opened={Boolean(deleteNode)}
        onClose={() => setDeleteNode(null)}
        title={t.kpi.manager.deleteNode}
        centered
        size="sm"
      >
        <Stack>
          <Text size="sm">{t.kpi.confirmDeleteNode}</Text>
          <Group justify="flex-end">
            <Button
              variant="default"
              onClick={() => setDeleteNode(null)}
              disabled={deleteNodeMut.isPending}
            >
              {t.common.action.cancel}
            </Button>
            <Button
              color="red"
              onClick={handleConfirmDeleteNode}
              loading={deleteNodeMut.isPending}
            >
              {t.common.action.delete}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}

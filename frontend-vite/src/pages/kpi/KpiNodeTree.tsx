/**
 * KpiNodeTree — KPI 트리 렌더 (매니저 = 편집 가능 / 본부 = readOnly).
 *
 * - flat `nodes` 배열을 buildTreeNodeViews 로 부모-자식 들여쓰기 순서로 평탄화 (계약: 클라이언트 조립).
 * - per-parent 가중치 합 뱃지 = node.childWeightSum / node.childWeightComplete
 *   (자식이 있는 노드만 표시. complete=100% / incomplete<100% / exceeded>100%).
 * - readOnly=false 일 때만 노드별 액션(하위 추가·수정·삭제·배정 관리) 노출.
 *
 * 파생값(weight·childWeightSum 등)은 BE 응답 그대로 표시 — FE 재계산 금지.
 */
import {
  ActionIcon,
  Badge,
  Group,
  Menu,
  Stack,
  Text,
} from '@mantine/core';
import {
  IconDotsVertical,
  IconPlus,
  IconUsers,
} from '@tabler/icons-react';

import {
  buildTreeNodeViews,
  formatWeight,
  type KpiNodeResponse,
} from '../../api/kpi';
import { useT } from '../../i18n';

export interface KpiNodeActions {
  onAddChild: (parent: KpiNodeResponse) => void;
  onEdit: (node: KpiNodeResponse) => void;
  onDelete: (node: KpiNodeResponse) => void;
  onManageAssignments: (node: KpiNodeResponse) => void;
}

interface Props {
  nodes: KpiNodeResponse[];
  readOnly?: boolean;
  actions?: KpiNodeActions;
}

const INDENT_PX = 24;

export function KpiNodeTree({
  nodes,
  readOnly = false,
  actions,
}: Props): React.ReactNode {
  const t = useT();
  const views = buildTreeNodeViews(nodes);

  // 자식 보유 여부 — 가중치 합 뱃지를 부모(자식 있는 노드)에만 표시.
  const hasChildren = new Set<string>();
  for (const n of nodes) {
    if (n.parentId) hasChildren.add(n.parentId);
  }

  return (
    <Stack gap={4}>
      {views.map(({ node, depth }) => (
        <Group
          key={node.id}
          gap="sm"
          wrap="nowrap"
          justify="space-between"
          style={{
            paddingLeft: depth * INDENT_PX,
            borderLeft: depth > 0 ? '2px solid var(--mantine-color-gray-3)' : undefined,
            paddingTop: 4,
            paddingBottom: 4,
          }}
        >
          <Group gap="sm" wrap="nowrap" style={{ minWidth: 0 }}>
            <Text size="sm" fw={depth === 0 ? 600 : 500} truncate>
              {node.label}
            </Text>
            <Badge size="sm" variant="light" color="blue">
              {formatWeight(node.weight)}
            </Badge>
            {node.bscPerspective && (
              <Badge size="sm" variant="outline" color="grape">
                {t.kpi.bscPerspective[node.bscPerspective]}
              </Badge>
            )}
            {node.target != null && (
              <Text size="xs" c="dimmed" truncate>
                {t.kpi.node.target}: {node.target}
                {node.unit ? ` ${node.unit}` : ''}
              </Text>
            )}
            {node.assignmentCount > 0 && (
              <Badge size="sm" variant="dot" color="teal">
                {t.kpi.node.assignmentCount}: {node.assignmentCount}
              </Badge>
            )}
          </Group>

          <Group gap="xs" wrap="nowrap">
            {hasChildren.has(node.id) && (
              <ChildWeightBadge
                sum={node.childWeightSum}
                complete={node.childWeightComplete}
              />
            )}
            {!readOnly && actions && (
              <>
                <ActionIcon
                  variant="subtle"
                  aria-label={t.kpi.manager.addChild}
                  onClick={() => actions.onAddChild(node)}
                >
                  <IconPlus size={16} />
                </ActionIcon>
                <ActionIcon
                  variant="subtle"
                  aria-label={t.kpi.manager.manageAssignments}
                  onClick={() => actions.onManageAssignments(node)}
                >
                  <IconUsers size={16} />
                </ActionIcon>
                <Menu shadow="md" position="bottom-end" withinPortal>
                  <Menu.Target>
                    <ActionIcon variant="subtle" aria-label="more">
                      <IconDotsVertical size={16} />
                    </ActionIcon>
                  </Menu.Target>
                  <Menu.Dropdown>
                    <Menu.Item onClick={() => actions.onEdit(node)}>
                      {t.kpi.manager.editNode}
                    </Menu.Item>
                    <Menu.Item
                      color="red"
                      onClick={() => actions.onDelete(node)}
                    >
                      {t.kpi.manager.deleteNode}
                    </Menu.Item>
                  </Menu.Dropdown>
                </Menu>
              </>
            )}
          </Group>
        </Group>
      ))}
    </Stack>
  );
}

interface ChildWeightBadgeProps {
  sum: number;
  complete: boolean;
}

/** 자식 가중치 합 뱃지 — complete=green / incomplete=yellow / exceeded=red. */
export function ChildWeightBadge({
  sum,
  complete,
}: ChildWeightBadgeProps): React.ReactNode {
  const t = useT();
  const pct = (sum * 100).toFixed(0);
  if (complete) {
    return (
      <Badge color="green" variant="filled" size="sm">
        {t.kpi.weightBadge.complete}
      </Badge>
    );
  }
  if (sum > 1.0001) {
    return (
      <Badge color="red" variant="filled" size="sm">
        {t.kpi.weightBadge.exceeded.replace('{sum}', pct)}
      </Badge>
    );
  }
  return (
    <Badge color="yellow" variant="light" size="sm">
      {t.kpi.weightBadge.incomplete.replace('{sum}', pct)}
    </Badge>
  );
}

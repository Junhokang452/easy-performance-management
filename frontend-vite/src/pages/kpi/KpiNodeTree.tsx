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
  Menu,
} from '@easy/ui-components/mantine';
import {
  IconDotsVertical,
  IconPlus,
  IconUsers,
} from '@tabler/icons-react';
import {
  PerformanceHierarchyList,
  PerformanceWeightStatusBadge,
  type PerformanceHierarchyRow,
} from '@easy/ui-components/performance';

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

  const rows: PerformanceHierarchyRow[] = views.map(({ node, depth }) => ({
    id: node.id,
    depth,
    title: node.label,
    metadata:
      node.target == null
        ? undefined
        : `${t.kpi.node.target}: ${node.target}${node.unit ? ` ${node.unit}` : ''}`,
    badges: (
      <>
        <Badge size="sm" variant="light" color="blue">
          {formatWeight(node.weight)}
        </Badge>
        {node.bscPerspective && (
          <Badge size="sm" variant="outline" color="grape">
            {t.kpi.bscPerspective[node.bscPerspective]}
          </Badge>
        )}
        {node.assignmentCount > 0 && (
          <Badge size="sm" variant="dot" color="teal">
            {t.kpi.node.assignmentCount}: {node.assignmentCount}
          </Badge>
        )}
      </>
    ),
    status: hasChildren.has(node.id) ? (
      <ChildWeightBadge
        sum={node.childWeightSum}
        complete={node.childWeightComplete}
      />
    ) : undefined,
    actions:
      !readOnly && actions ? (
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
              <Menu.Item color="red" onClick={() => actions.onDelete(node)}>
                {t.kpi.manager.deleteNode}
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </>
      ) : undefined,
  }));

  return <PerformanceHierarchyList rows={rows} mobileSize="compact" />;
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
      <PerformanceWeightStatusBadge state="complete" label={t.kpi.weightBadge.complete} />
    );
  }
  if (sum > 1.0001) {
    return (
      <PerformanceWeightStatusBadge
        state="exceeded"
        label={t.kpi.weightBadge.exceeded.replace('{sum}', pct)}
      />
    );
  }
  return (
    <PerformanceWeightStatusBadge
      state="incomplete"
      label={t.kpi.weightBadge.incomplete.replace('{sum}', pct)}
    />
  );
}

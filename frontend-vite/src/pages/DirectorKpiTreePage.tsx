/**
 * DirectorKpiTreePage (#18) — KPI 트리 (본부) (`/director/kpi-tree`).
 *
 * read-only 트리 + BSC ON/OFF 토글 (기본값 = tree.bscEnabled).
 * - OFF: KpiNodeTree readOnly (부모-자식 들여쓰기)
 * - ON : bscPerspective 4 관점 그룹핑(+미지정 그룹) — 관점별 노드 묶음
 *
 * STD-FE 5 정합.
 */
import { useEffect, useMemo, useState } from 'react';
import { Center, Group, Stack, Switch, Text } from '@easy/ui-components/mantine';
import {
  IconChartBar,
  IconGitBranch,
  IconListTree,
  IconTargetArrow,
} from '@tabler/icons-react';
import {
  UiBadge,
  UiLoader,
  PageHeader,
  SectionCard,
  EmptyState,
  ErrorBoundary,
} from '@easy/ui-components';
import {
  PerformanceGroupedListGrid,
  PerformanceMetricGrid,
  PerformanceProgressSummary,
  PerformanceSelectableSurface,
  formatPerformanceRatioPercent,
  formatPerformanceRatioText,
} from '@easy/ui-components/performance';

import {
  ALL_BSC_PERSPECTIVES,
  formatWeight,
  useKpiTreeDetailQuery,
  useKpiTreesQuery,
  type BscPerspective,
  type KpiNodeResponse,
  type KpiTreeResponse,
} from '../api/kpi';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleSelect } from './kpi/CycleSelect';
import { KpiNodeTree } from './kpi/KpiNodeTree';

export function DirectorKpiTreePage(): React.ReactNode {
  const t = useT();
  const [cycleId, setCycleId] = useState<string | null>(null);
  const [selectedTreeId, setSelectedTreeId] = useState<string | null>(null);

  const treesQuery = useKpiTreesQuery(cycleId);
  const trees = treesQuery.data ?? [];
  const portfolioStats = useMemo(() => buildTreePortfolioStats(trees), [trees]);

  useEffect(() => {
    setSelectedTreeId(null);
  }, [cycleId]);

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.kpi.director.title}
        description={t.kpi.director.description}
      />
      <SectionCard>
        <Stack>
          <CycleSelect value={cycleId} onChange={setCycleId} />

          {cycleId && treesQuery.isLoading && (
            <Center mih={80}>
              <UiLoader />
            </Center>
          )}
          {cycleId && treesQuery.isError && (
            <Text c="red">
              {t.common.message.loadError}:{' '}
              {getErrorMessage(treesQuery.error)}
            </Text>
          )}
          {cycleId &&
            !treesQuery.isLoading &&
            !treesQuery.isError &&
            trees.length === 0 && (
              <EmptyState title={t.kpi.director.empty} />
            )}

          {trees.length > 0 && (
            <Stack>
              <TreePortfolioOverview stats={portfolioStats} />
              <Group gap="xs" wrap="wrap">
                {trees.map((tree) => (
                  <PerformanceSelectableSurface
                    key={tree.id}
                    active={tree.id === selectedTreeId}
                    onClick={() => setSelectedTreeId(tree.id)}
                  >
                    <Text size="sm" fw={tree.id === selectedTreeId ? 700 : 500}>
                      {tree.name}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {t.kpi.level[tree.level]}
                      {tree.bscEnabled ? ' · BSC' : ''}
                    </Text>
                  </PerformanceSelectableSurface>
                ))}
              </Group>
            </Stack>
          )}
        </Stack>
      </SectionCard>

      {selectedTreeId && (
        <SectionCard>
          <DirectorTreeDetail treeId={selectedTreeId} />
        </SectionCard>
      )}
    </ErrorBoundary>
  );
}

interface DetailProps {
  treeId: string;
}

interface TreePortfolioStats {
  total: number;
  bscEnabled: number;
  orgScoped: number;
  levels: number;
}

function TreePortfolioOverview({
  stats,
}: {
  stats: TreePortfolioStats;
}): React.ReactNode {
  const t = useT();
  return (
    <Stack gap="sm">
      <PerformanceMetricGrid
        items={[
          {
            label: t.kpi.director.operating.trees,
            value: String(stats.total),
            description: t.kpi.director.operating.treesHint,
            icon: <IconListTree size={20} />,
            tone: 'brand',
          },
          {
            label: t.kpi.director.operating.bscCoverage,
            value: formatPerformanceRatioPercent(stats.bscEnabled, stats.total),
            description: formatPerformanceRatioText(stats.bscEnabled, stats.total),
            icon: <IconChartBar size={20} />,
            tone: stats.bscEnabled === stats.total ? 'green' : 'yellow',
          },
          {
            label: t.kpi.director.operating.orgScoped,
            value: String(stats.orgScoped),
            description: t.kpi.director.operating.orgScopedHint,
            icon: <IconGitBranch size={20} />,
            tone: 'blue',
          },
          {
            label: t.kpi.director.operating.levels,
            value: String(stats.levels),
            description: t.kpi.director.operating.levelsHint,
            icon: <IconTargetArrow size={20} />,
            tone: 'gray',
          },
        ]}
      />
      <PerformanceProgressSummary
        label={t.kpi.director.operating.bscProgress}
        value={stats.bscEnabled}
        total={stats.total}
      />
    </Stack>
  );
}

function DirectorTreeDetail({ treeId }: DetailProps): React.ReactNode {
  const t = useT();
  const { data, isLoading, isError, error } = useKpiTreeDetailQuery(treeId);

  // BSC 토글 — 트리 변경 시 tree.bscEnabled 를 기본값으로 동기화.
  const [bscView, setBscView] = useState(false);
  useEffect(() => {
    if (data) setBscView(data.bscEnabled);
  }, [data]);

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
        <UiLoader />
      </Center>
    );
  }

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Stack gap={0}>
          <Group gap={8}>
            <Text fw={600}>{data.name}</Text>
            <UiBadge color="gray" variant="light" size="sm">
              {t.kpi.director.readonly}
            </UiBadge>
          </Group>
          <Text size="xs" c="dimmed">
            {t.kpi.level[data.level]}
          </Text>
        </Stack>
        <Switch
          label={t.kpi.director.bscToggle}
          checked={bscView}
          onChange={(e) => setBscView(e.currentTarget.checked)}
        />
      </Group>

      <TreeDetailOverview nodes={data.nodes} />

      {data.nodes.length === 0 ? (
        <Text c="dimmed" size="sm">
          {t.kpi.manager.emptyTree}
        </Text>
      ) : bscView ? (
        <BscGroupedView nodes={data.nodes} />
      ) : (
        <KpiNodeTree nodes={data.nodes} readOnly />
      )}
    </Stack>
  );
}

interface TreeDetailStats {
  nodes: number;
  bscAssigned: number;
  weightComplete: number;
  assignments: number;
}

function TreeDetailOverview({
  nodes,
}: {
  nodes: KpiNodeResponse[];
}): React.ReactNode {
  const t = useT();
  const stats = useMemo(() => buildTreeDetailStats(nodes), [nodes]);
  return (
    <Stack gap="sm">
      <PerformanceMetricGrid
        items={[
          {
            label: t.kpi.director.operating.nodes,
            value: String(stats.nodes),
            description: t.kpi.director.operating.nodesHint,
            icon: <IconListTree size={20} />,
            tone: 'brand',
          },
          {
            label: t.kpi.director.operating.bscAssigned,
            value: formatPerformanceRatioPercent(stats.bscAssigned, stats.nodes),
            description: formatPerformanceRatioText(stats.bscAssigned, stats.nodes),
            icon: <IconChartBar size={20} />,
            tone: stats.bscAssigned === stats.nodes ? 'green' : 'yellow',
          },
          {
            label: t.kpi.director.operating.weightComplete,
            value: formatPerformanceRatioPercent(stats.weightComplete, stats.nodes),
            description: formatPerformanceRatioText(stats.weightComplete, stats.nodes),
            icon: <IconTargetArrow size={20} />,
            tone: stats.weightComplete === stats.nodes ? 'green' : 'yellow',
          },
          {
            label: t.kpi.director.operating.assignments,
            value: String(stats.assignments),
            description: t.kpi.director.operating.assignmentsHint,
            icon: <IconGitBranch size={20} />,
            tone: 'blue',
          },
        ]}
      />
      <PerformanceProgressSummary
        label={t.kpi.director.operating.weightProgress}
        value={stats.weightComplete}
        total={stats.nodes}
      />
    </Stack>
  );
}

interface BscGroupedViewProps {
  nodes: KpiNodeResponse[];
}

const UNASSIGNED = '__unassigned__';

function BscGroupedView({ nodes }: BscGroupedViewProps): React.ReactNode {
  const t = useT();

  // 관점별 그룹핑 — 4 관점 + 미지정.
  const groups: Array<{ key: BscPerspective | typeof UNASSIGNED; label: string }> = [
    ...ALL_BSC_PERSPECTIVES.map((p) => ({
      key: p,
      label: t.kpi.bscPerspective[p],
    })),
    { key: UNASSIGNED, label: t.kpi.bscPerspective.UNASSIGNED },
  ];

  const byPerspective = new Map<string, KpiNodeResponse[]>();
  for (const n of nodes) {
    const key = n.bscPerspective ?? UNASSIGNED;
    const bucket = byPerspective.get(key);
    if (bucket) bucket.push(n);
    else byPerspective.set(key, [n]);
  }

  return (
    <PerformanceGroupedListGrid
      mobileSize="comfortable"
      groups={groups.map((group) => {
        const items = (byPerspective.get(group.key) ?? []).slice().sort((a, b) =>
          a.label.localeCompare(b.label),
        );
        return {
          key: group.key,
          label: group.label,
          count: items.length,
          empty: '—',
          items: items.map((node) => ({
            key: node.id,
            label: node.label,
            trailing: (
              <UiBadge size="sm" variant="light" color="blue">
                {formatWeight(node.weight)}
              </UiBadge>
            ),
          })),
        };
      })}
    />
  );
}

function buildTreePortfolioStats(trees: KpiTreeResponse[]): TreePortfolioStats {
  return {
    total: trees.length,
    bscEnabled: trees.filter((tree) => tree.bscEnabled).length,
    orgScoped: trees.filter((tree) => tree.ownerOrgUnitId).length,
    levels: new Set(trees.map((tree) => tree.level)).size,
  };
}

function buildTreeDetailStats(nodes: KpiNodeResponse[]): TreeDetailStats {
  return {
    nodes: nodes.length,
    bscAssigned: nodes.filter((node) => node.bscPerspective).length,
    weightComplete: nodes.filter((node) => node.childWeightComplete).length,
    assignments: nodes.reduce((sum, node) => sum + node.assignmentCount, 0),
  };
}

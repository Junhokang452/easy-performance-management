/**
 * GoalAlignmentPage — 개인 KPI와 조직 KPI tree 정렬도 비교 화면.
 *
 * 기존 KPI API만 사용한다. 개인 배정의 `kpiNodeId`를 선택한 조직 트리 노드와
 * 매칭해 표시하며, 달성률·가중치·목표는 BE 응답값만 렌더한다.
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Badge,
  Button,
  Group,
  Progress,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { IconSearch, IconTargetArrow } from '@tabler/icons-react';
import {
  EmptyState,
  ErrorBoundary,
  LoadingState,
  PageHeader,
  SectionCard,
} from '@easy/ui-components';
import {
  PerformanceHierarchyList,
  PerformanceMetricGrid,
  type PerformanceHierarchyRow,
} from '@easy/ui-components/performance';

import {
  buildTreeNodeViews,
  formatAchievementRate,
  formatWeight,
  useKpiTreeDetailQuery,
  useKpiTreesQuery,
  useMyKpiAssignmentsQuery,
  type KpiNodeResponse,
  type MyKpiAssignmentResponse,
} from '../api/kpi';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleSelect } from './kpi/CycleSelect';

export function GoalAlignmentPage(): React.ReactNode {
  const t = useT();
  const [cycleId, setCycleId] = useState<string | null>(null);
  const [treeId, setTreeId] = useState<string | null>(null);
  const [employeeIdInput, setEmployeeIdInput] = useState('');
  const [applied, setApplied] = useState<{
    cycleId: string;
    employeeId: string;
  } | null>(null);

  const treesQuery = useKpiTreesQuery(cycleId);
  const trees = treesQuery.data ?? [];
  const treeDetailQuery = useKpiTreeDetailQuery(treeId);
  const myQuery = useMyKpiAssignmentsQuery(applied?.cycleId, applied?.employeeId);

  useEffect(() => {
    setTreeId(null);
    setApplied(null);
  }, [cycleId]);

  useEffect(() => {
    if (!treeId && trees.length > 0) {
      setTreeId(trees[0]?.id ?? null);
    }
  }, [treeId, trees]);

  const canLoad = Boolean(cycleId) && employeeIdInput.trim().length > 0;
  const selectedTree = trees.find((tree) => tree.id === treeId) ?? null;
  const assignments = myQuery.data ?? [];
  const nodes = treeDetailQuery.data?.nodes ?? [];
  const summary = useMemo(
    () => buildAlignmentSummary(nodes, assignments),
    [nodes, assignments],
  );

  const handleLoad = (): void => {
    if (!cycleId || !employeeIdInput.trim()) return;
    setApplied({ cycleId, employeeId: employeeIdInput.trim() });
  };

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.kpi.alignment.title}
        description={t.kpi.alignment.description}
      />
      <SectionCard>
        <Stack>
          <Group align="end" gap="md">
            <CycleSelect value={cycleId} onChange={setCycleId} />
            <Select
              label={t.kpi.alignment.tree}
              placeholder={t.kpi.alignment.treePlaceholder}
              data={trees.map((tree) => ({
                value: tree.id,
                label: `${tree.name} · ${t.kpi.level[tree.level]}`,
              }))}
              value={treeId}
              onChange={setTreeId}
              disabled={!cycleId || treesQuery.isLoading || trees.length === 0}
              miw={280}
            />
            <TextInput
              label={t.kpi.my.employeeId}
              placeholder={t.kpi.my.employeeIdPlaceholder}
              value={employeeIdInput}
              onChange={(e) => setEmployeeIdInput(e.currentTarget.value)}
              w={320}
            />
            <Button
              leftSection={<IconSearch size={16} />}
              onClick={handleLoad}
              disabled={!canLoad}
              loading={myQuery.isFetching && Boolean(applied)}
            >
              {t.kpi.my.load}
            </Button>
          </Group>
          {!cycleId ? (
            <Text size="sm" c="dimmed">
              {t.kpi.alignment.needCycle}
            </Text>
          ) : treesQuery.isError ? (
            <Text c="red">
              {t.common.message.loadError}: {getErrorMessage(treesQuery.error)}
            </Text>
          ) : treesQuery.isLoading ? (
            <LoadingState message={t.common.status.loading} />
          ) : trees.length === 0 ? (
            <EmptyState title={t.kpi.manager.empty} />
          ) : !applied ? (
            <Text size="sm" c="dimmed">
              {t.kpi.my.needInput}
            </Text>
          ) : null}
        </Stack>
      </SectionCard>

      {cycleId && applied && selectedTree ? (
        <AlignmentBody
          treeName={selectedTree.name}
          treeLoading={treeDetailQuery.isLoading}
          treeError={treeDetailQuery.error}
          myLoading={myQuery.isLoading}
          myError={myQuery.error}
          nodes={nodes}
          assignments={assignments}
          summary={summary}
        />
      ) : null}
    </ErrorBoundary>
  );
}

function AlignmentBody({
  treeName,
  treeLoading,
  treeError,
  myLoading,
  myError,
  nodes,
  assignments,
  summary,
}: {
  treeName: string;
  treeLoading: boolean;
  treeError: unknown;
  myLoading: boolean;
  myError: unknown;
  nodes: KpiNodeResponse[];
  assignments: MyKpiAssignmentResponse[];
  summary: AlignmentSummary;
}): React.ReactNode {
  const t = useT();

  if (treeError || myError) {
    return (
      <SectionCard>
        <Text c="red">
          {t.common.message.loadError}:{' '}
          {getErrorMessage(treeError ?? myError)}
        </Text>
      </SectionCard>
    );
  }

  if (treeLoading || myLoading) {
    return (
      <SectionCard>
        <LoadingState message={t.common.status.loading} />
      </SectionCard>
    );
  }

  if (nodes.length === 0 && assignments.length === 0) {
    return (
      <SectionCard>
        <EmptyState
          title={t.kpi.alignment.empty}
          description={t.kpi.alignment.emptyDescription}
        />
      </SectionCard>
    );
  }

  return (
    <Stack>
      <PerformanceMetricGrid
        mobileSize="comfortable"
        items={[
          {
            label: t.kpi.alignment.stat.aligned,
            value: `${summary.alignedCount}/${assignments.length}`,
            description: t.kpi.alignment.stat.alignedHint,
            icon: <IconTargetArrow size={18} />,
            tone: 'green',
          },
          {
            label: t.kpi.alignment.stat.coverage,
            value: formatPercent(summary.alignedCount, Math.max(assignments.length, 1)),
            description: t.kpi.alignment.stat.coverageHint.replace('{tree}', treeName),
            tone: 'brand',
          },
          {
            label: t.kpi.alignment.stat.avgAchievement,
            value: formatAverageAchievement(assignments),
            description: t.kpi.alignment.stat.avgAchievementHint,
            tone: 'blue',
          },
          {
            label: t.kpi.alignment.stat.unmatched,
            value: summary.unmatchedAssignments.length,
            description: t.kpi.alignment.stat.unmatchedHint,
            tone: summary.unmatchedAssignments.length > 0 ? 'yellow' : 'gray',
          },
        ]}
      />

      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="md">
        <SectionCard
          title={t.kpi.alignment.treePanel}
          description={treeName}
        >
          <PerformanceHierarchyList
            rows={buildHierarchyRows(nodes, summary.assignmentsByNode)}
            empty={t.kpi.manager.emptyTree}
            mobileSize="compact"
          />
        </SectionCard>

        <SectionCard
          title={t.kpi.alignment.personalPanel}
          description={t.kpi.alignment.personalPanelDescription}
        >
          <AssignmentTable assignments={assignments} nodeById={summary.nodeById} />
        </SectionCard>
      </SimpleGrid>
    </Stack>
  );
}

function AssignmentTable({
  assignments,
  nodeById,
}: {
  assignments: MyKpiAssignmentResponse[];
  nodeById: Map<string, KpiNodeResponse>;
}): React.ReactNode {
  const t = useT();

  if (assignments.length === 0) {
    return <EmptyState title={t.kpi.my.empty} description={t.kpi.my.emptyHint} />;
  }

  return (
    <Table striped highlightOnHover>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>{t.kpi.my.col.node}</Table.Th>
          <Table.Th>{t.kpi.alignment.orgMatch}</Table.Th>
          <Table.Th>{t.kpi.my.col.weight}</Table.Th>
          <Table.Th>{t.kpi.my.col.achievementRate}</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {assignments.map((assignment) => {
          const node = nodeById.get(assignment.kpiNodeId);
          return (
            <Table.Tr key={assignment.id}>
              <Table.Td>
                <Stack gap={2}>
                  <Text size="sm" fw={500}>
                    {assignment.nodeLabel}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {assignment.treeName}
                  </Text>
                </Stack>
              </Table.Td>
              <Table.Td>
                <Badge
                  variant={node ? 'light' : 'outline'}
                  color={node ? 'green' : 'yellow'}
                >
                  {node ? t.kpi.alignment.matched : t.kpi.alignment.unmatched}
                </Badge>
              </Table.Td>
              <Table.Td>
                <Text size="sm">{formatWeight(assignment.weight)}</Text>
              </Table.Td>
              <Table.Td>
                <AchievementCell rate={assignment.achievementRate} />
              </Table.Td>
            </Table.Tr>
          );
        })}
      </Table.Tbody>
    </Table>
  );
}

function AchievementCell({ rate }: { rate: number | null }): React.ReactNode {
  if (rate == null) {
    return (
      <Text size="sm" c="dimmed">
        —
      </Text>
    );
  }
  const color = rate >= 1 ? 'green' : rate >= 0.7 ? 'yellow' : 'red';
  return (
    <Group gap="xs" wrap="nowrap">
      <Progress value={Math.min(rate * 100, 100)} w={72} color={color} />
      <Badge color={color} variant="light">
        {formatAchievementRate(rate)}
      </Badge>
    </Group>
  );
}

interface AlignmentSummary {
  nodeById: Map<string, KpiNodeResponse>;
  assignmentsByNode: Map<string, MyKpiAssignmentResponse[]>;
  alignedCount: number;
  unmatchedAssignments: MyKpiAssignmentResponse[];
}

function buildAlignmentSummary(
  nodes: KpiNodeResponse[],
  assignments: MyKpiAssignmentResponse[],
): AlignmentSummary {
  const nodeById = new Map(nodes.map((node) => [node.id, node]));
  const assignmentsByNode = new Map<string, MyKpiAssignmentResponse[]>();
  const unmatchedAssignments: MyKpiAssignmentResponse[] = [];

  for (const assignment of assignments) {
    if (!nodeById.has(assignment.kpiNodeId)) {
      unmatchedAssignments.push(assignment);
      continue;
    }
    const bucket = assignmentsByNode.get(assignment.kpiNodeId);
    if (bucket) bucket.push(assignment);
    else assignmentsByNode.set(assignment.kpiNodeId, [assignment]);
  }

  return {
    nodeById,
    assignmentsByNode,
    alignedCount: assignments.length - unmatchedAssignments.length,
    unmatchedAssignments,
  };
}

function buildHierarchyRows(
  nodes: KpiNodeResponse[],
  assignmentsByNode: Map<string, MyKpiAssignmentResponse[]>,
): PerformanceHierarchyRow[] {
  return buildTreeNodeViews(nodes).map(({ node, depth }) => {
    const assignments = assignmentsByNode.get(node.id) ?? [];
    return {
      id: node.id,
      depth,
      title: node.label,
      metadata:
        node.target == null
          ? undefined
          : `${node.target}${node.unit ? ` ${node.unit}` : ''}`,
      badges: (
        <>
          <Badge size="sm" variant="light" color="blue">
            {formatWeight(node.weight)}
          </Badge>
          {node.bscPerspective ? (
            <Badge size="sm" variant="outline" color="grape">
              {node.bscPerspective}
            </Badge>
          ) : null}
        </>
      ),
      status:
        assignments.length > 0 ? (
          <Badge size="sm" variant="light" color="green">
            {assignments.length}
          </Badge>
        ) : (
          <Badge size="sm" variant="outline" color="gray">
            0
          </Badge>
        ),
    };
  });
}

function formatAverageAchievement(assignments: MyKpiAssignmentResponse[]): string {
  const rates = assignments
    .map((assignment) => assignment.achievementRate)
    .filter((rate): rate is number => rate != null);
  if (rates.length === 0) return '—';
  const avg = rates.reduce((sum, rate) => sum + rate, 0) / rates.length;
  return formatAchievementRate(avg);
}

function formatPercent(done: number, total: number): string {
  if (total <= 0) return '0%';
  return `${Math.round((done / total) * 100)}%`;
}

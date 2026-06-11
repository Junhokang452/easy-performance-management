/**
 * DirectorKpiTreePage (#18) — KPI 트리 (본부) (`/director/kpi-tree`).
 *
 * read-only 트리 + BSC ON/OFF 토글 (기본값 = tree.bscEnabled).
 * - OFF: KpiNodeTree readOnly (부모-자식 들여쓰기)
 * - ON : bscPerspective 4 관점 그룹핑(+미지정 그룹) — 관점별 노드 묶음
 *
 * STD-FE 5 정합.
 */
import { useEffect, useState } from 'react';
import {
  Badge,
  Card,
  Center,
  Group,
  Loader,
  SimpleGrid,
  Stack,
  Switch,
  Text,
} from '@mantine/core';
import {
  PageHeader,
  SectionCard,
  EmptyState,
  ErrorBoundary,
} from '@easy/ui-components';

import {
  ALL_BSC_PERSPECTIVES,
  formatWeight,
  useKpiTreeDetailQuery,
  useKpiTreesQuery,
  type BscPerspective,
  type KpiNodeResponse,
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
              <Loader />
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
            <Group gap="xs" wrap="wrap">
              {trees.map((tree) => (
                <Card
                  key={tree.id}
                  withBorder
                  padding="xs"
                  style={{
                    borderColor:
                      tree.id === selectedTreeId
                        ? 'var(--mantine-color-blue-5)'
                        : undefined,
                    cursor: 'pointer',
                  }}
                  onClick={() => setSelectedTreeId(tree.id)}
                >
                  <Text size="sm" fw={tree.id === selectedTreeId ? 700 : 500}>
                    {tree.name}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {t.kpi.level[tree.level]}
                    {tree.bscEnabled ? ' · BSC' : ''}
                  </Text>
                </Card>
              ))}
            </Group>
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
        <Loader />
      </Center>
    );
  }

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Stack gap={0}>
          <Group gap={8}>
            <Text fw={600}>{data.name}</Text>
            <Badge color="gray" variant="light" size="sm">
              {t.kpi.director.readonly}
            </Badge>
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
    <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }} spacing="md">
      {groups.map((group) => {
        const items = (byPerspective.get(group.key) ?? []).slice().sort((a, b) =>
          a.label.localeCompare(b.label),
        );
        return (
          <Card key={group.key} withBorder padding="sm">
            <Stack gap="xs">
              <Group justify="space-between">
                <Text fw={600} size="sm">
                  {group.label}
                </Text>
                <Badge size="sm" variant="light">
                  {items.length}
                </Badge>
              </Group>
              {items.length === 0 ? (
                <Text size="xs" c="dimmed">
                  —
                </Text>
              ) : (
                items.map((node) => (
                  <Group key={node.id} justify="space-between" gap="xs" wrap="nowrap">
                    <Text size="sm" truncate>
                      {node.label}
                    </Text>
                    <Badge size="sm" variant="light" color="blue">
                      {formatWeight(node.weight)}
                    </Badge>
                  </Group>
                ))
              )}
            </Stack>
          </Card>
        );
      })}
    </SimpleGrid>
  );
}

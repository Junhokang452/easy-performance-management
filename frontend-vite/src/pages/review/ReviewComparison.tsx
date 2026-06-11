/**
 * ReviewComparison — Self ↔ Manager 비교 view (매니저 평가 폼 내 탭, Wireframe 2 Tabs 정합).
 *
 * 좌우 나란히:
 * - selfComment ↔ managerComment (코멘트 비교)
 * - per-item autoScore ↔ managerScore 비교 표 (itemScore = managerScore ?? autoScore)
 *
 * kpi-items 는 매니저 폼과 동일 소스(live 계산 + 저장 managerScore merge). 점수는 BE 표시값 — 입력 폼 아님.
 */
import { Card, Grid, Group, SimpleGrid, Stack, Table, Text } from '@mantine/core';

import {
  formatScore,
  type ReviewKpiItemResponse,
  type ReviewResponse,
} from '../../api/reviews';
import { useT } from '../../i18n';

interface Props {
  review: ReviewResponse;
  items: ReviewKpiItemResponse[];
}

export function ReviewComparison({ review, items }: Props): React.ReactNode {
  const t = useT();

  return (
    <Stack>
      <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
        <CommentCard
          title={t.review.compare.self}
          comment={review.selfComment}
          color="cyan"
        />
        <CommentCard
          title={t.review.compare.manager}
          comment={review.managerComment}
          color="grape"
        />
      </SimpleGrid>

      <Text size="sm" fw={500} mt="xs">
        {t.review.compare.scoreCompare}
      </Text>
      {items.length === 0 ? (
        <Text c="dimmed" size="sm">
          {t.review.kpi.empty}
        </Text>
      ) : (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t.review.kpi.col.node}</Table.Th>
              <Table.Th>{t.review.compare.autoScore}</Table.Th>
              <Table.Th>{t.review.compare.managerScore}</Table.Th>
              <Table.Th>{t.review.compare.delta}</Table.Th>
              <Table.Th>{t.review.kpi.col.itemScore}</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {items.map((item) => (
              <Table.Tr key={item.assignmentId}>
                <Table.Td>
                  <Text size="sm" fw={500}>
                    {item.nodeLabel}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {item.treeName}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm" c="dimmed">
                    {formatScore(item.autoScore)}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm">{formatScore(item.managerScore)}</Text>
                </Table.Td>
                <Table.Td>
                  <DeltaText auto={item.autoScore} manager={item.managerScore} />
                </Table.Td>
                <Table.Td>
                  <Text size="sm" fw={500}>
                    {formatScore(item.itemScore)}
                  </Text>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      <Grid mt="xs">
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <ScoreSummary
            label={t.review.field.kpiScore}
            value={review.kpiScore}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <ScoreSummary
            label={t.review.field.finalScore}
            value={review.finalScore}
            grade={review.finalGrade}
          />
        </Grid.Col>
      </Grid>
    </Stack>
  );
}

function CommentCard({
  title,
  comment,
  color,
}: {
  title: string;
  comment: string | null;
  color: string;
}): React.ReactNode {
  const t = useT();
  return (
    <Card withBorder padding="md">
      <Group gap="xs" mb="xs">
        <Text size="sm" fw={600} c={color}>
          {title}
        </Text>
      </Group>
      {comment ? (
        <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
          {comment}
        </Text>
      ) : (
        <Text size="sm" c="dimmed">
          {t.review.compare.noComment}
        </Text>
      )}
    </Card>
  );
}

function DeltaText({
  auto,
  manager,
}: {
  auto: number | null;
  manager: number | null;
}): React.ReactNode {
  if (auto == null || manager == null) {
    return (
      <Text size="sm" c="dimmed">
        —
      </Text>
    );
  }
  const delta = Math.round((manager - auto) * 100) / 100;
  const color = delta > 0 ? 'green' : delta < 0 ? 'red' : 'dimmed';
  const sign = delta > 0 ? '+' : '';
  return (
    <Text size="sm" c={color}>
      {sign}
      {delta.toFixed(2)}
    </Text>
  );
}

function ScoreSummary({
  label,
  value,
  grade,
}: {
  label: string;
  value: number | null;
  grade?: string | null;
}): React.ReactNode {
  return (
    <Card withBorder padding="sm">
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      <Group gap="xs" align="baseline">
        <Text size="lg" fw={700}>
          {formatScore(value)}
        </Text>
        {grade && (
          <Text size="sm" fw={600} c="blue">
            {grade}
          </Text>
        )}
      </Group>
    </Card>
  );
}

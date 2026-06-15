/**
 * ReviewComparison — Self ↔ Manager 비교 view (매니저 평가 폼 내 탭, Wireframe 2 Tabs 정합).
 *
 * 좌우 나란히:
 * - selfComment ↔ managerComment (코멘트 비교)
 * - per-item autoScore ↔ managerScore 비교 표 (itemScore = managerScore ?? autoScore)
 *
 * kpi-items 는 매니저 폼과 동일 소스(live 계산 + 저장 managerScore merge). 점수는 BE 표시값 — 입력 폼 아님.
 */
import { SimpleGrid, Stack, Text } from '@easy/ui-components/mantine';
import {
  PerformanceCommentPanel,
  PerformanceScoreGrid,
} from '@easy/ui-components/performance';

import {
  formatScore,
  type ReviewKpiItemResponse,
  type ReviewResponse,
} from '../../api/reviews';
import { useT } from '../../i18n';
import { UiTable } from '@easy/ui-components';

interface Props {
  review: ReviewResponse;
  items: ReviewKpiItemResponse[];
}

export function ReviewComparison({ review, items }: Props): React.ReactNode {
  const t = useT();

  return (
    <Stack>
      <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
        <PerformanceCommentPanel
          title={t.review.compare.self}
          comment={review.selfComment}
          empty={t.review.compare.noComment}
          titleColor="cyan"
          mobileSize="comfortable"
        />
        <PerformanceCommentPanel
          title={t.review.compare.manager}
          comment={review.managerComment}
          empty={t.review.compare.noComment}
          titleColor="grape"
          mobileSize="comfortable"
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
        <UiTable striped highlightOnHover>
          <UiTable.Thead>
            <UiTable.Tr>
              <UiTable.Th>{t.review.kpi.col.node}</UiTable.Th>
              <UiTable.Th>{t.review.compare.autoScore}</UiTable.Th>
              <UiTable.Th>{t.review.compare.managerScore}</UiTable.Th>
              <UiTable.Th>{t.review.compare.delta}</UiTable.Th>
              <UiTable.Th>{t.review.kpi.col.itemScore}</UiTable.Th>
            </UiTable.Tr>
          </UiTable.Thead>
          <UiTable.Tbody>
            {items.map((item) => (
              <UiTable.Tr key={item.assignmentId}>
                <UiTable.Td>
                  <Text size="sm" fw={500}>
                    {item.nodeLabel}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {item.treeName}
                  </Text>
                </UiTable.Td>
                <UiTable.Td>
                  <Text size="sm" c="dimmed">
                    {formatScore(item.autoScore)}
                  </Text>
                </UiTable.Td>
                <UiTable.Td>
                  <Text size="sm">{formatScore(item.managerScore)}</Text>
                </UiTable.Td>
                <UiTable.Td>
                  <DeltaText auto={item.autoScore} manager={item.managerScore} />
                </UiTable.Td>
                <UiTable.Td>
                  <Text size="sm" fw={500}>
                    {formatScore(item.itemScore)}
                  </Text>
                </UiTable.Td>
              </UiTable.Tr>
            ))}
          </UiTable.Tbody>
        </UiTable>
      )}

      <PerformanceScoreGrid
        columns={{ base: 1, sm: 2 }}
        items={[
          {
            label: t.review.field.kpiScore,
            value: formatScore(review.kpiScore),
          },
          {
            label: t.review.field.finalScore,
            value: formatScore(review.finalScore),
            badge: review.finalGrade ? (
              <Text size="sm" fw={600} c="blue">
                {review.finalGrade}
              </Text>
            ) : null,
          },
        ]}
      />
    </Stack>
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

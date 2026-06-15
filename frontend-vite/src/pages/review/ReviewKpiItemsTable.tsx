/**
 * ReviewKpiItemsTable — review KPI 항목 표 (공유 컴포넌트).
 *
 * 모드:
 * - readOnly      : 자기평가 자체 점검 + 비교 view (점수 입력 비노출, autoScore/itemScore 표시만)
 * - score-input   : 매니저 채점 — per-item managerScore NumberInput(0~100) + autoScore 폴백 표시
 *
 * 계약 §6 ReviewKpiItemResponse / §5 산식. 점수는 BE 응답 표시가 원칙 —
 * score-input 의 입력값(overrides)은 매니저 폼 로컬 상태로 상위가 관리(프리뷰·제출에 사용).
 */
import { NumberInput, Table, Text } from '@easy/ui-components/mantine';

import {
  formatReviewAchievementRate,
  formatReviewWeight,
  formatScore,
  type ReviewKpiItemResponse,
} from '../../api/reviews';
import { useT } from '../../i18n';

interface ReadOnlyProps {
  mode: 'readOnly';
  items: ReviewKpiItemResponse[];
}

interface ScoreInputProps {
  mode: 'score-input';
  items: ReviewKpiItemResponse[];
  /** assignmentId → 입력 점수 (빈 값은 '' / null 은 미입력). */
  overrides: Record<string, number | string>;
  onChange: (assignmentId: string, value: number | string) => void;
  disabled?: boolean;
}

type Props = ReadOnlyProps | ScoreInputProps;

export function ReviewKpiItemsTable(props: Props): React.ReactNode {
  const t = useT();
  const { items } = props;

  if (items.length === 0) {
    return (
      <Text c="dimmed" size="sm">
        {t.review.kpi.empty}
      </Text>
    );
  }

  const scoreInput = props.mode === 'score-input';

  return (
    <Table striped highlightOnHover>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>{t.review.kpi.col.node}</Table.Th>
          <Table.Th>{t.review.kpi.col.weight}</Table.Th>
          <Table.Th>{t.review.kpi.col.target}</Table.Th>
          <Table.Th>{t.review.kpi.col.actual}</Table.Th>
          <Table.Th>{t.review.kpi.col.achievementRate}</Table.Th>
          <Table.Th>{t.review.kpi.col.autoScore}</Table.Th>
          {scoreInput ? (
            <Table.Th>{t.review.kpi.col.managerScore}</Table.Th>
          ) : (
            <Table.Th>{t.review.kpi.col.itemScore}</Table.Th>
          )}
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
              <Text size="sm">{formatReviewWeight(item.weight)}</Text>
            </Table.Td>
            <Table.Td>
              <Text size="sm">
                {item.target != null
                  ? `${item.target}${item.unit ? ` ${item.unit}` : ''}`
                  : '—'}
              </Text>
            </Table.Td>
            <Table.Td>
              <Text size="sm">
                {item.latestActualValue != null ? item.latestActualValue : '—'}
              </Text>
            </Table.Td>
            <Table.Td>
              <Text size="sm">
                {formatReviewAchievementRate(item.achievementRate)}
              </Text>
            </Table.Td>
            <Table.Td>
              <Text size="sm" c="dimmed">
                {formatScore(item.autoScore)}
              </Text>
            </Table.Td>
            {props.mode === 'score-input' ? (
              <Table.Td>
                <NumberInput
                  aria-label={t.review.kpi.col.managerScore}
                  value={props.overrides[item.assignmentId] ?? ''}
                  onChange={(v) => props.onChange(item.assignmentId, v)}
                  min={0}
                  max={100}
                  step={1}
                  decimalScale={2}
                  placeholder={formatScore(item.autoScore)}
                  disabled={props.disabled}
                  w={120}
                />
              </Table.Td>
            ) : (
              <Table.Td>
                <Text size="sm" fw={500}>
                  {formatScore(item.itemScore)}
                </Text>
              </Table.Td>
            )}
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  );
}

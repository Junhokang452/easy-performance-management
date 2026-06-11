/**
 * DistributionBars — 등급 분포 가로 막대 (현재 vs 목표 비교).
 *
 * `@mantine/charts` 미설치 → Box/Progress 수동 가로 막대.
 * 계약 §7: 현재(건수) 분포를 막대로, 목표(비율) 가 있으면 목표 마커/막대 병기.
 * UNRATED 버킷은 회색. **BE 응답 표시만** — 비율 환산(건수/총합)은 렌더용 % 계산만 허용(§7 주의).
 *
 * 듀얼 표기: 각 버킷에 건수 + 비율(%) 동시 노출. 목표가 있으면 목표 비율도 병기.
 */
import { Box, Group, Progress, Stack, Text } from '@mantine/core';

import {
  GRADE_BUCKETS,
  distributionTotal,
  formatRatio,
  type GradeBucket,
  type GradeCountWithUnratedMap,
  type GradeRatioMap,
} from '../../api/calibration';
import { useT } from '../../i18n';

const BAR_COLOR: Record<GradeBucket, string> = {
  S: 'red',
  A: 'orange',
  B: 'yellow',
  C: 'blue',
  D: 'grape',
  UNRATED: 'gray',
};

interface Props {
  /** 현재 분포 — 건수 (S~D + UNRATED). */
  current: GradeCountWithUnratedMap | null | undefined;
  /** 목표 분포 — 비율 0~1 (S~D, UNRATED 없음). null 이면 목표 막대 미표시. */
  target?: GradeRatioMap | null;
}

export function DistributionBars({ current, target }: Props): React.ReactNode {
  const t = useT();
  const total = distributionTotal(current);

  return (
    <Stack gap="xs">
      <Group justify="space-between">
        <Text size="xs" c="dimmed">
          {t.distribution.bars.current}
          {target ? ` / ${t.distribution.bars.target}` : ''}
        </Text>
        <Text size="xs" c="dimmed">
          {t.distribution.bars.totalCount.replace('{count}', String(total))}
        </Text>
      </Group>

      {GRADE_BUCKETS.map((bucket) => {
        const count = current?.[bucket] ?? 0;
        const currentRatio = total > 0 ? count / total : 0;
        // UNRATED 는 목표 비율 대상 아님.
        const targetRatio =
          bucket === 'UNRATED' ? null : (target?.[bucket] ?? null);
        return (
          <BucketRow
            key={bucket}
            bucket={bucket}
            label={
              bucket === 'UNRATED'
                ? t.calibration.grade.UNRATED
                : bucket
            }
            count={count}
            currentRatio={currentRatio}
            targetRatio={targetRatio}
          />
        );
      })}
    </Stack>
  );
}

interface BucketRowProps {
  bucket: GradeBucket;
  label: string;
  count: number;
  currentRatio: number;
  targetRatio: number | null;
}

function BucketRow({
  bucket,
  label,
  count,
  currentRatio,
  targetRatio,
}: BucketRowProps): React.ReactNode {
  const t = useT();
  return (
    <Group gap="sm" wrap="nowrap" align="center">
      <Text size="sm" fw={600} w={32} ta="center">
        {label}
      </Text>
      <Box style={{ flex: 1 }}>
        <Progress.Root size="lg" radius="sm">
          <Progress.Section
            value={currentRatio * 100}
            color={BAR_COLOR[bucket]}
          />
        </Progress.Root>
      </Box>
      <Group gap={6} w={150} justify="flex-end" wrap="nowrap">
        <Text size="sm" fw={500}>
          {count}
        </Text>
        <Text size="xs" c="dimmed">
          {formatRatio(currentRatio)}
        </Text>
        {targetRatio != null && (
          <Text size="xs" c="teal" title={t.distribution.bars.target}>
            {t.distribution.bars.targetMarker.replace(
              '{ratio}',
              formatRatio(targetRatio),
            )}
          </Text>
        )}
      </Group>
    </Group>
  );
}

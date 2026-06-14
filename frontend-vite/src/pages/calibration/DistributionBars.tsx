/**
 * DistributionBars — 등급 분포 가로 막대 (현재 vs 목표 비교).
 *
 * `@mantine/charts` 미설치 → Box/Progress 수동 가로 막대.
 * 계약 §7: 현재(건수) 분포를 막대로, 목표(비율) 가 있으면 목표 마커/막대 병기.
 * UNRATED 버킷은 회색. **BE 응답 표시만** — 비율 환산(건수/총합)은 렌더용 % 계산만 허용(§7 주의).
 *
 * 듀얼 표기: 각 버킷에 건수 + 비율(%) 동시 노출. 목표가 있으면 목표 비율도 병기.
 */
import { PerformanceDistributionBars } from '@easy/ui-components/performance';

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
    <PerformanceDistributionBars
      label={`${t.distribution.bars.current}${target ? ` / ${t.distribution.bars.target}` : ''}`}
      totalLabel={t.distribution.bars.totalCount.replace('{count}', String(total))}
      highlightLabel={t.distribution.bars.target}
      mobileSize="compact"
      buckets={GRADE_BUCKETS.map((bucket) => {
        const count = current?.[bucket] ?? 0;
        const currentRatio = total > 0 ? count / total : 0;
        const targetRatio =
          bucket === 'UNRATED' ? null : (target?.[bucket] ?? null);
        return {
          key: bucket,
          label: bucket === 'UNRATED' ? t.calibration.grade.UNRATED : bucket,
          count,
          ratio: currentRatio,
          ratioLabel: formatRatio(currentRatio),
          color: BAR_COLOR[bucket],
          targetLabel:
            targetRatio == null
              ? undefined
              : t.distribution.bars.targetMarker.replace(
                  '{ratio}',
                  formatRatio(targetRatio),
                ),
        };
      })}
    />
  );
}

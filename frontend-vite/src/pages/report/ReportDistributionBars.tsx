/**
 * ReportDistributionBars — 본인 리포트의 전사 등급 분포 가로 막대 (비율 전용).
 *
 * 계약 §5/§7 (#7): content.distribution 은 BE 동결 스냅샷의 **비율(0~1)** — 건수 비노출 (E9 소규모 역추적 방지).
 * 본인 등급(myGrade) 버킷을 강조. **BE 응답 표시만** — 비율 → % 환산 렌더만 허용(재계산 금지).
 *
 * 기존 calibration/DistributionBars 는 current=건수 / target=비율 듀얼 표기라 키 단위가 달라 그대로 재사용 불가
 * (본 슬라이스는 distribution 이 비율 단일 + 본인 강조 + UNRATED 없음) → 비율 전용 경량 변형 (P0_S5_FRONTEND 박제).
 */
import { Text } from '@mantine/core';
import { PerformanceDistributionBars } from '@easy/ui-components/performance';

import { formatReportRatio, type ReportDistribution } from '../../api/reports';
import { useT } from '../../i18n';

const SABCD: Array<'S' | 'A' | 'B' | 'C' | 'D'> = ['S', 'A', 'B', 'C', 'D'];

const BAR_COLOR: Record<'S' | 'A' | 'B' | 'C' | 'D', string> = {
  S: 'red',
  A: 'orange',
  B: 'yellow',
  C: 'blue',
  D: 'grape',
};

interface Props {
  /** 전사 분포 — 비율 0~1 (S~D). null 이면 미표시(상위에서 가드). */
  distribution: ReportDistribution | null | undefined;
  /** 본인 최종 등급 — 일치 버킷 강조. */
  myGrade?: string | null;
}

export function ReportDistributionBars({
  distribution,
  myGrade,
}: Props): React.ReactNode {
  const t = useT();
  if (!distribution) {
    return (
      <Text size="sm" c="dimmed">
        {t.report.card.distributionEmpty}
      </Text>
    );
  }

  return (
    <PerformanceDistributionBars
      label={t.report.card.distributionHint}
      highlightLabel={t.report.card.distributionMine}
      mobileSize="compact"
      buckets={SABCD.map((bucket) => {
        const ratio = distribution[bucket] ?? 0;
        const isMine = myGrade != null && myGrade === bucket;
        return {
          key: bucket,
          label: bucket,
          color: BAR_COLOR[bucket],
          ratio,
          ratioLabel: formatReportRatio(ratio),
          highlighted: isMine,
        };
      })}
    />
  );
}

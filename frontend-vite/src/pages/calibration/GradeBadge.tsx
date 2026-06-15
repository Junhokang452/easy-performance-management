/**
 * GradeBadge — 등급(S~D + UNRATED) Mantine Badge.
 *
 * 색상 매핑 (Mantine v9): S=red(최상위 강조) / A=orange / B=yellow / C=gray / D=dark / UNRATED=gray light.
 * 등급 라벨은 그대로 노출(S/A/B/C/D), UNRATED 는 i18n 라벨.
 */
import { Badge } from '@easy/ui-components/mantine';

import type { GradeBucket } from '../../api/calibration';
import { useT } from '../../i18n';

const COLOR_MAP: Record<GradeBucket, string> = {
  S: 'red',
  A: 'orange',
  B: 'yellow',
  C: 'gray',
  D: 'dark',
  UNRATED: 'gray',
};

interface Props {
  /** 등급 문자열 (S/A/B/C/D/UNRATED). 알 수 없는 값은 회색으로 그대로 표기. */
  grade: string | null | undefined;
  size?: string;
}

export function GradeBadge({ grade, size = 'sm' }: Props): React.ReactNode {
  const t = useT();
  if (grade == null || grade === '') {
    return (
      <Badge color="gray" variant="outline" size={size}>
        —
      </Badge>
    );
  }
  const known = grade in COLOR_MAP ? (grade as GradeBucket) : null;
  const color = known ? COLOR_MAP[known] : 'gray';
  const label =
    known === 'UNRATED' ? t.calibration.grade.UNRATED : grade;
  return (
    <Badge color={color} variant={grade === 'UNRATED' ? 'outline' : 'filled'} size={size}>
      {label}
    </Badge>
  );
}

/**
 * ReviewStatusBadge — PerformanceReview 상태 Mantine Badge (10 상태).
 *
 * 색상 매핑 (Mantine v9, CycleStatusBadge 패턴 정합):
 *   DRAFT=gray / SELF_PENDING=cyan / SELF_SUBMITTED=blue
 *   MANAGER_PENDING=violet / MANAGER_SUBMITTED=grape / CALIBRATION=orange
 *   FINALIZED=green / APPEAL_REQUESTED=red / APPEAL_RESOLVED=teal / ARCHIVED=dark
 */
import { Badge } from '@easy/ui-components/mantine';

import type { ReviewStatus } from '../../api/reviews';
import { useT } from '../../i18n';

const COLOR_MAP: Record<ReviewStatus, string> = {
  DRAFT: 'gray',
  SELF_PENDING: 'cyan',
  SELF_SUBMITTED: 'blue',
  MANAGER_PENDING: 'violet',
  MANAGER_SUBMITTED: 'grape',
  CALIBRATION: 'orange',
  FINALIZED: 'green',
  APPEAL_REQUESTED: 'red',
  APPEAL_RESOLVED: 'teal',
  ARCHIVED: 'dark',
};

interface Props {
  status: ReviewStatus;
}

export function ReviewStatusBadge({ status }: Props): React.ReactNode {
  const t = useT();
  return (
    <Badge color={COLOR_MAP[status]} variant="light">
      {t.review.status[status]}
    </Badge>
  );
}

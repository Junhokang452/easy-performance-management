/**
 * CycleStatusBadge — 사이클 상태 Mantine Badge.
 *
 * 색상 매핑 (Mantine v9):
 *   PLANNED=gray / ACTIVE=blue / GOAL_SETTING=cyan / MID_REVIEW=indigo
 *   SELF_REVIEW=violet / MANAGER_REVIEW=grape / CALIBRATION=orange
 *   FINALIZED=green / CANCELLED=red
 */
import { Badge } from '@mantine/core';

import type { CycleStatus } from '../../api/cycles';
import { useT } from '../../i18n';

const COLOR_MAP: Record<CycleStatus, string> = {
  PLANNED: 'gray',
  ACTIVE: 'blue',
  GOAL_SETTING: 'cyan',
  MID_REVIEW: 'indigo',
  SELF_REVIEW: 'violet',
  MANAGER_REVIEW: 'grape',
  CALIBRATION: 'orange',
  FINALIZED: 'green',
  CANCELLED: 'red',
};

interface Props {
  status: CycleStatus;
}

export function CycleStatusBadge({ status }: Props): React.ReactNode {
  const t = useT();
  return (
    <Badge color={COLOR_MAP[status]} variant="light">
      {t.cycles.status[status]}
    </Badge>
  );
}

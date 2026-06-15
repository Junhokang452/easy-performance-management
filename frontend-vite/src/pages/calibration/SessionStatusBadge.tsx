/**
 * SessionStatusBadge — CalibrationSession 상태 Mantine Badge (5 상태).
 *
 * 색상 매핑 (Mantine v9, ReviewStatusBadge 패턴 정합):
 *   PLANNED=gray / IN_SESSION=blue / ADJUSTED=violet / CONFIRMED=teal / CLOSED=green
 */
import { Badge } from '@easy/ui-components/mantine';

import type { CalibrationStatus } from '../../api/calibration';
import { useT } from '../../i18n';

const COLOR_MAP: Record<CalibrationStatus, string> = {
  PLANNED: 'gray',
  IN_SESSION: 'blue',
  ADJUSTED: 'violet',
  CONFIRMED: 'teal',
  CLOSED: 'green',
};

interface Props {
  status: CalibrationStatus;
}

export function SessionStatusBadge({ status }: Props): React.ReactNode {
  const t = useT();
  return (
    <Badge color={COLOR_MAP[status]} variant="light">
      {t.calibration.status[status]}
    </Badge>
  );
}

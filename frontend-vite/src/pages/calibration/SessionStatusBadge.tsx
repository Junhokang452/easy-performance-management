/**
 * SessionStatusBadge — CalibrationSession 상태 Mantine UiBadge (5 상태).
 *
 * 색상 매핑 (Mantine v9, ReviewStatusBadge 패턴 정합):
 *   PLANNED=gray / IN_SESSION=blue / ADJUSTED=violet / CONFIRMED=teal / CLOSED=green
 */


import type { CalibrationStatus } from '../../api/calibration';
import { useT } from '../../i18n';
import { UiBadge } from '@easy/ui-components';

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
    <UiBadge color={COLOR_MAP[status]} variant="light">
      {t.calibration.status[status]}
    </UiBadge>
  );
}

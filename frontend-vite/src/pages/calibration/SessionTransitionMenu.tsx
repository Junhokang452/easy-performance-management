/**
 * SessionTransitionMenu — CalibrationSession 상태 전이 메뉴 (ReviewTransitionMenu 패턴).
 *
 * 계약 §3: `POST /calibration-sessions/{id}/transition` 허용 전이만 노출 —
 * PLANNED→IN_SESSION + CONFIRMED→CLOSED.
 * CONFIRMED 진입(IN_SESSION/ADJUSTED→CONFIRMED)은 confirm 모달 전용이라 여기 미노출.
 * ADJUSTED 자동 승격(adjustments 첫 호출)도 메뉴 비노출.
 *
 * 전이 실패(잘못된 cycle 단계 등)는 ApiError 코드 → mapCalibrationErrorToMessage 로 표면.
 */
import { Button, Menu } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconChevronDown } from '@tabler/icons-react';

import {
  getAllowedCalibrationTransitions,
  useTransitionCalibrationSessionMutation,
  type CalibrationSessionResponse,
  type CalibrationStatus,
} from '../../api/calibration';
import { useT } from '../../i18n';
import { mapCalibrationErrorToMessage } from './errorMapping';

interface Props {
  cycleId: string;
  session: CalibrationSessionResponse;
}

export function SessionTransitionMenu({
  cycleId,
  session,
}: Props): React.ReactNode {
  const t = useT();
  const transitionMut = useTransitionCalibrationSessionMutation(
    cycleId,
    session.id,
  );
  const nextStatuses = getAllowedCalibrationTransitions(session.status);

  if (nextStatuses.length === 0) return null;

  const handleTransition = (next: CalibrationStatus): void => {
    transitionMut.mutate(
      { targetStatus: next },
      {
        onSuccess: () => {
          notifications.show({
            color: 'green',
            message: t.common.message.updated,
          });
        },
        onError: (err) => {
          notifications.show({
            color: 'red',
            message: mapCalibrationErrorToMessage(err, t),
          });
        },
      },
    );
  };

  return (
    <Menu shadow="md" position="bottom-end" withinPortal>
      <Menu.Target>
        <Button
          size="xs"
          variant="light"
          rightSection={<IconChevronDown size={14} />}
          loading={transitionMut.isPending}
        >
          {t.calibration.action.transition}
        </Button>
      </Menu.Target>
      <Menu.Dropdown>
        {nextStatuses.map((s) => (
          <Menu.Item key={s} onClick={() => handleTransition(s)}>
            {t.calibration.status[s]}
          </Menu.Item>
        ))}
      </Menu.Dropdown>
    </Menu>
  );
}

/**
 * AdjustGradeMenu — review 행별 등급 이동 (S~D Menu → reason 모달 → adjustments API).
 *
 * 계약 §7(#19) / §5: 등급 이동은 Select/Menu 로 (드래그는 P1 보류 박제).
 * adjustments = `review.finalGrade = toGrade` + session.adjustment_log append +
 * session IN_SESSION 이면 ADJUSTED 자동 승격 (BE 처리).
 *
 * 조정 가능 상태(IN_SESSION/ADJUSTED) 가 아니면 비활성 — 상위에서 enabled 제어.
 */
import { useState } from 'react';
import {
  Button,
  Group,
  Menu,
  Modal,
  Stack,
  Text,
  Textarea,
} from '@easy/ui-components/mantine';
import { notifications } from '@mantine/notifications';
import { IconChevronDown } from '@tabler/icons-react';

import {
  ADJUSTABLE_GRADES,
  useAdjustGradeMutation,
} from '../../api/calibration';
import { useT } from '../../i18n';
import { GradeBadge } from './GradeBadge';
import { mapCalibrationErrorToMessage } from './errorMapping';

interface Props {
  cycleId: string;
  sessionId: string;
  reviewId: string;
  /** 현재 등급(effectiveGrade) — 표시 + 동일 등급 비활성용. */
  currentGrade: string;
  /** 조정 가능 여부 (세션 IN_SESSION/ADJUSTED). false 면 비활성. */
  enabled: boolean;
}

export function AdjustGradeMenu({
  cycleId,
  sessionId,
  reviewId,
  currentGrade,
  enabled,
}: Props): React.ReactNode {
  const t = useT();
  const adjustMut = useAdjustGradeMutation(cycleId, sessionId);

  const [target, setTarget] = useState<string | null>(null);
  const [reason, setReason] = useState('');

  const closeReason = (): void => {
    if (adjustMut.isPending) return;
    setTarget(null);
    setReason('');
  };

  const handleSelect = (grade: string): void => {
    setReason('');
    setTarget(grade);
  };

  const handleSubmit = (): void => {
    if (!target) return;
    adjustMut.mutate(
      { reviewId, toGrade: target, reason: reason.trim() || null },
      {
        onSuccess: () => {
          notifications.show({
            color: 'green',
            message: t.calibration.adjust.done,
          });
          setTarget(null);
          setReason('');
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
    <>
      <Menu shadow="md" position="bottom-end" withinPortal disabled={!enabled}>
        <Menu.Target>
          <Button
            size="xs"
            variant="subtle"
            rightSection={<IconChevronDown size={14} />}
            disabled={!enabled}
          >
            {t.calibration.adjust.move}
          </Button>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Label>{t.calibration.adjust.selectGrade}</Menu.Label>
          {ADJUSTABLE_GRADES.map((g) => (
            <Menu.Item
              key={g}
              disabled={g === currentGrade}
              onClick={() => handleSelect(g)}
            >
              {g}
            </Menu.Item>
          ))}
        </Menu.Dropdown>
      </Menu>

      <Modal
        opened={target != null}
        onClose={closeReason}
        title={t.calibration.adjust.title}
        size="md"
        centered
      >
        <Stack>
          <Group gap="xs">
            <GradeBadge grade={currentGrade} />
            <Text size="sm" c="dimmed">
              →
            </Text>
            <GradeBadge grade={target} />
          </Group>

          <Textarea
            label={t.calibration.adjust.reason}
            placeholder={t.calibration.adjust.reasonPlaceholder}
            value={reason}
            onChange={(e) => setReason(e.currentTarget.value)}
            autosize
            minRows={3}
            maxRows={8}
          />

          <Group justify="flex-end" mt="sm">
            <Button
              variant="default"
              onClick={closeReason}
              disabled={adjustMut.isPending}
            >
              {t.common.action.cancel}
            </Button>
            <Button onClick={handleSubmit} loading={adjustMut.isPending}>
              {t.calibration.adjust.apply}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

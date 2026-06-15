/**
 * ConfirmSessionModal — CalibrationSession 확정 모달 (confirm endpoint 전용).
 *
 * 계약 §3/§5: confirm = IN_SESSION/ADJUSTED → CONFIRMED 전이 (transition 으로 불가).
 * `finalizeReviews == true` 면 cycle 의 status==CALIBRATION review 전부에 FINALIZED 전이 행 단위 적용
 * (kpiScore NULL 행은 skip). 응답 {session, finalizedCount, skippedCount}.
 *
 * `@mantine/modals` 미설치 → core Modal 인라인.
 */
import { useEffect, useState } from 'react';
import { Group, Modal, Stack, Switch, Text } from '@easy/ui-components/mantine';
import { notifications } from '@mantine/notifications';

import {
  useConfirmCalibrationSessionMutation,
  type CalibrationSessionResponse,
} from '../../api/calibration';
import { useT } from '../../i18n';
import { mapCalibrationErrorToMessage } from './errorMapping';
import { UiAlert, UiButton } from '@easy/ui-components';

interface Props {
  opened: boolean;
  onClose: () => void;
  cycleId: string;
  session: CalibrationSessionResponse;
}

export function ConfirmSessionModal({
  opened,
  onClose,
  cycleId,
  session,
}: Props): React.ReactNode {
  const t = useT();
  const confirmMut = useConfirmCalibrationSessionMutation(cycleId, session.id);

  const [finalizeReviews, setFinalizeReviews] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (opened) {
      setFinalizeReviews(false);
      setErrorMessage(null);
    }
  }, [opened]);

  const handleClose = (): void => {
    if (confirmMut.isPending) return;
    onClose();
  };

  const handleConfirm = (): void => {
    setErrorMessage(null);
    confirmMut.mutate(
      { finalizeReviews },
      {
        onSuccess: (res) => {
          notifications.show({
            color: 'green',
            message: finalizeReviews
              ? t.calibration.confirm.resultFinalized
                  .replace('{finalized}', String(res.finalizedCount))
                  .replace('{skipped}', String(res.skippedCount))
              : t.calibration.confirm.resultConfirmed,
          });
          onClose();
        },
        onError: (err) => {
          setErrorMessage(mapCalibrationErrorToMessage(err, t));
        },
      },
    );
  };

  return (
    <Modal
      opened={opened}
      onClose={handleClose}
      title={t.calibration.confirm.title}
      size="md"
      centered
    >
      <Stack>
        <Text size="sm">{t.calibration.confirm.description}</Text>

        <Switch
          label={t.calibration.confirm.finalizeReviews}
          description={t.calibration.confirm.finalizeReviewsHint}
          checked={finalizeReviews}
          onChange={(e) => setFinalizeReviews(e.currentTarget.checked)}
        />

        {finalizeReviews && (
          <UiAlert color="orange" variant="light">
            {t.calibration.confirm.finalizeWarning}
          </UiAlert>
        )}

        {errorMessage && (
          <UiAlert color="red" variant="light">
            {errorMessage}
          </UiAlert>
        )}

        <Group justify="flex-end" mt="sm">
          <UiButton
            variant="default"
            onClick={handleClose}
            disabled={confirmMut.isPending}
          >
            {t.common.action.cancel}
          </UiButton>
          <UiButton
            color="teal"
            onClick={handleConfirm}
            loading={confirmMut.isPending}
          >
            {t.calibration.confirm.submit}
          </UiButton>
        </Group>
      </Stack>
    </Modal>
  );
}

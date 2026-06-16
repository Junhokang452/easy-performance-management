/**
 * SessionFormModal — CalibrationSession 생성/수정 모달 (ReviewCreateModal / CycleCreateModal 패턴).
 *
 * 계약 §6:
 * - 생성: POST /cycles/{cycleId}/calibration-sessions {ownerOrgUnitId?, scheduledAt?, participantIds?}
 *         → 201 (status=PLANNED). FINALIZED·CANCELLED 외 cycle 단계에서 허용.
 * - 수정: PATCH /calibration-sessions/{sessionId} (동일 body) — session.status == PLANNED 한정.
 *
 * participantIds = UUID 다중 입력. TagsInput(@mantine/core) 사용 — 줄단위 폴백 불요.
 * scheduledAt = DateTimePicker (timestamptz, ISO 송신).
 */
import { useEffect, useState } from 'react';
import {
  Group,
  Modal,
  Stack,
  TagsInput,
  Text,
  TextInput,
} from '@easy/ui-components/mantine';
import { DateTimePicker } from '@mantine/dates';
import { showToast } from '../../shared/toast';

import {
  useCreateCalibrationSessionMutation,
  useUpdateCalibrationSessionMutation,
  type CalibrationSessionCreateRequest,
  type CalibrationSessionResponse,
  type CalibrationSessionUpdateRequest,
} from '../../api/calibration';
import { useT } from '../../i18n';
import { mapCalibrationErrorToMessage } from './errorMapping';
import { UiAlert, UiButton } from '@easy/ui-components';

interface Props {
  opened: boolean;
  onClose: () => void;
  cycleId: string;
  /** 수정 대상 — 없으면 생성 모드. */
  session?: CalibrationSessionResponse | null;
}

export function SessionFormModal({
  opened,
  onClose,
  cycleId,
  session,
}: Props): React.ReactNode {
  const t = useT();
  const isEdit = Boolean(session);

  const createMut = useCreateCalibrationSessionMutation(cycleId);
  const updateMut = useUpdateCalibrationSessionMutation(cycleId, session?.id ?? '');

  const [ownerOrgUnitId, setOwnerOrgUnitId] = useState('');
  const [scheduledAt, setScheduledAt] = useState<Date | null>(null);
  const [participantIds, setParticipantIds] = useState<string[]>([]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (opened) {
      setOwnerOrgUnitId(session?.ownerOrgUnitId ?? '');
      setScheduledAt(toDate(session?.scheduledAt ?? null));
      setParticipantIds(session?.participantIds ?? []);
      setErrorMessage(null);
    }
  }, [opened, session]);

  const pending = createMut.isPending || updateMut.isPending;

  const handleClose = (): void => {
    if (pending) return;
    onClose();
  };

  const handleSubmit = (): void => {
    setErrorMessage(null);
    const payload: CalibrationSessionCreateRequest &
      CalibrationSessionUpdateRequest = {
      ownerOrgUnitId: ownerOrgUnitId.trim() || null,
      scheduledAt: scheduledAt ? scheduledAt.toISOString() : null,
      participantIds: participantIds.length > 0 ? participantIds : null,
    };

    const onError = (err: unknown): void => {
      setErrorMessage(mapCalibrationErrorToMessage(err, t));
    };

    if (isEdit && session) {
      updateMut.mutate(payload, {
        onSuccess: () => {
          showToast({
            tone: 'success',
            message: t.common.message.updated,
          });
          onClose();
        },
        onError,
      });
    } else {
      createMut.mutate(payload, {
        onSuccess: () => {
          showToast({
            tone: 'success',
            message: t.common.message.created,
          });
          onClose();
        },
        onError,
      });
    }
  };

  return (
    <Modal
      opened={opened}
      onClose={handleClose}
      title={isEdit ? t.calibration.form.editTitle : t.calibration.form.createTitle}
      size="md"
      centered
    >
      <Stack>
        <TextInput
          label={t.calibration.form.ownerOrgUnitId}
          description={t.calibration.form.ownerOrgUnitIdHint}
          placeholder={t.calibration.form.ownerOrgUnitIdPlaceholder}
          value={ownerOrgUnitId}
          onChange={(e) => setOwnerOrgUnitId(e.currentTarget.value)}
        />

        <DateTimePicker
          label={t.calibration.form.scheduledAt}
          placeholder={t.calibration.form.scheduledAtPlaceholder}
          value={scheduledAt}
          onChange={(v) => setScheduledAt(toDate(v))}
          clearable
        />

        <TagsInput
          label={t.calibration.form.participantIds}
          description={t.calibration.form.participantIdsHint}
          placeholder={t.calibration.form.participantIdsPlaceholder}
          value={participantIds}
          onChange={setParticipantIds}
          clearable
        />

        {errorMessage && (
          <UiAlert color="red" variant="light">
            {errorMessage}
          </UiAlert>
        )}

        <Text size="xs" c="dimmed">
          {isEdit
            ? t.calibration.form.editNote
            : t.calibration.form.createNote}
        </Text>

        <Group justify="flex-end" mt="sm">
          <UiButton variant="default" onClick={handleClose} disabled={pending}>
            {t.common.action.cancel}
          </UiButton>
          <UiButton onClick={handleSubmit} loading={pending}>
            {isEdit ? t.common.action.save : t.common.action.create}
          </UiButton>
        </Group>
      </Stack>
    </Modal>
  );
}

function toDate(v: Date | string | null): Date | null {
  if (!v) return null;
  if (v instanceof Date) return v;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? null : d;
}

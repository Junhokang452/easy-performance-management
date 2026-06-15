/**
 * ActualFormModal — KPI 실적 입력 모달 (My KPI 화면).
 *
 * 모드:
 * - 'report' : 신규 실적 입력 (POST /kpi-assignments/{id}/actuals)
 * - 'correct': 기존 실적 정정 (POST /kpi-actuals/{actualId}/supersede — 신규 row + supersedesId)
 *
 * 계약 §4: actuals 는 UPDATE/DELETE 없음 — 정정은 supersede 전용 (talent ReviewDecision append-only).
 */
import { useEffect, useState } from 'react';
import {
  Group,
  Modal,
  NumberInput,
  Stack,
  Textarea,
  TextInput,
} from '@easy/ui-components/mantine';
import { DateInput } from '@mantine/dates';
import { notifications } from '@mantine/notifications';

import {
  useCreateActualMutation,
  useSupersedeActualMutation,
  type KpiActualResponse,
} from '../../api/kpi';
import { useT } from '../../i18n';
import { mapKpiErrorToMessage } from './errorMapping';
import { UiAlert, UiButton } from '@easy/ui-components';

type Mode =
  | { kind: 'report' }
  | { kind: 'correct'; original: KpiActualResponse };

interface Props {
  opened: boolean;
  onClose: () => void;
  assignmentId: string;
  cycleId: string;
  mode: Mode;
}

export function ActualFormModal({
  opened,
  onClose,
  assignmentId,
  cycleId,
  mode,
}: Props): React.ReactNode {
  const t = useT();
  const createMut = useCreateActualMutation(assignmentId, cycleId);
  const supersedeMut = useSupersedeActualMutation(assignmentId, cycleId);

  const [asOfDate, setAsOfDate] = useState<Date | null>(new Date());
  const [actualValue, setActualValue] = useState<number | string>('');
  const [evidenceUrl, setEvidenceUrl] = useState('');
  const [comment, setComment] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!opened) return;
    if (mode.kind === 'correct') {
      setAsOfDate(parseDate(mode.original.asOfDate));
      setActualValue(mode.original.actualValue);
      setEvidenceUrl(mode.original.evidenceUrl ?? '');
      setComment(mode.original.comment ?? '');
    } else {
      setAsOfDate(new Date());
      setActualValue('');
      setEvidenceUrl('');
      setComment('');
    }
    setErrorMessage(null);
  }, [opened, mode]);

  const pending = createMut.isPending || supersedeMut.isPending;

  const handleClose = (): void => {
    if (pending) return;
    onClose();
  };

  const handleSubmit = (): void => {
    setErrorMessage(null);
    const value = typeof actualValue === 'number' ? actualValue : Number(actualValue);
    if (!Number.isFinite(value)) {
      setErrorMessage(t.error.unknown);
      return;
    }
    if (mode.kind === 'correct' && !asOfDate) {
      setErrorMessage(t.error.unknown);
      return;
    }
    if (mode.kind === 'report' && !asOfDate) {
      setErrorMessage(t.error.unknown);
      return;
    }

    const onSuccess = (): void => {
      notifications.show({ color: 'green', message: t.common.message.created });
      onClose();
    };
    const onError = (err: unknown): void => {
      setErrorMessage(mapKpiErrorToMessage(err, t));
    };

    if (mode.kind === 'report') {
      createMut.mutate(
        {
          asOfDate: toIsoDate(asOfDate as Date),
          actualValue: value,
          evidenceUrl: evidenceUrl.trim() || null,
          comment: comment.trim() || null,
        },
        { onSuccess, onError },
      );
    } else {
      supersedeMut.mutate(
        {
          actualId: mode.original.id,
          req: {
            asOfDate: asOfDate ? toIsoDate(asOfDate) : null,
            actualValue: value,
            evidenceUrl: evidenceUrl.trim() || null,
            comment: comment.trim() || null,
          },
        },
        { onSuccess, onError },
      );
    }
  };

  const title =
    mode.kind === 'correct' ? t.kpi.actual.correctTitle : t.kpi.actual.title;

  return (
    <Modal opened={opened} onClose={handleClose} title={title} size="md" centered>
      <Stack>
        <DateInput
          label={t.kpi.actual.asOfDate}
          value={asOfDate}
          onChange={(d) => setAsOfDate(toDate(d))}
          withAsterisk
        />
        <NumberInput
          label={t.kpi.actual.actualValue}
          value={actualValue}
          onChange={setActualValue}
          decimalScale={4}
          withAsterisk
        />
        <TextInput
          label={t.kpi.actual.evidenceUrl}
          value={evidenceUrl}
          onChange={(e) => setEvidenceUrl(e.currentTarget.value)}
          maxLength={500}
        />
        <Textarea
          label={t.kpi.actual.comment}
          value={comment}
          onChange={(e) => setComment(e.currentTarget.value)}
          autosize={false}
          rows={3}
        />

        {errorMessage && (
          <UiAlert color="red" variant="light">
            {errorMessage}
          </UiAlert>
        )}

        <Group justify="flex-end" mt="sm">
          <UiButton variant="default" onClick={handleClose} disabled={pending}>
            {t.common.action.cancel}
          </UiButton>
          <UiButton onClick={handleSubmit} loading={pending}>
            {mode.kind === 'correct'
              ? t.kpi.actual.correct
              : t.kpi.actual.report}
          </UiButton>
        </Group>
      </Stack>
    </Modal>
  );
}

function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function parseDate(iso: string): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

function toDate(v: Date | string | null): Date | null {
  if (!v) return null;
  if (v instanceof Date) return v;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? null : d;
}

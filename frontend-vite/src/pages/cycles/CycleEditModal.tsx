/**
 * CycleEditModal — 사이클 부분 수정 (PATCH).
 */
import { useEffect, useState } from 'react';
import { Group, Modal, Stack } from '@easy/ui-components/mantine';
import { DateInput } from '@mantine/dates';
import { showToast } from '../../shared/toast';
import {
  UiAlert,
  FormActions,
  FormSelect,
  FormTextInput,
  PrimaryButton,
  SecondaryButton,
} from '@easy/ui-components';

import {
  ALL_CYCLE_TYPES,
  useUpdateCycleMutation,
  type CycleResponse,
  type CycleType,
  type CycleUpdateRequest,
} from '../../api/cycles';
import { useT } from '../../i18n';
import { mapApiErrorToMessage } from './errorMapping';

interface Props {
  opened: boolean;
  onClose: () => void;
  cycle: CycleResponse;
}

export function CycleEditModal({
  opened,
  onClose,
  cycle,
}: Props): React.ReactNode {
  const t = useT();
  const updateMut = useUpdateCycleMutation(cycle.id);

  const [name, setName] = useState(cycle.name);
  const [periodStart, setPeriodStart] = useState<Date | null>(
    parseDate(cycle.periodStart),
  );
  const [periodEnd, setPeriodEnd] = useState<Date | null>(
    parseDate(cycle.periodEnd),
  );
  const [cycleType, setCycleType] = useState<CycleType>(cycle.cycleType);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (opened) {
      setName(cycle.name);
      setPeriodStart(parseDate(cycle.periodStart));
      setPeriodEnd(parseDate(cycle.periodEnd));
      setCycleType(cycle.cycleType);
      setErrorMessage(null);
    }
  }, [opened, cycle]);

  const handleClose = (): void => {
    if (updateMut.isPending) return;
    onClose();
  };

  const handleSubmit = (): void => {
    setErrorMessage(null);
    if (periodStart && periodEnd && periodEnd <= periodStart) {
      setErrorMessage(t.error.E9804232);
      return;
    }
    const req: CycleUpdateRequest = {};
    if (name.trim() && name.trim() !== cycle.name) req.name = name.trim();
    if (periodStart && toIsoDate(periodStart) !== cycle.periodStart) {
      req.periodStart = toIsoDate(periodStart);
    }
    if (periodEnd && toIsoDate(periodEnd) !== cycle.periodEnd) {
      req.periodEnd = toIsoDate(periodEnd);
    }
    if (cycleType !== cycle.cycleType) req.cycleType = cycleType;

    updateMut.mutate(req, {
      onSuccess: () => {
        showToast({
          tone: 'success',
          message: t.common.message.updated,
        });
        onClose();
      },
      onError: (err) => {
        setErrorMessage(mapApiErrorToMessage(err, t));
      },
    });
  };

  return (
    <Modal
      opened={opened}
      onClose={handleClose}
      title={t.cycles.action.edit}
      size="md"
      centered
    >
      <Stack>
        <FormTextInput
          label={t.cycles.field.name}
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
          maxLength={100}
        />
        <Group grow>
          <DateInput
            label={t.cycles.field.periodStart}
            value={periodStart}
            onChange={(d) => setPeriodStart(toDate(d))}
          />
          <DateInput
            label={t.cycles.field.periodEnd}
            value={periodEnd}
            onChange={(d) => setPeriodEnd(toDate(d))}
          />
        </Group>
        <FormSelect
          label={t.cycles.field.cycleType}
          data={ALL_CYCLE_TYPES.map((c) => ({
            value: c,
            label: t.cycles.type[c],
          }))}
          value={cycleType}
          onChange={(v) => v && setCycleType(v as CycleType)}
          allowDeselect={false}
        />

        {errorMessage && (
          <UiAlert color="red" variant="light">
            {errorMessage}
          </UiAlert>
        )}

        <FormActions
          secondary={
            <SecondaryButton onClick={handleClose} disabled={updateMut.isPending}>
              {t.common.action.cancel}
            </SecondaryButton>
          }
          primary={
            <PrimaryButton onClick={handleSubmit} loading={updateMut.isPending}>
              {t.common.action.save}
            </PrimaryButton>
          }
        />
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

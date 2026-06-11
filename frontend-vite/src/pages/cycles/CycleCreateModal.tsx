/**
 * CycleCreateModal — 사이클 생성 모달 + 선택적 Policy 동시 생성.
 */
import { useState } from 'react';
import {
  Alert,
  Button,
  Group,
  Modal,
  Select,
  Stack,
  Switch,
  TextInput,
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { notifications } from '@mantine/notifications';

import {
  ALL_CYCLE_TYPES,
  isDistributionSumValid,
  useCreateCycleMutation,
  type CycleCreateRequest,
  type CycleType,
  type PolicyUpsertRequest,
} from '../../api/cycles';
import { useT } from '../../i18n';
import { mapApiErrorToMessage } from './errorMapping';
import { DEFAULT_POLICY, PolicyForm } from './PolicyForm';

interface Props {
  opened: boolean;
  onClose: () => void;
}

export function CycleCreateModal({ opened, onClose }: Props): React.ReactNode {
  const t = useT();
  const createMut = useCreateCycleMutation();

  const [name, setName] = useState('');
  const [periodStart, setPeriodStart] = useState<Date | null>(null);
  const [periodEnd, setPeriodEnd] = useState<Date | null>(null);
  const [cycleType, setCycleType] = useState<CycleType>('QUARTERLY');
  const [withPolicy, setWithPolicy] = useState(false);
  const [policy, setPolicy] = useState<PolicyUpsertRequest>(DEFAULT_POLICY);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const reset = (): void => {
    setName('');
    setPeriodStart(null);
    setPeriodEnd(null);
    setCycleType('QUARTERLY');
    setWithPolicy(false);
    setPolicy(DEFAULT_POLICY);
    setErrorMessage(null);
  };

  const handleClose = (): void => {
    if (createMut.isPending) return;
    reset();
    onClose();
  };

  const handleSubmit = (): void => {
    setErrorMessage(null);

    if (!name.trim() || !periodStart || !periodEnd) {
      setErrorMessage(t.error.unknown);
      return;
    }
    if (periodEnd <= periodStart) {
      setErrorMessage(t.error.E9804232);
      return;
    }
    if (withPolicy) {
      const needsDist =
        (policy.distributionMode === 'FORCED' ||
          policy.distributionMode === 'HYBRID') &&
        policy.ratingScale === 'S_A_B_C_D';
      if (needsDist && !isDistributionSumValid(policy.forcedDistribution)) {
        setErrorMessage(t.error.E9804233);
        return;
      }
    }

    const req: CycleCreateRequest = {
      name: name.trim(),
      periodStart: toIsoDate(periodStart),
      periodEnd: toIsoDate(periodEnd),
      cycleType,
      ...(withPolicy ? { policy } : {}),
    };

    createMut.mutate(req, {
      onSuccess: () => {
        notifications.show({
          color: 'green',
          message: t.common.message.created,
        });
        reset();
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
      title={t.cycles.create}
      size="lg"
      centered
    >
      <Stack>
        <TextInput
          label={t.cycles.field.name}
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
          maxLength={100}
          withAsterisk
        />
        <Group grow>
          <DateInput
            label={t.cycles.field.periodStart}
            value={periodStart}
            onChange={(d) => setPeriodStart(toDate(d))}
            withAsterisk
          />
          <DateInput
            label={t.cycles.field.periodEnd}
            value={periodEnd}
            onChange={(d) => setPeriodEnd(toDate(d))}
            withAsterisk
          />
        </Group>
        <Select
          label={t.cycles.field.cycleType}
          data={ALL_CYCLE_TYPES.map((c) => ({
            value: c,
            label: t.cycles.type[c],
          }))}
          value={cycleType}
          onChange={(v) => v && setCycleType(v as CycleType)}
          allowDeselect={false}
          withAsterisk
        />
        <Switch
          label={t.policy.title}
          checked={withPolicy}
          onChange={(e) => setWithPolicy(e.currentTarget.checked)}
        />
        {withPolicy && <PolicyForm value={policy} onChange={setPolicy} />}

        {errorMessage && (
          <Alert color="red" variant="light">
            {errorMessage}
          </Alert>
        )}

        <Group justify="flex-end" mt="sm">
          <Button
            variant="default"
            onClick={handleClose}
            disabled={createMut.isPending}
          >
            {t.common.action.cancel}
          </Button>
          <Button onClick={handleSubmit} loading={createMut.isPending}>
            {t.common.action.create}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function toIsoDate(d: Date): string {
  // yyyy-MM-dd local
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function toDate(v: Date | string | null): Date | null {
  if (!v) return null;
  if (v instanceof Date) return v;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? null : d;
}

/**
 * NodeFormModal — KPI 노드 생성/수정 모달 (매니저 트리 화면).
 *
 * 모드:
 * - create: POST /kpi-trees/{treeId}/nodes (parentId 로 root/child 구분)
 * - edit  : PATCH /kpi-nodes/{nodeId} (diff-only)
 *
 * 계약 §3: weight ∈ (0,1] (E9804237) / source ≠ MANUAL P0 거부 (E9804239 — FE 는 MANUAL 고정 권장).
 */
import { useEffect, useState } from 'react';
import {
  Group,
  Modal,
  NumberInput,
  Select,
  Stack,
  Text,
  TextInput,
} from '@easy/ui-components/mantine';
import { showToast } from '../../shared/toast';

import {
  ALL_BSC_PERSPECTIVES,
  useCreateKpiNodeMutation,
  useUpdateKpiNodeMutation,
  type BscPerspective,
  type KpiNodeResponse,
} from '../../api/kpi';
import { useT } from '../../i18n';
import { mapKpiErrorToMessage } from './errorMapping';
import { UiAlert, UiButton } from '@easy/ui-components';

export type NodeFormMode =
  | { kind: 'createRoot' }
  | { kind: 'createChild'; parent: KpiNodeResponse }
  | { kind: 'edit'; node: KpiNodeResponse };

interface Props {
  opened: boolean;
  onClose: () => void;
  treeId: string;
  mode: NodeFormMode;
}

const NO_PERSPECTIVE = '__none__';

export function NodeFormModal({
  opened,
  onClose,
  treeId,
  mode,
}: Props): React.ReactNode {
  const t = useT();
  const createMut = useCreateKpiNodeMutation(treeId);
  const updateMut = useUpdateKpiNodeMutation(treeId);

  const [label, setLabel] = useState('');
  const [weight, setWeight] = useState<number | string>(1);
  const [target, setTarget] = useState<number | string>('');
  const [unit, setUnit] = useState('');
  const [bscPerspective, setBscPerspective] = useState<string>(NO_PERSPECTIVE);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!opened) return;
    if (mode.kind === 'edit') {
      const n = mode.node;
      setLabel(n.label);
      setWeight(n.weight);
      setTarget(n.target ?? '');
      setUnit(n.unit ?? '');
      setBscPerspective(n.bscPerspective ?? NO_PERSPECTIVE);
    } else {
      setLabel('');
      setWeight(1);
      setTarget('');
      setUnit('');
      setBscPerspective(NO_PERSPECTIVE);
    }
    setErrorMessage(null);
  }, [opened, mode]);

  const pending = createMut.isPending || updateMut.isPending;

  const handleClose = (): void => {
    if (pending) return;
    onClose();
  };

  const handleSubmit = (): void => {
    setErrorMessage(null);
    if (!label.trim()) {
      setErrorMessage(t.error.unknown);
      return;
    }
    const weightNum = typeof weight === 'number' ? weight : Number(weight);
    if (!Number.isFinite(weightNum) || weightNum <= 0 || weightNum > 1) {
      setErrorMessage(t.error.E9804237);
      return;
    }
    const targetNum =
      target === '' || target == null
        ? null
        : typeof target === 'number'
          ? target
          : Number(target);
    const perspective: BscPerspective | null =
      bscPerspective === NO_PERSPECTIVE
        ? null
        : (bscPerspective as BscPerspective);

    const onSuccess = (): void => {
      showToast({
        tone: 'success',
        message:
          mode.kind === 'edit'
            ? t.common.message.updated
            : t.common.message.created,
      });
      onClose();
    };
    const onError = (err: unknown): void => {
      setErrorMessage(mapKpiErrorToMessage(err, t));
    };

    if (mode.kind === 'edit') {
      updateMut.mutate(
        {
          nodeId: mode.node.id,
          req: {
            label: label.trim(),
            weight: weightNum,
            target: targetNum,
            unit: unit.trim() || null,
            bscPerspective: perspective,
          },
        },
        { onSuccess, onError },
      );
    } else {
      const parentId =
        mode.kind === 'createChild' ? mode.parent.id : null;
      createMut.mutate(
        {
          parentId,
          label: label.trim(),
          weight: weightNum,
          target: targetNum,
          unit: unit.trim() || null,
          bscPerspective: perspective,
          source: 'MANUAL',
        },
        { onSuccess, onError },
      );
    }
  };

  const title =
    mode.kind === 'edit'
      ? t.kpi.manager.editNode
      : mode.kind === 'createChild'
        ? t.kpi.manager.addChild
        : t.kpi.manager.addRootNode;

  return (
    <Modal opened={opened} onClose={handleClose} title={title} size="md" centered>
      <Stack>
        {mode.kind === 'createChild' && (
          <Text size="xs" c="dimmed">
            {t.kpi.node.parent}: {mode.parent.label}
          </Text>
        )}
        <TextInput
          label={t.kpi.node.label}
          value={label}
          onChange={(e) => setLabel(e.currentTarget.value)}
          maxLength={200}
          withAsterisk
        />
        <NumberInput
          label={t.kpi.node.weight}
          description={t.kpi.node.weightHint}
          value={weight}
          onChange={setWeight}
          min={0}
          max={1}
          step={0.05}
          decimalScale={4}
          withAsterisk
        />
        <Group grow>
          <NumberInput
            label={t.kpi.node.target}
            value={target}
            onChange={setTarget}
            decimalScale={4}
          />
          <TextInput
            label={t.kpi.node.unit}
            value={unit}
            onChange={(e) => setUnit(e.currentTarget.value)}
            maxLength={20}
          />
        </Group>
        <Select
          label={t.kpi.node.bscPerspective}
          data={[
            { value: NO_PERSPECTIVE, label: t.kpi.bscPerspective.UNASSIGNED },
            ...ALL_BSC_PERSPECTIVES.map((p) => ({
              value: p,
              label: t.kpi.bscPerspective[p],
            })),
          ]}
          value={bscPerspective}
          onChange={(v) => setBscPerspective(v ?? NO_PERSPECTIVE)}
          allowDeselect={false}
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
            {mode.kind === 'edit'
              ? t.common.action.save
              : t.common.action.create}
          </UiButton>
        </Group>
      </Stack>
    </Modal>
  );
}

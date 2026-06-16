/**
 * TreeFormModal — KPI 트리 생성/수정 모달 (매니저 트리 화면).
 *
 * 모드:
 * - create: POST /cycles/{cycleId}/kpi-trees ({name, level, ownerOrgUnitId?, bscEnabled?})
 * - edit  : PATCH /kpi-trees/{treeId} (diff-only)
 *
 * ownerOrgUnitId 는 rm_org_unit 수신(P0-S6) 전이라 plain UUID 입력.
 */
import { useEffect, useState } from 'react';
import {
  Group,
  Modal,
  Select,
  Stack,
  Switch,
  TextInput,
} from '@easy/ui-components/mantine';
import { showToast } from '../../shared/toast';

import {
  ALL_KPI_TREE_LEVELS,
  useCreateKpiTreeMutation,
  useUpdateKpiTreeMutation,
  type KpiTreeLevel,
  type KpiTreeResponse,
} from '../../api/kpi';
import { useT } from '../../i18n';
import { mapKpiErrorToMessage } from './errorMapping';
import { UiAlert, UiButton } from '@easy/ui-components';

export type TreeFormMode =
  | { kind: 'create' }
  | { kind: 'edit'; tree: KpiTreeResponse };

interface Props {
  opened: boolean;
  onClose: () => void;
  cycleId: string;
  mode: TreeFormMode;
}

export function TreeFormModal({
  opened,
  onClose,
  cycleId,
  mode,
}: Props): React.ReactNode {
  const t = useT();
  const createMut = useCreateKpiTreeMutation(cycleId);
  const editTreeId = mode.kind === 'edit' ? mode.tree.id : '';
  const updateMut = useUpdateKpiTreeMutation(editTreeId, cycleId);

  const [name, setName] = useState('');
  const [level, setLevel] = useState<KpiTreeLevel>('CORPORATE');
  const [ownerOrgUnitId, setOwnerOrgUnitId] = useState('');
  const [bscEnabled, setBscEnabled] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!opened) return;
    if (mode.kind === 'edit') {
      setName(mode.tree.name);
      setLevel(mode.tree.level);
      setOwnerOrgUnitId(mode.tree.ownerOrgUnitId ?? '');
      setBscEnabled(mode.tree.bscEnabled);
    } else {
      setName('');
      setLevel('CORPORATE');
      setOwnerOrgUnitId('');
      setBscEnabled(false);
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
    if (!name.trim()) {
      setErrorMessage(t.error.unknown);
      return;
    }
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
          name: name.trim(),
          level,
          ownerOrgUnitId: ownerOrgUnitId.trim() || null,
          bscEnabled,
        },
        { onSuccess, onError },
      );
    } else {
      createMut.mutate(
        {
          name: name.trim(),
          level,
          ownerOrgUnitId: ownerOrgUnitId.trim() || null,
          bscEnabled,
        },
        { onSuccess, onError },
      );
    }
  };

  return (
    <Modal
      opened={opened}
      onClose={handleClose}
      title={mode.kind === 'edit' ? t.kpi.manager.editTree : t.kpi.tree.create}
      size="md"
      centered
    >
      <Stack>
        <TextInput
          label={t.kpi.tree.name}
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
          maxLength={100}
          withAsterisk
        />
        <Select
          label={t.kpi.tree.level}
          data={ALL_KPI_TREE_LEVELS.map((l) => ({
            value: l,
            label: t.kpi.level[l],
          }))}
          value={level}
          onChange={(v) => v && setLevel(v as KpiTreeLevel)}
          allowDeselect={false}
          withAsterisk
        />
        <TextInput
          label={t.kpi.tree.ownerOrgUnitId}
          value={ownerOrgUnitId}
          onChange={(e) => setOwnerOrgUnitId(e.currentTarget.value)}
        />
        <Switch
          label={t.kpi.tree.bscEnabled}
          checked={bscEnabled}
          onChange={(e) => setBscEnabled(e.currentTarget.checked)}
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

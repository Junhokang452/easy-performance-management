/**
 * PolicyEditModal — Policy 단독 편집 (GET + PUT).
 *
 * - 기존 Policy 가 있으면 usePolicyQuery 로 로드 → 폼 prefill
 * - 없으면 DEFAULT_POLICY 로 시작 (신규 생성)
 */
import { useEffect, useState } from 'react';
import { Center, Modal, Stack } from '@easy/ui-components/mantine';
import { notifications } from '@mantine/notifications';
import { UiAlert, UiLoader, FormActions, PrimaryButton, SecondaryButton } from '@easy/ui-components';

import {
  isDistributionSumValid,
  usePolicyQuery,
  useUpsertPolicyMutation,
  type CycleResponse,
  type PolicyUpsertRequest,
} from '../../api/cycles';
import { useT } from '../../i18n';
import { mapApiErrorToMessage } from './errorMapping';
import { DEFAULT_POLICY, PolicyForm } from './PolicyForm';

interface Props {
  opened: boolean;
  onClose: () => void;
  cycle: CycleResponse;
}

export function PolicyEditModal({
  opened,
  onClose,
  cycle,
}: Props): React.ReactNode {
  const t = useT();
  const hasPolicy = Boolean(cycle.policyId);
  const policyQuery = usePolicyQuery(hasPolicy && opened ? cycle.id : null);
  const upsertMut = useUpsertPolicyMutation(cycle.id);

  const [form, setForm] = useState<PolicyUpsertRequest>(DEFAULT_POLICY);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // 모달 open 또는 정책 로드 시 prefill
  useEffect(() => {
    if (!opened) return;
    if (hasPolicy && policyQuery.data) {
      const p = policyQuery.data;
      setForm({
        distributionMode: p.distributionMode,
        ratingScale: p.ratingScale,
        appealEnabled: p.appealEnabled,
        bscEnabled: p.bscEnabled,
        achievementLogCutoffDays: p.achievementLogCutoffDays,
        forcedDistribution: p.forcedDistribution,
      });
    } else if (!hasPolicy) {
      setForm(DEFAULT_POLICY);
    }
    setErrorMessage(null);
  }, [opened, hasPolicy, policyQuery.data]);

  const handleClose = (): void => {
    if (upsertMut.isPending) return;
    onClose();
  };

  const handleSubmit = (): void => {
    setErrorMessage(null);
    const needsDist =
      (form.distributionMode === 'FORCED' ||
        form.distributionMode === 'HYBRID') &&
      form.ratingScale === 'S_A_B_C_D';
    if (needsDist && !isDistributionSumValid(form.forcedDistribution)) {
      setErrorMessage(t.error.E9804233);
      return;
    }

    upsertMut.mutate(form, {
      onSuccess: () => {
        notifications.show({
          color: 'green',
          message: t.common.message.updated,
        });
        onClose();
      },
      onError: (err) => {
        setErrorMessage(mapApiErrorToMessage(err, t));
      },
    });
  };

  const loading = hasPolicy && policyQuery.isLoading;

  return (
    <Modal
      opened={opened}
      onClose={handleClose}
      title={t.policy.title}
      size="lg"
      centered
    >
      {loading ? (
        <Center mih={120}>
          <UiLoader />
        </Center>
      ) : (
        <Stack>
          <PolicyForm value={form} onChange={setForm} />

          {errorMessage && (
            <UiAlert color="red" variant="light">
              {errorMessage}
            </UiAlert>
          )}

          <FormActions
            secondary={
              <SecondaryButton onClick={handleClose} disabled={upsertMut.isPending}>
                {t.common.action.cancel}
              </SecondaryButton>
            }
            primary={
              <PrimaryButton onClick={handleSubmit} loading={upsertMut.isPending}>
                {t.common.action.save}
              </PrimaryButton>
            }
          />
        </Stack>
      )}
    </Modal>
  );
}

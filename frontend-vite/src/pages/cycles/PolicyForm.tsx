/**
 * PolicyForm — EvaluationPolicy 폼 (Cycle 생성 모달 + Policy 편집 모달 공유).
 *
 * 책임:
 * - distributionMode / ratingScale / appealEnabled / bscEnabled / achievementLogCutoffDays 입력
 * - distributionMode ∈ {FORCED, HYBRID} + ratingScale === S_A_B_C_D 일 때 5 분포 입력 + 합 검증
 *
 * controlled 컴포넌트 — 상위에서 value/onChange 주입.
 */
import {
  Group,
  NumberInput,
  Stack,
  Switch,
  Text,
} from '@easy/ui-components/mantine';
import { FormSelect } from '@easy/ui-components';

import {
  ALL_DISTRIBUTION_MODES,
  ALL_RATING_SCALES,
  SABCD_KEYS,
  isDistributionSumValid,
  type DistributionMode,
  type PolicyUpsertRequest,
  type RatingScale,
} from '../../api/cycles';
import { useT } from '../../i18n';

interface Props {
  value: PolicyUpsertRequest;
  onChange: (next: PolicyUpsertRequest) => void;
}

export function PolicyForm({ value, onChange }: Props): React.ReactNode {
  const t = useT();

  const needsDistribution =
    (value.distributionMode === 'FORCED' || value.distributionMode === 'HYBRID') &&
    value.ratingScale === 'S_A_B_C_D';

  const distSum = value.forcedDistribution
    ? Object.values(value.forcedDistribution).reduce(
        (acc, v) => acc + (v ?? 0),
        0,
      )
    : 0;
  const distSumValid = needsDistribution
    ? isDistributionSumValid(value.forcedDistribution)
    : true;

  const handleDistributionModeChange = (next: string | null): void => {
    if (!next) return;
    const mode = next as DistributionMode;
    const nextNeedsDist =
      (mode === 'FORCED' || mode === 'HYBRID') &&
      value.ratingScale === 'S_A_B_C_D';
    onChange({
      ...value,
      distributionMode: mode,
      forcedDistribution: nextNeedsDist
        ? (value.forcedDistribution ?? defaultSABCD())
        : null,
    });
  };

  const handleRatingScaleChange = (next: string | null): void => {
    if (!next) return;
    const scale = next as RatingScale;
    const nextNeedsDist =
      (value.distributionMode === 'FORCED' ||
        value.distributionMode === 'HYBRID') &&
      scale === 'S_A_B_C_D';
    onChange({
      ...value,
      ratingScale: scale,
      forcedDistribution: nextNeedsDist
        ? (value.forcedDistribution ?? defaultSABCD())
        : null,
    });
  };

  const handleDistChange = (k: string, n: number | string): void => {
    const numeric = typeof n === 'number' ? n : Number(n);
    onChange({
      ...value,
      forcedDistribution: {
        ...(value.forcedDistribution ?? defaultSABCD()),
        [k]: Number.isFinite(numeric) ? numeric : 0,
      },
    });
  };

  return (
    <Stack gap="sm">
      <FormSelect
        label={t.policy.field.distributionMode}
        data={ALL_DISTRIBUTION_MODES.map((m) => ({
          value: m,
          label: t.policy.distributionMode[m],
        }))}
        value={value.distributionMode}
        onChange={handleDistributionModeChange}
        allowDeselect={false}
        withAsterisk
      />
      <FormSelect
        label={t.policy.field.ratingScale}
        data={ALL_RATING_SCALES.map((s) => ({
          value: s,
          label: t.policy.ratingScale[s],
        }))}
        value={value.ratingScale}
        onChange={handleRatingScaleChange}
        allowDeselect={false}
        withAsterisk
      />
      <Switch
        label={t.policy.field.appealEnabled}
        checked={value.appealEnabled}
        onChange={(e) =>
          onChange({ ...value, appealEnabled: e.currentTarget.checked })
        }
      />
      <Switch
        label={t.policy.field.bscEnabled}
        checked={value.bscEnabled}
        onChange={(e) =>
          onChange({ ...value, bscEnabled: e.currentTarget.checked })
        }
      />
      <NumberInput
        label={t.policy.field.achievementLogCutoffDays}
        min={0}
        max={30}
        value={value.achievementLogCutoffDays}
        onChange={(n) =>
          onChange({
            ...value,
            achievementLogCutoffDays:
              typeof n === 'number' ? n : Number(n) || 0,
          })
        }
      />

      {needsDistribution && (
        <Stack gap={4}>
          <Text size="sm" fw={500}>
            {t.policy.field.forcedDistribution}
          </Text>
          <Group gap="xs" align="end">
            {SABCD_KEYS.map((k) => (
              <NumberInput
                key={k}
                label={k}
                w={80}
                min={0}
                max={1}
                step={0.05}
                decimalScale={2}
                value={value.forcedDistribution?.[k] ?? 0}
                onChange={(n) => handleDistChange(k, n)}
              />
            ))}
          </Group>
          <Text size="xs" c={distSumValid ? 'dimmed' : 'red'}>
            {distSumValid
              ? `${t.policy.distributionSum}: ${distSum.toFixed(3)}`
              : t.policy.distributionSumMustBeOne.replace(
                  '{sum}',
                  distSum.toFixed(3),
                )}
          </Text>
        </Stack>
      )}
    </Stack>
  );
}

function defaultSABCD(): Record<string, number> {
  return { S: 0.1, A: 0.2, B: 0.4, C: 0.2, D: 0.1 };
}

export const DEFAULT_POLICY: PolicyUpsertRequest = {
  distributionMode: 'HYBRID',
  ratingScale: 'S_A_B_C_D',
  appealEnabled: false,
  bscEnabled: false,
  achievementLogCutoffDays: 3,
  forcedDistribution: { S: 0.1, A: 0.2, B: 0.4, C: 0.2, D: 0.1 },
};

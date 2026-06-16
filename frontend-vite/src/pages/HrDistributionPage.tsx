/**
 * HrDistributionPage (#24) — HR 분포 시뮬레이터 (`/hr/distribution`).
 *
 * cycle Select → 분포 비교 막대(현재 vs 목표) + 정책 정보(distributionMode·ratingScale·forcedApplied UiBadge) +
 * 시뮬레이션 실행 → proposed 테이블(현재≠제안 행 강조) +
 * 강제 적용(확인 모달 — "review 등급 일괄 변경" 경고 + applied/skipped 결과) + simulationLog 이력.
 *
 * 계약 §6/§7. simulate=무저장 순수 계산 / apply=일괄 변경 + RatingDistribution upsert.
 * 등급·분포 계산은 BE 응답 표시만 (FE 재계산 금지). STD-FE 5 정합.
 */
import { useEffect, useState } from 'react';
import { Group, Modal, Stack, Text } from '@easy/ui-components/mantine';
import { IconPlayerPlay, IconAlertTriangle } from '@tabler/icons-react';
import {
  PerformanceChangedTableRow,
  PerformanceLogEntryCard,
} from '@easy/ui-components/performance';
import {
  UiBadge,
  UiAlert,
  UiButton,
  UiTable,
  PageHeader,
  SectionCard,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';
import { showToast } from '../shared/toast';

import {
  formatRatio,
  useApplyDistributionMutation,
  useDistributionQuery,
  useSimulateDistributionMutation,
  type DistributionSimulationResponse,
  type SimulationEntry,
} from '../api/calibration';
import { formatScore } from '../api/reviews';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleSelect } from './kpi/CycleSelect';
import { DistributionBars } from './calibration/DistributionBars';
import { GradeBadge } from './calibration/GradeBadge';
import { mapCalibrationErrorToMessage } from './calibration/errorMapping';

export function HrDistributionPage(): React.ReactNode {
  const t = useT();
  const [cycleId, setCycleId] = useState<string | null>(null);

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.distribution.page.title}
        description={t.distribution.page.description}
      />
      <SectionCard>
        <CycleSelect value={cycleId} onChange={setCycleId} />
      </SectionCard>

      {!cycleId ? (
        <SectionCard>
          <Text c="dimmed" size="sm">
            {t.distribution.page.needCycle}
          </Text>
        </SectionCard>
      ) : (
        <DistributionPanel cycleId={cycleId} />
      )}
    </ErrorBoundary>
  );
}

interface PanelProps {
  cycleId: string;
}

function DistributionPanel({ cycleId }: PanelProps): React.ReactNode {
  const t = useT();
  const distributionQuery = useDistributionQuery(cycleId);
  const distribution = distributionQuery.data ?? null;

  const simulateMut = useSimulateDistributionMutation(cycleId);
  const applyMut = useApplyDistributionMutation(cycleId);

  const [simResult, setSimResult] = useState<DistributionSimulationResponse | null>(
    null,
  );
  const [applyOpen, setApplyOpen] = useState(false);

  // cycle 바뀌면 시뮬레이션 결과 폐기 (단, 컴포넌트 key 로도 보호).
  useEffect(() => {
    setSimResult(null);
  }, [cycleId]);

  const handleSimulate = (): void => {
    simulateMut.mutate(
      { targetDistribution: null },
      {
        onSuccess: (res) => {
          setSimResult(res);
        },
        onError: (err) => {
          showToast({
            tone: 'danger',
            message: mapCalibrationErrorToMessage(err, t),
          });
        },
      },
    );
  };

  const handleApply = (): void => {
    applyMut.mutate(
      { targetDistribution: null },
      {
        onSuccess: (res) => {
          setApplyOpen(false);
          setSimResult(null);
          showToast({
            tone: 'success',
            message: t.distribution.apply.result
              .replace('{applied}', String(res.appliedCount))
              .replace('{skipped}', String(res.skippedCount)),
          });
        },
        onError: (err) => {
          setApplyOpen(false);
          showToast({
            tone: 'danger',
            message: mapCalibrationErrorToMessage(err, t),
          });
        },
      },
    );
  };

  if (distributionQuery.isError) {
    return (
      <SectionCard>
        <Text c="red">
          {t.common.message.loadError}:{' '}
          {getErrorMessage(distributionQuery.error)}
        </Text>
      </SectionCard>
    );
  }
  if (distributionQuery.isLoading) {
    return (
      <SectionCard>
        <LoadingState message={t.common.status.loading} />
      </SectionCard>
    );
  }
  if (!distribution) return null;

  // ABSOLUTE 모드 또는 비-S_A_B_C_D 척도는 강제 배분 불가 (BE 가 E9804248/E9804249 거부).
  const forcedSupported =
    distribution.distributionMode !== 'ABSOLUTE' &&
    distribution.ratingScale === 'S_A_B_C_D';

  return (
    <>
      <SectionCard>
        <Stack>
          <Group gap="xs">
            <UiBadge variant="light" color="blue">
              {t.distribution.policy.mode}:{' '}
              {distribution.distributionMode}
            </UiBadge>
            <UiBadge variant="light" color="grape">
              {t.distribution.policy.scale}: {distribution.ratingScale}
            </UiBadge>
            <UiBadge
              variant={distribution.forcedApplied ? 'filled' : 'outline'}
              color={distribution.forcedApplied ? 'teal' : 'gray'}
            >
              {distribution.forcedApplied
                ? t.distribution.policy.applied
                : t.distribution.policy.notApplied}
            </UiBadge>
            <UiBadge variant="light" color="gray">
              {t.distribution.policy.ready}: {distribution.calibrationReadyCount}
            </UiBadge>
          </Group>

          <DistributionBars
            current={distribution.currentDistribution}
            target={distribution.targetDistribution}
          />

          {!forcedSupported && (
            <UiAlert color="yellow" variant="light">
              {t.distribution.page.notSupported}
            </UiAlert>
          )}

          <Group justify="flex-end">
            <UiButton
              leftSection={<IconPlayerPlay size={16} />}
              variant="default"
              onClick={handleSimulate}
              loading={simulateMut.isPending}
              disabled={!forcedSupported}
            >
              {t.distribution.action.simulate}
            </UiButton>
            <UiButton
              leftSection={<IconAlertTriangle size={16} />}
              color="orange"
              onClick={() => setApplyOpen(true)}
              disabled={!forcedSupported}
            >
              {t.distribution.action.apply}
            </UiButton>
          </Group>
        </Stack>
      </SectionCard>

      {simResult && (
        <SectionCard>
          <ProposedTable result={simResult} />
        </SectionCard>
      )}

      {distribution.simulationLog && distribution.simulationLog.length > 0 && (
        <SectionCard>
          <SimulationLogPanel log={distribution.simulationLog} />
        </SectionCard>
      )}

      <Modal
        opened={applyOpen}
        onClose={() => {
          if (!applyMut.isPending) setApplyOpen(false);
        }}
        title={t.distribution.apply.title}
        centered
        size="md"
      >
        <Stack>
          <UiAlert color="orange" variant="light" icon={<IconAlertTriangle size={18} />}>
            {t.distribution.apply.warning}
          </UiAlert>
          <Text size="sm">
            {t.distribution.apply.confirmText.replace(
              '{count}',
              String(distribution.calibrationReadyCount),
            )}
          </Text>
          <Group justify="flex-end" mt="sm">
            <UiButton
              variant="default"
              onClick={() => setApplyOpen(false)}
              disabled={applyMut.isPending}
            >
              {t.common.action.cancel}
            </UiButton>
            <UiButton color="orange" onClick={handleApply} loading={applyMut.isPending}>
              {t.distribution.action.apply}
            </UiButton>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

interface ProposedTableProps {
  result: DistributionSimulationResponse;
}

function ProposedTable({ result }: ProposedTableProps): React.ReactNode {
  const t = useT();
  return (
    <Stack gap="xs">
      <Group justify="space-between">
        <Text fw={600} size="sm">
          {t.distribution.proposed.heading}
        </Text>
        <Group gap="xs">
          {(['S', 'A', 'B', 'C', 'D'] as const).map((g) => (
            <UiBadge key={g} variant="outline" color="gray" size="sm">
              {g} {result.resultingDistribution[g] ?? 0}
            </UiBadge>
          ))}
        </Group>
      </Group>

      {result.proposed.length === 0 ? (
        <Text size="sm" c="dimmed">
          {t.distribution.proposed.empty}
        </Text>
      ) : (
        <UiTable striped highlightOnHover>
          <UiTable.Thead>
            <UiTable.Tr>
              <UiTable.Th>{t.distribution.proposed.col.employeeId}</UiTable.Th>
              <UiTable.Th>{t.distribution.proposed.col.kpiScore}</UiTable.Th>
              <UiTable.Th>{t.distribution.proposed.col.currentGrade}</UiTable.Th>
              <UiTable.Th>{t.distribution.proposed.col.proposedGrade}</UiTable.Th>
            </UiTable.Tr>
          </UiTable.Thead>
          <UiTable.Tbody>
            {result.proposed.map((row) => {
              const changed = row.currentGrade !== row.proposedGrade;
              return (
                <PerformanceChangedTableRow key={row.reviewId} changed={changed}>
                  <UiTable.Td>
                    <Text size="sm" ff="monospace">
                      {row.employeeId}
                    </Text>
                  </UiTable.Td>
                  <UiTable.Td>
                    <Text size="sm">{formatScore(row.kpiScore)}</Text>
                  </UiTable.Td>
                  <UiTable.Td>
                    <GradeBadge grade={row.currentGrade} />
                  </UiTable.Td>
                  <UiTable.Td>
                    <Group gap={4}>
                      <GradeBadge grade={row.proposedGrade} />
                      {changed && (
                        <UiBadge size="xs" variant="light" color="orange">
                          {t.distribution.proposed.changed}
                        </UiBadge>
                      )}
                    </Group>
                  </UiTable.Td>
                </PerformanceChangedTableRow>
              );
            })}
          </UiTable.Tbody>
        </UiTable>
      )}
    </Stack>
  );
}

interface LogPanelProps {
  log: SimulationEntry[];
}

function SimulationLogPanel({ log }: LogPanelProps): React.ReactNode {
  const t = useT();
  // 역순 (최신 먼저).
  const entries = [...log].reverse();
  return (
    <Stack gap="xs">
      <Text fw={600} size="sm">
        {t.distribution.log.heading}
      </Text>
      {entries.map((entry, idx) => (
        <PerformanceLogEntryCard
          key={`${entry.at}-${idx}`}
          primary={
              <Text size="sm" fw={500}>
                {t.distribution.log.applied.replace(
                  '{count}',
                  String(entry.appliedCount),
                )}
              </Text>
          }
          secondary={
              <Text size="xs" c="dimmed">
                {t.distribution.log.skipped.replace(
                  '{count}',
                  String(entry.skippedCount),
                )}
              </Text>
          }
          timestamp={formatDateTime(entry.at)}
          details={(['S', 'A', 'B', 'C', 'D'] as const).map((g) => (
              <Text key={g} size="xs" c="dimmed">
                {g} {entry.resultingDistribution[g] ?? 0} (
                {formatRatio(entry.targetDistribution[g])})
              </Text>
          ))}
        />
      ))}
    </Stack>
  );
}

/** ISO timestamptz → 표시 문자열. null → '—'. */
function formatDateTime(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}

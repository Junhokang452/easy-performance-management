/**
 * HrDistributionPage (#24) — HR 분포 시뮬레이터 (`/hr/distribution`).
 *
 * cycle Select → 분포 비교 막대(현재 vs 목표) + 정책 정보(distributionMode·ratingScale·forcedApplied Badge) +
 * 시뮬레이션 실행 → proposed 테이블(현재≠제안 행 강조) +
 * 강제 적용(확인 모달 — "review 등급 일괄 변경" 경고 + applied/skipped 결과) + simulationLog 이력.
 *
 * 계약 §6/§7. simulate=무저장 순수 계산 / apply=일괄 변경 + RatingDistribution upsert.
 * 등급·분포 계산은 BE 응답 표시만 (FE 재계산 금지). STD-FE 5 정합.
 */
import { useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  Stack,
  Table,
  Text,
} from '@easy/ui-components/mantine';
import { notifications } from '@mantine/notifications';
import { IconPlayerPlay, IconAlertTriangle } from '@tabler/icons-react';
import {
  PerformanceChangedTableRow,
  PerformanceLogEntryCard,
} from '@easy/ui-components/performance';
import {
  PageHeader,
  SectionCard,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';

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
          notifications.show({
            color: 'red',
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
          notifications.show({
            color: 'green',
            message: t.distribution.apply.result
              .replace('{applied}', String(res.appliedCount))
              .replace('{skipped}', String(res.skippedCount)),
          });
        },
        onError: (err) => {
          setApplyOpen(false);
          notifications.show({
            color: 'red',
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
            <Badge variant="light" color="blue">
              {t.distribution.policy.mode}:{' '}
              {distribution.distributionMode}
            </Badge>
            <Badge variant="light" color="grape">
              {t.distribution.policy.scale}: {distribution.ratingScale}
            </Badge>
            <Badge
              variant={distribution.forcedApplied ? 'filled' : 'outline'}
              color={distribution.forcedApplied ? 'teal' : 'gray'}
            >
              {distribution.forcedApplied
                ? t.distribution.policy.applied
                : t.distribution.policy.notApplied}
            </Badge>
            <Badge variant="light" color="gray">
              {t.distribution.policy.ready}: {distribution.calibrationReadyCount}
            </Badge>
          </Group>

          <DistributionBars
            current={distribution.currentDistribution}
            target={distribution.targetDistribution}
          />

          {!forcedSupported && (
            <Alert color="yellow" variant="light">
              {t.distribution.page.notSupported}
            </Alert>
          )}

          <Group justify="flex-end">
            <Button
              leftSection={<IconPlayerPlay size={16} />}
              variant="default"
              onClick={handleSimulate}
              loading={simulateMut.isPending}
              disabled={!forcedSupported}
            >
              {t.distribution.action.simulate}
            </Button>
            <Button
              leftSection={<IconAlertTriangle size={16} />}
              color="orange"
              onClick={() => setApplyOpen(true)}
              disabled={!forcedSupported}
            >
              {t.distribution.action.apply}
            </Button>
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
          <Alert color="orange" variant="light" icon={<IconAlertTriangle size={18} />}>
            {t.distribution.apply.warning}
          </Alert>
          <Text size="sm">
            {t.distribution.apply.confirmText.replace(
              '{count}',
              String(distribution.calibrationReadyCount),
            )}
          </Text>
          <Group justify="flex-end" mt="sm">
            <Button
              variant="default"
              onClick={() => setApplyOpen(false)}
              disabled={applyMut.isPending}
            >
              {t.common.action.cancel}
            </Button>
            <Button color="orange" onClick={handleApply} loading={applyMut.isPending}>
              {t.distribution.action.apply}
            </Button>
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
            <Badge key={g} variant="outline" color="gray" size="sm">
              {g} {result.resultingDistribution[g] ?? 0}
            </Badge>
          ))}
        </Group>
      </Group>

      {result.proposed.length === 0 ? (
        <Text size="sm" c="dimmed">
          {t.distribution.proposed.empty}
        </Text>
      ) : (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t.distribution.proposed.col.employeeId}</Table.Th>
              <Table.Th>{t.distribution.proposed.col.kpiScore}</Table.Th>
              <Table.Th>{t.distribution.proposed.col.currentGrade}</Table.Th>
              <Table.Th>{t.distribution.proposed.col.proposedGrade}</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {result.proposed.map((row) => {
              const changed = row.currentGrade !== row.proposedGrade;
              return (
                <PerformanceChangedTableRow key={row.reviewId} changed={changed}>
                  <Table.Td>
                    <Text size="sm" ff="monospace">
                      {row.employeeId}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{formatScore(row.kpiScore)}</Text>
                  </Table.Td>
                  <Table.Td>
                    <GradeBadge grade={row.currentGrade} />
                  </Table.Td>
                  <Table.Td>
                    <Group gap={4}>
                      <GradeBadge grade={row.proposedGrade} />
                      {changed && (
                        <Badge size="xs" variant="light" color="orange">
                          {t.distribution.proposed.changed}
                        </Badge>
                      )}
                    </Group>
                  </Table.Td>
                </PerformanceChangedTableRow>
              );
            })}
          </Table.Tbody>
        </Table>
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

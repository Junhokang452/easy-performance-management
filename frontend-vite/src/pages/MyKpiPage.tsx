/**
 * MyKpiPage (#2) — 내 KPI (`/my/kpi`).
 *
 * cycle Select + employeeId 입력(SelfEvaluationPage 패턴 — principal 주입은 P0-S6 이후) →
 * my assignments 카드/테이블 (effective weight·target·최신 실적·달성률) +
 * 실적 입력 모달 + 실적 이력 모달(supersede 정정).
 *
 * 계약 §4: GET /kpi-assignments/my?cycleId=&employeeId= (둘 다 필수).
 *
 * STD-FE 5 정합 — LAZY(App.tsx) / RQ(useQuery+useMutation) / STRICT / ERROR-BOUNDARY / NEST.
 */
import { useState } from 'react';
import { Group, Stack, Text, TextInput } from '@easy/ui-components/mantine';
import { IconSearch } from '@tabler/icons-react';
import {
  UiBadge,
  UiButton,
  UiTable,
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';

import {
  formatAchievementRate,
  formatWeight,
  useMyKpiAssignmentsQuery,
  type MyKpiAssignmentResponse,
} from '../api/kpi';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleSelect } from './kpi/CycleSelect';
import { ActualFormModal } from './kpi/ActualFormModal';
import { ActualHistoryModal } from './kpi/ActualHistoryModal';

export function MyKpiPage(): React.ReactNode {
  const t = useT();

  // 입력(폼)과 조회(applied) 상태 분리 — '조회' 클릭 시 확정 (SelfEvaluation employeeId 패턴).
  const [cycleId, setCycleId] = useState<string | null>(null);
  const [employeeIdInput, setEmployeeIdInput] = useState('');
  const [applied, setApplied] = useState<{
    cycleId: string;
    employeeId: string;
  } | null>(null);

  const { data, isLoading, isError, error, isFetching } =
    useMyKpiAssignmentsQuery(applied?.cycleId, applied?.employeeId);

  const [reportTarget, setReportTarget] =
    useState<MyKpiAssignmentResponse | null>(null);
  const [historyTarget, setHistoryTarget] =
    useState<MyKpiAssignmentResponse | null>(null);

  const canLoad = Boolean(cycleId) && employeeIdInput.trim().length > 0;
  const rows = data ?? [];

  const handleLoad = (): void => {
    if (!cycleId || !employeeIdInput.trim()) return;
    setApplied({ cycleId, employeeId: employeeIdInput.trim() });
  };

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.kpi.my.title}
        description={t.kpi.my.description}
      />
      <SectionCard>
        <Stack>
          <Group align="end" gap="md">
            <CycleSelect value={cycleId} onChange={setCycleId} />
            <TextInput
              label={t.kpi.my.employeeId}
              placeholder={t.kpi.my.employeeIdPlaceholder}
              value={employeeIdInput}
              onChange={(e) => setEmployeeIdInput(e.currentTarget.value)}
              w={320}
            />
            <UiButton
              leftSection={<IconSearch size={16} />}
              onClick={handleLoad}
              disabled={!canLoad}
              loading={isFetching && Boolean(applied)}
            >
              {t.kpi.my.load}
            </UiButton>
          </Group>

          {!applied ? (
            <Text c="dimmed" size="sm">
              {t.kpi.my.needInput}
            </Text>
          ) : isError ? (
            <Text c="red">
              {t.common.message.loadError}: {getErrorMessage(error)}
            </Text>
          ) : isLoading ? (
            <LoadingState message={t.common.status.loading} />
          ) : rows.length === 0 ? (
            <EmptyState title={t.kpi.my.empty} description={t.kpi.my.emptyHint} />
          ) : (
            <UiTable striped highlightOnHover>
              <UiTable.Thead>
                <UiTable.Tr>
                  <UiTable.Th>{t.kpi.my.col.node}</UiTable.Th>
                  <UiTable.Th>{t.kpi.my.col.tree}</UiTable.Th>
                  <UiTable.Th>{t.kpi.my.col.weight}</UiTable.Th>
                  <UiTable.Th>{t.kpi.my.col.target}</UiTable.Th>
                  <UiTable.Th>{t.kpi.my.col.latestActual}</UiTable.Th>
                  <UiTable.Th>{t.kpi.my.col.achievementRate}</UiTable.Th>
                  <UiTable.Th />
                </UiTable.Tr>
              </UiTable.Thead>
              <UiTable.Tbody>
                {rows.map((row) => (
                  <UiTable.Tr key={row.id}>
                    <UiTable.Td>
                      <Group gap={6}>
                        <Text size="sm" fw={500}>
                          {row.nodeLabel}
                        </Text>
                        {row.bscPerspective && (
                          <UiBadge size="xs" variant="outline" color="grape">
                            {t.kpi.bscPerspective[row.bscPerspective]}
                          </UiBadge>
                        )}
                      </Group>
                    </UiTable.Td>
                    <UiTable.Td>
                      <Text size="sm" c="dimmed">
                        {row.treeName}
                      </Text>
                    </UiTable.Td>
                    <UiTable.Td>
                      <Text size="sm">{formatWeight(row.weight)}</Text>
                    </UiTable.Td>
                    <UiTable.Td>
                      <Text size="sm">
                        {row.target != null
                          ? `${row.target}${row.unit ? ` ${row.unit}` : ''}`
                          : '—'}
                      </Text>
                    </UiTable.Td>
                    <UiTable.Td>
                      {row.latestActualValue != null ? (
                        <Group gap={6}>
                          <Text size="sm">{row.latestActualValue}</Text>
                          {row.latestActualAsOfDate && (
                            <Text size="xs" c="dimmed">
                              ({row.latestActualAsOfDate})
                            </Text>
                          )}
                        </Group>
                      ) : (
                        <Text size="sm" c="dimmed">
                          —
                        </Text>
                      )}
                    </UiTable.Td>
                    <UiTable.Td>
                      <AchievementBadge rate={row.achievementRate} />
                    </UiTable.Td>
                    <UiTable.Td>
                      <Group gap={6} justify="flex-end">
                        <UiButton
                          size="xs"
                          variant="light"
                          onClick={() => setReportTarget(row)}
                        >
                          {t.kpi.my.reportActual}
                        </UiButton>
                        <UiButton
                          size="xs"
                          variant="subtle"
                          onClick={() => setHistoryTarget(row)}
                        >
                          {t.kpi.my.actualHistory}
                        </UiButton>
                      </Group>
                    </UiTable.Td>
                  </UiTable.Tr>
                ))}
              </UiTable.Tbody>
            </UiTable>
          )}
        </Stack>
      </SectionCard>

      {reportTarget && applied && (
        <ActualFormModal
          opened={Boolean(reportTarget)}
          onClose={() => setReportTarget(null)}
          assignmentId={reportTarget.id}
          cycleId={applied.cycleId}
          mode={{ kind: 'report' }}
        />
      )}
      {historyTarget && applied && (
        <ActualHistoryModal
          opened={Boolean(historyTarget)}
          onClose={() => setHistoryTarget(null)}
          assignmentId={historyTarget.id}
          cycleId={applied.cycleId}
        />
      )}
    </ErrorBoundary>
  );
}

function AchievementBadge({ rate }: { rate: number | null }): React.ReactNode {
  if (rate == null) {
    return (
      <Text size="sm" c="dimmed">
        —
      </Text>
    );
  }
  const color = rate >= 1 ? 'green' : rate >= 0.7 ? 'yellow' : 'red';
  return (
    <UiBadge color={color} variant="light">
      {formatAchievementRate(rate)}
    </UiBadge>
  );
}

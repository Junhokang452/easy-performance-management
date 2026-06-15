/**
 * ActualHistoryModal — KPI 실적 이력 모달 (My KPI 화면).
 *
 * - GET /kpi-assignments/{id}/actuals (asOfDate DESC)
 * - superseded 행은 '정정됨' 뱃지, 최신 유효 행은 '최신' 뱃지 + '정정' 버튼
 * - '정정' 클릭 → ActualFormModal(correct) 로 supersede (신규 row 생성)
 *
 * 계약 §4: 이미 supersede 된 actual 재정정 = E9804925 (BE 차단). FE 는 superseded 행에 정정 버튼 비노출.
 */
import { useState } from 'react';
import { Center, Group, Modal, Text } from '@easy/ui-components/mantine';

import {
  useAssignmentActualsQuery,
  type KpiActualResponse,
} from '../../api/kpi';
import { useT } from '../../i18n';
import { getErrorMessage } from '../../api/error';
import { ActualFormModal } from './ActualFormModal';
import { UiBadge, UiButton, UiLoader, UiTable } from '@easy/ui-components';

interface Props {
  opened: boolean;
  onClose: () => void;
  assignmentId: string;
  cycleId: string;
}

export function ActualHistoryModal({
  opened,
  onClose,
  assignmentId,
  cycleId,
}: Props): React.ReactNode {
  const t = useT();
  const { data, isLoading, isError, error } = useAssignmentActualsQuery(
    opened ? assignmentId : null,
  );
  const [correctTarget, setCorrectTarget] = useState<KpiActualResponse | null>(
    null,
  );

  const rows = data ?? [];

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={t.kpi.actual.history}
      size="xl"
      centered
    >
      {isError ? (
        <Text c="red">
          {t.common.message.loadError}: {getErrorMessage(error)}
        </Text>
      ) : isLoading ? (
        <Center mih={120}>
          <UiLoader />
        </Center>
      ) : rows.length === 0 ? (
        <Text c="dimmed" size="sm" py="md">
          {t.kpi.actual.empty}
        </Text>
      ) : (
        <UiTable striped highlightOnHover>
          <UiTable.Thead>
            <UiTable.Tr>
              <UiTable.Th>{t.kpi.actual.asOfDate}</UiTable.Th>
              <UiTable.Th>{t.kpi.actual.actualValue}</UiTable.Th>
              <UiTable.Th>{t.kpi.actual.comment}</UiTable.Th>
              <UiTable.Th>{t.kpi.actual.reportedAt}</UiTable.Th>
              <UiTable.Th />
            </UiTable.Tr>
          </UiTable.Thead>
          <UiTable.Tbody>
            {rows.map((row) => (
              <UiTable.Tr key={row.id}>
                <UiTable.Td>
                  <Text size="sm">{row.asOfDate}</Text>
                </UiTable.Td>
                <UiTable.Td>
                  <Text size="sm">{row.actualValue}</Text>
                </UiTable.Td>
                <UiTable.Td>
                  <Text size="sm" c="dimmed">
                    {row.comment ?? '—'}
                  </Text>
                </UiTable.Td>
                <UiTable.Td>
                  <Text size="xs" c="dimmed">
                    {formatInstant(row.createdAt)}
                  </Text>
                </UiTable.Td>
                <UiTable.Td>
                  <Group gap={6} justify="flex-end">
                    {row.superseded ? (
                      <UiBadge color="gray" variant="light">
                        {t.kpi.actual.superseded}
                      </UiBadge>
                    ) : (
                      <>
                        <UiBadge color="teal" variant="light">
                          {t.kpi.actual.latest}
                        </UiBadge>
                        <UiButton
                          size="xs"
                          variant="subtle"
                          onClick={() => setCorrectTarget(row)}
                        >
                          {t.kpi.actual.correct}
                        </UiButton>
                      </>
                    )}
                  </Group>
                </UiTable.Td>
              </UiTable.Tr>
            ))}
          </UiTable.Tbody>
        </UiTable>
      )}

      {correctTarget && (
        <ActualFormModal
          opened={Boolean(correctTarget)}
          onClose={() => setCorrectTarget(null)}
          assignmentId={assignmentId}
          cycleId={cycleId}
          mode={{ kind: 'correct', original: correctTarget }}
        />
      )}
    </Modal>
  );
}

function formatInstant(iso: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}

import { useDeferredValue, useEffect, useMemo, useState } from 'react';
import { Box, Group, Stack, Text } from '@easy/ui-components/mantine';
import { IconPlus, IconUsers } from '@tabler/icons-react';
import {
  EmptyState,
  LoadingState,
  LookupMultiSelect,
  LookupSelect,
  SectionCard,
  UiAlert,
  UiBadge,
  UiButton,
  UiTable,
} from '@easy/ui-components';

import type { CycleStatus } from '../../../api/cycles';
import {
  type Participant,
  useAdvanceCycleMutation,
  useCloseCycleMutation,
  useEmployeeDirectoryQuery,
  useOpenCycleMutation,
  useParticipantsQuery,
  usePublishReportsMutation,
  useUpsertParticipantsMutation,
} from '../../../api/evaluationWorkspace';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import { employeeLabel, statusLabel } from '../workspaceLabels';
import { workspaceErrorMessage } from '../workspaceError';
import { nextWorkflowStatus } from '../workflowRules';

export function OperationsPanel({
  cycleId,
  status,
}: {
  cycleId: string;
  status: CycleStatus;
}): React.ReactNode {
  const t = useT();
  const openMutation = useOpenCycleMutation(cycleId);
  const advanceMutation = useAdvanceCycleMutation(cycleId);
  const publishMutation = usePublishReportsMutation(cycleId);
  const closeMutation = useCloseCycleMutation(cycleId);
  const [confirmingOpen, setConfirmingOpen] = useState(false);
  const nextStatus = nextWorkflowStatus(status);
  const pending = openMutation.isPending
    || advanceMutation.isPending
    || publishMutation.isPending
    || closeMutation.isPending;
  const reportError = (error: unknown): void =>
    showToast({ tone: 'danger', message: workspaceErrorMessage(t, error) });

  return (
    <SectionCard>
      <Stack>
        <Group justify="space-between" align="flex-start">
          <Stack gap={2}>
            <Text fw={600}>{t.workspace.operations}</Text>
            <Text size="sm" c="dimmed">{t.workspace.copy.operationDescription}</Text>
          </Stack>
          <Group>
            {(status === 'PLANNED' || status === 'ACTIVE') && !confirmingOpen && (
              <UiButton loading={pending} onClick={() => setConfirmingOpen(true)}>
                {t.workspace.open}
              </UiButton>
            )}
          {nextStatus && (
            <UiButton
              loading={pending}
              onClick={() => advanceMutation.mutate(nextStatus, {
                onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.advanced }),
                onError: reportError,
              })}
            >
              {t.workspace.next}: {statusLabel(t, nextStatus)}
            </UiButton>
          )}
          {status === 'CALIBRATION' && (
            <>
              <UiButton
                variant="light"
                loading={pending}
                onClick={() => publishMutation.mutate(undefined, {
                  onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.published }),
                  onError: reportError,
                })}
              >
                {t.workspace.publish}
              </UiButton>
              <UiButton
                loading={pending}
                onClick={() => closeMutation.mutate(undefined, {
                  onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.closed }),
                  onError: reportError,
                })}
              >
                {t.workspace.close}
              </UiButton>
            </>
          )}
          </Group>
        </Group>
        {confirmingOpen && (
          <UiAlert color="yellow" title={t.workspace.copy.openConfirmTitle}>
            <Stack gap="sm">
              <Text size="sm">{t.workspace.copy.openConfirmBody}</Text>
              <Group justify="flex-end">
                <UiButton variant="subtle" onClick={() => setConfirmingOpen(false)}>
                  {t.common.action.cancel}
                </UiButton>
                <UiButton
                  loading={openMutation.isPending}
                  onClick={() => openMutation.mutate(undefined, {
                    onSuccess: () => {
                      setConfirmingOpen(false);
                      showToast({ tone: 'success', message: t.workspace.copy.opened });
                    },
                    onError: reportError,
                  })}
                >
                  {t.workspace.open}
                </UiButton>
              </Group>
            </Stack>
          </UiAlert>
        )}
      </Stack>
    </SectionCard>
  );
}

export function ParticipantPanel({
  cycleId,
  editable,
}: {
  cycleId: string;
  editable: boolean;
}): React.ReactNode {
  const t = useT();
  const participantsQuery = useParticipantsQuery(cycleId);
  const upsertMutation = useUpsertParticipantsMutation(cycleId);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [managerId, setManagerId] = useState<string | null>(null);
  const [participantSearch, setParticipantSearch] = useState('');
  const [managerSearch, setManagerSearch] = useState('');
  const participantDirectoryQuery = useEmployeeDirectoryQuery(useDeferredValue(participantSearch));
  const managerDirectoryQuery = useEmployeeDirectoryQuery(useDeferredValue(managerSearch));
  const participants = useMemo(() => participantsQuery.data?.content ?? [], [participantsQuery.data]);

  useEffect(() => {
    if (participants.length > 0) {
      setSelectedIds(participants.filter((item) => item.status === 'ACTIVE').map((item) => item.employee.id));
    }
  }, [participants]);

  const knownEmployees = useMemo(() => {
    const byId = new Map<string, Participant['employee']>();
    participants.forEach((participant) => {
      byId.set(participant.employee.id, participant.employee);
      if (participant.manager) byId.set(participant.manager.id, participant.manager);
    });
    participantDirectoryQuery.data?.content.forEach((employee) => byId.set(employee.id, employee));
    managerDirectoryQuery.data?.content.forEach((employee) => byId.set(employee.id, employee));
    return [...byId.values()];
  }, [managerDirectoryQuery.data, participantDirectoryQuery.data, participants]);

  const employeeOptions = knownEmployees
    .filter((employee) => employee.status === 'ACTIVE')
    .map((employee) => ({ value: employee.id, label: employeeLabel(employee) }));

  const assign = (): void => {
    if (!managerId) return;
    upsertMutation.mutate(
      selectedIds.map((employeeId) => ({ employeeId, managerEmployeeId: managerId })),
      {
        onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.assigned }),
        onError: (error) => showToast({ tone: 'danger', message: workspaceErrorMessage(t, error) }),
      },
    );
  };

  return (
    <SectionCard>
      <Stack>
        <Group justify="space-between">
          <Group gap="xs">
            <IconUsers size={18} aria-hidden />
            <Text fw={600}>{t.workspace.participants}</Text>
          </Group>
          <UiBadge variant="light">
            {participants.length}{t.workspace.copy.peopleSuffix}
          </UiBadge>
        </Group>
        {editable && (
          <>
            <Text size="sm" c="dimmed">{t.workspace.copy.assignmentHint}</Text>
            <Group align="end" wrap="wrap">
              <Box w={{ base: '100%', sm: 360 }}>
                <LookupMultiSelect
                  label={t.workspace.participantSelect}
                  placeholder={t.workspace.copy.employeeNamePlaceholder}
                  nothingFoundMessage={t.workspace.copy.noEmployeeMatches}
                  data={employeeOptions}
                  value={selectedIds}
                  onChange={setSelectedIds}
                  searchValue={participantSearch}
                  onSearchChange={setParticipantSearch}
                />
              </Box>
              <Box w={{ base: '100%', sm: 280 }}>
                <LookupSelect
                  label={t.workspace.reviewerSelect}
                  placeholder={t.workspace.copy.reviewerPlaceholder}
                  nothingFoundMessage={t.workspace.copy.noEmployeeMatches}
                  data={employeeOptions}
                  value={managerId}
                  onChange={setManagerId}
                  searchValue={managerSearch}
                  onSearchChange={setManagerSearch}
                />
              </Box>
              <UiButton
                leftSection={<IconPlus size={16} aria-hidden />}
                loading={upsertMutation.isPending}
                disabled={!managerId || selectedIds.length === 0}
                onClick={assign}
              >
                {t.workspace.assign}
              </UiButton>
            </Group>
          </>
        )}
        {(participantDirectoryQuery.isError || managerDirectoryQuery.isError) && (
          <UiAlert color="red">
            {workspaceErrorMessage(t, participantDirectoryQuery.error ?? managerDirectoryQuery.error)}
          </UiAlert>
        )}
        {participantsQuery.isError ? (
          <UiAlert color="red">{workspaceErrorMessage(t, participantsQuery.error)}</UiAlert>
        ) : participantsQuery.isLoading ? (
          <LoadingState message={t.workspace.copy.loadingParticipants} />
        ) : (
          <ParticipantTable participants={participants} />
        )}
      </Stack>
    </SectionCard>
  );
}

function ParticipantTable({ participants }: { participants: Participant[] }): React.ReactNode {
  const t = useT();
  if (participants.length === 0) {
    return (
      <EmptyState
        title={t.workspace.copy.noParticipantsTitle}
        description={t.workspace.copy.noParticipantsDescription}
      />
    );
  }
  return (
    <UiTable striped highlightOnHover>
      <UiTable.Thead>
        <UiTable.Tr>
          <UiTable.Th>{t.workspace.copy.member}</UiTable.Th>
          <UiTable.Th visibleFrom="sm">{t.workspace.copy.org}</UiTable.Th>
          <UiTable.Th visibleFrom="sm">{t.workspace.reviewerSelect}</UiTable.Th>
          <UiTable.Th>{t.workspace.copy.reviewStatus}</UiTable.Th>
        </UiTable.Tr>
      </UiTable.Thead>
      <UiTable.Tbody>
        {participants.map((participant) => (
          <UiTable.Tr key={participant.id}>
            <UiTable.Td><Text size="sm" fw={500}>{employeeLabel(participant.employee)}</Text></UiTable.Td>
            <UiTable.Td visibleFrom="sm">{participant.employee.orgUnitName ?? t.workspace.copy.notSpecified}</UiTable.Td>
            <UiTable.Td visibleFrom="sm">
              {participant.manager ? employeeLabel(participant.manager) : t.workspace.copy.unassigned}
            </UiTable.Td>
            <UiTable.Td>
              <UiBadge color={participant.reviewStatus === 'FINALIZED' ? 'green' : 'blue'} variant="light">
                {statusLabel(t, participant.reviewStatus ?? participant.status)}
              </UiBadge>
            </UiTable.Td>
          </UiTable.Tr>
        ))}
      </UiTable.Tbody>
    </UiTable>
  );
}

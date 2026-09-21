import { ProgramAuditTimeline } from '../components/ProgramAuditTimeline';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  FormSelect, FormTextInput, LookupMultiSelect,
  MasterDetailWorkspace, PageHeader, PerformanceMetricGrid, SectionCard, UiBadge,
  UiButton, UiGroup, UiStack, UiText,
} from '@easy/ui-components';

import { getErrorMessage } from '../../../api/error';
import { useAuth } from '../../../auth/AuthProvider';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import { useEmployeeOptionsQuery } from '../api/resources';
import {
  useFinalizeProgramMutation, useGenerateParticipantsMutation, useOpenProgramMutation,
  useParticipantGoalsQuery, useProgramDashboardQuery, useProgramParticipantsQuery, useProgramQuery,
  usePublishResultsMutation, useRunCalculationMutation,
  useStartStageMutation, type ParticipantResponse, type ProgramResponse, type ProgramStage,
} from '../api/programs';
import { GoalKpiLinkageTools } from '../components/GoalKpiLinkageTools';
import { CancelFinalization, NotificationTools, ParticipantAdministration, RosterAndGuideTools } from '../components/OperationsUtilities';
import { ReviewerLineAutomationTools } from '../components/ReviewerLineAutomationTools';
import { ResponsibleReminderTools } from '../components/ResponsibleReminderTools';
import { programStageLabel, programStageStatusLabel } from '../components/programLabels';
import { QueryState } from '../components/QueryState';

const stages: ProgramStage[] = ['GOAL', 'INTERMEDIATE', 'SELF_REVIEW', 'REVIEW', 'CALCULATION', 'CALIBRATION', 'FEEDBACK'];

export function ProgramOperationsPage(): React.ReactNode {
  const { programId = '' } = useParams(); const t = useT();
  const { session } = useAuth();
  const program = useProgramQuery(programId || null); const participants = useProgramParticipantsQuery(programId || null); const dashboard = useProgramDashboardQuery(programId || null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const rows = participants.data ?? []; const selected = rows.find((row) => row.id === selectedId) ?? rows[0] ?? null;
  const canAutomateReviewerLine = session?.roles.some((role) => role === 'HR_ADMIN' || role === 'SUPER_ADMIN') ?? false;
  useEffect(() => { if (!selectedId && rows[0]) setSelectedId(rows[0].id); }, [rows, selectedId]);
  return <UiStack gap="md"><PageHeader title={program.data?.name ?? t.program.operations.title} description={t.program.operations.description} actions={<UiButton component={Link} to={`/admin/evaluation-programs/${programId}/setup`}>{t.program.programs.setup}</UiButton>} />
    {dashboard.data ? <PerformanceMetricGrid items={[{ label: t.program.operations.participants, value: dashboard.data.participantCount }, { label: t.program.operations.completed, value: Object.values(dashboard.data.completed).reduce((a, b) => a + (b ?? 0), 0) }, { label: t.program.operations.incomplete, value: Object.values(dashboard.data.inProgress).reduce((a, b) => a + (b ?? 0), 0) }, { label: t.program.operations.delayed, value: Object.values(dashboard.data.blocked).reduce((a, b) => a + (b ?? 0), 0) }]} /> : null}
    <QueryState pending={participants.isPending} error={participants.error} empty={rows.length === 0} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>
      <MasterDetailWorkspace listLabel={t.program.operations.queue} detailLabel={t.program.operations.detail} list={<UiStack gap="xs">{rows.map((row) => <UiButton key={row.id} variant={selected?.id === row.id ? 'light' : 'subtle'} onClick={() => setSelectedId(row.id)}><UiStack gap={2} align="stretch"><UiText size="sm" fw={600}>{row.employee.name}</UiText><UiText size="xs" c="dimmed">{row.employee.orgUnitName ?? t.workspace.copy.notSpecified} · {programStageLabel(t, row.currentStage, row.currentRound)}</UiText></UiStack></UiButton>)}</UiStack>}>
        {selected && program.data ? <ParticipantDetail participant={selected} program={program.data} /> : null}
      </MasterDetailWorkspace>
    </QueryState>
    {program.data && selected && canAutomateReviewerLine ? <GoalKpiLinkageSection program={program.data} participant={selected} /> : null}
    <OperationsActions programId={programId} status={program.data?.status} participantIds={rows.filter((row) => row.status === 'ACTIVE').map((row) => row.id)} />
    {program.data ? <RosterAndGuideTools program={program.data} /> : null}
    {program.data && canAutomateReviewerLine ? <ReviewerLineAutomationTools program={program.data} participants={rows} /> : null}
    {program.data && canAutomateReviewerLine ? <ResponsibleReminderTools program={program.data} participants={rows} /> : null}
    {program.data ? <NotificationTools program={program.data} participants={rows} /> : null}
    {program.data ? <CancelFinalization program={program.data} /> : null}
    {program.data ? <ProgramAuditTimeline key={programId} programId={programId} participants={rows} /> : null}
  </UiStack>;
}

function GoalKpiLinkageSection({ program, participant }: { program: ProgramResponse; participant: ParticipantResponse }): React.ReactNode {
  const t = useT();
  const goals = useParticipantGoalsQuery(participant.id);
  return <QueryState pending={goals.isPending} error={goals.error} empty={!goals.data?.length} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>
    <GoalKpiLinkageTools key={participant.id} program={program} participant={participant} goals={goals.data ?? []} />
  </QueryState>;
}

function OperationsActions({ programId, status, participantIds }: { programId: string; status?: string; participantIds: string[] }): React.ReactNode {
  const t = useT(); const [search, setSearch] = useState(''); const employees = useEmployeeOptionsQuery(search); const [employeeIds, setEmployeeIds] = useState<string[]>([]); const [stage, setStage] = useState<ProgramStage>('GOAL'); const [reason, setReason] = useState('');
  const generate = useGenerateParticipantsMutation(); const open = useOpenProgramMutation(); const start = useStartStageMutation(); const calculate = useRunCalculationMutation(); const finalize = useFinalizeProgramMutation(); const publish = usePublishResultsMutation();
  const run = async (action: () => Promise<unknown>): Promise<void> => { try { await action(); setReason(''); showToast({ tone: 'success', message: t.program.common.submitSuccess }); } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); } };
  const editable = status === 'DRAFT'; const inProgress = status === 'OPEN'; const finalized = status === 'FINALIZED';
  return <SectionCard title={t.program.operations.title}><UiStack gap="sm"><LookupMultiSelect label={t.program.operations.participants} value={employeeIds} onChange={setEmployeeIds} data={(employees.data ?? []).map((employee) => ({ value: employee.id, label: `${employee.name} · ${employee.employeeNo}` }))} searchValue={search} onSearchChange={setSearch} /><UiGroup><UiButton disabled={!editable || !employeeIds.length} loading={generate.isPending} onClick={() => void run(() => generate.mutateAsync({ id: programId, employeeIds }))}>{t.program.operations.participants}</UiButton>{editable ? <UiButton loading={open.isPending} onClick={() => void run(() => open.mutateAsync(programId))}>{t.program.programs.open}</UiButton> : null}</UiGroup><UiGroup align="end" wrap="wrap"><FormSelect disabled={!inProgress} label={t.program.programs.currentStage} value={stage} data={stages.map((value) => ({ value, label: programStageLabel(t, value) }))} onChange={(value) => value && setStage(value as ProgramStage)} /><FormTextInput disabled={!inProgress} label={t.program.operations.reason} value={reason} onChange={(event) => setReason(event.currentTarget.value)} /><UiButton disabled={!inProgress || !reason.trim() || !participantIds.length} loading={start.isPending} onClick={() => void run(() => start.mutateAsync({ id: programId, stage, participantIds, reason }))}>{t.program.stages.start}</UiButton><UiButton variant="light" disabled={!inProgress || !reason.trim() || !participantIds.length} loading={calculate.isPending} onClick={() => void run(() => calculate.mutateAsync({ id: programId, participantIds, excludeIncomplete: true, reason }))}>{t.program.operations.calculate}</UiButton><UiButton variant="light" disabled={!inProgress || !reason.trim()} loading={finalize.isPending} onClick={() => void run(() => finalize.mutateAsync({ id: programId, reason }))}>{t.program.operations.finalize}</UiButton><UiButton variant="light" disabled={!finalized || !participantIds.length} loading={publish.isPending} onClick={() => void run(() => publish.mutateAsync({ id: programId, participantIds }))}>{t.program.operations.publish}</UiButton></UiGroup></UiStack></SectionCard>;
}

function ParticipantDetail({ participant, program }: { participant: ParticipantResponse; program: NonNullable<ReturnType<typeof useProgramQuery>['data']> }): React.ReactNode {
  const t = useT();
  return <UiStack gap="md"><SectionCard title={participant.employee.name} description={participant.employee.orgUnitName ?? t.workspace.copy.notSpecified}><UiGroup><UiBadge>{programStageLabel(t, participant.currentStage, participant.currentRound)}</UiBadge><UiBadge variant="outline">{programStageStatusLabel(t, participant.stageStatus)}</UiBadge><UiBadge variant="outline">{participant.groupName ?? t.program.programs.groups}</UiBadge></UiGroup><UiButton mt="md" component={Link} to={`/admin/evaluation-programs/${participant.programId}/participants/${participant.id}/preview`}>{t.program.programs.preview}</UiButton></SectionCard><ParticipantAdministration participant={participant} program={program} /></UiStack>;
}

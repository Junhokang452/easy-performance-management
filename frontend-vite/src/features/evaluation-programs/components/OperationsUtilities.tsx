import { useEffect, useMemo, useState } from 'react';
import { FileButton } from '@easy/ui-components/mantine';
import {
  FormNumberInput, FormSelect, FormTextInput, FormTextarea, LookupSelect, SectionCard,
  UiButton, UiGroup, UiSimpleGrid, UiStack, UiText,
} from '@easy/ui-components';

import { getErrorMessage } from '../../../api/error';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import { useEmployeeAssignmentsQuery, useEmployeeOptionsQuery } from '../api/resources';
import {
  programsApi, useAddParticipantMutation, useCancelFinalizationMutation,
  useDispatchNotificationsMutation, useImportParticipantsMutation, useImportReviewersMutation,
  useOverrideStageMutation, useParticipantReviewersQuery, usePreviewNotificationsMutation,
  useQueueNotificationsMutation, useReplaceReviewersMutation, useUpdateParticipantMutation,
  useUploadGuideMutation, useProgramGuidesQuery, type NotificationChannel,
  type ParticipantResponse, type ProgramResponse, type ProgramStage, type ProgramStageStatus,
  type ReviewerInput, type ReviewerRole,
} from '../api/programs';
import { downloadBlob } from './downloadBlob';
import { programStageLabel, programStageStatusLabel } from './programLabels';

type Run = (work: () => Promise<unknown>, success?: string) => Promise<void>;

const stages: ProgramStage[] = ['GOAL', 'INTERMEDIATE', 'SELF_REVIEW', 'REVIEW', 'CALCULATION', 'CALIBRATION', 'FEEDBACK'];
const stageStatuses: ProgramStageStatus[] = ['NOT_STARTED', 'READY', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED', 'BLOCKED'];
const roles: ReviewerRole[] = ['AGREEMENT_REVIEWER', 'CHECKER', 'REVIEWER', 'ADJUSTER', 'FINAL_FEEDBACK'];

function roleLabel(t: ReturnType<typeof useT>, role: ReviewerRole): string {
  return ({ AGREEMENT_REVIEWER: t.program.reviewerRoles.agreement, CHECKER: t.program.reviewerRoles.checker, REVIEWER: t.program.reviewerRoles.reviewer, ADJUSTER: t.program.reviewerRoles.adjuster, FINAL_FEEDBACK: t.program.reviewerRoles.finalFeedback })[role];
}

export function RosterAndGuideTools({ program }: { program: ProgramResponse }): React.ReactNode {
  const t = useT();
  const [employeeSearch, setEmployeeSearch] = useState('');
  const [employeeId, setEmployeeId] = useState<string | null>(null);
  const [assignmentId, setAssignmentId] = useState<string | null>(null);
  const [assignmentSearch, setAssignmentSearch] = useState('');
  const [weight, setWeight] = useState<number | string>(100);
  const employees = useEmployeeOptionsQuery(employeeSearch);
  const assignments = useEmployeeAssignmentsQuery(employeeId);
  const guides = useProgramGuidesQuery(program.id);
  const add = useAddParticipantMutation();
  const importParticipants = useImportParticipantsMutation();
  const importReviewers = useImportReviewersMutation();
  const uploadGuide = useUploadGuideMutation();
  const run: Run = async (work, success = t.program.common.saveSuccess) => {
    try { await work(); showToast({ tone: 'success', message: success }); }
    catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); }
  };
  const editable = program.status === 'DRAFT';
  useEffect(() => setAssignmentId(null), [employeeId]);

  return <UiSimpleGrid cols={{ base: 1, xl: 2 }}>
    <SectionCard title={t.program.operations.participants}>
      <UiStack gap="sm">
        <UiGroup wrap="wrap">
          <UiButton variant="light" onClick={() => void run(async () => downloadBlob(await programsApi.exportParticipants(program.id), `${program.name}-participants.xlsx`))}>{t.program.files.exportParticipants}</UiButton>
          <FileButton disabled={!editable} accept=".xlsx" onChange={(file) => file && void run(() => importParticipants.mutateAsync({ id: program.id, file }))}>{(props) => <UiButton {...props} disabled={!editable} variant="light" loading={importParticipants.isPending}>{t.program.files.importParticipants}</UiButton>}</FileButton>
          <UiButton variant="light" onClick={() => void run(async () => downloadBlob(await programsApi.exportReviewers(program.id), `${program.name}-reviewers.xlsx`))}>{t.program.files.exportReviewers}</UiButton>
          <FileButton disabled={!editable} accept=".xlsx" onChange={(file) => file && void run(() => importReviewers.mutateAsync({ id: program.id, file }))}>{(props) => <UiButton {...props} disabled={!editable} variant="light" loading={importReviewers.isPending}>{t.program.files.importReviewers}</UiButton>}</FileButton>
        </UiGroup>
        <LookupSelect label={t.program.common.employee} value={employeeId} onChange={setEmployeeId} searchValue={employeeSearch} onSearchChange={setEmployeeSearch} data={(employees.data ?? []).map((employee) => ({ value: employee.id, label: `${employee.name} · ${employee.employeeNo}` }))} />
        <LookupSelect label={t.program.files.assignment} value={assignmentId} onChange={setAssignmentId} searchValue={assignmentSearch} onSearchChange={setAssignmentSearch} data={(assignments.data ?? []).filter((option) => !assignmentSearch || `${option.label} ${option.description ?? ''}`.toLocaleLowerCase().includes(assignmentSearch.toLocaleLowerCase())).map((option) => ({ value: option.value, label: option.description ? `${option.label} · ${option.description}` : option.label }))} />
        <FormNumberInput label={t.program.files.participantWeight} min={0.0001} max={100} value={weight} onChange={setWeight} />
        <UiButton disabled={!editable || !employeeId || !assignmentId} loading={add.isPending} onClick={() => employeeId && assignmentId && void run(() => add.mutateAsync({ id: program.id, input: { employeeId, assignmentId, weightPercent: Number(weight) } }))}>{t.program.files.addParticipant}</UiButton>
      </UiStack>
    </SectionCard>
    <SectionCard title={t.program.files.guides}>
      <UiStack gap="sm">
        <FileButton disabled={!editable} accept=".pdf,.doc,.docx,.ppt,.pptx,.xlsx" onChange={(file) => file && void run(() => uploadGuide.mutateAsync({ id: program.id, file }))}>{(props) => <UiButton {...props} disabled={!editable} loading={uploadGuide.isPending}>{t.program.files.uploadGuide}</UiButton>}</FileButton>
        {(guides.data ?? []).map((guide) => <UiGroup key={guide.id} justify="space-between" wrap="nowrap"><UiStack gap={0}><UiText size="sm" fw={600}>{guide.filename}</UiText><UiText size="xs" c="dimmed">{Math.ceil(guide.size / 1024)} KB</UiText></UiStack><UiButton size="xs" variant="subtle" onClick={() => void run(async () => downloadBlob(await programsApi.downloadGuide(guide.id), guide.filename))}>{t.program.files.download}</UiButton></UiGroup>)}
        {!guides.isPending && !guides.data?.length ? <UiText size="sm" c="dimmed">{t.program.common.noData}</UiText> : null}
      </UiStack>
    </SectionCard>
  </UiSimpleGrid>;
}

interface ReviewerDraft { employeeId: string | null; name?: string; role: ReviewerRole; round: number; weightPercent: number }

export function ParticipantAdministration({ participant, program }: { participant: ParticipantResponse; program: ProgramResponse }): React.ReactNode {
  const t = useT(); const reviewers = useParticipantReviewersQuery(participant.id); const replace = useReplaceReviewersMutation(); const update = useUpdateParticipantMutation(); const override = useOverrideStageMutation();
  const [search, setSearch] = useState(''); const employees = useEmployeeOptionsQuery(search);
  const [drafts, setDrafts] = useState<ReviewerDraft[]>([]); const [weight, setWeight] = useState<number | string>(participant.weightPercent); const [groupId, setGroupId] = useState<string | null>(participant.groupId); const [reason, setReason] = useState('');
  const [toStage, setToStage] = useState<ProgramStage>(participant.currentStage); const [toStatus, setToStatus] = useState<ProgramStageStatus>(participant.stageStatus); const [round, setRound] = useState<number | string>(participant.currentRound);
  useEffect(() => { setDrafts((reviewers.data ?? []).filter((item) => item.status !== 'REVOKED').map((item) => ({ employeeId: item.reviewerEmployeeId, name: item.reviewerName, role: item.role, round: item.round, weightPercent: item.weightPercent }))); }, [reviewers.data]);
  useEffect(() => { setWeight(participant.weightPercent); setGroupId(participant.groupId); setToStage(participant.currentStage); setToStatus(participant.stageStatus); setRound(participant.currentRound); }, [participant]);
  const employeeOptions = useMemo(() => (employees.data ?? []).map((item) => ({ value: item.id, label: `${item.name} · ${item.employeeNo}` })), [employees.data]);
  const run: Run = async (work) => { try { await work(); setReason(''); showToast({ tone: 'success', message: t.program.common.saveSuccess }); } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); } };
  const reviewerInputs = drafts.filter((item): item is ReviewerDraft & { employeeId: string } => Boolean(item.employeeId)).map<ReviewerInput>((item) => ({ employeeId: item.employeeId, role: item.role, round: item.round, weightPercent: item.weightPercent }));
  const editDraft = (index: number, patch: Partial<ReviewerDraft>): void => setDrafts((items) => items.map((item, i) => i === index ? { ...item, ...patch } : item));

  return <UiStack gap="md">
    <SectionCard title={t.program.operations.reviewerAssignment}>
      <UiStack gap="sm">{drafts.map((draft, index) => <UiSimpleGrid key={index} cols={{ base: 1, md: 4 }}>
        <LookupSelect label={t.program.common.reviewer} value={draft.employeeId} onChange={(employeeId) => editDraft(index, { employeeId })} searchValue={search} onSearchChange={setSearch} data={draft.employeeId && !employeeOptions.some((item) => item.value === draft.employeeId) ? [{ value: draft.employeeId, label: draft.name ?? t.program.common.reviewer }, ...employeeOptions] : employeeOptions} />
        <FormSelect label={t.program.programs.type} value={draft.role} data={roles.map((role) => ({ value: role, label: roleLabel(t, role) }))} onChange={(role) => role && editDraft(index, { role: role as ReviewerRole })} />
        <FormNumberInput label={t.program.programs.currentStage} min={0} max={3} value={draft.round} onChange={(value) => editDraft(index, { round: Number(value) })} />
        <UiGroup align="end" wrap="nowrap"><FormNumberInput label={t.program.goals.weight} min={0} max={100} value={draft.weightPercent} onChange={(value) => editDraft(index, { weightPercent: Number(value) })} /><UiButton color="red" variant="subtle" onClick={() => setDrafts((items) => items.filter((_, i) => i !== index))}>{t.common.action.delete}</UiButton></UiGroup>
      </UiSimpleGrid>)}<UiGroup><UiButton variant="light" onClick={() => setDrafts((items) => [...items, { employeeId: null, role: 'REVIEWER', round: 1, weightPercent: 100 }])}>{t.program.reviewerRoles.add}</UiButton><UiButton disabled={!reviewerInputs.length} loading={replace.isPending} onClick={() => void run(() => replace.mutateAsync({ participantId: participant.id, reviewers: reviewerInputs }))}>{t.common.action.save}</UiButton></UiGroup></UiStack>
    </SectionCard>
    <SectionCard title={t.program.operations.detail}>
      <UiSimpleGrid cols={{ base: 1, md: 3 }}><FormSelect clearable label={t.program.programs.groups} value={groupId} data={program.configuration.groups.map((group) => ({ value: group.id, label: group.name }))} onChange={setGroupId} /><FormNumberInput label={t.program.files.participantWeight} min={0.0001} max={100} value={weight} onChange={setWeight} /><FormTextInput label={t.program.operations.reason} value={reason} onChange={(event) => setReason(event.currentTarget.value)} /></UiSimpleGrid>
      <UiGroup mt="sm"><UiButton disabled={!reason.trim()} onClick={() => void run(() => update.mutateAsync({ id: participant.id, input: { groupId: groupId ?? undefined, weightPercent: Number(weight), reason } }))}>{t.common.action.save}</UiButton>{participant.status === 'ACTIVE' ? <UiButton color="red" variant="light" disabled={!reason.trim()} onClick={() => void run(() => update.mutateAsync({ id: participant.id, input: { status: 'EXCLUDED', reason } }))}>{t.program.participantStatus.excluded}</UiButton> : <UiButton variant="light" disabled={!reason.trim()} onClick={() => void run(() => update.mutateAsync({ id: participant.id, input: { status: 'ACTIVE', reason } }))}>{t.program.participantStatus.reactivate}</UiButton>}</UiGroup>
    </SectionCard>
    <SectionCard title={t.program.files.overrideStage}>
      <UiSimpleGrid cols={{ base: 1, md: 4 }}><FormSelect label={t.program.programs.currentStage} value={toStage} data={stages.map((stage) => ({ value: stage, label: programStageLabel(t, stage) }))} onChange={(value) => value && setToStage(value as ProgramStage)} /><FormSelect label={t.program.common.status} value={toStatus} data={stageStatuses.map((value) => ({ value, label: programStageStatusLabel(t, value) }))} onChange={(value) => value && setToStatus(value as ProgramStageStatus)} /><FormNumberInput label={t.program.programs.currentStage} min={0} max={3} value={round} onChange={setRound} /><FormTextarea label={t.program.operations.reason} value={reason} onChange={(event) => setReason(event.currentTarget.value)} /></UiSimpleGrid>
      <UiButton mt="sm" disabled={!reason.trim()} loading={override.isPending} onClick={() => void run(() => override.mutateAsync({ participantId: participant.id, input: { toStage, toStatus, round: Number(round), reason } }))}>{t.program.files.overrideStage}</UiButton>
    </SectionCard>
  </UiStack>;
}

export function NotificationTools({ program, participants }: { program: ProgramResponse; participants: ParticipantResponse[] }): React.ReactNode {
  const t = useT(); const preview = usePreviewNotificationsMutation(); const queue = useQueueNotificationsMutation(); const dispatch = useDispatchNotificationsMutation();
  const [companyName, setCompanyName] = useState(''); const [subject, setSubject] = useState(''); const [body, setBody] = useState(''); const [channel, setChannel] = useState<NotificationChannel>('IN_APP');
  const recipientEmployeeIds = participants.filter((item) => item.status === 'ACTIVE').map((item) => item.employee.employeeId);
  const input = { recipientEmployeeIds, companyName, subjectTemplate: subject, bodyTemplate: body, channel };
  const valid = recipientEmployeeIds.length > 0 && companyName.trim() && subject.trim() && body.trim();
  const run: Run = async (work) => { try { await work(); showToast({ tone: 'success', message: t.program.common.submitSuccess }); } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); } };
  return <SectionCard title={t.program.notifications.title} description={`${t.program.operations.participants}: ${recipientEmployeeIds.length}`}><UiStack gap="sm"><UiSimpleGrid cols={{ base: 1, md: 2 }}><FormTextInput label={t.program.notifications.company} value={companyName} onChange={(event) => setCompanyName(event.currentTarget.value)} /><FormSelect label={t.program.notifications.channel} value={channel} data={[{ value: 'IN_APP', label: t.program.notifications.inApp }, { value: 'EMAIL', label: t.program.notifications.email }]} onChange={(value) => value && setChannel(value as NotificationChannel)} /></UiSimpleGrid><FormTextInput label={t.program.notifications.subject} value={subject} onChange={(event) => setSubject(event.currentTarget.value)} /><FormTextarea label={t.program.notifications.body} value={body} onChange={(event) => setBody(event.currentTarget.value)} /><UiGroup><UiButton variant="light" disabled={!valid} loading={preview.isPending} onClick={() => void run(() => preview.mutateAsync({ id: program.id, input }))}>{t.program.notifications.preview}</UiButton><UiButton disabled={!valid} loading={queue.isPending} onClick={() => void run(() => queue.mutateAsync({ id: program.id, input }))}>{t.program.notifications.queue}</UiButton><UiButton variant="light" loading={dispatch.isPending} onClick={() => void run(() => dispatch.mutateAsync(program.id))}>{t.program.notifications.dispatch}</UiButton></UiGroup>{preview.data?.map((item) => <SectionCard key={item.recipientEmployeeId} title={item.subject}><UiText size="sm">{item.body}</UiText></SectionCard>)}</UiStack></SectionCard>;
}

export function CancelFinalization({ program }: { program: ProgramResponse }): React.ReactNode {
  const t = useT(); const cancel = useCancelFinalizationMutation(); const [reason, setReason] = useState('');
  if (program.status !== 'FINALIZED') return null;
  return <SectionCard title={t.program.files.cancelFinalization}><UiStack gap="sm"><FormTextarea label={t.program.operations.reason} value={reason} onChange={(event) => setReason(event.currentTarget.value)} /><UiButton color="red" variant="light" disabled={!reason.trim()} loading={cancel.isPending} onClick={() => void cancel.mutateAsync({ id: program.id, reason }).then(() => { setReason(''); showToast({ tone: 'success', message: t.program.common.saveSuccess }); }).catch((error) => showToast({ tone: 'danger', message: getErrorMessage(error) }))}>{t.program.files.cancelFinalization}</UiButton></UiStack></SectionCard>;
}

import { useEffect, useState } from 'react';
import { FileButton } from '@easy/ui-components/mantine';
import {
  FormActions, FormNumberInput, FormSelect, FormTextInput, FormTextarea, LookupMultiSelect,
  LookupSelect, MasterDetailWorkspace, PageHeader, PerformanceLogEntryCard,
  PerformanceProgressSummary, SectionCard, UiBadge, UiButton, UiGroup, UiModal, UiStack,
  UiText, WorkspaceTabs,
} from '@easy/ui-components';

import { getErrorMessage } from '../../../api/error';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import {
  useAddChecklistMutation, useAddTaskActivityMutation, useAddTaskFeedbackMutation, useChangeTaskStatusMutation,
  useCreateTaskMutation, useDepartmentGoalsQuery, useEmployeeOptionsQuery,
  resourceApi, useApplyTaskLabelMutation, useCreateTaskLabelMutation, useRecordTaskProgressMutation,
  useRemoveTaskLabelMutation, useReopenTaskMutation, useTaskLabelsQuery, useTaskQuery, useTasksQuery,
  useUpdateChecklistMutation, useUploadTaskAttachmentMutation,
  type ProgressMode, type TaskCreateRequest, type TaskStatus, type TaskSummaryResponse,
} from '../api/resources';
import { QueryState } from '../components/QueryState';
import { downloadBlob } from '../components/downloadBlob';

const taskStatuses: TaskStatus[] = ['PLANNED', 'IN_PROGRESS', 'COMPLETED', 'DISCARDED'];
const today = new Date().toISOString().slice(0, 10);

export function TaskBoardPage(): React.ReactNode {
  const t = useT();
  const [status, setStatus] = useState<TaskStatus | 'ALL'>('ALL');
  const [search, setSearch] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const query = useTasksQuery({ status: status === 'ALL' ? undefined : status, q: search });
  const rows = query.data ?? [];
  useEffect(() => { if (!selectedId && rows[0]) setSelectedId(rows[0].id); }, [rows, selectedId]);
  return (
    <UiStack gap="md">
      <PageHeader title={t.program.tasks.title} description={t.program.tasks.description} actions={<UiButton onClick={() => setCreating(true)}>{t.program.tasks.create}</UiButton>} />
      <UiGroup align="end" wrap="wrap"><FormTextInput label={t.program.common.search} value={search} onChange={(event) => setSearch(event.currentTarget.value)} /><FormSelect label={t.program.common.status} value={status} data={[{ value: 'ALL', label: t.program.common.all }, ...taskStatuses.map((value) => ({ value, label: taskStatusLabel(t, value) }))]} onChange={(value) => value && setStatus(value as TaskStatus | 'ALL')} /></UiGroup>
      <QueryState pending={query.isPending} error={query.error} empty={rows.length === 0} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>
        <MasterDetailWorkspace listLabel={t.program.tasks.board} detailLabel={t.program.tasks.detail} list={<TaskBoardList rows={rows} selectedId={selectedId} onSelect={setSelectedId} />}>
          <TaskDetail taskId={selectedId} />
        </MasterDetailWorkspace>
      </QueryState>
      {creating ? <TaskCreateModal onClose={() => setCreating(false)} /> : null}
    </UiStack>
  );
}

function taskStatusLabel(t: ReturnType<typeof useT>, status: TaskStatus): string {
  return { PLANNED: t.program.tasks.planned, IN_PROGRESS: t.program.tasks.inProgress, COMPLETED: t.program.tasks.completed, DISCARDED: t.program.tasks.discarded }[status];
}

function activityTypeLabel(t: ReturnType<typeof useT>, type: string): string {
  const labels: Record<string, string> = {
    CREATED: t.program.tasks.activityCreated,
    UPDATED: t.program.tasks.activityUpdated,
    STATUS_CHANGED: t.program.tasks.activityStatusChanged,
    REOPENED: t.program.tasks.activityReopened,
    CHECKLIST_ADDED: t.program.tasks.activityChecklistAdded,
    CHECKLIST_UPDATED: t.program.tasks.activityChecklistUpdated,
    PROGRESS_UPDATED: t.program.tasks.activityProgressUpdated,
    COMMENT: t.program.tasks.activityComment,
    ATTACHMENT_ADDED: t.program.tasks.activityAttachmentAdded,
  };
  return labels[type] ?? t.program.tasks.activity;
}

function TaskBoardList({ rows, selectedId, onSelect }: { rows: TaskSummaryResponse[]; selectedId: string | null; onSelect: (id: string) => void }): React.ReactNode {
  const t = useT();
  return <UiStack gap="sm">{taskStatuses.map((status) => { const items = rows.filter((item) => item.status === status); return <SectionCard key={status} title={taskStatusLabel(t, status)} padding="sm"><UiStack gap="xs">{items.length ? items.map((item) => <UiButton key={item.id} variant={item.id === selectedId ? 'light' : 'subtle'} onClick={() => onSelect(item.id)}><UiStack gap={2} align="stretch"><UiText size="sm" fw={600}>{item.title}</UiText><UiText size="xs" c="dimmed">{item.progressPercent}%</UiText></UiStack></UiButton>) : <UiText size="sm" c="dimmed">{t.program.common.noData}</UiText>}</UiStack></SectionCard>; })}</UiStack>;
}

function TaskDetail({ taskId }: { taskId: string | null }): React.ReactNode {
  const t = useT();
  const query = useTaskQuery(taskId);
  const changeStatus = useChangeTaskStatusMutation();
  const addChecklist = useAddChecklistMutation();
  const updateChecklist = useUpdateChecklistMutation();
  const progress = useRecordTaskProgressMutation();
  const activity = useAddTaskActivityMutation();
  const feedback = useAddTaskFeedbackMutation();
  const reopen = useReopenTaskMutation(); const labels = useTaskLabelsQuery(); const createLabel = useCreateTaskLabelMutation(); const applyLabel = useApplyTaskLabelMutation(); const removeLabel = useRemoveTaskLabelMutation(); const uploadAttachment = useUploadTaskAttachmentMutation();
  const [employeeSearch, setEmployeeSearch] = useState('');
  const employees = useEmployeeOptionsQuery(employeeSearch);
  const [status, setStatus] = useState<TaskStatus>('PLANNED');
  const [reason, setReason] = useState('');
  const [checklistText, setChecklistText] = useState('');
  const [progressValue, setProgressValue] = useState<number | string>('');
  const [progressNote, setProgressNote] = useState('');
  const [activityMessage, setActivityMessage] = useState('');
  const [feedbackEmployeeId, setFeedbackEmployeeId] = useState<string | null>(null);
  const [feedbackRating, setFeedbackRating] = useState<number | string>(5);
  const [feedbackMessage, setFeedbackMessage] = useState('');
  const [labelName, setLabelName] = useState(''); const [labelId, setLabelId] = useState<string | null>(null);
  useEffect(() => { if (query.data) setStatus(query.data.task.status); }, [query.data]);
  if (!taskId) return <SectionCard><UiText c="dimmed">{t.program.common.noSelection}</UiText></SectionCard>;
  return <QueryState pending={query.isPending} error={query.error} empty={!query.data} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>{query.data ? <UiStack gap="md">
    <SectionCard title={query.data.task.title} description={query.data.description}><UiGroup><UiBadge>{taskStatusLabel(t, query.data.task.status)}</UiBadge><UiText size="sm">{query.data.task.periodStart} – {query.data.task.periodEnd}</UiText></UiGroup><PerformanceProgressSummary label={t.program.tasks.updateProgress} value={query.data.task.progressPercent} total={100} valueLabel={`${query.data.task.progressPercent}%`} /></SectionCard>
    <WorkspaceTabs defaultValue="properties"><WorkspaceTabs.List><WorkspaceTabs.Tab value="properties">{t.program.tasks.properties}</WorkspaceTabs.Tab><WorkspaceTabs.Tab value="activity">{t.program.tasks.activity}</WorkspaceTabs.Tab><WorkspaceTabs.Tab value="feedback">{t.program.tasks.feedback}</WorkspaceTabs.Tab></WorkspaceTabs.List>
      <WorkspaceTabs.Panel value="properties"><UiStack gap="md"><SectionCard title={t.program.common.status}><UiGroup align="end"><FormSelect label={t.program.common.status} value={status} data={taskStatuses.map((value) => ({ value, label: taskStatusLabel(t, value) }))} onChange={(value) => value && setStatus(value as TaskStatus)} /><FormTextInput label={t.program.operations.reason} value={reason} onChange={(event) => setReason(event.currentTarget.value)} /><UiButton loading={changeStatus.isPending} disabled={!reason.trim() || status === query.data.task.status} onClick={() => void changeStatus.mutateAsync({ id: taskId, status, reason }).then(() => { setReason(''); showToast({ tone: 'success', message: t.program.common.saveSuccess }); }).catch((error) => showToast({ tone: 'danger', message: getErrorMessage(error) }))}>{t.common.action.save}</UiButton>{query.data.task.status === 'COMPLETED' || query.data.task.status === 'DISCARDED' ? <UiButton variant="light" disabled={!reason.trim()} loading={reopen.isPending} onClick={() => void reopen.mutateAsync({ id: taskId, reason }).then(() => setReason('')).catch((error) => showToast({ tone: 'danger', message: getErrorMessage(error) }))}>{t.program.tasks.reopen}</UiButton> : null}</UiGroup></SectionCard>
        {query.data.task.progressMode === 'CHECKLIST' ? <SectionCard title={t.program.tasks.checklist}><UiStack gap="xs">{query.data.checklist.map((item) => <UiButton key={item.id} variant={item.completed ? 'light' : 'subtle'} onClick={() => void updateChecklist.mutateAsync({ id: taskId, itemId: item.id, completed: !item.completed })}>{item.completed ? '✓ ' : ''}{item.text}</UiButton>)}<UiGroup align="end"><FormTextInput label={t.program.tasks.addChecklist} value={checklistText} onChange={(event) => setChecklistText(event.currentTarget.value)} /><UiButton disabled={!checklistText.trim()} loading={addChecklist.isPending} onClick={() => void addChecklist.mutateAsync({ id: taskId, text: checklistText, displayOrder: query.data.checklist.length }).then(() => setChecklistText(''))}>{t.common.action.create}</UiButton></UiGroup></UiStack></SectionCard> : <SectionCard title={t.program.tasks.updateProgress}><UiGroup align="end"><FormNumberInput label={t.program.tasks.updateProgress} min={0} max={100} value={progressValue} onChange={setProgressValue} /><FormTextInput label={t.program.common.comment} value={progressNote} onChange={(event) => setProgressNote(event.currentTarget.value)} /><UiButton disabled={progressValue === '' || !progressNote.trim()} loading={progress.isPending} onClick={() => void progress.mutateAsync({ id: taskId, progressPercent: Number(progressValue), note: progressNote }).then(() => { setProgressValue(''); setProgressNote(''); })}>{t.common.action.save}</UiButton></UiGroup></SectionCard>}
        <SectionCard title={t.program.tasks.labels}><UiStack gap="sm"><UiGroup>{query.data.task.labels.map((label) => <UiGroup key={label.id} gap={2}><UiBadge variant="light">{label.name}</UiBadge><UiButton size="compact-xs" variant="subtle" onClick={() => void removeLabel.mutateAsync({ id: taskId, labelId: label.id })}>{t.common.action.delete}</UiButton></UiGroup>)}</UiGroup><UiGroup align="end"><FormSelect searchable label={t.program.tasks.labels} value={labelId} data={(labels.data ?? []).map((label) => ({ value: label.id, label: label.name }))} onChange={setLabelId} /><UiButton disabled={!labelId} onClick={() => labelId && void applyLabel.mutateAsync({ id: taskId, labelId })}>{t.common.action.save}</UiButton><FormTextInput label={t.program.catalogs.name} value={labelName} onChange={(event) => setLabelName(event.currentTarget.value)} /><UiButton disabled={!labelName.trim()} loading={createLabel.isPending} onClick={() => void createLabel.mutateAsync(labelName).then((created) => { setLabelId(created.id); setLabelName(''); })}>{t.common.action.create}</UiButton></UiGroup></UiStack></SectionCard>
        <SectionCard title={t.program.tasks.attachments}><UiStack gap="sm"><FileButton onChange={(file) => file && void uploadAttachment.mutateAsync({ id: taskId, file }).catch((error) => showToast({ tone: 'danger', message: getErrorMessage(error) }))}>{(props) => <UiButton {...props} loading={uploadAttachment.isPending}>{t.program.tasks.addAttachment}</UiButton>}</FileButton>{query.data.attachments.map((file) => <UiGroup key={file.id} justify="space-between"><UiText size="sm">{file.filename}</UiText><UiButton size="xs" variant="subtle" onClick={() => void resourceApi.downloadTaskAttachment(taskId, file.id).then((blob) => downloadBlob(blob, file.filename)).catch((error) => showToast({ tone: 'danger', message: getErrorMessage(error) }))}>{t.program.files.download}</UiButton></UiGroup>)}</UiStack></SectionCard>
      </UiStack></WorkspaceTabs.Panel>
      <WorkspaceTabs.Panel value="activity"><SectionCard title={t.program.tasks.activity}><UiStack gap="sm">{query.data.activities.map((item) => <PerformanceLogEntryCard key={item.id} primary={activityTypeLabel(t, item.type)} timestamp={new Date(item.createdAt).toLocaleString()}>{item.fromStatus && item.toStatus ? <UiText size="xs" c="dimmed">{taskStatusLabel(t, item.fromStatus)} → {taskStatusLabel(t, item.toStatus)}</UiText> : null}{item.message ? <UiText size="sm" mt="xs">{item.message}</UiText> : null}</PerformanceLogEntryCard>)}<FormTextarea label={t.program.tasks.addActivity} value={activityMessage} onChange={(event) => setActivityMessage(event.currentTarget.value)} /><UiButton disabled={!activityMessage.trim()} loading={activity.isPending} onClick={() => void activity.mutateAsync({ id: taskId, message: activityMessage }).then(() => setActivityMessage(''))}>{t.program.tasks.addActivity}</UiButton></UiStack></SectionCard></WorkspaceTabs.Panel>
      <WorkspaceTabs.Panel value="feedback"><UiStack gap="md"><SectionCard title={t.program.tasks.feedback}>{query.data.feedback.length ? <UiStack>{query.data.feedback.map((item) => <PerformanceLogEntryCard key={item.id} primary={`${item.rating}/5`} timestamp={new Date(item.createdAt).toLocaleString()}><UiText size="sm" mt="xs">{item.message}</UiText></PerformanceLogEntryCard>)}</UiStack> : <UiText c="dimmed">{t.program.common.noData}</UiText>}</SectionCard><SectionCard title={t.program.tasks.addFeedback}><UiStack gap="sm"><LookupSelect label={t.program.common.employee} value={feedbackEmployeeId} onChange={setFeedbackEmployeeId} data={(employees.data ?? []).map((employee) => ({ value: employee.id, label: `${employee.name} · ${employee.employeeNo}` }))} searchValue={employeeSearch} onSearchChange={setEmployeeSearch} /><FormNumberInput label={t.program.tasks.rating} min={1} max={5} value={feedbackRating} onChange={setFeedbackRating} /><FormTextarea label={t.program.common.comment} value={feedbackMessage} onChange={(event) => setFeedbackMessage(event.currentTarget.value)} /><UiButton disabled={!feedbackEmployeeId || !feedbackMessage.trim() || Number(feedbackRating) < 1 || Number(feedbackRating) > 5} loading={feedback.isPending} onClick={() => feedbackEmployeeId && void feedback.mutateAsync({ id: taskId, toEmployeeId: feedbackEmployeeId, rating: Number(feedbackRating), message: feedbackMessage }).then(() => { setFeedbackEmployeeId(null); setFeedbackMessage(''); showToast({ tone: 'success', message: t.program.common.saveSuccess }); }).catch((error) => showToast({ tone: 'danger', message: getErrorMessage(error) }))}>{t.program.tasks.addFeedback}</UiButton></UiStack></SectionCard></UiStack></WorkspaceTabs.Panel>
    </WorkspaceTabs>
  </UiStack> : null}</QueryState>;
}

function TaskCreateModal({ onClose }: { onClose: () => void }): React.ReactNode {
  const t = useT(); const create = useCreateTaskMutation();
  const [employeeSearch, setEmployeeSearch] = useState(''); const employees = useEmployeeOptionsQuery(employeeSearch);
  const goals = useDepartmentGoalsQuery({});
  const [owner, setOwner] = useState<string | null>(null); const [manager, setManager] = useState<string | null>(null); const [collaborators, setCollaborators] = useState<string[]>([]);
  const [form, setForm] = useState<Omit<TaskCreateRequest, 'stakeholders'>>({ title: '', periodStart: today, periodEnd: today, description: '', progressMode: 'CHECKLIST', departmentGoalId: null });
  const options = (employees.data ?? []).map((employee) => ({ value: employee.id, label: `${employee.name} · ${employee.employeeNo}` }));
  const submit = async (): Promise<void> => { if (!owner) return; const stakeholders = [{ employeeId: owner, role: 'OWNER' as const }, ...(manager ? [{ employeeId: manager, role: 'MANAGER' as const }] : []), ...collaborators.map((employeeId) => ({ employeeId, role: 'COLLABORATOR' as const }))]; try { await create.mutateAsync({ ...form, stakeholders }); showToast({ tone: 'success', message: t.program.common.saveSuccess }); onClose(); } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); } };
  return <UiModal opened onClose={onClose} title={t.program.tasks.create} size="lg"><UiStack gap="sm"><FormTextInput label={t.program.tasks.taskName} value={form.title} onChange={(event) => setForm({ ...form, title: event.currentTarget.value })} /><UiGroup grow><FormTextInput type="date" label={t.program.programs.startDate} value={form.periodStart} onChange={(event) => setForm({ ...form, periodStart: event.currentTarget.value })} /><FormTextInput type="date" label={t.program.programs.endDate} value={form.periodEnd} onChange={(event) => setForm({ ...form, periodEnd: event.currentTarget.value })} /></UiGroup><FormTextarea label={t.program.programs.descriptionLabel} value={form.description} onChange={(event) => setForm({ ...form, description: event.currentTarget.value })} /><FormSelect label={t.program.tasks.method} value={form.progressMode} data={[{ value: 'CHECKLIST', label: t.program.tasks.checklist }, { value: 'ACTUAL', label: t.program.tasks.actual }]} onChange={(value) => value && setForm({ ...form, progressMode: value as ProgressMode })} /><FormSelect clearable label={t.program.tasks.linkedGoals} value={form.departmentGoalId} data={(goals.data ?? []).map((goal) => ({ value: goal.id, label: goal.title }))} onChange={(value) => setForm({ ...form, departmentGoalId: value })} /><LookupSelect label={t.program.tasks.owner} value={owner} onChange={setOwner} data={options} searchValue={employeeSearch} onSearchChange={setEmployeeSearch} /><LookupSelect label={t.program.tasks.manager} value={manager} onChange={setManager} data={options} searchValue={employeeSearch} onSearchChange={setEmployeeSearch} /><LookupMultiSelect label={t.program.tasks.collaborators} value={collaborators} onChange={setCollaborators} data={options} searchValue={employeeSearch} onSearchChange={setEmployeeSearch} /><FormActions secondary={<UiButton variant="default" onClick={onClose}>{t.common.action.cancel}</UiButton>} primary={<UiButton loading={create.isPending} disabled={!form.title.trim() || !owner || form.periodEnd < form.periodStart} onClick={() => void submit()}>{t.common.action.save}</UiButton>} /></UiStack></UiModal>;
}

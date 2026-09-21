import { useEffect, useMemo, useState } from 'react';
import {
  FormActions, FormSwitch, FormTextInput, FormTextarea, LookupMultiSelect, LookupSelect,
  MasterDetailWorkspace, PageHeader, PerformanceCommentPanel, SectionCard, UiBadge,
  UiButton, UiGroup, UiModal, UiStack, UiText, WorkspaceTabs,
} from '@easy/ui-components';

import { getErrorMessage } from '../../../api/error';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import {
  useCreateInterviewMutation, useEmployeeOptionsQuery, useInterviewsQuery,
  useUpdateInterviewMutation, useUpdateInterviewVisibilityMutation,
  type InterviewCreateRequest, type InterviewResponse, type InterviewView,
} from '../api/resources';
import { QueryState } from '../components/QueryState';

const views: InterviewView[] = ['AUTHORED', 'MINE', 'BY_EMPLOYEE', 'REFERENCED'];
const localDateTime = (): string => new Date(Date.now() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 16);

export function InterviewWorkspacePage(): React.ReactNode {
  const t = useT();
  const [view, setView] = useState<InterviewView>('AUTHORED');
  const [employeeSearch, setEmployeeSearch] = useState('');
  const [employeeId, setEmployeeId] = useState<string | null>(null);
  const employees = useEmployeeOptionsQuery(employeeSearch);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<InterviewResponse | null>(null);
  const query = useInterviewsQuery(view, employeeId);
  const rows = query.data ?? [];
  const selected = rows.find((item) => item.id === selectedId) ?? rows[0] ?? null;
  useEffect(() => { setSelectedId(null); }, [view, employeeId]);
  const employeeMap = useMemo(() => new Map((employees.data ?? []).map((item) => [item.id, item.name])), [employees.data]);
  const labelFor = (id: string): string => employeeMap.get(id) ?? t.program.common.employee;
  const tabLabel = (value: InterviewView): string => ({ AUTHORED: t.program.interviews.writtenByMe, MINE: t.program.interviews.aboutMe, BY_EMPLOYEE: t.program.interviews.byEmployee, REFERENCED: t.program.interviews.referenced })[value];
  return <UiStack gap="md">
    <PageHeader title={t.program.interviews.title} description={t.program.interviews.description} actions={<UiButton onClick={() => setCreating(true)}>{t.program.interviews.create}</UiButton>} />
    <WorkspaceTabs value={view} onChange={(value) => value && setView(value as InterviewView)}><WorkspaceTabs.List>{views.map((value) => <WorkspaceTabs.Tab key={value} value={value}>{tabLabel(value)}</WorkspaceTabs.Tab>)}</WorkspaceTabs.List></WorkspaceTabs>
    {view === 'BY_EMPLOYEE' ? <LookupSelect label={t.program.interviews.subject} value={employeeId} onChange={setEmployeeId} data={(employees.data ?? []).map((item) => ({ value: item.id, label: `${item.name} · ${item.employeeNo}` }))} searchValue={employeeSearch} onSearchChange={setEmployeeSearch} /> : null}
    <QueryState pending={query.isPending} error={query.error} empty={rows.length === 0} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={view === 'BY_EMPLOYEE' && !employeeId ? t.program.common.noSelection : t.program.common.noData}>
      <MasterDetailWorkspace listLabel={t.program.interviews.list} detailLabel={t.program.interviews.detail} list={<UiStack gap="xs">{rows.map((item) => <UiButton key={item.id} variant={selected?.id === item.id ? 'light' : 'subtle'} onClick={() => setSelectedId(item.id)}><UiStack gap={2} align="stretch"><UiText size="sm" fw={600}>{labelFor(item.subjectEmployeeId)}</UiText><UiText size="xs" c="dimmed">{new Date(item.occurredAt).toLocaleString()}</UiText></UiStack></UiButton>)}</UiStack>}>
        {selected ? <InterviewDetail item={selected} labelFor={labelFor} editable={view === 'AUTHORED'} onEdit={() => setEditing(selected)} /> : null}
      </MasterDetailWorkspace>
    </QueryState>
    {creating ? <InterviewEditor onClose={() => setCreating(false)} /> : null}
    {editing ? <InterviewEditor item={editing} onClose={() => setEditing(null)} /> : null}
  </UiStack>;
}

function InterviewDetail({ item, labelFor, editable, onEdit }: { item: InterviewResponse; labelFor: (id: string) => string; editable: boolean; onEdit: () => void }): React.ReactNode {
  const t = useT(); const visibility = useUpdateInterviewVisibilityMutation();
  const updateVisibility = (subjectVisible: boolean, referencesVisible: boolean): void => { void visibility.mutateAsync({ id: item.id, subjectVisible, referencesVisible }).then(() => showToast({ tone: 'success', message: t.program.common.saveSuccess })).catch((error) => showToast({ tone: 'danger', message: getErrorMessage(error) })); };
  return <UiStack gap="md"><SectionCard title={labelFor(item.subjectEmployeeId)} description={new Date(item.occurredAt).toLocaleString()} actions={editable ? <UiButton variant="light" onClick={onEdit}>{t.common.action.edit}</UiButton> : undefined}><UiGroup><UiBadge variant="light">{item.subjectVisible ? t.program.interviews.visibleToSubject : t.program.common.readOnly}</UiBadge><UiBadge variant="outline">{item.referencesVisible ? t.program.interviews.visibleToReferences : t.program.common.readOnly}</UiBadge></UiGroup>{item.referenceEmployeeIds.length ? <UiText mt="sm" size="sm">{t.program.interviews.references}: {item.referenceEmployeeIds.map(labelFor).join(', ')}</UiText> : null}</SectionCard>
    <PerformanceCommentPanel title={t.program.interviews.summary} comment={item.summary} empty={t.program.common.noData} />
    <PerformanceCommentPanel title={t.program.interviews.issues} comment={item.keyIssues} empty={t.program.common.noData} />
    <PerformanceCommentPanel title={t.program.interviews.requests} comment={item.requests} empty={t.program.common.noData} />
    <PerformanceCommentPanel title={t.program.interviews.followUp} comment={item.followUp} empty={t.program.common.noData} />
    {editable ? <SectionCard title={t.program.interviews.visibility}><UiGroup><FormSwitch label={t.program.interviews.visibleToSubject} checked={item.subjectVisible} disabled={visibility.isPending} onChange={(event) => updateVisibility(event.currentTarget.checked, item.referencesVisible)} /><FormSwitch label={t.program.interviews.visibleToReferences} checked={item.referencesVisible} disabled={visibility.isPending} onChange={(event) => updateVisibility(item.subjectVisible, event.currentTarget.checked)} /></UiGroup></SectionCard> : null}
  </UiStack>;
}

function InterviewEditor({ item, onClose }: { item?: InterviewResponse; onClose: () => void }): React.ReactNode {
  const t = useT(); const create = useCreateInterviewMutation(); const update = useUpdateInterviewMutation();
  const [search, setSearch] = useState(''); const employees = useEmployeeOptionsQuery(search);
  const [form, setForm] = useState<InterviewCreateRequest>(() => item ? { subjectEmployeeId: item.subjectEmployeeId, occurredAt: item.occurredAt.slice(0, 16), summary: item.summary, keyIssues: item.keyIssues, requests: item.requests, followUp: item.followUp, subjectVisible: item.subjectVisible, referencesVisible: item.referencesVisible, referenceEmployeeIds: item.referenceEmployeeIds } : { subjectEmployeeId: '', occurredAt: localDateTime(), summary: '', keyIssues: '', requests: '', followUp: '', subjectVisible: true, referencesVisible: false, referenceEmployeeIds: [] });
  const options = (employees.data ?? []).map((employee) => ({ value: employee.id, label: `${employee.name} · ${employee.employeeNo}` }));
  const submit = async (): Promise<void> => { try { const occurredAt = new Date(form.occurredAt).toISOString(); if (item) await update.mutateAsync({ id: item.id, input: { occurredAt, summary: form.summary, keyIssues: form.keyIssues, requests: form.requests, followUp: form.followUp, referenceEmployeeIds: form.referenceEmployeeIds } }); else await create.mutateAsync({ ...form, occurredAt }); showToast({ tone: 'success', message: t.program.common.saveSuccess }); onClose(); } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); } };
  return <UiModal opened onClose={onClose} title={item ? t.common.action.edit : t.program.interviews.create} size="lg"><UiStack gap="sm">{!item ? <LookupSelect label={t.program.interviews.subject} value={form.subjectEmployeeId || null} onChange={(value) => setForm({ ...form, subjectEmployeeId: value ?? '' })} data={options} searchValue={search} onSearchChange={setSearch} /> : null}<LookupMultiSelect label={t.program.interviews.references} value={form.referenceEmployeeIds} onChange={(values) => setForm({ ...form, referenceEmployeeIds: values })} data={options} searchValue={search} onSearchChange={setSearch} /><FormTextInput type="datetime-local" label={t.program.common.period} value={form.occurredAt} onChange={(event) => setForm({ ...form, occurredAt: event.currentTarget.value })} /><FormTextarea required minRows={3} label={t.program.interviews.summary} value={form.summary} onChange={(event) => setForm({ ...form, summary: event.currentTarget.value })} /><FormTextarea label={t.program.interviews.issues} value={form.keyIssues} onChange={(event) => setForm({ ...form, keyIssues: event.currentTarget.value })} /><FormTextarea label={t.program.interviews.requests} value={form.requests} onChange={(event) => setForm({ ...form, requests: event.currentTarget.value })} /><FormTextarea label={t.program.interviews.followUp} value={form.followUp} onChange={(event) => setForm({ ...form, followUp: event.currentTarget.value })} />{!item ? <UiGroup><FormSwitch label={t.program.interviews.visibleToSubject} checked={form.subjectVisible} onChange={(event) => setForm({ ...form, subjectVisible: event.currentTarget.checked })} /><FormSwitch label={t.program.interviews.visibleToReferences} checked={form.referencesVisible} onChange={(event) => setForm({ ...form, referencesVisible: event.currentTarget.checked })} /></UiGroup> : null}<FormActions secondary={<UiButton variant="default" onClick={onClose}>{t.common.action.cancel}</UiButton>} primary={<UiButton loading={create.isPending || update.isPending} disabled={!form.summary.trim() || (!item && !form.subjectEmployeeId)} onClick={() => void submit()}>{t.common.action.save}</UiButton>} /></UiStack></UiModal>;
}

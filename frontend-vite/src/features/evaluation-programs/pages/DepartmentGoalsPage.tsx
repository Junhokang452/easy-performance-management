import { useEffect, useState } from 'react';
import {
  FormActions, FormNumberInput, FormTextInput, FormTextarea, LookupSelect,
  MasterDetailWorkspace, PageHeader, PerformanceProgressSummary, SectionCard,
  UiBadge, UiButton, UiGroup, UiModal, UiStack, UiText,
} from '@easy/ui-components';

import { getErrorMessage } from '../../../api/error';
import { useAuth } from '../../../auth/AuthProvider';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import {
  useCreateDepartmentGoalMutation, useDepartmentGoalsQuery, useDepartmentOptionsQuery,
  useCopyDepartmentGoalMutation, useRecordDepartmentActualMutation, useTransferDepartmentGoalMutation, useUpdateDepartmentGoalMutation,
  useTasksQuery,
  type DepartmentGoalResponse, type DepartmentGoalUpsertRequest,
} from '../api/resources';
import { QueryState } from '../components/QueryState';

const today = new Date().toISOString().slice(0, 10);

export function DepartmentGoalsPage(): React.ReactNode {
  const t = useT();
  const { session } = useAuth();
  const admin = session?.roles.some((role) => role === 'HR_ADMIN' || role === 'SUPER_ADMIN') ?? false;
  const [departmentSearch, setDepartmentSearch] = useState('');
  const departments = useDepartmentOptionsQuery(departmentSearch);
  const [departmentId, setDepartmentId] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editing, setEditing] = useState<DepartmentGoalResponse | null | undefined>(undefined);
  const query = useDepartmentGoalsQuery({ departmentId: departmentId ?? undefined });
  const rows = query.data ?? [];
  const selected = rows.find((item) => item.id === selectedId) ?? rows[0] ?? null;
  useEffect(() => { if (!selectedId && rows[0]) setSelectedId(rows[0].id); }, [rows, selectedId]);
  return (
    <UiStack gap="md">
      <PageHeader title={t.program.departmentGoals.title} description={t.program.departmentGoals.description} actions={admin && departmentId ? <UiButton onClick={() => setEditing(null)}>{t.program.departmentGoals.create}</UiButton> : undefined} />
      <LookupSelect label={t.program.departmentGoals.tree} value={departmentId} onChange={(value) => { setDepartmentId(value); setSelectedId(null); }} data={(departments.data ?? []).map((item) => ({ value: item.value, label: item.label }))} searchValue={departmentSearch} onSearchChange={setDepartmentSearch} placeholder={t.program.common.select} />
      {!departmentId ? <SectionCard><UiText c="dimmed">{t.program.departmentGoals.tree}</UiText></SectionCard> : (
        <QueryState pending={query.isPending} error={query.error} empty={rows.length === 0} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>
          <MasterDetailWorkspace
            listLabel={t.program.departmentGoals.title}
            detailLabel={t.program.departmentGoals.detail}
            list={<UiStack gap="xs">{rows.map((goal) => <UiButton key={goal.id} variant={selected?.id === goal.id ? 'light' : 'subtle'} onClick={() => setSelectedId(goal.id)}>{goal.title}</UiButton>)}</UiStack>}
          >
            {selected ? <DepartmentGoalDetail goal={selected} editable={admin} onEdit={() => setEditing(selected)} /> : null}
          </MasterDetailWorkspace>
        </QueryState>
      )}
      {editing !== undefined && departmentId ? <DepartmentGoalEditor item={editing} departmentId={departmentId} onClose={() => setEditing(undefined)} /> : null}
    </UiStack>
  );
}

function DepartmentGoalDetail({ goal, editable, onEdit }: { goal: DepartmentGoalResponse; editable: boolean; onEdit: () => void }): React.ReactNode {
  const t = useT();
  const actual = useRecordDepartmentActualMutation();
  const tasks = useTasksQuery({});
  const copy = useCopyDepartmentGoalMutation(); const transfer = useTransferDepartmentGoalMutation(); const [departmentSearch, setDepartmentSearch] = useState(''); const departments = useDepartmentOptionsQuery(departmentSearch); const [targetDepartmentId, setTargetDepartmentId] = useState<string | null>(null); const [reason, setReason] = useState(''); const [copyYear, setCopyYear] = useState<number | string>(goal.year + 1); const [copyStart, setCopyStart] = useState(goal.periodStart); const [copyEnd, setCopyEnd] = useState(goal.periodEnd);
  const [actualValue, setActualValue] = useState<number | string>(goal.actualValue ?? '');
  const [rate, setRate] = useState<number | string>(goal.achievementRate ?? '');
  const [note, setNote] = useState(goal.actualNote ?? '');
  const saveActual = async (): Promise<void> => {
    try { await actual.mutateAsync({ id: goal.id, actualValue: Number(actualValue), achievementRate: Number(rate), note }); showToast({ tone: 'success', message: t.program.common.saveSuccess }); }
    catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); }
  };
  return (
    <UiStack gap="md">
      <SectionCard title={goal.title} description={goal.definition} actions={editable ? <UiButton variant="light" onClick={onEdit}>{t.common.action.edit}</UiButton> : undefined}>
        <UiGroup><UiBadge>{goal.targetLevel}</UiBadge><UiBadge variant="outline">{goal.weight}%</UiBadge><UiText size="sm">{goal.periodStart} – {goal.periodEnd}</UiText></UiGroup>
      </SectionCard>
      <SectionCard title={t.program.departmentGoals.progress}>
        <PerformanceProgressSummary label={t.program.departmentGoals.actual} value={goal.achievementRate ?? 0} total={100} valueLabel={`${goal.achievementRate ?? 0}%`} />
        {editable ? <UiStack mt="md" gap="sm"><UiGroup grow><FormNumberInput label={t.program.departmentGoals.actual} value={actualValue} onChange={setActualValue} /><FormNumberInput label={t.program.tasks.updateProgress} min={0} value={rate} onChange={setRate} suffix="%" /></UiGroup><FormTextarea label={t.program.common.comment} value={note} onChange={(event) => setNote(event.currentTarget.value)} /><UiButton loading={actual.isPending} disabled={actualValue === '' || rate === ''} onClick={() => void saveActual()}>{t.common.action.save}</UiButton></UiStack> : null}
      </SectionCard>
      <SectionCard title={t.program.departmentGoals.linkedTasks}><UiStack gap="xs">{(tasks.data ?? []).filter((task) => task.departmentGoalId === goal.id).map((task) => <UiGroup key={task.id} justify="space-between"><UiText fw={600}>{task.title}</UiText><UiBadge variant="light">{task.progressPercent}%</UiBadge></UiGroup>)}{!(tasks.data ?? []).some((task) => task.departmentGoalId === goal.id) ? <UiText c="dimmed">{t.program.common.noData}</UiText> : null}</UiStack></SectionCard>
      {editable ? <SectionCard title={t.program.departmentGoals.copy}><UiStack gap="sm"><LookupSelect label={t.program.departmentGoals.tree} value={targetDepartmentId} onChange={setTargetDepartmentId} data={(departments.data ?? []).map((item) => ({ value: item.value, label: item.label }))} searchValue={departmentSearch} onSearchChange={setDepartmentSearch} /><UiGroup grow><FormNumberInput label={t.program.common.year} value={copyYear} onChange={setCopyYear} /><FormTextInput type="date" label={t.program.programs.startDate} value={copyStart} onChange={(event) => setCopyStart(event.currentTarget.value)} /><FormTextInput type="date" label={t.program.programs.endDate} value={copyEnd} onChange={(event) => setCopyEnd(event.currentTarget.value)} /></UiGroup><FormTextarea label={t.program.operations.reason} value={reason} onChange={(event) => setReason(event.currentTarget.value)} /><UiGroup><UiButton variant="light" disabled={!targetDepartmentId || copyEnd < copyStart} loading={copy.isPending} onClick={() => targetDepartmentId && void copy.mutateAsync({ id: goal.id, input: { year: Number(copyYear), periodStart: copyStart, periodEnd: copyEnd, departmentId: targetDepartmentId } }).then(() => showToast({ tone: 'success', message: t.program.common.saveSuccess })).catch((error) => showToast({ tone: 'danger', message: getErrorMessage(error) }))}>{t.program.departmentGoals.copy}</UiButton><UiButton variant="light" disabled={!targetDepartmentId || !reason.trim()} loading={transfer.isPending} onClick={() => targetDepartmentId && void transfer.mutateAsync({ id: goal.id, targetDepartmentId, reason }).then(() => { setReason(''); showToast({ tone: 'success', message: t.program.common.saveSuccess }); }).catch((error) => showToast({ tone: 'danger', message: getErrorMessage(error) }))}>{t.program.departmentGoals.transfer}</UiButton></UiGroup></UiStack></SectionCard> : null}
    </UiStack>
  );
}

function DepartmentGoalEditor({ item, departmentId, onClose }: { item: DepartmentGoalResponse | null; departmentId: string; onClose: () => void }): React.ReactNode {
  const t = useT();
  const create = useCreateDepartmentGoalMutation(); const update = useUpdateDepartmentGoalMutation();
  const [form, setForm] = useState<DepartmentGoalUpsertRequest>(() => item ? { year: item.year, periodStart: item.periodStart, periodEnd: item.periodEnd, departmentId: item.departmentId, catalogId: item.catalogId, title: item.title, definition: item.definition, weight: item.weight, targetLevel: item.targetLevel, unit: item.unit } : { year: new Date().getFullYear(), periodStart: today, periodEnd: today, departmentId, catalogId: null, title: '', definition: '', weight: 0, targetLevel: '', unit: '' });
  const submit = async (): Promise<void> => { try { if (item) await update.mutateAsync({ id: item.id, input: form }); else await create.mutateAsync(form); showToast({ tone: 'success', message: t.program.common.saveSuccess }); onClose(); } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); } };
  const valid = form.title.trim() && form.definition.trim() && form.targetLevel.trim() && form.unit.trim() && form.periodEnd >= form.periodStart;
  return <UiModal opened onClose={onClose} title={item ? t.common.action.edit : t.program.departmentGoals.create} size="lg"><UiStack gap="sm"><UiGroup grow><FormNumberInput label={t.program.common.year} min={2000} max={2200} value={form.year} onChange={(value) => setForm({ ...form, year: Number(value) })} /><FormNumberInput label={t.program.goals.weight} min={0} max={100} value={form.weight} onChange={(value) => setForm({ ...form, weight: Number(value) })} /></UiGroup><UiGroup grow><FormTextInput type="date" label={t.program.programs.startDate} value={form.periodStart} onChange={(event) => setForm({ ...form, periodStart: event.currentTarget.value })} /><FormTextInput type="date" label={t.program.programs.endDate} value={form.periodEnd} onChange={(event) => setForm({ ...form, periodEnd: event.currentTarget.value })} /></UiGroup><FormTextInput label={t.program.catalogs.name} value={form.title} onChange={(event) => setForm({ ...form, title: event.currentTarget.value })} /><FormTextarea label={t.program.catalogs.definition} value={form.definition} onChange={(event) => setForm({ ...form, definition: event.currentTarget.value })} /><UiGroup grow><FormTextInput label={t.program.goals.achievementLevels} value={form.targetLevel} onChange={(event) => setForm({ ...form, targetLevel: event.currentTarget.value })} /><FormTextInput label={t.program.tasks.method} value={form.unit} onChange={(event) => setForm({ ...form, unit: event.currentTarget.value })} /></UiGroup><FormActions secondary={<UiButton variant="default" onClick={onClose}>{t.common.action.cancel}</UiButton>} primary={<UiButton loading={create.isPending || update.isPending} disabled={!valid} onClick={() => void submit()}>{t.common.action.save}</UiButton>} /></UiStack></UiModal>;
}

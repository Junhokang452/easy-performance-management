import { useEffect, useMemo, useState } from 'react';
import {
  FormActions, FormNumberInput, FormSelect, FormSwitch, FormTextInput, FormTextarea,
  LookupMultiSelect, MasterDetailWorkspace, PageHeader, SectionCard, UiBadge, UiButton,
  UiGroup, UiModal, UiStack, UiText,
} from '@easy/ui-components';

import { getErrorMessage } from '../../../api/error';
import { useAuth } from '../../../auth/AuthProvider';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import {
  useCatalogActiveMutation, useCatalogsQuery, useCopyCatalogMutation, useCreateCatalogMutation, useDeleteCatalogMutation,
  useDepartmentOptionsQuery, useJobOptionsQuery,
  useUpdateCatalogMutation, type CatalogKind, type CatalogResponse, type CatalogUpsertRequest,
} from '../api/resources';
import { QueryState } from '../components/QueryState';

const emptyCatalog = (): CatalogUpsertRequest => ({ kind: 'PERFORMANCE', category: '', name: '', definition: '', active: true, displayOrder: 0, achievementLevels: [], assignments: [] });

export function CatalogLibraryPage(): React.ReactNode {
  const t = useT();
  const { session } = useAuth();
  const admin = session?.roles.some((role) => role === 'HR_ADMIN' || role === 'SUPER_ADMIN') ?? false;
  const [kind, setKind] = useState<CatalogKind | undefined>();
  const [search, setSearch] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editing, setEditing] = useState<CatalogResponse | null | undefined>(undefined);
  const query = useCatalogsQuery({ kind, includeInactive: admin });
  const rows = useMemo(() => (query.data ?? []).filter((item) => item.name.toLocaleLowerCase().includes(search.toLocaleLowerCase()) || item.category.toLocaleLowerCase().includes(search.toLocaleLowerCase())), [query.data, search]);
  const selected = rows.find((item) => item.id === selectedId) ?? rows[0] ?? null;
  useEffect(() => { if (!selectedId && rows[0]) setSelectedId(rows[0].id); }, [rows, selectedId]);

  return (
    <UiStack gap="md">
      <PageHeader title={t.program.catalogs.title} description={t.program.catalogs.description} actions={admin ? <UiButton onClick={() => setEditing(null)}>{t.program.catalogs.create}</UiButton> : undefined} />
      <UiGroup align="end" wrap="wrap">
        <FormTextInput label={t.program.common.search} value={search} onChange={(event) => setSearch(event.currentTarget.value)} />
        <FormSelect clearable label={t.program.programs.type} value={kind ?? null} data={[{ value: 'PERFORMANCE', label: t.program.programs.performance }, { value: 'COMPETENCY', label: t.program.programs.competency }]} onChange={(value) => setKind(value as CatalogKind | undefined)} />
      </UiGroup>
      <QueryState pending={query.isPending} error={query.error} empty={rows.length === 0} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>
        <MasterDetailWorkspace
          listLabel={t.program.catalogs.list}
          detailLabel={t.program.catalogs.detail}
          list={<UiStack gap="xs">{rows.map((item) => <UiButton key={item.id} variant={selected?.id === item.id ? 'light' : 'subtle'} justify="space-between" onClick={() => setSelectedId(item.id)}><span>{item.name}</span><UiBadge size="xs">{item.active ? t.common.status.active : t.common.status.inactive}</UiBadge></UiButton>)}</UiStack>}
        >
          {selected ? <CatalogDetail item={selected} editable={admin} onEdit={() => setEditing(selected)} /> : null}
        </MasterDetailWorkspace>
      </QueryState>
      {editing !== undefined ? <CatalogEditor item={editing} onClose={() => setEditing(undefined)} /> : null}
    </UiStack>
  );
}

function CatalogDetail({ item, editable, onEdit }: { item: CatalogResponse; editable: boolean; onEdit: () => void }): React.ReactNode {
  const t = useT();
  const active = useCatalogActiveMutation();
  const remove = useDeleteCatalogMutation();
  const copy = useCopyCatalogMutation(); const [copyName, setCopyName] = useState(`${item.name} ${t.program.catalogs.duplicate}`);
  const run = async (action: () => Promise<unknown>): Promise<void> => {
    try { await action(); showToast({ tone: 'success', message: t.program.common.saveSuccess }); }
    catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); }
  };
  return (
    <UiStack gap="md">
      <SectionCard title={item.name} description={item.category} actions={editable ? <UiGroup gap="xs"><UiButton variant="light" onClick={onEdit}>{t.common.action.edit}</UiButton><UiButton variant="subtle" loading={active.isPending} onClick={() => void run(() => active.mutateAsync({ id: item.id, active: !item.active }))}>{item.active ? t.common.status.inactive : t.common.status.active}</UiButton>{item.active ? <UiButton color="red" variant="subtle" loading={remove.isPending} onClick={() => void run(() => remove.mutateAsync(item.id))}>{t.common.action.delete}</UiButton> : null}</UiGroup> : undefined}>
        <UiText>{item.definition}</UiText>
        <UiGroup mt="sm"><UiBadge>{item.kind === 'PERFORMANCE' ? t.program.programs.performance : t.program.programs.competency}</UiBadge><UiBadge variant="outline">{item.active ? t.common.status.active : t.common.status.inactive}</UiBadge></UiGroup>
      </SectionCard>
      <SectionCard title={t.program.catalogs.levels}>
        <UiStack gap="xs">{item.achievementLevels.map((level) => <UiGroup key={level.id} justify="space-between"><UiText fw={600}>{level.code} · {level.label}</UiText><UiText size="sm" c="dimmed">{level.minValue ?? '–'} – {level.maxValue ?? '–'}</UiText></UiGroup>)}</UiStack>
      </SectionCard>
      <SectionCard title={t.program.catalogs.assignments}>
        <UiGroup>{item.assignments.map((assignment) => <UiBadge key={assignment.id} variant="light">{assignment.type === 'JOB' ? t.program.catalogs.assignments : t.program.common.department}</UiBadge>)}</UiGroup>
      </SectionCard>
      {editable ? <SectionCard title={t.program.catalogs.duplicate}><UiGroup align="end"><FormTextInput label={t.program.catalogs.name} value={copyName} onChange={(event) => setCopyName(event.currentTarget.value)} /><UiButton disabled={!copyName.trim()} loading={copy.isPending} onClick={() => void run(() => copy.mutateAsync({ id: item.id, name: copyName }))}>{t.program.catalogs.duplicate}</UiButton></UiGroup></SectionCard> : null}
    </UiStack>
  );
}

function CatalogEditor({ item, onClose }: { item: CatalogResponse | null; onClose: () => void }): React.ReactNode {
  const t = useT();
  const create = useCreateCatalogMutation();
  const update = useUpdateCatalogMutation();
  const [form, setForm] = useState<CatalogUpsertRequest>(() => item ? { kind: item.kind, category: item.category, name: item.name, definition: item.definition, active: item.active, displayOrder: item.displayOrder, achievementLevels: item.achievementLevels.map(({ id: _id, ...level }) => level), assignments: item.assignments.map(({ id: _id, ...assignment }) => assignment) } : emptyCatalog());
  const [jobSearch, setJobSearch] = useState('');
  const [departmentSearch, setDepartmentSearch] = useState('');
  const jobs = useJobOptionsQuery(jobSearch);
  const departments = useDepartmentOptionsQuery(departmentSearch);
  const pending = create.isPending || update.isPending;
  const valid = form.name.trim() && form.category.trim() && form.definition.trim();

  const submit = async (): Promise<void> => {
    try {
      if (item) await update.mutateAsync({ id: item.id, input: form }); else await create.mutateAsync(form);
      showToast({ tone: 'success', message: t.program.common.saveSuccess }); onClose();
    } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); }
  };
  const jobValues = form.assignments.filter((entry) => entry.type === 'JOB').map((entry) => entry.reference);
  const departmentValues = form.assignments.filter((entry) => entry.type === 'DEPARTMENT').map((entry) => entry.reference);
  return (
    <UiModal opened onClose={onClose} title={item ? t.common.action.edit : t.program.catalogs.create} size="xl">
      <UiStack gap="sm">
        <FormSelect label={t.program.programs.type} value={form.kind} data={[{ value: 'PERFORMANCE', label: t.program.programs.performance }, { value: 'COMPETENCY', label: t.program.programs.competency }]} onChange={(value) => value && setForm({ ...form, kind: value as CatalogKind })} />
        <FormTextInput required label={t.program.catalogs.category} value={form.category} onChange={(event) => setForm({ ...form, category: event.currentTarget.value })} />
        <FormTextInput required label={t.program.catalogs.name} value={form.name} onChange={(event) => setForm({ ...form, name: event.currentTarget.value })} />
        <FormTextarea required minRows={3} label={t.program.catalogs.definition} value={form.definition} onChange={(event) => setForm({ ...form, definition: event.currentTarget.value })} />
        <UiGroup><FormNumberInput label={t.program.catalogs.order} min={0} value={form.displayOrder} onChange={(value) => setForm({ ...form, displayOrder: Number(value) || 0 })} /><FormSwitch label={t.program.catalogs.active} checked={form.active} onChange={(event) => setForm({ ...form, active: event.currentTarget.checked })} /></UiGroup>
        <LookupMultiSelect label={t.program.common.department} value={departmentValues} onChange={(values) => setForm({ ...form, assignments: [...form.assignments.filter((entry) => entry.type !== 'DEPARTMENT'), ...values.map((reference) => ({ type: 'DEPARTMENT' as const, reference }))] })} data={(departments.data ?? []).map((option) => ({ value: option.value, label: option.label }))} searchValue={departmentSearch} onSearchChange={setDepartmentSearch} />
        <LookupMultiSelect label={t.program.catalogs.assignments} value={jobValues} onChange={(values) => setForm({ ...form, assignments: [...form.assignments.filter((entry) => entry.type !== 'JOB'), ...values.map((reference) => ({ type: 'JOB' as const, reference }))] })} data={(jobs.data ?? []).map((option) => ({ value: option.value, label: option.label }))} searchValue={jobSearch} onSearchChange={setJobSearch} />
        <SectionCard title={t.program.catalogs.levels}>
          <UiStack gap="xs">
            {form.achievementLevels.map((level, index) => <SectionCard key={index}><UiStack gap="xs"><UiGroup align="end" grow><FormTextInput label={t.program.catalogs.code} value={level.code} onChange={(event) => setForm({ ...form, achievementLevels: form.achievementLevels.map((entry, i) => i === index ? { ...entry, code: event.currentTarget.value } : entry) })} /><FormTextInput label={t.program.catalogs.name} value={level.label} onChange={(event) => setForm({ ...form, achievementLevels: form.achievementLevels.map((entry, i) => i === index ? { ...entry, label: event.currentTarget.value } : entry) })} /><FormNumberInput label={t.program.settings.lowerBound} value={level.minValue ?? ''} onChange={(value) => setForm({ ...form, achievementLevels: form.achievementLevels.map((entry, i) => i === index ? { ...entry, minValue: value === '' ? null : Number(value) } : entry) })} /><FormNumberInput label={t.program.settings.upperBound} value={level.maxValue ?? ''} onChange={(value) => setForm({ ...form, achievementLevels: form.achievementLevels.map((entry, i) => i === index ? { ...entry, maxValue: value === '' ? null : Number(value) } : entry) })} /></UiGroup><FormTextarea label={t.program.catalogs.definition} value={level.description} onChange={(event) => setForm({ ...form, achievementLevels: form.achievementLevels.map((entry, i) => i === index ? { ...entry, description: event.currentTarget.value } : entry) })} /><UiButton color="red" variant="subtle" onClick={() => setForm({ ...form, achievementLevels: form.achievementLevels.filter((_, i) => i !== index) })}>{t.common.action.delete}</UiButton></UiStack></SectionCard>)}
            <UiButton variant="light" onClick={() => setForm({ ...form, achievementLevels: [...form.achievementLevels, { code: '', label: '', minValue: null, maxValue: null, description: '', displayOrder: form.achievementLevels.length }] })}>{t.common.action.create}</UiButton>
          </UiStack>
        </SectionCard>
        <FormActions secondary={<UiButton variant="default" onClick={onClose}>{t.common.action.cancel}</UiButton>} primary={<UiButton loading={pending} disabled={!valid} onClick={() => void submit()}>{t.common.action.save}</UiButton>} />
      </UiStack>
    </UiModal>
  );
}

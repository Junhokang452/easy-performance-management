import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  FormActions,
  FormNumberInput,
  FormSelect,
  FormTextInput,
  PageHeader,
  SectionCard,
  UiBadge,
  UiButton,
  UiGroup,
  UiModal,
  UiSimpleGrid,
  UiStack,
  UiText,
} from '@easy/ui-components';

import { getErrorMessage } from '../../../api/error';
import { useAuth } from '../../../auth/AuthProvider';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import { useCopyProgramMutation, useCreateProgramMutation, useProgramsQuery, type EvaluationKind, type ProgramCreateRequest, type ProgramSummaryResponse } from '../api/programs';
import { evaluationKindLabel, programStatusLabel } from '../components/programLabels';
import { QueryState } from '../components/QueryState';

const today = new Date().toISOString().slice(0, 10);
const initialForm = (): ProgramCreateRequest => ({
  name: '', evaluationYear: new Date().getFullYear(), asOfDate: today, startsOn: today, endsOn: today, kind: 'PERFORMANCE',
});

export function ProgramListPage(): React.ReactNode {
  const t = useT();
  const { session } = useAuth();
  const isAdmin = session?.roles.some((role) => role === 'HR_ADMIN' || role === 'SUPER_ADMIN') ?? false;
  const isReviewer = session?.roles.some((role) => role === 'MANAGER' || role === 'DIRECTOR') ?? false;
  const query = useProgramsQuery();
  const create = useCreateProgramMutation();
  const [opened, setOpened] = useState(false);
  const [copySource, setCopySource] = useState<ProgramSummaryResponse | null>(null);
  const [form, setForm] = useState<ProgramCreateRequest>(initialForm);
  const programs = query.data ?? [];

  const submit = async (): Promise<void> => {
    if (!form.name.trim() || form.endsOn < form.startsOn) return;
    try {
      await create.mutateAsync(form);
      showToast({ tone: 'success', message: t.common.message.created });
      setOpened(false);
      setForm(initialForm());
    } catch (error) {
      showToast({ tone: 'danger', message: getErrorMessage(error) });
    }
  };

  return (
    <UiStack gap="md">
      <PageHeader
        title={isAdmin ? t.program.programs.title : t.program.programs.myTitle}
        description={isAdmin ? t.program.programs.description : t.program.programs.myDescription}
        actions={isAdmin ? <UiButton onClick={() => setOpened(true)}>{t.program.programs.create}</UiButton> : undefined}
      />
      <QueryState
        pending={query.isPending}
        error={query.error}
        empty={programs.length === 0}
        loadingLabel={t.common.status.loading}
        errorLabel={t.program.common.loadError}
        emptyTitle={isAdmin ? t.program.programs.empty : t.program.programs.assignedEmpty}
        emptyDescription={isAdmin ? t.program.programs.emptyHint : t.program.programs.assignedEmptyHint}
      >
        <UiSimpleGrid cols={{ base: 1, md: 2, xl: 3 }} spacing="md">
          {programs.map((program) => {
            const target = isAdmin ? `/admin/evaluation-programs/${program.id}/setup` : isReviewer ? '/review-queue' : `/evaluations/${program.id}`;
            return (
              <SectionCard key={program.id} title={program.name} description={`${program.startsOn} – ${program.endsOn}`}>
                <UiStack gap="sm">
                  <UiGroup gap="xs">
                    <UiBadge variant="light">{programStatusLabel(t, program.status)}</UiBadge>
                    <UiBadge variant="outline">{evaluationKindLabel(t, program.kind)}</UiBadge>
                    <UiText size="sm" c="dimmed">{program.evaluationYear}</UiText>
                  </UiGroup>
                  <UiText size="sm">{t.program.operations.participants}: {program.participantCount}</UiText>
                  <UiButton component={Link} to={target} variant="light" fullWidth>
                    {isAdmin ? t.program.programs.setup : isReviewer ? t.program.nav.reviewQueue : t.program.programs.nextAction}
                  </UiButton>
                  {isAdmin ? <UiButton variant="subtle" fullWidth onClick={() => setCopySource(program)}>{t.program.programs.duplicate}</UiButton> : null}
                </UiStack>
              </SectionCard>
            );
          })}
        </UiSimpleGrid>
      </QueryState>

      <UiModal opened={opened} onClose={() => setOpened(false)} title={t.program.programs.create} centered>
        <UiStack gap="sm">
          <FormTextInput required label={t.program.programs.name} value={form.name} onChange={(event) => setForm({ ...form, name: event.currentTarget.value })} />
          <FormNumberInput required label={t.program.common.year} min={2000} max={2200} value={form.evaluationYear} onChange={(value) => setForm({ ...form, evaluationYear: Number(value) || new Date().getFullYear() })} />
          <FormSelect
            required
            label={t.program.programs.type}
            value={form.kind}
            data={(['PERFORMANCE', 'COMPETENCY'] as EvaluationKind[]).map((value) => ({ value, label: evaluationKindLabel(t, value) }))}
            onChange={(value) => value && setForm({ ...form, kind: value as EvaluationKind })}
          />
          <FormTextInput required type="date" label={t.program.programs.startDate} value={form.startsOn} onChange={(event) => setForm({ ...form, startsOn: event.currentTarget.value })} />
          <FormTextInput required type="date" label={t.program.programs.endDate} value={form.endsOn} onChange={(event) => setForm({ ...form, endsOn: event.currentTarget.value })} />
          <FormTextInput required type="date" label={t.program.common.asOfDate} value={form.asOfDate} onChange={(event) => setForm({ ...form, asOfDate: event.currentTarget.value })} />
          <FormActions
            secondary={<UiButton variant="default" onClick={() => setOpened(false)}>{t.common.action.cancel}</UiButton>}
            primary={<UiButton loading={create.isPending} disabled={!form.name.trim() || form.endsOn < form.startsOn} onClick={() => void submit()}>{t.common.action.create}</UiButton>}
          />
        </UiStack>
      </UiModal>
      {copySource ? <CopyProgramModal source={copySource} onClose={() => setCopySource(null)} /> : null}
    </UiStack>
  );
}

function CopyProgramModal({ source, onClose }: { source: ProgramSummaryResponse; onClose: () => void }): React.ReactNode {
  const t = useT(); const copy = useCopyProgramMutation();
  const [form, setForm] = useState(() => ({ name: `${source.name} ${t.program.programs.duplicate}`, evaluationYear: source.evaluationYear + 1, asOfDate: source.asOfDate, startsOn: source.startsOn, endsOn: source.endsOn }));
  const submit = async (): Promise<void> => { try { await copy.mutateAsync({ id: source.id, input: form }); showToast({ tone: 'success', message: t.common.message.created }); onClose(); } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); } };
  return <UiModal opened onClose={onClose} title={t.program.programs.duplicate} centered><UiStack gap="sm"><FormTextInput label={t.program.programs.name} value={form.name} onChange={(event) => setForm({ ...form, name: event.currentTarget.value })} /><FormNumberInput label={t.program.common.year} value={form.evaluationYear} onChange={(value) => setForm({ ...form, evaluationYear: Number(value) })} /><FormTextInput type="date" label={t.program.common.asOfDate} value={form.asOfDate} onChange={(event) => setForm({ ...form, asOfDate: event.currentTarget.value })} /><FormTextInput type="date" label={t.program.programs.startDate} value={form.startsOn} onChange={(event) => setForm({ ...form, startsOn: event.currentTarget.value })} /><FormTextInput type="date" label={t.program.programs.endDate} value={form.endsOn} onChange={(event) => setForm({ ...form, endsOn: event.currentTarget.value })} /><FormActions secondary={<UiButton variant="default" onClick={onClose}>{t.common.action.cancel}</UiButton>} primary={<UiButton disabled={!form.name.trim() || form.endsOn < form.startsOn} loading={copy.isPending} onClick={() => void submit()}>{t.program.programs.duplicate}</UiButton>} /></UiStack></UiModal>;
}

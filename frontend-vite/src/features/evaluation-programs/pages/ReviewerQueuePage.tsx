import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  FormSelect,
  MasterDetailWorkspace,
  PageHeader,
  PerformanceMetricGrid,
  SectionCard,
  UiBadge,
  UiButton,
  UiStack,
  UiText,
} from '@easy/ui-components';

import { useT } from '../../../i18n';
import { useProgramParticipantsQuery, useProgramsQuery, type ParticipantResponse } from '../api/programs';
import { programStageLabel } from '../components/programLabels';
import { QueryState } from '../components/QueryState';

export function ReviewerQueuePage(): React.ReactNode {
  const t = useT();
  const programs = useProgramsQuery();
  const [programId, setProgramId] = useState<string | null>(null);
  const participants = useProgramParticipantsQuery(programId);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const rows = participants.data ?? [];
  const selected = rows.find((row) => row.id === selectedId) ?? rows[0] ?? null;
  useEffect(() => { setSelectedId(null); }, [programId]);

  return (
    <UiStack gap="md">
      <PageHeader title={t.program.operations.title} description={t.program.operations.description} />
      <FormSelect
        searchable
        label={t.program.programs.title}
        value={programId}
        data={(programs.data ?? []).map((program) => ({ value: program.id, label: `${program.evaluationYear} · ${program.name}` }))}
        onChange={setProgramId}
      />
      {programId ? (
        <QueryState pending={participants.isPending} error={participants.error} empty={!rows.length} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>
          <MasterDetailWorkspace
            listLabel={t.program.reviews.queue}
            detailLabel={t.program.reviews.detail}
            list={<UiStack gap="xs">{rows.map((row) => <UiButton key={row.id} variant={selected?.id === row.id ? 'light' : 'subtle'} onClick={() => setSelectedId(row.id)}><UiStack gap={2} align="stretch"><UiText size="sm" fw={600}>{row.employee.name}</UiText><UiText size="xs" c="dimmed">{row.employee.orgUnitName ?? t.workspace.copy.notSpecified}</UiText></UiStack></UiButton>)}</UiStack>}
          >
            {selected ? <QueueDetail participant={selected} /> : null}
          </MasterDetailWorkspace>
        </QueryState>
      ) : <SectionCard><UiText c="dimmed">{t.program.common.noSelection}</UiText></SectionCard>}
    </UiStack>
  );
}

function QueueDetail({ participant }: { participant: ParticipantResponse }): React.ReactNode {
  const t = useT();
  return <UiStack gap="md"><SectionCard title={participant.employee.name} description={participant.employee.orgUnitName ?? t.workspace.copy.notSpecified} actions={<UiBadge>{programStageLabel(t, participant.currentStage, participant.currentRound)}</UiBadge>}><PerformanceMetricGrid items={[{ label: t.program.goals.weight, value: `${participant.weightPercent}%` }, { label: t.program.programs.currentStage, value: programStageLabel(t, participant.currentStage, participant.currentRound) }]} /></SectionCard><UiButton component={Link} to={`/admin/evaluation-programs/${participant.programId}/participants/${participant.id}/review/${Math.max(1, participant.currentRound)}`}>{t.program.reviews.detail}</UiButton></UiStack>;
}

import { useState } from 'react';
import { FormSelect, SectionCard, UiButton, UiGroup, UiStack, UiText } from '@easy/ui-components';
import { useT } from '../../../i18n';
import { auditEventTypes, useProgramAuditQuery } from '../api/audit';
import type { ParticipantResponse } from '../api/programs';
import { useEmployeeOptionsQuery } from '../api/resources';
import { QueryState } from './QueryState';

export function ProgramAuditTimeline({ programId, participants }: { programId: string; participants: ParticipantResponse[] }): React.ReactNode {
  const t = useT(); const labels = t.program.audit;
  const [page, setPage] = useState(0); const [eventType, setEventType] = useState(''); const [participantId, setParticipantId] = useState('');
  const audit = useProgramAuditQuery(programId, page, eventType, participantId);
  const employees = useEmployeeOptionsQuery('');
  const eventLabel = (type: string): string => type === 'RESULT_PDF_EXPORTED' ? t.program.customPdf.action : type === 'REMINDERS_QUEUED' ? t.program.responsibleReminders.title : type === 'KPI_LINK_APPLIED' ? t.program.kpiLinkage.title : labels.events[auditEventTypes.indexOf(type as typeof auditEventTypes[number])] ?? type;
  return <SectionCard title={labels.title} description={labels.description}><UiStack gap="md">
    <UiGroup align="end" wrap="wrap">
      <FormSelect label={labels.event} value={eventType} data={[{ value: '', label: t.program.common.all }, ...auditEventTypes.map((value) => ({ value, label: eventLabel(value) }))]} onChange={(value) => { setEventType(value ?? ''); setPage(0); }} />
      <FormSelect label={t.program.common.employee} value={participantId} data={[{ value: '', label: t.program.common.all }, ...participants.map((p) => ({ value: p.id, label: p.employee.name }))]} onChange={(value) => { setParticipantId(value ?? ''); setPage(0); }} />
      <UiButton variant="light" loading={audit.isFetching} onClick={() => void audit.refetch()}>{labels.refresh}</UiButton>
    </UiGroup>
    <QueryState pending={audit.isPending} error={audit.error} empty={!audit.data?.content.length} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>
      <UiStack gap="md">{audit.data?.content.map((row) => <UiStack key={row.id} gap={4}>
        <UiText fw={600}>{eventLabel(row.eventType)}</UiText>
        <UiText size="sm">{new Date(row.createdAt).toLocaleString()} · {participants.find((p) => p.id === row.participantId)?.employee.name ?? row.participantId ?? labels.programScope}</UiText>
        <UiText size="sm">{labels.actor}: {employees.data?.find((e) => e.id === row.actorEmployeeId)?.name ?? row.actorEmployeeId ?? labels.unknownActor}</UiText>
        <UiText size="sm">{labels.reason}: {row.reason === row.eventType ? eventLabel(row.eventType) : row.reason}</UiText>
      </UiStack>)}</UiStack>
    </QueryState>
    <UiGroup><UiButton variant="subtle" disabled={page === 0 || audit.isFetching} onClick={() => setPage((p) => p - 1)}>{labels.previous}</UiButton><UiText size="sm">{page + 1} / {Math.max(1, audit.data?.totalPages ?? 1)}</UiText><UiButton variant="subtle" disabled={!audit.data || page + 1 >= audit.data.totalPages || audit.isFetching} onClick={() => setPage((p) => p + 1)}>{labels.next}</UiButton></UiGroup>
  </UiStack></SectionCard>;
}

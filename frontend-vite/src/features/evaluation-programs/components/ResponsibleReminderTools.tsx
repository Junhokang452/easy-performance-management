import { useRef, useState } from 'react';
import { FormMultiSelect, FormTextarea, SectionCard, UiBadge, UiButton, UiGroup, UiModal, UiStack, UiTable, UiText } from '@easy/ui-components';
import { getErrorMessage } from '../../../api/error';
import { useI18n, useT, type Locale } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import { isReminderStale, reminderStages, useReminderHistoryQuery, useReminderParticipantsQuery, useReminderPreviewMutation, useReminderQueueMutation, type ReminderQueueInput, type ReminderScope, type ReminderStage } from '../api/responsibleReminders';
import type { ParticipantResponse, ProgramResponse } from '../api/programs';
import { useEmployeeOptionsQuery } from '../api/resources';
import { programStageLabel } from './programLabels';
import classes from './ResponsibleReminderTools.module.css';

type Props = { program: ProgramResponse; participants: ParticipantResponse[] };
export function ResponsibleReminderTools(props: Props): React.ReactNode {
  const { locale } = useI18n();
  return <ReminderWorkspace key={`${props.program.id}:${locale}`} {...props} locale={locale} />;
}

function ReminderPages({ page, total, busy, onChange }: { page: number; total: number; busy: boolean; onChange: (page: number) => void }): React.ReactNode {
  const l = useT().program.responsibleReminders;
  if (total <= 1) return null;
  return <UiGroup gap="xs"><UiButton variant="default" size="xs" disabled={busy || !page} onClick={() => onChange(page - 1)}>{l.previous}</UiButton><UiText size="sm">{page + 1} / {total}</UiText><UiButton variant="default" size="xs" disabled={busy || page + 1 >= total} onClick={() => onChange(page + 1)}>{l.next}</UiButton></UiGroup>;
}

function ReminderWorkspace({ program, participants, locale }: Props & { locale: Locale }): React.ReactNode {
  const t = useT(); const l = t.program.responsibleReminders;
  const [participantPage, setParticipantPage] = useState(0);
  const [historyPage, setHistoryPage] = useState(0);
  const roster = useReminderParticipantsQuery(program.id, participantPage);
  const history = useReminderHistoryQuery(program.id, historyPage);
  const employeeOptions = useEmployeeOptionsQuery('');
  const preview = useReminderPreviewMutation(program.id);
  const queue = useReminderQueueMutation(program.id);
  const [participantIds, setParticipantIds] = useState<string[]>([]);
  const [selectedNames, setSelectedNames] = useState<Record<string, string>>({});
  const [stages, setStages] = useState<ReminderStage[]>([]);
  const [scope, setScope] = useState<ReminderScope | null>(null);
  const [candidateKeys, setCandidateKeys] = useState<string[]>([]);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [reason, setReason] = useState('');
  const attempt = useRef<{ signature: string; payload: ReminderQueueInput } | null>(null);
  const submitting = useRef(false);
  const busy = preview.isPending || queue.isPending;
  const open = program.status === 'OPEN';
  const options = new Map(Object.entries(selectedNames));
  for (const p of roster.data?.content ?? (participantPage === 0 ? participants : [])) options.set(p.id, p.employee.name);
  const actionLabel = (value: string): string => (l.actions as Record<string, string>)[value] ?? value;
  const reasonLabel = (value: string): string => (l.reasons as Record<string, string>)[value] ?? value;
  const statusLabel = (value: string): string => ({ READY: l.ready, ALREADY_QUEUED: l.alreadyQueued, BLOCKED: l.blocked, SENT: l.queued, READ: l.read } as Record<string, string>)[value] ?? value;

  const clear = (): void => { preview.reset(); queue.reset(); setScope(null); setCandidateKeys([]); setConfirmOpen(false); setReason(''); attempt.current = null; };
  const runPreview = async (): Promise<void> => {
    if (busy || !participantIds.length || participantIds.length > 100) return;
    clear();
    const input = { participantIds: [...participantIds], stages: [...stages], locale };
    try {
      const result = await preview.mutateAsync(input);
      setScope(input); setCandidateKeys(result.candidates.filter((row) => row.status === 'READY').slice(0, 100).map((row) => row.candidateKey));
    } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); }
  };
  const runQueue = async (): Promise<void> => {
    if (!preview.data || !scope || !reason.trim() || !candidateKeys.length || submitting.current || !open) return;
    const input = { ...scope, reminderOn: preview.data.reminderOn, previewHash: preview.data.previewHash, candidateKeys: [...candidateKeys].sort(), reason: reason.trim() };
    const signature = JSON.stringify(input);
    if (attempt.current?.signature !== signature) attempt.current = { signature, payload: { ...input, idempotencyKey: crypto.randomUUID() } };
    submitting.current = true;
    try {
      const response = await queue.mutateAsync(attempt.current.payload);
      showToast({ tone: 'success', message: `${l.queued}: ${response.queued} · ${l.duplicateSuppressed}: ${response.duplicateSuppressed}` });
      setConfirmOpen(false); setReason(''); setScope(null); setCandidateKeys([]); preview.reset(); setHistoryPage(0); attempt.current = null;
    } catch (error) {
      showToast({ tone: 'danger', message: isReminderStale(error) ? l.stale : getErrorMessage(error) });
      if (isReminderStale(error)) { setConfirmOpen(false); setScope(null); preview.reset(); attempt.current = null; }
    } finally { submitting.current = false; }
  };

  return <SectionCard title={l.title} description={l.description}><UiStack gap="sm">
    <UiText size="sm">{l.utcPolicy}</UiText>
    <FormMultiSelect label={l.participants} description={l.scopeLimit} value={participantIds} data={[...options].map(([value, label]) => ({ value, label }))} searchable maxValues={100} disabled={busy || !open} onChange={(values) => { setParticipantIds(values); setSelectedNames(Object.fromEntries(values.map((id) => [id, options.get(id) ?? id]))); clear(); }} />
    <ReminderPages page={participantPage} total={roster.data?.totalPages ?? 0} busy={busy || roster.isFetching} onChange={setParticipantPage} />
    {roster.isError ? <UiText c="red">{getErrorMessage(roster.error)}</UiText> : null}
    <FormMultiSelect label={l.stages} placeholder={l.allStages} value={stages} data={reminderStages.map((value) => ({ value, label: programStageLabel(t, value) }))} disabled={busy || !open} onChange={(values) => { setStages(values as ReminderStage[]); clear(); }} />
    <UiGroup><UiButton loading={preview.isPending} disabled={busy || !open || !participantIds.length} onClick={() => void runPreview()}>{l.preview}</UiButton><UiButton variant="light" disabled={busy || !open || !scope || !candidateKeys.length} onClick={() => setConfirmOpen(true)}>{l.queue}</UiButton></UiGroup>
    {queue.isError ? <UiText c="red" size="sm">{isReminderStale(queue.error) ? l.stale : getErrorMessage(queue.error)}</UiText> : null}
    {queue.data ? <UiText size="sm">{l.queued}: {queue.data.queued} · {l.duplicateSuppressed}: {queue.data.duplicateSuppressed}</UiText> : null}
    {preview.data ? <UiStack gap="sm">
      <UiText size="sm">{preview.data.reminderOn} · {preview.data.zoneId} · {l.ready}: {preview.data.summary.ready} · {l.alreadyQueued}: {preview.data.summary.alreadyQueued} · {l.blocked}: {preview.data.summary.blocked}</UiText>
      <FormMultiSelect label={`${l.selected} (${candidateKeys.length}/100)`} value={candidateKeys} maxValues={100} disabled={busy} data={preview.data.candidates.filter((row) => row.status === 'READY').map((row) => ({ value: row.candidateKey, label: `${row.participantName} · ${actionLabel(row.action)} → ${row.recipientName}` }))} onChange={(values) => { setCandidateKeys(values); attempt.current = null; }} searchable />
      <UiTable.ScrollContainer minWidth={700}><UiTable striped withTableBorder><UiTable.Thead><UiTable.Tr>{[l.participant, l.action, l.recipient, l.dueDate, l.status].map((heading) => <UiTable.Th key={heading}>{heading}</UiTable.Th>)}</UiTable.Tr></UiTable.Thead><UiTable.Tbody>{preview.data.candidates.map((row) => <UiTable.Tr key={row.candidateKey}><UiTable.Td>{row.participantName}</UiTable.Td><UiText component="td" size="sm">{programStageLabel(t, row.stage, row.currentRound)} · {actionLabel(row.action)}</UiText><UiTable.Td>{row.recipientName ?? '—'}</UiTable.Td><UiTable.Td>{row.dueDate ?? '—'}</UiTable.Td><UiTable.Td><UiBadge>{statusLabel(row.status)}</UiBadge>{row.reasonCode ? <UiText size="xs">{reasonLabel(row.reasonCode)}</UiText> : null}</UiTable.Td></UiTable.Tr>)}</UiTable.Tbody></UiTable></UiTable.ScrollContainer>
      {!preview.data.candidates.length ? <UiText size="sm">{l.empty}</UiText> : null}
      {preview.data.exclusions.map((row) => <UiText key={`${row.participantId}:${row.reasonCode}`} size="sm" c="dimmed">{options.get(row.participantId) ?? row.participantId}: {reasonLabel(row.reasonCode)}</UiText>)}
      {preview.data.candidates.filter((row) => candidateKeys.includes(row.candidateKey)).map((row) => <SectionCard key={row.candidateKey} title={row.subject ?? actionLabel(row.action)} description={row.recipientName ?? undefined}><UiText size="sm" className={classes.message}>{row.body}</UiText></SectionCard>)}
    </UiStack> : <UiText size="sm" c="dimmed">{l.previewFirst}</UiText>}
    <UiText fw={600}>{l.history}</UiText>
    {history.isPending ? <UiText size="sm">{t.common.status.loading}</UiText> : null}
    {history.isError ? <UiText c="red">{getErrorMessage(history.error)}</UiText> : null}
    {history.data?.content.map((row) => <SectionCard key={row.notificationId} title={row.subject} description={`${actionLabel(row.action)} · ${statusLabel(row.notificationStatus)} · ${row.reminderOn} UTC`}><UiText size="sm" className={classes.message}>{l.recipient}: {employeeOptions.data?.find((employee) => employee.id === row.recipientEmployeeId)?.name ?? row.recipientEmployeeId}</UiText><UiText size="sm" className={classes.message}>{row.body}</UiText><UiText size="xs" c="dimmed">{l.registeredAt}: {new Date(row.registeredAt).toLocaleString(locale)} · {l.readAt}: {row.readAt ? new Date(row.readAt).toLocaleString(locale) : '—'}</UiText></SectionCard>)}
    {history.data && !history.data.content.length ? <UiText size="sm" c="dimmed">{l.empty}</UiText> : null}
    <ReminderPages page={historyPage} total={history.data?.totalPages ?? 0} busy={history.isFetching} onChange={setHistoryPage} />
  </UiStack><UiModal opened={confirmOpen} title={l.confirmTitle} centered onClose={() => { if (!queue.isPending) setConfirmOpen(false); }}><UiStack gap="sm"><UiText>{l.utcPolicy}</UiText><UiText>{l.selected}: {candidateKeys.length}</UiText><FormTextarea required label={l.reason} maxLength={500} value={reason} disabled={queue.isPending} onChange={(event) => setReason(event.currentTarget.value)} /><UiGroup justify="end"><UiButton variant="default" disabled={queue.isPending} onClick={() => setConfirmOpen(false)}>{l.cancel}</UiButton><UiButton loading={queue.isPending} disabled={!reason.trim() || queue.isPending || !scope || !open} onClick={() => void runQueue()}>{l.confirm}</UiButton></UiGroup></UiStack></UiModal></SectionCard>;
}

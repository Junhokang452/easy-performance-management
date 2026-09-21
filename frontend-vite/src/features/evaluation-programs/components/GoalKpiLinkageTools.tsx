import { useMemo, useState } from 'react';
import {
  FormSelect,
  FormTextInput,
  FormTextarea,
  SectionCard,
  UiBadge,
  UiButton,
  UiGroup,
  UiModal,
  UiStack,
  UiTable,
  UiText,
} from '@easy/ui-components';

import { getErrorMessage } from '../../../api/error';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import {
  isGoalKpiPreviewStale,
  useGoalKpiApplyMutation,
  useGoalKpiCandidatesQuery,
  useGoalKpiCyclesQuery,
  useGoalKpiLinkHistoryQuery,
  useGoalKpiPreviewMutation,
  type KpiLinkAvailability,
  type KpiLinkPreviewResponse,
} from '../api/goalKpiLinkage';
import type { GoalResponse, ParticipantResponse, ProgramResponse } from '../api/programs';
import classes from './GoalKpiLinkageTools.module.css';

function availabilityLabel(t: ReturnType<typeof useT>, status: KpiLinkAvailability): string {
  return {
    READY: t.program.kpiLinkage.ready,
    SOURCE_MISSING: t.program.kpiLinkage.sourceMissing,
    BLOCKED: t.program.kpiLinkage.blocked,
  }[status];
}

function EvidencePagination({ page, totalPages, pending, onChange }: { page: number; totalPages: number; pending?: boolean; onChange: (page: number) => void }): React.ReactNode {
  const labels = useT().program.kpiLinkage;
  if (totalPages <= 1) return null;
  return <UiGroup gap="xs"><UiButton variant="default" size="xs" disabled={pending || page === 0} onClick={() => onChange(page - 1)}>{labels.previousPage}</UiButton><UiText size="sm">{page + 1} / {totalPages}</UiText><UiButton variant="default" size="xs" disabled={pending || page + 1 >= totalPages} onClick={() => onChange(page + 1)}>{labels.nextPage}</UiButton></UiGroup>;
}

export function ParticipantKpiEvidence({ programId, participantId, goals }: { programId: string; participantId: string; goals: GoalResponse[] }): React.ReactNode {
  const labels = useT().program.kpiLinkage;
  const [goalId, setGoalId] = useState<string | null>(null);
  if (!goals.length) return null;
  return <SectionCard title={labels.evidenceHistory} description={labels.evidenceOnly}><UiStack gap="sm"><FormSelect label={labels.goal} value={goalId} onChange={setGoalId} data={goals.map((goal) => ({ value: goal.id, label: goal.title }))} searchable clearable />{goalId ? <GoalKpiEvidenceHistory key={goalId} programId={programId} participantId={participantId} goalId={goalId} /> : null}</UiStack></SectionCard>;
}

function numberValue(value: number | null): string {
  return value == null ? '—' : new Intl.NumberFormat(undefined, { maximumFractionDigits: 6 }).format(value);
}

function dateValue(value: string | null): string {
  return value ?? '—';
}

function PreviewEvidence({ preview }: { preview: KpiLinkPreviewResponse }): React.ReactNode {
  const t = useT();
  const row = preview.row;
  const reasonLabels: Record<string, string> = { GOAL_NOT_FROZEN: t.program.kpiLinkage.goalNotFrozen, TARGET_NOT_POSITIVE: t.program.kpiLinkage.targetInvalid, WEIGHT_NOT_POSITIVE: t.program.kpiLinkage.weightInvalid, ACTUAL_MISSING: t.program.kpiLinkage.actualMissing };
  return (
    <UiStack gap="xs" mt="sm">
      <UiGroup gap="xs" wrap="wrap">
        <UiBadge>{availabilityLabel(t, row.status)}</UiBadge>
        <UiBadge variant="outline">{row.formulaVersion}</UiBadge>
        <UiText size="sm">{t.program.kpiLinkage.programAsOfDate}: {preview.programAsOfDate}</UiText>
        <UiText size="sm">{t.program.kpiLinkage.cutoffDate}: {preview.actualCutoffDate}</UiText>
      </UiGroup>
      <UiTable.ScrollContainer minWidth={600}>
      <UiTable striped withTableBorder>
        <UiTable.Tbody>
          <UiTable.Tr><UiTable.Th>{t.program.kpiLinkage.goal}</UiTable.Th><UiTable.Td>{row.goalTitle} · {row.goalWeightPercent}%</UiTable.Td></UiTable.Tr>
          <UiTable.Tr><UiTable.Th>{t.program.kpiLinkage.node}</UiTable.Th><UiTable.Td>{row.treeName} · {row.nodeLabel}</UiTable.Td></UiTable.Tr>
          <UiTable.Tr><UiTable.Th>{t.program.kpiLinkage.targetCaptured}</UiTable.Th><UiTable.Td>{numberValue(row.effectiveTarget)} {row.unit ?? ''}</UiTable.Td></UiTable.Tr>
          <UiTable.Tr><UiTable.Th>{t.program.kpiLinkage.actual}</UiTable.Th><UiTable.Td>{numberValue(row.actualValue)} {row.unit ?? ''} · {dateValue(row.actualAsOfDate)}</UiTable.Td></UiTable.Tr>
          <UiTable.Tr><UiTable.Th>{t.program.kpiLinkage.achievementRate}</UiTable.Th><UiTable.Td>{numberValue(row.achievementRate)}</UiTable.Td></UiTable.Tr>
          <UiTable.Tr><UiTable.Th>{t.program.kpiLinkage.autoScore}</UiTable.Th><UiTable.Td>{numberValue(row.autoScore)}</UiTable.Td></UiTable.Tr>
          <UiTable.Tr><UiTable.Th>{t.program.kpiLinkage.formula}</UiTable.Th><UiTable.Td>{row.formula}</UiTable.Td></UiTable.Tr>
        </UiTable.Tbody>
      </UiTable>
      </UiTable.ScrollContainer>
      {row.reasonCode ? <UiText c="dimmed" size="sm">{t.program.kpiLinkage.reasonCode}: {reasonLabels[row.reasonCode] ?? row.reasonCode}</UiText> : null}
      <UiText c="dimmed" size="sm">{t.program.kpiLinkage.evidenceOnly}</UiText>
    </UiStack>
  );
}

export function GoalKpiEvidenceHistory({
  programId,
  participantId,
  goalId,
}: {
  programId: string;
  participantId: string;
  goalId: string;
}): React.ReactNode {
  const t = useT();
  const [page, setPage] = useState(0);
  const history = useGoalKpiLinkHistoryQuery(programId, participantId, goalId, page);
  if (history.isPending) return <UiText c="dimmed" size="sm">{t.common.status.loading}</UiText>;
  if (history.isError) return <UiText c="red" size="sm">{getErrorMessage(history.error)}</UiText>;
  if (!history.data?.content.length) return <UiText size="sm" c="dimmed">{t.program.kpiLinkage.emptyHistory}</UiText>;
  return (
    <UiStack gap="xs" mt="sm">
      <UiText fw={600} size="sm">{t.program.kpiLinkage.evidenceHistory}</UiText>
      {history.data.content.map((entry) => (
        <SectionCard key={entry.evidenceId} title={`${t.program.kpiLinkage.revision} ${entry.revision}`} description={`${entry.evidence.treeName} · ${entry.evidence.nodeLabel}`}>
          <UiGroup gap="xs" wrap="wrap">
            <UiBadge>{entry.active ? t.program.kpiLinkage.active : t.program.kpiLinkage.superseded}</UiBadge>
            <UiText size="sm">{t.program.kpiLinkage.cutoffDate}: {entry.actualCutoffDate}</UiText>
            <UiText size="sm">{t.program.kpiLinkage.actual}: {numberValue(entry.evidence.actualValue)} {entry.evidence.unit ?? ''}</UiText>
            <UiText size="sm">{t.program.kpiLinkage.targetCaptured}: {numberValue(entry.evidence.effectiveTarget)}</UiText>
            <UiText size="sm">{t.program.kpiLinkage.autoScore}: {numberValue(entry.evidence.autoScore)}</UiText>
          </UiGroup>
          <UiText mt="xs" c="dimmed" size="sm">{t.program.kpiLinkage.capturedAt}: {new Date(entry.appliedAt).toLocaleString()}</UiText>
          <UiText c="dimmed" size="sm">{t.program.kpiLinkage.reason}: {entry.reason}</UiText>
          <UiText c="dimmed" size="sm" className={classes.formula}>{t.program.kpiLinkage.formula}: {entry.evidence.formula}</UiText>
        </SectionCard>
      ))}
      <EvidencePagination page={page} totalPages={history.data.totalPages} pending={history.isFetching} onChange={setPage} />
    </UiStack>
  );
}

export function GoalKpiLinkageTools({
  program,
  participant,
  goals,
}: {
  program: ProgramResponse;
  participant: ParticipantResponse;
  goals: GoalResponse[];
}): React.ReactNode {
  const t = useT();
  const labels = t.program.kpiLinkage;
  const [cyclePage, setCyclePage] = useState(0);
  const [candidatePage, setCandidatePage] = useState(0);
  const cycles = useGoalKpiCyclesQuery(cyclePage);
  const [goalId, setGoalId] = useState<string | null>(null);
  const [cycleId, setCycleId] = useState<string | null>(null);
  const [actualCutoffDate, setActualCutoffDate] = useState('');
  const [kpiAssignmentId, setKpiAssignmentId] = useState<string | null>(null);
  const [previewInput, setPreviewInput] = useState<{ goalId: string; cycleId: string; actualCutoffDate: string; kpiAssignmentId: string } | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [reason, setReason] = useState('');
  const candidates = useGoalKpiCandidatesQuery({ programId: program.id, participantId: participant.id, cycleId, actualCutoffDate: actualCutoffDate || null, page: candidatePage });
  const preview = useGoalKpiPreviewMutation(program.id, participant.id, goalId ?? '');
  const apply = useGoalKpiApplyMutation(program.id, participant.id, previewInput?.goalId ?? '');
  const selectedGoal = useMemo(() => goals.find((goal) => goal.id === goalId) ?? null, [goalId, goals]);
  const selectedCandidate = useMemo(() => (candidates.data?.content ?? []).find((candidate) => candidate.kpiAssignmentId === kpiAssignmentId) ?? null, [candidates.data, kpiAssignmentId]);
  const busy = preview.isPending || apply.isPending;
  const selectedCycle = cycles.data?.content.find((cycle) => cycle.id === cycleId);
  const minCutoff = [program.startsOn, selectedCycle?.periodStart ?? program.startsOn].sort().at(-1);
  const today = new Date();
  const localToday = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  const maxCutoff = [program.endsOn, selectedCycle?.periodEnd ?? program.endsOn, localToday].sort()[0];

  const clearPreview = (): void => {
    preview.reset();
    apply.reset();
    setPreviewInput(null);
    setConfirmOpen(false);
    setReason('');
  };
  const changeGoal = (value: string | null): void => { setGoalId(value); clearPreview(); };
  const changeCycle = (value: string | null): void => { setCycleId(value); setCandidatePage(0); setKpiAssignmentId(null); clearPreview(); };
  const changeCutoff = (value: string): void => { setActualCutoffDate(value); setCandidatePage(0); setKpiAssignmentId(null); clearPreview(); };
  const changeAssignment = (value: string | null): void => { setKpiAssignmentId(value); clearPreview(); };
  const previewReady = Boolean(goalId && cycleId && actualCutoffDate && kpiAssignmentId);
  const runPreview = async (): Promise<void> => {
    if (!goalId || !cycleId || !actualCutoffDate || !kpiAssignmentId) return;
    try {
      await preview.mutateAsync({ cycleId, actualCutoffDate, kpiAssignmentId });
      setPreviewInput({ goalId, cycleId, actualCutoffDate, kpiAssignmentId });
    } catch (error) {
      showToast({ tone: 'danger', message: getErrorMessage(error) });
    }
  };
  const runApply = async (): Promise<void> => {
    if (!preview.data || !previewInput || !reason.trim()) return;
    try {
      const { cycleId: capturedCycle, actualCutoffDate: capturedCutoff, kpiAssignmentId: capturedAssignment } = previewInput;
      await apply.mutateAsync({ cycleId: capturedCycle, actualCutoffDate: capturedCutoff, kpiAssignmentId: capturedAssignment, previewHash: preview.data.previewHash, reason: reason.trim() });
      showToast({ tone: 'success', message: labels.applySuccess });
      setConfirmOpen(false);
      setReason('');
      preview.reset();
      setPreviewInput(null);
    } catch (error) {
      showToast({ tone: 'danger', message: isGoalKpiPreviewStale(error) ? labels.stale : getErrorMessage(error) });
    }
  };

  return (
    <SectionCard title={labels.title} description={labels.description}>
      <UiStack gap="sm">
        <UiText size="sm">{participant.employee.name} · {labels.programAsOfDate}: {program.asOfDate}</UiText>
        <FormSelect label={labels.goal} value={goalId} data={goals.map((goal) => ({ value: goal.id, label: `${goal.title} · ${goal.weightPercent}%` }))} onChange={changeGoal} searchable disabled={busy} />
        <FormSelect label={labels.cycle} value={cycleId} data={(cycles.data?.content ?? []).map((cycle) => ({ value: cycle.id, label: `${cycle.name} · ${cycle.periodStart} – ${cycle.periodEnd}` }))} onChange={changeCycle} searchable clearable disabled={busy || cycles.isPending} />
        <EvidencePagination page={cyclePage} totalPages={cycles.data?.totalPages ?? 0} pending={busy || cycles.isFetching} onChange={(page) => { setCyclePage(page); changeCycle(null); }} />
        {cycles.isError ? <UiText c="red" size="sm">{getErrorMessage(cycles.error)}</UiText> : null}
        <FormTextInput type="date" label={labels.cutoffDate} value={actualCutoffDate} min={minCutoff} max={maxCutoff} disabled={busy} onChange={(event) => changeCutoff(event.currentTarget.value)} />
        <FormSelect label={labels.kpiAssignment} value={kpiAssignmentId} data={(candidates.data?.content ?? []).map((candidate) => ({ value: candidate.kpiAssignmentId, label: `${candidate.treeName} · ${candidate.nodeLabel} · ${availabilityLabel(t, candidate.availability)}` }))} onChange={changeAssignment} searchable clearable disabled={busy || !cycleId || !actualCutoffDate || candidates.isPending} />
        <EvidencePagination page={candidatePage} totalPages={candidates.data?.totalPages ?? 0} pending={busy || candidates.isFetching} onChange={(page) => { setCandidatePage(page); changeAssignment(null); }} />
        {selectedGoal && selectedCandidate ? <UiText c="dimmed" size="sm">{labels.selectionSummary}: {selectedGoal.title} → {selectedCandidate.nodeLabel}</UiText> : null}
        {candidates.isError ? <UiText c="red" size="sm">{getErrorMessage(candidates.error)}</UiText> : null}
        <UiGroup>
          <UiButton disabled={!previewReady || busy} loading={preview.isPending} onClick={() => void runPreview()}>{labels.preview}</UiButton>
          {preview.data?.row.status === 'READY' ? <UiButton variant="light" disabled={busy || apply.isError} onClick={() => setConfirmOpen(true)}>{labels.apply}</UiButton> : null}
        </UiGroup>
        {preview.data ? <PreviewEvidence preview={preview.data} /> : null}
        {apply.isError && isGoalKpiPreviewStale(apply.error) ? <UiText c="red" size="sm">{labels.stale}</UiText> : null}
        {goalId ? <GoalKpiEvidenceHistory key={goalId} programId={program.id} participantId={participant.id} goalId={goalId} /> : null}
      </UiStack>
      <UiModal opened={confirmOpen} onClose={() => { if (!apply.isPending) setConfirmOpen(false); }} title={labels.confirmTitle} centered>
        <UiStack gap="sm">
          <UiText>{labels.confirmBody}</UiText>
          <FormTextarea required maxLength={500} label={labels.reason} value={reason} disabled={apply.isPending} onChange={(event) => setReason(event.currentTarget.value)} />
          <UiGroup justify="end">
            <UiButton variant="default" disabled={apply.isPending} onClick={() => setConfirmOpen(false)}>{t.common.action.cancel}</UiButton>
            <UiButton disabled={!reason.trim() || apply.isPending || apply.isError} loading={apply.isPending} onClick={() => void runApply()}>{labels.apply}</UiButton>
          </UiGroup>
        </UiStack>
      </UiModal>
    </SectionCard>
  );
}

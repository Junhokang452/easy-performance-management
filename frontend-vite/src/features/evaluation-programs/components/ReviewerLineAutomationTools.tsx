import { useState } from 'react';
import {
  FormMultiSelect,
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
  autoReviewerRoles,
  isReviewerLinePreviewStale,
  useReviewerLineApplyMutation,
  useReviewerLinePreviewMutation,
  type AutoReviewerRole,
  type ReviewerLinePreviewResponse,
  type ReviewerLinePreviewStatus,
} from '../api/reviewerLineAutomation';
import type { ParticipantResponse, ProgramResponse } from '../api/programs';

const maxParticipantSelection = 100;

function roleLabel(t: ReturnType<typeof useT>, role: AutoReviewerRole): string {
  return {
    AGREEMENT_REVIEWER: t.program.reviewerRoles.agreement,
    CHECKER: t.program.reviewerRoles.checker,
    REVIEWER: t.program.reviewerRoles.reviewer,
    FINAL_FEEDBACK: t.program.reviewerRoles.finalFeedback,
  }[role];
}

function previewStatusLabel(
  t: ReturnType<typeof useT>,
  status: ReviewerLinePreviewStatus,
): string {
  return {
    READY: t.program.automation.ready,
    SOURCE_MISSING: t.program.automation.sourceMissing,
    BLOCKED: t.program.automation.blocked,
    SKIPPED_EXISTING: t.program.automation.skippedExisting,
  }[status];
}

export function ReviewerLineAutomationTools({
  program,
  participants,
}: {
  program: ProgramResponse;
  participants: ParticipantResponse[];
}): React.ReactNode {
  const t = useT();
  const labels = t.program.automation;
  const preview = useReviewerLinePreviewMutation(program.id);
  const apply = useReviewerLineApplyMutation(program.id);
  const [participantIds, setParticipantIds] = useState<string[]>([]);
  const [roles, setRoles] = useState<AutoReviewerRole[]>(['REVIEWER']);
  const [previewInput, setPreviewInput] = useState<{ participantIds: string[]; roles: AutoReviewerRole[] } | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [reason, setReason] = useState('');

  const clearPreview = (): void => {
    preview.reset();
    apply.reset();
    setPreviewInput(null);
    setConfirmOpen(false);
    setReason('');
  };
  const changeParticipants = (next: string[]): void => {
    setParticipantIds(next.slice(0, maxParticipantSelection));
    clearPreview();
  };
  const changeRoles = (next: string[]): void => {
    setRoles(next.filter((role): role is AutoReviewerRole => autoReviewerRoles.includes(role as AutoReviewerRole)));
    clearPreview();
  };
  const canPreview = participantIds.length > 0 && participantIds.length <= maxParticipantSelection && roles.length > 0;
  const previewData = preview.data;
  const readyToApply = Boolean(previewData && previewInput && previewData.summary.ready > 0);

  const requestPreview = async (): Promise<void> => {
    if (!canPreview) return;
    const input = { participantIds: [...participantIds], roles: [...roles] };
    try {
      await preview.mutateAsync(input);
      setPreviewInput(input);
      apply.reset();
    } catch (error) {
      showToast({ tone: 'danger', message: getErrorMessage(error) });
    }
  };
  const applyPreview = async (): Promise<void> => {
    if (!previewData || !previewInput || !reason.trim()) return;
    try {
      await apply.mutateAsync({ ...previewInput, previewHash: previewData.previewHash, reason: reason.trim() });
      showToast({ tone: 'success', message: labels.applySuccess });
      preview.reset();
      setPreviewInput(null);
      setConfirmOpen(false);
      setReason('');
    } catch (error) {
      showToast({ tone: 'danger', message: isReviewerLinePreviewStale(error) ? labels.stale : getErrorMessage(error) });
    }
  };

  const participantOptions = participants
    .filter((participant) => participant.status === 'ACTIVE')
    .map((participant) => ({
      value: participant.id,
      label: `${participant.employee.name} · ${participant.employee.orgUnitName ?? t.workspace.copy.notSpecified}`,
    }));

  return (
    <SectionCard title={labels.title} description={labels.description}>
      <UiStack gap="sm">
        <FormMultiSelect
          label={labels.participants}
          value={participantIds}
          onChange={changeParticipants}
          data={participantOptions}
          searchable
          clearable
        />
        <UiText size="xs" c="dimmed">{labels.selectionHint.replace('{max}', String(maxParticipantSelection))}</UiText>
        <FormMultiSelect
          label={labels.roles}
          value={roles}
          onChange={changeRoles}
          data={autoReviewerRoles.map((role) => ({ value: role, label: roleLabel(t, role) }))}
          clearable
        />
        <UiGroup>
          <UiButton disabled={!canPreview} loading={preview.isPending} onClick={() => void requestPreview()}>
            {labels.preview}
          </UiButton>
          {previewData ? <UiButton variant="subtle" onClick={clearPreview}>{labels.retry}</UiButton> : null}
        </UiGroup>
        {preview.data ? <ReviewerLinePreview preview={preview.data} participants={participants} /> : null}
        {apply.data ? (
          <UiText size="sm" c="dimmed">
            {labels.applied}: {apply.data.applied} · {labels.skipped}: {apply.data.skipped}
          </UiText>
        ) : null}
        {apply.isError && isReviewerLinePreviewStale(apply.error) ? (
          <UiText size="sm" c="red">{labels.stale}</UiText>
        ) : null}
        {readyToApply ? (
          <UiButton onClick={() => setConfirmOpen(true)}>{labels.apply}</UiButton>
        ) : previewData ? <UiText size="sm" c="dimmed">{labels.emptyPreview}</UiText> : null}
      </UiStack>
      <UiModal opened={confirmOpen} onClose={() => setConfirmOpen(false)} title={labels.confirmTitle} size="lg">
        <UiStack gap="sm">
          <UiText>{labels.confirmBody.replace('{ready}', String(previewData?.summary.ready ?? 0)).replace('{skipped}', String((previewData?.summary.total ?? 0) - (previewData?.summary.ready ?? 0)))}</UiText>
          <UiText size="sm" c="dimmed">{labels.asOfDate}: {previewData?.asOfDate ?? program.asOfDate}</UiText>
          <FormTextarea label={labels.reason} value={reason} maxLength={500} onChange={(event) => setReason(event.currentTarget.value)} />
          <UiGroup justify="flex-end">
            <UiButton variant="default" onClick={() => setConfirmOpen(false)}>{t.common.action.cancel}</UiButton>
            <UiButton disabled={!reason.trim()} loading={apply.isPending} onClick={() => void applyPreview()}>{labels.apply}</UiButton>
          </UiGroup>
        </UiStack>
      </UiModal>
    </SectionCard>
  );
}

function ReviewerLinePreview({
  preview,
  participants,
}: {
  preview: ReviewerLinePreviewResponse;
  participants: ParticipantResponse[];
}): React.ReactNode {
  const t = useT();
  const labels = t.program.automation;
  const participantName = new Map(participants.map((participant) => [participant.id, participant.employee.name]));
  return (
    <UiStack gap="sm">
      <UiGroup>
        <UiBadge variant="light">{labels.ready}: {preview.summary.ready}</UiBadge>
        <UiBadge variant="light">{labels.sourceMissing}: {preview.summary.sourceMissing}</UiBadge>
        <UiBadge variant="light">{labels.blocked}: {preview.summary.blocked}</UiBadge>
        <UiBadge variant="light">{labels.skippedExisting}: {preview.summary.skippedExisting}</UiBadge>
      </UiGroup>
      <UiText size="xs" c="dimmed">
        {labels.asOfDate}: {preview.asOfDate} · {labels.definitionRevision}: {preview.definitionRevision} · {labels.generatedAt}: {new Date(preview.generatedAt).toLocaleString()}
      </UiText>
      <UiTable.ScrollContainer minWidth={900}>
        <UiTable striped highlightOnHover>
          <UiTable.Thead>
            <UiTable.Tr>
              <UiTable.Th>{labels.participants}</UiTable.Th>
              <UiTable.Th>{labels.source}</UiTable.Th>
              <UiTable.Th>{labels.proposals}</UiTable.Th>
              <UiTable.Th>{t.program.common.status}</UiTable.Th>
              <UiTable.Th>{labels.issues}</UiTable.Th>
            </UiTable.Tr>
          </UiTable.Thead>
          <UiTable.Tbody>
            {preview.rows.map((row) => {
              const effectivePeriod = row.sourceEffectiveFrom
                ? `${row.sourceEffectiveFrom} – ${row.sourceEffectiveTo ?? t.workspace.copy.notSpecified}`
                : t.workspace.copy.notSpecified;
              return (
                <UiTable.Tr key={row.participantId}>
                  <UiTable.Td>{participantName.get(row.participantId) ?? row.participantEmployeeId}</UiTable.Td>
                  <UiTable.Td>
                    <UiText size="sm">{labels.source}: {row.sourceSystem ?? t.workspace.copy.notSpecified}</UiText>
                    <UiText size="xs" c="dimmed">{labels.effectivePeriod}: {effectivePeriod}</UiText>
                    {row.sourceDeleted ? <UiText size="xs" c="red">{labels.sourceDeleted}</UiText> : null}
                    {row.currentReviewerCount > 0 ? <UiText size="xs" c="dimmed">{labels.existingReviewerCount}: {row.currentReviewerCount}</UiText> : null}
                  </UiTable.Td>
                  <UiTable.Td>
                    <UiText size="sm">{row.proposedReviewerName ?? row.proposedReviewerEmployeeId ?? t.workspace.copy.notSpecified}</UiText>
                    <UiText size="xs" c="dimmed">
                      {row.proposals.map((proposal) => `${roleLabel(t, proposal.role)} · ${proposal.round} · ${proposal.weightPercent}%`).join(', ') || t.workspace.copy.notSpecified}
                    </UiText>
                  </UiTable.Td>
                  <UiTable.Td><UiBadge variant="light">{previewStatusLabel(t, row.status)}</UiBadge></UiTable.Td>
                  <UiTable.Td>{row.issues.map((issue) => <UiText key={`${issue.code}-${issue.message}`} size="xs">{issue.code}: {issue.message}</UiText>)}</UiTable.Td>
                </UiTable.Tr>
              );
            })}
          </UiTable.Tbody>
        </UiTable>
      </UiTable.ScrollContainer>
    </UiStack>
  );
}

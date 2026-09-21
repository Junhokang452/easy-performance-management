import { useEffect, useState } from 'react';
import { Group, Select, Stack, Text, Textarea } from '@easy/ui-components/mantine';
import {
  EmptyState,
  LoadingState,
  PerformanceMetricGrid,
  PerformanceProgressSummary,
  SectionCard,
  UiAlert,
  UiBadge,
  UiButton,
  UiTable,
} from '@easy/ui-components';

import type { FeedbackTask, WorkspaceRole } from '../../../api/evaluationWorkspace';
import {
  useCompleteFeedbackMutation,
  useFeedbackTasksQuery,
  useResolveAppealMutation,
  useResultsSummaryQuery,
  useSaveFeedbackMutation,
} from '../../../api/evaluationWorkspace';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import { employeeLabel, statusLabel } from '../workspaceLabels';
import { workspaceErrorMessage } from '../workspaceError';

export function ResultsAnalysisPanel({ cycleId }: { cycleId: string }): React.ReactNode {
  const t = useT();
  const resultsQuery = useResultsSummaryQuery(cycleId, true);
  if (resultsQuery.isLoading) return <LoadingState message={t.workspace.copy.loadingResults} />;
  if (resultsQuery.isError) return <UiAlert color="red">{workspaceErrorMessage(t, resultsQuery.error)}</UiAlert>;
  if (!resultsQuery.data) return null;
  const summary = resultsQuery.data;
  return (
    <SectionCard>
      <Stack>
        <Group justify="space-between">
          <Text fw={600}>{t.workspace.copy.resultsAnalysis}</Text>
          <UiBadge variant="light">
            {t.workspace.copy.finalized} {summary.finalizedCount}/{summary.participantCount}
          </UiBadge>
        </Group>
        <PerformanceMetricGrid
          items={[
            { key: 'participants', label: t.workspace.participantSelect, value: summary.participantCount },
            { key: 'average', label: t.workspace.copy.averageScore, value: summary.averageScore ?? t.workspace.copy.notCalculated },
            { key: 'published', label: t.workspace.publish, value: summary.publishedCount },
            { key: 'acknowledged', label: t.workspace.acknowledge, value: summary.acknowledgedCount },
          ]}
        />
        <PerformanceProgressSummary
          label={t.workspace.copy.finalized}
          value={summary.finalizedCount}
          total={summary.participantCount}
          color="green"
        />
        <PerformanceProgressSummary
          label={t.workspace.publish}
          value={summary.publishedCount}
          total={summary.participantCount}
          color="blue"
        />
        {summary.orgUnitRows.length === 0 ? (
          <EmptyState title={t.workspace.copy.noResultRows} />
        ) : (
          <UiTable.ScrollContainer minWidth={680}>
            <UiTable striped>
            <UiTable.Thead>
              <UiTable.Tr>
                <UiTable.Th>{t.workspace.copy.org}</UiTable.Th>
                <UiTable.Th>{t.workspace.participantSelect}</UiTable.Th>
                <UiTable.Th>{t.workspace.copy.averageScore}</UiTable.Th>
                <UiTable.Th>{t.workspace.copy.gradeDistribution}</UiTable.Th>
              </UiTable.Tr>
            </UiTable.Thead>
            <UiTable.Tbody>
              {summary.orgUnitRows.map((row) => (
                <UiTable.Tr key={row.orgUnitId ?? 'company'}>
                  <UiTable.Td>{row.orgUnitName ?? t.workspace.copy.companyWide}</UiTable.Td>
                  <UiTable.Td>{row.participantCount}</UiTable.Td>
                  <UiTable.Td>{row.averageScore ?? '—'}</UiTable.Td>
                  <UiTable.Td>
                    {Object.entries(row.gradeCounts).map(([grade, count]) => `${grade} ${count}`).join(' · ') || '—'}
                  </UiTable.Td>
                </UiTable.Tr>
              ))}
            </UiTable.Tbody>
            </UiTable>
          </UiTable.ScrollContainer>
        )}
      </Stack>
    </SectionCard>
  );
}

export function FeedbackOperationsPanel({
  cycleId,
  role,
}: {
  cycleId: string;
  role: WorkspaceRole;
}): React.ReactNode {
  const t = useT();
  const query = useFeedbackTasksQuery(cycleId, true);
  const [selectedReportId, setSelectedReportId] = useState<string | null>(null);
  useEffect(() => {
    const tasks = query.data ?? [];
    if (tasks.length > 0 && !tasks.some((task) => task.reportId === selectedReportId)) {
      setSelectedReportId(tasks[0]?.reportId ?? null);
    }
  }, [query.data, selectedReportId]);

  if (query.isLoading) return <LoadingState message={t.workspace.copy.loadingFeedback} />;
  if (query.isError) return <UiAlert color="red">{workspaceErrorMessage(t, query.error)}</UiAlert>;
  const tasks = query.data ?? [];
  if (tasks.length === 0) return null;
  const selected = tasks.find((task) => task.reportId === selectedReportId) ?? tasks[0] ?? null;
  const isHr = role === 'HR_ADMIN' || role === 'SUPER_ADMIN';
  return (
    <SectionCard>
      <Stack>
        <Text fw={600}>{t.workspace.copy.feedbackOperations}</Text>
        <Select
          label={t.workspace.copy.reviewSubject}
          value={selected?.reportId ?? null}
          onChange={setSelectedReportId}
          data={tasks.map((task) => ({
            value: task.reportId,
            label: `${employeeLabel(task.employee)} · ${statusLabel(t, task.status)}`,
          }))}
        />
        {selected && <FeedbackTaskEditor key={selected.reportId} task={selected} isHr={isHr} />}
      </Stack>
    </SectionCard>
  );
}

function FeedbackTaskEditor({
  task,
  isHr,
}: {
  task: FeedbackTask;
  isHr: boolean;
}): React.ReactNode {
  const t = useT();
  const saveMutation = useSaveFeedbackMutation();
  const completeMutation = useCompleteFeedbackMutation();
  const resolveMutation = useResolveAppealMutation();
  const [comment, setComment] = useState(task.comment ?? '');
  const [resolution, setResolution] = useState<'UPHELD' | null>(null);
  const [resolutionComment, setResolutionComment] = useState('');
  const actions = new Set(task.allowedActions);
  const editable = !isHr && actions.has('EDIT_FEEDBACK');
  const canComplete = !isHr && actions.has('COMPLETE_FEEDBACK');
  const canResolve = isHr && actions.has('RESOLVE_APPEAL');
  const reportError = (error: unknown): void =>
    showToast({ tone: 'danger', message: workspaceErrorMessage(t, error) });

  return (
    <Stack>
      <Group justify="space-between">
        <Text fw={600}>{employeeLabel(task.employee)}</Text>
        <UiBadge variant="light">{statusLabel(t, task.status)}</UiBadge>
      </Group>
      {task.appealReason && (
        <UiAlert color="yellow" title={t.workspace.copy.appealReason}>{task.appealReason}</UiAlert>
      )}
      {editable && (
        <>
          <Textarea
            label={t.workspace.copy.feedbackComment}
            value={comment}
            onChange={(event) => setComment(event.currentTarget.value)}
            minRows={4}
          />
          <Group justify="flex-end">
            <UiButton
              variant="light"
              disabled={!comment.trim()}
              loading={saveMutation.isPending}
              onClick={() => saveMutation.mutate(
                { reportId: task.reportId, comment: comment.trim() },
                {
                  onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.feedbackSaved }),
                  onError: reportError,
                },
              )}
            >
              {t.workspace.copy.feedbackSave}
            </UiButton>
            {canComplete && (
              <UiButton
                disabled={!comment.trim()}
                loading={completeMutation.isPending}
                onClick={() => completeMutation.mutate(
                  { reportId: task.reportId, comment: comment.trim() },
                  {
                    onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.feedbackCompleted }),
                    onError: reportError,
                  },
                )}
              >
                {t.workspace.copy.completeFeedback}
              </UiButton>
            )}
          </Group>
        </>
      )}
      {canResolve && (
        <>
          <Select
            label={t.workspace.copy.resolution}
            value={resolution}
            onChange={(value) => setResolution(value as typeof resolution)}
            data={[
              { value: 'UPHELD', label: t.workspace.copy.resolutionUpheld },
            ]}
          />
          <Textarea
            label={t.workspace.copy.resolutionComment}
            value={resolutionComment}
            onChange={(event) => setResolutionComment(event.currentTarget.value)}
            minRows={3}
          />
          <Group justify="flex-end">
            <UiButton
              disabled={!resolution || !resolutionComment.trim()}
              loading={resolveMutation.isPending}
              onClick={() => resolveMutation.mutate(
                {
                  reportId: task.reportId,
                  resolution: resolution as 'UPHELD',
                  comment: resolutionComment.trim(),
                },
                {
                  onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.appealResolved }),
                  onError: reportError,
                },
              )}
            >
              {t.workspace.copy.resolveAppeal}
            </UiButton>
          </Group>
        </>
      )}
      {!editable && !canResolve && (
        <Text size="sm" c="dimmed">{task.comment || t.workspace.copy.noFeedbackAction}</Text>
      )}
    </Stack>
  );
}

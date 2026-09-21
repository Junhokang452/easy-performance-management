import { useEffect, useState } from 'react';
import { blockerLabel } from '../workspaceLabels';
import { Group, NumberInput, Stack, Text, Textarea } from '@easy/ui-components/mantine';
import {
  EmptyState,
  LoadingState,
  PerformanceCommentPanel,
  SectionCard,
  UiAlert,
  UiBadge,
  UiButton,
  UiTable,
} from '@easy/ui-components';

import {
  type AllowedAction,
  type ManagerTask,
  useCompleteManagerIntermediateMutation,
  useGoalDecisionMutation,
  useManagerTasksQuery,
  useParticipantGoalsQuery,
  useParticipantIntermediateQuery,
  useSaveManagerIntermediateMutation,
  useSaveManagerReviewMutation,
  useSubmitManagerReviewMutation,
  useWorkspaceReviewItemsQuery,
} from '../../../api/evaluationWorkspace';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import { employeeLabel, statusLabel } from '../workspaceLabels';
import { workspaceErrorMessage } from '../workspaceError';

export function TeamTasksPanel({ cycleId }: { cycleId: string }): React.ReactNode {
  const t = useT();
  const tasksQuery = useManagerTasksQuery(cycleId);
  const [selectedParticipantId, setSelectedParticipantId] = useState<string | null>(null);

  useEffect(() => {
    const tasks = tasksQuery.data ?? [];
    if (tasks.length > 0 && !tasks.some((task) => task.participantId === selectedParticipantId)) {
      setSelectedParticipantId(tasks[0]?.participantId ?? null);
    }
  }, [selectedParticipantId, tasksQuery.data]);

  if (tasksQuery.isLoading) return <LoadingState message={t.workspace.copy.teamLoading} />;
  if (tasksQuery.isError) return <UiAlert color="red">{workspaceErrorMessage(t, tasksQuery.error)}</UiAlert>;
  const tasks = tasksQuery.data ?? [];
  if (tasks.length === 0) {
    return (
      <EmptyState
        title={t.workspace.copy.noTeamTitle}
        description={t.workspace.copy.noTeamDescription}
      />
    );
  }
  const selected = tasks.find((task) => task.participantId === selectedParticipantId) ?? tasks[0] ?? null;

  return (
    <SectionCard>
      <Stack>
        <Group justify="space-between">
          <Text fw={600}>{t.workspace.teamReview}</Text>
          <UiBadge variant="light">{tasks.length}{t.workspace.copy.peopleSuffix}</UiBadge>
        </Group>
        <UiTable striped highlightOnHover>
          <UiTable.Thead>
            <UiTable.Tr>
              <UiTable.Th>{t.workspace.copy.member}</UiTable.Th>
              <UiTable.Th>{t.workspace.goals}</UiTable.Th>
              <UiTable.Th visibleFrom="sm">{t.workspace.checkIn}</UiTable.Th>
              <UiTable.Th visibleFrom="sm">{t.workspace.teamReview}</UiTable.Th>
              <UiTable.Th visibleFrom="sm">{t.workspace.copy.nextWork}</UiTable.Th>
            </UiTable.Tr>
          </UiTable.Thead>
          <UiTable.Tbody>
            {tasks.map((task) => (
              <UiTable.Tr key={task.participantId}>
                <UiTable.Td>
                  <UiButton
                    variant={selected?.participantId === task.participantId ? 'light' : 'subtle'}
                    size="xs"
                    onClick={() => setSelectedParticipantId(task.participantId)}
                  >
                    {employeeLabel(task.employee)}
                  </UiButton>
                </UiTable.Td>
                <UiTable.Td>
                  {task.goalPendingApproval > 0
                    ? `${task.goalPendingApproval}${t.workspace.copy.pendingApprovalSuffix}`
                    : `${task.goalTotal}${t.workspace.copy.goalCountSuffix}`}
                </UiTable.Td>
                <UiTable.Td visibleFrom="sm">{statusLabel(t, task.intermediateReviewStatus)}</UiTable.Td>
                <UiTable.Td visibleFrom="sm">{statusLabel(t, task.reviewStatus)}</UiTable.Td>
                <UiTable.Td visibleFrom="sm">
                  <Text size="xs" c="dimmed">
                    {task.blockers[0] ? blockerLabel(t, task.blockers[0].code) : t.workspace.copy.nextWorkReady}
                  </Text>
                </UiTable.Td>
              </UiTable.Tr>
            ))}
          </UiTable.Tbody>
        </UiTable>
        {selected && <ManagerTaskDetail key={selected.participantId} task={selected} />}
      </Stack>
    </SectionCard>
  );
}

function ManagerTaskDetail({ task }: { task: ManagerTask }): React.ReactNode {
  const t = useT();
  const actions = new Set<AllowedAction>(task.allowedActions);
  const goalsQuery = useParticipantGoalsQuery(task.participantId);
  const intermediateQuery = useParticipantIntermediateQuery(task.participantId);
  const decisionMutation = useGoalDecisionMutation();
  const saveMutation = useSaveManagerIntermediateMutation();
  const completeMutation = useCompleteManagerIntermediateMutation();
  const [decisionComment, setDecisionComment] = useState('');
  const [managerComment, setManagerComment] = useState('');
  const [managerCommentDirty, setManagerCommentDirty] = useState(false);

  useEffect(() => {
    if (!managerCommentDirty) setManagerComment(intermediateQuery.data?.managerComment ?? '');
  }, [intermediateQuery.data?.managerComment, managerCommentDirty]);

  const reportError = (error: unknown): void =>
    showToast({ tone: 'danger', message: workspaceErrorMessage(t, error) });

  const completeIntermediate = async (): Promise<void> => {
    try {
      await saveMutation.mutateAsync({ participantId: task.participantId, comment: managerComment.trim() });
      await completeMutation.mutateAsync(task.participantId);
      showToast({ tone: 'success', message: t.workspace.copy.checkInCompleted });
    } catch (error) {
      reportError(error);
    }
  };

  return (
    <Stack mt="sm">
      <Text fw={600}>{task.employee.name}{t.workspace.copy.reviewPersonSuffix}</Text>
      {goalsQuery.isError && <UiAlert color="red">{workspaceErrorMessage(t, goalsQuery.error)}</UiAlert>}
      {goalsQuery.isLoading ? (
        <LoadingState message={t.workspace.copy.loadingGoals} />
      ) : (goalsQuery.data ?? []).length === 0 ? (
        <Text size="sm" c="dimmed">{t.workspace.copy.noGoals}</Text>
      ) : (
        (goalsQuery.data ?? []).map((goal) => (
          <SectionCard key={goal.id}>
            <Group justify="space-between" align="flex-start">
              <Stack gap={2}>
                <Text size="sm" fw={600}>{goal.title}</Text>
                <Text size="xs" c="dimmed">
                  {statusLabel(t, goal.status)} · {Math.round(goal.weight * 100)}%
                  {goal.target != null ? ` · ${t.workspace.copy.target} ${goal.target} ${goal.unit ?? ''}` : ''}
                </Text>
                {goal.description && <Text size="sm">{goal.description}</Text>}
              </Stack>
              {actions.has('REVIEW_GOAL') && goal.status === 'PENDING_APPROVAL' && (
                <Group>
                  <UiButton
                    size="xs"
                    variant="light"
                    loading={decisionMutation.isPending}
                    disabled={!decisionComment.trim()}
                    onClick={() => decisionMutation.mutate(
                      { goalId: goal.id, decision: 'REJECT', comment: decisionComment.trim() },
                      {
                        onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.goalRejected }),
                        onError: reportError,
                      },
                    )}
                  >
                    {t.workspace.copy.requestRevision}
                  </UiButton>
                  <UiButton
                    size="xs"
                    loading={decisionMutation.isPending}
                    onClick={() => decisionMutation.mutate(
                      { goalId: goal.id, decision: 'APPROVE', comment: decisionComment.trim() },
                      {
                        onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.goalApproved }),
                        onError: reportError,
                      },
                    )}
                  >
                    {t.workspace.copy.approve}
                  </UiButton>
                </Group>
              )}
            </Group>
          </SectionCard>
        ))
      )}
      {actions.has('REVIEW_GOAL') && (
        <Textarea
          label={t.workspace.copy.goalDecisionComment}
          value={decisionComment}
          onChange={(event) => setDecisionComment(event.currentTarget.value)}
          placeholder={t.workspace.copy.goalDecisionPlaceholder}
        />
      )}
      {intermediateQuery.isError && <UiAlert color="red">{workspaceErrorMessage(t, intermediateQuery.error)}</UiAlert>}
      {intermediateQuery.data && (
        <Stack gap="xs">
          <PerformanceCommentPanel
            title={t.workspace.progress}
            comment={intermediateQuery.data.progressSummary}
            empty={t.workspace.copy.noFeedbackAction}
          />
          <PerformanceCommentPanel
            title={t.workspace.achievements}
            comment={intermediateQuery.data.achievements}
            empty={t.workspace.copy.noFeedbackAction}
          />
          <PerformanceCommentPanel
            title={t.workspace.blockers}
            comment={intermediateQuery.data.blockers}
            empty={t.workspace.copy.noFeedbackAction}
          />
          <PerformanceCommentPanel
            title={t.workspace.supportNeeded}
            comment={intermediateQuery.data.supportNeeded}
            empty={t.workspace.copy.noFeedbackAction}
          />
        </Stack>
      )}
      {actions.has('COMPLETE_INTERMEDIATE_REVIEW') && (
        <>
          <Textarea
            label={t.workspace.copy.reviewerFeedback}
            value={managerComment}
            onChange={(event) => {
              setManagerCommentDirty(true);
              setManagerComment(event.currentTarget.value);
            }}
            placeholder={t.workspace.copy.reviewerFeedbackPlaceholder}
            minRows={3}
          />
          <Group justify="flex-end">
            <UiButton
              variant="light"
              disabled={!managerComment.trim()}
              loading={saveMutation.isPending}
              onClick={() => saveMutation.mutate(
                { participantId: task.participantId, comment: managerComment.trim() },
                {
                  onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.feedbackSaved }),
                  onError: reportError,
                },
              )}
            >
              {t.workspace.copy.feedbackSave}
            </UiButton>
            <UiButton
              disabled={!managerComment.trim()}
              loading={saveMutation.isPending || completeMutation.isPending}
              onClick={() => void completeIntermediate()}
            >
              {t.workspace.copy.completeCheckIn}
            </UiButton>
          </Group>
        </>
      )}
      {task.reviewId && actions.has('EDIT_MANAGER_REVIEW') && (
        <ManagerScoreForm
          reviewId={task.reviewId}
          canSubmit={actions.has('SUBMIT_MANAGER_REVIEW')}
        />
      )}
    </Stack>
  );
}

function ManagerScoreForm({
  reviewId,
  canSubmit,
}: {
  reviewId: string;
  canSubmit: boolean;
}): React.ReactNode {
  const t = useT();
  const itemsQuery = useWorkspaceReviewItemsQuery(reviewId);
  const saveMutation = useSaveManagerReviewMutation(reviewId);
  const submitMutation = useSubmitManagerReviewMutation(reviewId);
  const [comment, setComment] = useState('');
  const [scores, setScores] = useState<Record<string, number | ''>>({});
  const items = itemsQuery.data ?? [];
  const itemScores = items.map((item) => {
    const score = scores[item.assignmentId] ?? item.managerScore ?? '';
    return { assignmentId: item.assignmentId, managerScore: score === '' ? null : Number(score) };
  });
  const isComplete = itemScores.length > 0 && itemScores.every((item) => item.managerScore != null);
  const reportError = (error: unknown): void =>
    showToast({ tone: 'danger', message: workspaceErrorMessage(t, error) });

  if (itemsQuery.isLoading) return <LoadingState message={t.workspace.copy.loadingItems} />;
  if (itemsQuery.isError) return <UiAlert color="red">{workspaceErrorMessage(t, itemsQuery.error)}</UiAlert>;
  if (items.length === 0) return <Text size="sm" c="dimmed">{t.workspace.copy.noReviewItems}</Text>;

  return (
    <Stack>
      <Text fw={600}>{t.workspace.copy.teamScore}</Text>
      {items.map((item) => (
        <Group key={item.assignmentId} justify="space-between" align="end">
          <Stack gap={2}>
            <Text size="sm" fw={500}>{item.nodeLabel}</Text>
            <Text size="xs" c="dimmed">
              {t.workspace.copy.target} {item.target ?? '—'} {item.unit ?? ''} ·
              {' '}{t.workspace.copy.actual} {item.latestActualValue ?? '—'} ·
              {' '}{t.workspace.copy.autoScore} {item.autoScore ?? '—'}
            </Text>
          </Stack>
          <NumberInput
            label={t.workspace.copy.managerScore}
            aria-label={`${item.nodeLabel} ${t.workspace.copy.managerScore}`}
            min={0}
            max={100}
            value={scores[item.assignmentId] ?? item.managerScore ?? ''}
            onChange={(value) => setScores((previous) => ({
              ...previous,
              [item.assignmentId]: typeof value === 'number' ? value : '',
            }))}
            w={140}
          />
        </Group>
      ))}
      <Textarea
        label={t.workspace.copy.reviewComment}
        value={comment}
        onChange={(event) => setComment(event.currentTarget.value)}
        minRows={3}
      />
      <Group justify="flex-end">
        <UiButton
          variant="light"
          loading={saveMutation.isPending}
          onClick={() => saveMutation.mutate(
            { comment, itemScores },
            {
              onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.managerReviewSaved }),
              onError: reportError,
            },
          )}
        >
          {t.workspace.saveDraft}
        </UiButton>
        {canSubmit && (
          <UiButton
            loading={submitMutation.isPending}
            disabled={!isComplete}
            onClick={() => submitMutation.mutate(
              { comment, itemScores },
              {
                onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.managerReviewSubmitted }),
                onError: reportError,
              },
            )}
          >
            {t.workspace.copy.submitTeamReview}
          </UiButton>
        )}
      </Group>
      {canSubmit && !isComplete && <Text size="xs" c="dimmed">{t.workspace.copy.scoreRequiredHint}</Text>}
    </Stack>
  );
}

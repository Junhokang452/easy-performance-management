import { useState } from 'react';
import { Group, NumberInput, SimpleGrid, Stack, Text, TextInput, Textarea } from '@easy/ui-components/mantine';
import { IconCheck } from '@tabler/icons-react';
import {
  PerformanceCommentPanel,
  PerformanceMetricGrid,
  SectionCard,
  UiAlert,
  UiBadge,
  UiButton,
  UiTable,
} from '@easy/ui-components';

import {
  type AllowedAction,
  type Goal,
  type IntermediateReview,
  type WorkspaceResponse,
  useAcceptFeedbackMutation,
  useAcknowledgeReportMutation,
  useAppealMutation,
  useCheckInsQuery,
  useCreateCheckInMutation,
  useCreateGoalMutation,
  useSaveIntermediateMutation,
  useSaveSelfReviewMutation,
  useSubmitGoalMutation,
  useSubmitIntermediateMutation,
  useSubmitSelfReviewMutation,
  useUpdateGoalMutation,
} from '../../../api/evaluationWorkspace';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import { employeeLabel, statusLabel } from '../workspaceLabels';
import { workspaceErrorMessage } from '../workspaceError';
import { isValidGoalDraft } from '../workflowRules';

export function MyEvaluationPanel({
  cycleId,
  workspace,
}: {
  cycleId: string;
  workspace: WorkspaceResponse;
}): React.ReactNode {
  const t = useT();
  const actions = new Set<AllowedAction>(workspace.allowedActions);
  return (
    <Stack>
      <SectionCard>
        <Text fw={600}>{t.workspace.myEvaluation}</Text>
        <Text size="sm" c="dimmed" mt={4}>
          {workspace.participant
            ? `${employeeLabel(workspace.participant.employee)} · ${workspace.participant.employee.orgUnitName ?? t.workspace.copy.notSpecified}`
            : t.workspace.copy.noTask}
        </Text>
      </SectionCard>
      {workspace.goals.map((goal) => (
        <GoalCard
          key={goal.id}
          goal={goal}
          canEdit={actions.has('EDIT_GOAL')}
          canSubmit={actions.has('SUBMIT_GOAL')}
          canCheckIn={actions.has('ADD_CHECK_IN')}
        />
      ))}
      {actions.has('CREATE_GOAL') && <GoalForm cycleId={cycleId} />}
      {(actions.has('EDIT_INTERMEDIATE_REVIEW') || actions.has('SUBMIT_INTERMEDIATE_REVIEW')) && (
        <IntermediateReviewForm
          cycleId={cycleId}
          current={workspace.intermediateReview}
          submitEnabled={actions.has('SUBMIT_INTERMEDIATE_REVIEW')}
        />
      )}
      {workspace.review && (actions.has('EDIT_SELF_REVIEW') || actions.has('SUBMIT_SELF_REVIEW')) && (
        <SelfReviewForm
          reviewId={workspace.review.id}
          comment={workspace.review.selfComment ?? ''}
          submitEnabled={actions.has('SUBMIT_SELF_REVIEW')}
        />
      )}
      {workspace.report && actions.has('VIEW_REPORT') && (
        <ResultPanel
          reportId={workspace.report.id}
          acknowledged={workspace.report.acknowledged}
          finalScore={workspace.report.content.finalScore}
          finalGrade={workspace.report.content.finalGrade}
          managerComment={workspace.report.content.managerComment}
          feedback={workspace.feedback ?? null}
          canAcknowledge={actions.has('ACKNOWLEDGE_REPORT')}
          canAccept={actions.has('ACCEPT_FEEDBACK')}
          canAppeal={actions.has('APPEAL_FEEDBACK')}
        />
      )}
    </Stack>
  );
}

function GoalCard({
  goal,
  canEdit,
  canSubmit,
  canCheckIn,
}: {
  goal: Goal;
  canEdit: boolean;
  canSubmit: boolean;
  canCheckIn: boolean;
}): React.ReactNode {
  const t = useT();
  const updateMutation = useUpdateGoalMutation();
  const submitMutation = useSubmitGoalMutation();
  const editable = canEdit && (goal.status === 'DRAFT' || goal.status === 'REJECTED');
  const [title, setTitle] = useState(goal.title);
  const [description, setDescription] = useState(goal.description ?? '');
  const [weight, setWeight] = useState(goal.weight * 100);
  const [target, setTarget] = useState<number | string>(goal.target ?? '');
  const [unit, setUnit] = useState(goal.unit ?? '');
  const valid = isValidGoalDraft(title, weight);
  const reportError = (error: unknown): void =>
    showToast({ tone: 'danger', message: workspaceErrorMessage(t, error) });

  return (
    <SectionCard>
      <Stack>
        <Group justify="space-between">
          <Stack gap={0}>
            <Text fw={600}>{goal.title}</Text>
            <Text size="xs" c="dimmed">{editable ? t.workspace.copy.goalEdit : t.workspace.goals}</Text>
          </Stack>
          <UiBadge variant="light">{statusLabel(t, goal.status)}</UiBadge>
        </Group>
        {editable ? (
          <>
            <TextInput label={t.workspace.goal} value={title} onChange={(event) => setTitle(event.currentTarget.value)} />
            <Textarea
              label={t.workspace.goalDescription}
              value={description}
              onChange={(event) => setDescription(event.currentTarget.value)}
            />
            <SimpleGrid cols={{ base: 1, sm: 3 }}>
              <NumberInput label={t.workspace.weight} min={1} max={100} value={weight} onChange={(value) => setWeight(Number(value) || 0)} />
              <NumberInput label={t.workspace.copy.targetValue} value={target} onChange={setTarget} />
              <TextInput label={t.workspace.copy.unit} value={unit} onChange={(event) => setUnit(event.currentTarget.value)} />
            </SimpleGrid>
            {goal.decisionComment && (
              <UiAlert color="yellow" title={t.workspace.copy.revisionReason}>
                {goal.decisionComment}
              </UiAlert>
            )}
            <Group justify="flex-end">
              <UiButton
                variant="light"
                disabled={!valid}
                loading={updateMutation.isPending}
                onClick={() => updateMutation.mutate(
                  {
                    goalId: goal.id,
                    input: {
                      title: title.trim(),
                      description: description.trim() || null,
                      weight: weight / 100,
                      target: target === '' ? null : Number(target),
                      unit: unit.trim() || null,
                    },
                  },
                  {
                    onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.goalUpdated }),
                    onError: reportError,
                  },
                )}
              >
                {t.workspace.copy.saveChanges}
              </UiButton>
              {canSubmit && (
                <UiButton
                  disabled={!valid || updateMutation.isPending}
                  loading={submitMutation.isPending}
                  onClick={async () => {
                    try {
                      await updateMutation.mutateAsync({
                        goalId: goal.id,
                        input: {
                          title: title.trim(),
                          description: description.trim() || null,
                          weight: weight / 100,
                          target: target === '' ? null : Number(target),
                          unit: unit.trim() || null,
                        },
                      });
                      await submitMutation.mutateAsync(goal.id);
                      showToast({ tone: 'success', message: t.workspace.copy.goalSubmitted });
                    } catch (error) {
                      reportError(error);
                    }
                  }}
                >
                  {t.workspace.copy.requestApproval}
                </UiButton>
              )}
            </Group>
          </>
        ) : (
          <>
            <Text size="sm">{goal.description || t.workspace.copy.noDescription}</Text>
            <Text size="xs" c="dimmed">
              {t.workspace.weight} {Math.round(goal.weight * 100)}% ·
              {' '}{t.workspace.copy.target} {goal.target ?? '—'} {goal.unit ?? ''}
            </Text>
          </>
        )}
        {goal.status === 'APPROVED' && <CheckInHistory goalId={goal.id} />}
        {canCheckIn && goal.status === 'APPROVED' && <CheckInForm goalId={goal.id} />}
      </Stack>
    </SectionCard>
  );
}

function CheckInHistory({ goalId }: { goalId: string }): React.ReactNode {
  const t = useT();
  const checkInsQuery = useCheckInsQuery(goalId, true);
  if (checkInsQuery.isError) return <UiAlert color="red">{workspaceErrorMessage(t, checkInsQuery.error)}</UiAlert>;
  const rows = checkInsQuery.data ?? [];
  if (rows.length === 0) return <Text size="xs" c="dimmed">{t.workspace.copy.noCheckIns}</Text>;
  return (
    <UiTable.ScrollContainer minWidth={620}>
      <UiTable>
        <UiTable.Thead>
          <UiTable.Tr>
            <UiTable.Th>{t.workspace.copy.asOfDate}</UiTable.Th>
            <UiTable.Th>{t.workspace.copy.actualValue}</UiTable.Th>
            <UiTable.Th>{t.workspace.copy.progressPercent}</UiTable.Th>
            <UiTable.Th>{t.workspace.copy.note}</UiTable.Th>
          </UiTable.Tr>
        </UiTable.Thead>
        <UiTable.Tbody>
          {rows.map((row) => (
            <UiTable.Tr key={row.id}>
              <UiTable.Td>{row.asOfDate}</UiTable.Td>
              <UiTable.Td>{row.actualValue}</UiTable.Td>
              <UiTable.Td>{row.progressPercent == null ? '—' : `${row.progressPercent}%`}</UiTable.Td>
              <UiTable.Td>{row.note || '—'}</UiTable.Td>
            </UiTable.Tr>
          ))}
        </UiTable.Tbody>
      </UiTable>
    </UiTable.ScrollContainer>
  );
}

function CheckInForm({ goalId }: { goalId: string }): React.ReactNode {
  const t = useT();
  const mutation = useCreateCheckInMutation();
  const [asOfDate, setAsOfDate] = useState(new Date().toISOString().slice(0, 10));
  const [actualValue, setActualValue] = useState<number | string>('');
  const [progressPercent, setProgressPercent] = useState<number | string>('');
  const [note, setNote] = useState('');
  const [evidenceUrl, setEvidenceUrl] = useState('');
  const valid = asOfDate.length > 0 && actualValue !== '' && Number.isFinite(Number(actualValue));
  return (
    <Stack>
      <Text fw={600} size="sm">{t.workspace.copy.addCheckIn}</Text>
      <SimpleGrid cols={{ base: 1, sm: 3 }}>
        <TextInput type="date" label={t.workspace.copy.asOfDate} value={asOfDate} onChange={(event) => setAsOfDate(event.currentTarget.value)} />
        <NumberInput label={t.workspace.copy.actualValue} value={actualValue} onChange={setActualValue} />
        <NumberInput label={t.workspace.copy.progressPercent} min={0} max={100} value={progressPercent} onChange={setProgressPercent} />
      </SimpleGrid>
      <Textarea label={t.workspace.copy.note} value={note} onChange={(event) => setNote(event.currentTarget.value)} />
      <TextInput label={t.workspace.copy.evidenceUrl} value={evidenceUrl} onChange={(event) => setEvidenceUrl(event.currentTarget.value)} />
      <Group justify="flex-end">
        <UiButton
          disabled={!valid}
          loading={mutation.isPending}
          onClick={() => mutation.mutate(
            {
              goalId,
              input: {
                asOfDate,
                actualValue: Number(actualValue),
                progressPercent: progressPercent === '' ? undefined : Number(progressPercent),
                note: note.trim() || undefined,
                evidenceUrl: evidenceUrl.trim() || undefined,
              },
            },
            {
              onSuccess: () => {
                setActualValue('');
                setProgressPercent('');
                setNote('');
                setEvidenceUrl('');
                showToast({ tone: 'success', message: t.workspace.copy.checkInAdded });
              },
              onError: (error) => showToast({ tone: 'danger', message: workspaceErrorMessage(t, error) }),
            },
          )}
        >
          {t.workspace.copy.saveCheckIn}
        </UiButton>
      </Group>
    </Stack>
  );
}

function GoalForm({ cycleId }: { cycleId: string }): React.ReactNode {
  const t = useT();
  const mutation = useCreateGoalMutation(cycleId);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [weight, setWeight] = useState<number | string>('');
  const [target, setTarget] = useState<number | string>('');
  const [unit, setUnit] = useState('');
  const weightNumber = Number(weight);
  const valid = weight !== '' && isValidGoalDraft(title, weightNumber);
  return (
    <SectionCard>
      <Stack>
        <Text fw={600}>{t.workspace.copy.goalWrite}</Text>
        <TextInput label={t.workspace.goal} value={title} onChange={(event) => setTitle(event.currentTarget.value)} />
        <Textarea label={t.workspace.goalDescription} value={description} onChange={(event) => setDescription(event.currentTarget.value)} />
        <SimpleGrid cols={{ base: 1, sm: 3 }}>
          <NumberInput label={t.workspace.weight} min={1} max={100} value={weight} onChange={setWeight} />
          <NumberInput label={t.workspace.copy.targetValue} value={target} onChange={setTarget} />
          <TextInput label={t.workspace.copy.unit} value={unit} onChange={(event) => setUnit(event.currentTarget.value)} />
        </SimpleGrid>
        <Group justify="flex-end">
          <UiButton
            disabled={!valid}
            loading={mutation.isPending}
            onClick={() => mutation.mutate(
              {
                title: title.trim(),
                description: description.trim() || null,
                weight: weightNumber / 100,
                target: target === '' ? null : Number(target),
                unit: unit.trim() || null,
              },
              {
                onSuccess: () => {
                  setTitle('');
                  setDescription('');
                  setWeight('');
                  setTarget('');
                  setUnit('');
                  showToast({ tone: 'success', message: t.workspace.copy.goalCreated });
                },
                onError: (error) => showToast({ tone: 'danger', message: workspaceErrorMessage(t, error) }),
              },
            )}
          >
            {t.workspace.copy.saveGoal}
          </UiButton>
        </Group>
      </Stack>
    </SectionCard>
  );
}

function IntermediateReviewForm({
  cycleId,
  current,
  submitEnabled,
}: {
  cycleId: string;
  current: IntermediateReview | null;
  submitEnabled: boolean;
}): React.ReactNode {
  const t = useT();
  const saveMutation = useSaveIntermediateMutation(cycleId);
  const submitMutation = useSubmitIntermediateMutation(cycleId);
  const [progressSummary, setProgressSummary] = useState(current?.progressSummary ?? '');
  const [achievements, setAchievements] = useState(current?.achievements ?? '');
  const [blockers, setBlockers] = useState(current?.blockers ?? '');
  const [supportNeeded, setSupportNeeded] = useState(current?.supportNeeded ?? '');
  const input = {
    progressSummary: progressSummary.trim(),
    achievements: achievements.trim() || undefined,
    blockers: blockers.trim() || undefined,
    supportNeeded: supportNeeded.trim() || undefined,
  };
  const reportError = (error: unknown): void =>
    showToast({ tone: 'danger', message: workspaceErrorMessage(t, error) });
  return (
    <SectionCard>
      <Stack>
        <Text fw={600}>{t.workspace.checkIn}</Text>
        <Text size="sm" c="dimmed">{t.workspace.copy.checkInDescriptionShort}</Text>
        <Textarea label={t.workspace.progress} value={progressSummary} onChange={(event) => setProgressSummary(event.currentTarget.value)} minRows={3} />
        <Textarea label={t.workspace.achievements} value={achievements} onChange={(event) => setAchievements(event.currentTarget.value)} />
        <Textarea label={t.workspace.blockers} value={blockers} onChange={(event) => setBlockers(event.currentTarget.value)} />
        <Textarea label={t.workspace.supportNeeded} value={supportNeeded} onChange={(event) => setSupportNeeded(event.currentTarget.value)} />
        {current?.managerComment && (
          <PerformanceCommentPanel
            title={t.workspace.copy.reviewerFeedback}
            comment={current.managerComment}
            titleColor="blue"
          />
        )}
        <Group justify="flex-end">
          <UiButton
            variant="light"
            loading={saveMutation.isPending}
            disabled={!input.progressSummary}
            onClick={() => saveMutation.mutate(input, {
              onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.checkInSaved }),
              onError: reportError,
            })}
          >
            {t.workspace.saveDraft}
          </UiButton>
          {submitEnabled && (
            <UiButton
              loading={submitMutation.isPending}
              disabled={!input.progressSummary}
              onClick={() => submitMutation.mutate(input, {
                onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.checkInSubmitted }),
                onError: reportError,
              })}
            >
              {t.workspace.copy.submitToManager}
            </UiButton>
          )}
        </Group>
      </Stack>
    </SectionCard>
  );
}

function SelfReviewForm({
  reviewId,
  comment,
  submitEnabled,
}: {
  reviewId: string;
  comment: string;
  submitEnabled: boolean;
}): React.ReactNode {
  const t = useT();
  const saveMutation = useSaveSelfReviewMutation(reviewId);
  const submitMutation = useSubmitSelfReviewMutation(reviewId);
  const [value, setValue] = useState(comment);
  const reportError = (error: unknown): void =>
    showToast({ tone: 'danger', message: workspaceErrorMessage(t, error) });
  return (
    <SectionCard>
      <Stack>
        <Text fw={600}>{t.workspace.selfReview}</Text>
        <Textarea
          label={t.workspace.copy.selfReviewDescription}
          value={value}
          onChange={(event) => setValue(event.currentTarget.value)}
          minRows={5}
        />
        <Group justify="flex-end">
          <UiButton
            variant="light"
            loading={saveMutation.isPending}
            onClick={() => saveMutation.mutate(value, {
              onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.selfReviewSaved }),
              onError: reportError,
            })}
          >
            {t.workspace.saveDraft}
          </UiButton>
          {submitEnabled && (
            <UiButton
              loading={submitMutation.isPending}
              disabled={!value.trim()}
              onClick={() => submitMutation.mutate(value, {
                onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.selfReviewSubmitted }),
                onError: reportError,
              })}
            >
              {t.workspace.submit}
            </UiButton>
          )}
        </Group>
      </Stack>
    </SectionCard>
  );
}

function ResultPanel({
  reportId,
  acknowledged,
  finalScore,
  finalGrade,
  managerComment,
  feedback,
  canAcknowledge,
  canAccept,
  canAppeal,
}: {
  reportId: string;
  acknowledged: boolean;
  finalScore: number | null;
  finalGrade: string | null;
  managerComment: string | null;
  feedback: WorkspaceResponse['feedback'];
  canAcknowledge: boolean;
  canAccept: boolean;
  canAppeal: boolean;
}): React.ReactNode {
  const t = useT();
  const acknowledgeMutation = useAcknowledgeReportMutation(reportId);
  const acceptMutation = useAcceptFeedbackMutation();
  const appealMutation = useAppealMutation();
  const [appealReason, setAppealReason] = useState('');
  const reportError = (error: unknown): void =>
    showToast({ tone: 'danger', message: workspaceErrorMessage(t, error) });
  return (
    <SectionCard>
      <Stack>
        <Group justify="space-between" align="flex-start">
          <Stack gap={2}>
            <Text fw={600}>{t.workspace.copy.resultsFeedback}</Text>
            <Text size="sm" c="dimmed">
              {acknowledged ? t.workspace.copy.acknowledged : t.workspace.copy.acknowledgeHint}
            </Text>
          </Stack>
          {canAcknowledge && !acknowledged && (
            <UiButton
              leftSection={<IconCheck size={16} aria-hidden />}
              loading={acknowledgeMutation.isPending}
              onClick={() => acknowledgeMutation.mutate(undefined, {
                onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.resultAcknowledged }),
                onError: reportError,
              })}
            >
              {t.workspace.acknowledge}
            </UiButton>
          )}
        </Group>
        <PerformanceMetricGrid
          columns={{ base: 1, sm: 2 }}
          items={[
            { key: 'score', label: t.workspace.copy.finalScore, value: finalScore ?? t.workspace.copy.notCalculated },
            { key: 'grade', label: t.workspace.copy.finalGrade, value: finalGrade ?? t.workspace.copy.notCalculated },
          ]}
        />
        <PerformanceCommentPanel
          title={t.workspace.copy.reviewComment}
          comment={managerComment}
          empty={t.workspace.copy.noFeedbackAction}
        />
        {feedback?.comment && (
          <PerformanceCommentPanel
            title={t.workspace.copy.feedbackFromManager}
            comment={feedback.comment}
            titleColor="blue"
          />
        )}
        {canAccept && (
          <Group justify="flex-end">
            <UiButton
              loading={acceptMutation.isPending}
              onClick={() => acceptMutation.mutate(reportId, {
                onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.feedbackAccepted }),
                onError: reportError,
              })}
            >
              {t.workspace.copy.acceptFeedback}
            </UiButton>
          </Group>
        )}
        {canAppeal && (
          <Group align="end">
            <Textarea
              label={t.workspace.copy.appeal}
              placeholder={t.workspace.copy.appealPlaceholder}
              value={appealReason}
              onChange={(event) => setAppealReason(event.currentTarget.value)}
              flex={1}
            />
            <UiButton
              variant="light"
              disabled={!appealReason.trim()}
              loading={appealMutation.isPending}
              onClick={() => appealMutation.mutate(
                { reportId, reason: appealReason.trim() },
                {
                  onSuccess: () => {
                    setAppealReason('');
                    showToast({ tone: 'success', message: t.workspace.copy.appealSubmitted });
                  },
                  onError: reportError,
                },
              )}
            >
              {t.workspace.copy.appeal}
            </UiButton>
          </Group>
        )}
        {feedback?.status && (
          <Text size="xs" c="dimmed">{t.workspace.copy.feedbackStatus}: {statusLabel(t, feedback.status)}</Text>
        )}
      </Stack>
    </SectionCard>
  );
}

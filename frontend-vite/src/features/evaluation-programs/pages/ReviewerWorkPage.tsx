import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  FormActions, FormSelect, LookupMultiSelect,
  FormNumberInput,
  FormTextarea,
  PageHeader,
  PerformanceCommentPanel,
  SectionCard,
  UiBadge,
  UiButton,
  UiGroup,
  UiStack,
  UiText,
  WorkspaceTabs,
} from '@easy/ui-components';

import { getErrorMessage } from '../../../api/error';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import {
  useCompleteAdjustmentMutation, useCompleteIntermediateMutation,
  useCompleteReviewMutation,
  useDecideProgramGoalMutation,
  useDeliverFeedbackMutation,
  useParticipantFeedbackQuery, useParticipantGoalsQuery, useParticipantIntermediateQuery,
  useProgramParticipantsQuery,
  useReviewContextQuery,
  useResolveProgramFeedbackMutation,
  useSaveAdjustmentMutation, useSaveIntermediateMutation,
  useSaveFeedbackMutation,
  useSaveReviewMutation,
  type GoalResponse, type ParticipantResponse,
  type ReviewItemAnswerInput,
} from '../api/programs';
import { programStageLabel } from '../components/programLabels';
import { QueryState } from '../components/QueryState';
import { useTasksQuery } from '../api/resources';
import { ParticipantKpiEvidence } from '../components/GoalKpiLinkageTools';

type Tab = 'goals' | 'evidence' | 'review' | 'adjustment' | 'feedback';

export function ReviewerWorkPage(): React.ReactNode {
  const { programId = '', participantId = '', round = '1' } = useParams();
  const t = useT();
  const reviewRound = Math.max(1, Number(round) || 1);
  const participants = useProgramParticipantsQuery(programId || null);
  const participant = participants.data?.find((item) => item.id === participantId) ?? null;
  const goals = useParticipantGoalsQuery(participantId || null);
  const intermediate = useParticipantIntermediateQuery(participantId || null);
  const feedback = useParticipantFeedbackQuery(participantId || null);
  const [tab, setTab] = useState<Tab>('goals');

  useEffect(() => {
    if (!participant) return;
    if (participant.currentStage === 'INTERMEDIATE') setTab('evidence');
    else if (participant.currentStage === 'REVIEW' || participant.currentStage === 'SELF_REVIEW') setTab('review');
    else if (participant.currentStage === 'CALIBRATION' || participant.currentStage === 'CALCULATION') setTab('adjustment');
    else if (participant.currentStage === 'FEEDBACK') setTab('feedback');
  }, [participant]);

  return (
    <QueryState
      pending={participants.isPending}
      error={participants.error}
      empty={!participant}
      loadingLabel={t.common.status.loading}
      errorLabel={t.program.common.loadError}
      emptyTitle={t.program.common.noData}
    >
      {participant ? (
        <UiStack gap="md">
          <PageHeader
            title={participant.employee.name}
            description={participant.employee.orgUnitName ?? t.workspace.copy.notSpecified}
            actions={<UiBadge>{programStageLabel(t, participant.currentStage, participant.currentRound)}</UiBadge>}
          />
          <WorkspaceTabs value={tab} onChange={(value) => value && setTab(value as Tab)} keepMounted={false}>
            <WorkspaceTabs.List>
              <WorkspaceTabs.Tab value="goals">{t.program.goals.agreementTitle}</WorkspaceTabs.Tab>
              <WorkspaceTabs.Tab value="evidence">{t.program.reviews.evidence}</WorkspaceTabs.Tab>
              <WorkspaceTabs.Tab value="review">{t.program.reviews.detail}</WorkspaceTabs.Tab>
              <WorkspaceTabs.Tab value="adjustment">{t.program.stages.calibration}</WorkspaceTabs.Tab>
              <WorkspaceTabs.Tab value="feedback">{t.program.reviews.finalFeedback}</WorkspaceTabs.Tab>
            </WorkspaceTabs.List>
            <WorkspaceTabs.Panel value="goals"><QueryState pending={goals.isPending} error={goals.error} empty={!goals.data?.length} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}><GoalAgreement goals={goals.data ?? []} /></QueryState></WorkspaceTabs.Panel>
            <WorkspaceTabs.Panel value="evidence"><EvidencePanel participant={participant} current={intermediate.data ?? null} /></WorkspaceTabs.Panel>
            <WorkspaceTabs.Panel value="review"><ReviewEditor participant={participant} round={reviewRound} /></WorkspaceTabs.Panel>
            <WorkspaceTabs.Panel value="adjustment"><AdjustmentEditor participant={participant} /></WorkspaceTabs.Panel>
            <WorkspaceTabs.Panel value="feedback"><FeedbackEditor participantId={participantId} feedback={feedback.data ?? null} /></WorkspaceTabs.Panel>
          </WorkspaceTabs>
          <ParticipantKpiEvidence key={participantId} programId={programId} participantId={participantId} goals={goals.data ?? []} />
        </UiStack>
      ) : null}
    </QueryState>
  );
}

function GoalAgreement({ goals }: { goals: GoalResponse[] }): React.ReactNode {
  const t = useT();
  if (!goals.length) return <SectionCard><UiText c="dimmed">{t.program.common.noData}</UiText></SectionCard>;
  return <UiStack>{goals.map((goal) => <GoalDecisionCard key={goal.id} goal={goal} />)}</UiStack>;
}

function GoalDecisionCard({ goal }: { goal: GoalResponse }): React.ReactNode {
  const t = useT();
  const decide = useDecideProgramGoalMutation();
  const [opinion, setOpinion] = useState(goal.decisionOpinion ?? '');
  const pending = goal.status === 'AGREEMENT_REQUESTED';
  const submit = async (approve: boolean): Promise<void> => {
    try {
      await decide.mutateAsync({ goalId: goal.id, approve, opinion });
      showToast({ tone: 'success', message: t.program.common.submitSuccess });
    } catch (error) {
      showToast({ tone: 'danger', message: getErrorMessage(error) });
    }
  };
  return (
    <SectionCard title={goal.title} description={goal.definition} actions={<UiBadge variant="light">{goal.weightPercent}%</UiBadge>}>
      <UiStack gap="sm">
        <UiText size="sm">{goal.targetValue ?? '–'} {goal.unit}</UiText>
        {pending ? <><FormTextarea label={t.program.common.comment} value={opinion} onChange={(event) => setOpinion(event.currentTarget.value)} /><UiGroup><UiButton disabled={!opinion.trim()} loading={decide.isPending} onClick={() => void submit(true)}>{t.program.goals.agree}</UiButton><UiButton variant="light" color="orange" disabled={!opinion.trim()} loading={decide.isPending} onClick={() => void submit(false)}>{t.program.goals.requestRevision}</UiButton></UiGroup></> : <PerformanceCommentPanel title={t.program.common.comment} comment={goal.decisionOpinion} empty={t.program.common.noData} />}
      </UiStack>
    </SectionCard>
  );
}

function EvidencePanel({ participant, current }: { participant: ParticipantResponse; current: ReturnType<typeof useParticipantIntermediateQuery>['data'] }): React.ReactNode {
  const t = useT(); const tasks = useTasksQuery({}); const save = useSaveIntermediateMutation(); const complete = useCompleteIntermediateMutation(); const [opinion, setOpinion] = useState(current?.opinion ?? ''); const [taskIds, setTaskIds] = useState<string[]>((current?.taskEvidence ?? []).map((item) => item.taskId)); const [taskSearch, setTaskSearch] = useState(''); const editable = participant.currentStage === 'INTERMEDIATE' && participant.stageStatus === 'IN_PROGRESS';
  useEffect(() => { setOpinion(current?.opinion ?? ''); setTaskIds((current?.taskEvidence ?? []).map((item) => item.taskId)); }, [current]);
  const persist = async (finish: boolean): Promise<void> => { try { const input = { participantId: participant.id, opinion, taskIds }; if (finish) await complete.mutateAsync(input); else await save.mutateAsync(input); showToast({ tone: 'success', message: finish ? t.program.common.submitSuccess : t.program.common.saveSuccess }); } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); } };
  return (
    <UiStack gap="md">
      {editable ? <SectionCard title={t.program.stages.midReview}><UiStack><LookupMultiSelect label={t.program.reviews.evidence} value={taskIds} onChange={setTaskIds} data={(tasks.data ?? []).filter((task) => task.title.toLocaleLowerCase().includes(taskSearch.toLocaleLowerCase())).map((task) => ({ value: task.id, label: `${task.title} · ${task.progressPercent}%` }))} searchValue={taskSearch} onSearchChange={setTaskSearch} /><FormTextarea minRows={5} label={t.program.common.comment} value={opinion} onChange={(event) => setOpinion(event.currentTarget.value)} /><FormActions secondary={<UiButton variant="light" loading={save.isPending} disabled={!opinion.trim()} onClick={() => void persist(false)}>{t.program.reviews.saveDraft}</UiButton>} primary={<UiButton loading={complete.isPending} disabled={!opinion.trim()} onClick={() => void persist(true)}>{t.program.stages.complete}</UiButton>} /></UiStack></SectionCard> : <PerformanceCommentPanel title={t.program.stages.midReview} comment={current?.opinion} empty={t.program.common.noData} />}
      {(current?.taskEvidence ?? []).map((item) => (
        <SectionCard key={item.taskId} title={item.title} description={item.summary}>
          <UiGroup><UiBadge variant="light">{item.progressPercent}%</UiBadge><UiText size="sm" c="dimmed">{new Date(item.occurredAt).toLocaleDateString()}</UiText></UiGroup>
        </SectionCard>
      ))}
    </UiStack>
  );
}

function ReviewEditor({ participant, round }: { participant: ParticipantResponse; round: number }): React.ReactNode {
  const t = useT();
  const contextQuery = useReviewContextQuery(participant.id, round, participant.currentStage === 'REVIEW');
  const context = contextQuery.data;
  const initial = useMemo<ReviewItemAnswerInput[]>(() => {
    if (!context) return [];
    if (context.currentSubmission) return context.currentSubmission.answers.map(({ itemId, scaleCode, numericScore, opinion }) => ({ itemId, scaleCode, numericScore: scaleCode ? null : numericScore, opinion }));
    return context.items.map((item) => ({ itemId: item.id, scaleCode: null, numericScore: null, opinion: '' }));
  }, [context]);
  const [answers, setAnswers] = useState(initial);
  const [overallOpinion, setOverallOpinion] = useState(context?.currentSubmission?.overallOpinion ?? '');
  const save = useSaveReviewMutation();
  const complete = useCompleteReviewMutation();
  useEffect(() => { setAnswers(initial); setOverallOpinion(context?.currentSubmission?.overallOpinion ?? ''); }, [context, initial]);
  if (!context) return <SectionCard><UiText c="dimmed">{participant.currentStage === 'REVIEW' ? t.common.status.loading : t.program.common.readOnly}</UiText></SectionCard>;
  const done = context.currentSubmission?.status === 'COMPLETED';
  const persist = async (finish: boolean): Promise<void> => {
    try {
      const input = { participantId: context.participant.id, round, answers, overallOpinion };
      if (finish) await complete.mutateAsync(input); else await save.mutateAsync(input);
      showToast({ tone: 'success', message: finish ? t.program.common.submitSuccess : t.program.common.saveSuccess });
    } catch (error) {
      showToast({ tone: 'danger', message: getErrorMessage(error) });
    }
  };

  return (
    <UiStack gap="md">
      {answers.map((answer, index) => {
        const item = context.items.find((candidate) => candidate.id === answer.itemId);
        const scale = context.inputScales.find((candidate) => candidate.id === item?.scaleId);
        return <SectionCard key={answer.itemId} title={item?.title ?? t.program.reviews.itemResponse} description={item ? `${item.weightPercent}%` : undefined}><UiGroup grow align="start">{scale?.kind === 'GRADE' ? <FormSelect disabled={done} label={scale.name} value={answer.scaleCode} data={scale.levels.map((level) => ({ value: level.code, label: level.label }))} onChange={(value) => setAnswers((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, scaleCode: value, numericScore: null } : row))} /> : <FormNumberInput disabled={done} label={t.program.common.score} min={0} max={100} value={answer.numericScore ?? ''} onChange={(value) => setAnswers((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, numericScore: value === '' ? null : Number(value), scaleCode: null } : row))} />}<FormTextarea disabled={done} label={t.program.common.comment} value={answer.opinion} onChange={(event) => setAnswers((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, opinion: event.currentTarget.value } : row))} /></UiGroup></SectionCard>;
      })}
      <FormTextarea disabled={done} minRows={4} label={t.program.reviews.overallComment} value={overallOpinion} onChange={(event) => setOverallOpinion(event.currentTarget.value)} />
      {!done ? <FormActions secondary={<UiButton variant="light" loading={save.isPending} onClick={() => void persist(false)}>{t.program.reviews.saveDraft}</UiButton>} primary={<UiButton loading={complete.isPending} disabled={!answers.length || answers.some((item) => item.numericScore == null && !item.scaleCode)} onClick={() => void persist(true)}>{t.program.reviews.complete}</UiButton>} /> : <UiText c="dimmed">{t.program.common.readOnly}</UiText>}
    </UiStack>
  );
}

function AdjustmentEditor({ participant }: { participant: ParticipantResponse }): React.ReactNode {
  const t = useT();
  const save = useSaveAdjustmentMutation();
  const complete = useCompleteAdjustmentMutation();
  const [score, setScore] = useState<number | string>('');
  const [grade, setGrade] = useState('');
  const [reason, setReason] = useState('');
  const submit = async (finish: boolean): Promise<void> => {
    try {
      const input = { participantId: participant.id, adjustedScore: score === '' ? null : Number(score), adjustedGrade: grade, reason };
      if (finish) await complete.mutateAsync(input); else await save.mutateAsync(input);
      showToast({ tone: 'success', message: finish ? t.program.common.submitSuccess : t.program.common.saveSuccess });
    } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); }
  };
  return <SectionCard title={t.program.stages.calibration} description={participant.employee.name}><UiStack><UiGroup grow><FormNumberInput label={t.program.common.score} value={score} onChange={setScore} /><FormTextarea label={t.program.common.grade} value={grade} onChange={(event) => setGrade(event.currentTarget.value)} /></UiGroup><FormTextarea label={t.program.operations.reason} value={reason} onChange={(event) => setReason(event.currentTarget.value)} /><FormActions secondary={<UiButton variant="light" loading={save.isPending} disabled={!grade.trim() || !reason.trim()} onClick={() => void submit(false)}>{t.program.reviews.saveDraft}</UiButton>} primary={<UiButton loading={complete.isPending} disabled={!grade.trim() || !reason.trim()} onClick={() => void submit(true)}>{t.program.stages.complete}</UiButton>} /></UiStack></SectionCard>;
}

function FeedbackEditor({ participantId, feedback }: { participantId: string; feedback: Awaited<ReturnType<ReturnType<typeof useParticipantFeedbackQuery>['refetch']>>['data'] | null }): React.ReactNode {
  const t = useT();
  const save = useSaveFeedbackMutation();
  const deliver = useDeliverFeedbackMutation();
  const resolve = useResolveProgramFeedbackMutation();
  const [comment, setComment] = useState(feedback?.comment ?? '');
  const [resolutionComment, setResolutionComment] = useState(feedback?.resolutionComment ?? '');
  const submit = async (finish: boolean): Promise<void> => {
    try {
      if (finish) await deliver.mutateAsync({ participantId, comment }); else await save.mutateAsync({ participantId, comment });
      showToast({ tone: 'success', message: finish ? t.program.common.submitSuccess : t.program.common.saveSuccess });
    } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); }
  };
  const resolveAppeal = async (): Promise<void> => {
    if (!feedback) return;
    try { await resolve.mutateAsync({ feedbackId: feedback.id, input: { resolution: 'UPHELD', comment: resolutionComment } }); showToast({ tone: 'success', message: t.program.common.submitSuccess }); }
    catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); }
  };
  return <UiStack gap="md"><SectionCard title={t.program.reviews.finalFeedback}><FormTextarea minRows={6} disabled={feedback != null && feedback.status !== 'DRAFT'} label={t.program.common.comment} value={comment} onChange={(event) => setComment(event.currentTarget.value)} />{!feedback || feedback.status === 'DRAFT' ? <FormActions secondary={<UiButton variant="light" loading={save.isPending} disabled={!comment.trim()} onClick={() => void submit(false)}>{t.program.reviews.saveDraft}</UiButton>} primary={<UiButton loading={deliver.isPending} disabled={!comment.trim()} onClick={() => void submit(true)}>{t.program.operations.publish}</UiButton>} /> : null}</SectionCard>{feedback?.status === 'APPEALED' ? <SectionCard title={t.program.reviews.appeal} description={feedback.appealReason}><UiStack><FormTextarea label={t.program.common.comment} value={resolutionComment} onChange={(event) => setResolutionComment(event.currentTarget.value)} /><UiButton loading={resolve.isPending} disabled={!resolutionComment.trim()} onClick={() => void resolveAppeal()}>{t.program.reviews.accept}</UiButton></UiStack></SectionCard> : null}</UiStack>;
}

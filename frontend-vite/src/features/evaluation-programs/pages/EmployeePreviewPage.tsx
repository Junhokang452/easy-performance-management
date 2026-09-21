import { useParams } from 'react-router-dom';
import { PageHeader, PerformanceCommentPanel, PerformanceMetricGrid, SectionCard, UiBadge, UiGroup, UiStack, UiText } from '@easy/ui-components';

import { useT } from '../../../i18n';
import { useEmployeePreviewQuery } from '../api/programs';
import { programStageLabel } from '../components/programLabels';
import { QueryState } from '../components/QueryState';

export function EmployeePreviewPage(): React.ReactNode {
  const { participantId = '' } = useParams(); const t = useT(); const query = useEmployeePreviewQuery(participantId || null); const data = query.data;
  const score = data?.calculation?.adjustedScore ?? data?.calculation?.normalizedScore ?? null;
  return <QueryState pending={query.isPending} error={query.error} empty={!data} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>{data ? <UiStack gap="md"><PageHeader title={`${data.program.name} · ${t.program.programs.preview}`} description={`${data.program.startsOn} – ${data.program.endsOn}`} actions={<UiBadge>{programStageLabel(t, data.participant.currentStage, data.participant.currentRound)}</UiBadge>} />{data.goals.map((goal) => <SectionCard key={goal.id} title={goal.title} description={goal.definition}><UiGroup><UiText size="sm">{t.program.goals.weight}: {goal.weightPercent}%</UiText><UiText size="sm">{goal.targetValue ?? '–'} {goal.unit}</UiText></UiGroup></SectionCard>)}<PerformanceCommentPanel title={t.program.stages.midReview} comment={data.intermediate?.opinion} empty={t.program.common.noData} />{data.submissions.map((submission) => <PerformanceCommentPanel key={submission.id} title={submission.round === 0 ? t.program.reviews.selfTitle : programStageLabel(t, 'REVIEW', submission.round)} comment={submission.overallOpinion} empty={t.program.common.noData} />)}{data.calculation ? <PerformanceMetricGrid items={[...(score == null ? [] : [{ label: t.program.common.score, value: score }]), { label: t.program.common.grade, value: data.calculation.calculatedGrade }]} /> : <SectionCard><UiText c="dimmed">{t.program.reports.notPublished}</UiText></SectionCard>}<PerformanceCommentPanel title={t.program.reviews.finalFeedback} comment={data.feedback?.comment} empty={t.program.common.noData} /></UiStack> : null}</QueryState>;
}

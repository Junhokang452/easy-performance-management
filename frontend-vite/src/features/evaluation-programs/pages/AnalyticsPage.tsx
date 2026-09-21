import { useEffect, useState } from 'react';
import {
  FormMultiSelect, FormSelect, MasterDetailWorkspace, PageHeader,
  PerformanceCommentPanel, PerformanceDistributionBars, PerformanceMetricGrid, SectionCard, UiBadge, UiButton,
  UiGroup, UiStack, UiTable, UiText, WorkspaceTabs,
} from '@easy/ui-components';

import { useT } from '../../../i18n';
import { getErrorMessage } from '../../../api/error';
import { showToast } from '../../../shared/toast';
import {
  programsApi,
  useEmployeeFeedbackQuery, useGradeMatrixQuery, useItemResultsQuery, usePivotMutation,
  useProgramResultsQuery, useProgramQuery, useProgramsQuery, useReviewerResultsQuery, useReviewerTendenciesQuery,
} from '../api/programs';
import { downloadBlob } from '../components/downloadBlob';
import { CustomResultPdfModal } from '../components/CustomResultPdfModal';
import { QueryState } from '../components/QueryState';

type View = 'overview' | 'items' | 'reviewers' | 'feedback' | 'matrix' | 'custom' | 'tendency';
const axes = ['department', 'position', 'grade', 'job', 'evaluationYear'];

export function AnalyticsPage(): React.ReactNode {
  const t = useT(); const programs = useProgramsQuery(); const [programId, setProgramId] = useState<string | null>(null); const [view, setView] = useState<View>('overview');
  const choices = (programs.data ?? []).filter((program) => program.status === 'FINALIZED').map((program) => ({ value: program.id, label: `${program.evaluationYear} · ${program.name}` }));
  return <UiStack gap="md"><PageHeader title={t.program.analytics.title} description={t.program.analytics.description} /><FormSelect searchable label={t.program.programs.title} value={programId} data={choices} onChange={setProgramId} /><WorkspaceTabs keepMounted={false} value={view} onChange={(value) => value && setView(value as View)}><WorkspaceTabs.List><WorkspaceTabs.Tab value="overview">{t.program.analytics.overview}</WorkspaceTabs.Tab><WorkspaceTabs.Tab value="items">{t.program.analytics.itemResults}</WorkspaceTabs.Tab><WorkspaceTabs.Tab value="reviewers">{t.program.analytics.reviewerResults}</WorkspaceTabs.Tab><WorkspaceTabs.Tab value="feedback">{t.program.analytics.employeeFeedback}</WorkspaceTabs.Tab><WorkspaceTabs.Tab value="matrix">{t.program.analytics.gradeMatrix}</WorkspaceTabs.Tab><WorkspaceTabs.Tab value="custom">{t.program.analytics.custom}</WorkspaceTabs.Tab><WorkspaceTabs.Tab value="tendency">{t.program.analytics.tendency}</WorkspaceTabs.Tab></WorkspaceTabs.List><WorkspaceTabs.Panel value="overview"><ResultsOverview programId={programId} /></WorkspaceTabs.Panel><WorkspaceTabs.Panel value="items"><ItemResults programId={programId} /></WorkspaceTabs.Panel><WorkspaceTabs.Panel value="reviewers"><ReviewerResults programId={programId} /></WorkspaceTabs.Panel><WorkspaceTabs.Panel value="feedback"><EmployeeFeedback key={programId} programId={programId} /></WorkspaceTabs.Panel><WorkspaceTabs.Panel value="matrix"><GradeMatrix key={programId} programs={choices} xDefault={programId} /></WorkspaceTabs.Panel><WorkspaceTabs.Panel value="custom"><CustomPivot key={programId} programs={choices} defaultProgram={programId} /></WorkspaceTabs.Panel><WorkspaceTabs.Panel value="tendency"><ReviewerTendency key={programId} programId={programId} /></WorkspaceTabs.Panel></WorkspaceTabs></UiStack>;
}

function ItemResults({ programId }: { programId: string | null }): React.ReactNode {
  const t = useT(); const query = useItemResultsQuery(programId); const rows = query.data ?? [];
  if (!programId) return <SectionCard><UiText c="dimmed">{t.program.common.noSelection}</UiText></SectionCard>;
  return <QueryState pending={query.isPending} error={query.error} empty={!rows.length} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}><SectionCard title={t.program.analytics.itemResults}><UiTable.ScrollContainer minWidth={720}><UiTable striped><UiTable.Thead><UiTable.Tr><UiTable.Th>{t.program.common.employee}</UiTable.Th><UiTable.Th>{t.program.reviews.itemResponse}</UiTable.Th><UiTable.Th>{t.program.programs.currentStage}</UiTable.Th><UiTable.Th>{t.program.goals.weight}</UiTable.Th><UiTable.Th>{t.program.common.score}</UiTable.Th><UiTable.Th>{t.program.common.grade}</UiTable.Th></UiTable.Tr></UiTable.Thead><UiTable.Tbody>{rows.map((row) => <UiTable.Tr key={`${row.participantId}-${row.itemId}-${row.round}`}><UiTable.Td>{row.employee.name}</UiTable.Td><UiTable.Td>{row.itemTitle}</UiTable.Td><UiTable.Td>{row.round}</UiTable.Td><UiTable.Td>{row.weightPercent}%</UiTable.Td><UiTable.Td>{row.score ?? '–'}</UiTable.Td><UiTable.Td>{row.grade ?? row.scaleCode ?? '–'}</UiTable.Td></UiTable.Tr>)}</UiTable.Tbody></UiTable></UiTable.ScrollContainer></SectionCard></QueryState>;
}

function ReviewerResults({ programId }: { programId: string | null }): React.ReactNode {
  const t = useT(); const query = useReviewerResultsQuery(programId); const rows = query.data ?? [];
  if (!programId) return <SectionCard><UiText c="dimmed">{t.program.common.noSelection}</UiText></SectionCard>;
  return <QueryState pending={query.isPending} error={query.error} empty={!rows.length} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}><SectionCard title={t.program.analytics.reviewerResults}><UiTable.ScrollContainer minWidth={760}><UiTable striped><UiTable.Thead><UiTable.Tr><UiTable.Th>{t.program.common.reviewer}</UiTable.Th><UiTable.Th>{t.program.common.employee}</UiTable.Th><UiTable.Th>{t.program.programs.currentStage}</UiTable.Th><UiTable.Th>{t.program.common.score}</UiTable.Th><UiTable.Th>{t.program.common.grade}</UiTable.Th><UiTable.Th>{t.program.common.comment}</UiTable.Th></UiTable.Tr></UiTable.Thead><UiTable.Tbody>{rows.map((row) => <UiTable.Tr key={`${row.reviewerEmployeeId}-${row.participantId}-${row.round}`}><UiTable.Td>{row.reviewerName}</UiTable.Td><UiTable.Td>{row.employee.name}</UiTable.Td><UiTable.Td>{row.round}</UiTable.Td><UiTable.Td>{row.score ?? '–'}</UiTable.Td><UiTable.Td>{row.grade ?? '–'}</UiTable.Td><UiTable.Td>{row.opinion ?? '–'}</UiTable.Td></UiTable.Tr>)}</UiTable.Tbody></UiTable></UiTable.ScrollContainer></SectionCard></QueryState>;
}

function EmployeeFeedback({ programId }: { programId: string | null }): React.ReactNode {
  const t = useT(); const results = useProgramResultsQuery(programId); const [participantId, setParticipantId] = useState<string | null>(null); const detail = useEmployeeFeedbackQuery(participantId);
  const options = (results.data?.rows ?? []).map((row) => ({ value: row.participantId, label: `${row.employee.name} · ${row.employee.orgUnitName ?? t.workspace.copy.notSpecified}` }));
  if (!programId) return <SectionCard><UiText c="dimmed">{t.program.common.noSelection}</UiText></SectionCard>;
  return <UiStack gap="md"><FormSelect searchable label={t.program.common.employee} value={participantId} data={options} onChange={setParticipantId} />{participantId ? <QueryState pending={detail.isPending} error={detail.error} empty={!detail.data} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>{detail.data ? <UiStack gap="md"><PerformanceMetricGrid items={[{ label: t.program.common.score, value: detail.data.adjustment?.adjustedScore ?? detail.data.calculation?.adjustedScore ?? detail.data.calculation?.normalizedScore ?? '–' }, { label: t.program.common.grade, value: detail.data.adjustment?.adjustedGrade ?? detail.data.calculation?.calculatedGrade ?? '–' }]} /><PerformanceCommentPanel title={t.program.reviews.finalFeedback} comment={detail.data.feedback?.comment} empty={t.program.common.noData} /><SectionCard title={t.program.reviews.previousRound}>{detail.data.submissions.map((submission) => <PerformanceCommentPanel key={submission.id} title={`${submission.round}`} comment={submission.overallOpinion} empty={t.program.common.noData} />)}</SectionCard></UiStack> : null}</QueryState> : <SectionCard><UiText c="dimmed">{t.program.common.noSelection}</UiText></SectionCard>}</UiStack>;
}

function ResultsOverview({ programId }: { programId: string | null }): React.ReactNode {
  const t = useT(); const query = useProgramResultsQuery(programId); const data = query.data; const total = data?.grades.reduce((sum, item) => sum + item.count, 0) ?? 0;
  if (!programId) return <SectionCard><UiText c="dimmed">{t.program.common.noSelection}</UiText></SectionCard>;
  const exportResults = async (): Promise<void> => { try { downloadBlob(await programsApi.exportResults(programId), 'evaluation-results.xlsx'); } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); } };
  return <QueryState pending={query.isPending} error={query.error} empty={!data} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>{data ? <UiStack gap="md"><UiGroup justify="flex-end"><CustomResultPdfModal key={programId} programId={programId} /><UiButton variant="light" onClick={() => void exportResults()}>{t.program.files.exportResults}</UiButton></UiGroup><PerformanceMetricGrid items={[{ label: t.program.analytics.totalPeople, value: data.rows.length }, { label: t.workspace.copy.finalized, value: data.finalizedCount }]} /><SectionCard title={t.program.analytics.gradeDistribution}><PerformanceDistributionBars buckets={data.grades.map((item, index) => { const ratio = total ? item.count / total : 0; return { key: item.grade, label: item.grade, count: item.count, ratio, ratioLabel: `${Math.round(ratio * 100)}%`, color: ['blue', 'teal', 'grape', 'orange', 'gray'][index % 5] }; })} empty={t.program.common.noData} /></SectionCard><SectionCard title={t.program.analytics.overview}><UiTable.ScrollContainer minWidth={640}><UiTable striped><UiTable.Thead><UiTable.Tr><UiTable.Th>{t.program.common.employee}</UiTable.Th><UiTable.Th>{t.program.common.department}</UiTable.Th><UiTable.Th>{t.program.common.score}</UiTable.Th><UiTable.Th>{t.program.common.grade}</UiTable.Th><UiTable.Th>{t.program.common.status}</UiTable.Th></UiTable.Tr></UiTable.Thead><UiTable.Tbody>{data.rows.map((row) => <UiTable.Tr key={row.participantId}><UiTable.Td>{row.employee.name}</UiTable.Td><UiTable.Td>{row.employee.orgUnitName ?? '–'}</UiTable.Td><UiTable.Td>{row.score ?? '–'}</UiTable.Td><UiTable.Td>{row.grade ?? '–'}</UiTable.Td><UiTable.Td><UiBadge>{row.feedbackStatus ? ({ DRAFT: t.program.reviews.saveDraft, DELIVERED: t.program.operations.publish, AGREED: t.program.reviews.accept, APPEALED: t.program.reviews.appeal, RESOLVED: t.program.stages.complete } as Record<string, string>)[row.feedbackStatus] ?? t.program.common.noData : '–'}</UiBadge></UiTable.Td></UiTable.Tr>)}</UiTable.Tbody></UiTable></UiTable.ScrollContainer></SectionCard></UiStack> : null}</QueryState>;
}

function GradeMatrix({ programs, xDefault }: { programs: Array<{ value: string; label: string }>; xDefault: string | null }): React.ReactNode {
  const t = useT();
  const [x, setX] = useState<string | null>(xDefault);
  const [y, setY] = useState<string | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const query = useGradeMatrixQuery(x, x === y ? null : y);
  const xDefinition = useProgramQuery(x);
  const yDefinition = useProgramQuery(y);
  const cells = query.data?.cells ?? [];
  const xConfig = xDefinition.data?.configuration;
  const yConfig = yDefinition.data?.configuration;
  const xScale = xConfig?.scales.find((scale) => scale.id === xConfig.calculation.resultScaleId);
  const yScale = yConfig?.scales.find((scale) => scale.id === yConfig.calculation.resultScaleId);
  const xGrades = [...new Set([...(xScale?.levels.map((level) => level.code) ?? []), ...cells.map((cell) => cell.xGrade)])];
  const yGrades = [...new Set([...(yScale?.levels.map((level) => level.code) ?? []), ...cells.map((cell) => cell.yGrade)])];
  const detail = cells.find((cell) => `${cell.xGrade}:${cell.yGrade}` === selected);
  const changeX = (value: string | null): void => { setX(value); setSelected(null); if (value === y) setY(null); };
  return <UiStack gap="md">
    <UiGroup grow><FormSelect searchable label={t.program.analytics.xAxisEvaluation} value={x} data={programs} onChange={changeX} /><FormSelect searchable label={t.program.analytics.yAxisEvaluation} value={y} data={programs.filter((p) => p.value !== x)} onChange={(value) => { setY(value); setSelected(null); }} /></UiGroup>
    {x && y ? <QueryState pending={query.isPending} error={query.error} empty={!cells.length} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>
      <SectionCard title={t.program.analytics.gradeMatrix}><UiTable.ScrollContainer minWidth={480}><UiTable withTableBorder withColumnBorders><UiTable.Thead><UiTable.Tr><UiTable.Th>{t.program.common.grade}</UiTable.Th>{xGrades.map((grade) => <UiTable.Th key={grade}>{grade}</UiTable.Th>)}</UiTable.Tr></UiTable.Thead><UiTable.Tbody>{yGrades.map((yGrade) => <UiTable.Tr key={yGrade}><UiTable.Th>{yGrade}</UiTable.Th>{xGrades.map((xGrade) => { const cell = cells.find((c) => c.xGrade === xGrade && c.yGrade === yGrade); const key = `${xGrade}:${yGrade}`; return <UiTable.Td key={key}><UiButton fullWidth variant={selected === key ? 'filled' : 'light'} disabled={!cell?.count} aria-label={`${xGrade} / ${yGrade}: ${cell?.count ?? 0}`} onClick={() => setSelected(key)}>{cell?.count ?? 0}</UiButton></UiTable.Td>; })}</UiTable.Tr>)}</UiTable.Tbody></UiTable></UiTable.ScrollContainer></SectionCard>
      {detail ? <SectionCard title={`${detail.xGrade} / ${detail.yGrade} · ${detail.count}`}><UiStack gap="xs">{detail.employees.map((employee, index) => <UiText key={`${employee.employeeId}-${index}`}>{employee.name} · {employee.orgUnitName ?? '–'}</UiText>)}</UiStack></SectionCard> : null}
    </QueryState> : <SectionCard><UiText c="dimmed">{t.program.analytics.distinctPrograms}</UiText></SectionCard>}
  </UiStack>;
}

function CustomPivot({ programs, defaultProgram }: { programs: Array<{ value: string; label: string }>; defaultProgram: string | null }): React.ReactNode {
  const t = useT(); const [programId, setProgramId] = useState<string | null>(defaultProgram); const [rows, setRows] = useState<string[]>(['department']); const [columns, setColumns] = useState<string[]>(['grade']); const [cellIndex, setCellIndex] = useState<number | null>(null); const pivot = usePivotMutation();
  const labels: Record<string, string> = { department: t.program.common.department, position: t.program.groupPolicy.position, grade: t.program.common.grade, job: t.program.groupPolicy.job, evaluationYear: t.program.common.year };
  const axisOptions = axes.map((value) => ({ value, label: labels[value] }));
  const valid = Boolean(programId && rows.length && columns.length && [...rows, ...columns].includes('grade'));
  useEffect(() => { pivot.reset(); setCellIndex(null); }, [programId, rows, columns]);
  const exportData = async (kind: 'xlsx' | 'svg'): Promise<void> => { if (!programId || !valid) return; try { const blob = kind === 'xlsx' ? await programsApi.exportPivot(programId, rows, columns) : await programsApi.exportPivotSvg(programId, rows, columns); downloadBlob(blob, kind === 'xlsx' ? 'evaluation-pivot.xlsx' : 'evaluation-pivot.svg'); } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); } };
  const search = async (): Promise<void> => { if (!programId || !valid) return; try { await pivot.mutateAsync({ programId, rowAxes: rows, columnAxes: columns }); setCellIndex(null); } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); } };
  const selected = cellIndex === null ? null : pivot.data?.cells[cellIndex];
  return <UiStack gap="md"><UiGroup align="end"><FormSelect searchable label={t.program.programs.title} value={programId} data={programs} onChange={setProgramId} /><FormMultiSelect label={t.program.analytics.rows} value={rows} data={axisOptions} onChange={setRows} /><FormMultiSelect label={t.program.analytics.columns} value={columns} data={axisOptions.filter((axis) => !rows.includes(axis.value))} onChange={setColumns} /><UiButton disabled={!valid} loading={pivot.isPending} onClick={() => void search()}>{t.program.common.search}</UiButton><UiButton variant="light" disabled={!pivot.data || !valid} onClick={() => void exportData('xlsx')}>{t.program.files.exportPivot}</UiButton><UiButton variant="light" disabled={!pivot.data || !valid} onClick={() => void exportData('svg')}>{t.program.files.exportChart}</UiButton></UiGroup>
  {![...rows, ...columns].includes('grade') ? <UiText c="red">{t.program.analytics.gradeAxisRequired}</UiText> : null}
  {pivot.data ? <UiStack><SectionCard title={t.program.analytics.pivot}><UiTable.ScrollContainer minWidth={560}><UiTable><UiTable.Thead><UiTable.Tr><UiTable.Th>{t.program.analytics.rows}</UiTable.Th><UiTable.Th>{t.program.common.grade}</UiTable.Th><UiTable.Th>{t.program.analytics.totalPeople}</UiTable.Th></UiTable.Tr></UiTable.Thead><UiTable.Tbody>{pivot.data.cells.map((cell, index) => <UiTable.Tr key={index}><UiTable.Td>{Object.values(cell.dimensions).join(' · ')}</UiTable.Td><UiTable.Td>{cell.grade}</UiTable.Td><UiTable.Td><UiButton variant={cellIndex === index ? 'light' : 'subtle'} onClick={() => setCellIndex(index)}>{cell.count}</UiButton></UiTable.Td></UiTable.Tr>)}</UiTable.Tbody></UiTable></UiTable.ScrollContainer></SectionCard>{selected ? <SectionCard title={t.program.common.employee}><UiStack gap="xs">{selected.employees.map((employee, index) => <UiText key={`${employee.employeeId}-${index}`}>{employee.name} · {employee.orgUnitName ?? '–'}</UiText>)}</UiStack></SectionCard> : null}</UiStack> : null}</UiStack>;
}

function ReviewerTendency({ programId }: { programId: string | null }): React.ReactNode {
  const t = useT(); const query = useReviewerTendenciesQuery(programId); const rows = query.data ?? []; const [selectedIndex, setSelectedIndex] = useState(0); const item = rows[selectedIndex];
  if (!programId) return <SectionCard><UiText c="dimmed">{t.program.common.noSelection}</UiText></SectionCard>;
  return <QueryState pending={query.isPending} error={query.error} empty={!rows.length} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.analytics.finalOnly}><MasterDetailWorkspace listLabel={t.program.common.reviewer} detailLabel={t.program.analytics.tendency} list={<UiStack>{rows.map((row, index) => <UiButton key={row.reviewerEmployeeId} variant={index === selectedIndex ? 'light' : 'subtle'} onClick={() => setSelectedIndex(index)}>{row.reviewerName}</UiButton>)}</UiStack>}>{item ? <UiStack><PerformanceMetricGrid items={[{ label: t.program.analytics.averageScore, value: item.meanScore ?? '–' }, { label: t.program.analytics.scorePosition, value: item.organizationMeanRank ?? '–' }, { label: t.program.analytics.scoreVariation, value: item.standardDeviation ?? '–' }, { label: t.program.analytics.specificity, value: item.opinionSpecificity ?? '–' }, { label: t.program.analytics.sentiment, value: item.positiveRatio == null ? '–' : `${Math.round(item.positiveRatio * 100)}%` }, { label: t.program.analytics.averageLength, value: item.averageOpinionLength ?? '–' }]} /><SectionCard title={t.program.analytics.tendency}><UiText>{t.program.analytics.methodDescription}</UiText>{item.positiveRatio == null ? <UiText c="dimmed">{t.program.analytics.sentimentUnavailable}</UiText> : null}{item.unavailableReasons.length ? <UiText c="dimmed">{t.program.analytics.unavailableDetail}</UiText> : null}</SectionCard><SectionCard title={t.program.analytics.keywords}><UiGroup>{item.frequentKeywords.map((keyword) => <UiBadge key={keyword}>{keyword}</UiBadge>)}</UiGroup></SectionCard></UiStack> : null}</MasterDetailWorkspace></QueryState>;
}

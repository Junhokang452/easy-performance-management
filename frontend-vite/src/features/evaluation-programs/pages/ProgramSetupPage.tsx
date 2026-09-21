import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  FormActions,
  FormNumberInput,
  FormSelect,
  FormSwitch,
  FormTextInput,
  FormTextarea,
  PageHeader,
  SectionCard,
  UiAlert,
  UiBadge,
  UiButton,
  UiGroup,
  UiSimpleGrid,
  UiStack,
  UiText,
  WorkspaceTabs,
} from '@easy/ui-components';

import { getErrorMessage } from '../../../api/error';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import {
  useOpenProgramMutation,
  useApplyCommonItemsMutation,
  useProgramGuidesQuery,
  useProgramQuery,
  useUpdateProgramBasicMutation,
  useUpdateDefinitionMutation,
  type AllocationRowInput,
  type CommonItemInput,
  type DepartmentPerformanceGroupInput,
  type ProgramConfiguration,
  type ProgramStage,
  type RevieweeGroupInput,
  type ScaleDefinitionInput,
} from '../api/programs';
import { evaluationKindLabel, programStageLabel, programStatusLabel } from '../components/programLabels';
import { QueryState } from '../components/QueryState';
import { GroupConditionsEditor, ReviewerWeightPlansEditor } from '../components/GroupPolicyEditors';

const tabs = ['basic', 'stages', 'calculation', 'scales', 'groups', 'items', 'department-performance', 'allocation', 'disclosure'] as const;
type SetupTab = typeof tabs[number];

const stageOrder: ProgramStage[] = ['GOAL', 'INTERMEDIATE', 'SELF_REVIEW', 'REVIEW', 'CALCULATION', 'CALIBRATION', 'FEEDBACK'];
const scaleUses = ['INPUT', 'RESULT', 'DEPARTMENT_RESULT'] as const;
const scaleKinds = ['SCORE', 'GRADE'] as const;

function cloneConfiguration(value: ProgramConfiguration): ProgramConfiguration {
  return JSON.parse(JSON.stringify(value)) as ProgramConfiguration;
}

export function ProgramSetupPage(): React.ReactNode {
  const { programId = '', tab = 'basic' } = useParams();
  const activeTab = tabs.includes(tab as SetupTab) ? tab as SetupTab : 'basic';
  const navigate = useNavigate();
  const t = useT();
  const query = useProgramQuery(programId || null);
  const save = useUpdateDefinitionMutation();
  const open = useOpenProgramMutation();
  const applyItems = useApplyCommonItemsMutation();
  const [configuration, setConfiguration] = useState<ProgramConfiguration | null>(null);
  const [reason, setReason] = useState('');
  const readOnly = query.data?.status !== 'DRAFT';

  useEffect(() => {
    if (query.data) setConfiguration(cloneConfiguration(query.data.configuration));
  }, [query.data]);

  const tabItems = useMemo(() => [
    ['basic', t.program.programs.basic], ['stages', t.program.programs.stages], ['calculation', t.program.programs.calculation],
    ['scales', t.program.programs.scales], ['groups', t.program.programs.groups], ['items', t.program.programs.items],
    ['department-performance', t.program.programs.departmentPerformance], ['allocation', t.program.programs.allocation], ['disclosure', t.program.programs.disclosure],
  ] as Array<[SetupTab, string]>, [t]);

  const persist = async (): Promise<void> => {
    if (!configuration || !reason.trim()) return;
    try {
      await save.mutateAsync({ id: programId, configuration, reason });
      setReason('');
      showToast({ tone: 'success', message: t.program.common.saveSuccess });
    } catch (error) {
      showToast({ tone: 'danger', message: getErrorMessage(error) });
    }
  };

  const openProgram = async (): Promise<void> => {
    try {
      await open.mutateAsync(programId);
      showToast({ tone: 'success', message: t.program.programs.open });
    } catch (error) {
      showToast({ tone: 'danger', message: getErrorMessage(error) });
    }
  };

  return (
    <QueryState pending={query.isPending} error={query.error} empty={!query.data} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.common.noData}>
      {query.data && configuration ? (
        <UiStack gap="md">
          <PageHeader
            title={query.data.name}
            description={`${query.data.startsOn} – ${query.data.endsOn}`}
            actions={(
              <>
                <UiBadge variant="light">{programStatusLabel(t, query.data.status)}</UiBadge>
                <UiButton component={Link} to={`/admin/evaluation-programs/${programId}/operations`} variant="light">{t.program.programs.operations}</UiButton>
                {query.data.status === 'DRAFT' ? <UiButton variant="light" loading={applyItems.isPending} onClick={() => void applyItems.mutateAsync(programId).then(() => showToast({ tone: 'success', message: t.program.programs.applyCommonItems })).catch((error) => showToast({ tone: 'danger', message: getErrorMessage(error) }))}>{t.program.programs.applyCommonItems}</UiButton> : null}
                {query.data.status === 'DRAFT' ? <UiButton loading={open.isPending} onClick={() => void openProgram()}>{t.program.programs.open}</UiButton> : null}
              </>
            )}
          />
          <WorkspaceTabs value={activeTab} onChange={(value) => value && navigate(`/admin/evaluation-programs/${programId}/setup/${value}`)}>
            <WorkspaceTabs.List>
              {tabItems.map(([value, label]) => <WorkspaceTabs.Tab key={value} value={value}>{label}</WorkspaceTabs.Tab>)}
            </WorkspaceTabs.List>
            <WorkspaceTabs.Panel value="basic"><BasicPanel program={query.data} /></WorkspaceTabs.Panel>
            <WorkspaceTabs.Panel value="stages">{readOnly ? <ConfigurationReadOnlyPanel tab="stages" value={configuration} /> : <StagesPanel programId={programId} value={configuration} onChange={setConfiguration} />}</WorkspaceTabs.Panel>
            <WorkspaceTabs.Panel value="calculation">{readOnly ? <ConfigurationReadOnlyPanel tab="calculation" value={configuration} /> : <CalculationPanel value={configuration} onChange={setConfiguration} />}</WorkspaceTabs.Panel>
            <WorkspaceTabs.Panel value="scales">{readOnly ? <ConfigurationReadOnlyPanel tab="scales" value={configuration} /> : <ScalesPanel value={configuration} onChange={setConfiguration} />}</WorkspaceTabs.Panel>
            <WorkspaceTabs.Panel value="groups">{readOnly ? <ConfigurationReadOnlyPanel tab="groups" value={configuration} /> : <GroupsPanel value={configuration} onChange={setConfiguration} />}</WorkspaceTabs.Panel>
            <WorkspaceTabs.Panel value="items">{readOnly ? <ConfigurationReadOnlyPanel tab="items" value={configuration} /> : <ItemsPanel value={configuration} onChange={setConfiguration} />}</WorkspaceTabs.Panel>
            <WorkspaceTabs.Panel value="department-performance">{readOnly ? <ConfigurationReadOnlyPanel tab="department-performance" value={configuration} /> : <DepartmentPerformancePanel value={configuration} onChange={setConfiguration} />}</WorkspaceTabs.Panel>
            <WorkspaceTabs.Panel value="allocation">{readOnly ? <ConfigurationReadOnlyPanel tab="allocation" value={configuration} /> : <AllocationPanel value={configuration} onChange={setConfiguration} />}</WorkspaceTabs.Panel>
            <WorkspaceTabs.Panel value="disclosure">{readOnly ? <ConfigurationReadOnlyPanel tab="disclosure" value={configuration} /> : <DisclosurePanel value={configuration} onChange={setConfiguration} />}</WorkspaceTabs.Panel>
          </WorkspaceTabs>
          {activeTab !== 'basic' && !readOnly ? (
            <SectionCard>
              <UiStack gap="sm">
                <FormTextInput required label={t.program.operations.reason} value={reason} onChange={(event) => setReason(event.currentTarget.value)} maxLength={500} />
                <FormActions primary={<UiButton loading={save.isPending} disabled={!reason.trim()} onClick={() => void persist()}>{t.program.programs.saveDraft}</UiButton>} />
              </UiStack>
            </SectionCard>
          ) : null}
        </UiStack>
      ) : null}
    </QueryState>
  );
}

function BasicPanel({ program }: { program: NonNullable<ReturnType<typeof useProgramQuery>['data']> }): React.ReactNode {
  const t = useT();
  const update = useUpdateProgramBasicMutation();
  const [form, setForm] = useState(() => ({ name: program.name, evaluationYear: program.evaluationYear, asOfDate: program.asOfDate, startsOn: program.startsOn, endsOn: program.endsOn }));
  useEffect(() => setForm({ name: program.name, evaluationYear: program.evaluationYear, asOfDate: program.asOfDate, startsOn: program.startsOn, endsOn: program.endsOn }), [program]);
  const persist = async (): Promise<void> => {
    try { await update.mutateAsync({ id: program.id, input: form }); showToast({ tone: 'success', message: t.program.common.saveSuccess }); }
    catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); }
  };
  if (program.status === 'DRAFT') return (
    <SectionCard title={t.program.programs.basic}>
      <UiStack gap="sm">
        <UiSimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
          <FormTextInput required label={t.program.programs.name} value={form.name} onChange={(event) => setForm({ ...form, name: event.currentTarget.value })} />
          <FormNumberInput required label={t.program.common.year} min={2000} max={2200} value={form.evaluationYear} onChange={(value) => setForm({ ...form, evaluationYear: Number(value) || program.evaluationYear })} />
          <FormTextInput required type="date" label={t.program.common.asOfDate} value={form.asOfDate} onChange={(event) => setForm({ ...form, asOfDate: event.currentTarget.value })} />
          <FormTextInput required type="date" label={t.program.programs.startDate} value={form.startsOn} onChange={(event) => setForm({ ...form, startsOn: event.currentTarget.value })} />
          <FormTextInput required type="date" label={t.program.programs.endDate} value={form.endsOn} onChange={(event) => setForm({ ...form, endsOn: event.currentTarget.value })} />
          <ReadValue label={t.program.programs.type} value={evaluationKindLabel(t, program.kind)} />
        </UiSimpleGrid>
        <FormActions primary={<UiButton loading={update.isPending} disabled={!form.name.trim() || form.endsOn < form.startsOn} onClick={() => void persist()}>{t.common.action.save}</UiButton>} />
      </UiStack>
    </SectionCard>
  );
  return (
    <SectionCard title={t.program.programs.basic} description={t.program.common.readOnly}>
      <UiSimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
        <ReadValue label={t.program.programs.name} value={program.name} />
        <ReadValue label={t.program.common.year} value={String(program.evaluationYear)} />
        <ReadValue label={t.program.programs.type} value={evaluationKindLabel(t, program.kind)} />
        <ReadValue label={t.program.programs.startDate} value={program.startsOn} />
        <ReadValue label={t.program.programs.endDate} value={program.endsOn} />
        <ReadValue label={t.program.common.status} value={programStatusLabel(t, program.status)} />
      </UiSimpleGrid>
    </SectionCard>
  );
}

function ReadValue({ label, value }: { label: string; value: string }): React.ReactNode {
  return <UiStack gap={2}><UiText size="xs" c="dimmed">{label}</UiText><UiText fw={600}>{value}</UiText></UiStack>;
}

type ConfigPanelProps = { value: ProgramConfiguration; onChange: (next: ProgramConfiguration) => void };

function ConfigurationReadOnlyPanel({ tab, value }: { tab: SetupTab; value: ProgramConfiguration }): React.ReactNode {
  const t = useT();
  if (tab === 'stages') return <SectionCard title={t.program.programs.stages} description={t.program.common.readOnly}><UiStack>{value.stages.filter((stage) => stage.enabled).map((stage) => <UiText key={stage.stage}>{programStageLabel(t, stage.stage)} · {stage.startsOn ?? '–'} – {stage.endsOn ?? '–'}</UiText>)}</UiStack></SectionCard>;
  if (tab === 'scales') return <SectionCard title={t.program.programs.scales} description={t.program.common.readOnly}><UiStack>{value.scales.map((scale) => <UiText key={scale.id}>{scale.name} · {scale.levels.map((level) => level.label).join(', ')}</UiText>)}</UiStack></SectionCard>;
  if (tab === 'groups') return <SectionCard title={t.program.programs.groups} description={t.program.common.readOnly}><UiStack>{value.groups.map((group) => <UiText key={group.id}>{group.name} · {group.conditions.length} {t.program.groupPolicy.conditions}</UiText>)}</UiStack></SectionCard>;
  if (tab === 'items') return <SectionCard title={t.program.programs.items} description={t.program.common.readOnly}><UiStack>{value.commonItems.map((item) => <UiText key={item.id}>{item.title} · {item.weightPercent}%</UiText>)}</UiStack></SectionCard>;
  if (tab === 'department-performance') return <SectionCard title={t.program.programs.departmentPerformance} description={t.program.common.readOnly}><UiStack>{value.departmentPerformanceGroups.map((group) => <UiText key={group.id}>{group.name} · {group.grade}</UiText>)}</UiStack></SectionCard>;
  if (tab === 'allocation') return <SectionCard title={t.program.programs.allocation} description={t.program.common.readOnly}><UiStack>{value.allocationRows.map((row, index) => <UiText key={index}>{t.program.analytics.totalPeople}: {row.populationSize} · {Object.entries(row.gradeHeadcounts).map(([grade, count]) => `${grade} ${count}`).join(', ')}</UiText>)}</UiStack></SectionCard>;
  if (tab === 'disclosure') return <SectionCard title={t.program.programs.disclosure} description={t.program.common.readOnly}><UiText>{value.publication.memberResultVisibility === 'GRADE_ONLY' ? t.program.settings.memberGradeOnly : t.program.settings.memberScoreGrade}</UiText></SectionCard>;
  const method = ({ NONE: t.program.settings.none, MEAN: t.program.settings.mean, STANDARD_DEVIATION: t.program.settings.standardDeviation })[value.calculation.adjustmentMethod];
  return <SectionCard title={t.program.programs.calculation} description={t.program.common.readOnly}><UiText>{t.program.settings.adjustmentMethod}: {method}</UiText></SectionCard>;
}

function conditionLabels(t: ReturnType<typeof useT>) {
  return { title: t.program.groupPolicy.conditions, field: t.program.groupPolicy.field, operator: t.program.groupPolicy.operator, values: t.program.groupPolicy.values, add: t.program.groupPolicy.addCondition, remove: t.common.action.delete, noConditions: t.program.groupPolicy.noConditions, loadError: t.program.common.loadError, loading: t.common.status.loading, unavailable: t.program.groupPolicy.unavailable, fields: { ORG_UNIT: t.program.groupPolicy.orgUnit, POSITION: t.program.groupPolicy.position, GRADE: t.program.groupPolicy.grade, JOB: t.program.groupPolicy.job, EMPLOYMENT_TYPE: t.program.groupPolicy.employmentType, EMPLOYEE: t.program.groupPolicy.employee }, operators: { IN: t.program.groupPolicy.includes, NOT_IN: t.program.groupPolicy.excludes, EQUALS: t.program.groupPolicy.equals, NOT_EQUALS: t.program.groupPolicy.notEquals } };
}

function StagesPanel({ value, onChange, programId }: ConfigPanelProps & { programId: string }): React.ReactNode {
  const t = useT();
  const guides = useProgramGuidesQuery(programId);
  const updateStage = (stage: ProgramStage, patch: Partial<ProgramConfiguration['stages'][number]>): void => {
    const found = value.stages.find((item) => item.stage === stage);
    const next = found
      ? value.stages.map((item) => item.stage === stage ? { ...item, ...patch } : item)
      : [...value.stages, { stage, enabled: false, startsOn: null, endsOn: null, formMode: 'DEFINITION_ONLY' as const, itemOpinionEnabled: false, itemOpinionRequired: false, guideAttachmentId: null, ...patch }];
    onChange({ ...value, stages: next });
  };
  return (
    <UiStack gap="md">
      <SectionCard title={t.program.programs.stages} description={t.program.stages.periodHint}>
        <FormSelect label={t.program.goals.agreementTitle} value={value.goalMode} data={[{ value: 'AGREEMENT', label: t.program.goals.agreementTitle }, { value: 'SELF_REPORT', label: t.program.goals.resubmit }]} onChange={(mode) => mode && onChange({ ...value, goalMode: mode as ProgramConfiguration['goalMode'] })} />
      </SectionCard>
      {stageOrder.map((stage) => {
        const item = value.stages.find((candidate) => candidate.stage === stage);
        return (
          <SectionCard key={stage} title={programStageLabel(t, stage)} actions={<FormSwitch label={t.program.stages.useStage} checked={item?.enabled ?? false} onChange={(event) => updateStage(stage, { enabled: event.currentTarget.checked })} />}>
            <UiSimpleGrid cols={{ base: 1, sm: 2 }}>
              <FormTextInput type="date" label={t.program.programs.startDate} disabled={!item?.enabled} value={item?.startsOn ?? ''} onChange={(event) => updateStage(stage, { startsOn: event.currentTarget.value || null })} />
              <FormTextInput type="date" label={t.program.programs.endDate} disabled={!item?.enabled} value={item?.endsOn ?? ''} onChange={(event) => updateStage(stage, { endsOn: event.currentTarget.value || null })} />
            </UiSimpleGrid>
            {(stage === 'SELF_REVIEW' || stage === 'REVIEW') && item?.enabled ? (
              <UiGroup mt="sm"><FormSwitch label={t.program.stages.opinionRequired} checked={item.itemOpinionRequired} onChange={(event) => updateStage(stage, { itemOpinionEnabled: true, itemOpinionRequired: event.currentTarget.checked })} /></UiGroup>
            ) : null}
            {stage === 'GOAL' && item?.enabled ? <FormSelect mt="sm" label={t.program.programs.items} value={item.formMode} data={[{ value: 'DEFINITION_ONLY', label: t.program.settings.definitionOnly }, { value: 'DEFINITION_AND_ACHIEVEMENT_LEVELS', label: t.program.settings.definitionLevels }]} onChange={(next) => next && updateStage(stage, { formMode: next as NonNullable<typeof item.formMode> })} /> : null}
            {item?.enabled ? <FormSelect clearable mt="sm" label={t.program.files.guides} value={item.guideAttachmentId} data={(guides.data ?? []).map((guide) => ({ value: guide.id, label: guide.filename }))} onChange={(guideAttachmentId) => updateStage(stage, { guideAttachmentId })} /> : null}
          </SectionCard>
        );
      })}
    </UiStack>
  );
}

function CalculationPanel({ value, onChange }: ConfigPanelProps): React.ReactNode {
  const t = useT();
  const policy = value.calculation;
  return (
    <UiStack gap="md"><SectionCard title={t.program.programs.calculation}>
      <UiSimpleGrid cols={{ base: 1, sm: 2 }}>
        <FormSelect label={t.program.settings.inputScale} value={policy.inputScaleId} data={value.scales.map((scale) => ({ value: scale.id, label: scale.name || t.program.programs.scales }))} onChange={(next) => next && onChange({ ...value, calculation: { ...policy, inputScaleId: next } })} />
        <FormSelect label={t.program.settings.resultScale} value={policy.resultScaleId} data={value.scales.map((scale) => ({ value: scale.id, label: scale.name || t.program.programs.scales }))} onChange={(next) => next && onChange({ ...value, calculation: { ...policy, resultScaleId: next } })} />
        <FormSelect clearable label={t.program.settings.departmentScale} value={policy.departmentResultScaleId} data={value.scales.map((scale) => ({ value: scale.id, label: scale.name || t.program.programs.scales }))} onChange={(next) => onChange({ ...value, calculation: { ...policy, departmentResultScaleId: next } })} />
        <FormSelect label={t.program.settings.adjustmentTarget} value={policy.adjustmentTarget} data={[{ value: 'ALL', label: t.program.settings.all }, { value: 'ABSOLUTE_ONLY', label: t.program.settings.absoluteOnly }, { value: 'RELATIVE_ONLY', label: t.program.settings.relativeOnly }]} onChange={(next) => next && onChange({ ...value, calculation: { ...policy, adjustmentTarget: next as typeof policy.adjustmentTarget } })} />
        <FormSelect label={t.program.settings.adjustmentMethod} value={policy.adjustmentMethod} data={[{ value: 'NONE', label: t.program.settings.none }, { value: 'MEAN', label: t.program.settings.mean }, { value: 'STANDARD_DEVIATION', label: t.program.settings.standardDeviation }]} onChange={(next) => next && onChange({ ...value, calculation: { ...policy, adjustmentMethod: next as typeof policy.adjustmentMethod } })} />
        <FormSelect label={t.program.settings.population} value={policy.populationBasis} data={[{ value: 'DEPARTMENT', label: t.program.settings.departmentPopulation }, { value: 'DEPARTMENT_PERFORMANCE_GROUP', label: t.program.settings.departmentGroupPopulation }]} onChange={(next) => next && onChange({ ...value, calculation: { ...policy, populationBasis: next as typeof policy.populationBasis } })} />
        <FormNumberInput label={t.program.analytics.averageScore} min={0} max={100} value={policy.targetMean ?? ''} onChange={(next) => onChange({ ...value, calculation: { ...policy, targetMean: next === '' ? null : Number(next) } })} />
        <FormNumberInput label={t.program.analytics.scoreVariation} min={0} value={policy.targetStandardDeviation ?? ''} onChange={(next) => onChange({ ...value, calculation: { ...policy, targetStandardDeviation: next === '' ? null : Number(next) } })} />
        <FormNumberInput label={t.program.common.score} min={0} max={8} value={policy.decimalPlaces} onChange={(next) => onChange({ ...value, calculation: { ...policy, decimalPlaces: Number(next) || 0 } })} />
        <FormSwitch label={t.program.programs.departmentPerformance} checked={policy.departmentPerformanceEnabled} onChange={(event) => onChange({ ...value, calculation: { ...policy, departmentPerformanceEnabled: event.currentTarget.checked } })} />
      </UiSimpleGrid>
    </SectionCard><SectionCard title={t.program.settings.componentWeights}><UiStack gap="xs">{policy.componentWeights.map((component, index) => <UiSimpleGrid key={index} cols={{ base: 1, sm: 2 }}><FormTextInput label={t.program.settings.component} value={component.component} onChange={(event) => onChange({ ...value, calculation: { ...policy, componentWeights: policy.componentWeights.map((row, rowIndex) => rowIndex === index ? { ...row, component: event.currentTarget.value } : row) } })} /><FormNumberInput label={t.program.goals.weight} min={0} max={100} value={component.weightPercent} onChange={(weightPercent) => onChange({ ...value, calculation: { ...policy, componentWeights: policy.componentWeights.map((row, rowIndex) => rowIndex === index ? { ...row, weightPercent: Number(weightPercent) || 0 } : row) } })} /></UiSimpleGrid>)}</UiStack></SectionCard></UiStack>
  );
}

function ScalesPanel({ value, onChange }: ConfigPanelProps): React.ReactNode {
  const t = useT();
  const update = (index: number, next: ScaleDefinitionInput): void => onChange({ ...value, scales: value.scales.map((item, i) => i === index ? next : item) });
  const add = (): void => onChange({ ...value, scales: [...value.scales, { id: crypto.randomUUID(), name: '', use: 'INPUT', kind: 'GRADE', levels: [{ code: '', label: '', convertedScore: null, lowerExclusive: null, upperInclusive: null, color: null }] }] });
  return (
    <UiStack gap="md">
      {value.scales.map((scale, index) => (
        <SectionCard key={scale.id ?? index} title={scale.name || t.program.programs.scales} actions={<UiButton variant="subtle" color="red" onClick={() => onChange({ ...value, scales: value.scales.filter((_, i) => i !== index) })}>{t.common.action.delete}</UiButton>}>
          <UiSimpleGrid cols={{ base: 1, sm: 3 }}>
            <FormTextInput required label={t.program.programs.name} value={scale.name} onChange={(event) => update(index, { ...scale, name: event.currentTarget.value })} />
            <FormSelect label={t.program.programs.type} value={scale.use} data={scaleUses.map((use) => ({ value: use, label: ({ INPUT: t.program.settings.scaleInput, RESULT: t.program.settings.scaleResult, DEPARTMENT_RESULT: t.program.settings.scaleDepartment })[use] }))} onChange={(next) => next && update(index, { ...scale, use: next as ScaleDefinitionInput['use'] })} />
            <FormSelect label={t.program.common.grade} value={scale.kind} data={scaleKinds.map((kind) => ({ value: kind, label: kind === 'SCORE' ? t.program.settings.scaleScore : t.program.settings.scaleGrade }))} onChange={(next) => next && update(index, { ...scale, kind: next as ScaleDefinitionInput['kind'] })} />
          </UiSimpleGrid>
          <UiStack mt="sm" gap="xs">
            {scale.levels.map((level, levelIndex) => (
              <UiSimpleGrid key={`${index}-${levelIndex}`} cols={{ base: 1, sm: 3 }}>
                <FormTextInput label={t.program.catalogs.code} value={level.code} onChange={(event) => update(index, { ...scale, levels: scale.levels.map((item, i) => i === levelIndex ? { ...item, code: event.currentTarget.value } : item) })} />
                <FormTextInput label={t.program.catalogs.name} value={level.label} onChange={(event) => update(index, { ...scale, levels: scale.levels.map((item, i) => i === levelIndex ? { ...item, label: event.currentTarget.value } : item) })} />
                <FormNumberInput label={t.program.common.score} value={level.convertedScore ?? ''} onChange={(next) => update(index, { ...scale, levels: scale.levels.map((item, i) => i === levelIndex ? { ...item, convertedScore: next === '' ? null : Number(next) } : item) })} />
                <FormNumberInput label={t.program.settings.lowerBound} value={level.lowerExclusive ?? ''} onChange={(next) => update(index, { ...scale, levels: scale.levels.map((item, i) => i === levelIndex ? { ...item, lowerExclusive: next === '' ? null : Number(next) } : item) })} />
                <FormNumberInput label={t.program.settings.upperBound} value={level.upperInclusive ?? ''} onChange={(next) => update(index, { ...scale, levels: scale.levels.map((item, i) => i === levelIndex ? { ...item, upperInclusive: next === '' ? null : Number(next) } : item) })} />
                <UiButton variant="subtle" color="red" onClick={() => update(index, { ...scale, levels: scale.levels.filter((_, i) => i !== levelIndex) })}>{t.common.action.delete}</UiButton>
              </UiSimpleGrid>
            ))}
            <UiButton variant="light" onClick={() => update(index, { ...scale, levels: [...scale.levels, { code: '', label: '', convertedScore: null, lowerExclusive: null, upperInclusive: null, color: null }] })}>{t.common.action.create}</UiButton>
          </UiStack>
        </SectionCard>
      ))}
      <UiButton variant="light" onClick={add}>{t.common.action.create}</UiButton>
    </UiStack>
  );
}

function GroupsPanel({ value, onChange }: ConfigPanelProps): React.ReactNode {
  const t = useT();
  const update = (index: number, patch: Partial<RevieweeGroupInput>): void => onChange({ ...value, groups: value.groups.map((item, i) => i === index ? { ...item, ...patch } : item) });
  return (
    <UiStack gap="md">
      {value.groups.map((group, index) => (
        <SectionCard key={group.id ?? index} title={group.name || t.program.programs.groups}>
          <UiStack gap="md"><UiSimpleGrid cols={{ base: 1, sm: 2 }}>
            <FormTextInput required label={t.program.programs.name} value={group.name} onChange={(event) => update(index, { name: event.currentTarget.value })} />
            <FormNumberInput label={t.program.catalogs.order} min={0} value={group.priority} onChange={(next) => update(index, { priority: Number(next) || 0 })} />
            <FormTextarea label={t.program.catalogs.definition} value={group.definition} onChange={(event) => update(index, { definition: event.currentTarget.value })} />
            <FormSelect label={t.program.programs.items} value={group.itemAssignmentMode} data={[{ value: 'AGREEMENT', label: t.program.settings.assignmentAgreement }, { value: 'DESIGNATED', label: t.program.settings.assignmentDesignated }]} onChange={(next) => next && update(index, { itemAssignmentMode: next as RevieweeGroupInput['itemAssignmentMode'] })} />
            <FormSelect label={t.program.programs.calculation} value={group.evaluationMethod} data={[{ value: 'ABSOLUTE', label: t.program.settings.evaluationAbsolute }, { value: 'RELATIVE', label: t.program.settings.evaluationRelative }]} onChange={(next) => next && update(index, { evaluationMethod: next as RevieweeGroupInput['evaluationMethod'] })} />
            <FormSwitch label={t.program.stages.midReview} checked={group.intermediateEnabled} onChange={(event) => update(index, { intermediateEnabled: event.currentTarget.checked })} />
            <FormSwitch label={t.program.stages.selfReview} checked={group.selfReviewEnabled} onChange={(event) => update(index, { selfReviewEnabled: event.currentTarget.checked })} />
          </UiSimpleGrid><GroupConditionsEditor value={group.conditions} onChange={(conditions) => update(index, { conditions })} labels={conditionLabels(t)} /><ReviewerWeightPlansEditor value={group.reviewerWeightPlans} onChange={(reviewerWeightPlans) => update(index, { reviewerWeightPlans })} departmentEnabled={value.calculation.departmentPerformanceEnabled} labels={{ title: t.program.groupPolicy.weightPlans, count: t.program.groupPolicy.reviewerCount, department: t.program.groupPolicy.departmentWeight, total: t.program.groupPolicy.total, totalMustBe100: t.program.groupPolicy.totalMustBe100, add: t.program.groupPolicy.addPlan, remove: t.common.action.delete, variantTitle: (count) => t.program.groupPolicy.planTitle.replace('{count}', String(count)), roundLabel: (round) => t.program.groupPolicy.roundTitle.replace('{round}', String(round)) }} /></UiStack>
        </SectionCard>
      ))}
      <UiButton variant="light" onClick={() => onChange({ ...value, groups: [...value.groups, { id: crypto.randomUUID(), name: '', definition: '', itemAssignmentMode: 'AGREEMENT', evaluationMethod: 'ABSOLUTE', intermediateEnabled: true, selfReviewEnabled: true, priority: value.groups.length, conditions: [], reviewerWeightPlans: [{ actualReviewerCount: 1, reviewerWeights: { 1: 100 }, departmentWeight: 0 }] }] })}>{t.common.action.create}</UiButton>
    </UiStack>
  );
}

function ItemsPanel({ value, onChange }: ConfigPanelProps): React.ReactNode {
  const t = useT();
  const update = (index: number, patch: Partial<CommonItemInput>): void => onChange({ ...value, commonItems: value.commonItems.map((item, i) => i === index ? { ...item, ...patch } : item) });
  if (!value.groups.length) return <UiAlert color="yellow">{t.program.programs.groups}</UiAlert>;
  return (
    <UiStack gap="md">
      {value.commonItems.map((item, index) => (
        <SectionCard key={item.id ?? index} title={item.title || t.program.programs.items}>
          <UiSimpleGrid cols={{ base: 1, sm: 2 }}>
            <FormTextInput required label={t.program.catalogs.name} value={item.title} onChange={(event) => update(index, { title: event.currentTarget.value })} />
            <FormSelect label={t.program.programs.groups} value={item.groupId} data={value.groups.map((group) => ({ value: group.id, label: group.name || t.program.programs.groups }))} onChange={(groupId) => groupId && update(index, { groupId })} />
            <FormSelect label={t.program.settings.inputScale} value={item.scaleId} data={value.scales.map((scale) => ({ value: scale.id, label: scale.name || t.program.programs.scales }))} onChange={(scaleId) => scaleId && update(index, { scaleId })} />
            <FormNumberInput label={t.program.goals.weight} min={0} max={100} value={item.weightPercent} onChange={(next) => update(index, { weightPercent: Number(next) || 0 })} />
            <FormNumberInput label={t.program.common.reviewer} min={0} max={3} value={item.round} onChange={(next) => update(index, { round: Number(next) || 0 })} />
            <FormNumberInput label={t.program.catalogs.order} min={0} value={item.displayOrder} onChange={(next) => update(index, { displayOrder: Number(next) || 0 })} />
            <FormSwitch label={t.program.stages.opinionRequired} checked={item.opinionRequired} onChange={(event) => update(index, { opinionRequired: event.currentTarget.checked })} />
          </UiSimpleGrid>
          <FormTextarea mt="sm" label={t.program.catalogs.definition} value={item.definition} onChange={(event) => update(index, { definition: event.currentTarget.value })} />
        </SectionCard>
      ))}
      <UiButton variant="light" disabled={!value.scales[0]} onClick={() => onChange({ ...value, commonItems: [...value.commonItems, { id: crypto.randomUUID(), groupId: value.groups[0].id, round: 1, catalogItemId: null, title: '', definition: '', weightPercent: 0, scaleId: value.scales[0]?.id ?? '', displayOrder: value.commonItems.length, opinionRequired: false }] })}>{t.common.action.create}</UiButton>
    </UiStack>
  );
}

function DepartmentPerformancePanel({ value, onChange }: ConfigPanelProps): React.ReactNode {
  const t = useT();
  const update = (index: number, patch: Partial<DepartmentPerformanceGroupInput>): void => onChange({ ...value, departmentPerformanceGroups: value.departmentPerformanceGroups.map((item, i) => i === index ? { ...item, ...patch } : item) });
  return (
    <UiStack gap="md">
      {value.departmentPerformanceGroups.map((group, index) => (
        <SectionCard key={group.id ?? index} title={group.name || t.program.programs.departmentPerformance}>
          <UiStack gap="md"><UiSimpleGrid cols={{ base: 1, sm: 3 }}>
            <FormTextInput label={t.program.programs.name} value={group.name} onChange={(event) => update(index, { name: event.currentTarget.value })} />
            <FormTextInput label={t.program.common.grade} value={group.grade} onChange={(event) => update(index, { grade: event.currentTarget.value })} />
            <FormNumberInput label={t.program.catalogs.order} min={0} value={group.displayOrder} onChange={(next) => update(index, { displayOrder: Number(next) || 0 })} />
          </UiSimpleGrid><GroupConditionsEditor value={group.conditions} onChange={(conditions) => update(index, { conditions })} labels={conditionLabels(t)} /></UiStack>
        </SectionCard>
      ))}
      <UiButton variant="light" onClick={() => onChange({ ...value, departmentPerformanceGroups: [...value.departmentPerformanceGroups, { id: crypto.randomUUID(), name: '', grade: '', displayOrder: value.departmentPerformanceGroups.length, conditions: [] }] })}>{t.common.action.create}</UiButton>
    </UiStack>
  );
}

function AllocationPanel({ value, onChange }: ConfigPanelProps): React.ReactNode {
  const t = useT();
  const grades = value.scales.find((scale) => scale.use === 'RESULT')?.levels.map((level) => level.code) ?? [];
  const update = (index: number, next: AllocationRowInput): void => onChange({ ...value, allocationRows: value.allocationRows.map((item, i) => i === index ? next : item) });
  return (
    <UiStack gap="md">
      {value.allocationRows.map((row, index) => (
        <SectionCard key={index} title={`${t.program.programs.allocation} ${index + 1}`}>
          <UiSimpleGrid cols={{ base: 1, sm: Math.min(grades.length + 1, 4) }}>
            <FormNumberInput label={t.program.analytics.totalPeople} min={1} value={row.populationSize} onChange={(next) => update(index, { ...row, populationSize: Number(next) || 1 })} />
            {grades.map((grade) => <FormNumberInput key={grade} label={grade} min={0} value={row.gradeHeadcounts[grade] ?? 0} onChange={(next) => update(index, { ...row, gradeHeadcounts: { ...row.gradeHeadcounts, [grade]: Number(next) || 0 } })} />)}
          </UiSimpleGrid>
        </SectionCard>
      ))}
      <UiButton variant="light" disabled={!grades.length} onClick={() => onChange({ ...value, allocationRows: [...value.allocationRows, { populationSize: 1, gradeHeadcounts: Object.fromEntries(grades.map((grade) => [grade, 0])) }] })}>{t.common.action.create}</UiButton>
    </UiStack>
  );
}

function DisclosurePanel({ value, onChange }: ConfigPanelProps): React.ReactNode {
  const t = useT();
  const policy = value.publication;
  return (
    <SectionCard title={t.program.programs.disclosure}>
      <UiSimpleGrid cols={{ base: 1, sm: 2 }}>
        <FormSelect label={t.program.stages.previousRoundVisible} value={policy.previousRoundVisibility} data={[{ value: 'HIDDEN', label: t.program.settings.visibilityHidden }, { value: 'SCORE_ONLY', label: t.program.settings.visibilityScore }, { value: 'SCORE_AND_OPINION', label: t.program.settings.visibilityScoreOpinion }]} onChange={(next) => next && onChange({ ...value, publication: { ...policy, previousRoundVisibility: next as typeof policy.previousRoundVisibility } })} />
        <FormSelect label={t.program.reports.detail} value={policy.memberResultVisibility} data={[{ value: 'SCORE_AND_GRADE', label: t.program.settings.memberScoreGrade }, { value: 'GRADE_ONLY', label: t.program.settings.memberGradeOnly }]} onChange={(next) => next && onChange({ ...value, publication: { ...policy, memberResultVisibility: next as typeof policy.memberResultVisibility } })} />
        <FormSwitch label={t.program.operations.reviewerAssignment} checked={policy.showReviewerAllocation} onChange={(event) => onChange({ ...value, publication: { ...policy, showReviewerAllocation: event.currentTarget.checked } })} />
        <FormSwitch label={t.program.stages.feedback} checked={policy.feedbackEnabled} onChange={(event) => onChange({ ...value, publication: { ...policy, feedbackEnabled: event.currentTarget.checked } })} />
        <FormSwitch label={t.program.reviews.appeal} checked={policy.appealEnabled} onChange={(event) => onChange({ ...value, publication: { ...policy, appealEnabled: event.currentTarget.checked } })} />
        <FormSwitch label={t.program.reviews.recalculate} checked={policy.scoreAdjustmentAllowed} onChange={(event) => onChange({ ...value, publication: { ...policy, scoreAdjustmentAllowed: event.currentTarget.checked } })} />
      </UiSimpleGrid>
    </SectionCard>
  );
}

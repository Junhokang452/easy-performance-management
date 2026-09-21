import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { buildQueryKey } from '@easy/query-client';
import {
  FormNumberInput, FormSelect, LookupMultiSelect, SectionCard,
  UiButton, UiGroup, UiSimpleGrid, UiStack, UiText,
} from '@easy/ui-components';
import { apiClient } from '../../../api/client';
import type { GroupConditionInput, ReviewerWeightPlanInput } from '../api/programs';

type ConditionField = GroupConditionInput['field'];
type ConditionOperator = GroupConditionInput['operator'];
const fields: ConditionField[] = ['ORG_UNIT', 'POSITION', 'GRADE', 'JOB', 'EMPLOYMENT_TYPE', 'EMPLOYEE'];
const operators: ConditionOperator[] = ['IN', 'NOT_IN', 'EQUALS', 'NOT_EQUALS'];
interface Option { value: string; label: string; description?: string | null }

export interface GroupConditionLabels {
  title: string;
  field: string;
  operator: string;
  values: string;
  add: string;
  remove: string;
  noConditions: string;
  loadError: string;
  loading: string;
  unavailable: string;
  fields: Record<ConditionField, string>;
  operators: Record<ConditionOperator, string>;
}

export interface GroupConditionsEditorProps {
  value: GroupConditionInput[];
  onChange: (value: GroupConditionInput[]) => void;
  labels: GroupConditionLabels;
  disabled?: boolean;
}

/** Product condition rules, composed from the shared form/lookup assets. */
export function GroupConditionsEditor({ value, onChange, labels, disabled = false }: GroupConditionsEditorProps): React.ReactNode {
  const replace = (index: number, next: GroupConditionInput): void =>
    onChange(value.map((condition, position) => position === index ? next : condition));
  return (
    <UiStack gap="sm">
      <UiText fw={600}>{labels.title}</UiText>
      {value.length === 0 ? <UiText size="sm" c="dimmed">{labels.noConditions}</UiText> : null}
      {value.map((condition, index) => (
        <ConditionRow key={index} value={condition} labels={labels} disabled={disabled}
          onChange={(next) => replace(index, next)}
          onRemove={() => onChange(value.filter((_, position) => position !== index))} />
      ))}
      {!disabled ? <UiButton variant="light" disabled={value.length >= 20}
        onClick={() => onChange([...value, { field: 'ORG_UNIT', operator: 'IN', values: [] }])}>
        {labels.add}
      </UiButton> : null}
    </UiStack>
  );
}

function ConditionRow({ value, onChange, onRemove, labels, disabled }: {
  value: GroupConditionInput;
  onChange: (value: GroupConditionInput) => void;
  onRemove: () => void;
  labels: GroupConditionLabels;
  disabled: boolean;
}): React.ReactNode {
  const [search, setSearch] = useState('');
  const lookup = (selected?: string[]): Promise<Option[]> => apiClient.get<Option[]>(
    '/v1/evaluation-resources/lookup/condition-values',
    { params: { field: value.field, q: selected ? '' : search, values: selected?.join(',') } },
  ).then(({ data }) => data);
  const searchQuery = useQuery({
    queryKey: buildQueryKey('performance', 'condition-values', value.field, search),
    queryFn: () => lookup(),
  });
  const selectedQuery = useQuery({
    queryKey: buildQueryKey('performance', 'condition-selected-values', value.field, ...value.values),
    queryFn: () => lookup(value.values),
    enabled: value.values.length > 0,
  });
  const options = new Map<string, Option>();
  for (const selected of value.values) {
    options.set(selected, { value: selected, label: selectedQuery.isPending ? labels.loading : labels.unavailable });
  }
  for (const option of [...(selectedQuery.data ?? []), ...(searchQuery.data ?? [])]) options.set(option.value, option);
  return (
    <SectionCard>
      <UiStack gap="sm">
        <UiSimpleGrid cols={{ base: 1, sm: 2 }}>
          <FormSelect label={labels.field} disabled={disabled} value={value.field}
            data={fields.map((field) => ({ value: field, label: labels.fields[field] }))}
            onChange={(field) => {
              if (field) { setSearch(''); onChange({ ...value, field: field as ConditionField, values: [] }); }
            }} />
          <FormSelect label={labels.operator} disabled={disabled} value={value.operator}
            data={operators.map((operator) => ({ value: operator, label: labels.operators[operator] }))}
            onChange={(operator) => {
              if (operator) onChange({ ...value, operator: operator as ConditionOperator,
                values: operator === 'EQUALS' || operator === 'NOT_EQUALS' ? value.values.slice(0, 1) : value.values });
            }} />
        </UiSimpleGrid>
        <LookupMultiSelect label={labels.values} value={value.values}
          data={[...options.values()]} searchValue={search} onSearchChange={setSearch}
          onChange={(values) => { if (!disabled) onChange({ ...value, values: value.operator === 'EQUALS' || value.operator === 'NOT_EQUALS' ? values.slice(-1) : values }); }} />
        {searchQuery.isError || selectedQuery.isError ? <UiText role="alert" size="sm">{labels.loadError}</UiText> : null}
        {!disabled ? <UiGroup justify="flex-end"><UiButton variant="subtle" onClick={onRemove}>{labels.remove}</UiButton></UiGroup> : null}
      </UiStack>
    </SectionCard>
  );
}

export interface ReviewerWeightLabels {
  title: string;
  count: string;
  department: string;
  total: string;
  totalMustBe100: string;
  add: string;
  remove: string;
  variantTitle: (count: number) => string;
  roundLabel: (round: number) => string;
}

export interface ReviewerWeightPlansEditorProps {
  value: ReviewerWeightPlanInput[];
  onChange: (value: ReviewerWeightPlanInput[]) => void;
  labels: ReviewerWeightLabels;
  departmentEnabled: boolean;
  disabled?: boolean;
}

/** A separate weight plan is required for each actual evaluator count. */
export function ReviewerWeightPlansEditor({ value, onChange, labels, departmentEnabled, disabled = false }: ReviewerWeightPlansEditorProps): React.ReactNode {
  const replace = (index: number, plan: ReviewerWeightPlanInput): void =>
    onChange(value.map((current, position) => position === index ? plan : current));
  const nextCount = [1, 2, 3].find((count) => !value.some((plan) => plan.actualReviewerCount === count));
  return (
    <UiStack gap="sm">
      <UiText fw={600}>{labels.title}</UiText>
      {value.map((plan, index) => {
        const total = Object.values(plan.reviewerWeights).reduce((sum, weight) => sum + weight, 0) + plan.departmentWeight;
        const valid = Math.abs(total - 100) < 0.00001;
        return (
          <SectionCard key={index} title={labels.variantTitle(plan.actualReviewerCount)}>
            <UiStack gap="sm">
              <FormSelect label={labels.count} disabled={disabled} value={String(plan.actualReviewerCount)}
                data={[1, 2, 3].map((count) => ({ value: String(count), label: String(count),
                  disabled: value.some((other, position) => position !== index && other.actualReviewerCount === count) }))}
                onChange={(raw) => {
                  if (!raw) return;
                  const count = Number(raw);
                  replace(index, { ...plan, actualReviewerCount: count,
                    reviewerWeights: Object.fromEntries(Array.from({ length: count }, (_, round) => [String(round + 1), plan.reviewerWeights[String(round + 1)] ?? 0])) });
                }} />
              <UiSimpleGrid cols={{ base: 1, sm: 2 }}>
                {Array.from({ length: plan.actualReviewerCount }, (_, i) => i + 1).map((round) => (
                  <FormNumberInput key={round} label={labels.roundLabel(round)} disabled={disabled}
                    min={0} max={100} decimalScale={4} suffix="%" value={plan.reviewerWeights[String(round)] ?? 0}
                    onChange={(weight) => replace(index, { ...plan, reviewerWeights: { ...plan.reviewerWeights, [String(round)]: Number(weight) || 0 } })} />
                ))}
                {departmentEnabled || plan.departmentWeight !== 0 ? <FormNumberInput label={labels.department}
                  disabled={disabled} min={0} max={100} decimalScale={4} suffix="%" value={plan.departmentWeight}
                  onChange={(weight) => replace(index, { ...plan, departmentWeight: Number(weight) || 0 })} /> : null}
              </UiSimpleGrid>
              <UiGroup justify="space-between">
                <UiText size="sm" role={valid ? undefined : 'alert'}>{labels.total}: {Number(total.toFixed(4))}%{valid ? '' : ` · ${labels.totalMustBe100}`}</UiText>
                {!disabled && value.length > 1 ? <UiButton variant="subtle" onClick={() => onChange(value.filter((_, position) => position !== index))}>{labels.remove}</UiButton> : null}
              </UiGroup>
            </UiStack>
          </SectionCard>
        );
      })}
      {!disabled ? <UiButton variant="light" disabled={nextCount === undefined} onClick={() => {
        if (nextCount !== undefined) onChange([...value, { actualReviewerCount: nextCount,
          reviewerWeights: Object.fromEntries(Array.from({ length: nextCount }, (_, i) => [String(i + 1), i === 0 ? 100 : 0])), departmentWeight: 0 }]);
      }}>{labels.add}</UiButton> : null}
    </UiStack>
  );
}

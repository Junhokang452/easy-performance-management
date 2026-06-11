/**
 * CycleSelect — KPI 화면 공유 사이클 선택 Select (useCyclesQuery 기반).
 *
 * 3 KPI 화면(My/매니저/본부)이 공유. 빈 목록·로딩 상태 처리.
 */
import { Select, Text } from '@mantine/core';

import { useCyclesQuery } from '../../api/cycles';
import { useT } from '../../i18n';

interface Props {
  value: string | null;
  onChange: (cycleId: string | null) => void;
}

export function CycleSelect({ value, onChange }: Props): React.ReactNode {
  const t = useT();
  const { data, isLoading } = useCyclesQuery();
  const cycles = data?.content ?? [];

  if (!isLoading && cycles.length === 0) {
    return (
      <Text size="sm" c="dimmed">
        {t.kpi.noCycle}
      </Text>
    );
  }

  return (
    <Select
      label={t.kpi.selectCycle}
      placeholder={t.kpi.cyclePlaceholder}
      data={cycles.map((c) => ({ value: c.id, label: c.name }))}
      value={value}
      onChange={onChange}
      disabled={isLoading}
      searchable
      clearable
      maw={360}
    />
  );
}

import { PageHeader, PerformanceMetricGrid, SectionCard, UiStack, UiTable } from '@easy/ui-components';
import { useT } from '../../../i18n';
import { usePersonalHistoryQuery } from '../api/programs';
import { QueryState } from '../components/QueryState';
import { evaluationKindLabel } from '../components/programLabels';

export function PersonalReportPage(): React.ReactNode {
  const t = useT(); const query = usePersonalHistoryQuery(); const rows = query.data?.history ?? []; const scored = rows.filter((row) => row.score != null); const average = scored.length ? scored.reduce((sum, row) => sum + (row.score ?? 0), 0) / scored.length : null;
  return <UiStack gap="md"><PageHeader title={t.program.reports.title} description={t.program.reports.description} /><QueryState pending={query.isPending} error={query.error} empty={!rows.length} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.reports.notPublished}><UiStack gap="md"><PerformanceMetricGrid items={[{ label: t.program.reports.fiveYears, value: rows.length }, { label: t.program.analytics.averageScore, value: average == null ? '–' : average.toFixed(1) }]} /><SectionCard title={t.program.reports.history}><UiTable.ScrollContainer minWidth={560}><UiTable><UiTable.Thead><UiTable.Tr><UiTable.Th>{t.program.common.year}</UiTable.Th><UiTable.Th>{t.program.programs.title}</UiTable.Th><UiTable.Th>{t.program.programs.type}</UiTable.Th><UiTable.Th>{t.program.common.score}</UiTable.Th><UiTable.Th>{t.program.common.grade}</UiTable.Th></UiTable.Tr></UiTable.Thead><UiTable.Tbody>{rows.map((row) => <UiTable.Tr key={row.programId}><UiTable.Td>{row.year}</UiTable.Td><UiTable.Td>{row.programName}</UiTable.Td><UiTable.Td>{evaluationKindLabel(t, row.kind)}</UiTable.Td><UiTable.Td>{row.score ?? '–'}</UiTable.Td><UiTable.Td>{row.grade ?? '–'}</UiTable.Td></UiTable.Tr>)}</UiTable.Tbody></UiTable></UiTable.ScrollContainer></SectionCard></UiStack></QueryState></UiStack>;
}

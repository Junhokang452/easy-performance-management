/**
 * MyReportPage (#7) — 사원 본인 리포트 (`/my/report`).
 *
 * cycle Select + employeeId 입력(MyKpiPage 패턴) → GET /reports/my (404 = 빈 상태) →
 * 리포트 카드: finalGrade 대형 Badge + 점수 4종 분해(kpi 값 / mbo·competency·mra 는 "—" P1) +
 * KPI 항목 요약 표(content.kpiItems read-only — ReviewKpiItemsTable 재사용) + managerComment +
 * 전사 분포 % 가로 막대(content.distribution — 본인 등급 강조).
 *
 * 동작:
 * - 열람 자동 기록: 조회 성공 + viewedAt null 일 때 view 1회 (useEffect + ref 가드 — 무한 재호출 금지).
 * - 확인(acknowledge): 멱등, 완료 후 비활성 + 확인 시각 표시.
 * - Appeal 링크 비노출 (P0-S10).
 *
 * 계약 §6/§7. content 는 BE 동결 스냅샷 표시만 (재계산 금지, 분포 % 환산 렌더 한정). STD-FE 5 정합.
 */
import { useEffect, useRef, useState } from 'react';
import {
  Badge,
  Button,
  Card,
  Divider,
  Group,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconCircleCheck, IconSearch } from '@tabler/icons-react';
import {
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';

import {
  formatReportDateTime,
  useAcknowledgeReportMutation,
  useMyReportQuery,
  useViewReportMutation,
  type ReportResponse,
} from '../api/reports';
import { formatScore } from '../api/reviews';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleSelect } from './kpi/CycleSelect';
import { GradeBadge } from './calibration/GradeBadge';
import { ReviewKpiItemsTable } from './review/ReviewKpiItemsTable';
import { ReportDistributionBars } from './report/ReportDistributionBars';
import { isReportNotFound, mapReportErrorToMessage } from './report/errorMapping';

export function MyReportPage(): React.ReactNode {
  const t = useT();

  // 입력(폼)과 조회(applied) 상태 분리 — '조회' 클릭 시 확정 (MyKpiPage 패턴).
  const [cycleId, setCycleId] = useState<string | null>(null);
  const [employeeIdInput, setEmployeeIdInput] = useState('');
  const [applied, setApplied] = useState<{
    cycleId: string;
    employeeId: string;
  } | null>(null);

  const canLoad = Boolean(cycleId) && employeeIdInput.trim().length > 0;

  const handleLoad = (): void => {
    if (!cycleId || !employeeIdInput.trim()) return;
    setApplied({ cycleId, employeeId: employeeIdInput.trim() });
  };

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.report.my.title}
        description={t.report.my.description}
      />
      <SectionCard>
        <Group align="end" gap="md">
          <CycleSelect value={cycleId} onChange={setCycleId} />
          <TextInput
            label={t.report.my.employeeId}
            placeholder={t.report.my.employeeIdPlaceholder}
            value={employeeIdInput}
            onChange={(e) => setEmployeeIdInput(e.currentTarget.value)}
            w={320}
          />
          <Button
            leftSection={<IconSearch size={16} />}
            onClick={handleLoad}
            disabled={!canLoad}
          >
            {t.report.my.load}
          </Button>
        </Group>
      </SectionCard>

      {!applied ? (
        <SectionCard>
          <Text c="dimmed" size="sm">
            {t.report.my.needInput}
          </Text>
        </SectionCard>
      ) : (
        <MyReportContent
          key={`${applied.cycleId}:${applied.employeeId}`}
          cycleId={applied.cycleId}
          employeeId={applied.employeeId}
        />
      )}
    </ErrorBoundary>
  );
}

interface ContentProps {
  cycleId: string;
  employeeId: string;
}

function MyReportContent({ cycleId, employeeId }: ContentProps): React.ReactNode {
  const t = useT();
  const query = useMyReportQuery(cycleId, employeeId);
  const report = query.data ?? null;

  const viewMut = useViewReportMutation();
  const ackMut = useAcknowledgeReportMutation();

  // 열람 자동 기록 — viewedAt null + 조회 성공 시 1회 (report.id 당 단 한 번, 무한 재호출 금지).
  const viewedRef = useRef<string | null>(null);
  useEffect(() => {
    if (!report) return;
    if (report.viewedAt != null) return;
    if (viewedRef.current === report.id) return;
    viewedRef.current = report.id;
    viewMut.mutate(report.id);
    // viewMut 은 안정 참조가 아니므로 deps 에서 제외 — report 변화에만 반응 (ref 가 재호출 차단).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [report?.id, report?.viewedAt]);

  const handleAcknowledge = (): void => {
    if (!report) return;
    ackMut.mutate(
      { reportId: report.id, req: { actorEmployeeId: null } },
      {
        onSuccess: () => {
          notifications.show({
            color: 'green',
            message: t.report.my.acknowledgeDone,
          });
        },
        onError: (err) => {
          notifications.show({
            color: 'red',
            message: mapReportErrorToMessage(err, t),
          });
        },
      },
    );
  };

  if (query.isLoading) {
    return (
      <SectionCard>
        <LoadingState message={t.common.status.loading} />
      </SectionCard>
    );
  }
  if (query.isError) {
    // 404 (E9804449) = 아직 발행 전 → 빈 상태. 그 외는 실 에러.
    if (isReportNotFound(query.error)) {
      return (
        <SectionCard>
          <EmptyState
            title={t.report.my.empty}
            description={t.report.my.emptyHint}
          />
        </SectionCard>
      );
    }
    return (
      <SectionCard>
        <Text c="red">
          {t.common.message.loadError}: {getErrorMessage(query.error)}
        </Text>
      </SectionCard>
    );
  }
  if (!report) return null;

  return (
    <ReportCard
      report={report}
      onAcknowledge={handleAcknowledge}
      acknowledging={ackMut.isPending}
    />
  );
}

interface CardProps {
  report: ReportResponse;
  onAcknowledge: () => void;
  acknowledging: boolean;
}

function ReportCard({
  report,
  onAcknowledge,
  acknowledging,
}: CardProps): React.ReactNode {
  const t = useT();
  const { content } = report;
  const kpiItems = content.kpiItems ?? [];

  return (
    <>
      {/* 최종 등급 + 점수 분해 */}
      <SectionCard>
        <Stack>
          <Group justify="space-between" align="flex-start" wrap="nowrap">
            <Stack gap={4}>
              <Text size="xs" c="dimmed">
                {t.report.card.finalGrade}
              </Text>
              <GradeBadge grade={content.finalGrade} size="xl" />
            </Stack>
            <Stack gap={4} align="flex-end">
              <Text size="xs" c="dimmed">
                {t.report.card.finalScore}
              </Text>
              <Text fw={700} fz={28}>
                {formatScore(content.finalScore)}
              </Text>
            </Stack>
          </Group>

          <Divider />

          <SimpleGrid cols={{ base: 2, sm: 4 }} spacing="sm">
            <ScoreTile label={t.report.card.kpiScore} value={content.kpiScore} />
            <ScoreTile
              label={t.report.card.mboScore}
              value={content.mboScore}
              p1
            />
            <ScoreTile
              label={t.report.card.competencyScore}
              value={content.competencyScore}
              p1
            />
            <ScoreTile
              label={t.report.card.mraScore}
              value={content.mraScore}
              p1
            />
          </SimpleGrid>
        </Stack>
      </SectionCard>

      {/* KPI 항목 요약 (read-only — ReviewKpiItemsTable 재사용) */}
      <SectionCard>
        <Stack gap="xs">
          <Text fw={600} size="sm">
            {t.report.card.kpiSection}
          </Text>
          <ReviewKpiItemsTable mode="readOnly" items={kpiItems} />
        </Stack>
      </SectionCard>

      {/* 매니저 의견 */}
      <SectionCard>
        <Stack gap="xs">
          <Text fw={600} size="sm">
            {t.report.card.managerComment}
          </Text>
          {content.managerComment ? (
            <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
              {content.managerComment}
            </Text>
          ) : (
            <Text size="sm" c="dimmed">
              {t.report.card.noComment}
            </Text>
          )}
        </Stack>
      </SectionCard>

      {/* 전사 분포 % (본인 등급 강조) */}
      <SectionCard>
        <Stack gap="xs">
          <Text fw={600} size="sm">
            {t.report.card.distributionHeading}
          </Text>
          <ReportDistributionBars
            distribution={content.distribution}
            myGrade={content.finalGrade}
          />
        </Stack>
      </SectionCard>

      {/* 확인(acknowledge) */}
      <SectionCard>
        <Card withBorder padding="md">
          <Group justify="space-between" wrap="nowrap">
            <Stack gap={2}>
              <Text size="sm" fw={500}>
                {t.report.card.acknowledgeHeading}
              </Text>
              {report.acknowledged ? (
                <Group gap={6}>
                  <Badge color="green" variant="light" size="sm">
                    {t.report.card.acknowledged}
                  </Badge>
                  <Text size="xs" c="dimmed">
                    {formatReportDateTime(report.acknowledgedAt)}
                  </Text>
                </Group>
              ) : (
                <Text size="xs" c="dimmed">
                  {t.report.card.acknowledgeHint}
                </Text>
              )}
            </Stack>
            <Button
              leftSection={<IconCircleCheck size={16} />}
              color="green"
              onClick={onAcknowledge}
              loading={acknowledging}
              disabled={report.acknowledged}
            >
              {report.acknowledged
                ? t.report.card.acknowledgedButton
                : t.report.card.acknowledge}
            </Button>
          </Group>
        </Card>
      </SectionCard>
    </>
  );
}

interface ScoreTileProps {
  label: string;
  value: number | null;
  /** P0 미산출 항목 — null 일 때 "P1 예정" 안내. */
  p1?: boolean;
}

function ScoreTile({ label, value, p1 }: ScoreTileProps): React.ReactNode {
  const t = useT();
  return (
    <Card withBorder padding="sm">
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      {value != null ? (
        <Text fw={600} fz="lg">
          {formatScore(value)}
        </Text>
      ) : (
        <Text fw={500} fz="lg" c="dimmed">
          {p1 ? t.report.card.scoreP1 : '—'}
        </Text>
      )}
    </Card>
  );
}

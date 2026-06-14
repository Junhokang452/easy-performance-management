/**
 * DirectorCalibrationPage (#19) — 본부 Calibration grid (`/director/calibration`).
 *
 * cycle Select + session Select(해당 cycle 세션 — IN_SESSION/ADJUSTED 만 조정 가능 안내) →
 * 분포 막대(현재 vs 목표, GET distribution) + review 행 테이블(employeeId·kpiScore·effectiveGrade Badge) +
 * 행별 등급 이동(AdjustGradeMenu S~D + reason) → adjustments API +
 * 조정 이력 패널(선택 세션 adjustmentLog 역순 리스트).
 *
 * 계약 §6/§7. 등급·분포는 BE 표시값(reviews 목록 finalGrade ?? band 는 행 표시 보조 — §5).
 * STD-FE 5 정합.
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Badge,
  Group,
  ScrollArea,
  Select,
  Stack,
  Table,
  Text,
} from '@mantine/core';
import {
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';
import {
  PerformanceLogEntryCard,
  PerformancePreWrapText,
} from '@easy/ui-components/performance';

import {
  canAdjustSession,
  effectiveGradeLabel,
  useCalibrationSessionQuery,
  useCalibrationSessionsQuery,
  useDistributionQuery,
  type AdjustmentEntry,
} from '../api/calibration';
import {
  formatScore,
  useReviewsByCycleQuery,
  type ReviewResponse,
} from '../api/reviews';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleSelect } from './kpi/CycleSelect';
import { DistributionBars } from './calibration/DistributionBars';
import { GradeBadge } from './calibration/GradeBadge';
import { SessionStatusBadge } from './calibration/SessionStatusBadge';
import { AdjustGradeMenu } from './calibration/AdjustGradeMenu';

export function DirectorCalibrationPage(): React.ReactNode {
  const t = useT();
  const [cycleId, setCycleId] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);

  const sessionsQuery = useCalibrationSessionsQuery(cycleId);
  const sessions = useMemo(
    () => sessionsQuery.data ?? [],
    [sessionsQuery.data],
  );

  // cycle 변경 시 세션 선택 초기화.
  useEffect(() => {
    setSessionId(null);
  }, [cycleId]);

  const distributionQuery = useDistributionQuery(cycleId);
  const distribution = distributionQuery.data ?? null;

  const reviewsQuery = useReviewsByCycleQuery(cycleId);
  const reviews = useMemo(() => reviewsQuery.data ?? [], [reviewsQuery.data]);

  const sessionDetailQuery = useCalibrationSessionQuery(sessionId);
  const selectedSession = sessionDetailQuery.data ?? null;
  const adjustable = selectedSession
    ? canAdjustSession(selectedSession.status)
    : false;

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.calibration.director.title}
        description={t.calibration.director.description}
      />

      <SectionCard>
        <Stack>
          <Group align="end" gap="md" wrap="wrap">
            <CycleSelect value={cycleId} onChange={setCycleId} />
            {cycleId && (
              <Select
                label={t.calibration.director.selectSession}
                placeholder={t.calibration.director.sessionPlaceholder}
                data={sessions.map((s) => ({
                  value: s.id,
                  label: `${t.calibration.status[s.status]} · ${formatDateTime(
                    s.scheduledAt,
                  )}`,
                }))}
                value={sessionId}
                onChange={setSessionId}
                disabled={sessionsQuery.isLoading || sessions.length === 0}
                clearable
                miw={240}
                maw={360}
                flex="1 1 240px"
              />
            )}
          </Group>

          {cycleId && sessions.length === 0 && !sessionsQuery.isLoading && (
            <Text size="sm" c="dimmed">
              {t.calibration.director.noSession}
            </Text>
          )}

          {selectedSession && (
            <Group gap="xs">
              <SessionStatusBadge status={selectedSession.status} />
              {!adjustable && (
                <Text size="xs" c="dimmed">
                  {t.calibration.director.notAdjustableHint}
                </Text>
              )}
            </Group>
          )}
        </Stack>
      </SectionCard>

      {!cycleId ? (
        <SectionCard>
          <Text c="dimmed" size="sm">
            {t.calibration.director.needCycle}
          </Text>
        </SectionCard>
      ) : (
        <>
          <SectionCard>
            <Stack>
              <Text fw={600} size="sm">
                {t.distribution.bars.heading}
              </Text>
              {distributionQuery.isError ? (
                <Text c="red">
                  {t.common.message.loadError}:{' '}
                  {getErrorMessage(distributionQuery.error)}
                </Text>
              ) : distributionQuery.isLoading ? (
                <LoadingState message={t.common.status.loading} />
              ) : distribution ? (
                <DistributionBars
                  current={distribution.currentDistribution}
                  target={distribution.targetDistribution}
                />
              ) : null}
            </Stack>
          </SectionCard>

          <SectionCard>
            <Stack>
              <Text fw={600} size="sm">
                {t.calibration.director.reviewsHeading}
              </Text>
              {reviewsQuery.isError ? (
                <Text c="red">
                  {t.common.message.loadError}:{' '}
                  {getErrorMessage(reviewsQuery.error)}
                </Text>
              ) : reviewsQuery.isLoading ? (
                <LoadingState message={t.common.status.loading} />
              ) : reviews.length === 0 ? (
                <EmptyState title={t.calibration.director.emptyReviews} />
              ) : (
                <ReviewsTable
                  cycleId={cycleId}
                  sessionId={sessionId}
                  adjustable={adjustable}
                  reviews={reviews}
                />
              )}
            </Stack>
          </SectionCard>

          {selectedSession && (
            <SectionCard>
              <AdjustmentLogPanel log={selectedSession.adjustmentLog} />
            </SectionCard>
          )}
        </>
      )}
    </ErrorBoundary>
  );
}

interface ReviewsTableProps {
  cycleId: string;
  sessionId: string | null;
  adjustable: boolean;
  reviews: ReviewResponse[];
}

function ReviewsTable({
  cycleId,
  sessionId,
  adjustable,
  reviews,
}: ReviewsTableProps): React.ReactNode {
  const t = useT();
  return (
    <ScrollArea type="auto" offsetScrollbars>
      <Table striped highlightOnHover verticalSpacing="sm" miw={760}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t.calibration.director.col.employeeId}</Table.Th>
            <Table.Th>{t.calibration.director.col.status}</Table.Th>
            <Table.Th>{t.calibration.director.col.kpiScore}</Table.Th>
            <Table.Th>{t.calibration.director.col.grade}</Table.Th>
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {reviews.map((review) => {
            const grade = effectiveGradeLabel(review.finalGrade, review.kpiScore);
            // 조정 가능 = 세션 선택됨 + 조정 가능 상태 + review 가 CALIBRATION 상태.
            const canAdjustRow =
              adjustable && sessionId != null && review.status === 'CALIBRATION';
            return (
              <Table.Tr key={review.id}>
                <Table.Td>
                  <Text size="sm" ff="monospace">
                    {review.employeeId}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Badge size="sm" variant="light" color="gray">
                    {t.review.status[review.status]}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  <Text size="sm">{formatScore(review.kpiScore)}</Text>
                </Table.Td>
                <Table.Td>
                  <GradeBadge grade={grade} />
                </Table.Td>
                <Table.Td>
                  <Group justify="flex-end" wrap="nowrap">
                    {sessionId != null && (
                      <AdjustGradeMenu
                        cycleId={cycleId}
                        sessionId={sessionId}
                        reviewId={review.id}
                        currentGrade={grade}
                        enabled={canAdjustRow}
                      />
                    )}
                  </Group>
                </Table.Td>
              </Table.Tr>
            );
          })}
        </Table.Tbody>
      </Table>
    </ScrollArea>
  );
}

interface LogPanelProps {
  log: AdjustmentEntry[] | null;
}

function AdjustmentLogPanel({ log }: LogPanelProps): React.ReactNode {
  const t = useT();
  // 역순 (최신 먼저).
  const entries = useMemo(() => (log ? [...log].reverse() : []), [log]);

  return (
    <Stack gap="xs">
      <Text fw={600} size="sm">
        {t.calibration.director.logHeading}
      </Text>
      {entries.length === 0 ? (
        <Text size="sm" c="dimmed">
          {t.calibration.director.logEmpty}
        </Text>
      ) : (
        entries.map((entry, idx) => (
          <PerformanceLogEntryCard
            key={`${entry.reviewId}-${entry.at}-${idx}`}
            primary={
              <>
                <GradeBadge grade={entry.fromGrade} />
                <Text size="sm" c="dimmed">
                  →
                </Text>
                <GradeBadge grade={entry.toGrade} />
                <Text size="sm" ff="monospace" c="dimmed">
                  {entry.employeeId}
                </Text>
              </>
            }
            timestamp={formatDateTime(entry.at)}
          >
            {entry.reason && (
              <PerformancePreWrapText>{entry.reason}</PerformancePreWrapText>
            )}
          </PerformanceLogEntryCard>
        ))
      )}
    </Stack>
  );
}

/** ISO timestamptz → 표시 문자열. null → '—'. */
function formatDateTime(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}

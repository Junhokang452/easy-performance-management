/**
 * ManagerReviewPage (#12) — 매니저 평가 폼 (`/manager/review`).
 *
 * cycle Select → GET /cycles/{id}/reviews 목록(employeeId·status UiBadge·kpiScore) +
 * 개별/일괄 생성 모달 + transition 메뉴(§3 매트릭스 4개) → 행 선택 시 평가 패널:
 *   Tabs 2개 — [KPI 채점] kpi-items + per-item managerScore NumberInput + 가중 합산 프리뷰 + managerComment + 임시저장·제출
 *            [Self ↔ Manager 비교] selfComment ↔ managerComment + per-item autoScore ↔ managerScore
 *
 * 계약 §6 11 endpoint. 점수는 BE 가 SoT — 프리뷰만 §5 산식 클라 계산(표시용).
 * STD-FE 5 정합.
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Group,
  LoadingOverlay,
  Stack,
  Tabs,
  Text,
  Textarea,
} from '@easy/ui-components/mantine';
import { showToast } from '../shared/toast';
import {
  IconCircleCheck,
  IconClock,
  IconListCheck,
  IconPlus,
  IconTargetArrow,
} from '@tabler/icons-react';
import {
  PerformanceChangedTableRow,
  PerformanceMetricGrid,
  PerformanceProgressSummary,
  formatPerformanceRatioPercent,
  formatPerformanceRatioText,
} from '@easy/ui-components/performance';
import {
  UiBadge,
  UiAlert,
  UiButton,
  UiTable,
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';

import {
  formatScore,
  previewKpiScore,
  useReviewKpiItemsQuery,
  useReviewsByCycleQuery,
  useSubmitManagerMutation,
  useUpdateReviewMutation,
  type ReviewItemScoreInput,
  type ReviewKpiItemResponse,
  type ReviewResponse,
} from '../api/reviews';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { CycleSelect } from './kpi/CycleSelect';
import { ReviewStatusBadge } from './review/ReviewStatusBadge';
import { ReviewKpiItemsTable } from './review/ReviewKpiItemsTable';
import { ReviewTransitionMenu } from './review/ReviewTransitionMenu';
import { ReviewCreateModal } from './review/ReviewCreateModal';
import { ReviewComparison } from './review/ReviewComparison';
import { mapReviewErrorToMessage } from './review/errorMapping';

export function ManagerReviewPage(): React.ReactNode {
  const t = useT();
  const [cycleId, setCycleId] = useState<string | null>(null);
  const [selectedReviewId, setSelectedReviewId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const reviewsQuery = useReviewsByCycleQuery(cycleId);
  const reviews = reviewsQuery.data ?? [];

  useEffect(() => {
    setSelectedReviewId(null);
  }, [cycleId]);

  const selectedReview = useMemo(
    () => reviews.find((r) => r.id === selectedReviewId) ?? null,
    [reviews, selectedReviewId],
  );
  const queueStats = useMemo(() => buildReviewQueueStats(reviews), [reviews]);

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.review.manager.title}
        description={t.review.manager.description}
      />
      <SectionCard>
        <Stack>
          <Group justify="space-between" align="end">
            <CycleSelect value={cycleId} onChange={setCycleId} />
            {cycleId && (
              <UiButton
                leftSection={<IconPlus size={16} />}
                onClick={() => setCreateOpen(true)}
              >
                {t.review.manager.create}
              </UiButton>
            )}
          </Group>

          {!cycleId ? (
            <Text c="dimmed" size="sm">
              {t.review.manager.needCycle}
            </Text>
          ) : reviewsQuery.isError ? (
            <Text c="red">
              {t.common.message.loadError}:{' '}
              {getErrorMessage(reviewsQuery.error)}
            </Text>
          ) : reviewsQuery.isLoading ? (
            <LoadingState message={t.common.status.loading} />
          ) : reviews.length === 0 ? (
            <EmptyState
              title={t.review.manager.empty}
              action={{
                label: t.review.manager.create,
                onClick: () => setCreateOpen(true),
              }}
            />
          ) : (
            <Stack>
              <ReviewQueueOverview stats={queueStats} />
              <UiTable striped highlightOnHover>
                <UiTable.Thead>
                  <UiTable.Tr>
                    <UiTable.Th>{t.review.manager.col.employeeId}</UiTable.Th>
                    <UiTable.Th>{t.review.manager.col.status}</UiTable.Th>
                    <UiTable.Th>{t.review.manager.col.kpiScore}</UiTable.Th>
                    <UiTable.Th>{t.review.manager.col.finalScore}</UiTable.Th>
                    <UiTable.Th />
                  </UiTable.Tr>
                </UiTable.Thead>
                <UiTable.Tbody>
                  {reviews.map((review) => (
                    <PerformanceChangedTableRow
                      key={review.id}
                      onClick={() => setSelectedReviewId(review.id)}
                      changed={review.id === selectedReviewId}
                      interactive
                    >
                      <UiTable.Td>
                        <Text size="sm" ff="monospace">
                          {review.employeeId}
                        </Text>
                      </UiTable.Td>
                      <UiTable.Td>
                        <ReviewStatusBadge status={review.status} />
                      </UiTable.Td>
                      <UiTable.Td>
                        <Text size="sm">{formatScore(review.kpiScore)}</Text>
                      </UiTable.Td>
                      <UiTable.Td>
                        <Group gap={6}>
                          <Text size="sm">{formatScore(review.finalScore)}</Text>
                          {review.finalGrade && (
                            <UiBadge size="sm" variant="light" color="blue">
                              {review.finalGrade}
                            </UiBadge>
                          )}
                        </Group>
                      </UiTable.Td>
                      <UiTable.Td>
                        <Group
                          gap={4}
                          justify="flex-end"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <ReviewTransitionMenu review={review} />
                        </Group>
                      </UiTable.Td>
                    </PerformanceChangedTableRow>
                  ))}
                </UiTable.Tbody>
              </UiTable>
            </Stack>
          )}
        </Stack>
      </SectionCard>

      {selectedReview && (
        <SectionCard>
          <ReviewEvaluationPanel
            key={selectedReview.id}
            review={selectedReview}
          />
        </SectionCard>
      )}

      {cycleId && (
        <ReviewCreateModal
          opened={createOpen}
          onClose={() => setCreateOpen(false)}
          cycleId={cycleId}
        />
      )}
    </ErrorBoundary>
  );
}

interface ReviewQueueStats {
  total: number;
  managerPending: number;
  submittedOrLater: number;
  averageKpiScore: number | null;
}

function ReviewQueueOverview({
  stats,
}: {
  stats: ReviewQueueStats;
}): React.ReactNode {
  const t = useT();
  return (
    <Stack gap="sm">
      <PerformanceMetricGrid
        items={[
          {
            label: t.review.manager.workspace.queue,
            value: String(stats.total),
            description: t.review.manager.workspace.queueHint,
            icon: <IconListCheck size={20} />,
            tone: 'brand',
          },
          {
            label: t.review.manager.workspace.managerPending,
            value: String(stats.managerPending),
            description: t.review.manager.workspace.managerPendingHint,
            icon: <IconClock size={20} />,
            tone: stats.managerPending > 0 ? 'yellow' : 'green',
          },
          {
            label: t.review.manager.workspace.submitted,
            value: formatPerformanceRatioPercent(stats.submittedOrLater, stats.total),
            description: formatPerformanceRatioText(stats.submittedOrLater, stats.total),
            icon: <IconCircleCheck size={20} />,
            tone: 'green',
          },
          {
            label: t.review.manager.workspace.avgKpi,
            value: formatScore(stats.averageKpiScore),
            description: t.review.manager.workspace.avgKpiHint,
            icon: <IconTargetArrow size={20} />,
            tone: 'blue',
          },
        ]}
      />
      <PerformanceProgressSummary
        label={t.review.manager.workspace.progress}
        value={stats.submittedOrLater}
        total={stats.total}
      />
    </Stack>
  );
}

interface PanelProps {
  review: ReviewResponse;
}

function ReviewEvaluationPanel({ review }: PanelProps): React.ReactNode {
  const t = useT();
  const itemsQuery = useReviewKpiItemsQuery(review.id);
  const items = useMemo(() => itemsQuery.data ?? [], [itemsQuery.data]);

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Group gap="xs">
          <Text fw={600} ff="monospace">
            {review.employeeId}
          </Text>
          <ReviewStatusBadge status={review.status} />
        </Group>
      </Group>

      <ReviewContextSummary review={review} />

      {itemsQuery.isError ? (
        <Text c="red">
          {t.common.message.loadError}:{' '}
          {mapReviewErrorToMessage(itemsQuery.error, t)}
        </Text>
      ) : itemsQuery.isLoading ? (
        <LoadingState message={t.common.status.loading} />
      ) : (
        <Tabs defaultValue="score">
          <Tabs.List>
            <Tabs.Tab value="score">{t.review.manager.tabScore}</Tabs.Tab>
            <Tabs.Tab value="compare">{t.review.manager.tabCompare}</Tabs.Tab>
          </Tabs.List>

          <Tabs.Panel value="score" pt="md">
            <ManagerScoreTab review={review} items={items} />
          </Tabs.Panel>
          <Tabs.Panel value="compare" pt="md">
            <ReviewComparison review={review} items={items} />
          </Tabs.Panel>
        </Tabs>
      )}
    </Stack>
  );
}

function ReviewContextSummary({
  review,
}: {
  review: ReviewResponse;
}): React.ReactNode {
  const t = useT();
  return (
    <PerformanceMetricGrid
      items={[
        {
          label: t.review.manager.workspace.reviewee,
          value: review.employeeId,
          description: t.review.manager.workspace.revieweeHint,
          icon: <IconListCheck size={20} />,
          tone: 'brand',
        },
        {
          label: t.review.manager.col.kpiScore,
          value: formatScore(review.kpiScore),
          description: t.review.manager.workspace.serverScoreHint,
          icon: <IconTargetArrow size={20} />,
          tone: 'blue',
        },
        {
          label: t.review.manager.col.finalScore,
          value: formatScore(review.finalScore),
          description: review.finalGrade ?? t.review.manager.workspace.noGrade,
          icon: <IconCircleCheck size={20} />,
          tone: review.finalGrade ? 'green' : 'gray',
        },
        {
          label: t.review.manager.workspace.finalizedAt,
          value: review.finalizedAt ? t.review.manager.workspace.finalized : '-',
          description: review.finalizedAt ?? t.review.manager.workspace.notFinalized,
          icon: <IconClock size={20} />,
          tone: review.finalizedAt ? 'green' : 'gray',
        },
      ]}
    />
  );
}

interface ScoreTabProps {
  review: ReviewResponse;
  items: ReviewKpiItemResponse[];
}

function ManagerScoreTab({ review, items }: ScoreTabProps): React.ReactNode {
  const t = useT();
  const updateMut = useUpdateReviewMutation(review.id);
  const submitMut = useSubmitManagerMutation(review.id);

  const editable = review.status === 'MANAGER_PENDING';

  const [overrides, setOverrides] = useState<Record<string, number | string>>(
    {},
  );
  const [managerComment, setManagerComment] = useState(
    review.managerComment ?? '',
  );

  useEffect(() => {
    const init: Record<string, number | string> = {};
    for (const item of items) {
      init[item.assignmentId] = item.managerScore ?? '';
    }
    setOverrides(init);
    setManagerComment(review.managerComment ?? '');
  }, [review.id, review.managerComment, items]);

  const handleScoreChange = (
    assignmentId: string,
    value: number | string,
  ): void => {
    setOverrides((prev) => ({ ...prev, [assignmentId]: value }));
  };

  const preview = useMemo(() => {
    const numeric: Record<string, number | null> = {};
    for (const [id, v] of Object.entries(overrides)) {
      numeric[id] = v === '' || v == null ? null : Number(v);
    }
    return previewKpiScore(items, numeric);
  }, [items, overrides]);

  const buildItemScores = (): ReviewItemScoreInput[] =>
    items.map((item) => {
      const v = overrides[item.assignmentId];
      const managerScore = v === '' || v == null ? null : Number(v);
      return { assignmentId: item.assignmentId, managerScore };
    });

  const handleSave = (): void => {
    updateMut.mutate(
      {
        managerComment: managerComment.trim() || null,
        itemScores: buildItemScores(),
      },
      {
        onSuccess: () => {
          showToast({
            tone: 'success',
            message: t.common.message.updated,
          });
        },
        onError: (err) => {
          showToast({
            tone: 'danger',
            message: mapReviewErrorToMessage(err, t),
          });
        },
      },
    );
  };

  const handleSubmit = (): void => {
    submitMut.mutate(
      {
        managerComment: managerComment.trim() || null,
        itemScores: buildItemScores(),
      },
      {
        onSuccess: () => {
          showToast({
            tone: 'success',
            message: t.review.manager.submitted,
          });
        },
        onError: (err) => {
          showToast({
            tone: 'danger',
            message: mapReviewErrorToMessage(err, t),
          });
        },
      },
    );
  };

  const pending = updateMut.isPending || submitMut.isPending;

  return (
    <Stack pos="relative">
      <LoadingOverlay visible={pending} />

      {!editable && (
        <UiAlert color="gray" variant="light">
          {t.review.manager.notEditableHint}
        </UiAlert>
      )}

      {editable ? (
        <ReviewKpiItemsTable
          mode="score-input"
          items={items}
          overrides={overrides}
          onChange={handleScoreChange}
          disabled={pending}
        />
      ) : (
        <ReviewKpiItemsTable mode="readOnly" items={items} />
      )}

      <ScoreInputSummary preview={preview} items={items} overrides={overrides} />

      <Textarea
        label={t.review.manager.managerComment}
        placeholder={t.review.manager.managerCommentPlaceholder}
        value={managerComment}
        onChange={(e) => setManagerComment(e.currentTarget.value)}
        autosize
        minRows={4}
        maxRows={12}
        readOnly={!editable}
        mt="xs"
      />

      {editable && (
        <Group justify="flex-end">
          <UiButton
            variant="default"
            onClick={handleSave}
            loading={updateMut.isPending}
          >
            {t.review.manager.saveDraft}
          </UiButton>
          <UiButton onClick={handleSubmit} loading={submitMut.isPending}>
            {t.review.manager.submit}
          </UiButton>
        </Group>
      )}
    </Stack>
  );
}

function ScoreInputSummary({
  preview,
  items,
  overrides,
}: {
  preview: number | null;
  items: ReviewKpiItemResponse[];
  overrides: Record<string, number | string>;
}): React.ReactNode {
  const t = useT();
  const scored = items.filter((item) => {
    const value = overrides[item.assignmentId];
    return value !== '' && value != null;
  }).length;

  return (
    <Stack gap="sm">
      <PerformanceMetricGrid
        columns={{ base: 1, sm: 2 }}
        items={[
          {
            label: t.review.manager.previewKpiScore,
            value: formatScore(preview),
            description: t.review.manager.previewHint,
            icon: <IconTargetArrow size={20} />,
            tone: 'blue',
          },
          {
            label: t.review.manager.workspace.scoredItems,
            value: formatPerformanceRatioText(scored, items.length),
            description: t.review.manager.workspace.scoredItemsHint,
            icon: <IconListCheck size={20} />,
            tone: items.length > 0 && scored === items.length ? 'green' : 'yellow',
          },
        ]}
      />
      <PerformanceProgressSummary
        label={t.review.manager.workspace.scoringProgress}
        value={scored}
        total={items.length}
      />
    </Stack>
  );
}

function buildReviewQueueStats(reviews: ReviewResponse[]): ReviewQueueStats {
  const submittedStatuses = new Set([
    'MANAGER_SUBMITTED',
    'CALIBRATION',
    'FINALIZED',
    'APPEAL_REQUESTED',
    'APPEAL_RESOLVED',
    'ARCHIVED',
  ]);
  const kpiScores = reviews
    .map((review) => review.kpiScore)
    .filter((score): score is number => score != null);

  return {
    total: reviews.length,
    managerPending: reviews.filter((review) => review.status === 'MANAGER_PENDING')
      .length,
    submittedOrLater: reviews.filter((review) =>
      submittedStatuses.has(review.status),
    ).length,
    averageKpiScore:
      kpiScores.length > 0
        ? kpiScores.reduce((sum, score) => sum + score, 0) / kpiScores.length
        : null,
  };
}

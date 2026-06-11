/**
 * ManagerReviewPage (#12) — 매니저 평가 폼 (`/manager/review`).
 *
 * cycle Select → GET /cycles/{id}/reviews 목록(employeeId·status Badge·kpiScore) +
 * 개별/일괄 생성 모달 + transition 메뉴(§3 매트릭스 4개) → 행 선택 시 평가 패널:
 *   Tabs 2개 — [KPI 채점] kpi-items + per-item managerScore NumberInput + 가중 합산 프리뷰 + managerComment + 임시저장·제출
 *            [Self ↔ Manager 비교] selfComment ↔ managerComment + per-item autoScore ↔ managerScore
 *
 * 계약 §6 11 endpoint. 점수는 BE 가 SoT — 프리뷰만 §5 산식 클라 계산(표시용).
 * STD-FE 5 정합.
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Group,
  LoadingOverlay,
  Stack,
  Table,
  Tabs,
  Text,
  Textarea,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPlus } from '@tabler/icons-react';
import {
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

  // cycle 변경 시 선택 초기화.
  useEffect(() => {
    setSelectedReviewId(null);
  }, [cycleId]);

  const selectedReview = useMemo(
    () => reviews.find((r) => r.id === selectedReviewId) ?? null,
    [reviews, selectedReviewId],
  );

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
              <Button
                leftSection={<IconPlus size={16} />}
                onClick={() => setCreateOpen(true)}
              >
                {t.review.manager.create}
              </Button>
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
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t.review.manager.col.employeeId}</Table.Th>
                  <Table.Th>{t.review.manager.col.status}</Table.Th>
                  <Table.Th>{t.review.manager.col.kpiScore}</Table.Th>
                  <Table.Th>{t.review.manager.col.finalScore}</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {reviews.map((review) => (
                  <Table.Tr
                    key={review.id}
                    onClick={() => setSelectedReviewId(review.id)}
                    style={{
                      cursor: 'pointer',
                      backgroundColor:
                        review.id === selectedReviewId
                          ? 'var(--mantine-color-blue-light)'
                          : undefined,
                    }}
                  >
                    <Table.Td>
                      <Text size="sm" ff="monospace">
                        {review.employeeId}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <ReviewStatusBadge status={review.status} />
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">{formatScore(review.kpiScore)}</Text>
                    </Table.Td>
                    <Table.Td>
                      <Group gap={6}>
                        <Text size="sm">{formatScore(review.finalScore)}</Text>
                        {review.finalGrade && (
                          <Badge size="sm" variant="light" color="blue">
                            {review.finalGrade}
                          </Badge>
                        )}
                      </Group>
                    </Table.Td>
                    <Table.Td>
                      <Group
                        gap={4}
                        justify="flex-end"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <ReviewTransitionMenu review={review} />
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
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

interface ScoreTabProps {
  review: ReviewResponse;
  items: ReviewKpiItemResponse[];
}

function ManagerScoreTab({ review, items }: ScoreTabProps): React.ReactNode {
  const t = useT();
  const updateMut = useUpdateReviewMutation(review.id);
  const submitMut = useSubmitManagerMutation(review.id);

  // MANAGER_PENDING 에서만 채점·제출 가능 (계약 §3/§6).
  const editable = review.status === 'MANAGER_PENDING';

  // per-item 매니저 점수 입력 (assignmentId → number|''). 저장된 managerScore 로 초기화.
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

  // 가중 합산 프리뷰 — 표시 전용 (§5 산식, BE kpiScore 가 SoT).
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
          notifications.show({
            color: 'green',
            message: t.common.message.updated,
          });
        },
        onError: (err) => {
          notifications.show({
            color: 'red',
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
          notifications.show({
            color: 'green',
            message: t.review.manager.submitted,
          });
        },
        onError: (err) => {
          notifications.show({
            color: 'red',
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
        <Alert color="gray" variant="light">
          {t.review.manager.notEditableHint}
        </Alert>
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

      <Card withBorder padding="sm" mt="xs">
        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {t.review.manager.previewKpiScore}
          </Text>
          <Text size="lg" fw={700}>
            {formatScore(preview)}
          </Text>
        </Group>
        <Text size="xs" c="dimmed" mt={4}>
          {t.review.manager.previewHint}
        </Text>
      </Card>

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
          <Button
            variant="default"
            onClick={handleSave}
            loading={updateMut.isPending}
          >
            {t.review.manager.saveDraft}
          </Button>
          <Button onClick={handleSubmit} loading={submitMut.isPending}>
            {t.review.manager.submit}
          </Button>
        </Group>
      )}
    </Stack>
  );
}

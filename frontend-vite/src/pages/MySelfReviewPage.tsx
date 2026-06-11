/**
 * MySelfReviewPage (#4) — 자기평가 폼 (`/my/self-review`).
 *
 * cycle Select + employeeId 입력(MyKpiPage 패턴 — principal 주입은 P0-S6 이후) →
 * GET /reviews/my → 상태 Badge + KPI 자체 점검 표(read-only) + selfComment Textarea +
 * 임시저장(PATCH, SELF_PENDING 한정) + 제출(submit-self, 확인 모달) → SELF_SUBMITTED 이후 read-only 잠금.
 *
 * review 없으면(404 E9804447) 빈 상태 안내.
 *
 * 계약 §6 /reviews/my + /reviews/{id}/kpi-items + PATCH + submit-self.
 * STD-FE 5 정합.
 */
import { useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  Stack,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconLock, IconSearch } from '@tabler/icons-react';
import {
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';

import {
  useMyReviewQuery,
  useReviewKpiItemsQuery,
  useSubmitSelfMutation,
  useUpdateReviewMutation,
  type ReviewResponse,
} from '../api/reviews';
import { useT } from '../i18n';
import { CycleSelect } from './kpi/CycleSelect';
import { ReviewStatusBadge } from './review/ReviewStatusBadge';
import { ReviewKpiItemsTable } from './review/ReviewKpiItemsTable';
import { isReviewNotFound, mapReviewErrorToMessage } from './review/errorMapping';

export function MySelfReviewPage(): React.ReactNode {
  const t = useT();

  // 입력(폼)과 조회(applied) 상태 분리 — '조회' 클릭 시 확정 (MyKpiPage 패턴).
  const [cycleId, setCycleId] = useState<string | null>(null);
  const [employeeIdInput, setEmployeeIdInput] = useState('');
  const [applied, setApplied] = useState<{
    cycleId: string;
    employeeId: string;
  } | null>(null);

  const { data, isLoading, isError, error, isFetching } = useMyReviewQuery(
    applied?.cycleId,
    applied?.employeeId,
  );

  const canLoad = Boolean(cycleId) && employeeIdInput.trim().length > 0;

  const handleLoad = (): void => {
    if (!cycleId || !employeeIdInput.trim()) return;
    setApplied({ cycleId, employeeId: employeeIdInput.trim() });
  };

  const notFound = isError && isReviewNotFound(error);

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.review.self.title}
        description={t.review.self.description}
      />
      <SectionCard>
        <Stack>
          <Group align="end" gap="md">
            <CycleSelect value={cycleId} onChange={setCycleId} />
            <TextInput
              label={t.review.self.employeeId}
              placeholder={t.review.self.employeeIdPlaceholder}
              value={employeeIdInput}
              onChange={(e) => setEmployeeIdInput(e.currentTarget.value)}
              w={320}
            />
            <Button
              leftSection={<IconSearch size={16} />}
              onClick={handleLoad}
              disabled={!canLoad}
              loading={isFetching && Boolean(applied)}
            >
              {t.review.self.load}
            </Button>
          </Group>

          {!applied ? (
            <Text c="dimmed" size="sm">
              {t.review.self.needInput}
            </Text>
          ) : notFound ? (
            <EmptyState
              title={t.review.self.empty}
              description={t.review.self.emptyHint}
            />
          ) : isError ? (
            <Text c="red">
              {t.common.message.loadError}:{' '}
              {mapReviewErrorToMessage(error, t)}
            </Text>
          ) : isLoading || !data ? (
            <LoadingState message={t.common.status.loading} />
          ) : (
            <SelfReviewForm review={data} />
          )}
        </Stack>
      </SectionCard>
    </ErrorBoundary>
  );
}

interface SelfReviewFormProps {
  review: ReviewResponse;
}

function SelfReviewForm({ review }: SelfReviewFormProps): React.ReactNode {
  const t = useT();
  const itemsQuery = useReviewKpiItemsQuery(review.id);
  const updateMut = useUpdateReviewMutation(review.id);
  const submitMut = useSubmitSelfMutation(review.id);

  const [selfComment, setSelfComment] = useState(review.selfComment ?? '');
  const [confirmSubmit, setConfirmSubmit] = useState(false);

  // review 가 바뀌면(조회 갱신/제출 후) 코멘트 동기화.
  useEffect(() => {
    setSelfComment(review.selfComment ?? '');
  }, [review.id, review.selfComment]);

  // SELF_PENDING 에서만 자기평가 작성·제출 가능 (계약 §3/§6).
  const editable = review.status === 'SELF_PENDING';
  const locked = !editable;

  const items = itemsQuery.data ?? [];

  const handleSave = (): void => {
    updateMut.mutate(
      { selfComment: selfComment.trim() || null },
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
      { selfComment: selfComment.trim() || null },
      {
        onSuccess: () => {
          setConfirmSubmit(false);
          notifications.show({
            color: 'green',
            message: t.review.self.submitted,
          });
        },
        onError: (err) => {
          setConfirmSubmit(false);
          notifications.show({
            color: 'red',
            message: mapReviewErrorToMessage(err, t),
          });
        },
      },
    );
  };

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Group gap="xs">
          <Text fw={600}>{t.review.self.reviewTitle}</Text>
          <ReviewStatusBadge status={review.status} />
        </Group>
        {review.kpiScore != null && (
          <Badge color="blue" variant="light" size="lg">
            {t.review.field.kpiScore}: {review.kpiScore.toFixed(2)}
          </Badge>
        )}
      </Group>

      {locked && (
        <Alert color="gray" variant="light" icon={<IconLock size={16} />}>
          {t.review.self.lockedHint}
        </Alert>
      )}

      <Text size="sm" fw={500} mt="xs">
        {t.review.self.kpiSection}
      </Text>
      {itemsQuery.isError ? (
        <Text c="red">
          {t.common.message.loadError}: {mapReviewErrorToMessage(itemsQuery.error, t)}
        </Text>
      ) : itemsQuery.isLoading ? (
        <LoadingState message={t.common.status.loading} />
      ) : (
        <ReviewKpiItemsTable mode="readOnly" items={items} />
      )}

      <Textarea
        label={t.review.self.selfComment}
        placeholder={t.review.self.selfCommentPlaceholder}
        value={selfComment}
        onChange={(e) => setSelfComment(e.currentTarget.value)}
        autosize
        minRows={4}
        maxRows={12}
        readOnly={locked}
        mt="xs"
      />

      {editable && (
        <Group justify="flex-end">
          <Button
            variant="default"
            onClick={handleSave}
            loading={updateMut.isPending}
          >
            {t.review.self.saveDraft}
          </Button>
          <Button
            onClick={() => setConfirmSubmit(true)}
            loading={submitMut.isPending}
          >
            {t.review.self.submit}
          </Button>
        </Group>
      )}

      <Modal
        opened={confirmSubmit}
        onClose={() => setConfirmSubmit(false)}
        title={t.review.self.submit}
        centered
        size="sm"
      >
        <Stack>
          <Text size="sm">{t.review.self.confirmSubmit}</Text>
          <Group justify="flex-end">
            <Button
              variant="default"
              onClick={() => setConfirmSubmit(false)}
              disabled={submitMut.isPending}
            >
              {t.common.action.cancel}
            </Button>
            <Button onClick={handleSubmit} loading={submitMut.isPending}>
              {t.review.self.submit}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}

/**
 * ReviewTransitionMenu — review 상태 전이 메뉴 (CyclesPage 상태 전이 메뉴 패턴).
 *
 * 계약 §3: `POST /reviews/{id}/transition` 허용 전이 4개만 (DRAFT→SELF_PENDING /
 * SELF_SUBMITTED→MANAGER_PENDING / MANAGER_SUBMITTED→CALIBRATION / CALIBRATION→FINALIZED).
 * submit-self / submit-manager 는 전용 엔드포인트라 여기 미노출.
 *
 * 전이 실패(잘못된 cycle 단계 등)는 ApiError 코드 → mapReviewErrorToMessage 로 표면.
 */
import { Menu } from '@easy/ui-components/mantine';
import { showToast } from '../../shared/toast';
import { IconChevronDown } from '@tabler/icons-react';

import {
  getAllowedReviewTransitions,
  useTransitionReviewMutation,
  type ReviewResponse,
  type ReviewStatus,
} from '../../api/reviews';
import { useT } from '../../i18n';
import { mapReviewErrorToMessage } from './errorMapping';
import { UiButton } from '@easy/ui-components';

interface Props {
  review: ReviewResponse;
  /** transition POST 의 actorEmployeeId (FINALIZED 시 finalizedBy — principal 주입은 P0-S6 이후). */
  actorEmployeeId?: string | null;
}

export function ReviewTransitionMenu({
  review,
  actorEmployeeId,
}: Props): React.ReactNode {
  const t = useT();
  const transitionMut = useTransitionReviewMutation(review.id);
  const nextStatuses = getAllowedReviewTransitions(review.status);

  if (nextStatuses.length === 0) return null;

  const handleTransition = (next: ReviewStatus): void => {
    transitionMut.mutate(
      { targetStatus: next, actorEmployeeId: actorEmployeeId ?? null },
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

  return (
    <Menu shadow="md" position="bottom-end" withinPortal>
      <Menu.Target>
        <UiButton
          size="xs"
          variant="light"
          rightSection={<IconChevronDown size={14} />}
          loading={transitionMut.isPending}
        >
          {t.review.action.transition}
        </UiButton>
      </Menu.Target>
      <Menu.Dropdown>
        {nextStatuses.map((s) => (
          <Menu.Item key={s} onClick={() => handleTransition(s)}>
            {t.review.status[s]}
          </Menu.Item>
        ))}
      </Menu.Dropdown>
    </Menu>
  );
}

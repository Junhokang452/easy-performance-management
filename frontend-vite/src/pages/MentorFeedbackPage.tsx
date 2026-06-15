/**
 * MentorFeedbackPage — 멘토 피드백 페이지 (단계 4 EC-FE 진입, 매니저-팀원 1:1).
 *
 * BE 정합: GET/POST/PUT/DELETE `/api/internal/mentor-feedbacks` (Page envelope).
 * Category: GROWTH / RECOGNITION / COACHING / CONVERSATION.
 */
import { Stack, Text } from '@easy/ui-components/mantine';
import {
  UiBadge,
  PageHeader,
  SectionCard,
  EmptyState,
  LoadingState,
  ErrorBoundary,
} from '@easy/ui-components';
import {
  PerformancePreWrapText,
  PerformanceRecordCard,
} from '@easy/ui-components/performance';

import { useMentorFeedbackList, type FeedbackCategory } from '../api/mentorFeedback';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';

function categoryColor(category: FeedbackCategory): string {
  switch (category) {
    case 'GROWTH':
      return 'blue';
    case 'RECOGNITION':
      return 'green';
    case 'COACHING':
      return 'orange';
    case 'CONVERSATION':
      return 'grape';
    default:
      return 'gray';
  }
}

export function MentorFeedbackPage(): React.ReactNode {
  const t = useT();
  const { data, isLoading, isError, error } = useMentorFeedbackList();
  const rows = data?.content ?? [];

  const categoryLabel = (c: FeedbackCategory): string => {
    switch (c) {
      case 'GROWTH':
        return t.domain.mentorFeedback.categoryGrowth;
      case 'RECOGNITION':
        return t.domain.mentorFeedback.categoryRecognition;
      case 'COACHING':
        return t.domain.mentorFeedback.categoryCoaching;
      case 'CONVERSATION':
        return t.domain.mentorFeedback.categoryConversation;
      default:
        return c;
    }
  };

  return (
    <ErrorBoundary>
      <PageHeader
        title={t.domain.mentorFeedback.title}
        description={t.domain.mentorFeedback.description}
      />
      <SectionCard>
        {isError ? (
          <Text c="red">
            {t.common.message.loadError}: {getErrorMessage(error)}
          </Text>
        ) : isLoading ? (
          <LoadingState message={t.common.status.loading} />
        ) : rows.length === 0 ? (
          <EmptyState
            title={t.domain.mentorFeedback.empty}
            description={t.domain.mentorFeedback.emptyDescription}
          />
        ) : (
          <Stack>
            {rows.map((fb) => (
              <PerformanceRecordCard
                key={fb.id}
                mobileSize="comfortable"
                meta={
                  <>
                    <Text size="sm" fw={600}>
                      {fb.feedbackDate}
                    </Text>
                    <UiBadge color={categoryColor(fb.category)} variant="light">
                      {categoryLabel(fb.category)}
                    </UiBadge>
                  </>
                }
                badges={
                  fb.acknowledged ? (
                    <UiBadge color="green" variant="outline">
                      {t.domain.mentorFeedback.acknowledged}
                    </UiBadge>
                  ) : null
                }
              >
                <PerformancePreWrapText>{fb.content}</PerformancePreWrapText>
              </PerformanceRecordCard>
            ))}
          </Stack>
        )}
      </SectionCard>
    </ErrorBoundary>
  );
}

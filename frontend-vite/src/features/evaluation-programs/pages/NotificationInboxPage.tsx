import { PageHeader, SectionCard, UiBadge, UiButton, UiGroup, UiStack, UiText } from '@easy/ui-components';

import { getErrorMessage } from '../../../api/error';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import { useNotificationsQuery, useReadNotificationMutation, type NotificationStatus } from '../api/programs';
import { QueryState } from '../components/QueryState';

function statusLabel(t: ReturnType<typeof useT>, status: NotificationStatus): string {
  if (status === 'SENT') return t.program.notifications.sent;
  if (status === 'FAILED') return t.program.notifications.failed;
  if (status === 'CONFIG_REQUIRED') return t.program.notifications.configurationRequired;
  if (status === 'READY' || status === 'SENDING') return t.program.notifications.queued;
  return t.program.notifications.markRead;
}

export function NotificationInboxPage(): React.ReactNode {
  const t = useT(); const query = useNotificationsQuery(); const read = useReadNotificationMutation(); const items = query.data ?? [];
  const markRead = async (id: string): Promise<void> => { try { await read.mutateAsync(id); } catch (error) { showToast({ tone: 'danger', message: getErrorMessage(error) }); } };
  return <UiStack gap="md"><PageHeader title={t.program.notifications.inbox} description={t.program.notifications.title} /><QueryState pending={query.isPending} error={query.error} empty={!items.length} loadingLabel={t.common.status.loading} errorLabel={t.program.common.loadError} emptyTitle={t.program.notifications.empty}>{items.map((item) => <SectionCard key={item.id} title={item.subject} description={item.sentAt ?? undefined} actions={<UiBadge variant={item.readAt ? 'outline' : 'light'}>{statusLabel(t, item.status)}</UiBadge>}><UiStack gap="sm"><UiText>{item.body}</UiText>{!item.readAt ? <UiGroup justify="flex-end"><UiButton size="xs" variant="light" loading={read.isPending} onClick={() => void markRead(item.id)}>{t.program.notifications.markRead}</UiButton></UiGroup> : null}</UiStack></SectionCard>)}</QueryState></UiStack>;
}

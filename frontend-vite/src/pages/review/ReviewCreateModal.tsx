/**
 * ReviewCreateModal — review 생성 모달 (개별 + 일괄).
 *
 * 계약 §6:
 * - 개별: POST /cycles/{cycleId}/reviews  body {employeeId} → 201 ReviewResponse(DRAFT)
 * - 일괄: POST /cycles/{cycleId}/reviews/bulk body {employeeIds: UUID[]}
 *         → 201 {createdCount, skippedCount, created[]} (기존 (cycle×employee) 존재 시 skip — 에러 아님)
 *
 * SegmentedControl 로 개별/일괄 모드 전환. 일괄은 줄바꿈·콤마 구분 사원 ID 입력.
 */
import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Group,
  Modal,
  SegmentedControl,
  Stack,
  Text,
  Textarea,
  TextInput,
} from '@easy/ui-components/mantine';
import { notifications } from '@mantine/notifications';

import {
  useBulkCreateReviewMutation,
  useCreateReviewMutation,
} from '../../api/reviews';
import { useT } from '../../i18n';
import { mapReviewErrorToMessage } from './errorMapping';

interface Props {
  opened: boolean;
  onClose: () => void;
  cycleId: string;
}

type Mode = 'single' | 'bulk';

export function ReviewCreateModal({
  opened,
  onClose,
  cycleId,
}: Props): React.ReactNode {
  const t = useT();
  const createMut = useCreateReviewMutation(cycleId);
  const bulkMut = useBulkCreateReviewMutation(cycleId);

  const [mode, setMode] = useState<Mode>('single');
  const [employeeId, setEmployeeId] = useState('');
  const [employeeIdsText, setEmployeeIdsText] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (opened) {
      setMode('single');
      setEmployeeId('');
      setEmployeeIdsText('');
      setErrorMessage(null);
    }
  }, [opened]);

  const pending = createMut.isPending || bulkMut.isPending;

  const handleClose = (): void => {
    if (pending) return;
    onClose();
  };

  const parseIds = (text: string): string[] =>
    text
      .split(/[\s,]+/)
      .map((s) => s.trim())
      .filter((s) => s.length > 0);

  const handleSubmit = (): void => {
    setErrorMessage(null);
    const onError = (err: unknown): void => {
      setErrorMessage(mapReviewErrorToMessage(err, t));
    };

    if (mode === 'single') {
      const id = employeeId.trim();
      if (!id) {
        setErrorMessage(t.review.create.needEmployeeId);
        return;
      }
      createMut.mutate(
        { employeeId: id },
        {
          onSuccess: () => {
            notifications.show({
              color: 'green',
              message: t.common.message.created,
            });
            onClose();
          },
          onError,
        },
      );
    } else {
      const ids = parseIds(employeeIdsText);
      if (ids.length === 0) {
        setErrorMessage(t.review.create.needEmployeeIds);
        return;
      }
      bulkMut.mutate(
        { employeeIds: ids },
        {
          onSuccess: (res) => {
            notifications.show({
              color: 'green',
              message: t.review.create.bulkResult
                .replace('{created}', String(res.createdCount))
                .replace('{skipped}', String(res.skippedCount)),
            });
            onClose();
          },
          onError,
        },
      );
    }
  };

  return (
    <Modal
      opened={opened}
      onClose={handleClose}
      title={t.review.create.title}
      size="md"
      centered
    >
      <Stack>
        <SegmentedControl
          value={mode}
          onChange={(v) => setMode(v as Mode)}
          data={[
            { value: 'single', label: t.review.create.modeSingle },
            { value: 'bulk', label: t.review.create.modeBulk },
          ]}
          fullWidth
        />

        {mode === 'single' ? (
          <TextInput
            label={t.review.create.employeeId}
            placeholder={t.review.create.employeeIdPlaceholder}
            value={employeeId}
            onChange={(e) => setEmployeeId(e.currentTarget.value)}
            withAsterisk
          />
        ) : (
          <Textarea
            label={t.review.create.employeeIds}
            description={t.review.create.employeeIdsHint}
            placeholder={t.review.create.employeeIdsPlaceholder}
            value={employeeIdsText}
            onChange={(e) => setEmployeeIdsText(e.currentTarget.value)}
            autosize
            minRows={4}
            maxRows={10}
          />
        )}

        {errorMessage && (
          <Alert color="red" variant="light">
            {errorMessage}
          </Alert>
        )}

        <Text size="xs" c="dimmed">
          {t.review.create.note}
        </Text>

        <Group justify="flex-end" mt="sm">
          <Button variant="default" onClick={handleClose} disabled={pending}>
            {t.common.action.cancel}
          </Button>
          <Button onClick={handleSubmit} loading={pending}>
            {t.common.action.create}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

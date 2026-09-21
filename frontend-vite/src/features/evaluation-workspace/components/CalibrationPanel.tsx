import { useEffect, useMemo, useState } from 'react';
import { Group, NumberInput, Select, SimpleGrid, Stack, Text, Textarea } from '@easy/ui-components/mantine';
import { EmptyState, LoadingState, SectionCard, UiAlert, UiBadge, UiButton } from '@easy/ui-components';

import {
  useApplyCalibrationMutation,
  useCalibrationAdjustmentMutation,
  useCalibrationTasksQuery,
  useConfirmCalibrationMutation,
  useCreateCalibrationMutation,
} from '../../../api/evaluationWorkspace';
import { useT } from '../../../i18n';
import { showToast } from '../../../shared/toast';
import { employeeLabel, statusLabel } from '../workspaceLabels';
import { workspaceErrorMessage } from '../workspaceError';
import { distributionTotal, isValidDistribution } from '../workflowRules';

const GRADES = ['S', 'A', 'B', 'C', 'D'] as const;

export function CalibrationPanel({ cycleId }: { cycleId: string }): React.ReactNode {
  const t = useT();
  const tasksQuery = useCalibrationTasksQuery(cycleId, true);
  const createMutation = useCreateCalibrationMutation(cycleId);
  const applyMutation = useApplyCalibrationMutation(cycleId);
  const adjustMutation = useCalibrationAdjustmentMutation();
  const confirmMutation = useConfirmCalibrationMutation(cycleId);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [reviewId, setReviewId] = useState<string | null>(null);
  const [grade, setGrade] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [distribution, setDistribution] = useState<Record<string, number>>({});

  const data = tasksQuery.data;
  useEffect(() => {
    if (data && !sessionId) setSessionId(data.sessions[0]?.id ?? null);
    if (data && Object.keys(distribution).length === 0) {
      setDistribution(Object.fromEntries(GRADES.map((item) => [item, data.targetDistribution[item] ?? 0])));
    }
  }, [data, distribution, sessionId]);

  const targetTotal = useMemo(
    () => distributionTotal(distribution),
    [distribution],
  );
  const reportError = (error: unknown): void =>
    showToast({ tone: 'danger', message: workspaceErrorMessage(t, error) });

  if (tasksQuery.isLoading) return <LoadingState message={t.workspace.copy.loadingCalibration} />;
  if (tasksQuery.isError || !data) return <UiAlert color="red">{workspaceErrorMessage(t, tasksQuery.error)}</UiAlert>;

  const selectedSession = data.sessions.find((session) => session.id === sessionId) ?? null;
  return (
    <SectionCard>
      <Stack>
        <Group justify="space-between">
          <Stack gap={2}>
            <Text fw={600}>{t.workspace.calibration}</Text>
            <Text size="sm" c="dimmed">{t.workspace.copy.calibrationDescription}</Text>
          </Stack>
          <UiBadge color={isValidDistribution(distribution) ? 'green' : 'yellow'} variant="light">
            {t.workspace.copy.distributionTotal} {Math.round(targetTotal * 100)}%
          </UiBadge>
        </Group>

        {data.sessions.length === 0 ? (
          <EmptyState
            title={t.workspace.copy.noCalibrationSession}
            description={t.workspace.copy.noCalibrationSessionHint}
            action={{
              label: t.workspace.copy.createCalibrationSession,
              onClick: () => createMutation.mutate(undefined, {
                onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.calibrationSessionCreated }),
                onError: reportError,
              }),
            }}
          />
        ) : (
          <Select
            label={t.workspace.copy.calibrationSession}
            data={data.sessions.map((session) => ({
              value: session.id,
              label: `${statusLabel(t, session.status)} · ${session.participantCount}${t.workspace.copy.peopleSuffix}`,
            }))}
            value={sessionId}
            onChange={setSessionId}
          />
        )}

        <SimpleGrid cols={{ base: 1, sm: 5 }}>
          {GRADES.map((item) => (
            <NumberInput
              key={item}
              label={`${item} (%)`}
              min={0}
              max={100}
              decimalScale={1}
              value={(distribution[item] ?? 0) * 100}
              onChange={(value) => setDistribution((previous) => ({
                ...previous,
                [item]: (Number(value) || 0) / 100,
              }))}
            />
          ))}
        </SimpleGrid>
        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {t.workspace.copy.currentDistribution}:{' '}
            {GRADES.map((item) => `${item} ${data.currentDistribution[item] ?? 0}`).join(' · ')}
          </Text>
          <UiButton
            variant="light"
            disabled={!isValidDistribution(distribution) || data.rows.length === 0}
            loading={applyMutation.isPending}
            onClick={() => applyMutation.mutate(distribution, {
              onSuccess: () => showToast({ tone: 'success', message: t.workspace.copy.distributionApplied }),
              onError: reportError,
            })}
          >
            {t.workspace.copy.applyDistribution}
          </UiButton>
        </Group>

        {data.rows.length === 0 ? (
          <Text size="sm" c="dimmed">{t.workspace.copy.noCalibrationRows}</Text>
        ) : (
          <>
            <Select
              label={t.workspace.copy.reviewSubject}
              data={data.rows.map((row) => ({
                value: row.reviewId,
                label: `${employeeLabel(row.employee)} · ${row.proposedGrade ?? row.currentGrade ?? t.workspace.copy.notCalculated}`,
              }))}
              value={reviewId}
              onChange={setReviewId}
            />
            <Group align="end">
              <Select
                label={t.workspace.copy.adjustedGrade}
                data={[...GRADES]}
                value={grade}
                onChange={setGrade}
                w={160}
              />
              <Textarea
                label={t.workspace.copy.adjustmentReason}
                value={reason}
                onChange={(event) => setReason(event.currentTarget.value)}
                flex={1}
              />
              <UiButton
                disabled={!sessionId || !reviewId || !grade || !reason.trim()}
                loading={adjustMutation.isPending}
                onClick={() => adjustMutation.mutate(
                  {
                    sessionId: sessionId as string,
                    reviewId: reviewId as string,
                    toGrade: grade as string,
                    reason: reason.trim(),
                  },
                  {
                    onSuccess: () => {
                      setReason('');
                      showToast({ tone: 'success', message: t.workspace.copy.adjustmentSaved });
                    },
                    onError: reportError,
                  },
                )}
              >
                {t.workspace.copy.saveAdjustment}
              </UiButton>
            </Group>
          </>
        )}

        {selectedSession && (
          <Group justify="flex-end">
            <UiButton
              disabled={data.rows.length === 0 || selectedSession.status === 'CONFIRMED'}
              loading={confirmMutation.isPending}
              onClick={() => confirmMutation.mutate(selectedSession.id, {
                onSuccess: (response) => showToast({
                  tone: 'success',
                  message: `${t.workspace.copy.calibrationConfirmed} ${response.finalizedCount}${t.workspace.copy.countSuffix}`,
                }),
                onError: reportError,
              })}
            >
              {t.workspace.copy.confirmCalibration}
            </UiButton>
          </Group>
        )}
      </Stack>
    </SectionCard>
  );
}

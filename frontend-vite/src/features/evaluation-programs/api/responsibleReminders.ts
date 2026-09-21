import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../../api/client';
import { programKeys, type PageEnvelope, type ParticipantResponse } from './programs';
import type { Locale } from '../../../i18n';

export const reminderStages = ['GOAL', 'INTERMEDIATE', 'SELF_REVIEW', 'REVIEW', 'CALIBRATION', 'FEEDBACK'] as const;
export type ReminderStage = typeof reminderStages[number];
export interface ReminderScope { participantIds: string[]; stages: ReminderStage[]; locale: Locale }
export interface ReminderCandidate {
  candidateKey: string; participantId: string; participantEmployeeId: string; participantName: string;
  stage: ReminderStage; currentRound: number; action: string; ownerKind: 'SELF' | 'ASSIGNMENT' | 'UNRESOLVED';
  ownerRole: string | null; reviewerAssignmentId: string | null; recipientEmployeeId: string | null; recipientName: string | null;
  startsOn: string | null; dueDate: string | null; dueState: string; status: 'READY' | 'ALREADY_QUEUED' | 'BLOCKED';
  reasonCode: string | null; existingNotificationId: string | null; subject: string | null; body: string | null;
  deepLink: string | null; sourceFingerprint: string;
}
export interface ReminderPreview {
  programId: string; reminderOn: string; zoneId: 'UTC'; policyVersion: string; previewHash: string;
  candidates: ReminderCandidate[];
  exclusions: Array<{ participantId: string; currentStage: string | null; currentRound: number; status: 'COMPLETED' | 'NOT_APPLICABLE'; reasonCode: string }>;
  summary: { requestedParticipants: number; ready: number; alreadyQueued: number; blocked: number; completed: number; notApplicable: number };
}
export interface ReminderQueueInput extends ReminderScope {
  reminderOn: string; previewHash: string; candidateKeys: string[]; idempotencyKey: string; reason: string;
}
export interface ReminderQueueResponse {
  programId: string; reminderOn: string; policyVersion: string; previewHash: string; idempotencyKey: string;
  queued: number; duplicateSuppressed: number;
  rows: Array<{ candidateKey: string; notificationId: string; participantId: string; recipientEmployeeId: string;
    stage: string; action: string; disposition: 'QUEUED' | 'DUPLICATE_SUPPRESSED'; notificationStatus: 'SENT' | 'READ'; registeredAt: string }>;
}
export interface ReminderHistoryRow {
  notificationId: string; participantId: string; recipientEmployeeId: string; stage: ReminderStage; currentRound: number;
  action: string; reminderOn: string; policyVersion: string; notificationStatus: 'SENT' | 'READ'; subject: string; body: string;
  deepLink: string; registeredAt: string; readAt: string | null; idempotencyKey: string;
}
const base = (id: string): string => `/v1/evaluation-programs/${id}/incomplete-reminders`;
export function useReminderPreviewMutation(id: string) {
  return useMutation({ mutationFn: (input: ReminderScope) => apiClient.post<ReminderPreview>(`${base(id)}:preview`, input).then(({ data }) => data) });
}
export function useReminderQueueMutation(id: string) {
  const client = useQueryClient();
  return useMutation({ mutationFn: (input: ReminderQueueInput) => apiClient.post<ReminderQueueResponse>(`${base(id)}:queue`, input).then(({ data }) => data),
    onSuccess: () => client.invalidateQueries({ queryKey: programKeys.all() }) });
}
export function useReminderHistoryQuery(id: string, page: number) {
  return useQuery({ queryKey: [...programKeys.detail(id), 'incomplete-reminders', page],
    queryFn: () => apiClient.get<PageEnvelope<ReminderHistoryRow>>(base(id), { params: { page, size: 20 } }).then(({ data }) => data), enabled: Boolean(id) });
}
export function useReminderParticipantsQuery(id: string, page: number) {
  return useQuery({ queryKey: [...programKeys.participants(id), 'reminder-selection', page],
    queryFn: () => apiClient.get<PageEnvelope<ParticipantResponse>>(`/v1/evaluation-programs/${id}/participants`, { params: { page, size: 100 } }).then(({ data }) => data), enabled: Boolean(id) });
}
export function isReminderStale(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'response' in error && (error as { response?: { status?: number } }).response?.status === 409;
}

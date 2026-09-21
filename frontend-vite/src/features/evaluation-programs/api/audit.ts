import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../../api/client';
import { programKeys, type PageEnvelope } from './programs';

export const auditEventTypes = [
  'PROGRAM_CREATED', 'PROGRAM_BASIC_UPDATED', 'DEFINITION_REVISED', 'OPENED',
  'PARTICIPANT_CREATED', 'PARTICIPANT_CHANGED', 'REVIEWERS_CHANGED', 'STAGE_CHANGED',
  'GOAL_CHANGED', 'RESPONSE_COMPLETED', 'CALCULATED', 'ADJUSTED', 'FEEDBACK_CHANGED',
  'FEEDBACK_INVALIDATED', 'FINALIZED', 'FINALIZATION_CANCELLED', 'RESULT_PUBLISHED', 'EMPLOYEE_PREVIEWED',
  'KPI_LINK_APPLIED', 'REMINDERS_QUEUED', 'RESULT_PDF_EXPORTED',
] as const;
export type AuditEventType = typeof auditEventTypes[number];
export interface AuditRow {
  id: string; participantId: string | null; eventType: AuditEventType;
  reason: string; actorEmployeeId: string | null; createdAt: string;
}
export function useProgramAuditQuery(programId: string, page: number, eventType: string, participantId: string) {
  return useQuery({
    queryKey: [...programKeys.detail(programId), 'audit', page, eventType, participantId],
    queryFn: () => apiClient.get<PageEnvelope<AuditRow>>(`/v1/evaluation-programs/${programId}/audit-events`, {
      params: { page, size: 25, eventType: eventType || undefined, participantId: participantId || undefined },
    }).then(({ data }) => data),
    enabled: Boolean(programId),
  });
}

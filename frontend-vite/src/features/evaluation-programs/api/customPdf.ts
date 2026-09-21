import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../../api/client';
import { programKeys } from './programs';

export const pdfColumns = ['EMPLOYEE_NO', 'EMPLOYEE_NAME', 'DEPARTMENT', 'POSITION', 'JOB', 'SCORE', 'GRADE', 'FEEDBACK_STATUS'] as const;
export type PdfColumn = typeof pdfColumns[number];
export const pdfSections = ['SUMMARY', 'PARTICIPANT_TABLE'] as const;
export type PdfSection = typeof pdfSections[number];
export type PdfOrientation = 'PORTRAIT' | 'LANDSCAPE';
export type PdfLocale = 'ko' | 'en';
export interface CustomPdfRequest {
  locale: PdfLocale;
  orientation: PdfOrientation;
  sections: PdfSection[];
  participantColumns: PdfColumn[];
  title?: string;
}

export function validPdfOptions(request: CustomPdfRequest): boolean {
  const columns = request.participantColumns;
  if (!request.sections.length || new Set(request.sections).size !== request.sections.length) return false;
  if (request.title !== undefined && (!request.title.trim() || [...request.title].length > 100 || /[\u0000-\u001f\u007f-\u009f]/u.test(request.title))) return false;
  if (!request.sections.includes('PARTICIPANT_TABLE')) return columns.length === 0;
  return columns.length <= (request.orientation === 'PORTRAIT' ? 5 : 8) &&
    new Set(columns).size === columns.length && columns.includes('EMPLOYEE_NAME') &&
    (columns.includes('SCORE') || columns.includes('GRADE'));
}

export function useCustomPdfMutation(programId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: CustomPdfRequest) => apiClient.post<Blob>(
      `/v1/evaluation-programs/${programId}/results.pdf`, request,
      { responseType: 'blob', headers: { Accept: '*/*' } },
    ).then(({ data }) => data),
    onSuccess: () => { void client.invalidateQueries({ queryKey: [...programKeys.detail(programId), 'audit'] }); },
  });
}

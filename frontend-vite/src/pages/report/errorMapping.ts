/**
 * errorMapping — PerformanceReport 도메인 ApiError → i18n 메시지 매핑.
 *
 * BE-CC-5 ApiError shape: `{ code | errorCode, message, ... }` (lib `@easy/http-client`).
 * Performance 영역 prefix: E98. 계약 §4 검증 규칙 3 신규 + 재사용 2 (447 review / 441 cycle) = 5 키.
 *
 * calibration/errorMapping.ts 패턴 정합 — 자체 KNOWN_CODES Set + getErrorMessage fallback.
 */
import type { I18nDict } from '../../i18n/ko';
import { getErrorMessage } from '../../api/error';

type ReportErrorCode = keyof Pick<
  I18nDict['error'],
  | 'E9804449' // report not found (my 포함)
  | 'E9804252' // cycle not FINALIZED (publish/supersede)
  | 'E9804934' // report not active (superseded 행 view/acknowledge/supersede)
  | 'E9804447' // review not found (재사용)
  | 'E9804441' // cycle not found (재사용)
>;

const KNOWN_CODES: ReadonlySet<string> = new Set<ReportErrorCode>([
  'E9804449',
  'E9804252',
  'E9804934',
  'E9804447',
  'E9804441',
]);

function extractCode(err: unknown): string | null {
  if (err && typeof err === 'object') {
    if ('code' in err && typeof (err as { code?: unknown }).code === 'string') {
      return (err as { code: string }).code;
    }
    if (
      'errorCode' in err &&
      typeof (err as { errorCode?: unknown }).errorCode === 'string'
    ) {
      return (err as { errorCode: string }).errorCode;
    }
  }
  return null;
}

export function mapReportErrorToMessage(err: unknown, t: I18nDict): string {
  const code = extractCode(err);
  if (code && KNOWN_CODES.has(code)) {
    return t.error[code as ReportErrorCode];
  }
  return getErrorMessage(err) || t.error.unknown;
}

/** my report 404 (E9804449) 판정 — 빈 상태 vs 실 에러 분기용. */
export function isReportNotFound(err: unknown): boolean {
  return extractCode(err) === 'E9804449';
}

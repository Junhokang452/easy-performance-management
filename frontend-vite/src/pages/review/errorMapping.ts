/**
 * errorMapping — PerformanceReview 도메인 ApiError → i18n 메시지 매핑.
 *
 * BE-CC-5 ApiError shape: `{ code | errorCode, message, ... }` (lib `@easy/http-client`).
 * Performance 영역 prefix: E98. 계약 §4 검증 규칙 10종.
 *
 * cycles/errorMapping.ts · kpi/errorMapping.ts 패턴 정합 — 자체 KNOWN_CODES Set + getErrorMessage fallback.
 */
import type { I18nDict } from '../../i18n/ko';
import { getErrorMessage } from '../../api/error';

type ReviewErrorCode = keyof Pick<
  I18nDict['error'],
  | 'E9804447'
  | 'E9804240'
  | 'E9804241'
  | 'E9804242'
  | 'E9804243'
  | 'E9804244'
  | 'E9804245'
  | 'E9804928'
  | 'E9804929'
  | 'E9804930'
>;

const KNOWN_CODES: ReadonlySet<string> = new Set<ReviewErrorCode>([
  'E9804447',
  'E9804240',
  'E9804241',
  'E9804242',
  'E9804243',
  'E9804244',
  'E9804245',
  'E9804928',
  'E9804929',
  'E9804930',
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

export function mapReviewErrorToMessage(err: unknown, t: I18nDict): string {
  const code = extractCode(err);
  if (code && KNOWN_CODES.has(code)) {
    return t.error[code as ReviewErrorCode];
  }
  return getErrorMessage(err) || t.error.unknown;
}

/** review 가 없음(404 E9804447)인지 — 자기평가 페이지 빈 상태 분기용. */
export function isReviewNotFound(err: unknown): boolean {
  return extractCode(err) === 'E9804447';
}

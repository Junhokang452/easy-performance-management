/**
 * errorMapping — Cycles 도메인 ApiError → i18n 메시지 매핑.
 *
 * BE-CC-5 ApiError shape: `{ code, message, ... }` (lib `@easy/http-client`).
 * Performance 영역 prefix: E98 (E97 은 historical, error.ts isPerformanceError 와 별도).
 */
import type { I18nDict } from '../../i18n/ko';
import { getErrorMessage } from '../../api/error';

type CycleErrorCode = keyof Pick<
  I18nDict['error'],
  | 'E9804441'
  | 'E9804231'
  | 'E9804921'
  | 'E9804232'
  | 'E9804922'
  | 'E9804442'
  | 'E9804233'
  | 'E9804234'
  | 'E9804235'
  | 'E9804923'
>;

const KNOWN_CODES: ReadonlySet<string> = new Set<CycleErrorCode>([
  'E9804441',
  'E9804231',
  'E9804921',
  'E9804232',
  'E9804922',
  'E9804442',
  'E9804233',
  'E9804234',
  'E9804235',
  'E9804923',
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

export function mapApiErrorToMessage(err: unknown, t: I18nDict): string {
  const code = extractCode(err);
  if (code && KNOWN_CODES.has(code)) {
    return t.error[code as CycleErrorCode];
  }
  return getErrorMessage(err) || t.error.unknown;
}

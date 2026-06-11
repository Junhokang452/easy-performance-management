/**
 * errorMapping — Calibration/Distribution 도메인 ApiError → i18n 메시지 매핑.
 *
 * BE-CC-5 ApiError shape: `{ code | errorCode, message, ... }` (lib `@easy/http-client`).
 * Performance 영역 prefix: E98. 계약 §4 검증 규칙 10종 + 재사용 2 (E9804447 review / E9804442 policy) = 12 키.
 *
 * review/errorMapping.ts · cycles/errorMapping.ts 패턴 정합 — 자체 KNOWN_CODES Set + getErrorMessage fallback.
 */
import type { I18nDict } from '../../i18n/ko';
import { getErrorMessage } from '../../api/error';

type CalibrationErrorCode = keyof Pick<
  I18nDict['error'],
  | 'E9804448' // session not found
  | 'E9804246' // invalid status transition
  | 'E9804247' // distribution invalid target
  | 'E9804248' // distribution mode not forced
  | 'E9804249' // distribution scale not supported
  | 'E9804250' // cycle stage mismatch
  | 'E9804251' // adjustment invalid
  | 'E9804931' // session locked
  | 'E9804932' // session cannot delete
  | 'E9804933' // review not ready
  | 'E9804447' // review not found (재사용)
  | 'E9804442' // policy not found (재사용)
>;

const KNOWN_CODES: ReadonlySet<string> = new Set<CalibrationErrorCode>([
  'E9804448',
  'E9804246',
  'E9804247',
  'E9804248',
  'E9804249',
  'E9804250',
  'E9804251',
  'E9804931',
  'E9804932',
  'E9804933',
  'E9804447',
  'E9804442',
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

export function mapCalibrationErrorToMessage(
  err: unknown,
  t: I18nDict,
): string {
  const code = extractCode(err);
  if (code && KNOWN_CODES.has(code)) {
    return t.error[code as CalibrationErrorCode];
  }
  return getErrorMessage(err) || t.error.unknown;
}

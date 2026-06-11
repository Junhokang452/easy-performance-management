/**
 * errorMapping — KPI 도메인 ApiError → i18n 메시지 매핑.
 *
 * BE-CC-5 ApiError shape: `{ errorCode, message, ... }` (lib `@easy/http-client`).
 * Performance 영역 prefix: E98. 계약 §3 검증 규칙 12종.
 *
 * cycles/errorMapping.ts 패턴 정합 — 자체 KNOWN_CODES Set + getErrorMessage fallback.
 */
import type { I18nDict } from '../../i18n/ko';
import { getErrorMessage } from '../../api/error';

type KpiErrorCode = keyof Pick<
  I18nDict['error'],
  | 'E9804443'
  | 'E9804444'
  | 'E9804445'
  | 'E9804446'
  | 'E9804236'
  | 'E9804237'
  | 'E9804238'
  | 'E9804239'
  | 'E9804924'
  | 'E9804925'
  | 'E9804926'
  | 'E9804927'
>;

const KNOWN_CODES: ReadonlySet<string> = new Set<KpiErrorCode>([
  'E9804443',
  'E9804444',
  'E9804445',
  'E9804446',
  'E9804236',
  'E9804237',
  'E9804238',
  'E9804239',
  'E9804924',
  'E9804925',
  'E9804926',
  'E9804927',
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

export function mapKpiErrorToMessage(err: unknown, t: I18nDict): string {
  const code = extractCode(err);
  if (code && KNOWN_CODES.has(code)) {
    return t.error[code as KpiErrorCode];
  }
  return getErrorMessage(err) || t.error.unknown;
}

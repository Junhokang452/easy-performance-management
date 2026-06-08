/**
 * ApiError SoT — BE BE-CC-5 ApiException 정합 (단계 4 강화).
 *
 * BE 응답 shape (ApiException):
 * ```
 * {
 *   "errorCode": "E9704401",
 *   "message": "Self evaluation not found",
 *   "traceId": "...",
 *   "timestamp": "2026-06-08T..."
 * }
 * ```
 *
 * lib `@easy/http-client` `ApiError` 와 호환되도록 helper + PerformanceErrorCode prefix
 * 매핑 (E97*) 만 자체 보유.
 *
 * 자매품 일관 정합 — easy-recruit / easy-ware / easy-hcm / easy-job-evaluation 동일 패턴.
 * jobeval `cc1bc03` 패턴 정합.
 */
import type { ApiError as EasyApiError } from '@easy/http-client';

export type ApiError = EasyApiError;

/** Performance 영역 prefix (E97) 식별 */
export function isPerformanceError(err: unknown): err is ApiError {
  return (
    typeof err === 'object' &&
    err !== null &&
    'errorCode' in err &&
    typeof (err as { errorCode?: unknown }).errorCode === 'string' &&
    (err as { errorCode: string }).errorCode.startsWith('E97')
  );
}

/** ApiError → 사용자 메시지 추출 (i18n 미정합 시 BE message fallback) */
export function getErrorMessage(err: unknown): string {
  if (err && typeof err === 'object') {
    if ('message' in err && typeof (err as { message: unknown }).message === 'string') {
      return (err as { message: string }).message;
    }
  }
  return String(err);
}

/** ApiError → HTTP status 추출 (가능한 경우) */
export function getErrorStatus(err: unknown): number | null {
  if (err && typeof err === 'object' && 'status' in err) {
    const s = (err as { status?: unknown }).status;
    if (typeof s === 'number') return s;
  }
  return null;
}

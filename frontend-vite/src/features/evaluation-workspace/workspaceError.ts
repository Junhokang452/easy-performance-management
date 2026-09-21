import type { I18nDict } from '../../i18n/ko';

function errorPayload(error: unknown): Record<string, unknown> | null {
  if (!error || typeof error !== 'object') return null;
  const response = (error as { response?: { data?: unknown } }).response;
  if (response?.data && typeof response.data === 'object') {
    return response.data as Record<string, unknown>;
  }
  return error as Record<string, unknown>;
}

export function workspaceErrorMessage(t: I18nDict, error: unknown): string {
  const payload = errorPayload(error);
  const code = payload?.code ?? payload?.errorCode;
  if (typeof code === 'string') {
    const key = `error_${code}`;
    if (Object.hasOwn(t.workspace.copy, key)) {
      return t.workspace.copy[key as keyof I18nDict['workspace']['copy']];
    }
    if (Object.hasOwn(t.error, code)) return t.error[code as keyof I18nDict['error']];
  }
  const status = payload?.status ?? (error as { response?: { status?: number } } | null)?.response?.status;
  if (status === 401) return t.error.unauthorized;
  if (status === 403) return t.error.forbidden;
  return t.workspace.copy.unknownError;
}

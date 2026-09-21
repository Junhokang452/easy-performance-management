/** Suite locale IDs use BCP 47 in the browser; shared bundles use zh_CN. */
export const SUPPORTED_LOCALES = ['ko', 'en', 'ja', 'zh-CN', 'vi'] as const;
export type Locale = (typeof SUPPORTED_LOCALES)[number];
export const LOCALE_LABELS: Record<Locale, string> = {
  ko: '한국어', en: 'English', ja: '日本語', 'zh-CN': '中文（简体）', vi: 'Tiếng Việt',
};
export const STORAGE_KEY = 'easyperformance.locale';

export function normalizeLocale(value: string | null | undefined): Locale {
  const tag = value?.replaceAll('_', '-').toLowerCase();
  if (tag === 'zh' || tag === 'zh-cn' || tag === 'zh-hans') return 'zh-CN';
  const language = tag?.split('-')[0];
  return language === 'en' || language === 'ja' || language === 'vi' ? language : 'ko';
}

export function readSavedLocale(): Locale {
  if (typeof window === 'undefined') return 'ko';
  try { return normalizeLocale(window.localStorage.getItem(STORAGE_KEY)); }
  catch { return 'ko'; }
}

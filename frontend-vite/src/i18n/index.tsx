/** Five-language suite provider. Korean schema requires identical keys in every locale. */
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { DatesProvider } from '@mantine/dates';
import 'dayjs/locale/ko';
import 'dayjs/locale/ja';
import 'dayjs/locale/zh-cn';
import 'dayjs/locale/vi';

import type { I18nDict } from './ko';
import { dictionaries } from './dictionaries';
import { readSavedLocale, STORAGE_KEY, type Locale } from './locales';
export type { Locale } from './locales';


interface I18nContextValue {
  locale: Locale;
  setLocale: (next: Locale) => void;
  t: I18nDict;
}

const I18nContext = createContext<I18nContextValue | null>(null);

export function I18nProvider({ children }: { children: ReactNode }): React.ReactNode {
  const [locale, setLocaleState] = useState<Locale>(readSavedLocale);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    try { window.localStorage.setItem(STORAGE_KEY, locale); } catch { /* Storage may be disabled. */ }
    document.documentElement.lang = locale;
  }, [locale]);

  const value = useMemo<I18nContextValue>(
    () => ({
      locale,
      setLocale: setLocaleState,
      t: dictionaries[locale],
    }),
    [locale],
  );

  return (
    <I18nContext.Provider value={value}>
      <DatesProvider settings={{ locale: locale === 'zh-CN' ? 'zh-cn' : locale }}>
        {children}
      </DatesProvider>
    </I18nContext.Provider>
  );
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n must be used inside <I18nProvider>');
  return ctx;
}

export function useT(): I18nDict {
  return useI18n().t;
}

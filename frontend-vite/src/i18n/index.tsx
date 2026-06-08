/**
 * i18n Provider + useT() hook — ko/en 2 locale (단계 4 진입 기본).
 *
 * lib `@easy/i18n-common` bundle 통합은 후속 (lib FE 13 진입 후).
 * 현재는 자체 bundle ko/en + localStorage 영속화.
 *
 * ADR-027 i18n 라벨 표준 정합 — namespace 5 계층 + 5 locale 확장 게이트.
 * jobeval 단계 4 cutover `cc1bc03` 패턴 정합.
 */
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { ko, type I18nDict } from './ko';
import { en } from './en';

const STORAGE_KEY = 'easyperformance.locale';

export type Locale = 'ko' | 'en';

const dictionaries: Record<Locale, I18nDict> = { ko, en };

interface I18nContextValue {
  locale: Locale;
  setLocale: (next: Locale) => void;
  t: I18nDict;
}

const I18nContext = createContext<I18nContextValue | null>(null);

function readInitialLocale(): Locale {
  if (typeof window === 'undefined') return 'ko';
  const stored = window.localStorage.getItem(STORAGE_KEY);
  return stored === 'en' ? 'en' : 'ko';
}

export function I18nProvider({ children }: { children: ReactNode }): React.ReactNode {
  const [locale, setLocaleState] = useState<Locale>(readInitialLocale);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(STORAGE_KEY, locale);
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

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n must be used inside <I18nProvider>');
  return ctx;
}

export function useT(): I18nDict {
  return useI18n().t;
}

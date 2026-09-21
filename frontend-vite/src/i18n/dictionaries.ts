import { ko, type I18nDict } from './ko';
import { en } from './en';
import { ja } from './ja';
import { zhCN } from './zh-CN';
import { vi } from './vi';
import type { Locale } from './locales';

export const dictionaries: Record<Locale, I18nDict> = { ko, en, ja, 'zh-CN': zhCN, vi };

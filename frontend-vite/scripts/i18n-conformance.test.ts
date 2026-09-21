import assert from 'node:assert/strict';
import test from 'node:test';
import { ko } from '../src/i18n/ko.ts';
import { en } from '../src/i18n/en.ts';
import { ja } from '../src/i18n/ja.ts';
import { zhCN } from '../src/i18n/zh-CN.ts';
import { vi } from '../src/i18n/vi.ts';
import { normalizeLocale, SUPPORTED_LOCALES } from '../src/i18n/locales.ts';
import { workspaceErrorMessage } from '../src/features/evaluation-workspace/workspaceError.ts';

function flatten(value: object, prefix = ''): Record<string, string> {
  return Object.fromEntries(Object.entries(value).flatMap(([key, entry]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof entry === 'string' ? [[path, entry]] : Object.entries(flatten(entry, path));
  }));
}
const source = flatten(ko);
const english = flatten(en);
const placeholders = (text: string) => [...text.matchAll(/\{[a-zA-Z][a-zA-Z0-9_]*\}/g)].map(([v]) => v).sort();
for (const [locale, dictionary] of Object.entries({ ko, en, ja, 'zh-CN': zhCN, vi })) {
  test(`${locale}: all Korean-schema keys and interpolation variables are translated`, () => {
    const target = flatten(dictionary);
    assert.deepEqual(Object.keys(target).sort(), Object.keys(source).sort());
    for (const [key, value] of Object.entries(target)) {
      assert.ok(value.trim().length, `${locale}.${key}: empty translation`);
      assert.deepEqual(placeholders(value), placeholders(source[key]), `${locale}.${key}: interpolation mismatch`);
      if (locale !== 'ko') assert.ok(!/[가-힣]/u.test(value), `${locale}.${key}: untranslated Korean`);
      if (locale !== 'ko' && locale !== 'en' && english[key].length > 30) {
        assert.notEqual(value, english[key], `${locale}.${key}: untranslated English sentence`);
      }
      if (locale === 'ja' || locale === 'zh-CN') {
        const withoutVariables = value.replace(/\{[^}]*\}/g, '');
        assert.ok(!/\b(?:the|and|with|from|this|for|your|their|please|already|members|complete|required|before|after|reviewer|session)\b/i.test(withoutVariables), `${locale}.${key}: mixed untranslated English`);
      }
    }
  });
}
test('five suite locales normalize persisted and BCP 47 identifiers without changing the Chinese script', () => {
  assert.deepEqual(SUPPORTED_LOCALES, ['ko', 'en', 'ja', 'zh-CN', 'vi']);
  for (const [input, expected] of Object.entries({ zh: 'zh-CN', zh_CN: 'zh-CN', 'zh-Hans': 'zh-CN', 'ja-JP': 'ja', 'vi-VN': 'vi', 'en-US': 'en', 'ko-KR': 'ko', 'zh-TW': 'ko', invalid: 'ko' })) {
    assert.equal(normalizeLocale(input), expected);
  }
});

test('errors use the selected language and never expose raw server messages', () => {
  for (const dictionary of [ko, en, ja, zhCN, vi]) {
    assert.equal(workspaceErrorMessage(dictionary, { code: 'E9804935', message: 'English server details' }), dictionary.workspace.copy.error_E9804935);
    assert.equal(workspaceErrorMessage(dictionary, { response: { status: 403, data: {} } }), dictionary.error.forbidden);
    assert.equal(workspaceErrorMessage(dictionary, { message: 'Untranslated internal server message' }), dictionary.workspace.copy.unknownError);
  }
});

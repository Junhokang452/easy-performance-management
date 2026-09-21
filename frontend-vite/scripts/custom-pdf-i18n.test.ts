import assert from 'node:assert/strict';
import test from 'node:test';
import { customPdfI18n } from '../src/features/evaluation-programs/customPdfI18n.ts';

function flatten(value: Record<string, unknown>, prefix = ''): Array<[string, string]> {
  return Object.entries(value).flatMap(([key, item]) => typeof item === 'string'
    ? [[prefix + key, item] as [string, string]]
    : flatten(item as Record<string, unknown>, prefix + key + '.'));
}
const schema = flatten(customPdfI18n.ko).map(([key]) => key).sort();
for (const [locale, labels] of Object.entries(customPdfI18n)) {
  test(`PDF ${locale}: full key and column label parity, nonempty translated values`, () => {
    const values = flatten(labels);
    assert.deepEqual(values.map(([key]) => key).sort(), schema);
    assert.ok(values.every(([, value]) => value.trim().length > 0));
    if (locale !== 'ko') assert.ok(values.every(([, value]) => !/[가-힣]/u.test(value)), 'No Korean placeholder fallback');
    assert.ok(labels.hint.includes('200'));
  });
}

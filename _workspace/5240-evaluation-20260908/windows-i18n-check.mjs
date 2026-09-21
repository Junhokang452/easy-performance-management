import { registerHooks } from 'node:module';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
const root = '//wsl.localhost/Ubuntu/home/samsung/code/easy-performance-management';
const packageRoot = root + '/lib/easy-platform/easy-platform-core/packages/i18n-common';
const manifest = JSON.parse(readFileSync(packageRoot + '/package.json', 'utf8'));
registerHooks({ resolve(specifier, context, nextResolve) {
 if (specifier === '@easy/i18n-common' || specifier.startsWith('@easy/i18n-common/')) {
  const subpath = '.' + specifier.slice('@easy/i18n-common'.length);
  const target = manifest.exports[subpath]?.import;
  if (!target) throw new Error('Unexported shared i18n subpath: ' + subpath);
  return {url:pathToFileURL(resolve(packageRoot, target)).href,shortCircuit:true};
 }
 return nextResolve(specifier, context);
}});
await import(pathToFileURL(root + '/frontend-vite/scripts/i18n-conformance.test.ts').href);

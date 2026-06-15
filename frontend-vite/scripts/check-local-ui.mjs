#!/usr/bin/env node

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { extname, join, relative, resolve } from 'node:path';

const args = process.argv.slice(2);
const roots = args.filter((arg) => !arg.startsWith('--'));
const maxTagsArg = args.find((arg) => arg.startsWith('--max-tags='));
const maxBlockedArg = args.find((arg) => arg.startsWith('--max-blocked-imports='));
const maxFindingsArg = args.find((arg) => arg.startsWith('--max-findings='));
const maxTags = Number(maxTagsArg?.split('=')[1] ?? 0);
const maxBlockedImports = Number(maxBlockedArg?.split('=')[1] ?? 0);
const maxFindings = Number(maxFindingsArg?.split('=')[1] ?? 80);
const sourceRoots = roots.length > 0 ? roots : ['src'];
const extensions = new Set(['.ts', '.tsx']);
const ignoredSegments = ['/dist/', '/node_modules/'];

const blockedTags = [
  'ActionIcon',
  'Alert',
  'Badge',
  'Button',
  'Card',
  'Loader',
  'Paper',
  'Table',
  'ThemeIcon',
  'Tooltip',
];

const blockedMantineImports = new Set(blockedTags);

function filesUnder(path) {
  const absolute = resolve(path);
  if (statSync(absolute).isFile()) return [absolute];
  return readdirSync(absolute, { withFileTypes: true }).flatMap((entry) => {
    const child = join(absolute, entry.name);
    return entry.isDirectory() ? filesUnder(child) : [child];
  });
}

function importedNames(statement) {
  const match = statement.match(/import\s*\{([\s\S]*?)\}\s*from\s*['"]@easy\/ui-components\/mantine['"]/);
  if (!match) return [];
  return match[1]
    .split(',')
    .map((part) => part.trim().replace(/\s+as\s+.+$/, ''))
    .filter(Boolean);
}

const findings = [];
let tagCount = 0;
let importCount = 0;

const files = sourceRoots
  .flatMap(filesUnder)
  .filter((file) => extensions.has(extname(file)))
  .filter((file) => !ignoredSegments.some((segment) => file.includes(segment)));

for (const file of files) {
  const source = readFileSync(file, 'utf8');
  const relativeFile = relative(process.cwd(), file);

  for (const tag of blockedTags) {
    const matches = source.match(new RegExp(`<${tag}(\\.|[\\s>])`, 'g')) ?? [];
    if (matches.length > 0) {
      tagCount += matches.length;
      findings.push(`${relativeFile}: local UI tag <${tag}> x${matches.length}`);
    }
  }

  const importStatements = source.match(/import\s*\{[\s\S]*?\}\s*from\s*['"]@easy\/ui-components\/mantine['"]/g) ?? [];
  for (const statement of importStatements) {
    const blocked = importedNames(statement).filter((name) => blockedMantineImports.has(name));
    if (blocked.length > 0) {
      importCount += blocked.length;
      findings.push(`${relativeFile}: blocked mantine subpath import ${blocked.join(', ')}`);
    }
  }
}

console.log(
  `local-ui audit: tags=${tagCount}/${maxTags}, blocked-imports=${importCount}/${maxBlockedImports}`,
);

if (tagCount > maxTags || importCount > maxBlockedImports) {
  console.error('Local UI primitives exceed the migration baseline. Use @easy/ui-components DS wrappers.');
  findings.slice(0, maxFindings).forEach((finding) => console.error(`  ${finding}`));
  process.exitCode = 1;
}

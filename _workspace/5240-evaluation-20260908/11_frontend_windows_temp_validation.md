# Windows temporary-directory frontend validation

Date: 2026-09-08 (Asia/Seoul)  
Mode: Windows-only temporary-copy workaround. No WSL command was started for this attempt.

## Isolation

A unique directory was created under `%TEMP%`:

`C:\Users\SAMSUNG\AppData\Local\Temp\easy-performance-frontend-validation-5a65261bd15541a78de18e7c0bda4407`

It preserves the product-relative layout needed by `frontend-vite` file dependencies:

```
easy-performance-management/
  frontend-vite/
  lib/easy-platform/easy-platform-core/
```

Both trees were copied with `node_modules`, `build`, `dist`, `storybook-static`, `.git`, and `*.tsbuildinfo` excluded. The copied frontend and shared-core `package.json` files were present, and no excluded `node_modules` directory was present before install. The source product, its package files, and lockfiles were not modified.

## Commands and outcomes

| Step | Command | Exit | Result |
|---|---|---:|---|
| Copy (first attempt) | PowerShell temporary-layout construction | 1 | The PowerShell array construction produced a null target path before copying. The incomplete temporary directory was abandoned; product files were untouched. |
| Copy (replacement) | `robocopy <frontend> <temp frontend> /E /XD node_modules build dist storybook-static .git /XF *.tsbuildinfo`, same for shared core | 0 | Completed with the exclusions above. |
| Shared core install | `npm.cmd ci --ignore-scripts --legacy-peer-deps` in temp `easy-platform-core` | 1 | No output was emitted for approximately 2.5 minutes. It was interrupted to avoid an unbounded install wait; this is not an npm/package failure diagnosis. |

The core installation did not reach a usable completion point. Consequently frontend `npm.cmd ci` was **not** started, and the following gate commands were intentionally not attempted in a partially installed temp tree:

- `npm run typecheck`
- `npm run test:i18n`
- `npm run design:check`
- `npm run local-ui:check`
- production `npm run build`

OpenAPI generation remains intentionally unrun: no backend document server is available and the task explicitly retains it as unverified.

## Interpretation and next action

This attempt eliminates the prior UNC-current-directory `cmd.exe` limitation, but the Windows temporary copy still needs a completed core package installation before it can evaluate source correctness. The only recorded nonzero exit after isolation is a manual timeout interrupt, not a compiler/test assertion. Do not change product dependencies, lockfiles, or source to work around it.

When package installation can complete, resume from the same isolated layout (or a fresh equivalent one): core `npm.cmd ci --ignore-scripts --legacy-peer-deps`, frontend `npm.cmd ci --ignore-scripts --legacy-peer-deps`, then the five requested gates. Record each actual output and exit code before interpreting a source failure.

## Allowed retry — timed out, no source error (2026-09-08)

Before retry, Windows process inspection found **no** remaining `node.exe`, `npm.exe`, or `cmd.exe` whose command line contained this temporary validation root; therefore no process was terminated.

The requested core command was then run in the same isolated core directory:

```text
npm.cmd ci --ignore-scripts --legacy-peer-deps --verbose --foreground-scripts=false
```

It identified npm 10.9.3 and Node 22.20.0, read packages from the local npm cache, and issued a successful advisory bulk request (`POST .../security/advisories/bulk 200`). The repeated `reify failed optional dependency` messages were for non-Windows target binaries (Linux, Darwin, Android, etc.) and therefore are normal npm optional-dependency pruning, not the first installation error. No `npm ERR!`, registry/network failure, or package-resolution error was emitted.

After five minutes, the process still produced no completion line or exit code and was terminated under the requested maximum duration. Its exit code is `1` because of that explicit timeout interruption. The core install is thus **incomplete**, so frontend `npm.cmd ci` and all requested frontend gates remain unrun; running them against the partial dependency tree would not be a valid correction attempt.

The temporary-copy workaround proves the former UNC/current-directory limitation is bypassed, but it does not overcome the stalled Windows npm reify/finish phase. No product source, package file, or lockfile was changed.

## Partial-node_modules direct-execution check — blocked before execution

Per the no-more-install directive, the same Windows temporary copy was inspected without modifying it. The frontend dependency tree has not been installed:

| Required frontend entry/dependency | Present |
|---|---:|
| `node_modules/typescript/bin/tsc` | No |
| `node_modules/vite/bin/vite.js` | No |
| `node_modules/react/package.json` | No |
| `node_modules/@easy/ui-components/package.json` | No |

The partially installed shared core does contain its own TypeScript/Vite CLIs and Windows rolldown/esbuild optional binaries. They cannot validate `frontend-vite` on their own, because the frontend's direct React, `@easy/*`, and TypeScript package links are absent. Running core's compiler against the frontend project would only yield a known module-resolution failure and would not be a valid frontend gate result.

Accordingly no direct `tsc`, i18n, design/local-UI, shared-core build entry, or Vite production build was invoked. This follows the instruction to stop immediately on a missing module. No source, package file, lockfile, or generated product artifact changed.

## Continued Windows validation — bundled runtime (2026-09-08)

The earlier records above remain historical: the system Node/npm process was later observed to have an incomplete `npm ci` log (last four cache-miss tarballs, no `exit 0`). It was neither treated as a product defect nor used as a passing install. Validation instead used the already-present temporary frontend dependencies with the verified bundled Node 24.19.0 executable and explicit Windows working directories. No new install was started.

Only the shared packages whose published entry points require `dist/` were built in the temporary core copy. The frontend's package links resolve to those exact core workspaces; UI, tokens, and auth hooks are source/alias consumers and did not require a shared-core root build.

| Step | Command / entry | Exit | Result |
|---|---|---:|---|
| Shared HTTP client | `node node_modules/typescript/bin/tsc -p packages/http-client/tsconfig.json` | 0 | `dist/` generated. |
| Shared query client | `node node_modules/typescript/bin/tsc -p packages/query-client/tsconfig.json` | 0 | `dist/` generated. |
| Shared i18n common | `node node_modules/typescript/bin/tsc -p packages/i18n-common/tsconfig.json` | 0 | `dist/` generated. |
| OpenAPI schema | `node node_modules/openapi-typescript/bin/cli.js <windows-audit-held-openapi.json> -o src/api/generated/schema.d.ts` | 0 | Held API JSON (188,246 bytes) generated a 319,731-byte schema; audit route present. The exact mechanical output was synced to the approved original schema target, SHA-256 `e8f06328bcd3c310ef488c64d0b36475c8d3c05f8a4f8011679944f56646858e`. |
| Frontend typecheck | `node node_modules/typescript/bin/tsc -b --pretty false` | 0 | Passed with no stdout/stderr. |
| i18n conformance | bundled Node `--experimental-strip-types --test scripts/i18n-conformance.test.ts` | 0 | 7/7 passed. |
| Workspace tests | bundled Node workspace test entry | 0 | 5/5 passed. |
| Design check | `check-design-system.mjs src --max-hex=0 --max-inline-style=0` | 0 | hex 0/0, inline-style 0/0. |
| Local UI check | `check-local-ui.mjs src --max-tags=0 --max-blocked-imports=0` | 0 | tags 0/0, blocked imports 0/0. |
| Production Vite build | `node node_modules/vite/bin/vite.js build` | 0 | 7,501 modules transformed; complete `dist/` emitted; built in 5m 15s. |

The Vite command's tool bridge timed out at five minutes, but its retained managed-process result was recovered afterward: PID 48068, exit 0. Its only output warnings were plugin timing (CSS) and the standard chunk-size warning for the existing 721.94 kB client chunk; neither is a build failure.

The originally requested package-script `openapi:types` was deliberately not used because its shell interpolation is non-portable on Windows. The equivalent direct CLI was invoked against the supplied held OpenAPI JSON. No Storybook server was started; its static build/index artifact remains a separate read-only check. Product source and lockfiles were unchanged except for the explicitly authorized, mechanically regenerated `src/api/generated/schema.d.ts`.

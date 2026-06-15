import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * easy-performance-management Vite 설정 (단계 4 EC-FE 진입, G71 D=A, Task #113, 2026-06-08).
 *
 * - dev 서버 포트 5174 (jobeval 5173 충돌 회피)
 * - `/api` proxy → BE port 8087 (application.yml `${PORT:8087}`)
 * - jobeval cc1bc03 모범 정합
 *
 * 2026-06-12 mono 표면 — React 중복 인스턴스 사전 차단 (hcm `73d7eb3` / recruit `624e370` /
 * mra fix #14 동형 함정).
 *
 * 증상(자매품 실측): mono LIVE 에서 로그인 화면 미렌더 —
 *   "Cannot read properties of null (reading 'use')" (React Router caught).
 * 원인: @easy/* 4 패키지가 file:../lib/easy-platform/... 링크로 들어오는데 lib node_modules
 *   안의 react/react-dom 별도 설치본이 prod 번들에 함께 들어가 dual instance → context null.
 * 해결: dedupe 로 React 계열 + context 보유 라이브러리를 번들 singleton 강제 (hcm 8종 정합).
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@easy/tokens': resolve(
        __dirname,
        '../lib/easy-platform/easy-platform-core/packages/tokens/src/index.ts',
      ),
      '@easy/ui-components/performance': resolve(
        __dirname,
        '../lib/easy-platform/easy-platform-core/packages/ui-components/src/performance/index.ts',
      ),
      '@easy/ui-components/mantine': resolve(
        __dirname,
        '../lib/easy-platform/easy-platform-core/packages/ui-components/src/mantine.ts',
      ),
      '@easy/ui-components': resolve(
        __dirname,
        '../lib/easy-platform/easy-platform-core/packages/ui-components/src/index.ts',
      ),
      '@easy/auth-hooks': resolve(
        __dirname,
        '../lib/easy-platform/easy-platform-core/packages/auth-hooks/src/index.ts',
      ),
      '@easy/query-client': resolve(
        __dirname,
        '../lib/easy-platform/easy-platform-core/packages/query-client/src/index.ts',
      ),
      '@mantine/core': resolve(__dirname, 'node_modules/@mantine/core'),
      '@mantine/hooks': resolve(__dirname, 'node_modules/@mantine/hooks'),
      '@mantine/modals': resolve(__dirname, 'node_modules/@mantine/modals'),
      '@mantine/notifications': resolve(__dirname, 'node_modules/@mantine/notifications'),
      '@mantine/charts': resolve(__dirname, 'node_modules/@mantine/charts'),
      '@mantine/form': resolve(__dirname, 'node_modules/@mantine/form'),
      '@tabler/icons-react': resolve(__dirname, 'node_modules/@tabler/icons-react'),
      react: resolve(__dirname, 'node_modules/react'),
      'react-dom': resolve(__dirname, 'node_modules/react-dom'),
    },
    dedupe: [
      'react',
      'react-dom',
      'react-router',
      'react-router-dom',
      '@mantine/core',
      '@mantine/hooks',
      '@mantine/modals',
      '@mantine/notifications',
      '@mantine/charts',
      '@mantine/form',
      '@tanstack/react-query',
    ],
  },
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8087',
        changeOrigin: true,
      },
    },
  },
});

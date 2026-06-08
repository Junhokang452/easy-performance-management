import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * easy-performance-management Vite 설정 (단계 4 EC-FE 진입, G71 D=A, Task #113, 2026-06-08).
 *
 * - dev 서버 포트 5174 (jobeval 5173 충돌 회피)
 * - `/api` proxy → BE port 8087 (application.yml `${PORT:8087}`)
 * - jobeval cc1bc03 모범 정합
 */
export default defineConfig({
  plugins: [react()],
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

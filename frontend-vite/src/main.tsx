/**
 * main.tsx — performance FE 단계 4 EC-FE 진입점 (G71 D=A, Task #113, 2026-06-08).
 *
 * Provider 계층 (외→내):
 * - StrictMode (STD-FE-STRICT)
 * - MantineProvider (defaultColorScheme=auto + 토큰 SoT)
 * - Notifications
 * - QueryClientProvider (lib `@easy/query-client`)
 * - BrowserRouter
 * - I18nProvider (ko/en)
 * - AuthProvider (단계 3 JWT 미진입 — stub. BE 단계 3 진입 시 silent refresh 정합)
 * - App
 *
 * jobeval 단계 4 cutover `cc1bc03` 패턴 정합 (B2C 부재 — B2B + SMB 옵션, ADR-031).
 */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { BrowserRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { createEasyQueryClient } from '@easy/query-client';

import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import '@mantine/dates/styles.css';

import App from './App';
import { theme } from './theme/mantine-theme';
import { I18nProvider } from './i18n';
import { AuthProvider } from './auth/AuthProvider';

const queryClient = createEasyQueryClient({
  eventPrefix: 'easyperformance',
});

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('#root not found');

createRoot(rootElement).render(
  <StrictMode>
    <MantineProvider theme={theme} defaultColorScheme="auto">
      <Notifications position="top-right" />
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <I18nProvider>
            <AuthProvider>
              <App />
            </AuthProvider>
          </I18nProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </MantineProvider>
  </StrictMode>,
);

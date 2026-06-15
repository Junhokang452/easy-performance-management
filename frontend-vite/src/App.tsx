/**
 * App — performance FE 단계 4 EC-FE 진입 (G71 D=A, Task 113, 2026-06-08).
 *
 * STD-FE 5 정합:
 * - LAZY: 모든 페이지 `React.lazy()`
 * - STRICT: `<StrictMode>` (main.tsx)
 * - RQ: React Query 만 (useState fetch 금지)
 * - NEST: React 19 인터랙티브 요소 중첩 금지
 * - ERROR-BOUNDARY: `<PageBoundary>` = RouteErrorBoundary + Suspense + key reset
 *
 * 단계 4 강화 5건:
 * - LAZY (4 도메인 페이지 + LoginPage)
 * - RouteErrorBoundary + key={pathname} 리셋
 * - AuthProvider + ProtectedRoute (단계 3 JWT 미진입 — stub fallback)
 * - i18n ko/en + 다크모드 토글
 * - ApiError SoT (BE-CC-5 ApiException 정합)
 *
 * 누적 정합 — 단계 0 `58bf09d` + 단계 1 `b83acac` + 단계 2 `6895ba9` + 단계 5 SMB `27108e3`.
 * jobeval 단계 4 cutover `cc1bc03` 패턴 정합.
 */
import { lazy } from 'react';
import {
  AppShell,
  NavLink,
  Title,
  Text,
  Group,
  Burger,
} from '@easy/ui-components/mantine';
import { useDisclosure } from '@mantine/hooks';
import { Route, Routes, Link, useLocation } from 'react-router-dom';
import { UiButton, LoginBrandMark } from '@easy/ui-components';

import { PageBoundary } from './shared/PageBoundary';
import { AppHeaderActions } from './shared/AppHeaderActions';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { useAuth } from './auth/AuthProvider';
import { useT } from './i18n';

// STD-FE-LAZY — 모든 페이지 lazy import
const CockpitPage = lazy(() =>
  import('./pages/CockpitPage').then((m) => ({ default: m.CockpitPage })),
);
const SelfEvaluationPage = lazy(() =>
  import('./pages/SelfEvaluationPage').then((m) => ({ default: m.SelfEvaluationPage })),
);
const PersonalOkrPage = lazy(() =>
  import('./pages/PersonalOkrPage').then((m) => ({ default: m.PersonalOkrPage })),
);
const ReflectionJournalPage = lazy(() =>
  import('./pages/ReflectionJournalPage').then((m) => ({ default: m.ReflectionJournalPage })),
);
const MentorFeedbackPage = lazy(() =>
  import('./pages/MentorFeedbackPage').then((m) => ({ default: m.MentorFeedbackPage })),
);
const CyclesPage = lazy(() =>
  import('./pages/CyclesPage').then((m) => ({ default: m.CyclesPage })),
);
const MyKpiPage = lazy(() =>
  import('./pages/MyKpiPage').then((m) => ({ default: m.MyKpiPage })),
);
const GoalAlignmentPage = lazy(() =>
  import('./pages/GoalAlignmentPage').then((m) => ({
    default: m.GoalAlignmentPage,
  })),
);
const ManagerKpiTreePage = lazy(() =>
  import('./pages/ManagerKpiTreePage').then((m) => ({
    default: m.ManagerKpiTreePage,
  })),
);
const DirectorKpiTreePage = lazy(() =>
  import('./pages/DirectorKpiTreePage').then((m) => ({
    default: m.DirectorKpiTreePage,
  })),
);
const MySelfReviewPage = lazy(() =>
  import('./pages/MySelfReviewPage').then((m) => ({
    default: m.MySelfReviewPage,
  })),
);
const ManagerReviewPage = lazy(() =>
  import('./pages/ManagerReviewPage').then((m) => ({
    default: m.ManagerReviewPage,
  })),
);
const HrCalibrationSessionsPage = lazy(() =>
  import('./pages/HrCalibrationSessionsPage').then((m) => ({
    default: m.HrCalibrationSessionsPage,
  })),
);
const DirectorCalibrationPage = lazy(() =>
  import('./pages/DirectorCalibrationPage').then((m) => ({
    default: m.DirectorCalibrationPage,
  })),
);
const CalibrationAnalyticsPage = lazy(() =>
  import('./pages/CalibrationAnalyticsPage').then((m) => ({
    default: m.CalibrationAnalyticsPage,
  })),
);
const HrDistributionPage = lazy(() =>
  import('./pages/HrDistributionPage').then((m) => ({
    default: m.HrDistributionPage,
  })),
);
const HrReportsPage = lazy(() =>
  import('./pages/HrReportsPage').then((m) => ({
    default: m.HrReportsPage,
  })),
);
const MyReportPage = lazy(() =>
  import('./pages/MyReportPage').then((m) => ({
    default: m.MyReportPage,
  })),
);
const AdminTenantsPage = lazy(() =>
  import('./pages/AdminTenantsPage').then((m) => ({
    default: m.AdminTenantsPage,
  })),
);
const LoginPage = lazy(() =>
  import('./pages/LoginPage').then((m) => ({ default: m.LoginPage })),
);

export default function App(): React.ReactNode {
  const location = useLocation();
  const [navOpened, { toggle }] = useDisclosure();
  const { isAuthenticated, logout, session } = useAuth();
  const t = useT();
  // SUPER_ADMIN 전용 내비 노출 — JwtAuthFilter 가 roles claim 을 prefix 없이 발급 (BE 정합).
  const isSuperAdmin = session?.roles.includes('SUPER_ADMIN') ?? false;

  // 로그인 페이지는 AppShell 외부에서 직접 렌더 (Auth 요구 없음)
  if (location.pathname === '/login') {
    return (
      <PageBoundary>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </PageBoundary>
    );
  }

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !navOpened } }}
      padding="md"
      bg="var(--easy-color-canvas)"
    >
      <AppShell.Header p="md" bg="var(--easy-color-surface)" bd="0 0 1px 0 solid var(--easy-color-border)">
        <Group justify="space-between" h="100%" wrap="nowrap">
          <Group gap="md" wrap="nowrap" miw={0}>
            <Burger opened={navOpened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <LoginBrandMark size={30} radius={8}>
              <Text fw={800} c="var(--easy-color-text-inverse)">P</Text>
            </LoginBrandMark>
            <Title order={4} c="var(--easy-color-text)" textWrap="nowrap">{t.domain.app.title}</Title>
            <Text size="xs" c="dimmed" visibleFrom="sm" truncate>
              {t.domain.app.subtitle}
            </Text>
          </Group>
          <Group gap="xs" wrap="nowrap">
            <AppHeaderActions />
            {isAuthenticated && session && (
              <UiButton variant="subtle" size="xs" radius="md" onClick={() => void logout()}>
                {t.common.label.logout}
              </UiButton>
            )}
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Navbar p="xs" bg="var(--easy-color-surface)" bd="0 1px 0 0 solid var(--easy-color-border)">
        <Text size="xs" c="dimmed" mt="xs" mb={4} px="xs" fw={700}>
          {t.domain.nav.section}
        </Text>
        <NavLink
          component={Link}
          to="/"
          label={t.nav.cockpit}
          active={location.pathname === '/'}
        />
        <NavLink
          component={Link}
          to="/self-evaluations"
          label={t.domain.nav.selfEvaluation}
          active={location.pathname.startsWith('/self-evaluations')}
        />
        <NavLink
          component={Link}
          to="/personal-okrs"
          label={t.domain.nav.personalOkr}
          active={location.pathname.startsWith('/personal-okrs')}
        />
        <NavLink
          component={Link}
          to="/reflection-journals"
          label={t.domain.nav.reflectionJournal}
          active={location.pathname.startsWith('/reflection-journals')}
        />
        <NavLink
          component={Link}
          to="/mentor-feedbacks"
          label={t.domain.nav.mentorFeedback}
          active={location.pathname.startsWith('/mentor-feedbacks')}
        />
        <NavLink
          component={Link}
          to="/hr/cycles"
          label={t.nav.hr.cycles}
          active={location.pathname.startsWith('/hr/cycles')}
        />
        <NavLink
          component={Link}
          to="/my/kpi"
          label={t.nav.kpi.my}
          active={location.pathname.startsWith('/my/kpi')}
        />
        <NavLink
          component={Link}
          to="/kpi/alignment"
          label={t.nav.kpi.alignment}
          active={location.pathname.startsWith('/kpi/alignment')}
        />
        <NavLink
          component={Link}
          to="/manager/kpi-tree"
          label={t.nav.kpi.managerTree}
          active={location.pathname.startsWith('/manager/kpi-tree')}
        />
        <NavLink
          component={Link}
          to="/director/kpi-tree"
          label={t.nav.kpi.directorTree}
          active={location.pathname.startsWith('/director/kpi-tree')}
        />
        <NavLink
          component={Link}
          to="/my/self-review"
          label={t.nav.review.self}
          active={location.pathname.startsWith('/my/self-review')}
        />
        <NavLink
          component={Link}
          to="/manager/review"
          label={t.nav.review.manager}
          active={location.pathname.startsWith('/manager/review')}
        />
        <NavLink
          component={Link}
          to="/hr/calibration-sessions"
          label={t.nav.calibration.sessions}
          active={location.pathname.startsWith('/hr/calibration-sessions')}
        />
        <NavLink
          component={Link}
          to="/director/calibration"
          label={t.nav.calibration.director}
          active={location.pathname.startsWith('/director/calibration')}
        />
        <NavLink
          component={Link}
          to="/hr/calibration-analytics"
          label={t.nav.calibration.analytics}
          active={location.pathname.startsWith('/hr/calibration-analytics')}
        />
        <NavLink
          component={Link}
          to="/hr/distribution"
          label={t.nav.calibration.distribution}
          active={location.pathname.startsWith('/hr/distribution')}
        />
        <NavLink
          component={Link}
          to="/hr/reports"
          label={t.nav.report.hr}
          active={location.pathname.startsWith('/hr/reports')}
        />
        <NavLink
          component={Link}
          to="/my/report"
          label={t.nav.report.my}
          active={location.pathname.startsWith('/my/report')}
        />
        {isSuperAdmin && (
          <NavLink
            component={Link}
            to="/admin/tenants"
            label={t.nav.admin.tenants}
            active={location.pathname.startsWith('/admin/tenants')}
          />
        )}
      </AppShell.Navbar>
      <AppShell.Main bg="var(--easy-color-canvas)">
        <PageBoundary>
          <Routes>
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <CockpitPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/self-evaluations"
              element={
                <ProtectedRoute>
                  <SelfEvaluationPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/personal-okrs"
              element={
                <ProtectedRoute>
                  <PersonalOkrPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/reflection-journals"
              element={
                <ProtectedRoute>
                  <ReflectionJournalPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/mentor-feedbacks"
              element={
                <ProtectedRoute>
                  <MentorFeedbackPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/hr/cycles"
              element={
                <ProtectedRoute>
                  <CyclesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/my/kpi"
              element={
                <ProtectedRoute>
                  <MyKpiPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/kpi/alignment"
              element={
                <ProtectedRoute>
                  <GoalAlignmentPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/manager/kpi-tree"
              element={
                <ProtectedRoute>
                  <ManagerKpiTreePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/director/kpi-tree"
              element={
                <ProtectedRoute>
                  <DirectorKpiTreePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/my/self-review"
              element={
                <ProtectedRoute>
                  <MySelfReviewPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/manager/review"
              element={
                <ProtectedRoute>
                  <ManagerReviewPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/hr/calibration-sessions"
              element={
                <ProtectedRoute>
                  <HrCalibrationSessionsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/director/calibration"
              element={
                <ProtectedRoute>
                  <DirectorCalibrationPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/hr/calibration-analytics"
              element={
                <ProtectedRoute>
                  <CalibrationAnalyticsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/hr/distribution"
              element={
                <ProtectedRoute>
                  <HrDistributionPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/hr/reports"
              element={
                <ProtectedRoute>
                  <HrReportsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/my/report"
              element={
                <ProtectedRoute>
                  <MyReportPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin/tenants"
              element={
                <ProtectedRoute>
                  <AdminTenantsPage />
                </ProtectedRoute>
              }
            />
          </Routes>
        </PageBoundary>
      </AppShell.Main>
    </AppShell>
  );
}

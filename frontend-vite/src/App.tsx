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
 * - i18n ko/en/ja/zh-CN/vi + 다크모드 토글
 * - ApiError SoT (BE-CC-5 ApiException 정합)
 *
 * 누적 정합 — 단계 0 `58bf09d` + 단계 1 `b83acac` + 단계 2 `6895ba9` + 단계 5 SMB `27108e3`.
 * jobeval 단계 4 cutover `cc1bc03` 패턴 정합.
 */
import { lazy } from 'react';
import {
  Text,
  Group,
  Burger,
} from '@easy/ui-components/mantine';
import { useDisclosure } from '@mantine/hooks';
import { Route, Routes, Link, useLocation } from 'react-router-dom';
import {
  LoginBrandMark,
  SuiteShellBrandGroup,
  SuiteShellBrandTitle,
  SuiteShellHeader,
  SuiteShellHeaderActions,
  SuiteShellMain,
  SuiteShellNavbar,
  SuiteShellNavLink,
  SuiteShellRoot,
  SuiteShellSectionTitle,
  UiButton,
} from '@easy/ui-components';

import { PageBoundary } from './shared/PageBoundary';
import { AppHeaderActions } from './shared/AppHeaderActions';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { useAuth } from './auth/AuthProvider';
import { useT } from './i18n';

// STD-FE-LAZY — 모든 페이지 lazy import
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
const EvaluationWorkspacePage = lazy(() =>
  import('./features/evaluation-workspace/EvaluationWorkspacePage').then((m) => ({ default: m.EvaluationWorkspacePage })),
);
const ProgramListPage = lazy(() =>
  import('./features/evaluation-programs/pages/ProgramListPage').then((m) => ({ default: m.ProgramListPage })),
);
const ProgramSetupPage = lazy(() =>
  import('./features/evaluation-programs/pages/ProgramSetupPage').then((m) => ({ default: m.ProgramSetupPage })),
);
const ProgramOperationsPage = lazy(() =>
  import('./features/evaluation-programs/pages/ProgramOperationsPage').then((m) => ({ default: m.ProgramOperationsPage })),
);
const EvaluationWorkPage = lazy(() =>
  import('./features/evaluation-programs/pages/EvaluationWorkPage').then((m) => ({ default: m.EvaluationWorkPage })),
);
const EmployeePreviewPage = lazy(() =>
  import('./features/evaluation-programs/pages/EmployeePreviewPage').then((m) => ({ default: m.EmployeePreviewPage })),
);
const CatalogLibraryPage = lazy(() =>
  import('./features/evaluation-programs/pages/CatalogLibraryPage').then((m) => ({ default: m.CatalogLibraryPage })),
);
const DepartmentGoalsPage = lazy(() =>
  import('./features/evaluation-programs/pages/DepartmentGoalsPage').then((m) => ({ default: m.DepartmentGoalsPage })),
);
const TaskBoardPage = lazy(() =>
  import('./features/evaluation-programs/pages/TaskBoardPage').then((m) => ({ default: m.TaskBoardPage })),
);
const InterviewWorkspacePage = lazy(() =>
  import('./features/evaluation-programs/pages/InterviewWorkspacePage').then((m) => ({ default: m.InterviewWorkspacePage })),
);
const AnalyticsPage = lazy(() =>
  import('./features/evaluation-programs/pages/AnalyticsPage').then((m) => ({ default: m.AnalyticsPage })),
);
const PersonalReportPage = lazy(() =>
  import('./features/evaluation-programs/pages/PersonalReportPage').then((m) => ({ default: m.PersonalReportPage })),
);
const ReviewerWorkPage = lazy(() =>
  import('./features/evaluation-programs/pages/ReviewerWorkPage').then((m) => ({ default: m.ReviewerWorkPage })),
);
const ReviewerQueuePage = lazy(() =>
  import('./features/evaluation-programs/pages/ReviewerQueuePage').then((m) => ({ default: m.ReviewerQueuePage })),
);
const NotificationInboxPage = lazy(() =>
  import('./features/evaluation-programs/pages/NotificationInboxPage').then((m) => ({ default: m.NotificationInboxPage })),
);

export default function App(): React.ReactNode {
  const location = useLocation();
  const [navOpened, { toggle }] = useDisclosure();
  const { isAuthenticated, logout, session } = useAuth();
  const t = useT();
  // SUPER_ADMIN 전용 내비 노출 — JwtAuthFilter 가 roles claim 을 prefix 없이 발급 (BE 정합).
  const isSuperAdmin = session?.roles.includes('SUPER_ADMIN') ?? false;
  const isEvaluationAdmin = session?.roles.some((role) =>
    ['HR_ADMIN', 'SUPER_ADMIN'].includes(role),
  ) ?? false;
  const canOperateEvaluation = session?.roles.some((role) =>
    ['HR_ADMIN', 'SUPER_ADMIN', 'DIRECTOR', 'MANAGER'].includes(role),
  ) ?? false;
  const canViewAnalytics = session?.roles.some((role) =>
    ['HR_ADMIN', 'SUPER_ADMIN', 'DIRECTOR'].includes(role),
  ) ?? false;

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
    <SuiteShellRoot
      header={{ height: 56 }}
      navbar={{ width: 220, breakpoint: 'sm', collapsed: { mobile: !navOpened } }}
      padding="md"
    >
      <SuiteShellHeader px="sm">
        <Group justify="space-between" h="100%" wrap="nowrap">
          <Group gap="sm" wrap="nowrap" miw={0}>
            <Burger opened={navOpened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <LoginBrandMark size={30} mobileSize={26} radius={8}>P</LoginBrandMark>
            <SuiteShellBrandGroup gap="sm">
              <SuiteShellBrandTitle textWrap="nowrap" visibleFrom="sm">{t.workspace.appTitle}</SuiteShellBrandTitle>
              <Text size="sm" fw={700} hiddenFrom="sm">{t.workspace.copy.mobileAppTitle}</Text>
              <Text size="xs" c="dimmed" visibleFrom="md" truncate>
                {t.workspace.appSubtitle}
              </Text>
            </SuiteShellBrandGroup>
          </Group>
          <SuiteShellHeaderActions>
            <AppHeaderActions />
            {isAuthenticated && session && (
              <UiButton variant="subtle" size="xs" radius="md" onClick={() => void logout()}>
                {t.common.label.logout}
              </UiButton>
            )}
          </SuiteShellHeaderActions>
        </Group>
      </SuiteShellHeader>
      <SuiteShellNavbar p="xs">
        <SuiteShellSectionTitle mt="xs" mb={4}>
          {t.program.nav.section}
        </SuiteShellSectionTitle>
        <SuiteShellNavLink component={Link} to="/evaluations" label={t.program.nav.myEvaluations} active={location.pathname === '/' || location.pathname === '/evaluations' || location.pathname.startsWith('/evaluations/')} />
        {canOperateEvaluation && <SuiteShellNavLink component={Link} to="/review-queue" label={t.program.nav.reviewQueue} active={location.pathname.startsWith('/review-queue')} />}
        {isEvaluationAdmin && <SuiteShellNavLink component={Link} to="/admin/evaluation-programs" label={t.program.nav.programs} active={location.pathname.startsWith('/admin/evaluation-programs')} />}
        {isEvaluationAdmin && <SuiteShellNavLink component={Link} to="/evaluation-resources/catalogs" label={t.program.nav.catalogs} active={location.pathname.startsWith('/evaluation-resources/catalogs')} />}
        <SuiteShellNavLink component={Link} to="/department-goals" label={t.program.nav.departmentGoals} active={location.pathname.startsWith('/department-goals')} />
        <SuiteShellNavLink component={Link} to="/performance-tasks" label={t.program.nav.tasks} active={location.pathname.startsWith('/performance-tasks')} />
        <SuiteShellNavLink component={Link} to="/interviews" label={t.program.nav.interviews} active={location.pathname.startsWith('/interviews')} />
        <SuiteShellNavLink component={Link} to="/evaluation-reports" label={t.program.nav.reports} active={location.pathname.startsWith('/evaluation-reports')} />
        <SuiteShellNavLink component={Link} to="/evaluation-notifications" label={t.program.notifications.nav} active={location.pathname.startsWith('/evaluation-notifications')} />
        {canViewAnalytics && <SuiteShellNavLink component={Link} to="/evaluation-analytics" label={t.program.nav.analytics} active={location.pathname.startsWith('/evaluation-analytics')} />}
        <SuiteShellSectionTitle mt="xs" mb={4}>
          {t.domain.nav.section}
        </SuiteShellSectionTitle>
        <SuiteShellNavLink component={Link} to="/legacy-evaluations" label={canOperateEvaluation ? t.workspace.navOperate : t.workspace.navMy} active={location.pathname.startsWith('/legacy-evaluations') || location.pathname.startsWith('/workspace')} />
        {isEvaluationAdmin && (
          <SuiteShellNavLink
            component={Link}
            to="/hr/cycles"
            label={t.nav.hr.cycles}
            active={location.pathname.startsWith('/hr/cycles')}
          />
        )}
        {isSuperAdmin && (
          <SuiteShellNavLink
            component={Link}
            to="/admin/tenants"
            label={t.nav.admin.tenants}
            active={location.pathname.startsWith('/admin/tenants')}
          />
        )}
      </SuiteShellNavbar>
      <SuiteShellMain bg="var(--easy-color-canvas)">
        <PageBoundary>
          <Routes>
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <ProgramListPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/legacy-evaluations"
              element={
                <ProtectedRoute>
                  <EvaluationWorkspacePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/workspace/:cycleId"
              element={
                <ProtectedRoute>
                  <EvaluationWorkspacePage />
                </ProtectedRoute>
              }
            />
            <Route path="/evaluations" element={<ProtectedRoute><ProgramListPage /></ProtectedRoute>} />
            <Route path="/evaluations/:programId" element={<ProtectedRoute><EvaluationWorkPage /></ProtectedRoute>} />
            <Route path="/review-queue" element={<ProtectedRoute><ReviewerQueuePage /></ProtectedRoute>} />
            <Route path="/admin/evaluation-programs" element={<ProtectedRoute><ProgramListPage /></ProtectedRoute>} />
            <Route path="/admin/evaluation-programs/:programId/setup" element={<ProtectedRoute><ProgramSetupPage /></ProtectedRoute>} />
            <Route path="/admin/evaluation-programs/:programId/setup/:tab" element={<ProtectedRoute><ProgramSetupPage /></ProtectedRoute>} />
            <Route path="/admin/evaluation-programs/:programId/operations" element={<ProtectedRoute><ProgramOperationsPage /></ProtectedRoute>} />
            <Route path="/admin/evaluation-programs/:programId/participants/:participantId/preview" element={<ProtectedRoute><EmployeePreviewPage /></ProtectedRoute>} />
            <Route path="/admin/evaluation-programs/:programId/participants/:participantId/review/:round" element={<ProtectedRoute><ReviewerWorkPage /></ProtectedRoute>} />
            <Route path="/evaluation-resources/catalogs" element={<ProtectedRoute><CatalogLibraryPage /></ProtectedRoute>} />
            <Route path="/department-goals" element={<ProtectedRoute><DepartmentGoalsPage /></ProtectedRoute>} />
            <Route path="/performance-tasks" element={<ProtectedRoute><TaskBoardPage /></ProtectedRoute>} />
            <Route path="/interviews" element={<ProtectedRoute><InterviewWorkspacePage /></ProtectedRoute>} />
            <Route path="/evaluation-reports" element={<ProtectedRoute><PersonalReportPage /></ProtectedRoute>} />
            <Route path="/evaluation-notifications" element={<ProtectedRoute><NotificationInboxPage /></ProtectedRoute>} />
            <Route path="/evaluation-analytics" element={<ProtectedRoute><AnalyticsPage /></ProtectedRoute>} />
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
      </SuiteShellMain>
    </SuiteShellRoot>
  );
}

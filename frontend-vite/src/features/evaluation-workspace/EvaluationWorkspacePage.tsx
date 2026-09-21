import { Stack } from '@easy/ui-components/mantine';
import { EmptyState, ErrorBoundary, LoadingState, PageHeader, UiAlert, UiBadge } from '@easy/ui-components';
import { useNavigate, useParams } from 'react-router-dom';

import {
  useEvaluationWorkspaceQuery,
  useWorkspaceCycleQuery,
  useWorkspaceCyclesQuery,
  useWorkspaceMeQuery,
} from '../../api/evaluationWorkspace';
import { useT } from '../../i18n';
import { CalibrationPanel } from './components/CalibrationPanel';
import { FeedbackOperationsPanel, ResultsAnalysisPanel } from './components/FeedbackPanels';
import { MyEvaluationPanel } from './components/MyEvaluationPanel';
import { OperationsPanel, ParticipantPanel } from './components/OperationsPanels';
import { BlockerPanel, CycleSwitcher, PhaseRail } from './components/WorkspaceOverview';
import { TeamTasksPanel } from './components/TeamTasksPanel';
import { statusLabel } from './workspaceLabels';
import { workspaceErrorMessage } from './workspaceError';

const administrativeRoles = new Set(['HR_ADMIN', 'SUPER_ADMIN']);
const calibrationRoles = new Set(['HR_ADMIN', 'SUPER_ADMIN', 'DIRECTOR']);
const rosterRoles = new Set(['HR_ADMIN', 'SUPER_ADMIN', 'DIRECTOR', 'MANAGER']);

export function EvaluationWorkspacePage(): React.ReactNode {
  const params = useParams<{ cycleId: string }>();
  const navigate = useNavigate();
  const t = useT();
  const cyclesQuery = useWorkspaceCyclesQuery();
  const firstCycleId = cyclesQuery.data?.content?.[0]?.id ?? null;
  const cycleId = params.cycleId ?? firstCycleId;
  const cycleQuery = useWorkspaceCycleQuery(cycleId);
  const meQuery = useWorkspaceMeQuery();
  const workspaceQuery = useEvaluationWorkspaceQuery(cycleId);

  if (cyclesQuery.isLoading || meQuery.isLoading || (cycleId != null && cycleQuery.isLoading)) {
    return <LoadingState message={t.workspace.preparing} />;
  }

  if (cyclesQuery.isError || meQuery.isError || cycleQuery.isError) {
    return (
      <UiAlert color="red">
        {workspaceErrorMessage(t, cyclesQuery.error ?? meQuery.error ?? cycleQuery.error)}
      </UiAlert>
    );
  }

  if (!cycleId || !cycleQuery.data || !meQuery.data) {
    const canCreateCycle = meQuery.data != null && administrativeRoles.has(meQuery.data.role);
    return (
      <EmptyState
        title={canCreateCycle ? t.workspace.copy.noCycleTitle : t.workspace.copy.noAssignedCycleTitle}
        description={canCreateCycle ? t.workspace.copy.noCycleDescription : t.workspace.copy.noAssignedCycleDescription}
        action={canCreateCycle
          ? { label: t.workspace.copy.createCycle, onClick: () => navigate('/hr/cycles') }
          : undefined}
      />
    );
  }

  const role = meQuery.data.role;
  const isAdmin = administrativeRoles.has(role);
  const canViewRoster = rosterRoles.has(role);
  const canCalibrate = calibrationRoles.has(role);

  return (
    <ErrorBoundary>
      <PageHeader
        title={cycleQuery.data.name}
        description={`${cycleQuery.data.periodStart} ~ ${cycleQuery.data.periodEnd} · ${meQuery.data.displayName}`}
        actions={
          <UiBadge variant="light" color={cycleQuery.data.status === 'FINALIZED' ? 'green' : 'blue'}>
            {statusLabel(t, cycleQuery.data.status)}
          </UiBadge>
        }
      />
      <Stack gap="md">
        <CycleSwitcher currentId={cycleId} />
        <PhaseRail status={cycleQuery.data.status} />
        {workspaceQuery.isError ? (
          <UiAlert color="red">{workspaceErrorMessage(t, workspaceQuery.error)}</UiAlert>
        ) : workspaceQuery.isLoading ? (
          <LoadingState message={t.workspace.copy.loadingMyWork} />
        ) : workspaceQuery.data ? (
          <>
            {workspaceQuery.data.blockers.length > 0 && (
              <BlockerPanel blockers={workspaceQuery.data.blockers} />
            )}
            {isAdmin && cycleQuery.data.status !== 'FINALIZED' && (
              <OperationsPanel cycleId={cycleId} status={cycleQuery.data.status} />
            )}
            {canViewRoster && (
              <ParticipantPanel
                cycleId={cycleId}
                editable={isAdmin && ['PLANNED', 'ACTIVE'].includes(cycleQuery.data.status)}
              />
            )}
            {(role === 'MANAGER' || role === 'DIRECTOR') && <TeamTasksPanel cycleId={cycleId} />}
            {canCalibrate && cycleQuery.data.status === 'CALIBRATION' && (
              <CalibrationPanel cycleId={cycleId} />
            )}
            {canCalibrate && <ResultsAnalysisPanel cycleId={cycleId} />}
            {(isAdmin || role === 'MANAGER') && <FeedbackOperationsPanel cycleId={cycleId} role={role} />}
            <MyEvaluationPanel cycleId={cycleId} workspace={workspaceQuery.data} />
          </>
        ) : null}
      </Stack>
    </ErrorBoundary>
  );
}

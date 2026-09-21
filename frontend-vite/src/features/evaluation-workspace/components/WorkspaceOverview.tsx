import { Select, Stack, Text } from '@easy/ui-components/mantine';
import { UiAlert, WorkflowPhaseRail } from '@easy/ui-components';
import { useNavigate } from 'react-router-dom';

import type { CycleStatus } from '../../../api/cycles';
import type { GateBlocker } from '../../../api/evaluationWorkspace';
import { useWorkspaceCyclesQuery } from '../../../api/evaluationWorkspace';
import { useT } from '../../../i18n';
import { evaluationWorkspacePhases, getPhaseGate } from '../phaseMap';
import { blockerLabel, phaseDescription, phaseLabel } from '../workspaceLabels';

export function CycleSwitcher({ currentId }: { currentId: string }): React.ReactNode {
  const navigate = useNavigate();
  const t = useT();
  const cyclesQuery = useWorkspaceCyclesQuery();
  return (
    <Select
      label={t.workspace.cycle}
      value={currentId}
      data={(cyclesQuery.data?.content ?? []).map((cycle) => ({ value: cycle.id, label: cycle.name }))}
      onChange={(next) => {
        if (next) navigate(`/workspace/${next}`);
      }}
    />
  );
}

export function PhaseRail({ status }: { status: CycleStatus }): React.ReactNode {
  const t = useT();
  const gate = getPhaseGate(status);
  const currentIndex = evaluationWorkspacePhases.findIndex((phase) => phase.key === gate.key);
  return (
    <WorkflowPhaseRail
      label={t.workspace.copy.currentPhase}
      steps={evaluationWorkspacePhases.map((phase, index) => ({
        id: phase.key,
        label: phaseLabel(t, phase.key),
        state: index < currentIndex || gate.state === 'complete'
          ? 'complete'
          : index === currentIndex && gate.state === 'active'
            ? 'current'
            : index === currentIndex && gate.state === 'blocked'
              ? 'blocked'
              : 'upcoming',
      }))}
      description={`${phaseLabel(t, gate.key)} · ${phaseDescription(t, gate.key)}`}
    />
  );
}

export function BlockerPanel({ blockers }: { blockers: GateBlocker[] }): React.ReactNode {
  const t = useT();
  return (
    <UiAlert color="yellow" title={t.workspace.copy.blockersTitle}>
      <Stack gap={4}>
        {blockers.map((blocker) => (
          <Text key={`${blocker.code}-${blocker.message}`} size="sm">
            {blockerLabel(t, blocker.code)}
          </Text>
        ))}
      </Stack>
    </UiAlert>
  );
}

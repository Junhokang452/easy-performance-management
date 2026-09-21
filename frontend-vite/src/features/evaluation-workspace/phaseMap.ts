export type WorkspaceCycleStatus =
  | 'PLANNED'
  | 'ACTIVE'
  | 'GOAL_SETTING'
  | 'MID_REVIEW'
  | 'SELF_REVIEW'
  | 'MANAGER_REVIEW'
  | 'CALIBRATION'
  | 'FINALIZED'
  | 'CANCELLED';

export type WorkspacePhaseKey =
  | 'setup'
  | 'participants'
  | 'goals'
  | 'checkIn'
  | 'selfReview'
  | 'teamReview'
  | 'calibration'
  | 'results'
  | 'closure';

export type PhaseState = 'blocked' | 'active' | 'complete';

export interface WorkspacePhase {
  key: WorkspacePhaseKey;
  status: WorkspaceCycleStatus;
}

export interface PhaseGate extends WorkspacePhase {
  state: PhaseState;
}

export const evaluationWorkspacePhases: readonly WorkspacePhase[] = [
  { key: 'setup', status: 'ACTIVE' },
  { key: 'participants', status: 'ACTIVE' },
  { key: 'goals', status: 'GOAL_SETTING' },
  { key: 'checkIn', status: 'MID_REVIEW' },
  { key: 'selfReview', status: 'SELF_REVIEW' },
  { key: 'teamReview', status: 'MANAGER_REVIEW' },
  { key: 'calibration', status: 'CALIBRATION' },
  { key: 'results', status: 'FINALIZED' },
  { key: 'closure', status: 'FINALIZED' },
];

export function getPhaseGate(status: WorkspaceCycleStatus): PhaseGate {
  const keyByStatus: Record<WorkspaceCycleStatus, WorkspacePhaseKey> = {
    PLANNED: 'setup',
    ACTIVE: 'setup',
    GOAL_SETTING: 'goals',
    MID_REVIEW: 'checkIn',
    SELF_REVIEW: 'selfReview',
    MANAGER_REVIEW: 'teamReview',
    CALIBRATION: 'calibration',
    FINALIZED: 'closure',
    CANCELLED: 'closure',
  };
  const phase = evaluationWorkspacePhases.find(
    (candidate) => candidate.key === keyByStatus[status],
  ) ?? evaluationWorkspacePhases[0];
  const state: PhaseState = status === 'FINALIZED' ? 'complete' : status === 'PLANNED' || status === 'CANCELLED' ? 'blocked' : 'active';
  return { ...phase, state };
}

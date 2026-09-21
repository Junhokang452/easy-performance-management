import type { CycleStatus } from '../../api/cycles';

const NEXT_STATUS: Partial<Record<CycleStatus, CycleStatus>> = {
  GOAL_SETTING: 'MID_REVIEW',
  MID_REVIEW: 'SELF_REVIEW',
  SELF_REVIEW: 'MANAGER_REVIEW',
  MANAGER_REVIEW: 'CALIBRATION',
};

export function nextWorkflowStatus(status: CycleStatus): CycleStatus | null {
  return NEXT_STATUS[status] ?? null;
}

export function isValidGoalDraft(title: string, weightPercent: number): boolean {
  return title.trim().length > 0
    && Number.isFinite(weightPercent)
    && weightPercent > 0
    && weightPercent <= 100;
}

export function distributionTotal(distribution: Record<string, number>): number {
  return ['S', 'A', 'B', 'C', 'D'].reduce((sum, grade) => sum + (distribution[grade] ?? 0), 0);
}

export function isValidDistribution(distribution: Record<string, number>): boolean {
  return Object.values(distribution).every((value) => Number.isFinite(value) && value >= 0 && value <= 1)
    && Math.abs(distributionTotal(distribution) - 1) < 0.0001;
}

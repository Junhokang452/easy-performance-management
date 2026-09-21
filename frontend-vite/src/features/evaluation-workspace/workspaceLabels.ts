import type { EmployeeSummary } from '../../api/evaluationWorkspace';
import type { I18nDict } from '../../i18n/ko';
import type { WorkspacePhaseKey } from './phaseMap';

export function employeeLabel(employee: EmployeeSummary): string {
  return `${employee.name} · ${employee.employeeNo}`;
}

export function blockerLabel(t: I18nDict, code: string): string {
  return Object.hasOwn(t.blockers, code)
    ? t.blockers[code as keyof I18nDict['blockers']]
    : t.workspace.copy.blockersTitle;
}

export function statusLabel(t: I18nDict, status: string | null): string {
  if (status == null) return t.workspace.copy.statusEmpty;
  const key = `status_${status}`;
  if (Object.hasOwn(t.workspace.copy, key)) {
    return t.workspace.copy[key as keyof I18nDict['workspace']['copy']];
  }
  return t.workspace.copy.statusEmpty;
}

export function phaseLabel(t: I18nDict, key: WorkspacePhaseKey): string {
  const labels: Record<WorkspacePhaseKey, string> = {
    setup: t.workspace.setup,
    participants: t.workspace.participantsPhase,
    goals: t.workspace.goals,
    checkIn: t.workspace.checkIn,
    selfReview: t.workspace.selfReview,
    teamReview: t.workspace.teamReview,
    calibration: t.workspace.calibration,
    results: t.workspace.results,
    closure: t.workspace.closure,
  };
  return labels[key];
}

export function phaseDescription(t: I18nDict, key: WorkspacePhaseKey): string {
  const descriptions: Record<WorkspacePhaseKey, string> = {
    setup: t.workspace.copy.setupDescription,
    participants: t.workspace.copy.participantsDescription,
    goals: t.workspace.copy.goalsDescription,
    checkIn: t.workspace.copy.checkInDescription,
    selfReview: t.workspace.copy.selfReviewPhaseDescription,
    teamReview: t.workspace.copy.teamReviewDescription,
    calibration: t.workspace.copy.calibrationDescription,
    results: t.workspace.copy.resultsDescription,
    closure: t.workspace.copy.closureDescription,
  };
  return descriptions[key];
}

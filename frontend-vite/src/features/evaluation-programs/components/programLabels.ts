import type { I18nDict } from '../../../i18n/ko';
import type { EvaluationKind, ProgramStage, ProgramStageStatus, ProgramStatus } from '../api/programs';

export function programStatusLabel(t: I18nDict, status: ProgramStatus): string {
  return {
    DRAFT: t.program.programs.statusDraft,
    OPEN: t.program.programs.statusOpen,
    FINALIZED: t.program.programs.statusFinalized,
    CANCELLED: t.program.programs.statusCancelled,
  }[status];
}

export function programStageStatusLabel(t: I18nDict, status: ProgramStageStatus): string {
  return { NOT_STARTED: t.program.stageStatus.notStarted, READY: t.program.stageStatus.ready, IN_PROGRESS: t.program.stageStatus.inProgress, COMPLETED: t.program.stageStatus.completed, SKIPPED: t.program.stageStatus.skipped, BLOCKED: t.program.stageStatus.blocked }[status];
}

export function evaluationKindLabel(t: I18nDict, kind: EvaluationKind): string {
  return {
    PERFORMANCE: t.program.programs.performance,
    COMPETENCY: t.program.programs.competency,
    COMBINED: t.program.programs.combined,
    MULTI_RATER: t.program.programs.multiRater,
  }[kind];
}

export function programStageLabel(t: I18nDict, stage: ProgramStage, round?: number): string {
  if (stage === 'REVIEW') {
    return round === 2 ? t.program.stages.secondReview : round === 3 ? t.program.stages.thirdReview : t.program.stages.firstReview;
  }
  return {
    GOAL: t.program.stages.goal,
    INTERMEDIATE: t.program.stages.midReview,
    SELF_REVIEW: t.program.stages.selfReview,
    CALCULATION: t.program.programs.calculation,
    CALIBRATION: t.program.stages.calibration,
    FEEDBACK: t.program.stages.feedback,
  }[stage];
}

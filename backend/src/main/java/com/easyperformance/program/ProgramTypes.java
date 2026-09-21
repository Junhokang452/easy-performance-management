package com.easyperformance.program;

/** Stable wire enums for the configurable evaluation-program aggregate. */
public final class ProgramTypes {
    private ProgramTypes() {}

    public enum ProgramStatus { DRAFT, OPEN, FINALIZED, CANCELLED }
    public enum EvaluationKind { PERFORMANCE, COMPETENCY, COMBINED, MULTI_RATER }
    public enum ProgramStage { GOAL, INTERMEDIATE, SELF_REVIEW, REVIEW, CALCULATION, CALIBRATION, FEEDBACK }
    public enum GoalMode { AGREEMENT, SELF_REPORT }
    public enum FormMode { DEFINITION_ONLY, DEFINITION_AND_ACHIEVEMENT_LEVELS }
    public enum ItemAssignmentMode { AGREEMENT, DESIGNATED }
    public enum EvaluationMethod { ABSOLUTE, RELATIVE }
    public enum ScaleUse { INPUT, RESULT, DEPARTMENT_RESULT }
    public enum ScaleKind { SCORE, GRADE }
    public enum ConditionField { ORG_UNIT, POSITION, GRADE, JOB, EMPLOYMENT_TYPE, EMPLOYEE }
    public enum ConditionOperator { IN, NOT_IN, EQUALS, NOT_EQUALS }
    public enum ParticipantStatus { ACTIVE, EXCLUDED, DELETED }
    public enum ProgramStageStatus { NOT_STARTED, READY, IN_PROGRESS, COMPLETED, SKIPPED, BLOCKED }
    public enum ReviewerRole { SELF, AGREEMENT_REVIEWER, CHECKER, REVIEWER, ADJUSTER, FINAL_FEEDBACK }
    public enum AssignmentStatus { ASSIGNED, IN_PROGRESS, COMPLETED, REVOKED }
    public enum ReviewerAssignmentOrigin { MANUAL, XLSX, HCM_MANAGER }
    public enum ReviewerLinePreviewStatus { READY, SOURCE_MISSING, BLOCKED, SKIPPED_EXISTING }
    public enum ReviewerLineApplyStatus { APPLIED, SOURCE_MISSING, BLOCKED, SKIPPED_EXISTING }
    public enum GoalStatus { DRAFT, AGREEMENT_REQUESTED, AGREED, RETURNED, SELF_REPORTED }
    public enum SubmissionStatus { DRAFT, COMPLETED, INVALIDATED }
    public enum CalculationStatus { DRAFT, FINAL }
    public enum AdjustmentStatus { DRAFT, COMPLETED }
    public enum FeedbackStatus { DRAFT, DELIVERED, AGREED, APPEALED, RESOLVED }
    public enum AppealResolution { UPHELD, SCORE_ADJUSTED }
    public enum AdjustmentTarget { ALL, ABSOLUTE_ONLY, RELATIVE_ONLY }
    public enum AdjustmentMethod { NONE, MEAN, STANDARD_DEVIATION }
    public enum PopulationBasis { DEPARTMENT, DEPARTMENT_PERFORMANCE_GROUP }
    public enum MemberResultVisibility { SCORE_AND_GRADE, GRADE_ONLY }
    public enum PreviousRoundVisibility { HIDDEN, SCORE_ONLY, SCORE_AND_OPINION }
    public enum ProgramEventType {
        PROGRAM_CREATED, PROGRAM_BASIC_UPDATED, DEFINITION_REVISED, OPENED, PARTICIPANT_CREATED, PARTICIPANT_CHANGED,
        REVIEWERS_CHANGED, STAGE_CHANGED, GOAL_CHANGED, RESPONSE_COMPLETED, CALCULATED,
        ADJUSTED, FEEDBACK_CHANGED, FEEDBACK_INVALIDATED, FINALIZED, FINALIZATION_CANCELLED,
        RESULT_PUBLISHED, EMPLOYEE_PREVIEWED, REVIEWER_LINE_APPLIED, KPI_LINK_APPLIED, REMINDERS_QUEUED,
        RESULT_PDF_EXPORTED
    }
    public enum NotificationChannel { IN_APP, EMAIL }
    public enum NotificationStatus { READY, SENDING, CONFIG_REQUIRED, SENT, FAILED, READ }
}

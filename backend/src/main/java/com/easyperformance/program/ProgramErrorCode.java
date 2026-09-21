package com.easyperformance.program;

import com.easyware.platform.error.ErrorCodeContract;

public enum ProgramErrorCode implements ErrorCodeContract {
    PROGRAM_NOT_FOUND("E9804453",404), PARTICIPANT_NOT_FOUND("E9804454",404),
    GOAL_NOT_FOUND("E9804455",404), SUBMISSION_NOT_FOUND("E9804456",404),
    ADJUSTMENT_NOT_FOUND("E9804457",404), PROGRAM_FEEDBACK_NOT_FOUND("E9804458",404),
    PROGRAM_KPI_LINK_NOT_FOUND("E9804459",404),
    PROGRAM_FORBIDDEN("E9804302",403), PROGRAM_INVALID("E9804256",422),
    PROGRAM_STAGE_INVALID("E9804257",422), PROGRAM_TYPE_UNSUPPORTED("E9804258",422),
    PROGRAM_LOCKED("E9804937",409), PROGRAM_INCOMPLETE("E9804938",409),
    PROGRAM_DUPLICATE("E9804939",409), RESULT_NOT_PUBLISHED("E9804940",409),
    REVIEWER_LINE_STALE("E9804941",409), PROGRAM_KPI_LINK_STALE("E9804942",409),
    PROGRAM_KPI_LINK_CONFLICT("E9804943",409), PROGRAM_REMINDER_STALE("E9804944",409),
    PROGRAM_REMINDER_CONFLICT("E9804945",409);
    private final String code; private final int status;
    ProgramErrorCode(String code,int status){this.code=code;this.status=status;}
    public String code(){return code;}public int httpStatus(){return status;}public String messageKey(){return "error."+code;}
}

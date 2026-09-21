package com.easyperformance.resources;

import com.easyware.platform.error.ErrorCodeContract;

/** Product-local error codes for evaluation support resources. */
public enum ResourceErrorCode implements ErrorCodeContract {
    RESOURCE_NOT_FOUND("E9804460", 404),
    RESOURCE_FORBIDDEN("E9804360", 403),
    RESOURCE_INVALID("E9804260", 422),
    RESOURCE_CONFLICT("E9804960", 409),
    RESOURCE_LOCKED("E9804961", 409),
    ATTACHMENT_TOO_LARGE("E9804261", 422),
    ATTACHMENT_TYPE_UNSUPPORTED("E9804262", 422);

    private final String code;
    private final int httpStatus;

    ResourceErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public int httpStatus() { return httpStatus; }
    @Override public String messageKey() { return "error." + code; }
}

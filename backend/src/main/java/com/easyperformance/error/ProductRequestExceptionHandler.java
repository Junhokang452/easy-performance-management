package com.easyperformance.error;

import com.easyware.platform.error.ApiError;
import com.easyware.platform.error.ErrorCode;
import com.easyware.platform.error.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.easyperformance.resources.ResourceErrorCode;

import java.util.Map;

/** Product-level request decoding guard kept ahead of the shared catch-all handler. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ProductRequestExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException exception,
                                                   HttpServletRequest request) {
        return ResponseEntity.unprocessableContent().body(ApiError.of(ResourceErrorCode.ATTACHMENT_TOO_LARGE,
            "Upload exceeds the allowed size", MDC.get(TraceIdFilter.MDC_KEY), request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception,
                                                HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(ErrorCode.BAD_REQUEST, "Invalid request body",
            MDC.get(TraceIdFilter.MDC_KEY), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException exception,
                                                  HttpServletRequest request) {
        String expected = exception.getRequiredType() == null ? "unknown" : exception.getRequiredType().getSimpleName();
        return ResponseEntity.badRequest().body(ApiError.of(ErrorCode.BAD_REQUEST, "Invalid request parameter",
            Map.of("parameter", exception.getName(), "expectedType", expected),
            MDC.get(TraceIdFilter.MDC_KEY), request.getRequestURI()));
    }
}

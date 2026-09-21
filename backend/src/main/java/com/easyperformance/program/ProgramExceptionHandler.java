package com.easyperformance.program;

import com.easyware.platform.error.ApiError;
import com.easyware.platform.error.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice
public class ProgramExceptionHandler {
    @ExceptionHandler(ProgramRuleViolation.class)
    public ResponseEntity<ApiError> rule(ProgramRuleViolation error, HttpServletRequest request) {
        return ResponseEntity.unprocessableEntity().body(ApiError.of(ProgramErrorCode.PROGRAM_INVALID,
            error.getMessage(), Map.of("rule", error.rule()), MDC.get(TraceIdFilter.MDC_KEY), request.getRequestURI()));
    }
}

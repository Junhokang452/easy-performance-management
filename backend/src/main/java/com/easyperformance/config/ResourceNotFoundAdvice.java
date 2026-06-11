/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.config;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 미존재 정적 자원/경로 → 404 복원 (mono FE 서빙, easy-hcm `029cd02` / easy-time 정합).
 *
 * <p>lib {@code com.easyware.platform.error.GlobalExceptionHandler} 의 catch-all
 * {@code @ExceptionHandler(Exception.class)} 가 Boot 기본 404 인 {@link NoResourceFoundException}
 * 을 500 으로 삼키는 quirk 보정 — 본 advice 가 최우선 순위로 404 를 돌려준다
 * (asset hash 불일치·favicon 부재 진단 명확화, 브라우저 콘솔 500 노이즈 제거).
 * lib 측 영구 정합은 표준 갱신 후보로 별도 (suite 공통).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResourceNotFoundAdvice {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "message", "Not Found"));
    }
}

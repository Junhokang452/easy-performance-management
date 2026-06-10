/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.sync;

import com.easyperformance.sync.EasyTalentResultPushService.PushResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EasyTalentResultPushController — 확정 평가 결과 talent 송신 수동 트리거 (G_TALENT_D3 #2).
 *
 * <p>경로 {@code /api/admin/sync/easytalent/push} — permitAll 인 {@code /api/internal/**} 과
 * 분리해 JWT 인증 흐름 통과 (hcm `c342838` 패턴). LIVE 안전: 미설정 시 503 + success=false.
 */
@RestController
@RequestMapping("/api/admin/sync/easytalent")
public class EasyTalentResultPushController {

    private static final Logger log = LoggerFactory.getLogger(EasyTalentResultPushController.class);

    private final EasyTalentResultPushService pushService;

    public EasyTalentResultPushController(EasyTalentResultPushService pushService) {
        this.pushService = pushService;
    }

    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> pushAll() {
        PushResult result = pushService.sendAll();

        Map<String, Object> body = new LinkedHashMap<>();
        if (!result.sent()) {
            body.put("success", false);
            body.put("message", "easytalent S2S not configured");
            log.warn("[easytalent-results] manual push rejected — not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }

        body.put("success", true);
        body.put("message", "easytalent performance-results pushed");
        body.put("results", result.results());
        body.put("httpStatus", result.httpStatus());
        return ResponseEntity.ok(body);
    }
}

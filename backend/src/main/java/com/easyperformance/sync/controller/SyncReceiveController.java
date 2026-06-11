/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.sync.controller;

import com.easyperformance.error.PerformanceErrorCode;
import com.easyperformance.sync.dto.SyncDtos.CoreMasterBatchRequest;
import com.easyperformance.sync.dto.SyncDtos.CoreMasterBatchResponse;
import com.easyperformance.sync.service.ReadModelSyncService;
import com.easyware.platform.error.ApiException;
import com.easyware.platform.hmac.HmacService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * S2S 동기화 수신 — hcm core-master 채널 (P0-S6, talent SyncReceiveController #1 사본).
 *
 * <p>채널 보안 (3중 가드):
 * <ul>
 *   <li>Bearer 토큰 — {@code performance.s2s.hcm.bearer-token} (constant-time 비교)</li>
 *   <li>HMAC-SHA256 raw body 서명 — header {@code X-Signature}, lib BE 15 {@link HmacService}
 *       (secret ≥32자 미만이면 부팅 fail-fast)</li>
 *   <li>bearer/secret 미설정 시 E9805301(503) — 호출 자체 차단. 보안 critical fail-fast 전역
 *       규칙의 예외인 optional plug-in 패턴 (미설정 환경은 기능만 비활성, LIVE 안전)</li>
 * </ul>
 *
 * <p>HMAC 검증은 raw body 기준 — 검증 통과 후에만 JSON 파싱 (E9804005).
 * SecurityConfig 의 permitAll({@code /api/internal/**}) 하에서도 본 컨트롤러가 자체 인증한다.
 */
@RestController
@RequestMapping("/api/internal/sync")
public class SyncReceiveController {

    private static final Logger log = LoggerFactory.getLogger(SyncReceiveController.class);
    private static final String HMAC_HEADER = "X-Signature";

    private final ReadModelSyncService syncService;
    private final ObjectMapper objectMapper;
    private final SyncChannel hcm;

    public SyncReceiveController(
            ReadModelSyncService syncService,
            ObjectMapper objectMapper,
            @Value("${performance.s2s.hcm.bearer-token:}") String hcmBearerToken,
            @Value("${performance.s2s.hcm.hmac-secret:}") String hcmHmacSecret) {
        this.syncService = syncService;
        this.objectMapper = objectMapper;
        this.hcm = new SyncChannel("hcm", hcmBearerToken, hcmHmacSecret);
    }

    /** #1 hcm 인사 마스터 → rm_employee / rm_org_unit / rm_assignment. */
    @PostMapping(value = "/core-master", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CoreMasterBatchResponse> receiveCoreMaster(
            @RequestBody String rawBody,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = HMAC_HEADER, required = false) String signature) {
        hcm.authenticate(rawBody, authorization, signature);
        return ResponseEntity.ok(syncService.applyCoreMaster(
            parse(rawBody, CoreMasterBatchRequest.class, "core-master")));
    }

    private <T> T parse(String rawBody, Class<T> type, String channel) {
        try {
            return objectMapper.readValue(rawBody, type);
        } catch (Exception e) {
            log.warn("[performance-sync:{}] payload parse failed — {}", channel, e.getMessage());
            throw new ApiException(PerformanceErrorCode.SYNC_INVALID_PAYLOAD, Map.of("channel", channel));
        }
    }

    /**
     * 소스별 인증 채널 — bearer constant-time + HMAC raw body (lib HmacService timing-safe).
     * secret 설정 시에만 HmacService 생성 — 32자 미만이면 그 시점 fail-fast.
     */
    private static final class SyncChannel {

        private final String name;
        private final String bearerToken;
        private final HmacService hmacService;

        private SyncChannel(String name, String bearerToken, String hmacSecret) {
            this.name = name;
            this.bearerToken = bearerToken;
            this.hmacService = (hmacSecret == null || hmacSecret.isBlank())
                ? null : new HmacService(hmacSecret);
        }

        private void authenticate(String rawBody, String authorization, String signature) {
            if (bearerToken == null || bearerToken.isBlank() || hmacService == null) {
                log.warn("[performance-sync:{}] rejected — endpoint not configured (bearer/secret 미설정)", name);
                throw new ApiException(PerformanceErrorCode.SYNC_NOT_CONFIGURED, Map.of("channel", name));
            }
            if (authorization == null || !authorization.startsWith("Bearer ")
                    || !constantTimeEquals(authorization.substring(7), bearerToken)) {
                log.warn("[performance-sync:{}] bearer auth failed", name);
                throw new ApiException(PerformanceErrorCode.SYNC_AUTH_FAILED, Map.of("channel", name));
            }
            if (signature == null || signature.isBlank()
                    || !hmacService.verify(rawBody == null ? "" : rawBody, signature)) {
                log.warn("[performance-sync:{}] hmac signature verify failed", name);
                throw new ApiException(PerformanceErrorCode.SYNC_AUTH_FAILED, Map.of("channel", name));
            }
        }

        /** Timing-safe compare (UTF-8 bytes). */
        private static boolean constantTimeEquals(String a, String b) {
            if (a == null || b == null) {
                return false;
            }
            return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
        }
    }
}

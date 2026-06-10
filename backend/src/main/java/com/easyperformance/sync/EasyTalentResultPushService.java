/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.sync;

import com.easyperformance.domain.selfevaluation.entity.SelfEvaluation;
import com.easyperformance.domain.selfevaluation.entity.SelfEvaluationStatus;
import com.easyperformance.domain.selfevaluation.repository.SelfEvaluationRepository;
import com.easyware.platform.hmac.HmacService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * EasyTalentResultPushService — performance → easy-talent-management 확정 평가 결과 S2S 송신
 * (G_TALENT_D3 #2, BE-CC-4 Outbox-light sendAll 1단계, 2026-06-11).
 *
 * <p>easy-talent 수신측 계약 (talent `6022d5e` + TALENT_PLAN §2.2#2):
 * {@code POST {base-url}/api/internal/sync/performance-results} + Bearer + X-Signature HMAC.
 * Body {@code {results:[{id, employeeId, cycleId, cycleName, periodStart, periodEnd,
 * evaluationType, score, grade, finalizedAt, sourceVersion}]}} — <b>FINALIZED 만 발신</b>.
 *
 * <p>매핑: {@link SelfEvaluation} FINALIZED → evaluationType="SELF" (자기평가 확정본 —
 * 매니저/캘리브레이션 확정 평가 도입 시 "MANAGER"/"FINAL" 추가). finalizedAt ≈ updatedAt
 * (FINALIZED 전이 시각 컬럼 부재 — 확정 후 불변이라 근사 동치). sourceVersion = updatedAt epochMilli.
 *
 * <p>LIVE 안전: base-url/bearer/hmac-secret 미설정 시 {@link #isConfigured()}=false → 송신 0.
 * HmacService 는 secret 존재 시 직접 생성 (talent 수신측 SyncChannel 동형 — autoconfig 게이트 불요,
 * secret 32자 미만이면 그 시점 fail-fast).
 *
 * <p>자동 증분(Outbox-light sendChanged)은 후속 슬라이스 — 본 단계는 수동 sendAll 트리거.
 */
@Service
public class EasyTalentResultPushService {

    private static final Logger log = LoggerFactory.getLogger(EasyTalentResultPushService.class);
    private static final String HMAC_HEADER = "X-Signature";
    private static final String SYNC_PATH = "/api/internal/sync/performance-results";

    private final SelfEvaluationRepository selfEvaluations;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    private final String baseUrl;
    private final String bearerToken;
    @Nullable
    private final HmacService hmacService;

    public EasyTalentResultPushService(
            SelfEvaluationRepository selfEvaluations,
            ObjectMapper objectMapper,
            @Value("${performance.s2s.easytalent.base-url:}") String baseUrl,
            @Value("${performance.s2s.easytalent.bearer-token:}") String bearerToken,
            @Value("${performance.s2s.easytalent.hmac-secret:}") String hmacSecret) {
        this.selfEvaluations = selfEvaluations;
        this.objectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.restClient = RestClient.create();
        this.baseUrl = baseUrl;
        this.bearerToken = bearerToken;
        this.hmacService = (hmacSecret == null || hmacSecret.isBlank()) ? null : new HmacService(hmacSecret);
    }

    /** FINALIZED 전량 snapshot 1회 푸시 (수동 트리거 — 수신측 sourceVersion idempotent). */
    public PushResult sendAll() {
        if (!isConfigured()) {
            log.warn("[easytalent-results] push skipped — S2S not configured (base-url/bearer/secret)");
            return PushResult.notConfigured();
        }
        List<ResultUpsertDto> results = buildFinalizedResults();
        if (results.isEmpty()) {
            log.info("[easytalent-results] no FINALIZED evaluations — nothing to push");
            return new PushResult(true, 200, 0);
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(new ResultBatchPayload(results));
        } catch (Exception e) {
            log.error("[easytalent-results] payload serialize failed", e);
            throw new RuntimeException("easytalent results serialize failed", e);
        }

        URI uri = URI.create(baseUrl + SYNC_PATH);
        log.info("[easytalent-results] POST {} results={} bytes={}", uri, results.size(), body.length());
        try {
            var response = restClient.post()
                .uri(uri)
                .headers(h -> {
                    h.setContentType(MediaType.APPLICATION_JSON);
                    h.setBearerAuth(bearerToken);
                    h.set(HMAC_HEADER, hmacService.compute(body));
                    h.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                })
                .body(body)
                .retrieve()
                .toEntity(String.class);
            log.info("[easytalent-results] OK status={} results={}", response.getStatusCode(), results.size());
            return new PushResult(true, response.getStatusCode().value(), results.size());
        } catch (RestClientException e) {
            log.error("[easytalent-results] push failed uri={}", uri, e);
            throw new RuntimeException("easytalent results push failed", e);
        }
    }

    /** payload 빌드 — 단위 테스트 용도 package-private. */
    List<ResultUpsertDto> buildFinalizedResults() {
        List<ResultUpsertDto> out = new ArrayList<>();
        for (SelfEvaluation e : selfEvaluations.findAll()) {
            if (e.getStatus() != SelfEvaluationStatus.FINALIZED) {
                continue;
            }
            out.add(new ResultUpsertDto(
                e.getId(),
                e.getEmployeeId(),
                e.getCycleId(),
                null,                                  // cycleName — PerformanceCycle 도입 시 채움
                e.getPeriodStart(),
                e.getPeriodEnd(),
                "SELF",
                e.getScore() == null ? null : BigDecimal.valueOf(e.getScore()),
                null,                                  // grade — 등급 체계 도입 시 채움
                toOffset(e.getUpdatedAt()),            // finalizedAt ≈ updatedAt (확정 후 불변)
                e.getUpdatedAt() == null ? 0L : e.getUpdatedAt().toEpochMilli()
            ));
        }
        return out;
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank()
            && bearerToken != null && !bearerToken.isBlank()
            && hmacService != null;
    }

    @Nullable
    private static OffsetDateTime toOffset(@Nullable Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    // ── payload DTOs (talent 수신측 record shape 정합 — S2S 경계라 별도 보유) ──

    record ResultBatchPayload(List<ResultUpsertDto> results) {}

    record ResultUpsertDto(
        UUID id,
        UUID employeeId,
        UUID cycleId,
        String cycleName,
        LocalDate periodStart,
        LocalDate periodEnd,
        String evaluationType,
        BigDecimal score,
        String grade,
        OffsetDateTime finalizedAt,
        Long sourceVersion
    ) {}

    public record PushResult(boolean sent, int httpStatus, int results) {
        static PushResult notConfigured() {
            return new PushResult(false, 0, 0);
        }
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Auditing 인프라 — {@code @EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")} 에서
 * 참조. lib {@link com.easyware.platform.audit.BaseAuditEntity} 의 created_at/updated_at 컬럼이 사용.
 *
 * <p>단계 1 (본 슬라이스): 시간 = {@link Instant}.now (UTC) / actor = system UUID fallback.
 * 단계 3 BE-CC-2 JWT 5분리 진입 시 SecurityContextHolder 에서 실 사용자 UUID 추출하도록 갱신.
 *
 * <p>jobeval / hcm 정합.
 */
@Configuration
public class JpaAuditConfig {

    /** lib BaseAuditEntity 의 @CreatedDate / @LastModifiedDate 에 주입할 시간 공급자. UTC Instant. */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now().atOffset(ZoneOffset.UTC));
    }

    /**
     * lib BaseAuditEntity 의 @CreatedBy / @LastModifiedBy 에 주입할 사용자 공급자.
     *
     * <p>단계 1: SYSTEM_ACTOR UUID fallback (인증 미구현). 단계 3 진입 시 SecurityContextHolder 에서
     * JWT principal 의 UUID 추출. lib AuditorAwareSpi 인터페이스로 제공 패턴 정합.
     */
    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> Optional.of(SYSTEM_ACTOR);
    }

    /** 단계 1 시스템 actor UUID (인증 미구현 fallback). 단계 3 진입 시 SecurityContextHolder 로 대체. */
    private static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
}

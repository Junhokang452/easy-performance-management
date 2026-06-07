/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * 자매품 9호 — easy-performance-management 단계 1 BE-CC-1 진입 (2026-06-08).
 */
package com.easyperformance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * easy-performance-management 백엔드 진입점.
 *
 * <p>자매품 9호 — 성과 평가 도메인 (자기평가 + 개인 OKR + 회고 저널 + 멘토 피드백). ADR-022 자매품 정식
 * 편입 + ADR-030 듀얼 모드 5호 + ADR-013 Neon Model B 정합.
 *
 * <p>멀티테넌시(Model B)·control plane·프로비저닝·다계층 시드는 공유 lib easy-platform-core
 * (com.easyware.platform) 에 위임한다 (ADR-007 복붙 금지). base package 가 com.easyperformance 라 lib
 * 패키지를 {@code @ComponentScan}/{@code @EntityScan}/{@code @EnableJpaRepositories} 로 명시 포함해야
 * lib 빈 + entity + repository 가 등록된다 (jobeval/hcm 정렬).
 *
 * <p>{@code @EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")} — 자매품 정렬 (jobeval).
 * 단계 1 = 단일 DB 운영 (Model A 흉내) + tenant_id 컬럼 보유 — 단계 2 진입 시 Model B per-tenant DB
 * 단번 전환 (lib BE 14 TenantBootstrap 3 SPI seam 자연 결합).
 *
 * <p>lib autoconfig 게이트 (application.yml):
 * <ul>
 *   <li>easyplatform.tenantctx.enabled = true → 단계 1 진입 시 ON (TenantContextResolver 위임)</li>
 *   <li>easyplatform.rls.tenant.enabled = false → 단계 2 진입 시 ON (Model B + RLS 정책 박제 후)</li>
 *   <li>easyplatform.error.enabled = true → 단계 1 진입 시 ON (GlobalExceptionHandler 자동 등록)</li>
 *   <li>easyplatform.b2c.enabled = false → 단계 5 진입 시 ON (B2C 공통 테넌트 + RLS user_id)</li>
 * </ul>
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.easyperformance", "com.easyware.platform"})
@EnableJpaRepositories(basePackages = {
    "com.easyperformance",
    "com.easyware.platform.user",
    "com.easyware.platform.audit"
})
@EntityScan(basePackages = {
    "com.easyperformance",
    "com.easyware.platform.user",
    "com.easyware.platform.audit"
})
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@EnableCaching
@EnableScheduling
public class PerformanceManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(PerformanceManagementApplication.class, args);
    }
}

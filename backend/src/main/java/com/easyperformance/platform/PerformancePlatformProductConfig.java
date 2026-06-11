/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.platform;

import com.easyware.platform.PlatformProductConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * easy-performance-management 의 {@link PlatformProductConfig} 구현 — lib 멀티테넌시 제품 토큰
 * (talent `4e709da` / store-hr `1594c03` / jobeval / hcm 패턴 정합).
 *
 * <p>제품코드 모델: app_code=PERFORMANCE / database_name=performance (tenant_product_db.app_code /
 * app_subscription.app_code 정본 — 기존 {@code PerformanceTenantBootstrapConfig.PERFORMANCE_APP_CODE}
 * 와 동일 토큰). 앱 role=performance_app (NOBYPASSRLS). self-migrate 위치는 본 repo 의 Flyway 루트
 * {@code classpath:db/migration} (단일 location 그대로 — 프로비저닝 fan-out 이 V 최신까지 테넌트 DB 적용).
 *
 * <p>게이트: 멀티테넌시 ON({@code easyware.neon.multitenancy-enabled}) 일 때만 로드 —
 * OFF 면 단일 DB 부팅 무영향 (lib 게이트 ON 시 본 빈 부재 = 부팅 fail-fast, store-hr 실측 SPI #1).
 */
@Component
@ConditionalOnProperty(name = "easyware.neon.multitenancy-enabled", havingValue = "true")
public class PerformancePlatformProductConfig implements PlatformProductConfig {

    @Override
    public String appCode() {
        return "PERFORMANCE";
    }

    @Override
    public String databaseName() {
        return "performance";
    }

    @Override
    public String appRoleName() {
        return "performance_app";
    }

    @Override
    public String tenantSchemaLocations() {
        return "classpath:db/migration";
    }
}

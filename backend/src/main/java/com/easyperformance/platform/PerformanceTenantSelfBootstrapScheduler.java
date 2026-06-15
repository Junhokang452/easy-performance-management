/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.platform;

import com.easyware.platform.TenantProductDbStore;
import com.easyware.platform.tenant.AbstractTenantSelfBootstrapScheduler;
import com.easyware.platform.tenant.TenantBootstrap;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * performance (2026-06-15) — 자가 부트스트랩 스케줄러 — lib
 * {@link AbstractTenantSelfBootstrapScheduler} 베이스 활용 (hcm/recruit 동형).
 *
 * <p>30 초마다 control plane 의 {@code tenant_product_db} 폴링 → ACTIVE PERFORMANCE 테넌트 발견 →
 * {@link TenantBootstrap#bootstrap} → {@link PerformanceInitialAdminSeeder}.
 */
@Component
@ConditionalOnProperty(name = "easyplatform.performance.stage2.enabled", havingValue = "true")
public class PerformanceTenantSelfBootstrapScheduler extends AbstractTenantSelfBootstrapScheduler {

    private static final String PERFORMANCE_APP_CODE = "PERFORMANCE";

    private final PerformanceInitialAdminSeeder adminSeeder;

    public PerformanceTenantSelfBootstrapScheduler(
            ObjectProvider<TenantProductDbStore> productDbStore,
            ObjectProvider<TenantBootstrap> tenantBootstrap,
            ObjectProvider<MeterRegistry> meterRegistry,
            PerformanceInitialAdminSeeder adminSeeder) {
        super(productDbStore, tenantBootstrap, meterRegistry);
        this.adminSeeder = adminSeeder;
    }

    @Override
    protected String appCode() {
        return PERFORMANCE_APP_CODE;
    }

    @Override
    protected boolean seedAdmin(UUID tenantId) {
        return adminSeeder.seedIfAbsent(tenantId);
    }

    @Override
    @Scheduled(fixedDelayString = "${easyplatform.performance.stage2.poll-interval-ms:30000}",
               initialDelayString = "${easyplatform.performance.stage2.initial-delay-ms:15000}")
    public void scanAndBootstrap() {
        super.scanAndBootstrap();
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.platform;

import com.easyware.platform.TenantProductDb;
import com.easyware.platform.TenantProductDbStore;
import com.easyware.platform.tenant.AbstractTenantSelfBootstrapScheduler;
import com.easyware.platform.tenant.TenantBootstrap;
import com.easyware.platform.tenant.TenantSchemaMigrator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * performance (2026-06-15) — 자가 부트스트랩 스케줄러 — lib
 * {@link AbstractTenantSelfBootstrapScheduler} 베이스 활용 (recruit/store-hr/talent/learning 동형).
 *
 * <p>30 초마다 control plane 의 {@code tenant_product_db} 폴링.
 * {@code easyplatform.performance.stage2.self-migrate-enabled} 토글로 두 경로 분기 (ADR-055 b2.2,
 * recruit/store-hr/talent canary 정합):
 * <ul>
 *   <li><b>self-migrate=false (기본, legacy)</b>: ACTIVE PERFORMANCE 테넌트 → {@link TenantBootstrap#bootstrap}
 *       (owner-provisioned 전제) → {@link #seedAdmin(UUID)} (control plane 라우팅 기반 시드).</li>
 *   <li><b>self-migrate=true (Option B, canary)</b>: {@code findBootstrapCandidates} →
 *       {@code withBootstrapLock} → {@link TenantSchemaMigrator}(owner DS 로 Flyway db/migration) →
 *       {@link #seedAdmin(UUID, DataSource)} (라우팅 우회, ACTIVE 전이 전 시드) →
 *       {@code completeBootstrapWithSchemaVersion}.</li>
 * </ul>
 *
 * <p><b>owner=false consumer 안전</b>: Option B 는 {@code NeonApiClient}/{@code NeonProvisioningService}
 * 에 의존하지 않으므로, ADR-055 이후 {@code control-plane-owner=false} 인 performance 가 신규 테넌트 스키마를
 * 직접 마이그레이션할 수 있다(legacy 3-SPI 경로는 owner DB 프로비저닝 전제 → owner=false 에서 옵션 B no-op).
 */
@Component
@ConditionalOnProperty(name = "easyplatform.performance.stage2.enabled", havingValue = "true")
public class PerformanceTenantSelfBootstrapScheduler extends AbstractTenantSelfBootstrapScheduler {

    private static final String PERFORMANCE_APP_CODE = "PERFORMANCE";
    /** db/migration 최상위 마이그(V20260908_003__responsible_reminders.sql) Flyway 정규화 버전. */
    private static final String PERFORMANCE_SCHEMA_VERSION = "20260908.003";

    private final PerformanceInitialAdminSeeder adminSeeder;
    private final boolean selfMigrateEnabled;
    private final boolean activeDriftEnabled;
    private final Set<UUID> activeDriftCanaryTenantIds;

    public PerformanceTenantSelfBootstrapScheduler(
            ObjectProvider<TenantProductDbStore> productDbStore,
            ObjectProvider<TenantBootstrap> tenantBootstrap,
            ObjectProvider<MeterRegistry> meterRegistry,
            ObjectProvider<TenantSchemaMigrator> schemaMigrator,
            PerformanceInitialAdminSeeder adminSeeder,
            @Value("${easyplatform.performance.stage2.self-migrate-enabled:false}") boolean selfMigrateEnabled,
            @Value("${easyplatform.performance.stage2.active-drift-enabled:false}") boolean activeDriftEnabled,
            @Value("${easyplatform.performance.stage2.active-drift-canary-tenant-ids:}")
            String activeDriftCanaryTenantIds) {
        super(productDbStore, tenantBootstrap, meterRegistry, schemaMigrator);
        this.adminSeeder = adminSeeder;
        this.selfMigrateEnabled = selfMigrateEnabled;
        this.activeDriftEnabled = activeDriftEnabled;
        this.activeDriftCanaryTenantIds = parseCanaryTenantIds(activeDriftCanaryTenantIds);
    }

    @Override
    protected String appCode() {
        return PERFORMANCE_APP_CODE;
    }

    // ─── ADR-055 b2.2 Option B self-migrate 훅 ───────────────────────────────────────────────
    // self-migrate-enabled=true(canary) 면 base 가 scanAndSelfMigrate()(TenantSchemaMigrator) 분기로 dispatch.
    // false(기본) 면 레거시 seedAdmin(UUID) 경로(아래)만 사용 — 현행 거동 보존.

    /** Option B 활성 토글 — 단순 property read(코드 배포 없이 canary on/off). */
    @Override
    protected boolean schemaSelfMigrateEnabled() {
        return selfMigrateEnabled;
    }

    /** Option B — performance 자기 제품 tenant 스키마 Flyway location(db/migration). */
    @Override
    protected String tenantSchemaLocations() {
        return "classpath:db/migration";
    }

    /** Option B — drift 판정 기준 schema_version(현재 최상위 마이그와 일치). */
    @Override
    protected String expectedSchemaVersion() {
        return PERFORMANCE_SCHEMA_VERSION;
    }

    /**
     * ACTIVE drift 는 명시 토글과 비어 있지 않은 canary 목록이 모두 있어야만 켠다.
     * 토글만 잘못 켜도 전체 고객 DB fan-out 이 시작되지 않도록 fail-closed 한다.
     */
    @Override
    protected boolean activeDriftEnabled() {
        return activeDriftEnabled && !activeDriftCanaryTenantIds.isEmpty();
    }

    /** 신규 PROVISIONING 은 항상 허용하고, ACTIVE drift 만 고객 UUID canary 로 제한한다. */
    @Override
    protected boolean rolloutAllows(TenantProductDb candidate) {
        return candidate.status() != TenantProductDb.Status.ACTIVE
                || activeDriftCanaryTenantIds.contains(candidate.platformTenantId());
    }

    /** Option B — ACTIVE 전이 전, bootstrap owner DataSource 로 직접 시드(registry.get 우회). */
    @Override
    protected void seedAdmin(UUID tenantId, DataSource dataSource) {
        adminSeeder.seedIfAbsent(tenantId, dataSource);
    }

    /** 레거시 경로(self-migrate OFF) — control plane 라우팅 기반 시드. */
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

    private static Set<UUID> parseCanaryTenantIds(String configuredIds) {
        if (configuredIds == null || configuredIds.isBlank()) {
            return Set.of();
        }
        try {
            return Arrays.stream(configuredIds.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(UUID::fromString)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "easyplatform.performance.stage2.active-drift-canary-tenant-ids must contain comma-separated UUIDs",
                    ex);
        }
    }
}

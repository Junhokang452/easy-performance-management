/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.platform;

import com.easyware.platform.TenantProductDb;
import com.easyware.platform.TenantProductDbStore;
import com.easyware.platform.tenant.TenantBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P2.4 (2026-06-15) — performance 자가 부트스트랩 스케줄러 (hcm/store-hr/talent 패턴 사본).
 *
 * <p>30 초마다 control plane 의 {@code tenant_product_db} 폴링 → ACTIVE PERFORMANCE 테넌트 발견 →
 * {@link TenantBootstrap#bootstrap} → {@link PerformanceInitialAdminSeeder.}
 *
 * <p>Render 영향 0: {@code easyplatform.performance.stage2.enabled=true} 게이트 ON 시에만 생성.
 *
 * @since P2.4 (2026-06-15)
 */
@Component
@ConditionalOnProperty(name = "easyplatform.performance.stage2.enabled", havingValue = "true")
public class PerformanceTenantSelfBootstrapScheduler {

    private static final Logger log = LoggerFactory.getLogger(PerformanceTenantSelfBootstrapScheduler.class);

    private static final String PERFORMANCE_APP_CODE = "PERFORMANCE";

    private final Set<UUID> bootstrapped = ConcurrentHashMap.newKeySet();

    private final ObjectProvider<TenantProductDbStore> productDbStore;
    private final ObjectProvider<TenantBootstrap> tenantBootstrap;
    private final PerformanceInitialAdminSeeder adminSeeder;

    public PerformanceTenantSelfBootstrapScheduler(
            ObjectProvider<TenantProductDbStore> productDbStore,
            ObjectProvider<TenantBootstrap> tenantBootstrap,
            PerformanceInitialAdminSeeder adminSeeder) {
        this.productDbStore = productDbStore;
        this.tenantBootstrap = tenantBootstrap;
        this.adminSeeder = adminSeeder;
    }

    @Scheduled(fixedDelayString = "${easyplatform.performance.stage2.poll-interval-ms:30000}",
               initialDelayString = "${easyplatform.performance.stage2.initial-delay-ms:15000}")
    public void scanAndBootstrap() {
        TenantProductDbStore store = productDbStore.getIfAvailable();
        TenantBootstrap bootstrap = tenantBootstrap.getIfAvailable();
        if (store == null || bootstrap == null) {
            log.debug("[perf/self-bootstrap] 필수 빈 부재 — store={} bootstrap={}",
                    store != null, bootstrap != null);
            return;
        }
        List<TenantProductDb> rows;
        try {
            rows = store.findAllByAppCode(PERFORMANCE_APP_CODE);
        } catch (Exception ex) {
            log.warn("[perf/self-bootstrap] control DB 조회 실패. err={}", ex.getMessage());
            return;
        }
        if (rows.isEmpty()) return;
        Set<UUID> targets = new HashSet<>();
        for (TenantProductDb row : rows) {
            if (row.status() != TenantProductDb.Status.ACTIVE) continue;
            UUID id = row.platformTenantId();
            if (!bootstrapped.contains(id)) targets.add(id);
        }
        if (targets.isEmpty()) return;
        log.info("[perf/self-bootstrap] {} 개 신규 ACTIVE 발견 — 부트스트랩 시작", targets.size());
        for (UUID tenantId : targets) {
            try {
                bootstrap.bootstrap(PERFORMANCE_APP_CODE, tenantId);
                adminSeeder.seedIfAbsent(tenantId);
                bootstrapped.add(tenantId);
                log.info("[perf/self-bootstrap] tenantId={} 부트스트랩 완료", tenantId);
            } catch (Exception ex) {
                log.error("[perf/self-bootstrap] tenantId={} 실패 — 재시도. err={}",
                        tenantId, ex.getMessage(), ex);
            }
        }
    }

    void clearCacheForTest() { bootstrapped.clear(); }
    public int bootstrappedCount() { return bootstrapped.size(); }
}

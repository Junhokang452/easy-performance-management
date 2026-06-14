/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.platform;

import com.easyware.platform.tenant.TenantBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * P2.4 (2026-06-15) — 즉시 부트스트랩 트리거용 HTTP 엔드포인트 (hcm/store-hr/talent 패턴 사본).
 *
 * <p>경로: {@code POST /api/internal/admin/performance-tenant-bootstrap?tenantId=<UUID>}
 *
 * @since P2.4 (2026-06-15)
 */
@RestController("performanceTenantBootstrapController")
@RequestMapping("/api/internal/admin")
@ConditionalOnProperty(name = "easyplatform.performance.stage2.enabled", havingValue = "true")
@ConditionalOnBean(TenantBootstrap.class)
public class PerformanceTenantBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(PerformanceTenantBootstrapController.class);
    private static final String PERFORMANCE_APP_CODE = "PERFORMANCE";

    private final TenantBootstrap tenantBootstrap;
    private final PerformanceInitialAdminSeeder adminSeeder;

    public PerformanceTenantBootstrapController(TenantBootstrap tenantBootstrap,
                                                PerformanceInitialAdminSeeder adminSeeder) {
        this.tenantBootstrap = tenantBootstrap;
        this.adminSeeder = adminSeeder;
    }

    @PostMapping("/performance-tenant-bootstrap")
    public ResponseEntity<?> bootstrap(@RequestParam("tenantId") UUID tenantId) {
        log.info("[perf/bootstrap-controller] 수동 트리거 — tenantId={}", tenantId);
        try {
            var outcome = tenantBootstrap.bootstrap(PERFORMANCE_APP_CODE, tenantId);
            boolean seeded = adminSeeder.seedIfAbsent(tenantId);
            return ResponseEntity.accepted().body(Map.of(
                    "success", true,
                    "tenantId", tenantId.toString(),
                    "siblingCode", PERFORMANCE_APP_CODE,
                    "provisioned", outcome.provisioned(),
                    "adminSeeded", seeded,
                    "defaultAdminRole", PerformanceInitialAdminSeeder.DEFAULT_ADMIN_ROLE));
        } catch (Exception ex) {
            log.error("[perf/bootstrap-controller] 실패 — tenantId={} err={}",
                    tenantId, ex.getMessage(), ex);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "tenantId", tenantId.toString(),
                    "error", ex.getMessage()));
        }
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.platform;

import com.easyware.platform.PlatformTenant;
import com.easyware.platform.PlatformTenantStore;
import com.easyware.platform.TenantDataSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.easyware.platform.tenant.TenantAdminSeedSupport;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * P2.4 (2026-06-15) — performance 자매품 hcm/store-hr/talent 패턴 사본. 신규 performance 테넌트 DB 에
 * 초기 admin 사용자 삽입.
 *
 * <p>스키마 정합 (V20260612_001__app_user.sql): tenant_id+email UNIQUE / role IN (SUPER_ADMIN, HR_ADMIN,
 * DIRECTOR, MANAGER, EMPLOYEE).
 *
 * <p>Render 영향 0: {@code easyplatform.performance.stage2.enabled=true} 게이트 ON 시에만 빈 등록.
 */
@Component
@ConditionalOnProperty(name = "easyplatform.performance.stage2.enabled", havingValue = "true")
public class PerformanceInitialAdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(PerformanceInitialAdminSeeder.class);

    public static final String DEFAULT_ADMIN_EMAIL_TEMPLATE = "admin@%s.setup";
    public static final String DEFAULT_ADMIN_PASSWORD = "Admin1234!";
    public static final String DEFAULT_ADMIN_ROLE = "HR_ADMIN";

    private final ObjectProvider<TenantDataSourceRegistry> registry;
    private final ObjectProvider<PlatformTenantStore> tenantStore;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public PerformanceInitialAdminSeeder(ObjectProvider<TenantDataSourceRegistry> registry,
                                         ObjectProvider<PlatformTenantStore> tenantStore) {
        this.registry = registry;
        this.tenantStore = tenantStore;
    }

    public boolean seedIfAbsent(UUID tenantId) {
        TenantDataSourceRegistry reg = registry.getIfAvailable();
        PlatformTenantStore store = tenantStore.getIfAvailable();
        if (reg == null || store == null) {
            log.debug("[perf/admin-seed] 필수 빈 부재 — registry={} store={}. tenantId={}",
                    reg != null, store != null, tenantId);
            return false;
        }
        PlatformTenant tenant;
        DataSource ds;
        try {
            tenant = store.require(tenantId);
            ds = reg.get(tenantId, tenant.code());
        } catch (RuntimeException ex) {
            log.warn("[perf/admin-seed] 테넌트 DS 조회 실패 — 시드 SKIP. tenantId={} err={}",
                    tenantId, ex.getMessage());
            return false;
        }
        String email = String.format(DEFAULT_ADMIN_EMAIL_TEMPLATE, tenant.code().toLowerCase());
        String hash = encoder.encode(DEFAULT_ADMIN_PASSWORD);
        UUID adminId = UUID.randomUUID();
        // G5(표준): 명시 커밋 — raw JdbcTemplate 은 autoCommit=false DS 에서 커밋 안 돼 롤백(admin 미저장).
        int inserted = TenantAdminSeedSupport.executeWithCommit(ds, """
                INSERT INTO user_account (id, tenant_id, email, password_hash, display_name, role,
                                          active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, true, now(), now())
                ON CONFLICT (tenant_id, email) DO NOTHING
                """, adminId, tenantId, email, hash, "Tenant Administrator", DEFAULT_ADMIN_ROLE);
        if (inserted > 0) {
            log.info("[perf/admin-seed] 초기 admin 시드 완료 — tenantId={} email={}", tenantId, email);
            return true;
        }
        log.debug("[perf/admin-seed] admin 이미 존재 — SKIP. tenantId={} email={}", tenantId, email);
        return false;
    }
}

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
import java.util.Locale;
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
        return seedInto(tenantId, tenant.code(), ds);
    }

    /**
     * ADR-055 b2.2 Option B 전용 — self-migrate 가 ACTIVE 전이 <b>전</b>에 받은 bootstrap DataSource 로 직접 시드.
     * PROVISIONING row 는 fail-closed 라우팅이라 {@link TenantDataSourceRegistry#get} 으로 DS 를 못 얻으므로
     * registry 를 우회한다. {@code tenant.code()}(email 계산용)는 control plane 조회
     * ({@link PlatformTenantStore#require})로만 얻는다(DS 라우팅 X). 멱등 — ON CONFLICT DO NOTHING.
     *
     * <p><b>실패는 throw</b>: 스케줄러 훅 {@code seedAdmin(UUID, DataSource)} 이 {@code void} 라 false 반환을
     * 못 본다 → 시드 <i>실패</i>는 {@link IllegalStateException} 으로 전파해야
     * {@link AbstractTenantSelfBootstrapScheduler} 가 {@code recordBootstrapFailure} + PROVISIONING 유지
     * (ACTIVE 오승격 방지). {@code seedInto} 의 false(="이미 존재" 멱등 성공)와 구분.
     */
    public boolean seedIfAbsent(UUID tenantId, DataSource bootstrapDataSource) {
        PlatformTenantStore store = tenantStore.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException(
                    "[perf/admin-seed] Option B 시드 실패 — PlatformTenantStore 빈 부재(멀티테넌시 게이트 필요). tenantId="
                            + tenantId);
        }
        String code;
        try {
            code = store.require(tenantId).code();
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "[perf/admin-seed] Option B 시드 실패 — control plane 테넌트 조회 실패. tenantId=" + tenantId, ex);
        }
        return seedInto(tenantId, code, bootstrapDataSource);   // false = 이미 존재(멱등 성공) — throw 아님
    }

    /** {@code user_account} 한 행 멱등 시드. ds 는 호출자가 결정(라우팅 vs bootstrap). */
    private boolean seedInto(UUID tenantId, String tenantCode, DataSource ds) {
        String email = String.format(DEFAULT_ADMIN_EMAIL_TEMPLATE, tenantCode.toLowerCase(Locale.ROOT));
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

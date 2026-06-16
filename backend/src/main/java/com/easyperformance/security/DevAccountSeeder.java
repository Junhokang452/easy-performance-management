/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.security;

import com.easyperformance.domain.account.PerformanceUser;
import com.easyperformance.domain.account.PerformanceUserRepository;
import com.easyware.platform.TenantRoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * dev 계정 시더 — 게이트 {@code performance.auth.seed-dev-accounts} (기본 OFF, talent/store-hr 패턴).
 *
 * <p>5 페르소나 (비밀번호 'dev'): {@code dev-super-admin@performance.dev}(SUPER_ADMIN) /
 * {@code dev-hr-admin@performance.dev}(HR_ADMIN) / {@code dev-director@performance.dev}(DIRECTOR) /
 * {@code dev-manager@performance.dev}(MANAGER) / {@code dev-employee@performance.dev}(EMPLOYEE).
 * LIVE 안전 — 게이트 미설정 환경 무영향, prod ON 금지 (고정 비밀번호).
 *
 * <p>게이트 ON(단계 2) 환경은 부팅 시점 요청 컨텍스트 부재로 control DB 라우팅 함정 (recruit G149
 * 실측) — {@code app.tenancy.default-tenant-id} 설정 시 {@link TenantRoutingContext#within} 으로
 * 테넌트 DB 라우팅 후 시드.
 *
 * <p><b>talent DevAccountSeeder 와의 의도적 차이</b>: talent 는 {@code TenantSupport.currentTenantId()}
 * 로 테넌트를 읽지만, performance 의 {@code TenantSupport} 폴백은 고정 상수(0…01)라
 * {@code dev-default-tenant-id} 를 0…01 외 값으로 바꾼 환경에서 로그인 조회 테넌트
 * ({@link AuthService} 해석값)와 어긋난다 — 시더도 동일 해석값을 명시 사용해 로그인↔시드 테넌트
 * 일치를 보장한다.
 */
@Component
@ConditionalOnProperty(name = "performance.auth.seed-dev-accounts", havingValue = "true", matchIfMissing = false)
public class DevAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAccountSeeder.class);

    private record Seed(String email, String role, String displayName) {}

    private static final List<Seed> SEEDS = List.of(
        new Seed("dev-super-admin@performance.dev", "SUPER_ADMIN", "Dev Super Admin"),
        new Seed("dev-hr-admin@performance.dev",    "HR_ADMIN",    "Dev HR Admin"),
        new Seed("dev-director@performance.dev",    "DIRECTOR",    "Dev Director"),
        new Seed("dev-manager@performance.dev",     "MANAGER",     "Dev Manager"),
        new Seed("dev-employee@performance.dev",    "EMPLOYEE",    "Dev Employee"));

    private final PerformanceUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TenantRoutingContext routingContext;
    private final String defaultTenantIdStr;
    private final String defaultTenantCode;
    private final UUID resolvedTenantId;

    public DevAccountSeeder(PerformanceUserRepository users, PasswordEncoder passwordEncoder,
                            TenantRoutingContext routingContext,
                            @Value("${app.tenancy.default-tenant-id:}") String defaultTenantIdStr,
                            @Value("${app.tenancy.default-tenant-code:}") String defaultTenantCode,
                            @Value("${app.security.auth.dev-default-tenant-id:00000000-0000-0000-0000-000000000001}")
                            String devDefaultTenantIdStr) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.routingContext = routingContext;
        this.defaultTenantIdStr = defaultTenantIdStr;
        this.defaultTenantCode = defaultTenantCode;
        // AuthService 와 동일 해석 (default-tenant-id > dev-default-tenant-id) — 로그인↔시드 테넌트 일치.
        this.resolvedTenantId = AuthService.resolveDefaultTenantId(defaultTenantIdStr, devDefaultTenantIdStr);
    }

    @Override
    public void run(ApplicationArguments args) {
        // G6(표준): dev 시더는 best-effort — default-tenant 가 control plane 에서 삭제됐거나
        // 라우팅/시드가 실패해도 부팅을 중단시키지 않는다(테넌트 자유 생성·삭제 정책 정합).
        try {
            if (defaultTenantIdStr != null && !defaultTenantIdStr.isBlank()) {
                // 게이트 ON — 부팅 시점 요청 컨텍스트 부재 = control DB 라우팅 함정 → 테넌트 DB 명시 라우팅.
                routingContext.within(UUID.fromString(defaultTenantIdStr.trim()), defaultTenantCode, () -> {
                    seed(resolvedTenantId);
                    return null;
                });
            } else {
                seed(resolvedTenantId);
            }
        } catch (RuntimeException ex) {
            log.warn("[dev-account-seeder] default-tenant({}) 시드 SKIP — 부팅 계속. 사유: {}",
                    defaultTenantIdStr, ex.getMessage());
        }
    }

    void seed(UUID tenantId) {
        String hash = passwordEncoder.encode("dev");
        for (Seed seed : SEEDS) {
            if (users.existsByTenantIdAndEmail(tenantId, seed.email())) {
                continue;
            }
            PerformanceUser user = new PerformanceUser();
            user.setTenantId(tenantId);
            user.setEmail(seed.email());
            user.setPasswordHash(hash);
            user.setDisplayName(seed.displayName());
            user.setRole(seed.role());
            user.setActive(true);
            users.save(user);
            log.info("[dev-account-seeder] seeded {} ({})", seed.email(), seed.role());
        }
    }
}

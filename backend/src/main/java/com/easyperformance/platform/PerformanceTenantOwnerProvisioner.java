/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.platform;

import com.easyperformance.domain.account.PerformanceUser;
import com.easyperformance.domain.account.PerformanceUserRepository;
import com.easyware.platform.TenantOwnerProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * 초대 수락 시 테넌트 DB 에 OWNER {@link PerformanceUser}(HR_ADMIN)를 생성한다
 * (talent `4e709da` 사본 — store-hr/recruit 패턴 정합, lib 필수 SPI #3).
 *
 * <p>호출자(lib TenantInvitationService)가 {@code TenantRoutingContext.within} 으로 테넌트 DB
 * 라우팅을 잡은 상태에서 프록시 경유 호출. <b>수동 setId 금지</b> — Spring Data isNew 판정이
 * 깨져 merge 경로 실패 (recruit 게이트 ON 첫 실행 실측 — @PrePersist UuidV7 생성에 위임).
 */
@Component
@ConditionalOnProperty(name = "easyware.neon.multitenancy-enabled", havingValue = "true")
public class PerformanceTenantOwnerProvisioner implements TenantOwnerProvisioner {

    private final PerformanceUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public PerformanceTenantOwnerProvisioner(PerformanceUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void createOwnerAdmin(UUID tenantId, String email, String name, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByTenantIdAndEmail(tenantId, normalizedEmail)) {
            throw new IllegalStateException("user account already exists: " + normalizedEmail);
        }
        PerformanceUser owner = new PerformanceUser();
        owner.setTenantId(tenantId);
        owner.setEmail(normalizedEmail);
        owner.setPasswordHash(passwordEncoder.encode(rawPassword));
        owner.setDisplayName((name == null || name.isBlank()) ? normalizedEmail : name.trim());
        owner.setRole("HR_ADMIN");
        owner.setActive(true);
        users.save(owner);
    }
}

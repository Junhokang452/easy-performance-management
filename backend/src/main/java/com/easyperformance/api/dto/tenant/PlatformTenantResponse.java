/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.api.dto.tenant;

import com.easyware.platform.PlatformTenant;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * SystemAdmin 테넌트 응답 — lib {@link PlatformTenant} 의 frontend 미러
 * (store-hr `f8df3db` 사본, Jackson camelCase 정합).
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record PlatformTenantResponse(
        UUID id,
        String code,
        String name,
        String status,
        String region,
        String adminUsername,
        String adminEmail,
        String neonProjectId,
        String lastError,
        String countryCode,
        String defaultLocale,
        String timezone,
        String currencyCode,
        String industryCode,
        String companySize,
        String planCode,
        String enabledModules,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PlatformTenantResponse from(PlatformTenant t) {
        return new PlatformTenantResponse(
                t.id(),
                t.code(),
                t.name(),
                t.status() != null ? t.status().name() : null,
                t.region(),
                t.adminUsername(),
                t.adminEmail(),
                t.neonProjectId(),
                t.lastError(),
                t.countryCode(),
                t.defaultLocale(),
                t.timezone(),
                t.currencyCode(),
                t.industryCode(),
                t.companySize(),
                t.planCode(),
                t.enabledModules(),
                t.createdAt(),
                t.updatedAt());
    }
}

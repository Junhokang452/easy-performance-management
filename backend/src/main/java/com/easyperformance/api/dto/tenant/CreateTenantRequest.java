/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.api.dto.tenant;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SystemAdmin 테넌트 생성 입력 — lib {@code PlatformTenantStore.create} signature 정합.
 * store-hr `f8df3db` / recruit {@code CreateTenantRequest} 사본 (region max=80 — Neon region id
 * ex. aws-ap-southeast-1).
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record CreateTenantRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 80) String region,
        @NotBlank @Size(max = 100) String adminUsername,
        @NotBlank @Email @Size(max = 200) String adminEmail
) {
}

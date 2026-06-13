/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.config;

import com.easyware.platform.tenantctx.TenantContext;
import com.easyware.platform.tenantctx.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * PerformanceTenantContextResolver — lib tenantctx filter SPI.
 *
 * <p>Provides the resolver required when {@code easyplatform.tenantctx.enabled=true}. This prevents the
 * shared auto-config from publishing a NullBean named {@code tenantContextFilter}, which Tomcat later
 * tries to adapt as a servlet Filter during startup.
 *
 * <p>{@link com.easyperformance.security.JwtAuthFilter} writes the shared lib {@link TenantContext}; this
 * resolver exposes the current value to the lib filter without adding a second tenant source.
 */
@Component
public class PerformanceTenantContextResolver implements TenantContextResolver {

    @Override
    public TenantContext resolve(HttpServletRequest request) {
        return TenantContext.get();
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.platform;

import com.easyware.platform.TenantContextBridge;
import com.easyware.platform.tenantctx.TenantContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * easy-performance-management 의 {@link TenantContextBridge} 구현 — lib (라우팅/초대/control plane) 이
 * 본 제품의 요청 스코프 컨텍스트에 위임하도록 한다 (ADR-007 seam, talent `4e709da` 사본).
 *
 * <p>performance 는 자체 ThreadLocal 없이 lib {@link TenantContext} 를 그대로 사용 — 본 브리지도 lib
 * static API 위임 (set 은 b2b 컨텍스트 — performance 는 B2C 부재, ADR-031).
 *
 * <p><b>비-게이트</b> ({@code @ConditionalOnProperty} 없음): lib 의 비-게이트
 * {@code TenantRoutingContext}(@Component, {@code com.easyware.platform} scanBasePackages 포함으로
 * 항상 스캔)가 본 빈을 생성자 주입받으므로 게이트 OFF 단일 DB 부팅에서도 필요 — 부재 시
 * UnsatisfiedDependencyException 부팅 실패 (talent S8 2026-06-11 실측 함정. performance 는
 * `41499e3` scanBasePackages 전환 이후 본 빈 부재 상태가 잠복해 있었다 — 본 신설로 해소).
 */
@Component
public class PerformanceTenantContextBridge implements TenantContextBridge {

    @Override
    public UUID currentTenantId() {
        TenantContext ctx = TenantContext.get();
        return ctx != null ? ctx.getTenantId() : null;
    }

    @Override
    public UUID currentUserId() {
        TenantContext ctx = TenantContext.get();
        return ctx != null ? ctx.getUserId() : null;
    }

    @Override
    public void set(UUID userId, UUID tenantId) {
        TenantContext.set(TenantContext.b2b(tenantId, userId));
    }
}

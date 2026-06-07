/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.common;

import com.easyware.platform.tenantctx.TenantContext;

import java.util.UUID;

/**
 * 서비스 계층에서 현재 테넌트 ID 조회 — lib {@link TenantContext} thin adapter.
 *
 * <p>단계 1 BE-CC-1 (본 슬라이스): lib 게이트 OFF 가능 (dev 만 ON) — 미초기 상태에서 안전 fallback 으로
 * 시스템 테넌트 UUID 반환. 단계 2~3 진입 시 fallback 제거하고 {@link TenantContext#requireTenantId()}
 * 직접 호출로 전환.
 *
 * <p>fallback 사용은 LIVE 운영 전 반드시 제거. 본 클래스는 단계 1 진입 임시 어댑터.
 */
public final class TenantSupport {

    private TenantSupport() {
    }

    /** 단계 1 fallback 테넌트 UUID — 단일 DB 운영 가정. 단계 2 Model B 진입 시 fallback 제거. */
    public static final UUID FALLBACK_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * 현재 테넌트 UUID 조회. lib TenantContext 미초기 (게이트 OFF) 시 fallback.
     *
     * <p>단계 2 BE-CC-3 진입 시 fallback 제거 → {@code TenantContext.requireTenantId()} 직접.
     */
    public static UUID currentTenantId() {
        TenantContext ctx = TenantContext.get();
        return ctx != null ? ctx.getTenantId() : FALLBACK_TENANT_ID;
    }
}

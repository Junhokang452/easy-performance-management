/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.platform;

import com.easyware.platform.TenantSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * easy-performance-management 의 {@link TenantSeeder} 구현 — 멀티테넌시 ON 시 lib 필수 SPI
 * (talent `4e709da` 사본 / store-hr 실측 fail-fast #2 충족).
 *
 * <p>performance 스키마엔 tenant 자기행 테이블이 없다 (BE-CC-1 = TenantAwareAuditEntity tenant_id
 * 컬럼 방식 — store-hr 의 `tenant` 테이블과 달리 자기행 시드 불요). 도메인 시드도 없음 (평가
 * 사이클·정책·KPI 는 전부 운영 입력 + rm_* 은 hcm S2S 수신). 따라서 본 시더는 <b>멱등 no-op</b> —
 * 프로비저닝 흐름 로그만 박제. 시드가 생기면(정책 카탈로그 등) 본 메서드에 ON CONFLICT 멱등 삽입 추가.
 */
@Component
@ConditionalOnProperty(name = "easyware.neon.multitenancy-enabled", havingValue = "true")
public class PerformanceTenantSeeder implements TenantSeeder {

    private static final Logger log = LoggerFactory.getLogger(PerformanceTenantSeeder.class);

    @Override
    public void seed(String migrationJdbcUrl, UUID tenantId, String tenantCode, String tenantName) {
        log.info("[performance-tenant-seeder] no-op (performance 는 시드 대상 테이블 없음) tenantId={} code={}",
            tenantId, tenantCode);
    }
}

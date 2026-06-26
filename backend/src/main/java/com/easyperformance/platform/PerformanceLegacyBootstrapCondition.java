/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.platform;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * ADR-055 b2.2 canary — legacy 3-SPI 부트스트랩 경로({@link PerformanceTenantBootstrapConfig} +
 * {@link PerformanceTenantBootstrapController}) 활성 조건 (recruit/store-hr/talent/learning 정합).
 *
 * <p>{@code easyplatform.performance.stage2.enabled=true} <b>AND</b>
 * {@code easyplatform.performance.stage2.self-migrate-enabled=false|미설정} 일 때만 legacy 경로 ON.
 *
 * <p>self-migrate-enabled=true(canary) 면 legacy Config/Controller 가 <b>미등록</b>되어
 * {@link PerformanceTenantSelfBootstrapScheduler} 의 Option B(self-migrate) 단일 경로만 남는다
 * ({@code TenantBootstrap.bootstrap()} 우회 — 이중 경로 제거). 비-canary 환경(self-migrate 미설정)은
 * {@code matchIfMissing=true} 로 현행 거동(stage2.enabled 만으로 legacy ON) 보존.
 */
class PerformanceLegacyBootstrapCondition extends AllNestedConditions {

    PerformanceLegacyBootstrapCondition() {
        // @Configuration + @RestController 양쪽 부착 → component/bean 등록 단계 평가
        // (core ControlPlaneOwnerCondition 의 REGISTER_BEAN 정합).
        super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(name = "easyplatform.performance.stage2.enabled", havingValue = "true")
    static class Stage2Enabled {
    }

    @ConditionalOnProperty(name = "easyplatform.performance.stage2.self-migrate-enabled",
            havingValue = "false", matchIfMissing = true)
    static class SelfMigrateDisabled {
    }
}

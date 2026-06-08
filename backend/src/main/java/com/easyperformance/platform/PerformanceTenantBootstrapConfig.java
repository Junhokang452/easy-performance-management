/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.platform;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.easyware.platform.NeonProvisioningService;
import com.easyware.platform.PlatformTenant;
import com.easyware.platform.PlatformTenantStore;
import com.easyware.platform.tenant.TenantBootstrap;

/**
 * 단계 2 (Model B per-tenant DB) 진입 — lib BE 14 {@link TenantBootstrap} 3 SPI seam performance thin adapter.
 *
 * <p><b>Task #100 G65 D=A, 2026-06-08</b>: 자매품 9호 (easy-performance-management) 단계 2 cutover.
 * ADR-031 정합 (B2B-Enterprise per-tenant + SMB Shared, ware/hcm/recruit 패턴) — ~~듀얼 모드 5호~~ 폐기 후
 * B2B-Enterprise per-tenant 본질 진입. 단계 1 (`b83acac`) 에서 BE-CC-1 TenantAware + tenant_id 컬럼 확보,
 * 본 단계 2 에서 lib NeonProvisioningService 통합 + 3 SPI seam thin adapter 빈 등록.
 *
 * <p><b>위임 전략 — 중복 0</b>: performance 자체 프로비저닝 코드는 작성하지 않고 lib
 * {@link NeonProvisioningService} 와 {@link PlatformTenantStore} 에 thin adapter 로 위임. 자매품 9 의
 * 프로비저닝 흐름이 lib 한 곳에 수렴 (중복 0) — sign 1호 + jobeval 2호 + mra 3호 모범 정렬.
 *
 * <p><b>패턴 정합</b>:
 * <ul>
 *   <li>jobeval `bd46134` ({@code JobEvalTenantBootstrapConfig}) — 단순 thin adapter + cross-product 가드</li>
 *   <li>mra `b8ddcf0` ({@code MraTenantBootstrapConfig}) — {@link ObjectProvider} 듀얼 폴백 (옵션 A lib 위임 /
 *       옵션 B 셀프 폴백) — dev 환경에서 NEON_API_KEY 부재 시 부팅 가능</li>
 *   <li>performance — <b>두 패턴 결합</b>: ObjectProvider 듀얼 폴백 + cross-product "PERFORMANCE" 가드</li>
 * </ul>
 *
 * <p><b>3 SPI seam 매핑</b>:
 * <ul>
 *   <li>{@link TenantBootstrap.PlatformTenantLookup} → {@link PlatformTenantStore#require} 위임 (옵션 A) /
 *       Optional.empty (옵션 B 폴백)</li>
 *   <li>{@link TenantBootstrap.TenantProvisioner#ensureProject} →
 *       {@link NeonProvisioningService#createCustomerProject} 위임 (옵션 A) / 셀프 폴백 NO-OP (옵션 B)</li>
 *   <li>{@link TenantBootstrap.TenantProvisioner#ensureProductDb} →
 *       {@link NeonProvisioningService#activateSiblingDatabase} 위임 (옵션 A) / NO-OP (옵션 B)</li>
 *   <li>{@link TenantBootstrap.TenantMigrator#migrate} → no-op (ensureProductDb 가 이미 Flyway 실행)</li>
 * </ul>
 *
 * <p><b>cross-product 가드</b>: {@link #guardSiblingCode} — performance 어댑터는 "PERFORMANCE" / "performance"
 * 코드만 처리. 다른 자매품 코드 호출 시 {@link IllegalArgumentException} — cross-product 호출 방어.
 *
 * <p><b>게이트 분리</b> (옵션 A 보수, dev ON / LIVE OFF):
 * <ul>
 *   <li>{@code easyplatform.performance.stage2.enabled=true} (본 어댑터 빈 등록 게이트)</li>
 *   <li>{@code easyplatform.tenantbootstrap.enabled=true} (lib TenantBootstrap autoconfig 활성)</li>
 *   <li>{@code easyware.neon.multitenancy-enabled=true} (lib NeonProvisioningService 활성)</li>
 * </ul>
 *
 * <p><b>LIVE 영향 0</b>: performance LIVE 미운영 + application-prod.yml 명시 OFF 가드 + ObjectProvider
 * 옵션 B 폴백으로 NEON_API_KEY 부재 시도 부팅 가능.
 *
 * <p><b>토폴로지 정합 (2026-06-08 정정, Task #98)</b>: B2B-Enterprise per-tenant 본질 진입 (ware/hcm/recruit
 * 패턴) — ADR-031 정합. ~~듀얼 모드 5호~~ 박제 폐기 후 본 단계 2 가 본질 진입. 단계 5 SMB Shared 옵션은
 * 별도 슬라이스 (G67 결정 대기).
 *
 * <p><b>표준</b>: ADR-013 (Neon Model B) · ADR-024 (1 고객사 = 1 Neon 프로젝트) · ADR-031 (자매품 9 × 3
 * 토폴로지) · easy-platform-core lib BE 14 TenantBootstrap (#653) · 단계 1 cutover (`b83acac`).
 *
 * @since stage 2 (Task #100, 2026-06-08)
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "easyplatform.performance.stage2.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class PerformanceTenantBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(PerformanceTenantBootstrapConfig.class);

    /**
     * performance 자매품 코드 — lib {@link TenantBootstrap.TenantProvisioner#ensureProductDb} 호출 시 명시.
     * NeonProvisioningService.activateSiblingDatabase 가 "easyrecord-performance" DB 발행 + Flyway fan-out.
     */
    static final String PERFORMANCE_APP_CODE = "PERFORMANCE";

    /**
     * SPI #1 — Control plane platform_tenant 조회.
     *
     * <p>옵션 A: lib {@link PlatformTenantStore} 빈 존재 시 위임 ({@code easyware.neon.multitenancy-enabled=true}).
     * <p>옵션 B: 빈 부재 시 NO-OP (Optional.empty) — lib TenantBootstrap.bootstrap 흐름이 "신규" 로 인식 →
     *   옵션 B provisioner 가 셀프 폴백.
     *
     * @param platformTenantStore lib control plane 조회 (Optional — dev 단계 부재 정상).
     */
    @Bean
    public TenantBootstrap.PlatformTenantLookup performanceTenantLookup(
            ObjectProvider<PlatformTenantStore> platformTenantStore) {
        return (UUID tenantId) -> {
            PlatformTenantStore store = platformTenantStore.getIfAvailable();
            if (store == null) {
                log.debug("[performance-stage2/lookup] PlatformTenantStore 빈 부재 — 옵션 B NO-OP. tenantId={}",
                        tenantId);
                return Optional.empty();
            }
            try {
                PlatformTenant tenant = store.require(tenantId);
                log.debug("[performance-stage2/lookup] 옵션 A lib 위임 — tenantId={} status={}",
                        tenantId, tenant.status());
                return Optional.of(tenant);
            } catch (RuntimeException ex) {
                log.debug("[performance-stage2/lookup] lib store.require 실패 — 옵션 B 폴백. tenantId={} err={}",
                        tenantId, ex.getMessage());
                return Optional.empty();
            }
        };
    }

    /**
     * SPI #2 — Neon 프로젝트 + per-sibling DB 프로비저닝.
     *
     * <p>옵션 A: lib {@link NeonProvisioningService} 빈 존재 시 위임:
     * <ul>
     *   <li>{@code ensureProject} → {@code createCustomerProject(tenantId)} 멱등.</li>
     *   <li>{@code ensureProductDb} → {@code activateSiblingDatabase(tenantId, "PERFORMANCE")} 멱등.</li>
     * </ul>
     *
     * <p>옵션 B: 빈 부재 시 셀프 폴백 (warn 로그) — performance 단일 DB 모드 보존 (단계 1 정합).
     *
     * @param neonProvisioning lib NeonProvisioningService (Optional — multitenancy-enabled OFF 시 부재).
     */
    @Bean
    public TenantBootstrap.TenantProvisioner performanceTenantProvisioner(
            ObjectProvider<NeonProvisioningService> neonProvisioning) {
        return new TenantBootstrap.TenantProvisioner() {
            @Override
            public void ensureProject(TenantBootstrap.BootstrapRequest request) {
                guardSiblingCode(request.siblingCode());
                NeonProvisioningService service = neonProvisioning.getIfAvailable();
                if (service == null) {
                    log.warn("[performance-stage2/ensureProject] 옵션 B 셀프 폴백 — Neon project 발행 SKIP. "
                            + "request={} (단일 DB 모드 보존, NEON_API_KEY 정합 후 옵션 A 활성)", request);
                    return;
                }
                log.info("[performance-stage2/ensureProject] 옵션 A lib 위임 — createCustomerProject. tenantId={}",
                        request.tenantId());
                String projectId = service.createCustomerProject(request.tenantId());
                log.info("[performance-stage2/ensureProject] tenantId={} → neonProjectId={}",
                        request.tenantId(), projectId);
            }

            @Override
            public void ensureProductDb(UUID tenantId, String siblingCode) {
                guardSiblingCode(siblingCode);
                NeonProvisioningService service = neonProvisioning.getIfAvailable();
                if (service == null) {
                    log.warn("[performance-stage2/ensureProductDb] 옵션 B 셀프 폴백 — easyrecord-performance DB "
                            + "분리 SKIP. tenantId={} sibling={} (단일 DB 모드 보존)", tenantId, siblingCode);
                    return;
                }
                log.info("[performance-stage2/ensureProductDb] 옵션 A lib 위임 — activateSiblingDatabase. "
                        + "tenantId={} sibling={}", tenantId, siblingCode);
                // activateSiblingDatabase 가 Neon DB 생성 + migrateAndSeed(Flyway db/tenant) + 앱 role 발급
                // + tenant_product_db 행 ACTIVE 기록 일괄. 멱등.
                service.activateSiblingDatabase(tenantId, PERFORMANCE_APP_CODE);
            }
        };
    }

    /**
     * SPI #3 — Flyway 마이그. 본 어댑터에서는 no-op (ensureProductDb 가 이미 Flyway 실행).
     *
     * <p>lib {@link TenantBootstrap#bootstrap(String, UUID)} 흐름은 ensureProject → ensureProductDb → migrate
     * 순. {@link NeonProvisioningService#activateSiblingDatabase} 가 내부에서 {@code migrateAndSeed} 호출하므로
     * 본 단계에서 추가 Flyway 호출은 중복 (이중 적용 위험). 명시 no-op + 가드 로그로 흐름 가시화.
     */
    @Bean
    public TenantBootstrap.TenantMigrator performanceTenantMigrator() {
        return (UUID tenantId, String siblingCode) -> {
            guardSiblingCode(siblingCode);
            log.debug("[performance-stage2/migrate] tenantId={} sibling={} → no-op "
                    + "(ensureProductDb 내장 Flyway 사용)", tenantId, siblingCode);
        };
    }

    /**
     * cross-product 호출 방어 — performance 어댑터는 "PERFORMANCE" 코드만 처리.
     *
     * @param siblingCode lib SPI 진입 파라미터
     * @throws IllegalArgumentException 잘못된 자매품 코드
     */
    private static void guardSiblingCode(String siblingCode) {
        if (!PERFORMANCE_APP_CODE.equalsIgnoreCase(siblingCode)
                && !"performance".equalsIgnoreCase(siblingCode)) {
            throw new IllegalArgumentException(
                    "PerformanceTenantBootstrapConfig only handles PERFORMANCE sibling, got: " + siblingCode);
        }
    }

    /** performance 자매품 코드 노출 — integration test 등 외부 검증 사이트. */
    public static String siblingCode() {
        return PERFORMANCE_APP_CODE;
    }
}

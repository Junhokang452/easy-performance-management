/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.easyperformance.platform.PerformanceTenantBootstrapConfig;
import com.easyware.platform.NeonProvisioningService;
import com.easyware.platform.PlatformTenant;
import com.easyware.platform.PlatformTenantStore;
import com.easyware.platform.tenant.TenantBootstrap;

/**
 * G65 D=A 단계 2 NeonProvisioningService 실 통합 integration test (Task #100, 2026-06-08).
 *
 * <p><b>박제</b>: {@code _workspace/PERFORMANCE_STAGE2_CUTOVER_2026-06-08.md}.
 *
 * <p><b>검증 시나리오 (T1~T6 + 가드)</b>:
 * <ul>
 *   <li>T0 — 자매품 코드 정합 ("PERFORMANCE").</li>
 *   <li>T1 — SPI 3 빈 모두 옵션 B 폴백 모드에서 정상 동작 (단일 DB 모드 보존).</li>
 *   <li>T2 — 옵션 A 분기 (lib NeonProvisioningService 빈 존재) → createCustomerProject + activateSiblingDatabase 위임.</li>
 *   <li>T3 — 옵션 B 분기 (lib 빈 부재) → 셀프 폴백 (warn 로그, 예외 없음).</li>
 *   <li>T4 — PlatformTenantLookup 옵션 A 분기 (PlatformTenantStore 빈 존재) → require 위임.</li>
 *   <li>T5 — PlatformTenantLookup 옵션 B 분기 (빈 부재) → Optional.empty.</li>
 *   <li>T6 — cross-product 가드: 잘못된 siblingCode 호출 시 IllegalArgumentException.</li>
 * </ul>
 *
 * <p><b>검증 방식</b>: lib `NeonProvisioningService` + `PlatformTenantStore` 는 의존성이 많아 full
 * @SpringBootTest 부담이 큼 — `ObjectProvider` mock 으로 옵션 A/B 분기를 직접 검증. mra
 * `NeonProvisioningIntegrationTest` 정합.
 *
 * <p><b>회귀 가드</b>:
 * <ul>
 *   <li>SPI 빈 등록 회귀 0.</li>
 *   <li>옵션 A/B 분기 회귀 0.</li>
 *   <li>cross-product 가드 회귀 0 (PERFORMANCE 만 처리).</li>
 *   <li>NO-OP migrate 회귀 0 (ensureProductDb 내장 Flyway 사용).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NeonProvisioningIntegrationTest {

    private static final UUID TEST_TENANT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    @Mock
    private NeonProvisioningService neonProvisioningService;

    @Mock
    private PlatformTenantStore platformTenantStore;

    /** PlatformTenant 는 record (final) — mockito mock 불가 → 실 인스턴스 사용. */
    private final PlatformTenant platformTenant = new PlatformTenant(
            TEST_TENANT_ID,            // id
            "test-tenant",             // code
            "Test Tenant",             // name
            PlatformTenant.Status.ACTIVE,
            "aws-ap-northeast-2",      // region
            "test-admin",              // adminUsername
            "admin@test.local",        // adminEmail
            "neon-project-id",         // neonProjectId
            null,                      // lastError
            "KR",                      // countryCode
            "ko-KR",                   // defaultLocale
            "Asia/Seoul",              // timezone
            "KRW",                     // currencyCode
            null,                      // industryCode
            null,                      // companySize
            "FREE",                    // planCode
            "[\"PERFORMANCE\"]",      // enabledModules
            OffsetDateTime.now(),      // createdAt
            OffsetDateTime.now()       // updatedAt
    );

    private final PerformanceTenantBootstrapConfig config = new PerformanceTenantBootstrapConfig();

    @Test
    void siblingCodeIsPerformance() {
        // T0 — 자매품 코드 정합 (lib activateSiblingDatabase 호출 시 사용).
        assertThat(PerformanceTenantBootstrapConfig.siblingCode()).isEqualTo("PERFORMANCE");
    }

    @Test
    void provisionerOptionB_NoLibBean_FallsBackToSelfBootstrap() {
        // T3 — 옵션 B 분기: lib NeonProvisioningService 빈 부재 시 셀프 폴백 (warn 로그 + 예외 없음).
        ObjectProvider<NeonProvisioningService> emptyProvider = emptyObjectProvider();
        TenantBootstrap.TenantProvisioner provisioner = config.performanceTenantProvisioner(emptyProvider);

        TenantBootstrap.BootstrapRequest request = new TenantBootstrap.BootstrapRequest(
                TEST_TENANT_ID, PerformanceTenantBootstrapConfig.siblingCode());

        // ensureProject + ensureProductDb 모두 NO-OP (warn 로그 후 return) — 예외 없음.
        assertThatNoException().isThrownBy(() -> provisioner.ensureProject(request));
        assertThatNoException().isThrownBy(() ->
                provisioner.ensureProductDb(TEST_TENANT_ID, PerformanceTenantBootstrapConfig.siblingCode()));
    }

    @Test
    void provisionerOptionA_LibBeanPresent_DelegatesToNeonService() {
        // T2 — 옵션 A 분기: lib NeonProvisioningService 빈 존재 시 위임.
        ObjectProvider<NeonProvisioningService> provider = objectProviderOf(neonProvisioningService);
        TenantBootstrap.TenantProvisioner provisioner = config.performanceTenantProvisioner(provider);

        TenantBootstrap.BootstrapRequest request = new TenantBootstrap.BootstrapRequest(
                TEST_TENANT_ID, PerformanceTenantBootstrapConfig.siblingCode());

        // ensureProject 호출 → lib.createCustomerProject 위임.
        org.mockito.Mockito.when(neonProvisioningService.createCustomerProject(TEST_TENANT_ID))
                .thenReturn("neon-project-id");
        provisioner.ensureProject(request);
        org.mockito.Mockito.verify(neonProvisioningService).createCustomerProject(TEST_TENANT_ID);

        // ensureProductDb 호출 → lib.activateSiblingDatabase 위임.
        provisioner.ensureProductDb(TEST_TENANT_ID, PerformanceTenantBootstrapConfig.siblingCode());
        org.mockito.Mockito.verify(neonProvisioningService).activateSiblingDatabase(
                TEST_TENANT_ID, PerformanceTenantBootstrapConfig.siblingCode());
    }

    @Test
    void lookupOptionB_NoStoreBean_ReturnsEmpty() {
        // T5 — PlatformTenantLookup 옵션 B 분기: PlatformTenantStore 빈 부재 시 Optional.empty.
        ObjectProvider<PlatformTenantStore> emptyProvider = emptyObjectProvider();
        TenantBootstrap.PlatformTenantLookup lookup = config.performanceTenantLookup(emptyProvider);

        Optional<PlatformTenant> result = lookup.findById(TEST_TENANT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void lookupOptionA_StoreBeanPresent_DelegatesToRequire() {
        // T4 — PlatformTenantLookup 옵션 A 분기: PlatformTenantStore 빈 존재 시 require 위임.
        ObjectProvider<PlatformTenantStore> provider = objectProviderOf(platformTenantStore);
        org.mockito.Mockito.when(platformTenantStore.require(TEST_TENANT_ID)).thenReturn(platformTenant);

        TenantBootstrap.PlatformTenantLookup lookup = config.performanceTenantLookup(provider);
        Optional<PlatformTenant> result = lookup.findById(TEST_TENANT_ID);

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(platformTenant);
        assertThat(result.get().status()).isEqualTo(PlatformTenant.Status.ACTIVE);
    }

    @Test
    void lookupOptionA_StoreThrows_FallsBackToEmpty() {
        // T4.1 — PlatformTenantStore.require 가 예외를 던지면 옵션 B 폴백 (Optional.empty + debug 로그).
        ObjectProvider<PlatformTenantStore> provider = objectProviderOf(platformTenantStore);
        org.mockito.Mockito.when(platformTenantStore.require(TEST_TENANT_ID))
                .thenThrow(new IllegalStateException("tenant not found"));

        TenantBootstrap.PlatformTenantLookup lookup = config.performanceTenantLookup(provider);
        Optional<PlatformTenant> result = lookup.findById(TEST_TENANT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void migratorIsNoOpInSingleDbMode() {
        // T1.M — TenantMigrator 는 NO-OP (ensureProductDb 내장 Flyway 사용, 예외 없음).
        TenantBootstrap.TenantMigrator migrator = config.performanceTenantMigrator();
        assertThatNoException().isThrownBy(() ->
                migrator.migrate(TEST_TENANT_ID, PerformanceTenantBootstrapConfig.siblingCode()));
    }

    @Test
    void allThreeSpiBeansAreFunctional_OptionB() {
        // T1 — SPI 3 빈 모두 옵션 B 폴백 모드에서 정상 동작 (단일 DB 모드 보존).
        ObjectProvider<PlatformTenantStore> emptyStore = emptyObjectProvider();
        ObjectProvider<NeonProvisioningService> emptyNeon = emptyObjectProvider();

        TenantBootstrap.PlatformTenantLookup lookup = config.performanceTenantLookup(emptyStore);
        TenantBootstrap.TenantProvisioner provisioner = config.performanceTenantProvisioner(emptyNeon);
        TenantBootstrap.TenantMigrator migrator = config.performanceTenantMigrator();

        assertThat(lookup).isNotNull();
        assertThat(provisioner).isNotNull();
        assertThat(migrator).isNotNull();

        // 전체 흐름 시뮬레이션 (lib TenantBootstrap.bootstrap 패턴 모사).
        assertThat(lookup.findById(TEST_TENANT_ID)).isEmpty();
        assertThatNoException().isThrownBy(() -> provisioner.ensureProject(
                new TenantBootstrap.BootstrapRequest(TEST_TENANT_ID, "PERFORMANCE")));
        assertThatNoException().isThrownBy(() ->
                provisioner.ensureProductDb(TEST_TENANT_ID, "PERFORMANCE"));
        assertThatNoException().isThrownBy(() -> migrator.migrate(TEST_TENANT_ID, "PERFORMANCE"));
    }

    @Test
    void crossProductGuard_RejectsForeignSiblingCode_OnEnsureProject() {
        // T6.1 — cross-product 가드: provisioner.ensureProject 가 잘못된 siblingCode 거부.
        ObjectProvider<NeonProvisioningService> emptyProvider = emptyObjectProvider();
        TenantBootstrap.TenantProvisioner provisioner = config.performanceTenantProvisioner(emptyProvider);

        TenantBootstrap.BootstrapRequest foreignRequest = new TenantBootstrap.BootstrapRequest(
                TEST_TENANT_ID, "MRA");

        assertThatThrownBy(() -> provisioner.ensureProject(foreignRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PerformanceTenantBootstrapConfig only handles PERFORMANCE sibling");
    }

    @Test
    void crossProductGuard_RejectsForeignSiblingCode_OnEnsureProductDb() {
        // T6.2 — cross-product 가드: provisioner.ensureProductDb 가 잘못된 siblingCode 거부.
        ObjectProvider<NeonProvisioningService> emptyProvider = emptyObjectProvider();
        TenantBootstrap.TenantProvisioner provisioner = config.performanceTenantProvisioner(emptyProvider);

        assertThatThrownBy(() -> provisioner.ensureProductDb(TEST_TENANT_ID, "JOBEVAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PerformanceTenantBootstrapConfig only handles PERFORMANCE sibling");
    }

    @Test
    void crossProductGuard_RejectsForeignSiblingCode_OnMigrate() {
        // T6.3 — cross-product 가드: migrator 도 동일하게 잘못된 siblingCode 거부.
        TenantBootstrap.TenantMigrator migrator = config.performanceTenantMigrator();

        assertThatThrownBy(() -> migrator.migrate(TEST_TENANT_ID, "SIGN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PerformanceTenantBootstrapConfig only handles PERFORMANCE sibling");
    }

    @Test
    void crossProductGuard_AcceptsLowercaseSiblingCode() {
        // T6.4 — cross-product 가드: 소문자 "performance" 도 허용 (lib SPI 코드 정합).
        ObjectProvider<NeonProvisioningService> emptyProvider = emptyObjectProvider();
        TenantBootstrap.TenantProvisioner provisioner = config.performanceTenantProvisioner(emptyProvider);

        assertThatNoException().isThrownBy(() -> provisioner.ensureProductDb(TEST_TENANT_ID, "performance"));
    }

    // ─── 헬퍼: ObjectProvider 간이 구현 (mra NeonProvisioningIntegrationTest 정합) ───

    private static <T> ObjectProvider<T> objectProviderOf(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return instance;
            }

            @Override
            public T getObject() {
                return instance;
            }

            @Override
            public T getIfAvailable() {
                return instance;
            }

            @Override
            public T getIfUnique() {
                return instance;
            }
        };
    }

    private static <T> ObjectProvider<T> emptyObjectProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                throw new IllegalStateException("no bean");
            }

            @Override
            public T getObject() {
                throw new IllegalStateException("no bean");
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }
        };
    }
}

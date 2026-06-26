package com.easyperformance.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.easyware.platform.PlatformTenantStore;
import com.easyware.platform.TenantDataSourceRegistry;
import javax.sql.DataSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * ADR-055 b2.2 (2026-06-26): the Option B {@code seedIfAbsent(UUID, DataSource)} overload must
 * <b>throw</b> on a genuine seed failure (no {@link PlatformTenantStore} bean / lookup failure) — not
 * return false — because the scheduler hook {@code seedAdmin(UUID, DataSource)} is {@code void} and a
 * swallowed false would let {@code completeBootstrapWithSchemaVersion} promote a tenant to ACTIVE
 * without an admin row. Also guards the suite admin-email standard.
 */
class PerformanceInitialAdminSeederTest {

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        return (ObjectProvider<T>) mock(ObjectProvider.class);
    }

    @Test
    void adminEmailIsSetupNotLocal() {
        assertThat(String.format(PerformanceInitialAdminSeeder.DEFAULT_ADMIN_EMAIL_TEMPLATE, "acme"))
            .isEqualTo("admin@acme.setup");
    }

    @Test
    void optionBSeed_throwsWhenTenantStoreBeanAbsent() {
        // tenantStore ObjectProvider.getIfAvailable() == null → 시드 실패를 false 로 삼키지 않고 throw.
        PerformanceInitialAdminSeeder seeder =
                new PerformanceInitialAdminSeeder(emptyProvider(), emptyProvider());
        assertThatThrownBy(() -> seeder.seedIfAbsent(UUID.randomUUID(), mock(DataSource.class)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void optionBSeed_throwsWhenTenantLookupFails() {
        @SuppressWarnings("unchecked")
        ObjectProvider<PlatformTenantStore> storeProvider = mock(ObjectProvider.class);
        PlatformTenantStore store = mock(PlatformTenantStore.class);
        when(storeProvider.getIfAvailable()).thenReturn(store);
        when(store.require(any())).thenThrow(new IllegalStateException("tenant not found"));

        PerformanceInitialAdminSeeder seeder = new PerformanceInitialAdminSeeder(
                PerformanceInitialAdminSeederTest.<TenantDataSourceRegistry>emptyProvider(), storeProvider);
        assertThatThrownBy(() -> seeder.seedIfAbsent(UUID.randomUUID(), mock(DataSource.class)))
            .isInstanceOf(IllegalStateException.class);
    }
}

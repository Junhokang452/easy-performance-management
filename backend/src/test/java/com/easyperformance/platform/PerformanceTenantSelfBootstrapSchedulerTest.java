package com.easyperformance.platform;

import com.easyware.platform.TenantProductDb;
import com.easyware.platform.TenantProductDbStore;
import com.easyware.platform.tenant.TenantBootstrap;
import com.easyware.platform.tenant.TenantSchemaMigrator;
import io.micrometer.core.instrument.MeterRegistry;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PerformanceTenantSelfBootstrapSchedulerTest {

    private static final UUID CANARY = UUID.fromString("019eb0d5-0000-7000-8000-000000000011");
    private static final UUID OTHER = UUID.fromString("019eb0d5-0000-7000-8000-000000000012");
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V(.+)__.+\\.sql$");

    @Test
    void expectedVersionTracksLatestPackagedMigration() throws IOException {
        assertThat(scheduler(false, false, "").expectedVersion())
                .isEqualTo(latestPackagedMigrationVersion());
    }

    @Test
    void activeDriftRequiresBothExplicitOptInAndCanaryMembership() {
        TestableScheduler disabled = scheduler(false, false, CANARY.toString());
        assertThat(disabled.driftEnabled()).isFalse();

        TestableScheduler enabled = scheduler(false, true, CANARY + ", " + CANARY);
        assertThat(enabled.driftEnabled()).isTrue();
        assertThat(enabled.allows(active(CANARY))).isTrue();
        assertThat(enabled.allows(active(OTHER))).isFalse();
    }

    @Test
    void provisioningRowsRemainEligibleWithoutCanaryMembership() {
        TestableScheduler scheduler = scheduler(true, true, "");

        assertThat(scheduler.allows(provisioning(OTHER))).isTrue();
        assertThat(scheduler.allows(active(OTHER))).isFalse();
    }

    @Test
    void malformedCanaryTenantIdFailsFast() {
        assertThatThrownBy(() -> scheduler(false, true, "not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active-drift-canary-tenant-ids");
    }

    private static String latestPackagedMigrationVersion() throws IOException {
        Resource[] migrations = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*.sql");

        assertThat(migrations).as("performance Flyway migrations").isNotEmpty();
        return Arrays.stream(migrations)
                .map(PerformanceTenantSelfBootstrapSchedulerTest::migrationVersion)
                .max(MigrationVersion::compareTo)
                .orElseThrow()
                .getVersion();
    }

    private static MigrationVersion migrationVersion(Resource resource) {
        String filename = Objects.requireNonNull(resource.getFilename(), "migration filename");
        Matcher matcher = VERSIONED_MIGRATION.matcher(filename);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unexpected migration filename: " + filename);
        }
        return MigrationVersion.fromVersion(matcher.group(1).replace('_', '.'));
    }

    private static TestableScheduler scheduler(boolean selfMigrate, boolean activeDrift, String canaries) {
        return new TestableScheduler(provider(), provider(), provider(), provider(),
                mock(PerformanceInitialAdminSeeder.class), selfMigrate, activeDrift, canaries);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider() {
        return mock(ObjectProvider.class);
    }

    private static TenantProductDb active(UUID tenantId) {
        return row(tenantId, TenantProductDb.Status.ACTIVE);
    }

    private static TenantProductDb provisioning(UUID tenantId) {
        return row(tenantId, TenantProductDb.Status.PROVISIONING);
    }

    private static TenantProductDb row(UUID tenantId, TenantProductDb.Status status) {
        OffsetDateTime now = OffsetDateTime.now();
        return new TenantProductDb(UUID.randomUUID(), tenantId, "PERFORMANCE", status, "performance",
                "encrypted-app", "encrypted-owner", "20260612.001", null, now, now);
    }

    private static final class TestableScheduler extends PerformanceTenantSelfBootstrapScheduler {
        TestableScheduler(ObjectProvider<TenantProductDbStore> productDbStore,
                          ObjectProvider<TenantBootstrap> tenantBootstrap,
                          ObjectProvider<MeterRegistry> meterRegistry,
                          ObjectProvider<TenantSchemaMigrator> schemaMigrator,
                          PerformanceInitialAdminSeeder adminSeeder,
                          boolean selfMigrateEnabled,
                          boolean activeDriftEnabled,
                          String activeDriftCanaryTenantIds) {
            super(productDbStore, tenantBootstrap, meterRegistry, schemaMigrator, adminSeeder,
                    selfMigrateEnabled, activeDriftEnabled, activeDriftCanaryTenantIds);
        }

        String expectedVersion() {
            return expectedSchemaVersion();
        }

        boolean driftEnabled() {
            return activeDriftEnabled();
        }

        boolean allows(TenantProductDb row) {
            return rolloutAllows(row);
        }
    }
}

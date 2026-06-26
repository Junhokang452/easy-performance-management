package com.easyperformance.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * {@link PerformanceLegacyBootstrapCondition} — legacy 3-SPI 경로 게이트(ADR-055 b2.2) 회귀 가드.
 *
 * <p>legacy config/controller 자체를 띄우지 않고(외부 DS/멀티테넌시 의존 회피), 동일 조건을 붙인 경량
 * probe 빈의 등록/미등록으로 조건 진리표를 검증한다.
 */
class PerformanceLegacyBootstrapConditionTest {

    @Configuration(proxyBeanMethods = false)
    @Conditional(PerformanceLegacyBootstrapCondition.class)
    static class LegacyProbe {
        @Bean
        String performanceLegacyMarker() {
            return "legacy";
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LegacyProbe.class);

    @Test
    void stage2Enabled_selfMigrateMissing_registersLegacy() {
        runner.withPropertyValues("easyplatform.performance.stage2.enabled=true")
                .run(ctx -> assertThat(ctx).hasBean("performanceLegacyMarker"));
    }

    @Test
    void stage2Enabled_selfMigrateFalse_registersLegacy() {
        runner.withPropertyValues(
                        "easyplatform.performance.stage2.enabled=true",
                        "easyplatform.performance.stage2.self-migrate-enabled=false")
                .run(ctx -> assertThat(ctx).hasBean("performanceLegacyMarker"));
    }

    @Test
    void stage2Enabled_selfMigrateTrue_excludesLegacy() {
        runner.withPropertyValues(
                        "easyplatform.performance.stage2.enabled=true",
                        "easyplatform.performance.stage2.self-migrate-enabled=true")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("performanceLegacyMarker"));
    }

    @Test
    void stage2Disabled_excludesLegacy() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean("performanceLegacyMarker"));
    }
}

/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.config;

import jakarta.annotation.Nullable;
import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * mono 배포 (Render Docker) — Spring Boot 가 FE 정적 자원 (Vite build dist/) + SPA 진입점 서빙
 * (store-hr `bfe0076` 사본 / mra·hcm·time 자매품 정합).
 *
 * <p>경로 정책:
 * <ul>
 *   <li>{@code spring.web.resources.static-locations=file:/app/static/,classpath:/static/}
 *       (Dockerfile ENTRYPOINT 가 주입 — application-prod.yml 정합). Vite 산출물(`/assets/*`,
 *       `/index.html`, `/favicon.ico` 등)이 그대로 서빙.</li>
 *   <li>{@code /**} 핸들러 — 정적 자원이 존재하면 그대로, 미존재 + 비 API 경로면 {@code index.html}
 *       폴백 (React Router 진입 보존).</li>
 *   <li>{@code /api/**} / {@code /actuator/**} / {@code /v3/api-docs/**} / {@code /swagger-ui/**} 는
 *       본 핸들러에서 제외 (SecurityConfig 매처 + Spring MVC 핸들러가 우선).</li>
 * </ul>
 *
 * <p>인증은 BE API 호출 시점에 {@code JwtAuthFilter} 가 책임 (정적 자원/index.html 자체는 permitAll —
 * SecurityConfig store-hr `0c4a262` 매처 정합).
 */
@Configuration("performanceSpaForwardingConfig")
public class SpaForwardingConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry
            .addResourceHandler("/**")
            .addResourceLocations("file:/app/static/", "classpath:/static/")
            .resourceChain(true)
            .addResolver(new SpaPathResourceResolver());
    }

    /**
     * 정적 자원 미존재 시 {@code index.html} 폴백 — React Router 진입 보존.
     *
     * <p>API 경로({@code /api/**}, {@code /actuator/**}, {@code /v3/api-docs/**}, {@code /swagger-ui/**})는
     * Spring MVC handler mapping 이 우선 처리하므로 영향 없음 — 단 안전망으로 명시 제외.
     * 확장자(.js/.css/.png 등) 가 있는데 자원이 없으면 404 유지 (asset hash 불일치 등 진단 명확화 —
     * {@link ResourceNotFoundAdvice} 가 404 보존).
     */
    private static final class SpaPathResourceResolver extends PathResourceResolver {

        @Override
        @Nullable
        protected Resource getResource(@NonNull String resourcePath, @NonNull Resource location) throws IOException {
            Resource requested = super.getResource(resourcePath, location);
            if (requested != null) {
                return requested;
            }
            if (resourcePath.startsWith("api/")
                || resourcePath.startsWith("actuator/")
                || resourcePath.startsWith("v3/api-docs")
                || resourcePath.startsWith("swagger-ui")) {
                return null;
            }
            int lastDot = resourcePath.lastIndexOf('.');
            int lastSlash = resourcePath.lastIndexOf('/');
            boolean looksLikeAsset = lastDot > lastSlash;
            if (looksLikeAsset) {
                return null;
            }
            return super.getResource("index.html", location);
        }
    }
}

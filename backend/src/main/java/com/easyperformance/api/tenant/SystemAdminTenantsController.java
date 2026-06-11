/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.api.tenant;

import com.easyperformance.api.dto.tenant.CreateTenantRequest;
import com.easyperformance.api.dto.tenant.PlatformTenantResponse;
import com.easyware.platform.AppSubscriptionStore;
import com.easyware.platform.NeonProvisioningService;
import com.easyware.platform.PlatformProductConfig;
import com.easyware.platform.PlatformTenantStore;
import com.easyware.platform.error.ApiException;
import com.easyware.platform.error.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * SystemAdmin 테넌트 lifecycle endpoints — store-hr {@code SystemAdminTenantsController}
 * (`f8df3db`) 사본 + recruit/ware {@code PlatformTenantController} 표면 추종.
 *
 * <p>SecurityConfig 의 {@code /api/admin/**} matcher 가 {@code SUPER_ADMIN} 가드 적용
 * — performance {@code JwtAuthFilter} 는 roles claim 을 <b>prefix 없는</b>
 * {@code SimpleGrantedAuthority} 로 매핑하므로 {@code hasAuthority("SUPER_ADMIN")} (실측 —
 * store-hr 의 hasRole 과 의도적 차이).
 *
 * <p><b>create 흐름 (ADR-013/024)</b>: platform_tenants 등록 → PERFORMANCE 구독 시드
 * ({@code app_subscription}) → Neon 프로비저닝 fire-and-forget (수십 초) → 202 즉시.
 * FE 가 5초 폴링으로 PROVISIONING → ACTIVE/FAILED 추적.
 *
 * <p>게이트 OFF (단일 DB 개발) → lib 빈 부재 → 503 + FE 게이트 OFF 안내 카드.
 */
@RestController("performanceSystemAdminTenantsController")
@RequestMapping("/api/admin/tenants")
public class SystemAdminTenantsController {

    private final ObjectProvider<PlatformTenantStore> storeProvider;
    private final ObjectProvider<NeonProvisioningService> provisioningProvider;
    private final ObjectProvider<AppSubscriptionStore> subscriptionsProvider;
    private final ObjectProvider<PlatformProductConfig> productConfigProvider;

    public SystemAdminTenantsController(ObjectProvider<PlatformTenantStore> storeProvider,
                                        ObjectProvider<NeonProvisioningService> provisioningProvider,
                                        ObjectProvider<AppSubscriptionStore> subscriptionsProvider,
                                        ObjectProvider<PlatformProductConfig> productConfigProvider) {
        this.storeProvider = storeProvider;
        this.provisioningProvider = provisioningProvider;
        this.subscriptionsProvider = subscriptionsProvider;
        // 게이트 OFF (단일 DB 개발) 시 performance `PlatformProductConfig` 구현 빈 미등록 — ObjectProvider 로
        // 래핑해 부팅 통과 보장 + 게이트 ON 시점 create 호출에서 503 회피.
        this.productConfigProvider = productConfigProvider;
    }

    @GetMapping
    public List<PlatformTenantResponse> list() {
        PlatformTenantStore store = requireStore();
        return store.findAll().stream().map(PlatformTenantResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PlatformTenantResponse get(@PathVariable UUID id) {
        PlatformTenantStore store = requireStore();
        return PlatformTenantResponse.from(store.require(id));
    }

    @PostMapping
    public ResponseEntity<PlatformTenantResponse> create(@Valid @RequestBody CreateTenantRequest body) {
        PlatformTenantStore store = requireStore();
        var tenant = store.create(body.code(), body.name(), body.region().trim(),
                body.adminUsername(), body.adminEmail());
        // 엔타이틀먼트 — performance 콘솔 생성 = PERFORMANCE 구독 시드.
        // NeonProvisioningService 는 활성 구독을 조회해 라이선스된 제품의 DB 만 만든다 (ware/recruit/store-hr 동일).
        PlatformProductConfig productConfig = requireBean(productConfigProvider);
        requireBean(subscriptionsProvider).ensureActive(tenant.id(), productConfig.appCode());
        // fire-and-forget: background 풀에서 Neon 프로비저닝(수십 초) → 즉시 202.
        // FE 콘솔이 5초 폴링으로 PROVISIONING → ACTIVE 추적.
        requireBean(provisioningProvider).provisionAsync(tenant.id());
        return ResponseEntity.accepted().body(PlatformTenantResponse.from(store.require(tenant.id())));
    }

    /** FAILED 테넌트 재프로비저닝 — prepareRetry 는 동기(상태 검증), 실 작업은 background. */
    @PostMapping("/{id}/retry")
    public ResponseEntity<PlatformTenantResponse> retry(@PathVariable UUID id) {
        PlatformTenantStore store = requireStore();
        NeonProvisioningService provisioning = requireBean(provisioningProvider);
        provisioning.prepareRetry(id);
        provisioning.provisionAsync(id);
        return ResponseEntity.accepted().body(PlatformTenantResponse.from(store.require(id)));
    }

    /** 일시중지 (ACTIVE → SUSPENDED). 접속 차단·데이터 보존. */
    @PostMapping("/{id}/suspend")
    public PlatformTenantResponse suspend(@PathVariable UUID id) {
        PlatformTenantStore store = requireStore();
        requireBean(provisioningProvider).suspend(id);
        return PlatformTenantResponse.from(store.require(id));
    }

    /** 재개 (SUSPENDED → ACTIVE). */
    @PostMapping("/{id}/resume")
    public PlatformTenantResponse resume(@PathVariable UUID id) {
        PlatformTenantStore store = requireStore();
        requireBean(provisioningProvider).resume(id);
        return PlatformTenantResponse.from(store.require(id));
    }

    /** 오프보딩 — Neon 프로젝트 삭제 + control 행 CASCADE. 운영 절차는 export → 일시중지 → 폐기. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        requireBean(provisioningProvider).deprovision(id);
        return ResponseEntity.noContent().build();
    }

    private PlatformTenantStore requireStore() {
        return requireBean(storeProvider);
    }

    /** 게이트 OFF(단일 DB 개발) → lib 빈 부재 → 503 + FE 게이트 OFF 안내. */
    private <T> T requireBean(ObjectProvider<T> provider) {
        T bean = provider.getIfAvailable();
        if (bean == null) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return bean;
    }
}

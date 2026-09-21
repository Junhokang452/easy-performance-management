package com.easyperformance.security;

import com.easyware.platform.PlatformTenant;
import com.easyware.platform.PlatformTenantStore;
import com.easyware.platform.TenantRoutingContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("019eb0d5-0000-7000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("019eb0d5-0000-7000-8000-000000000002");

    @Test
    void refreshRunsRotationInsideRefreshTokensActiveTenantRoute() {
        AtomicBoolean insideTenantRoute = new AtomicBoolean();
        AuthService authService = mock(AuthService.class, invocation -> {
            if (invocation.getMethod().getName().equals("refreshTenantId")) {
                return TENANT_ID;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        @SuppressWarnings("unchecked")
        ObjectProvider<PlatformTenantStore> tenantStoreProvider = mock(ObjectProvider.class);
        PlatformTenantStore tenantStore = mock(PlatformTenantStore.class);
        TenantRoutingContext routingContext = mock(TenantRoutingContext.class);
        PlatformTenant tenant = mock(PlatformTenant.class);
        AuthController controller = new AuthController(authService, tenantStoreProvider, routingContext);
        var request = new AuthDtos.RefreshRequest("signed-refresh");
        var tokens = AuthDtos.TokenResponse.of(
            "access", 300, "rotated-refresh", 604800, USER_ID, TENANT_ID, List.of("EMPLOYEE"));

        when(tenantStoreProvider.getIfAvailable()).thenReturn(tenantStore);
        when(tenantStore.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenant.id()).thenReturn(TENANT_ID);
        when(tenant.status()).thenReturn(PlatformTenant.Status.ACTIVE);
        when(tenant.code()).thenReturn("ACME");
        when(authService.refresh(request)).thenAnswer(invocation -> {
            assertThat(insideTenantRoute).isTrue();
            return tokens;
        });
        doAnswer(invocation -> {
            insideTenantRoute.set(true);
            try {
                return ((Supplier<?>) invocation.getArgument(2)).get();
            } finally {
                insideTenantRoute.set(false);
            }
        }).when(routingContext).within(eq(TENANT_ID), eq("ACME"), any());

        assertThat(controller.refresh(request).getBody()).isEqualTo(tokens);
        verify(tenantStore).findById(TENANT_ID);
        verify(routingContext).within(eq(TENANT_ID), eq("ACME"), any());
    }

    @Test
    void refreshInSingleDatabaseModeDoesNotRequireTenantRouting() {
        AuthService authService = mock(AuthService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PlatformTenantStore> tenantStoreProvider = mock(ObjectProvider.class);
        TenantRoutingContext routingContext = mock(TenantRoutingContext.class);
        AuthController controller = new AuthController(authService, tenantStoreProvider, routingContext);
        var request = new AuthDtos.RefreshRequest("signed-refresh");
        var tokens = AuthDtos.TokenResponse.of(
            "access", 300, "rotated-refresh", 604800, USER_ID, TENANT_ID, List.of("EMPLOYEE"));
        when(tenantStoreProvider.getIfAvailable()).thenReturn(null);
        when(authService.refresh(request)).thenReturn(tokens);

        assertThat(controller.refresh(request).getBody()).isEqualTo(tokens);
        verifyNoInteractions(routingContext);
    }

    @Test
    void inactiveRefreshTenantIsRejectedBeforeTokenRotation() {
        AuthService authService = mock(AuthService.class, invocation -> {
            if (invocation.getMethod().getName().equals("refreshTenantId")) {
                return TENANT_ID;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        @SuppressWarnings("unchecked")
        ObjectProvider<PlatformTenantStore> tenantStoreProvider = mock(ObjectProvider.class);
        PlatformTenantStore tenantStore = mock(PlatformTenantStore.class);
        TenantRoutingContext routingContext = mock(TenantRoutingContext.class);
        PlatformTenant tenant = mock(PlatformTenant.class);
        AuthController controller = new AuthController(authService, tenantStoreProvider, routingContext);
        var request = new AuthDtos.RefreshRequest("signed-refresh");
        when(tenantStoreProvider.getIfAvailable()).thenReturn(tenantStore);
        when(tenantStore.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenant.status()).thenReturn(PlatformTenant.Status.SUSPENDED);

        assertThatThrownBy(() -> controller.refresh(request))
            .isInstanceOf(com.easyware.platform.error.ApiException.class);
        verify(authService, org.mockito.Mockito.never()).refresh(request);
        verifyNoInteractions(routingContext);
    }
}

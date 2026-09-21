package com.easyperformance.api.tenant;

import com.easyperformance.api.dto.tenant.CreateTenantRequest;
import com.easyware.platform.AppSubscriptionStore;
import com.easyware.platform.NeonProvisioningService;
import com.easyware.platform.PlatformProductConfig;
import com.easyware.platform.PlatformTenant;
import com.easyware.platform.PlatformTenantStore;
import com.easyware.platform.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SystemAdminTenantsControllerTest {

    @Test
    void createPreflightsOwnerProvisionerBeforeWritingControlPlaneState() {
        @SuppressWarnings("unchecked")
        ObjectProvider<PlatformTenantStore> storeProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NeonProvisioningService> provisioningProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AppSubscriptionStore> subscriptionsProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PlatformProductConfig> productConfigProvider = mock(ObjectProvider.class);
        PlatformTenantStore store = mock(PlatformTenantStore.class);
        AppSubscriptionStore subscriptions = mock(AppSubscriptionStore.class);
        PlatformProductConfig productConfig = mock(PlatformProductConfig.class);
        PlatformTenant tenant = mock(PlatformTenant.class);
        UUID tenantId = UUID.fromString("019eb0d5-0000-7000-8000-000000000021");
        CreateTenantRequest request = new CreateTenantRequest(
                "ACME", "Acme Corp", "aws-ap-northeast-1", "owner", "owner@acme.test");

        when(storeProvider.getIfAvailable()).thenReturn(store);
        when(provisioningProvider.getIfAvailable()).thenReturn(null);
        when(subscriptionsProvider.getIfAvailable()).thenReturn(subscriptions);
        when(productConfigProvider.getIfAvailable()).thenReturn(productConfig);
        when(productConfig.appCode()).thenReturn("PERFORMANCE");
        when(store.create("ACME", "Acme Corp", "aws-ap-northeast-1", "owner", "owner@acme.test"))
                .thenReturn(tenant);
        when(tenant.id()).thenReturn(tenantId);

        SystemAdminTenantsController controller = new SystemAdminTenantsController(
                storeProvider, provisioningProvider, subscriptionsProvider, productConfigProvider);

        assertThatThrownBy(() -> controller.create(request)).isInstanceOf(ApiException.class);

        verify(store, never()).create("ACME", "Acme Corp", "aws-ap-northeast-1", "owner", "owner@acme.test");
        verifyNoInteractions(subscriptions);
    }
}

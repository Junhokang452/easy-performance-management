package com.easyperformance.security;

import com.easyperformance.domain.account.PerformanceUserRepository;
import com.easyware.platform.error.ApiException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpHeaders;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrowserSessionControllerTest {
    private final AuthController auth = mock(AuthController.class);
    private final AuthService service = mock(AuthService.class);
    private final PerformanceUserRepository users = mock(PerformanceUserRepository.class);
    private final AuthDtos.TokenResponse tokens = AuthDtos.TokenResponse.of("access-secret", 300, "refresh-secret", 604800,
        UUID.randomUUID(), UUID.randomUUID(), List.of("EMPLOYEE"));
    private final BrowserSessionController controller = new BrowserSessionController(auth, service, users, true);

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        return request;
    }

    @Test void loginKeepsTokensInHttpOnlyCookiesAndReturnsMetadataOnly() {
        var credentials = new AuthDtos.LoginRequest("member@example.test", "password");
        when(auth.authenticateWithTenant(credentials)).thenReturn(tokens);
        var response = controller.login(credentials, request());
        assertThat(response.getBody().userId()).isEqualTo(tokens.userId());
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2).allSatisfy(cookie -> {
            assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Strict");
        });
        assertThat(response.getBody().toString()).doesNotContain("access-secret", "refresh-secret");
    }

    @Test void refreshReadsCookieAndRotatesBothCookies() {
        var request = request();
        request.setCookies(new Cookie("easyperformance_refresh", "old-refresh"));
        when(auth.refreshWithTenant(new AuthDtos.RefreshRequest("old-refresh"))).thenReturn(tokens);
        var response = controller.refresh(request);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).anyMatch(c -> c.contains("refresh-secret"));
        verify(auth).refreshWithTenant(new AuthDtos.RefreshRequest("old-refresh"));
    }

    @Test void cookieMutationsRejectRequestsWithoutCsrfHeader() {
        assertThatThrownBy(() -> controller.refresh(new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        verifyNoInteractions(service);
    }

    @Test void explicitCrossSiteMutationCannotOverrideFetchMetadataWithFallbackHeader() {
        var request = request();
        request.addHeader("Sec-Fetch-Site", "cross-site");

        assertThatThrownBy(() -> BrowserSessionController.requireBrowserMutation(request))
            .isInstanceOf(ApiException.class);
    }

    @Test void logoutRevokesRefreshAndExpiresCookies() {
        var request = request();
        request.setCookies(new Cookie("easyperformance_refresh", "old-refresh"));
        var response = controller.logout(request);
        verify(service).logout(new AuthDtos.LogoutRequest("old-refresh"));
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2)
            .allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));
    }
}

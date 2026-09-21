package com.easyperformance.security;

import com.easyware.platform.auth.ParsedToken;
import com.easyware.platform.tenantctx.TenantContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {
    private final JwtService jwt = mock(JwtService.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwt, null, "", "");
    private final UUID user = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();

    @Test void refreshTokenCannotAuthenticateAnApiRequest() throws Exception {
        when(jwt.parse("refresh")).thenReturn(new ParsedToken(user.toString(),
            Map.of("tid",tenant.toString(),"typ","refresh","roles",List.of("HR_ADMIN"))));
        var request = new MockHttpServletRequest("GET", "/api/v1/cycles");
        request.addHeader("Authorization", "Bearer refresh");
        filter.doFilter(request, new MockHttpServletResponse(), (req,res) ->
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
    }

    @Test void productCookieAuthenticatesAndContextsAreCleared() throws Exception {
        when(jwt.parse("access")).thenReturn(new ParsedToken(user.toString(),
            Map.of("tid",tenant.toString(),"typ","access","roles",List.of("EMPLOYEE"))));
        var request = new MockHttpServletRequest("GET", "/api/v1/evaluation-workspace/me");
        request.setCookies(new Cookie("easyperformance_access","access"));
        filter.doFilter(request, new MockHttpServletResponse(), (req,res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(user);
            assertThat(TenantContext.get().getTenantId()).isEqualTo(tenant);
        });
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(TenantContext.get()).isNull();
    }

    @Test void tokenWithoutTenantDoesNotFallBackToDefaultTenant() throws Exception {
        when(jwt.parse("no-tenant")).thenReturn(new ParsedToken(user.toString(),Map.of("typ","access")));
        var request = new MockHttpServletRequest("GET", "/api/v1/cycles");
        request.addHeader("Authorization","Bearer no-tenant");
        filter.doFilter(request,new MockHttpServletResponse(),(req,res) ->
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
    }

    @Test void crossOriginCookieMutationNeverReachesService() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/cycles");
        request.setCookies(new Cookie("easyperformance_access","access"));
        request.addHeader("Sec-Fetch-Site","cross-site");
        var response = new MockHttpServletResponse();
        var reached = new AtomicBoolean();
        filter.doFilter(request,response,(req,res) -> reached.set(true));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(reached).isFalse();
    }

    @Test void modelBTenantRoutingFailureNeverAuthenticatesOrReachesService() throws Exception {
        var routing = mock(com.easyware.platform.TenantRoutingContext.class);
        var modelBFilter = new JwtAuthFilter(jwt, routing, "", "");
        when(jwt.parse("access")).thenReturn(new ParsedToken(user.toString(),
            Map.of("tid", tenant.toString(), "typ", "access", "roles", List.of("EMPLOYEE"))));
        when(routing.setByActiveId(tenant)).thenReturn(false);
        var request = new MockHttpServletRequest("GET", "/api/v1/evaluation-workspace/me");
        request.addHeader("Authorization", "Bearer access");
        var response = new MockHttpServletResponse();
        var reached = new AtomicBoolean();

        modelBFilter.doFilter(request, response, (req, res) -> reached.set(true));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(reached).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(TenantContext.get()).isNull();
    }
}

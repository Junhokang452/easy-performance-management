package com.easyperformance.config;

import com.easyperformance.security.JwtService;
import com.easyware.platform.TenantRoutingContext;
import com.easyware.platform.error.ErrorMessageResolver;
import com.easyware.platform.error.LocaleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigInternalAdminTest.ProbeController.class)
@ContextConfiguration(classes = {SecurityConfig.class, SecurityConfigInternalAdminTest.ProbeController.class})
class SecurityConfigInternalAdminTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProbeController probe;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private TenantRoutingContext tenantRoutingContext;

    @MockitoBean
    private ErrorMessageResolver errorMessageResolver;

    @MockitoBean
    private LocaleResolver localeResolver;

    @BeforeEach
    void resetProbe() {
        probe.reset();
    }

    @Test
    void internalAdminRejectsUnauthenticatedCallerBeforeHandler() throws Exception {
        mvc.perform(post("/api/internal/admin/probe"))
                .andExpect(status().isUnauthorized());

        assertThat(probe.invocations()).isZero();
    }

    @Test
    void internalAdminRejectsOrdinaryAuthenticatedRoleBeforeHandler() throws Exception {
        mvc.perform(post("/api/internal/admin/probe")
                        .with(user("employee").authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                .andExpect(status().isForbidden());

        assertThat(probe.invocations()).isZero();
    }

    @Test
    void internalAdminAllowsSuperAdminAndInvokesHandler() throws Exception {
        mvc.perform(post("/api/internal/admin/probe")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("SUPER_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().string("handled"));

        assertThat(probe.invocations()).isEqualTo(1);
    }

    @Test
    void ordinaryInternalS2sRouteRemainsPublicForControllerLevelAuthentication() throws Exception {
        mvc.perform(post("/api/internal/sync/probe"))
                .andExpect(status().isOk())
                .andExpect(content().string("s2s"));
    }

    @Test
    void memberFacadesReachObjectAuthorizationForAuthenticatedMembers() throws Exception {
        for (String path : new String[]{"/api/v1/evaluation-programs/probe", "/api/v1/evaluation-resources/probe"}) {
            mvc.perform(post(path)
                    .with(user("employee").authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
                .andExpect(status().isOk());
        }
    }

    @Test
    void memberFacadesRemainClosedToAnonymousCallers() throws Exception {
        for (String path : new String[]{"/api/v1/evaluation-programs/probe", "/api/v1/evaluation-resources/probe"}) {
            mvc.perform(post(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rawLegacyOperatorSurfaceRemainsClosedToMembers() throws Exception {
        mvc.perform(post("/api/v1/legacy-probe")
                .with(user("employee").authorities(new SimpleGrantedAuthority("EMPLOYEE"))))
            .andExpect(status().isForbidden());
    }

    @RestController
    static class ProbeController {
        private final AtomicInteger invocations = new AtomicInteger();

        @PostMapping("/api/internal/admin/probe")
        String adminProbe() {
            invocations.incrementAndGet();
            return "handled";
        }

        @PostMapping("/api/internal/sync/probe")
        String s2sProbe() {
            return "s2s";
        }

        @PostMapping({"/api/v1/evaluation-programs/probe", "/api/v1/evaluation-resources/probe",
            "/api/v1/legacy-probe"})
        String facadeProbe() {
            return "facade";
        }

        int invocations() {
            return invocations.get();
        }

        void reset() {
            invocations.set(0);
        }
    }
}

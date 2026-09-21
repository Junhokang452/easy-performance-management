package com.easyperformance.workflow;

import com.easyware.platform.UuidV7;
import com.easyperformance.domain.account.PerformanceUser;
import com.easyperformance.domain.account.PerformanceUserRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;
import com.easyware.platform.tenantctx.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActorAccessTest {

    private final PerformanceUserRepository users = mock(PerformanceUserRepository.class);
    private final ActorAccess access = new ActorAccess(users);

    @AfterEach
    void clearContexts() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireActor_resolvesBoundEmployeeFromAuthenticatedAccount() {
        UUID tenantId = UuidV7.generate();
        UUID userId = UuidV7.generate();
        UUID employeeId = UuidV7.generate();
        PerformanceUser user = user(userId, tenantId, employeeId, "MANAGER");
        TenantContext.set(TenantContext.b2b(tenantId, userId));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));
        when(users.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));

        ActorAccess.Actor actor = access.requireActor();

        assertThat(actor.userId()).isEqualTo(userId);
        assertThat(actor.tenantId()).isEqualTo(tenantId);
        assertThat(actor.employeeId()).isEqualTo(employeeId);
        assertThat(actor.role()).isEqualTo("MANAGER");
    }

    @Test
    void requireEmployeeActor_rejectsAccountWithoutEmployeeBinding() {
        UUID tenantId = UuidV7.generate();
        UUID userId = UuidV7.generate();
        PerformanceUser user = user(userId, tenantId, null, "EMPLOYEE");
        TenantContext.set(TenantContext.b2b(tenantId, userId));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));
        when(users.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));

        assertThatThrownBy(access::requireEmployeeActor)
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(PerformanceErrorCode.ACTOR_EMPLOYEE_BINDING_REQUIRED);
    }

    private static PerformanceUser user(UUID id, UUID tenantId, UUID employeeId, String role) {
        PerformanceUser user = new PerformanceUser();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setEmail(id + "@performance.dev");
        user.setPasswordHash("unused");
        user.setDisplayName("Actor");
        user.setRole(role);
        user.setEmployeeId(employeeId);
        user.setActive(true);
        return user;
    }
}

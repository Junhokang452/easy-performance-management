package com.easyperformance.workflow;

import com.easyperformance.domain.account.PerformanceUser;
import com.easyperformance.domain.account.PerformanceUserRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;
import com.easyware.platform.tenantctx.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/** Resolves the authenticated account and its Core Master employee binding. */
@Component
public class ActorAccess {

    private final PerformanceUserRepository users;

    public ActorAccess(PerformanceUserRepository users) {
        this.users = users;
    }

    public Actor requireActor() {
        TenantContext tenantContext = TenantContext.get();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (tenantContext == null || tenantContext.getTenantId() == null
            || authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(PerformanceErrorCode.AUTH_LOGIN_FAILED);
        }
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new ApiException(PerformanceErrorCode.AUTH_LOGIN_FAILED);
        }
        UUID tenantId = tenantContext.getTenantId();
        PerformanceUser account = users.findByIdAndTenantId(userId, tenantId)
            .filter(PerformanceUser::isActive)
            .orElseThrow(() -> new ApiException(PerformanceErrorCode.AUTH_USER_NOT_FOUND));
        return new Actor(account.getId(), tenantId, account.getEmployeeId(),
            account.getDisplayName(), account.getRole());
    }

    public Actor requireEmployeeActor() {
        Actor actor = requireActor();
        if (actor.employeeId() == null) {
            throw new ApiException(PerformanceErrorCode.ACTOR_EMPLOYEE_BINDING_REQUIRED,
                Map.of("userId", actor.userId()));
        }
        return actor;
    }

    public Actor requireAnyRole(String... roles) {
        Actor actor = requireActor();
        if (Arrays.stream(roles).noneMatch(actor.role()::equals)) {
            throw new ApiException(PerformanceErrorCode.WORKFLOW_FORBIDDEN,
                Map.of("requiredRoles", Arrays.asList(roles), "actualRole", actor.role()));
        }
        return actor;
    }

    public record Actor(UUID userId, UUID tenantId, UUID employeeId, String displayName, String role) {}
}

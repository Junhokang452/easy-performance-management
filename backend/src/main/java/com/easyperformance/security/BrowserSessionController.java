package com.easyperformance.security;

import com.easyperformance.domain.account.PerformanceUserRepository;
import com.easyperformance.error.PerformanceErrorCode;
import com.easyware.platform.error.ApiException;
import com.easyware.platform.tenantctx.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Browser session boundary: tokens stay in product-scoped HttpOnly cookies. */
@RestController
@RequestMapping("/api/auth/session")
public class BrowserSessionController {
    static final String ACCESS_COOKIE = "easyperformance_access";
    static final String REFRESH_COOKIE = "easyperformance_refresh";
    private final AuthController auth;
    private final AuthService service;
    private final PerformanceUserRepository users;
    private final boolean secure;

    public BrowserSessionController(AuthController auth, AuthService service, PerformanceUserRepository users,
            @Value("${performance.auth.cookie-secure:true}") boolean secure) {
        this.auth = auth; this.service = service; this.users = users; this.secure = secure;
    }

    public record SessionResponse(UUID userId, UUID tenantId, List<String> roles) {}

    @PostMapping("/login")
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody AuthDtos.LoginRequest credentials,
                                                HttpServletRequest request) {
        requireBrowserMutation(request);
        return issue(auth.authenticateWithTenant(credentials));
    }

    @PostMapping("/refresh")
    public ResponseEntity<SessionResponse> refresh(HttpServletRequest request) {
        requireBrowserMutation(request);
        String refresh = cookie(request, REFRESH_COOKIE);
        if (refresh == null) throw new ApiException(PerformanceErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
        return issue(auth.refreshWithTenant(new AuthDtos.RefreshRequest(refresh)));
    }

    @GetMapping
    public ResponseEntity<SessionResponse> session() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var tenant = TenantContext.get();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)
                || tenant == null || tenant.getTenantId() == null) {
            throw new ApiException(PerformanceErrorCode.AUTH_LOGIN_FAILED);
        }
        var user = users.findByIdAndTenantId(userId, tenant.getTenantId())
            .filter(u -> u.isActive()).orElseThrow(() -> new ApiException(PerformanceErrorCode.AUTH_LOGIN_FAILED));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(new SessionResponse(user.getId(), user.getTenantId(), List.of(user.getRole())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        requireBrowserMutation(request);
        service.logout(new AuthDtos.LogoutRequest(cookie(request, REFRESH_COOKIE)));
        return ResponseEntity.noContent().headers(cookieHeaders("", "", 0, 0)).build();
    }

    private ResponseEntity<SessionResponse> issue(AuthDtos.TokenResponse tokens) {
        return ResponseEntity.ok().headers(cookieHeaders(tokens.accessToken(), tokens.refreshToken(),
                tokens.accessExpiresInSec(), tokens.refreshExpiresInSec()))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(new SessionResponse(tokens.userId(), tokens.tenantId(), tokens.roles()));
    }

    private HttpHeaders cookieHeaders(String access, String refresh, long accessSeconds, long refreshSeconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, buildCookie(ACCESS_COOKIE, access, "/api", accessSeconds));
        headers.add(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_COOKIE, refresh, "/api/auth/session", refreshSeconds));
        return headers;
    }

    private String buildCookie(String name, String value, String path, long seconds) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(secure).sameSite("Strict")
            .path(path).maxAge(Duration.ofSeconds(seconds)).build().toString();
    }

    static void requireBrowserMutation(HttpServletRequest request) {
        // Fetch metadata cannot be forged by another browser origin. Older browsers use a non-simple
        // header, which is protected by the same-origin policy (cross-origin CORS is not enabled).
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ((fetchSite != null && !"same-origin".equals(fetchSite))
                || (fetchSite == null
                    && !"XMLHttpRequest".equals(request.getHeader("X-Requested-With")))) {
            throw new ApiException(PerformanceErrorCode.AUTH_LOGIN_FAILED);
        }
    }

    static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() != null) for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && !cookie.getValue().isBlank()) return cookie.getValue();
        }
        return null;
    }
}

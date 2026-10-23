package com.hishob.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /**
     * Service-to-service user lookup. Exposed under /api because the gateway has no internal
     * route, so it shares the hard 401 treatment of /internal/** instead of falling through to
     * the user-JWT chain. Both AuthServiceClient implementations always send the token.
     */
    public static final String SERVICE_API_PATH = "/api/auth/users/batch";

    private final String expectedToken;

    public InternalAuthFilter(
            @Value("${hishob.internal.token}") String expectedToken) {
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new IllegalStateException("hishob.internal.token is not configured; set the INTERNAL_SERVICE_TOKEN environment variable for this service");
        }
        if (expectedToken.length() < 32) {
            throw new IllegalStateException("hishob.internal.token must be at least 32 characters");
        }
        String value = expectedToken.toLowerCase();
        if (value.contains("your-") || value.contains("replace-with") || value.contains("changeme")
                || value.contains("change-me") || value.contains("placeholder") || value.contains("example-")) {
            throw new IllegalStateException("hishob.internal.token still holds a template placeholder value");
        }
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean serviceToService = path.startsWith("/internal/") || path.equals(SERVICE_API_PATH);
        if (!serviceToService) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (token == null || token.isBlank() || !token.equals(expectedToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "internal-service", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}

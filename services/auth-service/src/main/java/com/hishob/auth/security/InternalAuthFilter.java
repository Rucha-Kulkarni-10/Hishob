package com.hishob.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    public static final String INTERNAL_SERVICE_AUTHORITY = "INTERNAL_SERVICE";

    /**
     * Service-to-service user lookup. Exposed under /api because the gateway has no internal
     * route, so it shares the hard 401 treatment of /internal/** instead of falling through to
     * the user-JWT chain. Both AuthServiceClient implementations always send the token.
     */
    public static final String SERVICE_API_PATH = "/api/auth/users/batch";

    /**
     * Shared with the authorization rules so both layers decide "internal" from the
     * decoded request path, never from the raw URI string.
     */
    public static final RequestMatcher INTERNAL_MATCHER = PathPatternRequestMatcher.pathPattern("/internal/**");

    /** POST is the only verb AuthController maps on {@link #SERVICE_API_PATH}. */
    public static final RequestMatcher SERVICE_API_MATCHER =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, SERVICE_API_PATH);

    /**
     * B-08: account existence is a service-to-service lookup, not a public oracle. The only callers
     * are the group/expense AuthServiceClient.validateUserExists methods, which send the internal
     * token; no browser flow uses this path.
     */
    public static final RequestMatcher USER_EXISTS_MATCHER =
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/auth/users/*/exists");

    private final byte[] expectedToken;

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
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !INTERNAL_MATCHER.matches(request)
                && !SERVICE_API_MATCHER.matches(request)
                && !USER_EXISTS_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (token == null || token.isBlank()
                || !MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority(INTERNAL_SERVICE_AUTHORITY)));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}

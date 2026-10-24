package com.hishob.notification.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Constructs the real filter so its credential checks run, then confirms the request-time
 * comparison is unchanged. All values are synthetic and test-only.
 */
class InternalAuthFilterCredentialGuardTest {

    private static final String VALID_TOKEN = "0123456789abcdef0123456789abcdef0123456789";
    private static final String SAME_LENGTH_DIFFERENT_TOKEN = "0123456789abcdef0123456789abcdef0123456788";
    private static final List<String> PLACEHOLDER_TOKENS = List.of(
            "your-internal-token-0123456789abcdef",
            "REPLACE-WITH-INTERNAL-TOKEN-01234567",
            "changeme-changeme-changeme-changeme-",
            "change-me-now-change-me-now-change-me",
            "placeholder-token-placeholder-token-1",
            "example-token-example-token-example-t");

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Startup aborts when hishob.internal.token is missing")
    void rejectsMissingToken() {
        assertThatThrownBy(() -> new InternalAuthFilter(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hishob.internal.token is not configured");
    }

    @Test
    @DisplayName("Startup aborts when hishob.internal.token is empty or whitespace")
    void rejectsBlankToken() {
        for (String blank : List.of("", "   ", "\t")) {
            assertThatThrownBy(() -> new InternalAuthFilter(blank))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("hishob.internal.token is not configured");
        }
    }

    @Test
    @DisplayName("Startup aborts when hishob.internal.token is shorter than 32 characters")
    void rejectsTooShortToken() {
        assertThatThrownBy(() -> new InternalAuthFilter("0123456789abcdef0123456789abcde"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    @DisplayName("Startup aborts when hishob.internal.token is a template placeholder, in any case")
    void rejectsPlaceholderToken() {
        for (String placeholder : PLACEHOLDER_TOKENS) {
            assertThatThrownBy(() -> new InternalAuthFilter(placeholder))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("template placeholder");
        }
    }

    @Test
    @DisplayName("A placeholder hidden inside an otherwise long token is still rejected")
    void rejectsEmbeddedPlaceholderToken() {
        assertThatThrownBy(() -> new InternalAuthFilter("0123456789abcdef-your-token-0123456789"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("template placeholder");
    }

    @Test
    @DisplayName("A real-length, non-placeholder token starts normally and is retained verbatim")
    void acceptsConfiguredToken() {
        assertThatCode(() -> new InternalAuthFilter(VALID_TOKEN)).doesNotThrowAnyException();

        InternalAuthFilter filter = new InternalAuthFilter(VALID_TOKEN);
        assertThat((byte[]) ReflectionTestUtils.getField(filter, "expectedToken"))
                .as("The configured token is kept byte-for-byte, so the comparison cannot be weakened by normalisation")
                .isEqualTo(VALID_TOKEN.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Request-time behaviour unchanged: a wrong internal token gets 401")
    void wrongTokenStillRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/probe");
        request.addHeader(InternalAuthFilter.INTERNAL_TOKEN_HEADER, "0123456789abcdef0123456789abcdef99999999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new InternalAuthFilter(VALID_TOKEN).doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("A same-length token differing only in the final byte gets 401")
    void sameLengthDifferentTokenStillRejected() throws Exception {
        assertThat(SAME_LENGTH_DIFFERENT_TOKEN).hasSameSizeAs(VALID_TOKEN);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/probe");
        request.addHeader(InternalAuthFilter.INTERNAL_TOKEN_HEADER, SAME_LENGTH_DIFFERENT_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new InternalAuthFilter(VALID_TOKEN).doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Request-time behaviour unchanged: a missing token gets 401")
    void missingTokenStillRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/probe");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new InternalAuthFilter(VALID_TOKEN).doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("The configured token passes through and grants only the INTERNAL_SERVICE authority")
    void configuredTokenStillAccepted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/probe");
        request.addHeader(InternalAuthFilter.INTERNAL_TOKEN_HEADER, VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new InternalAuthFilter(VALID_TOKEN).doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest()).isSameAs(request);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .as("Exactly one authority, so hasAuthority() cannot be satisfied by an ordinary user role")
                .containsExactly(InternalAuthFilter.INTERNAL_SERVICE_AUTHORITY);
    }
}

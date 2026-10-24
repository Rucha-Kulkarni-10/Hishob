package com.hishob.expense.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invokes the real {@code @PostConstruct} guard so a missing, reordered or weakened check fails
 * the build. Every value below is synthetic and test-only.
 */
class JwtServiceCredentialGuardTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef01";
    private static final List<String> PLACEHOLDER_SECRETS = List.of(
            "your-secret-key-0123456789abcdefghij",
            "REPLACE-WITH-REAL-VALUE-0123456789ab",
            "changeme-changeme-changeme-changeme",
            "change-me-now-change-me-now-change-m",
            "placeholder-value-placeholder-value1",
            "example-secret-example-secret-example");

    private static JwtService withSecret(String secret) {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", secret);
        return jwtService;
    }

    @Test
    @DisplayName("Startup aborts when jwt.secret is missing")
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> withSecret(null).validateConfiguredSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret is not configured");
    }

    @Test
    @DisplayName("Startup aborts when jwt.secret is empty or whitespace")
    void rejectsBlankSecret() {
        for (String blank : List.of("", "   ", "\t")) {
            assertThatThrownBy(() -> withSecret(blank).validateConfiguredSecret())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jwt.secret is not configured");
        }
    }

    @Test
    @DisplayName("Startup aborts when jwt.secret is shorter than 32 bytes")
    void rejectsTooShortSecret() {
        assertThatThrownBy(() -> withSecret("0123456789abcdef0123456789abcde").validateConfiguredSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("Length is measured in UTF-8 bytes, not characters")
    void rejectsMultiByteSecretBelow32Bytes() {
        String fifteenTwoByteChars = "\u00e9".repeat(15);
        assertThat(fifteenTwoByteChars).hasSize(15);

        assertThatThrownBy(() -> withSecret(fifteenTwoByteChars).validateConfiguredSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("Startup aborts when jwt.secret is a template placeholder, in any case")
    void rejectsPlaceholderSecret() {
        for (String placeholder : PLACEHOLDER_SECRETS) {
            assertThatThrownBy(() -> withSecret(placeholder).validateConfiguredSecret())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("template placeholder");
        }
    }

    @Test
    @DisplayName("A placeholder hidden inside an otherwise long secret is still rejected")
    void rejectsEmbeddedPlaceholderSecret() {
        assertThatThrownBy(() -> withSecret("0123456789abcdef-changeme-0123456789").validateConfiguredSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("template placeholder");
    }

    @Test
    @DisplayName("A real-length, non-placeholder secret starts normally")
    void acceptsConfiguredSecret() {
        assertThatCode(() -> withSecret(VALID_SECRET).validateConfiguredSecret())
                .doesNotThrowAnyException();
    }
}

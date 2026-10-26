package com.hishob.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CORS regression tests for the deployed frontend origin.
 *
 * <p>The preflight assertions are also the proof that {@code OPTIONS} never reaches an
 * authentication filter or a downstream service: these tests start no upstream sink, yet the
 * gateway's test routes point at 127.0.0.1:9999, so a 2xx preflight can only come from
 * {@code CorsWebFilter} answering the request itself.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "cors.allowed-origins=https://hishob-one.vercel.app,http://localhost:5173")
class GatewayCorsPreflightTest {

    private static final String PRODUCTION_ORIGIN = "https://hishob-one.vercel.app";
    private static final String FOREIGN_ORIGIN = "https://not-a-hishob-deployment.example.com";
    private static final String ALLOWED_HEADERS = "authorization,content-type";

    @LocalServerPort
    private int gatewayPort;

    private HttpResponse<String> preflight(String path, String origin, String requestedMethod) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(15))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .uri(URI.create("http://127.0.0.1:" + gatewayPort + path))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", requestedMethod)
                .header("Access-Control-Request-Headers", ALLOWED_HEADERS)
                .build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<String> gatewayGet(String path, String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(15))
                .GET()
                .uri(URI.create("http://127.0.0.1:" + gatewayPort + path))
                .header("Origin", origin)
                .build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static List<String> headerValues(HttpResponse<?> response, String name) {
        return response.headers().allValues(name);
    }

    @Test
    @DisplayName("Preflight from the production Vercel origin is accepted and answered by the gateway")
    void productionOriginPreflightSucceeds() throws Exception {
        HttpResponse<String> response = preflight("/api/auth/login", PRODUCTION_ORIGIN, "POST");

        assertThat(response.statusCode()).as("preflight status").isEqualTo(200);
        assertThat(headerValues(response, "access-control-allow-origin"))
                .as("exactly one allow-origin value, wildcard forbidden")
                .containsExactly(PRODUCTION_ORIGIN);
    }

    @ParameterizedTest(name = "production origin may preflight {0}")
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE"})
    void allRequiredMethodsAreAllowed(String method) throws Exception {
        HttpResponse<String> response = preflight("/api/auth/register", PRODUCTION_ORIGIN, method);

        assertThat(response.statusCode()).as("preflight status for " + method).isEqualTo(200);
        assertThat(headerValues(response, "access-control-allow-methods").toString().toUpperCase(Locale.ROOT))
                .as("allowed methods for " + method)
                .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }

    @Test
    @DisplayName("Authorization and Content-Type are the allowed request headers")
    void requiredHeadersAreAllowed() throws Exception {
        HttpResponse<String> response = preflight("/api/expenses", PRODUCTION_ORIGIN, "POST");

        assertThat(response.statusCode()).isEqualTo(200);
        String allowHeaders = headerValues(response, "access-control-allow-headers").toString().toLowerCase(Locale.ROOT);
        assertThat(allowHeaders).contains("authorization").contains("content-type");
        assertThat(headerValues(response, "access-control-max-age"))
                .as("preflight result must be cacheable")
                .isNotEmpty();
    }

    @Test
    @DisplayName("No credentials header is emitted while the filter is configured cookie-less")
    void credentialsAreNotEnabled() throws Exception {
        HttpResponse<String> response = preflight("/api/auth/login", PRODUCTION_ORIGIN, "POST");

        assertThat(headerValues(response, "access-control-allow-credentials"))
                .as("frontend sends a Bearer token, not cookies; credentials must stay off")
                .isEmpty();
    }

    @Test
    @DisplayName("Preflight from an unlisted origin is rejected without any allow-origin header")
    void foreignOriginGetsNoCorsHeader() throws Exception {
        HttpResponse<String> response = preflight("/api/auth/login", FOREIGN_ORIGIN, "POST");

        assertThat(response.statusCode()).as("foreign origin preflight status").isNotEqualTo(200);
        assertThat(headerValues(response, "access-control-allow-origin"))
                .as("fail-closed: no CORS grant for an unknown origin")
                .isEmpty();
    }

    @Test
    @DisplayName("The local development origin keeps working")
    void localhostDevOriginIsAllowed() throws Exception {
        HttpResponse<String> response = preflight("/api/auth/login", "http://localhost:5173", "POST");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(headerValues(response, "access-control-allow-origin")).containsExactly("http://localhost:5173");
    }

    @Test
    @DisplayName("A gateway-handled GET carries exactly one allow-origin header")
    void actualRequestGetsASingleAllowOriginHeader() throws Exception {
        HttpResponse<String> response = gatewayGet("/health", PRODUCTION_ORIGIN);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(headerValues(response, "access-control-allow-origin"))
                .as("duplicate CORS configuration would emit two values and break the browser")
                .containsExactly(PRODUCTION_ORIGIN);
    }

    @Test
    @DisplayName("A gateway-handled GET from an unlisted origin is rejected, with no allow-origin header")
    void actualRequestFromForeignOriginGetsNoCorsHeader() throws Exception {
        HttpResponse<String> response = gatewayGet("/health", FOREIGN_ORIGIN);

        // Spring's DefaultCorsProcessor rejects a disallowed Origin on actual requests too, not only on
        // preflights. Asserting the 403 keeps this test honest: a permissive regression would show up here.
        assertThat(response.statusCode()).as("unlisted origin must be rejected").isEqualTo(403);
        assertThat(headerValues(response, "access-control-allow-origin")).isEmpty();
    }

    /**
     * The deployed allow-list lives in the Render Blueprint, not in this module, so a code-only fix can
     * silently leave production rejecting the frontend. Guard the value that actually ships.
     */
    @Test
    @DisplayName("render.yaml publishes the production origin in the gateway CORS allow-list")
    void blueprintGatewayAllowListContainsProductionOrigin() throws IOException {
        String value = renderYamlGatewayCorsValue();

        assertThat(value)
                .as("render.yaml hishob-gateway CORS_ALLOWED_ORIGINS value")
                .contains(PRODUCTION_ORIGIN);
        assertThat(value)
                .as("retired Vercel origin must not come back")
                .doesNotContain("hishob-web.vercel.app");
    }

    private String renderYamlGatewayCorsValue() throws IOException {
        List<String> lines = Files.readAllLines(repoRoot().resolve("render.yaml"));
        int gateway = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("name: hishob-gateway")) {
                gateway = i;
                break;
            }
        }
        assertThat(gateway).as("hishob-gateway service block in render.yaml").isNotEqualTo(-1);

        for (int i = gateway + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("- type:")) {
                break;
            }
            if (!line.equals("key: CORS_ALLOWED_ORIGINS") && !line.equals("- key: CORS_ALLOWED_ORIGINS")) {
                continue;
            }
            // The value must be the very next line. Scanning further would silently read a value belonging
            // to another service's envVar, which makes this guard pass when the gateway key is missing.
            String next = i + 1 < lines.size() ? lines.get(i + 1).trim() : "";
            assertThat(next)
                    .as("CORS_ALLOWED_ORIGINS must be followed immediately by its value line")
                    .startsWith("value:");
            return next.substring("value:".length()).trim().replace("\"", "");
        }
        throw new AssertionError("hishob-gateway declares no CORS_ALLOWED_ORIGINS key in render.yaml");
    }

    private Path repoRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.exists(candidate.resolve("render.yaml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new AssertionError("Repository root not found from " + Path.of("").toAbsolutePath());
    }
}

package com.hishob.expense.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import javax.crypto.SecretKey;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the internal-service trust boundary over real HTTP against embedded Tomcat, so the
 * path shapes below are judged by the container, the firewall, the filter and the authorization
 * layer rather than by MockMvc's request stub.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InternalServiceTrustBoundaryTest {

    private static final String TEST_SECRET = "hishob-test-jwt-secret-for-automated-tests-only-0123456789abcdef";
    private static final String INTERNAL_TOKEN = "hishob-test-internal-token-for-automated-tests-only";
    private static final String INTERNAL_TOKEN_SAME_LENGTH = "hishob-test-internal-token-for-automated-tests-onlX";

    private static final String INTERNAL_PATH = "/internal/groups/" + UUID.randomUUID() + "/expenses";
    private static final String USER_PATH = "/api/expenses/overview";

    /**
     * Spring Security writes its response headers only for requests that enter the filter chain, so
     * this header locates the layer that answered: absent means the container or the firewall refused
     * in front of the chain, present means the filter or the authorization layer refused inside it.
     */
    private static final String IN_CHAIN_MARKER = "X-Content-Type-Options";

    @LocalServerPort
    private int port;

    private String userJwt() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("stranger@test.com")
                .claim("userId", UUID.randomUUID().toString())
                .claim("name", "Stranger")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3_600_000L))
                .signWith(key)
                .compact();
    }

    /**
     * Sends the request target verbatim over a raw socket, so no HTTP client can normalise the path
     * shape under test and the response headers stay visible for layer attribution.
     */
    private record Response(int status, String headers, String body) {
    }

    private Response send(String rawPath, String internalToken, String bearerJwt) throws IOException {
        StringBuilder headers = new StringBuilder();
        StringBuilder body = new StringBuilder();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            socket.setSoTimeout(15000);
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            out.print("GET " + rawPath + " HTTP/1.1\r\n");
            out.print("Host: 127.0.0.1:" + port + "\r\n");
            if (internalToken != null) {
                out.print("X-Internal-Token: " + internalToken + "\r\n");
            }
            if (bearerJwt != null) {
                out.print("Authorization: Bearer " + bearerJwt + "\r\n");
            }
            out.print("Connection: close\r\n\r\n");
            out.flush();

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = in.readLine();
            assertThat(statusLine)
                    .as("Tomcat must answer the request target %s", rawPath)
                    .isNotNull();

            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                headers.append(line).append('\n');
            }
            while ((line = in.readLine()) != null) {
                body.append(line);
            }
            int status = Integer.parseInt(statusLine.split(" ")[1]);
            return new Response(status, headers.toString(), body.toString());
        }
    }

    private int statusFor(String rawPath, String internalToken, String bearerJwt) throws IOException {
        return send(rawPath, internalToken, bearerJwt).status();
    }

    @Test
    @DisplayName("Positive control: a valid internal token reaches the internal endpoint")
    void testInternalTokenReachesInternalEndpoint() throws Exception {
        assertThat(statusFor(INTERNAL_PATH, INTERNAL_TOKEN, null)).isEqualTo(200);
    }

    @Test
    @DisplayName("A user JWT alone cannot reach an internal endpoint")
    void testUserJwtCannotReachInternalEndpoint() throws Exception {
        Response response = send(INTERNAL_PATH, null, userJwt());

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers())
                .as("401 from the internal filter, so the request did reach the security chain")
                .contains(IN_CHAIN_MARKER);
        assertThat(response.body())
                .as("The filter answers with an empty 401, not an application error body")
                .isEmpty();
    }

    @Test
    @DisplayName("No credentials at all cannot reach an internal endpoint")
    void testAnonymousCannotReachInternalEndpoint() throws Exception {
        assertThat(statusFor(INTERNAL_PATH, null, null)).isEqualTo(401);
    }

    @Test
    @DisplayName("An internal token alone cannot reach an ordinary user endpoint")
    void testInternalTokenCannotReachUserEndpoint() throws Exception {
        assertThat(statusFor(USER_PATH, INTERNAL_TOKEN, null))
                .as("X-Internal-Token authenticates nothing on /api/**; anonymous fails authenticated() with 403")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("Positive control: a user JWT is still accepted on its own endpoint")
    void testUserJwtStillReachesUserEndpoint() throws Exception {
        assertThat(statusFor(USER_PATH, null, userJwt()))
                .as("A user JWT must not be locked out of /api/** by the internal rules")
                .isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"   ", "wrong-token", "hishob-test-internal-token-for-automated-tests-onlX"})
    @DisplayName("Blank, wrong and same-length-different internal tokens are rejected")
    void testBadInternalTokensRejected(String suppliedToken) throws Exception {
        assertThat(statusFor(INTERNAL_PATH, suppliedToken, null)).isEqualTo(401);
    }

    @Test
    @DisplayName("Same-length-but-different token differing only in the final byte is rejected")
    void testSameLengthTokenRejectedByConstantTimeCompare() throws Exception {
        assertThat(INTERNAL_TOKEN_SAME_LENGTH).hasSameSizeAs(INTERNAL_TOKEN);
        assertThat(INTERNAL_TOKEN_SAME_LENGTH).isNotEqualTo(INTERNAL_TOKEN);

        assertThat(statusFor(INTERNAL_PATH, INTERNAL_TOKEN_SAME_LENGTH, null)).isEqualTo(401);
    }

    @Test
    @DisplayName("Percent-encoded internal path is judged internal by both layers")
    void testPercentEncodedInternalPathStaysClosedToUserJwt() throws Exception {
        String encoded = INTERNAL_PATH.replaceFirst("/internal/", "/%69nternal/");

        assertThat(statusFor(encoded, null, userJwt())).isEqualTo(401);
        assertThat(statusFor(encoded, INTERNAL_TOKEN, null))
                .as("A legitimate internal caller must not be broken by the matcher change")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("Trailing-slash internal path is judged internal by both layers")
    void testTrailingSlashInternalPathStaysInternal() throws Exception {
        String trailing = INTERNAL_PATH + "/";

        assertThat(statusFor(trailing, null, userJwt()))
                .as("The trailing-slash shape must still be judged internal, not fall through to the user rules")
                .isEqualTo(401);
        assertThat(statusFor(trailing, INTERNAL_TOKEN, null))
                .as("A valid internal caller clears filter and authorization; only MVC's unmapped trailing slash is left")
                .isEqualTo(404);
    }

    /**
     * Each shape is refused with 400 for BOTH credential shapes, including a perfectly valid
     * internal token. The 400 carries no Spring Security headers and echoes the raw, un-normalized
     * request target, which only happens in front of the security chain — so these paths are stopped
     * by the firewall / Tomcat path check, never reaching the filter or authorization layer.
     */
    private void assertRefusedBeforeInternalFilter(String label, String rawPath) throws Exception {
        for (Response response : java.util.List.of(
                send(rawPath, INTERNAL_TOKEN, null),
                send(rawPath, null, userJwt()))) {
            assertThat(response.status())
                    .as("%s is refused with 400", label)
                    .isEqualTo(400);
            assertThat(response.headers())
                    .as("%s is answered in front of the security chain (no Spring Security headers)", label)
                    .doesNotContain(IN_CHAIN_MARKER);
            assertThat(response.body())
                    .as("%s echoes the raw request target, proving it was refused before normalization", label)
                    .contains(rawPath);
        }
    }

    @Test
    @DisplayName("Repeated leading slash never reaches the internal endpoint")
    void testDoubleSlashInternalPathRefused() throws Exception {
        assertRefusedBeforeInternalFilter("repeated leading slash", "//" + INTERNAL_PATH.substring(1));
    }

    @Test
    @DisplayName("Matrix parameter on an internal path is refused")
    void testMatrixParameterInternalPathRefused() throws Exception {
        assertRefusedBeforeInternalFilter("matrix parameter", INTERNAL_PATH + ";jsessionid=abc");
    }

    @Test
    @DisplayName("Dot-segment traversal into an internal path is refused")
    void testTraversalInternalPathRefused() throws Exception {
        assertRefusedBeforeInternalFilter("dot-segment traversal", "/api/.." + INTERNAL_PATH);
    }

    @Test
    @DisplayName("Encoded dot-segment never opens the internal surface")
    void testEncodedSlashPathRefused() throws Exception {
        assertRefusedBeforeInternalFilter("encoded dot-segment", "/internal/%2e%2e" + INTERNAL_PATH);
    }
}
